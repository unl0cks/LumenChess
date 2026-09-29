package dev.lumenchess.player

import android.content.Context
import dev.lumenchess.analysis.insights.GameKind
import dev.lumenchess.analysis.insights.GameOrigin
import dev.lumenchess.analysis.insights.Outcome
import dev.lumenchess.analysis.insights.PlayerGame
import dev.lumenchess.analysis.insights.ReviewStats
import dev.lumenchess.analysis.rating.TimeClass
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.analysis.review.ReviewReconstruction
import dev.lumenchess.analysis.review.StoredReviewPly
import dev.lumenchess.analysis.review.EvaluationCodec
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.LibraryCursor
import dev.lumenchess.data.persistence.LibraryEntry
import dev.lumenchess.data.persistence.LibraryQuery
import dev.lumenchess.data.persistence.ReviewState
import dev.lumenchess.play.PlayGameHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the library once and describes every game from the user's side: local Play games (the
 * "You" tag), imported site games whose player name matches a linked account, and Arena games.
 * Imports that are not the user's own games are left out rather than guessed at.
 */
object PlayerGamesLoader {
    const val MAX_GAMES = 2_000
    private const val OPENING_PLIES = 30

    suspend fun load(context: Context, settings: PlayerSettings, maxGames: Int = MAX_GAMES): List<PlayerGame> {
        val library = AppData.library(context)
        val entries = ArrayList<LibraryEntry>()
        var cursor: LibraryCursor? = null
        do {
            val page = library.page(LibraryQuery(), cursor, limit = 100)
            entries += page.entries
            cursor = page.nextCursor
        } while (cursor != null && entries.size < maxGames)

        val mainlines = AppData.index(context).mainlines(maxGames).associateBy { it.gameId.value }
        val reviews = AppData.reviews(context)
        val completed = reviews.reviewsInState(ReviewState.COMPLETE).associateBy { it.gameId.value }
        val plies = reviews.lightweightPlies(completed.values.toList())

        return withContext(Dispatchers.Default) {
            val book = AppData.openingBook
            entries.mapNotNull { entry ->
                val base = describe(entry, settings.identities) ?: return@mapNotNull null
                val line = mainlines[entry.id.value]
                val start = line?.let { runCatching { Fen.parse(it.startFen, it.variant) }.getOrNull() }
                val positions = if (line != null && start != null) {
                    val list = ArrayList<Position>().apply { add(start) }
                    for (move in line.moves.take(OPENING_PLIES)) {
                        if (MoveGenerator.legalMoves(list.last()).none { it == move }) break
                        list += MoveGenerator.applyLegalMove(list.last(), move)
                    }
                    list
                } else null
                val opening = positions?.let { book.identify(it) }
                val review = base.userColor?.let { color ->
                    val record = completed[entry.id.value] ?: return@let null
                    if (line == null || start == null) return@let null
                    val stored = plies[record.id].orEmpty().map { row ->
                        StoredReviewPly(
                            ply = row.ply,
                            scoreAfter = EvaluationCodec.fromColumns(row.playedEvalCp, row.playedMateIn),
                            bestMove = row.bestMove,
                            classification = MoveClassification.fromName(row.classification),
                            expectedPointsLoss = row.expectedPointsLoss,
                            depth = row.depth, nodes = row.nodes, timeMillis = row.timeMillis,
                        )
                    }
                    val rebuilt = runCatching { ReviewReconstruction.fromLightweight(start, line.moves, stored) }.getOrNull() ?: return@let null
                    val side = rebuilt.summary().side(color)
                    ReviewStats(side.accuracy, side.gameRating, side.counts, side.phaseAccuracy, side.averageCentipawnLoss)
                }
                base.copy(
                    openingEco = opening?.eco,
                    openingFamily = opening?.family,
                    openingName = opening?.name,
                    review = review,
                )
            }
        }
    }

    /** Kind, side and result of one library game, without its moves. Null when it is not the user's. */
    internal fun describe(entry: LibraryEntry, identities: Set<String>): PlayerGame? {
        val sources = entry.sources
        if (GameSourceType.BRANCH in sources && sources.size == 1) return null
        val white = entry.headers["White"] ?: entry.whiteName
        val black = entry.headers["Black"] ?: entry.blackName
        val origin = when {
            GameSourceType.CHESS_COM in sources -> GameOrigin.CHESS_COM
            GameSourceType.LICHESS in sources -> GameOrigin.LICHESS
            GameSourceType.PGN_IMPORT in sources -> GameOrigin.IMPORTED
            else -> GameOrigin.LOCAL
        }
        val arena = GameSourceType.ENGINE_ARENA in sources
        val userColor: Color? = when {
            arena -> null
            white == PlayGameHeaders.HUMAN_NAME && black != PlayGameHeaders.HUMAN_NAME -> Color.WHITE
            black == PlayGameHeaders.HUMAN_NAME && white != PlayGameHeaders.HUMAN_NAME -> Color.BLACK
            isUser(white, identities) && !isUser(black, identities) -> Color.WHITE
            isUser(black, identities) && !isUser(white, identities) -> Color.BLACK
            else -> null
        }
        if (userColor == null && !arena) return null
        val kind = when {
            arena -> GameKind.ARENA
            origin == GameOrigin.LOCAL -> GameKind.ENGINE
            else -> GameKind.HUMAN
        }
        val outcome = userColor?.let { color ->
            when (entry.result) {
                "DRAW" -> Outcome.DRAW
                "WHITE_WIN" -> if (color == Color.WHITE) Outcome.WIN else Outcome.LOSS
                "BLACK_WIN" -> if (color == Color.BLACK) Outcome.WIN else Outcome.LOSS
                else -> null
            }
        }
        val opponentTag = if (userColor == Color.WHITE) "BlackElo" else "WhiteElo"
        return PlayerGame(
            gameId = entry.id.value,
            kind = kind,
            origin = origin,
            userColor = userColor,
            outcome = outcome,
            variant = entry.variant,
            timeClass = timeClass(entry),
            rated = entry.metadata.rated == true,
            playedAtEpochMillis = entry.metadata.playedAtEpochMillis ?: entry.metadata.createdAtEpochMillis,
            opponentName = if (userColor == Color.WHITE) black else if (userColor == Color.BLACK) white else null,
            opponentRating = userColor?.let { entry.headers[opponentTag]?.toIntOrNull() },
        )
    }

    private fun isUser(name: String?, identities: Set<String>): Boolean =
        name != null && identities.contains(name.lowercase())

    private fun timeClass(entry: LibraryEntry): TimeClass {
        val control = entry.metadata.timeControl
        if (control?.baseMillis != null) return TimeClass.of(control.baseMillis, control.incrementMillis)
        // PGN "600+5" (seconds); "-" or missing means untimed / daily, counted as Rapid.
        val tag = entry.headers["TimeControl"] ?: control?.raw ?: return TimeClass.RAPID
        val base = tag.substringBefore('+').toLongOrNull() ?: return TimeClass.RAPID
        val increment = tag.substringAfter('+', "0").toLongOrNull() ?: 0L
        return TimeClass.of(base * 1_000L, increment * 1_000L)
    }
}

package dev.lumenchess.games.imports

import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.data.persistence.GamePersistenceMetadata
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.PersistedTermination
import dev.lumenchess.data.persistence.TimeControlMetadata
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One game ready to store, with the provenance its own headers establish. */
data class ImportCandidate(
    val tree: GameTree,
    val sourceType: GameSourceType,
    /** Stable id from the site that played the game; null for plain PGN (the content decides). */
    val externalGameId: String?,
    val externalUrl: String?,
    val metadata: GamePersistenceMetadata,
    val whiteName: String?,
    val blackName: String?,
)

data class PgnImportBatch(val candidates: List<ImportCandidate>, val unreadable: Int)

/**
 * Turns pasted, opened or downloaded PGN into import candidates. Every game goes through the strict,
 * legality-checked core-chess parser on its own, so one malformed or unsupported game (a variant
 * this app does not play, say) is counted and skipped instead of sinking the whole batch.
 */
object PgnImport {
    fun parse(text: String, importedAtEpochMillis: Long): PgnImportBatch {
        val candidates = ArrayList<ImportCandidate>()
        var unreadable = 0
        for (chunk in split(text)) {
            val tree = runCatching { Pgn.parseGame(chunk) }.getOrNull()
            if (tree == null || tree.mainline().isEmpty()) {
                unreadable += 1
                continue
            }
            candidates += classify(tree, importedAtEpochMillis)
        }
        return PgnImportBatch(candidates, unreadable)
    }

    /**
     * Splits a multi-game PGN at each tag section that follows movetext. Tag-looking text inside a
     * brace comment (clock and eval annotations) never starts a new game.
     */
    fun split(text: String): List<String> {
        val games = ArrayList<String>()
        val current = StringBuilder()
        var sawMovetext = false
        var braceDepth = 0
        for (rawLine in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = rawLine.trim()
            val isTag = braceDepth == 0 && line.startsWith("[") && line.endsWith("]")
            if (isTag && sawMovetext) {
                current.toString().trim().takeIf { it.isNotEmpty() }?.let(games::add)
                current.clear()
                sawMovetext = false
            }
            if (!isTag && line.isNotEmpty()) sawMovetext = true
            for (ch in line) {
                if (ch == '{') braceDepth += 1 else if (ch == '}') braceDepth = (braceDepth - 1).coerceAtLeast(0)
            }
            current.append(rawLine).append('\n')
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let(games::add)
        return games
    }

    fun classify(tree: GameTree, importedAtEpochMillis: Long): ImportCandidate {
        val headers = tree.headers
        val site = headers["Site"].orEmpty()
        val link = headers["Link"].orEmpty()
        val lichessId = LICHESS_URL.find(site)?.groupValues?.get(1)
        val chessComGame = CHESS_COM_GAME_URL.find(link) ?: CHESS_COM_GAME_URL.find(site)
        val isChessCom = chessComGame != null || site.equals("Chess.com", ignoreCase = true)
        val (type, externalId, url) = when {
            lichessId != null -> Triple(GameSourceType.LICHESS, lichessId, "https://lichess.org/$lichessId")
            isChessCom -> Triple(
                GameSourceType.CHESS_COM,
                chessComGame?.let { "${it.groupValues[1]}/${it.groupValues[2]}" },
                chessComGame?.value,
            )
            else -> Triple(GameSourceType.PGN_IMPORT, null, null)
        }
        return ImportCandidate(
            tree = tree,
            sourceType = type,
            externalGameId = externalId,
            externalUrl = url,
            metadata = GamePersistenceMetadata(
                createdAtEpochMillis = importedAtEpochMillis,
                importedAtEpochMillis = importedAtEpochMillis,
                playedAtEpochMillis = playedAt(headers),
                termination = termination(headers["Termination"], tree),
                timeControl = timeControl(headers["TimeControl"]),
            ),
            whiteName = headers["White"]?.takeIf(::isRealName),
            blackName = headers["Black"]?.takeIf(::isRealName),
        )
    }

    /** PGN "600+5" (seconds) into milliseconds; "-" is untimed; anything else is kept verbatim. */
    fun timeControl(value: String?): TimeControlMetadata? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() && it != "?" } ?: return null
        if (raw == "-") return TimeControlMetadata(baseMillis = 0L, incrementMillis = 0L, raw = raw)
        val match = FISCHER.matchEntire(raw) ?: return TimeControlMetadata(raw = raw)
        val base = match.groupValues[1].toLong() * 1_000L
        val increment = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLong()?.times(1_000L) ?: 0L
        return TimeControlMetadata(baseMillis = base, incrementMillis = increment, raw = raw)
    }

    fun playedAt(headers: Map<String, String>): Long? {
        val dateText = headers["UTCDate"] ?: headers["Date"] ?: return null
        val date = runCatching { LocalDate.parse(dateText, PGN_DATE) }.getOrNull() ?: return null
        val time = (headers["UTCTime"] ?: headers["StartTime"])
            ?.let { runCatching { LocalTime.parse(it, PGN_TIME) }.getOrNull() }
            ?: LocalTime.NOON
        return date.atTime(time).toInstant(ZoneOffset.UTC).toEpochMilli()
    }

    fun termination(text: String?, tree: GameTree): PersistedTermination? {
        // Chess.com writes "<username> won by resignation" / "Game drawn by repetition"; only the
        // reason after "won"/"drawn" may be matched, or a username like "timelord" would decide it.
        val lower = text?.lowercase().orEmpty()
        val value = REASON.find(lower)?.groupValues?.get(1) ?: lower
        return when {
            "checkmate" in value -> PersistedTermination.CHECKMATE
            "resign" in value -> PersistedTermination.RESIGNATION
            "insufficient" in value -> PersistedTermination.INSUFFICIENT_MATERIAL
            "stalemate" in value -> PersistedTermination.STALEMATE
            "repetition" in value -> PersistedTermination.THREEFOLD_REPETITION
            "50-move" in value || "50 move" in value || "fifty" in value -> PersistedTermination.FIFTY_MOVE_RULE
            "agree" in value -> PersistedTermination.AGREEMENT
            "time" in value -> PersistedTermination.TIMEOUT
            "abandon" in value -> PersistedTermination.ABANDONED
            else -> finalPositionTermination(tree)
        }
    }

    /** Lichess writes "Normal" for both mate and resignation; the final position tells them apart. */
    private fun finalPositionTermination(tree: GameTree): PersistedTermination? {
        if (tree.result == null) return null
        val position = tree.mainline().lastOrNull()?.position ?: return null
        if (MoveGenerator.legalMoves(position).isNotEmpty()) return null
        return if (MoveGenerator.isInCheck(position, position.sideToMove)) {
            PersistedTermination.CHECKMATE
        } else {
            PersistedTermination.STALEMATE
        }
    }

    private fun isRealName(name: String) = name.isNotBlank() && name != "?"

    private val LICHESS_URL = Regex("""https?://(?:www\.)?lichess\.org/([A-Za-z0-9]{8})""")
    private val CHESS_COM_GAME_URL = Regex("""https?://(?:www\.)?chess\.com/game/(live|daily)/(\d+)""")
    private val REASON = Regex("""\b(?:won|drawn)\b(.*)$""")
    private val FISCHER = Regex("""(\d+)(?:\+(\d+))?""")
    private val PGN_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val PGN_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
}

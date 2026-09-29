package dev.lumenchess.insights

import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.persistence.LibraryEntry
import dev.lumenchess.data.persistence.PersistedTermination
import dev.lumenchess.play.PlayGameHeaders

/**
 * A Library card becomes an [InsightGame] only when it is a finished Human-vs-Engine game whose
 * side can be read from its PGN tags. Imports, Arena games, unfinished games and games saved before
 * Play wrote player tags are left out rather than guessed at.
 */
internal fun LibraryEntry.toInsightGame(): InsightGame? {
    val whiteIsHuman = headers["White"] == PlayGameHeaders.HUMAN_NAME
    val blackIsHuman = headers["Black"] == PlayGameHeaders.HUMAN_NAME
    if (whiteIsHuman == blackIsHuman) return null

    val outcome = when (result) {
        "DRAW" -> InsightOutcome.DRAW
        "WHITE_WIN" -> if (whiteIsHuman) InsightOutcome.WIN else InsightOutcome.LOSS
        "BLACK_WIN" -> if (blackIsHuman) InsightOutcome.WIN else InsightOutcome.LOSS
        else -> return null
    }
    val ending = when (metadata.termination) {
        PersistedTermination.CHECKMATE -> InsightEnding.CHECKMATE
        PersistedTermination.TIMEOUT -> InsightEnding.TIMEOUT
        PersistedTermination.RESIGNATION -> InsightEnding.RESIGNATION
        PersistedTermination.AGREEMENT -> InsightEnding.AGREEMENT
        PersistedTermination.STALEMATE -> InsightEnding.STALEMATE
        PersistedTermination.THREEFOLD_REPETITION, PersistedTermination.FIVEFOLD_REPETITION -> InsightEnding.REPETITION
        PersistedTermination.FIFTY_MOVE_RULE, PersistedTermination.SEVENTY_FIVE_MOVE_RULE -> InsightEnding.FIFTY_MOVE
        PersistedTermination.INSUFFICIENT_MATERIAL -> InsightEnding.INSUFFICIENT_MATERIAL
        else -> InsightEnding.OTHER
    }
    val opponentEloTag = if (whiteIsHuman) "BlackElo" else "WhiteElo"
    return InsightGame(
        humanColor = if (whiteIsHuman) InsightColor.WHITE else InsightColor.BLACK,
        outcome = outcome,
        ending = ending,
        baseMillis = metadata.timeControl?.baseMillis,
        incrementMillis = metadata.timeControl?.incrementMillis,
        opponentElo = headers[opponentEloTag]?.toIntOrNull(),
        chess960 = variant == Variant.CHESS960,
        playedAtEpochMillis = metadata.playedAtEpochMillis ?: metadata.createdAtEpochMillis,
    )
}

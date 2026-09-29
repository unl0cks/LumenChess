package dev.lumenchess.insights

import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.persistence.GamePersistenceMetadata
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.LibraryEntry
import dev.lumenchess.data.persistence.PersistedTermination
import dev.lumenchess.data.persistence.PersistentGameId
import dev.lumenchess.data.persistence.TimeControlMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InsightsMappingTest {
    private fun entry(
        result: String?,
        headers: Map<String, String>,
        termination: PersistedTermination? = PersistedTermination.CHECKMATE,
        variant: Variant = Variant.STANDARD,
    ) = LibraryEntry(
        id = PersistentGameId("g"),
        variant = variant,
        result = result,
        metadata = GamePersistenceMetadata(
            createdAtEpochMillis = 1_000L,
            playedAtEpochMillis = 2_000L,
            termination = termination,
            timeControl = TimeControlMetadata(600_000L, 5_000L, "600+5"),
        ),
        whiteName = null, blackName = null, whiteEngineName = null, blackEngineName = null,
        headers = headers,
        sources = setOf(GameSourceType.LOCAL),
        latestReviewState = null,
        isFavorite = false,
        isProtected = false,
    )

    private val asWhite = mapOf("White" to "You", "Black" to "Stockfish 18", "BlackElo" to "1600")
    private val asBlack = mapOf("White" to "Reckless 0.9.0", "Black" to "You", "WhiteElo" to "900")

    @Test
    fun aWhiteWinIsAWinWhenThePlayerWasWhiteAndALossWhenBlack() {
        assertEquals(InsightOutcome.WIN, entry("WHITE_WIN", asWhite).toInsightGame()?.outcome)
        assertEquals(InsightOutcome.LOSS, entry("WHITE_WIN", asBlack).toInsightGame()?.outcome)
        assertEquals(InsightOutcome.LOSS, entry("BLACK_WIN", asWhite).toInsightGame()?.outcome)
        assertEquals(InsightOutcome.WIN, entry("BLACK_WIN", asBlack).toInsightGame()?.outcome)
        assertEquals(InsightOutcome.DRAW, entry("DRAW", asBlack, PersistedTermination.STALEMATE).toInsightGame()?.outcome)
    }

    @Test
    fun opponentStrengthAndColourAreReadFromTheRightTags() {
        val white = entry("WHITE_WIN", asWhite).toInsightGame()!!
        assertEquals(InsightColor.WHITE, white.humanColor)
        assertEquals(1600, white.opponentElo)

        val black = entry("BLACK_WIN", asBlack).toInsightGame()!!
        assertEquals(InsightColor.BLACK, black.humanColor)
        assertEquals(900, black.opponentElo)
    }

    @Test
    fun timeControlVariantAndEndingCarryThrough() {
        val game = entry("BLACK_WIN", asBlack, PersistedTermination.TIMEOUT, Variant.CHESS960).toInsightGame()!!

        assertEquals(600_000L, game.baseMillis)
        assertEquals(5_000L, game.incrementMillis)
        assertEquals(true, game.chess960)
        assertEquals(InsightEnding.TIMEOUT, game.ending)
        assertEquals(2_000L, game.playedAtEpochMillis)
    }

    @Test
    fun gamesWithoutAPlayerTagOrAResultAreLeftOut() {
        assertNull(entry("WHITE_WIN", mapOf("White" to "Alice", "Black" to "Bob")).toInsightGame())
        assertNull(entry("WHITE_WIN", emptyMap()).toInsightGame())
        assertNull(entry("WHITE_WIN", mapOf("White" to "You", "Black" to "You")).toInsightGame())
        assertNull(entry(null, asWhite).toInsightGame())
        assertNull(entry("*", asWhite).toInsightGame())
    }

    @Test
    fun fullStrengthOpponentsHaveNoElo() {
        val game = entry("DRAW", mapOf("White" to "You", "Black" to "Stockfish 18"), PersistedTermination.AGREEMENT).toInsightGame()!!
        assertNull(game.opponentElo)
        assertEquals(InsightEnding.AGREEMENT, game.ending)
    }
}

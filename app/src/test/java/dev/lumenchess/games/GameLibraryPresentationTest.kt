package dev.lumenchess.games

import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.persistence.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameLibraryPresentationTest {
    private val entry = LibraryEntry(PersistentGameId("fixture"), Variant.STANDARD, "WHITE_WIN",
        GamePersistenceMetadata(createdAtEpochMillis = 0, timeControl = TimeControlMetadata(180000, 2000)),
        whiteName = null, blackName = null, whiteEngineName = "Stockfish", blackEngineName = null,
        headers = mapOf("Black" to "Guest", "Date" to "2026.09.11", "WhiteElo" to "1820"),
        sources = setOf(GameSourceType.ENGINE_ARENA), latestReviewState = null, isFavorite = false, isProtected = false)

    @Test fun cardsTranslateStoredResultsAndUseOnlyAvailableMetadata() {
        for ((stored, display) in listOf("WHITE_WIN" to "1-0", "BLACK_WIN" to "0-1", "DRAW" to "1/2-1/2")) {
            assertEquals("$display · Standard · 2026.09.11 · 3+2", entry.copy(result = stored).summary())
        }
        assertEquals("Standard · 2026.09.11 · 3+2", entry.copy(result = null).summary())
        assertEquals("Stockfish vs Guest", entry.playerNames())
        assertTrue(entry.details().contains("White 1820"))
    }

    private fun control(base: Long?, increment: Long?, raw: String? = null, headers: Map<String, String> = emptyMap()) =
        libraryTimeControl(
            GamePersistenceMetadata(createdAtEpochMillis = 0, timeControl = TimeControlMetadata(base, increment, raw)),
            headers,
        )

    @Test fun recordedTimeControlIsShownAsMinutesPlusIncrementNeverAsRawMilliseconds() {
        // Play and Arena store the raw string in milliseconds; it must not reach the screen.
        assertEquals("10+0", control(600_000, 0, raw = "600000+0"))
        assertEquals("3+2", control(180_000, 2_000, raw = "180000+2000"))
        assertEquals("15+10", control(900_000, 10_000))
        assertEquals("3s+0", control(3_000, 0, raw = "3000+0"))
        assertEquals("90s+1", control(90_000, 1_000))
        assertEquals("10+0", control(600_000, null))
    }

    @Test fun untimedAndImportedGamesFallBackToWhatWasRecorded() {
        assertEquals("Untimed", control(0, 0, raw = "0+0"))
        assertEquals("600+5", control(null, null, raw = "600+5"))
        assertEquals("40/7200:3600", control(null, null, headers = mapOf("TimeControl" to "40/7200:3600")))
        assertEquals(null, control(null, null))
    }
}

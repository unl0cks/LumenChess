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
            assertEquals("$display · Standard · 2026.09.11 · 180s + 2s", entry.copy(result = stored).summary())
        }
        assertEquals("Standard · 2026.09.11 · 180s + 2s", entry.copy(result = null).summary())
        assertEquals("Stockfish vs Guest", entry.playerNames())
        assertTrue(entry.details().contains("White 1820"))
    }
}

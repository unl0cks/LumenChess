package dev.lumenchess.arena

import kotlin.test.Test
import kotlin.test.assertEquals

class ArenaParticipantStatusTest {
    @Test
    fun activeEngineIsThinkingOnlyWhileBothRequiredHostsAreAvailable() {
        assertEquals("Thinking", arenaParticipantStatus(true, false, true, "Stockfish ready"))
        assertEquals("Stockfish restarting…", arenaParticipantStatus(true, false, false, "Stockfish restarting…"))
        assertEquals("Stockfish ready", arenaParticipantStatus(true, true, true, "Stockfish ready"))
        assertEquals("Stockfish ready", arenaParticipantStatus(false, false, true, "Stockfish ready"))
    }
}

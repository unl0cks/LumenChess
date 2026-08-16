package dev.lumenchess.engine.api

import dev.lumenchess.core.chess.Fen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EngineSessionContractTest {
    @Test
    fun sessionIdentityAndLifecycleCommandsAreTransportNeutral() {
        val position = Fen.parse("8/8/8/8/8/8/4K3/7k w - - 0 1")
        val request = EngineSearchRequest(
            searchId = EngineSearchId(4),
            positionRevision = PositionRevision(12),
            position = position,
            limits = EngineSearchLimits(depth = 8),
        )

        assertEquals("stockfish-main", EngineSessionId("stockfish-main").value)
        assertEquals(EngineSessionCommand.NewGame, EngineSessionCommand.NewGame)
        assertEquals(EngineSessionCommand.StartSearch(request), EngineSessionCommand.StartSearch(request))
        assertEquals(EngineSessionCommand.StopSearch(EngineSearchId(4)), EngineSessionCommand.StopSearch(EngineSearchId(4)))
        assertEquals(EngineSessionCommand.Close, EngineSessionCommand.Close)
    }

    @Test
    fun blankSessionIdentityIsRejected() {
        assertFailsWith<IllegalArgumentException> { EngineSessionId("   ") }
    }
}

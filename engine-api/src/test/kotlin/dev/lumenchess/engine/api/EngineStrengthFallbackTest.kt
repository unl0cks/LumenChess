package dev.lumenchess.engine.api

import dev.lumenchess.core.chess.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EngineStrengthFallbackTest {
    @Test
    fun humanizedSelectionFallsBackToTerminalBestMoveWithoutCoherentCandidateSnapshot() {
        val planning = EngineStrengthPlanner.plan(
            settings = EngineStrengthSettings(
                target = EngineStrengthTarget.Elo(1200),
                model = EngineStrengthModel.HUMANIZED,
                seed = 0x51A7L,
            ),
            capabilities = EngineCapabilities(
                variants = setOf(Variant.STANDARD, Variant.CHESS960),
                multiPv = EngineMultiPvCapability(256),
            ),
        )
        val plan = assertIs<EngineStrengthPlanning.Supported>(planning).plan

        assertEquals(
            "e2e4",
            EngineCandidateSelector.select(
                candidates = emptyList(),
                fallbackBestMoveUci = "e2e4",
                plan = plan,
                searchId = EngineSearchId(7001),
                positionRevision = PositionRevision(42),
            ),
        )
    }
}

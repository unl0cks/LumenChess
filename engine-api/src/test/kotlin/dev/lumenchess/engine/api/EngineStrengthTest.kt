package dev.lumenchess.engine.api

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineStrengthTest {
    private val stockfishLikeCapabilities = EngineCapabilities(
        variants = setOf(Variant.STANDARD, Variant.CHESS960),
        strength = EngineStrengthCapability.EloRange(1320, 3190),
    )
    private val noNativeStrengthCapabilities = EngineCapabilities(
        variants = setOf(Variant.STANDARD, Variant.CHESS960),
        strength = null,
    )

    @Test
    fun eloTargetsUseProductRangeWithoutForcingUiStepSize() {
        assertEquals(400, EngineStrengthTarget.Elo(400).value)
        assertEquals(3000, EngineStrengthTarget.Elo(3000).value)
        assertEquals(1737, EngineStrengthTarget.Elo(1737).value)
        assertFailsWith<IllegalArgumentException> { EngineStrengthTarget.Elo(399) }
        assertFailsWith<IllegalArgumentException> { EngineStrengthTarget.Elo(3001) }
    }

    @Test
    fun searchRequestDefaultsToFullStrengthAndCarriesExplicitStrengthIntent() {
        val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val defaults = EngineSearchRequest(
            searchId = EngineSearchId(1),
            positionRevision = PositionRevision(0),
            position = position,
            limits = EngineSearchLimits(depth = 4),
        )
        assertIs<EngineStrengthTarget.FullStrength>(defaults.strength.target)

        val explicit = EngineStrengthSettings(
            target = EngineStrengthTarget.Elo(1450),
            model = EngineStrengthModel.HYBRID,
            seed = 0x1234L,
        )
        val request = defaults.copy(strength = explicit)
        assertEquals(explicit, request.strength)
    }

    @Test
    fun fullStrengthBypassesNativeAndHumanizedLimiting() {
        val resolution = EngineStrengthPlanner.plan(
            EngineStrengthSettings.fullStrength(seed = 9L),
            stockfishLikeCapabilities,
        )
        val plan = assertIs<EngineStrengthPlanning.Supported>(resolution).plan
        assertNull(plan.nativeElo)
        assertNull(plan.humanization)
        assertEquals(EngineStrengthPlanner.CALIBRATION_VERSION, plan.calibrationVersion)
    }

    @Test
    fun engineNativeClampsToAdvertisedRangeAndNeverAddsHumanization() {
        val low = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(400), EngineStrengthModel.ENGINE_NATIVE, seed = 1L),
            stockfishLikeCapabilities,
        )
        assertEquals(1320, low.nativeElo)
        assertNull(low.humanization)

        val normal = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(1800), EngineStrengthModel.ENGINE_NATIVE, seed = 2L),
            stockfishLikeCapabilities,
        )
        assertEquals(1800, normal.nativeElo)
        assertNull(normal.humanization)
    }

    @Test
    fun engineNativeIsExplicitlyUnsupportedWhenEngineHasNoNativeLimiter() {
        val resolution = EngineStrengthPlanner.plan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(1400), EngineStrengthModel.ENGINE_NATIVE, seed = 3L),
            noNativeStrengthCapabilities,
        )
        val unsupported = assertIs<EngineStrengthPlanning.Unsupported>(resolution)
        assertTrue(unsupported.reason.contains("native", ignoreCase = true))
    }

    @Test
    fun hybridUsesNativeLimiterWhenAvailableAndHumanizationFadesWithElo() {
        val low = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(800), EngineStrengthModel.HYBRID, seed = 4L),
            stockfishLikeCapabilities,
        )
        val high = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(2800), EngineStrengthModel.HYBRID, seed = 4L),
            stockfishLikeCapabilities,
        )

        assertEquals(1320, low.nativeElo)
        assertEquals(2800, high.nativeElo)
        val lowHumanization = assertNotNull(low.humanization)
        val highLoss = high.humanization?.maximumLossCentipawns ?: 0
        val highCandidates = high.humanization?.candidateCount ?: 1
        assertTrue(highLoss < lowHumanization.maximumLossCentipawns)
        assertTrue(highCandidates <= lowHumanization.candidateCount)
    }

    @Test
    fun hybridFallsBackToHumanizationWhenNativeLimiterIsUnavailable() {
        val plan = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(1600), EngineStrengthModel.HYBRID, seed = 5L),
            noNativeStrengthCapabilities,
        )
        assertNull(plan.nativeElo)
        assertNotNull(plan.humanization)
    }

    @Test
    fun humanizedModeIsEngineAgnostic() {
        val plan = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(1800), EngineStrengthModel.HUMANIZED, seed = 6L),
            noNativeStrengthCapabilities,
        )
        assertNull(plan.nativeElo)
        assertNotNull(plan.humanization)
    }

    @Test
    fun calibrationIsExplicitlyVersioned() {
        assertTrue(EngineStrengthPlanner.CALIBRATION_VERSION.isNotBlank())
        assertTrue(EngineStrengthPlanner.CALIBRATION_VERSION.startsWith("m15-"))
    }

    @Test
    fun selectorIsDeterministicForSameSeedSearchRevisionAndCandidates() {
        val plan = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(1200), EngineStrengthModel.HUMANIZED, seed = 0x55AAL),
            noNativeStrengthCapabilities,
        )
        val candidates = plausibleCandidates()

        val first = EngineCandidateSelector.select(
            candidates,
            fallbackBestMoveUci = "e2e4",
            plan = plan,
            searchId = EngineSearchId(17),
            positionRevision = PositionRevision(23),
        )
        repeat(20) {
            assertEquals(
                first,
                EngineCandidateSelector.select(
                    candidates,
                    fallbackBestMoveUci = "e2e4",
                    plan = plan,
                    searchId = EngineSearchId(17),
                    positionRevision = PositionRevision(23),
                ),
            )
        }
    }

    @Test
    fun selectorCanChooseDifferentPlausibleCandidatesAcrossSeeds() {
        val outputs = (1L..128L).mapTo(mutableSetOf()) { seed ->
            val plan = supportedPlan(
                EngineStrengthSettings(EngineStrengthTarget.Elo(800), EngineStrengthModel.HUMANIZED, seed = seed),
                noNativeStrengthCapabilities,
            )
            EngineCandidateSelector.select(
                plausibleCandidates(),
                fallbackBestMoveUci = "e2e4",
                plan = plan,
                searchId = EngineSearchId(3),
                positionRevision = PositionRevision(9),
            )
        }
        assertTrue(outputs.size > 1, "Seeded selector should vary among plausible candidates")
    }

    @Test
    fun selectorNeverChoosesCandidateOutsideCalibratedLossWindow() {
        val candidates = plausibleCandidates() + EngineMoveCandidate(
            rank = 8,
            moveUci = "a2a4",
            score = EngineCandidateScore.Centipawns(-5000),
        )
        repeat(128) { index ->
            val plan = supportedPlan(
                EngineStrengthSettings(
                    EngineStrengthTarget.Elo(400),
                    EngineStrengthModel.HUMANIZED,
                    seed = index.toLong(),
                ),
                noNativeStrengthCapabilities,
            )
            val selected = EngineCandidateSelector.select(
                candidates,
                fallbackBestMoveUci = "e2e4",
                plan = plan,
                searchId = EngineSearchId(4),
                positionRevision = PositionRevision(10),
            )
            assertTrue(selected != "a2a4")
        }
    }

    @Test
    fun selectorIsIndependentOfCandidateInputOrdering() {
        val plan = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(1200), EngineStrengthModel.HUMANIZED, seed = 99L),
            noNativeStrengthCapabilities,
        )
        val candidates = plausibleCandidates()
        val forward = EngineCandidateSelector.select(
            candidates,
            fallbackBestMoveUci = "e2e4",
            plan = plan,
            searchId = EngineSearchId(5),
            positionRevision = PositionRevision(11),
        )
        val reversed = EngineCandidateSelector.select(
            candidates.reversed(),
            fallbackBestMoveUci = "e2e4",
            plan = plan,
            searchId = EngineSearchId(5),
            positionRevision = PositionRevision(11),
        )
        assertEquals(forward, reversed)
    }

    @Test
    fun mateWinningCandidateOutranksCentipawnCandidates() {
        val plan = supportedPlan(
            EngineStrengthSettings(EngineStrengthTarget.Elo(400), EngineStrengthModel.HUMANIZED, seed = 12L),
            noNativeStrengthCapabilities,
        )
        val selected = EngineCandidateSelector.select(
            listOf(
                EngineMoveCandidate(2, "d1h5", EngineCandidateScore.Centipawns(900)),
                EngineMoveCandidate(1, "f7f8", EngineCandidateScore.Mate(2)),
                EngineMoveCandidate(3, "a2a3", EngineCandidateScore.Centipawns(100)),
            ),
            fallbackBestMoveUci = "f7f8",
            plan = plan,
            searchId = EngineSearchId(6),
            positionRevision = PositionRevision(12),
        )
        assertEquals("f7f8", selected)
    }

    private fun supportedPlan(
        settings: EngineStrengthSettings,
        capabilities: EngineCapabilities,
    ): EngineStrengthPlan = assertIs<EngineStrengthPlanning.Supported>(
        EngineStrengthPlanner.plan(settings, capabilities),
    ).plan

    private fun plausibleCandidates(): List<EngineMoveCandidate> = listOf(
        EngineMoveCandidate(1, "e2e4", EngineCandidateScore.Centipawns(35)),
        EngineMoveCandidate(2, "d2d4", EngineCandidateScore.Centipawns(15)),
        EngineMoveCandidate(3, "g1f3", EngineCandidateScore.Centipawns(-10)),
        EngineMoveCandidate(4, "c2c4", EngineCandidateScore.Centipawns(-35)),
        EngineMoveCandidate(5, "b1c3", EngineCandidateScore.Centipawns(-70)),
        EngineMoveCandidate(6, "g2g3", EngineCandidateScore.Centipawns(-110)),
    )
}

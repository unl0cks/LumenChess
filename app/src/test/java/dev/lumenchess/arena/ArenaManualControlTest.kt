package dev.lumenchess.arena

import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlayEngineGateway
import dev.lumenchess.runtime.RuntimeDisposition
import dev.lumenchess.runtime.ManualClockPolicy
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArenaManualControlTest {
    private object NoopEngine : PlayEngineGateway {
        override fun startSearch(request: EngineSearchRequest) = Unit
        override fun cancelSearch(searchId: EngineSearchId) = Unit
    }

    private object NoopPersistence : ArenaPersistenceGateway {
        override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedArenaSetup) = Unit
    }

    @Test
    fun manualOpeningResolvesPerSideFiniteLeasesAndLockedDefault() {
        val setup = ArenaSetupResolver.resolve(
            ArenaSetupConfig(
                manualOpening = ArenaManualOpeningSetup(
                    sides = ArenaManualSide.BOTH,
                    limitMode = ArenaManualLimitMode.FINITE,
                    moveLimitText = "4",
                ),
            ),
            randomInt = { 0 },
        )

        assertEquals(4, setup.manualControl.white?.remainingMoves)
        assertEquals(4, setup.manualControl.black?.remainingMoves)
        assertEquals(ManualClockPolicy.LOCKED, setup.manualControl.clockPolicy)

        val coordinator = ArenaRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { 1_000L },
            NoopEngine,
            NoopEngine,
            NoopPersistence,
        )
        assertEquals(RuntimeController.HUMAN, coordinator.state.controllers.white)
        assertEquals(RuntimeController.HUMAN, coordinator.state.controllers.black)
    }

    @Test
    fun untilReleaseResolvesUnlimitedLeaseAndInvalidFiniteLimitIsRejected() {
        val untilRelease = ArenaSetupResolver.resolve(
            ArenaSetupConfig(
                manualOpening = ArenaManualOpeningSetup(
                    sides = ArenaManualSide.WHITE,
                    limitMode = ArenaManualLimitMode.UNTIL_RELEASE,
                ),
            ),
            randomInt = { 0 },
        )
        assertTrue(untilRelease.manualControl.white?.remainingMoves == null)

        val invalid = ArenaSetupValidator.validate(
            ArenaSetupConfig(
                manualOpening = ArenaManualOpeningSetup(
                    sides = ArenaManualSide.BLACK,
                    moveLimitText = "0",
                ),
            ),
        )
        assertTrue(invalid is ArenaSetupValidation.Invalid)
    }

    @Test
    fun manualMetadataUsesVersionTwoAndCarriesControllerPolicy() {
        val setup = ArenaSetupResolver.resolve(
            ArenaSetupConfig(
                white = ArenaEngineConfig(
                    engine = PlayEngine.STOCKFISH_18,
                    strengthModel = EngineStrengthModel.ENGINE_NATIVE,
                    strengthTarget = EngineStrengthTarget.Elo(2000),
                ),
                manualOpening = ArenaManualOpeningSetup(sides = ArenaManualSide.WHITE),
            ),
            randomInt = { 0 },
        )
        val coordinator = ArenaRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { 1_000L },
            NoopEngine,
            NoopEngine,
            NoopPersistence,
        )
        val encoded = ArenaSnapshotCodec.encode(coordinator.snapshotForRestore(), setup)

        assertEquals("2", encoded["lumen.arena.m20.version"])
        assertEquals("HUMAN", encoded["lumen.arena.m20.whiteController"])
        assertEquals("UNLIMITED", encoded["lumen.arena.m20.manualWhite"])
        assertEquals("LOCKED", encoded["lumen.arena.m20.manualClockPolicy"])
    }

    @Test
    fun coordinatorRoutesLegalManualMoveAndHandsExpiredSideBackToEngine() {
        val setup = ArenaSetupResolver.resolve(
            ArenaSetupConfig(
                manualOpening = ArenaManualOpeningSetup(
                    sides = ArenaManualSide.WHITE,
                    limitMode = ArenaManualLimitMode.FINITE,
                    moveLimitText = "1",
                ),
            ),
            randomInt = { 0 },
        )
        val white = RecordingEngine()
        val black = RecordingEngine()
        val coordinator = ArenaRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { 1_000L },
            white,
            black,
            NoopPersistence,
        )

        coordinator.start()
        coordinator.onEngineHostRecovered(Color.WHITE)
        coordinator.onEngineHostRecovered(Color.BLACK)
        val illegal = coordinator.humanMove(Move.parseUci("e2e5"))
        val legal = coordinator.humanMove(Move.parseUci("e2e4"))

        assertEquals(RuntimeDisposition.ILLEGAL_HUMAN_MOVE, illegal.disposition)
        assertEquals(1, illegal.state.manualControl.white?.remainingMoves)
        assertEquals(RuntimeDisposition.APPLIED, legal.disposition)
        assertEquals(PositionRevision(1), legal.state.positionRevision)
        assertEquals(RuntimeController.ENGINE, legal.state.controllers.white)
        assertEquals(1, black.started.size)
        assertEquals(PositionRevision(1), black.started.single().positionRevision)
    }

    private class RecordingEngine : PlayEngineGateway {
        val started = mutableListOf<EngineSearchRequest>()
        override fun startSearch(request: EngineSearchRequest) { started += request }
        override fun cancelSearch(searchId: EngineSearchId) = Unit
    }
}

package dev.lumenchess.arena

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.data.persistence.GamePersistenceMetadata
import dev.lumenchess.data.persistence.GameSourceRecord
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.LoadedCanonicalGame
import dev.lumenchess.data.persistence.PersistenceMappingException
import dev.lumenchess.data.persistence.PersistentGameId
import dev.lumenchess.data.persistence.PersistentSourceId
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlayEngineGateway
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.ManualClockPolicy
import dev.lumenchess.runtime.ManualControlLease
import dev.lumenchess.runtime.RuntimeManualControl
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaSnapshotCodecTest {
    private object NoopEngine : PlayEngineGateway {
        override fun startSearch(request: EngineSearchRequest) = Unit
        override fun cancelSearch(searchId: EngineSearchId) = Unit
    }
    private object NoopPersistence : ArenaPersistenceGateway {
        override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedArenaSetup) = Unit
    }

    @Test
    fun independentArenaConfigurationRoundTripsAsEngineArenaMetadata() {
        val setup = ArenaSetupResolver.resolve(
            ArenaSetupConfig(
                white = ArenaEngineConfig(
                    PlayEngine.RECKLESS_0_9_0,
                    EngineStrengthModel.HUMANIZED,
                    EngineStrengthTarget.Elo(1100),
                    12,
                ),
                black = ArenaEngineConfig(
                    PlayEngine.STOCKFISH_18,
                    EngineStrengthModel.ENGINE_NATIVE,
                    EngineStrengthTarget.Elo(2100),
                    34,
                ),
                timeControl = PlayTimeControl(180_000, 2_000),
                opening = ArenaOpeningSetup(
                    ArenaOpeningMode.OPENING_FAMILY,
                    familyId = "queens-pawn",
                    handoffPlies = 6,
                ),
            ),
            randomInt = { 0 },
        )
        val coordinator = ArenaRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { 5_000 },
            NoopEngine,
            NoopEngine,
            NoopPersistence,
        )
        coordinator.start()
        val snapshot = coordinator.snapshotForRestore()

        val decoded = ArenaSnapshotCodec.decode(loaded(snapshot, ArenaSnapshotCodec.encode(snapshot, setup)))

        assertEquals(PlayEngine.RECKLESS_0_9_0, decoded.setup.white.engine)
        assertEquals(EngineStrengthTarget.Elo(1100), decoded.setup.white.strength.target)
        assertEquals(PlayEngine.STOCKFISH_18, decoded.setup.black.engine)
        assertEquals(EngineStrengthModel.ENGINE_NATIVE, decoded.setup.black.strength.model)
        assertEquals(ArenaOpeningMode.OPENING_FAMILY, decoded.setup.opening.mode)
        assertEquals("queens-pawn", decoded.setup.opening.familyId)
        assertEquals(Fen.serialize(setup.initialPosition), Fen.serialize(decoded.setup.initialPosition))
        assertTrue(decoded.snapshot.paused)
        assertFalse(decoded.snapshot.clock.running)
        assertEquals(Color.WHITE, decoded.snapshot.position.sideToMove)
    }

    @Test
    fun nonArenaSourceCannotMasqueradeAsRestorableArena() {
        val setup = ArenaSetupResolver.resolve(ArenaSetupConfig(), randomInt = { 0 })
        val coordinator = ArenaRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { 1_000 },
            NoopEngine,
            NoopEngine,
            NoopPersistence,
        )
        val snapshot = coordinator.snapshotForRestore()
        val game = loaded(snapshot, ArenaSnapshotCodec.encode(snapshot, setup), GameSourceType.LOCAL)

        assertFailsWith<PersistenceMappingException> { ArenaSnapshotCodec.decode(game) }
    }

    @Test
    fun legacyVersionOneArenaMetadataRestoresAsEngineVsEngine() {
        val setup = ArenaSetupResolver.resolve(ArenaSetupConfig(), randomInt = { 0 })
        val coordinator = ArenaRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { 1_000 },
            NoopEngine,
            NoopEngine,
            NoopPersistence,
        )
        val snapshot = coordinator.snapshotForRestore()
        val legacy = ArenaSnapshotCodec.encode(snapshot, setup).toMutableMap().apply {
            this["lumen.arena.m20.version"] = "1"
            remove("lumen.arena.m20.whiteController")
            remove("lumen.arena.m20.blackController")
            remove("lumen.arena.m20.manualClockPolicy")
            remove("lumen.arena.m20.manualWhite")
            remove("lumen.arena.m20.manualBlack")
        }

        val decoded = ArenaSnapshotCodec.decode(loaded(snapshot, legacy))

        assertEquals(RuntimeController.ENGINE, decoded.snapshot.controllers.white)
        assertEquals(RuntimeController.ENGINE, decoded.snapshot.controllers.black)
        assertTrue(decoded.snapshot.manualControl == dev.lumenchess.runtime.RuntimeManualControl())
    }

    @Test
    fun manualLeasesAndClockPolicyRoundTripAfterAcceptedMoves() {
        val setup = ArenaSetupResolver.resolve(
            ArenaSetupConfig(manualOpening = ArenaManualOpeningSetup(
                sides = ArenaManualSide.BOTH,
                moveLimitText = "3",
                clockPolicy = ManualClockPolicy.COUNT_TIME,
            )),
            randomInt = { 0 },
        )
        val coordinator = ArenaRuntimeCoordinator.create(setup, MonotonicTimeSource { 1_000 }, NoopEngine, NoopEngine, NoopPersistence)
        coordinator.start()
        coordinator.humanMove(dev.lumenchess.core.chess.Move.parseUci("e2e4"))
        coordinator.setManualControl(coordinator.state.manualControl.copy(black = ManualControlLease()))
        val snapshot = coordinator.snapshotForRestore()
        val decoded = ArenaSnapshotCodec.decode(loaded(snapshot, ArenaSnapshotCodec.encode(snapshot, setup)))

        assertEquals(RuntimeManualControl(ManualControlLease(2), ManualControlLease(), ManualClockPolicy.COUNT_TIME), decoded.snapshot.manualControl)
        assertEquals(snapshot.controllers, decoded.snapshot.controllers)
        assertEquals(snapshot.position, decoded.snapshot.position)
        assertEquals(snapshot.positionRevision, decoded.snapshot.positionRevision)
        assertTrue(decoded.snapshot.paused)
        assertFalse(decoded.snapshot.clock.running)
    }

    private fun loaded(
        snapshot: RuntimeSnapshot,
        metadata: Map<String, String>,
        type: GameSourceType = GameSourceType.ENGINE_ARENA,
    ) = LoadedCanonicalGame(
        id = PersistentGameId("arena-1"),
        tree = snapshot.gameTree,
        metadata = GamePersistenceMetadata(createdAtEpochMillis = 123),
        whiteParticipant = null,
        blackParticipant = null,
        sources = listOf(
            GameSourceRecord(
                id = PersistentSourceId("source-1"),
                type = type,
                externalGameId = null,
                externalUrl = null,
                importedAtEpochMillis = null,
                lastSyncedAtEpochMillis = 123,
                sourceAccountId = null,
                metadata = metadata,
            ),
        ),
    )
}

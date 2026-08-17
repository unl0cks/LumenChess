package dev.lumenchess.play

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Variant
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
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlaySnapshotCodecTest {
    private class FakeTime(var now: Long = 5_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
    }

    private object NoopEngine : PlayEngineGateway {
        override fun startSearch(request: EngineSearchRequest) = Unit
        override fun cancelSearch(searchId: EngineSearchId) = Unit
    }

    private object NoopPersistence : PlayPersistenceGateway {
        override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedPlaySetup) = Unit
    }

    @Test
    fun chess960RuntimeRoundTripsThroughVersionedCanonicalMetadata() {
        val setup = PlaySetupResolver.resolve(
            PlaySetupConfig(
                variant = Variant.CHESS960,
                chess960Index = 321,
                engine = PlayEngine.RECKLESS_0_9_0,
                side = PlaySide.BLACK,
                strengthModel = EngineStrengthModel.HUMANIZED,
                strengthTarget = EngineStrengthTarget.Elo(1200),
                timeControl = PlayTimeControl(180_000L, 2_000L),
                strengthSeed = 99L,
            ),
        )
        val coordinator = PlayRuntimeCoordinator.create(
            setup,
            FakeTime(),
            NoopEngine,
            NoopPersistence,
        )
        coordinator.start()
        val snapshot = coordinator.snapshotForRestore()
        val encoded = PlaySnapshotCodec.encode(snapshot, setup)

        val decoded = PlaySnapshotCodec.decode(loadedGame(snapshot, encoded))

        assertEquals(PlayEngine.RECKLESS_0_9_0, decoded.setup.engine)
        assertEquals(Color.BLACK, decoded.setup.humanSide)
        assertEquals(321, decoded.setup.chess960Index)
        assertEquals(EngineStrengthModel.HUMANIZED, decoded.setup.strength.model)
        assertEquals(EngineStrengthTarget.Elo(1200), decoded.setup.strength.target)
        assertEquals(99L, decoded.setup.strength.seed)
        assertEquals(180_000L, decoded.setup.clockConfig.initialMillis)
        assertEquals(2_000L, decoded.setup.clockConfig.incrementMillis)
        assertEquals(snapshot.positionRevision, decoded.snapshot.positionRevision)
        assertEquals(Fen.serialize(snapshot.position), Fen.serialize(decoded.snapshot.position))
        assertEquals(snapshot.gameTree.mainline().map { it.move }, decoded.snapshot.gameTree.mainline().map { it.move })
        assertTrue(decoded.snapshot.paused)
        assertTrue(!decoded.snapshot.clock.running)
    }

    @Test
    fun restoreRejectsMetadataRevisionThatCannotDescribeCanonicalTree() {
        val setup = PlaySetupResolver.resolve(PlaySetupConfig())
        val coordinator = PlayRuntimeCoordinator.create(
            setup,
            FakeTime(),
            NoopEngine,
            NoopPersistence,
        )
        coordinator.start()
        val snapshot = coordinator.snapshotForRestore()
        val corrupted = PlaySnapshotCodec.encode(snapshot, setup).toMutableMap().apply {
            this["lumen.play.m19.positionRevision"] = "2"
        }

        assertFailsWith<PersistenceMappingException> {
            PlaySnapshotCodec.decode(loadedGame(snapshot, corrupted))
        }
    }

    private fun loadedGame(
        snapshot: RuntimeSnapshot,
        metadata: Map<String, String>,
    ): LoadedCanonicalGame = LoadedCanonicalGame(
        id = PersistentGameId("game-1"),
        tree = snapshot.gameTree,
        metadata = GamePersistenceMetadata(createdAtEpochMillis = 123L),
        whiteParticipant = null,
        blackParticipant = null,
        sources = listOf(
            GameSourceRecord(
                id = PersistentSourceId("source-1"),
                type = GameSourceType.LOCAL,
                externalGameId = null,
                externalUrl = null,
                importedAtEpochMillis = null,
                lastSyncedAtEpochMillis = 123L,
                sourceAccountId = null,
                metadata = metadata,
            ),
        ),
    )
}

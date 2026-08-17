package dev.lumenchess.play

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayRuntimeCoordinatorTest {
    private class FakeTime(var now: Long = 1_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
        fun advanceBy(millis: Long) { now += millis }
    }

    private class FakeEngine : PlayEngineGateway {
        val started = mutableListOf<EngineSearchRequest>()
        val cancelled = mutableListOf<EngineSearchId>()
        override fun startSearch(request: EngineSearchRequest) { started += request }
        override fun cancelSearch(searchId: EngineSearchId) { cancelled += searchId }
    }

    private class FakePersistence : PlayPersistenceGateway {
        val snapshots = mutableListOf<Pair<RuntimeSnapshot, ResolvedPlaySetup>>()
        override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedPlaySetup) {
            snapshots += snapshot to setup
        }
    }

    private fun setup(
        side: PlaySide = PlaySide.WHITE,
        engine: PlayEngine = PlayEngine.STOCKFISH_18,
        variant: Variant = Variant.STANDARD,
    ) = PlaySetupResolver.resolve(
        PlaySetupConfig(
            variant = variant,
            chess960Index = if (variant == Variant.CHESS960) 321 else null,
            engine = engine,
            side = side,
            strengthModel = EngineStrengthModel.HYBRID,
            strengthTarget = EngineStrengthTarget.Elo(1400),
            timeControl = PlayTimeControl(60_000L, 1_000L),
            strengthSeed = 77L,
        ),
    ) { Color.WHITE }

    @Test
    fun humanMoveStartsTypedEngineSearchForNewAuthoritativeRevision() {
        val engine = FakeEngine()
        val persistence = FakePersistence()
        val coordinator = PlayRuntimeCoordinator.create(setup(), FakeTime(), engine, persistence)

        coordinator.start()
        coordinator.onEngineHostRecovered()
        assertTrue(engine.started.isEmpty())

        coordinator.humanMove(Move.parseUci("e2e4"))

        val request = engine.started.single()
        assertEquals(PositionRevision(1), request.positionRevision)
        assertEquals(Variant.STANDARD, request.position.variant)
        assertEquals(EngineStrengthModel.HYBRID, request.strength.model)
        assertEquals(EngineStrengthTarget.Elo(1400), request.strength.target)
        assertEquals(77L, request.strength.seed)
        assertEquals(1L, coordinator.state.positionRevision.value)
    }

    @Test
    fun validEngineCompletionReturnsThroughRuntimeAndBecomesAuthoritativeOnce() {
        val engine = FakeEngine()
        val coordinator = PlayRuntimeCoordinator.create(setup(), FakeTime(), engine, FakePersistence())
        coordinator.start()
        coordinator.onEngineHostRecovered()
        coordinator.humanMove(Move.parseUci("e2e4"))
        val search = engine.started.single()

        coordinator.onEngineResult(
            EngineSearchResult(search.searchId, search.positionRevision, "e7e5"),
        )
        coordinator.onEngineResult(
            EngineSearchResult(search.searchId, search.positionRevision, "d7d5"),
        )

        assertEquals(listOf("e2e4", "e7e5"), coordinator.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(2L, coordinator.state.positionRevision.value)
    }

    @Test
    fun hostDeathCancelsAuthorityForOldSearchAndRecoveryStartsFreshCorrelation() {
        val engine = FakeEngine()
        val coordinator = PlayRuntimeCoordinator.create(setup(), FakeTime(), engine, FakePersistence())
        coordinator.start()
        coordinator.onEngineHostRecovered()
        coordinator.humanMove(Move.parseUci("e2e4"))
        val old = engine.started.single()

        coordinator.onEngineHostDied()
        coordinator.onEngineResult(EngineSearchResult(old.searchId, old.positionRevision, "e7e5"))
        assertEquals(listOf("e2e4"), coordinator.state.gameTree.mainline().map { it.move!!.uci })

        coordinator.onEngineHostRecovered()
        val replacement = engine.started.last()
        assertTrue(replacement.searchId != old.searchId)
        assertEquals(old.positionRevision, replacement.positionRevision)
    }

    @Test
    fun repeatedLifecycleSignalsDoNotDuplicateSearchOrRuntimeStart() {
        val engine = FakeEngine()
        val coordinator = PlayRuntimeCoordinator.create(
            setup(side = PlaySide.BLACK),
            FakeTime(),
            engine,
            FakePersistence(),
        )

        coordinator.start()
        coordinator.start()
        coordinator.onEngineHostRecovered()
        coordinator.onEngineHostRecovered()

        assertEquals(1, engine.started.size)
        assertEquals(PositionRevision(0), engine.started.single().positionRevision)
    }

    @Test
    fun backgroundPauseCancelsSearchPersistsAndForegroundStartsFreshSearch() {
        val engine = FakeEngine()
        val persistence = FakePersistence()
        val coordinator = PlayRuntimeCoordinator.create(
            setup(side = PlaySide.BLACK),
            FakeTime(),
            engine,
            persistence,
        )
        coordinator.start()
        coordinator.onEngineHostRecovered()
        val first = engine.started.single()

        coordinator.pause()
        assertTrue(coordinator.state.paused)
        assertTrue(first.searchId in engine.cancelled)
        assertTrue(persistence.snapshots.isNotEmpty())

        coordinator.resume()
        val replacement = engine.started.last()
        assertFalse(coordinator.state.paused)
        assertTrue(replacement.searchId != first.searchId)
    }

    @Test
    fun restoredRuntimeHasNoInFlightSearchAndDoesNotManufactureMove() {
        val time = FakeTime()
        val engine = FakeEngine()
        val original = PlayRuntimeCoordinator.create(setup(), time, engine, FakePersistence())
        original.start()
        original.onEngineHostRecovered()
        original.humanMove(Move.parseUci("e2e4"))
        val snapshot = original.snapshotForRestore()

        val restoredEngine = FakeEngine()
        val restored = PlayRuntimeCoordinator.restore(
            setup(),
            snapshot,
            time,
            restoredEngine,
            FakePersistence(),
        )

        assertEquals(listOf("e2e4"), restored.state.gameTree.mainline().map { it.move!!.uci })
        assertNull(restored.state.pendingEngineSearch)
        assertTrue(restored.state.paused)
        assertFalse(restored.state.engineHostAvailable)

        restored.onEngineHostRecovered()
        assertTrue(restoredEngine.started.isEmpty())
        restored.resume()
        assertEquals(1, restoredEngine.started.size)
    }

    @Test
    fun chess960EngineRequestPreservesVariantAndCorePosition() {
        val engine = FakeEngine()
        val coordinator = PlayRuntimeCoordinator.create(
            setup(side = PlaySide.BLACK, variant = Variant.CHESS960),
            FakeTime(),
            engine,
            FakePersistence(),
        )

        coordinator.start()
        coordinator.onEngineHostRecovered()

        val request = engine.started.single()
        assertEquals(Variant.CHESS960, request.position.variant)
        assertEquals(321, coordinator.setup.chess960Index)
    }

    @Test
    fun persistenceReceivesCanonicalRuntimeTreeRatherThanPartialEngineState() {
        val engine = FakeEngine()
        val persistence = FakePersistence()
        val coordinator = PlayRuntimeCoordinator.create(setup(), FakeTime(), engine, persistence)
        coordinator.start()
        coordinator.onEngineHostRecovered()
        coordinator.humanMove(Move.parseUci("e2e4"))

        val last = persistence.snapshots.last().first
        assertEquals(listOf("e2e4"), last.gameTree.mainline().map { it.move!!.uci })
        assertNotNull(coordinator.state.pendingEngineSearch)
        assertEquals(1L, last.positionRevision.value)
    }
}

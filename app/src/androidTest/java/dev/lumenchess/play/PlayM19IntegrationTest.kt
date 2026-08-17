package dev.lumenchess.play

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.persistence.LumenDatabaseFactory
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.engine.host.transport.EngineHostFailureCode
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.RuntimeTerminal
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayM19IntegrationTest {
    private lateinit var context: Context
    private val closeables = mutableListOf<AutoCloseable>()

    private object NoopPersistence : PlayPersistenceGateway {
        override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedPlaySetup) = Unit
    }

    private class RecordingEngine : PlayEngineGateway {
        val started = mutableListOf<EngineSearchRequest>()
        override fun startSearch(request: EngineSearchRequest) { started += request }
        override fun cancelSearch(searchId: EngineSearchId) = Unit
    }

    private class FakeTime(var now: Long = 10_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        closeables.asReversed().forEach {
            try {
                it.close()
            } catch (_: Throwable) {
                // Cleanup must not hide the test assertion that already ran.
            }
        }
        closeables.clear()
    }

    @Test
    fun standardGameProgressesWithStockfishAndReckless() {
        listOf(PlayEngine.STOCKFISH_18, PlayEngine.RECKLESS_0_9_0).forEach { engine ->
            val result = playOneStandardReply(engine)
            assertEquals(2L, result.positionRevision.value)
            assertEquals(2, result.gameTree.mainline().size)
            assertEquals("e2e4", result.gameTree.mainline().first().move!!.uci)
            assertEquals(Variant.STANDARD, result.position.variant)
        }
    }

    @Test
    fun chess960CanStartWithEngineToMoveAndRemainChess960AfterReply() {
        val setup = resolvedSetup(
            engine = PlayEngine.STOCKFISH_18,
            side = PlaySide.BLACK,
            variant = Variant.CHESS960,
        )
        val gateway = AndroidPlayEngineGateway(context, setup.engine)
        closeables += gateway
        val recovered = CountDownLatch(1)
        val moved = CountDownLatch(1)
        val failure = AtomicReference<EngineHostFailure?>()
        lateinit var coordinator: PlayRuntimeCoordinator

        gateway.setListener(runtimeListener(
            coordinator = { coordinator },
            onRecovered = { recovered.countDown() },
            onAuthoritativeMove = { if (coordinator.state.positionRevision.value >= 1L) moved.countDown() },
            onFailure = { failure.set(it) },
        ))
        coordinator = PlayRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { SystemClock.elapsedRealtime() },
            gateway,
            NoopPersistence,
        )
        coordinator.start()
        gateway.connect()

        assertTrue("Stockfish host did not recover", recovered.await(12, TimeUnit.SECONDS))
        assertTrue("Stockfish did not make a Chess960 move", moved.await(15, TimeUnit.SECONDS))
        assertNonFatalTransport(failure.get())
        assertEquals(1L, coordinator.state.positionRevision.value)
        assertEquals(Variant.CHESS960, coordinator.state.position.variant)
        assertEquals(1, coordinator.state.gameTree.mainline().size)
    }

    @Test
    fun engineRestartInvalidatesOldSearchAndAppliesExactlyOneFreshReply() {
        val setup = resolvedSetup(
            engine = PlayEngine.STOCKFISH_18,
            side = PlaySide.BLACK,
            variant = Variant.STANDARD,
        )
        val gateway = AndroidPlayEngineGateway(context, setup.engine)
        closeables += gateway
        val firstRecovery = CountDownLatch(1)
        val secondRecovery = CountDownLatch(1)
        val moved = CountDownLatch(1)
        val recoveryCount = AtomicInteger(0)
        val failure = AtomicReference<EngineHostFailure?>()
        lateinit var coordinator: PlayRuntimeCoordinator

        gateway.setListener(runtimeListener(
            coordinator = { coordinator },
            onRecovered = {
                if (recoveryCount.incrementAndGet() == 1) firstRecovery.countDown() else secondRecovery.countDown()
            },
            onAuthoritativeMove = { if (coordinator.state.positionRevision.value >= 1L) moved.countDown() },
            onFailure = { failure.set(it) },
        ))
        coordinator = PlayRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { SystemClock.elapsedRealtime() },
            gateway,
            NoopPersistence,
        )
        coordinator.start()
        gateway.connect()
        assertTrue("Initial Stockfish host did not recover", firstRecovery.await(12, TimeUnit.SECONDS))
        val oldSearch = requireNotNull(coordinator.state.pendingEngineSearch)

        gateway.restartHostForDiagnostics()

        assertTrue("Replacement Stockfish host did not recover", secondRecovery.await(12, TimeUnit.SECONDS))
        val replacement = requireNotNull(coordinator.state.pendingEngineSearch)
        assertNotEquals(oldSearch.searchId, replacement.searchId)
        assertEquals(oldSearch.positionRevision, replacement.positionRevision)
        assertTrue("Replacement Stockfish search did not finish", moved.await(15, TimeUnit.SECONDS))
        assertEquals(1L, coordinator.state.positionRevision.value)
        assertEquals(1, coordinator.state.gameTree.mainline().size)
        assertNonFatalTransport(failure.get())
    }

    @Test
    fun staleResultAndPremoveStillFlowThroughRuntimeOwner() {
        val engine = RecordingEngine()
        val time = FakeTime()
        val coordinator = PlayRuntimeCoordinator.create(
            resolvedSetup(PlayEngine.STOCKFISH_18, PlaySide.WHITE, Variant.STANDARD),
            time,
            engine,
            NoopPersistence,
        )
        coordinator.start()
        coordinator.onEngineHostRecovered()
        coordinator.humanMove(Move.parseUci("e2e4"))
        val search = engine.started.single()

        coordinator.queuePremove(Move.parseUci("g1f3"))
        coordinator.onEngineResult(
            EngineSearchResult(search.searchId, PositionRevision(0), "e7e5"),
        )
        assertEquals(1L, coordinator.state.positionRevision.value)
        assertNotNull(coordinator.state.queuedPremove)

        coordinator.onEngineResult(
            EngineSearchResult(search.searchId, search.positionRevision, "e7e5"),
        )

        assertEquals(listOf("e2e4", "e7e5", "g1f3"), coordinator.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(3L, coordinator.state.positionRevision.value)
        assertNull(coordinator.state.queuedPremove)
        assertEquals(59_900L, coordinator.state.clock.whiteRemainingMillis)
    }

    @Test
    fun canonicalLiveGameReloadKeepsUuidTreeAndTerminalWithoutManufacturedMove() {
        context.deleteDatabase(LumenDatabaseFactory.DEFAULT_NAME)
        context.getSharedPreferences("lumen-play", Context.MODE_PRIVATE).edit().clear().commit()
        val database = LumenDatabaseFactory.open(context)
        val persistence = AndroidPlayPersistenceGateway(context, database = database)
        closeables += persistence
        val setup = resolvedSetup(PlayEngine.STOCKFISH_18, PlaySide.WHITE, Variant.STANDARD)
        val coordinator = PlayRuntimeCoordinator.create(
            setup,
            FakeTime(),
            RecordingEngine(),
            persistence,
        )
        coordinator.start()
        coordinator.humanMove(Move.parseUci("e2e4"))
        coordinator.resign()

        val flushed = CountDownLatch(1)
        persistence.flushForTest { flushed.countDown() }
        assertTrue("Live persistence did not flush", flushed.await(10, TimeUnit.SECONDS))
        val stableId = assertNotNull(persistence.gameId)

        val restoredLatch = CountDownLatch(1)
        val restored = AtomicReference<RestoredPlayGame?>()
        val persistenceFailure = AtomicReference<Throwable?>()
        persistence.setListener(object : AndroidPlayPersistenceGateway.Listener {
            override fun onRestoreLoaded(game: RestoredPlayGame?) {
                restored.set(game)
                restoredLatch.countDown()
            }

            override fun onPersistenceFailure(error: Throwable) {
                persistenceFailure.set(error)
                restoredLatch.countDown()
            }
        })
        persistence.loadLastRestorableGame()

        assertTrue("Canonical live game did not reload", restoredLatch.await(10, TimeUnit.SECONDS))
        persistenceFailure.get()?.let { throw AssertionError("Restore failed", it) }
        val game = requireNotNull(restored.get())
        assertEquals(stableId, game.gameId)
        assertEquals(listOf("e2e4"), game.snapshot.gameTree.mainline().map { it.move!!.uci })
        assertEquals(1L, game.snapshot.positionRevision.value)
        assertEquals(RuntimeTerminal.Resignation(Color.WHITE), game.snapshot.terminal)
        assertTrue(game.snapshot.paused)
        assertNull(game.snapshot.gameTree.mainline().drop(1).firstOrNull())
    }

    private fun playOneStandardReply(engine: PlayEngine): dev.lumenchess.runtime.RuntimeState {
        val setup = resolvedSetup(engine, PlaySide.WHITE, Variant.STANDARD)
        val gateway = AndroidPlayEngineGateway(context, engine)
        closeables += gateway
        val recovered = CountDownLatch(1)
        val moved = CountDownLatch(1)
        val failure = AtomicReference<EngineHostFailure?>()
        lateinit var coordinator: PlayRuntimeCoordinator
        gateway.setListener(runtimeListener(
            coordinator = { coordinator },
            onRecovered = { recovered.countDown() },
            onAuthoritativeMove = { if (coordinator.state.positionRevision.value >= 2L) moved.countDown() },
            onFailure = { failure.set(it) },
        ))
        coordinator = PlayRuntimeCoordinator.create(
            setup,
            MonotonicTimeSource { SystemClock.elapsedRealtime() },
            gateway,
            NoopPersistence,
        )
        coordinator.start()
        gateway.connect()
        assertTrue("${engine.displayName} host did not recover", recovered.await(12, TimeUnit.SECONDS))
        coordinator.humanMove(Move.parseUci("e2e4"))
        assertTrue("${engine.displayName} did not reply", moved.await(15, TimeUnit.SECONDS))
        assertNonFatalTransport(failure.get())
        gateway.setListener(null)
        gateway.close()
        closeables.remove(gateway)
        return coordinator.state
    }

    private fun runtimeListener(
        coordinator: () -> PlayRuntimeCoordinator,
        onRecovered: () -> Unit,
        onAuthoritativeMove: () -> Unit,
        onFailure: (EngineHostFailure) -> Unit,
    ): AndroidPlayEngineGateway.Listener = object : AndroidPlayEngineGateway.Listener {
        override fun onEngineHostRecovered() {
            coordinator().onEngineHostRecovered()
            onRecovered()
        }

        override fun onEngineHostDied() {
            coordinator().onEngineHostDied()
        }

        override fun onEngineResult(result: EngineSearchResult) {
            coordinator().onEngineResult(result)
            onAuthoritativeMove()
        }

        override fun onEngineFailure(failure: EngineHostFailure) {
            if (failure.code != EngineHostFailureCode.STALE_TRANSPORT) onFailure(failure)
        }
    }

    private fun resolvedSetup(
        engine: PlayEngine,
        side: PlaySide,
        variant: Variant,
    ): ResolvedPlaySetup = PlaySetupResolver.resolve(
        PlaySetupConfig(
            variant = variant,
            chess960Index = if (variant == Variant.CHESS960) 321 else null,
            engine = engine,
            side = side,
            strengthModel = EngineStrengthModel.HYBRID,
            strengthTarget = EngineStrengthTarget.Elo(1400),
            timeControl = PlayTimeControl(60_000L, 1_000L),
            strengthSeed = 1234L,
        ),
    )

    private fun assertNonFatalTransport(failure: EngineHostFailure?) {
        if (failure != null) throw AssertionError("Unexpected engine-host failure: $failure")
    }
}

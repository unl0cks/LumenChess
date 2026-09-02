package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.ClockSide
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualControlRuntimeTest {
    private class FakeTime(var now: Long = 1_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
        fun advanceBy(millis: Long) { now += millis }
    }

    private fun move(uci: String): Move = Move.parseUci(uci)

    private fun runtime(
        time: FakeTime,
        position: Position = Position.initial(),
        controllers: RuntimeControllers,
        manualControl: RuntimeManualControl,
        incrementMillis: Long = 2_000L,
    ): GameRuntime = GameRuntime.create(
        initialPosition = position,
        clockConfig = ClockConfig(60_000L, incrementMillis),
        timeSource = time,
        controllers = controllers,
        manualControl = manualControl,
    )

    private fun start(runtime: GameRuntime) =
        runtime.dispatch(RuntimeEvent.Start(RuntimeEventId(1)))

    private fun lease(moves: Int? = null) = ManualControlLease(moves)

    @Test
    fun lockedBothSidesAcceptMovesWithoutTimeOrIncrementAndCountPerSide() {
        val time = FakeTime()
        val control = RuntimeManualControl(white = lease(2), black = lease(2))
        val runtime = runtime(
            time,
            controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.HUMAN),
            manualControl = control,
        )

        val started = start(runtime)
        time.advanceBy(5_000L)
        val white = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))
        time.advanceBy(5_000L)
        val black = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(3), move("e7e5")))

        assertFalse(started.state.clock.running)
        assertEquals(60_000L, white.state.clock.whiteRemainingMillis)
        assertEquals(60_000L, white.state.clock.blackRemainingMillis)
        assertEquals(1, white.state.manualControl.white?.remainingMoves)
        assertEquals(1, black.state.manualControl.black?.remainingMoves)
        assertEquals(ClockSide.WHITE, black.state.clock.activeSide)
    }

    @Test
    fun finiteLeaseHandsSideBackToEngineAtomicallyAndStartsFreshSearch() {
        val time = FakeTime()
        val runtime = runtime(
            time,
            controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.ENGINE),
            manualControl = RuntimeManualControl(white = lease(1)),
            incrementMillis = 0L,
        )

        start(runtime)
        val result = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))

        assertNull(result.state.manualControl.white)
        assertEquals(RuntimeController.ENGINE, result.state.controllers.white)
        assertTrue(result.state.clock.running)
        assertEquals(ClockSide.BLACK, result.state.clock.activeSide)
        assertTrue(result.effects.any { it is RuntimeEffect.StartEngineSearch })
    }

    @Test
    fun countTimeManualControlUsesNormalClockAndIncrement() {
        val time = FakeTime()
        val runtime = runtime(
            time,
            controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.ENGINE),
            manualControl = RuntimeManualControl(
                white = lease(1),
                clockPolicy = ManualClockPolicy.COUNT_TIME,
            ),
        )

        start(runtime)
        time.advanceBy(5_000L)
        val result = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))

        assertEquals(57_000L, result.state.clock.whiteRemainingMillis)
        assertEquals(ClockSide.BLACK, result.state.clock.activeSide)
        assertTrue(result.state.clock.running)
        assertTrue(result.effects.any { it is RuntimeEffect.StartEngineSearch })
    }

    @Test
    fun takeoverCannotRescueAClockThatAlreadyTimedOut() {
        val time = FakeTime()
        val runtime = runtime(
            time,
            controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.ENGINE),
            manualControl = RuntimeManualControl(),
            incrementMillis = 0L,
        )

        start(runtime)
        time.advanceBy(60_000L)
        val result = runtime.dispatch(
            RuntimeEvent.SetManualControl(
                RuntimeEventId(2),
                RuntimeManualControl(white = lease()),
            ),
        )

        assertEquals(RuntimeTerminal.Timeout(Color.WHITE), result.state.terminal)
        assertEquals(RuntimeDisposition.TERMINAL, result.disposition)
        assertEquals(RuntimeManualControl(), result.state.manualControl)
    }

    @Test
    fun takeoverCancelsCurrentSearchAndReturnCreatesNewSearchAtSameRevision() {
        val time = FakeTime()
        val runtime = runtime(
            time,
            controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.ENGINE),
            manualControl = RuntimeManualControl(),
            incrementMillis = 0L,
        )
        start(runtime)
        val afterMove = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))
        val oldSearch = afterMove.effects.filterIsInstance<RuntimeEffect.StartEngineSearch>().single()

        val taken = runtime.dispatch(
            RuntimeEvent.SetManualControl(
                RuntimeEventId(3),
                RuntimeManualControl(black = lease()),
            ),
        )
        val returned = runtime.dispatch(
            RuntimeEvent.SetManualControl(RuntimeEventId(4), RuntimeManualControl()),
        )
        val newSearch = returned.effects.filterIsInstance<RuntimeEffect.StartEngineSearch>().single()
        val stale = runtime.dispatch(
            RuntimeEvent.EngineCompleted(
                RuntimeEventId(5),
                EngineSearchResult(oldSearch.searchId, oldSearch.positionRevision, "e7e5"),
            ),
        )

        assertTrue(taken.effects.any { it == RuntimeEffect.CancelEngineSearch(oldSearch.searchId) })
        assertEquals(RuntimeController.HUMAN, taken.state.controllers.black)
        assertEquals(RuntimeController.ENGINE, returned.state.controllers.black)
        assertNotEquals(oldSearch.searchId, newSearch.searchId)
        assertEquals(oldSearch.positionRevision, newSearch.positionRevision)
        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, stale.disposition)
        assertEquals(afterMove.state.positionRevision, stale.state.positionRevision)
    }

    @Test
    fun bothSideControlReplacementIsAtomicAndClearsPremove() {
        val time = FakeTime()
        val runtime = runtime(
            time,
            controllers = RuntimeControllers(RuntimeController.ENGINE, RuntimeController.HUMAN),
            manualControl = RuntimeManualControl(),
        )
        start(runtime)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))

        val result = runtime.dispatch(
            RuntimeEvent.SetManualControl(
                RuntimeEventId(3),
                RuntimeManualControl(white = lease(), black = lease()),
            ),
        )

        assertEquals(RuntimeController.HUMAN, result.state.controllers.white)
        assertEquals(RuntimeController.HUMAN, result.state.controllers.black)
        assertNull(result.state.queuedPremove)
        assertEquals(RuntimeManualControl(white = lease(), black = lease()), result.state.manualControl)
    }

    @Test
    fun restoredManualLeaseRetainsStateButStartsPausedWithNoSearch() {
        val time = FakeTime()
        val control = RuntimeManualControl(white = lease(3), clockPolicy = ManualClockPolicy.LOCKED)
        val runtime = runtime(
            time,
            controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.ENGINE),
            manualControl = control,
        )
        start(runtime)
        val snapshot = runtime.snapshotForRestore()
        val restored = GameRuntime.restore(snapshot, time)

        assertEquals(control, restored.state.manualControl)
        assertEquals(RuntimeController.HUMAN, restored.state.controllers.white)
        assertEquals(RuntimeController.ENGINE, restored.state.controllers.black)
        assertTrue(restored.state.paused)
        assertFalse(restored.state.clock.running)
        assertNull(restored.state.pendingEngineSearch)
    }

    @Test
    fun blackToMoveManualOpeningDoesNotChargeWrongClock() {
        val time = FakeTime()
        val position = Fen.parse("7k/8/8/8/8/8/8/K7 b - - 0 1")
        val control = RuntimeManualControl(black = lease(1))
        val runtime = runtime(
            time,
            position = position,
            controllers = RuntimeControllers(RuntimeController.ENGINE, RuntimeController.HUMAN),
            manualControl = control,
        )
        val started = start(runtime)
        time.advanceBy(10_000L)

        assertEquals(ClockSide.BLACK, started.state.clock.activeSide)
        assertFalse(started.state.clock.running)
        assertEquals(60_000L, runtime.state.clock.blackRemainingMillis)
    }
}

package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.runtime.clock.*
import kotlin.test.*

class UntimedRuntimeTest {
    @Test fun untimedClockDoesNotTimeoutIncrementOrChargeButStillSwitchesSide() {
        var now = 0L
        val clock = DeterministicGameClock(MonotonicTimeSource { now })
        val initial = clock.create(ClockConfig(0, 0, enabled = false))
        val started = clock.start(initial).state
        now = 1_000_000
        assertFalse(started.running)
        assertNull(clock.settle(started).timeoutOccurred)
        val moved = clock.switchTurn(started).state
        assertEquals(ClockSide.BLACK, moved.activeSide)
        assertEquals(0L, moved.whiteRemainingMillis)
        assertNull(moved.timedOutSide)
        assertEquals(moved, clock.charge(moved, ClockSide.BLACK, 100).state)
        assertEquals(moved, clock.resume(clock.pause(moved).state).state)
    }

    @Test fun untimedManualLeaseExpiresAndEngineHandoffRestoresWithoutStartingClock() {
        var now = 0L
        val time = MonotonicTimeSource { now }
        val runtime = GameRuntime.create(
            Position.initial(), ClockConfig(0, 0, enabled = false), time,
            RuntimeControllers(RuntimeController.HUMAN, RuntimeController.ENGINE),
            manualControl = RuntimeManualControl(white = ManualControlLease(1)),
        )
        runtime.dispatch(RuntimeEvent.Start(RuntimeEventId(1)))
        now += 60_000
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), San.parse(runtime.state.position, "e4")))
        assertNull(runtime.state.terminal)
        assertEquals(RuntimeController.ENGINE, runtime.state.controllers.white)
        assertFalse(runtime.state.clock.running)
        val request = assertNotNull(runtime.state.pendingEngineSearch)
        runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(3),
            EngineSearchResult(request.searchId, request.positionRevision, "e7e5")))
        assertEquals(2L, runtime.state.positionRevision.value)
        val restored = GameRuntime.restore(runtime.snapshotForRestore(), time)
        restored.dispatch(RuntimeEvent.Resume(RuntimeEventId(4)))
        restored.dispatch(RuntimeEvent.EngineHostRecovered(RuntimeEventId(5)))
        now += 600_000
        restored.dispatch(RuntimeEvent.ClockCheck(RuntimeEventId(6)))
        assertFalse(restored.state.clock.enabled)
        assertFalse(restored.state.clock.running)
        assertNull(restored.state.terminal)
        assertNotNull(restored.state.pendingEngineSearch)
    }
}

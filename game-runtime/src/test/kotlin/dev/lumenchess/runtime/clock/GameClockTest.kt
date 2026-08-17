package dev.lumenchess.runtime.clock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameClockTest {
    private class FakeTimeSource(initialMillis: Long = 0L) : MonotonicTimeSource {
        var now: Long = initialMillis
        override fun nowMillis(): Long = now
        fun advanceBy(millis: Long) {
            require(millis >= 0)
            now += millis
        }
    }

    private fun clock(
        initialMillis: Long = 60_000L,
        incrementMillis: Long = 0L,
        activeSide: ClockSide = ClockSide.WHITE,
        startAt: Long = 1_000L,
    ): Triple<DeterministicGameClock, FakeTimeSource, ClockState> {
        val time = FakeTimeSource(startAt)
        val clock = DeterministicGameClock(time)
        val state = clock.create(
            ClockConfig(initialMillis = initialMillis, incrementMillis = incrementMillis),
            activeSide = activeSide,
        )
        return Triple(clock, time, state)
    }

    @Test
    fun initialStateIsPausedAndPreservesBothSidesAndActiveSide() {
        val (clock, _, state) = clock(initialMillis = 15_000L, activeSide = ClockSide.BLACK)

        val reading = clock.read(state)

        assertEquals(15_000L, reading.whiteRemainingMillis)
        assertEquals(15_000L, reading.blackRemainingMillis)
        assertEquals(ClockSide.BLACK, reading.activeSide)
        assertFalse(reading.running)
        assertNull(reading.timedOutSide)
    }

    @Test
    fun onlyActiveSideLosesElapsedMonotonicTime() {
        val (clock, time, initial) = clock()
        val started = clock.start(initial).state
        time.advanceBy(1_750L)

        val reading = clock.read(started)

        assertEquals(58_250L, reading.whiteRemainingMillis)
        assertEquals(60_000L, reading.blackRemainingMillis)
    }

    @Test
    fun blackCanBeInitialActiveSide() {
        val (clock, time, initial) = clock(activeSide = ClockSide.BLACK)
        val started = clock.start(initial).state
        time.advanceBy(400L)

        val reading = clock.read(started)

        assertEquals(60_000L, reading.whiteRemainingMillis)
        assertEquals(59_600L, reading.blackRemainingMillis)
    }

    @Test
    fun switchingTurnSettlesElapsedTimeAppliesIncrementAndStartsOpponentAtSameSample() {
        val (clock, time, initial) = clock(initialMillis = 10_000L, incrementMillis = 2_000L)
        var state = clock.start(initial).state
        time.advanceBy(3_000L)

        state = clock.switchTurn(state).state

        assertEquals(9_000L, state.whiteRemainingMillis)
        assertEquals(10_000L, state.blackRemainingMillis)
        assertEquals(ClockSide.BLACK, state.activeSide)
        time.advanceBy(500L)
        assertEquals(9_500L, clock.read(state).blackRemainingMillis)
    }

    @Test
    fun zeroIncrementAddsNothing() {
        val (clock, time, initial) = clock(initialMillis = 1_000L, incrementMillis = 0L)
        var state = clock.start(initial).state
        time.advanceBy(250L)

        state = clock.switchTurn(state).state

        assertEquals(750L, state.whiteRemainingMillis)
    }

    @Test
    fun zeroInitialTimeTimesOutOnStartAndIncrementCannotResurrectIt() {
        val (clock, _, initial) = clock(initialMillis = 0L, incrementMillis = 1_000L)

        val transition = clock.start(initial)
        val afterSwitchAttempt = clock.switchTurn(transition.state)

        assertEquals(ClockSide.WHITE, transition.timeoutOccurred)
        assertEquals(ClockSide.WHITE, transition.state.timedOutSide)
        assertEquals(0L, transition.state.whiteRemainingMillis)
        assertEquals(ClockSide.WHITE, afterSwitchAttempt.state.activeSide)
        assertNull(afterSwitchAttempt.timeoutOccurred)
    }

    @Test
    fun pauseSettlesElapsedTimeAndResumeDoesNotChargePausedDuration() {
        val (clock, time, initial) = clock(initialMillis = 5_000L)
        var state = clock.start(initial).state
        time.advanceBy(700L)

        state = clock.pause(state).state
        assertEquals(4_300L, state.whiteRemainingMillis)
        assertFalse(state.running)

        time.advanceBy(10_000L)
        assertEquals(4_300L, clock.read(state).whiteRemainingMillis)

        state = clock.resume(state).state
        assertTrue(state.running)
        time.advanceBy(300L)
        assertEquals(4_000L, clock.read(state).whiteRemainingMillis)
    }

    @Test
    fun repeatedReadsDoNotMutateStoredState() {
        val (clock, time, initial) = clock(initialMillis = 2_000L)
        val state = clock.start(initial).state
        time.advanceBy(250L)

        val first = clock.read(state)
        val second = clock.read(state)

        assertEquals(first, second)
        assertEquals(2_000L, state.whiteRemainingMillis)
        assertTrue(state.running)
    }

    @Test
    fun delayedEventDeliveryChargesAllElapsedTimeBeforeTurnSwitch() {
        val (clock, time, initial) = clock(initialMillis = 10_000L, incrementMillis = 1_000L)
        var state = clock.start(initial).state
        time.advanceBy(8_500L)

        state = clock.switchTurn(state).state

        assertEquals(2_500L, state.whiteRemainingMillis)
        assertEquals(ClockSide.BLACK, state.activeSide)
    }

    @Test
    fun timeoutClampsAtZeroAndIsReportedExactlyOnce() {
        val (clock, time, initial) = clock(initialMillis = 1_000L)
        var state = clock.start(initial).state
        time.advanceBy(1_500L)

        val first = clock.settle(state)
        state = first.state
        val second = clock.settle(state)

        assertEquals(ClockSide.WHITE, first.timeoutOccurred)
        assertEquals(0L, state.whiteRemainingMillis)
        assertFalse(state.running)
        assertEquals(ClockSide.WHITE, state.timedOutSide)
        assertNull(second.timeoutOccurred)
        assertEquals(state, second.state)
    }

    @Test
    fun timeoutWinsOverLateTurnSwitchAndDoesNotApplyIncrement() {
        val (clock, time, initial) = clock(initialMillis = 500L, incrementMillis = 5_000L)
        val started = clock.start(initial).state
        time.advanceBy(501L)

        val transition = clock.switchTurn(started)

        assertEquals(ClockSide.WHITE, transition.timeoutOccurred)
        assertEquals(0L, transition.state.whiteRemainingMillis)
        assertEquals(ClockSide.WHITE, transition.state.activeSide)
        assertEquals(ClockSide.WHITE, transition.state.timedOutSide)
    }

    @Test
    fun explicitClockChargeAppliesExactlyOnceAndCanCauseTimeout() {
        val (clock, _, initial) = clock(initialMillis = 100L)
        var state = clock.start(initial).state

        val charged = clock.charge(state, ClockSide.WHITE, 100L)
        state = charged.state
        val repeatedTerminalCharge = clock.charge(state, ClockSide.WHITE, 100L)

        assertEquals(0L, state.whiteRemainingMillis)
        assertEquals(ClockSide.WHITE, charged.timeoutOccurred)
        assertNull(repeatedTerminalCharge.timeoutOccurred)
        assertEquals(state, repeatedTerminalCharge.state)
    }

    @Test
    fun chargeNeverProducesNegativeTime() {
        val (clock, _, initial) = clock(initialMillis = 50L)
        val started = clock.start(initial).state

        val charged = clock.charge(started, ClockSide.WHITE, 5_000L)

        assertEquals(0L, charged.state.whiteRemainingMillis)
    }

    @Test
    fun fakeTimeMakesSameEventSequenceBitForBitDeterministic() {
        fun runScenario(): ClockState {
            val (clock, time, initial) = clock(initialMillis = 5_000L, incrementMillis = 250L)
            var state = clock.start(initial).state
            time.advanceBy(400L)
            state = clock.switchTurn(state).state
            time.advanceBy(600L)
            state = clock.pause(state).state
            time.advanceBy(9_999L)
            state = clock.resume(state).state
            time.advanceBy(200L)
            return clock.settle(state).state
        }

        assertEquals(runScenario(), runScenario())
    }

    @Test
    fun terminalClockOperationsAreIdempotent() {
        val (clock, time, initial) = clock(initialMillis = 10L)
        var state = clock.start(initial).state
        time.advanceBy(10L)
        state = clock.settle(state).state

        assertEquals(state, clock.pause(state).state)
        assertEquals(state, clock.resume(state).state)
        assertEquals(state, clock.start(state).state)
        assertEquals(state, clock.switchTurn(state).state)
        assertEquals(state, clock.charge(state, ClockSide.WHITE, 1L).state)
    }
}

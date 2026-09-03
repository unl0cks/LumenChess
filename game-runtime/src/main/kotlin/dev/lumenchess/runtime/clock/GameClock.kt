package dev.lumenchess.runtime.clock

import kotlin.math.max

data class ClockConfig(
    val initialMillis: Long,
    val incrementMillis: Long,
    val enabled: Boolean = true,
) {
    init {
        require(initialMillis >= 0L) { "Initial clock time cannot be negative" }
        require(incrementMillis >= 0L) { "Clock increment cannot be negative" }
    }
}

enum class ClockSide {
    WHITE,
    BLACK;

    val opposite: ClockSide
        get() = if (this == WHITE) BLACK else WHITE
}

data class ClockState(
    val whiteRemainingMillis: Long,
    val blackRemainingMillis: Long,
    val activeSide: ClockSide,
    val incrementMillis: Long,
    val running: Boolean,
    val lastSampleMillis: Long?,
    val timedOutSide: ClockSide?,
    val enabled: Boolean = true,
) {
    init {
        require(whiteRemainingMillis >= 0L) { "White clock cannot be negative" }
        require(blackRemainingMillis >= 0L) { "Black clock cannot be negative" }
        require(incrementMillis >= 0L) { "Clock increment cannot be negative" }
        require(running == (lastSampleMillis != null)) {
            "A running clock must have exactly one last monotonic sample"
        }
        require(timedOutSide == null || !running) { "A timed-out clock cannot keep running" }
        require(enabled || (!running && timedOutSide == null)) { "An untimed game cannot run or time out a clock" }
    }

    fun remaining(side: ClockSide): Long = when (side) {
        ClockSide.WHITE -> whiteRemainingMillis
        ClockSide.BLACK -> blackRemainingMillis
    }
}

data class ClockReading(
    val whiteRemainingMillis: Long,
    val blackRemainingMillis: Long,
    val activeSide: ClockSide,
    val running: Boolean,
    val timedOutSide: ClockSide?,
)

data class ClockTransition(
    val state: ClockState,
    val timeoutOccurred: ClockSide? = null,
)

/**
 * Immutable deterministic chess-clock transitions. Every mutating operation samples monotonic time
 * at most once and returns a new state. [read] projects a view without mutating the supplied state.
 */
class DeterministicGameClock(
    private val timeSource: MonotonicTimeSource,
) {
    fun create(
        config: ClockConfig,
        activeSide: ClockSide = ClockSide.WHITE,
    ): ClockState = ClockState(
        whiteRemainingMillis = config.initialMillis,
        blackRemainingMillis = config.initialMillis,
        activeSide = activeSide,
        incrementMillis = config.incrementMillis,
        running = false,
        lastSampleMillis = null,
        timedOutSide = null,
        enabled = config.enabled,
    )

    fun start(state: ClockState): ClockTransition {
        if (!state.enabled) return ClockTransition(state)
        if (state.timedOutSide != null || state.running) return ClockTransition(state)
        if (state.remaining(state.activeSide) == 0L) return timeout(state, state.activeSide)
        return ClockTransition(
            state.copy(
                running = true,
                lastSampleMillis = timeSource.nowMillis(),
            ),
        )
    }

    fun read(state: ClockState): ClockReading {
        val projected = if (state.running && state.timedOutSide == null) {
            settleAt(state, timeSource.nowMillis()).state
        } else {
            state
        }
        return projected.toReading()
    }

    fun settle(state: ClockState): ClockTransition {
        if (!state.running || state.timedOutSide != null) return ClockTransition(state)
        return settleAt(state, timeSource.nowMillis())
    }

    fun switchTurn(state: ClockState): ClockTransition {
        if (!state.enabled) return ClockTransition(state.copy(activeSide = state.activeSide.opposite))
        if (state.timedOutSide != null) return ClockTransition(state)
        val now = if (state.running) timeSource.nowMillis() else null
        val settled = if (now != null) settleAt(state, now) else ClockTransition(state)
        if (settled.timeoutOccurred != null || settled.state.timedOutSide != null) return settled

        val current = settled.state
        if (current.remaining(current.activeSide) == 0L) return timeout(current, current.activeSide)
        val incremented = current.withRemaining(
            current.activeSide,
            saturatingAdd(current.remaining(current.activeSide), current.incrementMillis),
        )
        return ClockTransition(
            incremented.copy(
                activeSide = current.activeSide.opposite,
                lastSampleMillis = if (incremented.running) now else null,
            ),
        )
    }

    fun pause(state: ClockState): ClockTransition {
        if (state.timedOutSide != null || !state.running) return ClockTransition(state)
        val settled = settleAt(state, timeSource.nowMillis())
        if (settled.timeoutOccurred != null) return settled
        return ClockTransition(
            settled.state.copy(
                running = false,
                lastSampleMillis = null,
            ),
        )
    }

    fun resume(state: ClockState): ClockTransition = start(state)

    fun charge(
        state: ClockState,
        side: ClockSide,
        millis: Long,
    ): ClockTransition {
        require(millis >= 0L) { "Clock charge cannot be negative" }
        if (!state.enabled) return ClockTransition(state)
        if (state.timedOutSide != null) return ClockTransition(state)

        val now = if (state.running) timeSource.nowMillis() else null
        val settled = if (now != null) settleAt(state, now) else ClockTransition(state)
        if (settled.timeoutOccurred != null || settled.state.timedOutSide != null) return settled
        if (millis == 0L) return settled

        val current = settled.state
        val remaining = max(0L, current.remaining(side) - millis.coerceAtMost(current.remaining(side)))
        val charged = current.withRemaining(side, remaining)
        if (remaining == 0L) return timeout(charged, side)
        return ClockTransition(
            charged.copy(lastSampleMillis = if (charged.running) now else null),
        )
    }

    private fun settleAt(state: ClockState, nowMillis: Long): ClockTransition {
        if (!state.running || state.timedOutSide != null) return ClockTransition(state)
        val last = requireNotNull(state.lastSampleMillis)
        val elapsed = max(0L, nowMillis - last)
        val side = state.activeSide
        val remainingBefore = state.remaining(side)
        if (elapsed >= remainingBefore) {
            return timeout(state.withRemaining(side, 0L), side)
        }
        return ClockTransition(
            state.withRemaining(side, remainingBefore - elapsed).copy(lastSampleMillis = nowMillis),
        )
    }

    private fun timeout(state: ClockState, side: ClockSide): ClockTransition {
        if (state.timedOutSide != null) return ClockTransition(state)
        return ClockTransition(
            state = state.withRemaining(side, 0L).copy(
                running = false,
                lastSampleMillis = null,
                timedOutSide = side,
            ),
            timeoutOccurred = side,
        )
    }

    private fun ClockState.withRemaining(side: ClockSide, millis: Long): ClockState = when (side) {
        ClockSide.WHITE -> copy(whiteRemainingMillis = millis)
        ClockSide.BLACK -> copy(blackRemainingMillis = millis)
    }

    private fun ClockState.toReading(): ClockReading = ClockReading(
        whiteRemainingMillis = whiteRemainingMillis,
        blackRemainingMillis = blackRemainingMillis,
        activeSide = activeSide,
        running = running,
        timedOutSide = timedOutSide,
    )

    private fun saturatingAdd(a: Long, b: Long): Long =
        if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
}

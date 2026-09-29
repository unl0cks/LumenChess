package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.clock.ClockSide
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How long one engine move should take, from the engine's own clock.
 *
 * [searchMillis] is the real wall-time budget handed to the engine (`go movetime`). Depth-limited
 * strength models finish far sooner than that, so [minimumTotalMillis] is the earliest moment (from
 * the start of the search) at which the finished move may be handed back to the runtime. The runtime
 * clock keeps running for the engine side for that whole interval; nothing here touches the clock.
 */
data class EngineThinkPlan(
    val searchMillis: Long,
    val minimumTotalMillis: Long,
    /** True when a weak, low-on-time opponent froze on this move (it may flag). */
    val stalled: Boolean = false,
) {
    init {
        require(searchMillis > 0L) { "Search budget must be positive" }
        require(minimumTotalMillis >= 0L) { "Minimum think time cannot be negative" }
    }

    fun remainingPresentationMillis(elapsedMillis: Long): Long =
        (minimumTotalMillis - elapsedMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
}

/**
 * Deterministic, position- and clock-aware think-time model for a clocked engine opponent.
 *
 * The model is deliberately not one delay: it depends on the time control and remaining clock, the
 * target strength (weaker opponents manage the clock worse and vary more), the phase of the game,
 * how forcing the position is (legal move count, check, recapture) and a seeded per-move variance.
 * Only Elo-limited opponents ever "freeze" in time trouble, so they can lose on time; a full
 * strength engine budgets safely and does not pad its search. Everything is a pure function of its
 * inputs so recorded games replay identically.
 */
object EngineThinkTimePolicy {
    /** Virtual clock used to size thinking when the game itself is untimed. */
    const val UNTIMED_VIRTUAL_MILLIS: Long = 600_000L

    /** Global pace knob: 1.0 would spend the "textbook" share of the clock on every move. */
    private const val TEMPO = 0.30
    private const val UNTIMED_TEMPO = 0.3
    private const val FULL_STRENGTH_SEARCH_CAP_MILLIS = 3_000L
    private const val NATIVE_SEARCH_CAP_MILLIS = 3_000L
    private const val DEPTH_LIMITED_SEARCH_CAP_MILLIS = 2_500L
    private const val COMFORTABLE_THINK_CAP_MILLIS = 12_000L
    private const val MIN_SEARCH_MILLIS = 60L

    fun plan(
        state: RuntimeState,
        strength: EngineStrengthSettings,
        initialClockMillis: Long,
        searchId: EngineSearchId,
    ): EngineThinkPlan {
        val position = state.position
        val mover = position.sideToMove
        val timed = state.clock.enabled
        val remaining = if (timed) {
            state.clock.remaining(if (mover == Color.WHITE) ClockSide.WHITE else ClockSide.BLACK)
        } else {
            UNTIMED_VIRTUAL_MILLIS
        }
        val initial = if (timed) max(initialClockMillis, remaining) else UNTIMED_VIRTUAL_MILLIS
        val increment = if (timed) state.clock.incrementMillis else 0L

        val elo = (strength.target as? EngineStrengthTarget.Elo)?.value
        val discipline = clockDiscipline(elo)

        val legalCount = MoveGenerator.legalMoves(position).size
        val inCheck = MoveGenerator.isInCheck(position, mover)
        val pieceCount = position.board.count { it != null }
        val recapture = lastMoveWasCapture(state)

        val rng = Rng(strength.seed, searchId.value, state.positionRevision.value)

        val movesLeft = (lerp(28.0, 46.0, discipline) - 0.4 * position.fullmoveNumber).coerceAtLeast(12.0)
        val base = remaining.toDouble() / movesLeft + 0.75 * increment
        val tempo = if (timed) TEMPO else UNTIMED_TEMPO

        var think = base * tempo *
            complexityFactor(legalCount) *
            phaseFactor(position.fullmoveNumber, pieceCount, elo) *
            (if (recapture) 0.6 else 1.0) *
            (if (inCheck) 0.75 else 1.0) *
            rng.logNormal(sigma = lerp(0.70, 0.30, discipline)).coerceIn(0.35, 3.2)

        var stalled = false
        if (timed && elo != null && discipline < STALL_DISCIPLINE_LIMIT && legalCount > 1 &&
            remaining < TIME_TROUBLE_FRACTION * initial
        ) {
            val stallProbability = STALL_MAX_PROBABILITY * (1.0 - discipline / STALL_DISCIPLINE_LIMIT)
            val roll = rng.nextUnit()
            val severity = rng.nextUnit()
            if (roll < stallProbability) {
                stalled = true
                think = remaining * lerp(0.40, 1.35, severity)
            }
        }

        if (!stalled) {
            think = think.coerceAtMost(COMFORTABLE_THINK_CAP_MILLIS.toDouble())
            if (elo == null) think = think.coerceAtMost(FULL_STRENGTH_SEARCH_CAP_MILLIS.toDouble())
            // Careful managers never plan to spend the last of their time; weak ones might.
            val safety = if (discipline >= STALL_DISCIPLINE_LIMIT || elo == null) {
                max(400.0, remaining * 0.06)
            } else {
                0.0
            }
            think = think.coerceAtMost(max(1.0, remaining - safety))
        }

        val perceptualFloor = min(
            if (legalCount == 1) 220.0 else if (elo == null) 320.0 else 380.0,
            max(120.0, remaining / 25.0),
        )
        think = max(think, perceptualFloor)

        val thinkMillis = think.toLong()
        val searchCeiling = (remaining - 10L).coerceAtLeast(1L)
        val searchMillis = when {
            elo == null -> min(thinkMillis, FULL_STRENGTH_SEARCH_CAP_MILLIS)
            strength.model == EngineStrengthModel.ENGINE_NATIVE -> min(thinkMillis, NATIVE_SEARCH_CAP_MILLIS)
            else -> min((thinkMillis * 0.9).toLong(), DEPTH_LIMITED_SEARCH_CAP_MILLIS)
        }.coerceAtLeast(MIN_SEARCH_MILLIS).coerceAtMost(searchCeiling)

        // A full-strength engine really searches; it is only held to the perceptual floor. Every
        // Elo-limited opponent is held for its whole planned think.
        val minimumTotal = if (elo == null) min(searchMillis, perceptualFloor.toLong()) else thinkMillis
        return EngineThinkPlan(
            searchMillis = searchMillis,
            minimumTotalMillis = minimumTotal,
            stalled = stalled,
        )
    }

    /** 0 = hopeless clock manager (400 Elo), 1 = perfect (full strength / 3000). */
    internal fun clockDiscipline(elo: Int?): Double {
        if (elo == null) return 1.0
        val normalized = ((elo - EngineStrengthTarget.MIN_ELO).toDouble() /
            (EngineStrengthTarget.MAX_ELO - EngineStrengthTarget.MIN_ELO)).coerceIn(0.0, 1.0)
        return normalized.pow(0.6)
    }

    private const val STALL_DISCIPLINE_LIMIT = 0.8
    private const val STALL_MAX_PROBABILITY = 0.25
    private const val TIME_TROUBLE_FRACTION = 0.12

    private fun complexityFactor(legalCount: Int): Double = when {
        legalCount <= 1 -> 0.10
        legalCount == 2 -> 0.30
        else -> (0.45 + legalCount / 60.0).coerceIn(0.6, 1.2)
    }

    private fun phaseFactor(fullmove: Int, pieceCount: Int, elo: Int?): Double = when {
        fullmove <= 4 -> if (elo != null && elo < 1000) 0.55 else 0.30
        fullmove <= 8 -> if (elo != null && elo < 1000) 0.85 else 0.50
        pieceCount <= 8 -> 0.85
        else -> 1.0
    }

    private fun lastMoveWasCapture(state: RuntimeState): Boolean {
        val node = state.gameTree.node(state.currentNodeId)
        val move = node.move ?: return false
        val parent = state.gameTree.parentOf(state.currentNodeId) ?: return false
        if (parent.position[move.to] != null) return true
        val moving = parent.position[move.from] ?: return false
        return moving.type == PieceType.PAWN &&
            parent.position.enPassantSquare == move.to &&
            move.from.file != move.to.file
    }

    private fun lerp(from: Double, to: Double, fraction: Double): Double = from + (to - from) * fraction

    /** SplitMix64 stream seeded from the recorded strength seed and the search identity. */
    private class Rng(seed: Long, searchId: Long, revision: Long) {
        private var state: Long = seed xor (searchId * -7046029254386353131L) xor (revision * -7723592293110705685L)

        private fun nextLong(): Long {
            state += -7046029254386353131L
            var z = state
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            return z xor (z ushr 31)
        }

        /** Uniform in the open interval (0, 1). */
        fun nextUnit(): Double = ((nextLong() ushr 11).toDouble() + 0.5) / (1L shl 53).toDouble()

        /** Log-normal with median 1. */
        fun logNormal(sigma: Double): Double {
            val u1 = nextUnit()
            val u2 = nextUnit()
            val gaussian = sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
            return exp(sigma * gaussian)
        }
    }
}

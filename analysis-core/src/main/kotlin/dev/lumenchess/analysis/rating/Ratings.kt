package dev.lumenchess.analysis.rating

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Chess.com's convention: estimated duration = base + 40 x increment. Untimed counts as Rapid. */
enum class TimeClass(val label: String) {
    BULLET("Bullet"), BLITZ("Blitz"), RAPID("Rapid");

    companion object {
        fun of(baseMillis: Long?, incrementMillis: Long?): TimeClass {
            if (baseMillis == null || baseMillis <= 0L) return RAPID
            val seconds = (baseMillis + 40L * (incrementMillis ?: 0L)) / 1_000L
            return when {
                seconds < 180L -> BULLET
                seconds < 600L -> BLITZ
                else -> RAPID
            }
        }
    }
}

enum class RatingSystem(val label: String) {
    GLICKO_2("Glicko-2"),
    GLICKO_1("Glicko"),
    FIDE("FIDE Elo"),
    FIDE_STRICT("FIDE Elo (strict)"),
}

/** Full per-system state, stored as-is so switching systems never loses another system's rating. */
data class RatingState(
    val rating: Double,
    val deviation: Double? = null,
    val volatility: Double? = null,
    val games: Int = 0,
) {
    val rounded: Int get() = Math.round(rating).toInt()
}

/** One result against one opponent: 1.0 win, 0.5 draw, 0.0 loss. */
data class RatedResult(
    val opponentRating: Double,
    val score: Double,
    /** Engines play at a fixed strength; a small deviation says we are sure of it. */
    val opponentDeviation: Double = 50.0,
    /** Whole days since this pool's previous game (rating deviation grows with inactivity). */
    val daysSincePrevious: Double = 0.0,
) {
    init { require(score in 0.0..1.0) { "Score must be between 0 and 1" } }
}

object RatingSystems {
    fun initial(system: RatingSystem): RatingState = when (system) {
        RatingSystem.GLICKO_2 -> RatingState(1_500.0, Glicko2.INITIAL_RD, Glicko2.INITIAL_VOLATILITY)
        RatingSystem.GLICKO_1 -> RatingState(1_500.0, Glicko1.INITIAL_RD)
        RatingSystem.FIDE, RatingSystem.FIDE_STRICT -> RatingState(1_500.0)
    }

    fun update(system: RatingSystem, state: RatingState, result: RatedResult): RatingState = when (system) {
        RatingSystem.GLICKO_2 -> Glicko2.update(state, result)
        RatingSystem.GLICKO_1 -> Glicko1.update(state, result)
        RatingSystem.FIDE -> FideElo.update(state, result, strict = false)
        RatingSystem.FIDE_STRICT -> FideElo.update(state, result, strict = true)
    }

    /** Applies results in order from the pool's initial state (event sourcing). */
    fun replay(system: RatingSystem, results: List<RatedResult>): RatingState =
        results.fold(initial(system)) { state, result -> update(system, state, result) }
}

/** Glickman's Glicko (1995), as used in Chess.com-style ratings. Each game is its own period. */
object Glicko1 {
    const val INITIAL_RD = 350.0
    const val MIN_RD = 30.0
    /** RD regrows from 50 to 350 over about a year of inactivity. */
    private const val C = 18.0
    private val Q = ln(10.0) / 400.0

    fun g(rd: Double): Double = 1.0 / sqrt(1.0 + 3.0 * Q * Q * rd * rd / (PI * PI))

    fun expected(rating: Double, opponent: Double, opponentRd: Double): Double =
        1.0 / (1.0 + 10.0.pow(-g(opponentRd) * (rating - opponent) / 400.0))

    fun update(state: RatingState, result: RatedResult): RatingState {
        val rd = minOf(INITIAL_RD, sqrt((state.deviation ?: INITIAL_RD).pow(2) + C * C * result.daysSincePrevious))
        return period(state.copy(deviation = rd), listOf(result), minRd = MIN_RD)
    }

    /** One rating period with several games (Glickman's formulas, no inactivity step). */
    fun period(state: RatingState, results: List<RatedResult>, minRd: Double = 0.0): RatingState {
        val rd = state.deviation ?: INITIAL_RD
        var dInverse = 0.0
        var sum = 0.0
        for (result in results) {
            val gj = g(result.opponentDeviation)
            val e = expected(state.rating, result.opponentRating, result.opponentDeviation)
            dInverse += Q * Q * gj * gj * e * (1.0 - e)
            sum += gj * (result.score - e)
        }
        val denominator = 1.0 / (rd * rd) + dInverse
        val rating = state.rating + Q / denominator * sum
        val newRd = sqrt(1.0 / denominator).coerceIn(minRd, INITIAL_RD)
        return RatingState(rating, newRd, null, state.games + results.size)
    }
}

/**
 * Glickman's Glicko-2 with Lichess's published parameters (rating 1500, RD 500, volatility 0.09,
 * tau 0.75, RD between 45 and 500, about 0.214 rating periods per day), one game per period.
 */
object Glicko2 {
    const val INITIAL_RD = 500.0
    const val INITIAL_VOLATILITY = 0.09
    const val MIN_RD = 45.0
    const val LICHESS_TAU = 0.75
    private const val PERIODS_PER_DAY = 0.21436
    private const val SCALE = 173.7178
    private const val EPSILON = 0.000001

    fun update(state: RatingState, result: RatedResult): RatingState {
        var phi = (state.deviation ?: INITIAL_RD) / SCALE
        val sigma = state.volatility ?: INITIAL_VOLATILITY
        // Inactivity: phi grows with volatility for each idle rating period.
        val idle = result.daysSincePrevious * PERIODS_PER_DAY
        if (idle > 0) phi = sqrt(phi * phi + sigma * sigma * idle)
        phi = minOf(phi, INITIAL_RD / SCALE)
        val updated = period(state.copy(deviation = phi * SCALE), listOf(result), LICHESS_TAU)
        return updated.copy(deviation = updated.deviation!!.coerceIn(MIN_RD, INITIAL_RD))
    }

    /** One rating period with several games, exactly as in Glickman's paper (steps 2-8). */
    fun period(state: RatingState, results: List<RatedResult>, tau: Double): RatingState {
        val mu = (state.rating - 1_500.0) / SCALE
        val phi = (state.deviation ?: INITIAL_RD) / SCALE
        val sigma = state.volatility ?: INITIAL_VOLATILITY
        var vInverse = 0.0
        var deltaSum = 0.0
        for (result in results) {
            val muJ = (result.opponentRating - 1_500.0) / SCALE
            val phiJ = result.opponentDeviation / SCALE
            val g = 1.0 / sqrt(1.0 + 3.0 * phiJ * phiJ / (PI * PI))
            val e = 1.0 / (1.0 + exp(-g * (mu - muJ)))
            vInverse += g * g * e * (1.0 - e)
            deltaSum += g * (result.score - e)
        }
        val v = 1.0 / vInverse
        val delta = v * deltaSum
        val newSigma = newVolatility(sigma, phi, v, delta, tau)
        val phiStar = sqrt(phi * phi + newSigma * newSigma)
        val newPhi = 1.0 / sqrt(1.0 / (phiStar * phiStar) + 1.0 / v)
        val newMu = mu + newPhi * newPhi * deltaSum
        return RatingState(newMu * SCALE + 1_500.0, newPhi * SCALE, newSigma, state.games + results.size)
    }

    /** Illinois-algorithm root find from Glickman's paper, step 5. */
    private fun newVolatility(sigma: Double, phi: Double, v: Double, delta: Double, tau: Double): Double {
        val a = ln(sigma * sigma)
        fun f(x: Double): Double {
            val ex = exp(x)
            val num = ex * (delta * delta - phi * phi - v - ex)
            val den = 2.0 * (phi * phi + v + ex).pow(2)
            return num / den - (x - a) / (tau * tau)
        }
        var lower = a
        var upper: Double
        if (delta * delta > phi * phi + v) {
            upper = ln(delta * delta - phi * phi - v)
        } else {
            var k = 1
            while (f(a - k * tau) < 0) k += 1
            upper = a - k * tau
        }
        var fLower = f(lower)
        var fUpper = f(upper)
        var iterations = 0
        while (abs(upper - lower) > EPSILON && iterations < 100) {
            val c = lower + (lower - upper) * fLower / (fUpper - fLower)
            val fC = f(c)
            if (fC * fUpper <= 0) {
                lower = upper
                fLower = fUpper
            } else {
                fLower /= 2.0
            }
            upper = c
            fUpper = fC
            iterations += 1
        }
        return exp(lower / 2.0)
    }
}

/**
 * FIDE-style Elo. Rating difference is capped at 400 for the expectation; K is 40 for the first 30
 * games, 20 below 2400, and 10 once 2400 has been reached. Practical mode lets ratings fall below
 * 1400 (useful for beginners); strict mode applies the 1400 floor of the current regulations.
 */
object FideElo {
    const val STRICT_FLOOR = 1_400.0
    const val PRACTICAL_FLOOR = 100.0

    fun expected(rating: Double, opponent: Double): Double {
        val difference = (opponent - rating).coerceIn(-400.0, 400.0)
        return 1.0 / (1.0 + 10.0.pow(difference / 400.0))
    }

    fun kFactor(state: RatingState): Double = when {
        state.games < 30 -> 40.0
        (state.deviation ?: 0.0) >= 2_400.0 || state.rating >= 2_400.0 -> 10.0
        else -> 20.0
    }

    fun update(state: RatingState, result: RatedResult, strict: Boolean): RatingState {
        val k = kFactor(state)
        val rating = state.rating + k * (result.score - expected(state.rating, result.opponentRating))
        val floor = if (strict) STRICT_FLOOR else PRACTICAL_FLOOR
        // FIDE's K = 10 is permanent once 2400 is reached; the peak is kept in `deviation`'s slot,
        // which Elo does not otherwise use.
        val peak = maxOf(state.deviation ?: 0.0, rating)
        return RatingState(rating.coerceAtLeast(floor), deviation = peak, games = state.games + 1)
    }
}

/**
 * Local Performance Estimate: a recency-weighted average of recent single-game performance
 * ratings (from Game Review), with a confidence that grows with the number of games behind it.
 */
object PerformanceEstimate {
    const val WINDOW = 12

    data class Estimate(val rating: Int, val games: Int) {
        val provisional: Boolean get() = games < 5
    }

    /** [newestFirst] game ratings; recent games weigh most (weights 1.0, 0.9, 0.81, ...). */
    fun of(newestFirst: List<Int>): Estimate? {
        val recent = newestFirst.take(WINDOW)
        if (recent.isEmpty()) return null
        var weighted = 0.0
        var weights = 0.0
        recent.forEachIndexed { index, rating ->
            val weight = 0.9.pow(index)
            weighted += rating * weight
            weights += weight
        }
        return Estimate(Math.round(weighted / weights / 10.0).toInt() * 10, recent.size)
    }
}

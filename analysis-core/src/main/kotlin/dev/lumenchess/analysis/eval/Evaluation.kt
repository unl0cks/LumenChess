package dev.lumenchess.analysis.eval

import dev.lumenchess.core.chess.Color
import kotlin.math.abs
import kotlin.math.exp

/**
 * An engine score from the point of view of the side to move: centipawns, or a forced mate in
 * [Mate.moves] moves (positive: the side to move mates; negative: it gets mated). A finished game
 * is [Checkmated] (the side to move has been mated) or, seen by the player who just moved,
 * [DeliveredMate]; the two negate into each other.
 */
sealed interface Score {
    data class Centipawns(val value: Int) : Score
    data class Mate(val moves: Int) : Score {
        init { require(moves != 0) { "Use Checkmated for a position that is already mate" } }
    }
    data object Checkmated : Score
    data object DeliveredMate : Score

    /** The same score seen by the other side (after a move, the side to move changes). */
    fun negated(): Score = when (this) {
        is Centipawns -> Centipawns(-value)
        is Mate -> Mate(-moves)
        Checkmated -> DeliveredMate
        DeliveredMate -> Checkmated
    }

    /** Centipawns with mates mapped far beyond any material score (for sorting and ACPL). */
    fun toCentipawnsBounded(cap: Int = 1_000): Int = when (this) {
        is Centipawns -> value.coerceIn(-cap, cap)
        is Mate -> if (moves > 0) cap else -cap
        Checkmated -> -cap
        DeliveredMate -> cap
    }

    companion object {
        /** A drawn or dead position. */
        val DRAW: Score = Centipawns(0)
    }
}

/**
 * Converts engine scores into expected points (0..1, a draw counting half) for the side to move.
 *
 * Uses the logistic curve Lichess publishes for its win-percentage bar and accuracy, fitted on
 * rated games; the constant is theirs, the use here is our own. Mates are certain results.
 */
object ExpectedPoints {
    const val MODEL = "lichess-winpct-logistic"
    private const val K = 0.00368208

    fun of(score: Score): Double = when (score) {
        is Score.Centipawns -> {
            val cp = score.value.coerceIn(-1_000, 1_000).toDouble()
            (1.0 + (2.0 / (1.0 + exp(-K * cp)) - 1.0)) / 2.0
        }
        is Score.Mate -> if (score.moves > 0) 1.0 else 0.0
        Score.Checkmated -> 0.0
        Score.DeliveredMate -> 1.0
    }

    /** Expected points for White given a score from [sideToMove]'s point of view. */
    fun forWhite(score: Score, sideToMove: Color): Double =
        if (sideToMove == Color.WHITE) of(score) else 1.0 - of(score)

    /** Pawns (for explanations): mates are reported separately, so they return null. */
    fun pawns(score: Score): Double? = (score as? Score.Centipawns)?.value?.div(100.0)

    fun isDecisive(points: Double): Boolean = points <= 0.03 || points >= 0.97

    fun swing(before: Double, after: Double): Double = abs(before - after)
}

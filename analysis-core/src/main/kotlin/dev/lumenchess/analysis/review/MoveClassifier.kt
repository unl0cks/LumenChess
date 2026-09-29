package dev.lumenchess.analysis.review

import dev.lumenchess.analysis.eval.ExpectedPoints
import dev.lumenchess.analysis.eval.Material
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.eval.StaticExchange
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San
import kotlin.math.exp

/**
 * Lumen's own, transparent move classification (model [ReviewModelVersion.CURRENT]).
 *
 * The base scale is expected-points loss (EPL) against the engine's best move, with the public
 * reference thresholds: Best 0, Excellent up to 0.02, Good up to 0.05, Inaccuracy up to 0.10,
 * Mistake up to 0.20, Blunder above. Special labels add conditions on top:
 *
 * - Book: the move reaches a named opening position (CC0 Lichess data) and is not a real error.
 * - Great: the best move where every alternative the engine considered loses at least
 *   [GREAT_GAP] expected points, in a position that is not already decided.
 * - Brilliant: a best or near-best move that deliberately leaves at least a minor piece's worth of
 *   material en prise (static exchange), while the position stays sound for the mover.
 * - Miss: after the opponent's mistake or blunder, a move that throws the gift away without making
 *   the position worse than it was before that mistake.
 *
 * This is not Chess.com's proprietary backend and does not claim to reproduce it.
 */
object MoveClassifier {
    const val EXCELLENT_MAX = 0.02
    const val GOOD_MAX = 0.05
    const val INACCURACY_MAX = 0.10
    const val MISTAKE_MAX = 0.20
    const val GREAT_GAP = 0.15
    const val BRILLIANT_MIN_SACRIFICE = 2
    const val BOOK_PLY_LIMIT = 30
    private const val BEST_EPSILON = 0.001

    val THRESHOLDS = doubleArrayOf(EXCELLENT_MAX, GOOD_MAX, INACCURACY_MAX, MISTAKE_MAX)

    data class Input(
        val ply: Int,
        val before: Position,
        val move: Move,
        val after: Position,
        val evalBefore: PositionEvaluation,
        val evalAfter: PositionEvaluation,
        /** The opponent's previous reviewed move, if any. */
        val previous: ReviewedMove?,
        val isBookPosition: Boolean,
        val phase: GamePhase,
        val rechecked: Boolean,
    )

    fun classify(input: Input): ReviewedMove {
        val before = input.before
        val move = input.move
        val mover = before.sideToMove
        val legalCount = MoveGenerator.legalMoves(before).size
        val forced = legalCount == 1
        val bestMove = input.evalBefore.bestMove
        val playedBest = bestMove == move

        val scoreBefore = input.evalBefore.score
        // Prefer the played move's score from the same search as the best move (a like-for-like
        // comparison); otherwise the search of the resulting position, seen by the mover.
        val scoreAfter = input.evalBefore.scoreOf(move) ?: input.evalAfter.score.negated()
        val pointsBefore = ExpectedPoints.of(scoreBefore)
        val pointsAfterRaw = ExpectedPoints.of(scoreAfter)
        val epl = if (playedBest || forced) 0.0 else (pointsBefore - pointsAfterRaw).coerceAtLeast(0.0)
        val pointsAfter = if (playedBest || forced) maxOf(pointsAfterRaw, pointsBefore) else pointsAfterRaw

        val sacrifice = sacrificedMaterial(before, move, input.after)
        val book = input.isBookPosition && input.ply < BOOK_PLY_LIMIT

        val base = when {
            forced || playedBest || epl <= BEST_EPSILON -> MoveClassification.BEST
            epl <= EXCELLENT_MAX -> MoveClassification.EXCELLENT
            epl <= GOOD_MAX -> MoveClassification.GOOD
            epl <= INACCURACY_MAX -> MoveClassification.INACCURACY
            epl <= MISTAKE_MAX -> MoveClassification.MISTAKE
            else -> MoveClassification.BLUNDER
        }

        val classification = when {
            book && epl <= INACCURACY_MAX -> MoveClassification.BOOK
            !forced && (base == MoveClassification.BEST || base == MoveClassification.EXCELLENT) &&
                sacrifice >= BRILLIANT_MIN_SACRIFICE && pointsAfter >= 0.45 && pointsBefore < 0.97 ->
                MoveClassification.BRILLIANT
            base == MoveClassification.BEST && isOnlyGoodMove(input, legalCount, pointsBefore) -> MoveClassification.GREAT
            base.isError && isMiss(input.previous, pointsBefore, pointsAfter) -> MoveClassification.MISS
            else -> base
        }

        val after = input.after
        val whitePointsAfter = ExpectedPoints.forWhite(input.evalAfter.score, after.sideToMove)
        return ReviewedMove(
            ply = input.ply,
            mover = mover,
            move = move,
            san = San.generate(before, move),
            bestMove = bestMove,
            bestSan = bestMove?.let { runCatching { San.generate(before, it) }.getOrNull() },
            scoreBefore = scoreBefore,
            scoreAfter = scoreAfter,
            pointsBefore = pointsBefore,
            pointsAfter = pointsAfter,
            expectedPointsLoss = epl,
            classification = classification,
            accuracy = moveAccuracy(epl),
            phase = input.phase,
            forced = forced,
            book = book,
            bestLine = input.evalBefore.lines.firstOrNull()?.moves.orEmpty(),
            sacrificedMaterial = sacrifice.coerceAtLeast(0),
            depth = input.evalBefore.depth,
            nodes = input.evalBefore.nodes,
            nodesPerSecond = input.evalBefore.nodesPerSecond,
            whitePointsAfter = whitePointsAfter,
            rechecked = input.rechecked,
        )
    }

    /**
     * Lichess's published per-move accuracy curve over win-percentage loss (with its +1
     * uncertainty allowance). [epl] is expected points lost, so the win-percentage drop is 100x.
     */
    fun moveAccuracy(epl: Double): Double {
        if (epl <= 0.0) return 100.0
        val raw = 103.1668100711649 * exp(-0.04354415386753951 * epl * 100.0) - 3.166924740191411
        return (raw + 1.0).coerceIn(0.0, 100.0)
    }

    /** True when the EPL sits close enough to a boundary that a deeper search could flip it. */
    fun isBorderline(epl: Double, margin: Double = 0.012): Boolean =
        THRESHOLDS.any { kotlin.math.abs(epl - it) < margin }

    private fun isOnlyGoodMove(input: Input, legalCount: Int, pointsBefore: Double): Boolean {
        if (legalCount <= 2) return false
        if (pointsBefore < 0.10 || pointsBefore > 0.97) return false
        val lines = input.evalBefore.lines
        if (lines.size < 2) return false
        val second = ExpectedPoints.of(lines[1].score)
        if (pointsBefore - second < GREAT_GAP) return false
        // A plain recapture of what was just taken is necessary but not remarkable.
        val previous = input.previous
        if (previous != null && previous.move.to == input.move.to && Material.isCapture(input.before, input.move)) {
            return false
        }
        return true
    }

    private fun isMiss(previous: ReviewedMove?, pointsBefore: Double, pointsAfter: Double): Boolean {
        if (previous == null) return false
        if (previous.classification != MoveClassification.MISTAKE && previous.classification != MoveClassification.BLUNDER) return false
        // What the mover had before the opponent's error, and what that error offered.
        val statusQuo = 1.0 - previous.pointsBefore
        val opportunity = pointsBefore - statusQuo
        if (opportunity < INACCURACY_MAX) return false
        // Threw the gift away, but did not fall below where things stood before it.
        return pointsAfter <= statusQuo + GOOD_MAX && pointsAfter >= statusQuo - INACCURACY_MAX
    }

    /**
     * Net material the move leaves for the taking (pawn units): the largest static-exchange loss
     * on a non-pawn piece that is newly en prise after the move, minus anything the move itself
     * captured. Pawn "sacrifices" and pieces that were already hanging do not count.
     */
    fun sacrificedMaterial(before: Position, move: Move, after: Position): Int {
        val mover = before.sideToMove
        val hangingBefore = StaticExchange.hangingPieces(before, mover).toMap()
        val newlyHanging = StaticExchange.hangingPieces(after, mover).filter { (square, loss) ->
            square == move.to || loss > (hangingBefore[square] ?: 0)
        }
        val offered = newlyHanging.maxOfOrNull { it.second } ?: return 0
        return offered - Material.captured(before, move) - Material.promotionGain(move)
    }

    /** Score as a short signed string: "+1.3", "-0.4", "M3", "-M2". */
    fun formatScore(score: Score): String = when (score) {
        is Score.Centipawns -> {
            val pawns = score.value / 100.0
            val text = String.format(java.util.Locale.ROOT, "%.1f", kotlin.math.abs(pawns))
            if (score.value > 0) "+$text" else if (score.value < 0) "-$text" else "0.0"
        }
        is Score.Mate -> if (score.moves > 0) "M${score.moves}" else "-M${-score.moves}"
        Score.DeliveredMate -> "#"
        Score.Checkmated -> "-#"
    }
}

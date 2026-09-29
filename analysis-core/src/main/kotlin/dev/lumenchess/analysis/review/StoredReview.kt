package dev.lumenchess.analysis.review

import dev.lumenchess.analysis.eval.ExpectedPoints
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San
import kotlin.math.ln

/**
 * One mainline move of a finished review as kept in the durable (lightweight) columns. These
 * survive heavy-cache compaction, so a review stays readable after its engine lines are gone.
 */
data class StoredReviewPly(
    val ply: Int,
    /** Evaluation of the position after the move, from that position's side to move. */
    val scoreAfter: Score?,
    /** Engine's best move in the position before the move. */
    val bestMove: Move?,
    val classification: MoveClassification?,
    val expectedPointsLoss: Double?,
    val depth: Int?,
    val nodes: Long?,
    val timeMillis: Long?,
)

/** Rebuilds reviewed moves from stored data (see [StoredReviewPly]). */
object ReviewReconstruction {
    /**
     * Returns null when the stored rows do not cover every move (an unfinished review).
     * Classifications and losses are the stored ones: a review never silently changes after it was
     * made, even if a newer model would judge a move differently.
     */
    fun fromLightweight(startPosition: Position, moves: List<Move>, stored: List<StoredReviewPly>): Reconstructed? {
        if (moves.isEmpty()) return null
        val byPly = stored.associateBy { it.ply }
        if ((0 until moves.size).any { ply ->
                val row = byPly[ply]
                row?.scoreAfter == null || row.classification == null || row.expectedPointsLoss == null
            }
        ) return null
        val positions = ArrayList<Position>(moves.size + 1).apply {
            add(startPosition)
            moves.forEach { add(MoveGenerator.applyLegalMove(last(), it)) }
        }
        val phases = PhaseDetector.phases(positions)
        val out = ArrayList<ReviewedMove>(moves.size)
        moves.forEachIndexed { ply, move ->
            val row = byPly.getValue(ply)
            val before = positions[ply]
            val after = positions[ply + 1]
            val epl = row.expectedPointsLoss!!
            val scoreAfter = row.scoreAfter!!.negated()
            val rawAfter = ExpectedPoints.of(scoreAfter)
            val scoreBefore = if (ply > 0) byPly.getValue(ply - 1).scoreAfter!! else scoreForPoints((rawAfter + epl).coerceIn(0.0, 1.0))
            val pointsBefore = ExpectedPoints.of(scoreBefore)
            val classification = row.classification!!
            val forced = MoveGenerator.legalMoves(before).size == 1
            val pointsAfter = if (epl == 0.0) maxOf(rawAfter, pointsBefore) else rawAfter
            val best = row.bestMove?.takeIf { candidate -> MoveGenerator.legalMoves(before).any { it == candidate } }
            out += ReviewedMove(
                ply = ply,
                mover = before.sideToMove,
                move = move,
                san = San.generate(before, move),
                bestMove = best,
                bestSan = best?.let { San.generate(before, it) },
                scoreBefore = scoreBefore,
                scoreAfter = scoreAfter,
                pointsBefore = pointsBefore,
                pointsAfter = pointsAfter,
                expectedPointsLoss = epl,
                classification = classification,
                accuracy = MoveClassifier.moveAccuracy(epl),
                phase = phases[ply],
                forced = forced,
                book = classification == MoveClassification.BOOK,
                bestLine = listOfNotNull(best),
                sacrificedMaterial = MoveClassifier.sacrificedMaterial(before, move, after).coerceAtLeast(0),
                depth = row.depth,
                nodes = row.nodes,
                nodesPerSecond = null,
                whitePointsAfter = ExpectedPoints.forWhite(row.scoreAfter, after.sideToMove),
                rechecked = false,
            )
        }
        val startWhite = ExpectedPoints.forWhite(out.first().scoreBefore, startPosition.sideToMove)
        return Reconstructed(out, startWhite)
    }

    data class Reconstructed(val moves: List<ReviewedMove>, val startWhitePoints: Double) {
        fun summary(): ReviewSummary = ReviewSummarizer.summarize(moves, startWhitePoints)
    }

    /** Inverse of the expected-points curve (centipawns for given points), for display only. */
    fun scoreForPoints(points: Double): Score {
        val p = points.coerceIn(0.001, 0.999)
        val cp = -ln(1.0 / p - 1.0) / 0.00368208
        return Score.Centipawns(Math.round(cp).toInt().coerceIn(-1_000, 1_000))
    }
}

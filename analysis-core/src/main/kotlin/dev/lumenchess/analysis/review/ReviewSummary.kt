package dev.lumenchess.analysis.review

import dev.lumenchess.analysis.eval.Material
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Opening / middlegame / endgame split, after the idea of Lichess's "Divider": the middlegame
 * begins once few minor and major pieces remain or a back rank has emptied, the endgame once at
 * most six of them remain.
 */
object PhaseDetector {
    fun phases(positions: List<Position>): List<GamePhase> {
        var reachedMiddle = false
        var reachedEnd = false
        return positions.mapIndexed { index, position ->
            val pieces = Material.majorsAndMinors(position)
            if (!reachedEnd && pieces <= 6) reachedEnd = true
            if (!reachedMiddle && (pieces <= 10 || backRankSparse(position) || index >= 40)) reachedMiddle = true
            when {
                reachedEnd -> GamePhase.ENDGAME
                reachedMiddle -> GamePhase.MIDDLEGAME
                else -> GamePhase.OPENING
            }
        }
    }

    private fun backRankSparse(position: Position): Boolean {
        fun count(rank: Int, color: Color) = (0..7).count { file ->
            val piece = position.board[rank * 8 + file]
            piece != null && piece.color == color && piece.type != PieceType.PAWN
        }
        return count(0, Color.WHITE) < 4 || count(7, Color.BLACK) < 4
    }
}

/**
 * Game accuracy per side, following Lichess's published approach: per-move accuracy from
 * win-percentage loss, combined as the average of a volatility-weighted mean (sharp positions
 * count more) and a harmonic mean (a few bad moves pull the score down hard).
 */
object GameAccuracy {
    /** [whitePoints] holds White's expected points for every position from the start (N + 1). */
    fun perSide(moves: List<ReviewedMove>, whitePoints: List<Double>): Map<Color, Double?> {
        if (moves.isEmpty() || whitePoints.size < 2) return mapOf(Color.WHITE to null, Color.BLACK to null)
        val percents = whitePoints.map { it * 100.0 }
        val windowSize = (percents.size / 10).coerceIn(2, 8).coerceAtMost(percents.size)
        val windows = List((windowSize - 2).coerceAtLeast(0)) { percents.take(windowSize) } +
            percents.windowed(windowSize)
        val weights = windows.map { standardDeviation(it).coerceIn(0.5, 12.0) }
        return Color.entries.associateWith { color ->
            val own = moves.filter { it.mover == color }
            if (own.isEmpty()) return@associateWith null
            val pairs = own.map { move -> move.accuracy to (weights.getOrNull(move.ply) ?: 1.0) }
            val weighted = pairs.sumOf { it.first * it.second } / pairs.sumOf { it.second }
            val harmonic = pairs.size / pairs.sumOf { 1.0 / maxOf(1.0, it.first) }
            (weighted + harmonic) / 2.0
        }
    }

    private fun standardDeviation(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}

/**
 * Single-game performance estimate from move quality (not a rating, and it swings a lot).
 *
 * Provisional calibration ([VERSION]): an accuracy-to-rating curve anchored where Lichess-style
 * accuracy is commonly reported for rating bands, shrunk towards 1500 for games with few
 * non-trivial moves. It will be re-fitted against reviewed reference games; the version is stored
 * with each review so old results stay stable.
 */
object GameRatingModel {
    const val VERSION = "lumen-game-rating-1"
    private val ANCHORS = listOf(
        0.0 to 100.0, 30.0 to 250.0, 45.0 to 450.0, 55.0 to 650.0, 65.0 to 900.0, 72.0 to 1_150.0,
        78.0 to 1_400.0, 82.0 to 1_600.0, 86.0 to 1_850.0, 90.0 to 2_150.0, 93.0 to 2_400.0,
        95.0 to 2_600.0, 97.0 to 2_800.0, 99.0 to 3_050.0, 100.0 to 3_200.0,
    )
    private const val PRIOR = 1_500.0
    private const val SHRINK = 8.0

    fun estimate(accuracy: Double?, significantMoves: Int): Int? {
        if (accuracy == null || significantMoves == 0) return null
        val raw = interpolate(accuracy.coerceIn(0.0, 100.0))
        val weight = significantMoves / (significantMoves + SHRINK)
        return ((PRIOR + (raw - PRIOR) * weight) / 10.0).roundToInt() * 10
    }

    private fun interpolate(accuracy: Double): Double {
        val upper = ANCHORS.indexOfFirst { it.first >= accuracy }.coerceAtLeast(1)
        val (x0, y0) = ANCHORS[upper - 1]
        val (x1, y1) = ANCHORS[upper]
        return y0 + (y1 - y0) * ((accuracy - x0) / (x1 - x0))
    }
}

data class SideSummary(
    val color: Color,
    val accuracy: Double?,
    val gameRating: Int?,
    val counts: Map<MoveClassification, Int>,
    val phaseAccuracy: Map<GamePhase, Double?>,
    val averageCentipawnLoss: Int?,
    val averageExpectedPointsLoss: Double?,
    val moves: Int,
)

data class ReviewSummary(
    val white: SideSummary,
    val black: SideSummary,
    /** White's expected points for each position from the start (the evaluation graph). */
    val graph: List<Double>,
    /** Plies worth stopping at: highlights, errors and large swings, in game order. */
    val keyMoments: List<Int>,
    val modelVersion: String = ReviewModelVersion.CURRENT,
) {
    fun side(color: Color): SideSummary = if (color == Color.WHITE) white else black
}

object ReviewSummarizer {
    private const val KEY_SWING = 0.15

    fun summarize(moves: List<ReviewedMove>, startWhitePoints: Double): ReviewSummary {
        val graph = listOf(startWhitePoints) + moves.map { it.whitePointsAfter }
        val accuracy = GameAccuracy.perSide(moves, graph)
        fun side(color: Color): SideSummary {
            val own = moves.filter { it.mover == color }
            val significant = own.filterNot { it.book || it.forced }
            val acc = accuracy[color]
            return SideSummary(
                color = color,
                accuracy = acc,
                gameRating = GameRatingModel.estimate(acc, significant.size),
                counts = MoveClassification.entries.associateWith { c -> own.count { it.classification == c } },
                phaseAccuracy = GamePhase.entries.associateWith { phase ->
                    own.filter { it.phase == phase }.takeIf { it.isNotEmpty() }?.map { it.accuracy }?.average()
                },
                averageCentipawnLoss = significant.takeIf { it.isNotEmpty() }?.map { it.centipawnLoss }?.average()?.roundToInt(),
                averageExpectedPointsLoss = significant.takeIf { it.isNotEmpty() }?.map { it.expectedPointsLoss }?.average(),
                moves = own.size,
            )
        }
        val keyMoments = moves.filterIndexed { index, move ->
            val swing = kotlin.math.abs(graph[index + 1] - graph[index])
            move.classification.isHighlight || move.classification.isError && move.classification != MoveClassification.INACCURACY ||
                swing >= KEY_SWING
        }.map { it.ply }
        return ReviewSummary(side(Color.WHITE), side(Color.BLACK), graph, keyMoments)
    }
}

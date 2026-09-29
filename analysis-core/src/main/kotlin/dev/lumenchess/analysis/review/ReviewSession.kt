package dev.lumenchess.analysis.review

import dev.lumenchess.analysis.eval.ExpectedPoints
import dev.lumenchess.analysis.eval.Material
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.openings.OpeningBook
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Rules
import dev.lumenchess.core.chess.Termination

enum class ReviewPreset { FAST, BALANCED, DEEP, CUSTOM }

data class ReviewSettings(
    val preset: ReviewPreset = ReviewPreset.BALANCED,
    /** CUSTOM only. */
    val customTimeMillis: Long = 1_000L,
    val customDepth: Int? = null,
    val customNodes: Long? = null,
    val customMultiPv: Int = 3,
    val criticalRechecks: Boolean = true,
) {
    /** Stored with the review so its classifications can always be traced to how they were made. */
    fun describe(): String = when (preset) {
        ReviewPreset.CUSTOM -> "custom:t=$customTimeMillis,d=${customDepth ?: "-"},n=${customNodes ?: "-"},pv=$customMultiPv,rc=$criticalRechecks"
        else -> preset.name.lowercase()
    }
}

data class SearchBudget(
    val timeMillis: Long?,
    val depth: Int?,
    val nodes: Long?,
    val multiPv: Int,
)

/** "Evaluate [position] (index [index] in the game) within [budget]." [pass] 2 is a deeper recheck. */
data class EvaluationRequest(
    val index: Int,
    val position: Position,
    val budget: SearchBudget,
    val pass: Int,
)

/**
 * A review's deterministic plan. It asks for one position evaluation at a time (adaptively
 * budgeted), accepts results (including ones restored from storage, which makes reviews resumable
 * after cancellation or process death), and finally classifies every move. Borderline moves, and
 * the rare special labels, get a second, deeper pass before they are final.
 *
 * It never touches an engine or threads: the caller runs each [EvaluationRequest] and reports back.
 */
class ReviewSession(
    val startPosition: Position,
    val moves: List<Move>,
    val settings: ReviewSettings = ReviewSettings(),
    private val openingBook: OpeningBook? = null,
    /** Score for the final position when the game ended by a rule the position alone does not show. */
    private val finalScoreOverride: Score? = null,
) {
    val positions: List<Position>
    private val evaluations: Array<PositionEvaluation?>
    private val passes: IntArray
    private val phases: List<GamePhase>
    private val recheckQueue = ArrayDeque<Int>()
    private var recheckPlanned = false
    private var classified: List<ReviewedMove>? = null

    init {
        val list = ArrayList<Position>(moves.size + 1)
        var position = startPosition
        list += position
        for (move in moves) {
            position = MoveGenerator.applyLegalMove(position, move)
            list += position
        }
        positions = list
        evaluations = arrayOfNulls(positions.size)
        passes = IntArray(positions.size)
        phases = PhaseDetector.phases(positions)
        positions.forEachIndexed { index, p ->
            ruleScore(index, p)?.let {
                evaluations[index] = PositionEvaluation(it)
                passes[index] = 2
            }
        }
    }

    val totalSteps: Int get() = positions.size
    val evaluatedCount: Int get() = evaluations.count { it != null }
    val progress: Double get() = evaluatedCount.toDouble() / positions.size

    /** Positions already evaluated (for persistence) with the pass that produced them. */
    fun evaluations(): Map<Int, Pair<PositionEvaluation, Int>> =
        evaluations.withIndex().mapNotNull { (i, e) -> e?.let { i to (it to passes[i]) } }.toMap()

    /** Restores evaluations saved by an earlier, interrupted run. */
    fun restore(index: Int, evaluation: PositionEvaluation, pass: Int) {
        if (index !in positions.indices) return
        evaluations[index] = evaluation
        passes[index] = pass
        classified = null
    }

    /** The next position to evaluate, or null when the review is complete. */
    fun nextRequest(): EvaluationRequest? {
        evaluations.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { index ->
            return EvaluationRequest(index, positions[index], budgetFor(index, pass = 1), pass = 1)
        }
        if (!recheckPlanned) planRechecks()
        while (recheckQueue.isNotEmpty()) {
            val index = recheckQueue.removeFirst()
            if (passes[index] < 2) return EvaluationRequest(index, positions[index], budgetFor(index, pass = 2), pass = 2)
        }
        return null
    }

    fun accept(index: Int, evaluation: PositionEvaluation, pass: Int) {
        if (index !in positions.indices) return
        // A deeper pass replaces a shallower one; never the other way round.
        if (pass >= passes[index] || evaluations[index] == null) {
            evaluations[index] = evaluation
            passes[index] = pass
        }
        classified = null
    }

    val isComplete: Boolean get() = evaluations.all { it != null } && nextRequestPeek() == null

    private fun nextRequestPeek(): Int? {
        if (!recheckPlanned) return 0
        return recheckQueue.firstOrNull { passes[it] < 2 }
    }

    /** Classifies every move using what is known now (requires every position evaluated). */
    fun reviewedMoves(): List<ReviewedMove> {
        classified?.let { return it }
        check(evaluations.all { it != null }) { "Review is not complete" }
        val result = ArrayList<ReviewedMove>(moves.size)
        moves.forEachIndexed { ply, move ->
            val reviewed = MoveClassifier.classify(
                MoveClassifier.Input(
                    ply = ply,
                    before = positions[ply],
                    move = move,
                    after = positions[ply + 1],
                    evalBefore = evaluations[ply]!!,
                    evalAfter = evaluations[ply + 1]!!,
                    previous = result.lastOrNull(),
                    isBookPosition = openingBook?.isBookPosition(positions[ply + 1]) == true,
                    phase = phases[ply],
                    rechecked = passes[ply] >= 2,
                ),
            )
            result += reviewed
        }
        classified = result
        return result
    }

    fun summary(): ReviewSummary {
        val start = evaluations[0]!!
        return ReviewSummarizer.summarize(reviewedMoves(), ExpectedPoints.forWhite(start.score, positions[0].sideToMove))
    }

    private fun planRechecks() {
        recheckPlanned = true
        if (!settings.criticalRechecks || settings.preset == ReviewPreset.FAST) return
        if (evaluations.any { it == null }) return
        val limit = when (settings.preset) {
            ReviewPreset.BALANCED -> 16
            ReviewPreset.DEEP -> Int.MAX_VALUE
            else -> 24
        }
        val candidates = reviewedMoves()
            .filter { move ->
                !move.book && !move.forced && (
                    MoveClassifier.isBorderline(move.expectedPointsLoss) ||
                        move.classification == MoveClassification.GREAT ||
                        move.classification == MoveClassification.BRILLIANT ||
                        move.classification == MoveClassification.MISS ||
                        move.classification == MoveClassification.BLUNDER
                    )
            }
            .sortedByDescending { it.expectedPointsLoss }
            .take(limit)
        val indices = LinkedHashSet<Int>()
        for (move in candidates.sortedBy { it.ply }) {
            indices += move.ply
            indices += move.ply + 1
        }
        recheckQueue.addAll(indices.filter { passes[it] < 2 })
    }

    /** Adaptive time: forced and quiet positions get less, tactical ones more. */
    fun budgetFor(index: Int, pass: Int): SearchBudget {
        val position = positions[index]
        val legal = MoveGenerator.legalMoves(position)
        val forced = legal.size <= 1
        val tactical = MoveGenerator.isInCheck(position, position.sideToMove) ||
            legal.count { Material.isCapture(position, it) } >= 3
        val base = when (settings.preset) {
            ReviewPreset.FAST -> SearchBudget(timeMillis = 200L, depth = 14, nodes = null, multiPv = 2)
            ReviewPreset.BALANCED -> SearchBudget(
                timeMillis = when {
                    forced -> 60L
                    tactical -> 900L
                    legal.size <= 12 -> 350L
                    else -> 550L
                },
                depth = if (forced) 10 else null,
                nodes = null,
                multiPv = if (forced) 1 else 3,
            )
            ReviewPreset.DEEP -> SearchBudget(
                timeMillis = when {
                    forced -> 150L
                    tactical -> 3_000L
                    else -> 1_600L
                },
                depth = null,
                nodes = null,
                multiPv = if (forced) 1 else 3,
            )
            ReviewPreset.CUSTOM -> SearchBudget(
                timeMillis = settings.customTimeMillis,
                depth = settings.customDepth,
                nodes = settings.customNodes,
                multiPv = settings.customMultiPv.coerceIn(1, 5),
            )
        }
        if (pass < 2) return base
        return base.copy(
            timeMillis = base.timeMillis?.let { it * 5 / 2 },
            depth = base.depth?.let { it + 4 },
            nodes = base.nodes?.let { it * 3 },
        )
    }

    private fun ruleScore(index: Int, position: Position): Score? {
        if (index == positions.lastIndex) finalScoreOverride?.let { return it }
        return when (Rules.termination(position)) {
            Termination.CHECKMATE -> Score.Checkmated
            Termination.STALEMATE -> Score.DRAW
            null -> if (Rules.isInsufficientMaterial(position)) Score.DRAW else null
        }
    }
}

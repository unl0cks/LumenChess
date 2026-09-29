package dev.lumenchess.analysis.review

import dev.lumenchess.analysis.eval.ExpectedPoints
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move

/** Versioned identity of the review algorithms. Stored with every review; bump on any change. */
object ReviewModelVersion {
    const val CURRENT = "lumen-review-1"
}

enum class MoveClassification(val label: String) {
    BRILLIANT("Brilliant"),
    GREAT("Great"),
    BOOK("Book"),
    BEST("Best"),
    EXCELLENT("Excellent"),
    GOOD("Good"),
    INACCURACY("Inaccuracy"),
    MISTAKE("Mistake"),
    MISS("Miss"),
    BLUNDER("Blunder");

    val isError: Boolean get() = this == INACCURACY || this == MISTAKE || this == MISS || this == BLUNDER
    val isHighlight: Boolean get() = this == BRILLIANT || this == GREAT

    companion object {
        fun fromName(name: String?): MoveClassification? = entries.firstOrNull { it.name == name }
    }
}

enum class GamePhase { OPENING, MIDDLEGAME, ENDGAME }

/** One engine line from an analysed position; [score] is from that position's side to move. */
data class EngineLine(val moves: List<Move>, val score: Score)

/**
 * Everything the review needs to know about one position. For finished positions (mate,
 * stalemate) there are no lines and [score] comes from the rules, not an engine.
 */
data class PositionEvaluation(
    val score: Score,
    val lines: List<EngineLine> = emptyList(),
    val depth: Int? = null,
    val nodes: Long? = null,
    val nodesPerSecond: Long? = null,
    val timeMillis: Long? = null,
) {
    val bestMove: Move? get() = lines.firstOrNull()?.moves?.firstOrNull()
    val points: Double get() = ExpectedPoints.of(score)

    /** Score of the line starting with [move], when the engine looked at it. */
    fun scoreOf(move: Move): Score? = lines.firstOrNull { it.moves.firstOrNull() == move }?.score
}

/** A move as reviewed, from its mover's point of view. */
data class ReviewedMove(
    /** 0-based index of the move in the mainline. */
    val ply: Int,
    val mover: Color,
    val move: Move,
    val san: String,
    val bestMove: Move?,
    val bestSan: String?,
    /** Best achievable score before the move, mover's view. */
    val scoreBefore: Score,
    /** Score after the played move, mover's view. */
    val scoreAfter: Score,
    val pointsBefore: Double,
    val pointsAfter: Double,
    val expectedPointsLoss: Double,
    val classification: MoveClassification,
    /** 0..100 for this move alone. */
    val accuracy: Double,
    val phase: GamePhase,
    val forced: Boolean,
    val book: Boolean,
    /** Principal variation after the best move, starting with it. */
    val bestLine: List<Move>,
    /** Material (pawn units) deliberately left en prise by the move, when that is what it did. */
    val sacrificedMaterial: Int,
    /** Engine depth reached for the position before the move. */
    val depth: Int?,
    val nodes: Long?,
    val nodesPerSecond: Long?,
    /** White's expected points after the move (for the evaluation graph). */
    val whitePointsAfter: Double,
    /** Borderline moves were re-searched deeper before this classification was final. */
    val rechecked: Boolean,
) {
    val centipawnLoss: Int
        get() = (scoreBefore.toCentipawnsBounded() - scoreAfter.toCentipawnsBounded()).coerceAtLeast(0)
}

package dev.lumenchess.analysis.review

import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.eval.StaticExchange
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San

data class MoveExplanation(val headline: String, val detail: String)

/**
 * Plain-language explanation of a reviewed move: what it was, why, and what was better. Built
 * only from facts the review established (scores, the engine's best line, static exchanges), so
 * it never invents motives the analysis cannot support.
 */
object MoveExplanations {
    fun explain(move: ReviewedMove, before: Position, after: Position, openingName: String? = null): MoveExplanation {
        val best = move.bestSan
        val evalChange = "${MoveClassifier.formatScore(move.scoreBefore)} → ${MoveClassifier.formatScore(move.scoreAfter)}"
        return when (move.classification) {
            MoveClassification.BRILLIANT -> MoveExplanation(
                "${move.san} is brilliant",
                "A sound sacrifice: it gives up ${material(move.sacrificedMaterial)} and the position still holds ($evalChange).",
            )
            MoveClassification.GREAT -> MoveExplanation(
                "${move.san} is a great move",
                if (move.pointsBefore >= 0.6) "The only move that keeps the advantage; everything else lets it go."
                else "The only move that holds the position; everything else loses ground.",
            )
            MoveClassification.BOOK -> MoveExplanation(
                "${move.san} is a book move",
                openingName?.let { "A known opening move: $it." } ?: "A known opening move.",
            )
            MoveClassification.BEST -> MoveExplanation(
                "${move.san} is best",
                if (move.forced) "The only legal move." else "The engine's top choice.",
            )
            MoveClassification.EXCELLENT -> MoveExplanation(
                "${move.san} is excellent",
                best?.let { "Almost as strong as $it." } ?: "Almost as strong as the best move.",
            )
            MoveClassification.GOOD -> MoveExplanation(
                "${move.san} is good",
                best?.let { "$it was a little stronger." } ?: "A slightly stronger move was available.",
            )
            MoveClassification.INACCURACY -> MoveExplanation(
                "${move.san} is an inaccuracy",
                listOfNotNull(consequence(move, before, after), best?.let { "$it was better." }, "($evalChange)").joinToString(" "),
            )
            MoveClassification.MISTAKE -> MoveExplanation(
                "${move.san} is a mistake",
                listOfNotNull(consequence(move, before, after), best?.let { "$it was much better." }, "($evalChange)").joinToString(" "),
            )
            MoveClassification.MISS -> MoveExplanation(
                "${move.san} misses a chance",
                listOfNotNull(
                    "Your opponent's last move gave you an opening.",
                    best?.let { "$it would have made use of it." },
                    "($evalChange)",
                ).joinToString(" "),
            )
            MoveClassification.BLUNDER -> MoveExplanation(
                "${move.san} is a blunder",
                listOfNotNull(consequence(move, before, after), best?.let { "$it was necessary." }, "($evalChange)").joinToString(" "),
            )
        }
    }

    /** The concrete damage, when the analysis shows one. */
    private fun consequence(move: ReviewedMove, before: Position, after: Position): String? {
        val scoreAfter = move.scoreAfter
        if (scoreAfter is Score.Mate && scoreAfter.moves < 0) {
            return "It allows a forced mate in ${-scoreAfter.moves}."
        }
        if (move.scoreBefore is Score.Mate && (move.scoreBefore as Score.Mate).moves > 0 && scoreAfter !is Score.Mate) {
            return "It lets a forced mate slip."
        }
        // A piece that is newly en prise (or more so than before), or the piece that just moved.
        val wasHanging = StaticExchange.hangingPieces(before, move.mover).toMap()
        val hanging = StaticExchange.hangingPieces(after, move.mover)
            .filter { (square, loss) -> loss >= 2 && (square == move.move.to || loss > (wasHanging[square] ?: 0)) }
            .maxByOrNull { it.second }
            ?: return null
        val piece = after[hanging.first]?.type?.let(::pieceName) ?: "piece"
        return "It leaves the $piece on ${hanging.first.algebraic} to be taken."
    }

    private fun material(pawns: Int): String = when {
        pawns >= 8 -> "the queen's worth of material"
        pawns >= 5 -> "a rook's worth of material"
        pawns >= 3 -> "a piece"
        else -> "material"
    }

    fun pieceName(type: PieceType): String = when (type) {
        PieceType.PAWN -> "pawn"
        PieceType.KNIGHT -> "knight"
        PieceType.BISHOP -> "bishop"
        PieceType.ROOK -> "rook"
        PieceType.QUEEN -> "queen"
        PieceType.KING -> "king"
    }

    /** SAN of an engine line from [position], stopping at the first move that does not apply. */
    fun lineSan(position: Position, moves: List<dev.lumenchess.core.chess.Move>, limit: Int = 6): List<String> {
        var current = position
        val out = ArrayList<String>()
        for (move in moves.take(limit)) {
            val san = runCatching { San.generate(current, move) }.getOrNull() ?: break
            out += san
            current = MoveGenerator.applyLegalMove(current, move)
        }
        return out
    }
}

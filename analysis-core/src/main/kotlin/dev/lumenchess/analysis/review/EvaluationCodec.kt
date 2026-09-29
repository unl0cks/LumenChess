package dev.lumenchess.analysis.review

import dev.lumenchess.analysis.eval.EngineLines
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.core.chess.Position

/** An evaluation as stored with a review, with the pass that produced it (2 = deeper recheck). */
data class StoredEvaluation(val evaluation: PositionEvaluation, val pass: Int)

/**
 * Compact, versioned text form of a [PositionEvaluation] for the review cache. Moves are UCI and
 * are re-validated against the position on decode, so a corrupt or foreign payload can never
 * introduce an illegal move.
 *
 * `lev1|p=2|s=c:35|d=22|n=123|v=456|t=789|l=c:35:e2e4 e7e5|l=m:-3:d1h5`
 */
object EvaluationCodec {
    const val FORMAT = "lumen-eval-1"
    private const val MAGIC = "lev1"

    fun encode(evaluation: PositionEvaluation, pass: Int): String = buildString {
        append(MAGIC).append("|p=").append(pass)
        append("|s=").append(encodeScore(evaluation.score))
        evaluation.depth?.let { append("|d=").append(it) }
        evaluation.nodes?.let { append("|n=").append(it) }
        evaluation.nodesPerSecond?.let { append("|v=").append(it) }
        evaluation.timeMillis?.let { append("|t=").append(it) }
        for (line in evaluation.lines) {
            append("|l=").append(encodeScore(line.score)).append(':')
            append(line.moves.joinToString(" ") { it.uci })
        }
    }

    fun decode(text: String, position: Position): StoredEvaluation? {
        val parts = text.split('|')
        if (parts.firstOrNull() != MAGIC) return null
        var pass = 1
        var score: Score? = null
        var depth: Int? = null
        var nodes: Long? = null
        var nps: Long? = null
        var time: Long? = null
        val lines = ArrayList<EngineLine>()
        for (part in parts.drop(1)) {
            val key = part.substringBefore('=', "")
            val value = part.substringAfter('=', "")
            when (key) {
                "p" -> pass = value.toIntOrNull() ?: return null
                "s" -> score = decodeScore(value) ?: return null
                "d" -> depth = value.toIntOrNull()
                "n" -> nodes = value.toLongOrNull()
                "v" -> nps = value.toLongOrNull()
                "t" -> time = value.toLongOrNull()
                "l" -> {
                    // "c:35:e2e4 e7e5" -> score kind, value, moves
                    val first = value.indexOf(':')
                    val second = if (first < 0) -1 else value.indexOf(':', first + 1)
                    if (second < 0) return null
                    val lineScore = decodeScore(value.substring(0, second)) ?: return null
                    val moves = EngineLines.legalPrefix(position, value.substring(second + 1))
                    if (moves.isNotEmpty()) lines += EngineLine(moves, lineScore)
                }
            }
        }
        val resolved = score ?: return null
        return StoredEvaluation(PositionEvaluation(resolved, lines, depth, nodes, nps, time), pass)
    }

    fun encodeScore(score: Score): String = when (score) {
        is Score.Centipawns -> "c:${score.value}"
        is Score.Mate -> "m:${score.moves}"
        Score.Checkmated -> "x:0"
        Score.DeliveredMate -> "w:0"
    }

    fun decodeScore(text: String): Score? {
        val kind = text.substringBefore(':', "")
        val value = text.substringAfter(':', "").toIntOrNull() ?: return null
        return when (kind) {
            "c" -> Score.Centipawns(value)
            "m" -> if (value != 0) Score.Mate(value) else null
            "x" -> Score.Checkmated
            "w" -> Score.DeliveredMate
            else -> null
        }
    }

    /**
     * Lightweight columns (always kept, even after the heavy cache is compacted): centipawns, or a
     * mate count, from the side to move. A checkmated side to move is stored as mate 0.
     */
    fun toColumns(score: Score): Pair<Int?, Int?> = when (score) {
        is Score.Centipawns -> score.value to null
        is Score.Mate -> null to score.moves
        Score.Checkmated -> null to 0
        Score.DeliveredMate -> null to Int.MAX_VALUE
    }

    fun fromColumns(centipawns: Int?, mateIn: Int?): Score? = when {
        mateIn == 0 -> Score.Checkmated
        mateIn == Int.MAX_VALUE -> Score.DeliveredMate
        mateIn != null -> Score.Mate(mateIn)
        centipawns != null -> Score.Centipawns(centipawns)
        else -> null
    }
}

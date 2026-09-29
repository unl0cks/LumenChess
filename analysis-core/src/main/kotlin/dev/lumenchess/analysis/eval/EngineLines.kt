package dev.lumenchess.analysis.eval

import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position

/**
 * Engine output is untrusted: a principal variation is kept only up to its first move that is not
 * legal in the position it is played from (core-chess stays the legality authority).
 */
object EngineLines {
    fun legalPrefix(position: Position, uciMoves: List<String>, limit: Int = 24): List<Move> {
        var current = position
        val out = ArrayList<Move>(minOf(uciMoves.size, limit))
        for (uci in uciMoves) {
            if (out.size >= limit) break
            val candidate = runCatching { Move.parseUci(uci) }.getOrNull() ?: break
            val legal = MoveGenerator.legalMoves(current).firstOrNull { it == candidate } ?: break
            out += legal
            current = MoveGenerator.applyLegalMove(current, legal)
        }
        return out
    }

    /** Parses a whitespace-separated UCI line. */
    fun legalPrefix(position: Position, line: String, limit: Int = 24): List<Move> =
        legalPrefix(position, line.split(' ').filter { it.isNotBlank() }, limit)
}

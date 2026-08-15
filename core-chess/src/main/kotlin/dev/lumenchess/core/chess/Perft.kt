package dev.lumenchess.core.chess

object Perft {
    fun count(position: Position, depth: Int): Long {
        require(depth >= 0) { "Depth cannot be negative" }
        if (depth == 0) return 1
        val legal = MoveGenerator.legalMoves(position)
        if (depth == 1) return legal.size.toLong()
        var nodes = 0L
        for (move in legal) {
            nodes += count(MoveGenerator.applyLegalMove(position, move), depth - 1)
        }
        return nodes
    }
}

package dev.lumenchess.core.chess

class GameHistory(initial: Position) {
    private val counts = mutableMapOf<Long, Int>()

    init { record(initial) }

    fun record(position: Position) {
        val key = position.repetitionKey
        counts[key] = (counts[key] ?: 0) + 1
    }

    fun occurrences(position: Position): Int = counts[position.repetitionKey] ?: 0
}

data class DrawStatus(
    val claimableFiftyMove: Boolean,
    val claimableThreefold: Boolean,
    val automaticSeventyFiveMove: Boolean,
    val automaticFivefold: Boolean,
    val insufficientMaterial: Boolean,
)

object Rules {
    fun termination(position: Position): Termination? {
        if (MoveGenerator.legalMoves(position).isNotEmpty()) return null
        return if (MoveGenerator.isInCheck(position, position.sideToMove)) Termination.CHECKMATE else Termination.STALEMATE
    }

    fun drawStatus(position: Position, history: GameHistory): DrawStatus = DrawStatus(
        claimableFiftyMove = position.halfmoveClock >= 100,
        claimableThreefold = history.occurrences(position) >= 3,
        automaticSeventyFiveMove = position.halfmoveClock >= 150,
        automaticFivefold = history.occurrences(position) >= 5,
        insufficientMaterial = isInsufficientMaterial(position),
    )

    fun isInsufficientMaterial(position: Position): Boolean {
        val nonKings = position.board.mapIndexedNotNull { index, piece ->
            if (piece == null || piece.type == PieceType.KING) null else Square.fromIndex(index) to piece
        }
        if (nonKings.any { (_, p) -> p.type in setOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN) }) return false
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1) return nonKings[0].second.type in setOf(PieceType.BISHOP, PieceType.KNIGHT)
        if (nonKings.all { it.second.type == PieceType.BISHOP }) {
            val colors = nonKings.map { (sq, _) -> (sq.file + sq.rank) and 1 }.toSet()
            return colors.size == 1
        }
        return false
    }
}

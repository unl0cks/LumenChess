package dev.lumenchess.analysis.eval

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square

object Material {
    /** Conventional pawn units. The king is priced out of reach so exchanges never "win" it. */
    fun value(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 1
        PieceType.KNIGHT -> 3
        PieceType.BISHOP -> 3
        PieceType.ROOK -> 5
        PieceType.QUEEN -> 9
        PieceType.KING -> 100
    }

    fun total(position: Position, color: Color): Int = position.board.sumOf { piece ->
        if (piece != null && piece.color == color && piece.type != PieceType.KING) value(piece.type) else 0
    }

    /** Queens, rooks, bishops and knights of both sides (Lichess "Divider" style phase input). */
    fun majorsAndMinors(position: Position): Int = position.board.count { piece ->
        piece != null && piece.type != PieceType.PAWN && piece.type != PieceType.KING
    }

    /** Material the move captures immediately (en passant included), in pawn units. */
    fun captured(position: Position, move: Move): Int {
        val target = position[move.to]
        if (target != null && target.color != position.sideToMove) return value(target.type)
        val mover = position[move.from] ?: return 0
        return if (mover.type == PieceType.PAWN && move.from.file != move.to.file && target == null) 1 else 0
    }

    /** Pawn units gained by promoting (queen = +8 over the pawn). */
    fun promotionGain(move: Move): Int = move.promotion?.let { value(it) - 1 } ?: 0

    fun isCapture(position: Position, move: Move): Boolean = captured(position, move) > 0

    fun isCheck(position: Position, move: Move): Boolean {
        val after = MoveGenerator.applyLegalMove(position, move)
        return MoveGenerator.isInCheck(after, after.sideToMove)
    }
}

/**
 * Static exchange evaluation: what [side] wins, in pawn units, by starting a capture sequence on
 * [target] with both sides always recapturing with their least valuable piece and free to stop.
 * X-ray attackers behind a capturing slider join in. Pins are ignored (a standard approximation).
 * Returns 0 when [side] cannot capture there profitably or at all.
 */
object StaticExchange {
    fun gain(position: Position, target: Square, side: Color): Int {
        val victim = position[target] ?: return 0
        if (victim.color == side) return 0
        val board = position.board.toTypedArray()
        val gains = IntArray(34)
        var depth = 0
        gains[0] = Material.value(victim.type)
        var mover = side
        var attackerSquare = leastValuableAttacker(board, target, mover) ?: return 0
        while (true) {
            depth += 1
            val attacker = board[attackerSquare.index]!!
            gains[depth] = Material.value(attacker.type) - gains[depth - 1]
            board[target.index] = attacker
            board[attackerSquare.index] = null
            mover = mover.opposite
            attackerSquare = leastValuableAttacker(board, target, mover) ?: break
            if (depth >= gains.size - 2) break
        }
        while (--depth > 0) {
            gains[depth - 1] = -maxOf(-gains[depth - 1], gains[depth])
        }
        return maxOf(0, gains[0])
    }

    /** Pieces of [color] (not pawns or kings) that the opponent wins material by capturing. */
    fun hangingPieces(position: Position, color: Color): List<Pair<Square, Int>> {
        val result = ArrayList<Pair<Square, Int>>()
        position.board.forEachIndexed { index, piece ->
            if (piece == null || piece.color != color) return@forEachIndexed
            if (piece.type == PieceType.PAWN || piece.type == PieceType.KING) return@forEachIndexed
            val square = Square.fromIndex(index)
            val loss = gain(position, square, color.opposite)
            if (loss > 0) result += square to loss
        }
        return result
    }

    private fun leastValuableAttacker(board: Array<Piece?>, target: Square, color: Color): Square? {
        var best: Square? = null
        var bestValue = Int.MAX_VALUE
        for (square in attackers(board, target, color)) {
            val value = Material.value(board[square.index]!!.type)
            if (value < bestValue) {
                bestValue = value
                best = square
            }
        }
        return best
    }

    private val KNIGHT = arrayOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val KING = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    private val ORTHOGONAL = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val DIAGONAL = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

    internal fun attackers(board: Array<Piece?>, target: Square, color: Color): List<Square> {
        val result = ArrayList<Square>(8)
        fun pieceAt(file: Int, rank: Int): Piece? =
            if (file in 0..7 && rank in 0..7) board[Square.of(file, rank).index] else null
        val pawnRank = if (color == Color.WHITE) target.rank - 1 else target.rank + 1
        for (df in intArrayOf(-1, 1)) {
            val piece = pieceAt(target.file + df, pawnRank)
            if (piece == Piece(color, PieceType.PAWN)) result += Square.of(target.file + df, pawnRank)
        }
        for ((df, dr) in KNIGHT) {
            if (pieceAt(target.file + df, target.rank + dr) == Piece(color, PieceType.KNIGHT)) {
                result += Square.of(target.file + df, target.rank + dr)
            }
        }
        for ((df, dr) in KING) {
            if (pieceAt(target.file + df, target.rank + dr) == Piece(color, PieceType.KING)) {
                result += Square.of(target.file + df, target.rank + dr)
            }
        }
        fun slide(directions: Array<Pair<Int, Int>>, types: Set<PieceType>) {
            for ((df, dr) in directions) {
                var file = target.file + df
                var rank = target.rank + dr
                while (file in 0..7 && rank in 0..7) {
                    val piece = board[Square.of(file, rank).index]
                    if (piece != null) {
                        if (piece.color == color && piece.type in types) result += Square.of(file, rank)
                        break
                    }
                    file += df
                    rank += dr
                }
            }
        }
        slide(ORTHOGONAL, setOf(PieceType.ROOK, PieceType.QUEEN))
        slide(DIAGONAL, setOf(PieceType.BISHOP, PieceType.QUEEN))
        return result
    }
}

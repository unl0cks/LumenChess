package dev.lumenchess.board

import dev.lumenchess.core.chess.CastleSide
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square

internal object ChessboardMoveResolver {
    fun candidates(
        position: Position,
        legalMoves: List<Move>,
        from: Square,
        target: Square,
    ): List<Move> {
        val fromMoves = legalMoves.filter { it.from == from }
        if (fromMoves.isEmpty()) return emptyList()

        val exact = fromMoves.filter { it.to == target }
        if (exact.isNotEmpty()) return exact

        return fromMoves.filter { move ->
            val side = MoveGenerator.castlingSide(position, move) ?: return@filter false
            castlingDisplayTarget(move, side) == target
        }
    }

    fun isCapture(position: Position, move: Move): Boolean {
        if (MoveGenerator.castlingSide(position, move) != null) return false
        val targetPiece = position[move.to]
        if (targetPiece != null) return targetPiece.color != position.sideToMove

        val movingPiece = position[move.from] ?: return false
        return movingPiece.type == PieceType.PAWN &&
            move.from.file != move.to.file &&
            position.enPassantSquare == move.to
    }

    private fun castlingDisplayTarget(move: Move, side: CastleSide): Square {
        val finalFile = when (side) {
            CastleSide.KING_SIDE -> 6
            CastleSide.QUEEN_SIDE -> 2
        }
        val kingFinal = Square.of(finalFile, move.from.rank)
        return if (kingFinal == move.from) move.to else kingFinal
    }
}

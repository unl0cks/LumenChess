package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class M6Chess960Test {
    @Test
    fun legalChess960CastleIsGeneratedFromNonStandardKingSquare() {
        val board = MutableList<Piece?>(64) { null }
        board[Square.parse("b1").index] = Piece(Color.WHITE, PieceType.KING)
        board[Square.parse("h1").index] = Piece(Color.WHITE, PieceType.ROOK)
        board[Square.parse("e8").index] = Piece(Color.BLACK, PieceType.KING)

        val position = Position(
            board = board,
            sideToMove = Color.WHITE,
            castlingRights = CastlingRights(whiteKingSide = true),
            enPassantSquare = null,
            halfmoveClock = 0,
            fullmoveNumber = 1,
            variant = Variant.CHESS960,
        )

        // UCI_Chess960 represents castling as king-start -> castling-rook-start.
        assertTrue(Move.parseUci("b1h1") in MoveGenerator.legalMoves(position))
    }
}

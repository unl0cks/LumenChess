package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Chess960AdditionalCastlingTest {
    @Test
    fun rookOnlyPathOccupancyCanBlockCastling() {
        // King path g1-f1-e1-d1-c1 is clear; b1 is only on the rook a1-d1 path.
        val position = Fen.parse("7k/8/8/8/8/8/8/RN4K1 w A - 0 1", Variant.CHESS960)
        assertTrue(Move.parseUci("g1a1") !in MoveGenerator.legalMoves(position))
    }

    @Test
    fun blackChess960CastlingUsesSymmetricUciAndFinalSquares() {
        val position = Fen.parse("rk2r3/8/8/8/8/8/8/4K3 b ea - 0 1", Variant.CHESS960)
        val castle = Move.parseUci("b8e8")
        assertTrue(castle in MoveGenerator.legalMoves(position))
        assertEquals(CastleSide.KING_SIDE, MoveGenerator.castlingSide(position, castle))

        val after = MoveGenerator.applyLegalMove(position, castle)
        assertEquals(Piece(Color.BLACK, PieceType.KING), after[Square.parse("g8")])
        assertEquals(Piece(Color.BLACK, PieceType.ROOK), after[Square.parse("f8")])
        assertEquals(Piece(Color.BLACK, PieceType.ROOK), after[Square.parse("a8")])
    }
}

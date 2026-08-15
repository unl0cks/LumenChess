package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Chess960CastlingTest {
    private fun chess960(fen: String): Position = Fen.parse(fen, Variant.CHESS960)
    private fun legalUci(position: Position): Set<String> = MoveGenerator.legalMoves(position).map { it.uci }.toSet()

    @Test
    fun representativeLayoutSupportsBothCastlingSidesAndFixedFinalSquares() {
        val position = chess960("4k3/8/8/8/8/8/8/RK2R3 w EA - 0 1")
        assertTrue("b1e1" in legalUci(position), "kingside UCI_Chess960 castle")
        assertTrue("b1a1" in legalUci(position), "queenside UCI_Chess960 castle")

        val kingSide = Move.parseUci("b1e1")
        assertEquals(CastleSide.KING_SIDE, MoveGenerator.castlingSide(position, kingSide))
        val afterKingSide = MoveGenerator.applyLegalMove(position, kingSide)
        assertEquals(Piece(Color.WHITE, PieceType.KING), afterKingSide[Square.parse("g1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), afterKingSide[Square.parse("f1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), afterKingSide[Square.parse("a1")])
        assertFalse(afterKingSide.castlingRights.whiteKingSide)
        assertFalse(afterKingSide.castlingRights.whiteQueenSide)

        val queenSide = Move.parseUci("b1a1")
        assertEquals(CastleSide.QUEEN_SIDE, MoveGenerator.castlingSide(position, queenSide))
        val afterQueenSide = MoveGenerator.applyLegalMove(position, queenSide)
        assertEquals(Piece(Color.WHITE, PieceType.KING), afterQueenSide[Square.parse("c1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), afterQueenSide[Square.parse("d1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), afterQueenSide[Square.parse("e1")])
    }

    @Test
    fun kingAlreadyOnDestinationSquareCanCastle() {
        val position = chess960("4k3/8/8/8/8/8/8/6KR w H - 0 1")
        val castle = Move.parseUci("g1h1")
        assertTrue(castle in MoveGenerator.legalMoves(position))
        val after = MoveGenerator.applyLegalMove(position, castle)
        assertEquals(Piece(Color.WHITE, PieceType.KING), after[Square.parse("g1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), after[Square.parse("f1")])
        assertNull(after[Square.parse("h1")])
    }

    @Test
    fun rookAlreadyOnDestinationSquareCanCastle() {
        val position = chess960("4k3/8/8/8/8/8/8/4KR2 w F - 0 1")
        val castle = Move.parseUci("e1f1")
        assertTrue(castle in MoveGenerator.legalMoves(position))
        val after = MoveGenerator.applyLegalMove(position, castle)
        assertEquals(Piece(Color.WHITE, PieceType.KING), after[Square.parse("g1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), after[Square.parse("f1")])
        assertNull(after[Square.parse("e1")])
    }

    @Test
    fun kingAndRookCanTransposeDuringCastling() {
        val position = chess960("7k/8/8/8/8/8/8/2RK4 w C - 0 1")
        val castle = Move.parseUci("d1c1")
        assertTrue(castle in MoveGenerator.legalMoves(position))
        val after = MoveGenerator.applyLegalMove(position, castle)
        assertEquals(Piece(Color.WHITE, PieceType.KING), after[Square.parse("c1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), after[Square.parse("d1")])
    }

    @Test
    fun blockedAndAttackedCastlingPathsAreRejected() {
        val blocked = chess960("7k/8/8/8/8/8/8/1K1NR3 w E - 0 1")
        assertTrue("b1e1" !in legalUci(blocked), "piece on d1 blocks the king path")

        val whileInCheck = chess960("1r5k/8/8/8/8/8/8/1K2R3 w E - 0 1")
        assertTrue("b1e1" !in legalUci(whileInCheck), "castling while in check")

        val throughCheck = chess960("3r3k/8/8/8/8/8/8/1K2R3 w E - 0 1")
        assertTrue("b1e1" !in legalUci(throughCheck), "castling through attacked d1")

        val intoCheck = chess960("6rk/8/8/8/8/8/8/1K2R3 w E - 0 1")
        assertTrue("b1e1" !in legalUci(intoCheck), "castling into attacked g1")
    }

    @Test
    fun movingCastlingRookCannotExposeKingOnFinalSquare() {
        // Mirrors the class of Chess960 blocker case called out in Stockfish's legality check:
        // removing the castling rook must not expose a line attack on a king that does not move.
        val position = chess960("7k/8/8/8/8/8/8/rRK5 w B - 0 1")
        assertTrue("c1b1" !in legalUci(position))
    }

    @Test
    fun castlingRightsTrackExactRookOriginsAcrossMovesAndCaptures() {
        val original = chess960("7k/8/8/8/8/8/8/RK2R3 w EA - 0 1")

        val rookMoved = MoveGenerator.applyLegalMove(original, Move.parseUci("e1e2"))
        assertNull(rookMoved.castlingRights.whiteKingSideRook)
        assertEquals(Square.parse("a1"), rookMoved.castlingRights.whiteQueenSideRook)

        val kingMoved = MoveGenerator.applyLegalMove(original, Move.parseUci("b1c1"))
        assertNull(kingMoved.castlingRights.whiteKingSideRook)
        assertNull(kingMoved.castlingRights.whiteQueenSideRook)

        val capturePosition = chess960("4r2k/8/8/8/8/8/8/RK2R3 b EA - 0 1")
        val rookCaptured = MoveGenerator.applyLegalMove(capturePosition, Move.parseUci("e8e1"))
        assertNull(rookCaptured.castlingRights.whiteKingSideRook)
        assertEquals(Square.parse("a1"), rookCaptured.castlingRights.whiteQueenSideRook)
    }

    @Test
    fun immutableMakeUnmakeRestoresExactStateAndKey() {
        val position = chess960("4k3/8/8/8/8/8/8/RK2R3 w EA - 17 23")
        val beforeFen = Fen.serialize(position)
        val beforeKey = position.repetitionKey
        val transition = MoveTransition.make(position, Move.parseUci("b1e1"))

        assertNotEquals(position, transition.after)
        val restored = transition.unmake()
        assertSame(position, restored)
        assertEquals(beforeFen, Fen.serialize(restored))
        assertEquals(beforeKey, restored.repetitionKey)
        assertEquals(17, restored.halfmoveClock)
        assertEquals(23, restored.fullmoveNumber)
        assertEquals(Square.parse("e1"), restored.castlingRights.whiteKingSideRook)
        assertEquals(Square.parse("a1"), restored.castlingRights.whiteQueenSideRook)
    }

    @Test
    fun standardCastlingEncodingAndBehaviorRemainUnchanged() {
        val position = Fen.parse("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        val legal = legalUci(position)
        assertTrue("e1g1" in legal)
        assertTrue("e1c1" in legal)
        assertEquals(CastleSide.KING_SIDE, MoveGenerator.castlingSide(position, Move.parseUci("e1g1")))

        val after = MoveGenerator.applyLegalMove(position, Move.parseUci("e1g1"))
        assertEquals(Piece(Color.WHITE, PieceType.KING), after[Square.parse("g1")])
        assertEquals(Piece(Color.WHITE, PieceType.ROOK), after[Square.parse("f1")])
        assertNull(after[Square.parse("h1")])
    }
}

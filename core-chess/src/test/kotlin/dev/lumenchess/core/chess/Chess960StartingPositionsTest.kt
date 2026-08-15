package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Chess960StartingPositionsTest {
    @Test
    fun all960StartingPositionsAreUniqueLegalArrangementsAndFenRoundTrip() {
        val signatures = mutableSetOf<String>()

        for (index in 0 until Chess960.POSITION_COUNT) {
            val backRank = Chess960.backRank(index)
            val signature = backRank.joinToString("") { it.fen.uppercaseChar().toString() }
            assertTrue(signatures.add(signature), "duplicate Chess960 back rank at index $index: $signature")

            assertEquals(1, backRank.count { it == PieceType.KING }, "king count at $index")
            assertEquals(2, backRank.count { it == PieceType.ROOK }, "rook count at $index")
            assertEquals(2, backRank.count { it == PieceType.BISHOP }, "bishop count at $index")
            assertEquals(2, backRank.count { it == PieceType.KNIGHT }, "knight count at $index")
            assertEquals(1, backRank.count { it == PieceType.QUEEN }, "queen count at $index")

            val bishopFiles = backRank.indices.filter { backRank[it] == PieceType.BISHOP }
            assertNotEquals(bishopFiles[0] % 2, bishopFiles[1] % 2, "bishops must start on opposite colors at $index")

            val kingFile = backRank.indexOf(PieceType.KING)
            val rookFiles = backRank.indices.filter { backRank[it] == PieceType.ROOK }
            assertTrue(rookFiles[0] < kingFile && kingFile < rookFiles[1], "king must start between rooks at $index")

            val position = Chess960.startingPosition(index)
            assertEquals(Variant.CHESS960, position.variant)
            assertEquals(Color.WHITE, position.sideToMove)
            assertEquals(0, position.halfmoveClock)
            assertEquals(1, position.fullmoveNumber)

            for (file in 0..7) {
                assertEquals(Piece(Color.WHITE, backRank[file]), position[Square.of(file, 0)], "white back rank at $index/$file")
                assertEquals(Piece(Color.BLACK, backRank[file]), position[Square.of(file, 7)], "black back rank at $index/$file")
                assertEquals(Piece(Color.WHITE, PieceType.PAWN), position[Square.of(file, 1)], "white pawn at $index/$file")
                assertEquals(Piece(Color.BLACK, PieceType.PAWN), position[Square.of(file, 6)], "black pawn at $index/$file")
            }

            assertEquals(Square.of(rookFiles[1], 0), position.castlingRights.whiteKingSideRook)
            assertEquals(Square.of(rookFiles[0], 0), position.castlingRights.whiteQueenSideRook)
            assertEquals(Square.of(rookFiles[1], 7), position.castlingRights.blackKingSideRook)
            assertEquals(Square.of(rookFiles[0], 7), position.castlingRights.blackQueenSideRook)

            val fen = Fen.serialize(position)
            assertEquals(position, Fen.parse(fen, Variant.CHESS960), "Chess960 FEN round trip at index $index")
        }

        assertEquals(Chess960.POSITION_COUNT, signatures.size)
        assertEquals("RNBQKBNR", Chess960.backRank(Chess960.STANDARD_POSITION_INDEX).joinToString("") { it.fen.uppercaseChar().toString() })
    }
}

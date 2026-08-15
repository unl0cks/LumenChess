package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class Chess960FenAndPerftTest {
    @Test
    fun shredderFenRoundTripsWithExactRookFiles() {
        val fen = "4k3/8/8/8/8/8/8/RK2R3 w EA - 7 12"
        val position = Fen.parse(fen, Variant.CHESS960)
        assertEquals(Square.parse("e1"), position.castlingRights.whiteKingSideRook)
        assertEquals(Square.parse("a1"), position.castlingRights.whiteQueenSideRook)
        assertEquals(fen, Fen.serialize(position))
    }

    @Test
    fun xFenKqRightsAreAcceptedAndCanonicalizedToShredderFen() {
        val xFen = "4k3/8/8/8/8/8/8/1R2KR1R w KQ - 0 1"
        val position = Fen.parse(xFen, Variant.CHESS960)
        assertEquals(Square.parse("h1"), position.castlingRights.whiteKingSideRook)
        assertEquals(Square.parse("b1"), position.castlingRights.whiteQueenSideRook)
        assertEquals("4k3/8/8/8/8/8/8/1R2KR1R w HB - 0 1", Fen.serialize(position))

        val innerRook = Fen.parse("4k3/8/8/8/8/8/8/1R2KR1R w F - 0 1", Variant.CHESS960)
        assertEquals(Square.parse("f1"), innerRook.castlingRights.whiteKingSideRook)
        assertEquals("4k3/8/8/8/8/8/8/1R2KR1R w F - 0 1", Fen.serialize(innerRook))
    }

    @Test
    fun standardLayoutParsedAsChess960UsesExactShredderRights() {
        val position = Fen.parse(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            Variant.CHESS960,
        )
        assertEquals(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w HAha - 0 1",
            Fen.serialize(position),
        )
    }

    @Test
    fun chess960FenValidationIsStrictAndStandardParserStaysStandardOnly() {
        assertThrows(IllegalArgumentException::class.java) {
            Fen.parse("4k3/8/8/8/8/8/8/1K6 w E - 0 1", Variant.CHESS960)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Fen.parse("4k3/8/8/8/8/8/8/4K2R w H - 0 1")
        }
    }

    @Test
    fun repetitionKeyIncludesExactCastlingRookAndVariant() {
        val eRight = Fen.parse("4k3/8/8/8/8/8/8/1K2R2R w E - 0 1", Variant.CHESS960)
        val hRight = Fen.parse("4k3/8/8/8/8/8/8/1K2R2R w H - 0 1", Variant.CHESS960)
        assertNotEquals(eRight.repetitionKey, hRight.repetitionKey)

        val noRightsFen = "4k3/8/8/8/8/8/8/1K6 w - - 0 1"
        val standard = Fen.parse(noRightsFen, Variant.STANDARD)
        val chess960 = Fen.parse(noRightsFen, Variant.CHESS960)
        assertNotEquals(standard.repetitionKey, chess960.repetitionKey)
    }

    @Test
    fun stockfishChess960ReferencePerftInnerRookRightDepth2() {
        // official-stockfish/Stockfish tests/perft.sh
        val position = Fen.parse(
            "rr6/2kpp3/1ppn2p1/p2b1q1p/P4P1P/1PNN2P1/2PP4/1K2R2R b E - 1 20",
            Variant.CHESS960,
        )
        assertEquals(1438L, Perft.count(position, 2))
    }

    @Test
    fun stockfishChess960ReferencePerftInnerRookRightDepth3() {
        // official-stockfish/Stockfish tests/perft.sh
        val position = Fen.parse(
            "rr6/2kpp3/1ppn2p1/p2b1q1p/P4P1P/1PNN2P1/2PP4/1K2RR2 w E - 0 20",
            Variant.CHESS960,
        )
        assertEquals(37340L, Perft.count(position, 3))
    }
}

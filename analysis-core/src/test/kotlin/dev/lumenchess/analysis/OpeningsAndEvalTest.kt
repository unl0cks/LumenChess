package dev.lumenchess.analysis

import dev.lumenchess.analysis.eval.ExpectedPoints
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.eval.StaticExchange
import dev.lumenchess.analysis.openings.OpeningBook
import dev.lumenchess.core.chess.Chess960
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San
import dev.lumenchess.core.chess.Square
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun play(vararg sans: String, from: Position = Position.initial()): List<Position> {
    val positions = arrayListOf(from)
    for (san in sans) positions += MoveGenerator.applyLegalMove(positions.last(), San.parse(positions.last(), san))
    return positions
}

class OpeningBookTest {
    private val book = OpeningBook.bundled()

    @Test fun theBundledCc0DataLoadsEveryLine() {
        assertTrue(book.size > 3_500, "loaded ${book.size} openings")
        assertEquals(
            "Sicilian Defense",
            book.identify(play("e4", "c5"))?.name,
        )
    }

    @Test fun deeperLinesNameTheVariation() {
        val najdorf = book.identify(play("e4", "c5", "Nf3", "d6", "d4", "cxd4", "Nxd4", "Nf6", "Nc3", "a6"))
        assertNotNull(najdorf)
        assertEquals("B90", najdorf.eco)
        assertEquals("Sicilian Defense", najdorf.family)
        assertEquals("Najdorf Variation", najdorf.variation)
    }

    @Test fun transpositionsResolveByPositionNotMoveOrder() {
        val viaD4 = book.identify(play("d4", "Nf6", "c4", "e6", "Nc3", "Bb4"))
        val viaC4 = book.identify(play("c4", "e6", "Nc3", "Nf6", "d4", "Bb4"))
        assertNotNull(viaD4)
        assertEquals(viaD4, viaC4)
        assertTrue(viaD4.name.startsWith("Nimzo-Indian"), viaD4.name)
    }

    @Test fun offBookAndChess960PositionsAreNotNamed() {
        val positions = play("e4", "e5", "Ke2", "Ke7", "Ke1", "Ke8", "Ke2")
        assertTrue(book.lastBookIndex(positions) in 1 until positions.lastIndex)
        assertNull(book.at(Chess960.startingPosition(0)))
    }
}

class ExpectedPointsTest {
    @Test fun equalIsHalfAndTheCurveIsSymmetric() {
        assertEquals(0.5, ExpectedPoints.of(Score.Centipawns(0)), 1e-9)
        val plus = ExpectedPoints.of(Score.Centipawns(150))
        val minus = ExpectedPoints.of(Score.Centipawns(-150))
        assertEquals(1.0, plus + minus, 1e-9)
        assertTrue(plus in 0.60..0.70, "150 cp gives $plus")
    }

    @Test fun matesAreCertainAndCheckmateNegatesCorrectly() {
        assertEquals(1.0, ExpectedPoints.of(Score.Mate(3)))
        assertEquals(0.0, ExpectedPoints.of(Score.Mate(-2)))
        assertEquals(0.0, ExpectedPoints.of(Score.Checkmated))
        assertEquals(Score.DeliveredMate, Score.Checkmated.negated())
        assertEquals(1.0, ExpectedPoints.of(Score.Checkmated.negated()))
        assertEquals(0.0, ExpectedPoints.forWhite(Score.Mate(1), Color.BLACK))
    }
}

class StaticExchangeTest {
    @Test fun aDefendedPawnIsNotWorthAKnight() {
        val position = Fen.parse("4k3/8/2p5/3p4/4N3/8/8/4K3 w - - 0 1")
        assertEquals(0, StaticExchange.gain(position, Square.parse("d5"), Color.WHITE))
    }

    @Test fun anUndefendedPieceIsWonOutright() {
        val position = Fen.parse("4k3/8/8/3n4/4P3/8/8/4K3 w - - 0 1")
        assertEquals(3, StaticExchange.gain(position, Square.parse("d5"), Color.WHITE))
        assertEquals(listOf(Square.parse("d5") to 3), StaticExchange.hangingPieces(position, Color.BLACK))
    }

    @Test fun xRayAttackersJoinTheExchange() {
        // Rooks doubled on the d-file win the knight defended once by a rook.
        val position = Fen.parse("3r2k1/8/8/3n4/8/8/3R4/3RK3 w - - 0 1")
        assertEquals(3, StaticExchange.gain(position, Square.parse("d5"), Color.WHITE))
        // A single rook against knight + rook defence gains nothing.
        val single = Fen.parse("3r2k1/8/8/3n4/8/8/8/3RK3 w - - 0 1")
        assertEquals(0, StaticExchange.gain(single, Square.parse("d5"), Color.WHITE))
    }
}

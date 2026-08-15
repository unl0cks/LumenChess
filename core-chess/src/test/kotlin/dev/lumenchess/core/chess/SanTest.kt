package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SanTest {
    private fun standard(fen: String): Position = Fen.parse(fen)
    private fun chess960(fen: String): Position = Fen.parse(fen, Variant.CHESS960)

    @Test
    fun ordinaryPawnAndPieceMovesRoundTrip() {
        val initial = Position.initial()
        assertSan(initial, "e2e4", "e4")
        assertSan(initial, "g1f3", "Nf3")
    }

    @Test
    fun capturesAndPawnCapturesUseCanonicalSan() {
        val pieceCapture = standard("7k/8/8/3p4/8/8/8/3QK3 w - - 0 1")
        assertSan(pieceCapture, "d1d5", "Qxd5")

        val pawnCapture = standard("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        assertSan(pawnCapture, "e4d5", "exd5")
    }

    @Test
    fun checkAndCheckmateSuffixesAreGenerated() {
        val check = standard("4k3/8/8/8/8/8/8/K3R3 w - - 0 1")
        assertSan(check, "e1e7", "Re7+")

        val mate = standard("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4")
        assertSan(mate, "h5f7", "Qxf7#")
    }

    @Test
    fun promotionsAndPromotionChecksAreGenerated() {
        val position = standard("k7/4P3/8/8/8/8/8/7K w - - 0 1")
        assertSan(position, "e7e8n", "e8=N")
        assertSan(position, "e7e8q", "e8=Q+")
    }

    @Test
    fun enPassantIsARegularPawnCaptureInSan() {
        val position = standard("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2")
        assertSan(position, "e5d6", "exd6")
    }

    @Test
    fun standardCastlingUsesLetterO() {
        val position = standard("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertSan(position, "e1g1", "O-O")
        assertSan(position, "e1c1", "O-O-O")
        assertEquals(Move.parseUci("e1g1"), San.parse(position, "0-0"))
        assertEquals(Move.parseUci("e1c1"), San.parse(position, "0-0-0"))
    }

    @Test
    fun chess960CastlingUsesPositionAwareClassification() {
        val representative = chess960("4k3/8/8/8/8/8/8/RK2R3 w EA - 0 1")
        assertSan(representative, "b1e1", "O-O")
        assertSan(representative, "b1a1", "O-O-O")

        val kingDoesNotMove = chess960("4k3/8/8/8/8/8/8/6KR w H - 0 1")
        assertSan(kingDoesNotMove, "g1h1", "O-O")

        val rookDoesNotMove = chess960("4k3/8/8/8/8/8/8/4KR2 w F - 0 1")
        assertSan(rookDoesNotMove, "e1f1", "O-O")
    }

    @Test
    fun fileRankAndFullSquareDisambiguationAreMinimal() {
        val fileOnly = standard("7k/8/8/8/8/8/8/KN3N2 w - - 0 1")
        assertSan(fileOnly, "b1d2", "Nbd2")

        val rankOnly = standard("7k/8/8/8/8/4R3/8/K3R3 w - - 0 1")
        assertSan(rankOnly, "e1e2", "R1e2")

        val both = standard("7k/8/8/8/8/1N6/8/KN3N2 w - - 0 1")
        assertSan(both, "b1d2", "Nb1d2")
    }

    @Test
    fun pinnedPseudoCompetitorDoesNotForceDisambiguation() {
        val position = standard("5r1k/8/8/8/8/1N3N2/5K2/8 w - - 0 1")
        assertSan(position, "b3d4", "Nd4")
    }

    @Test
    fun parserRejectsIllegalAndAmbiguousSanInsteadOfGuessing() {
        val initial = Position.initial()
        assertThrows(IllegalSanException::class.java) { San.parse(initial, "Qh5") }

        val ambiguous = standard("7k/8/8/8/8/8/8/KN3N2 w - - 0 1")
        assertThrows(AmbiguousSanException::class.java) { San.parse(ambiguous, "Nd2") }
    }

    @Test
    fun parserRejectsIncorrectExplicitCheckSuffix() {
        val initial = Position.initial()
        assertThrows(IllegalSanException::class.java) { San.parse(initial, "e4+") }
    }

    private fun assertSan(position: Position, uci: String, expected: String) {
        val move = Move.parseUci(uci)
        assertEquals(expected, San.generate(position, move))
        assertEquals(move, San.parse(position, expected))
    }
}

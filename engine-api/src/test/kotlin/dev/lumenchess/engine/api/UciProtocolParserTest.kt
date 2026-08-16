package dev.lumenchess.engine.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UciProtocolParserTest {
    @Test
    fun parsesHandshakeIdentityAndReadiness() {
        assertEquals(UciEvent.IdName("Stockfish 18"), UciProtocolParser.parse("id name Stockfish 18"))
        assertEquals(UciEvent.IdAuthor("the Stockfish developers"), UciProtocolParser.parse("id author the Stockfish developers"))
        assertEquals(UciEvent.UciOk, UciProtocolParser.parse("uciok"))
        assertEquals(UciEvent.ReadyOk, UciProtocolParser.parse("readyok"))
    }

    @Test
    fun parsesTypedOptions() {
        assertEquals(
            UciEvent.Option(UciOption.Spin("Threads", defaultValue = 1, min = 1, max = 1024)),
            UciProtocolParser.parse("option name Threads type spin default 1 min 1 max 1024"),
        )
        assertEquals(
            UciEvent.Option(UciOption.Check("Ponder", defaultValue = false)),
            UciProtocolParser.parse("option name Ponder type check default false"),
        )
        assertEquals(
            UciEvent.Option(UciOption.Combo("Style", defaultValue = "Normal", values = listOf("Normal", "Risky", "Solid"))),
            UciProtocolParser.parse("option name Style type combo default Normal var Normal var Risky var Solid"),
        )
        assertEquals(UciEvent.Option(UciOption.Button("Clear Hash")), UciProtocolParser.parse("option name Clear Hash type button"))
        assertEquals(UciEvent.Option(UciOption.StringOption("SyzygyPath", defaultValue = "<empty>")), UciProtocolParser.parse("option name SyzygyPath type string default <empty>"))
    }

    @Test
    fun parsesRichInfoLineWithoutLosingSearchMetadata() {
        val event = UciProtocolParser.parse(
            "info depth 22 seldepth 31 multipv 2 score cp -37 lowerbound nodes 123456 nps 900000 time 137 hashfull 42 tbhits 3 pv e2e4 e7e5 g1f3",
        )
        val info = assertIs<UciEvent.Info>(event).info

        assertEquals(22, info.depth)
        assertEquals(31, info.selectiveDepth)
        assertEquals(2, info.multiPv)
        assertEquals(UciScore.Centipawns(-37, UciScoreBound.LOWER), info.score)
        assertEquals(123456L, info.nodes)
        assertEquals(900000L, info.nodesPerSecond)
        assertEquals(137L, info.timeMillis)
        assertEquals(42, info.hashFullPermille)
        assertEquals(3L, info.tablebaseHits)
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), info.principalVariation)
    }

    @Test
    fun parsesMateScoreAndBestMoveWithPonder() {
        val mate = assertIs<UciEvent.Info>(UciProtocolParser.parse("info depth 18 score mate -3 upperbound pv h7h8q"))
        assertEquals(UciScore.Mate(-3, UciScoreBound.UPPER), mate.info.score)

        assertEquals(
            UciEvent.BestMove(bestMove = "e2e4", ponder = "e7e5"),
            UciProtocolParser.parse("bestmove e2e4 ponder e7e5"),
        )
    }

    @Test
    fun bestmoveNullTokensBecomeNoMove() {
        assertEquals(UciEvent.BestMove(bestMove = null, ponder = null), UciProtocolParser.parse("bestmove 0000"))
        assertEquals(UciEvent.BestMove(bestMove = null, ponder = null), UciProtocolParser.parse("bestmove (none)"))
    }

    @Test
    fun infoStringPreservesRemainingText() {
        val info = assertIs<UciEvent.Info>(UciProtocolParser.parse("info depth 1 string NNUE evaluation using net with spaces"))
        assertEquals(1, info.info.depth)
        assertEquals("NNUE evaluation using net with spaces", info.info.string)
        assertNull(info.info.score)
    }

    @Test
    fun unknownLinesAreExplicitRatherThanSilentlyMisparsed() {
        assertEquals(UciEvent.Unknown("custom engine chatter"), UciProtocolParser.parse("custom engine chatter"))
    }

    @Test
    fun malformedKnownLinesFailLoudly() {
        assertFailsWith<UciProtocolException> { UciProtocolParser.parse("bestmove") }
        assertFailsWith<UciProtocolException> { UciProtocolParser.parse("info depth nope") }
        assertFailsWith<UciProtocolException> { UciProtocolParser.parse("option name Threads type spin default x min 1 max 2") }
        assertFailsWith<UciProtocolException> { UciProtocolParser.parse("id") }
    }

    @Test
    fun commandEncoderProducesCanonicalUciCommands() {
        assertEquals("uci", UciCommandEncoder.encode(UciCommand.Initialize))
        assertEquals("isready", UciCommandEncoder.encode(UciCommand.IsReady))
        assertEquals("setoption name UCI_Chess960 value true", UciCommandEncoder.encode(UciCommand.SetOption("UCI_Chess960", "true")))
        assertEquals("position fen 8/8/8/8/8/8/4K3/7k w - - 0 1 moves e2e3", UciCommandEncoder.encode(UciCommand.Position("8/8/8/8/8/8/4K3/7k w - - 0 1", listOf("e2e3"))))
        assertEquals("go depth 12 nodes 50000 movetime 250 multipv 3", UciCommandEncoder.encode(UciCommand.Go(depth = 12, nodes = 50_000, moveTimeMillis = 250, multiPv = 3)))
        assertEquals("stop", UciCommandEncoder.encode(UciCommand.Stop))
        assertEquals("quit", UciCommandEncoder.encode(UciCommand.Quit))
        assertTrue(UciCommandEncoder.encode(UciCommand.NewGame).startsWith("ucinewgame"))
    }
}

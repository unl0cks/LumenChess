package dev.lumenchess.data.persistence

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameContentFingerprintTest {
    @Test
    fun equivalentStandardPgnPresentationProducesSameFingerprint() {
        val compact = Pgn.parseGame("[Event \"A\"]\n[Result \"*\"]\n\n1. e4 e5 2. Nf3 *")
        val decorated = Pgn.parseGame(
            """
            [Event "Different presentation"]
            [Site "Ignored"]
            [Result "*"]

            1. e4 {comment ignored} e5 2. Nf3 $1 *
            """.trimIndent(),
        )

        val first = GameContentFingerprint.compute(compact)
        val second = GameContentFingerprint.compute(decorated)
        assertEquals(first, second)
        assertTrue(first.matches(Regex("gcf1:[0-9a-f]{64}")))
    }

    @Test
    fun chess960AndCustomFenSemanticsAreDeterministic() {
        val chess960A = Pgn.parseGame(
            "[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/RK2R3 w EA - 0 1\"]\n[Result \"*\"]\n\n1. O-O *",
        )
        val chess960B = Pgn.parseGame(
            "[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/RK2R3 w EA - 0 1\"]\n[Result \"*\"]\n\n1. 0-0 *",
        )
        val customA = Pgn.parseGame(
            "[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/K7 b - - 0 12\"]\n[Result \"*\"]\n\n12... Kg7 *",
        )
        val customB = Pgn.parseGame(
            "[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/K7 b - - 0 12\"]\n[Result \"*\"]\n\n12... Kg7 {ignored} *",
        )

        assertEquals(GameContentFingerprint.compute(chess960A), GameContentFingerprint.compute(chess960B))
        assertEquals(GameContentFingerprint.compute(customA), GameContentFingerprint.compute(customB))
    }

    @Test
    fun differentMainlineOrStartingPositionProducesDifferentFingerprint() {
        val e4 = Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")
        val d4 = Pgn.parseGame("[Result \"*\"]\n\n1. d4 *")
        val custom = Pgn.parseGame(
            "[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/4P3/K7 w - - 0 1\"]\n[Result \"*\"]\n\n1. e4 *",
        )

        assertNotEquals(GameContentFingerprint.compute(e4), GameContentFingerprint.compute(d4))
        assertNotEquals(GameContentFingerprint.compute(e4), GameContentFingerprint.compute(custom))
    }

    @Test
    fun variantDifferenceProducesDifferentFingerprintEvenWithSameBoardAndMoves() {
        val standard = GameTree.create(Position.initial(Variant.STANDARD))
            .addMove(GameTree.create(Position.initial(Variant.STANDARD)).rootId, Move.parseUci("e2e4"))
        val standardTree = GameTree.create(Position.initial(Variant.STANDARD)).let { tree ->
            tree.addMove(tree.rootId, Move.parseUci("e2e4")).tree
        }
        val chess960Start = Fen.parse(Fen.serialize(Position.initial(Variant.STANDARD)), Variant.CHESS960)
        val chess960Tree = GameTree.create(chess960Start).let { tree ->
            tree.addMove(tree.rootId, Move.parseUci("e2e4")).tree
        }

        assertNotEquals(GameContentFingerprint.compute(standardTree), GameContentFingerprint.compute(chess960Tree))
        assertEquals(1, standard.tree.mainline().size)
    }

    @Test
    fun separatelySavedIdenticalContentMustNotBeAutoMergedByFingerprint() {
        val tree = Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 *")
        val first = GameContentFingerprint.compute(tree)
        val second = GameContentFingerprint.compute(tree)
        assertEquals(first, second)
        // Equality is intentionally only a candidate signal. Repository identity tests verify separate saves stay separate.
    }
}

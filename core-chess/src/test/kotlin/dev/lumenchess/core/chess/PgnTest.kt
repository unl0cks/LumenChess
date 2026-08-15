package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PgnTest {
    @Test
    fun parsesSimpleStandardGameAndCompleteResult() {
        val game = Pgn.parseGame(
            """
            [Event "Simple"]
            [White "Alpha"]
            [Black "Beta"]
            [Result "1-0"]

            1. f3 e5 2. g4 Qh4# 1-0
            """.trimIndent(),
        )

        assertEquals(Variant.STANDARD, game.startPosition.variant)
        assertEquals(GameResult.WHITE_WIN, game.result)
        assertEquals(listOf("f3", "e5", "g4", "Qh4#"), game.mainline().map { it.san })
        assertEquals("Simple", game.headers["Event"])
    }

    @Test
    fun parsesBlackToMoveCustomFenStartWithEllipsis() {
        val fen = "7k/8/8/8/8/8/8/K7 b - - 0 12"
        val game = Pgn.parseGame(
            """
            [SetUp "1"]
            [FEN "$fen"]
            [Result "*"]

            12... Kg7 *
            """.trimIndent(),
        )

        assertEquals(Fen.parse(fen), game.startPosition)
        assertEquals(listOf("Kg7"), game.mainline().map { it.san })
        assertNull(game.result)
    }

    @Test
    fun parsesChess960VariantAndPositionAwareCastling() {
        val fen = "7k/8/8/8/8/8/8/RK2R3 w EA - 0 1"
        val game = Pgn.parseGame(
            """
            [Variant "Chess960"]
            [SetUp "1"]
            [FEN "$fen"]
            [Result "*"]

            1. O-O *
            """.trimIndent(),
        )

        assertEquals(Variant.CHESS960, game.startPosition.variant)
        assertEquals(Fen.parse(fen, Variant.CHESS960), game.startPosition)
        assertEquals(Move.parseUci("b1e1"), game.mainline().single().move)
        assertEquals("O-O", game.mainline().single().san)
    }

    @Test
    fun commentsSemicolonCommentsNumericAndSymbolicNagsAreStructured() {
        val game = Pgn.parseGame(
            """
            [Result "*"]

            1. e4 {central space} $1 e5?! ;reply note
            2. Nf3 *
            """.trimIndent(),
        )
        val e4 = game.mainline()[0]
        val e5 = game.mainline()[1]

        assertEquals(listOf("central space"), e4.comments)
        assertEquals(listOf(Nag(1)), e4.nags)
        assertEquals(listOf("reply note"), e5.comments)
        assertEquals(listOf(Nag(6)), e5.nags)
    }

    @Test
    fun parsesOneVariationFromThePositionBeforeTheReplacedMove() {
        val game = Pgn.parseGame(
            """
            [Result "*"]

            1. e4 e5 (1... c5 {Sicilian}) 2. Nf3 *
            """.trimIndent(),
        )
        val e4 = game.mainline()[0]
        val children = game.childrenOf(e4.id)

        assertEquals(listOf("e5", "c5"), children.map { it.san })
        assertEquals(e4.id, game.parentOf(children[1].id)?.id)
        assertEquals(listOf("Sicilian"), children[1].comments)
        assertEquals("Nf3", game.mainline()[2].san)
    }

    @Test
    fun parsesMultipleSiblingAndNestedVariationsWithAnnotations() {
        val game = Pgn.parseGame(
            """
            [Result "*"]

            1. e4 e5
            (1... c5 {Sicilian} $1 2. Nf3 (2. d4 $5) Nc6)
            (1... e6?!)
            2. Nf3 *
            """.trimIndent(),
        )
        val e4 = game.mainline()[0]
        val siblings = game.childrenOf(e4.id)
        assertEquals(listOf("e5", "c5", "e6"), siblings.map { it.san })
        assertEquals(listOf(Nag(1)), siblings[1].nags)
        assertEquals(listOf("Sicilian"), siblings[1].comments)
        assertEquals(listOf(Nag(6)), siblings[2].nags)

        val c5Children = game.childrenOf(siblings[1].id)
        assertEquals(listOf("Nf3", "d4"), c5Children.map { it.san })
        assertEquals(listOf(Nag(5)), c5Children[1].nags)
        assertEquals("Nc6", game.mainlineChildOf(c5Children[0].id)?.san)
    }

    @Test
    fun ravRegressionRejectsMoveThatIsOnlyLegalFromTheWrongCurrentPosition() {
        val error = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame(
                """
                [Result "*"]

                1. e4 (1... c5) *
                """.trimIndent(),
            )
        }
        assertEquals("c5", error.token)
        assertTrue(error.message.orEmpty().contains("illegal", ignoreCase = true))
    }

    @Test
    fun parsesPromotionCheckStandardCastlingAndCheckmate() {
        val promotion = Pgn.parseGame(
            """
            [SetUp "1"]
            [FEN "k7/4P3/8/8/8/8/8/7K w - - 0 1"]
            [Result "*"]

            1. e8=Q+ *
            """.trimIndent(),
        )
        assertEquals("e8=Q+", promotion.mainline().single().san)

        val castling = Pgn.parseGame(
            """
            [Result "*"]

            1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. O-O *
            """.trimIndent(),
        )
        assertEquals("O-O", castling.mainline().last().san)

        val mate = Pgn.parseGame(
            """
            [Result "0-1"]

            1. f3 e5 2. g4 Qh4# 0-1
            """.trimIndent(),
        )
        assertEquals("Qh4#", mate.mainline().last().san)
        assertEquals(GameResult.BLACK_WIN, mate.result)
    }

    @Test
    fun malformedTagFailsWithContext() {
        val error = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[Event \"oops\"\n1. e4 *")
        }
        assertTrue(error.index >= 0)
        assertTrue(error.message.orEmpty().contains("tag", ignoreCase = true))
    }

    @Test
    fun unclosedVariationFailsInsteadOfReturningPartialGame() {
        val error = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 (1... c5 2. Nf3")
        }
        assertTrue(error.message.orEmpty().contains("variation", ignoreCase = true))
    }

    @Test
    fun illegalSanAndImpossibleMoveFailWithTokenAndPly() {
        val error = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[Result \"*\"]\n\n1. e5 *")
        }
        assertEquals("e5", error.token)
        assertEquals(1, error.ply)
    }

    @Test
    fun invalidFenAndSetUpWithoutFenFailClearly() {
        val invalidFen = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[SetUp \"1\"]\n[FEN \"not-a-fen\"]\n[Result \"*\"]\n\n*")
        }
        assertTrue(invalidFen.message.orEmpty().contains("FEN"))

        val missingFen = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[SetUp \"1\"]\n[Result \"*\"]\n\n*")
        }
        assertTrue(missingFen.message.orEmpty().contains("FEN"))
    }

    @Test
    fun contradictoryHeaderAndMovetextResultsAreRejected() {
        val error = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[Result \"1-0\"]\n\n1. e4 0-1")
        }
        assertTrue(error.message.orEmpty().contains("Result"))
    }

    @Test
    fun missingMovetextResultIsRejected() {
        val error = assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[Result \"*\"]\n\n1. e4")
        }
        assertTrue(error.message.orEmpty().contains("result", ignoreCase = true))
    }

    @Test
    fun parseGamesSupportsMultipleGames() {
        val games = Pgn.parseGames(
            """
            [Event "One"]
            [Result "*"]

            1. e4 *

            [Event "Two"]
            [Result "1/2-1/2"]

            1. d4 d5 1/2-1/2
            """.trimIndent(),
        )

        assertEquals(2, games.size)
        assertEquals("One", games[0].headers["Event"])
        assertEquals("Two", games[1].headers["Event"])
        assertEquals(GameResult.DRAW, games[1].result)
    }

    @Test
    fun parseGameRejectsTrailingSecondGame() {
        assertThrows(PgnParseException::class.java) {
            Pgn.parseGame("[Result \"*\"]\n\n1. e4 *\n\n[Result \"*\"]\n\n1. d4 *")
        }
    }

    @Test
    fun escapedHeaderValuesRoundTripSemantically() {
        val game = Pgn.parseGame(
            """
            [Event "A \"quoted\" \\ path"]
            [Result "*"]

            1. e4 *
            """.trimIndent(),
        )
        val reparsed = Pgn.parseGame(Pgn.serialize(game))
        assertEquals("A \"quoted\" \\ path", reparsed.headers["Event"])
        assertSemanticTreeEquals(game, reparsed)
    }

    @Test
    fun standardPgnSemanticRoundTripIsDeterministic() {
        assertRoundTrip(
            """
            [Event "Round trip"]
            [White "Alpha"]
            [Black "Beta"]
            [Result "*"]

            1. e4 e5 2. Nf3 Nc6 3. Bb5 *
            """.trimIndent(),
        )
    }

    @Test
    fun chess960PgnSemanticRoundTripPreservesVariantFenAndCastlingRights() {
        assertRoundTrip(
            """
            [Event "960"]
            [Variant "Chess960"]
            [SetUp "1"]
            [FEN "7k/8/8/8/8/8/8/RK2R3 w EA - 0 1"]
            [Result "*"]

            1. O-O *
            """.trimIndent(),
        )
    }

    @Test
    fun nestedVariationSemanticRoundTripPreservesTreeCommentsAndNags() {
        assertRoundTrip(
            """
            [Result "*"]

            1. e4 e5
            (1... c5 {Sicilian} $1 2. Nf3 (2. d4 $5) Nc6)
            (1... e6?!)
            2. Nf3 *
            """.trimIndent(),
        )
    }

    @Test
    fun fenStartedGameSemanticRoundTripPreservesStartPositionAndBlackMoveNumber() {
        assertRoundTrip(
            """
            [SetUp "1"]
            [FEN "7k/8/8/8/8/8/8/K7 b - - 0 12"]
            [Result "*"]

            12... Kg7 *
            """.trimIndent(),
        )
    }

    @Test
    fun standardInitialSerializationDoesNotInventSetUpOrFenTags() {
        val game = Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")
        val output = Pgn.serialize(game)
        assertTrue(!output.contains("[SetUp "))
        assertTrue(!output.contains("[FEN "))
    }

    @Test
    fun chess960SerializationAlwaysCarriesVariantSetUpAndCanonicalFen() {
        val game = Pgn.parseGame(
            """
            [Variant "Fischer Random"]
            [FEN "7k/8/8/8/8/8/8/RK2R3 w EA - 0 1"]
            [Result "*"]

            1. O-O *
            """.trimIndent(),
        )
        val output = Pgn.serialize(game)
        assertTrue(output.contains("[Variant \"Chess960\"]"))
        assertTrue(output.contains("[SetUp \"1\"]"))
        assertTrue(output.contains("[FEN \"${Fen.serialize(game.startPosition)}\"]"))
    }

    private fun assertRoundTrip(input: String) {
        val first = Pgn.parseGame(input)
        val serialized = Pgn.serialize(first)
        val second = Pgn.parseGame(serialized)
        assertSemanticTreeEquals(first, second)
        assertEquals(serialized, Pgn.serialize(second))
    }

    private fun assertSemanticTreeEquals(expected: GameTree, actual: GameTree) {
        assertEquals(expected.startPosition, actual.startPosition)
        assertEquals(expected.result, actual.result)
        assertEquals(expected.headers, actual.headers)
        assertEquals(expected.rootComments, actual.rootComments)
        compareChildren(expected, expected.rootId, actual, actual.rootId)
    }

    private fun compareChildren(expected: GameTree, expectedParent: GameNodeId, actual: GameTree, actualParent: GameNodeId) {
        val expectedChildren = expected.childrenOf(expectedParent)
        val actualChildren = actual.childrenOf(actualParent)
        assertEquals(expectedChildren.size, actualChildren.size)
        expectedChildren.zip(actualChildren).forEach { (left, right) ->
            assertEquals(left.move, right.move)
            assertEquals(left.san, right.san)
            assertEquals(left.position, right.position)
            assertEquals(left.leadingComments, right.leadingComments)
            assertEquals(left.comments, right.comments)
            assertEquals(left.nags, right.nags)
            assertEquals(left.annotations, right.annotations)
            compareChildren(expected, left.id, actual, right.id)
        }
    }
}

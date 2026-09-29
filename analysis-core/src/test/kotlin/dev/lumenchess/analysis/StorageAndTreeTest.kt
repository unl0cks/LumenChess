package dev.lumenchess.analysis

import dev.lumenchess.analysis.eval.EngineLines
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.openings.OpeningBook
import dev.lumenchess.analysis.review.EngineLine
import dev.lumenchess.analysis.review.EvaluationCodec
import dev.lumenchess.analysis.review.PositionEvaluation
import dev.lumenchess.analysis.review.ReviewPreset
import dev.lumenchess.analysis.review.ReviewReconstruction
import dev.lumenchess.analysis.review.ReviewSession
import dev.lumenchess.analysis.review.ReviewSettings
import dev.lumenchess.analysis.review.StoredReviewPly
import dev.lumenchess.analysis.tree.TreeEditing
import dev.lumenchess.core.chess.Chess960
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineLinesTest {
    @Test fun aPrincipalVariationStopsAtItsFirstIllegalMove() {
        val start = Position.initial()
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), EngineLines.legalPrefix(start, "e2e4 e7e5 g1f3 e5e3 b8c6").map { it.uci })
        assertEquals(emptyList(), EngineLines.legalPrefix(start, "zz99 e2e4"))
        assertEquals(1, EngineLines.legalPrefix(start, "e2e4 e7e5 g1f3", limit = 1).size)
    }

    @Test fun chess960CastlingIsKingTakesRook() {
        // Position 518 is the standard arrangement; castling is encoded king-to-rook in Chess960.
        val position = play("e4", "e5", "Nf3", "Nc6", "Bc4", "Bc5", from = Chess960.startingPosition(518)).last()
        assertEquals(listOf("e1h1"), EngineLines.legalPrefix(position, "e1h1").map { it.uci })
        assertEquals(emptyList(), EngineLines.legalPrefix(position, "e1g1").map { it.uci })
    }
}

class EvaluationCodecTest {
    @Test fun evaluationsRoundTripAndForeignMovesAreDropped() {
        val position = Position.initial()
        val evaluation = PositionEvaluation(
            Score.Centipawns(31),
            listOf(
                EngineLine(EngineLines.legalPrefix(position, "e2e4 e7e5 g1f3"), Score.Centipawns(31)),
                EngineLine(EngineLines.legalPrefix(position, "d2d4 d7d5"), Score.Mate(-7)),
            ),
            depth = 22, nodes = 1_234_567L, nodesPerSecond = 890_000L, timeMillis = 1_400L,
        )
        val text = EvaluationCodec.encode(evaluation, pass = 2)
        val decoded = EvaluationCodec.decode(text, position)
        assertNotNull(decoded)
        assertEquals(2, decoded.pass)
        assertEquals(evaluation, decoded.evaluation)

        val tampered = text.replace("e2e4 e7e5", "e2e5 e7e5")
        val safe = EvaluationCodec.decode(tampered, position)!!.evaluation
        assertEquals(1, safe.lines.size, "an illegal first move drops that line")
        assertNull(EvaluationCodec.decode("garbage", position))
    }

    @Test fun terminalScoresSurviveTheLightweightColumns() {
        for (score in listOf(Score.Centipawns(-45), Score.Mate(3), Score.Mate(-2), Score.Checkmated, Score.DeliveredMate)) {
            val (cp, mate) = EvaluationCodec.toColumns(score)
            assertEquals(score, EvaluationCodec.fromColumns(cp, mate))
            assertEquals(score, EvaluationCodec.decodeScore(EvaluationCodec.encodeScore(score)))
        }
    }
}

class ReviewReconstructionTest {
    @Test fun aCompactedReviewKeepsItsClassificationsAndSummary() {
        val sans = listOf("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#")
        val positions = play(*sans.toTypedArray())
        val moves = sans.mapIndexed { i, san -> San.parse(positions[i], san) }
        val session = ReviewSession(Position.initial(), moves, ReviewSettings(ReviewPreset.FAST), OpeningBook.bundled())
        while (true) {
            val request = session.nextRequest() ?: break
            session.accept(request.index, TinyEngine.evaluate(request.position, request.budget.multiPv), request.pass)
        }
        val reviewed = session.reviewedMoves()
        val evaluations = session.evaluations()
        val stored = reviewed.map { move ->
            StoredReviewPly(
                ply = move.ply,
                scoreAfter = evaluations.getValue(move.ply + 1).first.score,
                bestMove = move.bestMove,
                classification = move.classification,
                expectedPointsLoss = move.expectedPointsLoss,
                depth = move.depth, nodes = move.nodes, timeMillis = null,
            )
        }
        val rebuilt = ReviewReconstruction.fromLightweight(Position.initial(), moves, stored)
        assertNotNull(rebuilt)
        assertEquals(reviewed.map { it.classification }, rebuilt.moves.map { it.classification })
        assertEquals(reviewed.map { it.bestSan }, rebuilt.moves.map { it.bestSan })
        val original = session.summary()
        val restored = rebuilt.summary()
        assertEquals(original.graph.drop(1), restored.graph.drop(1))
        assertEquals(original.white.accuracy!!, restored.white.accuracy!!, 0.5)
        assertEquals(original.black.accuracy!!, restored.black.accuracy!!, 0.5)
        assertEquals(original.black.counts, restored.black.counts)

        assertNull(ReviewReconstruction.fromLightweight(Position.initial(), moves, stored.dropLast(1)), "unfinished reviews are not rebuilt")
    }

    @Test fun theExpectedPointsCurveInvertsForDisplay() {
        val score = ReviewReconstruction.scoreForPoints(0.75) as Score.Centipawns
        assertTrue(score.value in 280..320, "${score.value}")
    }
}

class TreeEditingTest {
    private fun tree(): Triple<GameTree, List<Move>, Move> {
        var tree = GameTree.create()
        val mainline = play("e4", "e5", "Nf3")
        val moves = (0 until 3).map { i -> San.parse(mainline[i], listOf("e4", "e5", "Nf3")[i]) }
        var parent = tree.rootId
        for (move in moves) {
            val added = tree.addMove(parent, move)
            tree = added.tree
            parent = added.nodeId
        }
        val sideline = San.parse(mainline[1], "c5")
        return Triple(tree, moves, sideline)
    }

    @Test fun playingAnExistingMoveReusesItsNode() {
        val (tree, moves, sideline) = tree()
        val e4 = tree.mainline().first().id
        val (same, sameId) = TreeEditing.play(tree, tree.rootId, moves.first())
        assertEquals(tree, same)
        assertEquals(e4, sameId)
        val (branched, c5) = TreeEditing.play(tree, e4, sideline)
        assertEquals(2, branched.childrenOf(e4).size)
        assertEquals("c5", branched.node(c5).san)
        assertEquals(listOf(0, 1), TreeEditing.pathTo(branched, c5))
    }

    @Test fun promoteAndDeleteRebuildThroughLegalMoves() {
        val (tree, _, sideline) = tree()
        val e4 = tree.mainline().first().id
        val (branched, c5) = TreeEditing.play(tree, e4, sideline)
        val promoted = TreeEditing.promote(branched, c5)
        assertEquals(listOf("e4", "c5"), promoted.tree.mainline().map { it.san })
        val newC5 = promoted.map(c5)!!
        assertEquals(listOf(0, 0), TreeEditing.pathTo(promoted.tree, newC5))

        val deleted = TreeEditing.delete(promoted.tree, newC5)
        assertEquals(listOf("e4", "e5", "Nf3"), deleted.tree.mainline().map { it.san })
        assertNull(deleted.tree.nodes.values.firstOrNull { it.san == "c5" })
        assertEquals(3, TreeEditing.lineTo(deleted.tree, deleted.tree.mainline().last().id).size)
        assertTrue(MoveGenerator.legalMoves(deleted.tree.mainline().last().position).isNotEmpty())
    }
}

class ExplorerIndexTest {
    private fun game(result: dev.lumenchess.core.chess.GameResult?, vararg sans: String, at: Long = 0L): dev.lumenchess.analysis.explorer.IndexedGame {
        val positions = play(*sans)
        val moves = sans.mapIndexed { i, san -> San.parse(positions[i], san) }
        return dev.lumenchess.analysis.explorer.IndexedGame(Position.initial(), moves, result, at)
    }

    @Test fun movesAreCountedByPositionWithResultsFromWhitesSide() {
        val index = dev.lumenchess.analysis.explorer.ExplorerIndex.build(
            listOf(
                game(dev.lumenchess.core.chess.GameResult.WHITE_WIN, "e4", "e5", "Nf3", "Nc6", at = 10),
                game(dev.lumenchess.core.chess.GameResult.DRAW, "e4", "c5", at = 20),
                game(dev.lumenchess.core.chess.GameResult.BLACK_WIN, "d4", "d5"),
                // Transposes to the first game after 3 plies.
                game(null, "Nf3", "e5", "e4", "Nc6"),
            ),
        )
        assertEquals(4, index.gameCount)
        val first = index.movesAt(Position.initial())
        assertEquals("e2e4", first.first().move.uci)
        assertEquals(2, first.first().games)
        assertEquals(1, first.first().whiteWins)
        assertEquals(1, first.first().draws)
        assertEquals(20L, first.first().lastPlayedEpochMillis)
        assertEquals(4, index.gamesAt(Position.initial()))
        // Both move orders reach the same position; its continuation merges.
        val merged = index.movesAt(play("e4", "e5", "Nf3").last())
        assertEquals(listOf("b8c6"), merged.map { it.move.uci })
        assertEquals(2, merged.single().games)
        assertTrue(index.movesAt(play("e4", "e5", "Nf3", "Nc6").last()).isEmpty())
    }
}

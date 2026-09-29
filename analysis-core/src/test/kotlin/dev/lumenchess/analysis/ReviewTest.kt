package dev.lumenchess.analysis

import dev.lumenchess.analysis.eval.ExpectedPoints
import dev.lumenchess.analysis.eval.Material
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.openings.OpeningBook
import dev.lumenchess.analysis.review.EngineLine
import dev.lumenchess.analysis.review.GameAccuracy
import dev.lumenchess.analysis.review.GamePhase
import dev.lumenchess.analysis.review.GameRatingModel
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.analysis.review.MoveClassifier
import dev.lumenchess.analysis.review.MoveExplanations
import dev.lumenchess.analysis.review.PhaseDetector
import dev.lumenchess.analysis.review.PositionEvaluation
import dev.lumenchess.analysis.review.ReviewPreset
import dev.lumenchess.analysis.review.ReviewSession
import dev.lumenchess.analysis.review.ReviewSettings
import dev.lumenchess.analysis.review.ReviewedMove
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A tiny deterministic "engine" for tests: two-ply material negamax that recognises mate. Enough to
 * exercise the whole review flow without Stockfish.
 */
internal object TinyEngine {
    private const val MATE = 100_000

    fun evaluate(position: Position, multiPv: Int = 3): PositionEvaluation {
        val scored = MoveGenerator.legalMoves(position).map { move ->
            move to -search(MoveGenerator.applyLegalMove(position, move), 1)
        }.sortedByDescending { it.second }
        if (scored.isEmpty()) {
            return PositionEvaluation(if (MoveGenerator.isInCheck(position, position.sideToMove)) Score.Checkmated else Score.DRAW)
        }
        val lines = scored.take(multiPv).map { (move, value) -> EngineLine(listOf(move), toScore(value)) }
        return PositionEvaluation(lines.first().score, lines, depth = 2, nodes = 1_000L, nodesPerSecond = 100_000L, timeMillis = 10L)
    }

    private fun search(position: Position, depth: Int): Int {
        val moves = MoveGenerator.legalMoves(position)
        if (moves.isEmpty()) return if (MoveGenerator.isInCheck(position, position.sideToMove)) -MATE else 0
        if (depth == 0) {
            val side = position.sideToMove
            return (Material.total(position, side) - Material.total(position, side.opposite)) * 100
        }
        return moves.maxOf { -search(MoveGenerator.applyLegalMove(position, it), depth - 1) }
    }

    private fun toScore(value: Int): Score = when {
        value >= MATE -> Score.Mate(1)
        value <= -MATE -> Score.Mate(-1)
        else -> Score.Centipawns(value)
    }
}

class MoveClassifierTest {
    private fun eval(vararg lines: Pair<String, Int>, position: Position): PositionEvaluation {
        val engineLines = lines.map { (san, cp) -> EngineLine(listOf(San.parse(position, san)), Score.Centipawns(cp)) }
        return PositionEvaluation(engineLines.first().score, engineLines, depth = 20)
    }

    private fun classify(
        before: Position,
        san: String,
        evalBefore: PositionEvaluation,
        afterCp: Int,
        previous: ReviewedMove? = null,
        book: Boolean = false,
        ply: Int = 40,
    ): ReviewedMove {
        val move = San.parse(before, san)
        val after = MoveGenerator.applyLegalMove(before, move)
        return MoveClassifier.classify(
            MoveClassifier.Input(
                ply = ply, before = before, move = move, after = after,
                evalBefore = evalBefore,
                evalAfter = PositionEvaluation(Score.Centipawns(-afterCp)),
                previous = previous, isBookPosition = book, phase = GamePhase.MIDDLEGAME, rechecked = false,
            ),
        )
    }

    private val start = Position.initial()

    @Test fun expectedPointsLossMapsToThePublishedScale() {
        val best = eval("e4" to 0, "d4" to 0, "Nf3" to 0, position = start)
        assertEquals(MoveClassification.BEST, classify(start, "e4", best, afterCp = 0).classification)
        assertEquals(MoveClassification.EXCELLENT, classify(start, "a3", best, afterCp = -10).classification)
        assertEquals(MoveClassification.GOOD, classify(start, "a3", best, afterCp = -30).classification)
        assertEquals(MoveClassification.INACCURACY, classify(start, "a3", best, afterCp = -60).classification)
        assertEquals(MoveClassification.MISTAKE, classify(start, "a3", best, afterCp = -130).classification)
        assertEquals(MoveClassification.BLUNDER, classify(start, "a3", best, afterCp = -300).classification)
    }

    @Test fun perMoveAccuracyFollowsTheLichessCurve() {
        assertEquals(100.0, MoveClassifier.moveAccuracy(0.0))
        val small = MoveClassifier.moveAccuracy(0.02)
        val large = MoveClassifier.moveAccuracy(0.30)
        assertTrue(small in 92.0..97.0, "2% loss gives $small")
        assertTrue(large < 30.0, "30% loss gives $large")
    }

    @Test fun bookMovesAndForcedMovesAreNotJudged() {
        val best = eval("e4" to 20, "d4" to 20, position = start)
        assertEquals(MoveClassification.BOOK, classify(start, "d4", best, afterCp = 15, book = true, ply = 0).classification)
        // Outside the book the same kind of move is judged normally (a3 is not one of the engine lines).
        assertEquals(MoveClassification.INACCURACY, classify(start, "a3", best, afterCp = -60, book = false, ply = 0).classification)
        val only = Fen.parse("7k/8/8/8/8/8/6r1/7K w - - 0 1") // Kxg2 is the only legal move
        assertEquals(1, MoveGenerator.legalMoves(only).size)
        val forced = classify(only, "Kxg2", PositionEvaluation(Score.Centipawns(0), listOf(EngineLine(listOf(San.parse(only, "Kxg2")), Score.Centipawns(0)))), afterCp = -900)
        assertEquals(MoveClassification.BEST, forced.classification)
        assertTrue(forced.forced)
    }

    @Test fun theOnlyMoveThatHoldsIsGreat() {
        // Black threatens mate; only one defence keeps White afloat.
        val position = Fen.parse("r1b1k2r/pppp1ppp/2n5/2b1p3/2B1P2q/3P1N2/PPP2PPP/RNBQK2R w KQkq - 0 1")
        val lines = eval("Nxh4" to 250, "O-O" to -200, "Qe2" to -250, position = position)
        val move = classify(position, "Nxh4", lines, afterCp = 250)
        assertEquals(MoveClassification.GREAT, move.classification)
    }

    @Test fun aSoundPieceSacrificeIsBrilliantButAMereBestMoveIsNot() {
        val position = Fen.parse("4k3/8/8/4N3/8/8/8/4K3 w - - 0 1")
        val lines = eval("Nf7" to 60, "Nd3" to 55, "Kd2" to 50, position = position)
        val sacrifice = classify(position, "Nf7", lines, afterCp = 60)
        assertEquals(3, sacrifice.sacrificedMaterial)
        assertEquals(MoveClassification.BRILLIANT, sacrifice.classification)

        val quiet = eval("Nd3" to 60, "Nf7" to 55, "Kd2" to 50, position = position)
        assertEquals(MoveClassification.BEST, classify(position, "Nd3", quiet, afterCp = 60).classification)
    }

    @Test fun throwingAwayTheOpponentsGiftIsAMiss() {
        val position = start
        val previous = classify(position, "h3", eval("e4" to 0, "d4" to 0, position = position), afterCp = -300)
        assertEquals(MoveClassification.BLUNDER, previous.classification)
        val reply = MoveGenerator.applyLegalMove(position, San.parse(position, "h3"))
        val opportunity = eval("e5" to 300, "d5" to 280, "Nf6" to 250, position = reply)
        val missed = classify(reply, "h6", opportunity, afterCp = 0, previous = previous.copy(mover = Color.WHITE))
        assertEquals(MoveClassification.MISS, missed.classification)
    }
}

class ReviewSessionTest {
    private val book = OpeningBook.bundled()

    private fun moves(vararg sans: String): Pair<Position, List<Move>> {
        var position = Position.initial()
        val list = ArrayList<Move>()
        for (san in sans) {
            val move = San.parse(position, san)
            list += move
            position = MoveGenerator.applyLegalMove(position, move)
        }
        return Position.initial() to list
    }

    private fun runToEnd(session: ReviewSession, limit: Int = 1_000): Int {
        var requests = 0
        while (true) {
            val request = session.nextRequest() ?: break
            session.accept(request.index, TinyEngine.evaluate(request.position, request.budget.multiPv), request.pass)
            requests += 1
            check(requests < limit) { "review did not converge" }
        }
        return requests
    }

    @Test fun aFullReviewClassifiesEveryMoveAndSummarisesBothSides() {
        val (start, list) = moves("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#")
        val session = ReviewSession(start, list, ReviewSettings(ReviewPreset.BALANCED), book)
        runToEnd(session)
        assertTrue(session.isComplete)
        val reviewed = session.reviewedMoves()
        assertEquals(list.size, reviewed.size)
        assertEquals(MoveClassification.BLUNDER, reviewed[5].classification, "Nf6 allows mate")
        assertEquals(Score.Mate(-1), reviewed[5].scoreAfter)
        assertTrue(reviewed[6].classification == MoveClassification.BEST || reviewed[6].classification == MoveClassification.GREAT)
        assertEquals(1.0, reviewed[6].whitePointsAfter)
        val explanation = MoveExplanations.explain(reviewed[5], session.positions[5], session.positions[6])
        assertTrue(explanation.detail.contains("forced mate in 1"), explanation.detail)

        val summary = session.summary()
        assertEquals(list.size + 1, summary.graph.size)
        assertTrue(summary.keyMoments.contains(5))
        val white = summary.white.accuracy!!
        val black = summary.black.accuracy!!
        assertTrue(white > black, "white $white vs black $black")
        assertEquals(1, summary.black.counts[MoveClassification.BLUNDER])
    }

    @Test fun borderlineAndCriticalMovesGetADeeperSecondPass() {
        val (start, list) = moves("e4", "e5", "Qh5", "Nc6", "Bc4", "Nf6", "Qxf7#")
        val balanced = ReviewSession(start, list, ReviewSettings(ReviewPreset.BALANCED), book)
        val fast = ReviewSession(start, list, ReviewSettings(ReviewPreset.FAST), book)
        val balancedRequests = runToEnd(balanced)
        val fastRequests = runToEnd(fast)
        assertTrue(balancedRequests > fastRequests, "balanced $balancedRequests, fast $fastRequests")
        assertTrue(balanced.reviewedMoves()[5].rechecked, "the blunder was re-searched")
        assertFalse(fast.reviewedMoves()[5].rechecked)
        val first = balanced.budgetFor(5, pass = 1)
        val second = balanced.budgetFor(5, pass = 2)
        assertTrue(second.timeMillis!! > first.timeMillis!!)
    }

    @Test fun anInterruptedReviewResumesWithoutRepeatingWork() {
        val (start, list) = moves("d4", "d5", "c4", "e6", "Nc3", "Nf6", "Bg5", "Be7")
        val full = ReviewSession(start, list, ReviewSettings(ReviewPreset.FAST), book)
        val fullRequests = runToEnd(full)

        val first = ReviewSession(start, list, ReviewSettings(ReviewPreset.FAST), book)
        repeat(4) {
            val request = first.nextRequest()!!
            first.accept(request.index, TinyEngine.evaluate(request.position, request.budget.multiPv), request.pass)
        }
        val saved = first.evaluations()
        val resumed = ReviewSession(start, list, ReviewSettings(ReviewPreset.FAST), book)
        saved.forEach { (index, value) -> resumed.restore(index, value.first, value.second) }
        val remaining = runToEnd(resumed)
        assertEquals(fullRequests, 4 + remaining)
        assertEquals(full.reviewedMoves().map { it.classification }, resumed.reviewedMoves().map { it.classification })
        assertTrue(resumed.reviewedMoves().take(4).all { it.book || it.classification == MoveClassification.BEST })
    }

    @Test fun forcedPositionsGetATinyBudgetAndTacticalOnesMore() {
        val start = Fen.parse("7k/8/8/8/8/8/6r1/7K w - - 0 1")
        val session = ReviewSession(start, listOf(Move.parseUci("h1g2")), ReviewSettings(ReviewPreset.BALANCED))
        val forced = session.budgetFor(0, 1)
        assertEquals(1, forced.multiPv)
        assertTrue(forced.timeMillis!! <= 100L)
        val quietStart = Position.initial()
        val quiet = ReviewSession(quietStart, listOf(Move.parseUci("e2e4")), ReviewSettings(ReviewPreset.BALANCED)).budgetFor(0, 1)
        assertTrue(quiet.timeMillis!! in 300L..600L)
        assertEquals(3, quiet.multiPv)
    }
}

class SummaryModelsTest {
    @Test fun phasesAdvanceAndNeverGoBack() {
        val positions = play("e4", "e5", "Nf3", "Nc6")
        assertEquals(List(positions.size) { GamePhase.OPENING }, PhaseDetector.phases(positions))
        val endgame = Fen.parse("4k3/4p3/8/8/8/8/4P3/R3K3 w - - 0 1")
        assertEquals(listOf(GamePhase.ENDGAME), PhaseDetector.phases(listOf(endgame)))
    }

    @Test fun accuracyPunishesAFewBadMovesThroughTheHarmonicMean() {
        fun move(ply: Int, accuracy: Double) = ReviewedMove(
            ply = ply, mover = if (ply % 2 == 0) Color.WHITE else Color.BLACK,
            move = Move.parseUci("e2e4"), san = "e4", bestMove = null, bestSan = null,
            scoreBefore = Score.DRAW, scoreAfter = Score.DRAW, pointsBefore = 0.5, pointsAfter = 0.5,
            expectedPointsLoss = 0.0, classification = MoveClassification.BEST, accuracy = accuracy,
            phase = GamePhase.MIDDLEGAME, forced = false, book = false, bestLine = emptyList(),
            sacrificedMaterial = 0, depth = null, nodes = null, nodesPerSecond = null,
            whitePointsAfter = 0.5, rechecked = false,
        )
        val points = List(21) { 0.5 }
        val clean = GameAccuracy.perSide(List(20) { move(it, 100.0) }, points)
        assertEquals(100.0, clean[Color.WHITE]!!, 1e-9)
        val oneBlunder = GameAccuracy.perSide(List(20) { move(it, if (it == 4) 5.0 else 100.0) }, points)
        assertTrue(oneBlunder[Color.WHITE]!! < 80.0, "one blunder gives ${oneBlunder[Color.WHITE]}")
        assertEquals(100.0, oneBlunder[Color.BLACK]!!, 1e-9)
    }

    @Test fun gameRatingRisesWithAccuracyAndShrinksShortGamesTowardsTheMiddle() {
        val low = GameRatingModel.estimate(60.0, 30)!!
        val mid = GameRatingModel.estimate(80.0, 30)!!
        val high = GameRatingModel.estimate(95.0, 30)!!
        assertTrue(low < mid && mid < high, "$low < $mid < $high")
        val short = GameRatingModel.estimate(95.0, 3)!!
        assertTrue(short < high && short > 1_500, "short game $short")
        assertNull(GameRatingModel.estimate(null, 10))
        assertEquals(0, GameRatingModel.estimate(99.0, 40)!! % 10)
    }

    @Test fun scoresFormatForHumans() {
        assertEquals("+1.3", MoveClassifier.formatScore(Score.Centipawns(130)))
        assertEquals("-0.4", MoveClassifier.formatScore(Score.Centipawns(-40)))
        assertEquals("M3", MoveClassifier.formatScore(Score.Mate(3)))
        assertEquals("-M2", MoveClassifier.formatScore(Score.Mate(-2)))
        assertEquals("#", MoveClassifier.formatScore(Score.DeliveredMate))
        assertNotNull(ExpectedPoints.pawns(Score.Centipawns(250)))
    }
}

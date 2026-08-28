package dev.lumenchess.board

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardMotionTest {
    @Test
    fun `grounded precision tokens remain frozen`() {
        assertEquals(70, GroundedPrecisionBoardMotion.pickupDurationMillis)
        assertEquals(90, GroundedPrecisionBoardMotion.legalDropDurationMillis)
        assertEquals(120, GroundedPrecisionBoardMotion.illegalDropDurationMillis)
        assertEquals(145, GroundedPrecisionBoardMotion.humanMoveDurationMillis)
        assertEquals(155, GroundedPrecisionBoardMotion.engineMoveDurationMillis)
        assertEquals(110, GroundedPrecisionBoardMotion.premoveDurationMillis)
        assertEquals(55, GroundedPrecisionBoardMotion.captureFadeDurationMillis)
        assertEquals(1.04f, GroundedPrecisionBoardMotion.pickupScale, 0.0001f)
        assertEquals(-2f, GroundedPrecisionBoardMotion.pickupLiftDp, 0.0001f)
        assertEquals(.20f, GroundedPrecisionBoardMotion.heldShadowAlpha, 0.0001f)
        assertEquals(1.3f, GroundedPrecisionBoardMotion.heldShadowBlurDp, 0.0001f)
        assertEquals(1.5f, GroundedPrecisionBoardMotion.heldShadowOffsetDp, 0.0001f)
    }

    @Test
    fun `illegal return keeps every held property continuous until rest`() {
        val start = GroundedPrecisionBoardMotion.dragVisuals(1f)
        val eightyPercent = GroundedPrecisionBoardMotion.dragVisuals(.20f)
        val end = GroundedPrecisionBoardMotion.dragVisuals(0f)

        assertEquals(1.04f, start.scale, 0.0001f)
        assertTrue(eightyPercent.scale > 1f)
        assertTrue(eightyPercent.liftDp < 0f)
        assertTrue(eightyPercent.shadowAlpha > 0f)
        assertTrue(eightyPercent.shadowOffsetDp > 0f)
        assertEquals(1f, end.scale, 0.0001f)
        assertEquals(0f, end.liftDp, 0.0001f)
        assertEquals(0f, end.shadowAlpha, 0.0001f)
        assertEquals(0f, end.shadowOffsetDp, 0.0001f)
    }

    @Test
    fun `ordinary human engine and premove transitions use their own durations`() {
        val before = Fen.parse("7k/8/8/8/8/8/4P3/7K w - - 0 1")
        val move = Move.parseUci("e2e4")
        val after = MoveGenerator.applyLegalMove(before, move)

        val human = BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true)
        val engine = BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.ENGINE, true)
        val premove = BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.PREMOVE, true)

        assertEquals(145, assertInstanceOf(BoardMotionPlan.Travel::class.java, human).durationMillis)
        assertEquals(155, assertInstanceOf(BoardMotionPlan.Travel::class.java, engine).durationMillis)
        assertEquals(110, assertInstanceOf(BoardMotionPlan.Travel::class.java, premove).durationMillis)
    }

    @Test
    fun `standard castling uses atomic fallback`() {
        val before = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val move = Move.parseUci("e1g1")
        val after = MoveGenerator.applyLegalMove(before, move)

        assertEquals(
            BoardMotionPlan.Atomic,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )
    }

    @Test
    fun `chess960 castling is atomic when king or rook begins on destination`() {
        val stationaryKing = Fen.parse("7k/8/8/8/8/8/8/R1K5 w A - 0 1", Variant.CHESS960)
        val stationaryKingMove = Move.parseUci("c1a1")
        val stationaryRook = Fen.parse("7k/8/8/8/8/8/8/4KR2 w F - 0 1", Variant.CHESS960)
        val stationaryRookMove = Move.parseUci("e1f1")

        assertEquals(
            BoardMotionPlan.Atomic,
            BoardMotionPlanner.plan(
                stationaryKing,
                MoveGenerator.applyLegalMove(stationaryKing, stationaryKingMove),
                stationaryKingMove,
                BoardMovePresentation.HUMAN_TAP,
                true,
            ),
        )
        assertEquals(
            BoardMotionPlan.Atomic,
            BoardMotionPlanner.plan(
                stationaryRook,
                MoveGenerator.applyLegalMove(stationaryRook, stationaryRookMove),
                stationaryRookMove,
                BoardMovePresentation.HUMAN_TAP,
                true,
            ),
        )
    }

    @Test
    fun `ordinary king move is not misclassified as castling`() {
        val before = Fen.parse("7k/8/8/8/8/8/8/4K3 w - - 0 1")
        val move = Move.parseUci("e1f1")
        val after = MoveGenerator.applyLegalMove(before, move)

        assertInstanceOf(
            BoardMotionPlan.Travel::class.java,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )
    }

    @Test
    fun `animation disabled snaps directly to authoritative state`() {
        val before = Fen.parse("7k/8/8/8/8/8/4P3/7K w - - 0 1")
        val move = Move.parseUci("e2e4")
        val after = MoveGenerator.applyLegalMove(before, move)

        assertEquals(
            BoardMotionPlan.Atomic,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, false),
        )
    }

    @Test
    fun `capture planning records victim square without changing attacker motion`() {
        val before = Fen.parse("7k/8/8/3p4/4P3/8/8/7K w - - 0 1")
        val move = Move.parseUci("e4d5")
        val after = MoveGenerator.applyLegalMove(before, move)
        val plan = assertInstanceOf(
            BoardMotionPlan.Travel::class.java,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )

        assertEquals(Square.parse("d5"), plan.capturedSquare)
        assertEquals(GroundedPrecisionBoardMotion.captureFadeDurationMillis, plan.captureFadeDurationMillis)
    }

    @Test
    fun `new revision or orientation cancels stale presentation`() {
        val active = BoardMotionIdentity(12L, ChessboardOrientation.WHITE)
        assertFalse(active.isStaleAgainst(BoardMotionIdentity(12L, ChessboardOrientation.WHITE)))
        assertTrue(active.isStaleAgainst(BoardMotionIdentity(13L, ChessboardOrientation.WHITE)))
        assertTrue(active.isStaleAgainst(BoardMotionIdentity(12L, ChessboardOrientation.BLACK)))
    }

    @Test
    fun `runtime projection classification distinguishes human engine and premove`() {
        assertEquals(
            BoardMovePresentation.HUMAN_TAP,
            BoardMovePresentationClassifier.classify(revisionDelta = 1L, lastMoverIsHuman = true),
        )
        assertEquals(
            BoardMovePresentation.ENGINE,
            BoardMovePresentationClassifier.classify(revisionDelta = 1L, lastMoverIsHuman = false),
        )
        assertEquals(
            BoardMovePresentation.PREMOVE,
            BoardMovePresentationClassifier.classify(revisionDelta = 2L, lastMoverIsHuman = true),
        )
    }
}

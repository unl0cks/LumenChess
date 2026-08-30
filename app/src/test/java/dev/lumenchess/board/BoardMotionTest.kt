package dev.lumenchess.board

import dev.lumenchess.core.chess.Color
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
    fun `standard castling creates concurrent deterministic king and rook legs`() {
        val before = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val move = Move.parseUci("e1g1")
        val after = MoveGenerator.applyLegalMove(before, move)

        val plan = assertInstanceOf(
            BoardMotionPlan.Castling::class.java,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )

        assertEquals(165, plan.durationMillis)
        assertEquals(Square.parse("e1"), plan.king.from)
        assertEquals(Square.parse("g1"), plan.king.to)
        assertEquals(Square.parse("h1"), plan.rook.from)
        assertEquals(Square.parse("f1"), plan.rook.to)
        assertTrue(plan.king.zIndex > plan.rook.zIndex)
        assertEquals(setOf(Square.parse("g1"), Square.parse("f1")), plan.suppressedSquares)
    }

    @Test
    fun `all four standard castling moves create two concurrent legs`() {
        listOf(
            Triple("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1", Color.WHITE),
            Triple("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1c1", Color.WHITE),
            Triple("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "e8g8", Color.BLACK),
            Triple("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1", "e8c8", Color.BLACK),
        ).forEach { (fen, uci, color) ->
            val before = Fen.parse(fen)
            val move = Move.parseUci(uci)
            val plan = assertInstanceOf(
                BoardMotionPlan.Castling::class.java,
                BoardMotionPlanner.plan(
                    before,
                    MoveGenerator.applyLegalMove(before, move),
                    move,
                    BoardMovePresentation.HUMAN_TAP,
                    true,
                ),
            )

            assertEquals(color, plan.color)
            assertFalse(plan.king.isStatic)
            assertFalse(plan.rook.isStatic)
            assertEquals(165, plan.durationMillis)
        }
    }

    @Test
    fun `ordinary Chess960 castling derives the rook source from castling rights`() {
        val before = Fen.parse("4k3/8/8/8/8/8/8/RK2R3 w EA - 0 1", Variant.CHESS960)
        val move = Move.parseUci("b1e1")
        val plan = assertInstanceOf(
            BoardMotionPlan.Castling::class.java,
            BoardMotionPlanner.plan(
                before,
                MoveGenerator.applyLegalMove(before, move),
                move,
                BoardMovePresentation.HUMAN_TAP,
                true,
            ),
        )

        assertEquals(Square.parse("b1"), plan.king.from)
        assertEquals(Square.parse("g1"), plan.king.to)
        assertEquals(Square.parse("e1"), plan.rook.from)
        assertEquals(Square.parse("f1"), plan.rook.to)
    }

    @Test
    fun `chess960 castling no longer uses atomic fallback when one member is stationary`() {
        val stationaryKing = Fen.parse("4k3/8/8/8/8/8/8/6KR w H - 0 1", Variant.CHESS960)
        val stationaryKingMove = Move.parseUci("g1h1")
        val stationaryRook = Fen.parse("7k/8/8/8/8/8/8/4KR2 w F - 0 1", Variant.CHESS960)
        val stationaryRookMove = Move.parseUci("e1f1")

        val kingStatic = assertInstanceOf(
            BoardMotionPlan.Castling::class.java,
            BoardMotionPlanner.plan(
                stationaryKing,
                MoveGenerator.applyLegalMove(stationaryKing, stationaryKingMove),
                stationaryKingMove,
                BoardMovePresentation.HUMAN_TAP,
                true,
            ),
        )
        val rookStatic = assertInstanceOf(
            BoardMotionPlan.Castling::class.java,
            BoardMotionPlanner.plan(
                stationaryRook,
                MoveGenerator.applyLegalMove(stationaryRook, stationaryRookMove),
                stationaryRookMove,
                BoardMovePresentation.HUMAN_TAP,
                true,
            ),
        )

        assertTrue(kingStatic.king.isStatic)
        assertFalse(kingStatic.rook.isStatic)
        assertEquals(setOf(Square.parse("f1")), kingStatic.suppressedSquares)
        assertFalse(rookStatic.king.isStatic)
        assertTrue(rookStatic.rook.isStatic)
        assertEquals(setOf(Square.parse("g1")), rookStatic.suppressedSquares)
    }

    @Test
    fun `chess960 crossing plan keeps explicit identities and king above rook`() {
        val before = Fen.parse("7k/8/8/8/8/8/8/2RK4 w C - 0 1", Variant.CHESS960)
        val move = Move.parseUci("d1c1")
        val after = MoveGenerator.applyLegalMove(before, move)
        val plan = assertInstanceOf(
            BoardMotionPlan.Castling::class.java,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )

        assertEquals(Square.parse("d1"), plan.king.from)
        assertEquals(Square.parse("c1"), plan.king.to)
        assertEquals(Square.parse("c1"), plan.rook.from)
        assertEquals(Square.parse("d1"), plan.rook.to)
        assertTrue(plan.king.zIndex > plan.rook.zIndex)
        assertEquals(setOf(Square.parse("c1"), Square.parse("d1")), plan.suppressedSquares)
    }

    @Test
    fun `promotion travel preserves the pawn rather than traveling the canonical promoted piece`() {
        val before = Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1")
        val move = Move.parseUci("a7a8q")
        val after = MoveGenerator.applyLegalMove(before, move)
        val plan = assertInstanceOf(
            BoardMotionPlan.Travel::class.java,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )

        assertEquals(before[move.from], plan.piece)
        assertEquals(145, plan.durationMillis)
        assertEquals(before[move.from], plan.promotion?.outgoingPiece)
        assertEquals(after[move.to], plan.promotion?.promotedPiece)
        assertEquals(80, plan.promotion?.durationMillis)
        assertEquals(.96f, plan.promotion?.initialScale)
    }

    @Test
    fun `capture promotion keeps the real victim square while the pawn travels`() {
        val before = Fen.parse("4k2r/6P1/8/8/8/8/8/4K3 w - - 0 1")
        val move = Move.parseUci("g7h8n")
        val after = MoveGenerator.applyLegalMove(before, move)
        val plan = assertInstanceOf(
            BoardMotionPlan.Travel::class.java,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )

        assertEquals(before[move.from], plan.piece)
        assertEquals(Square.parse("h8"), plan.capturedSquare)
        assertEquals(before[Square.parse("h8")], plan.capturedPiece)
        assertEquals(after[move.to], plan.promotion?.promotedPiece)
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

        val castleBefore = Fen.parse("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        val castleMove = Move.parseUci("e1g1")
        assertEquals(
            BoardMotionPlan.Atomic,
            BoardMotionPlanner.plan(
                castleBefore,
                MoveGenerator.applyLegalMove(castleBefore, castleMove),
                castleMove,
                BoardMovePresentation.HUMAN_TAP,
                false,
            ),
        )

        val promotionBefore = Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1")
        val promotionMove = Move.parseUci("a7a8q")
        assertEquals(
            BoardMotionPlan.Atomic,
            BoardMotionPlanner.plan(
                promotionBefore,
                MoveGenerator.applyLegalMove(promotionBefore, promotionMove),
                promotionMove,
                BoardMovePresentation.HUMAN_TAP,
                false,
            ),
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
    fun `en passant still fades the victim on its original square`() {
        val before = Fen.parse("7k/8/8/3pP3/8/8/8/7K w - d6 0 1")
        val move = Move.parseUci("e5d6")
        val after = MoveGenerator.applyLegalMove(before, move)
        val plan = assertInstanceOf(
            BoardMotionPlan.Travel::class.java,
            BoardMotionPlanner.plan(before, after, move, BoardMovePresentation.HUMAN_TAP, true),
        )

        assertEquals(Square.parse("d5"), plan.capturedSquare)
        assertEquals(before[Square.parse("d5")], plan.capturedPiece)
        assertEquals(before[Square.parse("e5")], plan.piece)
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

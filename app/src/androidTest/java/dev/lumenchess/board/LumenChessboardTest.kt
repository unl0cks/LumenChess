package dev.lumenchess.board

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LumenChessboardTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun tapInputEmitsOnlyCoreLegalMove() {
        var emitted: Move? = null
        composeRule.setContent { LumenTheme { LumenChessboard(Position.initial(), { emitted = it }) } }
        composeRule.onNodeWithTag("square-e2").performClick(); composeRule.onNodeWithTag("square-e5").performClick()
        composeRule.runOnIdle { assertNull(emitted) }
        composeRule.onNodeWithTag("square-e4").performClick()
        composeRule.runOnIdle { assertEquals(Move.parseUci("e2e4"), emitted) }
    }

    @Test fun dragInputEmitsSameLegalMoveAsTapInput() {
        var emitted: Move? = null
        composeRule.setContent { LumenTheme { LumenChessboard(Position.initial(), { emitted = it }) } }
        composeRule.onNodeWithTag(CHESSBOARD_TEST_TAG).performTouchInput {
            val cell = width / 8f
            swipe(Offset(cell * 4.5f, cell * 6.5f), Offset(cell * 4.5f, cell * 4.5f), 200)
        }
        composeRule.runOnIdle { assertEquals(Move.parseUci("e2e4"), emitted) }
    }

    @Test fun blackOrientationActuallyFlipsBoardGeometry() {
        composeRule.setContent { LumenTheme { LumenChessboard(Position.initial(), {}, orientation = ChessboardOrientation.BLACK) } }
        val a1 = composeRule.onNodeWithTag("square-a1").fetchSemanticsNode().boundsInRoot.center
        val h8 = composeRule.onNodeWithTag("square-h8").fetchSemanticsNode().boundsInRoot.center
        assertTrue("a1 must be right of h8 when Black is at the bottom", a1.x > h8.x)
        assertTrue("a1 must be above h8 when Black is at the bottom", a1.y < h8.y)
    }

    @Test fun chess960VisualCastleDestinationResolvesToCoreCastlingMove() {
        val position = Fen.parse("7k/8/8/8/8/8/8/1R2K3 w B - 0 1", Variant.CHESS960)
        var emitted: Move? = null
        composeRule.setContent { LumenTheme { LumenChessboard(position, { emitted = it }) } }
        composeRule.onNodeWithTag("square-e1").performClick(); composeRule.onNodeWithTag("square-c1").performClick()
        composeRule.runOnIdle { assertEquals("UI castling to c1 must emit the Chess960 core encoding king-to-rook-origin", Move.parseUci("e1b1"), emitted) }
    }

    @Test fun chess960StationaryKingCastleCanBeEnteredViaRookOrigin() {
        val position = Fen.parse("7k/8/8/8/8/8/8/R1K5 w A - 0 1", Variant.CHESS960)
        var emitted: Move? = null
        composeRule.setContent { LumenTheme { LumenChessboard(position, { emitted = it }) } }
        composeRule.onNodeWithTag("square-c1").performClick(); composeRule.onNodeWithTag("square-a1").performClick()
        composeRule.runOnIdle { assertEquals(Move.parseUci("c1a1"), emitted) }
    }

    @Test fun alwaysAskPromotionDefersMoveUntilChoice() {
        val position = Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1")
        var emitted: Move? = null
        composeRule.setContent { LumenTheme { LumenChessboard(position, { emitted = it }, input = ChessboardInput(promotionPolicy = PromotionPolicy.ALWAYS_ASK)) } }
        composeRule.onNodeWithTag("square-a7").performClick(); composeRule.onNodeWithTag("square-a8").performClick()
        composeRule.runOnIdle { assertNull(emitted) }
        composeRule.onNodeWithTag("promotion-choice-knight").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(Move(Square.parse("a7"), Square.parse("a8"), PieceType.KNIGHT), emitted) }
    }

    @Test fun autoQueenPromotionEmitsQueenWithoutPicker() {
        val position = Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1")
        var emitted: Move? = null
        composeRule.setContent { LumenTheme { LumenChessboard(position, { emitted = it }, input = ChessboardInput(promotionPolicy = PromotionPolicy.AUTO_QUEEN)) } }
        composeRule.onNodeWithTag("square-a7").performClick(); composeRule.onNodeWithTag("square-a8").performClick()
        composeRule.runOnIdle { assertEquals(Move(Square.parse("a7"), Square.parse("a8"), PieceType.QUEEN), emitted) }
    }

    @Test fun squareSemanticsExposePieceAndInteractionState() {
        composeRule.setContent {
            LumenTheme {
                LumenChessboard(
                    position = Position.initial(),
                    highlights = ChessboardHighlights(lastMove = Move.parseUci("e2e4"), premoveSquares = setOf(Square.parse("g1"), Square.parse("f3"))),
                    arrows = listOf(ChessboardArrow(Square.parse("d1"), Square.parse("h5"))), onMove = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("e2, White pawn").assertIsDisplayed().assertHasClickAction()
        val e4State = composeRule.onNodeWithTag("square-e4").fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }
        val g1State = composeRule.onNodeWithTag("square-g1").fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }
        assertTrue(e4State.contains("last move")); assertTrue(g1State.contains("premove"))
    }

    @Test fun selectingPieceExposesCoreLegalAndCaptureDestinations() {
        val position = Fen.parse("7k/8/8/3p4/4P3/8/8/7K w - - 0 1")
        composeRule.setContent { LumenTheme { LumenChessboard(position, {}) } }
        composeRule.onNodeWithTag("square-e4").performClick()
        val e5State = composeRule.onNodeWithTag("square-e5").fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }
        val d5State = composeRule.onNodeWithTag("square-d5").fetchSemanticsNode().config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }
        assertTrue(e5State.contains("legal move")); assertTrue(d5State.contains("capture"))
    }

    @Test fun inputCanDisableTapWithoutDisablingBoardSemantics() {
        var emitted: Move? = null
        composeRule.setContent { LumenTheme { LumenChessboard(Position.initial(), { emitted = it }, input = ChessboardInput(tapEnabled = false, dragEnabled = true)) } }
        composeRule.onNodeWithTag("square-e2").assertHasNoClickAction()
        composeRule.runOnIdle { assertNull(emitted) }
        composeRule.onNodeWithContentDescription("e2, White pawn").assertIsDisplayed()
    }

    @Test fun changingPieceSetChangesStyleWithoutChangingMoveInput() {
        val selectedSet = mutableStateOf<PieceSet>(LumenVectorPieceSet)
        val emitted = mutableListOf<Move>()
        composeRule.setContent {
            LumenTheme {
                LumenChessboard(position = Position.initial(), onMove = emitted::add, pieceSet = selectedSet.value)
            }
        }
        composeRule.onNodeWithTag("piece-e2-lumen-vector").assertIsDisplayed()
        composeRule.onNodeWithTag("square-e2").performClick(); composeRule.onNodeWithTag("square-e4").performClick()
        composeRule.runOnIdle { selectedSet.value = LumenOutlinePieceSet }
        composeRule.onNodeWithTag("piece-e2-lumen-outline").assertIsDisplayed()
        composeRule.onNodeWithTag("square-e2").performClick(); composeRule.onNodeWithTag("square-e4").performClick()
        composeRule.runOnIdle { assertEquals(listOf(Move.parseUci("e2e4"), Move.parseUci("e2e4")), emitted) }
    }
}

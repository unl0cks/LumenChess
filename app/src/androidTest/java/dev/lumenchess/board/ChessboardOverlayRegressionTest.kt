package dev.lumenchess.board

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChessboardOverlayRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exactOrdinaryKingMoveWinsOverAmbiguousChess960CastleTarget() {
        val position = Fen.parse(
            "7k/8/8/8/8/8/8/RK6 w A - 0 1",
            Variant.CHESS960,
        )
        var emitted: Move? = null

        composeRule.setContent {
            LumenTheme {
                LumenChessboard(position = position, onMove = { emitted = it })
            }
        }

        composeRule.onNodeWithTag("square-b1").performClick()
        composeRule.onNodeWithTag("square-c1").performClick()

        composeRule.runOnIdle {
            assertEquals(
                "An exact ordinary legal move must not be silently reinterpreted as castling",
                Move.parseUci("b1c1"),
                emitted,
            )
        }
    }

    @Test
    fun checkHighlightAndArrowLayerArePresent() {
        val position = Fen.parse("7k/7R/8/8/8/8/8/K7 b - - 0 1")

        composeRule.setContent {
            LumenTheme {
                LumenChessboard(
                    position = position,
                    arrows = listOf(
                        ChessboardArrow(
                            from = Square.parse("h7"),
                            to = Square.parse("h8"),
                            style = ChessboardArrowStyle.WARNING,
                        ),
                    ),
                    onMove = {},
                )
            }
        }

        val state = composeRule.onNodeWithTag("square-h8")
            .fetchSemanticsNode().config.getOrElse(SemanticsProperties.StateDescription) { "" }
        assertTrue(state.contains("check"))
        composeRule.onNodeWithTag(CHESSBOARD_ARROWS_TEST_TAG).assertIsDisplayed()
    }
}

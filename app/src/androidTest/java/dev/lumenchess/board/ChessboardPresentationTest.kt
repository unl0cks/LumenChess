package dev.lumenchess.board

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.lumenchess.core.chess.Position
import dev.lumenchess.design.LumenTheme
import org.junit.Rule
import org.junit.Test

class ChessboardPresentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun providedPieceStyleReachesBoardWithoutChangingBoardCallSite() {
        composeRule.setContent {
            LumenTheme {
                ProvideChessboardPresentationStyle(
                    ChessboardPresentationStyle(pieceSet = LumenOutlinePieceSet),
                ) {
                    LumenChessboard(position = Position.initial(), onMove = {})
                }
            }
        }

        composeRule.onNodeWithTag("square-e2").assertIsDisplayed()
        composeRule.onNodeWithTag("piece-e2-lumen-outline").fetchSemanticsNode()
    }
}

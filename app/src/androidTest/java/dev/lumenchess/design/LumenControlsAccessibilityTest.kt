package dev.lumenchess.design

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class LumenControlsAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryButton_hasAccessibleClickSemanticsAndMinimumTouchTarget() {
        composeRule.setContent {
            LumenTheme {
                LumenButton(label = "Start Game", onClick = {})
            }
        }

        composeRule.onNodeWithText("Start Game")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}

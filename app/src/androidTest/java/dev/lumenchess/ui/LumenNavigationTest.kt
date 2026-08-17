package dev.lumenchess.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.lumenchess.MainActivity
import org.junit.Rule
import org.junit.Test

class LumenNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationUsesIconLabelTabsAndIntentionalFuturePreview() {
        composeRule.onNodeWithTag("main-tab-play").assertIsDisplayed()
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        composeRule.onNodeWithText("Arena").assertIsDisplayed()
        composeRule.onNodeWithText("Preview · not available in this build").assertIsDisplayed()
        composeRule.onAllNodesWithText("Coming in a later milestone").assertCountEquals(0)
        composeRule.onNodeWithTag("main-tab-play").performClick()
        composeRule.onNodeWithText("Human vs Engine").assertIsDisplayed()
    }
}

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
    fun navigationUsesCompactIconLabelTabsAndIntentionalProductPreview() {
        composeRule.onNodeWithTag("main-tab-play").assertIsDisplayed()
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        composeRule.onAllNodesWithText("Arena").assertCountEquals(2)
        composeRule.onNodeWithText("Set up engine battles and take over positions").assertIsDisplayed()
        composeRule.onAllNodesWithText("not available in this build", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Coming in a later milestone", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("main-tab-play").performClick()
        composeRule.onNodeWithText("New Game").assertIsDisplayed()
        composeRule.onAllNodesWithText("Human vs Engine").assertCountEquals(0)
    }
}

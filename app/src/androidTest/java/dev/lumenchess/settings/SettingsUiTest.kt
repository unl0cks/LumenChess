package dev.lumenchess.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.lumenchess.MainActivity
import org.junit.Rule
import org.junit.Test

class SettingsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun compactAppearancePresetAndIndividualOverridesRemainComposable() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("settings-play").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("play-settings-root").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-oled_dark").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings-board-pieces").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("board-preview").assertIsDisplayed()
        composeRule.onNodeWithTag("board-preview-board", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("customization-tab-3").performClick()
        composeRule.onNodeWithTag("customization-preset-midnight").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Midnight preset active")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("customization-tab-2").performClick()
        composeRule.onNodeWithTag("customization-background-graphite-haze").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Custom mix")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("customization-tab-0").performClick()
        composeRule.onNodeWithTag("customization-board-graphite").performScrollTo().performClick()
        composeRule.onNodeWithTag("board-preview").assertIsDisplayed()
    }
}

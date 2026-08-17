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
    fun appearancePresetAndIndividualOverridesRemainComposable() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("appearance-oled_dark").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Active appearance: OLED")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("settings-board-pieces").performClick()
        composeRule.onNodeWithTag("board-preview").assertIsDisplayed()

        composeRule.onNodeWithTag("customization-tab-presets").performClick()
        composeRule.onNodeWithTag("customization-preset-midnight").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Midnight preset active")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("customization-tab-background").performClick()
        composeRule.onNodeWithTag("customization-background-graphite-haze").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Custom mix")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("customization-tab-board").performClick()
        composeRule.onNodeWithTag("customization-board-graphite").performScrollTo().performClick()
        composeRule.onNodeWithTag("board-preview").assertIsDisplayed()
    }
}

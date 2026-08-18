package dev.lumenchess.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundsHapticsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun soundsAndHapticsExposeCompactMasterRowsAndEventDetailControls() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("settings-play").performClick()
        composeRule.onNodeWithTag("settings-sounds-haptics").performScrollTo().performClick()

        composeRule.onNodeWithTag("sounds-haptics-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback-sounds-master").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback-haptics-master").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback-import-pack").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("p5-feedback-event-move").performScrollTo().performClick()
        composeRule.onNodeWithTag("p5-feedback-event-detail").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback-preview-move").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback-import-move").assertIsDisplayed()
    }
}

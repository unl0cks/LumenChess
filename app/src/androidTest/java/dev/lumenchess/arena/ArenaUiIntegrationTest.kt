package dev.lumenchess.arena

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.lumenchess.MainActivity
import org.junit.Rule
import org.junit.Test

class ArenaUiIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun arenaTabExposesIndependentSetupAndStartsBoardFirstLive() {
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        composeRule.onNodeWithTag("arena-setup").assertIsDisplayed()
        composeRule.onNodeWithTag("arena-white-engine").assertIsDisplayed()
        composeRule.onNodeWithTag("arena-black-engine").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("arena-opening-options").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("arena-start").performScrollTo().performClick()

        composeRule.onNodeWithTag("arena-live").assertIsDisplayed()
        composeRule.onNodeWithTag("arena-board-stage").assertIsDisplayed()
        composeRule.onNodeWithTag("arena-evaluation-bar").assertIsDisplayed()
        composeRule.onNodeWithTag("arena-pause").assertIsDisplayed()
        composeRule.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
    }

    @Test
    fun playOverviewArenaHeroRoutesToTheSameSetup() {
        composeRule.onNodeWithTag("play-overview-arena").performClick()
        composeRule.onNodeWithTag("arena-setup").assertIsDisplayed()
    }

    @Test
    fun randomOpeningOffersTheDocumentedCustomHandoffDepth() {
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        composeRule.onNodeWithText("Random opening").performScrollTo().performClick()
        composeRule.onNodeWithText("Custom").performScrollTo().performClick()

        composeRule.onNodeWithText("Custom handoff (plies)").performScrollTo().assertIsDisplayed()
    }
}

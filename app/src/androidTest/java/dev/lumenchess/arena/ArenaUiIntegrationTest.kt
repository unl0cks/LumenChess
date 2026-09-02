package dev.lumenchess.arena

import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.lumenchess.MainActivity
import dev.lumenchess.runtime.RuntimeController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun manualOpeningControlsAreRuntimeOwnedAndKeepBothClocksLocked() {
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        composeRule.onNodeWithTag("arena-manual-options").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Both").performScrollTo().performClick()

        val viewModel = ViewModelProvider(composeRule.activity)[ArenaViewModel::class.java]
        composeRule.onNodeWithTag("arena-start").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.mode == ArenaScreenMode.LIVE && viewModel.uiState.value.runtime != null
        }

        val state = requireNotNull(viewModel.uiState.value.runtime)
        assertEquals(RuntimeController.HUMAN, state.controllers.white)
        assertEquals(RuntimeController.HUMAN, state.controllers.black)
        assertFalse(state.clock.running)

        composeRule.onNodeWithTag("arena-control").performClick()
        composeRule.onNodeWithText("Manual control").assertIsDisplayed()
    }
}

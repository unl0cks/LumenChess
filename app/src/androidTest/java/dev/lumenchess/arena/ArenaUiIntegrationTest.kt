package dev.lumenchess.arena

import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.lumenchess.MainActivity
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.core.chess.Color
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeTerminal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun humanCheckmateRetainsHumanPresentationSource() {
        val viewModel = startManualPosition("7k/8/5KQ1/8/8/8/8/8 w - - 0 1", ArenaManualSide.BOTH)
        composeRule.onNodeWithTag("square-g6").performClick()
        composeRule.onNodeWithTag("square-g7").performClick()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.runtime?.terminal != null }
        assertEquals(RuntimeTerminal.Checkmate(Color.WHITE), viewModel.uiState.value.runtime?.terminal)
        assertTrue(viewModel.uiState.value.lastMoveWasHuman)
    }

    @Test
    fun engineCheckmateReplacesPreviousHumanPresentationSource() {
        val viewModel = startManualPosition(
            "rnbqkbnr/pppp1ppp/8/4p3/8/5P2/PPPPP1PP/RNBQKBNR w KQkq - 0 2",
            ArenaManualSide.BOTH,
        )
        composeRule.onNodeWithTag("square-g2").performClick()
        composeRule.onNodeWithTag("square-g4").performClick()
        assertTrue(viewModel.uiState.value.lastMoveWasHuman)
        composeRule.runOnUiThread { viewModel.returnToEngine(ArenaManualSide.BLACK) }
        composeRule.waitUntil(25_000) { viewModel.uiState.value.runtime?.terminal != null }
        assertEquals(RuntimeTerminal.Checkmate(Color.BLACK), viewModel.uiState.value.runtime?.terminal)
        assertFalse(viewModel.uiState.value.lastMoveWasHuman)
    }

    private fun startManualPosition(fen: String, side: ArenaManualSide): ArenaViewModel {
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        val viewModel = ViewModelProvider(composeRule.activity)[ArenaViewModel::class.java]
        composeRule.runOnUiThread {
            viewModel.updateOpeningMode(ArenaOpeningMode.CUSTOM_FEN)
            viewModel.updateCustomFen(fen)
            viewModel.updateManualSide(side)
            viewModel.updateManualLimitMode(ArenaManualLimitMode.UNTIL_RELEASE)
            viewModel.updateEngine(Color.BLACK, PlayEngine.STOCKFISH_18)
            viewModel.updateStrengthTarget(Color.BLACK, EngineStrengthTarget.FullStrength)
            viewModel.updateStrengthModel(Color.BLACK, EngineStrengthModel.ENGINE_NATIVE)
            viewModel.startNewArena()
        }
        composeRule.waitUntil(10_000) { viewModel.uiState.value.runtime != null }
        composeRule.onNodeWithTag("arena-board-stage").assertIsDisplayed()
        return viewModel
    }
}

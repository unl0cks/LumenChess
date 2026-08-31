package dev.lumenchess.arena

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import dev.lumenchess.MainActivity
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.play.PlayEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArenaM20IntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun stockfishAndRecklessProgressUnderOneRuntimeWithoutMovingTheBoard() {
        openArena()
        val viewModel = ViewModelProvider(composeRule.activity)[ArenaViewModel::class.java]
        composeRule.runOnUiThread { viewModel.startNewArena() }
        waitForLive(viewModel)
        val initialBounds = boardBounds()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            (viewModel.uiState.value.runtime?.positionRevision?.value ?: 0L) >= 3L
        }

        val state = requireNotNull(viewModel.uiState.value.runtime)
        assertEquals(PlayEngine.STOCKFISH_18, viewModel.uiState.value.resolvedSetup?.white?.engine)
        assertEquals(PlayEngine.RECKLESS_0_9_0, viewModel.uiState.value.resolvedSetup?.black?.engine)
        assertTrue(state.positionRevision.value >= 3L)
        assertEquals(initialBounds, boardBounds())
    }

    @Test
    fun chess960AndHostRecoveryRemainPresentationOnly() {
        openArena()
        val viewModel = ViewModelProvider(composeRule.activity)[ArenaViewModel::class.java]
        composeRule.runOnUiThread {
            viewModel.updateVariant(Variant.CHESS960)
            viewModel.updateChess960Index(42)
            viewModel.startNewArena()
        }
        waitForLive(viewModel)
        val initialBounds = boardBounds()
        composeRule.waitUntil(timeoutMillis = 25_000) {
            (viewModel.uiState.value.runtime?.positionRevision?.value ?: 0L) >= 1L
        }

        composeRule.runOnUiThread { viewModel.restartEngineHostForTest(Color.BLACK) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.runtime?.engineHostAvailable == false
        }
        composeRule.waitUntil(timeoutMillis = 25_000) {
            viewModel.uiState.value.runtime?.engineHostAvailable == true
        }

        val state = requireNotNull(viewModel.uiState.value.runtime)
        assertEquals(Variant.CHESS960, state.position.variant)
        assertFalse(state.paused)
        assertTrue(state.started)
        assertEquals(initialBounds, boardBounds())
    }

    private fun waitForLive(viewModel: ArenaViewModel) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            viewModel.uiState.value.mode == ArenaScreenMode.LIVE && viewModel.uiState.value.runtime != null
        }
        composeRule.onNodeWithTag("arena-board-stage").fetchSemanticsNode()
    }

    private fun openArena() {
        composeRule.onNodeWithTag("p5-play-overview").fetchSemanticsNode()
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        composeRule.onNodeWithTag("arena-setup").fetchSemanticsNode()
    }

    private fun boardBounds(): Rect = composeRule.onNodeWithTag("arena-board-stage")
        .fetchSemanticsNode().boundsInRoot
}

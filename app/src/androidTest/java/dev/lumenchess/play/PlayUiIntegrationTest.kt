package dev.lumenchess.play

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import dev.lumenchess.MainActivity
import dev.lumenchess.board.CHESSBOARD_TEST_TAG
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayUiIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun setupShowsTypedRecklessNativeConstraintInsteadOfSendingUnsupportedOptions() {
        composeRule.onNodeWithTag(PLAY_SETUP_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Reckless 0.9.0").performScrollTo().performClick()
        composeRule.onNodeWithText("Native").performScrollTo().performClick()

        composeRule.onNodeWithText(
            "Engine native strength limiting is unavailable for this engine",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun standardSetupStartsCleanLiveScreenAndConfigurationRecreationRetainsOwner() {
        composeRule.onNodeWithText("Standard").performScrollTo().performClick()
        composeRule.onNodeWithText("White").performScrollTo().performClick()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForLiveScreen()

        composeRule.onNodeWithTag(CHESSBOARD_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("You").assertIsDisplayed()
        composeRule.onNodeWithText("Stockfish 18").assertIsDisplayed()
        composeRule.onNodeWithText("Resign").assertIsDisplayed()

        val beforeViewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
        val beforeCoordinator = requireNotNull(beforeViewModel.currentCoordinatorForTest())
        val beforeRevision = beforeCoordinator.state.positionRevision

        composeRule.activityRule.scenario.recreate()
        waitForLiveScreen()

        val afterViewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
        val afterCoordinator = requireNotNull(afterViewModel.currentCoordinatorForTest())
        assertSame(beforeViewModel, afterViewModel)
        assertSame(beforeCoordinator, afterCoordinator)
        assertEquals(beforeRevision, afterCoordinator.state.positionRevision)
        assertEquals(1, composeRule.onAllNodesWithTag(PLAY_LIVE_TEST_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun boardBoundsStayStableAcrossHumanMoveEngineThinkingAndEngineResult() {
        composeRule.onNodeWithText("Standard").performScrollTo().performClick()
        composeRule.onNodeWithText("White").performScrollTo().performClick()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForLiveScreen()

        val before = boardBounds()
        val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
        val beforeMoveCount = requireNotNull(viewModel.currentCoordinatorForTest()).state.gameTree.mainline().size

        composeRule.onNodeWithTag("square-e2").performClick()
        composeRule.onNodeWithTag("square-e4").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag(PLAY_PREMOVE_OVERLAY_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        val duringEngineThinking = boardBounds()
        assertStableBounds(before, duringEngineThinking)

        composeRule.waitUntil(timeoutMillis = 12_000L) {
            val moveCount = viewModel.currentCoordinatorForTest()?.state?.gameTree?.mainline()?.size ?: beforeMoveCount
            moveCount >= beforeMoveCount + 2
        }
        val afterEngineResult = boardBounds()
        assertStableBounds(before, afterEngineResult)
    }

    @Test
    fun chess960BlackSetupStartsWithResolvedChess960Runtime() {
        composeRule.onNodeWithText("Chess960").performScrollTo().performClick()
        composeRule.onNodeWithText("Black").performScrollTo().performClick()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForLiveScreen()

        val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
        val setup = requireNotNull(viewModel.uiState.value.resolvedSetup)
        val runtime = requireNotNull(viewModel.uiState.value.runtime)
        assertEquals(Variant.CHESS960, setup.variant)
        assertEquals(Color.BLACK, setup.humanSide)
        assertEquals(Variant.CHESS960, runtime.position.variant)
        assertTrue(requireNotNull(setup.chess960Index) in 0..959)
        composeRule.onNodeWithTag(CHESSBOARD_TEST_TAG).assertIsDisplayed()
    }

    private fun boardBounds(): Rect = composeRule
        .onNodeWithTag(CHESSBOARD_TEST_TAG)
        .fetchSemanticsNode()
        .boundsInRoot

    private fun assertStableBounds(expected: Rect, actual: Rect) {
        assertTrue("board top changed: expected=${expected.top}, actual=${actual.top}", abs(expected.top - actual.top) <= 1f)
        assertTrue("board bottom changed: expected=${expected.bottom}, actual=${actual.bottom}", abs(expected.bottom - actual.bottom) <= 1f)
        assertTrue("board width changed: expected=${expected.width}, actual=${actual.width}", abs(expected.width - actual.width) <= 1f)
        assertTrue("board height changed: expected=${expected.height}, actual=${actual.height}", abs(expected.height - actual.height) <= 1f)
    }

    private fun waitForLiveScreen() {
        composeRule.waitUntil(timeoutMillis = 12_000L) {
            composeRule.onAllNodesWithTag(PLAY_LIVE_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PLAY_LIVE_TEST_TAG).assertIsDisplayed()
    }
}

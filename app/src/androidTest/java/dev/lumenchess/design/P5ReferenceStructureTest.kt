package dev.lumenchess.design

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.lumenchess.MainActivity
import dev.lumenchess.play.PLAY_LIVE_TEST_TAG
import dev.lumenchess.play.PLAY_START_TEST_TAG
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class P5ReferenceStructureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun playTabOpensCompactOverviewBeforeNewGameConfiguration() {
        composeRule.onNodeWithTag("main-tab-play").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-play-overview").assertIsDisplayed()
        composeRule.onAllNodesWithTag("play-setup").assertCountEquals(0)
        composeRule.onAllNodesWithText("Human vs Engine").assertCountEquals(0)
        composeRule.onAllNodesWithText("LUMEN PLAY").assertCountEquals(0)

        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        composeRule.onNodeWithTag("play-setup").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-setup-shell").assertIsDisplayed()
    }

    @Test
    fun playOverviewContainsReferenceActionsAndQuickStart() {
        composeRule.onNodeWithTag("p5-play-overview").assertIsDisplayed()
        composeRule.onNodeWithTag("play-overview-vs-engine").assertIsDisplayed()
        composeRule.onNodeWithTag("play-overview-arena").assertIsDisplayed()
        composeRule.onNodeWithTag("play-overview-quick-start").assertIsDisplayed()
        composeRule.onAllNodesWithText("Play vs Engine").assertCountEquals(1)
        composeRule.onAllNodesWithText("Engine Arena").assertCountEquals(1)
        composeRule.onAllNodesWithText("Quick Start").assertCountEquals(1)
    }

    @Test
    fun playSetupUsesCompactFramedReferenceHierarchy() {
        openSetup()
        val frame = composeRule.onNodeWithTag("p5-setup-shell").fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithTag("lumen-topbar-title").fetchSemanticsNode().boundsInRoot
        assertTrue("New Game header must live inside the reference setup frame", title.top >= frame.top && title.bottom <= frame.bottom)
        composeRule.onNodeWithTag("p5-setup-content").assertIsDisplayed()
    }

    @Test
    fun setupContainsReferenceControlsAndCompactModeTiles() {
        openSetup()
        composeRule.onNodeWithTag("p5-match-my-elo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("p5-inc-delay").performScrollTo().assertIsDisplayed()

        val choice = composeRule.onNodeWithTag("p5-setup-standard").fetchSemanticsNode().boundsInRoot
        val nav = composeRule.onNodeWithTag("main-tab-play").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Reference game-mode tiles should be more compact than the bottom-navigation item",
            choice.height < nav.height * 0.92f,
        )
    }

    @Test
    fun liveGameUsesCohesiveLowerFrameWithMovesInfoAndAttachedActions() {
        openSetup()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 12_000L) {
            composeRule.onAllNodesWithTag(PLAY_LIVE_TEST_TAG).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("p5-live-shell").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-lower-region").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-action-strip").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-tab-moves").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-tab-info").assertIsDisplayed()

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val shell = composeRule.onNodeWithTag("p5-live-shell").fetchSemanticsNode().boundsInRoot
        val lower = composeRule.onNodeWithTag("p5-live-lower-region").fetchSemanticsNode().boundsInRoot
        val actions = composeRule.onNodeWithTag("p5-live-action-strip").fetchSemanticsNode().boundsInRoot
        assertTrue("Live upper frame should stay compact around players and board", shell.height < root.height * 0.72f)
        assertTrue("Action strip must be attached inside the lower game frame", actions.top >= lower.top && actions.bottom <= lower.bottom)
    }

    @Test
    fun settingsRootMatchesReferenceInformationArchitecture() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("settings-category-list").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-play").assertIsDisplayed()
        composeRule.onAllNodesWithTag("settings-appearance-section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-sounds-haptics").assertCountEquals(0)
        composeRule.onAllNodesWithText("Make LumenChess yours").assertCountEquals(0)
        composeRule.onAllNodesWithText("presentation-only", substring = true).assertCountEquals(0)
    }

    @Test
    fun playSettingsOwnsAppearanceBoardPiecesAndFeedbackDestinations() {
        openPlaySettings()
        composeRule.onNodeWithTag("play-settings-root").assertIsDisplayed()
        composeRule.onNodeWithTag("play-settings-time-controls").assertIsDisplayed()
        composeRule.onNodeWithTag("play-settings-appearance").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-board-pieces").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-sounds-haptics").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-system").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-dark").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-oled_dark").assertIsDisplayed()
        composeRule.onNodeWithTag("appearance-light").assertIsDisplayed()
    }

    @Test
    fun referenceTopBarIsCenteredAndSettingsRowsStayCompact() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val title = composeRule.onNodeWithTag("lumen-topbar-title").fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag("settings-play").fetchSemanticsNode().boundsInRoot
        val nav = composeRule.onNodeWithTag("main-tab-settings").fetchSemanticsNode().boundsInRoot
        assertTrue("Reference top bar title should be horizontally centered", abs(title.center.x - root.center.x) < root.width * 0.03f)
        assertTrue("Reference settings rows should stay close to compact navigation scale", row.height < nav.height * 1.12f)
    }

    @Test
    fun boardCustomizationUsesPremiumCompactVisualRows() {
        openPlaySettings()
        composeRule.onNodeWithTag("settings-board-pieces").performClick()
        composeRule.onAllNodesWithText("Back").assertCountEquals(0)
        composeRule.onNodeWithTag("customization-back").assertIsDisplayed()
        composeRule.onNodeWithTag("customization-options-grid").assertIsDisplayed()

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val preview = composeRule.onNodeWithTag("board-preview").fetchSemanticsNode().boundsInRoot
        val first = composeRule.onNodeWithTag("customization-board-lumen-blue").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("customization-board-midnight-oled").fetchSemanticsNode().boundsInRoot
        assertTrue("Board preview should remain useful without consuming half the screen", preview.height < root.height * 0.34f)
        assertTrue("Theme choices should be dense visual list rows", first.width > root.width * 0.82f)
        assertTrue("Theme choices should stack rather than form developer-style cards", second.top > first.top + first.height * 0.70f)
        assertTrue("Visual rows should stay compact", first.height < root.height * 0.105f)
    }

    @Test
    fun soundsAndHapticsKeepsEventConfigurationBehindCompactRows() {
        openPlaySettings()
        composeRule.onNodeWithTag("settings-sounds-haptics").performClick()
        composeRule.onNodeWithTag("p5-feedback-master-panel").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-feedback-event-list").performScrollTo().assertIsDisplayed()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val moveRow = composeRule.onNodeWithTag("p5-feedback-event-move").fetchSemanticsNode().boundsInRoot
        assertTrue("Feedback event rows should stay compact", moveRow.height < root.height * 0.085f)
        composeRule.onNodeWithTag("p5-feedback-event-move").performClick()
        composeRule.onNodeWithTag("p5-feedback-event-detail").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback-preview-move").assertIsDisplayed()
        composeRule.onNodeWithTag("feedback-import-move").assertIsDisplayed()
    }

    @Test
    fun futureTabPreviewUsesProductCopyRatherThanBuildInternalCopy() {
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        composeRule.onAllNodesWithText("not available in this build", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("later milestone", substring = true).assertCountEquals(0)
    }

    @Test
    fun bottomNavigationItemBoundsRemainStableAcrossSelection() {
        val before = listOf("play", "arena", "games", "insights", "settings").associateWith { tab ->
            composeRule.onNodeWithTag("main-tab-$tab").fetchSemanticsNode().boundsInRoot
        }
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        val after = listOf("play", "arena", "games", "insights", "settings").associateWith { tab ->
            composeRule.onNodeWithTag("main-tab-$tab").fetchSemanticsNode().boundsInRoot
        }
        assertEquals(before, after)
    }

    private fun openSetup() {
        composeRule.onNodeWithTag("p5-play-overview").assertIsDisplayed()
        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        composeRule.onNodeWithTag("play-setup").assertIsDisplayed()
    }

    private fun openPlaySettings() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("settings-play").performClick()
        composeRule.onNodeWithTag("play-settings-root").assertIsDisplayed()
    }
}

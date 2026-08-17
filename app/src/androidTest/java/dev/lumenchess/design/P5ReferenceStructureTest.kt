package dev.lumenchess.design

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import dev.lumenchess.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class P5ReferenceStructureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun playSetupUsesCompactApplicationHierarchyInsteadOfMarketingHero() {
        composeRule.onNodeWithTag("main-tab-play").assertIsDisplayed()

        composeRule.onAllNodesWithText("Human vs Engine").assertCountEquals(0)
        composeRule.onAllNodesWithText("LUMEN PLAY").assertCountEquals(0)
        composeRule.onAllNodesWithText("Configure a clean offline match.").assertCountEquals(0)
        composeRule.onNodeWithTag("p5-setup-shell").assertIsDisplayed()
    }

    @Test
    fun settingsDoesNotExposeArchitectureOrMarketingCopy() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()

        composeRule.onAllNodesWithText("Make LumenChess yours").assertCountEquals(0)
        composeRule.onAllNodesWithText("presentation-only", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Chess rules", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("settings-category-list").assertIsDisplayed()
    }

    @Test
    fun boardCustomizationUsesIconBackNavigationInsteadOfTextBackCard() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("settings-board-pieces").performClick()

        composeRule.onAllNodesWithText("Back").assertCountEquals(0)
        composeRule.onNodeWithTag("customization-back").assertIsDisplayed()
    }

    @Test
    fun boardCustomizationUsesReferenceStyleVisualGridInsteadOfFullWidthList() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("settings-board-pieces").performClick()
        composeRule.onNodeWithTag("customization-options-grid").assertIsDisplayed()

        val first = composeRule.onNodeWithTag("customization-board-lumen-blue").fetchSemanticsNode().boundsInRoot
        val second = composeRule.onNodeWithTag("customization-board-midnight-oled").fetchSemanticsNode().boundsInRoot
        assertTrue("First two board choices should share a grid row", abs(first.top - second.top) < 2f)
        assertTrue("Grid choices should not be full-width list rows", first.width < composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width * 0.60f)
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
}

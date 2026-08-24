package dev.lumenchess.visual

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P5DerivativeScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitScreenshotRun() {
        val enabled = InstrumentationRegistry.getArguments().getString("p5DerivativeQa") == "true"
        assumeTrue("Derivative screenshot QA runs only from the dedicated P5 workflow step", enabled)
    }

    @Test
    fun captureDerivativeVocabularyBatch() {
        openDarkPlaySettings()
        capture("05-play-settings.png")
        capturePressedRoot("05-play-settings-press-state.png", "settings-board-pieces")

        composeRule.onNodeWithTag("settings-board-pieces").performClick()
        waitForTag("derivative-board-appearance")
        capture("06-board.png")
        capturePressedRoot("06-board-press-state.png", "customization-board-midnight-oled")

        composeRule.onNodeWithTag("customization-tab-1").performClick()
        waitForTag("customization-piece-lumen-vector")
        capture("07-pieces.png")

        composeRule.onNodeWithTag("customization-tab-2").performClick()
        waitForTag("customization-background-lumen-night")
        capture("08-background.png")

        composeRule.onNodeWithTag("customization-tab-3").performClick()
        composeRule.onNodeWithTag("customization-preset-midnight").performScrollTo().assertIsDisplayed()
        capture("09-presets.png")

        composeRule.onNodeWithTag("customization-back").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("settings-sounds-haptics").performScrollTo().performClick()
        waitForTag("derivative-feedback-screen")
        capture("10-sounds-haptics.png")
        capturePressedRoot("10-sounds-haptics-press-state.png", "p5-feedback-event-move")

        composeRule.onNodeWithTag("p5-feedback-event-move").performScrollTo().performClick()
        waitForTag("derivative-feedback-detail")
        capture("11-feedback-detail.png")
        capturePressedRoot("11-feedback-detail-press-state.png", "feedback-preview-move")

        composeRule.onNodeWithContentDescription("Navigate back").performClick()
        waitForTag("derivative-feedback-screen")
        composeRule.onNodeWithTag("sounds-haptics-back").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("play-settings-back").performClick()
        waitForTag("settings-category-list")

        composeRule.onNodeWithTag("main-tab-play").performClick()
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("play-overview-arena").performClick()
        waitForTag("derivative-arena-preview")
        capture("12-arena-preview.png")
        composeRule.onNodeWithContentDescription("Navigate back").performClick()
        waitForTag("p5-play-overview")

        composeRule.onNodeWithTag("main-tab-arena").performClick()
        waitForTag("derivative-future-preview")
        capture("13-arena-tab.png")
        composeRule.onNodeWithTag("main-tab-games").performClick()
        waitForTag("derivative-future-preview")
        capture("14-games-tab.png")
        composeRule.onNodeWithTag("main-tab-insights").performClick()
        waitForTag("derivative-future-preview")
        capture("15-insights-tab.png")
    }

    private fun openDarkPlaySettings() {
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        composeRule.waitForIdle()
        waitForTag("derivative-play-settings")
    }

    private fun capturePressedRoot(name: String, tag: String) {
        val node = composeRule.onNodeWithTag(tag)
        node.assertIsDisplayed()
        composeRule.waitForIdle()
        val rest = node.captureToImage().asAndroidBitmap()
        val previousAutoAdvance = composeRule.mainClock.autoAdvance
        composeRule.mainClock.autoAdvance = false
        var pointerDown = false
        val pressed = try {
            node.performTouchInput { down(center) }
            pointerDown = true
            composeRule.mainClock.advanceTimeBy(240L)
            composeRule.waitForIdle()
            val captured = node.captureToImage().asAndroidBitmap()
            capture(name)
            captured
        } finally {
            try {
                if (pointerDown) {
                    node.performTouchInput { cancel() }
                    composeRule.mainClock.advanceTimeBy(160L)
                }
            } finally {
                composeRule.mainClock.autoAdvance = previousAutoAdvance
                composeRule.waitForIdle()
            }
        }

        val width = minOf(rest.width, pressed.width)
        val height = minOf(rest.height, pressed.height)
        var changed = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val before = rest.getPixel(x, y)
                val after = pressed.getPixel(x, y)
                val delta = kotlin.math.abs(android.graphics.Color.red(before) - android.graphics.Color.red(after)) +
                    kotlin.math.abs(android.graphics.Color.green(before) - android.graphics.Color.green(after)) +
                    kotlin.math.abs(android.graphics.Color.blue(before) - android.graphics.Color.blue(after))
                if (delta > 18) changed += 1
            }
        }
        val ratio = changed.toFloat() / (width * height).toFloat()
        println("P5_DERIVATIVE_PRESS tag=$tag visiblyChangedRatio=$ratio")
        assertTrue("$tag REST/PRESSED must remain perceptible; ratio=$ratio", ratio > .006f)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 6_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(root, "p5-derivative-screenshots").apply { mkdirs() }
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(directory, name)).use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Failed to encode $name" }
        }
    }
}

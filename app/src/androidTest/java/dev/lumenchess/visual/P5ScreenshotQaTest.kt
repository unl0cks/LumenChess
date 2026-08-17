package dev.lumenchess.visual

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.play.PLAY_LIVE_TEST_TAG
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import dev.lumenchess.play.PLAY_START_TEST_TAG
import dev.lumenchess.play.PlayViewModel
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P5ScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitScreenshotRun() {
        val enabled = InstrumentationRegistry.getArguments().getString("p5ScreenshotQa") == "true"
        assumeTrue("P5 screenshot QA runs only from the dedicated workflow step", enabled)
    }

    @Test
    fun captureMandatoryReferenceSet() {
        waitForTag(PLAY_SETUP_TEST_TAG)
        selectAppearance("dark")
        composeRule.onNodeWithTag("main-tab-play").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
        capture("01-setup.png")

        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForTag(PLAY_LIVE_TEST_TAG, timeoutMillis = 12_000L)
        capture("02-stockfish-live.png")

        backToSetup()
        composeRule.onNodeWithText("Stockfish 18").performScrollTo().performClick()
        composeRule.onNodeWithText("Reckless 0.9.0").performClick()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForTag(PLAY_LIVE_TEST_TAG, timeoutMillis = 12_000L)
        composeRule.onNodeWithText("Reckless 0.9.0").assertIsDisplayed()
        capture("03-reckless-live.png")

        backToSetup()
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("appearance-dark").assertIsDisplayed()
        capture("04-settings.png")

        composeRule.onNodeWithTag("settings-board-pieces").performClick()
        composeRule.onNodeWithTag("board-preview").assertIsDisplayed()
        capture("05-board.png")

        composeRule.onNodeWithTag("customization-tab-1").performClick()
        composeRule.onNodeWithTag("customization-piece-lumen").assertIsDisplayed()
        capture("06-pieces.png")

        composeRule.onNodeWithTag("customization-tab-2").performClick()
        composeRule.onNodeWithTag("customization-background-lumen-night").assertIsDisplayed()
        capture("07-background.png")

        composeRule.onNodeWithTag("customization-tab-3").performClick()
        composeRule.onNodeWithTag("customization-preset-midnight").assertIsDisplayed()
        capture("08-presets.png")

        composeRule.onNodeWithTag("customization-back").performClick()
        composeRule.onNodeWithTag("settings-sounds-haptics").performScrollTo().performClick()
        composeRule.onNodeWithTag("sounds-haptics-screen").assertIsDisplayed()
        capture("09-sounds-haptics.png")

        composeRule.onNodeWithTag("sounds-haptics-back").performClick()
        composeRule.onNodeWithTag("appearance-light").performClick()
        composeRule.onNodeWithTag("appearance-light").assertIsDisplayed()
        capture("10-light.png")

        composeRule.onNodeWithTag("appearance-oled_dark").performClick()
        composeRule.onNodeWithTag("appearance-oled_dark").assertIsDisplayed()
        capture("11-oled.png")
    }

    private fun selectAppearance(id: String) {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        composeRule.onNodeWithTag("appearance-$id").performClick()
        composeRule.waitForIdle()
    }

    private fun backToSetup() {
        composeRule.runOnIdle {
            ViewModelProvider(composeRule.activity)[PlayViewModel::class.java].backToSetup()
        }
        waitForTag(PLAY_SETUP_TEST_TAG)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 5_000L) {
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
        val directory = File(root, "p5-screenshots").apply { mkdirs() }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            FileOutputStream(File(directory, name)).use { output ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Failed to encode $name"
                }
            }
        } finally {
            screenshot.recycle()
        }
    }
}

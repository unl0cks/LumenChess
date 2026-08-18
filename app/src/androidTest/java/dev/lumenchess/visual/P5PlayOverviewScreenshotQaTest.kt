package dev.lumenchess.visual

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P5PlayOverviewScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitScreenshotRun() {
        val enabled =
            InstrumentationRegistry.getArguments().getString("p5PlayOverviewQa") == "true"
        assumeTrue(
            "Play overview screenshot QA runs only from the dedicated workflow step",
            enabled,
        )
    }

    @Test
    fun capturePlayOverviewOnly() {
        waitForTag("p5-play-overview")

        // Screenshot QA uses the product's real appearance preference path. The emulator defaults to
        // a light system theme, while the approved Play reference is the dark graphite Lumen theme.
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("main-tab-play").performClick()
        waitForTag("p5-play-overview")
        capture("00-play-overview.png")
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
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(directory, name)).use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to encode $name"
            }
        }
    }
}

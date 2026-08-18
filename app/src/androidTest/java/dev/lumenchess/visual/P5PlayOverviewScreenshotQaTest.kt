package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
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
import dev.lumenchess.R
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
        verifyInterTightRuntimeResource()
        verifyApprovedHeroAssetsPackaged()
        waitForTag("p5-play-overview")
        waitForTag("play-overview-vs-engine-hero")
        waitForTag("play-overview-arena-hero")

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

    private fun verifyInterTightRuntimeResource() {
        val resources = composeRule.activity.resources
        check(resources.getResourceEntryName(R.font.inter_tight_regular) == "inter_tight_regular") {
            "Play overview typography resource did not resolve to Inter Tight"
        }
        val typeface = resources.getFont(R.font.inter_tight_regular)
        check(typeface != Typeface.DEFAULT && typeface != Typeface.DEFAULT_BOLD) {
            "Inter Tight resolved to a platform default typeface"
        }
        println("P5 Play typography verified: inter_tight_regular loaded from app resources")
    }

    private fun verifyApprovedHeroAssetsPackaged() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        listOf(
            "play-overview/lumen_play_vs_engine_hero.png",
            "play-overview/lumen_engine_arena_hero.png",
        ).forEach { path ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { input -> BitmapFactory.decodeStream(input, null, bounds) }
            check(bounds.outWidth == 1254 && bounds.outHeight == 1254) {
                "$path must package the approved 1254x1254 hero PNG exactly; got ${bounds.outWidth}x${bounds.outHeight}"
            }
        }
        println("P5 Play hero assets verified: approved PNG resources packaged")
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

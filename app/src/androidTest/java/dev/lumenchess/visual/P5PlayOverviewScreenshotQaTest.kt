package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.R
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertTrue
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
        // The hero Images are intentionally decorative (contentDescription = null), so Compose may
        // merge their semantics. Wait on the rendered mode-card nodes after verifying the exact PNGs.
        waitForTag("play-overview-vs-engine")
        waitForTag("play-overview-arena")

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
        // The depth tag is intentionally inside the pressable card; inspect the unmerged tree so
        // the parent Button semantics do not hide the internal surface marker.
        waitForTag("play-overview-vs-engine-depth-surface", useUnmergedTree = true)
        assertArenaSubtitleWraps()
        capture("00-play-overview.png")
        capturePressState("play-overview-vs-engine")
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
        val approved = mapOf(
            "play-overview/lumen_play_vs_engine_hero.png" to
                "43a6accd71c5f9f1bfba552e0c409f5a95f25b1567b617c5c8851b5186d40e00",
            "play-overview/lumen_engine_arena_hero.png" to
                "2554fb301501a9f667652ab0631147bd7b38d868812b2dfecc0ea5bfa0aa12f2",
        )
        approved.forEach { (path, expectedSha256) ->
            val bytes = assets.open(path).use { input -> input.readBytes() }
            val actualSha256 = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { byte -> "%02x".format(byte) }
            check(actualSha256 == expectedSha256) {
                "$path does not match the exact approved PNG bytes: $actualSha256"
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            check(bounds.outWidth == 1254 && bounds.outHeight == 1254) {
                "$path must package the approved 1254x1254 hero PNG exactly; got ${bounds.outWidth}x${bounds.outHeight}"
            }
        }
        println("P5 Play hero assets verified: exact approved PNG bytes packaged")
    }

    private fun assertArenaSubtitleWraps() {
        val bounds = composeRule
            .onNodeWithText("Watch engines battle each other")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Engine Arena subtitle must occupy two lines in the reference composition; height=${bounds.height}",
            bounds.height >= 80f,
        )
    }

    private fun waitForTag(
        tag: String,
        timeoutMillis: Long = 5_000L,
        useUnmergedTree: Boolean = false,
    ) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule
                .onAllNodesWithTag(tag, useUnmergedTree = useUnmergedTree)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule
            .onNodeWithTag(tag, useUnmergedTree = useUnmergedTree)
            .assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun capturePressState(tag: String) {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(tag)
        val rest = node.captureToImage().asAndroidBitmap()

        node.performTouchInput {
            down(center)
            advanceEventTime(90L)
        }
        composeRule.waitForIdle()
        val pressed = node.captureToImage().asAndroidBitmap()
        node.performTouchInput { up() }
        composeRule.waitForIdle()

        val labelHeight = 34
        val gap = 10
        val width = rest.width + gap + pressed.width
        val height = labelHeight + maxOf(rest.height, pressed.height)
        val comparison = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(comparison)
        canvas.drawColor(android.graphics.Color.rgb(8, 8, 8))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(224, 228, 230)
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("REST", 8f, 25f, paint)
        canvas.drawText("PRESSED", (rest.width + gap + 8).toFloat(), 25f, paint)
        canvas.drawBitmap(rest, 0f, labelHeight.toFloat(), null)
        canvas.drawBitmap(pressed, (rest.width + gap).toFloat(), labelHeight.toFloat(), null)
        writeBitmap("00-play-overview-press-state.png", comparison)
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        writeBitmap(name, screenshot)
    }

    private fun writeBitmap(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(root, "p5-screenshots").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to encode $name"
            }
        }
    }
}

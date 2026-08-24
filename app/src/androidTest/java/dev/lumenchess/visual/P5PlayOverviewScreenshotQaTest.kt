package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.R
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
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
    fun captureApprovedPlayOverviewTranslation() {
        verifyInterTightRuntimeResource()
        verifyApprovedHeroAssetsPackaged()
        openApprovedDarkPlayOverview()

        assertApprovedStructure()
        assertArenaSubtitleWraps()
        capture("01-play-overview.png")
        capturePressStates()
    }

    @Test
    fun playVsEngineAndArenaPreserveExistingRoutes() {
        openApprovedDarkPlayOverview()

        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
        pressBack()
        waitForTag("p5-play-overview")

        composeRule.onNodeWithTag("play-overview-arena").performClick()
        composeRule.onNodeWithTag("derivative-arena-preview").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Set up engine battles, opening positions, and takeover play from the dedicated Arena tab.",
        ).assertIsDisplayed()
    }

    @Test
    fun quickStartUsesTheExistingSetupRoute() {
        openApprovedDarkPlayOverview()
        composeRule.onNodeWithTag("play-overview-quick-start").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
    }

    private fun openApprovedDarkPlayOverview() {
        waitForTag("p5-play-overview")
        waitForTag("play-overview-vs-engine")
        waitForTag("play-overview-arena")

        // Use the product's real appearance preference path. The emulator defaults to a light
        // system theme while the frozen Iteration 2 reference is the dark graphite Lumen theme.
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("main-tab-play").performClick()
        waitForTag("p5-play-overview")
        waitForTag("play-overview-quick-start")
        composeRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
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
            println("P5 Play hero SHA-256 verified: $path = $actualSha256")
        }
    }

    private fun assertApprovedStructure() {
        assertEquals(
            "Play vs Engine must remain one hero action",
            1,
            composeRule.onAllNodesWithTag("play-overview-vs-engine").fetchSemanticsNodes().size,
        )
        assertEquals(
            "Engine Arena must remain one hero action",
            1,
            composeRule.onAllNodesWithTag("play-overview-arena").fetchSemanticsNodes().size,
        )
        assertEquals(
            "Quick Start must remain one real last-used row",
            1,
            composeRule.onAllNodesWithTag("play-overview-quick-start").fetchSemanticsNodes().size,
        )

        assertArtLeftOfCopy("play-overview-vs-engine-hero", "play-overview-vs-engine-copy")
        assertArtLeftOfCopy("play-overview-arena-hero", "play-overview-arena-copy")

        composeRule.onNodeWithText("Quick Start").assertIsDisplayed()
        composeRule.onNodeWithText("Last used").assertIsDisplayed()
        composeRule.onNodeWithText("10 min · Rapid").assertIsDisplayed()
        composeRule.onNodeWithText("Stockfish 18 · 1600 Elo").assertIsDisplayed()
        listOf("play", "arena", "games", "insights", "settings").forEach { tab ->
            composeRule.onNodeWithTag("main-tab-$tab").assertIsDisplayed()
        }
    }

    private fun assertArtLeftOfCopy(artTag: String, copyTag: String) {
        val art = composeRule.onNodeWithTag(artTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val copy = composeRule.onNodeWithTag(copyTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("$artTag must remain left of its text column", art.right < copy.left)
        assertTrue("$artTag must have non-zero rendered size", art.width > 0f && art.height > 0f)
    }

    private fun assertArenaSubtitleWraps() {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText("Watch engines battle each other", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                action(textLayoutResults)
            }
        assertEquals(
            "Engine Arena subtitle must render as exactly two natural lines",
            2,
            textLayoutResults.single().lineCount,
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
        composeRule.onNodeWithTag(tag, useUnmergedTree = useUnmergedTree).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private data class PressPair(
        val label: String,
        val rest: Bitmap,
        val pressed: Bitmap,
        val changedRatio: Float,
    )

    private fun capturePressStates() {
        val pairs = listOf(
            capturePressPair("Play vs Engine", "play-overview-vs-engine"),
            capturePressPair("Engine Arena", "play-overview-arena"),
            capturePressPair("Quick Start", "play-overview-quick-start"),
        )
        pairs.forEach { pair ->
            assertTrue(
                "${pair.label} production press state must visibly change at least 1.8% of pixels; actual=${pair.changedRatio}",
                pair.changedRatio > .018f,
            )
        }

        val titleHeight = 30
        val stateLabelHeight = 28
        val gap = 10
        val rowGap = 12
        val width = pairs.maxOf { it.rest.width + gap + it.pressed.width }
        val height = pairs.sumOf { titleHeight + stateLabelHeight + maxOf(it.rest.height, it.pressed.height) } +
            rowGap * (pairs.size - 1)
        val comparison = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(comparison)
        canvas.drawColor(android.graphics.Color.rgb(8, 8, 8))
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(242, 245, 246)
            textSize = 21f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val statePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(176, 184, 188)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        var y = 0f
        pairs.forEachIndexed { index, pair ->
            canvas.drawText(pair.label, 8f, y + 22f, titlePaint)
            y += titleHeight
            canvas.drawText("REST", 8f, y + 20f, statePaint)
            canvas.drawText("PRESSED", (pair.rest.width + gap + 8).toFloat(), y + 20f, statePaint)
            y += stateLabelHeight
            canvas.drawBitmap(pair.rest, 0f, y, null)
            canvas.drawBitmap(pair.pressed, (pair.rest.width + gap).toFloat(), y, null)
            y += maxOf(pair.rest.height, pair.pressed.height)
            if (index != pairs.lastIndex) y += rowGap
        }
        writeBitmap("01-play-overview-press-state.png", comparison)
    }

    private fun capturePressPair(label: String, tag: String): PressPair {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(tag)
        val rest = node.captureToImage().asAndroidBitmap()

        node.performTouchInput {
            down(center)
            advanceEventTime(90L)
        }
        composeRule.waitForIdle()
        val pressedCapture = node.captureToImage().asAndroidBitmap()
        node.performTouchInput { cancel() }
        composeRule.waitForIdle()

        val pressed = centerCompressedPressCapture(rest, pressedCapture)
        return PressPair(label, rest, pressed, changedPixelRatio(rest, pressed))
    }

    private fun centerCompressedPressCapture(rest: Bitmap, pressed: Bitmap): Bitmap {
        assertTrue(
            "Production press capture must not grow beyond REST bounds: rest=${rest.width}x${rest.height}, pressed=${pressed.width}x${pressed.height}",
            pressed.width <= rest.width && pressed.height <= rest.height,
        )
        val normalized = Bitmap.createBitmap(rest.width, rest.height, Bitmap.Config.ARGB_8888)
        AndroidCanvas(normalized).drawBitmap(
            pressed,
            (rest.width - pressed.width) / 2f,
            (rest.height - pressed.height) / 2f,
            null,
        )
        return normalized
    }

    private fun changedPixelRatio(left: Bitmap, right: Bitmap): Float {
        assertEquals("Normalized press-state width changed unexpectedly", left.width, right.width)
        assertEquals("Normalized press-state height changed unexpectedly", left.height, right.height)
        val size = left.width * left.height
        val a = IntArray(size)
        val b = IntArray(size)
        left.getPixels(a, 0, left.width, 0, 0, left.width, left.height)
        right.getPixels(b, 0, right.width, 0, 0, right.width, right.height)
        var changed = 0
        for (index in 0 until size) {
            if (a[index] != b[index]) changed++
        }
        return changed.toFloat() / size.toFloat()
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

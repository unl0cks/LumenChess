package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.R
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlaySide
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.play.PlayViewModel
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P5SetupScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitScreenshotRun() {
        val enabled = InstrumentationRegistry.getArguments().getString("p5SetupQa") == "true"
        assumeTrue("P5 New Game screenshot QA runs only from its dedicated workflow step", enabled)
    }

    @Test
    fun captureNewGameReferenceOnly() {
        verifyInterTightRuntimeResource()
        waitForTag("p5-play-overview")
        selectDarkAppearance()
        composeRule.onNodeWithTag("main-tab-play").performClick()
        waitForTag("p5-play-overview")

        composeRule.runOnIdle {
            ViewModelProvider(composeRule.activity)[PlayViewModel::class.java].apply {
                updateVariant(Variant.STANDARD)
                updateEngine(PlayEngine.STOCKFISH_18)
                updateSide(PlaySide.WHITE)
                updateStrengthModel(EngineStrengthModel.HYBRID)
                updateStrengthTarget(EngineStrengthTarget.Elo(1450))
                updateTimeControl(PlayTimeControl(initialMillis = 600_000L, incrementMillis = 10_000L))
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)

        composeRule.onNodeWithTag("main-tab-play").assertDoesNotExist()
        listOf(
            "p5-setup-shell",
            "p5-setup-standard",
            "p5-setup-opponent",
            "p5-setup-strength-slider",
            "p5-match-my-elo",
            "p5-setup-strength-model",
            "p5-setup-side",
            "p5-setup-time",
            "p5-inc-delay",
            "p5-setup-start",
            "p5-setup-note-1",
            "p5-setup-note-2",
            "p5-setup-back",
        ).forEach(::waitForTag)

        composeRule.onNodeWithTag("p5-match-my-elo").assertIsNotEnabled()
        listOf("1450", "Stockfish 18", "Hybrid", "White", "Rapid", "10 sec", "Start Game").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Match My Elo is preview-only in this build.").assertIsDisplayed()
        composeRule.onNodeWithText("Your selected strength, side and clock apply when the game starts.").assertIsDisplayed()

        assertReferenceWidthRelationships()
        assertReferenceVerticalRelationships()
        logReferenceMetrics()
        capture("01-setup.png")
        capturePressState("p5-setup-standard")

        composeRule.onNodeWithTag("p5-setup-back").performClick()
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("main-tab-play").assertIsDisplayed()
    }

    private fun selectDarkAppearance() {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun verifyInterTightRuntimeResource() {
        val resources = composeRule.activity.resources
        check(resources.getResourceEntryName(R.font.inter_tight_regular) == "inter_tight_regular")
        val typeface = resources.getFont(R.font.inter_tight_regular)
        check(typeface != Typeface.DEFAULT && typeface != Typeface.DEFAULT_BOLD)
    }

    private fun assertReferenceWidthRelationships() {
        val metrics = composeRule.activity.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val shellInner = bounds("p5-setup-shell")
        val shellOuterWidth = shellInner.width + 22f * metrics.density
        val opponentWidth = bounds("p5-setup-opponent").width
        assertTrue("shell width ratio=${shellOuterWidth / screenWidth}", shellOuterWidth / screenWidth in 0.91f..0.96f)
        assertTrue("control width ratio=${opponentWidth / screenWidth}", opponentWidth / screenWidth in 0.86f..0.92f)
    }

    private fun assertReferenceVerticalRelationships() {
        val metrics = composeRule.activity.resources.displayMetrics
        val screenHeight = metrics.heightPixels.toFloat()
        val density = metrics.density
        val shellInner = bounds("p5-setup-shell")
        val shellTop = shellInner.top - 7f * density
        val shellBottom = shellInner.bottom + 7f * density
        val shellRatio = (shellBottom - shellTop) / screenHeight
        val compositionBottomRatio = bounds("p5-setup-note-2").bottom / screenHeight

        assertTrue("setup shell must occupy most of Pixel content; ratio=$shellRatio", shellRatio in 0.84f..0.91f)
        assertTrue("setup composition including truthful notes should reach 90-95% of viewport; bottom=$compositionBottomRatio", compositionBottomRatio in 0.90f..0.95f)

        assertHeightDp("p5-setup-header", 38f, 44f)
        assertHeightDp("p5-setup-standard", 56f, 64f)
        assertHeightDp("p5-setup-opponent", 56f, 64f)
        assertHeightDp("p5-setup-strength-slider", 24f, 30f)
        assertHeightDp("p5-match-my-elo", 48f, 54f)
        assertHeightDp("p5-setup-strength-model", 46f, 52f)
        assertHeightDp("p5-setup-side", 78f, 92f)
        assertHeightDp("p5-setup-time", 52f, 58f)
        assertHeightDp("p5-inc-delay", 52f, 58f)
        assertHeightDp("p5-setup-start", 48f, 54f)
    }

    private fun assertHeightDp(tag: String, min: Float, max: Float) {
        val density = composeRule.activity.resources.displayMetrics.density
        val height = bounds(tag).height / density
        assertTrue("$tag height=${height}dp expected $min..$max", height in min..max)
    }

    private fun logReferenceMetrics() {
        val metrics = composeRule.activity.resources.displayMetrics
        val shellInner = bounds("p5-setup-shell")
        val hp = 11f * metrics.density
        val vp = 7f * metrics.density
        println("P5_SETUP_METRIC viewport w=${metrics.widthPixels} h=${metrics.heightPixels}")
        println("P5_SETUP_METRIC p5-setup-shell x=${shellInner.left - hp} y=${shellInner.top - vp} w=${shellInner.width + hp * 2f} h=${shellInner.height + vp * 2f}")
        listOf(
            "p5-setup-header", "p5-setup-game-mode", "p5-setup-standard", "p5-setup-opponent",
            "p5-setup-strength-slider", "p5-match-my-elo", "p5-setup-strength-model", "p5-setup-side",
            "p5-setup-time", "p5-inc-delay", "p5-setup-start", "p5-setup-note-1", "p5-setup-note-2",
        ).forEach { tag ->
            val b = bounds(tag)
            println("P5_SETUP_METRIC $tag x=${b.left} y=${b.top} w=${b.width} h=${b.height}")
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun waitForTag(tag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun capturePressState(tag: String) {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(tag)
        val rest = node.captureToImage().asAndroidBitmap()
        node.performTouchInput { down(center); advanceEventTime(90L) }
        composeRule.waitForIdle()
        val pressed = node.captureToImage().asAndroidBitmap()
        val changedRatio = countDifferentPixels(rest, pressed).toFloat() / (rest.width * rest.height).toFloat()
        assertTrue("REST/PRESSED depth change must be immediately perceptible; changedRatio=$changedRatio", changedRatio > 0.055f)
        node.performTouchInput { up() }
        composeRule.waitForIdle()

        val labelHeight = 34
        val gap = 10
        val comparison = Bitmap.createBitmap(rest.width + gap + pressed.width, labelHeight + maxOf(rest.height, pressed.height), Bitmap.Config.ARGB_8888)
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
        writeBitmap("01-setup-press-state.png", comparison)
    }

    private fun countDifferentPixels(first: Bitmap, second: Bitmap): Int {
        if (first.width != second.width || first.height != second.height) return Int.MAX_VALUE
        val a = IntArray(first.width * first.height)
        val b = IntArray(second.width * second.height)
        first.getPixels(a, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(b, 0, second.width, 0, 0, second.width, second.height)
        return a.indices.count { a[it] != b[it] }
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        writeBitmap(name, composeRule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun writeBitmap(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(root, "p5-screenshots").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}

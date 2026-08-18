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

        // New Game is a focused Play subpage: root tab navigation must not consume its bottom edge.
        composeRule.onNodeWithTag("main-tab-play").assertDoesNotExist()
        waitForTag("p5-setup-shell")
        waitForTag("p5-setup-standard")
        waitForTag("p5-setup-opponent")
        waitForTag("p5-setup-strength-slider")
        waitForTag("p5-match-my-elo")
        waitForTag("p5-setup-strength-model")
        waitForTag("p5-setup-side")
        waitForTag("p5-setup-time")
        waitForTag("p5-inc-delay")
        waitForTag("p5-setup-start")
        waitForTag("p5-setup-back")

        composeRule.onNodeWithTag("p5-match-my-elo").assertIsNotEnabled()
        composeRule.onNodeWithText("1450").assertIsDisplayed()
        composeRule.onNodeWithText("Stockfish 18").assertIsDisplayed()
        composeRule.onNodeWithText("Hybrid").assertIsDisplayed()
        composeRule.onNodeWithText("White").assertIsDisplayed()
        composeRule.onNodeWithText("Rapid").assertIsDisplayed()
        composeRule.onNodeWithText("10 sec").assertIsDisplayed()
        composeRule.onNodeWithText("Start Game").assertIsDisplayed()
        composeRule.onNodeWithText("Match My Elo is preview-only in this build.").assertIsDisplayed()
        composeRule.onNodeWithText("Your selected strength, side and clock apply when the game starts.").assertIsDisplayed()

        assertReferenceWidthRelationships()
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
        check(resources.getResourceEntryName(R.font.inter_tight_regular) == "inter_tight_regular") {
            "New Game typography resource did not resolve to Inter Tight"
        }
        val typeface = resources.getFont(R.font.inter_tight_regular)
        check(typeface != Typeface.DEFAULT && typeface != Typeface.DEFAULT_BOLD) {
            "Inter Tight resolved to a platform default typeface"
        }
    }

    private fun assertReferenceWidthRelationships() {
        val rootWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val shellWidth = composeRule.onNodeWithTag("p5-setup-shell").fetchSemanticsNode().boundsInRoot.width
        val opponentWidth = composeRule.onNodeWithTag("p5-setup-opponent").fetchSemanticsNode().boundsInRoot.width
        assertTrue("Setup shell should occupy roughly the reference phone width", shellWidth / rootWidth in 0.91f..0.96f)
        assertTrue("Main setup controls should occupy roughly 89.5% of screen width", opponentWidth / rootWidth in 0.86f..0.92f)
    }

    private fun logReferenceMetrics() {
        listOf(
            "p5-setup-shell",
            "p5-setup-header",
            "p5-setup-game-mode",
            "p5-setup-opponent",
            "p5-setup-strength-slider",
            "p5-match-my-elo",
            "p5-setup-strength-model",
            "p5-setup-side",
            "p5-setup-time",
            "p5-inc-delay",
            "p5-setup-start",
            "p5-setup-note-1",
        ).forEach { tag ->
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            println("P5_SETUP_METRIC $tag x=${bounds.left} y=${bounds.top} w=${bounds.width} h=${bounds.height}")
        }
    }

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

        node.performTouchInput {
            down(center)
            advanceEventTime(90L)
        }
        composeRule.waitForIdle()
        val pressed = node.captureToImage().asAndroidBitmap()
        val changedPixels = countDifferentPixels(rest, pressed)
        assertTrue("REST and PRESSED must visibly differ in depth treatment; changed=$changedPixels", changedPixels > 250)
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
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Failed to encode $name" }
        }
    }
}

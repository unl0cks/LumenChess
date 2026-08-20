package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import dev.lumenchess.play.PLAY_LIVE_TEST_TAG
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import dev.lumenchess.play.PLAY_START_TEST_TAG
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlaySide
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.play.PlayViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import org.junit.Assert.assertEquals
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
    fun approvedStandardStateCapturesFrozenNewGameAndPressFamilies() {
        verifyInterTightRuntimeResource()
        enterNewGame()
        val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
        composeRule.runOnIdle {
            viewModel.apply {
                updateVariant(Variant.STANDARD)
                updateEngine(PlayEngine.STOCKFISH_18)
                updateSide(PlaySide.WHITE)
                updateStrengthModel(EngineStrengthModel.HYBRID)
                updateStrengthTarget(EngineStrengthTarget.Elo(1600))
                updateTimeControl(PlayTimeControl(initialMillis = 600_000L, incrementMillis = 0L))
            }
        }
        composeRule.waitForIdle()

        listOf(
            "p5-setup-shell",
            "p5-setup-plane",
            "p5-setup-standard",
            "p5-setup-opponent",
            "p5-setup-strength-slider",
            "p5-match-my-elo",
            "p5-setup-strength-model",
            "p5-setup-strength-model-selected",
            "p5-setup-side",
            "p5-setup-side-selected",
            "p5-setup-time",
            "p5-inc-delay",
            "p5-setup-start",
            "p5-setup-start-button",
            "p5-setup-note-1",
            "p5-setup-note-2",
            "p5-setup-back",
        ).forEach(::waitForTag)

        composeRule.onNodeWithTag("main-tab-play").assertDoesNotExist()
        composeRule.onNodeWithTag("p5-match-my-elo").assertIsNotEnabled()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).assertIsEnabled()
        listOf("1600", "Stockfish 18", "Hybrid", "White", "Rapid", "0 sec", "Start Game").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithText("Match My Elo is preview-only in this build.").assertIsDisplayed()
        composeRule.onNodeWithText("Your selected strength, side and clock apply when the game starts.").assertIsDisplayed()

        assertApprovedGeometry()
        capture("02-new-game.png")
        capturePressBoard(
            listOf(
                "Opponent selector" to "p5-setup-opponent",
                "Strength Model" to "p5-setup-strength-model-selected",
                "Side" to "p5-setup-side-selected",
                "Start Game" to "p5-setup-start-button",
            ),
        )

        composeRule.onNodeWithTag("p5-setup-back").performClick()
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("main-tab-play").assertIsDisplayed()
    }

    @Test
    fun setupControlsRemainStateDrivenAndChess960ConditionalLayoutIsPreserved() {
        enterNewGame()
        val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]

        composeRule.onNodeWithText("Chess960").performClick()
        composeRule.waitForIdle()
        assertEquals(Variant.CHESS960, viewModel.uiState.value.setup.variant)
        assertEquals(518, viewModel.uiState.value.setup.chess960Index)
        waitForTag("p5-setup-chess960-position")

        composeRule.onNodeWithText("Standard").performClick()
        composeRule.waitForIdle()
        assertEquals(Variant.STANDARD, viewModel.uiState.value.setup.variant)
        assertEquals(null, viewModel.uiState.value.setup.chess960Index)

        composeRule.onNodeWithTag("p5-setup-opponent").performClick()
        composeRule.onNodeWithText("Reckless 0.9.0").performClick()
        composeRule.waitForIdle()
        assertEquals(PlayEngine.RECKLESS_0_9_0, viewModel.uiState.value.setup.engine)

        composeRule.onNodeWithText("Humanized").performClick()
        composeRule.waitForIdle()
        assertEquals(EngineStrengthModel.HUMANIZED, viewModel.uiState.value.setup.strengthModel)

        composeRule.onNodeWithText("Black").performClick()
        composeRule.waitForIdle()
        assertEquals(PlaySide.BLACK, viewModel.uiState.value.setup.side)

        composeRule.onNodeWithTag("p5-setup-time").performClick()
        composeRule.onNodeWithText("Classical").performClick()
        composeRule.waitForIdle()
        assertEquals(1_800_000L, viewModel.uiState.value.setup.timeControl.initialMillis)

        composeRule.onNodeWithTag("p5-inc-delay").performClick()
        composeRule.onNodeWithText("5 sec").performClick()
        composeRule.waitForIdle()
        assertEquals(5_000L, viewModel.uiState.value.setup.timeControl.incrementMillis)

        composeRule.onNodeWithTag("p5-match-my-elo").assertIsNotEnabled()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun standardStartGameStillEntersLiveBoardFirstRoute() {
        enterNewGame()
        val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
        composeRule.runOnIdle {
            viewModel.apply {
                updateVariant(Variant.STANDARD)
                updateEngine(PlayEngine.STOCKFISH_18)
                updateSide(PlaySide.WHITE)
                updateStrengthModel(EngineStrengthModel.HYBRID)
                updateStrengthTarget(EngineStrengthTarget.Elo(1600))
                updateTimeControl(PlayTimeControl(initialMillis = 600_000L, incrementMillis = 0L))
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForTag(PLAY_LIVE_TEST_TAG, timeoutMillis = 12_000L)
        composeRule.onNodeWithTag("main-tab-play").assertDoesNotExist()
    }

    private fun enterNewGame() {
        waitForTag("p5-play-overview")
        selectDarkAppearance()
        composeRule.onNodeWithTag("main-tab-play").performClick()
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
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

    private fun assertApprovedGeometry() {
        val metrics = composeRule.activity.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()
        val plane = bounds("p5-setup-shell")
        val time = bounds("p5-setup-time")
        val increment = bounds("p5-inc-delay")
        val opponent = bounds("p5-setup-opponent")
        val start = bounds("p5-setup-start-button")
        val notesBottom = bounds("p5-setup-note-2").bottom

        assertTrue("setup plane should retain substantial occupancy", plane.height / screenHeight in 0.72f..0.84f)
        assertTrue("setup plane should preserve 358/390 reference width", plane.width / screenWidth in 0.90f..0.94f)
        assertTrue("opponent selector should span the setup content width", abs(opponent.width - time.width) < 3f * metrics.density)
        assertTrue("Time Control must remain full-width", time.width / plane.width > 0.90f)
        assertTrue("Inc / Delay must remain full-width", increment.width / plane.width > 0.90f)
        assertTrue("Time and Inc selectors must stack vertically", increment.top > time.bottom)
        assertTrue("Start Game must anchor below Inc / Delay", start.top > increment.bottom)
        assertTrue("truthful notes should remain visible near the lower viewport", notesBottom / screenHeight in 0.88f..0.99f)

        listOf("p5-setup-standard", "p5-setup-opponent", "p5-match-my-elo", "p5-setup-strength-model-selected", "p5-setup-side-selected", "p5-setup-time", "p5-inc-delay", "p5-setup-start-button").forEach { tag ->
            val heightDp = bounds(tag).height / metrics.density
            assertTrue("$tag must retain >=48dp effective target; height=$heightDp", heightDp >= 48f)
        }
    }

    private fun capturePressBoard(entries: List<Pair<String, String>>) {
        val captures = entries.map { (label, tag) ->
            val node = composeRule.onNodeWithTag(tag)
            composeRule.waitForIdle()
            val rest = node.captureToImage().asAndroidBitmap()
            node.performTouchInput { down(center); advanceEventTime(120L) }
            // New Game stays vertically scrollable for resume/Chess960 overflow. Compose delays
            // PressInteraction in scroll containers, so hold the real pointer through that delay
            // before capturing the production pressed state.
            Thread.sleep(220L)
            composeRule.waitForIdle()
            val pressed = node.captureToImage().asAndroidBitmap()
            node.performTouchInput { up() }
            composeRule.waitForIdle()

            val normalized = centerOnCanvas(pressed, rest.width, rest.height)
            val changed = visibleDifferenceRatio(rest, normalized, threshold = 10)
            assertTrue("$label REST/PRESSED must be perceptible; ratio=$changed", changed > 0.018f)
            PressCapture(label, rest, normalized)
        }

        val labelWidth = 210
        val stateGap = 12
        val rowGap = 18
        val rowLabelHeight = 28
        val width = labelWidth + captures.maxOf { it.rest.width + stateGap + it.pressed.width }
        val height = captures.sumOf { rowLabelHeight + maxOf(it.rest.height, it.pressed.height) } + rowGap * (captures.size - 1)
        val board = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(board)
        canvas.drawColor(android.graphics.Color.rgb(5, 7, 9))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(230, 236, 239)
            textSize = 22f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val secondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(141, 153, 158)
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        var y = 0f
        captures.forEachIndexed { index, capture ->
            canvas.drawText(capture.label, 8f, y + 22f, paint)
            canvas.drawText("REST", labelWidth.toFloat(), y + 22f, secondary)
            canvas.drawText("PRESSED", (labelWidth + capture.rest.width + stateGap).toFloat(), y + 22f, secondary)
            val imageY = y + rowLabelHeight
            canvas.drawBitmap(capture.rest, labelWidth.toFloat(), imageY, null)
            canvas.drawBitmap(capture.pressed, (labelWidth + capture.rest.width + stateGap).toFloat(), imageY, null)
            y += rowLabelHeight + maxOf(capture.rest.height, capture.pressed.height)
            if (index != captures.lastIndex) y += rowGap
        }
        writeBitmap("02-new-game-press-state.png", board)
    }

    private data class PressCapture(val label: String, val rest: Bitmap, val pressed: Bitmap)

    private fun centerOnCanvas(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        if (bitmap.width == width && bitmap.height == height) return bitmap
        val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(canvasBitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        canvas.drawBitmap(bitmap, ((width - bitmap.width) / 2f), ((height - bitmap.height) / 2f), null)
        return canvasBitmap
    }

    private fun visibleDifferenceRatio(first: Bitmap, second: Bitmap, threshold: Int): Float {
        if (first.width != second.width || first.height != second.height) return 1f
        val a = IntArray(first.width * first.height)
        val b = IntArray(first.width * first.height)
        first.getPixels(a, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(b, 0, second.width, 0, 0, second.width, second.height)
        var changed = 0
        a.indices.forEach { index ->
            val one = a[index]
            val two = b[index]
            val red = abs(android.graphics.Color.red(one) - android.graphics.Color.red(two))
            val green = abs(android.graphics.Color.green(one) - android.graphics.Color.green(two))
            val blue = abs(android.graphics.Color.blue(one) - android.graphics.Color.blue(two))
            if (maxOf(red, green, blue) >= threshold) changed++
        }
        return changed.toFloat() / a.size.toFloat()
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun waitForTag(tag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.waitForIdle()
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

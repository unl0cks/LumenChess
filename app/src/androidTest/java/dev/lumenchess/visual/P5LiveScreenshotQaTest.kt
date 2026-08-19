package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
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
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.R
import dev.lumenchess.board.CHESSBOARD_TEST_TAG
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineSearchResult
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
class P5LiveScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitScreenshotRun() {
        val enabled = InstrumentationRegistry.getArguments().getString("p5LiveQa") == "true"
        assumeTrue("P5 Live Game screenshot QA runs only from its dedicated workflow step", enabled)
    }

    @Test
    fun captureStockfishLiveReferenceOnly() {
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
                updateTimeControl(PlayTimeControl(initialMillis = 600_000L, incrementMillis = 0L))
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForLiveReady()

        composeRule.onNodeWithTag("main-tab-play").assertDoesNotExist()
        seedDeterministicOpening()
        assertAuthoritativeOpeningState()
        assertReferenceGeometry()
        logReferenceMetrics()

        capture("02-stockfish-live.png")
        capturePressState("p5-live-action-pause")
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

    /** Drives the real serialized runtime through the legal 1.e4 e5 2.Nf3 Nc6 3.Bb5 a6 4.Ba4 Nf6 5.O-O Be7 mainline. */
    private fun seedDeterministicOpening() {
        composeRule.runOnIdle {
            val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
            val coordinator = requireNotNull(viewModel.currentCoordinatorForTest())

            fun human(uci: String) {
                Thread.sleep(240L)
                coordinator.humanMove(Move.parseUci(uci))
            }

            fun engine(uci: String) {
                Thread.sleep(240L)
                val pending = requireNotNull(coordinator.state.pendingEngineSearch) {
                    "Expected pending engine search before deterministic move $uci"
                }
                coordinator.onEngineResult(
                    EngineSearchResult(
                        searchId = pending.searchId,
                        positionRevision = pending.positionRevision,
                        bestMoveUci = uci,
                    ),
                )
            }

            human("e2e4")
            engine("e7e5")
            human("g1f3")
            engine("b8c6")
            human("f1b5")
            engine("a7a6")
            human("b5a4")
            engine("g8f6")
            human("e1g1")
            engine("f8e7")

            // Refresh only the ViewModel projection; runtime/game tree remain authoritative.
            viewModel.cancelPremove()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("O-O").assertIsDisplayed()
        composeRule.onNodeWithText("Be7").assertIsDisplayed()
    }

    private fun assertAuthoritativeOpeningState() {
        composeRule.runOnIdle {
            val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
            val coordinator = requireNotNull(viewModel.currentCoordinatorForTest())
            val runtime = coordinator.state
            val mainline = runtime.gameTree.mainline()
            assertEquals(10, mainline.size)
            assertEquals("f8e7", requireNotNull(mainline.last().move).uci)
            assertEquals(Color.WHITE, runtime.position.sideToMove)
            assertTrue("white clock must be noninitial", runtime.clock.whiteRemainingMillis != 600_000L)
            assertTrue("black clock must be noninitial", runtime.clock.blackRemainingMillis != 600_000L)
        }

        val f8State = composeRule.onNodeWithTag("square-f8").fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.StateDescription) { "" }
        val e7State = composeRule.onNodeWithTag("square-e7").fetchSemanticsNode().config
            .getOrElse(SemanticsProperties.StateDescription) { "" }
        assertTrue("last-move source must be highlighted", f8State.contains("last move"))
        assertTrue("last-move destination must be highlighted", e7State.contains("last move"))
    }

    private fun assertReferenceGeometry() {
        val metrics = composeRule.activity.resources.displayMetrics
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()
        val board = bounds(CHESSBOARD_TEST_TAG)
        val lower = bounds("p5-live-lower-region")
        val actions = bounds("p5-live-action-strip")

        assertTrue("board must remain square: $board", abs(board.width - board.height) <= 1f)
        assertTrue("board width ratio=${board.width / screenWidth}", board.width / screenWidth in 0.92f..0.97f)
        assertTrue("lower game panel must sit below the board", lower.top >= board.bottom - 1f)

        val compositionBottomRatio = actions.bottom / screenHeight
        assertTrue(
            "live chess workspace must use almost the full useful Pixel viewport; bottom=$compositionBottomRatio",
            compositionBottomRatio in 0.89f..0.97f,
        )

        listOf(
            "p5-live-opponent-row",
            "p5-live-opponent-clock",
            "p5-live-player-row",
            "p5-live-player-clock",
            "p5-live-tabs",
            "p5-live-moves-rail",
            "p5-live-action-pause",
            "p5-live-action-resign",
            "p5-live-action-exit",
        ).forEach(::waitForTag)

        assertHeightDp("p5-live-opponent-row", 50f, 64f)
        assertHeightDp("p5-live-player-row", 50f, 64f)
        assertHeightDp("p5-live-tabs", 34f, 46f)
        assertHeightDp("p5-live-action-strip", 64f, 84f)

        val opponentClock = bounds("p5-live-opponent-clock")
        val playerClock = bounds("p5-live-player-clock")
        assertTrue("opponent clock width must be fixed", abs(opponentClock.width - playerClock.width) <= 1f)
        assertTrue("opponent clock height must be fixed", abs(opponentClock.height - playerClock.height) <= 1f)
    }

    private fun assertHeightDp(tag: String, min: Float, max: Float) {
        val density = composeRule.activity.resources.displayMetrics.density
        val height = bounds(tag).height / density
        assertTrue("$tag height=${height}dp expected $min..$max", height in min..max)
    }

    private fun logReferenceMetrics() {
        val metrics = composeRule.activity.resources.displayMetrics
        println("P5_LIVE_METRIC viewport w=${metrics.widthPixels} h=${metrics.heightPixels}")
        listOf(
            "p5-live-shell",
            "p5-live-opponent-row",
            "p5-live-opponent-clock",
            CHESSBOARD_TEST_TAG,
            "p5-live-player-row",
            "p5-live-player-clock",
            "p5-live-lower-region",
            "p5-live-tabs",
            "p5-live-moves-rail",
            "p5-live-action-strip",
        ).forEach { tag ->
            val b = bounds(tag)
            println("P5_LIVE_METRIC $tag x=${b.left} y=${b.top} w=${b.width} h=${b.height}")
        }
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun waitForLiveReady() {
        waitForTag(PLAY_LIVE_TEST_TAG, timeoutMillis = 12_000L)
        composeRule.waitUntil(timeoutMillis = 12_000L) {
            ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
                .currentCoordinatorForTest()?.state?.engineHostAvailable == true
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
        node.performTouchInput { down(center); advanceEventTime(80L) }
        composeRule.waitForIdle()
        val pressed = node.captureToImage().asAndroidBitmap()
        val visiblyChangedRatio = countVisiblyDifferentPixels(rest, pressed, threshold = 10).toFloat() /
            (rest.width * rest.height).toFloat()
        assertTrue(
            "compact action REST/PRESSED state must visibly compress; visiblyChangedRatio=$visiblyChangedRatio",
            visiblyChangedRatio > 0.025f,
        )
        node.performTouchInput { up() }
        composeRule.waitForIdle()

        val labelHeight = 34
        val gap = 10
        val comparison = Bitmap.createBitmap(
            rest.width + gap + pressed.width,
            labelHeight + maxOf(rest.height, pressed.height),
            Bitmap.Config.ARGB_8888,
        )
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
        writeBitmap("02-stockfish-live-press-state.png", comparison)
    }

    private fun countVisiblyDifferentPixels(first: Bitmap, second: Bitmap, threshold: Int): Int {
        if (first.width != second.width || first.height != second.height) return Int.MAX_VALUE
        val a = IntArray(first.width * first.height)
        val b = IntArray(first.width * first.height)
        first.getPixels(a, 0, first.width, 0, 0, first.width, first.height)
        second.getPixels(b, 0, second.width, 0, 0, second.width, second.height)
        return a.indices.count { index ->
            val one = a[index]
            val two = b[index]
            val red = abs(android.graphics.Color.red(one) - android.graphics.Color.red(two))
            val green = abs(android.graphics.Color.green(one) - android.graphics.Color.green(two))
            val blue = abs(android.graphics.Color.blue(one) - android.graphics.Color.blue(two))
            maxOf(red, green, blue) >= threshold
        }
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

package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertDoesNotExist
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
        assumeTrue(
            "P5 Live Game screenshot QA runs only from the integrated reference workflow",
            InstrumentationRegistry.getArguments().getString("p5LiveQa") == "true",
        )
    }

    @Test
    fun captureDefaultBoardFirstLiveReferenceOnly() {
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

        assertCanonicalSetupAndRuntime()
        seedDeterministicOpening()
        assertAuthoritativeOpeningState()
        assertBoardFirstPresentation()
        capture("03-live.png")
        capturePressState("Resign", "p5-live-action-resign")
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

    /** Drives the real serialized runtime through 1.e4 e5 2.Nf3 Nc6 3.Bb5 a6 4.Ba4 Nf6 5.O-O Be7. */
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
            viewModel.cancelPremove()
        }
        composeRule.waitForIdle()
    }

    private fun assertCanonicalSetupAndRuntime() {
        composeRule.runOnIdle {
            val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
            val setup = requireNotNull(viewModel.uiState.value.resolvedSetup)
            val coordinator = requireNotNull(viewModel.currentCoordinatorForTest())
            val runtime = coordinator.state

            assertEquals(Variant.STANDARD, setup.variant)
            assertEquals(PlayEngine.STOCKFISH_18, setup.engine)
            assertEquals(Color.WHITE, setup.humanSide)
            assertEquals(EngineStrengthModel.HYBRID, setup.strength.model)
            assertEquals(EngineStrengthTarget.Elo(1450), setup.strength.target)
            assertEquals(600_000L, setup.clockConfig.initialMillis)
            assertEquals(0L, setup.clockConfig.incrementMillis)
            assertEquals(setup, coordinator.setup)
            assertEquals(Variant.STANDARD, runtime.position.variant)
            assertEquals(Color.WHITE, runtime.position.sideToMove)
        }
    }

    private fun assertAuthoritativeOpeningState() {
        composeRule.runOnIdle {
            val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
            val runtime = requireNotNull(viewModel.currentCoordinatorForTest()).state
            val mainline = runtime.gameTree.mainline()
            assertEquals(10, mainline.size)
            assertEquals("f8e7", requireNotNull(mainline.last().move).uci)
            assertEquals(Color.WHITE, runtime.position.sideToMove)
            assertTrue("white clock must be noninitial", runtime.clock.whiteRemainingMillis != 600_000L)
            assertTrue("black clock must be noninitial", runtime.clock.blackRemainingMillis != 600_000L)
        }

        val f8State = composeRule.onNodeWithTag("square-f8").fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }
        val e7State = composeRule.onNodeWithTag("square-e7").fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }
        assertTrue("last-move source must be highlighted", f8State.contains("last move"))
        assertTrue("last-move destination must be highlighted", e7State.contains("last move"))
    }

    private fun assertBoardFirstPresentation() {
        composeRule.onNodeWithTag("main-tab-play").assertDoesNotExist()
        listOf(
            "p5-live-lower-region",
            "p5-live-tabs",
            "p5-live-moves-rail",
            "p5-live-action-pause",
            "p5-live-action-cancel",
        ).forEach { tag -> composeRule.onNodeWithTag(tag).assertDoesNotExist() }
        composeRule.onNodeWithText("Moves").assertDoesNotExist()
        composeRule.onNodeWithText("Info").assertDoesNotExist()

        listOf(
            "p5-live-opponent-row",
            "p5-live-opponent-clock",
            CHESSBOARD_TEST_TAG,
            "p5-live-player-row",
            "p5-live-player-clock",
            "p5-live-action-strip",
            "p5-live-action-resign",
            "p5-live-action-exit",
        ).forEach(::waitForTag)

        val liveRoot = bounds(PLAY_LIVE_TEST_TAG)
        val board = bounds(CHESSBOARD_TEST_TAG)
        val actionStrip = bounds("p5-live-action-strip")
        val density = composeRule.activity.resources.displayMetrics.density
        assertTrue("board must remain square: $board", abs(board.width - board.height) <= 1f)
        assertTrue("board width must retain P1-safe bounds: $board in $liveRoot", board.width / liveRoot.width in 0.92f..1f)
        assertTrue("essential action strip must retain its 72dp geometry: $actionStrip", actionStrip.height / density in 64f..84f)
        assertTrue(
            "essential action strip must be bottom-anchored to the Live root: actions=$actionStrip, root=$liveRoot",
            liveRoot.bottom - actionStrip.bottom <= 6f * density,
        )
    }

    private fun capturePressState(label: String, tag: String) {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(tag)
        val rest = node.captureToImage().asAndroidBitmap()
        // A real held pointer proves the Material press state. The test clock owns the delay and
        // animation; cancelling in finally prevents Resign from mutating the running game.
        val previousAutoAdvance = composeRule.mainClock.autoAdvance
        composeRule.mainClock.autoAdvance = false
        var pointerDown = false
        val pressed = try {
            node.performTouchInput { down(center) }
            pointerDown = true
            composeRule.mainClock.advanceTimeBy(240L)
            composeRule.waitForIdle()
            node.captureToImage().asAndroidBitmap()
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

        assertCancelledPressPreservesOpening()
        val visiblyChangedRatio = countVisiblyDifferentPixels(rest, pressed, threshold = 10).toFloat() /
            (rest.width * rest.height).toFloat()
        assertTrue("$label REST/PRESSED state must be perceptible; ratio=$visiblyChangedRatio", visiblyChangedRatio > 0.018f)

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
        canvas.drawText("$label REST", 8f, 25f, paint)
        canvas.drawText("$label PRESSED", (rest.width + gap + 8).toFloat(), 25f, paint)
        canvas.drawBitmap(rest, 0f, labelHeight.toFloat(), null)
        canvas.drawBitmap(pressed, (rest.width + gap).toFloat(), labelHeight.toFloat(), null)
        writeBitmap("03-live-press-state.png", comparison)
    }

    private fun assertCancelledPressPreservesOpening() {
        composeRule.runOnIdle {
            val runtime = requireNotNull(
                ViewModelProvider(composeRule.activity)[PlayViewModel::class.java].currentCoordinatorForTest(),
            ).state
            assertTrue("cancelled press must not resign the game", runtime.terminal == null)
            val mainline = runtime.gameTree.mainline()
            assertEquals("cancelled press must retain the deterministic opening", 10, mainline.size)
            assertEquals("f8e7", requireNotNull(mainline.last().move).uci)
        }
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

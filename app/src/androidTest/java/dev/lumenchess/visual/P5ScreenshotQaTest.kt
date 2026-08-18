package dev.lumenchess.visual

import android.graphics.Bitmap
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
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.core.chess.Move
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.play.PLAY_ENGINE_STATUS_TEST_TAG
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
        waitForTag("p5-play-overview")
        selectAppearance("dark")
        composeRule.onNodeWithTag("main-tab-play").performClick()
        waitForTag("p5-play-overview")
        capture("00-play-overview.png")

        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
        capture("01-setup.png")

        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForLiveReady()
        seedDeterministicOpening()
        capture("02-stockfish-live.png")

        backToSetup()
        composeRule.onNodeWithText("Stockfish 18").performScrollTo().performClick()
        composeRule.onNodeWithText("Reckless 0.9.0").performClick()
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForLiveReady()
        seedDeterministicOpening()
        composeRule.onNodeWithTag(PLAY_ENGINE_STATUS_TEST_TAG).assertIsDisplayed()
        capture("03-reckless-live.png")

        backToSetup()
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        capture("04-settings.png")

        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        capture("05-play-settings.png")

        composeRule.onNodeWithTag("settings-board-pieces").performClick()
        composeRule.onNodeWithTag("board-preview").assertIsDisplayed()
        capture("06-board.png")

        composeRule.onNodeWithTag("customization-tab-1").performClick()
        composeRule.onNodeWithTag("customization-piece-lumen-vector").assertIsDisplayed()
        capture("07-pieces.png")

        composeRule.onNodeWithTag("customization-tab-2").performClick()
        composeRule.onNodeWithTag("customization-background-lumen-night").assertIsDisplayed()
        capture("08-background.png")

        composeRule.onNodeWithTag("customization-tab-3").performClick()
        composeRule.onNodeWithTag("customization-preset-midnight").performScrollTo().assertIsDisplayed()
        capture("09-presets.png")

        composeRule.onNodeWithTag("customization-back").performClick()
        composeRule.onNodeWithTag("settings-sounds-haptics").performScrollTo().performClick()
        composeRule.onNodeWithTag("sounds-haptics-screen").assertIsDisplayed()
        capture("10-sounds-haptics.png")

        composeRule.onNodeWithTag("sounds-haptics-back").performClick()
        selectAppearanceFromPlaySettings("light")
        composeRule.onNodeWithTag("play-settings-back").performClick()
        waitForTag("settings-category-list")
        capture("11-light.png")

        composeRule.onNodeWithTag("settings-play").performClick()
        selectAppearanceFromPlaySettings("oled_dark")
        composeRule.onNodeWithTag("play-settings-back").performClick()
        waitForTag("settings-category-list")
        capture("12-oled.png")
    }

    private fun selectAppearance(id: String) {
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        selectAppearanceFromPlaySettings(id)
        composeRule.onNodeWithTag("play-settings-back").performClick()
        waitForTag("settings-category-list")
    }

    private fun selectAppearanceFromPlaySettings(id: String) {
        composeRule.onNodeWithTag("appearance-$id").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun backToSetup() {
        composeRule.runOnIdle {
            ViewModelProvider(composeRule.activity)[PlayViewModel::class.java].backToSetup()
        }
        waitForTag(PLAY_SETUP_TEST_TAG)
    }

    /**
     * Drives the real serialized runtime through a fixed legal Ruy Lopez sequence. Engine plies use
     * the pending search identity produced by the runtime, so the board, SAN mainline, revision and
     * last-move state are all authoritative rather than screenshot-only painted strings.
     */
    private fun seedDeterministicOpening() {
        composeRule.runOnIdle {
            val viewModel = ViewModelProvider(composeRule.activity)[PlayViewModel::class.java]
            val coordinator = requireNotNull(viewModel.currentCoordinatorForTest())

            fun human(uci: String) {
                Thread.sleep(550L)
                coordinator.humanMove(Move.parseUci(uci))
            }

            fun engine(uci: String) {
                Thread.sleep(550L)
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

            // Any late real-engine result now carries a stale revision. Refresh the ViewModel projection
            // through an existing harmless runtime command so the screenshot reads the committed state.
            viewModel.cancelPremove()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("O-O").assertIsDisplayed()
    }

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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(root, "p5-screenshots").apply { mkdirs() }
        val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(directory, name)).use { output ->
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Failed to encode $name" }
        }
    }
}

package dev.lumenchess.visual

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.play.PLAY_LIVE_TEST_TAG
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import dev.lumenchess.play.PLAY_START_TEST_TAG
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.play.PlayViewModel
import dev.lumenchess.runtime.RuntimeTerminal
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scripted end-to-end session on the real app and the real Stockfish, with true full-display
 * screenshots (status bar, dialogs and all) and measurements. Opt-in: it only runs in the completion
 * device lane. A failing step is recorded and the session continues, so earlier evidence survives.
 */
@RunWith(AndroidJUnit4::class)
class CompletionPassDeviceQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val out by lazy { File(compose.activity.getExternalFilesDir(null), "completion-qa").apply { mkdirs() } }
    private val notes = StringBuilder()
    private val failures = mutableListOf<String>()
    private val startedAt = SystemClock.elapsedRealtime()
    private val density get() = compose.activity.resources.displayMetrics.density

    @Before fun enabledOnlyInCompletionLane() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("completionQa") == "true")
    }

    @Test fun fullSession() {
        val vm = ViewModelProvider(compose.activity)[PlayViewModel::class.java]
        val metrics = compose.activity.resources.displayMetrics
        note("display ${metrics.widthPixels}x${metrics.heightPixels}px density=${metrics.density} " +
            "(${(metrics.widthPixels / metrics.density).toInt()}x${(metrics.heightPixels / metrics.density).toInt()}dp)")

        step("live game against Stockfish") {
            waitFor("p5-play-overview")
            shot("01-play-overview")
            compose.onNodeWithTag("play-overview-vs-engine").performClick()
            waitFor(PLAY_SETUP_TEST_TAG)
            shot("02-new-game-setup")
            compose.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
            waitFor(PLAY_LIVE_TEST_TAG, 20_000)
            Thread.sleep(700)
            shot("03-live-start")
            measureLiveLayout()

            val before = vm.uiState.value.clock
            note("clock at start: white=${before?.whiteRemainingMillis} black=${before?.blackRemainingMillis}")
            compose.onNodeWithTag("square-e2").performClick()
            compose.onNodeWithTag("square-e4").performClick()
            val movedAt = SystemClock.elapsedRealtime()
            Thread.sleep(300)
            shot("04-engine-thinking")
            val replied = pollUntil(40_000) { moveCount(vm) >= 2 }
            val thinkMs = SystemClock.elapsedRealtime() - movedAt
            val after = vm.uiState.value.clock
            note("engine replied=$replied after ${thinkMs}ms; clocks white=${after?.whiteRemainingMillis} black=${after?.blackRemainingMillis}")
            note("engine consumed clock: ${600_000L - (after?.blackRemainingMillis ?: 600_000L)}ms of its own time")
            check(replied) { "Stockfish never replied" }
            check(thinkMs >= 300) { "engine replied in ${thinkMs}ms, expected a considered think time" }
            Thread.sleep(500)
            shot("05-engine-replied")

            compose.onNodeWithTag("p5-live-action-menu").performClick()
            waitFor("p5-live-menu-dialog")
            shot("06-game-menu")
            compose.onNodeWithTag("p5-live-menu-close").performClick()
            waitGone("p5-live-menu-dialog")

            compose.onNodeWithTag("p5-live-action-draw").performClick()
            Thread.sleep(400)
            shot("07-draw-offer-declined")

            compose.onNodeWithTag("p5-live-action-flip").performClick()
            Thread.sleep(500)
            shot("08-board-flipped")
            compose.onNodeWithTag("p5-live-action-flip").performClick()

            compose.onNodeWithTag("p5-live-action-resign").performClick()
            waitFor("p5-live-resign-dialog")
            shot("09-resign-confirmation")
            compose.onNodeWithTag("p5-live-resign-confirm").performClick()
            waitFor("p5-live-result-dialog")
            Thread.sleep(300)
            shot("10-result-you-lost")
            compose.onNodeWithTag("p5-live-result-close").performClick()
            waitGone("p5-live-result-dialog")
            shot("11-finished-board")
            pollUntil(10_000) { vm.uiState.value.gameId != null }
            note("game persisted id=${vm.uiState.value.gameId}")
        }

        step("insights after a finished game") {
            compose.runOnUiThread { vm.backToSetup() }
            waitFor("main-tab-insights")
            compose.onNodeWithTag("main-tab-insights").performClick()
            waitFor("insights-root")
            pollUntil(10_000) { has("insights-score-card") || has("insights-empty") }
            Thread.sleep(400)
            shot("12-insights")
            note("insights shows score card=${has("insights-score-card")} empty=${has("insights-empty")}")
            check(has("insights-score-card")) { "Insights did not reflect the finished game" }
        }

        step("human runs out of time") {
            compose.onNodeWithTag("main-tab-play").performClick()
            compose.runOnUiThread {
                vm.updateTimeControl(PlayTimeControl(3_000L, 0L))
                vm.startNewGame()
            }
            waitFor(PLAY_LIVE_TEST_TAG, 20_000)
            Thread.sleep(1_500)
            shot("13-live-low-clock")
            val flagged = pollUntil(15_000) { vm.currentCoordinatorForTest()?.state?.terminal is RuntimeTerminal.Timeout }
            note("human flagged=$flagged terminal=${vm.currentCoordinatorForTest()?.state?.terminal}")
            check(flagged) { "human clock never ran out" }
            waitFor("p5-live-result-dialog")
            Thread.sleep(300)
            shot("14-timeout-result")
            compose.onNodeWithTag("p5-live-result-close").performClick()
        }

        step("castling motion") {
            startFromFen(vm, "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
            compose.onNodeWithTag("square-e1").performClick()
            compose.onNodeWithTag("square-g1").performClick()
            motionShots("15-castle")
            Thread.sleep(600)
            shot("15d-castled")
        }

        step("promotion") {
            startFromFen(vm, "7k/P7/8/8/8/8/8/K7 w - - 0 1")
            compose.onNodeWithTag("square-a7").performClick()
            compose.onNodeWithTag("square-a8").performClick()
            waitFor("promotion-choice-queen")
            shot("16-promotion-picker")
            compose.onNodeWithTag("promotion-choice-queen").performClick()
            motionShots("17-promotion")
            Thread.sleep(500)
            shot("17d-promoted")
        }

        step("capture motion") {
            startFromFen(vm, "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
            compose.onNodeWithTag("square-e4").performClick()
            compose.onNodeWithTag("square-d5").performClick()
            motionShots("18-capture")
            Thread.sleep(500)
            shot("18d-captured")
        }

        step("other tabs") {
            compose.runOnUiThread { vm.backToSetup() }
            waitFor("main-tab-arena")
            compose.onNodeWithTag("main-tab-arena").performClick()
            waitFor("arena-setup")
            Thread.sleep(300)
            shot("19-arena")
            compose.onNodeWithTag("main-tab-games").performClick()
            waitFor("library-list")
            Thread.sleep(400)
            shot("20-games")
            compose.onNodeWithTag("main-tab-settings").performClick()
            waitFor("settings-category-list")
            Thread.sleep(300)
            shot("21-settings")
        }

        File(out, "measurements.txt").writeText(notes.toString())
        assertTrue("steps failed: $failures", failures.isEmpty())
    }

    private fun startFromFen(vm: PlayViewModel, fen: String) {
        compose.runOnUiThread { vm.backToSetup() }
        waitFor("main-tab-play")
        compose.runOnUiThread {
            vm.updateTimeControl(PlayTimeControl(600_000L, 0L))
            vm.updateStartingFen(fen)
            vm.startNewGame()
        }
        waitFor(PLAY_LIVE_TEST_TAG, 20_000)
        Thread.sleep(600)
    }

    /** Three rapid frames right after a move, to catch the piece mid-travel. */
    private fun motionShots(prefix: String) {
        Thread.sleep(40)
        quickShot("$prefix-a")
        Thread.sleep(50)
        quickShot("$prefix-b")
        Thread.sleep(50)
        quickShot("$prefix-c")
    }

    private fun measureLiveLayout() {
        fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val root = bounds(PLAY_LIVE_TEST_TAG)
        val shell = bounds("p5-live-shell")
        val board = bounds("play-board-stage")
        val actions = bounds("p5-live-action-strip")
        val status = bounds("p5-live-status-slot")
        fun dp(px: Float) = "%.1f".format(px / density)
        note("live root ${dp(root.width)}x${dp(root.height)}dp; board ${dp(board.width)}x${dp(board.height)}dp " +
            "(${"%.3f".format(board.width / root.width)} of width)")
        note("top margin ${dp(shell.top - root.top)}dp, bottom margin ${dp(root.bottom - actions.bottom)}dp, " +
            "shell->actions ${dp(actions.top - shell.bottom)}dp, status slot ${dp(status.height)}dp, actions ${dp(actions.height)}dp")
        check(kotlin.math.abs(board.width - board.height) <= 1f) { "board is not square: $board" }
        check(kotlin.math.abs((shell.top - root.top) - (root.bottom - actions.bottom)) <= 32f * density) {
            "Live group is not vertically balanced"
        }
    }

    private fun moveCount(vm: PlayViewModel): Int =
        vm.currentCoordinatorForTest()?.state?.gameTree?.mainline()?.size ?: 0

    private fun has(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(tag: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { has(tag) }
    }

    private fun waitGone(tag: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { !has(tag) }
    }

    private fun pollUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            Thread.sleep(50)
        }
        return condition()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        quickShot(name)
    }

    private fun quickShot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun note(text: String) {
        notes.appendLine("[+${SystemClock.elapsedRealtime() - startedAt}ms] $text")
    }

    private fun step(name: String, block: () -> Unit) {
        note("STEP $name")
        try {
            block()
            note("OK   $name")
        } catch (error: Throwable) {
            failures += name
            note("FAIL $name: ${error::class.java.simpleName}: ${error.message}")
            runCatching { quickShot("zz-failure-${name.replace(' ', '-')}") }
        }
        File(out, "measurements.txt").writeText(notes.toString())
    }
}

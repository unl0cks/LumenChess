package dev.lumenchess.visual

import android.graphics.Bitmap
import android.util.Log
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
import dev.lumenchess.board.GroundedPrecisionBoardMotion
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
    private val failureDetails = mutableListOf<String>()
    private val startedAt = SystemClock.elapsedRealtime()
    private val density get() = compose.activity.resources.displayMetrics.density

    @Before fun enabledOnlyInCompletionLane() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("completionQa") == "true")
    }

    @Test fun fullSession() {
        val vm = ViewModelProvider(compose.activity)[PlayViewModel::class.java]
        val metrics = compose.activity.resources.displayMetrics
        val night = compose.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        note("appearance: ${if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"} (system)")
        note("display ${metrics.widthPixels}x${metrics.heightPixels}px density=${metrics.density} " +
            "(${(metrics.widthPixels / metrics.density).toInt()}x${(metrics.heightPixels / metrics.density).toInt()}dp)")

        step("live game against Stockfish") {
            waitFor("p5-play-overview")
            shot("01-play-overview")
            compose.onNodeWithTag("play-overview-vs-engine").performClick()
            waitFor(PLAY_SETUP_TEST_TAG)
            Thread.sleep(700)
            shot("02-new-game-setup")
            compose.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
            waitFor(PLAY_LIVE_TEST_TAG, 20_000)
            Thread.sleep(700)
            shot("03-live-start")
            measureLiveLayout()
            val cues = File(compose.activity.filesDir, "feedback/built-in").listFiles()
                ?.sortedBy { it.name }?.joinToString { "${it.name}(${it.length()}B)" }
            note("built-in sound cues preloaded on disk: ${cues ?: "none"}")

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
            backToPlayOverview(vm)
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

        val motion = GroundedPrecisionBoardMotion
        step("castling motion") {
            startFromFen(vm, "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
            compose.onNodeWithTag("square-e1").performClick()
            captureMotion(vm, "15-castle", motion.castlingDurationMillis) {
                compose.onNodeWithTag("square-g1").performClick()
            }
            check(pollUntil(4_000) { rankOf(vm, 1) == "R4RK1" }) { "castling did not reach the position: ${vm.currentFen()}" }
            note("castling result rank 1 = ${rankOf(vm, 1)}")
        }

        step("promotion") {
            startFromFen(vm, "7k/P7/8/8/8/8/8/K7 w - - 0 1")
            compose.onNodeWithTag("square-a7").performClick()
            compose.onNodeWithTag("square-a8").performClick()
            waitFor("promotion-choice-queen")
            Thread.sleep(300)
            shot("16-promotion-picker")
            captureMotion(vm, "17-promotion", motion.humanMoveDurationMillis + motion.promotionDurationMillis) {
                compose.onNodeWithTag("promotion-choice-queen").performClick()
            }
            check(pollUntil(4_000) { rankOf(vm, 8)?.contains('Q') == true }) { "promotion did not reach the position: ${vm.currentFen()}" }
            note("promotion result rank 8 = ${rankOf(vm, 8)}")
        }

        step("capture motion") {
            startFromFen(vm, "4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
            compose.onNodeWithTag("square-e4").performClick()
            captureMotion(vm, "18-capture", motion.humanMoveDurationMillis) {
                compose.onNodeWithTag("square-d5").performClick()
            }
            check(pollUntil(4_000) { rankOf(vm, 5) == "3P4" }) { "capture did not reach the position: ${vm.currentFen()}" }
            note("capture result rank 5 = ${rankOf(vm, 5)}")
        }

        step("arena live game") {
            backToPlayOverview(vm)
            compose.onNodeWithTag("main-tab-arena").performClick()
            waitFor("arena-setup")
            compose.onNodeWithTag("arena-start").performScrollTo().performClick()
            waitFor("arena-live", 20_000)
            Thread.sleep(4_000)
            shot("22-arena-live")
            val board = compose.onNodeWithTag("arena-board-stage").fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("arena-flip").fetchSemanticsNode().boundsInRoot
            note("arena board ${"%.1f".format(board.width / density)}dp square top=${"%.1f".format(board.top / density)}dp; " +
                "actions top=${"%.1f".format(actions.top / density)}dp (${"%.1f".format((actions.top - board.bottom) / density)}dp under the board)")
            compose.onNodeWithTag("arena-stop").performClick()
        }

        step("other tabs") {
            waitFor("main-tab-arena")
            compose.onNodeWithTag("main-tab-arena").performClick()
            waitFor("arena-setup")
            Thread.sleep(300)
            shot("19-arena-setup")
            compose.onNodeWithTag("main-tab-games").performClick()
            waitFor("library-list")
            Thread.sleep(400)
            shot("20-games")
            compose.onNodeWithTag("main-tab-settings").performClick()
            waitFor("settings-category-list")
            Thread.sleep(300)
            shot("21-settings")
        }

        step("light appearance") {
            // The system default is followed, so the light palette is checked on the same screens.
            shell("cmd uimode night no")
            Thread.sleep(2_000)
            compose.waitForIdle()
            shot("30-light-settings")
            waitFor("main-tab-play")
            compose.onNodeWithTag("main-tab-play").performClick()
            waitFor("p5-play-overview")
            Thread.sleep(500)
            shot("31-light-overview")
            compose.runOnUiThread {
                vm.updateTimeControl(PlayTimeControl(600_000L, 0L))
                vm.updateStartingFen("")
                vm.startNewGame()
            }
            waitFor(PLAY_LIVE_TEST_TAG, 20_000)
            Thread.sleep(800)
            shot("32-light-live")
        }

        preserveEvidence()
        assertTrue("steps failed: ${failureDetails.joinToString(" | ")}", failures.isEmpty())
    }

    private fun startFromFen(vm: PlayViewModel, fen: String) {
        compose.runOnUiThread { vm.backToSetup() }
        compose.waitForIdle()
        compose.runOnUiThread {
            vm.updateTimeControl(PlayTimeControl(600_000L, 0L))
            vm.updateStartingFen(fen)
            vm.startNewGame()
        }
        waitFor(PLAY_LIVE_TEST_TAG, 20_000)
        Thread.sleep(600)
    }

    /**
     * Leaves the finished game and returns to the Play overview, where the tab bar is shown. The New
     * Game page is a focused sub-page that hides the bar, exactly as it does for a person using Back.
     */
    private fun backToPlayOverview(vm: PlayViewModel) {
        compose.runOnUiThread { vm.backToSetup() }
        compose.waitForIdle()
        if (!has("main-tab-play")) {
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        }
        waitFor("main-tab-play")
    }

    private val transientMotionTags = listOf(
        "traveling-piece", "castling-king", "castling-rook",
        "promotion-outgoing-piece", "promotion-promoted-piece", "captured-piece-fade",
    )

    private fun visibleMotionOverlays() = transientMotionTags.filter { tag ->
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /**
     * Runs [trigger] with Compose's clock frozen, pauses the game so the engine's reply cannot
     * supersede the move, waits (in real time) for the board to start its motion plan, then steps the
     * animation to 30%, 60% and 90% of [durationMillis] and screenshots each frame. Stepping the clock
     * by hand is what makes mid-flight frames reproducible; free-running, a 200 ms slide is over
     * before a screenshot can be taken.
     *
     * The piece must still be travelling at 30% and 60%. Recompositions happen throughout (the Live
     * clock ticks every 100 ms), so this also guards against a recomposition cutting the motion short.
     */
    private fun captureMotion(vm: PlayViewModel, prefix: String, durationMillis: Int, trigger: () -> Unit) {
        compose.mainClock.autoAdvance = false
        val midFlight = mutableListOf<List<String>>()
        try {
            trigger()
            compose.runOnUiThread { vm.pause() }
            val triggeredAt = SystemClock.elapsedRealtime()
            var seen = emptyList<String>()
            pollUntil(3_000) {
                compose.mainClock.advanceTimeByFrame()
                seen = visibleMotionOverlays()
                Thread.sleep(10)
                seen.isNotEmpty()
            }
            note("$prefix: overlays $seen after ${SystemClock.elapsedRealtime() - triggeredAt}ms, plan ${durationMillis}ms")
            check(seen.isNotEmpty()) { "$prefix: the board never drew a travelling piece" }
            var elapsed = 0L
            listOf(0.30, 0.60, 0.90).forEachIndexed { index, fraction ->
                val target = (durationMillis * fraction).toLong()
                compose.mainClock.advanceTimeBy(target - elapsed)
                elapsed = target
                Thread.sleep(150) // let the drawn frame reach the display before capturing it
                quickShot("$prefix-${'a' + index}")
                val overlays = visibleMotionOverlays()
                if (index < 2) midFlight += overlays
                note("$prefix frame ${'a' + index} at ${(fraction * 100).toInt()}%: overlays $overlays")
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        check(midFlight.all { it.isNotEmpty() }) { "$prefix: motion ended early, mid-flight overlays $midFlight" }
    }

    private fun rankOf(vm: PlayViewModel, rank: Int): String? =
        vm.currentFen()?.substringBefore(' ')?.split('/')?.getOrNull(8 - rank)

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
        // A small JPEG travels through the CI log, where artifact storage may not be reachable.
        val small = Bitmap.createScaledBitmap(bitmap, bitmap.width * 22 / 100, bitmap.height * 22 / 100, true)
        FileOutputStream(File(out, "$name.jpg")).use { small.compress(Bitmap.CompressFormat.JPEG, 62, it) }
    }

    private fun note(text: String) {
        val line = "[+${SystemClock.elapsedRealtime() - startedAt}ms] $text"
        notes.appendLine(line)
        Log.i("CompletionQA", line)
    }

    /**
     * Gradle uninstalls the app after the run, which deletes its external files. Copy the evidence to
     * shell-owned storage so the lane can still pull it. UiAutomation tokenises the command on spaces
     * and does not understand quoting, so each command is a plain argument list.
     */
    private fun preserveEvidence() {
        File(out, "measurements.txt").writeText(notes.toString())
        runCatching {
            shell("mkdir -p $EVIDENCE_DIR")
            shell("cp -r ${out.absolutePath}/. $EVIDENCE_DIR/")
            shell("chmod -R a+rX $EVIDENCE_DIR")
        }.onFailure { Log.w("CompletionQA", "evidence copy failed: $it") }
    }

    private fun shell(command: String) {
        val output = instrumentation.uiAutomation.executeShellCommand(command).use { pipe ->
            java.io.FileInputStream(pipe.fileDescriptor).use { String(it.readBytes()) }
        }
        if (output.isNotBlank()) Log.w("CompletionQA", "`$command` -> ${output.trim()}")
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
            failureDetails += "$name -> ${error::class.java.simpleName}: ${error.message?.lineSequence()?.firstOrNull()}"
            preserveEvidence()
        }
    }

    private companion object {
        const val EVIDENCE_DIR = "/data/local/tmp/completion-qa"
    }
}

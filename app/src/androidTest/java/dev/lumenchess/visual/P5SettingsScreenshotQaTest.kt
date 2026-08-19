package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
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
import dev.lumenchess.board.CHESSBOARD_TEST_TAG
import dev.lumenchess.play.PLAY_LIVE_TEST_TAG
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import dev.lumenchess.play.PLAY_START_TEST_TAG
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
class P5SettingsScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitScreenshotRun() {
        val enabled = InstrumentationRegistry.getArguments().getString("p5SettingsQa") == "true"
        assumeTrue("P5 Settings screenshot QA runs only from its dedicated workflow step", enabled)
    }

    @Test
    fun defaultHumanVsEngineLivePresentationIsBoardFirst() {
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForTag(PLAY_LIVE_TEST_TAG, timeoutMillis = 12_000L)

        composeRule.onNodeWithTag("main-tab-play").assertDoesNotExist()
        composeRule.onNodeWithTag("p5-live-lower-region").assertDoesNotExist()
        composeRule.onNodeWithTag("p5-live-tabs").assertDoesNotExist()
        composeRule.onNodeWithTag("p5-live-moves-rail").assertDoesNotExist()
        composeRule.onNodeWithTag("p5-live-action-pause").assertDoesNotExist()
        composeRule.onNodeWithText("Moves").assertDoesNotExist()
        composeRule.onNodeWithText("Info").assertDoesNotExist()

        composeRule.onNodeWithTag("p5-live-opponent-row").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-opponent-clock").assertIsDisplayed()
        composeRule.onNodeWithTag(CHESSBOARD_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-player-row").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-player-clock").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-action-resign").assertIsDisplayed()
        composeRule.onNodeWithTag("p5-live-action-exit").assertIsDisplayed()

        val board = bounds(CHESSBOARD_TEST_TAG)
        assertTrue("board-first default must keep the board square: $board", abs(board.width - board.height) <= 1f)
        val screenWidth = composeRule.activity.resources.displayMetrics.widthPixels.toFloat()
        assertTrue("board width ratio=${board.width / screenWidth}", board.width / screenWidth in 0.92f..0.97f)
    }

    @Test
    fun captureSettingsRootReferenceOnly() {
        verifyInterTightRuntimeResource()
        selectDarkAppearanceAndReturnToSettingsRoot()
        assertSettingsRootStructure()
        assertSettingsGeometry()
        logSettingsMetrics()

        capture("04-settings.png")
        capturePressState("settings-category-play")
    }

    private fun selectDarkAppearanceAndReturnToSettingsRoot() {
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        composeRule.onNodeWithTag("play-settings-back").performClick()
        waitForTag("settings-category-list")
    }

    private fun assertSettingsRootStructure() {
        val expected = listOf(
            "Engines",
            "Play",
            "Game Review",
            "Ratings",
            "Accounts & Sync",
            "Advanced",
        )
        expected.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }

        val categoryRows = composeRule.onAllNodesWithTag("settings-category-row").fetchSemanticsNodes()
        assertEquals("Settings root must expose exactly six category rows", 6, categoryRows.size)

        composeRule.onNodeWithText("Make LumenChess yours").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-system").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-dark").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-oled_dark").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-light").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-board-pieces").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-sounds-haptics").assertDoesNotExist()
        composeRule.onNodeWithTag("board-preview").assertDoesNotExist()

        listOf("play", "arena", "games", "insights", "settings").forEach { tab ->
            composeRule.onNodeWithTag("main-tab-$tab").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("settings-root").assertIsDisplayed()
    }

    private fun assertSettingsGeometry() {
        val density = composeRule.activity.resources.displayMetrics.density
        val screenWidth = composeRule.activity.resources.displayMetrics.widthPixels.toFloat()
        val rows = composeRule.onAllNodesWithTag("settings-category-row").fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.top }
        assertEquals(6, rows.size)

        rows.forEachIndexed { index, row ->
            val heightDp = row.height / density
            assertTrue("row $index height=${heightDp}dp expected 66..74", heightDp in 66f..74f)
            assertTrue("row $index width ratio=${row.width / screenWidth}", row.width / screenWidth in 0.91f..0.96f)
            val leftDp = row.left / density
            assertTrue("row $index left=${leftDp}dp", leftDp in 10f..18f)
        }

        val heights = rows.map { it.height / density }
        assertTrue("category row heights must be stable: $heights", (heights.max() - heights.min()) <= 1.2f)
        rows.zipWithNext().forEachIndexed { index, (first, second) ->
            val gapDp = (second.top - first.bottom) / density
            assertTrue("row gap $index=${gapDp}dp expected 5..10", gapDp in 5f..10f)
        }

        val title = bounds("lumen-topbar-title")
        val titleHeightDp = title.height / density
        assertTrue("Settings title height=${titleHeightDp}dp", titleHeightDp in 20f..34f)
        assertTrue("first row must begin close beneath compact title", rows.first().top >= title.bottom - density * 4f)

        val nav = bounds("main-tab-settings")
        assertTrue("last category must remain above root nav", rows.last().bottom <= nav.top - density * 3f)
        val bodyUse = rows.last().bottom / nav.top
        assertTrue("six-row Settings list must substantially occupy the root body; ratio=$bodyUse", bodyUse in 0.53f..0.72f)
    }

    private fun verifyInterTightRuntimeResource() {
        val resources = composeRule.activity.resources
        check(resources.getResourceEntryName(R.font.inter_tight_regular) == "inter_tight_regular")
        val typeface = resources.getFont(R.font.inter_tight_regular)
        check(typeface != Typeface.DEFAULT && typeface != Typeface.DEFAULT_BOLD)
    }

    private fun logSettingsMetrics() {
        val metrics = composeRule.activity.resources.displayMetrics
        println("P5_SETTINGS_METRIC viewport w=${metrics.widthPixels} h=${metrics.heightPixels}")
        println("P5_SETTINGS_METRIC title=${bounds("lumen-topbar-title")}")
        composeRule.onAllNodesWithTag("settings-category-row").fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.top }
            .forEachIndexed { index, bounds -> println("P5_SETTINGS_METRIC row[$index]=$bounds") }
        println("P5_SETTINGS_METRIC nav=${bounds("main-tab-settings")}")
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
            "Settings row REST/PRESSED state must visibly compress; visiblyChangedRatio=$visiblyChangedRatio",
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
        writeBitmap("04-settings-press-state.png", comparison)
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

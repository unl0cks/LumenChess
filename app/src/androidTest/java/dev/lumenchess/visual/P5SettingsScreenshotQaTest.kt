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
        assumeTrue(
            "P5 Settings screenshot QA runs only from its dedicated workflow step",
            InstrumentationRegistry.getArguments().getString("p5SettingsQa") == "true",
        )
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
        assertSettingsApprovedGeometry()
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
        listOf(
            "settings-category-engines",
            "settings-category-play",
            "settings-category-review",
            "settings-category-ratings",
            "settings-category-accounts",
            "settings-category-advanced",
        ).forEach { composeRule.onNodeWithTag(it).assertIsDisplayed() }

        listOf("Engines", "Game Review", "Ratings", "Accounts & Sync", "Advanced").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }

        assertEquals(
            "Settings root must expose exactly six category rows",
            6,
            composeRule.onAllNodesWithTag("settings-category-row").fetchSemanticsNodes().size,
        )
        assertEquals(
            "approved Settings translation must expose exactly six canonical icon wells",
            6,
            composeRule.onAllNodesWithTag("settings-icon-well").fetchSemanticsNodes().size,
        )
        assertEquals(
            "approved Settings translation must expose exactly six canonical icon glyphs",
            6,
            composeRule.onAllNodesWithTag("settings-icon-glyph").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithText("Make LumenChess yours").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-system").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-dark").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-oled_dark").assertDoesNotExist()
        composeRule.onNodeWithTag("appearance-light").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-board-pieces").assertDoesNotExist()
        composeRule.onNodeWithTag("settings-sounds-haptics").assertDoesNotExist()
        composeRule.onNodeWithTag("board-preview").assertDoesNotExist()

        composeRule.onNodeWithTag("main-bottom-nav").assertIsDisplayed()
        assertEquals(
            "approved root navigation must expose five normalized icon glyphs",
            5,
            composeRule.onAllNodesWithTag("main-tab-icon", useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        listOf("play", "arena", "games", "insights", "settings").forEach { tab ->
            composeRule.onNodeWithTag("main-tab-$tab").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("main-tab-settings-well", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("settings-root").assertIsDisplayed()
    }

    private fun assertSettingsApprovedGeometry() {
        val density = composeRule.activity.resources.displayMetrics.density
        val screenWidth = composeRule.activity.resources.displayMetrics.widthPixels.toFloat()
        val rows = composeRule.onAllNodesWithTag("settings-category-row").fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.top }
        assertEquals(6, rows.size)

        rows.forEachIndexed { index, row ->
            val heightDp = row.height / density
            assertTrue("row $index height=${heightDp}dp expected approved 90..96", heightDp in 90f..96f)
            assertTrue("row $index width ratio=${row.width / screenWidth}", row.width / screenWidth in 0.912f..0.928f)
            val leftDp = row.left / density
            assertTrue("row $index left=${leftDp}dp expected approved 16..20", leftDp in 16f..20f)
        }

        val heights = rows.map { it.height / density }
        assertTrue("category row heights must be stable: $heights", (heights.max() - heights.min()) <= 1.2f)
        rows.zipWithNext().forEachIndexed { index, (first, second) ->
            val gapDp = (second.top - first.bottom) / density
            assertTrue("row gap $index=${gapDp}dp expected approved 8..12", gapDp in 8f..12f)
        }

        val root = bounds("settings-root")
        val title = bounds("lumen-topbar-title")
        val titleTopDp = (title.top - root.top) / density
        val titleHeightDp = title.height / density
        assertTrue("Settings title top=${titleTopDp}dp expected approved 39..52", titleTopDp in 39f..52f)
        assertTrue("Settings title height=${titleHeightDp}dp", titleHeightDp in 25f..34f)

        val firstRowTopDp = (rows.first().top - root.top) / density
        assertTrue("first approved row top=${firstRowTopDp}dp expected 124..138", firstRowTopDp in 124f..138f)

        val wells = composeRule.onAllNodesWithTag("settings-icon-well").fetchSemanticsNodes().map { it.boundsInRoot }
        wells.forEachIndexed { index, well ->
            val widthDp = well.width / density
            val heightDp = well.height / density
            assertTrue("icon well $index width=${widthDp}dp expected 45..50", widthDp in 45f..50f)
            assertTrue("icon well $index height=${heightDp}dp expected 45..50", heightDp in 45f..50f)
        }

        val glyphs = composeRule.onAllNodesWithTag("settings-icon-glyph").fetchSemanticsNodes().map { it.boundsInRoot }
        glyphs.forEachIndexed { index, glyph ->
            val widthDp = glyph.width / density
            val heightDp = glyph.height / density
            assertTrue("settings glyph $index width=${widthDp}dp expected 23..27", widthDp in 23f..27f)
            assertTrue("settings glyph $index height=${heightDp}dp expected 23..27", heightDp in 23f..27f)
        }

        val nav = bounds("main-bottom-nav")
        val navHeightDp = nav.height / density
        assertTrue("approved nav height=${navHeightDp}dp expected 78..84", navHeightDp in 78f..84f)
        val navIcons = composeRule.onAllNodesWithTag("main-tab-icon", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        navIcons.forEachIndexed { index, icon ->
            val widthDp = icon.width / density
            val heightDp = icon.height / density
            assertTrue("nav icon $index width=${widthDp}dp expected 23..27", widthDp in 23f..27f)
            assertTrue("nav icon $index height=${heightDp}dp expected 23..27", heightDp in 23f..27f)
        }

        assertTrue("last category must remain above root nav", rows.last().bottom <= nav.top)
        val approvedNegativeSpaceDp = (nav.top - rows.last().bottom) / density
        assertTrue(
            "approved negative space=${approvedNegativeSpaceDp}dp expected 120..150",
            approvedNegativeSpaceDp in 120f..150f,
        )
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
        println("P5_SETTINGS_METRIC root=${bounds("settings-root")}")
        println("P5_SETTINGS_METRIC title=${bounds("lumen-topbar-title")}")
        composeRule.onAllNodesWithTag("settings-category-row").fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .sortedBy { it.top }
            .forEachIndexed { index, rowBounds -> println("P5_SETTINGS_METRIC row[$index]=$rowBounds") }
        println("P5_SETTINGS_METRIC nav=${bounds("main-bottom-nav")}")
    }

    private fun capturePressState(tag: String) {
        composeRule.waitForIdle()
        val targetBounds = bounds(tag)
        val rootBounds = bounds("settings-root")
        val rootNode = composeRule.onNodeWithTag("settings-root")
        val restRoot = rootNode.captureToImage().asAndroidBitmap()
        val node = composeRule.onNodeWithTag(tag)

        val previousAutoAdvance = composeRule.mainClock.autoAdvance
        composeRule.mainClock.autoAdvance = false
        var pointerDown = false
        val pressedRoot = try {
            node.performTouchInput { down(center) }
            pointerDown = true
            // PressInteraction delay, recomposition and the 70 ms press tween are driven by
            // Compose's MainTestClock, not by TouchInjectionScope event timestamps.
            composeRule.mainClock.advanceTimeBy(240L)
            composeRule.waitForIdle()
            rootNode.captureToImage().asAndroidBitmap()
        } finally {
            if (pointerDown) {
                node.performTouchInput { up() }
                composeRule.mainClock.advanceTimeBy(160L)
                composeRule.waitForIdle()
            }
            composeRule.mainClock.autoAdvance = previousAutoAdvance
        }

        val density = composeRule.activity.resources.displayMetrics.density
        val rest = cropAroundTarget(restRoot, rootBounds, targetBounds, paddingPx = (8f * density).toInt())
        val pressed = cropAroundTarget(pressedRoot, rootBounds, targetBounds, paddingPx = (8f * density).toInt())
        val visiblyChangedRatio = countVisiblyDifferentPixels(rest, pressed, threshold = 8).toFloat() /
            (rest.width * rest.height).toFloat()
        assertTrue(
            "Settings row REST/PRESSED material must visibly settle; visiblyChangedRatio=$visiblyChangedRatio",
            visiblyChangedRatio > 0.018f,
        )

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

    private fun cropAroundTarget(bitmap: Bitmap, rootBounds: Rect, targetBounds: Rect, paddingPx: Int): Bitmap {
        val left = (targetBounds.left - rootBounds.left).toInt() - paddingPx
        val top = (targetBounds.top - rootBounds.top).toInt() - paddingPx
        val right = (targetBounds.right - rootBounds.left).toInt() + paddingPx
        val bottom = (targetBounds.bottom - rootBounds.top).toInt() + paddingPx
        val clampedLeft = left.coerceIn(0, bitmap.width - 1)
        val clampedTop = top.coerceIn(0, bitmap.height - 1)
        val clampedRight = right.coerceIn(clampedLeft + 1, bitmap.width)
        val clampedBottom = bottom.coerceIn(clampedTop + 1, bitmap.height)
        return Bitmap.createBitmap(
            bitmap,
            clampedLeft,
            clampedTop,
            clampedRight - clampedLeft,
            clampedBottom - clampedTop,
        )
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

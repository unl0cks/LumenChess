package dev.lumenchess.arena

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.MainActivity
import dev.lumenchess.core.chess.Variant
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArenaNativeQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitM20Run() {
        assumeTrue(
            "M20 native QA runs only from the bounded Arena workflow",
            InstrumentationRegistry.getArguments().getString("m20ArenaQa") == "true",
        )
    }

    @Test
    fun capturePublicLumenStandardAndChess960Arena() {
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("main-tab-arena").performClick()
        waitForTag("arena-setup")
        captureRoot("01-arena-setup.png")

        val viewModel = ViewModelProvider(composeRule.activity)[ArenaViewModel::class.java]
        composeRule.runOnUiThread {
            viewModel.updateOpeningMode(ArenaOpeningMode.RANDOM_OPENING)
            viewModel.updateOpeningHandoff(6)
        }
        composeRule.onNodeWithTag("arena-opening-options").performScrollTo()
        captureRoot("02-arena-opening-setup.png")

        composeRule.runOnUiThread { viewModel.updateOpeningMode(ArenaOpeningMode.NORMAL) }
        composeRule.runOnUiThread { viewModel.startNewArena() }
        waitForProgress(viewModel, minimumRevision = 3L)
        assertNotNull("Arena evaluation should receive correlated rank-one UCI info", viewModel.uiState.value.evaluation)
        val standardBounds = boardBounds()
        captureRoot("03-arena-standard-live.png")
        captureBoard("04-arena-standard-board.png")

        composeRule.runOnUiThread { viewModel.flipBoard() }
        composeRule.waitForIdle()
        assertEquals(standardBounds, boardBounds())
        captureRoot("05-arena-standard-flipped.png")

        composeRule.runOnUiThread {
            viewModel.stopArena()
            viewModel.updateVariant(Variant.CHESS960)
            viewModel.updateChess960Index(42)
            viewModel.startNewArena()
        }
        waitForProgress(viewModel, minimumRevision = 2L)
        val chess960Bounds = boardBounds()
        assertEquals(standardBounds.width, chess960Bounds.width, 0f)
        assertEquals(standardBounds.height, chess960Bounds.height, 0f)
        captureRoot("06-arena-chess960-live.png")
        captureBoard("07-arena-chess960-board.png")

        writeMetadata(standardBounds, chess960Bounds, viewModel)
    }

    private fun waitForProgress(viewModel: ArenaViewModel, minimumRevision: Long) {
        composeRule.waitUntil(timeoutMillis = 35_000) {
            viewModel.uiState.value.mode == ArenaScreenMode.LIVE &&
                (viewModel.uiState.value.runtime?.positionRevision?.value ?: 0L) >= minimumRevision
        }
        waitForTag("arena-live")
        composeRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.evaluation != null }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun captureRoot(name: String) = writeBitmap(name, composeRule.onRoot().captureToImage().asAndroidBitmap())
    private fun captureBoard(name: String) = writeBitmap(
        name,
        composeRule.onNodeWithTag("arena-board-stage").captureToImage().asAndroidBitmap(),
    )

    private fun boardBounds(): Rect = composeRule.onNodeWithTag("arena-board-stage")
        .fetchSemanticsNode().boundsInRoot

    private fun writeBitmap(name: String, bitmap: Bitmap) {
        FileOutputStream(File(outputDirectory(), name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun writeMetadata(standard: Rect, chess960: Rect, viewModel: ArenaViewModel) {
        val metrics = composeRule.activity.resources.displayMetrics
        File(outputDirectory(), "board-bounds.txt").writeText(
            buildString {
                appendLine("viewport=${metrics.widthPixels}x${metrics.heightPixels}")
                appendLine("densityDpi=${metrics.densityDpi}")
                appendLine("standard=$standard")
                appendLine("chess960=$chess960")
                appendLine("deltaWidth=${chess960.width - standard.width}")
                appendLine("deltaHeight=${chess960.height - standard.height}")
                appendLine("revision=${viewModel.uiState.value.runtime?.positionRevision?.value}")
                appendLine("pieceSet=public application selection")
            },
        )
    }

    private fun outputDirectory(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(requireNotNull(context.getExternalFilesDir(null)), "m20-arena-native").apply { mkdirs() }
    }
}

package dev.lumenchess.visual

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.BuildConfig
import dev.lumenchess.MainActivity
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardPalette
import dev.lumenchess.board.PersonalPieceMetadataCodec
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.board.ThemedLumenChessboard
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Position
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.settings.AppAppearance
import dev.lumenchess.settings.AppearanceSettings
import dev.lumenchess.settings.DataStoreAppearanceSettingsRepository
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P6PersonalPieceScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitPersonalRun() {
        val enabled = InstrumentationRegistry.getArguments().getString("p6PersonalQa") == "true"
        assumeTrue("P6 personal-piece QA runs only from an explicit local personal build", enabled)
        assumeTrue("P6 personal-piece QA requires generated local assets", BuildConfig.LUMEN_PERSONAL_ASSETS)
    }

    @Test
    fun capturePersonalPieceSelectorAndCatalogExtents() {
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("appearance-dark").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-board-pieces").performScrollTo().performClick()
        waitForTag("derivative-board-appearance")
        composeRule.onNodeWithTag("customization-tab-1").performClick()
        waitForTag("customization-piece-lumen-vector")

        val metadata = PersonalPieceMetadataCodec.decode(BuildConfig.LUMEN_PERSONAL_PIECE_STYLES)
        assertEquals(39, metadata.size)
        metadata.forEach { style ->
            assertEquals(1, composeRule.onAllNodesWithTag("customization-piece-${style.id}").fetchSemanticsNodes().size)
        }
        captureRoot("selector/01-personal-pieces-top.png")

        composeRule.onNodeWithTag("customization-piece-${metadata.last().id}")
            .performScrollTo()
            .assertIsDisplayed()
        captureRoot("selector/02-personal-pieces-bottom.png")
    }

    @Test
    fun selectedPersonalStylePersistsAcrossActivityRecreationAndDrivesPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = DataStoreAppearanceSettingsRepository.from(context)
        val privateId = "private.chesscom.ejgfv"
        runBlocking { repository.update { it.withPieceSet("lumen-vector") } }

        openPieceSelector()
        composeRule.onNodeWithTag("customization-piece-$privateId").performScrollTo().performClick()
        waitForPiece("piece-a8-$privateId")
        runBlocking {
            withTimeout(5_000L) { repository.settings.first { it.pieceSetId == privateId } }
        }

        composeRule.activityRule.scenario.recreate()
        openPieceSelector()
        waitForPiece("piece-a8-$privateId")

        composeRule.onNodeWithTag("customization-piece-lumen-vector").performScrollTo().performClick()
        runBlocking {
            withTimeout(5_000L) { repository.settings.first { it.pieceSetId == "lumen-vector" } }
        }
    }

    @Test
    fun verifyAllAssetsAndCaptureActualRendererBoards() {
        val metadata = PersonalPieceMetadataCodec.decode(BuildConfig.LUMEN_PERSONAL_PIECE_STYLES)
        assertEquals(39, metadata.size)
        val assets = composeRule.activity.assets
        val tokens = listOf("bb", "bk", "bn", "bp", "bq", "br", "wb", "wk", "wn", "wp", "wq", "wr")
        metadata.forEach { style ->
            tokens.forEach { token ->
                val bitmap = assets.open("${style.assetDirectory}/$token.png").use(BitmapFactory::decodeStream)
                assertNotNull("${style.id}/$token must decode", bitmap)
                check(requireNotNull(bitmap).width > 0 && bitmap.height > 0)
            }
        }

        val selectedStyleId = mutableStateOf(metadata.first().id)
        val boardSize = mutableFloatStateOf(110f)
        val position = mutableStateOf(Position.initial())
        composeRule.activity.setContent {
            LumenTheme(settings = AppearanceSettings(appearance = AppAppearance.DARK)) {
                Box(
                    Modifier.fillMaxSize().background(LumenColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    ThemedLumenChessboard(
                        position = position.value,
                        onMove = {},
                        modifier = Modifier.size(boardSize.floatValue.dp).testTag("p6-personal-board"),
                        input = ChessboardInput(tapEnabled = false, dragEnabled = false),
                        palette = ChessboardPalette.default(),
                        pieceSet = PieceSetCatalog.definition(selectedStyleId.value),
                    )
                }
            }
        }
        waitForTag("p6-personal-board")

        metadata.forEach { style ->
            composeRule.runOnIdle {
                selectedStyleId.value = style.id
                boardSize.floatValue = 110f
                position.value = Position.initial()
            }
            captureNode("matrix/${style.sourceDirectory}.png", "p6-personal-board")
        }

        val technicalSamples = linkedMapOf(
            "neo" to "private.chesscom.ejgfv",
            "real-3d" to "private.chesscom.3d_wood",
            "3d-staunton" to "private.chesscom.3d_staunton",
            "3d-plastic" to "private.chesscom.3d_plastic",
            "3d-chesskid" to "private.chesscom.3d_chesskid",
        )
        technicalSamples.forEach { (label, styleId) ->
            composeRule.runOnIdle {
                selectedStyleId.value = styleId
                boardSize.floatValue = 320f
                position.value = Position.initial()
            }
            captureNode("samples/$label-starting.png", "p6-personal-board")
        }

        composeRule.runOnIdle {
            selectedStyleId.value = "private.chesscom.ejgfv"
            position.value = Fen.parse("r1bq1rk1/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 7")
        }
        captureNode("samples/neo-middlegame.png", "p6-personal-board")

        composeRule.runOnIdle {
            selectedStyleId.value = "lumen-vector"
            position.value = Position.initial()
        }
        captureNode("samples/public-lumen-starting.png", "p6-personal-board")
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun openPieceSelector() {
        waitForTag("p5-play-overview")
        composeRule.onNodeWithTag("main-tab-settings").performClick()
        waitForTag("settings-category-list")
        composeRule.onNodeWithTag("settings-play").performClick()
        waitForTag("play-settings-root")
        composeRule.onNodeWithTag("settings-board-pieces").performScrollTo().performClick()
        waitForTag("derivative-board-appearance")
        composeRule.onNodeWithTag("customization-tab-1").performClick()
        waitForTag("customization-piece-lumen-vector")
    }

    private fun waitForPiece(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun captureRoot(relativeName: String) {
        composeRule.waitForIdle()
        writeBitmap(relativeName, composeRule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun captureNode(relativeName: String, tag: String) {
        composeRule.waitForIdle()
        writeBitmap(relativeName, composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap())
    }

    private fun writeBitmap(relativeName: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = requireNotNull(context.getExternalFilesDir(null))
        val destination = File(root, "p6-personal-screenshots/$relativeName")
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }
}

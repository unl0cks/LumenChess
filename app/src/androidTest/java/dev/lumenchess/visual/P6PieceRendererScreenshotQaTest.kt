package dev.lumenchess.visual

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.board.CHESSBOARD_TEST_TAG
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardPalette
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.board.LumenVectorPieceSet
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.settings.AppAppearance
import dev.lumenchess.settings.AppearanceSettings
import dev.lumenchess.settings.BoardPreview
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P6PieceRendererScreenshotQaTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun requireExplicitScreenshotRun() {
        assumeTrue(
            "P6 piece renderer screenshot QA runs only from the dedicated API-37 workflow",
            InstrumentationRegistry.getArguments().getString("p6PieceQa") == "true",
        )
    }

    @Test
    fun captureContactSheet() {
        composeRule.setContent {
            LumenTheme(AppearanceSettings(appearance = AppAppearance.OLED_DARK)) {
                ContactSheet(Modifier.testTag("p6-contact-sheet"))
            }
        }
        captureNode("p6-contact-sheet.png", "p6-contact-sheet")
    }

    @Test
    fun captureWhiteLightCriticalSheet() {
        composeRule.setContent {
            LumenTheme { WhiteLightCritical(Modifier.testTag("p6-white-light-critical")) }
        }
        captureNode("p6-white-light-critical.png", "p6-white-light-critical")
    }

    @Test
    fun captureBlackMidnightCriticalSheet() {
        composeRule.setContent {
            LumenTheme { BlackMidnightCritical(Modifier.testTag("p6-black-midnight-critical")) }
        }
        captureNode("p6-black-midnight-critical.png", "p6-black-midnight-critical")
    }

    @Test
    fun captureRoundedCellWidthMatrix() {
        composeRule.setContent {
            LumenTheme { RoundingMatrix(Modifier.testTag("p6-rounding-matrix")) }
        }
        captureNode("p6-rounding-matrix.png", "p6-rounding-matrix")
    }

    @Test
    fun captureStartingPosition() {
        captureBoard("p6-starting-position.png", Position.initial())
    }

    @Test
    fun captureRepresentativePosition() {
        captureBoard(
            "p6-representative-position.png",
            Fen.parse("r1bqk2r/1pppbppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQ1RK1 w kq - 4 6"),
        )
    }

    @Test
    fun captureProductionBoardPreview() {
        composeRule.setContent {
            LumenTheme(AppearanceSettings(appearance = AppAppearance.OLED_DARK)) {
                val previewSize = with(LocalDensity.current) { 800.toDp() }
                Box(Modifier.fillMaxSize().background(ComposeColor(0xFF0D0F10)), contentAlignment = Alignment.Center) {
                    BoardPreview(
                        settings = AppearanceSettings(pieceSetId = LumenVectorPieceSet.id),
                        modifier = Modifier.size(previewSize).testTag("p6-production-board-preview"),
                    )
                }
            }
        }
        captureNode("p6-production-board-preview.png", "p6-production-board-preview")
    }

    private fun captureBoard(name: String, position: Position) {
        composeRule.setContent {
            LumenTheme(AppearanceSettings(appearance = AppAppearance.OLED_DARK)) {
                val boardSize = with(LocalDensity.current) { 1280.toDp() }
                Box(Modifier.fillMaxSize().background(ComposeColor(0xFF0D0F10)), contentAlignment = Alignment.Center) {
                    LumenChessboard(
                        position = position,
                        onMove = {},
                        modifier = Modifier.size(boardSize),
                        input = ChessboardInput(tapEnabled = false, dragEnabled = false),
                        palette = ChessboardPalette.default(),
                        pieceSet = LumenVectorPieceSet,
                    )
                }
            }
        }
        captureNode(name, CHESSBOARD_TEST_TAG)
    }

    @Composable
    private fun ContactSheet(modifier: Modifier = Modifier) {
        val cell = pixelDp(160)
        Column(modifier) {
            PieceRow(Color.WHITE, ComposeColor(0xFFE7E6C8), cell)
            PieceRow(Color.WHITE, ComposeColor(0xFF4E8191), cell)
            PieceRow(Color.BLACK, ComposeColor(0xFFE7E6C8), cell)
            PieceRow(Color.BLACK, ComposeColor(0xFF4E8191), cell)
        }
    }

    @Composable
    private fun WhiteLightCritical(modifier: Modifier = Modifier) {
        PieceRow(Color.WHITE, ComposeColor(0xFFE7E6C8), pixelDp(160), modifier)
    }

    @Composable
    private fun BlackMidnightCritical(modifier: Modifier = Modifier) {
        Column(modifier) {
            PieceRow(Color.BLACK, ComposeColor(0xFF394449), pixelDp(160))
            PieceRow(Color.BLACK, ComposeColor(0xFF121719), pixelDp(160))
        }
    }

    @Composable
    private fun RoundingMatrix(modifier: Modifier = Modifier) {
        Column(modifier) {
            listOf(159, 160, 161).forEach { pixels ->
                PieceRow(Color.WHITE, ComposeColor(0xFFE7E6C8), pixelDp(pixels))
                PieceRow(Color.BLACK, ComposeColor(0xFF121719), pixelDp(pixels))
            }
        }
    }

    @Composable
    private fun PieceRow(
        side: Color,
        background: ComposeColor,
        cell: Dp,
        modifier: Modifier = Modifier,
    ) {
        Row(modifier) {
            listOf(
                PieceType.PAWN,
                PieceType.ROOK,
                PieceType.KNIGHT,
                PieceType.BISHOP,
                PieceType.QUEEN,
                PieceType.KING,
            ).forEach { type ->
                Box(Modifier.size(cell).background(background), contentAlignment = Alignment.Center) {
                    LumenVectorPieceSet.Piece(
                        piece = Piece(side, type),
                        tint = if (side == Color.WHITE) ComposeColor(0xFFF0EBDD) else ComposeColor(0xFF202224),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Composable
    private fun pixelDp(pixels: Int): Dp = with(LocalDensity.current) { pixels.toDp() }

    private fun captureNode(name: String, tag: String) {
        composeRule.waitForIdle()
        val bitmap = composeRule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
        writeBitmap(name, bitmap)
    }

    private fun writeBitmap(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = requireNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        val directory = File(root, "p6-piece-screenshots").apply { mkdirs() }
        FileOutputStream(File(directory, name)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Failed to encode $name" }
        }
    }
}

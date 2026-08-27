package dev.lumenchess.visual

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
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
import dev.lumenchess.BuildConfig
import dev.lumenchess.MainActivity
import dev.lumenchess.board.BoardFeedbackCheckFrame
import dev.lumenchess.board.BoardFeedbackUnderPiece
import dev.lumenchess.board.BoardHistoryRole
import dev.lumenchess.board.BoardPremoveRole
import dev.lumenchess.board.BoardSquareFeedback
import dev.lumenchess.board.ChessboardArrow
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardPalette
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.play.PLAY_LIVE_TEST_TAG
import dev.lumenchess.play.PLAY_SETUP_TEST_TAG
import dev.lumenchess.play.PLAY_START_TEST_TAG
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlaySide
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.play.PlayViewModel
import dev.lumenchess.settings.DataStoreAppearanceSettingsRepository
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P6BoardFeedbackScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitPersonalRun() {
        assumeTrue(
            "P6 board-feedback QA runs only from the explicit local capture lane",
            InstrumentationRegistry.getArguments().getString("p6BoardFeedbackQa") == "true",
        )
        assumeTrue("Neo and 3D Staunton captures require generated local assets", BuildConfig.LUMEN_PERSONAL_ASSETS)
    }

    @Test
    fun captureExactFeedbackStatesAndRendererCompatibility() {
        val state = mutableStateOf(FeedbackCellState())
        val pieceSetId = mutableStateOf(NEO_ID)
        composeRule.activity.setContent {
            LumenTheme {
                val density = LocalDensity.current
                val cellSize = with(density) { 160.toDp() }
                val palette = ChessboardPalette.default()
                val pieceSet = PieceSetCatalog.definition(pieceSetId.value)
                val current = state.value
                Box(
                    Modifier.fillMaxSize().background(LumenColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(cellSize)
                            .background(if (current.dark) palette.darkSquare else palette.lightSquare)
                            .testTag(FEEDBACK_CELL_TAG),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoardFeedbackUnderPiece(
                            darkSquare = current.dark,
                            feedback = current.feedback,
                            selected = current.selected,
                            legalTarget = current.legal,
                            captureTarget = current.capture,
                            extraHighlight = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                        current.piece?.let { piece ->
                            pieceSet.Piece(
                                piece = piece,
                                tint = if (piece.color == Color.WHITE) palette.whitePiece else palette.blackPiece,
                                modifier = Modifier.fillMaxSize(.90f),
                            )
                        }
                        BoardFeedbackCheckFrame(current.check, Modifier.fillMaxSize())
                    }
                }
            }
        }
        waitForTag(FEEDBACK_CELL_TAG)

        val cases = linkedMapOf(
            "01-selected-empty" to FeedbackCellState(selected = true),
            "02-selected-occupied" to FeedbackCellState(dark = true, piece = WHITE_KNIGHT, selected = true),
            "03-legal-light" to FeedbackCellState(legal = true),
            "04-legal-dark" to FeedbackCellState(dark = true, legal = true),
            "05-capture-light" to FeedbackCellState(piece = BLACK_QUEEN, capture = true),
            "06-capture-dark" to FeedbackCellState(dark = true, piece = BLACK_KNIGHT, capture = true),
            "07-last-origin" to FeedbackCellState(
                piece = WHITE_PAWN,
                feedback = BoardSquareFeedback(history = BoardHistoryRole.ORIGIN),
            ),
            "08-last-destination" to FeedbackCellState(
                dark = true,
                piece = WHITE_BISHOP,
                feedback = BoardSquareFeedback(history = BoardHistoryRole.DESTINATION),
            ),
            "09-check-light" to FeedbackCellState(piece = BLACK_KING, check = true),
            "10-check-dark" to FeedbackCellState(dark = true, piece = BLACK_KING, check = true),
            "11-premove-origin" to FeedbackCellState(
                piece = WHITE_KNIGHT,
                feedback = BoardSquareFeedback(premove = BoardPremoveRole.ORIGIN),
            ),
            "12-premove-destination" to FeedbackCellState(
                dark = true,
                feedback = BoardSquareFeedback(premove = BoardPremoveRole.DESTINATION),
            ),
            "13-first-premove-tap" to FeedbackCellState(
                dark = true,
                piece = WHITE_PAWN,
                feedback = BoardSquareFeedback(premove = BoardPremoveRole.PENDING_ORIGIN),
            ),
            "14-history-check" to FeedbackCellState(
                piece = BLACK_KING,
                feedback = BoardSquareFeedback(history = BoardHistoryRole.DESTINATION),
                check = true,
            ),
        )
        cases.forEach { (name, value) ->
            composeRule.runOnIdle { state.value = value }
            captureNode("states/$name.png", FEEDBACK_CELL_TAG)
        }

        listOf(NEO_ID to "neo", STAUNTON_ID to "3d-staunton", "lumen-vector" to "public-lumen")
            .forEach { (id, label) ->
                composeRule.runOnIdle {
                    pieceSetId.value = id
                    state.value = FeedbackCellState(piece = BLACK_QUEEN, capture = true)
                }
                captureNode("renderer/$label-capture.png", FEEDBACK_CELL_TAG)
                composeRule.runOnIdle {
                    state.value = FeedbackCellState(
                        dark = true,
                        piece = BLACK_KING,
                        feedback = BoardSquareFeedback(history = BoardHistoryRole.DESTINATION),
                        check = true,
                    )
                }
                captureNode("renderer/$label-history-check.png", FEEDBACK_CELL_TAG)
            }
    }

    @Test
    fun captureBoardScaleStressPositions() {
        val position = mutableStateOf(Position.initial())
        val highlights = mutableStateOf(ChessboardHighlights())
        val arrows = mutableStateOf(emptyList<ChessboardArrow>())
        val pieceSetId = mutableStateOf(NEO_ID)
        composeRule.activity.setContent {
            LumenTheme {
                val density = LocalDensity.current
                val boardSize = with(density) { 1280.toDp() }
                Box(
                    Modifier.fillMaxSize().background(LumenColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenChessboard(
                        position = position.value,
                        onMove = {},
                        modifier = Modifier.size(boardSize).testTag(FEEDBACK_BOARD_TAG),
                        input = ChessboardInput(tapEnabled = true, dragEnabled = false),
                        highlights = highlights.value,
                        arrows = arrows.value,
                        palette = ChessboardPalette.default(),
                        pieceSet = PieceSetCatalog.definition(pieceSetId.value),
                    )
                }
            }
        }
        waitForTag(FEEDBACK_BOARD_TAG)

        composeRule.onNodeWithTag("square-e2").performClick()
        captureNode("boards/01-starting-selected.png", FEEDBACK_BOARD_TAG)

        composeRule.runOnIdle {
            position.value = MIDDLEGAME
            highlights.value = ChessboardHighlights(
                lastMove = Move.parseUci("d7d6"),
                premove = Move.parseUci("f2g3"),
            )
            arrows.value = listOf(ChessboardArrow(Square.parse("c4"), Square.parse("f7")))
        }
        composeRule.onNodeWithTag("square-c4").performClick()
        captureNode("boards/02-combined-stress.png", FEEDBACK_BOARD_TAG)
        captureNode("boards/03-middlegame.png", FEEDBACK_BOARD_TAG)

        composeRule.runOnIdle {
            position.value = CHECK_ENDGAME
            highlights.value = ChessboardHighlights(lastMove = Move.parseUci("e7e8"))
            arrows.value = emptyList()
        }
        captureNode("boards/04-sparse-endgame.png", FEEDBACK_BOARD_TAG)

        composeRule.runOnIdle {
            position.value = MIDDLEGAME
            highlights.value = ChessboardHighlights(
                lastMove = Move.parseUci("d7d6"),
                premove = Move.parseUci("f2g3"),
            )
        }
        composeRule.onNodeWithTag("square-c4").performClick()
        listOf(NEO_ID to "neo", STAUNTON_ID to "3d-staunton", "lumen-vector" to "public-lumen")
            .forEach { (id, label) ->
                composeRule.runOnIdle { pieceSetId.value = id }
                captureNode("boards/renderer-$label.png", FEEDBACK_BOARD_TAG)
            }
    }

    @Test
    fun captureActualNeoLiveShellWithSelectionAndLegalDestinations() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            DataStoreAppearanceSettingsRepository.from(context).update { current -> current.withPieceSet(NEO_ID) }
        }
        composeRule.activityRule.scenario.recreate()
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
        composeRule.onNodeWithTag("play-overview-vs-engine").performClick()
        waitForTag(PLAY_SETUP_TEST_TAG)
        composeRule.onNodeWithTag(PLAY_START_TEST_TAG).performScrollTo().performClick()
        waitForTag(PLAY_LIVE_TEST_TAG, timeoutMillis = 12_000L)
        composeRule.onNodeWithTag("square-e2").performClick()
        captureRoot("live/01-phone-scale-live.png")
        captureNode("live/02-native-board.png", FEEDBACK_CHESSBOARD_TAG)
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.waitForIdle()
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
        val destination = File(
            requireNotNull(context.getExternalFilesDir(null)),
            "p6-board-feedback-screenshots/$relativeName",
        )
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private data class FeedbackCellState(
        val dark: Boolean = false,
        val piece: Piece? = null,
        val feedback: BoardSquareFeedback = BoardSquareFeedback(),
        val selected: Boolean = false,
        val legal: Boolean = false,
        val capture: Boolean = false,
        val check: Boolean = false,
    )

    private companion object {
        const val NEO_ID = "private.chesscom.ejgfv"
        const val STAUNTON_ID = "private.chesscom.3d_staunton"
        const val FEEDBACK_CELL_TAG = "p6-board-feedback-cell"
        const val FEEDBACK_BOARD_TAG = "p6-board-feedback-board"
        const val FEEDBACK_CHESSBOARD_TAG = "lumen-chessboard"

        val WHITE_PAWN = Piece(Color.WHITE, PieceType.PAWN)
        val WHITE_KNIGHT = Piece(Color.WHITE, PieceType.KNIGHT)
        val WHITE_BISHOP = Piece(Color.WHITE, PieceType.BISHOP)
        val BLACK_KNIGHT = Piece(Color.BLACK, PieceType.KNIGHT)
        val BLACK_QUEEN = Piece(Color.BLACK, PieceType.QUEEN)
        val BLACK_KING = Piece(Color.BLACK, PieceType.KING)

        val MIDDLEGAME = Fen.parse("r1bq1rk1/ppp2ppp/2np1n2/2b1p3/2B1P3/2NP1N2/PPP2PPP/R1BQ1RK1 w - - 0 7")
        val CHECK_ENDGAME = Fen.parse("4k3/8/8/8/8/8/4R3/4K3 b - - 0 1")
    }
}

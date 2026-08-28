package dev.lumenchess.visual

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.BuildConfig
import dev.lumenchess.MainActivity
import dev.lumenchess.board.BoardMovePresentation
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.GroundedPrecisionBoardMotion
import dev.lumenchess.board.HeldPieceOverlay
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.board.PieceSet
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.board.PromotionPolicy
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit local-only native capture lane for the approved P6.4 motion checkpoint. */
@RunWith(AndroidJUnit4::class)
class P6BoardMotionScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun requireExplicitPersonalRun() {
        assumeTrue(
            "P6 motion QA runs only from the explicit local capture lane",
            InstrumentationRegistry.getArguments().getString("p6BoardMotionQa") == "true",
        )
        assumeTrue("Neo and 3D Staunton captures require generated local assets", BuildConfig.LUMEN_PERSONAL_ASSETS)
    }

    @Test
    fun captureHeldPieceAndAlphaShadowAcrossRenderers() {
        val pieceSetId = mutableStateOf(NEO_ID)
        val heldFraction = mutableStateOf(0f)
        val oldShadow = mutableStateOf(false)
        composeRule.activity.setContent {
            LumenTheme {
                val density = LocalDensity.current
                val boardDp = with(density) { BOARD_PX.toDp() }
                val cellPx = BOARD_PX / 8f
                val cellDp = with(density) { cellPx.toDp() }
                val pieceSet = PieceSetCatalog.definition(pieceSetId.value)
                Box(
                    Modifier.fillMaxSize().background(LumenColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(boardDp).testTag(MOTION_BOARD_TAG)) {
                        LumenChessboard(
                            position = HELD_BOARD,
                            onMove = {},
                            modifier = Modifier.fillMaxSize(),
                            pieceSet = pieceSet,
                        )
                        if (oldShadow.value) {
                            RejectedRectangularHeldPiece(
                                pieceSet = pieceSet,
                                cellPx = cellPx,
                                cellDp = cellDp,
                                position = Offset(cellPx * 4.5f, cellPx * 4.25f),
                            )
                        } else {
                            HeldPieceOverlay(
                                piece = WHITE_KNIGHT,
                                position = Offset(cellPx * 4.5f, cellPx * 4.25f),
                                cellPx = cellPx,
                                cellDp = cellDp,
                                palette = dev.lumenchess.board.ChessboardPalette.default(),
                                pieceSet = pieceSet,
                                visuals = GroundedPrecisionBoardMotion.dragVisuals(heldFraction.value),
                            )
                        }
                    }
                }
            }
        }

        captureBoard("shadow/01-neo-rest.png")
        composeRule.runOnIdle { heldFraction.value = 1f }
        captureBoard("shadow/02-neo-alpha-held.png")
        composeRule.runOnIdle { oldShadow.value = true }
        captureBoard("shadow/03-neo-rejected-rectangular.png")

        composeRule.runOnIdle {
            oldShadow.value = false
            pieceSetId.value = STAUNTON_ID
        }
        captureBoard("shadow/04-3d-staunton-alpha-held.png")
        composeRule.runOnIdle { pieceSetId.value = "lumen-vector" }
        captureBoard("shadow/05-public-lumen-alpha-held.png")
    }

    @Test
    fun captureNativeTravelCapturePremoveAndCancellationFrames() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Position.initial())
        val lastMove = mutableStateOf<Move?>(null)
        val presentation = mutableStateOf(BoardMovePresentation.HUMAN_TAP)
        val revision = mutableLongStateOf(0L)
        val orientation = mutableStateOf(ChessboardOrientation.WHITE)
        composeRule.activity.setContent {
            LumenTheme {
                val density = LocalDensity.current
                val boardDp = with(density) { BOARD_PX.toDp() }
                Box(
                    Modifier.fillMaxSize().background(LumenColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenChessboard(
                        position = position.value,
                        onMove = {},
                        modifier = Modifier.size(boardDp).testTag(MOTION_BOARD_TAG),
                        orientation = orientation.value,
                        highlights = ChessboardHighlights(
                            lastMove = lastMove.value,
                            positionRevision = revision.longValue,
                            movePresentation = presentation.value,
                        ),
                        pieceSet = PieceSetCatalog.definition(NEO_ID),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        fun reset(next: Position) {
            composeRule.runOnIdle {
                position.value = next
                lastMove.value = null
                presentation.value = BoardMovePresentation.HUMAN_TAP
                revision.longValue += 1L
                orientation.value = ChessboardOrientation.WHITE
            }
            composeRule.mainClock.advanceTimeByFrame()
        }

        fun transition(
            folder: String,
            move: Move,
            source: BoardMovePresentation,
            frameTimes: List<Long>,
        ) {
            composeRule.runOnIdle {
                position.value = MoveGenerator.applyLegalMove(position.value, move)
                lastMove.value = move
                presentation.value = source
                revision.longValue += 1L
            }
            captureTimedFrames(folder, frameTimes)
        }

        reset(Position.initial())
        transition(
            folder = "travel/human-tap",
            move = Move.parseUci("e2e4"),
            source = BoardMovePresentation.HUMAN_TAP,
            frameTimes = listOf(16L, 48L, 80L, 112L, 160L),
        )
        transition(
            folder = "travel/engine",
            move = Move.parseUci("e7e5"),
            source = BoardMovePresentation.ENGINE,
            frameTimes = listOf(16L, 48L, 80L, 112L, 160L, 176L),
        )

        reset(Fen.parse("7k/8/8/3p4/4P3/8/8/7K w - - 0 1"))
        transition(
            folder = "capture",
            move = Move.parseUci("e4d5"),
            source = BoardMovePresentation.HUMAN_TAP,
            frameTimes = listOf(16L, 32L, 48L, 64L, 96L, 160L),
        )

        reset(Fen.parse("7k/8/8/8/8/8/4P3/7K w - - 0 1"))
        transition(
            folder = "premove",
            move = Move.parseUci("e2e4"),
            source = BoardMovePresentation.PREMOVE,
            frameTimes = listOf(16L, 48L, 80L, 112L, 128L),
        )

        reset(Position.initial())
        composeRule.runOnIdle {
            val move = Move.parseUci("e2e4")
            position.value = MoveGenerator.applyLegalMove(position.value, move)
            lastMove.value = move
            presentation.value = BoardMovePresentation.HUMAN_TAP
            revision.longValue += 1L
        }
        composeRule.mainClock.advanceTimeBy(48L)
        captureBoard("flip/01-before-flip.png")
        composeRule.runOnIdle { orientation.value = ChessboardOrientation.BLACK }
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("flip/02-authoritative-after-flip.png")
    }

    @Test
    fun captureAtomicCastlingAndPromotionSanity() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(STANDARD_CASTLE)
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        composeRule.activity.setContent {
            LumenTheme {
                val density = LocalDensity.current
                val boardDp = with(density) { BOARD_PX.toDp() }
                Box(
                    Modifier.fillMaxSize().background(LumenColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    LumenChessboard(
                        position = position.value,
                        onMove = { move ->
                            position.value = MoveGenerator.applyLegalMove(position.value, move)
                            lastMove.value = move
                            revision.longValue += 1L
                        },
                        modifier = Modifier.size(boardDp).testTag(MOTION_BOARD_TAG),
                        input = ChessboardInput(promotionPolicy = PromotionPolicy.ALWAYS_ASK),
                        highlights = ChessboardHighlights(
                            lastMove = lastMove.value,
                            positionRevision = revision.longValue,
                            movePresentation = BoardMovePresentation.HUMAN_TAP,
                        ),
                        pieceSet = PieceSetCatalog.definition(NEO_ID),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        fun reset(next: Position) {
            composeRule.runOnIdle {
                position.value = next
                lastMove.value = null
                revision.longValue += 1L
            }
            composeRule.mainClock.advanceTimeByFrame()
        }

        composeRule.onNodeWithTag("square-e1").performClick()
        composeRule.onNodeWithTag("square-g1").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("special/01-standard-castle-atomic.png")

        reset(CHESS960_CASTLE)
        composeRule.onNodeWithTag("square-c1").performClick()
        composeRule.onNodeWithTag("square-a1").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("special/02-chess960-castle-atomic.png")

        reset(PROMOTION)
        composeRule.onNodeWithTag("square-a7").performClick()
        composeRule.onNodeWithTag("square-a8").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("special/03-promotion-picker.png")
        composeRule.onNodeWithTag("promotion-choice-queen").performClick()
        captureTimedFrames("special/promotion-travel", listOf(16L, 48L, 96L, 160L))

        reset(CAPTURE_PROMOTION)
        composeRule.onNodeWithTag("square-g7").performClick()
        composeRule.onNodeWithTag("square-h8").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("special/04-capture-promotion-picker.png")
        composeRule.onNodeWithTag("promotion-choice-queen").performClick()
        captureTimedFrames("special/capture-promotion", listOf(16L, 32L, 64L, 96L, 160L))
    }

    private fun captureTimedFrames(folder: String, frameTimes: List<Long>) {
        var elapsed = 0L
        frameTimes.forEach { target ->
            composeRule.mainClock.advanceTimeBy(target - elapsed)
            elapsed = target
            captureBoard("$folder/${target.toString().padStart(3, '0')}ms.png")
        }
    }

    private fun captureBoard(relativeName: String) {
        composeRule.waitForIdle()
        writeBitmap(relativeName, composeRule.onNodeWithTag(MOTION_BOARD_TAG).captureToImage().asAndroidBitmap())
    }

    private fun writeBitmap(relativeName: String, bitmap: Bitmap) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File(
            requireNotNull(context.getExternalFilesDir(null)),
            "p6-board-motion-native/$relativeName",
        )
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private companion object {
        const val NEO_ID = "private.chesscom.ejgfv"
        const val STAUNTON_ID = "private.chesscom.3d_staunton"
        const val MOTION_BOARD_TAG = "p6-motion-board"
        const val BOARD_PX = 1280

        val WHITE_KNIGHT = Piece(Color.WHITE, PieceType.KNIGHT)
        val HELD_BOARD = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKB1R w KQkq - 0 1")
        val STANDARD_CASTLE = Fen.parse("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        val CHESS960_CASTLE = Fen.parse("7k/8/8/8/8/8/8/R1K5 w A - 0 1", Variant.CHESS960)
        val PROMOTION = Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1")
        val CAPTURE_PROMOTION = Fen.parse("4k2r/6P1/8/8/8/8/8/4K3 w - - 0 1")
    }
}

@androidx.compose.runtime.Composable
private fun RejectedRectangularHeldPiece(
    pieceSet: PieceSet,
    cellPx: Float,
    cellDp: androidx.compose.ui.unit.Dp,
    position: Offset,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cellDp)
                .graphicsLayer {
                    translationX = position.x - cellPx / 2f
                    translationY = position.y - cellPx / 2f
                    scaleX = 1.055f
                    scaleY = 1.055f
                    shadowElevation = 8.dp.toPx()
                }
                .zIndex(5f),
            contentAlignment = Alignment.Center,
        ) {
            pieceSet.Piece(
                piece = Piece(Color.WHITE, PieceType.KNIGHT),
                tint = dev.lumenchess.board.ChessboardPalette.default().whitePiece,
                modifier = Modifier.fillMaxSize(.90f),
            )
        }
    }
}

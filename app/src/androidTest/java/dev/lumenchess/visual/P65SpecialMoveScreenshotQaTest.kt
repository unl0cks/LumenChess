package dev.lumenchess.visual

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
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
import dev.lumenchess.board.BoardMovePresentation
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
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
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit local-only native capture lane for the approved P6.5B checkpoint. */
@RunWith(AndroidJUnit4::class)
class P65SpecialMoveScreenshotQaTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val boundRecords = mutableListOf<BoundRecord>()
    private val rendererRecords = mutableListOf<RendererRecord>()

    @Before
    fun requireExplicitPersonalRun() {
        assumeTrue(
            "P6.5B QA runs only from the explicit local capture lane",
            InstrumentationRegistry.getArguments().getString("p65SpecialMoveQa") == "true",
        )
        assumeTrue("Private renderer evidence requires generated local assets", BuildConfig.LUMEN_PERSONAL_ASSETS)
        assertEquals(NEO_ID, PieceSetCatalog.definition(NEO_ID).id)
        assertEquals(STAUNTON_ID, PieceSetCatalog.definition(STAUNTON_ID).id)
        assertEquals(PUBLIC_LUMEN_ID, PieceSetCatalog.definition(PUBLIC_LUMEN_ID).id)
    }

    @Test
    fun captureStandardAndChess960Castling() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(STANDARD_WHITE_CASTLE)
        val lastMove = mutableStateOf<Move?>(null)
        val presentation = mutableStateOf(BoardMovePresentation.HUMAN_TAP)
        val revision = mutableLongStateOf(0L)
        val orientation = mutableStateOf(ChessboardOrientation.WHITE)
        val pieceSetId = mutableStateOf(NEO_ID)
        setMotionBoard(position, lastMove, presentation, revision, orientation, pieceSetId)

        fun castling(
            folder: String,
            before: Position,
            move: String,
            renderer: String = NEO_ID,
        ) {
            reset(position, lastMove, presentation, revision, orientation, pieceSetId, before, renderer)
            captureBoard("castling/$folder/000-before.png", "$folder-before")
            commit(position, lastMove, presentation, revision, Move.parseUci(move))
            captureTimedFrames(
                folder = "castling/$folder",
                times = listOf(0L, 32L, 80L, 128L, 176L, 192L),
                boundLabel = folder,
            )
        }

        castling("01-white-standard-oo", STANDARD_WHITE_CASTLE, "e1g1")
        castling("02-white-standard-ooo", STANDARD_WHITE_CASTLE, "e1c1")
        castling("03-black-standard-oo", STANDARD_BLACK_CASTLE, "e8g8")
        castling("04-black-standard-ooo", STANDARD_BLACK_CASTLE, "e8c8")
        castling("05-chess960-ordinary", CHESS960_ORDINARY, "b1e1")
        castling("06-chess960-king-static", CHESS960_KING_STATIC, "g1h1")
        castling("07-chess960-rook-static", CHESS960_ROOK_STATIC, "e1f1")

        listOf(
            NEO_ID to "neo",
            STAUNTON_ID to "3d-staunton",
            PUBLIC_LUMEN_ID to "public-lumen",
        ).forEach { (id, label) ->
            rendererRecords += RendererRecord("castling-crossing-$label", id, PieceSetCatalog.definition(id).id)
            castling("08-crossing-$label", CHESS960_CROSSING, "d1c1", id)
        }

        reset(
            position,
            lastMove,
            presentation,
            revision,
            orientation,
            pieceSetId,
            CHESS960_CROSSING,
            NEO_ID,
        )
        commit(position, lastMove, presentation, revision, Move.parseUci("d1c1"))
        composeRule.mainClock.advanceTimeBy(80L)
        captureBoard("cancellation/01-castling-before-flip.png", "castling-before-flip")
        composeRule.runOnIdle { orientation.value = ChessboardOrientation.BLACK }
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("cancellation/02-castling-after-flip.png", "castling-after-flip")

        reset(
            position,
            lastMove,
            presentation,
            revision,
            orientation,
            pieceSetId,
            STANDARD_WHITE_CASTLE,
            NEO_ID,
        )
        commit(position, lastMove, presentation, revision, Move.parseUci("e1g1"))
        composeRule.mainClock.advanceTimeBy(64L)
        captureBoard("cancellation/03-castling-before-new-revision.png", "castling-before-revision")
        commit(position, lastMove, presentation, revision, Move.parseUci("e8e7"))
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("cancellation/04-castling-after-new-revision.png", "castling-after-revision")

        writeBounds("metadata/board-bounds-castling.json")
        writeRendererRecords("metadata/renderer-castling.json")
    }

    @Test
    fun capturePromotionCapturePromotionAndInterruption() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(WHITE_QUIET_PROMOTION)
        val lastMove = mutableStateOf<Move?>(null)
        val presentation = mutableStateOf(BoardMovePresentation.HUMAN_TAP)
        val revision = mutableLongStateOf(0L)
        val orientation = mutableStateOf(ChessboardOrientation.WHITE)
        val pieceSetId = mutableStateOf(NEO_ID)
        setMotionBoard(position, lastMove, presentation, revision, orientation, pieceSetId)

        fun promotion(
            folder: String,
            before: Position,
            move: String,
            source: BoardMovePresentation,
            renderer: String = NEO_ID,
            fullTimeline: Boolean = true,
        ) {
            reset(position, lastMove, presentation, revision, orientation, pieceSetId, before, renderer)
            rendererRecords += RendererRecord("promotion-$folder", renderer, PieceSetCatalog.definition(renderer).id)
            captureBoard("promotion/$folder/000-before.png", "$folder-before")
            commit(position, lastMove, presentation, revision, Move.parseUci(move), source)
            if (fullTimeline) {
                val travelEnd = when (source) {
                    BoardMovePresentation.HUMAN_TAP -> 145L
                    BoardMovePresentation.ENGINE -> 155L
                    BoardMovePresentation.PREMOVE -> 110L
                }
                captureTimedFrames(
                    folder = "promotion/$folder",
                    times = listOf(0L, 32L, 64L, travelEnd, travelEnd + 16L, travelEnd + 40L, travelEnd + 80L, travelEnd + 96L),
                    boundLabel = folder,
                )
            } else {
                composeRule.mainClock.advanceTimeBy(280L)
                composeRule.mainClock.advanceTimeByFrame()
                captureBoard("promotion/$folder/280-final.png", "$folder-final")
            }
        }

        promotion("01-white-queen-human", WHITE_QUIET_PROMOTION, "a7a8q", BoardMovePresentation.HUMAN_TAP)
        promotion("02-white-knight-premove", WHITE_QUIET_PROMOTION, "a7a8n", BoardMovePresentation.PREMOVE)
        promotion("03-white-rook", WHITE_QUIET_PROMOTION, "a7a8r", BoardMovePresentation.HUMAN_TAP, fullTimeline = false)
        promotion("04-white-bishop", WHITE_QUIET_PROMOTION, "a7a8b", BoardMovePresentation.HUMAN_TAP, fullTimeline = false)
        promotion("05-black-queen-engine", BLACK_QUIET_PROMOTION, "a2a1q", BoardMovePresentation.ENGINE)
        promotion("06-black-knight", BLACK_QUIET_PROMOTION, "a2a1n", BoardMovePresentation.HUMAN_TAP, fullTimeline = false)
        promotion("07-black-rook", BLACK_QUIET_PROMOTION, "a2a1r", BoardMovePresentation.HUMAN_TAP, fullTimeline = false)
        promotion("08-black-bishop", BLACK_QUIET_PROMOTION, "a2a1b", BoardMovePresentation.HUMAN_TAP, fullTimeline = false)
        promotion("09-capture-queen", WHITE_CAPTURE_PROMOTION, "g7h8q", BoardMovePresentation.HUMAN_TAP)
        promotion("10-capture-knight", WHITE_CAPTURE_PROMOTION, "g7h8n", BoardMovePresentation.HUMAN_TAP)

        listOf(
            NEO_ID to "neo",
            STAUNTON_ID to "3d-staunton",
            PUBLIC_LUMEN_ID to "public-lumen",
        ).forEach { (id, label) ->
            promotion(
                folder = "11-renderer-$label",
                before = WHITE_QUIET_PROMOTION,
                move = "a7a8q",
                source = BoardMovePresentation.HUMAN_TAP,
                renderer = id,
            )
        }

        reset(
            position,
            lastMove,
            presentation,
            revision,
            orientation,
            pieceSetId,
            WHITE_QUIET_PROMOTION,
            NEO_ID,
        )
        commit(position, lastMove, presentation, revision, Move.parseUci("a7a8q"))
        composeRule.mainClock.advanceTimeBy(176L)
        captureBoard("cancellation/05-promotion-bridge-before-flip.png", "promotion-before-flip")
        composeRule.runOnIdle { orientation.value = ChessboardOrientation.BLACK }
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("cancellation/06-promotion-bridge-after-flip.png", "promotion-after-flip")

        reset(
            position,
            lastMove,
            presentation,
            revision,
            orientation,
            pieceSetId,
            WHITE_QUIET_PROMOTION,
            NEO_ID,
        )
        commit(position, lastMove, presentation, revision, Move.parseUci("a7a8q"))
        composeRule.mainClock.advanceTimeBy(176L)
        captureBoard("cancellation/07-promotion-before-new-revision.png", "promotion-before-revision")
        commit(position, lastMove, presentation, revision, Move.parseUci("h8g7"))
        composeRule.mainClock.advanceTimeByFrame()
        captureBoard("cancellation/08-promotion-after-new-revision.png", "promotion-after-revision")

        writeBounds("metadata/board-bounds-promotion.json")
        writeRendererRecords("metadata/renderer-promotion.json")
    }

    @Test
    fun captureEnPassantRegression() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(EN_PASSANT)
        val lastMove = mutableStateOf<Move?>(null)
        val presentation = mutableStateOf(BoardMovePresentation.HUMAN_TAP)
        val revision = mutableLongStateOf(0L)
        val orientation = mutableStateOf(ChessboardOrientation.WHITE)
        val pieceSetId = mutableStateOf(NEO_ID)
        setMotionBoard(position, lastMove, presentation, revision, orientation, pieceSetId)
        captureBoard("en-passant/000-before.png", "en-passant-before")
        commit(position, lastMove, presentation, revision, Move.parseUci("e5d6"))
        captureTimedFrames(
            folder = "en-passant",
            times = listOf(0L, 16L, 32L, 48L, 64L, 96L, 160L, 176L),
            boundLabel = "en-passant",
        )
        writeBounds("metadata/board-bounds-en-passant.json")
    }

    @Test
    fun captureActualNeoLiveContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            DataStoreAppearanceSettingsRepository.from(context).update { current -> current.withPieceSet(NEO_ID) }
        }
        assertEquals(NEO_ID, PieceSetCatalog.definition(NEO_ID).id)
        rendererRecords += RendererRecord("actual-live", NEO_ID, PieceSetCatalog.definition(NEO_ID).id)
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
        captureRoot("live/01-neo-live-phone-context.png")
        val liveBounds = composeRule.onNodeWithTag(MOTION_CHESSBOARD_TAG).fetchSemanticsNode().boundsInRoot
        boundRecords += BoundRecord(
            "actual-neo-live",
            liveBounds.left,
            liveBounds.top,
            liveBounds.right,
            liveBounds.bottom,
        )
        captureNode("live/02-neo-live-native-board.png", MOTION_CHESSBOARD_TAG)
        writeBounds("metadata/board-bounds-live.json")
        writeRendererRecords("metadata/renderer-live.json")
    }

    private fun setMotionBoard(
        position: MutableState<Position>,
        lastMove: MutableState<Move?>,
        presentation: MutableState<BoardMovePresentation>,
        revision: MutableLongState,
        orientation: MutableState<ChessboardOrientation>,
        pieceSetId: MutableState<String>,
    ) {
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
                        pieceSet = PieceSetCatalog.definition(pieceSetId.value),
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun reset(
        position: MutableState<Position>,
        lastMove: MutableState<Move?>,
        presentation: MutableState<BoardMovePresentation>,
        revision: MutableLongState,
        orientation: MutableState<ChessboardOrientation>,
        pieceSetId: MutableState<String>,
        next: Position,
        renderer: String,
    ) {
        composeRule.runOnIdle {
            position.value = next
            lastMove.value = null
            presentation.value = BoardMovePresentation.HUMAN_TAP
            revision.longValue += 1L
            orientation.value = ChessboardOrientation.WHITE
            pieceSetId.value = renderer
        }
        assertEquals(renderer, PieceSetCatalog.definition(renderer).id)
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun commit(
        position: MutableState<Position>,
        lastMove: MutableState<Move?>,
        presentation: MutableState<BoardMovePresentation>,
        revision: MutableLongState,
        move: Move,
        source: BoardMovePresentation = BoardMovePresentation.HUMAN_TAP,
    ) {
        composeRule.runOnIdle {
            position.value = MoveGenerator.applyLegalMove(position.value, move)
            lastMove.value = move
            presentation.value = source
            revision.longValue += 1L
        }
    }

    private fun captureTimedFrames(folder: String, times: List<Long>, boundLabel: String) {
        var elapsed = 0L
        times.forEach { target ->
            composeRule.mainClock.advanceTimeBy(target - elapsed)
            elapsed = target
            captureBoard(
                "$folder/${target.toString().padStart(3, '0')}ms.png",
                "$boundLabel-${target}ms",
            )
        }
    }

    private fun captureBoard(relativeName: String, boundLabel: String) {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(MOTION_BOARD_TAG)
        val bounds = node.fetchSemanticsNode().boundsInRoot
        boundRecords += BoundRecord(boundLabel, bounds.left, bounds.top, bounds.right, bounds.bottom)
        writeBitmap(relativeName, node.captureToImage().asAndroidBitmap())
    }

    private fun captureRoot(relativeName: String) {
        composeRule.waitForIdle()
        writeBitmap(relativeName, composeRule.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun captureNode(relativeName: String, tag: String) {
        composeRule.waitForIdle()
        writeBitmap(relativeName, composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap())
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun writeBounds(relativeName: String) {
        val entries = boundRecords.joinToString(",\n") { record ->
            """  {"state":"${record.label}","left":${record.left},"top":${record.top},"right":${record.right},"bottom":${record.bottom},"width":${record.right - record.left},"height":${record.bottom - record.top}}"""
        }
        writeText(relativeName, "[\n$entries\n]\n")
    }

    private fun writeRendererRecords(relativeName: String) {
        val entries = rendererRecords.joinToString(",\n") { record ->
            """  {"capture":"${record.capture}","storedId":"${record.storedId}","resolvedId":"${record.resolvedId}"}"""
        }
        writeText(relativeName, "[\n$entries\n]\n")
    }

    private fun writeBitmap(relativeName: String, bitmap: Bitmap) {
        val destination = outputFile(relativeName)
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun writeText(relativeName: String, content: String) {
        val destination = outputFile(relativeName)
        destination.parentFile?.mkdirs()
        destination.writeText(content)
    }

    private fun outputFile(relativeName: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(requireNotNull(context.getExternalFilesDir(null)), "$OUTPUT_ROOT/$relativeName")
    }

    private data class BoundRecord(
        val label: String,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    private data class RendererRecord(
        val capture: String,
        val storedId: String,
        val resolvedId: String,
    )

    private companion object {
        const val NEO_ID = "private.chesscom.ejgfv"
        const val STAUNTON_ID = "private.chesscom.3d_staunton"
        const val PUBLIC_LUMEN_ID = "lumen-vector"
        const val MOTION_BOARD_TAG = "p65-special-move-board"
        const val MOTION_CHESSBOARD_TAG = "lumen-chessboard"
        const val OUTPUT_ROOT = "p65-special-move-native"
        const val BOARD_PX = 1280

        val STANDARD_WHITE_CASTLE = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val STANDARD_BLACK_CASTLE = Fen.parse("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1")
        val CHESS960_ORDINARY = Fen.parse("4k3/8/8/8/8/8/8/RK2R3 w EA - 0 1", Variant.CHESS960)
        val CHESS960_KING_STATIC = Fen.parse("4k3/8/8/8/8/8/8/6KR w H - 0 1", Variant.CHESS960)
        val CHESS960_ROOK_STATIC = Fen.parse("4k3/8/8/8/8/8/8/4KR2 w F - 0 1", Variant.CHESS960)
        val CHESS960_CROSSING = Fen.parse("7k/8/8/8/8/8/8/2RK4 w C - 0 1", Variant.CHESS960)
        val WHITE_QUIET_PROMOTION = Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1")
        val BLACK_QUIET_PROMOTION = Fen.parse("7k/8/8/8/8/8/p7/7K b - - 0 1")
        val WHITE_CAPTURE_PROMOTION = Fen.parse("4k2r/6P1/8/8/8/8/8/4K3 w - - 0 1")
        val EN_PASSANT = Fen.parse("7k/8/8/3pP3/8/8/8/7K w - d6 0 1")
    }
}

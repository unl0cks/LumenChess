package dev.lumenchess.board

import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.design.LumenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LumenChessboardMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun legalDragSettlesContinuouslyWithoutChangingBoardBounds() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Position.initial())
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)
        val initialBounds = boardBounds()

        drag("e2", "e4")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(110L)
        transient("dragged-piece").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(Move.parseUci("e2e4"), lastMove.value) }
        composeRule.onNodeWithContentDescription("e4, White pawn").assertExists()
        assertEquals(initialBounds, boardBounds())
    }

    @Test
    fun illegalDragReturnsForFullDurationThenClearsWithoutRuntimeMutation() {
        composeRule.mainClock.autoAdvance = false
        var emitted: Move? = null
        composeRule.setContent {
            LumenTheme {
                LumenChessboard(Position.initial(), onMove = { emitted = it })
            }
        }
        val initialBounds = boardBounds()

        drag("e2", "e5")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(140L)
        transient("dragged-piece").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, emitted) }
        composeRule.onNodeWithTag("piece-e2-lumen-vector", useUnmergedTree = true).assertExists()
        assertEquals(initialBounds, boardBounds())
    }

    @Test
    fun boardFlipCancelsAStaleTravelOverlay() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Position.initial())
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        val orientation = mutableStateOf(ChessboardOrientation.WHITE)
        composeRule.setContent {
            LumenTheme {
                LumenChessboard(
                    position = position.value,
                    onMove = { move ->
                        position.value = MoveGenerator.applyLegalMove(position.value, move)
                        lastMove.value = move
                        revision.longValue += 1L
                    },
                    orientation = orientation.value,
                    highlights = ChessboardHighlights(
                        lastMove = lastMove.value,
                        positionRevision = revision.longValue,
                        movePresentation = BoardMovePresentation.HUMAN_TAP,
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("square-e2").performClick()
        composeRule.onNodeWithTag("square-e4").performClick()

        composeRule.runOnIdle { orientation.value = ChessboardOrientation.BLACK }
        composeRule.mainClock.advanceTimeByFrame()
        transient("traveling-piece").assertDoesNotExist()
        composeRule.onNodeWithTag("piece-e4-lumen-vector", useUnmergedTree = true).assertExists()
    }

    @Test
    fun standardCastlingTravelsBothPiecesWithoutCanonicalDuplicatesOrBoardMovement() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Fen.parse("4k3/8/8/8/8/8/8/4K2R w K - 0 1"))
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)
        val initialBounds = boardBounds()

        composeRule.onNodeWithTag("square-e1").performClick()
        composeRule.onNodeWithTag("square-g1").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        transient("castling-rook").assertExists()
        transient("castling-king").assertExists()
        composeRule.onNodeWithTag("piece-g1-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("piece-f1-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(initialBounds, boardBounds())

        composeRule.mainClock.advanceTimeBy(180L)
        composeRule.mainClock.advanceTimeByFrame()
        transient("castling-rook").assertDoesNotExist()
        transient("castling-king").assertDoesNotExist()
        composeRule.onNodeWithTag("piece-g1-lumen-vector", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("piece-f1-lumen-vector", useUnmergedTree = true).assertExists()
        assertEquals(initialBounds, boardBounds())
    }

    @Test
    fun chess960ZeroDistanceKingStaysCanonicalWhileOnlyRookTravels() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Fen.parse("4k3/8/8/8/8/8/8/6KR w H - 0 1", dev.lumenchess.core.chess.Variant.CHESS960))
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)

        composeRule.onNodeWithTag("square-g1").performClick()
        composeRule.onNodeWithTag("square-h1").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        transient("castling-king").assertDoesNotExist()
        transient("castling-rook").assertExists()
        composeRule.onNodeWithTag("piece-g1-lumen-vector", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("piece-f1-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun promotionTravelsPawnThenBridgesToCanonicalPromotedPiece() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Fen.parse("7k/P7/8/8/8/8/8/7K w - - 0 1"))
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)

        composeRule.onNodeWithTag("square-a7").performClick()
        composeRule.onNodeWithTag("square-a8").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("promotion-choice-queen").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        transient("traveling-piece").assertExists()
        composeRule.onNodeWithTag("piece-a8-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(145L)
        composeRule.mainClock.advanceTimeByFrame()
        transient("promotion-outgoing-piece").assertExists()
        transient("promotion-promoted-piece").assertExists()
        composeRule.onNodeWithTag("piece-a8-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(96L)
        composeRule.mainClock.advanceTimeByFrame()
        transient("promotion-outgoing-piece").assertDoesNotExist()
        transient("promotion-promoted-piece").assertDoesNotExist()
        composeRule.onNodeWithTag("piece-a8-lumen-vector", useUnmergedTree = true).assertExists()
    }

    @Test
    fun capturePromotionFadesVictimBeforeTheReplacementBridge() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Fen.parse("4k2r/6P1/8/8/8/8/8/4K3 w - - 0 1"))
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)

        composeRule.onNodeWithTag("square-g7").performClick()
        composeRule.onNodeWithTag("square-h8").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("promotion-choice-knight").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        transient("captured-piece-fade").assertExists()
        transient("traveling-piece").assertExists()
        composeRule.mainClock.advanceTimeBy(70L)
        transient("captured-piece-fade").assertDoesNotExist()
        transient("traveling-piece").assertExists()

        composeRule.mainClock.advanceTimeBy(96L)
        composeRule.mainClock.advanceTimeByFrame()
        transient("promotion-promoted-piece").assertExists()
        composeRule.onNodeWithTag("piece-h8-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun enPassantKeepsVictimFadeOnItsRealSquareAndResolvesOnce() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Fen.parse("7k/8/8/3pP3/8/8/8/7K w - d6 0 1"))
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)

        composeRule.onNodeWithTag("square-e5").performClick()
        composeRule.onNodeWithTag("square-d6").performClick()
        composeRule.mainClock.advanceTimeByFrame()

        transient("captured-piece-fade").assertExists()
        transient("traveling-piece").assertExists()
        composeRule.onNodeWithTag("piece-d5-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(180L)
        composeRule.mainClock.advanceTimeByFrame()
        transient("captured-piece-fade").assertDoesNotExist()
        transient("traveling-piece").assertDoesNotExist()
        composeRule.onNodeWithTag("piece-d6-lumen-vector", useUnmergedTree = true).assertExists()
    }

    @Test
    fun newerRevisionCancelsAnActiveCastlingPlan() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Fen.parse("4k3/8/8/8/8/8/8/4K2R w K - 0 1"))
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)

        composeRule.onNodeWithTag("square-e1").performClick()
        composeRule.onNodeWithTag("square-g1").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        transient("castling-king").assertExists()
        transient("castling-rook").assertExists()

        composeRule.runOnIdle {
            val supersedingMove = Move.parseUci("e8e7")
            position.value = MoveGenerator.applyLegalMove(position.value, supersedingMove)
            lastMove.value = supersedingMove
            revision.longValue += 1L
        }
        composeRule.mainClock.advanceTimeByFrame()

        transient("castling-king").assertDoesNotExist()
        transient("castling-rook").assertDoesNotExist()
        composeRule.onNodeWithTag("piece-g1-lumen-vector", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("piece-f1-lumen-vector", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("piece-e7-lumen-vector", useUnmergedTree = true).assertDoesNotExist()
        transient("traveling-piece").assertExists()
    }

    @Test
    fun capturedVictimFadesBeforeAttackerTravelCompletes() {
        composeRule.mainClock.autoAdvance = false
        val position = mutableStateOf(Fen.parse("7k/8/8/3p4/4P3/8/8/7K w - - 0 1"))
        val lastMove = mutableStateOf<Move?>(null)
        val revision = mutableLongStateOf(0L)
        setMotionBoard(position, lastMove, revision)

        composeRule.onNodeWithTag("square-e4").performClick()
        composeRule.onNodeWithTag("square-d5").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(70L)
        transient("captured-piece-fade").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(110L)
        transient("traveling-piece").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(Move.parseUci("e4d5"), lastMove.value) }
        composeRule.onNodeWithContentDescription("d5, White pawn").assertExists()
    }

    private fun setMotionBoard(
        position: androidx.compose.runtime.MutableState<Position>,
        lastMove: androidx.compose.runtime.MutableState<Move?>,
        revision: androidx.compose.runtime.MutableLongState,
    ) {
        composeRule.setContent {
            LumenTheme {
                LumenChessboard(
                    position = position.value,
                    onMove = { move ->
                        position.value = MoveGenerator.applyLegalMove(position.value, move)
                        lastMove.value = move
                        revision.longValue += 1L
                    },
                    highlights = ChessboardHighlights(
                        lastMove = lastMove.value,
                        positionRevision = revision.longValue,
                        movePresentation = BoardMovePresentation.HUMAN_TAP,
                    ),
                )
            }
        }
    }

    private fun drag(from: String, to: String) {
        val source = dev.lumenchess.core.chess.Square.parse(from)
        val target = dev.lumenchess.core.chess.Square.parse(to)
        composeRule.onNodeWithTag(CHESSBOARD_TEST_TAG).performTouchInput {
            val cell = width / 8f
            swipe(
                start = Offset(cell * (source.file + .5f), cell * (7 - source.rank + .5f)),
                end = Offset(cell * (target.file + .5f), cell * (7 - target.rank + .5f)),
                durationMillis = 200L,
            )
        }
    }

    private fun boardBounds() = composeRule.onNodeWithTag(CHESSBOARD_TEST_TAG)
        .fetchSemanticsNode().boundsInRoot

    private fun transient(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)
}

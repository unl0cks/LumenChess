package dev.lumenchess.board

import androidx.compose.ui.graphics.toArgb
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Square
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardFeedbackTest {
    @Test
    fun `160 pixel geometry reproduces the approved quiet classical hybrid`() {
        val geometry = BoardFeedbackGeometry.forSquare(160f)

        assertEquals(28f, geometry.selectionLeg, 0.001f)
        assertEquals(5f, geometry.selectionStroke, 0.001f)
        assertEquals(8f, geometry.selectionInset, 0.001f)
        assertEquals(12f, geometry.legalDotRadius, 0.001f)
        assertEquals(56f, geometry.captureRadius, 0.001f)
        assertEquals(7f, geometry.captureStroke, 0.001f)
        assertEquals(8f, geometry.checkInset, 0.001f)
        assertEquals(5f, geometry.checkStroke, 0.001f)
        assertEquals(70f, geometry.premoveRailLength, 0.001f)
        assertEquals(5f, geometry.premoveRailStroke, 0.001f)
        assertEquals(50f, geometry.pendingPremoveRailLength, 0.001f)
        assertEquals(4f, geometry.pendingPremoveRailStroke, 0.001f)
    }

    @Test
    fun `feedback colors and opacities match the approved tokens`() {
        assertColor(QuietClassicalBoardFeedback.selection, 0x6AD1DC, .94f)
        assertColor(QuietClassicalBoardFeedback.legalMoveLight, 0x173942, .56f)
        assertColor(QuietClassicalBoardFeedback.legalMoveDark, 0xD6F0F2, .56f)
        assertColor(QuietClassicalBoardFeedback.legalCaptureLight, 0x285C64, .72f)
        assertColor(QuietClassicalBoardFeedback.legalCaptureDark, 0xD0EEF0, .72f)
        assertColor(QuietClassicalBoardFeedback.lastMoveOrigin, 0xC9BA61, .18f)
        assertColor(QuietClassicalBoardFeedback.lastMoveDestination, 0xC9BA61, .30f)
        assertColor(QuietClassicalBoardFeedback.check, 0xD96066, .84f)
        assertColor(QuietClassicalBoardFeedback.premove, 0x8398BA, .80f)
        assertColor(QuietClassicalBoardFeedback.pendingPremove, 0x8398BA, .67f)
    }

    @Test
    fun `history premove and pending origin roles remain distinct`() {
        val last = Move.parseUci("e2e4")
        val premove = Move.parseUci("g1f3")
        val pending = Square.parse("b1")
        val highlights = ChessboardHighlights(
            lastMove = last,
            premove = premove,
            pendingPremoveOrigin = pending,
        )

        assertEquals(BoardHistoryRole.ORIGIN, highlights.feedbackFor(last.from).history)
        assertEquals(BoardHistoryRole.DESTINATION, highlights.feedbackFor(last.to).history)
        assertEquals(BoardPremoveRole.ORIGIN, highlights.feedbackFor(premove.from).premove)
        assertEquals(BoardPremoveRole.DESTINATION, highlights.feedbackFor(premove.to).premove)
        assertEquals(BoardPremoveRole.PENDING_ORIGIN, highlights.feedbackFor(pending).premove)
        assertEquals(BoardPremoveRole.NONE, highlights.feedbackFor(Square.parse("a8")).premove)
    }

    private fun assertColor(
        actual: androidx.compose.ui.graphics.Color,
        expectedRgb: Int,
        expectedAlpha: Float,
    ) {
        assertEquals(expectedRgb, actual.toArgb() and 0x00FFFFFF)
        assertTrue(
            kotlin.math.abs(actual.alpha - expectedAlpha) < .001f,
            "expected alpha $expectedAlpha but was ${actual.alpha}",
        )
    }
}

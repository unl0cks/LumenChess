package dev.lumenchess.board

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

internal enum class BoardHistoryRole {
    NONE,
    ORIGIN,
    DESTINATION,
}

internal enum class BoardPremoveRole {
    NONE,
    ORIGIN,
    DESTINATION,
    PENDING_ORIGIN,
}

internal data class BoardSquareFeedback(
    val history: BoardHistoryRole = BoardHistoryRole.NONE,
    val premove: BoardPremoveRole = BoardPremoveRole.NONE,
)

internal object QuietClassicalBoardFeedback {
    val selection = Color(0xFF6AD1DC).copy(alpha = .94f)

    val legalMoveLight = Color(0xFF173942).copy(alpha = .56f)
    val legalMoveDark = Color(0xFFD6F0F2).copy(alpha = .56f)
    val legalCaptureLight = Color(0xFF285C64).copy(alpha = .72f)
    val legalCaptureDark = Color(0xFFD0EEF0).copy(alpha = .72f)

    val lastMoveOrigin = Color(0xFFC9BA61).copy(alpha = .18f)
    val lastMoveDestination = Color(0xFFC9BA61).copy(alpha = .30f)
    val check = Color(0xFFD96066).copy(alpha = .84f)

    val premove = Color(0xFF8398BA).copy(alpha = .80f)
    val pendingPremove = Color(0xFF8398BA).copy(alpha = .67f)
}

internal data class BoardFeedbackGeometry(
    val referencePixel: Float,
    val selectionLeg: Float,
    val selectionStroke: Float,
    val selectionInset: Float,
    val legalDotRadius: Float,
    val captureRadius: Float,
    val captureStroke: Float,
    val checkInset: Float,
    val checkStroke: Float,
    val checkCornerRadius: Float,
    val premoveRailInset: Float,
    val premoveRailLength: Float,
    val premoveRailStroke: Float,
    val pendingPremoveRailLength: Float,
    val pendingPremoveRailStroke: Float,
) {
    companion object {
        fun forSquare(side: Float): BoardFeedbackGeometry {
            val unit = side / REFERENCE_SIDE
            return BoardFeedbackGeometry(
                referencePixel = unit,
                selectionLeg = 28f * unit,
                selectionStroke = 5f * unit,
                selectionInset = 8f * unit,
                legalDotRadius = 12f * unit,
                captureRadius = 56f * unit,
                captureStroke = 7f * unit,
                checkInset = 8f * unit,
                checkStroke = 5f * unit,
                checkCornerRadius = 4f * unit,
                premoveRailInset = 10f * unit,
                premoveRailLength = 70f * unit,
                premoveRailStroke = 5f * unit,
                pendingPremoveRailLength = 50f * unit,
                pendingPremoveRailStroke = 4f * unit,
            )
        }

        private const val REFERENCE_SIDE = 160f
    }
}

@Composable
internal fun BoardFeedbackUnderPiece(
    darkSquare: Boolean,
    feedback: BoardSquareFeedback,
    selected: Boolean,
    legalTarget: Boolean,
    captureTarget: Boolean,
    extraHighlight: Color?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val geometry = BoardFeedbackGeometry.forSquare(side)

        extraHighlight?.let { drawRect(it, size = size) }

        when (feedback.history) {
            BoardHistoryRole.NONE -> Unit
            BoardHistoryRole.ORIGIN -> drawRect(QuietClassicalBoardFeedback.lastMoveOrigin, size = size)
            BoardHistoryRole.DESTINATION -> drawRect(QuietClassicalBoardFeedback.lastMoveDestination, size = size)
        }

        drawPremoveRails(feedback.premove, geometry)

        if (selected) drawSelectionBrackets(geometry)

        when {
            captureTarget -> drawCircle(
                color = if (darkSquare) {
                    QuietClassicalBoardFeedback.legalCaptureDark
                } else {
                    QuietClassicalBoardFeedback.legalCaptureLight
                },
                radius = geometry.captureRadius,
                center = center,
                style = Stroke(width = geometry.captureStroke),
            )

            legalTarget -> drawCircle(
                color = if (darkSquare) {
                    QuietClassicalBoardFeedback.legalMoveDark
                } else {
                    QuietClassicalBoardFeedback.legalMoveLight
                },
                radius = geometry.legalDotRadius,
                center = center,
            )
        }
    }
}

@Composable
internal fun BoardFeedbackCheckFrame(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Canvas(modifier) {
        val side = min(size.width, size.height)
        val geometry = BoardFeedbackGeometry.forSquare(side)
        val farEdge = side - geometry.referencePixel - geometry.checkInset
        drawRoundRect(
            color = QuietClassicalBoardFeedback.check,
            topLeft = Offset(geometry.checkInset, geometry.checkInset),
            size = Size(farEdge - geometry.checkInset, farEdge - geometry.checkInset),
            cornerRadius = CornerRadius(geometry.checkCornerRadius),
            style = Stroke(width = geometry.checkStroke),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPremoveRails(
    role: BoardPremoveRole,
    geometry: BoardFeedbackGeometry,
) {
    if (role == BoardPremoveRole.NONE) return
    val vertical = role != BoardPremoveRole.DESTINATION
    val pending = role == BoardPremoveRole.PENDING_ORIGIN
    val length = if (pending) geometry.pendingPremoveRailLength else geometry.premoveRailLength
    val stroke = if (pending) geometry.pendingPremoveRailStroke else geometry.premoveRailStroke
    val color = if (pending) QuietClassicalBoardFeedback.pendingPremove else QuietClassicalBoardFeedback.premove
    val half = length / 2f
    val farEdge = min(size.width, size.height) - geometry.referencePixel - geometry.premoveRailInset

    if (vertical) {
        drawLine(
            color = color,
            start = Offset(geometry.premoveRailInset, center.y - half),
            end = Offset(geometry.premoveRailInset, center.y + half),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
        drawLine(
            color = color,
            start = Offset(farEdge, center.y - half),
            end = Offset(farEdge, center.y + half),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
    } else {
        drawLine(
            color = color,
            start = Offset(center.x - half, geometry.premoveRailInset),
            end = Offset(center.x + half, geometry.premoveRailInset),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
        drawLine(
            color = color,
            start = Offset(center.x - half, farEdge),
            end = Offset(center.x + half, farEdge),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionBrackets(
    geometry: BoardFeedbackGeometry,
) {
    val near = geometry.selectionInset
    val far = min(size.width, size.height) - geometry.referencePixel - geometry.selectionInset
    val leg = geometry.selectionLeg
    val stroke = geometry.selectionStroke
    val color = QuietClassicalBoardFeedback.selection

    drawLine(color, Offset(near, near + leg), Offset(near, near), stroke, StrokeCap.Butt)
    drawLine(color, Offset(near, near), Offset(near + leg, near), stroke, StrokeCap.Butt)
    drawLine(color, Offset(far - leg, near), Offset(far, near), stroke, StrokeCap.Butt)
    drawLine(color, Offset(far, near), Offset(far, near + leg), stroke, StrokeCap.Butt)
    drawLine(color, Offset(near, far - leg), Offset(near, far), stroke, StrokeCap.Butt)
    drawLine(color, Offset(near, far), Offset(near + leg, far), stroke, StrokeCap.Butt)
    drawLine(color, Offset(far - leg, far), Offset(far, far), stroke, StrokeCap.Butt)
    drawLine(color, Offset(far, far), Offset(far, far - leg), stroke, StrokeCap.Butt)
}

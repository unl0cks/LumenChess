package dev.lumenchess.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.lumenchess.core.chess.Color as ChessColor
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import kotlin.math.min

object LumenVectorPieceSet : PieceSet {
    override val id: String = "lumen-vector"
    override val displayName: String = "Lumen"

    @Composable
    override fun Piece(piece: Piece, tint: Color, modifier: Modifier) {
        LumenPieceArtwork(piece = piece, tint = tint, outlined = false, modifier = modifier)
    }
}

@Composable
internal fun LumenPieceArtwork(
    piece: Piece,
    tint: Color,
    outlined: Boolean,
    modifier: Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawLumenPiece(type = piece.type, side = piece.color, tint = tint, outlined = outlined)
        }
    }
}

private fun DrawScope.drawLumenPiece(
    type: PieceType,
    side: ChessColor,
    tint: Color,
    outlined: Boolean,
) {
    val scale = min(size.width, size.height)
    val ox = (size.width - scale) / 2f
    val oy = (size.height - scale) / 2f
    fun x(value: Float) = ox + scale * value
    fun y(value: Float) = oy + scale * value
    fun point(px: Float, py: Float) = Offset(x(px), y(py))

    val edge = if (side == ChessColor.WHITE) Color(0xFF607184) else Color(0xFF607892)
    val shine = if (side == ChessColor.WHITE) Color.White.copy(alpha = 0.72f) else Color(0xFF9AB0C8).copy(alpha = 0.52f)
    val strokeWidth = scale * if (outlined) 0.052f else 0.034f
    val fill = if (outlined) tint.copy(alpha = 0.13f) else tint

    fun styledPath(path: Path) {
        drawPath(path = path, color = fill)
        drawPath(path = path, color = if (outlined) edge else edge.copy(alpha = 0.72f), style = Stroke(strokeWidth))
    }
    fun styledCircle(cx: Float, cy: Float, radius: Float) {
        drawCircle(color = fill, radius = scale * radius, center = point(cx, cy))
        drawCircle(color = if (outlined) edge else edge.copy(alpha = 0.72f), radius = scale * radius, center = point(cx, cy), style = Stroke(strokeWidth))
    }
    fun base(top: Float = 0.72f) {
        val basePath = Path().apply {
            moveTo(x(0.22f), y(top))
            quadraticBezierTo(x(0.18f), y(top + 0.03f), x(0.20f), y(top + 0.08f))
            lineTo(x(0.80f), y(top + 0.08f))
            quadraticBezierTo(x(0.82f), y(top + 0.03f), x(0.78f), y(top))
            close()
        }
        styledPath(basePath)
        drawLine(shine, point(0.29f, top + 0.025f), point(0.59f, top + 0.025f), scale * 0.018f)
    }
    fun torso(shoulderY: Float = 0.48f, waistY: Float = 0.69f) {
        val body = Path().apply {
            moveTo(x(0.38f), y(shoulderY))
            quadraticBezierTo(x(0.50f), y(shoulderY - 0.045f), x(0.62f), y(shoulderY))
            lineTo(x(0.69f), y(waistY))
            lineTo(x(0.31f), y(waistY))
            close()
        }
        styledPath(body)
        drawLine(shine, point(0.41f, shoulderY + 0.04f), point(0.37f, waistY - 0.035f), scale * 0.018f)
    }

    when (type) {
        PieceType.PAWN -> {
            styledCircle(0.50f, 0.29f, 0.105f)
            torso(0.44f, 0.69f)
            base()
        }
        PieceType.ROOK -> {
            val crown = Path().apply {
                moveTo(x(0.25f), y(0.24f)); lineTo(x(0.25f), y(0.40f))
                lineTo(x(0.31f), y(0.44f)); lineTo(x(0.69f), y(0.44f)); lineTo(x(0.75f), y(0.40f))
                lineTo(x(0.75f), y(0.24f)); lineTo(x(0.65f), y(0.24f)); lineTo(x(0.65f), y(0.33f))
                lineTo(x(0.56f), y(0.33f)); lineTo(x(0.56f), y(0.24f)); lineTo(x(0.44f), y(0.24f))
                lineTo(x(0.44f), y(0.33f)); lineTo(x(0.35f), y(0.33f)); lineTo(x(0.35f), y(0.24f)); close()
            }
            styledPath(crown)
            val body = Path().apply {
                moveTo(x(0.32f), y(0.45f)); lineTo(x(0.68f), y(0.45f)); lineTo(x(0.64f), y(0.69f)); lineTo(x(0.36f), y(0.69f)); close()
            }
            styledPath(body); base()
        }
        PieceType.KNIGHT -> {
            val horse = Path().apply {
                moveTo(x(0.28f), y(0.69f))
                quadraticBezierTo(x(0.34f), y(0.58f), x(0.34f), y(0.48f))
                quadraticBezierTo(x(0.31f), y(0.39f), x(0.40f), y(0.31f))
                lineTo(x(0.53f), y(0.20f)); lineTo(x(0.69f), y(0.31f)); lineTo(x(0.62f), y(0.39f))
                quadraticBezierTo(x(0.75f), y(0.48f), x(0.66f), y(0.60f))
                lineTo(x(0.72f), y(0.69f)); close()
            }
            styledPath(horse)
            drawCircle(shine, scale * 0.025f, point(0.55f, 0.32f))
            drawLine(shine, point(0.42f, 0.38f), point(0.57f, 0.45f), scale * 0.018f)
            base()
        }
        PieceType.BISHOP -> {
            val mitre = Path().apply {
                moveTo(x(0.50f), y(0.18f))
                quadraticBezierTo(x(0.33f), y(0.31f), x(0.39f), y(0.43f))
                quadraticBezierTo(x(0.43f), y(0.50f), x(0.50f), y(0.51f))
                quadraticBezierTo(x(0.57f), y(0.50f), x(0.61f), y(0.43f))
                quadraticBezierTo(x(0.67f), y(0.31f), x(0.50f), y(0.18f)); close()
            }
            styledPath(mitre)
            drawLine(edge, point(0.57f, 0.25f), point(0.43f, 0.42f), scale * 0.035f)
            torso(0.49f, 0.69f); base()
        }
        PieceType.QUEEN -> {
            val crown = Path().apply {
                moveTo(x(0.24f), y(0.43f)); lineTo(x(0.22f), y(0.24f)); lineTo(x(0.38f), y(0.37f))
                lineTo(x(0.50f), y(0.18f)); lineTo(x(0.62f), y(0.37f)); lineTo(x(0.78f), y(0.24f))
                lineTo(x(0.76f), y(0.43f)); quadraticBezierTo(x(0.50f), y(0.52f), x(0.24f), y(0.43f)); close()
            }
            styledPath(crown)
            styledCircle(0.22f, 0.22f, 0.035f); styledCircle(0.50f, 0.16f, 0.035f); styledCircle(0.78f, 0.22f, 0.035f)
            torso(0.48f, 0.69f); base()
        }
        PieceType.KING -> {
            drawLine(edge, point(0.50f, 0.13f), point(0.50f, 0.31f), scale * 0.09f)
            drawLine(tint, point(0.50f, 0.13f), point(0.50f, 0.31f), scale * 0.052f)
            drawLine(edge, point(0.41f, 0.20f), point(0.59f, 0.20f), scale * 0.09f)
            drawLine(tint, point(0.41f, 0.20f), point(0.59f, 0.20f), scale * 0.052f)
            val crown = Path().apply {
                moveTo(x(0.32f), y(0.37f)); quadraticBezierTo(x(0.50f), y(0.28f), x(0.68f), y(0.37f))
                lineTo(x(0.63f), y(0.49f)); lineTo(x(0.37f), y(0.49f)); close()
            }
            styledPath(crown); torso(0.48f, 0.69f); base()
        }
    }
}
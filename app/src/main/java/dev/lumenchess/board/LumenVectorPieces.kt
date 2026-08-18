package dev.lumenchess.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.lumenchess.core.chess.Color as ChessColor
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import kotlin.math.min

/**
 * Project-owned modern Staunton artwork.
 *
 * The default Lumen set deliberately uses classical proportions rather than icon silhouettes:
 * restrained bases, narrow stems, rounded transitions, and subtle directional shading preserve
 * form on both light and blue squares without relying on heavy outlines.
 */
object LumenVectorPieceSet : PieceSet {
    override val id: String = "lumen-vector"
    override val displayName: String = "Lumen"

    @Composable
    override fun Piece(piece: Piece, tint: Color, modifier: Modifier) {
        LumenPieceArtwork(piece, tint, outlined = false, modifier)
    }
}

@Composable
internal fun LumenPieceArtwork(piece: Piece, tint: Color, outlined: Boolean, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawLumenPiece(piece.type, piece.color, tint, outlined) }
    }
}

private fun DrawScope.drawLumenPiece(type: PieceType, side: ChessColor, tint: Color, outlined: Boolean) {
    val art = min(size.width, size.height) * .84f
    val ox = (size.width - art) / 2f
    val oy = (size.height - art) / 2f + art * .008f
    fun x(v: Float) = ox + art * v
    fun y(v: Float) = oy + art * v
    fun p(px: Float, py: Float) = Offset(x(px), y(py))

    val white = side == ChessColor.WHITE
    val body = if (white) Color(0xFFF0EBDD) else Color(0xFF414648)
    val shade = if (white) Color(0xFFC8C4BA) else Color(0xFF292D2F)
    val highlight = if (white) Color(0xFFFFFCF3) else Color(0xFF737D80)
    val edge = if (white) Color(0xFF777D7E) else Color(0xFF202426)
    val fill = if (outlined) tint.copy(alpha = .06f) else body
    val line = if (outlined) tint.copy(alpha = .90f) else edge
    val stroke = art * if (outlined) .020f else .0095f
    val bodyBrush = Brush.horizontalGradient(
        colors = if (white) listOf(highlight, body, shade) else listOf(highlight.copy(alpha = .82f), body, shade),
        startX = x(.28f),
        endX = x(.74f),
    )

    fun filled(path: Path) {
        if (outlined) drawPath(path, fill) else drawPath(path, bodyBrush)
        drawPath(path, line, style = Stroke(stroke, cap = StrokeCap.Round))
    }

    fun ellipseHead(cx: Float, cy: Float, rx: Float, ry: Float) {
        val k = .5522848f
        val path = Path().apply {
            moveTo(x(cx), y(cy - ry))
            cubicTo(x(cx + rx * k), y(cy - ry), x(cx + rx), y(cy - ry * k), x(cx + rx), y(cy))
            cubicTo(x(cx + rx), y(cy + ry * k), x(cx + rx * k), y(cy + ry), x(cx), y(cy + ry))
            cubicTo(x(cx - rx * k), y(cy + ry), x(cx - rx), y(cy + ry * k), x(cx - rx), y(cy))
            cubicTo(x(cx - rx), y(cy - ry * k), x(cx - rx * k), y(cy - ry), x(cx), y(cy - ry))
            close()
        }
        filled(path)
        if (!outlined) drawCircle(highlight.copy(alpha = .45f), art * rx * .19f, p(cx - rx * .30f, cy - ry * .34f))
    }

    fun collar(top: Float, half: Float, height: Float = .055f) {
        val path = Path().apply {
            moveTo(x(.50f - half), y(top + height * .24f))
            quadraticBezierTo(x(.50f), y(top - height * .24f), x(.50f + half), y(top + height * .24f))
            lineTo(x(.50f + half * .91f), y(top + height))
            quadraticBezierTo(x(.50f), y(top + height * 1.16f), x(.50f - half * .91f), y(top + height))
            close()
        }
        filled(path)
    }

    fun stem(top: Float, topHalf: Float, bottom: Float = .73f, bottomHalf: Float = .125f) {
        val path = Path().apply {
            moveTo(x(.50f - topHalf), y(top))
            cubicTo(x(.47f), y(top + .11f), x(.44f), y(bottom - .11f), x(.50f - bottomHalf), y(bottom))
            quadraticBezierTo(x(.50f), y(bottom + .018f), x(.50f + bottomHalf), y(bottom))
            cubicTo(x(.56f), y(bottom - .11f), x(.53f), y(top + .11f), x(.50f + topHalf), y(top))
            close()
        }
        filled(path)
        if (!outlined) {
            drawLine(
                highlight.copy(alpha = if (white) .36f else .24f),
                p(.50f - topHalf * .48f, top + .055f),
                p(.50f - bottomHalf * .45f, bottom - .055f),
                art * .008f,
                StrokeCap.Round,
            )
        }
    }

    fun base(top: Float = .72f, half: Float = .265f) {
        val rim = Path().apply {
            moveTo(x(.50f - half * .78f), y(top))
            quadraticBezierTo(x(.50f), y(top - .021f), x(.50f + half * .78f), y(top))
            lineTo(x(.50f + half * .91f), y(top + .058f))
            quadraticBezierTo(x(.50f), y(top + .076f), x(.50f - half * .91f), y(top + .058f))
            close()
        }
        filled(rim)
        val foot = Path().apply {
            moveTo(x(.50f - half * .94f), y(top + .063f))
            quadraticBezierTo(x(.50f), y(top + .045f), x(.50f + half * .94f), y(top + .063f))
            cubicTo(x(.50f + half), y(top + .108f), x(.50f + half * .88f), y(.875f), x(.50f + half * .78f), y(.89f))
            quadraticBezierTo(x(.50f), y(.906f), x(.50f - half * .78f), y(.89f))
            cubicTo(x(.50f - half * .88f), y(.875f), x(.50f - half), y(top + .108f), x(.50f - half * .94f), y(top + .063f))
            close()
        }
        filled(foot)
        if (!outlined) {
            drawLine(
                highlight.copy(alpha = if (white) .34f else .22f),
                p(.50f - half * .63f, top + .094f),
                p(.50f + half * .32f, top + .076f),
                art * .0075f,
                StrokeCap.Round,
            )
        }
    }

    fun tinyFinial(cx: Float, cy: Float, radius: Float) {
        drawCircle(if (outlined) fill else bodyBrush, art * radius, p(cx, cy))
        drawCircle(line, art * radius, p(cx, cy), style = Stroke(stroke))
    }

    when (type) {
        PieceType.PAWN -> {
            ellipseHead(.50f, .225f, .078f, .080f)
            collar(.325f, .112f, .052f)
            stem(.385f, .069f, .715f, .120f)
            base(.708f, .245f)
        }

        PieceType.ROOK -> {
            val crown = Path().apply {
                moveTo(x(.285f), y(.165f))
                lineTo(x(.355f), y(.165f)); lineTo(x(.355f), y(.245f))
                lineTo(x(.455f), y(.245f)); lineTo(x(.455f), y(.165f))
                lineTo(x(.545f), y(.165f)); lineTo(x(.545f), y(.245f))
                lineTo(x(.645f), y(.245f)); lineTo(x(.645f), y(.165f))
                lineTo(x(.715f), y(.165f))
                lineTo(x(.694f), y(.345f))
                quadraticBezierTo(x(.50f), y(.375f), x(.306f), y(.345f))
                close()
            }
            filled(crown)
            collar(.350f, .205f, .047f)
            val tower = Path().apply {
                moveTo(x(.355f), y(.405f))
                cubicTo(x(.375f), y(.50f), x(.385f), y(.62f), x(.370f), y(.715f))
                quadraticBezierTo(x(.50f), y(.736f), x(.630f), y(.715f))
                cubicTo(x(.615f), y(.62f), x(.625f), y(.50f), x(.645f), y(.405f))
                close()
            }
            filled(tower)
            base(.708f, .272f)
        }

        PieceType.KNIGHT -> {
            val horse = Path().apply {
                moveTo(x(.300f), y(.720f))
                cubicTo(x(.315f), y(.630f), x(.350f), y(.555f), x(.423f), y(.490f))
                cubicTo(x(.355f), y(.455f), x(.350f), y(.390f), x(.382f), y(.325f))
                cubicTo(x(.410f), y(.268f), x(.438f), y(.228f), x(.454f), y(.178f))
                lineTo(x(.470f), y(.105f))
                quadraticBezierTo(x(.520f), y(.150f), x(.535f), y(.222f))
                cubicTo(x(.595f), y(.205f), x(.665f), y(.225f), x(.704f), y(.278f))
                cubicTo(x(.743f), y(.332f), x(.730f), y(.390f), x(.675f), y(.425f))
                cubicTo(x(.635f), y(.451f), x(.593f), y(.462f), x(.552f), y(.484f))
                cubicTo(x(.557f), y(.546f), x(.613f), y(.625f), x(.662f), y(.720f))
                quadraticBezierTo(x(.50f), y(.748f), x(.300f), y(.720f))
                close()
            }
            filled(horse)
            if (!outlined) {
                val mane = if (white) shade.copy(alpha = .62f) else highlight.copy(alpha = .38f)
                val manePath = Path().apply {
                    moveTo(x(.455f), y(.190f))
                    cubicTo(x(.398f), y(.288f), x(.365f), y(.370f), x(.405f), y(.470f))
                }
                drawPath(manePath, mane, style = Stroke(art * .012f, cap = StrokeCap.Round))
            }
            drawCircle(if (outlined) line else edge, art * .012f, p(.596f, .300f))
            drawLine(line.copy(alpha = .72f), p(.640f, .382f), p(.696f, .365f), art * .008f, StrokeCap.Round)
            base(.711f, .275f)
        }

        PieceType.BISHOP -> {
            val head = Path().apply {
                moveTo(x(.50f), y(.105f))
                cubicTo(x(.405f), y(.180f), x(.365f), y(.278f), x(.390f), y(.365f))
                cubicTo(x(.410f), y(.430f), x(.455f), y(.465f), x(.50f), y(.478f))
                cubicTo(x(.545f), y(.465f), x(.590f), y(.430f), x(.610f), y(.365f))
                cubicTo(x(.635f), y(.278f), x(.595f), y(.180f), x(.50f), y(.105f))
                close()
            }
            filled(head)
            drawLine(line, p(.565f, .185f), p(.435f, .388f), art * .014f, StrokeCap.Round)
            collar(.455f, .128f, .050f)
            stem(.518f, .086f, .718f, .126f)
            base(.710f, .258f)
        }

        PieceType.QUEEN -> {
            val crown = Path().apply {
                moveTo(x(.295f), y(.365f))
                cubicTo(x(.330f), y(.320f), x(.350f), y(.278f), x(.365f), y(.222f))
                cubicTo(x(.405f), y(.280f), x(.440f), y(.295f), x(.468f), y(.210f))
                quadraticBezierTo(x(.50f), y(.175f), x(.532f), y(.210f))
                cubicTo(x(.560f), y(.295f), x(.595f), y(.280f), x(.635f), y(.222f))
                cubicTo(x(.650f), y(.278f), x(.670f), y(.320f), x(.705f), y(.365f))
                quadraticBezierTo(x(.50f), y(.405f), x(.295f), y(.365f))
                close()
            }
            filled(crown)
            tinyFinial(.345f, .190f, .026f)
            tinyFinial(.500f, .145f, .027f)
            tinyFinial(.655f, .190f, .026f)
            collar(.383f, .175f, .050f)
            stem(.445f, .102f, .719f, .139f)
            base(.710f, .278f)
        }

        PieceType.KING -> {
            val crossWidth = art * .017f
            drawLine(line, p(.50f, .070f), p(.50f, .235f), crossWidth, StrokeCap.Round)
            drawLine(line, p(.430f, .135f), p(.570f, .135f), crossWidth, StrokeCap.Round)
            if (!outlined) {
                val inner = if (white) highlight else body
                drawLine(inner, p(.50f, .082f), p(.50f, .223f), crossWidth * .46f, StrokeCap.Round)
                drawLine(inner, p(.442f, .135f), p(.558f, .135f), crossWidth * .46f, StrokeCap.Round)
            }
            val crown = Path().apply {
                moveTo(x(.340f), y(.350f))
                cubicTo(x(.355f), y(.280f), x(.420f), y(.235f), x(.50f), y(.230f))
                cubicTo(x(.580f), y(.235f), x(.645f), y(.280f), x(.660f), y(.350f))
                lineTo(x(.635f), y(.410f))
                quadraticBezierTo(x(.50f), y(.435f), x(.365f), y(.410f))
                close()
            }
            filled(crown)
            collar(.413f, .155f, .050f)
            stem(.476f, .094f, .719f, .143f)
            base(.710f, .282f)
        }
    }
}

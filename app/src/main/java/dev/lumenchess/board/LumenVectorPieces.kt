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

    val white = side == ChessColor.WHITE
    val body = if (white) Color(0xFFF0EBDD) else Color(0xFF202224)
    val edge = if (white) Color(0xFF41484B) else Color(0xFF0A0B0C)
    val highlight = if (white) Color(0xFFFFFFFF).copy(alpha = .68f) else Color(0xFF8FA4AA).copy(alpha = .42f)
    val resolvedBody = if (outlined) tint.copy(alpha = .08f) else body
    val resolvedEdge = if (outlined) tint.copy(alpha = .92f) else edge
    val stroke = scale * if (outlined) .044f else .024f

    fun styled(path: Path) {
        drawPath(path, resolvedBody)
        drawPath(path, resolvedEdge, style = Stroke(stroke))
    }
    fun circle(cx: Float, cy: Float, radius: Float) {
        drawCircle(resolvedBody, scale * radius, point(cx, cy))
        drawCircle(resolvedEdge, scale * radius, point(cx, cy), style = Stroke(stroke))
    }
    fun ellipseLike(cx: Float, top: Float, halfWidth: Float, height: Float) {
        val path = Path().apply {
            moveTo(x(cx - halfWidth), y(top + height * .52f))
            quadraticBezierTo(x(cx - halfWidth), y(top), x(cx), y(top))
            quadraticBezierTo(x(cx + halfWidth), y(top), x(cx + halfWidth), y(top + height * .52f))
            quadraticBezierTo(x(cx + halfWidth * .9f), y(top + height), x(cx), y(top + height))
            quadraticBezierTo(x(cx - halfWidth * .9f), y(top + height), x(cx - halfWidth), y(top + height * .52f))
            close()
        }
        styled(path)
    }
    fun base(top: Float = .75f, left: Float = .16f, right: Float = .84f) {
        val upper = Path().apply {
            moveTo(x(left + .08f), y(top))
            quadraticBezierTo(x(.50f), y(top - .035f), x(right - .08f), y(top))
            lineTo(x(right - .02f), y(top + .075f))
            lineTo(x(left + .02f), y(top + .075f))
            close()
        }
        styled(upper)
        val foot = Path().apply {
            moveTo(x(left), y(top + .08f))
            quadraticBezierTo(x(.50f), y(top + .045f), x(right), y(top + .08f))
            lineTo(x(right - .025f), y(.91f))
            quadraticBezierTo(x(.50f), y(.935f), x(left + .025f), y(.91f))
            close()
        }
        styled(foot)
        drawLine(highlight, point(left + .11f, top + .105f), point(.58f, top + .082f), scale * .014f)
    }
    fun stem(top: Float, shoulder: Float = .31f, waist: Float = .70f) {
        val path = Path().apply {
            moveTo(x(.50f - shoulder / 2f), y(top))
            quadraticBezierTo(x(.50f), y(top - .035f), x(.50f + shoulder / 2f), y(top))
            lineTo(x(.65f), y(waist))
            quadraticBezierTo(x(.50f), y(waist + .025f), x(.35f), y(waist))
            close()
        }
        styled(path)
        drawLine(highlight, point(.405f, top + .06f), point(.37f, waist - .035f), scale * .013f)
    }

    when (type) {
        PieceType.PAWN -> {
            circle(.50f, .245f, .115f)
            val collar = Path().apply {
                moveTo(x(.36f), y(.36f)); quadraticBezierTo(x(.50f), y(.33f), x(.64f), y(.36f))
                lineTo(x(.62f), y(.43f)); lineTo(x(.38f), y(.43f)); close()
            }
            styled(collar)
            stem(.42f, .25f, .70f)
            base(.70f, .20f, .80f)
        }

        PieceType.ROOK -> {
            val crown = Path().apply {
                moveTo(x(.19f), y(.15f)); lineTo(x(.31f), y(.15f)); lineTo(x(.31f), y(.25f))
                lineTo(x(.43f), y(.25f)); lineTo(x(.43f), y(.15f)); lineTo(x(.57f), y(.15f))
                lineTo(x(.57f), y(.25f)); lineTo(x(.69f), y(.25f)); lineTo(x(.69f), y(.15f))
                lineTo(x(.81f), y(.15f)); lineTo(x(.79f), y(.39f));
                quadraticBezierTo(x(.50f), y(.44f), x(.21f), y(.39f)); close()
            }
            styled(crown)
            val bodyPath = Path().apply {
                moveTo(x(.29f), y(.40f)); lineTo(x(.71f), y(.40f)); lineTo(x(.66f), y(.73f));
                quadraticBezierTo(x(.50f), y(.75f), x(.34f), y(.73f)); close()
            }
            styled(bodyPath)
            drawLine(highlight, point(.34f,.46f), point(.31f,.68f), scale*.014f)
            base(.72f, .14f, .86f)
        }

        PieceType.KNIGHT -> {
            val horse = Path().apply {
                moveTo(x(.22f), y(.75f))
                quadraticBezierTo(x(.29f), y(.62f), x(.34f), y(.53f))
                quadraticBezierTo(x(.28f), y(.43f), x(.35f), y(.31f))
                lineTo(x(.48f), y(.10f)); lineTo(x(.56f), y(.23f)); lineTo(x(.69f), y(.18f))
                quadraticBezierTo(x(.83f), y(.29f), x(.72f), y(.41f))
                lineTo(x(.61f), y(.49f))
                quadraticBezierTo(x(.70f), y(.61f), x(.75f), y(.75f))
                close()
            }
            styled(horse)
            drawCircle(highlight, scale*.022f, point(.58f,.31f))
            val mane = Path().apply {
                moveTo(x(.36f), y(.31f)); lineTo(x(.29f), y(.45f)); lineTo(x(.38f), y(.43f));
                lineTo(x(.32f), y(.55f)); lineTo(x(.43f), y(.49f));
            }
            drawPath(mane, resolvedEdge, style = Stroke(scale*.018f))
            base(.73f, .14f, .86f)
        }

        PieceType.BISHOP -> {
            val head = Path().apply {
                moveTo(x(.50f), y(.09f))
                quadraticBezierTo(x(.28f), y(.27f), x(.37f), y(.43f))
                quadraticBezierTo(x(.41f), y(.51f), x(.50f), y(.53f))
                quadraticBezierTo(x(.59f), y(.51f), x(.63f), y(.43f))
                quadraticBezierTo(x(.72f), y(.27f), x(.50f), y(.09f)); close()
            }
            styled(head)
            drawLine(resolvedEdge, point(.60f,.20f), point(.42f,.43f), scale*.032f)
            stem(.50f, .34f, .73f)
            base(.72f, .15f, .85f)
        }

        PieceType.QUEEN -> {
            val crown = Path().apply {
                moveTo(x(.20f), y(.38f)); lineTo(x(.16f), y(.17f)); lineTo(x(.34f), y(.31f))
                lineTo(x(.50f), y(.10f)); lineTo(x(.66f), y(.31f)); lineTo(x(.84f), y(.17f))
                lineTo(x(.80f), y(.38f)); quadraticBezierTo(x(.50f), y(.47f), x(.20f), y(.38f)); close()
            }
            styled(crown)
            circle(.16f,.15f,.032f); circle(.50f,.08f,.035f); circle(.84f,.15f,.032f)
            val collar = Path().apply {
                moveTo(x(.28f),y(.40f)); quadraticBezierTo(x(.50f),y(.46f),x(.72f),y(.40f))
                lineTo(x(.68f),y(.48f)); lineTo(x(.32f),y(.48f)); close()
            }
            styled(collar)
            stem(.47f, .37f, .74f)
            base(.73f, .13f, .87f)
        }

        PieceType.KING -> {
            val crossStroke = scale * .075f
            drawLine(resolvedEdge, point(.50f,.055f), point(.50f,.255f), crossStroke)
            drawLine(body, point(.50f,.06f), point(.50f,.25f), scale*.046f)
            drawLine(resolvedEdge, point(.39f,.145f), point(.61f,.145f), crossStroke)
            drawLine(body, point(.395f,.145f), point(.605f,.145f), scale*.046f)
            val crown = Path().apply {
                moveTo(x(.28f),y(.33f)); quadraticBezierTo(x(.50f),y(.23f),x(.72f),y(.33f))
                lineTo(x(.66f),y(.47f)); quadraticBezierTo(x(.50f),y(.50f),x(.34f),y(.47f)); close()
            }
            styled(crown)
            stem(.46f, .38f, .74f)
            base(.73f, .12f, .88f)
        }
    }
}

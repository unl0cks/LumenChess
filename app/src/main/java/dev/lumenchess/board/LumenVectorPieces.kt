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

/** Project-owned slim shaded chess artwork, drawn from original paths. */
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
    val art = min(size.width, size.height) * .88f
    val ox = (size.width-art)/2f
    val oy = (size.height-art)/2f + art*.015f
    fun x(v:Float)=ox+art*v
    fun y(v:Float)=oy+art*v
    fun p(px:Float,py:Float)=Offset(x(px),y(py))

    val white=side==ChessColor.WHITE
    val body=if(white) Color(0xFFF2EFE5) else Color(0xFF383C3E)
    val edge=if(white) Color(0xFF8C9293) else Color(0xFF17191A)
    val shadow=if(white) Color(0xFFB7BAB5) else Color(0xFF202325)
    val highlight=if(white) Color(0xFFFFFEFA) else Color(0xFF7E878A)
    val fill=if(outlined) tint.copy(alpha=.07f) else body
    val line=if(outlined) tint.copy(alpha=.90f) else edge
    val stroke=art*(if(outlined).024f else .0135f)

    fun styled(path:Path,shadowLine:Boolean=true) {
        drawPath(path,fill); drawPath(path,line,style=Stroke(stroke))
        if(!outlined&&shadowLine) drawPath(path,shadow.copy(alpha=.30f),style=Stroke(art*.006f))
    }
    fun circle(cx:Float,cy:Float,r:Float) {
        drawCircle(fill,art*r,p(cx,cy)); drawCircle(line,art*r,p(cx,cy),style=Stroke(stroke))
        if(!outlined) drawCircle(highlight.copy(alpha=.55f),art*r*.22f,p(cx-r*.28f,cy-r*.28f))
    }
    fun collar(top:Float,half:Float) {
        val path=Path().apply {
            moveTo(x(.50f-half),y(top)); quadraticBezierTo(x(.50f),y(top-.025f),x(.50f+half),y(top))
            lineTo(x(.50f+half*.88f),y(top+.07f)); quadraticBezierTo(x(.50f),y(top+.085f),x(.50f-half*.88f),y(top+.07f)); close()
        }; styled(path)
    }
    fun stem(top:Float,topHalf:Float,bottomHalf:Float=.155f,bottom:Float=.72f) {
        val path=Path().apply {
            moveTo(x(.50f-topHalf),y(top)); quadraticBezierTo(x(.46f),y((top+bottom)*.53f),x(.50f-bottomHalf),y(bottom))
            quadraticBezierTo(x(.50f),y(bottom+.025f),x(.50f+bottomHalf),y(bottom)); quadraticBezierTo(x(.54f),y((top+bottom)*.53f),x(.50f+topHalf),y(top)); close()
        }; styled(path)
        if(!outlined) drawLine(highlight.copy(alpha=.45f),p(.43f,top+.08f),p(.40f,bottom-.035f),art*.010f)
    }
    fun base(top:Float=.72f,half:Float=.31f) {
        val upper=Path().apply {
            moveTo(x(.50f-half*.82f),y(top)); quadraticBezierTo(x(.50f),y(top-.028f),x(.50f+half*.82f),y(top))
            lineTo(x(.50f+half*.94f),y(top+.07f)); quadraticBezierTo(x(.50f),y(top+.09f),x(.50f-half*.94f),y(top+.07f)); close()
        }; styled(upper)
        val foot=Path().apply {
            moveTo(x(.50f-half),y(top+.075f)); quadraticBezierTo(x(.50f),y(top+.045f),x(.50f+half),y(top+.075f))
            lineTo(x(.50f+half*.92f),y(.88f)); quadraticBezierTo(x(.50f),y(.905f),x(.50f-half*.92f),y(.88f)); close()
        }; styled(foot)
        if(!outlined) drawLine(highlight.copy(alpha=.40f),p(.50f-half*.72f,top+.096f),p(.56f,top+.075f),art*.009f)
    }

    when(type) {
        PieceType.PAWN -> { circle(.50f,.235f,.095f); collar(.345f,.125f); stem(.405f,.095f,.145f,.70f); base(.70f,.285f) }
        PieceType.ROOK -> {
            val crown=Path().apply {
                moveTo(x(.235f),y(.17f)); lineTo(x(.325f),y(.17f)); lineTo(x(.325f),y(.255f)); lineTo(x(.445f),y(.255f)); lineTo(x(.445f),y(.17f)); lineTo(x(.555f),y(.17f)); lineTo(x(.555f),y(.255f)); lineTo(x(.675f),y(.255f)); lineTo(x(.675f),y(.17f)); lineTo(x(.765f),y(.17f)); lineTo(x(.74f),y(.37f)); quadraticBezierTo(x(.50f),y(.405f),x(.26f),y(.37f)); close()
            }; styled(crown)
            val tower=Path().apply { moveTo(x(.31f),y(.39f)); lineTo(x(.69f),y(.39f)); lineTo(x(.64f),y(.72f)); quadraticBezierTo(x(.50f),y(.745f),x(.36f),y(.72f)); close() }
            styled(tower); if(!outlined) drawLine(highlight.copy(alpha=.42f),p(.35f,.44f),p(.32f,.67f),art*.009f); base(.71f,.32f)
        }
        PieceType.KNIGHT -> {
            val horse=Path().apply {
                moveTo(x(.245f),y(.73f)); quadraticBezierTo(x(.29f),y(.62f),x(.35f),y(.53f)); quadraticBezierTo(x(.30f),y(.45f),x(.35f),y(.35f)); lineTo(x(.47f),y(.145f)); lineTo(x(.535f),y(.255f)); lineTo(x(.64f),y(.21f)); quadraticBezierTo(x(.755f),y(.29f),x(.69f),y(.405f)); quadraticBezierTo(x(.62f),y(.46f),x(.56f),y(.49f)); quadraticBezierTo(x(.66f),y(.59f),x(.70f),y(.73f)); close()
            }; styled(horse); drawCircle(if(outlined)line else highlight,art*.017f,p(.56f,.315f)); drawLine(line.copy(alpha=.75f),p(.36f,.34f),p(.31f,.49f),art*.010f); drawLine(line.copy(alpha=.75f),p(.31f,.49f),p(.40f,.455f),art*.010f); base(.72f,.32f)
        }
        PieceType.BISHOP -> {
            val head=Path().apply { moveTo(x(.50f),y(.115f)); quadraticBezierTo(x(.34f),y(.235f),x(.38f),y(.385f)); quadraticBezierTo(x(.41f),y(.475f),x(.50f),y(.505f)); quadraticBezierTo(x(.59f),y(.475f),x(.62f),y(.385f)); quadraticBezierTo(x(.66f),y(.235f),x(.50f),y(.115f)); close() }
            styled(head); drawLine(line,p(.57f,.205f),p(.43f,.41f),art*.018f); collar(.49f,.145f); stem(.555f,.11f,.155f,.72f); base(.72f,.31f)
        }
        PieceType.QUEEN -> {
            val crown=Path().apply { moveTo(x(.25f),y(.37f)); lineTo(x(.205f),y(.19f)); lineTo(x(.35f),y(.305f)); lineTo(x(.50f),y(.135f)); lineTo(x(.65f),y(.305f)); lineTo(x(.795f),y(.19f)); lineTo(x(.75f),y(.37f)); quadraticBezierTo(x(.50f),y(.43f),x(.25f),y(.37f)); close() }
            styled(crown); circle(.205f,.175f,.024f); circle(.50f,.115f,.026f); circle(.795f,.175f,.024f); collar(.405f,.19f); stem(.47f,.13f,.17f,.72f); base(.72f,.325f)
        }
        PieceType.KING -> {
            val cross=art*.026f; drawLine(line,p(.50f,.07f),p(.50f,.235f),cross); drawLine(fill,p(.50f,.075f),p(.50f,.23f),cross*.54f); drawLine(line,p(.415f,.145f),p(.585f,.145f),cross); drawLine(fill,p(.42f,.145f),p(.58f,.145f),cross*.54f)
            val crown=Path().apply { moveTo(x(.32f),y(.32f)); quadraticBezierTo(x(.50f),y(.24f),x(.68f),y(.32f)); lineTo(x(.64f),y(.445f)); quadraticBezierTo(x(.50f),y(.475f),x(.36f),y(.445f)); close() }
            styled(crown); collar(.445f,.175f); stem(.51f,.13f,.175f,.72f); base(.72f,.33f)
        }
    }
}

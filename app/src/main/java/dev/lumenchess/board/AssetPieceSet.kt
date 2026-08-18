package dev.lumenchess.board

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.lumenchess.core.chess.Color as ChessColor
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType

/** Optional local/private piece renderer. Missing files always fall back to the public Lumen set. */
class AssetPieceSet(
    override val id: String,
    override val displayName: String,
    private val assetDirectory: String,
    private val fallback: PieceSet = LumenVectorPieceSet,
) : PieceSet {
    @Composable
    override fun Piece(piece: Piece, tint: Color, modifier: Modifier) {
        val context = LocalContext.current
        val assetPath = "$assetDirectory/${piece.assetToken()}.png"
        val bitmap = remember(assetPath) {
            runCatching {
                context.assets.open(assetPath).use { input -> BitmapFactory.decodeStream(input)?.asImageBitmap() }
            }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
        } else {
            fallback.Piece(piece, tint, modifier)
        }
    }
}

private fun Piece.assetToken(): String {
    val colorPrefix = if (this.color == ChessColor.WHITE) "w" else "b"
    val typeToken = when (type) {
        PieceType.KING -> "k"
        PieceType.QUEEN -> "q"
        PieceType.ROOK -> "r"
        PieceType.BISHOP -> "b"
        PieceType.KNIGHT -> "n"
        PieceType.PAWN -> "p"
    }
    return colorPrefix + typeToken
}

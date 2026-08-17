package dev.lumenchess.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.lumenchess.core.chess.Piece

object LumenOutlinePieceSet : PieceSet {
    override val id: String = "lumen-outline"
    override val displayName: String = "Lumen Outline"

    @Composable
    override fun Piece(piece: Piece, tint: Color, modifier: Modifier) {
        LumenPieceArtwork(piece = piece, tint = tint, outlined = true, modifier = modifier)
    }
}

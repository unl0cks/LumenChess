package dev.lumenchess.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.lumenchess.core.chess.Piece

interface PieceSet {
    val id: String
    val displayName: String

    @Composable
    fun Piece(
        piece: Piece,
        tint: Color,
        modifier: Modifier = Modifier,
    )
}

object PieceSetCatalog {
    val builtIns: List<PieceSet> = listOf(LumenVectorPieceSet, LumenOutlinePieceSet)

    fun definition(id: String): PieceSet = builtIns.firstOrNull { it.id == id } ?: LumenVectorPieceSet
}

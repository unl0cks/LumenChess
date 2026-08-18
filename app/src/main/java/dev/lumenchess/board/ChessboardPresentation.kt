package dev.lumenchess.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

data class ChessboardPresentationStyle(
    val palette: ChessboardPalette = ChessboardPalette.default(),
    val pieceSet: PieceSet = LumenVectorPieceSet,
    val boardAssetPath: String? = null,
)

internal val LocalChessboardPresentationStyle = staticCompositionLocalOf { ChessboardPresentationStyle() }

@Composable
fun ProvideChessboardPresentationStyle(style: ChessboardPresentationStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChessboardPresentationStyle provides style, content = content)
}

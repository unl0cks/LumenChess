package dev.lumenchess.board

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position

/** Board renderer that keeps local/private board assets behind the normal board presentation API. */
@Composable
fun ThemedLumenChessboard(
    position: Position,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ChessboardOrientation = ChessboardOrientation.WHITE,
    input: ChessboardInput = ChessboardInput(),
    highlights: ChessboardHighlights = ChessboardHighlights(),
    arrows: List<ChessboardArrow> = emptyList(),
    palette: ChessboardPalette? = null,
    pieceSet: PieceSet? = null,
    boardAssetPath: String? = null,
) {
    val presentation = LocalChessboardPresentationStyle.current
    val resolvedPalette = palette ?: presentation.palette
    val resolvedPieceSet = pieceSet ?: presentation.pieceSet
    val resolvedAsset = boardAssetPath ?: presentation.boardAssetPath
    val context = LocalContext.current
    val boardBitmap = remember(resolvedAsset) {
        resolvedAsset?.let { path ->
            runCatching {
                context.assets.open(path).use { inputStream -> BitmapFactory.decodeStream(inputStream)?.asImageBitmap() }
            }.getOrNull()
        }
    }

    if (boardBitmap == null) {
        LumenChessboard(
            position = position,
            onMove = onMove,
            modifier = modifier,
            orientation = orientation,
            input = input,
            highlights = highlights,
            arrows = arrows,
            palette = resolvedPalette,
            pieceSet = resolvedPieceSet,
        )
        return
    }

    Box(modifier) {
        Image(
            bitmap = boardBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = if (orientation == ChessboardOrientation.BLACK) 180f else 0f },
            contentScale = ContentScale.FillBounds,
        )
        LumenChessboard(
            position = position,
            onMove = onMove,
            modifier = Modifier.fillMaxSize(),
            orientation = orientation,
            input = input,
            highlights = highlights,
            arrows = arrows,
            palette = resolvedPalette.copy(lightSquare = Color.Transparent, darkSquare = Color.Transparent),
            pieceSet = resolvedPieceSet,
        )
    }
}

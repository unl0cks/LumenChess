package dev.lumenchess.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.Position
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.design.LumenColors

@Composable
fun BoardPreview(
    settings: AppearanceSettings,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .testTag("board-preview")
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val widthTarget = maxWidth * 0.84f
        val heightTarget = maxHeight - 12.dp
        val boardSize = if (widthTarget <= heightTarget) widthTarget else heightTarget
        LumenChessboard(
            position = Position.initial(),
            onMove = {},
            modifier = Modifier.size(boardSize).testTag("board-preview-board"),
            input = ChessboardInput(tapEnabled = false, dragEnabled = false),
            palette = BoardThemeCatalog.palette(settings),
            pieceSet = PieceSetCatalog.definition(settings.pieceSetId),
        )
    }
}

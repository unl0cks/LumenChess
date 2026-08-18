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
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.board.ThemedLumenChessboard
import dev.lumenchess.core.chess.Position
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.design.LumenColors

@Composable
fun BoardPreview(settings: AppearanceSettings, modifier: Modifier = Modifier) {
    val boardDefinition = BoardThemeCatalog.definition(settings.boardThemeId)
    BoxWithConstraints(
        modifier = modifier.background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .testTag("board-preview").padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val widthTarget = maxWidth * .97f
        val heightTarget = maxHeight - 4.dp
        val boardSize = if (widthTarget <= heightTarget) widthTarget else heightTarget
        ThemedLumenChessboard(
            position = Position.initial(),
            onMove = {},
            modifier = Modifier.size(boardSize).testTag("board-preview-board"),
            input = ChessboardInput(tapEnabled = false, dragEnabled = false),
            palette = BoardThemeCatalog.palette(settings),
            pieceSet = PieceSetCatalog.definition(settings.pieceSetId),
            boardAssetPath = boardDefinition.assetPath,
        )
    }
}

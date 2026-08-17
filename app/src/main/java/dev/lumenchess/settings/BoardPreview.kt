package dev.lumenchess.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import dev.lumenchess.customization.BackgroundCatalog
import dev.lumenchess.customization.BoardThemeCatalog

@Composable
fun BoardPreview(
    settings: AppearanceSettings,
    modifier: Modifier = Modifier,
) {
    val background = BackgroundCatalog.definition(settings.backgroundId)
    val isLight = settings.appearance == AppAppearance.LIGHT
    val top = if (isLight) background.lightTop else background.darkTop
    val bottom = if (isLight) background.lightBottom else background.darkBottom

    Box(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .testTag("board-preview")
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        LumenChessboard(
            position = Position.initial(),
            onMove = {},
            modifier = Modifier.size(204.dp),
            input = ChessboardInput(tapEnabled = false, dragEnabled = false),
            palette = BoardThemeCatalog.palette(settings),
            pieceSet = PieceSetCatalog.definition(settings.pieceSetId),
        )
    }
}

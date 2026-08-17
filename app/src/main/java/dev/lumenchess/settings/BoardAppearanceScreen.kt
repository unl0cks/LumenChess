package dev.lumenchess.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lumenchess.board.PieceSet
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.Color as ChessColor
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.customization.BackgroundCatalog
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.customization.LumenPreset
import dev.lumenchess.customization.LumenPresetCatalog
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenPanel
import dev.lumenchess.design.LumenSpacing
import dev.lumenchess.design.LumenTabs
import dev.lumenchess.design.LumenTopBar

private enum class CustomizationTab(val label: String) {
    BOARD("Board"),
    PIECES("Pieces"),
    BACKGROUND("Background"),
    PRESETS("Presets"),
}

@Composable
fun BoardAppearanceScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(CustomizationTab.BOARD) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LumenTopBar(
            title = "Board & Pieces",
            onBack = onBack,
            backTestTag = "customization-back",
        )
        Text(
            settings.presetId?.let { id -> LumenPresetCatalog.definition(id)?.let { "${it.displayName} preset active" } }
                ?: "Custom mix",
            style = MaterialTheme.typography.bodySmall,
            color = LumenColors.OnSurfaceMuted,
            modifier = Modifier.padding(start = 2.dp),
        )

        LumenPanel(Modifier.fillMaxWidth()) {
            BoardPreview(
                settings = settings,
                modifier = Modifier.fillMaxWidth().height(322.dp),
            )
        }

        LumenTabs(
            labels = CustomizationTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelected = { tab = CustomizationTab.entries[it] },
            testTagPrefix = "customization-tab",
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            when (tab) {
                CustomizationTab.BOARD -> BoardThemeCatalog.builtIns.forEach { definition ->
                    VisualOptionRow(
                        title = definition.displayName,
                        subtitle = definition.description,
                        selected = settings.boardThemeId == definition.id,
                        tag = "customization-board-${definition.id}",
                        leading = {
                            BoardSwatch(definition.palette.lightSquare, definition.palette.darkSquare)
                        },
                    ) {
                        onSettingsChange(
                            settings.withBoardTheme(definition.id).copy(
                                customLightSquareArgb = null,
                                customDarkSquareArgb = null,
                            ),
                        )
                    }
                }

                CustomizationTab.PIECES -> PieceSetCatalog.builtIns.forEach { definition ->
                    VisualOptionRow(
                        title = definition.displayName,
                        subtitle = if (definition.id == AppearanceSettings.DEFAULT_PIECE_SET_ID) {
                            "Original filled Lumen Staunton set"
                        } else {
                            "Original geometric outline set"
                        },
                        selected = settings.pieceSetId == definition.id,
                        tag = "customization-piece-${definition.id}",
                        leading = { PieceMiniatures(definition) },
                    ) { onSettingsChange(settings.withPieceSet(definition.id)) }
                }

                CustomizationTab.BACKGROUND -> BackgroundCatalog.builtIns.forEach { definition ->
                    VisualOptionRow(
                        title = definition.displayName,
                        subtitle = definition.description,
                        selected = settings.backgroundId == definition.id,
                        tag = "customization-background-${definition.id}",
                        leading = {
                            Box(
                                Modifier
                                    .size(width = 54.dp, height = 38.dp)
                                    .background(
                                        Brush.verticalGradient(listOf(definition.darkTop, definition.darkBottom)),
                                        RoundedCornerShape(7.dp),
                                    ),
                            )
                        },
                    ) { onSettingsChange(settings.withBackground(definition.id)) }
                }

                CustomizationTab.PRESETS -> LumenPresetCatalog.builtIns.forEach { definition ->
                    VisualOptionRow(
                        title = definition.displayName,
                        subtitle = "Board, pieces and background",
                        selected = settings.presetId == definition.id,
                        tag = "customization-preset-${definition.id}",
                        leading = { PresetMiniature(definition) },
                    ) { onSettingsChange(definition.applyTo(settings)) }
                }
            }
            Box(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun VisualOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    LumenPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        selected = selected,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(width = 58.dp, height = 42.dp), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurface,
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
            }
            if (selected) {
                Text("✓", style = MaterialTheme.typography.titleMedium, color = LumenColors.AccentBlueBright)
            }
        }
    }
}

@Composable
private fun BoardSwatch(light: Color, dark: Color) {
    Canvas(Modifier.size(width = 46.dp, height = 38.dp)) {
        val cellW = size.width / 2f
        val cellH = size.height / 2f
        repeat(2) { rank ->
            repeat(2) { file ->
                drawRect(
                    color = if ((file + rank) % 2 == 0) light else dark,
                    topLeft = Offset(file * cellW, rank * cellH),
                    size = androidx.compose.ui.geometry.Size(cellW, cellH),
                )
            }
        }
    }
}

@Composable
private fun PieceMiniatures(pieceSet: PieceSet) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom) {
        listOf(PieceType.KING, PieceType.KNIGHT, PieceType.ROOK).forEach { type ->
            pieceSet.Piece(
                piece = Piece(ChessColor.WHITE, type),
                tint = Color(0xFFF0EBDD),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PresetMiniature(preset: LumenPreset) {
    val board = BoardThemeCatalog.definition(preset.boardThemeId).palette
    val background = BackgroundCatalog.definition(preset.backgroundId)
    Box(
        Modifier
            .size(width = 54.dp, height = 38.dp)
            .background(
                Brush.verticalGradient(listOf(background.darkTop, background.darkBottom)),
                RoundedCornerShape(7.dp),
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoardSwatch(board.lightSquare, board.darkSquare)
    }
}

package dev.lumenchess.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import dev.lumenchess.design.LumenTabs
import dev.lumenchess.design.LumenTopBar

private enum class CustomizationTab(val label: String) {
    BOARD("Board"), PIECES("Pieces"), BACKGROUND("Background"), PRESETS("Presets"),
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
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        LumenTopBar("Board & Pieces", onBack = onBack, backTestTag = "customization-back")
        Text(
            settings.presetId?.let { id -> LumenPresetCatalog.definition(id)?.let { "${it.displayName} preset active" } } ?: "Custom mix",
            style = MaterialTheme.typography.bodySmall,
            color = LumenColors.OnSurfaceMuted,
            modifier = Modifier.padding(start = 2.dp).testTag("customization-status"),
        )

        LumenPanel(Modifier.fillMaxWidth()) {
            BoardPreview(settings, Modifier.fillMaxWidth().height(380.dp))
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
                .verticalScroll(rememberScrollState())
                .testTag("customization-options-grid"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                CustomizationTab.BOARD -> BoardThemeCatalog.builtIns.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { definition ->
                            VisualOptionCard(
                                title = definition.displayName,
                                subtitle = definition.description,
                                selected = settings.boardThemeId == definition.id,
                                tag = "customization-board-${definition.id}",
                                modifier = Modifier.weight(1f),
                                preview = { BoardSwatch(definition.palette.lightSquare, definition.palette.darkSquare) },
                            ) {
                                onSettingsChange(
                                    settings.withBoardTheme(definition.id).copy(
                                        customLightSquareArgb = null,
                                        customDarkSquareArgb = null,
                                    ),
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                CustomizationTab.PIECES -> PieceSetCatalog.builtIns.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { definition ->
                            VisualOptionCard(
                                title = definition.displayName,
                                subtitle = if (definition.id == AppearanceSettings.DEFAULT_PIECE_SET_ID) "Filled Staunton" else "Geometric outline",
                                selected = settings.pieceSetId == definition.id,
                                tag = "customization-piece-${definition.id}",
                                modifier = Modifier.weight(1f),
                                preview = { PieceMiniatures(definition) },
                            ) { onSettingsChange(settings.withPieceSet(definition.id)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                CustomizationTab.BACKGROUND -> BackgroundCatalog.builtIns.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { definition ->
                            VisualOptionCard(
                                title = definition.displayName,
                                subtitle = definition.description,
                                selected = settings.backgroundId == definition.id,
                                tag = "customization-background-${definition.id}",
                                modifier = Modifier.weight(1f),
                                preview = {
                                    Box(
                                        Modifier
                                            .fillMaxSize()
                                            .background(Brush.verticalGradient(listOf(definition.darkTop, definition.darkBottom)), RoundedCornerShape(6.dp)),
                                    )
                                },
                            ) { onSettingsChange(settings.withBackground(definition.id)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                CustomizationTab.PRESETS -> LumenPresetCatalog.builtIns.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { definition ->
                            VisualOptionCard(
                                title = definition.displayName,
                                subtitle = "Board · pieces · background",
                                selected = settings.presetId == definition.id,
                                tag = "customization-preset-${definition.id}",
                                modifier = Modifier.weight(1f),
                                preview = { PresetMiniature(definition) },
                            ) { onSettingsChange(definition.applyTo(settings)) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Box(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VisualOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    LumenPanel(
        modifier = modifier.height(128.dp).clickable(onClick = onClick).testTag(tag),
        selected = selected,
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(LumenColors.Background.copy(alpha = .55f), RoundedCornerShape(7.dp))
                    .padding(5.dp),
                contentAlignment = Alignment.Center,
            ) { preview() }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selected) Text("✓", style = MaterialTheme.typography.labelLarge, color = LumenColors.AccentBlueBright)
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BoardSwatch(light: Color, dark: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val cellW = size.width / 4f
        val cellH = size.height / 3f
        repeat(3) { rank -> repeat(4) { file ->
            drawRect(
                color = if ((file + rank) % 2 == 0) light else dark,
                topLeft = Offset(file * cellW, rank * cellH),
                size = androidx.compose.ui.geometry.Size(cellW, cellH),
            )
        } }
    }
}

@Composable
private fun PieceMiniatures(pieceSet: PieceSet) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(PieceType.KING, PieceType.KNIGHT, PieceType.ROOK).forEach { type ->
            pieceSet.Piece(Piece(ChessColor.WHITE, type), Color(0xFFF0EBDD), Modifier.size(34.dp))
        }
    }
}

@Composable
private fun PresetMiniature(preset: LumenPreset) {
    val board = BoardThemeCatalog.definition(preset.boardThemeId).palette
    val background = BackgroundCatalog.definition(preset.backgroundId)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(background.darkTop, background.darkBottom)), RoundedCornerShape(6.dp))
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(.78f)) { BoardSwatch(board.lightSquare, board.darkSquare) }
    }
}

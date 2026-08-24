package dev.lumenchess.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenDerivativeTabs
import dev.lumenchess.design.LumenDerivativeTopBar
import dev.lumenchess.design.lumenP5IdentityPalette

private enum class CustomizationTab(val label: String) { BOARD("Board"), PIECES("Pieces"), BACKGROUND("Background"), PRESETS("Presets") }

@Composable
fun BoardAppearanceScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(CustomizationTab.BOARD) }
    LumenDerivativePage(
        modifier = modifier.fillMaxSize(),
        testTag = "derivative-board-appearance",
        horizontalPadding = 16,
        spacing = 7,
    ) {
        LumenDerivativeTopBar("Board & Pieces", onBack = onBack, backTestTag = "customization-back")
        LumenDerivativeSurface(
            role = DerivativeSurfaceRole.RECESSED_TRAY,
            modifier = Modifier.fillMaxWidth().height(274.dp),
            testTag = "derivative-board-preview-frame",
            contentPadding = androidx.compose.foundation.layout.PaddingValues(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoardPreview(settings, Modifier.fillMaxWidth().height(258.dp))
        }

        LumenDerivativeTabs(
            labels = CustomizationTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelected = { tab = CustomizationTab.entries[it] },
            testTagPrefix = "customization-tab",
        )

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).testTag("customization-options-grid"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (tab) {
                CustomizationTab.BOARD -> BoardThemeCatalog.builtIns.forEach { d ->
                    VisualOptionRow(
                        title = d.displayName,
                        subtitle = d.description,
                        selected = settings.boardThemeId == d.id,
                        tag = "customization-board-${d.id}",
                        preview = { BoardSwatch(d.palette.lightSquare, d.palette.darkSquare) },
                    ) { onSettingsChange(settings.withBoardTheme(d.id).copy(customLightSquareArgb = null, customDarkSquareArgb = null)) }
                }
                CustomizationTab.PIECES -> PieceSetCatalog.builtIns.forEach { d ->
                    VisualOptionRow(
                        title = d.displayName,
                        subtitle = if (d.id == AppearanceSettings.DEFAULT_PIECE_SET_ID) "Classical Lumen set" else "Alternative piece style",
                        selected = settings.pieceSetId == d.id,
                        tag = "customization-piece-${d.id}",
                        preview = { PieceMiniatures(d) },
                    ) { onSettingsChange(settings.withPieceSet(d.id)) }
                }
                CustomizationTab.BACKGROUND -> BackgroundCatalog.builtIns.forEach { d ->
                    VisualOptionRow(
                        title = d.displayName,
                        subtitle = d.description,
                        selected = settings.backgroundId == d.id,
                        tag = "customization-background-${d.id}",
                        preview = {
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(d.darkTop, d.darkBottom)), RoundedCornerShape(4.dp)))
                        },
                    ) { onSettingsChange(settings.withBackground(d.id)) }
                }
                CustomizationTab.PRESETS -> LumenPresetCatalog.builtIns.forEach { d ->
                    VisualOptionRow(
                        title = d.displayName,
                        subtitle = "Board · pieces · background",
                        selected = settings.presetId == d.id,
                        tag = "customization-preset-${d.id}",
                        preview = { PresetMiniature(d) },
                    ) { onSettingsChange(d.applyTo(settings)) }
                }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}

@Composable
private fun VisualOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    preview: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativeSurface(
        role = if (selected) DerivativeSurfaceRole.SELECTED_FACE else DerivativeSurfaceRole.NEUTRAL_ROW,
        modifier = Modifier.fillMaxWidth().height(78.dp),
        onClick = onClick,
        testTag = tag,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 7.dp),
    ) {
        if (selected) Box(Modifier.matchParentSize().testTag("derivative-catalog-selected"))
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            LumenDerivativeSurface(
                role = DerivativeSurfaceRole.RECESSED_TRAY,
                modifier = Modifier.width(88.dp).height(62.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                contentAlignment = Alignment.Center,
            ) { preview() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) palette.cyanMicro else palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selected) SelectedCheck(palette.cyanMicro)
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SelectedCheck(tint: Color) {
    Canvas(Modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * .20f, size.height * .53f)
            lineTo(size.width * .42f, size.height * .73f)
            lineTo(size.width * .80f, size.height * .28f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun BoardSwatch(light: Color, dark: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val cw = size.width / 6f; val ch = size.height / 4f
        repeat(4) { r -> repeat(6) { f -> drawRect(if ((f+r)%2==0) light else dark, Offset(f*cw,r*ch), androidx.compose.ui.geometry.Size(cw,ch)) } }
    }
}

@Composable
private fun PieceMiniatures(pieceSet: PieceSet) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(PieceType.KING, PieceType.KNIGHT, PieceType.ROOK).forEach { type ->
            pieceSet.Piece(Piece(ChessColor.WHITE, type), Color(0xFFF0EBDD), Modifier.size(31.dp))
        }
    }
}

@Composable
private fun PresetMiniature(preset: LumenPreset) {
    val board = BoardThemeCatalog.definition(preset.boardThemeId).palette
    val bg = BackgroundCatalog.definition(preset.backgroundId)
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.darkTop, bg.darkBottom)), RoundedCornerShape(4.dp)).padding(5.dp),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.fillMaxSize(.82f)) { BoardSwatch(board.lightSquare, board.darkSquare) } }
}

package dev.lumenchess.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.customization.BackgroundCatalog
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.customization.LumenPresetCatalog
import dev.lumenchess.design.LumenColors

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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onBack).testTag("customization-back"),
                color = LumenColors.SurfaceRaised,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Back", Modifier.padding(horizontal = 14.dp, vertical = 14.dp), style = MaterialTheme.typography.labelLarge)
            }
            Column(Modifier.weight(1f)) {
                Text("Board & Pieces", style = MaterialTheme.typography.headlineMedium)
                Text(
                    settings.presetId?.let { id -> LumenPresetCatalog.definition(id)?.let { "${it.displayName} preset active" } }
                        ?: "Custom mix",
                    style = MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                )
            }
        }

        Surface(color = LumenColors.Surface, shape = RoundedCornerShape(18.dp)) {
            BoardPreview(settings = settings, modifier = Modifier.fillMaxWidth().height(238.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CustomizationTab.entries.forEach { option ->
                val selected = tab == option
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .selectable(selected = selected, onClick = { tab = option }, role = Role.Tab)
                        .testTag("customization-tab-${option.name.lowercase()}"),
                    color = if (selected) LumenColors.AccentBlueSoft else LumenColors.SurfaceRaised,
                    shape = RoundedCornerShape(11.dp),
                ) {
                    Text(
                        option.label,
                        Modifier.padding(horizontal = 5.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
                    )
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                CustomizationTab.BOARD -> BoardThemeCatalog.builtIns.forEach { definition ->
                    CustomizationOption(
                        title = definition.displayName,
                        subtitle = definition.description,
                        selected = settings.boardThemeId == definition.id,
                        tag = "customization-board-${definition.id}",
                    ) { onSettingsChange(settings.withBoardTheme(definition.id).copy(customLightSquareArgb = null, customDarkSquareArgb = null)) }
                }
                CustomizationTab.PIECES -> PieceSetCatalog.builtIns.forEach { definition ->
                    CustomizationOption(
                        title = definition.displayName,
                        subtitle = if (definition.id == AppearanceSettings.DEFAULT_PIECE_SET_ID) "Original filled Lumen vector set" else "Original geometric outline treatment",
                        selected = settings.pieceSetId == definition.id,
                        tag = "customization-piece-${definition.id}",
                    ) { onSettingsChange(settings.withPieceSet(definition.id)) }
                }
                CustomizationTab.BACKGROUND -> BackgroundCatalog.builtIns.forEach { definition ->
                    CustomizationOption(
                        title = definition.displayName,
                        subtitle = definition.description,
                        selected = settings.backgroundId == definition.id,
                        tag = "customization-background-${definition.id}",
                    ) { onSettingsChange(settings.withBackground(definition.id)) }
                }
                CustomizationTab.PRESETS -> LumenPresetCatalog.builtIns.forEach { definition ->
                    CustomizationOption(
                        title = definition.displayName,
                        subtitle = "Apply a coordinated board, piece and background starting point. Individual overrides remain editable.",
                        selected = settings.presetId == definition.id,
                        tag = "customization-preset-${definition.id}",
                    ) { onSettingsChange(definition.applyTo(settings)) }
                }
            }
        }
    }
}

@Composable
private fun CustomizationOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(tag),
        color = if (selected) LumenColors.AccentBlueSoft else LumenColors.Surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
        }
    }
}

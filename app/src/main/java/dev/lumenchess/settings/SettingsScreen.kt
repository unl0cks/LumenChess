package dev.lumenchess.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors

@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onOpenBoardAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SETTINGS", style = MaterialTheme.typography.labelSmall, color = LumenColors.AccentBlueBright)
        Text("Make LumenChess yours", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Appearance stays presentation-only. Chess rules, clocks and engine state do not live here.",
            style = MaterialTheme.typography.bodyMedium,
            color = LumenColors.OnSurfaceMuted,
        )

        SettingsSection("APPEARANCE") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceChoice("System", AppAppearance.SYSTEM, settings, onSettingsChange, Modifier.weight(1f))
                AppearanceChoice("Dark", AppAppearance.DARK, settings, onSettingsChange, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppearanceChoice("OLED", AppAppearance.OLED_DARK, settings, onSettingsChange, Modifier.weight(1f))
                AppearanceChoice("Light", AppAppearance.LIGHT, settings, onSettingsChange, Modifier.weight(1f))
            }
            Text(
                "Active appearance: ${appearanceLabel(settings.appearance)}",
                style = MaterialTheme.typography.bodySmall,
                color = LumenColors.OnSurfaceMuted,
            )
        }

        SettingsCategory(
            title = "Board & Pieces",
            subtitle = "Board palettes, Lumen piece sets, backgrounds and presets",
            tag = "settings-board-pieces",
            onClick = onOpenBoardAppearance,
        )
        SettingsCategory(
            title = "Sounds & Haptics",
            subtitle = "Move feedback, event sounds and tactile response",
            tag = "settings-sounds-haptics",
            onClick = null,
            trailing = "P4",
        )
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceFaint)
        Surface(color = LumenColors.Surface.copy(alpha = .96f), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { content() }
        }
    }
}

@Composable
private fun AppearanceChoice(
    label: String,
    appearance: AppAppearance,
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    modifier: Modifier,
) {
    val selected = settings.appearance == appearance
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = { onSettingsChange(settings.copy(appearance = appearance)) },
                role = Role.RadioButton,
            )
            .testTag("appearance-${appearance.name.lowercase()}"),
        color = if (selected) LumenColors.AccentBlueSoft else LumenColors.SurfaceRaised,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
        )
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    subtitle: String,
    tag: String,
    onClick: (() -> Unit)?,
    trailing: String? = null,
) {
    var modifier = Modifier.fillMaxWidth().testTag(tag)
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Surface(modifier = modifier, color = LumenColors.Surface, shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
            }
            trailing?.let {
                Surface(color = LumenColors.SurfaceRaised, shape = RoundedCornerShape(9.dp)) {
                    Text(it, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceFaint)
                }
            }
        }
    }
}

private fun appearanceLabel(appearance: AppAppearance): String = when (appearance) {
    AppAppearance.SYSTEM -> "System"
    AppAppearance.DARK -> "Dark"
    AppAppearance.OLED_DARK -> "OLED"
    AppAppearance.LIGHT -> "Light"
}

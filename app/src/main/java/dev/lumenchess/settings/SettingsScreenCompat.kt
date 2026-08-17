package dev.lumenchess.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Keeps existing direct SettingsScreen callers source-compatible with the P4 route addition. */
@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onOpenBoardAppearance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        settings = settings,
        onSettingsChange = onSettingsChange,
        onOpenBoardAppearance = onOpenBoardAppearance,
        onOpenSoundsHaptics = {},
        modifier = modifier,
    )
}

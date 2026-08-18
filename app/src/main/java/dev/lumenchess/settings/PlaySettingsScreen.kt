package dev.lumenchess.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenListRow
import dev.lumenchess.design.LumenPanel
import dev.lumenchess.design.LumenSegment
import dev.lumenchess.design.LumenTopBar

@Composable
fun PlaySettingsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    onOpenBoardAppearance: () -> Unit,
    onOpenSoundsHaptics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boardName = BoardThemeCatalog.definition(settings.boardThemeId).displayName
    val pieceName = PieceSetCatalog.definition(settings.pieceSetId).displayName
    val soundName = if (settings.soundPackId == AppearanceSettings.DEFAULT_SOUND_PACK_ID) "Lumen Default" else settings.soundPackId

    Column(
        modifier.fillMaxSize().testTag("play-settings-root")
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 13.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        LumenTopBar("Play", onBack = onBack, backTestTag = "play-settings-back")

        LumenListRow(
            title = "Time Controls",
            subtitle = "10 min · Rapid",
            modifier = Modifier.testTag("play-settings-time-controls"),
            showChevron = true,
        )

        LumenListRow(
            title = "Appearance",
            subtitle = when (settings.appearance) {
                AppAppearance.SYSTEM -> "System"
                AppAppearance.DARK -> "Dark"
                AppAppearance.OLED_DARK -> "OLED"
                AppAppearance.LIGHT -> "Light"
            },
            modifier = Modifier.testTag("play-settings-appearance"),
            showChevron = false,
        )
        LumenPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LumenSegment("System", settings.appearance == AppAppearance.SYSTEM, { onSettingsChange(settings.copy(appearance = AppAppearance.SYSTEM)) }, Modifier.weight(1f), testTag = "appearance-system")
                LumenSegment("Dark", settings.appearance == AppAppearance.DARK, { onSettingsChange(settings.copy(appearance = AppAppearance.DARK)) }, Modifier.weight(1f), testTag = "appearance-dark")
                LumenSegment("OLED", settings.appearance == AppAppearance.OLED_DARK, { onSettingsChange(settings.copy(appearance = AppAppearance.OLED_DARK)) }, Modifier.weight(1f), testTag = "appearance-oled_dark")
                LumenSegment("Light", settings.appearance == AppAppearance.LIGHT, { onSettingsChange(settings.copy(appearance = AppAppearance.LIGHT)) }, Modifier.weight(1f), testTag = "appearance-light")
            }
        }

        LumenListRow(
            title = "Board & Pieces",
            subtitle = "$boardName · $pieceName",
            modifier = Modifier.testTag("settings-board-pieces"),
            onClick = onOpenBoardAppearance,
        )
        LumenListRow(
            title = "Sounds & Haptics",
            subtitle = soundName,
            modifier = Modifier.testTag("settings-sounds-haptics"),
            onClick = onOpenSoundsHaptics,
        )

        Text(
            "Play preferences apply to local engine games and are stored on-device.",
            style = MaterialTheme.typography.labelSmall,
            color = LumenColors.OnSurfaceMuted,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
        )
    }
}

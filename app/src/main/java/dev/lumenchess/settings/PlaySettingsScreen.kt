package dev.lumenchess.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeRow
import dev.lumenchess.design.LumenDerivativeSegment
import dev.lumenchess.design.LumenDerivativeTopBar
import dev.lumenchess.design.LumenDerivativeTray
import dev.lumenchess.design.lumenP5IdentityPalette

@Composable
fun PlaySettingsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    onOpenBoardAppearance: () -> Unit,
    onOpenSoundsHaptics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = lumenP5IdentityPalette()
    val boardName = BoardThemeCatalog.definition(settings.boardThemeId).displayName
    val pieceName = PieceSetCatalog.definition(settings.pieceSetId).displayName
    val soundName = if (settings.soundPackId == AppearanceSettings.DEFAULT_SOUND_PACK_ID) {
        "Lumen Default"
    } else {
        settings.soundPackId
    }

    Box(modifier.fillMaxSize().testTag("play-settings-root")) {
        LumenDerivativePage(
            modifier = Modifier.fillMaxSize(),
            testTag = "derivative-play-settings",
            scrollable = true,
            spacing = 8,
        ) {
            LumenDerivativeTopBar(
                title = "Play",
                onBack = onBack,
                backTestTag = "play-settings-back",
            )

            LumenDerivativeRow(
                title = "Time Controls",
                subtitle = "10 min · Rapid",
                modifier = Modifier.testTag("play-settings-time-controls"),
                showChevron = true,
            )

            LumenDerivativeRow(
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
            LumenDerivativeTray(
                modifier = Modifier.fillMaxWidth(),
                testTag = "derivative-appearance-tray",
                padding = PaddingValues(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    LumenDerivativeSegment(
                        label = "System",
                        selected = settings.appearance == AppAppearance.SYSTEM,
                        onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.SYSTEM)) },
                        testTag = "appearance-system",
                    )
                    LumenDerivativeSegment(
                        label = "Dark",
                        selected = settings.appearance == AppAppearance.DARK,
                        onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.DARK)) },
                        testTag = "appearance-dark",
                    )
                    LumenDerivativeSegment(
                        label = "OLED",
                        selected = settings.appearance == AppAppearance.OLED_DARK,
                        onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.OLED_DARK)) },
                        testTag = "appearance-oled_dark",
                    )
                    LumenDerivativeSegment(
                        label = "Light",
                        selected = settings.appearance == AppAppearance.LIGHT,
                        onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.LIGHT)) },
                        testTag = "appearance-light",
                    )
                }
            }

            LumenDerivativeRow(
                title = "Board & Pieces",
                subtitle = "$boardName · $pieceName",
                onClick = onOpenBoardAppearance,
                testTag = "settings-board-pieces",
            )
            LumenDerivativeRow(
                title = "Sounds & Haptics",
                subtitle = soundName,
                onClick = onOpenSoundsHaptics,
                testTag = "settings-sounds-haptics",
            )

            Text(
                text = "Play preferences apply to local engine games and are stored on-device.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.muted,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 5.dp),
            )
        }
    }
}

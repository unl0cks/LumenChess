package dev.lumenchess.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.play.PlayScreenMode
import dev.lumenchess.play.PlayViewModel
import dev.lumenchess.play.PolishedPlayRoute
import dev.lumenchess.settings.AppearanceSettings
import dev.lumenchess.settings.DataStoreAppearanceSettingsRepository

internal enum class MainTab(val label: String, val previewCopy: String) {
    Play("Play", "Play against Stockfish or Reckless"),
    Arena("Arena", "Engine matches, takeover and branching"),
    Games("Games", "Your local and imported chess library"),
    Insights("Insights", "Performance trends and chess statistics"),
    Settings("Settings", "Appearance, board, sound and gameplay preferences"),
}

@Composable
fun LumenChessApp() {
    var currentTab by remember { mutableStateOf(MainTab.Play) }
    val playViewModel: PlayViewModel = viewModel()
    val playUi by playViewModel.uiState
    val context = LocalContext.current
    val appearanceRepository = remember(context.applicationContext) {
        DataStoreAppearanceSettingsRepository.from(context.applicationContext)
    }
    val appearanceSettings by appearanceRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppearanceSettings(),
    )
    val livePlay = currentTab == MainTab.Play && playUi.mode == PlayScreenMode.LIVE

    LumenTheme(settings = appearanceSettings) {
        Scaffold(
            containerColor = LumenColors.Background,
            bottomBar = {
                if (!livePlay) LumenBottomNavigation(currentTab) { currentTab = it }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (currentTab == MainTab.Play) {
                    PolishedPlayRoute(viewModel = playViewModel, modifier = Modifier.fillMaxSize())
                } else {
                    FutureSurfacePreview(currentTab)
                }
            }
        }
    }
}

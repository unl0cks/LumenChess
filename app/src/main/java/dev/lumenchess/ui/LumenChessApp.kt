package dev.lumenchess.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumenchess.board.ChessboardPresentationStyle
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.board.ProvideChessboardPresentationStyle
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.play.PlayScreenMode
import dev.lumenchess.play.PlayViewModel
import dev.lumenchess.play.PolishedPlayRoute
import dev.lumenchess.settings.AppearanceSettings
import dev.lumenchess.settings.BoardAppearanceScreen
import dev.lumenchess.settings.DataStoreAppearanceSettingsRepository
import dev.lumenchess.settings.SettingsScreen
import kotlinx.coroutines.launch

internal enum class MainTab(val label: String, val previewCopy: String) {
    Play("Play", "Play against Stockfish or Reckless"),
    Arena("Arena", "Engine matches, takeover and branching"),
    Games("Games", "Your local and imported chess library"),
    Insights("Insights", "Performance trends and chess statistics"),
    Settings("Settings", "Appearance, board, sound and gameplay preferences"),
}

private enum class SettingsDestination { ROOT, BOARD_APPEARANCE }

@Composable
fun LumenChessApp() {
    var currentTab by remember { mutableStateOf(MainTab.Play) }
    var settingsDestination by remember { mutableStateOf(SettingsDestination.ROOT) }
    val playViewModel: PlayViewModel = viewModel()
    val playUi by playViewModel.uiState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appearanceRepository = remember(context.applicationContext) {
        DataStoreAppearanceSettingsRepository.from(context.applicationContext)
    }
    val persistedAppearanceSettings by appearanceRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppearanceSettings(),
    )
    var appearanceSettings by remember { mutableStateOf(persistedAppearanceSettings) }
    val livePlay = currentTab == MainTab.Play && playUi.mode == PlayScreenMode.LIVE

    LaunchedEffect(persistedAppearanceSettings) {
        appearanceSettings = persistedAppearanceSettings
    }
    LaunchedEffect(currentTab) {
        if (currentTab != MainTab.Settings) settingsDestination = SettingsDestination.ROOT
    }

    fun persist(settings: AppearanceSettings) {
        appearanceSettings = settings
        scope.launch { appearanceRepository.update { settings } }
    }

    LumenTheme(settings = appearanceSettings) {
        ProvideChessboardPresentationStyle(
            style = ChessboardPresentationStyle(
                palette = BoardThemeCatalog.palette(appearanceSettings),
                pieceSet = PieceSetCatalog.definition(appearanceSettings.pieceSetId),
            ),
        ) {
            Scaffold(
                containerColor = LumenColors.Background,
                bottomBar = {
                    if (!livePlay) LumenBottomNavigation(currentTab) { currentTab = it }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (currentTab) {
                        MainTab.Play -> PolishedPlayRoute(viewModel = playViewModel, modifier = Modifier.fillMaxSize())
                        MainTab.Settings -> when (settingsDestination) {
                            SettingsDestination.ROOT -> SettingsScreen(
                                settings = appearanceSettings,
                                onSettingsChange = ::persist,
                                onOpenBoardAppearance = { settingsDestination = SettingsDestination.BOARD_APPEARANCE },
                                modifier = Modifier.fillMaxSize(),
                            )
                            SettingsDestination.BOARD_APPEARANCE -> BoardAppearanceScreen(
                                settings = appearanceSettings,
                                onSettingsChange = ::persist,
                                onBack = { settingsDestination = SettingsDestination.ROOT },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        else -> FutureSurfacePreview(currentTab)
                    }
                }
            }
        }
    }
}
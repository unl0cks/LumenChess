package dev.lumenchess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumenchess.board.ChessboardPresentationStyle
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.board.ProvideChessboardPresentationStyle
import dev.lumenchess.arena.ArenaRoute
import dev.lumenchess.arena.ArenaScreenMode
import dev.lumenchess.arena.ArenaViewModel
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.games.GameLibraryRoute
import dev.lumenchess.games.GameLibraryViewModel
import dev.lumenchess.insights.InsightsRoute
import dev.lumenchess.insights.InsightsViewModel
import dev.lumenchess.play.PlayScreenMode
import dev.lumenchess.play.PlayViewModel
import dev.lumenchess.play.ReferencePlayRoute
import dev.lumenchess.settings.AboutSettingsScreen
import dev.lumenchess.settings.AppearanceSettings
import dev.lumenchess.settings.BoardAppearanceScreen
import dev.lumenchess.settings.DataStoreAppearanceSettingsRepository
import dev.lumenchess.settings.EnginesSettingsScreen
import dev.lumenchess.settings.PlaySettingsScreen
import dev.lumenchess.settings.SettingsScreen
import dev.lumenchess.settings.SoundsHapticsScreen
import kotlinx.coroutines.launch

internal enum class MainTab(val label:String) {
    Play("Play"),
    Arena("Arena"),
    Games("Games"),
    Insights("Insights"),
    Settings("Settings"),
}
private enum class SettingsDestination { ROOT, PLAY, BOARD_APPEARANCE, SOUNDS_HAPTICS, ENGINES, ABOUT }

@Composable
fun LumenChessApp() {
    var currentTab by rememberSaveable { mutableStateOf(MainTab.Play) }
    var settingsDestination by remember { mutableStateOf(SettingsDestination.ROOT) }
    var playFocusedSubpage by remember { mutableStateOf(false) }
    val playViewModel:PlayViewModel=viewModel()
    val arenaViewModel:ArenaViewModel=viewModel()
    val playUi by playViewModel.uiState
    val arenaUi by arenaViewModel.uiState
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val appearanceRepository=remember(context.applicationContext){ DataStoreAppearanceSettingsRepository.from(context.applicationContext) }
    val persistedAppearanceSettings by appearanceRepository.settings.collectAsStateWithLifecycle(initialValue=AppearanceSettings())
    var appearanceSettings by remember { mutableStateOf(persistedAppearanceSettings) }
    val livePlay=currentTab==MainTab.Play&&playUi.mode==PlayScreenMode.LIVE
    val liveArena=currentTab==MainTab.Arena&&arenaUi.mode==ArenaScreenMode.LIVE
    val focusedPlaySubpage=currentTab==MainTab.Play&&playFocusedSubpage
    val slideDistance=with(LocalDensity.current){10.dp.roundToPx()}

    // System back walks up the hierarchy instead of leaving the app: Settings sub-pages return to their
    // parent, any other tab returns to Play. Nested screens (Live, Arena, the Library viewer, New Game)
    // register their own handlers later in composition and therefore take precedence.
    BackHandler(enabled=currentTab!=MainTab.Play){currentTab=MainTab.Play}
    BackHandler(enabled=currentTab==MainTab.Settings&&settingsDestination!=SettingsDestination.ROOT){
        settingsDestination=when(settingsDestination){
            SettingsDestination.BOARD_APPEARANCE,SettingsDestination.SOUNDS_HAPTICS->SettingsDestination.PLAY
            else->SettingsDestination.ROOT
        }
    }

    LaunchedEffect(persistedAppearanceSettings){appearanceSettings=persistedAppearanceSettings}
    LaunchedEffect(currentTab){if(currentTab!=MainTab.Settings)settingsDestination=SettingsDestination.ROOT}
    fun persist(settings:AppearanceSettings){appearanceSettings=settings;scope.launch{appearanceRepository.update{settings}}}

    LumenTheme(settings=appearanceSettings) {
        val boardDefinition=BoardThemeCatalog.definition(appearanceSettings.boardThemeId)
        ProvideChessboardPresentationStyle(
            ChessboardPresentationStyle(
                palette=BoardThemeCatalog.palette(appearanceSettings),
                pieceSet=PieceSetCatalog.definition(appearanceSettings.pieceSetId),
                boardAssetPath=boardDefinition.assetPath,
            ),
        ) {
            Scaffold(
                containerColor=LumenColors.Background,
                bottomBar={
                    if(!livePlay&&!liveArena&&!focusedPlaySubpage) {
                        LumenBottomNavigation(currentTab){currentTab=it}
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedContent(
                        targetState=currentTab to settingsDestination,
                        transitionSpec={
                            val tabDirection=targetState.first.ordinal.compareTo(initialState.first.ordinal)
                            val subDirection=targetState.second.ordinal.compareTo(initialState.second.ordinal)
                            val direction=if(tabDirection!=0)tabDirection else subDirection
                            val sign=if(direction>=0)1 else -1
                            (fadeIn(LumenMotion.normalTween())+slideInHorizontally(LumenMotion.normalTween()){sign*slideDistance})
                                .togetherWith(fadeOut(LumenMotion.fastTween())+slideOutHorizontally(LumenMotion.fastTween()){-sign*slideDistance})
                        },label="lumen-page-transition",
                    ) { (tab,destination) ->
                        when(tab) {
                            MainTab.Play -> ReferencePlayRoute(
                                viewModel=playViewModel,
                                modifier=Modifier.fillMaxSize(),
                                onFocusedSubpageChanged={playFocusedSubpage=it},
                                onOpenArena={currentTab=MainTab.Arena},
                            )
                            MainTab.Arena -> ArenaRoute(
                                viewModel=arenaViewModel,
                                modifier=Modifier.fillMaxSize(),
                            )
                            MainTab.Games -> GameLibraryRoute(
                                viewModel = viewModel(factory = GameLibraryViewModel.Factory),
                                modifier = Modifier.fillMaxSize(),
                                reservedGameIds = setOfNotNull(playUi.gameId, playUi.restorableGame?.gameId,
                                    arenaUi.gameId, arenaUi.restorableGame?.gameId),
                                ownershipReady = playUi.ownershipReady && arenaUi.ownershipReady,
                            )
                            MainTab.Settings -> when(destination) {
                                SettingsDestination.ROOT -> SettingsScreen(
                                    settings=appearanceSettings,
                                    onSettingsChange=::persist,
                                    onOpenBoardAppearance={settingsDestination=SettingsDestination.BOARD_APPEARANCE},
                                    onOpenSoundsHaptics={settingsDestination=SettingsDestination.SOUNDS_HAPTICS},
                                    modifier=Modifier.fillMaxSize(),
                                    onOpenPlaySettings={settingsDestination=SettingsDestination.PLAY},
                                    onOpenEngines={settingsDestination=SettingsDestination.ENGINES},
                                    onOpenAbout={settingsDestination=SettingsDestination.ABOUT},
                                )
                                SettingsDestination.PLAY -> PlaySettingsScreen(
                                    settings=appearanceSettings,
                                    onSettingsChange=::persist,
                                    onBack={settingsDestination=SettingsDestination.ROOT},
                                    onOpenBoardAppearance={settingsDestination=SettingsDestination.BOARD_APPEARANCE},
                                    onOpenSoundsHaptics={settingsDestination=SettingsDestination.SOUNDS_HAPTICS},
                                    modifier=Modifier.fillMaxSize(),
                                )
                                SettingsDestination.BOARD_APPEARANCE -> BoardAppearanceScreen(appearanceSettings,::persist,{settingsDestination=SettingsDestination.PLAY},Modifier.fillMaxSize())
                                SettingsDestination.SOUNDS_HAPTICS -> SoundsHapticsScreen(appearanceSettings,::persist,{settingsDestination=SettingsDestination.PLAY},Modifier.fillMaxSize())
                                SettingsDestination.ENGINES -> EnginesSettingsScreen({settingsDestination=SettingsDestination.ROOT},Modifier.fillMaxSize())
                                SettingsDestination.ABOUT -> AboutSettingsScreen({settingsDestination=SettingsDestination.ROOT},Modifier.fillMaxSize())
                            }
                            MainTab.Insights -> InsightsRoute(
                                viewModel = viewModel<InsightsViewModel>(),
                                onPlay = { currentTab = MainTab.Play },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}

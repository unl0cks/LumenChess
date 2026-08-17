package dev.lumenchess.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.play.PlayRoute
import dev.lumenchess.play.PlayScreenMode
import dev.lumenchess.play.PlayViewModel

private enum class MainTab(val label: String) {
    Play("Play"),
    Arena("Arena"),
    Games("Games"),
    Insights("Insights"),
    Settings("Settings"),
}

@Composable
fun LumenChessApp() {
    var currentTab by remember { mutableStateOf(MainTab.Play) }
    val playViewModel: PlayViewModel = viewModel()
    val playUi by playViewModel.uiState
    val livePlay = currentTab == MainTab.Play && playUi.mode == PlayScreenMode.LIVE

    LumenTheme {
        Scaffold(
            bottomBar = {
                if (!livePlay) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { currentTab = tab },
                                icon = { Text(tab.label.take(1)) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (currentTab == MainTab.Play) {
                    PlayRoute(
                        viewModel = playViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(currentTab.label)
                        Text("Coming in a later milestone")
                    }
                }
            }
        }
    }
}

package dev.lumenchess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.play.PlayRoute
import dev.lumenchess.play.PlayScreenMode
import dev.lumenchess.play.PlayViewModel

private enum class MainTab(val label: String, val previewCopy: String) {
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
    val livePlay = currentTab == MainTab.Play && playUi.mode == PlayScreenMode.LIVE

    LumenTheme {
        Scaffold(
            containerColor = LumenColors.Background,
            bottomBar = {
                if (!livePlay) {
                    LumenBottomNavigation(
                        current = currentTab,
                        onSelect = { currentTab = it },
                    )
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
                    FutureSurfacePreview(currentTab)
                }
            }
        }
    }
}

@Composable
private fun LumenBottomNavigation(
    current: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Surface(
        color = LumenColors.Surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(tab) },
                            role = Role.Tab,
                        )
                        .semantics { contentDescription = "${tab.label} tab" }
                        .testTag("main-tab-${tab.label.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 28.dp)
                            .background(
                                if (selected) LumenColors.AccentBlueSoft else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(14.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        LumenTabIcon(tab, selected)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun FutureSurfacePreview(tab: MainTab) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LumenColors.BackgroundLift, LumenColors.Background),
                ),
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LumenColors.Surface,
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = LumenColors.AccentBlueSoft,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        LumenTabIcon(tab, selected = true, modifier = Modifier.size(32.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(tab.label, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    tab.previewCopy,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LumenColors.OnSurfaceMuted,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = LumenColors.SurfaceRaised,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        "Preview · not available in this build",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = LumenColors.AccentBlueBright,
                    )
                }
            }
        }
    }
}

@Composable
private fun LumenTabIcon(
    tab: MainTab,
    selected: Boolean,
    modifier: Modifier = Modifier.size(20.dp),
) {
    val color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.085f
        when (tab) {
            MainTab.Play -> {
                drawCircle(color, radius = w * 0.35f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(stroke))
                drawLine(color, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), stroke, StrokeCap.Round)
            }
            MainTab.Arena -> {
                drawLine(color, Offset(w * 0.24f, h * 0.22f), Offset(w * 0.76f, h * 0.78f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.76f, h * 0.22f), Offset(w * 0.24f, h * 0.78f), stroke, StrokeCap.Round)
                drawCircle(color, radius = stroke * 1.3f, center = Offset(w * 0.23f, h * 0.21f))
                drawCircle(color, radius = stroke * 1.3f, center = Offset(w * 0.77f, h * 0.21f))
            }
            MainTab.Games -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.2f, h * 0.18f),
                    size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                    style = Stroke(stroke),
                )
                repeat(3) { index ->
                    val y = h * (0.34f + index * 0.16f)
                    drawLine(color, Offset(w * 0.32f, y), Offset(w * 0.68f, y), stroke * 0.75f, StrokeCap.Round)
                }
            }
            MainTab.Insights -> {
                drawLine(color, Offset(w * 0.22f, h * 0.76f), Offset(w * 0.22f, h * 0.52f), stroke * 1.5f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.76f), Offset(w * 0.5f, h * 0.34f), stroke * 1.5f, StrokeCap.Round)
                drawLine(color, Offset(w * 0.78f, h * 0.76f), Offset(w * 0.78f, h * 0.2f), stroke * 1.5f, StrokeCap.Round)
            }
            MainTab.Settings -> {
                drawCircle(color, radius = w * 0.29f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(stroke))
                drawCircle(color, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(stroke))
                drawLine(color, Offset(w * 0.08f, h * 0.5f), Offset(w * 0.2f, h * 0.5f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.8f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.5f, h * 0.2f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.5f, h * 0.8f), Offset(w * 0.5f, h * 0.92f), stroke, StrokeCap.Round)
            }
        }
    }
}

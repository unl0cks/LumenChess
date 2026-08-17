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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors

@Composable
internal fun LumenBottomNavigation(current: MainTab, onSelect: (MainTab) -> Unit) {
    Surface(color = LumenColors.Surface.copy(alpha = .98f), tonalElevation = 0.dp) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp, vertical = 6.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier.weight(1f).height(56.dp)
                        .selectable(selected, { onSelect(tab) }, role = Role.Tab)
                        .semantics { contentDescription = "${tab.label} tab" }
                        .testTag("main-tab-${tab.label.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(width = 38.dp, height = 28.dp).background(
                            if (selected) LumenColors.AccentBlueSoft else androidx.compose.ui.graphics.Color.Transparent,
                            RoundedCornerShape(14.dp),
                        ),
                        contentAlignment = Alignment.Center,
                    ) { TabIcon(tab, selected) }
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelMedium, color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted)
                }
            }
        }
    }
}

@Composable
internal fun FutureSurfacePreview(tab: MainTab) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background))).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(Modifier.fillMaxWidth(), color = LumenColors.Surface, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = LumenColors.AccentBlueSoft, shape = RoundedCornerShape(18.dp)) {
                    Box(Modifier.padding(15.dp), contentAlignment = Alignment.Center) { TabIcon(tab, true, Modifier.size(30.dp)) }
                }
                Spacer(Modifier.height(16.dp))
                Text(tab.label, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(5.dp))
                Text(tab.previewCopy, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
                Spacer(Modifier.height(14.dp))
                Surface(color = LumenColors.SurfaceRaised, shape = RoundedCornerShape(10.dp)) {
                    Text(
                        "Preview · not available in this build",
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = LumenColors.AccentBlueBright,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab, selected: Boolean, modifier: Modifier = Modifier.size(20.dp)) {
    val color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = size.minDimension * .085f
        when (tab) {
            MainTab.Play -> {
                drawCircle(color, w*.34f, Offset(w*.5f,h*.5f), style = Stroke(s))
                drawLine(color, Offset(w*.5f,h*.2f), Offset(w*.5f,h*.8f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.2f,h*.5f), Offset(w*.8f,h*.5f), s, StrokeCap.Round)
            }
            MainTab.Arena -> {
                drawLine(color, Offset(w*.24f,h*.22f), Offset(w*.76f,h*.78f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.76f,h*.22f), Offset(w*.24f,h*.78f), s, StrokeCap.Round)
                drawCircle(color, s*1.3f, Offset(w*.23f,h*.21f)); drawCircle(color, s*1.3f, Offset(w*.77f,h*.21f))
            }
            MainTab.Games -> {
                drawRoundRect(color, Offset(w*.2f,h*.18f), Size(w*.6f,h*.64f), CornerRadius(w*.08f), style = Stroke(s))
                repeat(3) { i -> val y=h*(.34f+i*.16f); drawLine(color,Offset(w*.32f,y),Offset(w*.68f,y),s*.75f,StrokeCap.Round) }
            }
            MainTab.Insights -> {
                drawLine(color,Offset(w*.22f,h*.76f),Offset(w*.22f,h*.52f),s*1.5f,StrokeCap.Round)
                drawLine(color,Offset(w*.5f,h*.76f),Offset(w*.5f,h*.34f),s*1.5f,StrokeCap.Round)
                drawLine(color,Offset(w*.78f,h*.76f),Offset(w*.78f,h*.2f),s*1.5f,StrokeCap.Round)
            }
            MainTab.Settings -> {
                drawCircle(color,w*.29f,Offset(w*.5f,h*.5f),style=Stroke(s)); drawCircle(color,w*.09f,Offset(w*.5f,h*.5f),style=Stroke(s))
                drawLine(color,Offset(w*.08f,h*.5f),Offset(w*.2f,h*.5f),s,StrokeCap.Round); drawLine(color,Offset(w*.8f,h*.5f),Offset(w*.92f,h*.5f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.5f,h*.08f),Offset(w*.5f,h*.2f),s,StrokeCap.Round); drawLine(color,Offset(w*.5f,h*.8f),Offset(w*.5f,h*.92f),s,StrokeCap.Round)
            }
        }
    }
}

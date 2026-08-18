package dev.lumenchess.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenRadii

@Composable
internal fun LumenBottomNavigation(current: MainTab, onSelect: (MainTab) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(50.dp).background(LumenColors.Surface.copy(alpha=.99f))
            .border(1.dp,LumenColors.Outline.copy(alpha=.62f),RoundedCornerShape(0.dp)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal=5.dp).selectableGroup(), horizontalArrangement=Arrangement.spacedBy(1.dp)) {
            MainTab.entries.forEach { tab ->
                val selected = tab == current
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val tint by animateColorAsState(
                    if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
                    LumenMotion.fastTween(), label="nav-tint-${tab.label}",
                )
                val scale by animateFloatAsState(
                    if (pressed) .94f else if (selected) 1.03f else 1f,
                    if (pressed) LumenMotion.fastTween() else LumenMotion.normalTween(),
                    label="nav-scale-${tab.label}",
                )
                Column(
                    Modifier.weight(1f).height(50.dp)
                        .clickable(interactionSource=interaction,indication=null,role=Role.Tab,onClick={ onSelect(tab) })
                        .semantics { contentDescription = "${tab.label} tab" }
                        .testTag("main-tab-${tab.label.lowercase()}"),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.Center,
                ) {
                    Box(Modifier.height(1.5.dp).fillMaxWidth(.26f).background(if (selected) LumenColors.AccentBlueBright else Color.Transparent,RoundedCornerShape(2.dp)))
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.size(18.dp).graphicsLayer { scaleX=scale; scaleY=scale }, contentAlignment=Alignment.Center) {
                        TabIcon(tab,tint,Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(tab.label,style=MaterialTheme.typography.labelSmall,color=tint,maxLines=1)
                }
            }
        }
    }
}

@Composable
internal fun FutureSurfacePreview(tab: MainTab) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift,LumenColors.Background)))
            .padding(horizontal=15.dp,vertical=16.dp),
    ) {
        Column(verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Text(tab.label,style=MaterialTheme.typography.headlineMedium,color=LumenColors.OnSurface)
            Box(
                Modifier.fillMaxWidth().background(LumenColors.Surface,RoundedCornerShape(LumenRadii.Panel))
                    .border(1.dp,LumenColors.Outline,RoundedCornerShape(LumenRadii.Panel)).padding(horizontal=11.dp,vertical=10.dp),
            ) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier.size(31.dp).background(LumenColors.AccentBlueGhost,RoundedCornerShape(LumenRadii.Control))
                            .border(1.dp,LumenColors.AccentBlue.copy(alpha=.45f),RoundedCornerShape(LumenRadii.Control)),
                        contentAlignment=Alignment.Center,
                    ) { TabIcon(tab,LumenColors.AccentBlueBright,Modifier.size(18.dp)) }
                    Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        Text(tab.label,style=MaterialTheme.typography.titleMedium,color=LumenColors.OnSurface)
                        Text(tab.previewCopy,style=MaterialTheme.typography.bodySmall,color=LumenColors.OnSurfaceMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIcon(tab: MainTab,color: Color,modifier: Modifier=Modifier.size(18.dp)) {
    Canvas(modifier) {
        val w=size.width; val h=size.height; val s=size.minDimension*.082f
        when(tab) {
            MainTab.Play -> {
                drawCircle(color,w*.34f,Offset(w*.5f,h*.5f),style=Stroke(s))
                drawLine(color,Offset(w*.5f,h*.2f),Offset(w*.5f,h*.8f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.2f,h*.5f),Offset(w*.8f,h*.5f),s,StrokeCap.Round)
            }
            MainTab.Arena -> {
                drawLine(color,Offset(w*.24f,h*.22f),Offset(w*.76f,h*.78f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.76f,h*.22f),Offset(w*.24f,h*.78f),s,StrokeCap.Round)
                drawCircle(color,s*1.25f,Offset(w*.23f,h*.21f)); drawCircle(color,s*1.25f,Offset(w*.77f,h*.21f))
            }
            MainTab.Games -> {
                drawRoundRect(color,Offset(w*.2f,h*.18f),Size(w*.6f,h*.64f),CornerRadius(w*.08f),style=Stroke(s))
                repeat(3) { i -> val y=h*(.34f+i*.16f); drawLine(color,Offset(w*.32f,y),Offset(w*.68f,y),s*.75f,StrokeCap.Round) }
            }
            MainTab.Insights -> {
                drawLine(color,Offset(w*.22f,h*.76f),Offset(w*.22f,h*.52f),s*1.45f,StrokeCap.Round)
                drawLine(color,Offset(w*.5f,h*.76f),Offset(w*.5f,h*.34f),s*1.45f,StrokeCap.Round)
                drawLine(color,Offset(w*.78f,h*.76f),Offset(w*.78f,h*.2f),s*1.45f,StrokeCap.Round)
            }
            MainTab.Settings -> {
                drawCircle(color,w*.29f,Offset(w*.5f,h*.5f),style=Stroke(s)); drawCircle(color,w*.09f,Offset(w*.5f,h*.5f),style=Stroke(s))
                drawLine(color,Offset(w*.08f,h*.5f),Offset(w*.2f,h*.5f),s,StrokeCap.Round); drawLine(color,Offset(w*.8f,h*.5f),Offset(w*.92f,h*.5f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.5f,h*.08f),Offset(w*.5f,h*.2f),s,StrokeCap.Round); drawLine(color,Offset(w*.5f,h*.8f),Offset(w*.5f,h*.92f),s,StrokeCap.Round)
            }
        }
    }
}

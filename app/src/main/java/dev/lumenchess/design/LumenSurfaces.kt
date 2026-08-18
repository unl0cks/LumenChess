package dev.lumenchess.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LumenPanel(modifier: Modifier = Modifier, selected: Boolean = false, content: @Composable () -> Unit) {
    val border by animateColorAsState(
        if (selected) LumenColors.AccentBlueBright else LumenColors.Outline,
        LumenMotion.fastTween(), label = "lumen-panel-border",
    )
    val shape = RoundedCornerShape(LumenRadii.Panel)
    val fill = Brush.verticalGradient(
        if (selected) listOf(LumenColors.AccentBlueSoft, LumenColors.Surface)
        else listOf(LumenColors.SurfaceRaised, LumenColors.Surface),
    )
    Box(
        modifier.clip(shape).background(fill).border(1.dp, border, shape)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) { content() }
}

@Composable
fun LumenListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailingText: String? = null,
    showChevron: Boolean = onClick != null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) .992f else 1f,
        if (pressed) LumenMotion.fastTween() else LumenMotion.releaseSpring(),
        label = "lumen-list-row-scale",
    )
    val shape = RoundedCornerShape(7.dp)
    val fill = Brush.verticalGradient(
        if (pressed && enabled) listOf(LumenColors.SurfaceHighest, LumenColors.SurfaceRaised)
        else listOf(LumenColors.SurfaceRaised, LumenColors.Surface),
    )
    var resolved = modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape).background(fill)
        .border(1.dp, if (pressed && enabled) LumenColors.OutlineStrong else LumenColors.Outline, shape)
    if (onClick != null) {
        resolved = resolved.clickable(
            interactionSource = interaction, indication = null, enabled = enabled,
            role = Role.Button, onClick = onClick,
        )
    }
    Row(
        resolved.padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.let { Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { it() } }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium,
                color = if (enabled) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle, style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) LumenColors.OnSurfaceMuted else LumenColors.OnSurfaceFaint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingText?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted, maxLines = 1)
        }
        if (showChevron) LumenChevron(enabled)
    }
}

@Composable
fun LumenSettingRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    var resolved = modifier
    if (testTag != null) resolved = resolved.testTag(testTag)
    Row(
        resolved.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) LumenColors.OnSurface else LumenColors.OnSurfaceMuted)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted)
        }
        LumenToggle(checked, onCheckedChange, enabled = enabled, contentDescription = title)
    }
}

@Composable
fun LumenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backTestTag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val backIconColor = LumenColors.OnSurfaceMuted
    Box(modifier.fillMaxWidth().height(46.dp)) {
        Text(
            title,
            Modifier.align(Alignment.Center).padding(horizontal = 48.dp).testTag("lumen-topbar-title"),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = LumenColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (onBack != null) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                if (pressed) LumenMotion.IconPressScale else 1f,
                if (pressed) LumenMotion.fastTween() else LumenMotion.releaseSpring(),
                label = "lumen-back-scale",
            )
            var back = Modifier.align(Alignment.CenterStart).size(46.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(LumenRadii.Compact))
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Navigate back" }
            if (backTestTag != null) back = back.testTag(backTestTag)
            Box(back, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(18.dp)) {
                    val stroke = 1.6.dp.toPx()
                    drawLine(backIconColor, Offset(size.width*.72f,size.height*.18f), Offset(size.width*.35f,size.height*.5f), stroke, StrokeCap.Round)
                    drawLine(backIconColor, Offset(size.width*.35f,size.height*.5f), Offset(size.width*.72f,size.height*.82f), stroke, StrokeCap.Round)
                }
            }
        }
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun LumenDropdownRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    var resolved = modifier
    if (testTag != null) resolved = resolved.testTag(testTag)
    LumenListRow(title, modifier = resolved, onClick = onClick, leading = leading, trailingText = value, showChevron = true)
}

@Composable
fun LumenTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    Row(modifier.fillMaxWidth().height(40.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val tint by animateColorAsState(
                if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
                LumenMotion.fastTween(), label = "lumen-tab-tint-$index",
            )
            var tab = Modifier.weight(1f).height(40.dp).clickable(
                interactionSource = interaction, indication = null, role = Role.Tab,
                onClick = { onSelected(index) },
            )
            if (testTagPrefix != null) tab = tab.testTag("$testTagPrefix-$index")
            Box(tab, contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = if (pressed) LumenColors.OnSurface else tint, maxLines = 1)
                if (selected) Box(
                    Modifier.align(Alignment.BottomCenter).height(2.dp).fillMaxWidth(.62f)
                        .clip(CircleShape).background(LumenColors.AccentBlueBright),
                )
            }
        }
    }
}

@Composable
fun LumenEngineBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(28.dp).clip(CircleShape)
            .background(Brush.radialGradient(listOf(LumenColors.AccentBlueBright, LumenColors.AccentBlue)))
            .border(1.dp, LumenColors.AccentBlueBright.copy(alpha=.72f), CircleShape)
            .semantics { contentDescription = "$label engine" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(15.dp)) {
            val path = Path(); val cx=size.width/2; val cy=size.height/2
            val outer=size.minDimension*.48f; val inner=outer*.43f
            repeat(10) { i ->
                val r=if (i%2==0) outer else inner; val a=-PI/2+i*PI/5
                val x=cx+cos(a).toFloat()*r; val y=cy+sin(a).toFloat()*r
                if (i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }
            path.close(); drawPath(path, Color(0xFFF5F7F7))
        }
    }
}

@Composable
fun LumenClock(text: String, modifier: Modifier = Modifier, active: Boolean = false, light: Boolean = false) {
    val animatedBorder by animateColorAsState(
        if (active) LumenColors.AccentBlue.copy(alpha=.72f) else LumenColors.Outline,
        LumenMotion.fastTween(), label = "lumen-clock-border",
    )
    val bg = if (light) Color(0xFFF0EFE8) else if (active) LumenColors.SurfaceHighest else LumenColors.Background
    val border = if (light) Color(0xFFCAC8BE) else animatedBorder
    val textColor = if (light) Color(0xFF16191A) else LumenColors.OnSurface
    Box(
        modifier.defaultMinSize(minWidth=84.dp,minHeight=40.dp).clip(RoundedCornerShape(6.dp))
            .background(bg).border(1.dp,border,RoundedCornerShape(6.dp)).padding(horizontal=10.dp,vertical=5.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style=MaterialTheme.typography.titleLarge, fontWeight=FontWeight.SemiBold, color=textColor, maxLines=1) }
}

@Composable
fun LumenToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val track by animateColorAsState(if (checked) LumenColors.AccentBlue.copy(alpha=.70f) else LumenColors.SurfaceHighest, LumenMotion.normalTween(), label="lumen-toggle-track")
    val border by animateColorAsState(if (checked) LumenColors.AccentBlueBright else LumenColors.OutlineStrong, LumenMotion.normalTween(), label="lumen-toggle-border")
    val thumbOffset by animateDpAsState(if (checked) 15.dp else 3.dp, LumenMotion.normalTween(), label="lumen-toggle-thumb")
    var resolved = modifier.size(width=48.dp,height=48.dp).toggleable(
        value=checked, interactionSource=interaction, indication=null, enabled=enabled,
        role=Role.Switch, onValueChange=onCheckedChange,
    )
    if (contentDescription != null) resolved = resolved.semantics { this.contentDescription = contentDescription }
    Box(resolved, contentAlignment=Alignment.Center) {
        Box(Modifier.size(width=32.dp,height=20.dp).clip(CircleShape).background(track).border(1.dp,border,CircleShape)) {
            Box(Modifier.offset(x=thumbOffset,y=3.dp).size(14.dp).clip(CircleShape).background(if (checked) LumenColors.OnSurface else LumenColors.OnSurfaceMuted))
        }
    }
}

@Composable
private fun LumenChevron(enabled: Boolean) {
    val color = if (enabled) LumenColors.OnSurfaceMuted else LumenColors.OutlineStrong
    Canvas(Modifier.size(14.dp)) {
        val stroke=1.35.dp.toPx()
        drawLine(color, Offset(size.width*.36f,size.height*.25f), Offset(size.width*.64f,size.height*.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width*.64f,size.height*.5f), Offset(size.width*.36f,size.height*.75f), stroke, StrokeCap.Round)
    }
}

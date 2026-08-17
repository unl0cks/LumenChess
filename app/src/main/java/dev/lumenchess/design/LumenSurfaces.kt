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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LumenPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val border by animateColorAsState(
        targetValue = if (selected) LumenColors.AccentBlueBright else LumenColors.Outline,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-panel-border",
    )
    val fill by animateColorAsState(
        targetValue = if (selected) LumenColors.AccentBlueGhost else LumenColors.Surface,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-panel-fill",
    )
    val shape = RoundedCornerShape(LumenRadii.Panel)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
    val fill by animateColorAsState(
        targetValue = if (pressed && enabled) LumenColors.SurfaceHighest else LumenColors.Surface,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-list-row-fill",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.99f else 1f,
        animationSpec = if (pressed) LumenMotion.fastTween() else LumenMotion.releaseSpring(),
        label = "lumen-list-row-scale",
    )
    val shape = RoundedCornerShape(LumenRadii.Panel)
    var rowModifier = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .background(fill)
        .border(1.dp, LumenColors.Outline, shape)
    if (onClick != null) {
        rowModifier = rowModifier.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Row(
        modifier = rowModifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.let {
            Box(
                Modifier.size(30.dp),
                contentAlignment = Alignment.Center,
            ) { it() }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) LumenColors.OnSurface else LumenColors.OnSurfaceFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) LumenColors.OnSurfaceMuted else LumenColors.OnSurfaceFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) LumenColors.OnSurfaceMuted else LumenColors.OnSurfaceFaint,
                maxLines = 1,
            )
        }
        if (showChevron) LumenChevron(enabled = enabled)
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
    LumenListRow(
        title = title,
        subtitle = subtitle,
        modifier = resolved,
        enabled = enabled,
        trailingText = null,
        showChevron = false,
    )
    // Overlaying a separate row would duplicate semantics, so callers that need an
    // inline toggle should use LumenToggle in their own compact Row. This overload
    // remains intentionally small and is not used for screen-wide switches.
}

@Composable
fun LumenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backTestTag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) LumenMotion.IconPressScale else 1f,
                animationSpec = if (pressed) LumenMotion.fastTween() else LumenMotion.releaseSpring(),
                label = "lumen-back-scale",
            )
            var backModifier = Modifier
                .size(48.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(LumenRadii.Compact))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .semantics { contentDescription = "Navigate back" }
            if (backTestTag != null) backModifier = backModifier.testTag(backTestTag)
            Box(backModifier, contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(20.dp)) {
                    val stroke = 1.8.dp.toPx()
                    drawLine(LumenColors.OnSurfaceMuted, Offset(size.width * .72f, size.height * .18f), Offset(size.width * .35f, size.height * .5f), stroke, StrokeCap.Round)
                    drawLine(LumenColors.OnSurfaceMuted, Offset(size.width * .35f, size.height * .5f), Offset(size.width * .72f, size.height * .82f), stroke, StrokeCap.Round)
                }
            }
            Spacer(Modifier.width(2.dp))
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = LumenColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
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
    LumenListRow(
        title = title,
        subtitle = null,
        modifier = resolved,
        onClick = onClick,
        leading = leading,
        trailingText = value,
        showChevron = true,
    )
}

@Composable
fun LumenTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    Row(modifier = modifier.fillMaxWidth().height(48.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val tint by animateColorAsState(
                targetValue = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
                animationSpec = LumenMotion.fastTween(),
                label = "lumen-tab-tint-$index",
            )
            var tabModifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onSelected(index) },
                )
            if (testTagPrefix != null) tabModifier = tabModifier.testTag("$testTagPrefix-$index")
            Box(tabModifier, contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pressed) LumenColors.OnSurface else tint,
                    maxLines = 1,
                )
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .height(2.dp)
                            .fillMaxWidth(.58f)
                            .clip(CircleShape)
                            .background(LumenColors.AccentBlueBright),
                    )
                }
            }
        }
    }
}

@Composable
fun LumenEngineBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LumenColors.AccentBlueSoft)
            .border(1.dp, LumenColors.AccentBlue.copy(alpha = .55f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.take(1).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = LumenColors.AccentBlueBright,
        )
    }
}

@Composable
fun LumenClock(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val border by animateColorAsState(
        targetValue = if (active) LumenColors.AccentBlue.copy(alpha = .75f) else LumenColors.Outline,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-clock-border",
    )
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(LumenRadii.Control))
            .background(if (active) LumenColors.SurfaceHighest else LumenColors.Background)
            .border(1.dp, border, RoundedCornerShape(LumenRadii.Control))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = LumenColors.OnSurface,
            maxLines = 1,
        )
    }
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
    val track by animateColorAsState(
        targetValue = if (checked) LumenColors.AccentBlue.copy(alpha = .70f) else LumenColors.SurfaceHighest,
        animationSpec = LumenMotion.normalTween(),
        label = "lumen-toggle-track",
    )
    val border by animateColorAsState(
        targetValue = if (checked) LumenColors.AccentBlueBright else LumenColors.OutlineStrong,
        animationSpec = LumenMotion.normalTween(),
        label = "lumen-toggle-border",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 17.dp else 3.dp,
        animationSpec = LumenMotion.normalTween(),
        label = "lumen-toggle-thumb",
    )
    var resolved = modifier
        .size(width = 48.dp, height = 48.dp)
        .toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
    if (contentDescription != null) resolved = resolved.semantics { this.contentDescription = contentDescription }
    Box(resolved, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 22.dp)
                .clip(CircleShape)
                .background(track)
                .border(1.dp, border, CircleShape),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset, y = 3.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (checked) LumenColors.OnSurface else LumenColors.OnSurfaceMuted),
            )
        }
    }
}

@Composable
private fun LumenChevron(enabled: Boolean) {
    Canvas(Modifier.size(16.dp)) {
        val color = if (enabled) LumenColors.OnSurfaceFaint else LumenColors.Outline
        val stroke = 1.5.dp.toPx()
        drawLine(color, Offset(size.width * .36f, size.height * .25f), Offset(size.width * .64f, size.height * .5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * .64f, size.height * .5f), Offset(size.width * .36f, size.height * .75f), stroke, StrokeCap.Round)
    }
}

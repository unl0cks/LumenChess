package dev.lumenchess.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

object LumenDimensions {
    val MinimumTouchTarget = 48.dp
    val ControlHeight = 46.dp
    val CompactControlHeight = 40.dp
    val ScreenPadding = 16.dp
    val CardSpacing = 10.dp
    val Border = 1.dp
}

@Composable
private fun Modifier.lumenPressState(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = LumenMotion.PressScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) LumenMotion.fastTween() else LumenMotion.releaseSpring(),
        label = "lumen-press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun LumenPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (pressed) LumenColors.AccentBlueBright else LumenColors.AccentBlue,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-primary-border",
    )
    val shape = RoundedCornerShape(LumenRadii.Control)
    var resolved = modifier
        .defaultMinSize(minWidth = LumenDimensions.MinimumTouchTarget, minHeight = LumenDimensions.MinimumTouchTarget)
    if (testTag != null) resolved = resolved.testTag(testTag)
    Box(
        modifier = resolved
            .lumenPressState(interaction)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.verticalGradient(
                        listOf(
                            LumenColors.AccentBlueBright.copy(alpha = if (pressed) .84f else .96f),
                            LumenColors.AccentBlue.copy(alpha = if (pressed) .82f else 1f),
                        ),
                    )
                } else {
                    Brush.verticalGradient(listOf(LumenColors.SurfaceRaised, LumenColors.SurfaceRaised))
                },
            )
            .border(LumenDimensions.Border, if (enabled) borderColor else LumenColors.Outline, shape)
            .drawBehind {
                if (enabled) {
                    drawRect(
                        color = borderColor.copy(alpha = if (pressed) .14f else .08f),
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = 14.dp, vertical = 11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) Color.White else LumenColors.OnSurfaceFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compatibility alias retained for P1-P4 call sites/tests. */
@Composable
fun LumenButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = LumenPrimaryButton(label = label, onClick = onClick, modifier = modifier)

@Composable
fun LumenSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LumenOutlinedButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        foreground = LumenColors.OnSurface,
        activeBorder = LumenColors.AccentBlue,
    )
}

@Composable
fun LumenDangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LumenOutlinedButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        foreground = LumenColors.Destructive,
        activeBorder = LumenColors.Destructive,
    )
}

@Composable
private fun LumenOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    foreground: Color,
    activeBorder: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(LumenRadii.Control)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = LumenDimensions.MinimumTouchTarget, minHeight = LumenDimensions.MinimumTouchTarget)
            .lumenPressState(interaction)
            .clip(shape)
            .background(if (pressed) LumenColors.SurfaceHighest else LumenColors.SurfaceRaised)
            .border(1.dp, if (pressed) activeBorder else LumenColors.Outline, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) foreground else LumenColors.OnSurfaceFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LumenIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(contentDescription.isNotBlank()) { "Interactive icons require a content description." }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(LumenRadii.Compact)
    Box(
        modifier = modifier
            .size(LumenDimensions.MinimumTouchTarget)
            .lumenPressState(interaction, LumenMotion.IconPressScale)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(shape)
                .background(if (pressed) LumenColors.SurfaceHighest else Color.Transparent)
                .border(1.dp, if (pressed) LumenColors.OutlineStrong else Color.Transparent, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled) LumenColors.OnSurfaceMuted else LumenColors.OnSurfaceFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

data class LumenSegmentOption<T>(val value: T, val label: String)

@Composable
fun <T> LumenSegmentedControl(
    options: List<LumenSegmentOption<T>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            LumenSegment(
                label = option.label,
                selected = option.value == selected,
                onClick = { onSelected(option.value) },
                modifier = Modifier.weight(1f),
                testTag = testTagPrefix?.let { "$it-${option.label.lowercase().replace(' ', '-')}" },
            )
        }
    }
}

@Composable
fun RowScope.LumenSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (selected) LumenColors.AccentBlueGhost else if (pressed) LumenColors.SurfaceHighest else LumenColors.SurfaceRaised,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-segment-fill",
    )
    val border by animateColorAsState(
        targetValue = if (selected) LumenColors.AccentBlueBright else if (pressed) LumenColors.OutlineStrong else LumenColors.Outline,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-segment-border",
    )
    val text by animateColorAsState(
        targetValue = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
        animationSpec = LumenMotion.fastTween(),
        label = "lumen-segment-text",
    )
    val shape = RoundedCornerShape(LumenRadii.Control)
    var resolved = modifier
        .defaultMinSize(minHeight = LumenDimensions.MinimumTouchTarget)
    if (testTag != null) resolved = resolved.testTag(testTag)
    Box(
        modifier = resolved
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) text else LumenColors.OnSurfaceFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LumenSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    enabled: Boolean = true,
) {
    fun snapped(raw: Float): Float {
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return clamped
        val intervals = steps + 1
        val fraction = (clamped - valueRange.start) / (valueRange.endInclusive - valueRange.start)
        return valueRange.start + (valueRange.endInclusive - valueRange.start) *
            ((fraction * intervals).roundToInt() / intervals.toFloat())
    }

    val inactiveTrack = LumenColors.OutlineStrong
    val activeTrack = if (enabled) LumenColors.AccentBlue else LumenColors.OnSurfaceFaint
    val thumbColor = if (enabled) LumenColors.AccentBlueBright else LumenColors.OnSurfaceFaint
    val thumbEdge = LumenColors.Background.copy(alpha = .7f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LumenDimensions.MinimumTouchTarget)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                if (!enabled) disabled()
                setProgress { target ->
                    if (!enabled) false else {
                        onValueChange(snapped(target))
                        true
                    }
                }
            }
            .pointerInput(enabled, valueRange, steps) {
                if (!enabled) return@pointerInput
                fun resolve(x: Float): Float {
                    val width = size.width.coerceAtLeast(1)
                    val fraction = (x / width).coerceIn(0f, 1f)
                    return snapped(valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction)
                }
                detectTapGestures { offset -> onValueChange(resolve(offset.x)) }
            }
            .pointerInput(enabled, valueRange, steps) {
                if (!enabled) return@pointerInput
                fun resolve(x: Float): Float {
                    val width = size.width.coerceAtLeast(1)
                    val fraction = (x / width).coerceIn(0f, 1f)
                    return snapped(valueRange.start + (valueRange.endInclusive - valueRange.start) * fraction)
                }
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange(resolve(change.position.x))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val trackHeight = 3.dp.toPx()
            val y = size.height / 2f
            val thumbRadius = 6.dp.toPx()
            drawRoundRect(
                color = inactiveTrack,
                topLeft = androidx.compose.ui.geometry.Offset(0f, y - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight),
            )
            drawRoundRect(
                color = activeTrack,
                topLeft = androidx.compose.ui.geometry.Offset(0f, y - trackHeight / 2f),
                size = androidx.compose.ui.geometry.Size(size.width * fraction, trackHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight),
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadius,
                center = androidx.compose.ui.geometry.Offset(size.width * fraction, y),
            )
            drawCircle(
                color = thumbEdge,
                radius = thumbRadius,
                center = androidx.compose.ui.geometry.Offset(size.width * fraction, y),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }
    }
}

package dev.lumenchess.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class DerivativeSurfaceRole {
    NEUTRAL_ROW,
    RECESSED_TRAY,
    SELECTED_FACE,
    DISABLED_SURFACE,
    PREVIEW_PANEL,
    ACTION,
}

internal data class DerivativeSurfaceSpec(
    val restElevationDp: Float,
    val pressedElevationDp: Float,
    val pressedScale: Float,
    val pressedOffsetDp: Float,
    val measuredHeightDeltaDp: Float,
    val outlineAlpha: Float,
    val illuminationAlpha: Float,
    val edgeDarkeningAlpha: Float,
    val radiusDp: Float,
    val requiresOpaqueFill: Boolean,
    val usesFiniteHighlight: Boolean,
)

internal fun derivativeSurfaceSpec(role: DerivativeSurfaceRole): DerivativeSurfaceSpec = when (role) {
    DerivativeSurfaceRole.NEUTRAL_ROW -> DerivativeSurfaceSpec(
        restElevationDp = 4f,
        pressedElevationDp = 1f,
        pressedScale = .994f,
        pressedOffsetDp = 1.25f,
        measuredHeightDeltaDp = 0f,
        outlineAlpha = .13f,
        illuminationAlpha = .10f,
        edgeDarkeningAlpha = .06f,
        radiusDp = 11f,
        requiresOpaqueFill = true,
        usesFiniteHighlight = false,
    )

    DerivativeSurfaceRole.RECESSED_TRAY -> DerivativeSurfaceSpec(
        restElevationDp = 0f,
        pressedElevationDp = 0f,
        pressedScale = 1f,
        pressedOffsetDp = 0f,
        measuredHeightDeltaDp = 0f,
        outlineAlpha = .10f,
        illuminationAlpha = .035f,
        edgeDarkeningAlpha = .22f,
        radiusDp = 12f,
        requiresOpaqueFill = true,
        usesFiniteHighlight = false,
    )

    DerivativeSurfaceRole.SELECTED_FACE -> DerivativeSurfaceSpec(
        restElevationDp = 5f,
        pressedElevationDp = 1f,
        pressedScale = .992f,
        pressedOffsetDp = 1.5f,
        measuredHeightDeltaDp = 0f,
        outlineAlpha = .31f,
        illuminationAlpha = .18f,
        edgeDarkeningAlpha = .08f,
        radiusDp = 9f,
        requiresOpaqueFill = true,
        usesFiniteHighlight = false,
    )

    DerivativeSurfaceRole.DISABLED_SURFACE -> DerivativeSurfaceSpec(
        restElevationDp = 1f,
        pressedElevationDp = 0f,
        pressedScale = 1f,
        pressedOffsetDp = 0f,
        measuredHeightDeltaDp = 0f,
        outlineAlpha = .07f,
        illuminationAlpha = .025f,
        edgeDarkeningAlpha = .08f,
        radiusDp = 11f,
        requiresOpaqueFill = true,
        usesFiniteHighlight = false,
    )

    DerivativeSurfaceRole.PREVIEW_PANEL -> DerivativeSurfaceSpec(
        restElevationDp = 3f,
        pressedElevationDp = 1f,
        pressedScale = .995f,
        pressedOffsetDp = 1f,
        measuredHeightDeltaDp = 0f,
        outlineAlpha = .12f,
        illuminationAlpha = .08f,
        edgeDarkeningAlpha = .10f,
        radiusDp = 13f,
        requiresOpaqueFill = true,
        usesFiniteHighlight = false,
    )

    DerivativeSurfaceRole.ACTION -> DerivativeSurfaceSpec(
        restElevationDp = 3f,
        pressedElevationDp = 0f,
        pressedScale = .986f,
        pressedOffsetDp = 1.75f,
        measuredHeightDeltaDp = 0f,
        outlineAlpha = .18f,
        illuminationAlpha = .10f,
        edgeDarkeningAlpha = .10f,
        radiusDp = 9f,
        requiresOpaqueFill = true,
        usesFiniteHighlight = false,
    )
}

@Composable
internal fun LumenDerivativePage(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    scrollable: Boolean = false,
    horizontalPadding: Int = 16,
    verticalPadding: Int = 4,
    spacing: Int = 8,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = lumenP5IdentityPalette()
    var resolved = modifier
        .fillMaxSize()
        .derivativePageBackground(palette)
    if (testTag != null) resolved = resolved.testTag(testTag)
    if (scrollable) resolved = resolved.verticalScroll(rememberScrollState())
    Column(
        resolved.padding(horizontal = horizontalPadding.dp, vertical = verticalPadding.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
        content = content,
    )
}

@Composable
internal fun LumenDerivativeTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTestTag: String? = null,
) {
    val palette = lumenP5IdentityPalette()
    Box(modifier.fillMaxWidth().height(52.dp)) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 52.dp).testTag("lumen-topbar-title"),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) .94f else 1f,
            animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
            label = "derivative-back-scale-$title",
        )
        var back = Modifier
            .align(Alignment.CenterStart)
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onBack,
            )
            .semantics { contentDescription = "Navigate back" }
        if (backTestTag != null) back = back.testTag(backTestTag)
        Box(back, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = 1.85.dp.toPx()
                val path = Path().apply {
                    moveTo(size.width * .68f, size.height * .18f)
                    lineTo(size.width * .33f, size.height * .50f)
                    lineTo(size.width * .68f, size.height * .82f)
                }
                drawPath(
                    path = path,
                    color = if (pressed) palette.cyanMicro else palette.muted,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
internal fun LumenDerivativeSurface(
    role: DerivativeSurfaceRole,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = lumenP5IdentityPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec = derivativeSurfaceSpec(if (enabled) role else DerivativeSurfaceRole.DISABLED_SURFACE)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) spec.pressedScale else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "derivative-surface-scale-$role",
    )
    val offset by animateDpAsState(
        targetValue = if (pressed && enabled) spec.pressedOffsetDp.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "derivative-surface-offset-$role",
    )
    val elevation by animateDpAsState(
        targetValue = (if (pressed && enabled) spec.pressedElevationDp else spec.restElevationDp).dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "derivative-surface-elevation-$role",
    )
    val colors = derivativeSurfaceColors(role = if (enabled) role else DerivativeSurfaceRole.DISABLED_SURFACE, palette = palette, pressed = pressed)
    val top by animateColorAsState(colors.top, LumenMotion.fastTween(), label = "derivative-top-$role")
    val middle by animateColorAsState(colors.middle, LumenMotion.fastTween(), label = "derivative-middle-$role")
    val bottom by animateColorAsState(colors.bottom, LumenMotion.fastTween(), label = "derivative-bottom-$role")
    val outline by animateColorAsState(colors.outline, LumenMotion.fastTween(), label = "derivative-outline-$role")
    val shape = RoundedCornerShape(spec.radiusDp.dp)

    var outer = modifier.defaultMinSize(minWidth = LumenDimensions.MinimumTouchTarget)
    if (testTag != null) outer = outer.testTag(testTag)
    if (onClick != null) {
        outer = outer.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }

    Box(
        outer
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset.toPx()
            }
            .shadow(elevation, shape, clip = false)
            .derivativeSurfaceFace(
                spec = spec,
                top = top,
                middle = middle,
                bottom = bottom,
                outline = outline,
                illumination = colors.illumination,
            )
            .padding(contentPadding),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
internal fun LumenDerivativeRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    showChevron: Boolean = onClick != null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativeSurface(
        role = if (enabled) DerivativeSurfaceRole.NEUTRAL_ROW else DerivativeSurfaceRole.DISABLED_SURFACE,
        modifier = modifier.fillMaxWidth().heightIn(min = 66.dp),
        enabled = enabled,
        onClick = onClick,
        testTag = testTag,
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 17.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (enabled) palette.text else palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
                        color = if (enabled) palette.muted else palette.faint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
            if (showChevron) DerivativeChevron(if (enabled) palette.cyan else palette.faint)
        }
    }
}

@Composable
internal fun LumenDerivativeTray(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    padding: PaddingValues = PaddingValues(6.dp),
    spacing: Int = 5,
    content: @Composable ColumnScope.() -> Unit,
) {
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.RECESSED_TRAY,
        modifier = modifier,
        testTag = testTag,
        contentPadding = padding,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            content = content,
        )
    }
}

@Composable
internal fun RowScope.LumenDerivativeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativeSurface(
        role = if (selected) DerivativeSurfaceRole.SELECTED_FACE else DerivativeSurfaceRole.DISABLED_SURFACE,
        modifier = modifier.weight(1f).height(48.dp),
        enabled = enabled,
        onClick = onClick,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
            color = when {
                !enabled -> palette.faint
                selected -> palette.cyanMicro
                else -> palette.muted
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun LumenDerivativeTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String? = null,
) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.RECESSED_TRAY,
        modifier = modifier.fillMaxWidth().height(46.dp),
        testTag = "derivative-tabs-bed",
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                var tab = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelected(index) },
                    )
                if (testTagPrefix != null) tab = tab.testTag("$testTagPrefix-$index")
                Box(tab, contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 15.sp),
                        color = when {
                            pressed -> palette.text
                            selected -> palette.cyanMicro
                            else -> palette.muted
                        },
                        maxLines = 1,
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(.54f)
                                .height(2.dp)
                                .derivativePill(palette.cyan),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LumenDerivativeToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativeSurface(
        role = if (enabled) DerivativeSurfaceRole.NEUTRAL_ROW else DerivativeSurfaceRole.DISABLED_SURFACE,
        modifier = modifier.fillMaxWidth().heightIn(min = 62.dp),
        enabled = enabled,
        testTag = testTag,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = if (enabled) palette.text else palette.muted,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 15.sp),
                        color = if (enabled) palette.muted else palette.faint,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            LumenToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                contentDescription = title,
            )
        }
    }
}

@Composable
internal fun LumenDerivativeAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.ACTION,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        enabled = enabled,
        onClick = onClick,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = if (enabled) palette.text else palette.faint,
            maxLines = 1,
        )
    }
}

@Composable
internal fun LumenDerivativeSectionLabel(text: String, modifier: Modifier = Modifier) {
    val palette = lumenP5IdentityPalette()
    Text(
        text = text,
        modifier = modifier.padding(start = 3.dp, top = 3.dp, bottom = 1.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
        color = palette.text,
    )
}

private data class DerivativeSurfaceColors(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val outline: Color,
    val illumination: Color,
)

private fun derivativeSurfaceColors(
    role: DerivativeSurfaceRole,
    palette: LumenP5IdentityPalette,
    pressed: Boolean,
): DerivativeSurfaceColors {
    val spec = derivativeSurfaceSpec(role)
    val isLight = palette.appBackground.red + palette.appBackground.green + palette.appBackground.blue > 1.5f
    val top: Color
    val middle: Color
    val bottom: Color
    val outlineBase: Color

    when (role) {
        DerivativeSurfaceRole.NEUTRAL_ROW -> {
            top = if (pressed) palette.rowPressedTop else palette.rowTop
            middle = if (pressed) palette.rowPressedMid else palette.rowMid
            bottom = if (pressed) palette.rowPressedBottom else palette.rowBottom
            outlineBase = if (pressed) palette.cyan else palette.steel
        }

        DerivativeSurfaceRole.RECESSED_TRAY -> {
            top = lerp(palette.insetSurface, palette.appBackground, if (isLight) .16f else .58f)
            middle = lerp(palette.insetSurface, palette.appBackground, if (isLight) .08f else .28f)
            bottom = lerp(palette.insetSurface, palette.rowBottom, if (isLight) .10f else .42f)
            outlineBase = palette.steel
        }

        DerivativeSurfaceRole.SELECTED_FACE -> {
            top = lerp(palette.rowTop, palette.steel, if (isLight) .14f else .27f)
            middle = lerp(palette.rowMid, palette.steel, if (isLight) .10f else .20f)
            bottom = lerp(palette.rowBottom, palette.steel, if (isLight) .08f else .14f)
            outlineBase = palette.cyan
        }

        DerivativeSurfaceRole.DISABLED_SURFACE -> {
            top = lerp(palette.rowTop, palette.appBackground, if (isLight) .10f else .34f)
            middle = lerp(palette.rowMid, palette.appBackground, if (isLight) .10f else .40f)
            bottom = lerp(palette.rowBottom, palette.appBackground, if (isLight) .12f else .46f)
            outlineBase = palette.muted
        }

        DerivativeSurfaceRole.PREVIEW_PANEL -> {
            top = lerp(palette.rowTop, palette.steel, if (isLight) .04f else .09f)
            middle = palette.rowMid
            bottom = lerp(palette.rowBottom, palette.appBackground, if (isLight) .04f else .16f)
            outlineBase = palette.steel
        }

        DerivativeSurfaceRole.ACTION -> {
            top = if (pressed) palette.rowPressedTop else lerp(palette.rowTop, palette.steel, if (isLight) .04f else .10f)
            middle = if (pressed) palette.rowPressedMid else palette.rowMid
            bottom = if (pressed) palette.rowPressedBottom else palette.rowBottom
            outlineBase = palette.cyan
        }
    }

    return DerivativeSurfaceColors(
        top = top,
        middle = middle,
        bottom = bottom,
        outline = outlineBase.copy(alpha = spec.outlineAlpha),
        illumination = palette.steel.copy(alpha = spec.illuminationAlpha),
    )
}

private fun Modifier.derivativePageBackground(palette: LumenP5IdentityPalette): Modifier = drawWithCache {
    val base = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to palette.appBackgroundLift,
            .34f to palette.appBackground,
            1f to lerp(palette.appBackground, Color.Black, .18f),
        ),
    )
    val ambient = Brush.radialGradient(
        colors = listOf(palette.steel.copy(alpha = .065f), Color.Transparent),
        center = Offset(size.width * .52f, -size.height * .03f),
        radius = size.width * 1.12f,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(ambient)
    }
}

private fun Modifier.derivativeSurfaceFace(
    spec: DerivativeSurfaceSpec,
    top: Color,
    middle: Color,
    bottom: Color,
    outline: Color,
    illumination: Color,
): Modifier = drawWithCache {
    val radius = spec.radiusDp.dp.toPx()
    val corner = CornerRadius(radius, radius)
    val face = Brush.verticalGradient(colorStops = arrayOf(0f to top, .52f to middle, 1f to bottom))
    val localLight = Brush.radialGradient(
        colors = listOf(illumination, Color.Transparent),
        center = Offset(size.width * .20f, size.height * .16f),
        radius = size.maxDimension * .78f,
    )
    val edgeShade = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = spec.edgeDarkeningAlpha),
            Color.Transparent,
            Color.Black.copy(alpha = spec.edgeDarkeningAlpha * .36f),
        ),
    )
    val stroke = 1.dp.toPx()
    onDrawBehind {
        drawRoundRect(face, cornerRadius = corner)
        drawRoundRect(localLight, cornerRadius = corner)
        drawRoundRect(edgeShade, cornerRadius = corner)
        drawRoundRect(outline, cornerRadius = corner, style = Stroke(stroke))
    }
}

private fun Modifier.derivativePill(color: Color): Modifier = drawWithCache {
    val corner = CornerRadius(size.height / 2f, size.height / 2f)
    onDrawBehind { drawRoundRect(color, cornerRadius = corner) }
}

@Composable
private fun DerivativeChevron(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.65.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * .39f, size.height * .27f)
            lineTo(size.width * .62f, size.height * .50f)
            lineTo(size.width * .39f, size.height * .73f)
        }
        drawPath(path, tint, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

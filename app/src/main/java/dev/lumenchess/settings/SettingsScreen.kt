package dev.lumenchess.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenP5IdentityPalette
import dev.lumenchess.design.LumenP5SettingsGeometry
import dev.lumenchess.design.lumenP5IdentityPalette

@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onOpenBoardAppearance: () -> Unit,
    onOpenSoundsHaptics: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlaySettings: () -> Unit = onOpenBoardAppearance,
) {
    // Root Settings remains category-only. These retained parameters are still owned by the deeper
    // Settings routes and intentionally remain part of the public screen contract.
    @Suppress("UNUSED_VARIABLE")
    val retainedSettingsContract = Triple(settings, onSettingsChange, onOpenSoundsHaptics)
    val palette = lumenP5IdentityPalette()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings-root")
            .approvedSettingsBackground(palette)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LumenP5SettingsGeometry.ScreenMargin)
            .padding(top = LumenP5SettingsGeometry.RootTopPadding),
    ) {
        Box(
            Modifier.fillMaxWidth().height(LumenP5SettingsGeometry.TitleHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Settings",
                modifier = Modifier.testTag("lumen-topbar-title"),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = LumenP5SettingsGeometry.SettingsTitleSize,
                    lineHeight = LumenP5SettingsGeometry.SettingsTitleLineHeight,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = palette.text,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(LumenP5SettingsGeometry.TitleToListGap))

        Column(
            Modifier.fillMaxWidth().testTag("settings-category-list"),
            verticalArrangement = Arrangement.spacedBy(LumenP5SettingsGeometry.RowGap),
        ) {
            SettingsCategoryRow(
                kind = SettingsGlyphKind.ENGINE,
                title = "Engines",
                subtitle = "Manage installed engines",
                uniqueTag = "settings-category-engines",
                palette = palette,
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.PLAY,
                title = "Play",
                subtitle = "Time controls, themes, sounds, board",
                uniqueTag = "settings-category-play",
                legacyTag = "settings-play",
                palette = palette,
                onClick = onOpenPlaySettings,
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.REVIEW,
                title = "Game Review",
                subtitle = "Analysis settings, move classification",
                uniqueTag = "settings-category-review",
                palette = palette,
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.RATING,
                title = "Ratings",
                subtitle = "Rating mode, system, match options",
                uniqueTag = "settings-category-ratings",
                palette = palette,
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.ACCOUNT,
                title = "Accounts & Sync",
                subtitle = "Chess.com, Lichess",
                uniqueTag = "settings-category-accounts",
                palette = palette,
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.ADVANCED,
                title = "Advanced",
                subtitle = "Developer & advanced",
                uniqueTag = "settings-category-advanced",
                palette = palette,
            )
        }
    }
}

private fun Modifier.approvedSettingsBackground(palette: LumenP5IdentityPalette): Modifier = drawWithCache {
    val base = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to palette.appBackgroundLift,
            .28f to palette.appBackground,
            1f to Color(0xFF070A0C),
        ),
    )
    val ambient = Brush.radialGradient(
        colors = listOf(palette.steel.copy(alpha = .09f), Color.Transparent),
        center = Offset(size.width * .55f, -size.height * .04f),
        radius = size.width * 1.08f,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(ambient)
    }
}

@Composable
private fun SettingsCategoryRow(
    kind: SettingsGlyphKind,
    title: String,
    subtitle: String,
    uniqueTag: String,
    palette: LumenP5IdentityPalette,
    legacyTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .992f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-scale-$title",
    )
    val offset by animateDpAsState(
        targetValue = if (pressed) 2.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-offset-$title",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 6.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-shadow-$title",
    )
    val highlightAlpha by animateFloatAsState(
        targetValue = if (pressed) .008f else .043f,
        animationSpec = LumenMotion.fastTween(),
        label = "settings-row-highlight-$title",
    )
    val illuminationAlpha by animateFloatAsState(
        targetValue = if (pressed) .12f else .135f,
        animationSpec = LumenMotion.fastTween(),
        label = "settings-row-illumination-$title",
    )
    val topColor by animateColorAsState(
        targetValue = if (pressed) palette.rowPressedTop else palette.rowTop,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-top-$title",
    )
    val midColor by animateColorAsState(
        targetValue = if (pressed) palette.rowPressedMid else palette.rowMid,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-mid-$title",
    )
    val bottomColor by animateColorAsState(
        targetValue = if (pressed) palette.rowPressedBottom else palette.rowBottom,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-bottom-$title",
    )
    val outlineColor by animateColorAsState(
        targetValue = if (pressed) palette.rowPressedOutline else palette.rowOutline,
        animationSpec = LumenMotion.fastTween(),
        label = "settings-row-outline-$title",
    )
    val shape = RoundedCornerShape(LumenP5SettingsGeometry.RowRadius)

    Box(
        Modifier
            .fillMaxWidth()
            .height(LumenP5SettingsGeometry.RowHeight)
            .testTag(uniqueTag),
    ) {
        // Stable full-bounds test target. The visual face may translate/scale while pressed.
        Box(Modifier.matchParentSize().testTag("settings-category-row"))

        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = offset.toPx()
                }
                .shadow(shadowElevation, shape, clip = false)
                .approvedSettingsRowFace(
                    palette = palette,
                    topColor = topColor,
                    midColor = midColor,
                    bottomColor = bottomColor,
                    outlineColor = outlineColor,
                    highlightAlpha = highlightAlpha,
                    illuminationAlpha = illuminationAlpha,
                ),
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = LumenP5SettingsGeometry.RowContentHorizontal,
                        vertical = LumenP5SettingsGeometry.RowContentVertical,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumenP5SettingsGeometry.RowContentGap),
            ) {
                SettingsIconWell(kind = kind, palette = palette, pressed = pressed)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = LumenP5SettingsGeometry.RowTitleSize,
                            lineHeight = LumenP5SettingsGeometry.RowTitleLineHeight,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = LumenP5SettingsGeometry.RowSubtitleSize,
                            lineHeight = LumenP5SettingsGeometry.RowSubtitleLineHeight,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = palette.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SettingsChevron(palette.cyan.copy(alpha = .88f))
            }
        }

        if (onClick != null) {
            var hitTarget = Modifier.matchParentSize()
            if (legacyTag != null) hitTarget = hitTarget.testTag(legacyTag)
            Box(
                hitTarget.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            )
        }
    }
}

private fun Modifier.approvedSettingsRowFace(
    palette: LumenP5IdentityPalette,
    topColor: Color,
    midColor: Color,
    bottomColor: Color,
    outlineColor: Color,
    highlightAlpha: Float,
    illuminationAlpha: Float,
): Modifier = drawWithCache {
    val radiusPx = LumenP5SettingsGeometry.RowRadius.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val face = Brush.verticalGradient(
        colorStops = arrayOf(0f to topColor, .48f to midColor, 1f to bottomColor),
    )
    val illumination = Brush.radialGradient(
        colorStops = arrayOf(
            0f to palette.steel.copy(alpha = illuminationAlpha),
            .33f to palette.steel.copy(alpha = illuminationAlpha * .52f),
            .63f to palette.steel.copy(alpha = illuminationAlpha * .13f),
            .77f to Color.Transparent,
        ),
        center = Offset(53.dp.toPx(), size.height * .50f),
        radius = 66.dp.toPx(),
    )
    val strokeWidth = 1.dp.toPx()
    val topHighlight = Color.White.copy(alpha = highlightAlpha)
    onDrawBehind {
        drawRoundRect(brush = face, cornerRadius = corner)
        drawRoundRect(brush = illumination, cornerRadius = corner)
        drawLine(
            color = topHighlight,
            start = Offset(radiusPx, strokeWidth),
            end = Offset(size.width - radiusPx, strokeWidth),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = outlineColor,
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )
    }
}

private enum class SettingsGlyphKind { ENGINE, PLAY, REVIEW, RATING, ACCOUNT, ADVANCED }

@Composable
private fun SettingsIconWell(kind: SettingsGlyphKind, palette: LumenP5IdentityPalette, pressed: Boolean) {
    val wellScale by animateFloatAsState(
        targetValue = if (pressed) .985f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-well-scale-$kind",
    )
    val wellOffset by animateDpAsState(
        targetValue = if (pressed) .75.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-well-offset-$kind",
    )
    val wellElevation by animateDpAsState(
        targetValue = if (pressed) 1.dp else 3.5.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-well-shadow-$kind",
    )
    val shape = RoundedCornerShape(LumenP5SettingsGeometry.IconWellRadius)
    Box(
        Modifier
            .size(LumenP5SettingsGeometry.IconWellSize)
            .testTag("settings-icon-well")
            .graphicsLayer {
                scaleX = wellScale
                scaleY = wellScale
                translationY = wellOffset.toPx()
            }
            .shadow(wellElevation, shape, clip = false)
            .approvedIconWell(palette, pressed),
        contentAlignment = Alignment.Center,
    ) {
        SettingsGlyph(
            kind = kind,
            tint = palette.cyanMicro,
            modifier = Modifier.size(LumenP5SettingsGeometry.SettingsIconSize).testTag("settings-icon-glyph"),
        )
    }
}

private fun Modifier.approvedIconWell(palette: LumenP5IdentityPalette, pressed: Boolean): Modifier = drawWithCache {
    val radiusPx = LumenP5SettingsGeometry.IconWellRadius.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val top = if (pressed) Color(0xFF142C35) else Color(0xFF16303A)
    val bottom = if (pressed) Color(0xFF0F2229) else Color(0xFF10262E)
    val face = Brush.verticalGradient(listOf(top, bottom))
    val localLight = Brush.radialGradient(
        colors = listOf(
            palette.cyan.copy(alpha = if (pressed) .17f else .18f),
            Color.Transparent,
        ),
        center = Offset(size.width * .34f, size.height * if (pressed) .30f else .28f),
        radius = size.minDimension * .62f,
    )
    val border = palette.cyan.copy(alpha = .38f)
    val upperHighlight = palette.cyanMicro.copy(alpha = if (pressed) .07f else .12f)
    val stroke = 1.dp.toPx()
    onDrawBehind {
        drawRoundRect(face, cornerRadius = corner)
        drawRoundRect(localLight, cornerRadius = corner)
        drawLine(
            upperHighlight,
            start = Offset(radiusPx * .70f, stroke),
            end = Offset(size.width - radiusPx * .70f, stroke),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawRoundRect(border, cornerRadius = corner, style = Stroke(stroke))
    }
}

@Composable
private fun SettingsGlyph(kind: SettingsGlyphKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unitX = size.width / 24f
        val unitY = size.height / 24f
        val strokeWidth = 1.75f * unitX
        fun point(x: Float, y: Float) = Offset(x * unitX, y * unitY)
        fun drawLine24(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = strokeWidth) =
            drawLine(tint, point(x1, y1), point(x2, y2), width, StrokeCap.Round)

        when (kind) {
            SettingsGlyphKind.ENGINE -> {
                drawRoundRect(
                    tint,
                    topLeft = point(6f, 6f),
                    size = Size(12f * unitX, 12f * unitY),
                    cornerRadius = CornerRadius(2f * unitX, 2f * unitY),
                    style = Stroke(strokeWidth),
                )
                drawRoundRect(
                    tint,
                    topLeft = point(9f, 9f),
                    size = Size(6f * unitX, 6f * unitY),
                    cornerRadius = CornerRadius(unitX, unitY),
                    style = Stroke(strokeWidth),
                )
                listOf(9f, 12f, 15f).forEach { x ->
                    drawLine24(x, 3.8f, x, 6f)
                    drawLine24(x, 18f, x, 20.2f)
                }
                listOf(9f, 12f, 15f).forEach { y ->
                    drawLine24(3.8f, y, 6f, y)
                    drawLine24(18f, y, 20.2f, y)
                }
            }

            SettingsGlyphKind.PLAY -> {
                drawCircle(tint, radius = 2.6f * unitX, center = point(10f, 6.25f), style = Stroke(strokeWidth))
                drawLine24(6.9f, 16.8f, 13.1f, 16.8f)
                val pawn = Path().apply {
                    moveTo(8.1f * unitX, 16.8f * unitY)
                    lineTo(8.85f * unitX, 13.1f * unitY)
                    lineTo(11.15f * unitX, 13.1f * unitY)
                    lineTo(11.9f * unitX, 16.8f * unitY)
                }
                drawPath(pawn, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine24(8.9f, 10.2f, 11.1f, 10.2f)
                val play = Path().apply {
                    moveTo(14.4f * unitX, 8.7f * unitY)
                    lineTo(19.1f * unitX, 12f * unitY)
                    lineTo(14.4f * unitX, 15.3f * unitY)
                    close()
                }
                drawPath(play, tint)
            }

            SettingsGlyphKind.REVIEW -> {
                drawCircle(tint, radius = 4.2f * unitX, center = point(9f, 9f), style = Stroke(strokeWidth))
                drawLine24(12.1f, 12.1f, 15.4f, 15.4f)
                val tower = Path().apply {
                    moveTo(15.1f * unitX, 8.4f * unitY)
                    lineTo(18.5f * unitX, 8.4f * unitY)
                    lineTo(18.5f * unitX, 10.4f * unitY)
                    lineTo(15.9f * unitX, 10.4f * unitY)
                    lineTo(15.9f * unitX, 13.6f * unitY)
                }
                drawPath(tower, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val check = Path().apply {
                    moveTo(14.9f * unitX, 14.9f * unitY)
                    lineTo(16.3f * unitX, 16.3f * unitY)
                    lineTo(19.4f * unitX, 12.9f * unitY)
                }
                drawPath(check, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            SettingsGlyphKind.RATING -> {
                val star = Path().apply {
                    moveTo(12f * unitX, 4.3f * unitY)
                    lineTo(14.22f * unitX, 8.75f * unitY)
                    lineTo(19.15f * unitX, 9.48f * unitY)
                    lineTo(15.58f * unitX, 12.95f * unitY)
                    lineTo(16.42f * unitX, 17.82f * unitY)
                    lineTo(12f * unitX, 15.5f * unitY)
                    lineTo(7.58f * unitX, 17.82f * unitY)
                    lineTo(8.42f * unitX, 12.95f * unitY)
                    lineTo(4.85f * unitX, 9.48f * unitY)
                    lineTo(9.78f * unitX, 8.75f * unitY)
                    close()
                }
                drawPath(star, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(tint, radius = 1.1f * unitX, center = point(12f, 11.55f))
            }

            SettingsGlyphKind.ACCOUNT -> {
                drawRoundRect(
                    tint,
                    topLeft = point(4.5f, 9.2f),
                    size = Size(10f * unitX, 5.6f * unitY),
                    cornerRadius = CornerRadius(2.8f * unitX, 2.8f * unitY),
                    style = Stroke(strokeWidth),
                )
                drawCircle(tint, radius = 2.8f * unitX, center = point(17.8f, 12f), style = Stroke(strokeWidth))
                drawLine24(13.5f, 12f, 15f, 12f)
                val upperArc = Path().apply {
                    moveTo(5.7f * unitX, 8.1f * unitY)
                    cubicTo(7.2f * unitX, 6.55f * unitY, 9.8f * unitX, 6.2f * unitY, 11.8f * unitX, 6.9f * unitY)
                }
                drawPath(upperArc, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine24(5.7f, 8.1f, 6.1f, 5.9f)
                val lowerArc = Path().apply {
                    moveTo(18f * unitX, 16.5f * unitY)
                    cubicTo(16.4f * unitX, 17.6f * unitY, 14.2f * unitX, 18.2f * unitY, 12.2f * unitX, 17.7f * unitY)
                }
                drawPath(lowerArc, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            SettingsGlyphKind.ADVANCED -> {
                listOf(7f, 12f, 17f).forEach { x -> drawLine24(x, 4.5f, x, 19.5f) }
                drawCircle(tint, radius = 1.8f * unitX, center = point(7f, 9f))
                drawCircle(tint, radius = 1.8f * unitX, center = point(12f, 15f))
                drawCircle(tint, radius = 1.8f * unitX, center = point(17f, 7f))
            }
        }
    }
}

@Composable
private fun SettingsChevron(tint: Color) {
    Canvas(Modifier.size(LumenP5SettingsGeometry.ChevronSize)) {
        val strokeWidth = 1.9.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * .40f, size.height * .27f)
            lineTo(size.width * .63f, size.height * .50f)
            lineTo(size.width * .40f, size.height * .73f)
        }
        drawPath(path, tint, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

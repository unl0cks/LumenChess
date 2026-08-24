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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenP5IdentityPalette
import dev.lumenchess.design.LumenP5SettingsGeometry
import dev.lumenchess.design.LumenRadii
import dev.lumenchess.design.LumenTypography
import dev.lumenchess.design.lumenP5IdentityPalette

@Composable
internal fun LumenBottomNavigation(current: MainTab, onSelect: (MainTab) -> Unit) {
    val palette = lumenP5IdentityPalette()
    Box(
        Modifier
            .fillMaxWidth()
            .height(LumenP5SettingsGeometry.NavHeight)
            .testTag("main-bottom-nav")
            .approvedNavigationPlane(palette),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(
                    start = LumenP5SettingsGeometry.NavHorizontalPadding,
                    end = LumenP5SettingsGeometry.NavHorizontalPadding,
                    top = LumenP5SettingsGeometry.NavTopPadding,
                    bottom = LumenP5SettingsGeometry.NavBottomPadding,
                )
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == current
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val tint by animateColorAsState(
                    targetValue = if (selected) palette.cyan else palette.muted.copy(alpha = .90f),
                    animationSpec = LumenMotion.fastTween(),
                    label = "nav-tint-${tab.label}",
                )
                val scale by animateFloatAsState(
                    targetValue = if (pressed) .965f else 1f,
                    animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
                    label = "nav-scale-${tab.label}",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        )
                        .semantics { contentDescription = "${tab.label} tab" }
                        .testTag("main-tab-${tab.label.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top,
                ) {
                    Box(
                        Modifier
                            .width(LumenP5SettingsGeometry.NavIndicatorWidth)
                            .height(LumenP5SettingsGeometry.NavIndicatorHeight)
                            .drawWithCache {
                                onDrawBehind {
                                    if (selected) {
                                        drawRoundRect(
                                            color = palette.cyanMicro,
                                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                                        )
                                    }
                                }
                            },
                    )
                    Spacer(Modifier.height(2.dp))
                    var iconSlotModifier = Modifier
                        .width(LumenP5SettingsGeometry.NavIconSlotWidth)
                        .height(LumenP5SettingsGeometry.NavIconSlotHeight)
                    if (selected && tab == MainTab.Settings) {
                        iconSlotModifier = iconSlotModifier.testTag("main-tab-settings-well")
                    }
                    Box(
                        iconSlotModifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .approvedNavigationIconSlot(palette, selected),
                        contentAlignment = Alignment.Center,
                    ) {
                        LumenNavGlyph(
                            tab = tab,
                            tint = tint,
                            modifier = Modifier
                                .size(LumenP5SettingsGeometry.NavIconSize)
                                .testTag("main-tab-icon"),
                        )
                    }
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = tab.label,
                        style = LumenTypography.BottomNav.copy(
                            fontSize = LumenP5SettingsGeometry.NavLabelSize,
                            lineHeight = LumenP5SettingsGeometry.NavLabelLineHeight,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun Modifier.approvedNavigationPlane(palette: LumenP5IdentityPalette): Modifier = drawWithCache {
    val face = Brush.verticalGradient(listOf(palette.navTop, palette.navBottom))
    val selectedAmbient = Brush.radialGradient(
        colors = listOf(palette.steel.copy(alpha = .055f), Color.Transparent),
        center = Offset(size.width * .92f, size.height * .18f),
        radius = 90.dp.toPx(),
    )
    val upperOcclusion = Brush.verticalGradient(
        colors = listOf(Color.Black.copy(alpha = .20f), Color.Transparent),
        startY = 0f,
        endY = 15.dp.toPx(),
    )
    val upperInnerLight = Color(0xFF7097A6).copy(alpha = .035f)
    onDrawBehind {
        drawRect(face)
        drawRect(selectedAmbient)
        drawRect(upperOcclusion)
        // A diffuse one-pixel-equivalent inner light replaces the former hard divider.
        drawRect(
            color = upperInnerLight,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, .35.dp.toPx()),
        )
    }
}

private fun Modifier.approvedNavigationIconSlot(
    palette: LumenP5IdentityPalette,
    selected: Boolean,
): Modifier = if (!selected) this else drawWithCache {
    val radiusPx = LumenP5SettingsGeometry.NavIconSlotRadius.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val face = Brush.verticalGradient(listOf(Color(0xFF15303A), Color(0xFF10252D)))
    val localLight = Brush.radialGradient(
        colors = listOf(palette.cyan.copy(alpha = .20f), Color.Transparent),
        center = Offset(size.width * .50f, size.height * .40f),
        radius = size.minDimension * .72f,
    )
    val stroke = 1.dp.toPx()
    onDrawBehind {
        drawRoundRect(face, cornerRadius = corner)
        drawRoundRect(localLight, cornerRadius = corner)
        drawRoundRect(
            palette.cyan.copy(alpha = .18f),
            cornerRadius = corner,
            style = Stroke(stroke),
        )
        drawLine(
            palette.cyanMicro.copy(alpha = .10f),
            start = Offset(radiusPx * .65f, stroke),
            end = Offset(size.width - radiusPx * .65f, stroke),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LumenNavGlyph(tab: MainTab, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ux = size.width / 24f
        val uy = size.height / 24f
        val strokeWidth = 1.9f * ux
        fun point(x: Float, y: Float) = Offset(x * ux, y * uy)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, point(x1, y1), point(x2, y2), strokeWidth, StrokeCap.Round)
        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (tab) {
            MainTab.Play -> {
                drawCircle(tint, 2.05f * ux, point(8.35f, 5.85f), style = stroke)
                line(6.45f, 9f, 10.25f, 9f)
                val pawnBody = Path().apply {
                    moveTo(6.9f * ux, 9f * uy)
                    cubicTo(7.45f * ux, 10.45f * uy, 7.4f * ux, 11.7f * uy, 6.5f * ux, 13f * uy)
                    lineTo(10.2f * ux, 13f * uy)
                    cubicTo(9.3f * ux, 11.7f * uy, 9.25f * ux, 10.45f * uy, 9.8f * ux, 9f * uy)
                }
                drawPath(pawnBody, tint, style = stroke)
                line(5.65f, 15.2f, 11.05f, 15.2f)
                line(5f, 17.65f, 11.7f, 17.65f)
                val play = Path().apply {
                    moveTo(15.1f * ux, 9.3f * uy)
                    lineTo(19.1f * ux, 12f * uy)
                    lineTo(15.1f * ux, 14.7f * uy)
                    close()
                }
                drawPath(play, tint)
            }

            MainTab.Arena -> {
                val left = Path().apply {
                    moveTo(3.4f * ux, 5.9f * uy)
                    lineTo(4.9f * ux, 5.9f * uy)
                    lineTo(4.9f * ux, 7.45f * uy)
                    lineTo(6.45f * ux, 7.45f * uy)
                    lineTo(6.45f * ux, 5.9f * uy)
                    lineTo(7.9f * ux, 5.9f * uy)
                    lineTo(7.9f * ux, 8.9f * uy)
                    lineTo(7.15f * ux, 10.05f * uy)
                    lineTo(7.15f * ux, 15.4f * uy)
                    lineTo(4.2f * ux, 15.4f * uy)
                    lineTo(4.2f * ux, 10.05f * uy)
                    lineTo(3.4f * ux, 8.9f * uy)
                    close()
                }
                drawPath(left, tint, style = stroke)
                line(3.45f, 17.55f, 8.1f, 17.55f)

                val right = Path().apply {
                    moveTo(16.1f * ux, 5.9f * uy)
                    lineTo(17.55f * ux, 5.9f * uy)
                    lineTo(17.55f * ux, 7.45f * uy)
                    lineTo(19.1f * ux, 7.45f * uy)
                    lineTo(19.1f * ux, 5.9f * uy)
                    lineTo(20.6f * ux, 5.9f * uy)
                    lineTo(20.6f * ux, 8.9f * uy)
                    lineTo(19.8f * ux, 10.05f * uy)
                    lineTo(19.8f * ux, 15.4f * uy)
                    lineTo(16.85f * ux, 15.4f * uy)
                    lineTo(16.85f * ux, 10.05f * uy)
                    lineTo(16.1f * ux, 8.9f * uy)
                    close()
                }
                drawPath(right, tint, style = stroke)
                line(15.9f, 17.55f, 20.55f, 17.55f)

                val bolt = Path().apply {
                    moveTo(11.75f * ux, 7.55f * uy)
                    lineTo(10.25f * ux, 10.8f * uy)
                    lineTo(11.8f * ux, 10.8f * uy)
                    lineTo(10.95f * ux, 15.15f * uy)
                    lineTo(13.75f * ux, 10.25f * uy)
                    lineTo(12.15f * ux, 10.25f * uy)
                    lineTo(13.3f * ux, 7.55f * uy)
                    close()
                }
                drawPath(bolt, tint)
            }

            MainTab.Games -> {
                drawRoundRect(
                    tint,
                    topLeft = point(5f, 4.4f),
                    size = Size(14f * ux, 15.2f * uy),
                    cornerRadius = CornerRadius(2.15f * ux, 2.15f * uy),
                    style = stroke,
                )
                drawRoundRect(
                    tint,
                    topLeft = point(7.25f, 6.7f),
                    size = Size(6.5f * ux, 6.5f * uy),
                    cornerRadius = CornerRadius(.7f * ux, .7f * uy),
                    style = stroke,
                )
                line(10.5f, 6.7f, 10.5f, 13.2f)
                line(7.25f, 9.95f, 13.75f, 9.95f)
                line(7.25f, 16.2f, 15.75f, 16.2f)
            }

            MainTab.Insights -> {
                line(5f, 18.5f, 5f, 13f)
                line(9.2f, 18.5f, 9.2f, 9.7f)
                line(13.4f, 18.5f, 13.4f, 12.1f)
                line(17.6f, 18.5f, 17.6f, 6.3f)
                val trend = Path().apply {
                    moveTo(4.7f * ux, 10.2f * uy)
                    lineTo(8.7f * ux, 8f * uy)
                    lineTo(12.7f * ux, 9.5f * uy)
                    lineTo(17.7f * ux, 5.5f * uy)
                }
                drawPath(trend, tint, style = stroke)
                drawCircle(tint, radius = 1f * ux, center = point(4.7f, 10.2f))
                drawCircle(tint, radius = 1f * ux, center = point(8.7f, 8f))
                drawCircle(tint, radius = 1f * ux, center = point(12.7f, 9.5f))
                drawCircle(tint, radius = 1f * ux, center = point(17.7f, 5.5f))
            }

            MainTab.Settings -> {
                drawCircle(tint, 5.15f * ux, point(12f, 12f), style = stroke)
                drawCircle(tint, 2.05f * ux, point(12f, 12f), style = stroke)
                line(12f, 3.2f, 12f, 5.2f)
                line(12f, 18.8f, 12f, 20.8f)
                line(3.2f, 12f, 5.2f, 12f)
                line(18.8f, 12f, 20.8f, 12f)
                line(5.75f, 5.75f, 7.15f, 7.15f)
                line(16.85f, 16.85f, 18.25f, 18.25f)
                line(18.25f, 5.75f, 16.85f, 7.15f)
                line(7.15f, 16.85f, 5.75f, 18.25f)
            }
        }
    }
}

@Composable
internal fun FutureSurfacePreview(tab: MainTab) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativePage(
        modifier = Modifier.fillMaxSize(),
        testTag = "derivative-future-preview",
        horizontalPadding = 18,
        verticalPadding = 22,
        spacing = 18,
    ) {
        Text(
            text = tab.label,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = palette.text,
        )
        LumenDerivativeSurface(
            role = DerivativeSurfaceRole.PREVIEW_PANEL,
            modifier = Modifier.fillMaxWidth().height(96.dp),
            testTag = "derivative-preview-panel",
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                LumenDerivativeSurface(
                    role = DerivativeSurfaceRole.SELECTED_FACE,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LegacyPreviewTabIcon(tab, palette.cyanMicro, Modifier.fillMaxSize())
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = palette.text,
                    )
                    Text(
                        text = tab.previewCopy,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                        color = palette.muted,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

/** Existing placeholder-surface iconography stays untouched by the root-navigation translation. */
@Composable
private fun LegacyPreviewTabIcon(tab: MainTab,color: Color,modifier: Modifier=Modifier.size(18.dp)) {
    Canvas(modifier) {
        val w=size.width
        val h=size.height
        val s=size.minDimension*.076f
        when(tab) {
            MainTab.Play -> {
                val knight = Path().apply {
                    moveTo(w*.18f,h*.80f)
                    lineTo(w*.80f,h*.80f)
                    lineTo(w*.74f,h*.68f)
                    cubicTo(w*.82f,h*.56f,w*.77f,h*.41f,w*.62f,h*.31f)
                    lineTo(w*.79f,h*.17f)
                    lineTo(w*.61f,h*.08f)
                    lineTo(w*.45f,h*.16f)
                    lineTo(w*.34f,h*.30f)
                    lineTo(w*.17f,h*.51f)
                    lineTo(w*.43f,h*.47f)
                    lineTo(w*.23f,h*.65f)
                }
                drawPath(knight,color,style=Stroke(width=s,cap=StrokeCap.Round,join=StrokeJoin.Round))
                drawLine(color,Offset(w*.14f,h*.88f),Offset(w*.84f,h*.88f),s,StrokeCap.Round)
                drawCircle(color,s*.62f,Offset(w*.50f,h*.25f))
                val play = Path().apply {
                    moveTo(w*.56f,h*.48f)
                    lineTo(w*.75f,h*.58f)
                    lineTo(w*.56f,h*.68f)
                    close()
                }
                drawPath(play,color.copy(alpha=.92f))
            }
            MainTab.Arena -> {
                drawLine(color,Offset(w*.24f,h*.22f),Offset(w*.76f,h*.78f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.76f,h*.22f),Offset(w*.24f,h*.78f),s,StrokeCap.Round)
                drawCircle(color,s*1.15f,Offset(w*.23f,h*.21f))
                drawCircle(color,s*1.15f,Offset(w*.77f,h*.21f))
            }
            MainTab.Games -> {
                drawRoundRect(color,Offset(w*.2f,h*.18f),Size(w*.6f,h*.64f),CornerRadius(w*.08f),style=Stroke(s))
                repeat(3) { i ->
                    val y=h*(.34f+i*.16f)
                    drawLine(color,Offset(w*.32f,y),Offset(w*.68f,y),s*.74f,StrokeCap.Round)
                }
            }
            MainTab.Insights -> {
                drawLine(color,Offset(w*.22f,h*.76f),Offset(w*.22f,h*.52f),s*1.24f,StrokeCap.Round)
                drawLine(color,Offset(w*.5f,h*.76f),Offset(w*.5f,h*.34f),s*1.24f,StrokeCap.Round)
                drawLine(color,Offset(w*.78f,h*.76f),Offset(w*.78f,h*.2f),s*1.24f,StrokeCap.Round)
            }
            MainTab.Settings -> {
                drawCircle(color,w*.29f,Offset(w*.5f,h*.5f),style=Stroke(s))
                drawCircle(color,w*.09f,Offset(w*.5f,h*.5f),style=Stroke(s))
                drawLine(color,Offset(w*.08f,h*.5f),Offset(w*.2f,h*.5f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.8f,h*.5f),Offset(w*.92f,h*.5f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.5f,h*.08f),Offset(w*.5f,h*.2f),s,StrokeCap.Round)
                drawLine(color,Offset(w*.5f,h*.8f),Offset(w*.5f,h*.92f),s,StrokeCap.Round)
            }
        }
    }
}

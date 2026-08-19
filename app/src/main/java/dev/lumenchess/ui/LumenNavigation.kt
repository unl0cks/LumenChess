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
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = strokeWidth) =
            drawLine(tint, point(x1, y1), point(x2, y2), width, StrokeCap.Round)
        val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (tab) {
            MainTab.Play -> {
                drawCircle(tint, 2.6f * ux, point(10f, 6.3f), style = stroke)
                line(6.9f, 16.7f, 13.1f, 16.7f)
                val pawn = Path().apply {
                    moveTo(8.1f * ux, 16.7f * uy)
                    lineTo(8.9f * ux, 13f * uy)
                    lineTo(11.1f * ux, 13f * uy)
                    lineTo(11.9f * ux, 16.7f * uy)
                }
                drawPath(pawn, tint, style = stroke)
                line(8.9f, 10.1f, 11.1f, 10.1f)
                val play = Path().apply {
                    moveTo(14.5f * ux, 8.6f * uy)
                    lineTo(19.2f * ux, 12f * uy)
                    lineTo(14.5f * ux, 15.4f * uy)
                    close()
                }
                drawPath(play, tint)
            }

            MainTab.Arena -> {
                val left = Path().apply {
                    moveTo(4.5f * ux, 6.2f * uy)
                    lineTo(9.8f * ux, 6.2f * uy)
                    lineTo(9.8f * ux, 9.3f * uy)
                    lineTo(8.7f * ux, 9.3f * uy)
                    lineTo(8.7f * ux, 14.4f * uy)
                    lineTo(10.7f * ux, 14.4f * uy)
                    lineTo(10.7f * ux, 17.5f * uy)
                    lineTo(4f * ux, 17.5f * uy)
                    lineTo(4f * ux, 14.4f * uy)
                    lineTo(6f * ux, 14.4f * uy)
                    lineTo(6f * ux, 9.3f * uy)
                    lineTo(4.5f * ux, 9.3f * uy)
                    close()
                }
                val right = Path().apply {
                    moveTo(19.5f * ux, 6.2f * uy)
                    lineTo(14.2f * ux, 6.2f * uy)
                    lineTo(14.2f * ux, 9.3f * uy)
                    lineTo(15.3f * ux, 9.3f * uy)
                    lineTo(15.3f * ux, 14.4f * uy)
                    lineTo(13.3f * ux, 14.4f * uy)
                    lineTo(13.3f * ux, 17.5f * uy)
                    lineTo(20f * ux, 17.5f * uy)
                    lineTo(20f * ux, 14.4f * uy)
                    lineTo(18f * ux, 14.4f * uy)
                    lineTo(18f * ux, 9.3f * uy)
                    lineTo(19.5f * ux, 9.3f * uy)
                    close()
                }
                drawPath(left, tint, style = stroke)
                drawPath(right, tint, style = stroke)
                line(11.2f, 10.1f, 12.8f, 11.7f)
                line(12.8f, 11.7f, 11.2f, 13.3f)
                line(12.8f, 10.1f, 11.2f, 11.7f)
                line(11.2f, 11.7f, 12.8f, 13.3f)
            }

            MainTab.Games -> {
                drawRoundRect(
                    tint,
                    topLeft = point(5f, 4.5f),
                    size = Size(14f * ux, 15f * uy),
                    cornerRadius = CornerRadius(2.2f * ux, 2.2f * uy),
                    style = stroke,
                )
                line(5.8f, 8f, 18.2f, 8f)
                val squareSize = 2.15f * ux
                listOf(7.5f to 10.3f, 10.3f to 10.3f, 7.5f to 13.1f, 10.3f to 13.1f).forEach { (x, y) ->
                    drawRoundRect(
                        tint,
                        topLeft = point(x, y),
                        size = Size(squareSize, squareSize),
                        cornerRadius = CornerRadius(.35f * ux, .35f * uy),
                    )
                }
                line(7.5f, 15.6f, 16.3f, 15.6f)
            }

            MainTab.Insights -> {
                line(4.5f, 19f, 19.5f, 19f)
                drawRoundRect(tint, point(6.2f, 12f), Size(2.4f * ux, 5.2f * uy), CornerRadius(.6f * ux, .6f * uy))
                drawRoundRect(tint, point(10.8f, 9f), Size(2.4f * ux, 8.2f * uy), CornerRadius(.6f * ux, .6f * uy))
                drawRoundRect(tint, point(15.4f, 5.4f), Size(2.4f * ux, 11.8f * uy), CornerRadius(.6f * ux, .6f * uy))
                val trend = Path().apply {
                    moveTo(6.9f * ux, 9.5f * uy)
                    lineTo(11.8f * ux, 6.5f * uy)
                    lineTo(16.2f * ux, 8.1f * uy)
                    lineTo(19.2f * ux, 4.5f * uy)
                }
                drawPath(trend, tint, style = stroke)
            }

            MainTab.Settings -> {
                drawCircle(tint, 6.2f * ux, point(12f, 12f), style = stroke)
                drawCircle(tint, 2.2f * ux, point(12f, 12f), style = stroke)
                line(3.5f, 12f, 5.2f, 12f)
                line(18.8f, 12f, 20.5f, 12f)
                line(12f, 3.5f, 12f, 5.2f)
                line(12f, 18.8f, 12f, 20.5f)
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
                    ) { LegacyPreviewTabIcon(tab,LumenColors.AccentBlueBright,Modifier.size(18.dp)) }
                    Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        Text(tab.label,style=MaterialTheme.typography.titleMedium,color=LumenColors.OnSurface)
                        Text(tab.previewCopy,style=MaterialTheme.typography.bodySmall,color=LumenColors.OnSurfaceMuted)
                    }
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

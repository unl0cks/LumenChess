package dev.lumenchess.play

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenP5IdentityPalette
import dev.lumenchess.design.lumenP5IdentityPalette
import dev.lumenchess.engine.api.EngineStrengthTarget

/** Native translation of the visually approved P5 Play Overview Iteration 2 proof. */
@Composable
internal fun ApprovedPlayOverviewScreen(
    ui: PlayUiState,
    onPlayVsEngine: () -> Unit,
    onArenaPreview: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setup = ui.setup
    val elo = (setup.strengthTarget as? EngineStrengthTarget.Elo)?.value
    val minutes = setup.timeControl.initialMillis / 60_000L
    val increment = setup.timeControl.incrementMillis / 1_000L
    val timeLabel = when {
        minutes <= 3L -> "Blitz"
        minutes <= 15L -> "Rapid"
        else -> "Classical"
    }
    val quickPrimary = buildString {
        append("$minutes min")
        if (increment > 0L) append(" + $increment sec")
        append(" · $timeLabel")
    }
    val quickSecondary = buildString {
        append(setup.engine.displayName)
        if (elo != null) append(" · $elo Elo")
    }
    val palette = lumenP5IdentityPalette()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .approvedPlayBackground(palette)
            .testTag("p5-play-overview"),
    ) {
        // The approved browser proof is 390 units wide. Scale all reference geometry from the
        // actual native content width instead of treating CSS px as Android dp.
        val ref = PlayReferenceScale(maxWidth.value / 390f)
        val contentWidth = ref.dp(358f)
        val heroHeight = ref.dp(168f)

        ApprovedPlayTopBar(
            ref = ref,
            onBack = onBack,
            modifier = Modifier
                .offset(x = ref.dp(16f), y = ref.dp(34f))
                .width(contentWidth)
                .height(ref.dp(48f)),
        )

        ApprovedPlayHeroCard(
            ref = ref,
            artwork = ApprovedPlayArtwork.ENGINE,
            title = "Play vs Engine",
            subtitle = "Challenge a chess engine",
            onClick = onPlayVsEngine,
            modifier = Modifier
                .offset(x = ref.dp(16f), y = ref.dp(112f))
                .width(contentWidth)
                .height(heroHeight)
                .testTag("play-overview-vs-engine"),
        )

        ApprovedPlayHeroCard(
            ref = ref,
            artwork = ApprovedPlayArtwork.ARENA,
            title = "Engine Arena",
            subtitle = "Watch engines battle each other",
            onClick = onArenaPreview,
            modifier = Modifier
                .offset(x = ref.dp(16f), y = ref.dp(292f))
                .width(contentWidth)
                .height(heroHeight)
                .testTag("play-overview-arena"),
        )

        Box(
            Modifier
                .offset(x = ref.dp(16f), y = ref.dp(532f))
                .width(contentWidth)
                .height(ref.dp(139f)),
        ) {
            Text(
                "Quick Start",
                modifier = Modifier.offset(x = ref.dp(2f), y = 0.dp),
                color = palette.text,
                fontSize = ref.sp(14.5f),
                lineHeight = ref.sp(18f),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Last used",
                modifier = Modifier.offset(x = ref.dp(2f), y = ref.dp(21f)),
                color = palette.faint.copy(alpha = .96f),
                fontSize = ref.sp(10.5f),
                lineHeight = ref.sp(14f),
                fontWeight = FontWeight.Medium,
            )
            ApprovedQuickStartRow(
                ref = ref,
                primary = quickPrimary,
                secondary = quickSecondary,
                onClick = onPlayVsEngine,
                modifier = Modifier
                    .offset(y = ref.dp(53f))
                    .width(contentWidth)
                    .height(ref.dp(86f))
                    .testTag("play-overview-quick-start"),
            )
        }
    }
}

private data class PlayReferenceScale(val factor: Float) {
    fun dp(referencePx: Float): Dp = (referencePx * factor).dp
    fun sp(referencePx: Float) = (referencePx * factor).sp
}

private enum class ApprovedPlayArtwork(
    val assetPath: String,
    val testTag: String,
    val widthPx: Float,
    val heightPx: Float,
) {
    ENGINE(
        assetPath = "play-overview/lumen_play_vs_engine_hero.png",
        testTag = "play-overview-vs-engine-hero",
        widthPx = 96f,
        heightPx = 100f,
    ),
    ARENA(
        assetPath = "play-overview/lumen_engine_arena_hero.png",
        testTag = "play-overview-arena-hero",
        widthPx = 100f,
        heightPx = 104f,
    ),
}

@Composable
private fun ApprovedPlayTopBar(
    ref: PlayReferenceScale,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) LumenMotion.IconPressScale else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseSpring(),
        label = "approved-play-back-scale",
    )
    val palette = lumenP5IdentityPalette()

    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "Play",
            color = palette.text,
            fontSize = ref.sp(21f),
            lineHeight = ref.sp(25f),
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(ref.dp(48f))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .semantics { contentDescription = "Navigate back" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(ref.dp(23f))) {
                val stroke = ref.dp(1.8f).toPx()
                val tint = palette.muted.copy(alpha = .94f)
                drawLine(
                    tint,
                    Offset(size.width * .64f, size.height * .18f),
                    Offset(size.width * .35f, size.height * .50f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(size.width * .35f, size.height * .50f),
                    Offset(size.width * .64f, size.height * .82f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ApprovedPlayHeroCard(
    ref: PlayReferenceScale,
    artwork: ApprovedPlayArtwork,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = lumenP5IdentityPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .982f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-hero-scale-${artwork.name}",
    )
    val yOffset by animateDpAsState(
        targetValue = if (pressed) ref.dp(2f) else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-hero-y-${artwork.name}",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) ref.dp(1.8f) else ref.dp(6f),
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-hero-shadow-${artwork.name}",
    )
    val illumination by animateFloatAsState(
        targetValue = if (pressed) 1.14f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-hero-light-${artwork.name}",
    )
    val glowTightness by animateFloatAsState(
        targetValue = if (pressed) .88f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-hero-glow-size-${artwork.name}",
    )
    val shape = RoundedCornerShape(ref.dp(14f))

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = yOffset.toPx()
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (pressed) .24f else .34f),
                spotColor = Color.Black.copy(alpha = if (pressed) .30f else .44f),
            )
            .clip(shape)
            .approvedHeroMaterial(
                ref = ref,
                palette = palette,
                artwork = artwork,
                pressed = pressed,
                illumination = illumination,
                glowTightness = glowTightness,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "$title. $subtitle" },
    ) {
        ApprovedHeroArtwork(
            artwork = artwork,
            modifier = Modifier
                .offset(
                    x = ref.dp(18f),
                    y = ref.dp((168f - artwork.heightPx) / 2f),
                )
                .width(ref.dp(artwork.widthPx))
                .height(ref.dp(artwork.heightPx)),
        )

        Box(
            Modifier
                .offset(x = ref.dp(140f))
                .width(ref.dp(190f))
                .fillMaxHeight()
                .testTag(
                    if (artwork == ApprovedPlayArtwork.ENGINE) {
                        "play-overview-vs-engine-copy"
                    } else {
                        "play-overview-arena-copy"
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    title,
                    color = palette.text,
                    fontSize = ref.sp(17f),
                    lineHeight = ref.sp(21f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    modifier = Modifier
                        .width(ref.dp(168f))
                        .offset(y = ref.dp(6f)),
                    color = palette.muted.copy(alpha = .96f),
                    fontSize = ref.sp(12f),
                    lineHeight = ref.sp(17f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

private fun Modifier.approvedHeroMaterial(
    ref: PlayReferenceScale,
    palette: LumenP5IdentityPalette,
    artwork: ApprovedPlayArtwork,
    pressed: Boolean,
    illumination: Float,
    glowTightness: Float,
): Modifier = drawWithCache {
    val corner = CornerRadius(ref.dp(14f).toPx())
    val outlineWidth = ref.dp(1f).toPx().coerceAtLeast(1f)
    val face = if (pressed) {
        Brush.verticalGradient(
            listOf(palette.rowPressedTop, palette.rowPressedMid, palette.rowPressedBottom),
        )
    } else {
        Brush.verticalGradient(listOf(palette.rowTop, palette.rowMid, palette.rowBottom))
    }
    val glowCenter = Offset(ref.dp(66f).toPx(), size.height * .50f)
    val glowRadius = ref.dp(88f).toPx() * glowTightness
    val warmCenter = Offset(ref.dp(92f).toPx(), size.height * .54f)
    val warmRadius = ref.dp(47f).toPx() * glowTightness

    onDrawBehind {
        drawRoundRect(brush = face, cornerRadius = corner)
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.cyan.copy(alpha = .13f * illumination),
                    palette.steel.copy(alpha = .065f * illumination),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = glowRadius,
            ),
            cornerRadius = corner,
        )
        if (artwork == ApprovedPlayArtwork.ARENA) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFE3AC61).copy(alpha = .025f * illumination),
                        Color.Transparent,
                    ),
                    center = warmCenter,
                    radius = warmRadius,
                ),
                cornerRadius = corner,
            )
        }
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (pressed) .018f else .040f),
                    Color.White.copy(alpha = if (pressed) .006f else .012f),
                    Color.Transparent,
                ),
                endY = size.height * .48f,
            ),
            cornerRadius = corner,
        )
        // Diffuse whole-body lower occlusion; deliberately not a painted bottom strip/divider.
        drawRoundRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    .58f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (pressed) .10f else .19f),
                ),
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = if (pressed) {
                palette.rowPressedOutline
            } else {
                palette.rowOutline
            },
            cornerRadius = corner,
            style = Stroke(width = outlineWidth),
        )
    }
}

@Composable
private fun ApprovedHeroArtwork(
    artwork: ApprovedPlayArtwork,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image = remember(context, artwork) {
        context.assets.open(artwork.assetPath).use { input ->
            checkNotNull(BitmapFactory.decodeStream(input)) {
                "Unable to decode approved Play hero asset: ${artwork.assetPath}"
            }.asImageBitmap()
        }
    }
    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier.testTag(artwork.testTag),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun ApprovedQuickStartRow(
    ref: PlayReferenceScale,
    primary: String,
    secondary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = lumenP5IdentityPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .991f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-quick-scale",
    )
    val yOffset by animateDpAsState(
        targetValue = if (pressed) ref.dp(2f) else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-quick-y",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) ref.dp(1f) else ref.dp(3.6f),
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "approved-play-quick-shadow",
    )
    val shape = RoundedCornerShape(ref.dp(11f))

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = yOffset.toPx()
            }
            .shadow(
                elevation = elevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (pressed) .20f else .29f),
                spotColor = Color.Black.copy(alpha = if (pressed) .24f else .36f),
            )
            .clip(shape)
            .drawWithCache {
                val corner = CornerRadius(ref.dp(11f).toPx())
                val face = if (pressed) {
                    Brush.verticalGradient(listOf(palette.rowPressedTop, palette.rowPressedBottom))
                } else {
                    Brush.verticalGradient(listOf(palette.rowMid, palette.rowBottom))
                }
                onDrawBehind {
                    drawRoundRect(brush = face, cornerRadius = corner)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = if (pressed) .014f else .028f),
                                Color.Transparent,
                            ),
                            endY = size.height * .45f,
                        ),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                .62f to Color.Transparent,
                                1f to Color.Black.copy(alpha = if (pressed) .07f else .13f),
                            ),
                        ),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = palette.rowOutline.copy(alpha = .88f),
                        cornerRadius = corner,
                        style = Stroke(width = ref.dp(.75f).toPx().coerceAtLeast(1f)),
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Quick Start. $primary. $secondary" },
    ) {
        ApprovedQuickClockWell(
            ref = ref,
            palette = palette,
            modifier = Modifier
                .offset(x = ref.dp(13f), y = ref.dp(22f))
                .size(ref.dp(42f)),
        )
        Box(
            Modifier
                .offset(x = ref.dp(67f))
                .width(ref.dp(244f))
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column {
                Text(
                    primary,
                    color = palette.text,
                    fontSize = ref.sp(13f),
                    lineHeight = ref.sp(16f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    secondary,
                    modifier = Modifier.offset(y = ref.dp(3f)),
                    color = palette.muted.copy(alpha = .92f),
                    fontSize = ref.sp(11f),
                    lineHeight = ref.sp(14f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Canvas(
            Modifier
                .offset(x = ref.dp(327f), y = ref.dp(34f))
                .size(ref.dp(18f)),
        ) {
            val stroke = ref.dp(1.65f).toPx()
            val tint = palette.faint.copy(alpha = .96f)
            drawLine(
                tint,
                Offset(size.width * .37f, size.height * .22f),
                Offset(size.width * .64f, size.height * .50f),
                stroke,
                StrokeCap.Round,
            )
            drawLine(
                tint,
                Offset(size.width * .64f, size.height * .50f),
                Offset(size.width * .37f, size.height * .78f),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ApprovedQuickClockWell(
    ref: PlayReferenceScale,
    palette: LumenP5IdentityPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.testTag("play-overview-quick-clock-well")) {
        val radius = ref.dp(10f).toPx()
        val corner = CornerRadius(radius)
        drawRoundRect(color = palette.insetSurface, cornerRadius = corner)
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.cyan.copy(alpha = .115f),
                    palette.steel.copy(alpha = .055f),
                    Color.Transparent,
                ),
                center = Offset(size.width * .42f, size.height * .38f),
                radius = size.minDimension * .72f,
            ),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = palette.cyan.copy(alpha = .105f),
            cornerRadius = corner,
            style = Stroke(width = ref.dp(.75f).toPx().coerceAtLeast(1f)),
        )

        val center = this.center
        val clockRadius = ref.dp(8.2f).toPx()
        val stroke = ref.dp(1.75f).toPx()
        val tint = palette.cyan.copy(alpha = .96f)
        drawCircle(tint, clockRadius, center = center, style = Stroke(width = stroke))
        drawLine(
            tint,
            center,
            Offset(center.x, center.y - clockRadius * .48f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            center,
            Offset(center.x + clockRadius * .42f, center.y + clockRadius * .20f),
            stroke,
            StrokeCap.Round,
        )
    }
}

private fun Modifier.approvedPlayBackground(
    palette: LumenP5IdentityPalette,
): Modifier = drawWithCache {
    val dark = (palette.appBackground.red + palette.appBackground.green + palette.appBackground.blue) < 1.5f
    val bottom = if (dark) Color(0xFF070A0C) else palette.appBackground
    val topGlowCenter = Offset(size.width * .09f, 0f)
    val topGlowRadius = size.width * 1.05f
    onDrawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to palette.appBackgroundLift,
                    .27f to palette.appBackground,
                    1f to bottom,
                ),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.steel.copy(alpha = .055f),
                    Color.Transparent,
                ),
                center = topGlowCenter,
                radius = topGlowRadius,
            ),
            center = topGlowCenter,
            radius = topGlowRadius,
        )
    }
}

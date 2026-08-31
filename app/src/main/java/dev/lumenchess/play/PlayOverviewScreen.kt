package dev.lumenchess.play

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenDerivativeTopBar
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenTypography
import dev.lumenchess.design.lumenP5IdentityPalette
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class PlayOverviewArtwork(
    val assetPath: String,
    val testTag: String,
) {
    ENGINE(
        assetPath = "play-overview/lumen_play_vs_engine_hero.png",
        testTag = "play-overview-vs-engine-hero",
    ),
    ARENA(
        assetPath = "play-overview/lumen_engine_arena_hero.png",
        testTag = "play-overview-arena-hero",
    ),
}

private enum class QuickGlyph { CLOCK, ENGINE }
private enum class PlaySurfaceKind { ENGINE, ARENA, QUICK }

@Composable
internal fun ReferencePlayOverviewScreen(
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
    val pageGlow = LumenColors.AccentBlueBright

    Column(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to LumenColors.BackgroundLift,
                        .54f to LumenColors.Background,
                        1f to LumenColors.Background,
                    ),
                ),
            )
            .drawBehind {
                val glowCenter = Offset(size.width * .28f, size.height * .31f)
                val glowRadius = size.width * .72f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            pageGlow.copy(alpha = .018f),
                            pageGlow.copy(alpha = .006f),
                            Color.Transparent,
                        ),
                        center = glowCenter,
                        radius = glowRadius,
                    ),
                    center = glowCenter,
                    radius = glowRadius,
                )
            }
            .padding(horizontal = 24.dp, vertical = 22.dp)
            .testTag("p5-play-overview"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayOverviewTopBar(title = "Play", onBack = onBack)

        Spacer(Modifier.height(40.dp))

        PlayModeCard(
            title = "Play vs Engine",
            subtitle = "Challenge a chess engine",
            artwork = PlayOverviewArtwork.ENGINE,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.88f)
                .testTag("play-overview-vs-engine"),
            onClick = onPlayVsEngine,
        )
        PlayModeCard(
            title = "Engine Arena",
            subtitle = "Watch engines battle each other",
            artwork = PlayOverviewArtwork.ARENA,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.88f)
                .testTag("play-overview-arena"),
            onClick = onArenaPreview,
        )

        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Quick Start",
                style = LumenTypography.SectionTitle,
                color = LumenColors.OnSurface.copy(alpha = .98f),
            )
            Text(
                "Last used",
                style = LumenTypography.Meta,
                color = LumenColors.OnSurfaceMuted.copy(alpha = .96f),
            )
        }

        Spacer(Modifier.height(30.dp))
        QuickStartCard(
            primary = quickPrimary,
            secondary = quickSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.95f)
                .testTag("play-overview-quick-start"),
            onClick = onPlayVsEngine,
        )
    }
}

@Composable
private fun PlayOverviewTopBar(title: String, onBack: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) LumenMotion.IconPressScale else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseSpring(),
        label = "play-overview-back-scale",
    )
    val backColor = LumenColors.OnSurfaceMuted.copy(alpha = .92f)

    Box(Modifier.fillMaxWidth().height(36.dp)) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = LumenTypography.PlayTitle,
            color = LumenColors.OnSurface.copy(alpha = .98f),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onBack,
                )
                .semantics { contentDescription = "Navigate back" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(16.dp)) {
                val stroke = 1.4.dp.toPx()
                drawLine(
                    backColor,
                    Offset(size.width * .70f, size.height * .18f),
                    Offset(size.width * .34f, size.height * .50f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    backColor,
                    Offset(size.width * .34f, size.height * .50f),
                    Offset(size.width * .70f, size.height * .82f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun PlayTactileSurface(
    kind: PlaySurfaceKind,
    modifier: Modifier,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    depthTestTag: String? = null,
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val isQuick = kind == PlaySurfaceKind.QUICK
    val scale by animateFloatAsState(
        targetValue = if (pressed) {
            if (isQuick) .989f else LumenMotion.PlayCardPressScale
        } else {
            1f
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "play-tactile-scale-$kind",
    )
    val pressOffset by animateDpAsState(
        targetValue = if (pressed) {
            if (isQuick) .45.dp else .8.dp
        } else {
            0.dp
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "play-tactile-offset-$kind",
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (pressed) {
            if (isQuick) .7.dp else 1.dp
        } else {
            if (isQuick) 2.5.dp else 4.dp
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "play-tactile-shadow-$kind",
    )
    val lowerEdge by animateDpAsState(
        targetValue = if (pressed) {
            if (isQuick) .4.dp else .55.dp
        } else {
            if (isQuick) 1.6.dp else 2.4.dp
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "play-tactile-edge-$kind",
    )
    val pressDarken by animateFloatAsState(
        targetValue = if (pressed) {
            if (isQuick) .032f else .048f
        } else {
            0f
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "play-tactile-darken-$kind",
    )
    val illuminationGain by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "play-tactile-glow-$kind",
    )
    val border by animateColorAsState(
        targetValue = if (pressed) {
            LumenColors.OutlineStrong.copy(alpha = .94f)
        } else {
            LumenColors.Outline.copy(alpha = if (isQuick) .74f else .82f)
        },
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "play-tactile-border-$kind",
    )

    val shape = RoundedCornerShape(if (isQuick) 10.dp else 11.dp)
    val surface = LumenColors.Surface
    val raised = LumenColors.SurfaceRaised
    val highest = LumenColors.SurfaceHighest
    val faceLeft = when (kind) {
        PlaySurfaceKind.ENGINE -> lerp(surface, raised, .47f)
        PlaySurfaceKind.ARENA -> lerp(surface, raised, .42f)
        PlaySurfaceKind.QUICK -> lerp(surface, raised, .31f)
    }
    val faceMiddle = when (kind) {
        PlaySurfaceKind.QUICK -> lerp(surface, raised, .17f)
        else -> lerp(surface, raised, .25f)
    }
    val faceRight = lerp(surface, Color.Black, if (isQuick) .035f else .06f)
    val coolGlow = LumenColors.AccentBlueBright
    val warmGlow = Color(0xFFE3AC61)
    val edgeHighlight = LumenColors.OnSurface

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = pressOffset.toPx()
            }
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (pressed) .20f else .34f),
                spotColor = Color.Black.copy(alpha = if (pressed) .28f else .48f),
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(faceLeft, faceMiddle, faceRight),
                ),
            )
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            edgeHighlight.copy(alpha = if (isQuick) .028f else .040f),
                            edgeHighlight.copy(alpha = .010f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * .58f,
                    ),
                )

                if (!isQuick) {
                    val glowCenter = Offset(size.width * .18f, size.height * .49f)
                    val glowRadius = size.height * .63f
                    val primaryAlpha = when (kind) {
                        PlaySurfaceKind.ENGINE -> .116f
                        PlaySurfaceKind.ARENA -> .104f
                        PlaySurfaceKind.QUICK -> 0f
                    } * illuminationGain
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                coolGlow.copy(alpha = primaryAlpha),
                                coolGlow.copy(alpha = .036f * illuminationGain),
                                Color.Transparent,
                            ),
                            center = glowCenter,
                            radius = glowRadius,
                        ),
                        center = glowCenter,
                        radius = glowRadius,
                    )
                    if (kind == PlaySurfaceKind.ARENA) {
                        val warmCenter = Offset(size.width * .275f, size.height * .53f)
                        val warmRadius = size.height * .31f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    warmGlow.copy(alpha = .026f * illuminationGain),
                                    warmGlow.copy(alpha = .009f * illuminationGain),
                                    Color.Transparent,
                                ),
                                center = warmCenter,
                                radius = warmRadius,
                            ),
                            center = warmCenter,
                            radius = warmRadius,
                        )
                    }
                }

                if (pressDarken > 0f) {
                    drawRect(Color.Black.copy(alpha = pressDarken))
                }

                val lowerEdgePx = lowerEdge.toPx()
                if (lowerEdgePx > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = .08f),
                                Color.Black.copy(alpha = if (isQuick) .24f else .31f),
                            ),
                            startY = size.height - lowerEdgePx,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, size.height - lowerEdgePx),
                        size = Size(size.width, lowerEdgePx),
                    )
                }

                val inset = 1.dp.toPx()
                val innerWidth = (size.width - inset * 2f).coerceAtLeast(0f)
                val innerHeight = (size.height - inset * 2f).coerceAtLeast(0f)
                drawRoundRect(
                    color = edgeHighlight.copy(alpha = if (pressed) .068f else .038f),
                    topLeft = Offset(inset, inset),
                    size = Size(innerWidth, innerHeight),
                    cornerRadius = CornerRadius((if (isQuick) 9.dp else 10.dp).toPx()),
                    style = Stroke(width = .55.dp.toPx()),
                )
                drawLine(
                    color = edgeHighlight.copy(alpha = if (pressed) .088f else .062f),
                    start = Offset(13.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 13.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = .6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        val innerModifier = if (depthTestTag != null) {
            Modifier.fillMaxSize().testTag(depthTestTag)
        } else {
            Modifier.fillMaxSize()
        }
        Box(innerModifier) { content() }
    }
}

@Composable
private fun PlayModeCard(
    title: String,
    subtitle: String,
    artwork: PlayOverviewArtwork,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val surfaceKind = if (artwork == PlayOverviewArtwork.ENGINE) {
        PlaySurfaceKind.ENGINE
    } else {
        PlaySurfaceKind.ARENA
    }

    PlayTactileSurface(
        kind = surfaceKind,
        modifier = modifier,
        interactionSource = interaction,
        onClick = onClick,
        depthTestTag = if (artwork == PlayOverviewArtwork.ENGINE) {
            "play-overview-vs-engine-depth-surface"
        } else {
            null
        },
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayModeArtwork(
                kind = artwork,
                modifier = Modifier.size(86.dp),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    title,
                    style = LumenTypography.ModeTitle,
                    color = LumenColors.OnSurface.copy(alpha = .985f),
                )
                Text(
                    subtitle,
                    modifier = if (artwork == PlayOverviewArtwork.ARENA) {
                        Modifier.fillMaxWidth(.54f)
                    } else {
                        Modifier
                    },
                    style = LumenTypography.ModeSubtitle,
                    color = LumenColors.OnSurfaceMuted.copy(alpha = .985f),
                )
            }
        }
    }
}

@Composable
private fun PlayModeArtwork(
    kind: PlayOverviewArtwork,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image = remember(context, kind) {
        context.assets.open(kind.assetPath).use { input ->
            checkNotNull(BitmapFactory.decodeStream(input)) {
                "Unable to decode approved Play hero asset: ${kind.assetPath}"
            }.asImageBitmap()
        }
    }

    Image(
        bitmap = image,
        contentDescription = null,
        modifier = modifier.testTag(kind.testTag),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun QuickStartCard(
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    PlayTactileSurface(
        kind = PlaySurfaceKind.QUICK,
        modifier = modifier,
        interactionSource = interaction,
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            QuickStartLine(QuickGlyph.CLOCK, primary, emphasized = true)
            Spacer(Modifier.height(4.dp))
            QuickStartLine(QuickGlyph.ENGINE, secondary, emphasized = false)
        }
    }
}

@Composable
private fun QuickStartLine(
    glyph: QuickGlyph,
    text: String,
    emphasized: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        QuickStartGlyph(glyph, Modifier.size(26.dp))
        Text(
            text,
            style = if (emphasized) {
                LumenTypography.QuickPrimary
            } else {
                LumenTypography.QuickSecondary
            },
            color = if (emphasized) {
                LumenColors.OnSurface.copy(alpha = .98f)
            } else {
                LumenColors.OnSurfaceMuted.copy(alpha = .985f)
            },
        )
    }
}

@Composable
private fun QuickStartGlyph(kind: QuickGlyph, modifier: Modifier = Modifier) {
    val tint = LumenColors.AccentBlueBright
    val well = LumenColors.SurfaceHighest
    val wellOutline = LumenColors.OutlineStrong
    Canvas(modifier) {
        val stroke = 1.45.dp.toPx()
        drawCircle(well.copy(alpha = .92f), size.minDimension * .48f)
        drawCircle(
            wellOutline.copy(alpha = .72f),
            size.minDimension * .46f,
            style = Stroke(stroke * .62f),
        )
        drawCircle(
            Color.Black.copy(alpha = .12f),
            size.minDimension * .38f,
            style = Stroke(stroke * .50f),
        )
        when (kind) {
            QuickGlyph.CLOCK -> {
                drawCircle(tint.copy(alpha = .10f), size.minDimension * .34f)
                drawCircle(
                    tint.copy(alpha = .94f),
                    size.minDimension * .30f,
                    style = Stroke(stroke * 1.02f),
                )
                drawCircle(
                    Color.White.copy(alpha = .10f),
                    size.minDimension * .24f,
                    style = Stroke(stroke * .46f),
                )
                drawLine(
                    tint,
                    center,
                    Offset(center.x, size.height * .29f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    center,
                    Offset(size.width * .63f, size.height * .56f),
                    stroke,
                    StrokeCap.Round,
                )
            }
            QuickGlyph.ENGINE -> {
                val badge = Path()
                repeat(6) { index ->
                    val angle = (-PI / 2) + index * PI / 3
                    val point = Offset(
                        center.x + cos(angle).toFloat() * size.minDimension * .30f,
                        center.y + sin(angle).toFloat() * size.minDimension * .30f,
                    )
                    if (index == 0) badge.moveTo(point.x, point.y) else badge.lineTo(point.x, point.y)
                }
                badge.close()
                drawPath(
                    badge,
                    brush = Brush.radialGradient(
                        listOf(
                            tint.copy(alpha = .22f),
                            tint.copy(alpha = .075f),
                        ),
                        center = Offset(size.width * .40f, size.height * .35f),
                        radius = size.minDimension * .36f,
                    ),
                )
                drawPath(
                    badge,
                    tint.copy(alpha = .92f),
                    style = Stroke(width = stroke * .94f, join = StrokeJoin.Round),
                )
                drawCircle(tint.copy(alpha = .92f), size.minDimension * .105f)
                repeat(4) { index ->
                    val angle = index * PI / 2
                    val inner = size.minDimension * .16f
                    val outer = size.minDimension * .225f
                    drawLine(
                        tint.copy(alpha = .84f),
                        Offset(
                            center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner,
                        ),
                        Offset(
                            center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer,
                        ),
                        stroke * .88f,
                        StrokeCap.Round,
                    )
                }
                drawCircle(Color.White.copy(alpha = .32f), size.minDimension * .032f)
            }
        }
    }
}

@Composable
internal fun ReferenceArenaPreviewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = lumenP5IdentityPalette()
    LumenDerivativePage(
        modifier = modifier.fillMaxSize(),
        testTag = "derivative-arena-preview",
        horizontalPadding = 18,
        spacing = 12,
    ) {
        LumenDerivativeTopBar(title = "Engine Arena", onBack = onBack)
        LumenDerivativeSurface(
            role = DerivativeSurfaceRole.PREVIEW_PANEL,
            modifier = Modifier.fillMaxWidth().height(132.dp),
            testTag = "derivative-preview-panel",
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                PlayModeArtwork(kind = PlayOverviewArtwork.ARENA, modifier = Modifier.size(78.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Engine Arena",
                        style = LumenTypography.ModeTitle,
                        color = palette.text,
                    )
                    Text(
                        text = "Set up engine battles, opening positions, and independent strength profiles in Arena.",
                        style = LumenTypography.ModeSubtitle,
                        color = palette.muted,
                    )
                }
            }
        }
    }
}

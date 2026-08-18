package dev.lumenchess.play

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenTypography
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

    Column(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LumenColors.BackgroundLift, LumenColors.Background),
                ),
            )
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
                style = LumenTypography.SectionTitle.copy(
                    fontSize = 18.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = LumenColors.OnSurface,
            )
            Text(
                "Last used",
                style = LumenTypography.Meta.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = LumenColors.OnSurfaceMuted,
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
    val backColor = LumenColors.OnSurfaceMuted

    Box(Modifier.fillMaxWidth().height(36.dp)) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = LumenTypography.PlayTitle.copy(
                fontSize = 18.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = LumenColors.OnSurface,
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
private fun PlayModeCard(
    title: String,
    subtitle: String,
    artwork: PlayOverviewArtwork,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) LumenMotion.PlayCardPressScale else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseSpring(),
        label = "play-mode-card-scale",
    )
    val border by animateColorAsState(
        targetValue = if (pressed) {
            LumenColors.OutlineStrong
        } else {
            LumenColors.Outline.copy(alpha = .82f)
        },
        animationSpec = LumenMotion.pressTween(),
        label = "play-mode-card-border",
    )
    val shape = RoundedCornerShape(11.dp)
    val illumination = LumenColors.AccentBlueBright
    val edgeHighlight = LumenColors.OnSurface
    val fill = if (pressed) {
        Brush.horizontalGradient(
            listOf(
                LumenColors.SurfaceHighest,
                LumenColors.SurfaceRaised,
                LumenColors.SurfaceRaised,
            ),
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                LumenColors.SurfaceHighest.copy(alpha = .82f),
                LumenColors.SurfaceRaised.copy(alpha = .97f),
                LumenColors.Surface.copy(alpha = .99f),
            ),
        )
    }

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 3.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = .34f),
                spotColor = Color.Black.copy(alpha = .46f),
            )
            .clip(shape)
            .background(fill)
            .drawBehind {
                val glowCenter = Offset(size.width * .18f, size.height * .50f)
                val glowRadius = size.height * .74f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            illumination.copy(alpha = if (pressed) .20f else .145f),
                            illumination.copy(alpha = .055f),
                            Color.Transparent,
                        ),
                        center = glowCenter,
                        radius = glowRadius,
                    ),
                    center = glowCenter,
                    radius = glowRadius,
                )
                drawLine(
                    color = edgeHighlight.copy(alpha = .055f),
                    start = Offset(13.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 13.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = .65.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
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
                style = LumenTypography.ModeTitle.copy(
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = LumenColors.OnSurface,
            )
            Text(
                subtitle,
                modifier = if (artwork == PlayOverviewArtwork.ARENA) {
                    Modifier.fillMaxWidth(.80f)
                } else {
                    Modifier
                },
                style = LumenTypography.ModeSubtitle.copy(
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = LumenColors.OnSurfaceMuted.copy(alpha = .94f),
            )
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
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .shadow(
                elevation = 2.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = .28f),
                spotColor = Color.Black.copy(alpha = .36f),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        LumenColors.SurfaceRaised.copy(alpha = .97f),
                        LumenColors.Surface.copy(alpha = .99f),
                    ),
                ),
            )
            .border(1.dp, LumenColors.Outline.copy(alpha = .80f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        QuickStartLine(QuickGlyph.CLOCK, primary, emphasized = true)
        Spacer(Modifier.height(4.dp))
        QuickStartLine(QuickGlyph.ENGINE, secondary, emphasized = false)
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
                LumenTypography.QuickPrimary.copy(fontSize = 17.sp, lineHeight = 20.sp)
            } else {
                LumenTypography.QuickSecondary.copy(fontSize = 15.sp, lineHeight = 18.sp)
            },
            color = if (emphasized) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun QuickStartGlyph(kind: QuickGlyph, modifier: Modifier = Modifier) {
    val tint = LumenColors.AccentBlueBright
    Canvas(modifier) {
        val stroke = 1.45.dp.toPx()
        when (kind) {
            QuickGlyph.CLOCK -> {
                drawCircle(tint.copy(alpha = .15f), size.minDimension * .48f)
                drawCircle(
                    tint.copy(alpha = .98f),
                    size.minDimension * .35f,
                    style = Stroke(stroke * 1.20f),
                )
                drawCircle(
                    Color.White.copy(alpha = .12f),
                    size.minDimension * .28f,
                    style = Stroke(stroke * .52f),
                )
                drawLine(
                    tint,
                    center,
                    Offset(center.x, size.height * .27f),
                    stroke * 1.12f,
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    center,
                    Offset(size.width * .65f, size.height * .57f),
                    stroke * 1.12f,
                    StrokeCap.Round,
                )
            }
            QuickGlyph.ENGINE -> {
                drawCircle(tint.copy(alpha = .12f), size.minDimension * .48f)
                val badge = Path()
                repeat(6) { index ->
                    val angle = (-PI / 2) + index * PI / 3
                    val point = Offset(
                        center.x + cos(angle).toFloat() * size.minDimension * .34f,
                        center.y + sin(angle).toFloat() * size.minDimension * .34f,
                    )
                    if (index == 0) badge.moveTo(point.x, point.y) else badge.lineTo(point.x, point.y)
                }
                badge.close()
                drawPath(
                    badge,
                    brush = Brush.radialGradient(
                        listOf(
                            tint.copy(alpha = .26f),
                            tint.copy(alpha = .10f),
                        ),
                        center = Offset(size.width * .40f, size.height * .35f),
                        radius = size.minDimension * .40f,
                    ),
                )
                drawPath(
                    badge,
                    tint.copy(alpha = .96f),
                    style = Stroke(width = stroke * 1.05f, join = StrokeJoin.Round),
                )
                drawCircle(tint.copy(alpha = .94f), size.minDimension * .115f)
                repeat(4) { index ->
                    val angle = index * PI / 2
                    val inner = size.minDimension * .17f
                    val outer = size.minDimension * .25f
                    drawLine(
                        tint.copy(alpha = .88f),
                        Offset(
                            center.x + cos(angle).toFloat() * inner,
                            center.y + sin(angle).toFloat() * inner,
                        ),
                        Offset(
                            center.x + cos(angle).toFloat() * outer,
                            center.y + sin(angle).toFloat() * outer,
                        ),
                        stroke * .95f,
                        StrokeCap.Round,
                    )
                }
                drawCircle(Color.White.copy(alpha = .36f), size.minDimension * .035f)
            }
        }
    }
}

@Composable
internal fun ReferenceArenaPreviewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LumenColors.BackgroundLift, LumenColors.Background),
                ),
            )
            .padding(horizontal = 13.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dev.lumenchess.design.LumenTopBar("Engine Arena", onBack = onBack)
        dev.lumenchess.design.LumenPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Engine Arena",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Arena setup will arrive with its engine-battle runtime. The Play shell is already reserved for it.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                )
            }
        }
    }
}

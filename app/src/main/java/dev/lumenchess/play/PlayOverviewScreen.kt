package dev.lumenchess.play

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
import dev.lumenchess.design.LumenTypography
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class PlayOverviewArtwork { ENGINE, ARENA }
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
            .padding(horizontal = 22.dp, vertical = 22.dp)
            .testTag("p5-play-overview"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlayOverviewTopBar(title = "Play", onBack = onBack)

        Spacer(Modifier.height(34.dp))

        PlayModeCard(
            title = "Play vs Engine",
            subtitle = "Challenge a chess engine",
            artwork = PlayOverviewArtwork.ENGINE,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.85f)
                .testTag("play-overview-vs-engine"),
            onClick = onPlayVsEngine,
        )
        PlayModeCard(
            title = "Engine Arena",
            subtitle = "Watch engines battle each other",
            artwork = PlayOverviewArtwork.ARENA,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.85f)
                .testTag("play-overview-arena"),
            onClick = onArenaPreview,
        )

        Spacer(Modifier.height(27.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Quick Start",
                style = LumenTypography.SectionTitle.copy(
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = LumenColors.OnSurface,
            )
            Text(
                "Last used",
                style = LumenTypography.Meta.copy(fontSize = 11.5.sp, lineHeight = 14.sp),
                color = LumenColors.OnSurfaceMuted,
            )
        }

        Spacer(Modifier.height(31.dp))
        QuickStartCard(
            primary = quickPrimary,
            secondary = quickSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2.83f)
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

    Box(Modifier.fillMaxWidth().height(40.dp)) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = LumenTypography.PlayTitle.copy(fontSize = 19.sp, lineHeight = 21.sp),
            color = LumenColors.OnSurface,
            fontWeight = FontWeight.SemiBold,
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
    val fill = if (pressed) {
        Brush.verticalGradient(listOf(LumenColors.SurfaceHighest, LumenColors.SurfaceRaised))
    } else {
        Brush.verticalGradient(
            listOf(
                LumenColors.SurfaceRaised.copy(alpha = .98f),
                LumenColors.Surface.copy(alpha = .99f),
                LumenColors.SurfaceRaised.copy(alpha = .90f),
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
            .border(1.dp, border, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayModeArtwork(
            kind = artwork,
            pressed = pressed,
            modifier = Modifier.size(116.dp),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = LumenTypography.ModeTitle.copy(
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = LumenColors.OnSurface,
            )
            Text(
                subtitle,
                modifier = if (artwork == PlayOverviewArtwork.ARENA) {
                    Modifier.fillMaxWidth(.86f)
                } else {
                    Modifier
                },
                style = LumenTypography.ModeSubtitle.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = LumenColors.OnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun PlayModeArtwork(
    kind: PlayOverviewArtwork,
    pressed: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (pressed) LumenColors.AccentBlueBright else LumenColors.AccentBlue
    val bright = LumenColors.AccentBlueBright
    val deep = LumenColors.AccentBlueSoft

    Canvas(modifier) {
        val unit = size.minDimension
        val glowAlpha = if (pressed) .43f else .31f
        val glowCenter = Offset(size.width * .46f, size.height * .51f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    bright.copy(alpha = glowAlpha),
                    accent.copy(alpha = .15f),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = unit * .54f,
            ),
            center = glowCenter,
            radius = unit * .52f,
        )

        when (kind) {
            PlayOverviewArtwork.ENGINE -> {
                val shield = Path().apply {
                    moveTo(size.width * .12f, size.height * .22f)
                    lineTo(size.width * .37f, size.height * .08f)
                    lineTo(size.width * .72f, size.height * .15f)
                    lineTo(size.width * .90f, size.height * .39f)
                    lineTo(size.width * .80f, size.height * .73f)
                    lineTo(size.width * .51f, size.height * .94f)
                    lineTo(size.width * .18f, size.height * .82f)
                    lineTo(size.width * .02f, size.height * .51f)
                    close()
                }
                drawPath(
                    path = shield,
                    brush = Brush.linearGradient(
                        listOf(
                            bright.copy(alpha = .94f),
                            accent.copy(alpha = .76f),
                            deep.copy(alpha = .98f),
                        ),
                        start = Offset(size.width * .12f, size.height * .11f),
                        end = Offset(size.width * .80f, size.height * .90f),
                    ),
                )
                drawPath(
                    path = shield,
                    color = Color(0xFFCDEBF2).copy(alpha = .38f),
                    style = Stroke(width = 1.1.dp.toPx(), join = StrokeJoin.Round),
                )

                val rook = Path().apply {
                    moveTo(size.width * .24f, size.height * .74f)
                    lineTo(size.width * .62f, size.height * .74f)
                    lineTo(size.width * .59f, size.height * .64f)
                    lineTo(size.width * .56f, size.height * .36f)
                    lineTo(size.width * .63f, size.height * .31f)
                    lineTo(size.width * .63f, size.height * .23f)
                    lineTo(size.width * .55f, size.height * .23f)
                    lineTo(size.width * .55f, size.height * .30f)
                    lineTo(size.width * .47f, size.height * .30f)
                    lineTo(size.width * .47f, size.height * .22f)
                    lineTo(size.width * .38f, size.height * .22f)
                    lineTo(size.width * .38f, size.height * .30f)
                    lineTo(size.width * .30f, size.height * .30f)
                    lineTo(size.width * .30f, size.height * .23f)
                    lineTo(size.width * .22f, size.height * .23f)
                    lineTo(size.width * .22f, size.height * .32f)
                    lineTo(size.width * .30f, size.height * .38f)
                    lineTo(size.width * .31f, size.height * .63f)
                    close()
                }
                drawPath(rook, Color(0xFF17323B).copy(alpha = .86f))

                val bolt = Path().apply {
                    moveTo(size.width * .60f, size.height * .12f)
                    lineTo(size.width * .35f, size.height * .48f)
                    lineTo(size.width * .52f, size.height * .48f)
                    lineTo(size.width * .36f, size.height * .87f)
                    lineTo(size.width * .79f, size.height * .37f)
                    lineTo(size.width * .58f, size.height * .37f)
                    close()
                }
                drawPath(bolt, Color(0xFFF5FCFD))
                drawPath(
                    bolt,
                    bright.copy(alpha = .66f),
                    style = Stroke(width = 1.25.dp.toPx(), join = StrokeJoin.Round),
                )

                drawLine(
                    bright.copy(alpha = .69f),
                    Offset(size.width * .01f, size.height * .31f),
                    Offset(size.width * .15f, size.height * .38f),
                    1.2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    bright.copy(alpha = .54f),
                    Offset(size.width * .75f, size.height * .77f),
                    Offset(size.width * .95f, size.height * .68f),
                    1.1.dp.toPx(),
                    StrokeCap.Round,
                )
            }

            PlayOverviewArtwork.ARENA -> {
                val globeCenter = Offset(size.width * .46f, size.height * .50f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF89A9FF).copy(alpha = .94f),
                            Color(0xFF416BB7).copy(alpha = .95f),
                            deep.copy(alpha = .99f),
                        ),
                        center = Offset(size.width * .34f, size.height * .29f),
                        radius = unit * .50f,
                    ),
                    radius = unit * .39f,
                    center = globeCenter,
                )
                drawCircle(
                    color = Color(0xFFCAD7FF).copy(alpha = .44f),
                    radius = unit * .39f,
                    center = globeCenter,
                    style = Stroke(width = 1.05.dp.toPx()),
                )

                val blade = Color(0xFFD7EAF0)
                drawLine(
                    blade.copy(alpha = .90f),
                    Offset(size.width * .07f, size.height * .18f),
                    Offset(size.width * .88f, size.height * .84f),
                    2.7.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    blade.copy(alpha = .84f),
                    Offset(size.width * .87f, size.height * .14f),
                    Offset(size.width * .08f, size.height * .83f),
                    2.35.dp.toPx(),
                    StrokeCap.Round,
                )

                val knight = Path().apply {
                    moveTo(size.width * .24f, size.height * .76f)
                    lineTo(size.width * .69f, size.height * .76f)
                    lineTo(size.width * .65f, size.height * .66f)
                    cubicTo(
                        size.width * .71f,
                        size.height * .56f,
                        size.width * .68f,
                        size.height * .44f,
                        size.width * .57f,
                        size.height * .36f,
                    )
                    lineTo(size.width * .70f, size.height * .25f)
                    lineTo(size.width * .57f, size.height * .17f)
                    lineTo(size.width * .45f, size.height * .23f)
                    lineTo(size.width * .35f, size.height * .35f)
                    lineTo(size.width * .22f, size.height * .52f)
                    lineTo(size.width * .42f, size.height * .49f)
                    lineTo(size.width * .27f, size.height * .64f)
                    close()
                }
                drawPath(knight, Color(0xFFF1F5F7))
                drawPath(
                    knight,
                    Color(0xFFBFD3DB).copy(alpha = .58f),
                    style = Stroke(width = .9.dp.toPx(), join = StrokeJoin.Round),
                )
                drawCircle(
                    color = Color(0xFF16313A),
                    radius = 1.65.dp.toPx(),
                    center = Offset(size.width * .49f, size.height * .31f),
                )

                val spark = Color(0xFFE2B65C)
                drawLine(
                    spark,
                    Offset(size.width * .58f, size.height * .06f),
                    Offset(size.width * .60f, size.height * .29f),
                    1.75.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    spark.copy(alpha = .82f),
                    Offset(size.width * .51f, size.height * .15f),
                    Offset(size.width * .68f, size.height * .20f),
                    1.25.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
    }
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
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        QuickStartLine(QuickGlyph.CLOCK, primary, emphasized = true)
        Spacer(Modifier.height(6.dp))
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickStartGlyph(glyph, Modifier.size(26.dp))
        Text(
            text,
            style = if (emphasized) {
                LumenTypography.QuickPrimary.copy(fontSize = 14.sp, lineHeight = 17.sp)
            } else {
                LumenTypography.QuickSecondary.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
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
                drawCircle(tint.copy(alpha = .13f), size.minDimension * .48f)
                drawCircle(tint, size.minDimension * .35f, style = Stroke(stroke))
                drawLine(tint, center, Offset(center.x, size.height * .27f), stroke, StrokeCap.Round)
                drawLine(
                    tint,
                    center,
                    Offset(size.width * .65f, size.height * .57f),
                    stroke,
                    StrokeCap.Round,
                )
            }
            QuickGlyph.ENGINE -> {
                drawCircle(tint.copy(alpha = .13f), size.minDimension * .48f)
                drawCircle(tint, size.minDimension * .25f, style = Stroke(stroke))
                repeat(6) { index ->
                    val angle = index * PI / 3
                    val inner = size.minDimension * .29f
                    val outer = size.minDimension * .39f
                    val start = Offset(
                        center.x + cos(angle).toFloat() * inner,
                        center.y + sin(angle).toFloat() * inner,
                    )
                    val end = Offset(
                        center.x + cos(angle).toFloat() * outer,
                        center.y + sin(angle).toFloat() * outer,
                    )
                    drawLine(tint, start, end, stroke * 1.25f, StrokeCap.Round)
                }
                drawCircle(tint, size.minDimension * .07f)
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

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
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
                style = LumenTypography.SectionTitle,
                color = LumenColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Last used",
                style = LumenTypography.Meta,
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
    Box(Modifier.fillMaxWidth().height(40.dp)) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            style = LumenTypography.PlayTitle,
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
                val color = LumenColors.OnSurfaceMuted
                val stroke = 1.4.dp.toPx()
                drawLine(
                    color,
                    Offset(size.width * .70f, size.height * .18f),
                    Offset(size.width * .34f, size.height * .50f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
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
        targetValue = if (pressed) LumenColors.OutlineStrong else LumenColors.Outline.copy(alpha = .82f),
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
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PlayModeArtwork(
            kind = artwork,
            pressed = pressed,
            modifier = Modifier.size(104.dp),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = LumenTypography.ModeTitle,
                color = LumenColors.OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                subtitle,
                style = LumenTypography.ModeSubtitle,
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

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    bright.copy(alpha = glowAlpha),
                    accent.copy(alpha = .15f),
                    Color.Transparent,
                ),
                center = Offset(size.width * .46f, size.height * .51f),
                radius = unit * .53f,
            ),
            center = Offset(size.width * .46f, size.height * .51f),
            radius = unit * .51f,
        )

        when (kind) {
            PlayOverviewArtwork.ENGINE -> {
                val energyShield = Path().apply {
                    moveTo(size.width * .18f, size.height * .24f)
                    lineTo(size.width * .38f, size.height * .12f)
                    lineTo(size.width * .67f, size.height * .18f)
                    lineTo(size.width * .83f, size.height * .39f)
                    lineTo(size.width * .75f, size.height * .68f)
                    lineTo(size.width * .51f, size.height * .87f)
                    lineTo(size.width * .24f, size.height * .77f)
                    lineTo(size.width * .10f, size.height * .51f)
                    close()
                }
                drawPath(
                    path = energyShield,
                    brush = Brush.linearGradient(
                        listOf(
                            bright.copy(alpha = .92f),
                            accent.copy(alpha = .72f),
                            deep.copy(alpha = .96f),
                        ),
                        start = Offset(size.width * .18f, size.height * .15f),
                        end = Offset(size.width * .74f, size.height * .84f),
                    ),
                )
                drawPath(
                    path = energyShield,
                    color = Color(0xFFCDEBF2).copy(alpha = .36f),
                    style = Stroke(width = 1.1.dp.toPx()),
                )

                val rook = Path().apply {
                    moveTo(size.width * .29f, size.height * .70f)
                    lineTo(size.width * .61f, size.height * .70f)
                    lineTo(size.width * .58f, size.height * .62f)
                    lineTo(size.width * .55f, size.height * .38f)
                    lineTo(size.width * .61f, size.height * .33f)
                    lineTo(size.width * .61f, size.height * .26f)
                    lineTo(size.width * .54f, size.height * .26f)
                    lineTo(size.width * .54f, size.height * .32f)
                    lineTo(size.width * .47f, size.height * .32f)
                    lineTo(size.width * .47f, size.height * .25f)
                    lineTo(size.width * .40f, size.height * .25f)
                    lineTo(size.width * .40f, size.height * .32f)
                    lineTo(size.width * .33f, size.height * .32f)
                    lineTo(size.width * .33f, size.height * .26f)
                    lineTo(size.width * .26f, size.height * .26f)
                    lineTo(size.width * .26f, size.height * .34f)
                    lineTo(size.width * .33f, size.height * .39f)
                    lineTo(size.width * .34f, size.height * .61f)
                    close()
                }
                drawPath(rook, Color(0xFF17323B).copy(alpha = .84f))

                val bolt = Path().apply {
                    moveTo(size.width * .59f, size.height * .17f)
                    lineTo(size.width * .37f, size.height * .48f)
                    lineTo(size.width * .52f, size.height * .48f)
                    lineTo(size.width * .38f, size.height * .82f)
                    lineTo(size.width * .75f, size.height * .39f)
                    lineTo(size.width * .57f, size.height * .39f)
                    close()
                }
                drawPath(bolt, Color(0xFFF5FCFD))
                drawPath(
                    bolt,
                    bright.copy(alpha = .64f),
                    style = Stroke(
                        width = 1.25.dp.toPx(),
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )

                drawLine(
                    bright.copy(alpha = .67f),
                    Offset(size.width * .09f, size.height * .34f),
                    Offset(size.width * .19f, size.height * .39f),
                    1.2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    bright.copy(alpha = .52f),
                    Offset(size.width * .72f, size.height * .72f),
                    Offset(size.width * .86f, size.height * .66f),
                    1.1.dp.toPx(),
                    StrokeCap.Round,
                )
            }

            PlayOverviewArtwork.ARENA -> {
                val globeCenter = Offset(size.width * .46f, size.height * .50f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF89A9FF).copy(alpha = .92f),
                            Color(0xFF416BB7).copy(alpha = .94f),
                            deep.copy(alpha = .98f),
                        ),
                        center = Offset(size.width * .36f, size.height * .31f),
                        radius = unit * .46f,
                    ),
                    radius = unit * .34f,
                    center = globeCenter,
                )
                drawCircle(
                    color = Color(0xFFCAD7FF).copy(alpha = .42f),
                    radius = unit * .34f,
                    center = globeCenter,
                    style = Stroke(width = 1.05.dp.toPx()),
                )

                val blade = Color(0xFFD7EAF0)
                drawLine(
                    blade.copy(alpha = .88f),
                    Offset(size.width * .15f, size.height * .25f),
                    Offset(size.width * .80f, size.height * .78f),
                    2.6.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    blade.copy(alpha = .80f),
                    Offset(size.width * .78f, size.height * .20f),
                    Offset(size.width * .17f, size.height * .76f),
                    2.25.dp.toPx(),
                    StrokeCap.Round,
                )

                val knight = Path().apply {
                    moveTo(size.width * .28f, size.height * .71f)
                    lineTo(size.width * .66f, size.height * .71f)
                    lineTo(size.width * .62f, size.height * .63f)
                    cubicTo(
                        size.width * .67f, size.height * .55f,
                        size.width * .65f, size.height * .46f,
                        size.width * .57f, size.height * .40f,
                    )
                    lineTo(size.width * .68f, size.height * .31f)
                    lineTo(size.width * .57f, size.height * .24f)
                    lineTo(size.width * .47f, size.height * .29f)
                    lineTo(size.width * .39f, size.height * .39f)
                    lineTo(size.width * .28f, size.height * .52f)
                    lineTo(size.width * .44f, size.height * .50f)
                    lineTo(size.width * .32f, size.height * .62f)
                    close()
                }
                drawPath(knight, Color(0xFFF1F5F7))
                drawPath(
                    knight,
                    Color(0xFFBFD3DB).copy(alpha = .55f),
                    style = Stroke(width = .9.dp.toPx()),
                )
                drawCircle(
                    color = Color(0xFF16313A),
                    radius = 1.55.dp.toPx(),
                    center = Offset(size.width * .50f, size.height * .36f),
                )

                val spark = Color(0xFFE2B65C)
                drawLine(
                    spark,
                    Offset(size.width * .57f, size.height * .13f),
                    Offset(size.width * .59f, size.height * .32f),
                    1.7.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    spark.copy(alpha = .80f),
                    Offset(size.width * .52f, size.height * .20f),
                    Offset(size.width * .65f, size.height * .24f),
                    1.2.dp.toPx(),
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
        Spacer(Modifier.height(7.dp))
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
        QuickStartGlyph(glyph, Modifier.size(22.dp))
        Text(
            text,
            style = if (emphasized) LumenTypography.QuickPrimary else LumenTypography.QuickSecondary,
            color = if (emphasized) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
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
                drawLine(tint, center, Offset(size.width * .65f, size.height * .57f), stroke, StrokeCap.Round)
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
        modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .padding(horizontal = 13.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dev.lumenchess.design.LumenTopBar("Engine Arena", onBack = onBack)
        dev.lumenchess.design.LumenPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

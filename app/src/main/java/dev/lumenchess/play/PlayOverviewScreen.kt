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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenTopBar
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
        LumenTopBar(title = "Play", onBack = onBack)

        // The reference phone leaves deliberate breathing room between its compact header and the
        // two large mode cards. This spacer is proportion-driven rather than a generic list gap.
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
        targetValue = if (pressed) LumenColors.OutlineStrong else LumenColors.Outline.copy(alpha = .78f),
        animationSpec = LumenMotion.pressTween(),
        label = "play-mode-card-border",
    )
    val shape = RoundedCornerShape(11.dp)
    val fill = if (pressed) {
        Brush.verticalGradient(listOf(LumenColors.SurfaceHighest, LumenColors.SurfaceRaised))
    } else {
        Brush.verticalGradient(listOf(LumenColors.SurfaceRaised, LumenColors.Surface))
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
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PlayModeArtwork(
            kind = artwork,
            pressed = pressed,
            modifier = Modifier.size(84.dp),
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp),
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
    val surface = LumenColors.Surface
    Canvas(modifier) {
        val glowAlpha = if (pressed) .48f else .36f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(bright.copy(alpha = glowAlpha), bright.copy(alpha = .09f), Color.Transparent),
                center = center,
                radius = size.minDimension * .56f,
            ),
            radius = size.minDimension * .50f,
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    bright.copy(alpha = .88f),
                    accent.copy(alpha = .72f),
                    surface.copy(alpha = .98f),
                ),
                center = Offset(size.width * .37f, size.height * .31f),
                radius = size.minDimension * .54f,
            ),
            radius = size.minDimension * .37f,
        )
        drawCircle(
            color = Color.White.copy(alpha = .18f),
            radius = size.minDimension * .34f,
            style = Stroke(width = 1.dp.toPx()),
        )

        when (kind) {
            PlayOverviewArtwork.ENGINE -> {
                val bolt = Path().apply {
                    moveTo(size.width * .53f, size.height * .17f)
                    lineTo(size.width * .31f, size.height * .48f)
                    lineTo(size.width * .47f, size.height * .47f)
                    lineTo(size.width * .37f, size.height * .81f)
                    lineTo(size.width * .72f, size.height * .39f)
                    lineTo(size.width * .54f, size.height * .39f)
                    close()
                }
                drawPath(bolt, Color(0xFFF4FBFC))
                drawPath(
                    bolt,
                    bright.copy(alpha = .55f),
                    style = Stroke(width = 1.2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round),
                )
                val crownY = size.height * .72f
                drawLine(
                    color = Color.White.copy(alpha = .70f),
                    start = Offset(size.width * .19f, crownY),
                    end = Offset(size.width * .29f, crownY),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            PlayOverviewArtwork.ARENA -> {
                val bladeColor = Color(0xFFCFE7EF)
                val stroke = 2.dp.toPx()
                drawLine(
                    bladeColor.copy(alpha = .80f),
                    Offset(size.width * .24f, size.height * .28f),
                    Offset(size.width * .72f, size.height * .74f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    bladeColor.copy(alpha = .80f),
                    Offset(size.width * .72f, size.height * .27f),
                    Offset(size.width * .27f, size.height * .73f),
                    stroke,
                    StrokeCap.Round,
                )
                drawCircle(
                    color = Color(0xFFD7A45A).copy(alpha = .88f),
                    radius = 3.dp.toPx(),
                    center = center,
                )
                val knight = Path().apply {
                    moveTo(size.width * .42f, size.height * .68f)
                    lineTo(size.width * .60f, size.height * .68f)
                    cubicTo(
                        size.width * .63f, size.height * .59f,
                        size.width * .62f, size.height * .51f,
                        size.width * .55f, size.height * .45f,
                    )
                    lineTo(size.width * .62f, size.height * .35f)
                    lineTo(size.width * .51f, size.height * .30f)
                    lineTo(size.width * .44f, size.height * .36f)
                    lineTo(size.width * .36f, size.height * .52f)
                    lineTo(size.width * .47f, size.height * .50f)
                    lineTo(size.width * .39f, size.height * .61f)
                    close()
                }
                drawPath(knight, Color(0xFFF0F5F6))
                drawCircle(
                    color = Color(0xFF16262B),
                    radius = 1.3.dp.toPx(),
                    center = Offset(size.width * .50f, size.height * .39f),
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
            .background(Brush.verticalGradient(listOf(LumenColors.SurfaceRaised, LumenColors.Surface)))
            .border(1.dp, LumenColors.Outline.copy(alpha = .76f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickStartLine(QuickGlyph.CLOCK, primary, emphasized = true)
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
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        QuickStartGlyph(glyph, Modifier.size(19.dp))
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
        val stroke = 1.5.dp.toPx()
        when (kind) {
            QuickGlyph.CLOCK -> {
                drawCircle(tint.copy(alpha = .16f), size.minDimension * .49f)
                drawCircle(tint, size.minDimension * .36f, style = Stroke(stroke))
                drawLine(tint, center, Offset(center.x, size.height * .27f), stroke, StrokeCap.Round)
                drawLine(tint, center, Offset(size.width * .66f, size.height * .57f), stroke, StrokeCap.Round)
            }
            QuickGlyph.ENGINE -> {
                drawCircle(tint.copy(alpha = .15f), size.minDimension * .49f)
                val path = Path()
                val outer = size.minDimension * .34f
                val inner = outer * .45f
                repeat(10) { index ->
                    val radius = if (index % 2 == 0) outer else inner
                    val angle = -PI / 2 + index * PI / 5
                    val x = center.x + cos(angle).toFloat() * radius
                    val y = center.y + sin(angle).toFloat() * radius
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, tint)
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
        LumenTopBar("Engine Arena", onBack = onBack)
        dev.lumenchess.design.LumenPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Engine Arena", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Arena setup will arrive with its engine-battle runtime. The Play shell is already reserved for it.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                )
            }
        }
    }
}

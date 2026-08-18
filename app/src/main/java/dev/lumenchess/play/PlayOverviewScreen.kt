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
import androidx.compose.ui.draw.drawBehind
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
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = LumenColors.OnSurface,
            )
            Text(
                subtitle,
                modifier = if (artwork == PlayOverviewArtwork.ARENA) {
                    Modifier.fillMaxWidth(.72f)
                } else {
                    Modifier
                },
                style = LumenTypography.ModeSubtitle.copy(
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
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
    pressed: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = if (pressed) LumenColors.AccentBlueBright else LumenColors.AccentBlue
    val bright = LumenColors.AccentBlueBright
    val deep = LumenColors.AccentBlueSoft

    Canvas(modifier) {
        val unit = size.minDimension
        val glowAlpha = if (pressed) .46f else .34f
        val glowCenter = Offset(size.width * .46f, size.height * .51f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    bright.copy(alpha = glowAlpha),
                    accent.copy(alpha = .17f),
                    accent.copy(alpha = .045f),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = unit * .58f,
            ),
            center = glowCenter,
            radius = unit * .56f,
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
                    color = Color.Black.copy(alpha = .27f),
                    style = Stroke(width = 5.dp.toPx(), join = StrokeJoin.Round),
                )
                drawPath(
                    path = shield,
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFF79B3C4).copy(alpha = .96f),
                            bright.copy(alpha = .88f),
                            accent.copy(alpha = .77f),
                            Color(0xFF294A56).copy(alpha = .98f),
                        ),
                        start = Offset(size.width * .13f, size.height * .09f),
                        end = Offset(size.width * .80f, size.height * .91f),
                    ),
                )
                val facet = Path().apply {
                    moveTo(size.width * .13f, size.height * .23f)
                    lineTo(size.width * .37f, size.height * .09f)
                    lineTo(size.width * .49f, size.height * .12f)
                    lineTo(size.width * .37f, size.height * .44f)
                    lineTo(size.width * .15f, size.height * .52f)
                    close()
                }
                drawPath(
                    facet,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = .13f),
                            bright.copy(alpha = .07f),
                            Color.Transparent,
                        ),
                        start = Offset(size.width * .15f, size.height * .12f),
                        end = Offset(size.width * .47f, size.height * .52f),
                    ),
                )
                drawPath(
                    path = shield,
                    color = Color(0xFFD4F4FA).copy(alpha = .43f),
                    style = Stroke(width = 1.05.dp.toPx(), join = StrokeJoin.Round),
                )
                drawLine(
                    Color.White.copy(alpha = .24f),
                    Offset(size.width * .18f, size.height * .20f),
                    Offset(size.width * .40f, size.height * .09f),
                    .85.dp.toPx(),
                    StrokeCap.Round,
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
                drawPath(
                    rook,
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFF234651).copy(alpha = .96f),
                            Color(0xFF112B34).copy(alpha = .94f),
                            Color(0xFF0B2027).copy(alpha = .97f),
                        ),
                        start = Offset(size.width * .25f, size.height * .23f),
                        end = Offset(size.width * .61f, size.height * .75f),
                    ),
                )
                drawPath(
                    rook,
                    bright.copy(alpha = .19f),
                    style = Stroke(width = .8.dp.toPx(), join = StrokeJoin.Round),
                )

                val bolt = Path().apply {
                    moveTo(size.width * .60f, size.height * .12f)
                    lineTo(size.width * .35f, size.height * .48f)
                    lineTo(size.width * .52f, size.height * .48f)
                    lineTo(size.width * .36f, size.height * .87f)
                    lineTo(size.width * .79f, size.height * .37f)
                    lineTo(size.width * .58f, size.height * .37f)
                    close()
                }
                drawPath(
                    bolt,
                    bright.copy(alpha = .27f),
                    style = Stroke(width = 5.5.dp.toPx(), join = StrokeJoin.Round),
                )
                drawPath(
                    bolt,
                    Brush.linearGradient(
                        listOf(
                            Color.White,
                            Color(0xFFEAFBFF),
                            Color(0xFFC9F3FA),
                        ),
                        start = Offset(size.width * .58f, size.height * .12f),
                        end = Offset(size.width * .39f, size.height * .87f),
                    ),
                )
                drawPath(
                    bolt,
                    bright.copy(alpha = .58f),
                    style = Stroke(width = 1.15.dp.toPx(), join = StrokeJoin.Round),
                )
                drawLine(
                    Color.White.copy(alpha = .55f),
                    Offset(size.width * .58f, size.height * .19f),
                    Offset(size.width * .41f, size.height * .46f),
                    .75.dp.toPx(),
                    StrokeCap.Round,
                )

                drawLine(
                    bright.copy(alpha = .73f),
                    Offset(size.width * .01f, size.height * .31f),
                    Offset(size.width * .15f, size.height * .38f),
                    1.2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    bright.copy(alpha = .58f),
                    Offset(size.width * .75f, size.height * .77f),
                    Offset(size.width * .95f, size.height * .68f),
                    1.1.dp.toPx(),
                    StrokeCap.Round,
                )
                drawCircle(
                    bright.copy(alpha = .55f),
                    radius = 1.1.dp.toPx(),
                    center = Offset(size.width * .86f, size.height * .24f),
                )
            }

            PlayOverviewArtwork.ARENA -> {
                val globeCenter = Offset(size.width * .46f, size.height * .50f)
                val blade = Color(0xFFD7EAF0)
                val bladeShadow = Color(0xFF10242C)

                drawLine(
                    bladeShadow.copy(alpha = .72f),
                    Offset(size.width * .07f, size.height * .18f),
                    Offset(size.width * .88f, size.height * .84f),
                    5.4.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    blade.copy(alpha = .85f),
                    Offset(size.width * .07f, size.height * .18f),
                    Offset(size.width * .88f, size.height * .84f),
                    2.35.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    bladeShadow.copy(alpha = .72f),
                    Offset(size.width * .87f, size.height * .14f),
                    Offset(size.width * .08f, size.height * .83f),
                    5.0.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    blade.copy(alpha = .80f),
                    Offset(size.width * .87f, size.height * .14f),
                    Offset(size.width * .08f, size.height * .83f),
                    2.15.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    Color(0xFF9BC8D4).copy(alpha = .76f),
                    Offset(size.width * .10f, size.height * .24f),
                    Offset(size.width * .17f, size.height * .17f),
                    1.7.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    Color(0xFF9BC8D4).copy(alpha = .68f),
                    Offset(size.width * .81f, size.height * .19f),
                    Offset(size.width * .88f, size.height * .26f),
                    1.7.dp.toPx(),
                    StrokeCap.Round,
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFA3BDFF).copy(alpha = .96f),
                            Color(0xFF5479C7).copy(alpha = .97f),
                            Color(0xFF2A477B).copy(alpha = .99f),
                            deep.copy(alpha = .99f),
                        ),
                        center = Offset(size.width * .34f, size.height * .29f),
                        radius = unit * .51f,
                    ),
                    radius = unit * .39f,
                    center = globeCenter,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = .12f),
                            bright.copy(alpha = .05f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * .33f, size.height * .29f),
                        radius = unit * .31f,
                    ),
                    radius = unit * .34f,
                    center = globeCenter,
                )
                drawCircle(
                    color = Color(0xFFD8E4FF).copy(alpha = .42f),
                    radius = unit * .39f,
                    center = globeCenter,
                    style = Stroke(width = 1.0.dp.toPx()),
                )
                drawCircle(
                    color = Color(0xFF10263A).copy(alpha = .42f),
                    radius = unit * .315f,
                    center = globeCenter,
                    style = Stroke(width = .75.dp.toPx()),
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
                drawPath(
                    knight,
                    Color(0xFF0B1B24).copy(alpha = .33f),
                    style = Stroke(width = 3.7.dp.toPx(), join = StrokeJoin.Round),
                )
                drawPath(
                    knight,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White,
                            Color(0xFFEAF4F7),
                            Color(0xFFBFD4DC),
                        ),
                        start = Offset(size.width * .31f, size.height * .20f),
                        end = Offset(size.width * .65f, size.height * .77f),
                    ),
                )
                drawPath(
                    knight,
                    Color(0xFFFFFFFF).copy(alpha = .36f),
                    style = Stroke(width = .8.dp.toPx(), join = StrokeJoin.Round),
                )
                drawLine(
                    Color.White.copy(alpha = .42f),
                    Offset(size.width * .46f, size.height * .24f),
                    Offset(size.width * .61f, size.height * .33f),
                    .85.dp.toPx(),
                    StrokeCap.Round,
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
                drawCircle(
                    spark.copy(alpha = .64f),
                    radius = .95.dp.toPx(),
                    center = Offset(size.width * .72f, size.height * .12f),
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
        Spacer(Modifier.height(5.dp))
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
        QuickStartGlyph(glyph, Modifier.size(28.dp))
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

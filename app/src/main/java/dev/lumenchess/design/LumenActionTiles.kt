package dev.lumenchess.design

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Drawn action icons shared by the Live and Arena screens. Vector strokes only, never text glyphs. */
enum class LumenActionGlyph { PAUSE, PLAY, FLAG, CANCEL, FLIP, DRAW, MENU, NEW_GAME, REMATCH, STOP, CONTROL, BRANCH }

/** The recessed strip that holds a row of [LumenActionTile]s. */
@Composable
fun LumenActionStrip(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val stripShape = RoundedCornerShape(7.dp)
    Row(
        modifier
            .background(LumenColors.SurfaceRaised.copy(alpha = .91f), stripShape)
            .border(1.dp, LumenColors.Outline.copy(alpha = .70f), stripShape)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** One raised, pressable icon-and-label key. Pressing sinks it slightly; no springs or bounce. */
@Composable
fun RowScope.LumenActionTile(
    label: String,
    glyph: LumenActionGlyph,
    testTag: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .955f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "action-tile-scale-$label",
    )
    val offset by animateDpAsState(
        targetValue = if (pressed) 1.2.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "action-tile-offset-$label",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) .2.dp else 1.8.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "action-tile-shadow-$label",
    )
    val lowerEdge by animateDpAsState(
        targetValue = if (pressed) .4.dp else 2.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "action-tile-edge-$label",
    )
    val shape = RoundedCornerShape(5.dp)
    val tint = if (destructive) LumenColors.Destructive else LumenColors.OnSurfaceMuted
    val faceTop = if (destructive) {
        LumenColors.DestructiveSoft.copy(alpha = if (pressed) .36f else .24f)
    } else if (pressed) {
        LumenColors.SurfaceHighest.copy(alpha = .82f)
    } else {
        LumenColors.SurfaceHighest.copy(alpha = .66f)
    }
    val faceBottom = if (pressed) LumenColors.Surface.copy(alpha = .98f) else LumenColors.SurfaceRaised
    val lowerEdgeColor = if (destructive) {
        LumenColors.Destructive.copy(alpha = .23f)
    } else {
        LumenColors.OutlineStrong.copy(alpha = .72f)
    }
    val contentTint = if (pressed && !destructive) LumenColors.OnSurface else tint

    Box(
        Modifier.weight(1f).fillMaxSize().testTag(testTag)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset.toPx()
            }
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(LumenColors.Background)
            .drawBehind {
                drawRect(
                    color = lowerEdgeColor,
                    topLeft = Offset(0f, size.height - lowerEdge.toPx()),
                )
            }
            .padding(bottom = lowerEdge)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(faceTop, faceBottom)))
            .border(
                1.dp,
                if (destructive) LumenColors.Destructive.copy(alpha = .46f)
                else LumenColors.OutlineStrong.copy(alpha = if (pressed) .92f else .76f),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            LumenActionGlyphIcon(glyph, contentTint)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Medium,
                color = contentTint,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun LumenActionGlyphIcon(glyph: LumenActionGlyph, tint: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(w * x1, h * y1), Offset(w * x2, h * y2), stroke, StrokeCap.Round)
        when (glyph) {
            LumenActionGlyph.PAUSE -> {
                line(.36f, .24f, .36f, .76f)
                line(.64f, .24f, .64f, .76f)
            }
            LumenActionGlyph.PLAY -> drawPath(
                Path().apply {
                    moveTo(w * .36f, h * .24f)
                    lineTo(w * .73f, h * .50f)
                    lineTo(w * .36f, h * .76f)
                    close()
                },
                tint,
            )
            LumenActionGlyph.FLAG -> {
                line(.31f, .18f, .31f, .82f)
                drawPath(
                    Path().apply {
                        moveTo(w * .32f, h * .22f)
                        lineTo(w * .72f, h * .30f)
                        lineTo(w * .56f, h * .49f)
                        lineTo(w * .32f, h * .44f)
                        close()
                    },
                    tint,
                )
            }
            LumenActionGlyph.CANCEL -> {
                line(.27f, .27f, .73f, .73f)
                line(.73f, .27f, .27f, .73f)
            }
            LumenActionGlyph.FLIP -> {
                // Two opposed vertical arrows: the board turns over.
                line(.32f, .78f, .32f, .22f); line(.32f, .22f, .20f, .36f); line(.32f, .22f, .44f, .36f)
                line(.68f, .22f, .68f, .78f); line(.68f, .78f, .56f, .64f); line(.68f, .78f, .80f, .64f)
            }
            LumenActionGlyph.DRAW -> {
                // "=": the result of a drawn game.
                line(.24f, .38f, .76f, .38f)
                line(.24f, .62f, .76f, .62f)
            }
            LumenActionGlyph.MENU -> listOf(.25f, .5f, .75f).forEach { x ->
                drawCircle(tint, radius = stroke * .85f, center = Offset(w * x, h * .5f))
            }
            LumenActionGlyph.NEW_GAME -> {
                line(.24f, .5f, .76f, .5f)
                line(.5f, .24f, .5f, .76f)
            }
            LumenActionGlyph.REMATCH -> {
                // Opposed horizontal arrows: same opponent, colours swapped.
                line(.22f, .36f, .78f, .36f); line(.78f, .36f, .62f, .24f); line(.78f, .36f, .62f, .48f)
                line(.78f, .64f, .22f, .64f); line(.22f, .64f, .38f, .52f); line(.22f, .64f, .38f, .76f)
            }
            LumenActionGlyph.STOP -> drawRoundRect(
                tint,
                topLeft = Offset(w * .28f, h * .28f),
                size = Size(w * .44f, h * .44f),
                cornerRadius = CornerRadius(stroke, stroke),
                style = Stroke(stroke),
            )
            LumenActionGlyph.CONTROL -> {
                // A person: you take over the moves.
                drawCircle(tint, radius = w * .12f, center = Offset(w * .5f, h * .33f), style = Stroke(stroke))
                drawArc(
                    tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * .24f, h * .56f),
                    size = Size(w * .52f, h * .40f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            LumenActionGlyph.BRANCH -> {
                // A line that forks: a variation leaves the main game.
                line(.36f, .20f, .36f, .80f)
                drawArc(
                    tint,
                    startAngle = 90f,
                    sweepAngle = -90f,
                    useCenter = false,
                    topLeft = Offset(w * .36f - w * .28f, h * .30f),
                    size = Size(w * .56f, h * .36f),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawCircle(tint, radius = stroke * 1.1f, center = Offset(w * .64f, h * .30f))
                drawCircle(tint, radius = stroke * 1.1f, center = Offset(w * .36f, h * .80f))
            }
        }
    }
}

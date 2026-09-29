package dev.lumenchess.analysis.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.analysis.review.MoveClassification

/** Review colours: distinct hues, readable on both light and dark boards. */
internal val MoveClassification.color: Color
    get() = when (this) {
        MoveClassification.BRILLIANT -> Color(0xFF1FB5A4)
        MoveClassification.GREAT -> Color(0xFF5D8FC9)
        MoveClassification.BOOK -> Color(0xFFA7865F)
        MoveClassification.BEST -> Color(0xFF7FB24A)
        MoveClassification.EXCELLENT -> Color(0xFF8DBA5C)
        MoveClassification.GOOD -> Color(0xFF94AE86)
        MoveClassification.INACCURACY -> Color(0xFFE9B83A)
        MoveClassification.MISTAKE -> Color(0xFFEC8F45)
        MoveClassification.MISS -> Color(0xFFE56E62)
        MoveClassification.BLUNDER -> Color(0xFFD9443A)
    }

/** Short annotation symbol; Best and Book are drawn as glyphs instead. */
internal val MoveClassification.symbol: String
    get() = when (this) {
        MoveClassification.BRILLIANT -> "!!"
        MoveClassification.GREAT -> "!"
        MoveClassification.BOOK -> ""
        MoveClassification.BEST -> ""
        MoveClassification.EXCELLENT -> "✓"
        MoveClassification.GOOD -> "✓"
        MoveClassification.INACCURACY -> "?!"
        MoveClassification.MISTAKE -> "?"
        MoveClassification.MISS -> "✕"
        MoveClassification.BLUNDER -> "??"
    }

@Composable
internal fun ClassificationBadge(classification: MoveClassification, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(classification.color, CircleShape)
            .semantics { contentDescription = classification.label }
            .testTag("classification-badge-${classification.name}"),
        contentAlignment = Alignment.Center,
    ) {
        when (classification) {
            MoveClassification.BEST -> Canvas(Modifier.size(size * .62f)) { drawPath(starPath(this.size), Color.White) }
            MoveClassification.BOOK -> Canvas(Modifier.size(size * .62f)) {
                val w = this.size.width
                val h = this.size.height
                val stroke = Stroke(width = w * .11f, cap = StrokeCap.Round)
                val left = Path().apply { moveTo(w * .5f, h * .22f); lineTo(w * .08f, h * .12f); lineTo(w * .08f, h * .82f); lineTo(w * .5f, h * .92f) }
                val right = Path().apply { moveTo(w * .5f, h * .22f); lineTo(w * .92f, h * .12f); lineTo(w * .92f, h * .82f); lineTo(w * .5f, h * .92f) }
                drawPath(left, Color.White, style = stroke)
                drawPath(right, Color.White, style = stroke)
                drawLine(Color.White, Offset(w * .5f, h * .22f), Offset(w * .5f, h * .92f), strokeWidth = w * .09f)
            }
            else -> Text(
                classification.symbol,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * .5f).sp,
                lineHeight = (size.value * .55f).sp,
                maxLines = 1,
            )
        }
    }
}

private fun starPath(size: Size): Path {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = size.minDimension / 2f
    val inner = outer * .45f
    return Path().apply {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outer else inner
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
            val y = cy + (radius * kotlin.math.sin(angle)).toFloat() + outer * .06f
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * White's expected points across the game (0..1), filled white above black like an evaluation
 * bar laid on its side. Error moves are marked; tapping selects the nearest move.
 */
@Composable
internal fun EvaluationGraph(
    graph: List<Double>,
    markers: List<Pair<Int, MoveClassification>>,
    selectedPly: Int?,
    onSelectPly: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
) {
    val dark = Color(0xFF2A2F31)
    val light = Color(0xFFE9E5DA)
    val midline = Color(0x5580878A)
    val cursor = Color(0xFF69B8D2)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag("review-evaluation-graph")
            .semantics { contentDescription = "Evaluation graph. Tap to jump to a move." }
            .pointerInput(graph.size) {
                detectTapGestures { offset ->
                    if (graph.size < 2) return@detectTapGestures
                    val step = size.width.toFloat() / (graph.size - 1)
                    val index = Math.round(offset.x / step).coerceIn(0, graph.size - 1)
                    onSelectPly(index - 1)
                }
            },
    ) {
        drawRect(dark)
        if (graph.size < 2) return@Canvas
        val step = size.width / (graph.size - 1)
        val area = Path().apply {
            moveTo(0f, size.height)
            graph.forEachIndexed { i, points -> lineTo(i * step, size.height * (1f - points.toFloat())) }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(area, light)
        drawLine(midline, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
        for ((ply, classification) in markers) {
            val x = (ply + 1) * step
            val y = size.height * (1f - graph.getOrElse(ply + 1) { .5 }.toFloat())
            drawCircle(classification.color, radius = 3.2.dp.toPx(), center = Offset(x, y))
        }
        selectedPly?.let { ply ->
            val x = (ply + 1).coerceIn(0, graph.lastIndex) * step
            drawLine(cursor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.6.dp.toPx())
        }
    }
}

/** Vertical evaluation bar (White from the bottom unless flipped). */
@Composable
internal fun VerticalEvaluationBar(whitePoints: Double?, label: String?, flipped: Boolean, modifier: Modifier = Modifier) {
    val white = (whitePoints ?: .5).toFloat().coerceIn(.02f, .98f)
    val dark = Color(0xFF2A2F31)
    val light = Color(0xFFE9E5DA)
    Box(
        modifier
            .background(dark)
            .testTag("analysis-evaluation-bar")
            .semantics { contentDescription = "Evaluation ${label ?: "unknown"}" },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val whiteHeight = size.height * white
            if (!flipped) drawRect(light, topLeft = Offset(0f, size.height - whiteHeight), size = Size(size.width, whiteHeight))
            else drawRect(light, topLeft = Offset.Zero, size = Size(size.width, whiteHeight))
        }
        if (label != null) {
            val whiteAhead = white >= .5f
            // The number sits on the side that is ahead, in that side's contrasting colour.
            val onBottom = whiteAhead != flipped
            Text(
                label,
                modifier = Modifier.align(if (onBottom) Alignment.BottomCenter else Alignment.TopCenter),
                color = if (whiteAhead) dark else light,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, lineHeight = 9.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

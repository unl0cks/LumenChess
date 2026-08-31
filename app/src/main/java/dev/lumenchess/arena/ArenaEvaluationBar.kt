package dev.lumenchess.arena

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.design.LumenColors
import java.util.Locale
import kotlin.math.abs

internal fun ArenaEvaluation.whiteFraction(): Float = when {
    whiteMateIn != null -> if (whiteMateIn > 0) .94f else .06f
    whiteCentipawns != null -> (.5f + whiteCentipawns / 1600f).coerceIn(.08f, .92f)
    else -> .5f
}

internal fun ArenaEvaluation.label(): String = when {
    whiteMateIn != null -> if (whiteMateIn > 0) "+M$whiteMateIn" else "-M${abs(whiteMateIn)}"
    whiteCentipawns != null -> String.format(Locale.US, "%+.2f", whiteCentipawns / 100f)
    else -> "0.00"
}

@Composable
internal fun ArenaEvaluationBar(
    evaluation: ArenaEvaluation?,
    modifier: Modifier = Modifier,
) {
    val resolved = evaluation ?: ArenaEvaluation()
    val whiteWeight = resolved.whiteFraction()
    val blackWeight = 1f - whiteWeight
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(shape)
            .background(LumenColors.SurfaceHighest)
            .testTag("arena-evaluation-bar"),
    ) {
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Box(Modifier.weight(blackWeight).fillMaxHeight().background(Color(0xFF24292B)))
            Box(Modifier.weight(whiteWeight).fillMaxHeight().background(Color(0xFFE7E3D7)))
        }
        Text(
            text = resolved.label(),
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 5.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (whiteWeight >= .56f) Color(0xFF1B2022) else LumenColors.OnSurface,
        )
        resolved.depth?.let { depth ->
            Text(
                text = "d$depth",
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                color = if (whiteWeight > .78f) Color(0xFF343A3C) else LumenColors.OnSurfaceMuted,
            )
        }
    }
}

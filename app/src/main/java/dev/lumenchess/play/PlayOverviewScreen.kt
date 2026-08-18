package dev.lumenchess.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenEngineBadge
import dev.lumenchess.design.LumenListRow
import dev.lumenchess.design.LumenPanel
import dev.lumenchess.design.LumenTopBar
import dev.lumenchess.engine.api.EngineStrengthTarget

private enum class PlayOverviewGlyph { PLAY, ARENA, CLOCK }

@Composable
internal fun ReferencePlayOverviewScreen(
    ui: PlayUiState,
    onPlayVsEngine: () -> Unit,
    onArenaPreview: () -> Unit,
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
    val quickPrimary = "$minutes min${if (increment > 0) " + $increment sec" else ""} · $timeLabel"
    val quickSecondary = buildString {
        append(setup.engine.displayName)
        if (elo != null) append(" · $elo Elo")
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .padding(horizontal = 13.dp, vertical = 3.dp)
            .testTag("p5-play-overview"),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        LumenTopBar("Play")

        LumenListRow(
            title = "Play vs Engine",
            subtitle = "Challenge a chess engine",
            modifier = Modifier.testTag("play-overview-vs-engine"),
            onClick = onPlayVsEngine,
            leading = { OverviewGlyph(PlayOverviewGlyph.PLAY) },
        )
        LumenListRow(
            title = "Engine Arena",
            subtitle = "Watch engines battle each other",
            modifier = Modifier.testTag("play-overview-arena"),
            onClick = onArenaPreview,
            leading = { OverviewGlyph(PlayOverviewGlyph.ARENA) },
        )

        Text(
            "Quick Start",
            style = MaterialTheme.typography.labelLarge,
            color = LumenColors.OnSurface,
            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
        )
        LumenPanel(Modifier.fillMaxWidth().testTag("play-overview-quick-start")) {
            Row(
                Modifier.fillMaxWidth().height(43.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier.size(29.dp).background(LumenColors.SurfaceHighest, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) { OverviewGlyph(PlayOverviewGlyph.CLOCK) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(quickPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(quickSecondary, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted)
                }
                LumenEngineBadge(setup.engine.displayName)
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
        LumenPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Engine Arena", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Arena setup will arrive with its engine-battle runtime. The Play shell is already reserved for it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun OverviewGlyph(kind: PlayOverviewGlyph) {
    val tint = LumenColors.AccentBlueBright
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.55.dp.toPx()
        when (kind) {
            PlayOverviewGlyph.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width * .30f, size.height * .18f)
                    lineTo(size.width * .80f, size.height * .50f)
                    lineTo(size.width * .30f, size.height * .82f)
                    close()
                }
                drawPath(path, tint)
            }
            PlayOverviewGlyph.ARENA -> {
                drawLine(tint, Offset(size.width * .20f, size.height * .18f), Offset(size.width * .76f, size.height * .80f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .80f, size.height * .18f), Offset(size.width * .24f, size.height * .80f), stroke, StrokeCap.Round)
                drawCircle(tint, stroke * .9f, Offset(size.width * .20f, size.height * .18f))
                drawCircle(tint, stroke * .9f, Offset(size.width * .80f, size.height * .18f))
            }
            PlayOverviewGlyph.CLOCK -> {
                drawCircle(tint, size.minDimension * .38f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                drawLine(tint, center, Offset(center.x, size.height * .28f), stroke, StrokeCap.Round)
                drawLine(tint, center, Offset(size.width * .66f, size.height * .58f), stroke, StrokeCap.Round)
            }
        }
    }
}

package dev.lumenchess.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSectionLabel
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.lumenP5IdentityPalette

@Composable
fun InsightsRoute(
    viewModel: InsightsViewModel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState
    // Re-read whenever the tab opens so a game that just finished is already counted.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    InsightsScreen(ui, onPlay, viewModel::refresh, modifier)
}

@Composable
internal fun InsightsScreen(
    ui: InsightsUiState,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LumenDerivativePage(
        modifier,
        testTag = "insights-root",
        scrollable = true,
        verticalPadding = 12,
        spacing = 10,
    ) {
        Column {
            Text("Insights", style = MaterialTheme.typography.headlineMedium, color = LumenColors.OnSurface)
            Text(
                "Your results against the engines",
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.OnSurfaceMuted,
            )
        }

        val summary = ui.summary
        when {
            summary != null && !summary.isEmpty -> InsightsContent(summary)
            ui.error != null -> InsightsNotice(
                title = "Insights unavailable",
                body = ui.error,
                action = "Try again",
                onAction = onRetry,
                testTag = "insights-error",
            )
            ui.loading -> Text(
                "Reading your games…",
                modifier = Modifier.testTag("insights-loading"),
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.OnSurfaceMuted,
            )
            else -> InsightsNotice(
                title = "No finished games yet",
                body = "Play a game against Stockfish or Reckless and your record, streaks and how your games end will appear here.",
                action = "Start a game",
                onAction = onPlay,
                testTag = "insights-empty",
            )
        }
    }
}

@Composable
private fun InsightsNotice(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    testTag: String,
) {
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.PREVIEW_PANEL,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
            LumenDerivativeAction(action, onAction, Modifier.fillMaxWidth(), testTag = "$testTag-action")
        }
    }
}

@Composable
private fun InsightsContent(summary: InsightsSummary) {
    ScoreCard(summary)
    RecentForm(summary)

    LumenDerivativeSectionLabel("By colour")
    InsightRecordRow("As White", summary.asWhite)
    InsightRecordRow("As Black", summary.asBlack)

    if (summary.byTimeControl.isNotEmpty()) {
        LumenDerivativeSectionLabel("By time control")
        summary.byTimeControl.forEach { InsightRecordRow(it.label, it.record) }
    }
    if (summary.byStrength.isNotEmpty()) {
        LumenDerivativeSectionLabel("By opponent strength")
        summary.byStrength.forEach { InsightRecordRow(it.label, it.record) }
    }

    val endings = endingRows(summary.endings)
    if (endings.isNotEmpty()) {
        LumenDerivativeSectionLabel("How your games end")
        endings.forEach { (label, count) -> InsightValueRow(label, count.toString()) }
    }
    if (summary.chess960Games > 0) {
        InsightValueRow("Chess960 games", summary.chess960Games.toString())
    }
}

@Composable
private fun ScoreCard(summary: InsightsSummary) {
    val record = summary.overall
    val score = record.scorePercent ?: 0
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.PREVIEW_PANEL,
        modifier = Modifier.fillMaxWidth().testTag("insights-score-card"),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "$score%",
                    modifier = Modifier.testTag("insights-score"),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
                    color = LumenColors.OnSurface,
                )
                Column(Modifier.padding(bottom = 5.dp)) {
                    Text("Score", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
                    Text(
                        "${record.games} ${if (record.games == 1) "game" else "games"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LumenColors.OnSurfaceMuted,
                    )
                }
            }
            RecordBar(record)
            Text(
                "${record.wins} won · ${record.draws} drawn · ${record.losses} lost",
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.OnSurfaceMuted,
            )
        }
    }
}

/** Solid, adjacent segments with exact edges: no gradients, so no internal seams. */
@Composable
private fun RecordBar(record: InsightRecord) {
    val palette = lumenP5IdentityPalette()
    val win = palette.cyan
    val draw = palette.muted.copy(alpha = .55f)
    val loss = LumenColors.Destructive.copy(alpha = .85f)
    Canvas(
        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            .background(palette.insetSurface)
            .semantics { contentDescription = "${record.wins} wins, ${record.draws} draws, ${record.losses} losses" },
    ) {
        val total = record.games.coerceAtLeast(1).toFloat()
        var x = 0f
        fun segment(count: Int, color: Color) {
            if (count == 0) return
            val width = size.width * count / total
            drawRect(color, topLeft = Offset(x, 0f), size = Size(width, size.height))
            x += width
        }
        segment(record.wins, win)
        segment(record.draws, draw)
        segment(record.losses, loss)
    }
}

@Composable
private fun RecentForm(summary: InsightsSummary) {
    if (summary.recentForm.isEmpty()) return
    val palette = lumenP5IdentityPalette()
    val spoken = summary.recentForm.joinToString(", ") { it.name.lowercase() }
    LumenDerivativeSectionLabel("Recent form")
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.NEUTRAL_ROW,
        modifier = Modifier.fillMaxWidth().testTag("insights-recent-form"),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.semantics { contentDescription = "Recent results, newest first: $spoken" },
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                summary.recentForm.forEach { outcome ->
                    Box(
                        Modifier.size(14.dp).clip(CircleShape).background(
                            when (outcome) {
                                InsightOutcome.WIN -> palette.cyan
                                InsightOutcome.DRAW -> palette.muted.copy(alpha = .55f)
                                InsightOutcome.LOSS -> LumenColors.Destructive.copy(alpha = .85f)
                            },
                        ),
                    )
                }
            }
            summary.streak?.let { streak ->
                if (streak.length > 1) {
                    Text(
                        streakLabel(streak),
                        style = MaterialTheme.typography.bodySmall,
                        color = LumenColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightRecordRow(label: String, record: InsightRecord) {
    val score = record.scorePercent
    InsightValueRow(
        label = label,
        value = "${record.wins}–${record.draws}–${record.losses}" + if (score != null) " · $score%" else "",
    )
}

@Composable
private fun InsightValueRow(label: String, value: String) {
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.NEUTRAL_ROW,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = LumenColors.OnSurface)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = LumenColors.OnSurfaceMuted,
            )
        }
    }
}

internal fun streakLabel(streak: InsightStreak): String = when (streak.outcome) {
    InsightOutcome.WIN -> "${streak.length} wins in a row"
    InsightOutcome.LOSS -> "${streak.length} losses in a row"
    InsightOutcome.DRAW -> "${streak.length} draws in a row"
}

/** Only endings that actually happened; zero rows are noise. */
internal fun endingRows(endings: InsightEndings): List<Pair<String, Int>> = listOf(
    "Won by checkmate" to endings.checkmatesGiven,
    "Lost by checkmate" to endings.checkmatesTaken,
    "Won on time" to endings.wonOnTime,
    "Lost on time" to endings.lostOnTime,
    "Opponent resigned" to endings.opponentResigned,
    "You resigned" to endings.youResigned,
    "Drawn" to endings.drawn,
).filter { it.second > 0 }

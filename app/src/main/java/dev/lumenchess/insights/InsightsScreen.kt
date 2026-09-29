package dev.lumenchess.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.lumenchess.analysis.insights.GameKind
import dev.lumenchess.analysis.insights.GameOrigin
import dev.lumenchess.analysis.insights.InsightsFilter
import dev.lumenchess.analysis.insights.Outcome
import dev.lumenchess.analysis.insights.PlayerGame
import dev.lumenchess.analysis.insights.PoolKey
import dev.lumenchess.analysis.insights.Record
import dev.lumenchess.analysis.rating.TimeClass
import dev.lumenchess.analysis.review.GamePhase
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.analysis.ui.AnalysisRequest
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSectionLabel
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.lumenP5IdentityPalette
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun InsightsRoute(
    viewModel: InsightsViewModel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAnalysis: (AnalysisRequest) -> Unit = {},
) {
    val ui by viewModel.uiState
    // Re-read whenever the tab opens so a game that just finished is already counted.
    LaunchedEffect(viewModel) { viewModel.refresh() }
    InsightsScreen(ui, viewModel, onPlay, onOpenAnalysis, modifier)
}

@Composable
internal fun InsightsScreen(
    ui: InsightsUiState,
    vm: InsightsViewModel,
    onPlay: () -> Unit,
    onOpenAnalysis: (AnalysisRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    LumenDerivativePage(modifier, testTag = "insights-root", scrollable = true, verticalPadding = 12, spacing = 10) {
        Column {
            Text("Insights", style = MaterialTheme.typography.headlineMedium, color = LumenColors.OnSurface)
            Text(
                "Your games, move quality and strength",
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.OnSurfaceMuted,
            )
        }
        val view = ui.view
        when {
            ui.error != null -> InsightsNotice("Insights unavailable", ui.error, "Try again", { vm.refresh(force = true) }, "insights-error")
            ui.loading && view == null -> Text(
                "Reading your games…",
                modifier = Modifier.testTag("insights-loading"),
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.OnSurfaceMuted,
            )
            ui.isEmpty || view == null -> InsightsNotice(
                title = "No finished games yet",
                body = "Play a game against Stockfish or Reckless, or import your Chess.com / Lichess games (Games › Import, or link your accounts in Settings). Results, move quality and ratings appear here.",
                action = "Start a game",
                onAction = onPlay,
                testTag = "insights-empty",
            )
            else -> {
                FilterSummary(ui.filter, ui.defaultedToAll) { filtersOpen = true }
                OverviewCard(view.overview, view.games.size)
                RatingsCard(ui)
                StrengthTrend(view)
                MoveQualityCard(view, vm)
                PhasesCard(view.phases)
                OpeningsCard(view, vm)
                SegmentsCard("By time control", view.timeClasses)
                SegmentsCard("By colour", view.colors)
                TrendCard(view)
                CompareCard(view)
            }
        }
    }
    if (filtersOpen) FiltersDialog(ui.filter, onApply = { vm.setFilter(it); filtersOpen = false }, onDismiss = { filtersOpen = false })
    ui.drill?.let { drill ->
        DrillDialog(drill, onClose = vm::closeDrill) { game ->
            vm.closeDrill()
            onOpenAnalysis(AnalysisRequest.LibraryGame(game.gameId, review = true))
        }
    }
}

@Composable
private fun InsightsNotice(title: String, body: String, action: String, onAction: () -> Unit, testTag: String) {
    LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL, Modifier.fillMaxWidth().testTag(testTag)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
            LumenDerivativeAction(action, onAction, Modifier.fillMaxWidth(), testTag = "$testTag-action")
        }
    }
}

private fun InsightsFilter.describe(): String = listOf(
    kind?.label?.let { "$it games" } ?: "All games",
    timeClass?.label,
    variant?.let { if (it == Variant.CHESS960) "Chess960" else "Standard" },
    origin?.label,
    days?.let { if (it >= 365) "last year" else "last $it days" },
    rated?.let { if (it) "rated" else "unrated" },
).filterNotNull().joinToString(" · ")

@Composable
private fun FilterSummary(filter: InsightsFilter, defaultedToAll: Boolean, onOpen: () -> Unit) {
    LumenDerivativeSurface(
        DerivativeSurfaceRole.NEUTRAL_ROW,
        Modifier.fillMaxWidth().heightIn(min = 48.dp),
        onClick = onOpen,
        testTag = "insights-filters",
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(filter.describe(), style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
                if (defaultedToAll) {
                    Text("Showing all games: not enough games against people yet.", style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
                }
            }
            Text("Filters", style = MaterialTheme.typography.labelLarge, color = LumenColors.AccentBlueBright)
        }
    }
}

@Composable
private fun OverviewCard(overview: dev.lumenchess.analysis.insights.Overview, games: Int) {
    val record = overview.record
    LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL, Modifier.fillMaxWidth().testTag("insights-score-card")) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${record.scorePercent ?: 0}%",
                    modifier = Modifier.testTag("insights-score"),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold),
                    color = LumenColors.OnSurface,
                )
                Column(Modifier.padding(bottom = 5.dp)) {
                    Text("Score", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
                    Text("$games ${if (games == 1) "game" else "games"}", style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
                }
            }
            RecordBar(record)
            Text("${record.wins} won · ${record.draws} drawn · ${record.losses} lost", style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Accuracy", overview.averageAccuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "–", Modifier.weight(1f))
                Stat("Est. strength", overview.averageGameRating?.toString() ?: "–", Modifier.weight(1f))
                Stat("Opponents", overview.averageOpponentRating?.toString() ?: "–", Modifier.weight(1f))
            }
            if (overview.reviewed < games) {
                Text(
                    "Accuracy and strength come from Game Review: ${overview.reviewed} of $games games reviewed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted, maxLines = 1)
    }
}

/** Solid, adjacent segments with exact edges: no gradients, so no internal seams. */
@Composable
private fun RecordBar(record: Record) {
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
private fun RatingsCard(ui: InsightsUiState) {
    val ratings = ui.ratings ?: return
    LumenDerivativeSectionLabel("Ratings")
    LumenDerivativeSurface(DerivativeSurfaceRole.NEUTRAL_ROW, Modifier.fillMaxWidth().testTag("insights-ratings")) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("", Modifier.weight(1f))
                Text("Rated · ${ratings.system.label}", Modifier.weight(1.4f), style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)
                Text("Performance", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)
            }
            for (variant in Variant.entries) {
                for (timeClass in TimeClass.entries) {
                    val key = PoolKey(variant, timeClass)
                    val rated = ratings.rated(key)
                    val performance = ratings.performance[key]
                    if (rated == null && performance == null && variant == Variant.CHESS960) continue
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${if (variant == Variant.CHESS960) "960 " else ""}${timeClass.label}",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurface,
                        )
                        Text(
                            rated?.let { point ->
                                val rd = point.state.deviation?.takeIf { ratings.system.name.startsWith("GLICKO") }
                                "${point.state.rounded}${rd?.let { " ±${it.toInt()}" } ?: ""} (${point.state.games})"
                            } ?: "–",
                            Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurface,
                        )
                        Text(
                            performance?.let { "${it.rating}${if (it.provisional) "?" else ""}" } ?: "–",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurface,
                        )
                    }
                }
            }
            listOfNotNull(
                ui.settings.chessComRatings?.let { r -> "Chess.com: " + listOfNotNull(r.bullet?.let { "Bullet $it" }, r.blitz?.let { "Blitz $it" }, r.rapid?.let { "Rapid $it" }).joinToString(" · ") },
                ui.settings.lichessRatings?.let { r -> "Lichess: " + listOfNotNull(r.bullet?.let { "Bullet $it" }, r.blitz?.let { "Blitz $it" }, r.rapid?.let { "Rapid $it" }).joinToString(" · ") },
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted) }
            Text(
                "Rated games against the engines move your local rating. Performance is estimated from Game Review (\"?\" = fewer than 5 reviewed games).",
                style = MaterialTheme.typography.bodySmall,
                color = LumenColors.OnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun StrengthTrend(view: InsightsView) {
    val points = view.strength
    if (points.size < 2) return
    LumenDerivativeSectionLabel("Strength trend")
    LumenDerivativeSurface(DerivativeSurfaceRole.NEUTRAL_ROW, Modifier.fillMaxWidth().testTag("insights-strength-trend")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val line = LumenColors.AccentBlueBright
            val grid = LumenColors.Outline
            val min = points.minOf { it.value }
            val max = points.maxOf { it.value }
            Canvas(
                Modifier.fillMaxWidth().height(90.dp).semantics {
                    contentDescription = "Single-game strength from ${min.toInt()} to ${max.toInt()} over ${points.size} reviewed games"
                },
            ) {
                val span = (max - min).coerceAtLeast(100.0)
                val low = (min + max) / 2 - span / 2
                fun y(value: Double) = size.height * (1f - ((value - low) / span).toFloat())
                drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
                val step = size.width / (points.size - 1)
                val path = Path()
                // A 5-game moving average keeps the line readable; single games swing a lot.
                points.indices.forEach { i ->
                    val window = points.subList((i - 4).coerceAtLeast(0), i + 1)
                    val value = window.sumOf { it.value } / window.size
                    val x = i * step
                    if (i == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
                    drawCircle(line.copy(alpha = .35f), 2.dp.toPx(), Offset(x, y(points[i].value)))
                }
                drawPath(path, line, style = Stroke(2.dp.toPx()))
            }
            Text("${min.toInt()} – ${max.toInt()} · line shows the 5-game average", style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun MoveQualityCard(view: InsightsView, vm: InsightsViewModel) {
    val quality = view.moveQuality
    if (quality.reviewedGames == 0) return
    LumenDerivativeSectionLabel("Move quality (per game)")
    listOf(
        MoveClassification.INACCURACY to quality.inaccuracies,
        MoveClassification.MISTAKE to quality.mistakes,
        MoveClassification.MISS to quality.misses,
        MoveClassification.BLUNDER to quality.blunders,
        MoveClassification.GREAT to quality.greats,
        MoveClassification.BRILLIANT to quality.brilliants,
    ).forEach { (classification, perGame) ->
        ValueRow(
            label = "${classification.label}s",
            value = String.format(Locale.US, "%.1f", perGame),
            onClick = { vm.drillErrors(classification) },
            testTag = "insights-quality-${classification.name}",
        )
    }
    Text("From ${quality.reviewedGames} reviewed games. Tap a row to see those games.", style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
}

@Composable
private fun PhasesCard(phases: Map<GamePhase, Double?>) {
    if (phases.values.all { it == null }) return
    LumenDerivativeSectionLabel("Game phases (accuracy)")
    GamePhase.entries.forEach { phase ->
        phases[phase]?.let { accuracy ->
            ValueRow(phase.name.lowercase().replaceFirstChar { it.uppercase() }, String.format(Locale.US, "%.1f", accuracy))
        }
    }
}

@Composable
private fun OpeningsCard(view: InsightsView, vm: InsightsViewModel) {
    if (view.openings.isEmpty()) return
    LumenDerivativeSectionLabel("Openings")
    view.openings.take(6).forEach { stat ->
        ValueRow(
            label = "${stat.name} (${if (stat.color == dev.lumenchess.core.chess.Color.WHITE) "White" else "Black"})",
            value = "${stat.record.games} · ${stat.record.scorePercent ?: 0}%",
            onClick = { vm.drillOpening(stat) },
        )
    }
    view.best?.let { ValueRow("Best: ${it.name}", "${it.record.scorePercent ?: 0}% in ${it.record.games}", onClick = { vm.drillOpening(it) }) }
    view.worst?.let { ValueRow("Worst: ${it.name}", "${it.record.scorePercent ?: 0}% in ${it.record.games}", onClick = { vm.drillOpening(it) }) }
}

@Composable
private fun SegmentsCard(title: String, segments: List<dev.lumenchess.analysis.insights.Segment>) {
    val shown = segments.filter { it.games > 0 }
    if (shown.isEmpty()) return
    LumenDerivativeSectionLabel(title)
    shown.forEach { segment ->
        val record = segment.record
        ValueRow(
            segment.label,
            "${record.wins}–${record.draws}–${record.losses}" + (record.scorePercent?.let { " · $it%" } ?: "") +
                (segment.averageAccuracy?.let { " · acc ${String.format(Locale.US, "%.0f", it)}" } ?: ""),
        )
    }
}

@Composable
private fun TrendCard(view: InsightsView) {
    val trend = view.trend
    if (listOf(trend.accuracyChange, trend.gameRatingChange, trend.errorsPerGameChange, trend.scoreChange).all { it == null }) return
    LumenDerivativeSectionLabel("Recent trend (last 10 vs previous 10)")
    trend.accuracyChange?.let { ValueRow("Accuracy", signed(it, "%.1f") + if (it >= 0) " ▲" else " ▼") }
    trend.gameRatingChange?.let { ValueRow("Strength", (if (it >= 0) "+$it ▲" else "$it ▼")) }
    trend.errorsPerGameChange?.let { ValueRow("Errors per game", signed(it, "%.1f") + if (it <= 0) " (fewer) ▲" else " (more) ▼") }
    trend.scoreChange?.let { ValueRow("Score", (if (it >= 0) "+$it%" else "$it%")) }
}

@Composable
private fun CompareCard(view: InsightsView) {
    val (current, previous) = view.compare
    if (current.games == 0 && previous.games == 0) return
    LumenDerivativeSectionLabel("Last 30 days vs previous 30")
    ValueRow("Games", "${current.games} vs ${previous.games}")
    ValueRow("Score", "${current.record.scorePercent ?: "–"}% vs ${previous.record.scorePercent ?: "–"}%")
    if (current.averageAccuracy != null || previous.averageAccuracy != null) {
        ValueRow("Accuracy", "${current.averageAccuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "–"} vs ${previous.averageAccuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "–"}")
    }
}

private fun signed(value: Double, format: String): String =
    (if (value >= 0) "+" else "-") + String.format(Locale.US, format, abs(value))

@Composable
private fun ValueRow(label: String, value: String, onClick: (() -> Unit)? = null, testTag: String? = null) {
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.NEUTRAL_ROW,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        testTag = testTag,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = LumenColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = LumenColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun FiltersDialog(initial: InsightsFilter, onApply: (InsightsFilter) -> Unit, onDismiss: () -> Unit) {
    var filter by androidx.compose.runtime.remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL, Modifier.testTag("insights-filters-dialog")) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filters", style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                ChoiceRow("Game type", listOf(null to "All") + GameKind.entries.map { it to it.label }, filter.kind) { filter = filter.copy(kind = it) }
                ChoiceRow("Time", listOf(null to "All") + TimeClass.entries.map { it to it.label }, filter.timeClass) { filter = filter.copy(timeClass = it) }
                ChoiceRow("Variant", listOf(null to "All", Variant.STANDARD to "Standard", Variant.CHESS960 to "960"), filter.variant) { filter = filter.copy(variant = it) }
                ChoiceRow("Source", listOf(null to "All") + GameOrigin.entries.map { it to it.label }, filter.origin) { filter = filter.copy(origin = it) }
                ChoiceRow("Period", listOf(7 to "7d", 30 to "30d", 90 to "90d", 365 to "1y", null to "All"), filter.days) { filter = filter.copy(days = it) }
                ChoiceRow("Rated", listOf(null to "All", true to "Rated", false to "Unrated"), filter.rated) { filter = filter.copy(rated = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LumenDerivativeAction("Reset", { filter = InsightsFilter() }, Modifier.weight(1f), testTag = "insights-filters-reset")
                    LumenDerivativeAction("Apply", { onApply(filter) }, Modifier.weight(1f), testTag = "insights-filters-apply")
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { (value, label) ->
            LumenDerivativeSurface(
                if (value == selected) DerivativeSurfaceRole.SELECTED_FACE else DerivativeSurfaceRole.NEUTRAL_ROW,
                Modifier.heightIn(min = 40.dp),
                onClick = { onSelect(value) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface) }
        }
    }
}

@Composable
private fun DrillDialog(drill: InsightsDrill, onClose: () -> Unit, onOpen: (PlayerGame) -> Unit) {
    Dialog(onDismissRequest = onClose) {
        LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL, Modifier.testTag("insights-drill")) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${drill.title} · ${drill.games.size} games", style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                if (drill.games.isEmpty()) Text("No games.", color = LumenColors.OnSurfaceMuted)
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(drill.games, key = { it.gameId }) { game ->
                        LumenDerivativeSurface(
                            DerivativeSurfaceRole.NEUTRAL_ROW,
                            Modifier.fillMaxWidth(),
                            onClick = { onOpen(game) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Column {
                                Text(
                                    listOfNotNull(
                                        when (game.outcome) { Outcome.WIN -> "Won"; Outcome.DRAW -> "Drew"; Outcome.LOSS -> "Lost"; null -> null },
                                        game.opponentName?.let { "vs $it" },
                                        game.opponentRating?.let { "($it)" },
                                    ).joinToString(" "),
                                    style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurface,
                                )
                                Text(
                                    listOfNotNull(
                                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(game.playedAtEpochMillis)),
                                        game.timeClass.label,
                                        game.openingName,
                                        game.review?.accuracy?.let { "acc ${String.format(Locale.US, "%.0f", it)}" },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                LumenDerivativeAction("Close", onClose, Modifier.fillMaxWidth(), testTag = "insights-drill-close")
            }
        }
    }
}

package dev.lumenchess.analysis.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.lumenchess.analysis.engine.EngineAnalysisClient
import dev.lumenchess.analysis.review.GamePhase
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.analysis.review.MoveClassifier
import dev.lumenchess.analysis.review.MoveExplanations
import dev.lumenchess.analysis.review.ReviewPreset
import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Rules
import dev.lumenchess.core.chess.San
import dev.lumenchess.core.chess.Termination
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.ReviewState
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativeSegment
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.review.ReviewRunPhase
import java.util.Locale

// --- Review --------------------------------------------------------------------------------------

@Composable
internal fun ReviewPane(ui: AnalysisUiState, vm: AnalysisViewModel) {
    val review = ui.review
    val progress = ui.reviewProgress
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("analysis-review-pane"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            progress != null && progress.active -> ReviewProgressCard(progress.phase, progress.fraction, progress.evaluated, progress.total, vm::cancelReview)
            review == null -> StartReviewCard(ui, vm)
            else -> {
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReviewMode.entries.forEach { mode ->
                        LumenDerivativeSegment(mode.label, ui.reviewMode == mode, { vm.setReviewMode(mode) }, testTag = "review-mode-${mode.name}")
                    }
                }
                EvaluationGraph(
                    graph = review.summary.graph,
                    markers = review.moves.filter { it.classification.isError && it.classification != MoveClassification.INACCURACY || it.classification.isHighlight }
                        .map { it.ply to it.classification },
                    selectedPly = ui.mainlinePly ?: if (ui.nodeId == ui.tree.rootId) -1 else null,
                    onSelectPly = vm::selectMainlinePly,
                )
                when (ui.reviewMode) {
                    ReviewMode.GUIDED -> CoachCard(ui, vm, keyMoments = false)
                    ReviewMode.KEY_MOMENTS -> CoachCard(ui, vm, keyMoments = true)
                    ReviewMode.FULL -> ReviewSummaryTable(ui)
                }
                if (progress?.phase == ReviewRunPhase.FAILED) AnalysisNote(progress.message ?: "The last review attempt failed.")
            }
        }
    }
}

@Composable
private fun ReviewProgressCard(phase: ReviewRunPhase, fraction: Float, evaluated: Int, total: Int, onCancel: () -> Unit) {
    LumenDerivativeSurface(DerivativeSurfaceRole.RECESSED_TRAY, Modifier.fillMaxWidth().testTag("review-progress")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (phase) {
                    ReviewRunPhase.QUEUED -> "Waiting to review…"
                    ReviewRunPhase.RECHECKING -> "Double-checking critical moves…"
                    else -> "Reviewing the game…"
                },
                color = LumenColors.OnSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Box(Modifier.fillMaxWidth().height(6.dp).background(LumenColors.SurfaceHighest, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(.02f, 1f)).fillMaxHeight().background(LumenColors.AccentBlueBright, RoundedCornerShape(3.dp)))
            }
            if (total > 0) AnalysisNote("$evaluated of $total positions analysed. You can leave this screen; the review keeps going.")
            LumenDerivativeAction("Stop review", onCancel, Modifier.fillMaxWidth(), testTag = "review-cancel")
        }
    }
}

@Composable
private fun StartReviewCard(ui: AnalysisUiState, vm: AnalysisViewModel) {
    LumenDerivativeSurface(DerivativeSurfaceRole.RECESSED_TRAY, Modifier.fillMaxWidth().testTag("review-start-card")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Game Review", color = LumenColors.OnSurface, style = MaterialTheme.typography.titleMedium)
            AnalysisNote(
                "Every move is checked by the engine, then classified, with accuracy and a performance estimate for both sides. " +
                    "Runs on this device; nothing is uploaded.",
            )
            val resumable = ui.reviewRecord?.state?.let { it == ReviewState.PARTIAL || it == ReviewState.CANCELLED || it == ReviewState.FAILED || it == ReviewState.RUNNING } == true
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(ReviewPreset.FAST to "Fast", ReviewPreset.BALANCED to "Balanced", ReviewPreset.DEEP to "Deep").forEach { (preset, label) ->
                    LumenDerivativeSegment(label, ui.reviewPreset == preset, { vm.setReviewPreset(preset) }, testTag = "review-preset-${preset.name}")
                }
            }
            AnalysisNote(
                when (ui.reviewPreset) {
                    ReviewPreset.FAST -> "Fast: a quick look (about 0.2 s a position)."
                    ReviewPreset.DEEP -> "Deep: slow and thorough, every borderline move re-checked."
                    else -> "Balanced: about half a second a position, critical moves re-checked."
                },
            )
            LumenDerivativeAction(if (resumable) "Resume review" else "Start review", { vm.startReview() }, Modifier.fillMaxWidth(), testTag = "review-start")
            ui.reviewProgress?.takeIf { it.phase == ReviewRunPhase.FAILED }?.message?.let { AnalysisNote(it) }
        }
    }
}

@Composable
private fun CoachCard(ui: AnalysisUiState, vm: AnalysisViewModel, keyMoments: Boolean) {
    val review = ui.review ?: return
    val move = ui.reviewedMove
    LumenDerivativeSurface(DerivativeSurfaceRole.RECESSED_TRAY, Modifier.fillMaxWidth().testTag("review-coach")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (move == null) {
                if (ui.nodeId == ui.tree.rootId) {
                    SummaryHeader(ui)
                    AnalysisNote(review.opening?.let { "Opening: ${it.name} (${it.eco})" } ?: "Start of the game.")
                } else {
                    AnalysisNote("This position is off the reviewed game. Tap a move on the rail to return.")
                }
            } else {
                val positions = remember(ui.tree) { ui.mainlineIds.map { ui.tree.node(it).position } }
                val before = if (move.ply == 0) ui.tree.startPosition else positions[move.ply - 1]
                val after = positions[move.ply]
                val explanation = remember(move) {
                    MoveExplanations.explain(move, before, after, review.opening?.name?.takeIf { move.book })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClassificationBadge(move.classification, size = 26.dp)
                    Text(explanation.headline, color = LumenColors.OnSurface, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        MoveClassifier.formatScore(if (move.mover == dev.lumenchess.core.chess.Color.WHITE) move.scoreAfter else move.scoreAfter.negated()),
                        color = LumenColors.OnSurfaceMuted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(explanation.detail, color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.bodyMedium)
                if (move.bestLine.size > 1 || (move.bestMove != null && move.bestMove != move.move)) {
                    val line = remember(move) { MoveExplanations.lineSan(before, move.bestLine, limit = 6) }
                    if (line.isNotEmpty() && move.bestMove != move.move) {
                        Text("Best line: ${line.joinToString(" ")}", color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (keyMoments) {
                    LumenDerivativeAction("Previous", vm::previousKeyMoment, Modifier.weight(1f), enabled = review.summary.keyMoments.any { it < (ui.mainlinePly ?: Int.MAX_VALUE) }, testTag = "review-previous-key")
                    LumenDerivativeAction("Next key moment", vm::nextKeyMoment, Modifier.weight(1.4f), enabled = review.summary.keyMoments.any { it > (ui.mainlinePly ?: -1) }, testTag = "review-next-key")
                } else {
                    val best = move?.bestMove
                    if (move != null && best != null && best != move.move && move.classification.isError) {
                        LumenDerivativeAction("Try the best move", { vm.tryBest(move.ply) }, Modifier.weight(1.3f), testTag = "review-try-best")
                    }
                    LumenDerivativeAction("Next move", vm::forward, Modifier.weight(1f), enabled = ui.tree.mainlineChildOf(ui.nodeId) != null, testTag = "review-next")
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(ui: AnalysisUiState) {
    val summary = ui.review?.summary ?: return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(summary.white, summary.black).forEach { side ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (side.color == dev.lumenchess.core.chess.Color.WHITE) "White" else "Black", color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.labelMedium)
                Text(
                    side.accuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "–",
                    color = LumenColors.OnSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { contentDescription = "Accuracy ${side.accuracy?.let { String.format(Locale.US, "%.1f", it) } ?: "unknown"}" },
                )
                Text("Accuracy", color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.labelSmall)
                side.gameRating?.let { Text("Game rating $it", color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun ReviewSummaryTable(ui: AnalysisUiState) {
    val review = ui.review ?: return
    val summary = review.summary
    LumenDerivativeSurface(DerivativeSurfaceRole.RECESSED_TRAY, Modifier.fillMaxWidth().testTag("review-summary")) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryHeader(ui)
            val order = listOf(
                MoveClassification.BRILLIANT, MoveClassification.GREAT, MoveClassification.BEST, MoveClassification.EXCELLENT,
                MoveClassification.GOOD, MoveClassification.BOOK, MoveClassification.INACCURACY, MoveClassification.MISTAKE,
                MoveClassification.MISS, MoveClassification.BLUNDER,
            )
            for (classification in order) {
                Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${summary.white.counts[classification] ?: 0}", Modifier.width(36.dp), color = LumenColors.OnSurface, style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        ClassificationBadge(classification, size = 16.dp)
                        Text("  ${classification.label}", color = classification.color, style = MaterialTheme.typography.labelLarge)
                    }
                    Text("${summary.black.counts[classification] ?: 0}", Modifier.width(36.dp), color = LumenColors.OnSurface, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("By phase", color = LumenColors.OnSurface, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            for (phase in GamePhase.entries) {
                val white = summary.white.phaseAccuracy[phase]
                val black = summary.black.phaseAccuracy[phase]
                if (white == null && black == null) continue
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(white?.let { String.format(Locale.US, "%.0f", it) } ?: "–", Modifier.width(36.dp), color = LumenColors.OnSurface, style = MaterialTheme.typography.labelLarge)
                    Text(phase.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f), color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.labelLarge)
                    Text(black?.let { String.format(Locale.US, "%.0f", it) } ?: "–", Modifier.width(36.dp), color = LumenColors.OnSurface, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("Technical details", color = LumenColors.OnSurface, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
            val depths = review.moves.mapNotNull { it.depth }
            val details = listOfNotNull(
                "Engine: ${review.record.engineName}",
                "Settings: ${review.record.profile ?: "default"}",
                "Model: ${review.record.modelVersion} · accuracy after Lichess's published method · ${dev.lumenchess.analysis.review.GameRatingModel.VERSION}",
                depths.takeIf { it.isNotEmpty() }?.let { "Depth: average ${it.average().toInt()}, deepest ${it.max()}" },
                "Average centipawn loss: ${summary.white.averageCentipawnLoss ?: "–"} (White) · ${summary.black.averageCentipawnLoss ?: "–"} (Black)",
                "Moves double-checked: ${review.moves.count { it.rechecked }}",
                if (!review.hasEngineLines) "Engine lines were cleared by storage cleanup; results are kept." else null,
                "Game rating is a single-game estimate from move quality, not a rating and not Chess.com's formula.",
            )
            details.forEach { AnalysisNote(it) }
        }
    }
}

// --- Engine lines --------------------------------------------------------------------------------

@Composable
internal fun EngineLinesPane(ui: AnalysisUiState, vm: AnalysisViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("analysis-lines-pane"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val termination = Rules.termination(ui.position)
        when {
            termination != null -> AnalysisNote(if (termination == Termination.CHECKMATE) "Checkmate." else "Stalemate.")
            !ui.engineEnabled -> {
                AnalysisNote("The engine is off.")
                LumenDerivativeAction("Turn the engine on", { vm.setEngineEnabled(true) }, testTag = "analysis-engine-on")
            }
            ui.engineStatus == EngineAnalysisClient.Status.FAILED -> AnalysisNote(ui.engineMessage ?: "The engine could not start.")
            else -> {
                val snapshot = ui.snapshot?.takeIf { it.position == ui.position }
                if (snapshot == null || snapshot.lines.isEmpty()) {
                    AnalysisNote(if (ui.engineStatus == EngineAnalysisClient.Status.READY) "Thinking…" else "Starting the engine…")
                } else {
                    snapshot.lines.forEach { line ->
                        val sans = remember(line) { MoveExplanations.lineSan(ui.position, line.moves, limit = 10) }
                        val white = if (ui.position.sideToMove == dev.lumenchess.core.chess.Color.WHITE) line.score else line.score.negated()
                        LumenDerivativeSurface(
                            DerivativeSurfaceRole.NEUTRAL_ROW,
                            Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            onClick = { line.moves.firstOrNull()?.let(vm::play) },
                            testTag = "analysis-line-${line.rank}",
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ScoreChip(MoveClassifier.formatScore(white), whiteAhead = dev.lumenchess.analysis.eval.ExpectedPoints.of(white) >= .5)
                                Text(
                                    numbered(ui, sans),
                                    color = LumenColors.OnSurface,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    AnalysisNote(
                        listOfNotNull(
                            "Stockfish 18",
                            snapshot.depth?.let { "depth $it" },
                            snapshot.nodesPerSecond?.takeIf { it > 0 }?.let { "${String.format(Locale.US, "%.1f", it / 1_000_000.0)} M nodes/s" },
                            if (snapshot.finished) null else "live",
                        ).joinToString(" · "),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(text: String, whiteAhead: Boolean) {
    Box(
        Modifier
            .width(52.dp)
            .background(if (whiteAhead) Color(0xFFE9E5DA) else Color(0xFF2A2F31), RoundedCornerShape(5.dp))
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (whiteAhead) Color(0xFF2A2F31) else Color(0xFFE9E5DA), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun numbered(ui: AnalysisUiState, sans: List<String>): String {
    var number = ui.position.fullmoveNumber
    var white = ui.position.sideToMove == dev.lumenchess.core.chess.Color.WHITE
    val out = StringBuilder()
    sans.forEachIndexed { index, san ->
        if (white) out.append(number).append(". ") else if (index == 0) out.append(number).append("… ")
        out.append(san).append(' ')
        if (!white) number += 1
        white = !white
    }
    return out.toString().trim()
}

// --- Moves ---------------------------------------------------------------------------------------

private data class MoveToken(val text: String, val nodeId: GameNodeId?, val level: Int)

private fun moveTokens(tree: GameTree): List<MoveToken> {
    val out = ArrayList<MoveToken>()
    fun label(node: dev.lumenchess.core.chess.GameNode, forceNumber: Boolean): String {
        val parent = tree.node(node.parentId!!).position
        return when {
            parent.sideToMove == dev.lumenchess.core.chess.Color.WHITE -> "${parent.fullmoveNumber}. ${node.san}"
            forceNumber -> "${parent.fullmoveNumber}… ${node.san}"
            else -> node.san.orEmpty()
        }
    }
    fun line(start: GameNodeId, level: Int, firstForced: Boolean) {
        var parent = start
        var force = firstForced
        while (true) {
            val children = tree.childrenOf(parent)
            val main = children.firstOrNull() ?: break
            out += MoveToken(label(main, force), main.id, level)
            force = false
            for (variation in children.drop(1)) {
                out += MoveToken("(", null, level + 1)
                out += MoveToken(label(variation, true), variation.id, level + 1)
                line(variation.id, level + 1, firstForced = false)
                out += MoveToken(")", null, level + 1)
                force = true
            }
            parent = main.id
        }
    }
    line(tree.rootId, 0, firstForced = true)
    return out
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun MovesPane(ui: AnalysisUiState, vm: AnalysisViewModel) {
    var editing by remember { mutableStateOf<GameNodeId?>(null) }
    val tokens = remember(ui.tree) { moveTokens(ui.tree) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("analysis-moves-pane"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ui.opening?.let { AnalysisNote("${it.eco} · ${it.name}") }
        if (tokens.isEmpty()) AnalysisNote("No moves yet. Make a move on the board to start a line.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tokens.forEach { token ->
                val id = token.nodeId
                if (id == null) {
                    Text(token.text, color = LumenColors.OnSurfaceFaint, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val selected = id == ui.nodeId
                    val ply = ui.mainlineIds.indexOf(id)
                    val classification = if (ply >= 0) ui.review?.moves?.getOrNull(ply)?.classification else null
                    Text(
                        token.text,
                        color = when {
                            selected -> LumenColors.OnSurface
                            classification != null && classification.isError -> classification.color
                            token.level > 0 -> LumenColors.OnSurfaceMuted
                            else -> LumenColors.OnSurface
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (token.level > 0) 13.sp else 14.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .background(if (selected) LumenColors.AccentBlueGhost else Color.Transparent, RoundedCornerShape(4.dp))
                            .combinedClickable(
                                onClick = { vm.select(id) },
                                onLongClickLabel = "Edit variation",
                                onLongClick = { if (id !in ui.mainlineIds || ui.game == null) editing = id },
                            )
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
        }
        if (ui.edited) AnalysisNote("Long-press a move you added to promote or delete its line. Save it from More.")
        ui.saveMessage?.let { AnalysisNote(it) }
    }
    editing?.let { id ->
        Dialog(onDismissRequest = { editing = null }) {
            LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ui.tree.node(id).san.orEmpty(), color = LumenColors.OnSurface, style = MaterialTheme.typography.titleMedium)
                    val isFirst = ui.tree.node(id).parentId?.let { ui.tree.childrenOf(it).firstOrNull()?.id == id } == true
                    LumenDerivativeAction("Make this the main line", { vm.promote(id); editing = null }, Modifier.fillMaxWidth(), enabled = !isFirst, testTag = "variation-promote")
                    LumenDerivativeAction("Delete from here", { vm.delete(id); editing = null }, Modifier.fillMaxWidth(), testTag = "variation-delete")
                    LumenDerivativeAction("Cancel", { editing = null }, Modifier.fillMaxWidth(), testTag = "variation-cancel")
                }
            }
        }
    }
}

// --- Explorer ------------------------------------------------------------------------------------

@Composable
internal fun ExplorerPane(ui: AnalysisUiState, vm: AnalysisViewModel) {
    val position = ui.position
    val book = AppData.openingBook
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("analysis-explorer-pane"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val here = remember(position) { book.at(position) }
        Text(
            here?.let { "${it.eco} · ${it.name}" } ?: ui.opening?.let { "Out of book (from ${it.name})" } ?: "Not a named opening position",
            color = LumenColors.OnSurface,
            style = MaterialTheme.typography.titleSmall,
        )
        val bookMoves = remember(position) {
            MoveGenerator.legalMoves(position).mapNotNull { move ->
                book.at(MoveGenerator.applyLegalMove(position, move))?.let { move to it }
            }.sortedBy { it.second.name.length }
        }
        if (bookMoves.isNotEmpty()) {
            Text("Book moves", color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.labelLarge)
            bookMoves.take(8).forEach { (move, opening) ->
                ExplorerRow(San.generate(position, move), opening.name, null) { vm.play(move) }
            }
        }
        Text("Lichess", color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        if (!ui.onlineEnabled) {
            AnalysisNote("Move statistics from millions of Lichess games. Sends this position (FEN) to Lichess.")
            LumenDerivativeAction("Show Lichess statistics", { vm.setOnline(true) }, Modifier.fillMaxWidth(), testTag = "explorer-online-on")
        } else {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LumenDerivativeSegment("Lichess players", ui.onlineDatabase == "lichess", { vm.setOnline(true, "lichess") }, testTag = "explorer-online-lichess")
                LumenDerivativeSegment("Masters", ui.onlineDatabase == "masters", { vm.setOnline(true, "masters") }, testTag = "explorer-online-masters")
                LumenDerivativeSegment("Off", false, { vm.setOnline(false) }, testTag = "explorer-online-off")
            }
            val online = ui.online
            when {
                ui.onlineLoading -> AnalysisNote("Asking Lichess…")
                ui.onlineError != null -> AnalysisNote(ui.onlineError)
                online != null && online.moves.isEmpty() -> AnalysisNote("No ${if (online.database == "masters") "master" else "Lichess"} games from this position.")
                online != null -> {
                    online.moves.take(10).forEach { move ->
                        val total = move.games.coerceAtLeast(1).toDouble()
                        ExplorerRow(
                            move.san,
                            "${compactCount(move.games)} games" + (move.averageRating?.let { " · avg $it" } ?: ""),
                            Triple(move.white / total, move.draws / total, move.black / total),
                        ) {
                            MoveGenerator.legalMoves(position).firstOrNull { it.uci == move.uci }?.let(vm::play)
                        }
                    }
                    AnalysisNote(if (online.database == "masters") "Lichess masters database (OTB, 2200+)." else "Lichess rated blitz, rapid and classical, 1600+.")
                }
            }
        }
        Text("Your games", color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
        val index = ui.explorer
        when {
            ui.explorerLoading -> AnalysisNote("Reading your games…")
            ui.explorerError != null -> {
                AnalysisNote(ui.explorerError)
                LumenDerivativeAction("Try again", vm::loadExplorer, testTag = "explorer-retry")
            }
            index == null -> LumenDerivativeAction("Show my games here", vm::loadExplorer, testTag = "explorer-load")
            else -> {
                val moves = remember(index, position) { index.movesAt(position) }
                if (moves.isEmpty()) {
                    AnalysisNote("None of your ${index.gameCount} saved games reached this position (first 40 moves are indexed).")
                } else {
                    moves.take(10).forEach { stat ->
                        ExplorerRow(
                            San.generate(position, stat.move),
                            "${stat.games} game${if (stat.games == 1) "" else "s"}",
                            Triple(stat.whiteShare, stat.drawShare, stat.blackShare),
                        ) { vm.play(stat.move) }
                    }
                    AnalysisNote("From ${index.gameCount} games in your library. Results are shown for White / draw / Black.")
                }
            }
        }
    }
}

private fun compactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 10_000 -> "${value / 1_000}k"
    else -> value.toString()
}

@Composable
private fun ExplorerRow(san: String, detail: String, shares: Triple<Double, Double, Double>?, onClick: () -> Unit) {
    LumenDerivativeSurface(
        DerivativeSurfaceRole.NEUTRAL_ROW,
        Modifier.fillMaxWidth().heightIn(min = 40.dp),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(san, Modifier.width(56.dp), color = LumenColors.OnSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, Modifier.weight(1f), color = LumenColors.OnSurfaceMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (shares != null) {
                Row(Modifier.width(96.dp).height(10.dp)) {
                    val (white, draw, black) = shares
                    if (white > 0) Box(Modifier.weight(white.toFloat()).fillMaxHeight().background(Color(0xFFE9E5DA)))
                    if (draw > 0) Box(Modifier.weight(draw.toFloat()).fillMaxHeight().background(Color(0xFF8A9295)))
                    if (black > 0) Box(Modifier.weight(black.toFloat()).fillMaxHeight().background(Color(0xFF2A2F31)))
                    if (white + draw + black <= 0.0) Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF55595B)))
                }
            }
        }
    }
}

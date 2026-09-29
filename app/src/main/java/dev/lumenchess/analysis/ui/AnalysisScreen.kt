package dev.lumenchess.analysis.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.analysis.review.MoveClassifier
import dev.lumenchess.board.BoardMovePresentation
import dev.lumenchess.board.ChessboardArrow
import dev.lumenchess.board.ChessboardArrowStyle
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.ThemedLumenChessboard
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.core.chess.Square
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenActionGlyph
import dev.lumenchess.design.LumenActionStrip
import dev.lumenchess.design.LumenActionTile
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenDerivativeTabs
import dev.lumenchess.design.LumenDerivativeTopBar

@Composable
fun AnalysisRoute(
    viewModel: AnalysisViewModel,
    request: AnalysisRequest,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(request) { viewModel.open(request) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setVisible(true)
                Lifecycle.Event.ON_STOP -> viewModel.setVisible(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.setVisible(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setVisible(false)
        }
    }
    BackHandler(onBack = onClose)
    val ui by viewModel.uiState
    AnalysisScreen(ui, viewModel, onClose, modifier)
}

@Composable
private fun AnalysisScreen(ui: AnalysisUiState, vm: AnalysisViewModel, onClose: () -> Unit, modifier: Modifier) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    LumenDerivativePage(modifier, testTag = "analysis-screen", verticalPadding = 2, spacing = 6) {
        LumenDerivativeTopBar(ui.title, onClose, backTestTag = "analysis-back")
        when {
            ui.loading -> AnalysisNote("Opening…")
            ui.error != null -> {
                AnalysisNote(ui.error)
                LumenDerivativeAction("Try again", vm::retry, testTag = "analysis-retry")
            }
            else -> AnalysisContent(ui, vm, onMenu = { menuOpen = true })
        }
    }
    if (menuOpen) AnalysisMenu(ui, vm, onDismiss = { menuOpen = false })
}

@Composable
private fun ColumnScope.AnalysisContent(ui: AnalysisUiState, vm: AnalysisViewModel, onMenu: () -> Unit) {
    var presentation by remember { mutableStateOf(BoardMovePresentation.ENGINE) }
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val barWidth = 14.dp
        val gap = 6.dp
        // The board never takes more than ~55% of the height, so panes keep usable room on short
        // and landscape windows.
        val boardSide = minOf(maxWidth - barWidth - gap, maxHeight * .55f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val topColor = if (ui.flipped) Color.WHITE else Color.BLACK
            PlayerLine(ui, topColor)
            Row(Modifier.fillMaxWidth().height(boardSide), horizontalArrangement = Arrangement.Center) {
                val score = ui.whiteScore
                VerticalEvaluationBar(
                    whitePoints = ui.whitePoints,
                    label = score?.let { MoveClassifier.formatScore(it).removePrefix("+") },
                    flipped = ui.flipped,
                    modifier = Modifier.width(barWidth).fillMaxHeight(),
                )
                Spacer(Modifier.width(gap))
                AnalysisBoard(ui, boardSide.value, presentation) { move ->
                    presentation = BoardMovePresentation.HUMAN_TAP
                    vm.play(move)
                }
            }
            PlayerLine(ui, topColor.opposite)
            MoveRail(ui) { id -> presentation = BoardMovePresentation.ENGINE; vm.select(id) }
            LumenActionStrip(Modifier.fillMaxWidth().height(58.dp)) {
                LumenActionTile("Start", LumenActionGlyph.FIRST, "analysis-first", { presentation = BoardMovePresentation.ENGINE; vm.toStart() })
                LumenActionTile("Back", LumenActionGlyph.PREVIOUS, "analysis-previous", { presentation = BoardMovePresentation.ENGINE; vm.back() })
                LumenActionTile("Next", LumenActionGlyph.NEXT, "analysis-next", { presentation = BoardMovePresentation.ENGINE; vm.forward() })
                LumenActionTile("End", LumenActionGlyph.LAST, "analysis-last", { presentation = BoardMovePresentation.ENGINE; vm.toEnd() })
                LumenActionTile(if (ui.engineEnabled) "Engine" else "Off", LumenActionGlyph.ENGINE, "analysis-engine-toggle", { vm.setEngineEnabled(!ui.engineEnabled) })
                LumenActionTile("Flip", LumenActionGlyph.FLIP, "analysis-flip", vm::flip)
                LumenActionTile("More", LumenActionGlyph.MENU, "analysis-menu", onMenu)
            }
            val panes = buildList {
                if (ui.canReview) add(AnalysisPane.REVIEW)
                add(AnalysisPane.LINES)
                add(AnalysisPane.MOVES)
                add(AnalysisPane.EXPLORER)
            }
            val selected = panes.indexOf(ui.pane).coerceAtLeast(0)
            LumenDerivativeTabs(panes.map { it.label }, selected, { vm.setPane(panes[it]) }, testTagPrefix = "analysis-pane")
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (panes[selected]) {
                    AnalysisPane.REVIEW -> ReviewPane(ui, vm)
                    AnalysisPane.LINES -> EngineLinesPane(ui, vm)
                    AnalysisPane.MOVES -> MovesPane(ui, vm)
                    AnalysisPane.EXPLORER -> ExplorerPane(ui, vm)
                }
            }
        }
    }
}

@Composable
private fun AnalysisBoard(
    ui: AnalysisUiState,
    sideDp: Float,
    presentation: BoardMovePresentation,
    onMove: (dev.lumenchess.core.chess.Move) -> Unit,
) {
    val orientation = if (ui.flipped) ChessboardOrientation.BLACK else ChessboardOrientation.WHITE
    val node = ui.node
    val arrows = buildList {
        val snapshot = ui.snapshot?.takeIf { it.position == ui.position }
        if (ui.engineEnabled && snapshot != null) {
            snapshot.lines.take(2).forEachIndexed { index, line ->
                line.moves.firstOrNull()?.let { add(ChessboardArrow(it.from, it.to, if (index == 0) ChessboardArrowStyle.PRIMARY else ChessboardArrowStyle.SECONDARY)) }
            }
        }
        // In review, show what should have been played instead of a flawed move.
        val reviewed = ui.reviewedMove
        if (reviewed != null && reviewed.bestMove != null && reviewed.bestMove != reviewed.move &&
            reviewed.classification != MoveClassification.BOOK && reviewed.classification != MoveClassification.BEST &&
            reviewed.classification != MoveClassification.BRILLIANT && reviewed.classification != MoveClassification.GREAT
        ) {
            add(ChessboardArrow(reviewed.bestMove!!.from, reviewed.bestMove!!.to, ChessboardArrowStyle.SECONDARY))
        }
    }
    Box(
        Modifier.size(sideDp.dp).testTag("analysis-board").semantics {
            stateDescription = "${if (ui.flipped) "Black" else "White"} at the bottom; ${Fen.serialize(ui.position)}"
        },
    ) {
        ThemedLumenChessboard(
            position = node.position,
            onMove = onMove,
            modifier = Modifier.fillMaxSize(),
            orientation = orientation,
            input = ChessboardInput(tapEnabled = true, dragEnabled = true),
            highlights = ChessboardHighlights(lastMove = node.move, movePresentation = presentation),
            arrows = arrows.distinct(),
        )
        val reviewed = ui.reviewedMove
        if (reviewed != null) {
            val cell = sideDp / 8f
            val (column, row) = visualCell(reviewed.move.to, orientation)
            val badge = 20f
            ClassificationBadge(
                reviewed.classification,
                size = badge.dp,
                modifier = Modifier.offset(
                    x = ((column + 1) * cell - badge * .72f).coerceIn(0f, sideDp - badge).dp,
                    y = (row * cell - badge * .28f).coerceIn(0f, sideDp - badge).dp,
                ),
            )
        }
    }
}

private fun visualCell(square: Square, orientation: ChessboardOrientation): Pair<Int, Int> = when (orientation) {
    ChessboardOrientation.WHITE -> square.file to (7 - square.rank)
    ChessboardOrientation.BLACK -> (7 - square.file) to square.rank
}

@Composable
private fun PlayerLine(ui: AnalysisUiState, color: Color) {
    val game = ui.game
    val participant = if (color == Color.WHITE) game?.whiteParticipant else game?.blackParticipant
    val header = ui.tree.headers[if (color == Color.WHITE) "White" else "Black"]
    val name = participant?.displayName ?: participant?.engineName ?: header ?: if (color == Color.WHITE) "White" else "Black"
    val elo = ui.tree.headers[if (color == Color.WHITE) "WhiteElo" else "BlackElo"]?.takeIf { it.all(Char::isDigit) }
    val side = ui.review?.summary?.side(color)
    Row(
        Modifier.fillMaxWidth().height(20.dp).padding(start = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(10.dp).background(
                if (color == Color.WHITE) androidx.compose.ui.graphics.Color(0xFFE9E5DA) else androidx.compose.ui.graphics.Color(0xFF2A2F31),
                RoundedCornerShape(2.dp),
            ),
        )
        Text(
            listOfNotNull(name, elo?.let { "($it)" }).joinToString(" "),
            color = LumenColors.OnSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        side?.accuracy?.let { accuracy ->
            Text(
                "Accuracy ${"%.1f".format(accuracy)}",
                color = LumenColors.OnSurfaceMuted,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

/** Horizontal rail of mainline moves; reviewed moves carry their classification colour. */
@Composable
private fun MoveRail(ui: AnalysisUiState, onSelect: (GameNodeId) -> Unit) {
    val line = remember(ui.tree, ui.nodeId) { railLine(ui) }
    val state = rememberLazyListState()
    val currentIndex = line.indexOfFirst { it.id == ui.nodeId }
    LaunchedEffect(currentIndex, line.size) {
        if (currentIndex >= 0) state.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }
    LazyRow(
        Modifier.fillMaxWidth().height(36.dp).testTag("analysis-move-rail"),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(line, key = { _, node -> node.id.value }) { _, node ->
            val selected = node.id == ui.nodeId
            val ply = ui.mainlineIds.indexOf(node.id)
            val classification = ui.review?.moves?.getOrNull(ply)?.classification?.takeIf { ply >= 0 }
            val parent = ui.tree.node(node.parentId!!)
            val moveNumber = parent.position.fullmoveNumber
            val white = parent.position.sideToMove == Color.WHITE
            val label = if (white) "$moveNumber. ${node.san}" else node.san.orEmpty()
            Row(
                Modifier
                    .height(30.dp)
                    .background(
                        if (selected) LumenColors.AccentBlueGhost else LumenColors.SurfaceRaised.copy(alpha = .6f),
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(node.id) }
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = listOfNotNull(label, classification?.label).joinToString(", ") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (classification != null && classification != MoveClassification.BEST && classification != MoveClassification.BOOK &&
                    classification != MoveClassification.EXCELLENT && classification != MoveClassification.GOOD
                ) {
                    ClassificationBadge(classification, size = 14.dp)
                }
                Text(
                    label,
                    color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The line through the current node: its ancestors, then the main continuation after it. */
private fun railLine(ui: AnalysisUiState): List<dev.lumenchess.core.chess.GameNode> {
    val tree = ui.tree
    val before = dev.lumenchess.analysis.tree.TreeEditing.lineTo(tree, ui.nodeId).map(tree::node)
    val after = ArrayList<dev.lumenchess.core.chess.GameNode>()
    var current = tree.mainlineChildOf(ui.nodeId)
    while (current != null) {
        after += current
        current = tree.mainlineChildOf(current.id)
    }
    return before + after
}

@Composable
private fun AnalysisMenu(ui: AnalysisUiState, vm: AnalysisViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun copy(label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    }
    Dialog(onDismissRequest = onDismiss) {
        LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Analysis", style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                Text("Engine lines", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurfaceMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { count ->
                        LumenDerivativeAction(
                            if (count == ui.multiPv) "[$count]" else "$count",
                            { vm.setMultiPv(count) },
                            Modifier.weight(1f),
                            testTag = "analysis-lines-$count",
                        )
                    }
                }
                LumenDerivativeAction(
                    if (ui.edited) "Save analysis to Games" else "Save a copy to Games",
                    { vm.saveAnalysis(); onDismiss() },
                    Modifier.fillMaxWidth(),
                    enabled = ui.tree.childrenOf(ui.tree.rootId).isNotEmpty(),
                    testTag = "analysis-save",
                )
                LumenDerivativeAction("Copy FEN", { copy("LumenChess FEN", Fen.serialize(ui.position)); onDismiss() }, Modifier.fillMaxWidth(), testTag = "analysis-copy-fen")
                LumenDerivativeAction("Copy PGN", { copy("LumenChess PGN", Pgn.serialize(ui.tree)); onDismiss() }, Modifier.fillMaxWidth(), testTag = "analysis-copy-pgn")
                if (ui.review != null) {
                    LumenDerivativeAction("Review again", { vm.startReview(restart = true); onDismiss() }, Modifier.fillMaxWidth(), testTag = "analysis-review-again")
                }
                LumenDerivativeAction("Close", onDismiss, Modifier.fillMaxWidth(), testTag = "analysis-menu-close")
            }
        }
    }
}

@Composable
internal fun AnalysisNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
}

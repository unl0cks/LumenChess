package dev.lumenchess.games

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.ThemedLumenChessboard
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.*
import java.text.DateFormat
import java.util.Date
import android.content.ClipData
import android.content.ClipboardManager

/** Only presentation selection changes here; all displayed positions come from the loaded tree. */
@Composable
internal fun GameLibraryViewer(ui: GameLibraryUiState, vm: GameLibraryViewModel, modifier: Modifier) {
    val context = LocalContext.current
    LumenDerivativePage(modifier, testTag = "library-viewer", verticalPadding = 4, spacing = 8) {
        LumenDerivativeTopBar("Saved game", vm::backToList, backTestTag = "library-back")
        val game = ui.game
        val node = ui.selectedNode
        if (game == null || node == null) {
            LibraryNote(if (ui.opening) "Opening saved game…" else ui.openError ?: "No game selected.")
            if (ui.openError != null) LumenDerivativeAction("Retry", vm::retryOpen, testTag = "library-open-retry")
            return@LumenDerivativePage
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            // Reserve a square independent of history length, selected variation, or orientation.
            // Short/landscape windows retain room for controls and scrollable history below it.
            val boardSide = minOf(maxWidth, maxHeight * .60f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().height(boardSide), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(boardSide).testTag("library-board-stage").semantics {
                        stateDescription = "${if (ui.flipped) "Black" else "White"} orientation; ${Fen.serialize(node.position)}"
                    }) {
                        key(game.id, node.id) {
                            ThemedLumenChessboard(
                                position = node.position, onMove = {}, modifier = Modifier.fillMaxSize(),
                                orientation = if (ui.flipped) ChessboardOrientation.BLACK else ChessboardOrientation.WHITE,
                                input = ChessboardInput(tapEnabled = false, dragEnabled = false),
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LumenDerivativeAction("Start", vm::root, Modifier.weight(1f), enabled = ui.nodePath.isNotEmpty(), testTag = "library-root")
                    LumenDerivativeAction("Back", vm::previous, Modifier.weight(1f), enabled = ui.nodePath.isNotEmpty(), testTag = "library-previous")
                    LumenDerivativeAction("Next", vm::next, Modifier.weight(1f), enabled = game.tree.childrenOf(node.id).isNotEmpty(), testTag = "library-next")
                    LumenDerivativeAction("End", vm::end, Modifier.weight(1f), testTag = "library-end")
                    LumenDerivativeAction("Flip", vm::flip, Modifier.weight(1f), testTag = "library-flip")
                }
                val historyState = rememberLazyListState()
                LaunchedEffect(ui.nodePath) { historyState.scrollToItem(0) }
                LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("library-history"), state = historyState,
                    verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    item {
                        Text(if (ui.nodePath.isEmpty()) "Start position" else "Ply ${ui.nodePath.size}: ${node.san}",
                            color = LumenColors.OnSurface, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.testTag("library-selected-move"))
                        LibraryNote("Read-only · ${if (game.tree.startPosition.variant == Variant.CHESS960) "Chess960" else "Standard"}")
                    }
                    itemsIndexed(game.tree.childrenOf(node.id)) { index, child ->
                        LumenDerivativeAction(
                            "${if (index == 0) "Mainline" else "Variation $index"}: ${child.san}",
                            { vm.selectPath(ui.nodePath + index) }, Modifier.fillMaxWidth(), testTag = "library-child-$index",
                        )
                    }
                    if (ui.nodePath.isNotEmpty()) item {
                        LibraryNote((1..ui.nodePath.size).joinToString("  ") { depth ->
                            libraryNodeAtPath(game.tree, ui.nodePath.take(depth)).san.orEmpty()
                        })
                    }
                    if (node.leadingComments.isNotEmpty() || node.comments.isNotEmpty() || (ui.nodePath.isEmpty() && game.tree.rootComments.isNotEmpty())) item {
                        LibraryNote((node.leadingComments + node.comments + if (ui.nodePath.isEmpty()) game.tree.rootComments else emptyList()).joinToString("\n"))
                    }
                    item {
                        LumenDerivativeSurface(DerivativeSurfaceRole.RECESSED_TRAY, Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Game details", color = LumenColors.OnSurface, style = MaterialTheme.typography.titleMedium)
                                libraryResultLabel(game.tree.result?.name)?.let { LibraryNote("Result: $it") }
                                LibraryNote("Saved: ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(game.metadata.createdAtEpochMillis))}")
                                game.metadata.rated?.let { LibraryNote(if (it) "Rated" else "Unrated") }
                                game.metadata.termination?.let { LibraryNote("Termination: ${it.name.lowercase().replace('_', ' ')}") }
                                game.whiteParticipant?.let { LibraryNote("White: ${it.displayName ?: it.engineName ?: it.kind.name}") }
                                game.blackParticipant?.let { LibraryNote("Black: ${it.displayName ?: it.engineName ?: it.kind.name}") }
                                game.sources.map { it.type }.distinct().takeIf { it.isNotEmpty() }?.let { LibraryNote(it.joinToString(" / ") { type -> type.libraryLabel() }) }
                                libraryTimeControl(game.metadata, game.tree.headers)?.let { LibraryNote("Time control: $it") }
                                game.tree.headers.forEach { (name, value) -> LibraryNote("$name: $value") }
                            }
                        }
                    }
                    item {
                        LumenDerivativeAction("Copy PGN", {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                ClipData.newPlainText("LumenChess PGN", dev.lumenchess.core.chess.Pgn.serialize(game.tree)),
                            )
                        }, Modifier.fillMaxWidth(), testTag = "library-export-pgn")
                        LumenDerivativeAction("Copy FEN", {
                            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                ClipData.newPlainText("LumenChess FEN", Fen.serialize(node.position)),
                            )
                        }, Modifier.fillMaxWidth(), testTag = "library-copy-fen")
                        LibraryNote("Review, Analyze, and the Library branch editor remain unavailable in this build.")
                    }
                }
            }
        }
    }
}

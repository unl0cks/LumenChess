package dev.lumenchess.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.persistence.*
import dev.lumenchess.design.*
import java.text.DateFormat
import java.util.Date

@Composable
fun GameLibraryRoute(viewModel: GameLibraryViewModel, modifier: Modifier = Modifier, reservedGameIds: Set<String> = emptySet()) {
    val ui by viewModel.uiState
    SideEffect { viewModel.setReservedGameIds(reservedGameIds) }
    LaunchedEffect(viewModel) { viewModel.refresh() }
    BackHandler(enabled = ui.selectedGameId != null, onBack = viewModel::backToList)
    if (ui.selectedGameId != null) {
        GameLibraryViewer(ui, viewModel, modifier)
    } else {
        GameLibraryScreen(ui, viewModel, modifier)
    }
}

@Composable
private fun GameLibraryScreen(ui: GameLibraryUiState, vm: GameLibraryViewModel, modifier: Modifier) {
    val filterState = rememberLazyListState(ui.query.filter.ordinal)
    LumenDerivativePage(modifier, testTag = "library-list", verticalPadding = 12, spacing = 10) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Games", style = MaterialTheme.typography.headlineMedium, color = LumenColors.OnSurface)
                Text("Your chess library", style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
            }
            LumenDerivativeAction("Refresh", vm::refresh, enabled = !ui.loading, testTag = "library-refresh")
        }
        OutlinedTextField(
            value = ui.query.search, onValueChange = vm::setSearch,
            modifier = Modifier.fillMaxWidth().testTag("library-search"),
            label = { Text("Search players, engines, or headers") }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LumenColors.OnSurface, unfocusedTextColor = LumenColors.OnSurface,
                focusedBorderColor = LumenColors.AccentBlueBright, unfocusedBorderColor = LumenColors.OutlineStrong,
                focusedLabelColor = LumenColors.AccentBlueBright, unfocusedLabelColor = LumenColors.OnSurfaceMuted,
                cursorColor = LumenColors.AccentBlueBright,
            ),
        )
        LazyRow(Modifier.fillMaxWidth().testTag("library-filters"), state = filterState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibraryFilter.entries, key = { it.name }) { filter ->
                LumenDerivativeSurface(
                    if (ui.query.filter == filter) DerivativeSurfaceRole.SELECTED_FACE else DerivativeSurfaceRole.NEUTRAL_ROW,
                    modifier = Modifier.heightIn(min = 48.dp), onClick = { vm.setFilter(filter) },
                    testTag = "library-filter-${filter.name}",
                ) { Text(filter.label, color = LumenColors.OnSurface, style = MaterialTheme.typography.labelLarge) }
            }
        }
        if (ui.actionPending) LibraryNote("Saving changes…")
        ui.actionError?.let { message ->
            LibraryNote(message)
            if (ui.canRetryAction) LumenDerivativeAction("Retry update", vm::retryMutation, testTag = "library-action-retry")
        }
        if (ui.entries.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryNote(when {
                    ui.loading -> "Loading games…"
                    ui.listError != null -> ui.listError
                    ui.query == LibraryQuery() -> "No saved games yet. Games you save in Play and Arena will appear here."
                    else -> "No games match these filters. Try another source or search."
                })
                if (ui.listError != null) LumenDerivativeAction("Retry", vm::retryList, testTag = "library-list-retry")
            }
        } else key(ui.query) {
        // Mount the list only after its data exists so restoration isn't clamped by an empty load.
        val listState = rememberLazyListState(ui.listIndex, ui.listOffset)
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) -> vm.setListPosition(index, offset) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("library-cards"), state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(ui.entries, key = { it.id.value }) { entry ->
                LumenDerivativeSurface(
                    DerivativeSurfaceRole.NEUTRAL_ROW,
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        role = Role.Button, onClick = { vm.open(entry.id) },
                        onLongClickLabel = "Game actions", onLongClick = { vm.showActions(entry) },
                    ), testTag = "library-card-${entry.id.value}",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(entry.playerNames(), style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                        Text(entry.summary(), style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
                        Text(entry.details(), style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
                        if (entry.isFavorite || entry.isProtected) Text(
                            listOfNotNull("Favorite".takeIf { entry.isFavorite }, "Protected".takeIf { entry.isProtected }).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = LumenColors.AccentBlueBright,
                        )
                    }
                }
            }
            if (ui.loading) item { LibraryNote("Loading games…") }
            ui.listError?.let { message -> item {
                LibraryNote(message)
                LumenDerivativeAction("Retry", vm::retryList, testTag = "library-list-retry")
            } }
            if (ui.nextCursor != null && !ui.loading && ui.listError == null) item {
                LumenDerivativeAction("Load more", vm::loadMore, Modifier.fillMaxWidth(), testTag = "library-load-more")
            }
        }
        }
    }
    ui.contextEntry?.let { entry ->
        Dialog(onDismissRequest = vm::dismissActions) {
            LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.playerNames(), style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface)
                    LumenDerivativeAction(if (entry.isFavorite) "Remove favorite" else "Favorite", { vm.toggleFavorite(entry) }, Modifier.fillMaxWidth(), enabled = !ui.actionPending, testTag = "library-favorite")
                    LumenDerivativeAction(if (entry.isProtected) "Remove protection" else "Protect", { vm.toggleProtected(entry) }, Modifier.fillMaxWidth(), enabled = !ui.actionPending, testTag = "library-protect")
                    val reserved = entry.id.value in ui.reservedGameIds
                    LumenDerivativeAction("Delete", { vm.requestDelete(entry.id) }, Modifier.fillMaxWidth(), enabled = !reserved && !ui.actionPending, testTag = "library-delete")
                    if (reserved) LibraryNote("Owned by the current Play or Arena session. Deletion is unavailable while this game can still be saved or resumed.")
                    LibraryUnavailableActions()
                    LumenDerivativeAction("Close", vm::dismissActions, Modifier.fillMaxWidth(), testTag = "library-actions-close")
                }
            }
        }
    }
    ui.deleteId?.let {
        Dialog(onDismissRequest = vm::cancelDelete) {
            LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Delete saved game?", style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
                    LibraryNote("This permanently deletes this game and its saved review data. Favorite and Protect prevent automatic cleanup, but do not prevent this confirmed deletion.")
                    LumenDerivativeAction("Cancel", vm::cancelDelete, Modifier.fillMaxWidth(), testTag = "library-delete-cancel")
                    LumenDerivativeAction("Delete game", vm::confirmDelete, Modifier.fillMaxWidth(), testTag = "library-delete-confirm")
                }
            }
        }
    }
}

@Composable internal fun LibraryNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
}

@Composable internal fun LibraryUnavailableActions() {
    LibraryNote("Review, Analyze, Export, and the Library branch editor are not available in this build. Branching from an Arena session remains available in Arena.", Modifier.testTag("library-unavailable"))
}

internal val LibraryFilter.label: String get() = when (this) {
    LibraryFilter.ALL -> "All"
    LibraryFilter.LOCAL -> "Local"
    LibraryFilter.ENGINE_ARENA -> "Engine Arena"
    LibraryFilter.CHESS_COM -> "Chess.com"
    LibraryFilter.LICHESS -> "Lichess"
    LibraryFilter.IMPORTED -> "Imported"
    LibraryFilter.BRANCHES -> "Branches / Analysis"
    LibraryFilter.FAVORITES -> "Favorites"
}

internal fun LibraryEntry.playerNames(): String = "${whiteName ?: whiteEngineName ?: headers["White"] ?: "White"} vs ${blackName ?: blackEngineName ?: headers["Black"] ?: "Black"}"

internal fun LibraryEntry.summary(): String = listOfNotNull(
    libraryResultLabel(result), if (variant == Variant.CHESS960) "Chess960" else "Standard",
    headers["Date"] ?: DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(metadata.playedAtEpochMillis ?: metadata.createdAtEpochMillis)),
    libraryTimeControl(metadata, headers),
).joinToString(" · ")

internal fun libraryResultLabel(result: String?): String? = when (result) {
    "WHITE_WIN" -> "1-0"
    "BLACK_WIN" -> "0-1"
    "DRAW" -> "1/2-1/2"
    else -> null
}

internal fun LibraryEntry.details(): String = listOfNotNull(
    sources.joinToString(" / ") { it.libraryLabel() }.takeIf { it.isNotEmpty() },
    whiteEngineName?.takeIf { it != whiteName }?.let { "White engine: $it" },
    blackEngineName?.takeIf { it != blackName }?.let { "Black engine: $it" },
    headers["WhiteElo"]?.let { "White $it" }, headers["BlackElo"]?.let { "Black $it" },
    headers["Opening"], headers["ECO"], headers["Event"],
    latestReviewState?.let { "Review: ${it.name.lowercase()}" },
).joinToString(" · ")

internal fun GameSourceType.libraryLabel(): String = when (this) {
    GameSourceType.LOCAL -> "Local"; GameSourceType.ENGINE_ARENA -> "Engine Arena"
    GameSourceType.CHESS_COM -> "Chess.com"; GameSourceType.LICHESS -> "Lichess"
    GameSourceType.PGN_IMPORT -> "Imported"; GameSourceType.BRANCH -> "Branch"; GameSourceType.OTHER -> "Other"
}

internal fun libraryTimeControl(metadata: GamePersistenceMetadata, headers: Map<String, String>): String? {
    val control = metadata.timeControl
    return control?.raw ?: headers["TimeControl"] ?: control?.baseMillis?.let { base ->
        val increment = control.incrementMillis
        "${base / 1000}s" + (increment?.let { " + ${it / 1000}s" } ?: "")
    }
}

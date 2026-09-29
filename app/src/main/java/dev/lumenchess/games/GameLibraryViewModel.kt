package dev.lumenchess.games

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lumenchess.core.chess.GameNode
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.data.persistence.*
import dev.lumenchess.BuildConfig
import dev.lumenchess.games.imports.HttpGet
import dev.lumenchess.games.imports.ImportCandidate
import dev.lumenchess.games.imports.OnlineGameSources
import dev.lumenchess.games.imports.OnlineImportException
import dev.lumenchess.games.imports.OnlineSite
import dev.lumenchess.games.imports.PgnImport
import dev.lumenchess.games.imports.PgnImportBatch
import dev.lumenchess.games.imports.UrlConnectionHttpGet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ImportResult { ADDED, ALREADY_IN_LIBRARY, UNSUPPORTED }

/** Storage boundary keeps database ownership and cancellable reads separate from presentation. */
internal interface LibraryStore {
    suspend fun page(query: LibraryQuery, cursor: LibraryCursor?): LibraryPage
    suspend fun load(id: PersistentGameId): LoadedCanonicalGame?
    suspend fun favorite(id: PersistentGameId, value: Boolean): Boolean
    suspend fun protect(id: PersistentGameId, value: Boolean): Boolean
    suspend fun delete(id: PersistentGameId): Boolean
    /** Stores one imported game unless the same source game is already in the library. */
    suspend fun importGame(candidate: ImportCandidate): ImportResult = ImportResult.UNSUPPORTED
    fun close()
}

internal class RoomLibraryStore(private val database: LumenDatabase, private val ownsDatabase: Boolean = true) : LibraryStore {
    private val library = GameLibraryRepository(database)
    private val games = GamePersistenceRepository(database)
    override suspend fun page(query: LibraryQuery, cursor: LibraryCursor?) = library.page(query, cursor, limit = 40)
    override suspend fun load(id: PersistentGameId) = games.loadGame(id)
    override suspend fun favorite(id: PersistentGameId, value: Boolean) = library.setFavorite(id, value)
    override suspend fun protect(id: PersistentGameId, value: Boolean) = library.setProtected(id, value)
    override suspend fun delete(id: PersistentGameId) = library.delete(id)
    override suspend fun importGame(candidate: ImportCandidate): ImportResult {
        // Site games are identified by the site's game id; plain PGN by its content, so the same
        // file imported twice (or a site game pasted after an account import) is not duplicated.
        val source = GameSourceDraft(
            type = candidate.sourceType,
            externalGameId = candidate.externalGameId ?: GameContentFingerprint.compute(candidate.tree),
            externalUrl = candidate.externalUrl,
            importedAtEpochMillis = candidate.metadata.importedAtEpochMillis,
        )
        if (games.hasExternalGame(source)) return ImportResult.ALREADY_IN_LIBRARY
        games.persistExternalGame(
            PersistGameRequest(
                tree = candidate.tree,
                metadata = candidate.metadata,
                whiteParticipant = candidate.whiteName?.let { ParticipantDraft(ParticipantKind.EXTERNAL, displayName = it) },
                blackParticipant = candidate.blackName?.let { ParticipantDraft(ParticipantKind.EXTERNAL, displayName = it) },
            ),
            source,
        )
        return ImportResult.ADDED
    }
    override fun close() { if (ownsDatabase) LumenDatabaseFactory.close(database) }
}

data class GameLibraryUiState(
    val query: LibraryQuery = LibraryQuery(),
    val entries: List<LibraryEntry> = emptyList(),
    val nextCursor: LibraryCursor? = null,
    val loading: Boolean = false,
    val listError: String? = null,
    val selectedGameId: PersistentGameId? = null,
    val game: LoadedCanonicalGame? = null,
    val opening: Boolean = false,
    val openError: String? = null,
    val nodePath: List<Int> = emptyList(),
    val flipped: Boolean = false,
    val listIndex: Int = 0,
    val listOffset: Int = 0,
    val contextEntry: LibraryEntry? = null,
    val deleteId: PersistentGameId? = null,
    val reservedGameIds: Set<String> = emptySet(),
    val ownershipReady: Boolean = false,
    val actionPending: Boolean = false,
    val actionError: String? = null,
    val canRetryAction: Boolean = false,
    val importing: Boolean = false,
    val importStatus: String? = null,
) {
    val selectedNode: GameNode? get() = game?.let { libraryNodeAtPath(it.tree, nodePath) }
}

class GameLibraryViewModel internal constructor(
    private val saved: SavedStateHandle,
    private val store: LibraryStore,
) : ViewModel() {
    constructor(application: Application, savedStateHandle: SavedStateHandle) : this(
        savedStateHandle, RoomLibraryStore(LumenDatabaseFactory.open(application.applicationContext)),
    )

    private val mutableUi = mutableStateOf(GameLibraryUiState(
        query = LibraryQuery(LibraryFilter.entries.firstOrNull { it.name == saved.get<String>("filter") } ?: LibraryFilter.ALL,
            saved["search"] ?: ""),
        nodePath = saved.get<IntArray>("nodePath")?.toList().orEmpty(),
        flipped = saved["flipped"] ?: false,
        listIndex = saved["listIndex"] ?: 0,
        listOffset = saved["listOffset"] ?: 0,
    ))
    val uiState: State<GameLibraryUiState> = mutableUi
    private var pageJob: Job? = null
    private var openJob: Job? = null
    private var pageGeneration = 0L
    private var openGeneration = 0L
    private var retryAction: (() -> Unit)? = null
    private var importJob: Job? = null
    internal var httpGet: HttpGet = UrlConnectionHttpGet("LumenChess/${BuildConfig.VERSION_NAME} (Android; game import)")
    private var failedPageAppend = false

    companion object {
        val Factory = viewModelFactory {
            initializer { GameLibraryViewModel(checkNotNull(this[APPLICATION_KEY]), createSavedStateHandle()) }
        }
    }

    init {
        refresh()
        saved.get<String>("gameId")?.let { open(PersistentGameId(it), restorePath = true) }
    }

    /** Imports every readable game in pasted or opened PGN text. */
    fun importPgnText(text: String) = importPgnSource { text }

    /** [read] runs off the main thread, so it may open and read a file. */
    fun importPgnSource(read: () -> String) = runImport("Reading PGN…") { PgnImport.parse(read(), System.currentTimeMillis()) }

    /** Downloads and imports the player's most recent games from Chess.com or Lichess. */
    fun importFromSite(site: OnlineSite, username: String) = runImport("Downloading from ${site.label}…") {
        PgnImport.parse(OnlineGameSources.fetchRecentPgn(site, username, httpGet), System.currentTimeMillis())
    }

    fun clearImportStatus() {
        if (!uiState.value.importing) mutableUi.value = uiState.value.copy(importStatus = null)
    }

    private fun runImport(progress: String, read: () -> PgnImportBatch) {
        if (importJob?.isActive == true) return
        mutableUi.value = uiState.value.copy(importing = true, importStatus = progress)
        importJob = viewModelScope.launch {
            val status = try {
                val batch = withContext(Dispatchers.IO) { read() }
                var added = 0
                var existing = 0
                var failed = batch.unreadable
                for (candidate in batch.candidates) {
                    when (runCatching { store.importGame(candidate) }.getOrNull()) {
                        ImportResult.ADDED -> added += 1
                        ImportResult.ALREADY_IN_LIBRARY -> existing += 1
                        ImportResult.UNSUPPORTED, null -> failed += 1
                    }
                }
                importSummary(added, existing, failed)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: OnlineImportException) { error.message
            } catch (_: Exception) { "Import failed. Nothing was changed." }
            mutableUi.value = uiState.value.copy(importing = false, importStatus = status)
            refresh()
        }
    }

    fun setSearch(value: String) = changeQuery(uiState.value.query.copy(search = value))
    fun setFilter(value: LibraryFilter) = changeQuery(uiState.value.query.copy(filter = value))

    private fun changeQuery(query: LibraryQuery) {
        if (query == uiState.value.query) return
        saved["filter"] = query.filter.name
        saved["search"] = query.search
        saved["loadedCount"] = 40
        setListPosition(0, 0)
        mutableUi.value = uiState.value.copy(query = query, entries = emptyList(), nextCursor = null, contextEntry = null)
        refresh()
    }

    /** Refresh the visible page range so flags/deletes preserve the user's list context. */
    fun refresh() = readPages(append = false)
    fun loadMore() { if (!uiState.value.loading && uiState.value.nextCursor != null) readPages(append = true) }
    fun retryList() = readPages(append = failedPageAppend)

    private fun readPages(append: Boolean) {
        pageJob?.cancel()
        val generation = ++pageGeneration
        val query = uiState.value.query
        val initial = if (append) uiState.value.entries else emptyList()
        val cursor = if (append) uiState.value.nextCursor else null
        val targetCount = if (append) initial.size + 40 else maxOf(40, saved["loadedCount"] ?: 40)
        mutableUi.value = uiState.value.copy(loading = true, listError = null)
        pageJob = viewModelScope.launch {
            try {
                val entries = initial.toMutableList()
                var next = cursor
                do {
                    val page = store.page(query, next)
                    if (generation != pageGeneration) return@launch
                    entries += page.entries
                    next = page.nextCursor
                } while (entries.size < targetCount && next != null)
                saved["loadedCount"] = maxOf(40, entries.size)
                mutableUi.value = uiState.value.copy(entries = entries.distinctBy { it.id }, nextCursor = next, loading = false)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (generation == pageGeneration) {
                    failedPageAppend = append
                    mutableUi.value = uiState.value.copy(loading = false, listError = "Could not load your games. Try again.")
                }
            }
        }
    }

    fun setListPosition(index: Int, offset: Int) {
        saved["listIndex"] = index.coerceAtLeast(0)
        saved["listOffset"] = offset.coerceAtLeast(0)
        mutableUi.value = uiState.value.copy(listIndex = index.coerceAtLeast(0), listOffset = offset.coerceAtLeast(0))
    }

    fun open(id: PersistentGameId) = open(id, restorePath = false)

    private fun open(id: PersistentGameId, restorePath: Boolean) {
        openJob?.cancel()
        val generation = ++openGeneration
        saved["gameId"] = id.value
        if (!restorePath) { saved["nodePath"] = intArrayOf(); saved["flipped"] = false }
        mutableUi.value = uiState.value.copy(selectedGameId = id, game = null, opening = true, openError = null,
            contextEntry = null, nodePath = if (restorePath) uiState.value.nodePath else emptyList(),
            flipped = if (restorePath) uiState.value.flipped else false)
        openJob = viewModelScope.launch {
            try {
                val game = store.load(id)
                if (generation != openGeneration) return@launch
                mutableUi.value = uiState.value.copy(game = game, opening = false,
                    openError = if (game == null) "This game is no longer in your library." else null)
                if (game != null) selectPath(uiState.value.nodePath)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                if (generation == openGeneration) mutableUi.value = uiState.value.copy(opening = false,
                    openError = "This saved game could not be opened. Its data may be incomplete or damaged. Try again.")
            }
        }
    }

    fun retryOpen() { uiState.value.selectedGameId?.let { open(it, restorePath = true) } }
    fun backToList() {
        ++openGeneration
        openJob?.cancel()
        saved.remove<String>("gameId")
        mutableUi.value = uiState.value.copy(selectedGameId = null, game = null, opening = false, openError = null)
    }

    fun selectPath(path: List<Int>) {
        val tree = uiState.value.game?.tree ?: return
        val valid = if (libraryNodeAtPath(tree, path).id == tree.rootId) emptyList() else path.toList()
        saved["nodePath"] = valid.toIntArray()
        mutableUi.value = uiState.value.copy(nodePath = valid)
    }
    fun root() = selectPath(emptyList())
    fun previous() = selectPath(uiState.value.nodePath.dropLast(1))
    fun next() {
        val current = uiState.value.selectedNode ?: return
        if (uiState.value.game!!.tree.childrenOf(current.id).isNotEmpty()) selectPath(uiState.value.nodePath + 0)
    }
    fun end() { uiState.value.game?.let { selectPath(libraryMainlineEndPath(it.tree)) } }
    fun flip() {
        val value = !uiState.value.flipped
        saved["flipped"] = value
        mutableUi.value = uiState.value.copy(flipped = value)
    }

    fun setReservedGameIds(ids: Set<String>) = setOwnership(ids, ready = true)
    fun setOwnership(ids: Set<String>, ready: Boolean) {
        mutableUi.value = uiState.value.copy(reservedGameIds = ids.toSet(), ownershipReady = ready)
    }
    fun showActions(entry: LibraryEntry) {
        retryAction = null
        mutableUi.value = uiState.value.copy(contextEntry = entry, actionError = null, canRetryAction = false)
    }
    fun dismissActions() { mutableUi.value = uiState.value.copy(contextEntry = null) }
    fun toggleFavorite(entry: LibraryEntry) = mutate { store.favorite(entry.id, !entry.isFavorite) }
    fun toggleProtected(entry: LibraryEntry) = mutate { store.protect(entry.id, !entry.isProtected) }
    fun requestDelete(id: PersistentGameId) {
        retryAction = null
        if (!canDelete(id)) {
            blockDeletion()
            return
        }
        mutableUi.value = uiState.value.copy(contextEntry = null, deleteId = id, actionError = null, canRetryAction = false)
    }
    fun cancelDelete() { mutableUi.value = uiState.value.copy(deleteId = null) }
    fun confirmDelete() {
        val id = uiState.value.deleteId ?: return
        if (!canDelete(id)) {
            blockDeletion()
            return
        }
        mutate {
            // Re-check at execution as the active session can change while a dialog is open.
            if (!canDelete(id)) throw OwnershipUnavailableException()
            store.delete(id)
        }
    }
    fun retryMutation() { retryAction?.invoke() }
    private fun mutate(operation: suspend () -> Boolean) {
        if (uiState.value.actionPending) return
        retryAction = { mutate(operation) }
        mutableUi.value = uiState.value.copy(actionPending = true, actionError = null, canRetryAction = false, contextEntry = null, deleteId = null)
        viewModelScope.launch {
            try {
                val found = operation()
                mutableUi.value = uiState.value.copy(actionPending = false,
                    actionError = if (found) null else "This game is no longer in your library.")
                retryAction = null
                refresh()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: OwnershipUnavailableException) {
                retryAction = null
                blockDeletion()
            } catch (_: Exception) {
                mutableUi.value = uiState.value.copy(actionPending = false, canRetryAction = true, actionError = "Could not update this game. Try again.")
            }
        }
    }

    private fun canDelete(id: PersistentGameId): Boolean =
        uiState.value.ownershipReady && id.value !in uiState.value.reservedGameIds

    private fun blockDeletion() {
        val message = if (uiState.value.ownershipReady) {
            "This game belongs to the current Play or Arena session and cannot be deleted yet."
        } else {
            "Play or Arena is still determining game ownership. Deletion is unavailable until that finishes."
        }
        mutableUi.value = uiState.value.copy(
            contextEntry = null,
            deleteId = null,
            actionPending = false,
            actionError = message,
            canRetryAction = false,
        )
    }

    private class OwnershipUnavailableException : IllegalStateException()

    override fun onCleared() { pageJob?.cancel(); openJob?.cancel(); store.close() }
}

/** Child order is canonical; runtime GameNodeId values are never persisted as UUIDs. */
internal fun libraryNodeAtPath(tree: GameTree, path: List<Int>): GameNode {
    var node = tree.root
    for (index in path) node = tree.childrenOf(node.id).getOrNull(index) ?: return tree.root
    return node
}

internal fun libraryMainlineEndPath(tree: GameTree): List<Int> = List(tree.mainline().size) { 0 }

/** "Imported 12 games · 3 already in your library · 1 could not be read" */
internal fun importSummary(added: Int, existing: Int, failed: Int): String {
    if (added == 0 && existing == 0 && failed == 0) return "No games found"
    fun games(count: Int) = if (count == 1) "1 game" else "$count games"
    return buildList {
        add("Imported ${games(added)}")
        if (existing > 0) add("$existing already in your library")
        if (failed > 0) add("$failed could not be read")
    }.joinToString(" · ")
}

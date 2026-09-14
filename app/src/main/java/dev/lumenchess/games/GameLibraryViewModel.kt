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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Storage boundary keeps database ownership and cancellable reads separate from presentation. */
internal interface LibraryStore {
    suspend fun page(query: LibraryQuery, cursor: LibraryCursor?): LibraryPage
    suspend fun load(id: PersistentGameId): LoadedCanonicalGame?
    suspend fun favorite(id: PersistentGameId, value: Boolean): Boolean
    suspend fun protect(id: PersistentGameId, value: Boolean): Boolean
    suspend fun delete(id: PersistentGameId): Boolean
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
    val actionPending: Boolean = false,
    val actionError: String? = null,
    val canRetryAction: Boolean = false,
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

    fun setReservedGameIds(ids: Set<String>) { mutableUi.value = uiState.value.copy(reservedGameIds = ids.toSet()) }
    fun showActions(entry: LibraryEntry) {
        retryAction = null
        mutableUi.value = uiState.value.copy(contextEntry = entry, actionError = null, canRetryAction = false)
    }
    fun dismissActions() { mutableUi.value = uiState.value.copy(contextEntry = null) }
    fun toggleFavorite(entry: LibraryEntry) = mutate { store.favorite(entry.id, !entry.isFavorite) }
    fun toggleProtected(entry: LibraryEntry) = mutate { store.protect(entry.id, !entry.isProtected) }
    fun requestDelete(id: PersistentGameId) {
        retryAction = null
        mutableUi.value = uiState.value.copy(contextEntry = null, deleteId = id, actionError = null, canRetryAction = false)
    }
    fun cancelDelete() { mutableUi.value = uiState.value.copy(deleteId = null) }
    fun confirmDelete() {
        val id = uiState.value.deleteId ?: return
        if (id.value in uiState.value.reservedGameIds) {
            mutableUi.value = uiState.value.copy(deleteId = null, actionError = "This game belongs to the current Play or Arena session and cannot be deleted yet.")
            return
        }
        mutate {
            // Re-check at execution as the active session can change while a dialog is open.
            check(id.value !in uiState.value.reservedGameIds)
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
            } catch (_: Exception) {
                mutableUi.value = uiState.value.copy(actionPending = false, canRetryAction = true, actionError = "Could not update this game. Try again.")
            }
        }
    }

    override fun onCleared() { pageJob?.cancel(); openJob?.cancel(); store.close() }
}

/** Child order is canonical; runtime GameNodeId values are never persisted as UUIDs. */
internal fun libraryNodeAtPath(tree: GameTree, path: List<Int>): GameNode {
    var node = tree.root
    for (index in path) node = tree.childrenOf(node.id).getOrNull(index) ?: return tree.root
    return node
}

internal fun libraryMainlineEndPath(tree: GameTree): List<Int> = List(tree.mainline().size) { 0 }

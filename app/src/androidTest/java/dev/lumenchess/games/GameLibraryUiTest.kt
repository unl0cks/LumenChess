package dev.lumenchess.games

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.*
import dev.lumenchess.data.persistence.*
import dev.lumenchess.design.LumenTheme
import dev.lumenchess.ui.LumenChessApp
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameLibraryUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: LumenDatabase
    private lateinit var repository: GamePersistenceRepository
    private lateinit var store: RoomLibraryStore
    private val owners = mutableListOf<ViewModelStore>()

    @Before fun setUp() {
        database = LumenDatabaseFactory.inMemory(ApplicationProvider.getApplicationContext())
        repository = GamePersistenceRepository(database)
        store = RoomLibraryStore(database, ownsDatabase = false)
    }

    @After fun tearDown() {
        compose.runOnUiThread { owners.forEach { it.clear() } }
        LumenDatabaseFactory.close(database)
    }

    private fun model(handle: SavedStateHandle = SavedStateHandle(), source: LibraryStore = store): GameLibraryViewModel {
        lateinit var result: GameLibraryViewModel
        compose.runOnUiThread {
            result = GameLibraryViewModel(handle, source)
            owners += ViewModelStore().also { it.put("library", result) }
        }
        return result
    }

    private fun save(name: String, source: GameSourceType = GameSourceType.LOCAL, created: Long = 100L,
                     tree: GameTree = Pgn.parseGame("1. e4 e5 (1... c5) 2. Nf3 *")): PersistentGameId = runBlocking {
        repository.saveGame(PersistGameRequest(tree.withHeaders(tree.headers + mapOf("White" to name, "Black" to "Opponent", "WhiteElo" to "1820", "Event" to "Library fixture")),
            metadata = GamePersistenceMetadata(createdAtEpochMillis = created, timeControl = TimeControlMetadata(180000, 2000)),
            sources = listOf(GameSourceDraft(source))))
    }

    private fun loaded(vm: GameLibraryViewModel) = compose.waitUntil(10_000) { !vm.uiState.value.loading }

    @Test fun databaseCardsFilterSearchPageAppendAndDurableActions() {
        val ids = (0..40).map { save("Local $it", created = 100L + it) }
        val arena = save("Stockfish", GameSourceType.ENGINE_ARENA, 1000L)
        val vm = model()
        compose.setContent { LumenTheme { GameLibraryRoute(vm) } }
        loaded(vm)
        assertEquals(40, vm.uiState.value.entries.size)
        compose.runOnIdle { vm.loadMore() }
        loaded(vm)
        assertEquals(42, vm.uiState.value.entries.map { it.id }.distinct().size)
        for (filter in LibraryFilter.entries) {
            compose.onNodeWithTag("library-filters").performScrollToNode(hasTestTag("library-filter-${filter.name}"))
            compose.onNodeWithTag("library-filter-${filter.name}").assertIsDisplayed().performClick()
            loaded(vm)
            assertEquals(filter, vm.uiState.value.query.filter)
        }
        compose.runOnIdle { vm.setFilter(LibraryFilter.ENGINE_ARENA) }
        loaded(vm)
        assertEquals(listOf(arena), vm.uiState.value.entries.map { it.id })
        compose.onNodeWithTag("library-card-${arena.value}").performTouchInput { longClick() }
        compose.onNodeWithTag("library-favorite").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.entries.singleOrNull()?.isFavorite == true }
        compose.onNodeWithTag("library-card-${arena.value}").performTouchInput { longClick() }
        compose.onNodeWithTag("library-protect").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.entries.singleOrNull()?.isProtected == true }
        compose.onNodeWithTag("library-card-${arena.value}").performTouchInput { longClick() }
        compose.onNodeWithTag("library-delete").performClick()
        compose.onNodeWithTag("library-delete-cancel").performClick()
        assertNotNull(runBlocking { repository.loadGame(arena) })
        compose.onNodeWithTag("library-card-${arena.value}").performTouchInput { longClick() }
        compose.onNodeWithTag("library-delete").performClick()
        compose.onNodeWithTag("library-delete-confirm").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.entries.isEmpty() && !vm.uiState.value.loading }
        assertNull(runBlocking { repository.loadGame(arena) })
        assertNotNull(runBlocking { repository.loadGame(ids.first()) })
        compose.runOnIdle { vm.setFilter(LibraryFilter.ALL) }
        loaded(vm)
        compose.onNodeWithTag("library-search").performTextInput("Local 40")
        loaded(vm)
        assertEquals(listOf(ids.last()), vm.uiState.value.entries.map { it.id })
    }

    @Test fun canonicalViewerNavigatesVariationsWithoutMutationAndKeepsBoardBounds() {
        val id = save("Variation fixture")
        val original = runBlocking { repository.loadGame(id)!! }
        val vm = model()
        compose.setContent { LumenTheme { GameLibraryRoute(vm) } }
        loaded(vm)
        compose.onNodeWithTag("library-card-${id.value}").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.game != null }
        val bounds = compose.onNodeWithTag("library-board-stage").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("library-next").performClick()
        compose.onNodeWithTag("library-history").performScrollToNode(hasTestTag("library-child-1"))
        compose.onNodeWithTag("library-child-1").performClick()
        assertEquals("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2", Fen.serialize(vm.uiState.value.selectedNode!!.position))
        compose.onNodeWithTag("library-flip").performClick()
        assertEquals(bounds, compose.onNodeWithTag("library-board-stage").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("library-board-stage").performTouchInput { click(center); swipe(center, topCenter) }
        assertEquals(listOf(0, 1), vm.uiState.value.nodePath)
        compose.onNodeWithTag("library-history").performScrollToNode(hasTestTag("library-unavailable"))
        compose.onNodeWithTag("library-unavailable").assertIsDisplayed()
        compose.onNodeWithTag("library-back").performClick()
        compose.onNodeWithTag("library-card-${id.value}").assertIsDisplayed()
        assertEquals(Pgn.serialize(original.tree), Pgn.serialize(runBlocking { repository.loadGame(id)!!.tree }))
    }

    @Test fun savedStateRestoresQueryGameVariationOrientationAndListContext() {
        val id = save("Restored")
        val handle = SavedStateHandle()
        val vm = model(handle)
        loaded(vm)
        compose.runOnUiThread { vm.setSearch("Restored"); vm.setListPosition(0, 37); vm.open(id) }
        compose.waitUntil(10_000) { vm.uiState.value.game != null }
        compose.runOnUiThread { vm.selectPath(listOf(0, 1)); vm.flip() }
        lateinit var snapshot: Map<String, Any?>
        compose.runOnUiThread { snapshot = handle.keys().associateWith { handle.get<Any?>(it) }; owners.first().clear() }
        val restored = model(SavedStateHandle(snapshot))
        compose.waitUntil(10_000) { restored.uiState.value.game != null }
        assertEquals(id, restored.uiState.value.game!!.id)
        assertEquals("Restored", restored.uiState.value.query.search)
        assertEquals(listOf(0, 1), restored.uiState.value.nodePath)
        assertEquals("c5", restored.uiState.value.selectedNode!!.san)
        assertTrue(restored.uiState.value.flipped)
        assertEquals(37, restored.uiState.value.listOffset)
    }

    @Test fun latestQueryAndOpenWinEvenWhenAnOldReadIgnoresCancellation() {
        val first = save("Old")
        val second = save("New")
        val queryStarted = CompletableDeferred<Unit>()
        val queryRelease = CompletableDeferred<Unit>()
        val openStarted = CompletableDeferred<Unit>()
        val openRelease = CompletableDeferred<Unit>()
        val delayed = object : LibraryStore by store {
            override suspend fun page(query: LibraryQuery, cursor: LibraryCursor?): LibraryPage {
                val result = store.page(query, cursor)
                if (query.search == "Old") withContext(NonCancellable) { queryStarted.complete(Unit); queryRelease.await() }
                return result
            }
            override suspend fun load(id: PersistentGameId): LoadedCanonicalGame? {
                val result = store.load(id)
                if (id == first) withContext(NonCancellable) { openStarted.complete(Unit); openRelease.await() }
                return result
            }
        }
        val vm = model(source = delayed)
        compose.setContent { LumenTheme { GameLibraryRoute(vm) } }
        loaded(vm)
        compose.runOnIdle { vm.setSearch("Old") }
        runBlocking { withTimeout(10_000) { queryStarted.await() } }
        compose.runOnIdle { vm.setSearch("New") }
        loaded(vm)
        compose.runOnIdle { queryRelease.complete(Unit) }
        compose.waitForIdle()
        assertEquals(listOf(second), vm.uiState.value.entries.map { it.id })
        compose.runOnIdle { vm.open(first) }
        runBlocking { withTimeout(10_000) { openStarted.await() } }
        compose.runOnIdle { vm.open(second) }
        compose.waitUntil(10_000) { vm.uiState.value.game?.id == second }
        compose.runOnIdle { openRelease.complete(Unit) }
        compose.waitForIdle()
        assertEquals(second, vm.uiState.value.game!!.id)
        compose.runOnIdle { vm.backToList() }
        assertNull(vm.uiState.value.game)
    }

    @Test fun ownedGameDeletionIsGuardedAtConfirmationAndMissingOpenCanRetry() {
        val id = save("Owned")
        val vm = model()
        loaded(vm)
        compose.runOnUiThread { vm.requestDelete(id); vm.setReservedGameIds(setOf(id.value)); vm.confirmDelete() }
        assertNotNull(runBlocking { repository.loadGame(id) })
        assertNotNull(vm.uiState.value.actionError)
        assertFalse(vm.uiState.value.canRetryAction)
        compose.runOnUiThread { vm.open(PersistentGameId("missing")) }
        compose.waitUntil(10_000) { vm.uiState.value.openError != null }
        assertNull(vm.uiState.value.game)
        compose.runOnUiThread { vm.backToList(); vm.open(id) }
        compose.waitUntil(10_000) { vm.uiState.value.game?.id == id }
        assertNull(vm.uiState.value.openError)
    }

    @Test fun chess960ViewerUsesCanonicalStartPosition() {
        val tree = GameTree.create(Chess960.startingPosition(0))
        val id = save("Chess960", tree = tree)
        val vm = model()
        compose.setContent { LumenTheme { GameLibraryRoute(vm) } }
        compose.runOnIdle { vm.open(id) }
        compose.waitUntil(10_000) { vm.uiState.value.game != null }
        assertEquals(Variant.CHESS960, vm.uiState.value.selectedNode!!.position.variant)
        assertEquals(Fen.serialize(tree.startPosition), Fen.serialize(vm.uiState.value.selectedNode!!.position))
        compose.onNodeWithTag("library-board-stage").assertIsDisplayed()
    }

    @Test fun realScrollSurvivesOpeningAndReturningToTheSameFilteredList() {
        (0..41).forEach { save("Scroll $it", created = it.toLong()) }
        val vm = model()
        compose.setContent { LumenTheme { GameLibraryRoute(vm) } }
        loaded(vm)
        compose.onNodeWithTag("library-cards").performScrollToIndex(20)
        compose.waitUntil(5_000) { vm.uiState.value.listIndex == 20 }
        val id = vm.uiState.value.entries[20].id
        val bounds = compose.onNodeWithTag("library-card-${id.value}").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("library-card-${id.value}").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.game != null }
        compose.onNodeWithTag("library-back").performClick()
        compose.onNodeWithTag("library-card-${id.value}").assertIsDisplayed()
        assertEquals(bounds, compose.onNodeWithTag("library-card-${id.value}").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun failedRefreshRetriesThePrefixAndCorruptOpenRecoversAfterRepair() {
        (0..40).forEach { save("Retry $it", created = it.toLong()) }
        var failPage = false
        val failing = object : LibraryStore by store {
            override suspend fun page(query: LibraryQuery, cursor: LibraryCursor?): LibraryPage {
                if (failPage) throw IllegalStateException("Transient read failure")
                return store.page(query, cursor)
            }
        }
        val vm = model(source = failing)
        compose.setContent { LumenTheme { GameLibraryRoute(vm) } }
        loaded(vm)
        val first = vm.uiState.value.entries.first().id
        runBlocking { store.favorite(first, true) }
        compose.runOnIdle { failPage = true; vm.refresh() }
        compose.waitUntil(10_000) { vm.uiState.value.listError != null }
        compose.runOnIdle { failPage = false; vm.retryList() }
        loaded(vm)
        assertTrue(vm.uiState.value.entries.first().isFavorite)
        assertEquals(40, vm.uiState.value.entries.size)
        val fingerprint = runBlocking { database.gameDao().gameById(first.value)!!.contentFingerprint }
        runBlocking { database.gameDao().overwriteFingerprint(first.value, "damaged-fingerprint") }
        compose.runOnIdle { vm.open(first) }
        compose.waitUntil(10_000) { vm.uiState.value.openError != null }
        assertNull(vm.uiState.value.game)
        runBlocking { database.gameDao().overwriteFingerprint(first.value, fingerprint) }
        compose.runOnIdle { vm.retryOpen() }
        compose.waitUntil(10_000) { vm.uiState.value.game?.id == first }
        assertNull(vm.uiState.value.openError)
    }

    @Test fun applicationRouteCreatesItsViewModelAndRestoresTheGamesTab() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LumenChessApp() }
        compose.onNodeWithTag("main-tab-games").performClick()
        compose.onNodeWithTag("library-list").assertIsDisplayed()
        compose.onNodeWithTag("library-search").performTextInput("App restoration fixture")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("library-list").assertIsDisplayed()
        compose.onNodeWithTag("library-search").assertTextContains("App restoration fixture")
    }

    @Test fun flagFailureHasARetryThatUpdatesTheRealDatabase() {
        val id = save("Flag retry")
        var fail = true
        val failing = object : LibraryStore by store {
            override suspend fun favorite(id: PersistentGameId, value: Boolean): Boolean {
                if (fail) throw IllegalStateException("Transient write failure")
                return store.favorite(id, value)
            }
        }
        val vm = model(source = failing)
        compose.setContent { LumenTheme { GameLibraryRoute(vm) } }
        loaded(vm)
        compose.runOnIdle { vm.toggleFavorite(vm.uiState.value.entries.single()) }
        compose.waitUntil(10_000) { vm.uiState.value.canRetryAction }
        assertFalse(runBlocking { store.page(LibraryQuery(), null).entries.single().isFavorite })
        compose.runOnIdle { fail = false }
        compose.onNodeWithTag("library-action-retry").performClick()
        compose.waitUntil(10_000) { vm.uiState.value.entries.singleOrNull()?.isFavorite == true }
        assertEquals(id, vm.uiState.value.entries.single().id)
        assertFalse(vm.uiState.value.canRetryAction)
    }
}

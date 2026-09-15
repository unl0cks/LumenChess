package dev.lumenchess.games

import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.BuildConfig
import dev.lumenchess.MainActivity
import dev.lumenchess.arena.ArenaSnapshotCodec
import dev.lumenchess.arena.ArenaScreenMode
import dev.lumenchess.arena.ArenaViewModel
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.*
import dev.lumenchess.data.persistence.*
import dev.lumenchess.settings.DataStoreAppearanceSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Public, opt-in evidence from MainActivity. The host force-stops between these two methods. */
@RunWith(AndroidJUnit4::class)
class GameLibraryReviewQaTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var library: GameLibraryViewModel
    private val ui get() = rule.runOnUiThread { library.uiState.value }
    private val arenaUi get() = rule.runOnUiThread {
        val store = rule.activity.viewModelStore
        val key = store.keys().single { store[it] is ArenaViewModel }
        (store[key] as ArenaViewModel).uiState.value
    }
    private val style = "lumen-vector"
    private var reference: Rect? = null
    private var bounds = JSONArray()
    private val captures = JSONArray()

    @Before fun optIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("m23LibraryQa") == "true")
        assertEquals(37, android.os.Build.VERSION.SDK_INT)
        assertFalse("Public evidence must not contain private assets", BuildConfig.LUMEN_PERSONAL_ASSETS)
        assertEquals(style, PieceSetCatalog.definition(style).id)
    }

    @Test fun captureLibraryAndPrepareRestore() {
        runBlocking { DataStoreAppearanceSettingsRepository.from(rule.activity).update { it.withPieceSet(style) } }
        // These two records are real product saves: native board input, manual Arena and M22 branch UI.
        tag("main-tab-arena").performClick()
        choose("Standard", "arena-game-options")
        choose("Both", "arena-manual-options")
        choose("Until released", "arena-manual-options")
        tag("arena-start").performScrollTo().performClick()
        arenaMove("e2", "e4"); arenaMove("e7", "e5")
        arenaMove("g1", "f3"); arenaMove("b8", "c6")
        val arenaId = savedArenaId()
        tag("arena-branch").performClick()
        tag("arena-history-prev").performClick()
        tag("arena-branch-here").performClick()
        rule.waitUntil(10_000) { arenaUi.branchDraft != null }
        choose("Without clocks", "arena-game-options")
        tag("arena-start").performScrollTo().performClick()
        arenaMove("d7", "d6")
        val branchId = savedArenaId()
        tag("arena-pause").performClick()
        tag("arena-save-variation").performClick()
        rule.waitUntil(10_000) { arenaUi.message?.startsWith("Saved") == true }
        assertNull(arenaUi.runtime!!.pendingEngineSearch)
        assertFalse(arenaUi.runtime!!.clock.enabled)
        val origin = withDb { ArenaSnapshotCodec.decode(GamePersistenceRepository(it).loadGame(branchId)!!).setup.branchOrigin!! }
        assertEquals(arenaId, origin.gameId)
        assertNotNull(origin.nodeId)
        // Live Arena hides the bottom tabs. Stop an already-paused session to return to setup;
        // this closes its adapters without dispatching another pause or modifying saved games.
        assertTrue(arenaUi.runtime!!.paused)
        val arenaBeforeExit = canonical(arenaId).toString()
        val branchBeforeExit = canonical(branchId).toString()
        tag("arena-stop").performClick()
        rule.waitUntil(10_000) {
            arenaUi.let { it.mode == ArenaScreenMode.SETUP && it.ownershipReady }
        }
        assertNull(arenaUi.runtime)
        assertEquals(arenaBeforeExit, canonical(arenaId).toString())
        assertEquals(branchBeforeExit, canonical(branchId).toString())
        tag("main-tab-games").assertIsDisplayed()

        // Synthetic metadata is explicitly labelled; no external account/import operation is implied.
        val standard = seed("M23 fixture 100%_ Standard", Pgn.parseGame("1. e4 e5 (1... c5) 2. Nf3 *"),
            listOf(GameSourceType.LOCAL, GameSourceType.CHESS_COM, GameSourceType.LICHESS, GameSourceType.PGN_IMPORT))
        var chess960Tree = GameTree.create(Chess960.startingPosition(0))
        repeat(2) {
            val node = chess960Tree.mainline().lastOrNull() ?: chess960Tree.root
            chess960Tree = chess960Tree.addMove(node.id, MoveGenerator.legalMoves(node.position).first { it.promotion == null }).tree
        }
        val chess960 = seed("M23 fixture Chess960 index 0", chess960Tree, listOf(GameSourceType.PGN_IMPORT))
        seed("M23 fixture 100XX Standard search decoy", Pgn.parseGame("1. d4 d5 *"), listOf(GameSourceType.LOCAL))
        tag("main-tab-games").performClick()
        loaded()
        tag("library-refresh").performClick(); loaded()
        assertEquals(ui.entries.size, ui.entries.map { it.id }.distinct().size)
        assertEquals(setOf(GameSourceType.LOCAL, GameSourceType.CHESS_COM, GameSourceType.LICHESS, GameSourceType.PGN_IMPORT),
            ui.entries.single { it.id == standard }.sources)
        capture("00-library-mixed-sources")

        val filters = JSONArray()
        for (filter in LibraryFilter.entries.filter { it != LibraryFilter.FAVORITES }) {
            filter(filter)
            val expected = withDb { GameLibraryRepository(it).page(LibraryQuery(filter)).entries.map { entry -> entry.id } }
            assertEquals(expected, ui.entries.map { it.id })
            assertEquals(ui.entries.size, ui.entries.map { it.id }.distinct().size)
            capture("01-filter-${filter.name.lowercase()}")
            filters.put(JSONObject().put("filter", filter.name).put("gameIds", JSONArray(expected.map { it.value })))
        }
        filter(LibraryFilter.ALL)
        search("100%_")
        assertEquals(listOf(standard), ui.entries.map { it.id })
        capture("02-literal-search-fixture")
        card(standard).performTouchInput { longClick() }
        capture("03-favorite-protect-context", dialog = true)
        tag("library-favorite").performClick()
        rule.waitUntil(10_000) { !ui.actionPending && ui.entries.single().isFavorite }
        card(standard).performTouchInput { longClick() }
        tag("library-protect").performClick()
        rule.waitUntil(10_000) { !ui.actionPending && ui.entries.single().isProtected }
        capture("04-persisted-flags")
        search("")
        filter(LibraryFilter.FAVORITES)
        assertTrue(ui.entries.any { it.id == standard && it.isFavorite && it.isProtected })
        capture("05-favorites")
        filter(LibraryFilter.ALL)
        search("M23 fixture")
        tag("library-cards").performScrollToNode(hasTestTag("library-card-${standard.value}"))
        val listIndex = ui.listIndex
        val listOffset = ui.listOffset
        val cardBounds = card(standard).fetchSemanticsNode().boundsInRoot
        val before = canonical(standard)
        open(standard)
        capture("06-standard-root", board = true)
        tag("library-next").performClick()
        capture("07-standard-history-e4", board = true)
        tag("library-history").performScrollToNode(hasTestTag("library-child-1"))
        tag("library-child-1").performClick()
        assertEquals(listOf(0, 1), ui.nodePath)
        assertEquals("c5", ui.selectedNode!!.san)
        capture("08-standard-variation", board = true)
        tag("library-flip").performClick()
        capture("09-standard-flipped", board = true)
        tag("library-end").performClick()
        capture("10-standard-mainline-end", board = true)
        tag("library-root").performClick()
        capture("11-standard-return-root", board = true)
        tag("library-back").performClick(); loaded()
        assertEquals("M23 fixture", ui.query.search)
        assertEquals(LibraryFilter.ALL, ui.query.filter)
        assertEquals(listIndex, ui.listIndex)
        assertEquals(listOffset, ui.listOffset)
        assertEquals(cardBounds, card(standard).fetchSemanticsNode().boundsInRoot)
        capture("12-retained-list-context")
        assertEquals(before.toString(), canonical(standard).toString())

        open(chess960)
        assertEquals(Chess960.startingPosition(0), ui.selectedNode!!.position)
        capture("13-chess960-root", board = true)
        tag("library-next").performClick()
        assertEquals(Variant.CHESS960, ui.selectedNode!!.position.variant)
        capture("14-chess960-history", board = true)
        tag("library-back").performClick(); loaded(); search("")
        open(arenaId)
        capture("15-real-arena-root", board = true)
        repeat(3) { tag("library-next").performClick() }
        tag("library-history").performScrollToNode(hasTestTag("library-child-1"))
        tag("library-child-1").performClick()
        assertEquals("d6", ui.selectedNode!!.san)
        capture("16-real-m22-saved-variation", board = true)
        tag("library-back").performClick(); loaded()
        filter(LibraryFilter.BRANCHES)
        assertTrue(ui.entries.any { it.id == branchId })
        capture("17-real-branch-list")
        open(branchId)
        assertEquals(origin.fen, Fen.serialize(ui.selectedNode!!.position))
        capture("18-real-branch-root", board = true)
        tag("library-next").performClick()
        capture("19-real-branch-history", board = true)

        val records = JSONArray(listOf(standard, chess960, arenaId, branchId).map { canonical(it) })
        write("expected-restore.json", JSONObject().put("pid", Process.myPid())
            .put("standardId", standard.value).put("chess960Id", chess960.value)
            .put("arenaId", arenaId.value).put("branchId", branchId.value)
            .put("branchOriginGameId", origin.gameId.value).put("branchOriginNodeId", origin.nodeId)
            .put("branchOriginFen", origin.fen).put("records", records)
            .put("filters", filters).put("referenceBounds", rect(reference!!))
            .put("fixtureDisclosure", "M23 fixture names and external source tags are synthetic metadata seeded through canonical repositories. Arena and branch records were saved through actual product UI."))
        write("before-process.json", JSONObject().put("pid", Process.myPid()).put("captures", captures))
    }

    @Test fun restoreLibraryAfterProcessDeath() {
        val expected = JSONObject(File(output(), "expected-restore.json").readText())
        assertNotEquals("Host must launch a separate process", expected.getInt("pid"), Process.myPid())
        assertEquals(style, storedStyle())
        val savedBounds = expected.getJSONArray("referenceBounds")
        reference = Rect(savedBounds.getDouble(0).toFloat(), savedBounds.getDouble(1).toFloat(),
            savedBounds.getDouble(2).toFloat(), savedBounds.getDouble(3).toFloat())
        bounds = JSONObject(File(output(), "board-bounds.json").readText()).getJSONArray("samples")
        val records = expected.getJSONArray("records")
        val verified = JSONArray()
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val current = canonical(PersistentGameId(record.getString("gameId")))
            assertEquals("Canonical tree, persistent UUIDs, sources and flags survive", record.toString(), current.toString())
            verified.put(current)
        }
        // Force-stop does not retain Android saved-instance-state. Explicitly reopen through UI.
        tag("main-tab-games").performClick(); loaded()
        assertNull(ui.selectedGameId)
        assertEquals(LibraryQuery(), ui.query)
        capture("20-new-process-library")
        val standard = PersistentGameId(expected.getString("standardId"))
        search("100%_")
        assertEquals(listOf(standard), ui.entries.map { it.id })
        assertTrue(ui.entries.single().isFavorite && ui.entries.single().isProtected)
        capture("21-restored-flags")
        open(standard)
        capture("22-restored-standard-root", board = true)
        tag("library-next").performClick()
        tag("library-history").performScrollToNode(hasTestTag("library-child-1"))
        tag("library-child-1").performClick()
        capture("23-restored-standard-variation", board = true)
        tag("library-back").performClick(); loaded(); search("")
        open(PersistentGameId(expected.getString("chess960Id")))
        assertEquals(Chess960.startingPosition(0), ui.selectedNode!!.position)
        capture("24-restored-chess960-root", board = true)
        tag("library-next").performClick()
        capture("25-restored-chess960-history", board = true)
        tag("library-back").performClick(); loaded()
        filter(LibraryFilter.BRANCHES)
        val branch = PersistentGameId(expected.getString("branchId"))
        open(branch)
        val origin = withDb { ArenaSnapshotCodec.decode(GamePersistenceRepository(it).loadGame(branch)!!).setup.branchOrigin!! }
        assertEquals(expected.getString("branchOriginGameId"), origin.gameId.value)
        assertEquals(expected.getString("branchOriginNodeId"), origin.nodeId)
        assertEquals(expected.getString("branchOriginFen"), origin.fen)
        assertEquals(origin.fen, Fen.serialize(ui.selectedNode!!.position))
        capture("26-restored-m22-branch-root", board = true)
        tag("library-next").performClick()
        capture("27-restored-m22-branch-history", board = true)
        write("restoration.json", JSONObject().put("passed", true)
            .put("beforePid", expected.getInt("pid")).put("afterPid", Process.myPid())
            .put("separateProcess", true).put("repositoryRestorationVerified", true)
            .put("uiSavedStateRestorationClaimed", false)
            .put("restorationMethod", "Host force-stop, new MainActivity, explicit UI reopening of saved canonical records. SavedStateHandle reconstruction and Compose saved-state restoration are covered separately by GameLibraryUiTest.")
            .put("records", verified).put("captures", captures))
    }

    private fun seed(name: String, tree: GameTree, sources: List<GameSourceType>): PersistentGameId = withDb { db ->
        GamePersistenceRepository(db).saveGame(PersistGameRequest(
            tree.withHeaders(tree.headers + mapOf("White" to name, "Black" to "M23 fixture opponent",
                "Event" to "Synthetic native QA fixture", "WhiteElo" to "1820", "Opening" to "Fixture metadata")),
            metadata = GamePersistenceMetadata(timeControl = TimeControlMetadata(180000, 2000)),
            sources = sources.map { GameSourceDraft(it, metadata = mapOf("qa.fixture" to "M23 synthetic metadata")) },
        ))
    }

    private fun arenaMove(from: String, to: String) {
        rule.waitUntil(10_000) { arenaUi.runtime != null }
        val revision = arenaUi.runtime!!.positionRevision
        tag("square-$from").performClick(); tag("square-$to").performClick()
        rule.waitUntil(10_000) { arenaUi.runtime!!.positionRevision != revision }
        assertNull(arenaUi.runtime!!.pendingEngineSearch)
    }

    private fun savedArenaId(): PersistentGameId {
        rule.waitUntil(10_000) { arenaUi.gameId != null }
        val id = PersistentGameId(arenaUi.gameId!!)
        rule.waitUntil(10_000) {
            withDb { db -> GamePersistenceRepository(db).loadGame(id)?.let {
                ArenaSnapshotCodec.decode(it).snapshot.positionRevision == arenaUi.runtime!!.positionRevision
            } == true }
        }
        return id
    }

    private fun canonical(id: PersistentGameId): JSONObject = withDb { db ->
        val game = GamePersistenceRepository(db).loadGame(id)!!
        val entry = GameLibraryRepository(db).page(LibraryQuery(), limit = 100).entries.single { it.id == id }
        JSONObject().put("gameId", id.value).put("pgn", Pgn.serialize(game.tree))
            .put("variant", game.tree.startPosition.variant.name).put("startFen", Fen.serialize(game.tree.startPosition))
            .put("castlingState", game.tree.startPosition.castlingRights.toString())
            .put("historyFens", JSONArray(game.tree.mainline().map { Fen.serialize(it.position) }))
            .put("persistentNodes", JSONArray(db.gameDao().nodesForGame(id.value).sortedBy { it.id }.map {
                JSONObject().put("id", it.id).put("parentId", it.parentNodeId ?: JSONObject.NULL)
                    .put("siblingOrder", it.siblingOrder).put("san", it.san)
            }))
            .put("sources", JSONArray(game.sources.sortedBy { it.id.value }.map {
                JSONObject().put("id", it.id.value).put("type", it.type.name)
                    .put("metadata", JSONObject(it.metadata.toSortedMap()))
            }))
            .put("favorite", entry.isFavorite).put("protected", entry.isProtected)
    }

    private fun capture(name: String, board: Boolean = false, dialog: Boolean = false) {
        rule.waitForIdle()
        assertEquals(style, storedStyle())
        val resolved = PieceSetCatalog.definition(storedStyle()).id
        assertEquals(style, resolved)
        val event = JSONObject().put("name", name).put("pid", Process.myPid())
            .put("storedPieceSetId", storedStyle()).put("resolvedPieceSetId", resolved)
            .put("captureMethod", "Compose captureToImage: actual MainActivity")
        if (board) {
            val stage = tag("library-board-stage")
            val actual = stage.fetchSemanticsNode().boundsInRoot
            if (reference == null) reference = actual
            val ref = reference!!
            val delta = listOf(actual.left - ref.left, actual.top - ref.top, actual.right - ref.right, actual.bottom - ref.bottom)
            bounds.put(JSONObject().put("state", name).put("pid", Process.myPid()).put("bounds", rect(actual))
                .put("deltaPixels", JSONArray(delta)))
            write("board-bounds.json", JSONObject().put("lane", "actual MainActivity Library viewer")
                .put("coordinateSpace", "Compose boundsInRoot pixels").put("referenceBounds", rect(ref))
                .put("requiredDeltaPixels", 0).put("samples", bounds))
            assertEquals("Same Library viewer lane must have exact zero board drift: $name", ref, actual)
            val node = ui.selectedNode!!
            val description = stage.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            assertEquals("${if (ui.flipped) "Black" else "White"} orientation; ${Fen.serialize(node.position)}", description)
            val rendered = rule.onAllNodes(SemanticsMatcher("rendered piece") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("piece-") == true
            }, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
            val expected = node.position.board.mapIndexedNotNull { i, piece ->
                piece?.let { "piece-${Square.fromIndex(i).algebraic}-$style" }
            }
            assertEquals("Every expected piece must render exactly once using the public set", expected.sorted(), rendered.sorted())
            node.position.board.forEachIndexed { i, piece ->
                val square = Square.fromIndex(i).algebraic
                val contents = if (piece == null) "empty"
                    else "${if (piece.color == Color.WHITE) "White" else "Black"} ${piece.type.name.lowercase()}"
                rule.onNodeWithTag("square-$square", useUnmergedTree = true)
                    .assertContentDescriptionEquals("$square, $contents")
            }
            event.put("gameId", ui.game!!.id.value).put("fen", Fen.serialize(node.position))
                .put("variant", node.position.variant.name).put("path", JSONArray(ui.nodePath))
                .put("orientation", if (ui.flipped) "Black" else "White")
                .put("bounds", rect(actual)).put("renderedPieceTags", JSONArray(rendered.sorted()))
            File(output(), "$name-board.png").outputStream().use {
                assertTrue(stage.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        File(output(), "$name.png").outputStream().use {
            val root = if (dialog) rule.onNode(isDialog()) else rule.onRoot()
            assertTrue(root.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        captures.put(event)
        write("progress-${Process.myPid()}.json", JSONObject().put("api", android.os.Build.VERSION.SDK_INT)
            .put("displayMetrics", rule.activity.resources.displayMetrics.toString()).put("captures", captures))
    }

    private fun open(id: PersistentGameId) {
        tag("library-cards").performScrollToNode(hasTestTag("library-card-${id.value}"))
        card(id).performClick()
        rule.waitUntil(10_000) { ui.game?.id == id && !ui.opening }
    }
    private fun loaded() {
        // A tab click schedules AnimatedContent composition. Wait for the real route before
        // touching its activity-owned model; a default provider cannot construct this model.
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("library-list").fetchSemanticsNodes().size == 1 }
        if (!::library.isInitialized) rule.runOnUiThread {
            val store = rule.activity.viewModelStore
            val key = store.keys().single { store[it] is GameLibraryViewModel } as String
            val existing = store[key] as GameLibraryViewModel
            library = ViewModelProvider(rule.activity, GameLibraryViewModel.Factory)[key, GameLibraryViewModel::class.java]
            assertSame("QA must observe the instance already composed by the actual route", existing, library)
        }
        rule.waitUntil(10_000) { !ui.loading }
        rule.waitForIdle()
        assertNull(ui.listError)
    }
    private fun filter(value: LibraryFilter) {
        tag("library-filters").performScrollToNode(hasTestTag("library-filter-${value.name}"))
        tag("library-filter-${value.name}").performClick(); loaded()
        assertEquals(value, ui.query.filter)
    }
    private fun search(value: String) {
        tag("library-search").performTextReplacement(value)
        closeSoftKeyboard(); loaded()
        assertEquals(value, ui.query.search)
    }
    private fun choose(text: String, section: String) = rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(section))).performScrollTo().performClick()
    private fun tag(name: String) = rule.onNodeWithTag(name)
    private fun card(id: PersistentGameId) = tag("library-card-${id.value}")
    private fun storedStyle() = runBlocking { DataStoreAppearanceSettingsRepository.from(rule.activity).settings.first().pieceSetId }
    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    private fun output() = File(rule.activity.getExternalFilesDir(null), "m23-library-native").apply { mkdirs() }
    private fun write(name: String, data: JSONObject) = File(output(), name).writeText(data.toString(2))
    private fun <T> withDb(block: suspend (LumenDatabase) -> T): T {
        val db = LumenDatabaseFactory.open(rule.activity)
        return try { runBlocking { block(db) } } finally { LumenDatabaseFactory.close(db) }
    }
}

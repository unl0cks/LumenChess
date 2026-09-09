package dev.lumenchess.arena

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.BuildConfig
import dev.lumenchess.MainActivity
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.*
import dev.lumenchess.data.persistence.*
import dev.lumenchess.settings.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Opt-in, actual product UI. Host force-stops between the two methods for restoration evidence. */
class ArenaBranchReviewQaTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[ArenaViewModel::class.java]
    private val state get() = requireNotNull(vm.uiState.value.runtime)
    private val style = "private.chesscom.ejgfv"
    private val events = JSONArray()
    private val boundSamples = JSONArray()
    private var reference: Rect? = null

    @Before fun optIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("m22Review") == "true")
        assertEquals(37, android.os.Build.VERSION.SDK_INT)
        assertTrue(BuildConfig.LUMEN_PERSONAL_ASSETS)
        assertEquals(style, PieceSetCatalog.definition(style).id)
    }

    @Test fun branchSaveReturnAndPrepareRestoration() {
        tag("main-tab-settings").performClick()
        tag("settings-play").performClick()
        tag("appearance-dark").performScrollTo().performClick()
        tag("settings-board-pieces").performScrollTo().performClick()
        tag("customization-tab-1").performClick()
        tag("customization-piece-$style").performScrollTo().performClick()
        tag("customization-back").performClick()
        tag("play-settings-back").performClick()
        tag("main-tab-arena").performClick()
        choose("Both", "arena-manual-options")
        choose("Until released", "arena-manual-options")
        tag("arena-start").performScrollTo().performClick()
        move("e2", "e4"); move("e7", "e5"); move("g1", "f3"); move("b8", "c6")
        capture("00-source-current")
        tag("arena-branch").performClick()
        tag("arena-history-prev").performClick()
        capture("01-original-history")
        val original = saved()
        val originalId = original.gameId
        tag("arena-branch-here").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.branchDraft != null }
        choose("Without clocks", "arena-game-options")
        tag("arena-game-options").performScrollTo()
        capture("02-sandbox-configuration", board = false)
        tag("arena-start").performScrollTo().performClick()
        capture("02b-sandbox-origin")
        move("d7", "d6")
        assertFalse(state.clock.enabled)
        capture("03-untimed-alternative")
        assertEquals(original.snapshot.position, load(originalId).snapshot.position)
        tag("arena-control").performClick()
        rule.onAllNodes(hasText("Both") and hasAnyAncestor(hasTestTag("arena-manual-dialog")))[1].performClick()
        val revision = state.positionRevision.value
        rule.waitUntil(35_000) {
            stable(if (state.pendingEngineSearch == null) "engine-result" else "engine-thinking")
            state.positionRevision.value >= revision + 4
        }
        tag("arena-pause").performClick()
        capture("04-engine-continuation")
        assertFalse(state.clock.running)
        assertEquals(original.snapshot.position, load(originalId).snapshot.position)
        tag("arena-save-variation").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.message?.startsWith("Saved") == true }
        capture("05-explicit-variation-save")
        val updated = load(originalId)
        assertEquals(original.snapshot.position, updated.snapshot.position)
        assertEquals(original.snapshot.positionRevision, updated.snapshot.positionRevision)
        assertTrue(updated.snapshot.gameTree.nodes.size > original.snapshot.gameTree.nodes.size)
        tag("arena-original").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.gameId == originalId }
        assertTrue(state.paused)
        capture("06-original-preserved")

        tag("arena-stop").performClick()
        choose("Chess960", "arena-game-options")
        choose("Random Chess960", "arena-opening-options")
        choose("Both", "arena-manual-options")
        choose("Until released", "arena-manual-options")
        tag("arena-start").performScrollTo().performClick()
        legalMove(); legalMove()
        tag("arena-branch").performClick()
        tag("arena-history-prev").performClick()
        tag("arena-branch-here").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.branchDraft != null }
        choose("Without clocks", "arena-game-options")
        tag("arena-start").performScrollTo().performClick()
        legalMove()
        capture("07-chess960-sandbox")
        val persisted = saved()
        assertNotNull(persisted.setup.branchOrigin)
        File(output(), "expected-restore.json").writeText(JSONObject()
            .put("gameId", persisted.gameId).put("fen", Fen.serialize(persisted.snapshot.position))
            .put("revision", persisted.snapshot.positionRevision.value).put("origin", persisted.setup.branchOrigin.toString())
            .put("manual", persisted.snapshot.manualControl.toString()).toString(2))
        saveEvents("before-process.json")
    }

    @Test fun restoreSandboxAfterHostForceStop() {
        val expected = JSONObject(File(output(), "expected-restore.json").readText())
        assertEquals(style, settings().pieceSetId)
        tag("main-tab-arena").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.restorableGame != null }
        assertEquals(expected.getString("gameId"), vm.uiState.value.restorableGame?.gameId)
        tag("arena-resume").performScrollTo()
        capture("08-resume-sandbox", board = false)
        tag("arena-resume").performClick()
        assertEquals(expected.getString("fen"), Fen.serialize(state.position))
        assertEquals(expected.getLong("revision"), state.positionRevision.value)
        assertEquals(expected.getString("origin"), vm.uiState.value.resolvedSetup?.branchOrigin.toString())
        assertEquals(expected.getString("manual"), state.manualControl.toString())
        assertFalse(state.clock.enabled)
        assertNull(state.pendingEngineSearch)
        capture("09-restored-sandbox")
        legalMove()
        stable()
        tag("arena-save-variation").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.message?.startsWith("Saved") == true }
        saveEvents("after-process.json")
    }

    private fun move(from: String, to: String) {
        val revision = state.positionRevision
        tag("square-$from").performClick(); tag("square-$to").performClick()
        rule.waitUntil(5_000) { state.positionRevision != revision }
        stable()
    }
    private fun legalMove() { val move = MoveGenerator.legalMoves(state.position).first { it.promotion == null }; move(move.from.algebraic, move.to.algebraic) }
    private fun choose(text: String, section: String) = rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(section))).performScrollTo().performClick()
    private fun tag(name: String) = rule.onNodeWithTag(name)
    private fun settings() = runBlocking { DataStoreAppearanceSettingsRepository.from(rule.activity).settings.first() }
    private fun output() = File(rule.activity.getExternalFilesDir(null), "m22-review").apply { mkdirs() }
    private fun load(id: String): RestoredArenaGame {
        val db = LumenDatabaseFactory.open(rule.activity)
        return try { ArenaSnapshotCodec.decode(runBlocking { LiveGamePersistenceRepository(db).load(PersistentGameId(id)) }!!) }
        finally { LumenDatabaseFactory.close(db) }
    }
    private fun saved(): RestoredArenaGame {
        rule.waitUntil(10_000) { vm.uiState.value.gameId != null }
        var saved: RestoredArenaGame? = null
        rule.waitUntil(10_000) { saved = load(vm.uiState.value.gameId!!); saved!!.snapshot.positionRevision == state.positionRevision }
        return saved!!
    }
    private fun stable(label: String = "board-interaction") {
        val bounds = tag("arena-board-stage").fetchSemanticsNode().boundsInRoot
        if (reference == null) reference = bounds
        assertEquals("M22 board bounds", reference, bounds)
        boundSamples.put(JSONObject().put("state", label)
            .put("revision", state.positionRevision.value)
            .put("searchPending", state.pendingEngineSearch != null)
            .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))))
    }
    private fun capture(name: String, board: Boolean = true) {
        rule.waitForIdle()
        assertEquals(style, settings().pieceSetId)
        assertEquals(style, PieceSetCatalog.definition(style).id)
        val event = JSONObject().put("name", name).put("elapsedRealtimeMillis", android.os.SystemClock.elapsedRealtime())
            .put("storedPieceSetId", style).put("resolvedPieceSetId", style).put("assetSource", "pieces/ejgfv")
        if (board) {
            stable(name)
            val pieces = rule.onAllNodes(SemanticsMatcher("piece renderer") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("piece-") == true }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(pieces.isNotEmpty())
            pieces.forEach { assertTrue(it.config[SemanticsProperties.TestTag].endsWith("-$style")) }
            val bounds = reference!!
            event.put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                .put("fen", Fen.serialize(state.position)).put("revision", state.positionRevision.value)
                .put("gameId", vm.uiState.value.gameId).put("origin", vm.uiState.value.resolvedSetup?.branchOrigin.toString())
                .put("white", vm.uiState.value.resolvedSetup?.white.toString()).put("black", vm.uiState.value.resolvedSetup?.black.toString())
                .put("manual", state.manualControl.toString()).put("clockEnabled", state.clock.enabled).put("paused", state.paused)
            val historyPly = vm.uiState.value.historyPly
            val displayedPosition = historyPly?.let {
                if (it == 0) state.gameTree.startPosition else state.gameTree.mainline()[it - 1].position
            } ?: state.position
            event.put("historyPly", historyPly).put("displayedFen", Fen.serialize(displayedPosition))
        }
        events.put(event)
        File(output(), "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        saveEvents("progress.json")
    }
    private fun saveEvents(name: String) = File(output(), name).writeText(JSONObject()
        .put("api", 37).put("viewport", "1344x2992@489dpi")
        .put("events", events).put("boardBoundsSamples", boundSamples).toString(2))
}

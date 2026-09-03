package dev.lumenchess.arena

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
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
import dev.lumenchess.board.PersonalPieceMetadataCodec
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.*
import dev.lumenchess.data.persistence.*
import dev.lumenchess.engine.api.*
import dev.lumenchess.play.AndroidPlayEngineGateway
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.runtime.*
import dev.lumenchess.settings.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Opt-in native product flow. Actions use UI; runtime/transport/persistence probes are read-only. */
class ArenaManualReviewQaTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val args get() = InstrumentationRegistry.getArguments()
    private val style get() = args.getString("m21PieceSet") ?: "lumen-vector"
    private val vm get() = ViewModelProvider(rule.activity)[ArenaViewModel::class.java]
    private val state get() = requireNotNull(vm.uiState.value.runtime)
    private val events = JSONArray()
    private var reference: Rect? = null
    private val searches = mutableListOf<Pair<Color, EngineSearchRequest>>()

    @Before fun optIn() {
        assumeTrue(args.getString("m21Review") == "true")
        assertEquals(37, android.os.Build.VERSION.SDK_INT)
        assertEquals(style, PieceSetCatalog.definition(style).id)
        if (style.startsWith("private.")) assertTrue(BuildConfig.LUMEN_PERSONAL_ASSETS)
    }

    @Test fun takeoverClocksRoutingAndPrepareRestoration() {
        selectRendererThroughSettings()
        choose("2000", "arena-white-engine")
        choose("Native", "arena-white-engine")
        choose("1200", "arena-black-engine")
        choose("Humanized", "arena-black-engine")
        start()
        waitRevision(2)
        capture("01-engines-only")
        observeSearches()

        control("White")
        waitTurn(Color.WHITE)
        assertOwnership(RuntimeController.HUMAN, RuntimeController.ENGINE)
        val locked = state.clock
        elapse(650)
        assertEquals(locked, state.clock)
        val move = nextMove()
        tag("square-${move.from.algebraic}").performClick()
        capture("02-white-manual-selected-locked")
        val revision = state.positionRevision.value
        tag("square-${move.to.algebraic}").performClick()
        waitRevision(revision + 2)
        waitTurn(Color.WHITE)
        record("white-tap-and-black-engine-reply")
        control("White", returning = true)
        assertOwnership(RuntimeController.ENGINE, RuntimeController.ENGINE)
        val returned = state.positionRevision.value
        waitRevision(returned + 2)
        record("white-return-thinking-and-result")

        control("Black")
        waitTurn(Color.BLACK)
        assertOwnership(RuntimeController.ENGINE, RuntimeController.HUMAN)
        val drag = nextMove()
        val board = tag("lumen-chessboard").fetchSemanticsNode().boundsInRoot
        val source = tag("square-${drag.from.algebraic}").fetchSemanticsNode().boundsInRoot.center - board.topLeft
        val destination = tag("square-${drag.to.algebraic}").fetchSemanticsNode().boundsInRoot.center - board.topLeft
        val dragRevision = state.positionRevision.value
        tag("lumen-chessboard").performTouchInput {
            down(source)
            moveTo(source + Offset(32f, 0f), delayMillis = 32)
            moveTo(destination, delayMillis = 160)
        }
        tag("dragged-piece").assertExists()
        capture("03-black-manual-held")
        tag("lumen-chessboard").performTouchInput { up() }
        waitRevision(dragRevision + 1)
        record("black-legal-drag-release")
        control("Both")
        assertOwnership(RuntimeController.HUMAN, RuntimeController.HUMAN)
        capture("04-both-manual-locked")
        control("White", returning = true)
        assertOwnership(RuntimeController.ENGINE, RuntimeController.HUMAN)
        record("return-one-side-retains-black")
        control("Both", returning = true)
        assertOwnership(RuntimeController.ENGINE, RuntimeController.ENGINE)
        val bothReturned = state.positionRevision.value
        waitRevision(bothReturned + 2)
        capture("05-returned-engines")
        assertRouting()

        control("Both")
        tag("arena-control").performClick()
        dialogChoice("Count time").performClick()
        assertEquals(ManualClockPolicy.COUNT_TIME, state.manualControl.clockPolicy)
        val side = state.position.sideToMove
        val reading = requireNotNull(vm.uiState.value.clock)
        elapse(750)
        val after = requireNotNull(vm.uiState.value.clock)
        if (side == Color.WHITE) assertTrue(after.whiteRemainingMillis < reading.whiteRemainingMillis)
        else assertTrue(after.blackRemainingMillis < reading.blackRemainingMillis)
        record("06-count-time")
        tag("arena-control").performClick()
        rule.waitForIdle()
        // The platform capture includes the real dialog window and dimmed Live context.
        File(output(), "06-count-time.png").outputStream().use {
            val screenshot = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            screenshot.recycle()
        }
        dialogChoice("Cancel").performClick()
        tapMove()
        tag("arena-stop").performClick()
        waitTag("arena-setup")

        choose("Both", "arena-manual-options")
        rule.onNodeWithText("Manual moves (1–99)").performScrollTo().performTextReplacement("1")
        choose("Clocks paused", "arena-manual-options")
        capture("07-finite-opening-setup", board = false)
        start()
        tapMove()
        assertOwnership(RuntimeController.ENGINE, RuntimeController.HUMAN)
        assertFalse(state.clock.running)
        tapMove()
        assertOwnership(RuntimeController.ENGINE, RuntimeController.ENGINE)
        assertTrue(state.clock.running)
        waitRevision(4)
        record("finite-lease-expiry-and-engine-handoff")
        tag("arena-stop").performClick()
        waitTag("arena-setup")

        choose("Random Chess960", "arena-opening-options")
        rule.onNodeWithText("Manual moves (1–99)").performScrollTo().performTextReplacement("2")
        start()
        assertEquals(Variant.CHESS960, state.position.variant)
        tapMove()
        tapMove()
        assertEquals(1, state.manualControl.white!!.remainingMoves)
        assertEquals(1, state.manualControl.black!!.remainingMoves)
        tag("arena-flip").performClick()
        record("chess960-flipped")
        tag("arena-flip").performClick()
        tag("arena-pause").performClick()
        capture("08-chess960-before-process-stop")
        val saved = persisted()
        val expected = describe("restore-expected").put("gameId", saved.gameId)
        File(output(), "restore-expected.json").writeText(expected.toString(2))
        saveEvents("before.json")
        // The external runner force-stops/reopens the app before the second test.
    }

    @Test fun verifyProcessRestorationAndReturn() {
        val expected = JSONObject(File(output(), "restore-expected.json").readText())
        waitTag("p5-play-overview")
        assertEquals(style, settings().pieceSetId)
        tag("main-tab-arena").performClick()
        rule.waitUntil(10_000) { vm.uiState.value.restorableGame != null }
        val saved = vm.uiState.value.restorableGame!!
        assertEquals(expected.getString("gameId"), saved.gameId)
        assertEquals(expected.getString("fen"), Fen.serialize(saved.snapshot.position))
        assertEquals(expected.getLong("revision"), saved.snapshot.positionRevision.value)
        assertEquals(expected.getString("whiteConfig"), saved.setup.white.toString())
        assertEquals(expected.getString("blackConfig"), saved.setup.black.toString())
        assertEquals(expected.getLong("clockWhite"), saved.snapshot.clock.whiteRemainingMillis)
        assertEquals(expected.getLong("clockBlack"), saved.snapshot.clock.blackRemainingMillis)
        assertEquals(1, saved.snapshot.manualControl.white!!.remainingMoves)
        assertEquals(1, saved.snapshot.manualControl.black!!.remainingMoves)
        assertTrue(saved.snapshot.paused)
        assertFalse(saved.snapshot.clock.running)
        tag("arena-resume").performScrollTo().assertIsDisplayed()
        capture("09-restored-resume-entry", board = false)
        tag("arena-resume").performClick()
        waitTag("arena-live")
        val bounds = expected.getJSONArray("bounds")
        reference = Rect(bounds.getDouble(0).toFloat(), bounds.getDouble(1).toFloat(), bounds.getDouble(2).toFloat(), bounds.getDouble(3).toFloat())
        assertEquals(expected.getString("fen"), Fen.serialize(state.position))
        assertOwnership(RuntimeController.HUMAN, RuntimeController.HUMAN)
        assertNull(state.pendingEngineSearch)
        assertFalse(state.clock.running)
        capture("10-restored-live-manual")
        rule.waitUntil(20_000) { state.engineHostAvailable }
        observeSearches()
        tapMove()
        tapMove()
        assertOwnership(RuntimeController.ENGINE, RuntimeController.ENGINE)
        val revision = state.positionRevision.value
        waitRevision(revision + 2)
        assertRouting()
        record("restored-finite-expiry-and-engine-progression")
        tag("arena-pause").performClick()
        persisted()
        saveEvents("after.json")
    }

    private fun selectRendererThroughSettings() {
        waitTag("p5-play-overview")
        tag("main-tab-settings").performClick()
        tag("settings-play").performClick()
        tag("appearance-dark").performScrollTo().performClick()
        tag("settings-board-pieces").performScrollTo().performClick()
        tag("customization-tab-1").performClick()
        tag("customization-piece-$style").performScrollTo().performClick()
        rule.waitUntil(5_000) { settings().pieceSetId == style }
        tag("customization-back").performClick()
        tag("play-settings-back").performClick()
        tag("main-tab-arena").performClick()
        waitTag("arena-setup")
    }

    private fun choose(label: String, section: String) {
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(section))).performScrollTo().performClick()
    }
    private fun dialogChoice(label: String, index: Int = 0) = rule.onAllNodes(
        hasText(label) and hasAnyAncestor(hasTestTag("arena-manual-dialog")),
    )[index]
    private fun control(label: String, returning: Boolean = false) {
        tag("arena-control").performClick()
        dialogChoice(label, if (returning) 1 else 0).performClick()
        rule.waitForIdle()
        record("${if (returning) "return" else "takeover"}-$label")
    }
    private fun start() {
        tag("arena-start").performScrollTo().performClick()
        waitTag("arena-live")
        stable("start")
    }
    private fun waitTurn(side: Color) {
        rule.waitUntil(25_000) { state.position.sideToMove == side }
    }
    private fun waitRevision(revision: Long) {
        rule.waitUntil(35_000) {
            stable("thinking/result")
            state.positionRevision.value >= revision
        }
    }
    private fun nextMove() = MoveGenerator.legalMoves(state.position).first { it.promotion == null }
    private fun tapMove() {
        val move = nextMove()
        val revision = state.positionRevision.value
        tag("square-${move.from.algebraic}").performClick()
        tag("square-${move.to.algebraic}").performClick()
        waitRevision(revision + 1)
        record("tap-${move.uci}")
    }
    private fun elapse(duration: Long) {
        val start = SystemClock.elapsedRealtime()
        rule.waitUntil(5_000) { SystemClock.elapsedRealtime() - start >= duration }
    }
    private fun assertOwnership(white: RuntimeController, black: RuntimeController) {
        assertEquals(white, state.controllers.white)
        assertEquals(black, state.controllers.black)
        for (side in Color.entries) {
            val human = state.controllers.forSide(side) == RuntimeController.HUMAN
            val manualNodes = rule.onAllNodes(hasText("Manual control", substring = true) and
                hasAnyAncestor(hasTestTag("arena-${side.name.lowercase()}-row"))).fetchSemanticsNodes()
            assertEquals("Visible ownership for $side", human, manualNodes.isNotEmpty())
        }
    }
    private fun observeSearches() {
        rule.runOnUiThread {
            for (side in Color.entries) {
                val gatewayField = ArenaViewModel::class.java.getDeclaredField(if (side == Color.WHITE) "whiteGateway" else "blackGateway").apply { isAccessible = true }
                val gateway = gatewayField.get(vm) as AndroidPlayEngineGateway
                val field = AndroidPlayEngineGateway::class.java.getDeclaredField("session").apply { isAccessible = true }
                val real = field.get(gateway) as EngineSession
                field.set(gateway, object : EngineSession by real {
                    override fun submit(command: EngineSessionCommand) {
                        if (command is EngineSessionCommand.StartSearch) searches += side to command.request
                        real.submit(command)
                    }
                })
            }
        }
    }
    private fun assertRouting() {
        assertEquals(setOf(Color.WHITE, Color.BLACK), searches.map { it.first }.toSet())
        searches.forEach { (side, request) ->
            val setup = vm.uiState.value.resolvedSetup!!
            assertEquals(side, request.position.sideToMove)
            assertEquals(if (side == Color.WHITE) setup.white.strength else setup.black.strength, request.strength)
        }
        events.put(JSONObject().put("realHostSearches", JSONArray(searches.map { (side, request) ->
            JSONObject().put("side", side.name).put("slot", if (side == Color.WHITE) "A" else "B")
                .put("revision", request.positionRevision.value).put("strength", request.strength.toString())
        })))
    }
    private fun persisted(): RestoredArenaGame {
        rule.waitUntil(10_000) { vm.uiState.value.gameId != null }
        val db = LumenDatabaseFactory.open(rule.activity)
        try {
            var saved: RestoredArenaGame? = null
            rule.waitUntil(10_000) {
                val loaded = runBlocking { LiveGamePersistenceRepository(db).load(PersistentGameId(vm.uiState.value.gameId!!)) }
                saved = loaded?.let(ArenaSnapshotCodec::decode)
                saved?.snapshot?.position == state.position && saved?.snapshot?.manualControl == state.manualControl &&
                    saved?.snapshot?.clock?.whiteRemainingMillis == state.clock.whiteRemainingMillis &&
                    saved?.snapshot?.clock?.blackRemainingMillis == state.clock.blackRemainingMillis
            }
            assertTrue(saved!!.snapshot.paused)
            assertFalse(saved!!.snapshot.clock.running)
            return saved!!
        } finally { LumenDatabaseFactory.close(db) }
    }
    private fun describe(name: String): JSONObject {
        assertEquals(style, settings().pieceSetId)
        assertEquals(style, PieceSetCatalog.definition(style).id)
        val setup = vm.uiState.value.resolvedSetup!!
        return JSONObject().put("phase", name).put("storedPieceSetId", style).put("resolvedPieceSetId", style)
            .put("assetSource", PersonalPieceMetadataCodec.decode(BuildConfig.LUMEN_PERSONAL_PIECE_STYLES).firstOrNull { it.id == style }?.assetDirectory ?: "project-owned vector")
            .put("fen", Fen.serialize(state.position)).put("revision", state.positionRevision.value)
            .put("whiteConfig", setup.white.toString()).put("blackConfig", setup.black.toString())
            .put("whiteController", state.controllers.white.name).put("blackController", state.controllers.black.name)
            .put("manualControl", state.manualControl.toString()).put("clockWhite", state.clock.whiteRemainingMillis)
            .put("clockBlack", state.clock.blackRemainingMillis).put("clockRunning", state.clock.running)
            .put("paused", state.paused).put("pendingSearch", state.pendingEngineSearch?.searchId?.value)
            .put("bounds", JSONArray(listOf(bounds().left, bounds().top, bounds().right, bounds().bottom)))
    }
    private fun record(name: String) {
        stable(name)
        events.put(describe(name))
    }
    private fun capture(name: String, board: Boolean = true) {
        rule.waitForIdle()
        if (board) {
            record(name)
            val pieces = rule.onAllNodes(SemanticsMatcher("piece renderer") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("piece-") == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue(pieces.isNotEmpty())
            pieces.forEach { assertTrue(it.config[SemanticsProperties.TestTag].endsWith("-$style")) }
        }
        File(output(), "$name.png").outputStream().use {
            check(rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        saveEvents("progress.json")
    }
    private fun stable(phase: String) {
        val current = bounds()
        if (reference == null) reference = current
        assertEquals("Board movement at $phase", reference, current)
    }
    private fun bounds() = tag("arena-board-stage").fetchSemanticsNode().boundsInRoot
    private fun tag(value: String) = rule.onNodeWithTag(value)
    private fun waitTag(value: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    private fun settings() = runBlocking { DataStoreAppearanceSettingsRepository.from(rule.activity).settings.first() }
    private fun output() = File(rule.activity.getExternalFilesDir(null), "m21-review/$style").apply { mkdirs() }
    private fun saveEvents(name: String) {
        File(output(), name).writeText(JSONObject().put("api", android.os.Build.VERSION.SDK_INT)
            .put("densityDpi", rule.activity.resources.displayMetrics.densityDpi).put("events", events).toString(2))
    }
}

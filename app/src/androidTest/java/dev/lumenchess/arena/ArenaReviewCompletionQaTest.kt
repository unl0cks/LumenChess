package dev.lumenchess.arena

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import dev.lumenchess.settings.*
import java.io.File
import java.io.FileOutputStream
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

/** Opt-in native review. All product actions go through existing UI; diagnostics are read-only. */
@RunWith(AndroidJUnit4::class)
class ArenaReviewCompletionQaTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val args get() = InstrumentationRegistry.getArguments()
    private val style get() = args.getString("m20PieceSet") ?: "lumen-vector"
    private val lane get() = when (style) {
        "private.chesscom.ejgfv" -> "neo"
        "private.chesscom.3d_staunton" -> "3d-staunton"
        else -> "public-lumen"
    }
    private val vm get() = ViewModelProvider(rule.activity)[ArenaViewModel::class.java]
    private val events = JSONArray()
    private var referenceBounds: Rect? = null

    @Before fun explicitLocalOrPublicQaOnly() {
        assumeTrue(args.getString("m20CompletionQa") == "true")
        if (style.startsWith("private.")) {
            assertTrue("Private QA must never silently use a public build", BuildConfig.LUMEN_PERSONAL_ASSETS)
            assertEquals(39, PersonalPieceMetadataCodec.decode(BuildConfig.LUMEN_PERSONAL_PIECE_STYLES).size)
        }
        assertEquals(style, PieceSetCatalog.definition(style).id)
    }

    @Test fun completeSetupOpeningAndAssignmentFlows() {
        openArenaThroughSettings()
        configureAsymmetricEngines()
        tag("arena-start").performScrollTo().assertIsEnabled().assertIsDisplayed()
        val cta = tag("arena-start").fetchSemanticsNode().boundsInRoot
        val nav = tag("main-tab-arena").fetchSemanticsNode().boundsInRoot
        assertTrue("Start Arena must sit above persistent navigation", cta.bottom <= nav.top)
        capture("01-setup-bottom-valid", board = false)

        startPaused("02-fixed-normal-start")
        assertEquals(Position.initial(), vm.uiState.value.resolvedSetup!!.initialPosition)
        assertEquals(PlayEngine.STOCKFISH_18, vm.uiState.value.resolvedSetup!!.white.engine)
        assertEquals(PlayEngine.RECKLESS_0_9_0, vm.uiState.value.resolvedSetup!!.black.engine)
        progressWithRealHosts("03-fixed-progressed")
        tag("arena-flip").performClick()
        capture("04-fixed-flipped")
        stop()

        choose("Random opening", "arena-opening-options")
        choose("4 ply", "arena-opening-options")
        repeat(2) { index ->
            startPaused("05-random-opening-${index + 1}")
            val opening = vm.uiState.value.resolvedSetup!!.opening
            assertEquals(ArenaOpeningMode.RANDOM_OPENING, opening.mode)
            assertEquals(4, opening.appliedMoves.size)
            val line = ArenaOpeningCatalog.lines.single { it.name == opening.label }
            assertEquals(line.moves.take(4), opening.appliedMoves.map { it.uci })
            progressWithRealHosts("random-opening-${index + 1}-progress", screenshot = false)
            stop()
        }

        choose("Opening family", "arena-opening-options")
        choose("Queen's Pawn", "arena-opening-options")
        tag("arena-start").performScrollTo().assertIsEnabled()
        capture("06-opening-family-setup", board = false)
        startPaused("07-opening-family-live")
        assertEquals("queens-pawn", vm.uiState.value.resolvedSetup!!.opening.familyId)
        assertEquals(ArenaOpeningMode.OPENING_FAMILY, vm.uiState.value.resolvedSetup!!.opening.mode)
        assertTrue(vm.uiState.value.resolvedSetup!!.opening.label in listOf("Queen's Gambit", "Slav Defence", "King's Indian Defence"))
        progressWithRealHosts("family-progress", screenshot = false)
        stop()

        choose("Custom FEN", "arena-opening-options")
        tag("arena-start").performScrollTo().assertIsNotEnabled()
        capture("08-custom-fen-empty", board = false)
        tag("arena-custom-fen").performScrollTo().performTextReplacement("not a chess position")
        rule.activityRule.scenario.onActivity { it.window.decorView.clearFocus() }
        tag("arena-start").performScrollTo().assertIsNotEnabled()
        capture("09-custom-fen-invalid", board = false)
        assertEquals(ArenaScreenMode.SETUP, vm.uiState.value.mode)
        tag("arena-custom-fen").performScrollTo().performTextReplacement(VALID_FEN)
        tag("arena-start").performScrollTo().assertIsEnabled()
        capture("10-custom-fen-valid", board = false)
        startPaused("11-custom-fen-live")
        assertEquals(VALID_FEN, Fen.serialize(vm.uiState.value.resolvedSetup!!.initialPosition))
        progressWithRealHosts("fen-progress", screenshot = false)
        stop()

        choose("Random Chess960", "arena-opening-options")
        startPaused("12-random-chess960")
        val resolved = vm.uiState.value.resolvedSetup!!
        assertEquals(Variant.CHESS960, resolved.variant)
        assertTrue(resolved.chess960Index!! in 0..959)
        assertEquals(Chess960.startingPosition(resolved.chess960Index!!), resolved.initialPosition)
        val rank = Fen.serialize(resolved.initialPosition).substringBefore('/').lowercase()
        val bishops = rank.indices.filter { rank[it] == 'b' }
        val rooks = rank.indices.filter { rank[it] == 'r' }
        assertEquals(2, bishops.size)
        assertNotEquals(bishops[0] % 2, bishops[1] % 2)
        assertTrue(rooks[0] < rank.indexOf('k') && rank.indexOf('k') < rooks[1])
        progressWithRealHosts("13-chess960-progressed")
        stop()

        choose("Standard", "arena-game-options")
        choose("Normal start", "arena-opening-options")
        choose("Random", "arena-game-options")
        startPaused("14-random-colors")
        val assigned = vm.uiState.value.resolvedSetup!!
        assertEquals(setOf(PlayEngine.STOCKFISH_18, PlayEngine.RECKLESS_0_9_0), setOf(assigned.white.engine, assigned.black.engine))
        progressWithRealHosts("15-random-colors-progressed")
        verifyCanonicalRecord("random-colors")
        writeEvents("setup-opening-assignment.json")
    }

    @Test fun selectedRendererStartingProgressionAndFlip() {
        openArenaThroughSettings()
        startPaused("20-renderer-starting")
        progressWithRealHosts("21-renderer-middlegame", minimumMoves = 8)
        tag("arena-flip").performClick()
        capture("22-renderer-flipped")
        writeEvents("renderer.json")
    }

    @Test fun prepareProcessRestoration() {
        openArenaThroughSettings()
        configureAsymmetricEngines()
        choose("Random", "arena-game-options")
        startPaused("30-restore-initial")
        progressWithRealHosts("31-before-process-stop", minimumMoves = 4)
        val canonical = verifyCanonicalRecord("before-process-stop")
        val restored = ArenaSnapshotCodec.decode(canonical)
        assertTrue(restored.snapshot.paused)
        assertFalse(restored.snapshot.clock.running)
        val expected = JSONObject()
            .put("gameId", canonical.id.value)
            .put("fen", Fen.serialize(restored.snapshot.position))
            .put("revision", restored.snapshot.positionRevision.value)
            .put("white", restored.setup.white.toString())
            .put("black", restored.setup.black.toString())
            .put("clockWhite", restored.snapshot.clock.whiteRemainingMillis)
            .put("clockBlack", restored.snapshot.clock.blackRemainingMillis)
            .put("bounds", rectJson(bounds()))
        File(output(), "restore-expected.json").writeText(expected.toString(2))
        writeEvents("restore-before.json")
        // Runner force-stops the app between this test process and verifyProcessRestoration.
    }

    @Test fun verifyProcessRestoration() {
        val expected = JSONObject(File(output(), "restore-expected.json").readText())
        openArenaThroughSettings()
        rule.waitUntil(10_000) { vm.uiState.value.restorableGame != null }
        val restored = vm.uiState.value.restorableGame!!
        assertEquals(expected.getString("gameId"), restored.gameId)
        assertEquals(expected.getString("fen"), Fen.serialize(restored.snapshot.position))
        assertEquals(expected.getLong("revision"), restored.snapshot.positionRevision.value)
        assertEquals(expected.getString("white"), restored.setup.white.toString())
        assertEquals(expected.getString("black"), restored.setup.black.toString())
        assertEquals(expected.getLong("clockWhite"), restored.snapshot.clock.whiteRemainingMillis)
        assertEquals(expected.getLong("clockBlack"), restored.snapshot.clock.blackRemainingMillis)
        assertTrue(restored.snapshot.paused)
        assertFalse(restored.snapshot.clock.running)
        tag("arena-resume").performScrollTo().assertIsDisplayed()
        capture("32-restoration-entry", board = false)
        tag("arena-resume").performClick()
        waitTag("arena-live")
        tag("arena-pause").performClick()
        rule.waitUntil(5_000) { vm.uiState.value.runtime?.paused == true }
        assertEquals(expected.getString("gameId"), vm.uiState.value.gameId)
        assertEquals(expected.getString("fen"), Fen.serialize(vm.uiState.value.runtime!!.position))
        assertNull(vm.uiState.value.runtime!!.pendingEngineSearch)
        val prior = expected.getJSONArray("bounds")
        referenceBounds = Rect(prior.getDouble(0).toFloat(), prior.getDouble(1).toFloat(), prior.getDouble(2).toFloat(), prior.getDouble(3).toFloat())
        capture("33-restored-live")
        events.put(JSONObject().put("restoration", "paused canonical snapshot; explicit Resume Arena resumes, then QA pauses via UI").put("inFlightPersisted", false))
        progressWithRealHosts("34-restored-progressed")
        writeEvents("restore-after.json")
    }

    private fun configureAsymmetricEngines() {
        choose("Stockfish 18", "arena-white-engine")
        choose("2000", "arena-white-engine")
        choose("Native", "arena-white-engine")
        choose("Reckless 0.9.0", "arena-black-engine")
        choose("1200", "arena-black-engine")
        choose("Humanized", "arena-black-engine")
        assertEquals(EngineStrengthTarget.Elo(2000), vm.uiState.value.setup.white.strengthTarget)
        assertEquals(EngineStrengthTarget.Elo(1200), vm.uiState.value.setup.black.strengthTarget)
        assertEquals(EngineStrengthModel.ENGINE_NATIVE, vm.uiState.value.setup.white.strengthModel)
        assertEquals(EngineStrengthModel.HUMANIZED, vm.uiState.value.setup.black.strengthModel)
    }

    private fun openArenaThroughSettings() {
        waitTag("p5-play-overview")
        tag("main-tab-settings").performClick()
        waitTag("settings-category-list")
        tag("settings-play").performClick()
        waitTag("play-settings-root")
        tag("appearance-dark").performScrollTo().performClick()
        tag("settings-board-pieces").performScrollTo().performClick()
        waitTag("derivative-board-appearance")
        tag("customization-tab-1").performClick()
        tag("customization-piece-$style").performScrollTo().performClick()
        rule.waitUntil(5_000) { settings().pieceSetId == style }
        capture("00-selected-settings", board = false)
        tag("customization-back").performClick()
        tag("play-settings-back").performClick()
        tag("main-tab-arena").performClick()
        waitTag("arena-setup")
    }

    private fun choose(label: String, section: String) {
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(section)))
            .performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun startPaused(name: String) {
        tag("arena-start").performScrollTo().assertIsEnabled().performClick()
        waitTag("arena-live")
        tag("arena-pause").performClick()
        rule.waitUntil(5_000) { vm.uiState.value.runtime?.paused == true }
        assertEquals("Must capture the actual initial position, not a later revision", 0L, vm.uiState.value.runtime!!.positionRevision.value)
        assertEquals(vm.uiState.value.resolvedSetup!!.initialPosition, vm.uiState.value.runtime!!.position)
        capture(name)
    }

    private fun stop() {
        tag("arena-stop").performClick()
        waitTag("arena-setup")
    }

    private fun progressWithRealHosts(name: String, screenshot: Boolean = true, minimumMoves: Long = 3) {
        rule.waitUntil(20_000) { vm.uiState.value.runtime?.engineHostAvailable == true }
        val requests = mutableListOf<Pair<Color, EngineSearchRequest>>()
        installReadOnlySessionTap(Color.WHITE, requests)
        installReadOnlySessionTap(Color.BLACK, requests)
        val start = vm.uiState.value.runtime!!.positionRevision.value
        tag("arena-pause").performClick()
        rule.waitUntil(45_000) {
            assertStableBounds("$name-thinking-result")
            vm.uiState.value.runtime!!.positionRevision.value >= start + minimumMoves
        }
        tag("arena-pause").performClick()
        rule.waitUntil(5_000) { vm.uiState.value.runtime!!.paused }
        val setup = vm.uiState.value.resolvedSetup!!
        assertEquals(setOf(Color.WHITE, Color.BLACK), requests.map { it.first }.toSet())
        val requestJson = JSONArray()
        requests.forEach { (side, request) ->
            assertEquals(side, request.position.sideToMove)
            val engine = if (side == Color.WHITE) setup.white else setup.black
            assertEquals(engine.strength, request.strength)
            requestJson.put(JSONObject().put("side", side.name).put("engine", engine.engine.name)
                .put("slot", if (side == Color.WHITE) "A" else "B").put("strength", request.strength.toString())
                .put("searchId", request.searchId.value).put("revision", request.positionRevision.value))
        }
        events.put(JSONObject().put("phase", name).put("realBinderSearches", requestJson))
        assertNotNull(vm.uiState.value.evaluation)
        if (screenshot) capture(name) else record(name)
    }

    /** Observe the real transport-neutral submission boundary, forwarding every command unchanged. */
    private fun installReadOnlySessionTap(side: Color, requests: MutableList<Pair<Color, EngineSearchRequest>>) {
        rule.runOnUiThread {
            val gatewayField = ArenaViewModel::class.java.getDeclaredField(if (side == Color.WHITE) "whiteGateway" else "blackGateway").apply { isAccessible = true }
            val gateway = gatewayField.get(vm) as AndroidPlayEngineGateway
            val field = AndroidPlayEngineGateway::class.java.getDeclaredField("session").apply { isAccessible = true }
            val real = field.get(gateway) as EngineSession
            field.set(gateway, object : EngineSession by real {
                override fun submit(command: EngineSessionCommand) {
                    if (command is EngineSessionCommand.StartSearch) requests += side to command.request
                    real.submit(command)
                }
            })
        }
    }

    private fun verifyCanonicalRecord(phase: String): LoadedCanonicalGame {
        rule.waitUntil(10_000) { vm.uiState.value.gameId != null }
        val id = vm.uiState.value.gameId!!
        val database = LumenDatabaseFactory.open(rule.activity)
        try {
            var result: LoadedCanonicalGame? = null
            rule.waitUntil(10_000) {
                result = runBlocking { LiveGamePersistenceRepository(database).load(PersistentGameId(id)) }
                result?.tree?.mainline()?.size?.toLong() == vm.uiState.value.runtime!!.positionRevision.value
            }
            val game = result!!
            val setup = vm.uiState.value.resolvedSetup!!
            assertEquals(setup.white.engine.displayName, game.whiteParticipant!!.displayName)
            assertEquals(setup.black.engine.displayName, game.blackParticipant!!.displayName)
            assertTrue(game.sources.any { it.type == GameSourceType.ENGINE_ARENA })
            val decoded = ArenaSnapshotCodec.decode(game)
            assertEquals(setup.white, decoded.setup.white)
            assertEquals(setup.black, decoded.setup.black)
            assertEquals(vm.uiState.value.runtime!!.position, decoded.snapshot.position)
            assertTrue(decoded.snapshot.paused)
            events.put(JSONObject().put("phase", phase).put("canonicalGameId", id)
                .put("whiteParticipant", game.whiteParticipant!!.displayName)
                .put("blackParticipant", game.blackParticipant!!.displayName).put("restorePaused", true))
            return game
        } finally { LumenDatabaseFactory.close(database) }
    }

    private fun capture(name: String, board: Boolean = true) {
        rule.waitForIdle()
        record(name, board)
        bitmap("$name.png", rule.onRoot().captureToImage().asAndroidBitmap())
        if (board && (name.contains("renderer") || name == "33-restored-live")) {
            bitmap("$name-board.png", tag("arena-board-stage").captureToImage().asAndroidBitmap())
        }
    }

    private fun record(name: String, board: Boolean = true) {
        val settings = settings()
        assertEquals(AppAppearance.DARK, settings.appearance)
        assertEquals(style, settings.pieceSetId)
        assertEquals(style, PieceSetCatalog.definition(settings.pieceSetId).id)
        val entry = JSONObject().put("phase", name).put("storedPieceSetId", settings.pieceSetId)
            .put("resolvedPieceSetId", PieceSetCatalog.definition(settings.pieceSetId).id)
            .put("assetSource", PersonalPieceMetadataCodec.decode(BuildConfig.LUMEN_PERSONAL_PIECE_STYLES)
                .firstOrNull { it.id == style }?.assetDirectory ?: "project-owned vector")
        if (board) {
            assertStableBounds(name)
            val pieces = rule.onAllNodes(SemanticsMatcher("piece renderer identity") {
                it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("piece-") == true
            }, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("Must observe actual board piece renderer tags", pieces.isNotEmpty())
            pieces.forEach { assertTrue(it.config[SemanticsProperties.TestTag].endsWith("-$style")) }
            val state = vm.uiState.value
            entry.put("bounds", rectJson(bounds())).put("fen", Fen.serialize(state.runtime!!.position))
                .put("revision", state.runtime.positionRevision.value).put("paused", state.runtime.paused)
                .put("orientation", state.orientation.name).put("opening", state.resolvedSetup!!.opening.label)
                .put("openingMode", state.resolvedSetup.opening.mode.name).put("openingFamily", state.resolvedSetup.opening.familyId)
                .put("initialFen", Fen.serialize(state.resolvedSetup.initialPosition))
                .put("chess960Index", state.resolvedSetup.chess960Index)
                .put("white", state.resolvedSetup.white.toString()).put("black", state.resolvedSetup.black.toString())
                .put("randomSeed", "product RNG; no QA seed injected")
        }
        events.put(entry)
    }

    private fun assertStableBounds(phase: String) {
        val now = bounds()
        if (referenceBounds == null) referenceBounds = now
        assertEquals("Board moved at $phase", referenceBounds, now)
    }

    private fun bounds() = tag("arena-board-stage").fetchSemanticsNode().boundsInRoot
    private fun rectJson(rect: Rect) = JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))
    private fun tag(value: String) = rule.onNodeWithTag(value)
    private fun waitTag(value: String) {
        rule.waitUntil(10_000) { rule.onAllNodesWithTag(value).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }
    private fun settings() = runBlocking { DataStoreAppearanceSettingsRepository.from(rule.activity).settings.first() }
    private fun output() = File(rule.activity.getExternalFilesDir(null), "m20-review-completion/$lane").apply { mkdirs() }
    private fun bitmap(name: String, bitmap: Bitmap) {
        FileOutputStream(File(output(), name)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun writeEvents(name: String) {
        File(output(), name).writeText(JSONObject().put("api", android.os.Build.VERSION.SDK_INT)
            .put("densityDpi", rule.activity.resources.displayMetrics.densityDpi)
            .put("events", events).toString(2))
    }

    companion object {
        private const val VALID_FEN = "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 2 3"
    }
}

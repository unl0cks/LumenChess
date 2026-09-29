package dev.lumenchess.analysis.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lumenchess.analysis.engine.AnalysisSnapshot
import dev.lumenchess.analysis.engine.EngineAnalysisClient
import dev.lumenchess.analysis.eval.ExpectedPoints
import dev.lumenchess.analysis.explorer.ExplorerIndex
import dev.lumenchess.analysis.explorer.IndexedGame
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.openings.Opening
import dev.lumenchess.analysis.review.ReviewPreset
import dev.lumenchess.analysis.review.ReviewSettings
import dev.lumenchess.analysis.review.ReviewedMove
import dev.lumenchess.analysis.tree.TreeEditing
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameNode
import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Rules
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.GameSourceDraft
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.LoadedCanonicalGame
import dev.lumenchess.data.persistence.PersistGameRequest
import dev.lumenchess.data.persistence.PersistentGameId
import dev.lumenchess.data.persistence.ReviewRecord
import dev.lumenchess.review.LoadedReview
import dev.lumenchess.review.ReviewCoordinator
import dev.lumenchess.review.ReviewLoader
import dev.lumenchess.review.ReviewRunProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Analysis screen was opened on. */
sealed interface AnalysisRequest {
    /** A saved game; [review] opens straight into Game Review (and starts one when missing). */
    data class LibraryGame(val gameId: String, val review: Boolean = false, val startPly: Int? = null) : AnalysisRequest
    /** An empty board from a position (null FEN = the normal start). */
    data class FromPosition(val fen: String? = null, val variant: Variant = Variant.STANDARD) : AnalysisRequest
}

enum class AnalysisPane(val label: String) { REVIEW("Review"), LINES("Engine"), MOVES("Moves"), EXPLORER("Explorer") }

enum class ReviewMode(val label: String) { GUIDED("Guided"), FULL("Full"), KEY_MOMENTS("Key moments") }

data class AnalysisUiState(
    val request: AnalysisRequest? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val title: String = "Analysis",
    val game: LoadedCanonicalGame? = null,
    val tree: GameTree = GameTree.create(),
    val nodeId: GameNodeId = tree.rootId,
    val flipped: Boolean = false,
    val engineEnabled: Boolean = true,
    val engineStatus: EngineAnalysisClient.Status = EngineAnalysisClient.Status.CONNECTING,
    val engineMessage: String? = null,
    val multiPv: Int = 3,
    val snapshot: AnalysisSnapshot? = null,
    val pane: AnalysisPane = AnalysisPane.LINES,
    val review: LoadedReview? = null,
    val reviewRecord: ReviewRecord? = null,
    val reviewProgress: ReviewRunProgress? = null,
    val reviewMode: ReviewMode = ReviewMode.GUIDED,
    val reviewPreset: ReviewPreset = ReviewPreset.BALANCED,
    /** The mainline as loaded; variations the user adds never change which moves were reviewed. */
    val mainlineIds: List<GameNodeId> = emptyList(),
    val opening: Opening? = null,
    val edited: Boolean = false,
    val saveMessage: String? = null,
    val explorer: ExplorerIndex? = null,
    val explorerLoading: Boolean = false,
    val explorerError: String? = null,
) {
    val node: GameNode get() = tree.node(nodeId)
    val position: Position get() = node.position

    /** 0-based mainline ply of the current node's move, or null off the mainline / at the start. */
    val mainlinePly: Int? get() = mainlineIds.indexOf(nodeId).takeIf { it >= 0 }
    val reviewedMove: ReviewedMove? get() = mainlinePly?.let { review?.moves?.getOrNull(it) }
    val canReview: Boolean get() = game != null && mainlineIds.isNotEmpty()

    /** Engine score for White (current snapshot, else the stored review), for the evaluation bar. */
    val whitePoints: Double?
        get() {
            val best = snapshot?.takeIf { it.position == position }?.best
            if (best != null) return ExpectedPoints.forWhite(best.score, position.sideToMove)
            val ply = mainlinePly
            val summary = review?.summary ?: return null
            return if (ply == null) {
                if (nodeId == tree.rootId) summary.graph.firstOrNull() else null
            } else summary.graph.getOrNull(ply + 1)
        }

    val whiteScore: Score?
        get() {
            val best = snapshot?.takeIf { it.position == position }?.best ?: return reviewScoreForWhite()
            return if (position.sideToMove == dev.lumenchess.core.chess.Color.WHITE) best.score else best.score.negated()
        }

    private fun reviewScoreForWhite(): Score? {
        val move = reviewedMove ?: return null
        // scoreAfter is the mover's view of the position after the move.
        return if (move.mover == dev.lumenchess.core.chess.Color.WHITE) move.scoreAfter else move.scoreAfter.negated()
    }
}

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val _ui = mutableStateOf(AnalysisUiState())
    val uiState: State<AnalysisUiState> = _ui
    private var ui: AnalysisUiState
        get() = _ui.value
        set(value) { _ui.value = value }

    private var client: EngineAnalysisClient? = null
    private var visible = false
    private var loadJob: Job? = null
    private var lastPublish = 0L
    /** Position and line count of the search in flight, so re-entry never restarts it needlessly. */
    private var activeKey: Pair<Position, Int>? = null

    init {
        viewModelScope.launch {
            ReviewCoordinator.progress.collectLatest { all ->
                val id = ui.game?.id?.value ?: return@collectLatest
                ui = ui.copy(reviewProgress = all[id])
            }
        }
        viewModelScope.launch {
            ReviewCoordinator.finished.collect { gameId ->
                if (ui.game?.id?.value == gameId) reloadReview()
            }
        }
    }

    fun open(request: AnalysisRequest) {
        if (ui.request == request && ui.error == null) return
        stopEngine()
        loadJob?.cancel()
        ui = AnalysisUiState(request = request, loading = true, engineEnabled = ui.engineEnabled, multiPv = ui.multiPv,
            reviewPreset = ui.reviewPreset, flipped = false, explorer = ui.explorer)
        loadJob = viewModelScope.launch {
            try {
                when (request) {
                    is AnalysisRequest.FromPosition -> {
                        val start = request.fen?.let { Fen.parse(it, request.variant) }
                            ?: if (request.variant == Variant.CHESS960) dev.lumenchess.core.chess.Chess960.startingPosition(518) else Position.initial()
                        val tree = GameTree.create(start)
                        ui = ui.copy(loading = false, title = "Analysis", tree = tree, nodeId = tree.rootId,
                            pane = AnalysisPane.LINES, opening = null)
                    }
                    is AnalysisRequest.LibraryGame -> {
                        val game = AppData.games(getApplication<Application>()).loadGame(PersistentGameId(request.gameId))
                            ?: throw IllegalStateException("This game is no longer in the library.")
                        val mainline = game.tree.mainline().map { it.id }
                        val opening = withContext(Dispatchers.Default) { AppData.openingBook.identify(game.tree) }
                        val (record, review) = ReviewLoader.load(getApplication<Application>(), game)
                        val start = request.startPly?.let { ply -> mainline.getOrNull(ply - 1) }
                            ?: if (request.review) game.tree.rootId else mainline.lastOrNull() ?: game.tree.rootId
                        val userIsBlack = game.blackParticipant?.kind == dev.lumenchess.data.persistence.ParticipantKind.HUMAN_LOCAL &&
                            game.whiteParticipant?.kind != dev.lumenchess.data.persistence.ParticipantKind.HUMAN_LOCAL
                        ui = ui.copy(
                            loading = false,
                            title = if (request.review) "Game Review" else "Analysis",
                            game = game,
                            tree = game.tree,
                            nodeId = start,
                            mainlineIds = mainline,
                            review = review,
                            reviewRecord = record,
                            reviewProgress = ReviewCoordinator.progress.value[game.id.value],
                            opening = opening,
                            flipped = userIsBlack,
                            pane = if (request.review || review != null) AnalysisPane.REVIEW else AnalysisPane.LINES,
                        )
                        if (request.review && review == null && !ReviewCoordinator.isBusy(game.id.value) && mainline.isNotEmpty()) {
                            startReview()
                        }
                    }
                }
                refreshEngine()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                ui = ui.copy(loading = false, error = error.message ?: "Could not open this game.")
            }
        }
    }

    fun retry() {
        val request = ui.request ?: return
        ui = ui.copy(request = null)
        open(request)
    }

    // --- Navigation ---------------------------------------------------------------------------

    fun select(nodeId: GameNodeId) {
        if (nodeId !in ui.tree.nodes) return
        ui = ui.copy(nodeId = nodeId)
        refreshEngine()
    }

    fun toStart() = select(ui.tree.rootId)
    fun back() { ui.node.parentId?.let(::select) }
    fun forward() { ui.tree.mainlineChildOf(ui.nodeId)?.let { select(it.id) } }
    fun toEnd() {
        var current = ui.nodeId
        while (true) current = ui.tree.mainlineChildOf(current)?.id ?: break
        select(current)
    }
    fun selectMainlinePly(ply: Int) {
        if (ply < 0) select(ui.tree.rootId) else ui.mainlineIds.getOrNull(ply)?.let(::select)
    }
    fun flip() { ui = ui.copy(flipped = !ui.flipped) }

    fun nextKeyMoment() {
        val moments = ui.review?.summary?.keyMoments ?: return
        val current = ui.mainlinePly ?: -1
        moments.firstOrNull { it > current }?.let(::selectMainlinePly)
    }

    fun previousKeyMoment() {
        val moments = ui.review?.summary?.keyMoments ?: return
        val current = ui.mainlinePly ?: Int.MAX_VALUE
        moments.lastOrNull { it < current }?.let(::selectMainlinePly)
    }

    // --- Editing ------------------------------------------------------------------------------

    /** A move made on the board: follows an existing continuation or creates a variation. */
    fun play(move: Move) {
        if (Rules.termination(ui.position) != null) return
        val (tree, id) = TreeEditing.play(ui.tree, ui.nodeId, move)
        val created = tree !== ui.tree
        ui = ui.copy(tree = tree, nodeId = id, edited = ui.edited || created, saveMessage = null)
        refreshEngine()
    }

    /** Goes back to before mainline move [ply] and plays the engine's choice there instead. */
    fun tryBest(ply: Int) {
        val best = ui.review?.moves?.getOrNull(ply)?.bestMove ?: return
        val parent = if (ply == 0) ui.tree.rootId else ui.mainlineIds.getOrNull(ply - 1) ?: return
        ui = ui.copy(nodeId = parent)
        play(best)
    }

    /** Plays the engine's first line move. */
    fun playBest() {
        val best = ui.snapshot?.takeIf { it.position == ui.position }?.best?.moves?.firstOrNull() ?: return
        play(best)
    }

    fun promote(nodeId: GameNodeId) = edit { TreeEditing.promote(ui.tree, nodeId) }

    fun delete(nodeId: GameNodeId) {
        if (nodeId == ui.tree.rootId || nodeId in ui.mainlineIds) return
        val parent = ui.tree.node(nodeId).parentId
        edit(select = parent) { TreeEditing.delete(ui.tree, nodeId) }
    }

    private fun edit(select: GameNodeId? = null, block: () -> TreeEditing.Edit) {
        val edit = block()
        val target = edit.map(select ?: ui.nodeId) ?: edit.tree.rootId
        ui = ui.copy(
            tree = edit.tree,
            nodeId = target,
            mainlineIds = ui.mainlineIds.mapNotNull { edit.map(it) },
            edited = true,
            saveMessage = null,
        )
        refreshEngine()
    }

    /** Saves the analysed tree as its own library game (source: Branches / Analysis). */
    fun saveAnalysis() {
        val tree = ui.tree
        if (tree.mainline().isEmpty() && tree.childrenOf(tree.rootId).isEmpty()) return
        viewModelScope.launch {
            ui = try {
                val headers = LinkedHashMap(tree.headers).apply {
                    put("Event", "LumenChess analysis")
                    ui.game?.let { put("Annotator", "LumenChess") }
                }
                AppData.games(getApplication<Application>()).saveGame(
                    PersistGameRequest(
                        tree = rebuildWithHeaders(tree, headers),
                        sources = listOf(GameSourceDraft(GameSourceType.BRANCH, importedAtEpochMillis = System.currentTimeMillis())),
                    ),
                )
                ui.copy(edited = false, saveMessage = "Saved to Games › Branches / Analysis")
            } catch (error: Exception) {
                ui.copy(saveMessage = "Could not save: ${error.message ?: "storage error"}")
            }
        }
    }

    private fun rebuildWithHeaders(tree: GameTree, headers: Map<String, String>): GameTree = tree.withHeaders(headers)

    // --- Engine -------------------------------------------------------------------------------

    fun setVisible(visible: Boolean) {
        this.visible = visible
        if (visible) refreshEngine() else stopEngine()
    }

    fun setEngineEnabled(enabled: Boolean) {
        ui = ui.copy(engineEnabled = enabled, snapshot = if (enabled) ui.snapshot else null)
        if (enabled) refreshEngine() else stopEngine()
    }

    fun setMultiPv(lines: Int) {
        ui = ui.copy(multiPv = lines.coerceIn(1, EngineAnalysisClient.MAX_LINES))
        refreshEngine()
    }

    fun setPane(pane: AnalysisPane) {
        ui = ui.copy(pane = pane)
        if (pane == AnalysisPane.EXPLORER && ui.explorer == null) loadExplorer()
    }

    /** Builds the offline "your games" move statistics from the library (mainlines, 40 plies). */
    fun loadExplorer() {
        if (ui.explorerLoading) return
        ui = ui.copy(explorerLoading = true, explorerError = null)
        viewModelScope.launch {
            ui = try {
                val records = AppData.index(getApplication<Application>()).mainlines()
                val index = withContext(Dispatchers.Default) {
                    ExplorerIndex.build(
                        records.mapNotNull { record ->
                            val start = runCatching { Fen.parse(record.startFen, record.variant) }.getOrNull() ?: return@mapNotNull null
                            IndexedGame(
                                start = start,
                                moves = record.moves,
                                result = dev.lumenchess.core.chess.GameResult.entries.firstOrNull { it.name == record.result },
                                playedAtEpochMillis = record.playedAtEpochMillis ?: record.createdAtEpochMillis,
                            )
                        },
                    )
                }
                ui.copy(explorer = index, explorerLoading = false)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                ui.copy(explorerLoading = false, explorerError = "Could not read your games.")
            }
        }
    }
    fun setReviewMode(mode: ReviewMode) {
        ui = ui.copy(reviewMode = mode)
        if (mode == ReviewMode.KEY_MOMENTS && ui.mainlinePly?.let { it in (ui.review?.summary?.keyMoments ?: emptyList()) } != true) nextKeyMoment()
    }
    fun setReviewPreset(preset: ReviewPreset) { ui = ui.copy(reviewPreset = preset) }

    private fun refreshEngine() {
        if (!visible || !ui.engineEnabled || ui.loading) return
        val position = ui.position
        if (Rules.termination(position) != null) {
            stopEngine()
            ui = ui.copy(snapshot = null)
            return
        }
        val engine = client ?: EngineAnalysisClient(getApplication<Application>()).also { created ->
            client = created
            viewModelScope.launch {
                created.status.collect { status ->
                    ui = ui.copy(engineStatus = status)
                    if (status == EngineAnalysisClient.Status.READY) refreshEngine()
                }
            }
            viewModelScope.launch { created.failure.collect { ui = ui.copy(engineMessage = it) } }
        }
        if (engine.status.value != EngineAnalysisClient.Status.READY) return
        val key = position to ui.multiPv
        if (activeKey == key) return
        activeKey = key
        ui = ui.copy(snapshot = ui.snapshot?.takeIf { it.position == position })
        engine.analyze(
            position = position,
            multiPv = ui.multiPv,
            onUpdate = { snapshot -> publish(snapshot, force = false) },
            onFinished = { snapshot ->
                if (activeKey == key) activeKey = null
                publish(snapshot, force = true)
            },
        )
    }

    private fun publish(snapshot: AnalysisSnapshot, force: Boolean) {
        if (snapshot.position != ui.position) return
        val now = SystemClock.elapsedRealtime()
        // At most ~8 updates a second: engines print many lines per iteration.
        if (!force && now - lastPublish < 120L && ui.snapshot?.position == snapshot.position) return
        lastPublish = now
        ui = ui.copy(snapshot = snapshot)
    }

    private fun stopEngine() {
        client?.stop()
        activeKey = null
    }

    // --- Review -------------------------------------------------------------------------------

    fun startReview(restart: Boolean = false) {
        val game = ui.game ?: return
        ReviewCoordinator.request(getApplication<Application>(), game.id.value, ReviewSettings(ui.reviewPreset), restart)
        ui = ui.copy(pane = AnalysisPane.REVIEW)
    }

    fun cancelReview() {
        ui.game?.let { ReviewCoordinator.cancel(it.id.value) }
    }

    private fun reloadReview() {
        val game = ui.game ?: return
        viewModelScope.launch {
            val (record, review) = ReviewLoader.load(getApplication<Application>(), game)
            ui = ui.copy(review = review, reviewRecord = record)
        }
    }

    override fun onCleared() {
        client?.close()
        client = null
    }
}

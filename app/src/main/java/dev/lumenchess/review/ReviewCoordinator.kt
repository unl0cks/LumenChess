package dev.lumenchess.review

import android.content.Context
import dev.lumenchess.analysis.engine.EngineAnalysisClient
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.review.EvaluationCodec
import dev.lumenchess.analysis.review.PositionEvaluation
import dev.lumenchess.analysis.review.ReviewModelVersion
import dev.lumenchess.analysis.review.ReviewSession
import dev.lumenchess.analysis.review.ReviewSettings
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.LoadedCanonicalGame
import dev.lumenchess.data.persistence.PersistedTermination
import dev.lumenchess.data.persistence.PersistentGameId
import dev.lumenchess.data.persistence.ReviewPlyRecord
import dev.lumenchess.data.persistence.ReviewRecord
import dev.lumenchess.data.persistence.ReviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReviewRunPhase { QUEUED, RUNNING, RECHECKING, COMPLETE, FAILED, CANCELLED }

data class ReviewRunProgress(
    val gameId: String,
    val phase: ReviewRunPhase,
    val evaluated: Int = 0,
    val total: Int = 0,
    val message: String? = null,
) {
    val fraction: Float get() = if (total <= 0) 0f else (evaluated.toFloat() / total).coerceIn(0f, 1f)
    val active: Boolean get() = phase == ReviewRunPhase.QUEUED || phase == ReviewRunPhase.RUNNING || phase == ReviewRunPhase.RECHECKING
}

/**
 * Runs Game Reviews one at a time, app-wide, so a review keeps going while the user moves around
 * the app. Every evaluation is written as soon as it exists, which makes reviews resumable after
 * cancellation, engine death or process death (the next request for the same game and settings
 * continues where it stopped). Engine work happens in the isolated analysis slot.
 */
object ReviewCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _progress = MutableStateFlow<Map<String, ReviewRunProgress>>(emptyMap())
    val progress: StateFlow<Map<String, ReviewRunProgress>> = _progress.asStateFlow()
    private val _finished = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Game ids whose review just completed (or failed): screens showing them reload. */
    val finished: SharedFlow<String> = _finished.asSharedFlow()

    private data class Request(val gameId: String, val settings: ReviewSettings, val restart: Boolean)

    private val queue = ArrayDeque<Request>()
    private var worker: Job? = null
    private var running: Request? = null
    private var runningJob: Job? = null
    private var client: EngineAnalysisClient? = null

    /** Queues a review of [gameId]. A complete review with the same settings is kept unless [restart]. */
    fun request(context: Context, gameId: String, settings: ReviewSettings = ReviewSettings(), restart: Boolean = false) {
        val appContext = context.applicationContext
        if (running?.gameId == gameId || queue.any { it.gameId == gameId }) return
        queue.addLast(Request(gameId, settings, restart))
        setProgress(ReviewRunProgress(gameId, ReviewRunPhase.QUEUED))
        if (worker?.isActive != true) worker = scope.launch { drain(appContext) }
    }

    /** Stops a queued or running review. Work done so far is kept for a later resume. */
    fun cancel(gameId: String) {
        queue.removeAll { it.gameId == gameId }
        if (running?.gameId == gameId) runningJob?.cancel()
        _progress.update { it + (gameId to ReviewRunProgress(gameId, ReviewRunPhase.CANCELLED)) }
    }

    fun isBusy(gameId: String): Boolean = _progress.value[gameId]?.active == true

    private suspend fun drain(context: Context) {
        // The opening book is parsed once, off the main thread.
        withContext(Dispatchers.Default) { AppData.openingBook }
        try {
            while (true) {
                while (queue.isNotEmpty()) {
                    val next = queue.removeFirst()
                    running = next
                    val job = scope.launch { runOne(context, next) }
                    runningJob = job
                    job.join()
                    running = null
                    runningJob = null
                }
                // Keep the engine warm briefly for a follow-up request, then release its process.
                var waited = 0L
                while (queue.isEmpty() && waited < ENGINE_IDLE_MILLIS) {
                    delay(IDLE_POLL_MILLIS)
                    waited += IDLE_POLL_MILLIS
                }
                if (queue.isEmpty()) break
            }
        } finally {
            client?.close()
            client = null
        }
    }

    private suspend fun runOne(context: Context, request: Request) {
        val gameId = request.gameId
        var record: ReviewRecord? = null
        var evaluatedAtCancel = 0
        try {
            val game = AppData.games(context).loadGame(PersistentGameId(gameId))
                ?: return fail(gameId, "This game is no longer in the library.")
            val moves = game.tree.mainline().mapNotNull { it.move }
            if (moves.isEmpty()) return fail(gameId, "There are no moves to review.")
            val reviews = AppData.reviews(context)
            val nodeIds = reviews.mainlineNodeIds(game.id)
            if (nodeIds.size != moves.size) return fail(gameId, "The saved game could not be matched to its moves.")

            val engine = client ?: EngineAnalysisClient(context).also { client = it }
            val profile = request.settings.describe()
            val existing = reviews.latestReview(game.id)
            val sameSetup = existing != null && existing.modelVersion == ReviewModelVersion.CURRENT &&
                existing.profile == profile && existing.engineName == engine.engine.displayName
            if (sameSetup && existing!!.state == ReviewState.COMPLETE && !request.restart) {
                setProgress(ReviewRunProgress(gameId, ReviewRunPhase.COMPLETE, moves.size + 1, moves.size + 1))
                _finished.tryEmit(gameId)
                return
            }
            val resume = sameSetup && !request.restart
            val current = if (resume) {
                existing!!.also { reviews.updateState(it.id, ReviewState.RUNNING, it.progressPly) }
            } else {
                reviews.startReview(game.id, ReviewModelVersion.CURRENT, engine.engine.displayName, null, profile)
            }
            record = current

            val session = ReviewSession(game.tree.startPosition, moves, request.settings, AppData.openingBook, finalScoreOverride(game))
            val rows = HashMap<Int, ReviewPlyRecord>()
            if (resume) {
                for (row in reviews.plies(current)) {
                    rows[row.ply] = row.copy(heavy = emptyMap())
                    row.heavy[FORMAT_EVAL]?.let { EvaluationCodec.decode(it, session.positions[row.ply]) }
                        ?.let { session.restore(row.ply, it.evaluation, it.pass) }
                    row.heavy[FORMAT_FINAL]?.let { EvaluationCodec.decode(it, session.positions[moves.size]) }
                        ?.let { session.restore(moves.size, it.evaluation, it.pass) }
                }
            }
            fun row(ply: Int) = rows.getOrPut(ply) { ReviewPlyRecord(ply = ply, nodeId = nodeIds[ply]) }

            var failures = 0
            while (true) {
                coroutineContextEnsureActive()
                val next = session.nextRequest() ?: break
                val phase = if (next.pass >= 2) ReviewRunPhase.RECHECKING else ReviewRunPhase.RUNNING
                setProgress(ReviewRunProgress(gameId, phase, session.evaluatedCount, session.totalSteps))
                val evaluation = try {
                    engine.evaluate(next.position, next.budget)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += 1
                    if (failures > MAX_ENGINE_FAILURES) throw error
                    delay(RETRY_DELAY_MILLIS)
                    continue
                }
                session.accept(next.index, evaluation, next.pass)
                evaluatedAtCancel = session.evaluatedCount
                // Persist immediately: this is the resume checkpoint.
                if (next.index < moves.size) {
                    val updated = row(next.index).withBefore(evaluation, EvaluationCodec.encode(evaluation, next.pass))
                    rows[next.index] = updated.copy(heavy = emptyMap())
                    reviews.savePly(current, updated)
                }
                if (next.index > 0) {
                    val (cp, mate) = EvaluationCodec.toColumns(evaluation.score)
                    val previous = row(next.index - 1).copy(playedEvalCp = cp, playedMateIn = mate)
                    val heavy = if (next.index == moves.size) mapOf(FORMAT_FINAL to EvaluationCodec.encode(evaluation, next.pass)) else emptyMap()
                    rows[next.index - 1] = previous
                    reviews.savePly(current, previous.copy(heavy = heavy))
                }
                reviews.updateState(current.id, ReviewState.RUNNING, (next.index - 1).coerceAtLeast(0))
            }

            // Final classification pass: every durable column, in mainline order.
            val reviewed = session.reviewedMoves()
            val evaluations = session.evaluations()
            for (move in reviewed) {
                val before = evaluations[move.ply]?.first
                val after = evaluations[move.ply + 1]?.first
                val (cp, mate) = after?.let { EvaluationCodec.toColumns(it.score) } ?: (null to null)
                val finalRow = row(move.ply).copy(
                    playedEvalCp = cp,
                    playedMateIn = mate,
                    bestMove = before?.bestMove,
                    classification = move.classification.name,
                    expectedPointsLoss = move.expectedPointsLoss,
                    depth = before?.depth,
                    nodes = before?.nodes,
                    timeMillis = before?.timeMillis,
                    heavy = emptyMap(),
                )
                rows[move.ply] = finalRow
                reviews.savePly(current, finalRow)
            }
            reviews.updateState(current.id, ReviewState.COMPLETE, moves.size)
            setProgress(ReviewRunProgress(gameId, ReviewRunPhase.COMPLETE, session.totalSteps, session.totalSteps))
            _finished.tryEmit(gameId)
        } catch (error: CancellationException) {
            record?.let { withContext(kotlinx.coroutines.NonCancellable) { AppData.reviews(context).updateState(it.id, ReviewState.PARTIAL, it.progressPly) } }
            setProgress(ReviewRunProgress(gameId, ReviewRunPhase.CANCELLED, evaluatedAtCancel, 0))
            throw error
        } catch (error: Exception) {
            record?.let { AppData.reviews(context).updateState(it.id, ReviewState.FAILED, it.progressPly) }
            fail(gameId, error.message ?: "The review stopped unexpectedly.")
        }
    }

    private suspend fun coroutineContextEnsureActive() {
        kotlin.coroutines.coroutineContext.ensureActive()
    }

    private fun fail(gameId: String, message: String) {
        setProgress(ReviewRunProgress(gameId, ReviewRunPhase.FAILED, message = message))
        _finished.tryEmit(gameId)
    }

    private fun setProgress(progress: ReviewRunProgress) {
        _progress.update { it + (progress.gameId to progress) }
    }

    private fun ReviewPlyRecord.withBefore(evaluation: PositionEvaluation, payload: String) = copy(
        bestMove = evaluation.bestMove,
        depth = evaluation.depth,
        nodes = evaluation.nodes,
        timeMillis = evaluation.timeMillis,
        heavy = mapOf(FORMAT_EVAL to payload),
    )

    /**
     * Games that ended by a rule the final position alone does not show (repetition, move-count
     * rules) are scored as drawn there. Resignation, time and agreement are judged on the board.
     */
    private fun finalScoreOverride(game: LoadedCanonicalGame): Score? = when (game.metadata.termination) {
        PersistedTermination.THREEFOLD_REPETITION, PersistedTermination.FIVEFOLD_REPETITION,
        PersistedTermination.FIFTY_MOVE_RULE, PersistedTermination.SEVENTY_FIVE_MOVE_RULE,
        -> Score.DRAW
        else -> null
    }

    const val FORMAT_EVAL = EvaluationCodec.FORMAT
    const val FORMAT_FINAL = EvaluationCodec.FORMAT + "-final"
    private const val MAX_ENGINE_FAILURES = 3
    private const val RETRY_DELAY_MILLIS = 1_500L
    private const val ENGINE_IDLE_MILLIS = 20_000L
    private const val IDLE_POLL_MILLIS = 250L
}

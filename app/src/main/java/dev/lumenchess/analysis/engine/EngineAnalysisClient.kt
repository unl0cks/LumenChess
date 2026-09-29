package dev.lumenchess.analysis.engine

import android.content.Context
import android.os.SystemClock
import dev.lumenchess.analysis.eval.EngineLines
import dev.lumenchess.analysis.eval.Score
import dev.lumenchess.analysis.review.EngineLine
import dev.lumenchess.analysis.review.PositionEvaluation
import dev.lumenchess.analysis.review.SearchBudget
import dev.lumenchess.core.chess.Position
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchInfo
import dev.lumenchess.engine.api.EngineSearchLimits
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineSessionId
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.engine.api.UciScore
import dev.lumenchess.engine.api.UciScoreBound
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.engine.host.transport.EngineSlot
import dev.lumenchess.play.AndroidPlayEngineGateway
import dev.lumenchess.play.PlayEngine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** One MultiPV line as currently known, scored from the analysed position's side to move. */
data class AnalysisLine(
    val rank: Int,
    val score: Score,
    val moves: List<dev.lumenchess.core.chess.Move>,
    val depth: Int?,
    /** False while the engine only knows a bound (fail high / fail low) for this line. */
    val exact: Boolean,
)

data class AnalysisSnapshot(
    val searchId: Long,
    val position: Position,
    val lines: List<AnalysisLine>,
    val depth: Int?,
    val nodes: Long?,
    val nodesPerSecond: Long?,
    val elapsedMillis: Long,
    val finished: Boolean,
) {
    val best: AnalysisLine? get() = lines.firstOrNull()

    fun toEvaluation(): PositionEvaluation? {
        val top = best ?: return null
        return PositionEvaluation(
            score = top.score,
            lines = lines.map { EngineLine(it.moves, it.score) },
            depth = depth,
            nodes = nodes,
            nodesPerSecond = nodesPerSecond,
            timeMillis = elapsedMillis,
        )
    }
}

class EngineAnalysisException(message: String) : IllegalStateException(message)

/**
 * Analysis and Review access to an engine in its own isolated process (slot C).
 *
 * Engine output stays untrusted presentation data: every line is cut at its first move that
 * core-chess does not accept. There is at most one search in flight; a new search always stops
 * the previous one first, which is the host's one-pending-search contract. Callbacks arrive on the
 * main thread (the gateway serializes them), and every method must be called on the main thread.
 */
class EngineAnalysisClient(
    context: Context,
    val engine: PlayEngine = PlayEngine.STOCKFISH_18,
) : AutoCloseable {
    enum class Status { CONNECTING, READY, FAILED, CLOSED }

    private val gateway = AndroidPlayEngineGateway(
        context = context,
        engine = engine,
        sessionId = EngineSessionId("analysis-${UUID.randomUUID()}"),
        slot = EngineSlot.C,
        forwardAllLines = true,
    )
    private val _status = MutableStateFlow(Status.CONNECTING)
    val status: StateFlow<Status> = _status.asStateFlow()
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()

    private var nextSearchId = 1L
    private var current: ActiveSearch? = null

    private class ActiveSearch(
        val id: Long,
        val position: Position,
        val multiPv: Int,
        val startedAt: Long,
        val onUpdate: (AnalysisSnapshot) -> Unit,
        val onFinished: (AnalysisSnapshot) -> Unit,
    ) {
        val lines = sortedMapOf<Int, AnalysisLine>()
        var depth: Int? = null
        var nodes: Long? = null
        var nps: Long? = null
    }

    init {
        gateway.setListener(object : AndroidPlayEngineGateway.Listener {
            override fun onEngineHostRecovered() {
                _status.value = Status.READY
                _failure.value = null
            }

            override fun onEngineHostDied() {
                _status.value = Status.CONNECTING
                // A search in a dead host never finishes; report what was known so callers move on.
                current?.let { search ->
                    current = null
                    search.onFinished(snapshot(search, finished = true))
                }
            }

            override fun onEngineResult(result: EngineSearchResult) {
                val search = current ?: return
                if (result.searchId.value != search.id) return
                current = null
                search.onFinished(snapshot(search, finished = true))
            }

            override fun onEngineInfo(info: EngineSearchInfo) = handleInfo(info)

            override fun onEngineFailure(failure: EngineHostFailure) {
                _failure.value = failure.message
                current?.let { search ->
                    current = null
                    search.onFinished(snapshot(search, finished = true))
                }
            }
        })
        gateway.connect()
    }

    /**
     * Starts analysing [position] (stopping any earlier search). With no limits the search runs
     * until [stop] or the next [analyze]. Returns the search id.
     */
    fun analyze(
        position: Position,
        multiPv: Int,
        limits: EngineSearchLimits = EngineSearchLimits(),
        onUpdate: (AnalysisSnapshot) -> Unit = {},
        onFinished: (AnalysisSnapshot) -> Unit = {},
    ): Long {
        check(_status.value != Status.CLOSED) { "Analysis engine is closed" }
        stop()
        val id = nextSearchId++
        val search = ActiveSearch(id, position, multiPv.coerceIn(1, MAX_LINES), SystemClock.elapsedRealtime(), onUpdate, onFinished)
        current = search
        gateway.startSearch(
            EngineSearchRequest(
                searchId = EngineSearchId(id),
                positionRevision = PositionRevision(id),
                position = position,
                limits = limits,
                multiPv = search.multiPv,
            ),
        )
        return id
    }

    /** Stops the running search, if any. Its callbacks are not called after this. */
    fun stop() {
        val search = current ?: return
        current = null
        gateway.cancelSearch(EngineSearchId(search.id))
    }

    /** Evaluates one position within [budget] (used by Game Review). */
    suspend fun evaluate(position: Position, budget: SearchBudget): PositionEvaluation {
        withTimeout(CONNECT_TIMEOUT_MILLIS) {
            status.first { it == Status.READY || it == Status.CLOSED }
        }
        if (_status.value == Status.CLOSED) throw EngineAnalysisException("Analysis engine is closed")
        return suspendCancellableCoroutine { continuation ->
            val id = analyze(
                position = position,
                multiPv = budget.multiPv,
                limits = EngineSearchLimits(depth = budget.depth, nodes = budget.nodes, moveTimeMillis = budget.timeMillis),
                onFinished = { snapshot ->
                    if (!continuation.isActive) return@analyze
                    val evaluation = snapshot.toEvaluation()
                    if (evaluation != null) {
                        continuation.resume(evaluation)
                    } else {
                        continuation.resumeWithException(
                            EngineAnalysisException(_failure.value ?: "The engine returned no evaluation"),
                        )
                    }
                },
            )
            continuation.invokeOnCancellation {
                // Cancellation may arrive off the main thread; the gateway tolerates a stale stop.
                if (current?.id == id) stop()
            }
        }
    }

    override fun close() {
        if (_status.value == Status.CLOSED) return
        stop()
        _status.value = Status.CLOSED
        gateway.setListener(null)
        gateway.close()
    }

    private fun handleInfo(info: EngineSearchInfo) {
        val search = current ?: return
        if (info.searchId.value != search.id) return
        val rank = info.multiPvRank
        if (rank !in 1..search.multiPv) return
        val score = info.score?.toScore() ?: return
        val moves = EngineLines.legalPrefix(search.position, info.principalVariation)
        if (moves.isEmpty()) return
        search.lines[rank] = AnalysisLine(rank, score, moves, info.depth, exact = info.score?.bound == UciScoreBound.EXACT)
        if (rank == 1) {
            search.depth = info.depth ?: search.depth
            search.nodes = info.nodes ?: search.nodes
            search.nps = info.nodesPerSecond ?: search.nps
        }
        search.onUpdate(snapshot(search, finished = false))
    }

    private fun snapshot(search: ActiveSearch, finished: Boolean) = AnalysisSnapshot(
        searchId = search.id,
        position = search.position,
        lines = search.lines.values.toList(),
        depth = search.depth,
        nodes = search.nodes,
        nodesPerSecond = search.nps,
        elapsedMillis = SystemClock.elapsedRealtime() - search.startedAt,
        finished = finished,
    )

    companion object {
        const val MAX_LINES = 4
        private const val CONNECT_TIMEOUT_MILLIS = 20_000L

        internal fun UciScore.toScore(): Score = when (this) {
            is UciScore.Centipawns -> Score.Centipawns(value)
            is UciScore.Mate -> if (moves == 0) Score.Checkmated else Score.Mate(moves)
        }
    }
}

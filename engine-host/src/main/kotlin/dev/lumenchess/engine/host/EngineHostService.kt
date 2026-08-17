package dev.lumenchess.engine.host

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineCandidateSelector
import dev.lumenchess.engine.api.EngineCapabilities
import dev.lumenchess.engine.api.EngineMultiPvCapability
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineStrengthCapability
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthPlan
import dev.lumenchess.engine.api.EngineStrengthPlanner
import dev.lumenchess.engine.api.EngineStrengthPlanning
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.engine.api.UciCommand
import dev.lumenchess.engine.api.UciCommandEncoder
import dev.lumenchess.engine.api.UciEvent
import dev.lumenchess.engine.api.UciProtocolException
import dev.lumenchess.engine.api.UciProtocolParser
import dev.lumenchess.engine.host.transport.EngineHostFailureCode
import dev.lumenchess.engine.host.transport.IEngineHost
import dev.lumenchess.engine.host.transport.IEngineHostCallback
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One service instance owns at most one engine session. Slot A and Slot B are declared as distinct
 * isolated Android processes; native/UCI work is therefore never hosted in the app process.
 */
abstract class EngineHostService : Service() {
    private val sessions = ConcurrentHashMap<String, HostSession>()
    private val hostGenerationToken: Long by lazy {
        SystemClock.elapsedRealtimeNanos() xor (Process.myPid().toLong() shl 32)
    }

    private val binder = object : IEngineHost.Stub() {
        override fun getHostGeneration(): Long = hostGenerationToken

        override fun getProcessId(): Int = Process.myPid()

        override fun openSession(
            requestedSessionId: String,
            engineId: String,
            callback: IEngineHostCallback,
        ): String {
            require(requestedSessionId.isNotBlank()) { "Session identity cannot be blank" }
            require(sessions.isEmpty()) { "An isolated engine slot may own only one session" }
            val hostedBackend = createHostedBackend(engineId)
            val session = HostSession(
                sessionId = requestedSessionId,
                hostGeneration = hostGenerationToken,
                callback = callback,
                backend = hostedBackend.backend,
                capabilities = hostedBackend.capabilities,
            )
            check(sessions.putIfAbsent(requestedSessionId, session) == null) { "Session already exists" }
            return requestedSessionId
        }

        override fun newGame(sessionId: String) {
            requireSession(sessionId).newGame()
        }

        override fun startSearch(
            sessionId: String,
            searchId: Long,
            positionRevision: Long,
            fen: String,
            variant: String,
            depth: Int,
            nodes: Long,
            moveTimeMillis: Long,
            multiPv: Int,
            strengthModel: String,
            targetElo: Int,
            strengthSeed: Long,
        ) {
            require(searchId > 0L) { "Search identity must be positive" }
            require(positionRevision >= 0L) { "Position revision cannot be negative" }
            require(fen.isNotBlank()) { "Position FEN cannot be blank" }
            require(variant == "STANDARD" || variant == "CHESS960") { "Unsupported variant '$variant'" }
            require(depth >= 0 && nodes >= 0L && moveTimeMillis >= 0L) { "Search limits cannot be negative" }
            require(multiPv > 0) { "MultiPV must be positive" }
            require(targetElo == 0 || targetElo in EngineStrengthTarget.MIN_ELO..EngineStrengthTarget.MAX_ELO) {
                "Strength target must be 0 (full) or ${EngineStrengthTarget.MIN_ELO}..${EngineStrengthTarget.MAX_ELO} Elo"
            }
            val model = try {
                EngineStrengthModel.valueOf(strengthModel)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Unknown strength model '$strengthModel'")
            }
            val strengthTarget = if (targetElo == 0) {
                EngineStrengthTarget.FullStrength
            } else {
                EngineStrengthTarget.Elo(targetElo)
            }
            requireSession(sessionId).startSearch(
                SearchEnvelope(
                    searchId = searchId,
                    positionRevision = positionRevision,
                    fen = fen,
                    chess960 = variant == "CHESS960",
                    depth = depth.takeIf { it > 0 },
                    nodes = nodes.takeIf { it > 0L },
                    moveTimeMillis = moveTimeMillis.takeIf { it > 0L },
                    multiPv = multiPv,
                    strength = EngineStrengthSettings(
                        target = strengthTarget,
                        model = model,
                        seed = strengthSeed,
                    ),
                ),
            )
        }

        override fun stopSearch(sessionId: String, searchId: Long) {
            requireSession(sessionId).stopSearch(searchId)
        }

        override fun closeSession(sessionId: String) {
            sessions.remove(sessionId)?.close()
        }

        override fun shutdownHost() {
            sessions.values.forEach { it.close() }
            sessions.clear()
            stopSelf()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        sessions.values.forEach { it.close() }
        sessions.clear()
        super.onDestroy()
    }

    private fun requireSession(sessionId: String): HostSession =
        sessions[sessionId] ?: throw IllegalStateException("Unknown engine session '$sessionId'")

    private fun createHostedBackend(engineId: String): HostedBackend = when (engineId) {
        Stockfish18Engine.ID -> HostedBackend(Stockfish18UciBackend(), Stockfish18Engine.capabilities)
        Reckless09Engine.ID -> HostedBackend(Reckless09UciBackend(), Reckless09Engine.capabilities)
        "mock" -> HostedBackend(debugMock(MockMode.NORMAL), MOCK_CAPABILITIES)
        "mock-malformed" -> HostedBackend(debugMock(MockMode.MALFORMED), MOCK_CAPABILITIES)
        "mock-crash" -> HostedBackend(debugMock(MockMode.CRASH), MOCK_CAPABILITIES)
        else -> throw IllegalArgumentException("Unknown engine backend '$engineId'")
    }

    private fun debugMock(mode: MockMode): UciBackend {
        check(BuildConfig.DEBUG) { "M12 mock backends are available only in debug builds" }
        return MockUciBackend(mode)
    }

    private companion object {
        val MOCK_CAPABILITIES = EngineCapabilities(
            variants = setOf(Variant.STANDARD, Variant.CHESS960),
            multiPv = EngineMultiPvCapability(4),
        )
    }
}

class EngineSlotAService : EngineHostService()
class EngineSlotBService : EngineHostService()

private data class HostedBackend(
    val backend: UciBackend,
    val capabilities: EngineCapabilities,
)

private data class SearchEnvelope(
    val searchId: Long,
    val positionRevision: Long,
    val fen: String,
    val chess960: Boolean,
    val depth: Int?,
    val nodes: Long?,
    val moveTimeMillis: Long?,
    val multiPv: Int,
    val strength: EngineStrengthSettings,
    var cancelled: Boolean = false,
) {
    var strengthPlan: EngineStrengthPlan? = null
    var candidateAccumulator: EngineCandidateAccumulator? = null
    var effectiveDepth: Int? = depth
    var effectiveMultiPv: Int = multiPv
}

/** Serializes UCI search lifecycle so late bestmove output can never be relabelled as a newer search. */
private class HostSession(
    private val sessionId: String,
    private val hostGeneration: Long,
    private val callback: IEngineHostCallback,
    private val backend: UciBackend,
    private val capabilities: EngineCapabilities,
) : AutoCloseable {
    private val lock = Any()
    private var ready = false
    private var closed = false
    private var active: SearchEnvelope? = null
    private var pending: SearchEnvelope? = null

    init {
        backend.start(object : UciBackend.Listener {
            override fun onLine(line: String) = handleLine(line)
            override fun onFailure(error: Throwable) = handleBackendFailure(error)
        })
        backend.send(UciCommandEncoder.encode(UciCommand.Initialize))
    }

    fun newGame() = synchronized(lock) {
        if (closed) return@synchronized
        if (active != null || pending != null) {
            fail(EngineHostFailureCode.BUSY, "Cannot start a new game while a search is active")
            return@synchronized
        }
        ready = false
        backend.send(UciCommandEncoder.encode(UciCommand.NewGame))
        backend.send(UciCommandEncoder.encode(UciCommand.IsReady))
    }

    fun startSearch(request: SearchEnvelope) = synchronized(lock) {
        if (closed) return@synchronized
        when {
            !ready -> {
                if (pending != null) {
                    fail(EngineHostFailureCode.BUSY, "A search is already queued")
                } else if (prepareSearch(request)) {
                    pending = request
                }
            }
            active == null -> if (prepareSearch(request)) beginSearch(request)
            active?.cancelled == true && pending == null -> if (prepareSearch(request)) pending = request
            else -> fail(EngineHostFailureCode.BUSY, "Overlapping UCI searches are not allowed")
        }
    }

    fun stopSearch(searchId: Long) = synchronized(lock) {
        if (closed) return@synchronized
        if (pending?.searchId == searchId) {
            pending = null
            return@synchronized
        }
        val current = active
        if (current != null && current.searchId == searchId && !current.cancelled) {
            current.cancelled = true
            backend.send(UciCommandEncoder.encode(UciCommand.Stop))
        }
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        active = null
        pending = null
        try {
            backend.send(UciCommandEncoder.encode(UciCommand.Quit))
        } finally {
            backend.close()
        }
    }

    private fun prepareSearch(request: SearchEnvelope): Boolean {
        val plan = when (val planning = EngineStrengthPlanner.plan(request.strength, capabilities)) {
            is EngineStrengthPlanning.Supported -> planning.plan
            is EngineStrengthPlanning.Unsupported -> {
                fail(EngineHostFailureCode.SESSION, planning.reason)
                return false
            }
        }

        val humanization = plan.humanization
        val effectiveMultiPv = maxOf(request.multiPv, humanization?.candidateCount ?: 1)
        val maxMultiPv = capabilities.multiPv?.maxLines ?: 1
        if (effectiveMultiPv > maxMultiPv) {
            fail(
                EngineHostFailureCode.SESSION,
                "Strength model requires $effectiveMultiPv MultiPV lines but this engine supports at most $maxMultiPv",
            )
            return false
        }

        request.strengthPlan = plan
        request.effectiveMultiPv = effectiveMultiPv
        request.effectiveDepth = minNullable(request.depth, humanization?.depthCap)
        request.candidateAccumulator = humanization?.let {
            EngineCandidateAccumulator(expectedLines = it.candidateCount)
        }
        return true
    }

    private fun beginSearch(request: SearchEnvelope) {
        val plan = checkNotNull(request.strengthPlan) { "Search must be strength-planned before execution" }
        active = request
        backend.send(UciCommandEncoder.encode(UciCommand.SetOption("UCI_Chess960", request.chess960.toString())))

        if (capabilities.strength is EngineStrengthCapability.EloRange) {
            if (plan.nativeElo != null) {
                backend.send(UciCommandEncoder.encode(UciCommand.SetOption("UCI_LimitStrength", "true")))
                backend.send(UciCommandEncoder.encode(UciCommand.SetOption("UCI_Elo", plan.nativeElo.toString())))
            } else {
                backend.send(UciCommandEncoder.encode(UciCommand.SetOption("UCI_LimitStrength", "false")))
            }
        }

        backend.send(UciCommandEncoder.encode(UciCommand.SetOption("MultiPV", request.effectiveMultiPv.toString())))
        backend.send(UciCommandEncoder.encode(UciCommand.Position(request.fen)))
        backend.send(
            UciCommandEncoder.encode(
                UciCommand.Go(
                    depth = request.effectiveDepth,
                    nodes = request.nodes,
                    moveTimeMillis = request.moveTimeMillis,
                ),
            ),
        )
    }

    private fun handleLine(line: String) = synchronized(lock) {
        if (closed) return@synchronized
        val event = try {
            UciProtocolParser.parse(line)
        } catch (error: UciProtocolException) {
            active = null
            pending = null
            fail(EngineHostFailureCode.PROTOCOL, error.message ?: "Malformed UCI output")
            backend.close()
            closed = true
            return@synchronized
        }

        when (event) {
            UciEvent.UciOk -> backend.send(UciCommandEncoder.encode(UciCommand.IsReady))
            UciEvent.ReadyOk -> {
                ready = true
                if (active == null) pending?.also { pending = null; beginSearch(it) }
            }
            is UciEvent.Info -> active?.candidateAccumulator?.observe(event.info)
            is UciEvent.BestMove -> {
                val completed = active ?: return@synchronized
                active = null
                if (!completed.cancelled) {
                    val plan = checkNotNull(completed.strengthPlan)
                    val selectedMove = EngineCandidateSelector.select(
                        candidates = completed.candidateAccumulator?.snapshot().orEmpty(),
                        fallbackBestMoveUci = event.bestMove,
                        plan = plan,
                        searchId = EngineSearchId(completed.searchId),
                        positionRevision = PositionRevision(completed.positionRevision),
                    )
                    try {
                        callback.onSearchResult(
                            sessionId,
                            hostGeneration,
                            completed.searchId,
                            completed.positionRevision,
                            selectedMove.orEmpty(),
                        )
                    } catch (_: RemoteException) {
                        // The caller disappeared; the isolated host must remain self-contained.
                    }
                }
                pending?.also { pending = null; beginSearch(it) }
            }
            is UciEvent.IdAuthor,
            is UciEvent.IdName,
            is UciEvent.Option,
            is UciEvent.Unknown,
            -> Unit
        }
    }

    private fun handleBackendFailure(error: Throwable) = synchronized(lock) {
        if (closed) return@synchronized
        active = null
        pending = null
        fail(EngineHostFailureCode.BACKEND, error.message ?: error::class.java.simpleName)
        closed = true
        backend.close()
    }

    private fun fail(code: EngineHostFailureCode, message: String) {
        try {
            callback.onHostFailure(sessionId, hostGeneration, code.wireValue, message)
        } catch (_: RemoteException) {
            // Failure delivery is best effort after the caller Binder has died.
        }
    }

    private fun minNullable(requested: Int?, cap: Int?): Int? = when {
        requested == null -> cap
        cap == null -> requested
        else -> minOf(requested, cap)
    }
}

internal interface UciBackend : AutoCloseable {
    interface Listener {
        fun onLine(line: String)
        fun onFailure(error: Throwable)
    }

    fun start(listener: Listener)
    fun send(command: String)
    override fun close()
}

private enum class MockMode { NORMAL, MALFORMED, CRASH }

/** Debug-only UCI fake used to prove process and search lifecycle before M13 introduces real engines. */
private class MockUciBackend(private val mode: MockMode) : UciBackend {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val closed = AtomicBoolean(false)
    @Volatile private var listener: UciBackend.Listener? = null

    override fun start(listener: UciBackend.Listener) {
        this.listener = listener
    }

    override fun send(command: String) {
        if (closed.get()) return
        when {
            command == "uci" -> {
                emit("id name LumenChess M12 Mock")
                emit("id author LumenChess")
                emit("option name UCI_Chess960 type check default false")
                emit("option name MultiPV type spin default 1 min 1 max 4")
                emit("uciok")
            }
            command == "isready" -> emit("readyok")
            command.startsWith("go") -> executor.schedule({ finishSearch() }, 120, TimeUnit.MILLISECONDS)
            command == "quit" -> close()
            else -> Unit
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow()
    }

    private fun finishSearch() {
        if (closed.get()) return
        when (mode) {
            MockMode.NORMAL -> emit("bestmove e2e4")
            MockMode.MALFORMED -> emit("bestmove")
            MockMode.CRASH -> Process.killProcess(Process.myPid())
        }
    }

    private fun emit(line: String) {
        try {
            listener?.onLine(line)
        } catch (error: Throwable) {
            listener?.onFailure(error)
        }
    }
}

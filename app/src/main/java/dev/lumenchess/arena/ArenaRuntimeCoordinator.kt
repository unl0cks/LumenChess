package dev.lumenchess.arena

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchInfo
import dev.lumenchess.engine.api.EngineSearchLimits
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.UciScore
import dev.lumenchess.play.PlayEngineGateway
import dev.lumenchess.runtime.GameRuntime
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeControllers
import dev.lumenchess.runtime.RuntimeDispatchResult
import dev.lumenchess.runtime.RuntimeEffect
import dev.lumenchess.runtime.RuntimeEvent
import dev.lumenchess.runtime.RuntimeEventId
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.RuntimeManualControl
import dev.lumenchess.runtime.clock.MonotonicTimeSource

interface ArenaPersistenceGateway {
    fun persist(snapshot: RuntimeSnapshot, setup: ResolvedArenaSetup)
}

data class ArenaEvaluation(
    val whiteCentipawns: Int? = null,
    val whiteMateIn: Int? = null,
    val depth: Int? = null,
    val nodes: Long? = null,
    val nodesPerSecond: Long? = null,
    val principalVariation: List<String> = emptyList(),
)

class ArenaRuntimeCoordinator private constructor(
    val setup: ResolvedArenaSetup,
    private val runtime: GameRuntime,
    private val whiteEngine: PlayEngineGateway,
    private val blackEngine: PlayEngineGateway,
    private val persistence: ArenaPersistenceGateway,
    private val onEvaluation: (ArenaEvaluation) -> Unit,
    nextEventId: Long,
) {
    private val coordinatorLock = Any()
    private var eventCounter = nextEventId
    private val readyHosts = mutableSetOf<Color>()
    private val searchOwners = mutableMapOf<EngineSearchId, Color>()

    val state: RuntimeState get() = runtime.state

    fun start() = dispatch { RuntimeEvent.Start(it) }
    fun clockCheck() = dispatch { RuntimeEvent.ClockCheck(it) }
    fun pause() = dispatch { RuntimeEvent.Pause(it) }
    fun resume() = dispatch { RuntimeEvent.Resume(it) }
    fun resign(side: Color) = dispatch { RuntimeEvent.Resign(it, side) }
    fun agreeDraw() = dispatch { RuntimeEvent.AgreeDraw(it) }

    fun onEngineHostRecovered(side: Color) {
        synchronized(coordinatorLock) {
            readyHosts += side
            if (readyHosts.size == 2 && !state.engineHostAvailable) {
                dispatch { RuntimeEvent.EngineHostRecovered(it) }
            }
        }
    }

    fun onEngineHostDied(side: Color) {
        synchronized(coordinatorLock) {
            readyHosts -= side
            // A failure in either required host invalidates the single authoritative Arena search.
            // Unlike human-vs-engine Play, that search may belong to the still-alive sibling host, so
            // cancel it explicitly before the runtime discards its pending identity.
            val cancellations = searchOwners.toList()
            // Remove ownership before invoking a gateway. A host may synchronously deliver a final
            // callback while cancellation is in flight; that callback must already be stale.
            searchOwners.clear()
            cancellations.forEach { (searchId, owner) -> engine(owner).cancelSearch(searchId) }
            if (state.engineHostAvailable) dispatch { RuntimeEvent.EngineHostDied(it) }
        }
    }

    fun onEngineResult(side: Color, result: EngineSearchResult): RuntimeDispatchResult? {
        synchronized(coordinatorLock) {
            if (searchOwners[result.searchId] != side) return null
            searchOwners.remove(result.searchId)
            return dispatch { RuntimeEvent.EngineCompleted(it, result) }
        }
    }

    fun onEngineInfo(side: Color, info: EngineSearchInfo) {
        synchronized(coordinatorLock) {
            val pending = state.pendingEngineSearch ?: return
            if (
                searchOwners[info.searchId] != side ||
                pending.searchId != info.searchId ||
                pending.positionRevision != info.positionRevision
            ) return

            val sign = if (side == Color.WHITE) 1 else -1
            val (centipawns, mate) = when (val score = info.score) {
                is UciScore.Centipawns -> score.value * sign to null
                is UciScore.Mate -> null to score.moves * sign
                null -> null to null
            }
            onEvaluation(
                ArenaEvaluation(
                    whiteCentipawns = centipawns,
                    whiteMateIn = mate,
                    depth = info.depth,
                    nodes = info.nodes,
                    nodesPerSecond = info.nodesPerSecond,
                    principalVariation = info.principalVariation,
                ),
            )
        }
    }

    fun humanMove(move: Move): RuntimeDispatchResult =
        dispatch { RuntimeEvent.HumanMove(it, move) }

    fun setManualControl(manualControl: RuntimeManualControl): RuntimeDispatchResult =
        dispatch { RuntimeEvent.SetManualControl(it, manualControl) }

    fun snapshotForRestore(): RuntimeSnapshot = runtime.snapshotForRestore()

    private fun dispatch(event: (RuntimeEventId) -> RuntimeEvent): RuntimeDispatchResult {
        synchronized(coordinatorLock) {
            val result = runtime.dispatch(event(RuntimeEventId(eventCounter++)))
            execute(result.effects)
            return result
        }
    }

    private fun execute(effects: List<RuntimeEffect>) {
        effects.forEach { effect ->
            when (effect) {
                is RuntimeEffect.StartEngineSearch -> {
                    val side = effect.position.sideToMove
                    val configured = if (side == Color.WHITE) setup.white else setup.black
                    searchOwners[effect.searchId] = side
                    engine(side).startSearch(
                        EngineSearchRequest(
                            searchId = effect.searchId,
                            positionRevision = effect.positionRevision,
                            position = effect.position,
                            limits = EngineSearchLimits(moveTimeMillis = engineMoveTimeMillis(side)),
                            strength = configured.strength,
                        ),
                    )
                }
                is RuntimeEffect.CancelEngineSearch -> {
                    searchOwners.remove(effect.searchId)?.let { owner ->
                        engine(owner).cancelSearch(effect.searchId)
                    }
                }
                is RuntimeEffect.PersistSnapshot -> persistence.persist(effect.snapshot, setup)
            }
        }
    }

    private fun engine(side: Color): PlayEngineGateway =
        if (side == Color.WHITE) whiteEngine else blackEngine

    private fun engineMoveTimeMillis(side: Color): Long {
        val clock = state.clock
        val remaining = when (side) {
            Color.WHITE -> clock.whiteRemainingMillis
            Color.BLACK -> clock.blackRemainingMillis
        }
        val desired = (remaining / 40L + clock.incrementMillis / 2L).coerceIn(50L, 1_500L)
        return minOf(desired, (remaining - 10L).coerceAtLeast(1L))
    }

    companion object {
        fun create(
            setup: ResolvedArenaSetup,
            timeSource: MonotonicTimeSource,
            whiteEngine: PlayEngineGateway,
            blackEngine: PlayEngineGateway,
            persistence: ArenaPersistenceGateway,
            onEvaluation: (ArenaEvaluation) -> Unit = {},
        ): ArenaRuntimeCoordinator = ArenaRuntimeCoordinator(
            setup = setup,
            runtime = GameRuntime.create(
                initialPosition = setup.initialPosition,
                clockConfig = setup.clockConfig,
                timeSource = timeSource,
                controllers = RuntimeControllers(
                    white = if (setup.manualControl.white != null) RuntimeController.HUMAN else RuntimeController.ENGINE,
                    black = if (setup.manualControl.black != null) RuntimeController.HUMAN else RuntimeController.ENGINE,
                ),
                engineHostAvailable = false,
                manualControl = setup.manualControl,
            ),
            whiteEngine = whiteEngine,
            blackEngine = blackEngine,
            persistence = persistence,
            onEvaluation = onEvaluation,
            nextEventId = 1L,
        )

        fun restore(
            setup: ResolvedArenaSetup,
            snapshot: RuntimeSnapshot,
            timeSource: MonotonicTimeSource,
            whiteEngine: PlayEngineGateway,
            blackEngine: PlayEngineGateway,
            persistence: ArenaPersistenceGateway,
            onEvaluation: (ArenaEvaluation) -> Unit = {},
        ): ArenaRuntimeCoordinator = ArenaRuntimeCoordinator(
            setup = setup,
            runtime = GameRuntime.restore(snapshot, timeSource),
            whiteEngine = whiteEngine,
            blackEngine = blackEngine,
            persistence = persistence,
            onEvaluation = onEvaluation,
            nextEventId = (snapshot.processedEventIds.maxOfOrNull { it.value } ?: 0L) + 1L,
        )
    }
}

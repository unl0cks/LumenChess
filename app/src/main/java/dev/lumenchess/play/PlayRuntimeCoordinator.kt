package dev.lumenchess.play

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchLimits
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.runtime.GameRuntime
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeControllers
import dev.lumenchess.runtime.RuntimeDispatchResult
import dev.lumenchess.runtime.RuntimeEffect
import dev.lumenchess.runtime.RuntimeEvent
import dev.lumenchess.runtime.RuntimeEventId
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.clock.ClockSide
import dev.lumenchess.runtime.clock.MonotonicTimeSource

interface PlayEngineGateway {
    fun startSearch(request: EngineSearchRequest)
    fun cancelSearch(searchId: EngineSearchId)
}

interface PlayPersistenceGateway {
    fun persist(snapshot: RuntimeSnapshot, setup: ResolvedPlaySetup)
}

class PlayRuntimeCoordinator private constructor(
    val setup: ResolvedPlaySetup,
    private val runtime: GameRuntime,
    private val engine: PlayEngineGateway,
    private val persistence: PlayPersistenceGateway,
    nextEventId: Long,
) {
    private var eventCounter = nextEventId

    val state: RuntimeState
        get() = runtime.state

    fun start() = dispatch { RuntimeEvent.Start(it) }
    fun clockCheck() = dispatch { RuntimeEvent.ClockCheck(it) }
    fun pause() = dispatch { RuntimeEvent.Pause(it) }
    fun resume() = dispatch { RuntimeEvent.Resume(it) }
    fun humanMove(move: Move) = dispatch { RuntimeEvent.HumanMove(it, move) }
    fun queuePremove(move: Move) = dispatch { RuntimeEvent.QueuePremove(it, setup.humanSide, move) }
    fun cancelPremove() = dispatch { RuntimeEvent.CancelPremove(it, setup.humanSide) }
    fun onEngineHostDied() = dispatch { RuntimeEvent.EngineHostDied(it) }
    fun onEngineHostRecovered() = dispatch { RuntimeEvent.EngineHostRecovered(it) }
    fun resign() = dispatch { RuntimeEvent.Resign(it, setup.humanSide) }
    fun agreeDraw() = dispatch { RuntimeEvent.AgreeDraw(it) }
    fun onEngineResult(result: EngineSearchResult) = dispatch { RuntimeEvent.EngineCompleted(it, result) }

    fun snapshotForRestore(): RuntimeSnapshot = runtime.snapshotForRestore()

    private fun dispatch(event: (RuntimeEventId) -> RuntimeEvent): RuntimeDispatchResult {
        val result = runtime.dispatch(event(RuntimeEventId(eventCounter++)))
        execute(result.effects)
        return result
    }

    private fun execute(effects: List<RuntimeEffect>) {
        effects.forEach { effect ->
            when (effect) {
                is RuntimeEffect.StartEngineSearch -> engine.startSearch(
                    EngineSearchRequest(
                        searchId = effect.searchId,
                        positionRevision = effect.positionRevision,
                        position = effect.position,
                        limits = EngineSearchLimits(moveTimeMillis = engineMoveTimeMillis()),
                        strength = setup.strength,
                    ),
                )
                is RuntimeEffect.CancelEngineSearch -> engine.cancelSearch(effect.searchId)
                is RuntimeEffect.PersistSnapshot -> persistence.persist(effect.snapshot, setup)
            }
        }
    }

    /**
     * M19 uses a deliberately small deterministic time-management policy because the typed M11 API
     * currently exposes move-time limits rather than full UCI clock fields. The runtime clock remains
     * authoritative; this value only bounds external engine work and can be replaced by richer time
     * management later without changing ownership.
     */
    private fun engineMoveTimeMillis(): Long {
        val clock = state.clock
        val remaining = when (clock.activeSide) {
            ClockSide.WHITE -> clock.whiteRemainingMillis
            ClockSide.BLACK -> clock.blackRemainingMillis
        }
        val desired = (remaining / 40L + clock.incrementMillis / 2L).coerceIn(50L, 1_500L)
        return minOf(desired, (remaining - 10L).coerceAtLeast(1L))
    }

    companion object {
        fun create(
            setup: ResolvedPlaySetup,
            timeSource: MonotonicTimeSource,
            engine: PlayEngineGateway,
            persistence: PlayPersistenceGateway,
        ): PlayRuntimeCoordinator {
            val controllers = RuntimeControllers(
                white = if (setup.humanSide == Color.WHITE) RuntimeController.HUMAN else RuntimeController.ENGINE,
                black = if (setup.humanSide == Color.BLACK) RuntimeController.HUMAN else RuntimeController.ENGINE,
            )
            return PlayRuntimeCoordinator(
                setup = setup,
                runtime = GameRuntime.create(
                    initialPosition = setup.initialPosition,
                    clockConfig = setup.clockConfig,
                    timeSource = timeSource,
                    controllers = controllers,
                    engineHostAvailable = false,
                ),
                engine = engine,
                persistence = persistence,
                nextEventId = 1L,
            )
        }

        fun restore(
            setup: ResolvedPlaySetup,
            snapshot: RuntimeSnapshot,
            timeSource: MonotonicTimeSource,
            engine: PlayEngineGateway,
            persistence: PlayPersistenceGateway,
        ): PlayRuntimeCoordinator = PlayRuntimeCoordinator(
            setup = setup,
            runtime = GameRuntime.restore(snapshot, timeSource),
            engine = engine,
            persistence = persistence,
            nextEventId = (snapshot.processedEventIds.maxOfOrNull { it.value } ?: 0L) + 1L,
        )
    }
}

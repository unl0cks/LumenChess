package dev.lumenchess.runtime

import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Position
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.DeterministicGameClock
import dev.lumenchess.runtime.clock.MonotonicTimeSource

/**
 * The single authoritative serialized owner of a live game.
 *
 * Callers may execute returned [RuntimeEffect] values asynchronously, but they can only feed their
 * results back through [dispatch]. Engines, Binder services, persistence and UI never receive a
 * mutation path into [RuntimeState].
 */
class GameRuntime private constructor(
    initialState: RuntimeState,
    private val timeSource: MonotonicTimeSource,
) {
    private val ownerLock = Any()
    private var authoritativeState: RuntimeState = initialState

    val state: RuntimeState
        get() = synchronized(ownerLock) { authoritativeState }

    fun dispatch(event: RuntimeEvent): RuntimeDispatchResult = synchronized(ownerLock) {
        val current = authoritativeState
        if (event.id in current.processedEventIds) {
            return@synchronized RuntimeDispatchResult(
                state = current,
                effects = emptyList(),
                disposition = RuntimeDisposition.DUPLICATE_EVENT,
            )
        }

        // Freeze one monotonic sample for the complete serialized event. Every clock transition made
        // while reducing this event observes this exact value, so ordering never depends on scheduler
        // or instruction timing.
        val eventSample = timeSource.nowMillis()
        val eventClock = DeterministicGameClock(MonotonicTimeSource { eventSample })
        val transition = GameRuntimeReducer.reduce(current, event, eventClock)
        val finalState = transition.state.copy(
            processedEventIds = transition.state.processedEventIds + event.id,
        )
        authoritativeState = finalState

        val effects = if (transition.persist) {
            transition.effects + RuntimeEffect.PersistSnapshot(finalState.toSnapshot())
        } else {
            transition.effects
        }
        RuntimeDispatchResult(
            state = finalState,
            effects = effects,
            disposition = transition.disposition,
        )
    }

    /**
     * Produces a crash/restore-safe snapshot without mutating the live owner. In-flight engine search
     * state and queued premove input are intentionally absent from [RuntimeSnapshot]; restored games
     * must establish fresh external correlation and user intent before either can affect the game.
     */
    fun snapshotForRestore(): RuntimeSnapshot = synchronized(ownerLock) {
        val sample = timeSource.nowMillis()
        val clock = DeterministicGameClock(MonotonicTimeSource { sample })
        GameRuntimeReducer.settleForSnapshot(authoritativeState, clock).toSnapshot()
    }

    companion object {
        fun create(
            initialPosition: Position,
            clockConfig: ClockConfig,
            timeSource: MonotonicTimeSource,
            controllers: RuntimeControllers,
            engineHostAvailable: Boolean = true,
        ): GameRuntime {
            val clock = DeterministicGameClock(timeSource).create(clockConfig)
            val tree = GameTree.create(initialPosition)
            return GameRuntime(
                initialState = RuntimeState(
                    position = initialPosition,
                    gameTree = tree,
                    currentNodeId = tree.rootId,
                    clock = clock,
                    controllers = controllers,
                    positionRevision = PositionRevision(0),
                    pendingEngineSearch = null,
                    queuedPremove = null,
                    paused = true,
                    started = false,
                    engineHostAvailable = engineHostAvailable,
                    terminal = null,
                    processedEventIds = emptySet(),
                    nextEngineSearchId = 1L,
                ),
                timeSource = timeSource,
            )
        }

        fun restore(
            snapshot: RuntimeSnapshot,
            timeSource: MonotonicTimeSource,
        ): GameRuntime = GameRuntime(
            initialState = RuntimeState(
                position = snapshot.position,
                gameTree = snapshot.gameTree,
                currentNodeId = snapshot.currentNodeId,
                clock = snapshot.clock.copy(running = false, lastSampleMillis = null),
                controllers = snapshot.controllers,
                positionRevision = snapshot.positionRevision,
                pendingEngineSearch = null,
                queuedPremove = null,
                paused = true,
                started = snapshot.started,
                // A restored process must observe a real host-connected event before restarting work.
                engineHostAvailable = false,
                terminal = snapshot.terminal,
                processedEventIds = snapshot.processedEventIds.toSet(),
                nextEngineSearchId = snapshot.nextEngineSearchId,
            ),
            timeSource = timeSource,
        )
    }
}

private fun RuntimeState.toSnapshot(): RuntimeSnapshot = RuntimeSnapshot(
    position = position,
    gameTree = gameTree,
    currentNodeId = currentNodeId,
    clock = clock,
    controllers = controllers,
    positionRevision = positionRevision,
    paused = paused,
    started = started,
    terminal = terminal,
    processedEventIds = processedEventIds.toSet(),
    nextEngineSearchId = nextEngineSearchId,
)

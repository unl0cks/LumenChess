package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.runtime.clock.ClockState

@JvmInline
value class RuntimeEventId(val value: Long) {
    init {
        require(value > 0L) { "Runtime event id must be positive" }
    }
}

enum class RuntimeController {
    HUMAN,
    ENGINE,
}

data class RuntimeControllers(
    val white: RuntimeController,
    val black: RuntimeController,
) {
    fun forSide(side: Color): RuntimeController = when (side) {
        Color.WHITE -> white
        Color.BLACK -> black
    }

    fun withSide(side: Color, controller: RuntimeController): RuntimeControllers = when (side) {
        Color.WHITE -> copy(white = controller)
        Color.BLACK -> copy(black = controller)
    }
}

sealed interface RuntimeTerminal {
    data class Timeout(val loser: Color) : RuntimeTerminal
    data class Resignation(val loser: Color) : RuntimeTerminal
    data object DrawAgreement : RuntimeTerminal
    data class Checkmate(val winner: Color) : RuntimeTerminal
    data object Stalemate : RuntimeTerminal
}

data class PendingEngineSearch(
    val searchId: EngineSearchId,
    val positionRevision: PositionRevision,
)

data class RuntimeState(
    val position: Position,
    val gameTree: GameTree,
    val currentNodeId: GameNodeId,
    val clock: ClockState,
    val controllers: RuntimeControllers,
    val positionRevision: PositionRevision,
    val pendingEngineSearch: PendingEngineSearch?,
    val paused: Boolean,
    val started: Boolean,
    val engineHostAvailable: Boolean,
    val terminal: RuntimeTerminal?,
    val processedEventIds: Set<RuntimeEventId>,
    val nextEngineSearchId: Long,
)

data class RuntimeSnapshot(
    val position: Position,
    val gameTree: GameTree,
    val currentNodeId: GameNodeId,
    val clock: ClockState,
    val controllers: RuntimeControllers,
    val positionRevision: PositionRevision,
    val paused: Boolean,
    val started: Boolean,
    val terminal: RuntimeTerminal?,
    val processedEventIds: Set<RuntimeEventId>,
    val nextEngineSearchId: Long,
)

sealed interface RuntimeEvent {
    val id: RuntimeEventId

    data class Start(override val id: RuntimeEventId) : RuntimeEvent
    data class HumanMove(override val id: RuntimeEventId, val move: Move) : RuntimeEvent
    data class EngineCompleted(override val id: RuntimeEventId, val result: EngineSearchResult) : RuntimeEvent
    data class Pause(override val id: RuntimeEventId) : RuntimeEvent
    data class Resume(override val id: RuntimeEventId) : RuntimeEvent
    data class ChangeController(
        override val id: RuntimeEventId,
        val side: Color,
        val controller: RuntimeController,
    ) : RuntimeEvent
    data class EngineHostDied(override val id: RuntimeEventId) : RuntimeEvent
    data class EngineHostRecovered(override val id: RuntimeEventId) : RuntimeEvent
    data class Resign(override val id: RuntimeEventId, val loser: Color) : RuntimeEvent
    data class AgreeDraw(override val id: RuntimeEventId) : RuntimeEvent
}

sealed interface RuntimeEffect {
    data class StartEngineSearch(
        val searchId: EngineSearchId,
        val positionRevision: PositionRevision,
        val position: Position,
    ) : RuntimeEffect

    data class CancelEngineSearch(val searchId: EngineSearchId) : RuntimeEffect

    /** Canonical runtime snapshot request. Executing persistence never mutates runtime state directly. */
    data class PersistSnapshot(val snapshot: RuntimeSnapshot) : RuntimeEffect
}

enum class RuntimeDisposition {
    APPLIED,
    DUPLICATE_EVENT,
    STALE_ENGINE_RESULT,
    ILLEGAL_ENGINE_RESULT,
    ILLEGAL_HUMAN_MOVE,
    WRONG_CONTROLLER,
    PAUSED,
    TERMINAL,
    IGNORED,
}

data class RuntimeDispatchResult(
    val state: RuntimeState,
    val effects: List<RuntimeEffect>,
    val disposition: RuntimeDisposition,
)

internal data class RuntimeTransition(
    val state: RuntimeState,
    val effects: List<RuntimeEffect> = emptyList(),
    val disposition: RuntimeDisposition = RuntimeDisposition.APPLIED,
    val persist: Boolean = false,
)

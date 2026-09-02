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

const val DEFAULT_PREMOVE_COST_MILLIS: Long = 100L

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

/** A presentation-facing control lease owned by the authoritative runtime.
 *
 * A null move count means that control remains manual until an explicit release. A lease is
 * deliberately separate from [RuntimeController.HUMAN] so ordinary Play sessions keep their
 * existing semantics and so the Arena UI cannot become a second move counter.
 */
data class ManualControlLease(
    val remainingMoves: Int? = null,
) {
    init {
        require(remainingMoves == null || remainingMoves > 0) {
            "A manual control lease must have a positive move count or be unlimited"
        }
    }
}

enum class ManualClockPolicy {
    LOCKED,
    COUNT_TIME,
}

data class RuntimeManualControl(
    val white: ManualControlLease? = null,
    val black: ManualControlLease? = null,
    val clockPolicy: ManualClockPolicy = ManualClockPolicy.LOCKED,
) {
    fun forSide(side: Color): ManualControlLease? = when (side) {
        Color.WHITE -> white
        Color.BLACK -> black
    }

    val isActive: Boolean get() = white != null || black != null
    val clocksLocked: Boolean get() = isActive && clockPolicy == ManualClockPolicy.LOCKED

    fun withSide(side: Color, lease: ManualControlLease?): RuntimeManualControl = when (side) {
        Color.WHITE -> copy(white = lease)
        Color.BLACK -> copy(black = lease)
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

/**
 * A single ephemeral premove intent. It is deliberately not present in [RuntimeSnapshot]: a queued
 * input is not canonical game state and must never survive process restoration into an unrelated
 * future position.
 */
data class QueuedPremove(
    val side: Color,
    val move: Move,
    val queuedAtRevision: PositionRevision,
)

data class RuntimeState(
    val position: Position,
    val gameTree: GameTree,
    val currentNodeId: GameNodeId,
    val clock: ClockState,
    val controllers: RuntimeControllers,
    val positionRevision: PositionRevision,
    val pendingEngineSearch: PendingEngineSearch?,
    val queuedPremove: QueuedPremove?,
    val paused: Boolean,
    val started: Boolean,
    val engineHostAvailable: Boolean,
    val terminal: RuntimeTerminal?,
    val processedEventIds: Set<RuntimeEventId>,
    val nextEngineSearchId: Long,
    val manualControl: RuntimeManualControl = RuntimeManualControl(),
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
    val manualControl: RuntimeManualControl = RuntimeManualControl(),
)

sealed interface RuntimeEvent {
    val id: RuntimeEventId

    data class Start(override val id: RuntimeEventId) : RuntimeEvent
    /** Neutral boundary event used by lifecycle/presentation scheduling to make a flag fall authoritative. */
    data class ClockCheck(override val id: RuntimeEventId) : RuntimeEvent
    data class HumanMove(override val id: RuntimeEventId, val move: Move) : RuntimeEvent
    data class EngineCompleted(override val id: RuntimeEventId, val result: EngineSearchResult) : RuntimeEvent
    data class QueuePremove(
        override val id: RuntimeEventId,
        val side: Color,
        val move: Move,
    ) : RuntimeEvent
    data class CancelPremove(
        override val id: RuntimeEventId,
        val side: Color,
    ) : RuntimeEvent
    data class Pause(override val id: RuntimeEventId) : RuntimeEvent
    data class Resume(override val id: RuntimeEventId) : RuntimeEvent
    data class ChangeController(
        override val id: RuntimeEventId,
        val side: Color,
        val controller: RuntimeController,
    ) : RuntimeEvent
    /** Atomically replaces the manually controlled side set and its clock policy. */
    data class SetManualControl(
        override val id: RuntimeEventId,
        val manualControl: RuntimeManualControl,
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

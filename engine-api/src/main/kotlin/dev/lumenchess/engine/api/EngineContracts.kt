package dev.lumenchess.engine.api

import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant

@JvmInline
value class EngineSearchId(val value: Long) {
    init { require(value > 0L) { "Engine search ID must be positive" } }
}

@JvmInline
value class PositionRevision(val value: Long) {
    init { require(value >= 0L) { "Position revision cannot be negative" } }
}

@JvmInline
value class EngineSessionId(val value: String) {
    init { require(value.isNotBlank()) { "Engine session ID cannot be blank" } }
}

data class EngineMultiPvCapability(val maxLines: Int) {
    init { require(maxLines > 0) { "MultiPV line count must be positive" } }
}

sealed interface EngineStrengthCapability {
    data class EloRange(val minElo: Int, val maxElo: Int) : EngineStrengthCapability {
        init {
            require(minElo > 0) { "Minimum Elo must be positive" }
            require(maxElo >= minElo) { "Maximum Elo cannot be below minimum Elo" }
        }
    }
}

data class EngineCapabilities(
    val variants: Set<Variant>,
    val multiPv: EngineMultiPvCapability? = null,
    val supportsPonder: Boolean = false,
    val strength: EngineStrengthCapability? = null,
) {
    init { require(variants.isNotEmpty()) { "At least one supported variant is required" } }

    fun supports(variant: Variant): Boolean = variant in variants
}

data class EngineSearchLimits(
    val depth: Int? = null,
    val nodes: Long? = null,
    val moveTimeMillis: Long? = null,
) {
    init {
        require(depth == null || depth > 0) { "Search depth must be positive" }
        require(nodes == null || nodes > 0L) { "Search node limit must be positive" }
        require(moveTimeMillis == null || moveTimeMillis > 0L) { "Move time must be positive" }
    }
}

data class EngineSearchRequest(
    val searchId: EngineSearchId,
    val positionRevision: PositionRevision,
    val position: Position,
    val limits: EngineSearchLimits,
    val multiPv: Int = 1,
) {
    init { require(multiPv > 0) { "Requested MultiPV line count must be positive" } }
}

data class EngineSearchResult(
    val searchId: EngineSearchId,
    val positionRevision: PositionRevision,
    val bestMoveUci: String?,
)

sealed interface EngineSessionCommand {
    data object NewGame : EngineSessionCommand
    data class StartSearch(val request: EngineSearchRequest) : EngineSessionCommand
    data class StopSearch(val searchId: EngineSearchId) : EngineSessionCommand
    data object Close : EngineSessionCommand
}

/**
 * Transport-neutral engine session boundary. M12 owns the Android process/Binder transport that
 * eventually implements this contract; M11 deliberately makes no process or native assumptions.
 */
interface EngineSession {
    val sessionId: EngineSessionId
    val capabilities: EngineCapabilities
    fun submit(command: EngineSessionCommand)
}

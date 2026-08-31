package dev.lumenchess.arena

import dev.lumenchess.core.chess.Color
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.LoadedCanonicalGame
import dev.lumenchess.data.persistence.PersistenceMappingException
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeControllers
import dev.lumenchess.runtime.RuntimeEventId
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.RuntimeTerminal
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.ClockSide
import dev.lumenchess.runtime.clock.ClockState

data class RestoredArenaGame(
    val gameId: String,
    val setup: ResolvedArenaSetup,
    val snapshot: RuntimeSnapshot,
    val createdAtEpochMillis: Long,
)

object ArenaSnapshotCodec {
    private const val VERSION = "1"
    private const val PREFIX = "lumen.arena.m20."

    fun encode(snapshot: RuntimeSnapshot, setup: ResolvedArenaSetup): Map<String, String> = buildMap {
        put(key("version"), VERSION)
        encodeEngine("white", setup.white).forEach(::put)
        encodeEngine("black", setup.black).forEach(::put)
        setup.chess960Index?.let { put(key("chess960Index"), it.toString()) }
        put(key("initialMillis"), setup.clockConfig.initialMillis.toString())
        put(key("incrementMillis"), setup.clockConfig.incrementMillis.toString())
        put(key("openingMode"), setup.opening.mode.name)
        put(key("openingLabel"), setup.opening.label)
        setup.opening.familyId?.let { put(key("openingFamily"), it) }
        put(key("positionRevision"), snapshot.positionRevision.value.toString())
        put(key("clockWhite"), snapshot.clock.whiteRemainingMillis.toString())
        put(key("clockBlack"), snapshot.clock.blackRemainingMillis.toString())
        put(key("clockActive"), snapshot.clock.activeSide.name)
        put(key("clockIncrement"), snapshot.clock.incrementMillis.toString())
        snapshot.clock.timedOutSide?.let { put(key("clockTimedOut"), it.name) }
        put(key("started"), snapshot.started.toString())
        snapshot.terminal?.let { put(key("terminal"), encodeTerminal(it)) }
        put(key("processedEventIds"), snapshot.processedEventIds.map { it.value }.sorted().joinToString(","))
        put(key("nextEngineSearchId"), snapshot.nextEngineSearchId.toString())
    }

    fun decode(game: LoadedCanonicalGame): RestoredArenaGame {
        val metadata = game.sources
            .asSequence()
            .filter { it.type == GameSourceType.ENGINE_ARENA }
            .map { it.metadata }
            .firstOrNull { it[key("version")] == VERSION }
            ?: throw PersistenceMappingException("Game ${game.id.value} has no M20 Arena restore metadata")

        val initialMillis = longValue(metadata, "initialMillis")
        val incrementMillis = longValue(metadata, "incrementMillis")
        val openingMode = enumValue<ArenaOpeningMode>(metadata, "openingMode")
        val setup = ResolvedArenaSetup(
            variant = game.tree.startPosition.variant,
            chess960Index = metadata[key("chess960Index")]?.let { value ->
                value.toIntOrNull() ?: malformed("Chess960 index", value)
            },
            white = decodeEngine(metadata, "white"),
            black = decodeEngine(metadata, "black"),
            clockConfig = ClockConfig(initialMillis, incrementMillis),
            opening = ResolvedArenaOpening(
                mode = openingMode,
                label = required(metadata, "openingLabel"),
                familyId = metadata[key("openingFamily")],
                position = game.tree.startPosition,
                appliedMoves = emptyList(),
            ),
            initialPosition = game.tree.startPosition,
        )
        val mainline = game.tree.mainline()
        val revision = longValue(metadata, "positionRevision")
        if (revision != mainline.size.toLong()) {
            throw PersistenceMappingException("Stored Arena revision $revision disagrees with canonical plies ${mainline.size}")
        }
        val currentNode = mainline.lastOrNull() ?: game.tree.root
        val clock = ClockState(
            whiteRemainingMillis = longValue(metadata, "clockWhite"),
            blackRemainingMillis = longValue(metadata, "clockBlack"),
            activeSide = enumValue(metadata, "clockActive"),
            incrementMillis = longValue(metadata, "clockIncrement"),
            running = false,
            lastSampleMillis = null,
            timedOutSide = metadata[key("clockTimedOut")]?.let { parseEnum<ClockSide>(it, "clockTimedOut") },
        )
        val processedIds = required(metadata, "processedEventIds")
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { token -> token.toLongOrNull()?.let(::RuntimeEventId) ?: malformed("event id", token) }
            ?.toSet()
            .orEmpty()
        val snapshot = RuntimeSnapshot(
            position = currentNode.position,
            gameTree = game.tree,
            currentNodeId = currentNode.id,
            clock = clock,
            controllers = RuntimeControllers(RuntimeController.ENGINE, RuntimeController.ENGINE),
            positionRevision = PositionRevision(revision),
            paused = true,
            started = booleanValue(metadata, "started"),
            terminal = metadata[key("terminal")]?.let(::decodeTerminal),
            processedEventIds = processedIds,
            nextEngineSearchId = longValue(metadata, "nextEngineSearchId").also {
                if (it <= 0) throw PersistenceMappingException("Stored next Arena search id must be positive")
            },
        )
        return RestoredArenaGame(game.id.value, setup, snapshot, game.metadata.createdAtEpochMillis)
    }

    private fun encodeEngine(prefix: String, engine: ResolvedArenaEngine): Map<String, String> = mapOf(
        key("${prefix}Engine") to engine.engine.name,
        key("${prefix}StrengthModel") to engine.strength.model.name,
        key("${prefix}StrengthTarget") to encodeStrengthTarget(engine.strength.target),
        key("${prefix}StrengthSeed") to engine.strength.seed.toString(),
    )

    private fun decodeEngine(metadata: Map<String, String>, prefix: String): ResolvedArenaEngine =
        ResolvedArenaEngine(
            engine = enumValue(metadata, "${prefix}Engine"),
            strength = EngineStrengthSettings(
                target = decodeStrengthTarget(required(metadata, "${prefix}StrengthTarget")),
                model = enumValue<EngineStrengthModel>(metadata, "${prefix}StrengthModel"),
                seed = longValue(metadata, "${prefix}StrengthSeed"),
            ),
        )

    private fun key(name: String) = PREFIX + name
    private fun required(metadata: Map<String, String>, name: String): String =
        metadata[key(name)] ?: throw PersistenceMappingException("Missing stored Arena metadata '$name'")
    private fun longValue(metadata: Map<String, String>, name: String): Long =
        required(metadata, name).toLongOrNull() ?: malformed("long $name", required(metadata, name))
    private fun booleanValue(metadata: Map<String, String>, name: String): Boolean = when (val value = required(metadata, name)) {
        "true" -> true
        "false" -> false
        else -> malformed("boolean $name", value)
    }
    private inline fun <reified T : Enum<T>> enumValue(metadata: Map<String, String>, name: String): T =
        parseEnum(required(metadata, name), name)
    private inline fun <reified T : Enum<T>> parseEnum(value: String, name: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: malformed("enum $name", value)

    private fun encodeStrengthTarget(target: EngineStrengthTarget): String = when (target) {
        EngineStrengthTarget.FullStrength -> "FULL"
        is EngineStrengthTarget.Elo -> "ELO:${target.value}"
    }
    private fun decodeStrengthTarget(value: String): EngineStrengthTarget = when {
        value == "FULL" -> EngineStrengthTarget.FullStrength
        value.startsWith("ELO:") -> value.substringAfter(':').toIntOrNull()
            ?.let { runCatching { EngineStrengthTarget.Elo(it) }.getOrNull() }
            ?: malformed("strength target", value)
        else -> malformed("strength target", value)
    }
    private fun encodeTerminal(terminal: RuntimeTerminal): String = when (terminal) {
        is RuntimeTerminal.Timeout -> "TIMEOUT:${terminal.loser.name}"
        is RuntimeTerminal.Resignation -> "RESIGNATION:${terminal.loser.name}"
        RuntimeTerminal.DrawAgreement -> "DRAW_AGREEMENT"
        is RuntimeTerminal.Checkmate -> "CHECKMATE:${terminal.winner.name}"
        RuntimeTerminal.Stalemate -> "STALEMATE"
    }
    private fun decodeTerminal(value: String): RuntimeTerminal = when {
        value == "DRAW_AGREEMENT" -> RuntimeTerminal.DrawAgreement
        value == "STALEMATE" -> RuntimeTerminal.Stalemate
        value.startsWith("TIMEOUT:") -> RuntimeTerminal.Timeout(parseEnum<Color>(value.substringAfter(':'), "loser"))
        value.startsWith("RESIGNATION:") -> RuntimeTerminal.Resignation(parseEnum<Color>(value.substringAfter(':'), "loser"))
        value.startsWith("CHECKMATE:") -> RuntimeTerminal.Checkmate(parseEnum<Color>(value.substringAfter(':'), "winner"))
        else -> malformed("terminal", value)
    }
    private fun malformed(kind: String, value: String): Nothing =
        throw PersistenceMappingException("Malformed stored Arena $kind '$value'")
}

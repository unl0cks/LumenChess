package dev.lumenchess.play

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.data.persistence.LoadedCanonicalGame
import dev.lumenchess.data.persistence.PersistenceMappingException
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeControllers
import dev.lumenchess.runtime.RuntimeEventId
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.RuntimeTerminal
import dev.lumenchess.runtime.clock.ClockSide
import dev.lumenchess.runtime.clock.ClockState

/** M19 metadata codec. Canonical chess history stays in Room; only live-runtime restoration state lives here. */
object PlaySnapshotCodec {
    private const val VERSION = "1"
    private const val PREFIX = "lumen.play.m19."

    fun encode(snapshot: RuntimeSnapshot, setup: ResolvedPlaySetup): Map<String, String> = buildMap {
        put(key("version"), VERSION)
        put(key("engine"), setup.engine.name)
        put(key("humanSide"), setup.humanSide.name)
        put(key("strengthModel"), setup.strength.model.name)
        put(key("strengthTarget"), encodeStrengthTarget(setup.strength.target))
        put(key("strengthSeed"), setup.strength.seed.toString())
        setup.chess960Index?.let { put(key("chess960Index"), it.toString()) }
        put(key("initialMillis"), setup.clockConfig.initialMillis.toString())
        put(key("incrementMillis"), setup.clockConfig.incrementMillis.toString())

        put(key("positionRevision"), snapshot.positionRevision.value.toString())
        put(key("clockWhite"), snapshot.clock.whiteRemainingMillis.toString())
        put(key("clockBlack"), snapshot.clock.blackRemainingMillis.toString())
        put(key("clockActive"), snapshot.clock.activeSide.name)
        put(key("clockIncrement"), snapshot.clock.incrementMillis.toString())
        snapshot.clock.timedOutSide?.let { put(key("clockTimedOut"), it.name) }
        put(key("started"), snapshot.started.toString())
        put(key("paused"), snapshot.paused.toString())
        snapshot.terminal?.let { put(key("terminal"), encodeTerminal(it)) }
        put(
            key("processedEventIds"),
            snapshot.processedEventIds.map { it.value }.sorted().joinToString(","),
        )
        put(key("nextEngineSearchId"), snapshot.nextEngineSearchId.toString())
    }

    fun decode(game: LoadedCanonicalGame): RestoredPlayGame {
        val metadata = game.sources
            .asSequence()
            .map { it.metadata }
            .firstOrNull { it[key("version")] == VERSION }
            ?: throw PersistenceMappingException("Game ${game.id.value} has no M19 Play restore metadata")

        val engine = enumValue<PlayEngine>(metadata, "engine")
        val humanSide = enumValue<Color>(metadata, "humanSide")
        val strengthModel = enumValue<EngineStrengthModel>(metadata, "strengthModel")
        val strengthTarget = decodeStrengthTarget(required(metadata, "strengthTarget"))
        val seed = longValue(metadata, "strengthSeed")
        val initialMillis = longValue(metadata, "initialMillis")
        val incrementMillis = longValue(metadata, "incrementMillis")
        val chess960Index = metadata[key("chess960Index")]?.toIntOrNull()

        val setupConfig = PlaySetupConfig(
            variant = game.tree.startPosition.variant,
            chess960Index = chess960Index,
            engine = engine,
            side = if (humanSide == Color.WHITE) PlaySide.WHITE else PlaySide.BLACK,
            strengthModel = strengthModel,
            strengthTarget = strengthTarget,
            timeControl = PlayTimeControl(initialMillis, incrementMillis),
            strengthSeed = seed,
        )
        val setup = PlaySetupResolver.resolve(setupConfig)
        if (Fen.serialize(setup.initialPosition) != Fen.serialize(game.tree.startPosition)) {
            throw PersistenceMappingException("Stored Play setup does not reproduce game ${game.id.value} start position")
        }

        val mainline = game.tree.mainline()
        val revision = longValue(metadata, "positionRevision")
        if (revision != mainline.size.toLong()) {
            throw PersistenceMappingException(
                "Stored Play revision $revision disagrees with ${mainline.size} canonical plies for ${game.id.value}",
            )
        }
        val currentNode = mainline.lastOrNull() ?: game.tree.root
        val terminal = metadata[key("terminal")]?.let(::decodeTerminal)
        val clockTimedOut = metadata[key("clockTimedOut")]?.let { parseEnum<ClockSide>(it, "clockTimedOut") }
        val clock = ClockState(
            whiteRemainingMillis = longValue(metadata, "clockWhite"),
            blackRemainingMillis = longValue(metadata, "clockBlack"),
            activeSide = enumValue(metadata, "clockActive"),
            incrementMillis = longValue(metadata, "clockIncrement"),
            running = false,
            lastSampleMillis = null,
            timedOutSide = clockTimedOut,
        )
        val controllers = RuntimeControllers(
            white = if (humanSide == Color.WHITE) RuntimeController.HUMAN else RuntimeController.ENGINE,
            black = if (humanSide == Color.BLACK) RuntimeController.HUMAN else RuntimeController.ENGINE,
        )
        val processedIds = required(metadata, "processedEventIds")
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.map { token ->
                token.toLongOrNull()?.let(::RuntimeEventId)
                    ?: throw PersistenceMappingException("Malformed stored Play event id '$token'")
            }
            ?.toSet()
            .orEmpty()
        val nextSearchId = longValue(metadata, "nextEngineSearchId")
        if (nextSearchId <= 0L) throw PersistenceMappingException("Stored next engine search id must be positive")

        val snapshot = RuntimeSnapshot(
            position = currentNode.position,
            gameTree = game.tree,
            currentNodeId = currentNode.id,
            clock = clock,
            controllers = controllers,
            positionRevision = PositionRevision(revision),
            paused = true,
            started = booleanValue(metadata, "started"),
            terminal = terminal,
            processedEventIds = processedIds,
            nextEngineSearchId = nextSearchId,
        )
        return RestoredPlayGame(
            gameId = game.id.value,
            setup = setup,
            snapshot = snapshot,
            createdAtEpochMillis = game.metadata.createdAtEpochMillis,
        )
    }

    private fun key(name: String): String = PREFIX + name

    private fun required(metadata: Map<String, String>, name: String): String =
        metadata[key(name)] ?: throw PersistenceMappingException("Missing stored Play metadata '$name'")

    private fun longValue(metadata: Map<String, String>, name: String): Long =
        required(metadata, name).toLongOrNull()
            ?: throw PersistenceMappingException("Malformed stored Play long '$name'")

    private fun booleanValue(metadata: Map<String, String>, name: String): Boolean = when (val value = required(metadata, name)) {
        "true" -> true
        "false" -> false
        else -> throw PersistenceMappingException("Malformed stored Play boolean '$name=$value'")
    }

    private inline fun <reified T : Enum<T>> enumValue(metadata: Map<String, String>, name: String): T =
        parseEnum(required(metadata, name), name)

    private inline fun <reified T : Enum<T>> parseEnum(value: String, name: String): T =
        enumValues<T>().firstOrNull { it.name == value }
            ?: throw PersistenceMappingException("Malformed stored Play enum '$name=$value'")

    private fun encodeStrengthTarget(target: EngineStrengthTarget): String = when (target) {
        EngineStrengthTarget.FullStrength -> "FULL"
        is EngineStrengthTarget.Elo -> "ELO:${target.value}"
    }

    private fun decodeStrengthTarget(value: String): EngineStrengthTarget = when {
        value == "FULL" -> EngineStrengthTarget.FullStrength
        value.startsWith("ELO:") -> value.removePrefix("ELO:").toIntOrNull()?.let(EngineStrengthTarget::Elo)
            ?: throw PersistenceMappingException("Malformed stored Play strength target '$value'")
        else -> throw PersistenceMappingException("Malformed stored Play strength target '$value'")
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
        value.startsWith("TIMEOUT:") -> RuntimeTerminal.Timeout(parseEnum(value.substringAfter(':'), "terminal loser"))
        value.startsWith("RESIGNATION:") -> RuntimeTerminal.Resignation(parseEnum(value.substringAfter(':'), "terminal loser"))
        value.startsWith("CHECKMATE:") -> RuntimeTerminal.Checkmate(parseEnum(value.substringAfter(':'), "terminal winner"))
        else -> throw PersistenceMappingException("Malformed stored Play terminal '$value'")
    }
}

data class RestoredPlayGame(
    val gameId: String,
    val setup: ResolvedPlaySetup,
    val snapshot: RuntimeSnapshot,
    val createdAtEpochMillis: Long,
)

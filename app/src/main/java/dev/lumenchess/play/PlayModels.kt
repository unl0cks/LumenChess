package dev.lumenchess.play

import dev.lumenchess.core.chess.Chess960
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineCapabilities
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthPlanner
import dev.lumenchess.engine.api.EngineStrengthPlanning
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.host.Reckless09Engine
import dev.lumenchess.engine.host.Stockfish18Engine
import dev.lumenchess.runtime.clock.ClockConfig

data class PlayTimeControl(
    val initialMillis: Long = 600_000L,
    val incrementMillis: Long = 0L,
)

enum class PlaySide { WHITE, BLACK, RANDOM }

enum class PlayEngine(
    val id: String,
    val displayName: String,
    val capabilities: EngineCapabilities,
) {
    STOCKFISH_18(Stockfish18Engine.ID, "Stockfish 18", Stockfish18Engine.capabilities),
    RECKLESS_0_9_0(Reckless09Engine.ID, "Reckless 0.9.0", Reckless09Engine.capabilities),
}

data class PlaySetupConfig(
    val variant: Variant = Variant.STANDARD,
    val chess960Index: Int? = null,
    val engine: PlayEngine = PlayEngine.STOCKFISH_18,
    val side: PlaySide = PlaySide.WHITE,
    val strengthModel: EngineStrengthModel = EngineStrengthModel.HYBRID,
    val strengthTarget: EngineStrengthTarget = EngineStrengthTarget.Elo(1600),
    val timeControl: PlayTimeControl = PlayTimeControl(),
    val strengthSeed: Long = 0L,
)

data class ResolvedPlaySetup(
    val variant: Variant,
    val chess960Index: Int?,
    val engine: PlayEngine,
    val humanSide: Color,
    val strength: EngineStrengthSettings,
    val clockConfig: ClockConfig,
    val initialPosition: Position,
)

sealed interface PlaySetupValidation {
    data object Valid : PlaySetupValidation
    data class Invalid(val reason: String) : PlaySetupValidation
    data class UnsupportedStrength(val reason: String) : PlaySetupValidation
}

object PlaySetupValidator {
    fun validate(setup: PlaySetupConfig): PlaySetupValidation {
        if (setup.timeControl.initialMillis <= 0L) {
            return PlaySetupValidation.Invalid("Initial clock time must be positive")
        }
        if (setup.timeControl.incrementMillis < 0L) {
            return PlaySetupValidation.Invalid("Increment cannot be negative")
        }
        if (setup.variant == Variant.CHESS960 && setup.chess960Index !in 0..959) {
            return PlaySetupValidation.Invalid("Chess960 index must be between 0 and 959")
        }
        if (!setup.engine.capabilities.supports(setup.variant)) {
            return PlaySetupValidation.Invalid("Selected engine does not support ${setup.variant}")
        }
        val settings = EngineStrengthSettings(setup.strengthTarget, setup.strengthModel, setup.strengthSeed)
        return when (val plan = EngineStrengthPlanner.plan(settings, setup.engine.capabilities)) {
            is EngineStrengthPlanning.Supported -> PlaySetupValidation.Valid
            is EngineStrengthPlanning.Unsupported -> PlaySetupValidation.UnsupportedStrength(plan.reason)
        }
    }
}

object PlaySetupResolver {
    fun resolve(
        setup: PlaySetupConfig,
        randomSide: () -> Color = { if (kotlin.random.Random.nextBoolean()) Color.WHITE else Color.BLACK },
    ): ResolvedPlaySetup {
        when (val validation = PlaySetupValidator.validate(setup)) {
            PlaySetupValidation.Valid -> Unit
            is PlaySetupValidation.Invalid -> error(validation.reason)
            is PlaySetupValidation.UnsupportedStrength -> error(validation.reason)
        }
        val human = when (setup.side) {
            PlaySide.WHITE -> Color.WHITE
            PlaySide.BLACK -> Color.BLACK
            PlaySide.RANDOM -> randomSide()
        }
        val index = if (setup.variant == Variant.CHESS960) requireNotNull(setup.chess960Index) else null
        return ResolvedPlaySetup(
            variant = setup.variant,
            chess960Index = index,
            engine = setup.engine,
            humanSide = human,
            strength = EngineStrengthSettings(setup.strengthTarget, setup.strengthModel, setup.strengthSeed),
            clockConfig = ClockConfig(setup.timeControl.initialMillis, setup.timeControl.incrementMillis),
            initialPosition = if (setup.variant == Variant.STANDARD) Position.initial() else Chess960.startingPosition(index!!),
        )
    }
}

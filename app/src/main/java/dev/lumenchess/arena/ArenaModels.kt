package dev.lumenchess.arena

import dev.lumenchess.core.chess.Chess960
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthPlanner
import dev.lumenchess.engine.api.EngineStrengthPlanning
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.ManualClockPolicy
import dev.lumenchess.runtime.ManualControlLease
import dev.lumenchess.runtime.RuntimeManualControl

enum class ArenaColorAssignment { FIXED, RANDOM }

enum class ArenaOpeningMode {
    NORMAL,
    RANDOM_OPENING,
    OPENING_FAMILY,
    CUSTOM_FEN,
    RANDOM_CHESS960,
}

enum class ArenaManualSide {
    NONE,
    WHITE,
    BLACK,
    BOTH,
}

enum class ArenaManualLimitMode {
    FINITE,
    UNTIL_RELEASE,
}

data class ArenaManualOpeningSetup(
    val sides: ArenaManualSide = ArenaManualSide.NONE,
    val limitMode: ArenaManualLimitMode = ArenaManualLimitMode.FINITE,
    val moveLimitText: String = "3",
    val clockPolicy: ManualClockPolicy = ManualClockPolicy.LOCKED,
) {
    val moveLimit: Int?
        get() = moveLimitText.toIntOrNull()

    fun toRuntimeControl(): RuntimeManualControl {
        if (sides == ArenaManualSide.NONE) return RuntimeManualControl()
        val lease = when (limitMode) {
            ArenaManualLimitMode.FINITE -> ManualControlLease(moveLimit)
            ArenaManualLimitMode.UNTIL_RELEASE -> ManualControlLease()
        }
        return RuntimeManualControl(
            white = if (sides == ArenaManualSide.WHITE || sides == ArenaManualSide.BOTH) lease else null,
            black = if (sides == ArenaManualSide.BLACK || sides == ArenaManualSide.BOTH) lease else null,
            clockPolicy = clockPolicy,
        )
    }
}

data class ArenaEngineConfig(
    val engine: PlayEngine = PlayEngine.STOCKFISH_18,
    val strengthModel: EngineStrengthModel = EngineStrengthModel.HYBRID,
    val strengthTarget: EngineStrengthTarget = EngineStrengthTarget.Elo(1600),
    val strengthSeed: Long = 0L,
)

data class ArenaOpeningSetup(
    val mode: ArenaOpeningMode = ArenaOpeningMode.NORMAL,
    val familyId: String? = null,
    val customFen: String = "",
    val handoffPlies: Int = 8,
)

data class ArenaSetupConfig(
    val variant: Variant = Variant.STANDARD,
    val chess960Index: Int? = null,
    val white: ArenaEngineConfig = ArenaEngineConfig(),
    val black: ArenaEngineConfig = ArenaEngineConfig(engine = PlayEngine.RECKLESS_0_9_0),
    val colorAssignment: ArenaColorAssignment = ArenaColorAssignment.FIXED,
    val timeControl: PlayTimeControl = PlayTimeControl(),
    val opening: ArenaOpeningSetup = ArenaOpeningSetup(),
    val manualOpening: ArenaManualOpeningSetup = ArenaManualOpeningSetup(),
)

data class ResolvedArenaEngine(
    val engine: PlayEngine,
    val strength: EngineStrengthSettings,
)

data class ResolvedArenaOpening(
    val mode: ArenaOpeningMode,
    val label: String,
    val familyId: String?,
    val position: Position,
    val appliedMoves: List<dev.lumenchess.core.chess.Move>,
)

data class ResolvedArenaSetup(
    val variant: Variant,
    val chess960Index: Int?,
    val white: ResolvedArenaEngine,
    val black: ResolvedArenaEngine,
    val clockConfig: ClockConfig,
    val opening: ResolvedArenaOpening,
    val initialPosition: Position,
    val manualControl: RuntimeManualControl = RuntimeManualControl(),
)

sealed interface ArenaSetupValidation {
    data object Valid : ArenaSetupValidation
    data class Invalid(val reason: String) : ArenaSetupValidation
    data class UnsupportedStrength(val reason: String) : ArenaSetupValidation
}

object ArenaSetupValidator {
    fun validate(setup: ArenaSetupConfig): ArenaSetupValidation {
        if (setup.timeControl.initialMillis <= 0L) {
            return ArenaSetupValidation.Invalid("Initial clock time must be positive")
        }
        if (setup.timeControl.incrementMillis < 0L) {
            return ArenaSetupValidation.Invalid("Increment cannot be negative")
        }
        if (setup.chess960Index != null && setup.chess960Index !in 0 until Chess960.POSITION_COUNT) {
            return ArenaSetupValidation.Invalid("Chess960 index must be between 0 and 959")
        }
        if (setup.opening.handoffPlies !in 1..12) {
            return ArenaSetupValidation.Invalid("Opening handoff must be between 1 and 12 plies")
        }
        if (setup.manualOpening.sides != ArenaManualSide.NONE &&
            setup.manualOpening.limitMode == ArenaManualLimitMode.FINITE
        ) {
            val moveLimit = setup.manualOpening.moveLimit
            if (moveLimit == null || moveLimit !in 1..99) {
                return ArenaSetupValidation.Invalid("Manual move limit must be between 1 and 99")
            }
        }
        if (setup.opening.mode == ArenaOpeningMode.OPENING_FAMILY &&
            ArenaOpeningCatalog.byFamily(setup.opening.familyId).isEmpty()
        ) {
            return ArenaSetupValidation.Invalid("Choose an available opening family")
        }
        if (setup.opening.mode == ArenaOpeningMode.CUSTOM_FEN) {
            val parsed = runCatching { Fen.parse(setup.opening.customFen, setup.variant) }
            if (parsed.isFailure) {
                return ArenaSetupValidation.Invalid(
                    parsed.exceptionOrNull()?.message ?: "Custom FEN is invalid",
                )
            }
        }
        if (setup.opening.mode == ArenaOpeningMode.RANDOM_CHESS960 && setup.variant != Variant.CHESS960) {
            return ArenaSetupValidation.Invalid("Random Chess960 requires the Chess960 variant")
        }
        if (
            setup.variant == Variant.CHESS960 &&
            setup.opening.mode in setOf(ArenaOpeningMode.RANDOM_OPENING, ArenaOpeningMode.OPENING_FAMILY)
        ) {
            return ArenaSetupValidation.Invalid("Opening-book starts are available for Standard chess only")
        }
        for ((side, config) in listOf("White" to setup.white, "Black" to setup.black)) {
            if (!config.engine.capabilities.supports(setup.variant)) {
                return ArenaSetupValidation.Invalid("$side engine does not support ${setup.variant}")
            }
            val settings = config.toStrengthSettings()
            when (val plan = EngineStrengthPlanner.plan(settings, config.engine.capabilities)) {
                is EngineStrengthPlanning.Supported -> Unit
                is EngineStrengthPlanning.Unsupported -> {
                    return ArenaSetupValidation.UnsupportedStrength("$side: ${plan.reason}")
                }
            }
        }
        return ArenaSetupValidation.Valid
    }
}

object ArenaSetupResolver {
    fun resolve(
        setup: ArenaSetupConfig,
        randomInt: (Int) -> Int = { kotlin.random.Random.nextInt(it) },
    ): ResolvedArenaSetup {
        when (val validation = ArenaSetupValidator.validate(setup)) {
            ArenaSetupValidation.Valid -> Unit
            is ArenaSetupValidation.Invalid -> error(validation.reason)
            is ArenaSetupValidation.UnsupportedStrength -> error(validation.reason)
        }

        val swap = setup.colorAssignment == ArenaColorAssignment.RANDOM && randomInt(2) == 1
        val whiteConfig = if (swap) setup.black else setup.white
        val blackConfig = if (swap) setup.white else setup.black
        val resolvedOpening = when (setup.opening.mode) {
            ArenaOpeningMode.NORMAL -> {
                val position = if (setup.variant == Variant.STANDARD) {
                    Position.initial()
                } else {
                    Chess960.startingPosition(setup.chess960Index ?: Chess960.STANDARD_POSITION_INDEX)
                }
                ResolvedArenaOpening(
                    mode = ArenaOpeningMode.NORMAL,
                    label = if (setup.variant == Variant.STANDARD) "Normal start" else "Chess960 #${setup.chess960Index ?: Chess960.STANDARD_POSITION_INDEX}",
                    familyId = null,
                    position = position,
                    appliedMoves = emptyList(),
                )
            }
            ArenaOpeningMode.RANDOM_OPENING -> ArenaOpeningCatalog.resolveRandom(
                handoffPlies = setup.opening.handoffPlies,
                randomInt = randomInt,
            )
            ArenaOpeningMode.OPENING_FAMILY -> ArenaOpeningCatalog.resolveFamily(
                familyId = requireNotNull(setup.opening.familyId),
                handoffPlies = setup.opening.handoffPlies,
                randomInt = randomInt,
            )
            ArenaOpeningMode.CUSTOM_FEN -> {
                val position = Fen.parse(setup.opening.customFen, setup.variant)
                ResolvedArenaOpening(
                    mode = ArenaOpeningMode.CUSTOM_FEN,
                    label = "Custom FEN",
                    familyId = null,
                    position = position,
                    appliedMoves = emptyList(),
                )
            }
            ArenaOpeningMode.RANDOM_CHESS960 -> {
                val index = randomInt(Chess960.POSITION_COUNT)
                val position = Chess960.startingPosition(index)
                ResolvedArenaOpening(
                    mode = ArenaOpeningMode.RANDOM_CHESS960,
                    label = "Random Chess960 #$index",
                    familyId = null,
                    position = position,
                    appliedMoves = emptyList(),
                )
            }
        }
        val chess960Index = when {
            resolvedOpening.mode == ArenaOpeningMode.RANDOM_CHESS960 ->
                resolvedOpening.label.substringAfterLast('#').toInt()
            setup.variant == Variant.CHESS960 -> setup.chess960Index ?: Chess960.STANDARD_POSITION_INDEX
            else -> null
        }
        return ResolvedArenaSetup(
            variant = resolvedOpening.position.variant,
            chess960Index = chess960Index,
            white = whiteConfig.resolve(),
            black = blackConfig.resolve(),
            clockConfig = ClockConfig(setup.timeControl.initialMillis, setup.timeControl.incrementMillis),
            opening = resolvedOpening,
            initialPosition = resolvedOpening.position,
            manualControl = setup.manualOpening.toRuntimeControl(),
        )
    }
}

private fun ArenaEngineConfig.resolve(): ResolvedArenaEngine = ResolvedArenaEngine(
    engine = engine,
    strength = toStrengthSettings(),
)

private fun ArenaEngineConfig.toStrengthSettings(): EngineStrengthSettings = EngineStrengthSettings(
    target = strengthTarget,
    model = strengthModel,
    seed = strengthSeed,
)

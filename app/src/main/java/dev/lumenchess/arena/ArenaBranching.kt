package dev.lumenchess.arena

import dev.lumenchess.data.persistence.BranchOrigin
import dev.lumenchess.play.PlayTimeControl

/** Reuse the source's independent engines; only the sandbox receives these editable settings. */
internal fun ResolvedArenaSetup.forBranch(origin: BranchOrigin): ArenaSetupConfig = ArenaSetupConfig(
    variant = variant,
    chess960Index = chess960Index,
    white = ArenaEngineConfig(white.engine, white.strength.model, white.strength.target, white.strength.seed),
    black = ArenaEngineConfig(black.engine, black.strength.model, black.strength.target, black.strength.seed),
    timeControl = if (clockConfig.enabled) PlayTimeControl(clockConfig.initialMillis, clockConfig.incrementMillis) else PlayTimeControl(),
    untimed = !clockConfig.enabled,
    opening = ArenaOpeningSetup(ArenaOpeningMode.CUSTOM_FEN, customFen = origin.fen),
    manualOpening = ArenaManualOpeningSetup(ArenaManualSide.BOTH, ArenaManualLimitMode.UNTIL_RELEASE),
)

package dev.lumenchess.play

import android.content.Context
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget

/**
 * Remembers the last setup a game was started with ("Reckless · Match Your Elo · Rapid 10 min ·
 * Random · Standard"). A one-off custom starting position and the per-game seed are not kept.
 */
internal object PlaySetupMemory {
    private const val PREFERENCES = "lumen_play_setup"

    fun remember(context: Context, setup: PlaySetupConfig) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("variant", setup.variant.name)
            .putInt("chess960Index", setup.chess960Index ?: -1)
            .putString("engine", setup.engine.name)
            .putString("side", setup.side.name)
            .putString("strengthModel", setup.strengthModel.name)
            .putInt("elo", (setup.strengthTarget as? EngineStrengthTarget.Elo)?.value ?: 0)
            .putLong("initialMillis", setup.timeControl.initialMillis)
            .putLong("incrementMillis", setup.timeControl.incrementMillis)
            .putBoolean("rated", setup.rated)
            .putBoolean("matchYourElo", setup.matchYourElo)
            .apply()
    }

    fun recall(context: Context): PlaySetupConfig? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (!preferences.contains("engine")) return null
        return runCatching {
            val variant = Variant.valueOf(preferences.getString("variant", Variant.STANDARD.name)!!)
            val elo = preferences.getInt("elo", 1600)
            PlaySetupConfig(
                variant = variant,
                chess960Index = preferences.getInt("chess960Index", -1).takeIf { it in 0..959 && variant == Variant.CHESS960 }
                    ?: if (variant == Variant.CHESS960) 518 else null,
                engine = PlayEngine.valueOf(preferences.getString("engine", PlayEngine.STOCKFISH_18.name)!!),
                side = PlaySide.valueOf(preferences.getString("side", PlaySide.WHITE.name)!!),
                strengthModel = EngineStrengthModel.valueOf(preferences.getString("strengthModel", EngineStrengthModel.HYBRID.name)!!),
                strengthTarget = if (elo == 0) EngineStrengthTarget.FullStrength else EngineStrengthTarget.Elo(elo),
                timeControl = PlayTimeControl(preferences.getLong("initialMillis", 600_000L), preferences.getLong("incrementMillis", 0L)),
                rated = preferences.getBoolean("rated", false),
                matchYourElo = preferences.getBoolean("matchYourElo", false),
            ).takeIf { PlaySetupValidator.validate(it) is PlaySetupValidation.Valid }
        }.getOrNull()
    }
}

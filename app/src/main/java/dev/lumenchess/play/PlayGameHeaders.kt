package dev.lumenchess.play

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthTarget

/**
 * PGN tags for a Human-vs-Engine game. Without them a saved Play game reads "White vs Black" in the
 * Library, exports a bare movetext, and Insights could not tell which side the player was on.
 * The runtime game tree itself stays header-free; these are applied when a game is saved or exported.
 */
internal object PlayGameHeaders {
    const val HUMAN_NAME = "You"

    fun build(setup: ResolvedPlaySetup): Map<String, String> = buildMap {
        val engineName = setup.engine.displayName
        val humanIsWhite = setup.humanSide == Color.WHITE
        put("Event", "LumenChess Play")
        put("White", if (humanIsWhite) HUMAN_NAME else engineName)
        put("Black", if (humanIsWhite) engineName else HUMAN_NAME)
        (setup.strength.target as? EngineStrengthTarget.Elo)?.let { elo ->
            put(if (humanIsWhite) "BlackElo" else "WhiteElo", elo.value.toString())
        }
        if (setup.clockConfig.enabled) {
            put("TimeControl", "${setup.clockConfig.initialMillis / 1_000L}+${setup.clockConfig.incrementMillis / 1_000L}")
        }
        if (setup.variant == Variant.CHESS960) put("Variant", "Chess960")
    }
}

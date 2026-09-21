package dev.lumenchess.play

import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget

/**
 * Presentation timing for unexpectedly fast engine callbacks.
 *
 * The runtime remains authoritative: this policy only determines how long the Android bridge
 * waits before delivering an already validated search result to the runtime. Native engine timing
 * is left untouched, while the two human-facing modes get a strength-sensitive lower bound so a
 * fast local search still reads as a considered move. The clock continues to run during the
 * wait, so a genuinely slow/weak position can still lose time naturally.
 */
internal object EngineResponseTimingPolicy {
    fun minimumPresentationMillis(settings: EngineStrengthSettings): Long {
        if (settings.model == EngineStrengthModel.ENGINE_NATIVE) return 0L
        val elo = (settings.target as? EngineStrengthTarget.Elo)?.value
        val base = when (settings.model) {
            EngineStrengthModel.HUMANIZED -> 360L
            EngineStrengthModel.HYBRID -> 220L
            EngineStrengthModel.ENGINE_NATIVE -> 0L
        }
        if (elo == null) return base

        // Lower settings should read as more deliberate; high settings stay brisk. This is a
        // monotonic calibration curve rather than one universal artificial delay.
        val normalized = ((elo - EngineStrengthTarget.MIN_ELO).toDouble() /
            (EngineStrengthTarget.MAX_ELO - EngineStrengthTarget.MIN_ELO)).coerceIn(0.0, 1.0)
        val range = when (settings.model) {
            EngineStrengthModel.HUMANIZED -> 620L
            EngineStrengthModel.HYBRID -> 390L
            EngineStrengthModel.ENGINE_NATIVE -> 0L
        }
        return base + ((1.0 - normalized) * range).toLong()
    }

    fun remainingMillis(settings: EngineStrengthSettings, elapsedMillis: Long): Long =
        (minimumPresentationMillis(settings) - elapsedMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
}

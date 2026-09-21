package dev.lumenchess.play

import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineResponseTimingPolicyTest {
    @Test
    fun nativeModeNeverAddsPresentationDelay() {
        val settings = EngineStrengthSettings(
            target = EngineStrengthTarget.Elo(1600),
            model = EngineStrengthModel.ENGINE_NATIVE,
        )

        assertEquals(0L, EngineResponseTimingPolicy.minimumPresentationMillis(settings))
        assertEquals(0L, EngineResponseTimingPolicy.remainingMillis(settings, 1L))
    }

    @Test
    fun lowerHumanizedStrengthGetsMoreThinkingRoom() {
        val low = EngineStrengthSettings(EngineStrengthTarget.Elo(800), EngineStrengthModel.HUMANIZED)
        val high = EngineStrengthSettings(EngineStrengthTarget.Elo(2400), EngineStrengthModel.HUMANIZED)

        assertTrue(
            EngineResponseTimingPolicy.minimumPresentationMillis(low) >
                EngineResponseTimingPolicy.minimumPresentationMillis(high),
        )
    }

    @Test
    fun elapsedSearchTimeReducesOnlyTheRemainingPresentationWait() {
        val settings = EngineStrengthSettings(EngineStrengthTarget.Elo(1600), EngineStrengthModel.HYBRID)
        val minimum = EngineResponseTimingPolicy.minimumPresentationMillis(settings)

        assertEquals(minimum, EngineResponseTimingPolicy.remainingMillis(settings, 0L))
        assertEquals(0L, EngineResponseTimingPolicy.remainingMillis(settings, minimum + 50L))
        assertTrue(
            EngineResponseTimingPolicy.remainingMillis(settings, minimum / 2) < minimum,
        )
    }
}

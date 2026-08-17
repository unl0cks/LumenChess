package dev.lumenchess.play

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaySetupConfigTest {
    @Test
    fun standardStockfishSetupBuildsExpectedInitialPositionAndHumanSide() {
        val setup = PlaySetupConfig(
            variant = Variant.STANDARD,
            engine = PlayEngine.STOCKFISH_18,
            side = PlaySide.WHITE,
            strengthModel = EngineStrengthModel.HYBRID,
            strengthTarget = EngineStrengthTarget.Elo(1600),
            timeControl = PlayTimeControl(600_000L, 0L),
        )

        val resolved = PlaySetupResolver.resolve(setup) { Color.BLACK }

        assertEquals(Color.WHITE, resolved.humanSide)
        assertEquals(Variant.STANDARD, resolved.initialPosition.variant)
        assertEquals(600_000L, resolved.clockConfig.initialMillis)
        assertEquals(0L, resolved.clockConfig.incrementMillis)
        assertEquals("stockfish-18", resolved.engine.id)
        assertIs<PlaySetupValidation.Valid>(PlaySetupValidator.validate(setup))
    }

    @Test
    fun chess960SetupUsesCommittedIndexAndPreservesVariant() {
        val setup = PlaySetupConfig(
            variant = Variant.CHESS960,
            chess960Index = 123,
            engine = PlayEngine.STOCKFISH_18,
            side = PlaySide.BLACK,
            strengthModel = EngineStrengthModel.HUMANIZED,
            strengthTarget = EngineStrengthTarget.Elo(1200),
            timeControl = PlayTimeControl(180_000L, 2_000L),
        )

        val resolved = PlaySetupResolver.resolve(setup) { Color.WHITE }

        assertEquals(Color.BLACK, resolved.humanSide)
        assertEquals(Variant.CHESS960, resolved.initialPosition.variant)
        assertEquals(123, resolved.chess960Index)
        assertEquals(2_000L, resolved.clockConfig.incrementMillis)
    }

    @Test
    fun randomSideUsesInjectedDeterministicChooser() {
        val setup = PlaySetupConfig(
            engine = PlayEngine.STOCKFISH_18,
            side = PlaySide.RANDOM,
        )

        assertEquals(Color.BLACK, PlaySetupResolver.resolve(setup) { Color.BLACK }.humanSide)
        assertEquals(Color.WHITE, PlaySetupResolver.resolve(setup) { Color.WHITE }.humanSide)
    }

    @Test
    fun recklessEngineNativeEloIsTypedUnsupportedInsteadOfRawUciFallback() {
        val setup = PlaySetupConfig(
            engine = PlayEngine.RECKLESS_0_9_0,
            strengthModel = EngineStrengthModel.ENGINE_NATIVE,
            strengthTarget = EngineStrengthTarget.Elo(1500),
        )

        val validation = PlaySetupValidator.validate(setup)

        val unsupported = assertIs<PlaySetupValidation.UnsupportedStrength>(validation)
        assertTrue(unsupported.reason.isNotBlank())
    }

    @Test
    fun recklessHumanizedEloIsSupportedWithoutNativeLimiting() {
        val setup = PlaySetupConfig(
            engine = PlayEngine.RECKLESS_0_9_0,
            strengthModel = EngineStrengthModel.HUMANIZED,
            strengthTarget = EngineStrengthTarget.Elo(1500),
        )

        assertIs<PlaySetupValidation.Valid>(PlaySetupValidator.validate(setup))
    }

    @Test
    fun fullStrengthIsValidForBothPinnedEngines() {
        for (engine in PlayEngine.entries) {
            val setup = PlaySetupConfig(
                engine = engine,
                strengthModel = EngineStrengthModel.HYBRID,
                strengthTarget = EngineStrengthTarget.FullStrength,
            )
            assertIs<PlaySetupValidation.Valid>(PlaySetupValidator.validate(setup))
        }
    }

    @Test
    fun invalidChess960IndexAndClockAreRejectedBeforeRuntimeCreation() {
        assertIs<PlaySetupValidation.Invalid>(
            PlaySetupValidator.validate(
                PlaySetupConfig(variant = Variant.CHESS960, chess960Index = 960),
            ),
        )
        assertIs<PlaySetupValidation.Invalid>(
            PlaySetupValidator.validate(
                PlaySetupConfig(timeControl = PlayTimeControl(initialMillis = 0L, incrementMillis = 0L)),
            ),
        )
    }
}

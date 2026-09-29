package dev.lumenchess.play

import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PlayGameHeadersTest {
    @Test
    fun humanIsNamedAndEngineCarriesItsStrengthOnTheRightSide() {
        val asWhite = PlayGameHeaders.build(
            PlaySetupResolver.resolve(
                PlaySetupConfig(side = PlaySide.WHITE, strengthTarget = EngineStrengthTarget.Elo(1600), timeControl = PlayTimeControl(180_000L, 2_000L)),
            ),
        )
        assertEquals("You", asWhite["White"])
        assertEquals("Stockfish 18", asWhite["Black"])
        assertEquals("1600", asWhite["BlackElo"])
        assertFalse("WhiteElo" in asWhite)
        assertEquals("180+2", asWhite["TimeControl"])

        val asBlack = PlayGameHeaders.build(
            PlaySetupResolver.resolve(
                PlaySetupConfig(side = PlaySide.BLACK, engine = PlayEngine.RECKLESS_0_9_0, strengthTarget = EngineStrengthTarget.Elo(900)),
            ),
        )
        assertEquals("Reckless 0.9.0", asBlack["White"])
        assertEquals("You", asBlack["Black"])
        assertEquals("900", asBlack["WhiteElo"])
    }

    @Test
    fun fullStrengthHasNoEloTagAndChess960IsMarked() {
        val headers = PlayGameHeaders.build(
            PlaySetupResolver.resolve(
                PlaySetupConfig(
                    variant = Variant.CHESS960,
                    chess960Index = 100,
                    strengthTarget = EngineStrengthTarget.FullStrength,
                ),
            ),
        )
        assertFalse("WhiteElo" in headers)
        assertFalse("BlackElo" in headers)
        assertEquals("Chess960", headers["Variant"])
    }
}

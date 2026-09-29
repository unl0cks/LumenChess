package dev.lumenchess.play

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.RuntimeTerminal
import dev.lumenchess.runtime.clock.ClockConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class LiveGameSummaryTest {
    @Test
    fun resultsAreWordedFromTheHumansPointOfView() {
        assertEquals(
            LiveResultSummary("You won", "Checkmate"),
            liveResultSummary(RuntimeTerminal.Checkmate(Color.WHITE), humanSide = Color.WHITE),
        )
        assertEquals(
            LiveResultSummary("You lost", "Checkmate"),
            liveResultSummary(RuntimeTerminal.Checkmate(Color.WHITE), humanSide = Color.BLACK),
        )
        assertEquals(
            LiveResultSummary("You won", "Black ran out of time"),
            liveResultSummary(RuntimeTerminal.Timeout(Color.BLACK), humanSide = Color.WHITE),
        )
        assertEquals(
            LiveResultSummary("You lost", "White ran out of time"),
            liveResultSummary(RuntimeTerminal.Timeout(Color.WHITE), humanSide = Color.WHITE),
        )
        assertEquals(
            LiveResultSummary("You lost", "White resigned"),
            liveResultSummary(RuntimeTerminal.Resignation(Color.WHITE), humanSide = Color.WHITE),
        )
    }

    @Test
    fun everyDrawKindReadsAsADrawWithItsReason() {
        mapOf(
            RuntimeTerminal.DrawAgreement to "Draw agreed",
            RuntimeTerminal.Stalemate to "Stalemate",
            RuntimeTerminal.InsufficientMaterial to "Insufficient material",
            RuntimeTerminal.ThreefoldRepetition to "Threefold repetition",
            RuntimeTerminal.FiftyMoveRule to "Fifty-move rule",
        ).forEach { (terminal, detail) ->
            assertEquals(LiveResultSummary("Draw", detail), liveResultSummary(terminal, Color.BLACK))
        }
    }

    @Test
    fun timeControlsUseStandardNotation() {
        assertEquals("10+0", formatLiveTimeControl(ClockConfig(600_000L, 0L)))
        assertEquals("3+2", formatLiveTimeControl(ClockConfig(180_000L, 2_000L)))
        assertEquals("30s+1", formatLiveTimeControl(ClockConfig(30_000L, 1_000L)))
        assertEquals("Untimed", formatLiveTimeControl(ClockConfig(600_000L, 0L, enabled = false)))
    }

    @Test
    fun gameSummaryNamesOpponentStrengthTimeVariantAndSide() {
        val setup = PlaySetupResolver.resolve(
            PlaySetupConfig(
                variant = Variant.CHESS960,
                chess960Index = 321,
                engine = PlayEngine.RECKLESS_0_9_0,
                side = PlaySide.BLACK,
                strengthModel = EngineStrengthModel.HUMANIZED,
                strengthTarget = EngineStrengthTarget.Elo(1200),
                timeControl = PlayTimeControl(180_000L, 2_000L),
            ),
        )

        assertEquals(
            listOf(
                "Opponent" to "Reckless 0.9.0",
                "Strength" to "1200 Elo · Humanized",
                "Time" to "3+2",
                "Variant" to "Chess960 #321",
                "You play" to "Black",
            ),
            liveGameSummary(setup),
        )
    }

    @Test
    fun fullStrengthDropsTheModelSuffix() {
        val setup = PlaySetupResolver.resolve(PlaySetupConfig(strengthTarget = EngineStrengthTarget.FullStrength))
        assertEquals("Full strength", liveStrengthLabel(setup))
    }
}

package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EngineDrawPolicyTest {
    private fun stateFor(fen: String): RuntimeState = GameRuntime.create(
        initialPosition = Fen.parse(fen),
        clockConfig = ClockConfig(600_000L, 0L),
        timeSource = MonotonicTimeSource { 1_000L },
        controllers = RuntimeControllers(RuntimeController.HUMAN, RuntimeController.ENGINE),
    ).state

    private val level = "4k3/pp3ppp/8/8/8/8/PP3PPP/4K3 w - - 0 30"
    private val engineDown = "4k3/pp3ppp/8/8/8/8/PP3PPP/3QK3 w - - 0 30" // white queen up, engine is black
    private val engineUp = "3qk3/pp3ppp/8/8/8/8/PP3PPP/4K3 w - - 0 30"

    private fun elo(value: Int, seed: Long = 7L) =
        EngineStrengthSettings(EngineStrengthTarget.Elo(value), EngineStrengthModel.HYBRID, seed)

    @Test
    fun nobodyAgreesADrawInTheOpening() {
        val response = EngineDrawPolicy.respond(stateFor(level), Color.BLACK, elo(1200), plies = 12)
        assertIs<DrawOfferResponse.Declined>(response)
    }

    @Test
    fun anEngineThatIsClearlyWorseAccepts() {
        val response = EngineDrawPolicy.respond(stateFor(engineDown), Color.BLACK, elo(2400), plies = 60)
        assertEquals(DrawOfferResponse.Accepted, response)
    }

    @Test
    fun anEngineThatIsClearlyBetterDeclines() {
        val response = EngineDrawPolicy.respond(stateFor(engineUp), Color.BLACK, elo(400), plies = 60)
        assertIs<DrawOfferResponse.Declined>(response)
    }

    @Test
    fun levelPositionsAreDecidedDeterministicallyPerGameAndPly() {
        val state = stateFor(level)
        repeat(20) { seed ->
            val a = EngineDrawPolicy.respond(state, Color.BLACK, elo(1600, seed.toLong()), plies = 44)
            val b = EngineDrawPolicy.respond(state, Color.BLACK, elo(1600, seed.toLong()), plies = 44)
            assertEquals(a, b)
        }
    }

    @Test
    fun weakerOpponentsSettleForLevelDrawsMoreOftenThanStrongOnes() {
        val state = stateFor(level)
        fun acceptRate(rating: Int) = (1..600).count { seed ->
            EngineDrawPolicy.respond(state, Color.BLACK, elo(rating, seed.toLong()), plies = 50) ==
                DrawOfferResponse.Accepted
        }
        val weak = acceptRate(500)
        val strong = acceptRate(2800)
        assertTrue(weak > strong, "weak=$weak strong=$strong")
        assertTrue(weak in 250..550 && strong in 100..400, "rates should be sane: weak=$weak strong=$strong")
    }

    @Test
    fun materialBalanceCountsBothSidesFromTheAskersPerspective() {
        assertEquals(9.0, EngineDrawPolicy.materialBalance(stateFor(engineDown), Color.WHITE))
        assertEquals(-9.0, EngineDrawPolicy.materialBalance(stateFor(engineDown), Color.BLACK))
        assertEquals(0.0, EngineDrawPolicy.materialBalance(stateFor(level), Color.WHITE))
    }

    @Test
    fun cooldownBlocksBackToBackOffers() {
        assertFalse(EngineDrawPolicy.inCooldown(null, 40))
        assertTrue(EngineDrawPolicy.inCooldown(36, 40))
        assertFalse(EngineDrawPolicy.inCooldown(20, 40))
    }
}

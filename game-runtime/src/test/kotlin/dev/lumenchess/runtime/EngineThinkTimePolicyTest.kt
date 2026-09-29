package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EngineThinkTimePolicyTest {
    private val opening = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
    private val middlegame = "r2q1rk1/pp2bppp/2n1bn2/2pp4/3P4/2N1PN2/PPQ1BPPP/R1B2RK1 w - - 0 15"
    private val singleReply = "7k/8/6K1/8/8/8/8/R7 b - - 0 40"

    private fun stateFor(fen: String, remaining: Long, increment: Long = 0L, timed: Boolean = true): RuntimeState {
        val position = Fen.parse(fen)
        return GameRuntime.create(
            initialPosition = position,
            clockConfig = ClockConfig(remaining, increment, enabled = timed),
            timeSource = MonotonicTimeSource { 1_000L },
            controllers = RuntimeControllers(RuntimeController.ENGINE, RuntimeController.ENGINE),
        ).state
    }

    private fun elo(value: Int, model: EngineStrengthModel = EngineStrengthModel.HYBRID, seed: Long = 5L) =
        EngineStrengthSettings(EngineStrengthTarget.Elo(value), model, seed)

    private fun plans(
        fen: String,
        strength: (Long) -> EngineStrengthSettings,
        remaining: Long,
        initial: Long = remaining,
        increment: Long = 0L,
        samples: Int = 400,
    ): List<EngineThinkPlan> {
        val state = stateFor(fen, remaining, increment)
        return (1..samples).map { i ->
            EngineThinkTimePolicy.plan(state, strength(i.toLong()), initial, EngineSearchId(i.toLong()))
        }
    }

    private fun List<Long>.median(): Long = sorted()[size / 2]

    private fun List<Long>.coefficientOfVariation(): Double {
        val mean = average()
        val variance = sumOf { (it - mean) * (it - mean) } / size
        return sqrt(variance) / mean
    }

    @Test
    fun planIsDeterministicForTheSameSettingsAndSearch() {
        val state = stateFor(middlegame, 600_000L)
        val settings = elo(1600, seed = 42L)
        val a = EngineThinkTimePolicy.plan(state, settings, 600_000L, EngineSearchId(9))
        val b = EngineThinkTimePolicy.plan(state, settings, 600_000L, EngineSearchId(9))
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsAndSearchesProduceDifferentThinkTimes() {
        val state = stateFor(middlegame, 600_000L)
        val values = (1..40).map { i ->
            EngineThinkTimePolicy.plan(state, elo(1600, seed = i.toLong()), 600_000L, EngineSearchId(3)).minimumTotalMillis
        }.toSet()
        assertTrue(values.size > 10, "seed must vary the think time, got $values")
        val bySearch = (1..40).map { i ->
            EngineThinkTimePolicy.plan(state, elo(1600), 600_000L, EngineSearchId(i.toLong())).minimumTotalMillis
        }.toSet()
        assertTrue(bySearch.size > 10, "search identity must vary the think time")
    }

    @Test
    fun onlyReplyIsFastAndOpeningIsFasterThanMiddlegame() {
        assertEquals(1, MoveGenerator.legalMoves(Fen.parse(singleReply)).size)
        val forced = plans(singleReply, { elo(1600, seed = it) }, 600_000L).map { it.minimumTotalMillis }
        val opening = plans(opening, { elo(1600, seed = it) }, 600_000L).map { it.minimumTotalMillis }
        val middle = plans(middlegame, { elo(1600, seed = it) }, 600_000L).map { it.minimumTotalMillis }
        println("median think ms @1600 10+0 forced=${forced.median()} opening=${opening.median()} middlegame=${middle.median()}")
        assertTrue(forced.median() < 1_000L, "forced reply should be near-instant, was ${forced.median()}")
        assertTrue(opening.median() < middle.median(), "opening ${opening.median()} vs middlegame ${middle.median()}")
        assertTrue(middle.median() in 2_000L..12_000L, "rapid middlegame think should be seconds, was ${middle.median()}")
    }

    @Test
    fun thinkingScalesWithTimeControlAndIncrement() {
        val bullet = plans(middlegame, { elo(1600, seed = it) }, 60_000L).map { it.minimumTotalMillis }
        val rapid = plans(middlegame, { elo(1600, seed = it) }, 600_000L).map { it.minimumTotalMillis }
        val withIncrement = plans(middlegame, { elo(1600, seed = it) }, 60_000L, increment = 10_000L)
            .map { it.minimumTotalMillis }
        println("median think ms @1600 1+0=${bullet.median()} 10+0=${rapid.median()} 1+10=${withIncrement.median()}")
        assertTrue(bullet.median() < rapid.median())
        assertTrue(withIncrement.median() > bullet.median())
    }

    @Test
    fun thinkShrinksAsTheClockRunsDown() {
        val plenty = plans(middlegame, { elo(1600, seed = it) }, 300_000L, initial = 300_000L)
            .map { it.minimumTotalMillis }
        val low = plans(middlegame, { elo(1600, seed = it) }, 15_000L, initial = 300_000L)
            .filterNot { it.stalled }.map { it.minimumTotalMillis }
        assertTrue(low.median() < plenty.median() / 4, "low-clock ${low.median()} vs ${plenty.median()}")
    }

    @Test
    fun weakerOpponentsVaryMoreThanStrongOnes() {
        // 3+0 keeps both distributions clear of the comfortable-think cap so variance is comparable.
        val weak = plans(middlegame, { elo(500, seed = it) }, 180_000L, samples = 600).map { it.minimumTotalMillis }
        val strong = plans(middlegame, { elo(2600, seed = it) }, 180_000L, samples = 600).map { it.minimumTotalMillis }
        assertTrue(weak.coefficientOfVariation() > strong.coefficientOfVariation())
    }

    @Test
    fun comfortablePlansNeverPlanPastTheRemainingClock() {
        listOf(400, 900, 1600, 2400, 3000).forEach { rating ->
            listOf(5_000L, 30_000L, 180_000L, 900_000L).forEach { remaining ->
                plans(middlegame, { elo(rating, seed = it) }, remaining, initial = 900_000L, samples = 120)
                    .filterNot { it.stalled }
                    .forEach { plan ->
                        assertTrue(
                            plan.minimumTotalMillis <= remaining.coerceAtLeast(400L),
                            "elo=$rating remaining=$remaining planned ${plan.minimumTotalMillis}",
                        )
                        assertTrue(plan.searchMillis in 1L..(remaining - 10L).coerceAtLeast(1L))
                    }
            }
        }
    }

    @Test
    fun weakOpponentsCanFreezeInTimeTroubleAndLoseOnTime() {
        val trouble = plans(middlegame, { elo(500, seed = it) }, 6_000L, initial = 60_000L, samples = 800)
        val flagging = trouble.filter { it.stalled && it.minimumTotalMillis >= 6_000L }
        println("weak time-trouble stalls=${trouble.count { it.stalled }} of ${trouble.size}, flagging=${flagging.size}")
        assertTrue(flagging.isNotEmpty(), "a 500 Elo opponent must be able to flag")
        assertTrue(trouble.count { it.stalled } < trouble.size / 2, "freezing must stay the exception")
    }

    @Test
    fun strongOpponentsAndFullStrengthNeverFreeze() {
        val strong = plans(middlegame, { elo(2700, seed = it) }, 6_000L, initial = 60_000L, samples = 800)
        val full = plans(
            middlegame,
            { EngineStrengthSettings.fullStrength(it) },
            6_000L,
            initial = 60_000L,
            samples = 800,
        )
        assertFalse(strong.any { it.stalled })
        assertFalse(full.any { it.stalled })
        assertTrue(full.all { it.minimumTotalMillis < 6_000L })
    }

    @Test
    fun timeTroubleFreezingDoesNotApplyWhileTheClockIsHealthy() {
        val healthy = plans(middlegame, { elo(500, seed = it) }, 50_000L, initial = 60_000L, samples = 400)
        assertFalse(healthy.any { it.stalled })
    }

    @Test
    fun fullStrengthReallySearchesAndIsNotPadded() {
        val full = plans(middlegame, { EngineStrengthSettings.fullStrength(it) }, 600_000L)
        assertTrue(full.all { it.searchMillis <= 3_000L })
        assertTrue(full.all { it.minimumTotalMillis <= it.searchMillis })
        assertTrue(full.median { it.searchMillis } >= 1_000L)
    }

    @Test
    fun depthLimitedModelsAreHeldForTheWholeThinkButNativeSearchesForReal() {
        val hybrid = plans(middlegame, { elo(1200, EngineStrengthModel.HYBRID, it) }, 600_000L)
        val humanized = plans(middlegame, { elo(1200, EngineStrengthModel.HUMANIZED, it) }, 600_000L)
        val native = plans(middlegame, { elo(1200, EngineStrengthModel.ENGINE_NATIVE, it) }, 600_000L)
        (hybrid + humanized).forEach { assertTrue(it.minimumTotalMillis >= 380L) }
        // Native Stockfish consumes its whole movetime, so the plan asks it for the real think time.
        native.filter { it.minimumTotalMillis <= 3_000L }.forEach {
            assertEquals(it.minimumTotalMillis.coerceAtLeast(60L), it.searchMillis)
        }
        native.forEach { assertTrue(it.searchMillis <= 3_000L) }
    }

    @Test
    fun untimedGamesStillGetBoundedHumanScaleThinking() {
        val state = stateFor(middlegame, 1L, timed = false)
        val values = (1..200).map {
            EngineThinkTimePolicy.plan(state, elo(1600, seed = it.toLong()), 0L, EngineSearchId(it.toLong()))
        }
        assertTrue(values.all { it.minimumTotalMillis in 380L..12_000L })
        assertTrue(values.none { it.stalled })
    }

    @Test
    fun presentationDelayCountsRealSearchTimeAlreadySpent() {
        val plan = EngineThinkPlan(searchMillis = 1_000L, minimumTotalMillis = 2_500L)
        assertEquals(2_500L, plan.remainingPresentationMillis(0L))
        assertEquals(1_000L, plan.remainingPresentationMillis(1_500L))
        assertEquals(0L, plan.remainingPresentationMillis(9_000L))
        assertEquals(2_500L, plan.remainingPresentationMillis(-50L))
    }

    @Test
    fun catalogueOfThinkTimesForEyeballing() {
        val header = "elo/model      1+0      3+0      10+0     15+10"
        println(header)
        listOf(
            "400 hybrid" to elo(400),
            "1000 hybrid" to elo(1000),
            "1600 hybrid" to elo(1600),
            "2200 hybrid" to elo(2200),
            "2800 hybrid" to elo(2800),
            "1600 native" to elo(1600, EngineStrengthModel.ENGINE_NATIVE),
        ).forEach { (label, settings) ->
            val cells = listOf(60_000L to 0L, 180_000L to 0L, 600_000L to 0L, 900_000L to 10_000L).map { (t, inc) ->
                val m = plans(middlegame, { settings.copy(seed = it) }, t, increment = inc).filterNot { it.stalled }
                    .map { it.minimumTotalMillis }.median()
                "%.1fs".format(m / 1000.0)
            }
            println("%-14s %-8s %-8s %-8s %-8s".format(label, cells[0], cells[1], cells[2], cells[3]))
        }
        assertNotEquals(0, Position.initial().board.size)
    }

    private fun List<EngineThinkPlan>.median(selector: (EngineThinkPlan) -> Long): Long =
        map(selector).median()
}

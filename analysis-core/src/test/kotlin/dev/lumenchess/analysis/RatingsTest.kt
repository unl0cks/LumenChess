package dev.lumenchess.analysis

import dev.lumenchess.analysis.rating.FideElo
import dev.lumenchess.analysis.rating.Glicko1
import dev.lumenchess.analysis.rating.Glicko2
import dev.lumenchess.analysis.rating.PerformanceEstimate
import dev.lumenchess.analysis.rating.RatedResult
import dev.lumenchess.analysis.rating.RatingState
import dev.lumenchess.analysis.rating.RatingSystem
import dev.lumenchess.analysis.rating.RatingSystems
import dev.lumenchess.analysis.rating.TimeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RatingsTest {
    /** Glickman's worked example: 1500 (RD 200) beats 1400/30, loses to 1550/100 and 1700/300. */
    private val example = listOf(
        RatedResult(1_400.0, 1.0, opponentDeviation = 30.0),
        RatedResult(1_550.0, 0.0, opponentDeviation = 100.0),
        RatedResult(1_700.0, 0.0, opponentDeviation = 300.0),
    )

    @Test fun glickoMatchesGlickmansPaper() {
        val after = Glicko1.period(RatingState(1_500.0, 200.0), example)
        assertEquals(1_464.0, after.rating, 0.5)
        assertEquals(151.4, after.deviation!!, 0.1)
    }

    @Test fun glicko2MatchesGlickmansPaper() {
        val after = Glicko2.period(RatingState(1_500.0, 200.0, 0.06), example, tau = 0.5)
        assertEquals(1_464.06, after.rating, 0.01)
        assertEquals(151.52, after.deviation!!, 0.01)
        assertEquals(0.05999, after.volatility!!, 0.00001)
    }

    @Test fun ratingsMoveTheRightWayAndDeviationShrinksWithGames() {
        for (system in RatingSystem.entries) {
            val start = RatingSystems.initial(system)
            val win = RatingSystems.update(system, start, RatedResult(1_500.0, 1.0))
            val loss = RatingSystems.update(system, start, RatedResult(1_500.0, 0.0))
            assertTrue(win.rating > start.rating && loss.rating < start.rating, "$system")
            assertEquals(1, win.games)
        }
        val g2 = RatingSystems.replay(RatingSystem.GLICKO_2, List(20) { RatedResult(1_500.0, 0.5) })
        assertTrue(g2.deviation!! < 150.0, "RD after 20 games: ${g2.deviation}")
        assertEquals(1_500.0, g2.rating, 1.0)
    }

    @Test fun inactivityWidensTheDeviation() {
        val settled = RatingState(1_600.0, 60.0, 0.06, games = 50)
        val soon = Glicko2.update(settled, RatedResult(1_600.0, 0.5, daysSincePrevious = 0.0))
        val later = Glicko2.update(settled, RatedResult(1_600.0, 0.5, daysSincePrevious = 180.0))
        assertTrue(later.deviation!! > soon.deviation!!)
        val g1Later = Glicko1.update(RatingState(1_600.0, 60.0), RatedResult(1_600.0, 0.5, daysSincePrevious = 365.0))
        assertTrue(g1Later.deviation!! > 60.0)
    }

    @Test fun fideUsesTheFourHundredCapKFactorsAndFloors() {
        assertEquals(1.0 / 11.0, FideElo.expected(2_000.0, 2_600.0), 1e-9)
        val newcomer = FideElo.update(RatingState(1_500.0), RatedResult(1_500.0, 1.0), strict = false)
        assertEquals(1_520.0, newcomer.rating, 1e-9)
        val veteran = RatingState(2_000.0, deviation = 2_000.0, games = 60)
        assertEquals(20.0, FideElo.kFactor(veteran))
        assertEquals(10.0, FideElo.kFactor(veteran.copy(deviation = 2_410.0)))
        val beginner = RatingState(1_410.0, games = 40)
        assertEquals(1_400.0, FideElo.update(beginner, RatedResult(1_400.0, 0.0), strict = true).rating)
        assertTrue(FideElo.update(beginner, RatedResult(1_400.0, 0.0), strict = false).rating < 1_400.0)
    }

    @Test fun performanceEstimateWeighsRecentGamesMost() {
        val estimate = PerformanceEstimate.of(listOf(1_800, 1_200, 1_200, 1_200))!!
        assertTrue(estimate.rating > 1_350, "${estimate.rating}")
        assertTrue(estimate.provisional)
        assertEquals(12, PerformanceEstimate.of(List(20) { 1_500 })!!.games)
    }

    @Test fun timeClassesFollowBasePlusFortyIncrements() {
        assertEquals(TimeClass.BULLET, TimeClass.of(60_000, 1_000))
        assertEquals(TimeClass.BLITZ, TimeClass.of(180_000, 2_000))
        assertEquals(TimeClass.BLITZ, TimeClass.of(300_000, 0))
        assertEquals(TimeClass.RAPID, TimeClass.of(600_000, 0))
        assertEquals(TimeClass.RAPID, TimeClass.of(null, null))
    }
}

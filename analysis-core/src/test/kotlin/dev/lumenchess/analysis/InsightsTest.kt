package dev.lumenchess.analysis

import dev.lumenchess.analysis.insights.GameKind
import dev.lumenchess.analysis.insights.GameOrigin
import dev.lumenchess.analysis.insights.Insights
import dev.lumenchess.analysis.insights.InsightsFilter
import dev.lumenchess.analysis.insights.MatchYourElo
import dev.lumenchess.analysis.insights.Outcome
import dev.lumenchess.analysis.insights.PlayerGame
import dev.lumenchess.analysis.insights.PoolKey
import dev.lumenchess.analysis.insights.RatingReplay
import dev.lumenchess.analysis.insights.ReviewStats
import dev.lumenchess.analysis.rating.RatingSystem
import dev.lumenchess.analysis.rating.TimeClass
import dev.lumenchess.analysis.review.GamePhase
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsTest {
    private val day = InsightsFilter.DAY_MILLIS
    private val now = 1_000 * day

    private fun game(
        id: Int,
        outcome: Outcome,
        daysAgo: Int = id,
        kind: GameKind = GameKind.ENGINE,
        timeClass: TimeClass = TimeClass.BLITZ,
        rated: Boolean = true,
        opponent: Int? = 1_500,
        color: Color = if (id % 2 == 0) Color.WHITE else Color.BLACK,
        opening: String? = "Sicilian Defense",
        accuracy: Double? = null,
        blunders: Int = 0,
        gameRating: Int? = null,
    ) = PlayerGame(
        gameId = "g$id", kind = kind, origin = GameOrigin.LOCAL, userColor = color, outcome = outcome,
        variant = Variant.STANDARD, timeClass = timeClass, rated = rated, playedAtEpochMillis = now - daysAgo * day,
        opponentRating = opponent, openingFamily = opening, openingEco = "B20", openingName = opening,
        review = accuracy?.let {
            ReviewStats(it, gameRating, mapOf(MoveClassification.BLUNDER to blunders), mapOf(GamePhase.OPENING to it + 5), null)
        },
    )

    @Test fun filtersAndOverviewCountOnlyTheSelectedGames() {
        val games = listOf(
            game(1, Outcome.WIN, accuracy = 80.0, gameRating = 1_600),
            game(2, Outcome.LOSS, accuracy = 60.0, gameRating = 1_000),
            game(3, Outcome.DRAW, timeClass = TimeClass.RAPID),
            game(40, Outcome.WIN, daysAgo = 40),
        )
        val blitz30 = Insights.filter(games, InsightsFilter(timeClass = TimeClass.BLITZ, days = 30), now)
        assertEquals(listOf("g1", "g2"), blitz30.map { it.gameId })
        val overview = Insights.overview(blitz30)
        assertEquals(1, overview.record.wins)
        assertEquals(1, overview.record.losses)
        assertEquals(70.0, overview.averageAccuracy!!, 1e-9)
        assertEquals(1_300, overview.averageGameRating)
        assertEquals(50, overview.record.scorePercent)
        assertEquals(2, Insights.moveQuality(blitz30).reviewedGames)
        assertEquals(75.0, Insights.phases(blitz30).getValue(GamePhase.OPENING)!!, 1e-9)
    }

    @Test fun openingsRankByGamesAndBestWorstNeedEnoughGames() {
        val games = (1..4).map { game(it, Outcome.WIN, color = Color.WHITE, opening = "Italian Game") } +
            (5..8).map { game(it, Outcome.LOSS, color = Color.WHITE, opening = "French Defense") } +
            listOf(game(9, Outcome.DRAW, color = Color.BLACK, opening = "Italian Game"))
        val openings = Insights.openings(games)
        assertEquals("French Defense", openings.first().name) // ties broken by name, both 4 games
        val (best, worst) = Insights.bestAndWorst(openings)
        assertEquals("Italian Game", best!!.name)
        assertEquals("French Defense", worst!!.name)
        assertEquals(Color.BLACK, openings.single { it.record.games == 1 }.color)
    }

    @Test fun trendComparesTheNewestWindowWithTheOneBefore() {
        val improving = (0 until 20).map { i ->
            game(i, if (i < 10) Outcome.WIN else Outcome.LOSS, accuracy = if (i < 10) 85.0 else 70.0, blunders = if (i < 10) 0 else 2, gameRating = if (i < 10) 1_700 else 1_300)
        }
        val trend = Insights.trend(improving.sortedByDescending { it.playedAtEpochMillis })
        assertEquals(15.0, trend.accuracyChange!!, 1e-9)
        assertEquals(400, trend.gameRatingChange)
        assertEquals(-2.0, trend.errorsPerGameChange!!, 1e-9)
        assertEquals(100, trend.scoreChange)
        assertNull(Insights.trend(improving.take(5)).accuracyChange)
        assertEquals(20, Insights.strengthSeries(improving).size)
    }
}

class RatingReplayTest {
    private val day = InsightsFilter.DAY_MILLIS

    private fun rated(id: Int, outcome: Outcome, opponent: Int, timeClass: TimeClass = TimeClass.BLITZ, rated: Boolean = true) = PlayerGame(
        gameId = "r$id", kind = GameKind.ENGINE, origin = GameOrigin.LOCAL, userColor = Color.WHITE, outcome = outcome,
        variant = Variant.STANDARD, timeClass = timeClass, rated = rated, playedAtEpochMillis = id * day, opponentRating = opponent,
    )

    @Test fun onlyRatedLocalEngineGamesMoveTheRatingAndPoolsStaySeparate() {
        val games = listOf(
            rated(1, Outcome.WIN, 1_500),
            rated(2, Outcome.WIN, 1_600),
            rated(3, Outcome.LOSS, 1_400, timeClass = TimeClass.RAPID),
            rated(4, Outcome.WIN, 2_000, rated = false),
        )
        val pools = RatingReplay.replay(games, RatingSystem.GLICKO_2)
        val blitz = pools.getValue(PoolKey(Variant.STANDARD, TimeClass.BLITZ))
        assertEquals(listOf("r1", "r2"), blitz.map { it.gameId })
        assertTrue(blitz.last().state.rating > blitz.first().state.rating)
        val rapid = pools.getValue(PoolKey(Variant.STANDARD, TimeClass.RAPID))
        assertTrue(rapid.single().state.rating < 1_500.0)
        for (system in RatingSystem.entries) assertEquals(2, RatingReplay.replay(games, system).getValue(PoolKey(Variant.STANDARD, TimeClass.BLITZ)).size)
    }

    @Test fun performanceEstimateUsesReviewedGamesPerPool() {
        val reviewed = (1..6).map { i ->
            rated(i, Outcome.DRAW, 1_500).copy(review = ReviewStats(80.0, 1_500 + i * 10, emptyMap(), emptyMap(), null))
        }
        val estimate = RatingReplay.performance(reviewed).getValue(PoolKey(Variant.STANDARD, TimeClass.BLITZ))
        assertNotNull(estimate)
        assertEquals(6, estimate.games)
        assertTrue(estimate.rating in 1_520..1_560, "${estimate.rating}")
    }

    @Test fun matchYourEloStaysInRangeAndSnapsToFifty() {
        val random = Random(7)
        val targets = List(200) { MatchYourElo.target(1_475, 100, random) }
        assertTrue(targets.all { it in 1_350..1_600 && it % 50 == 0 }, "$targets")
        assertTrue(targets.distinct().size > 2, "targets vary")
        assertEquals(400, MatchYourElo.target(250, 0))
        assertEquals(3_000, MatchYourElo.target(3_300, 50, random))
    }
}

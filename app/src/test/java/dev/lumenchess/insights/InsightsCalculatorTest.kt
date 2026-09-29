package dev.lumenchess.insights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsCalculatorTest {
    private fun game(
        outcome: InsightOutcome,
        color: InsightColor = InsightColor.WHITE,
        ending: InsightEnding = InsightEnding.CHECKMATE,
        base: Long? = 600_000L,
        increment: Long? = 0L,
        elo: Int? = 1600,
        chess960: Boolean = false,
        at: Long = 0L,
    ) = InsightGame(color, outcome, ending, base, increment, elo, chess960, at)

    @Test
    fun noGamesIsAnHonestEmptySummary() {
        val summary = InsightsCalculator.summarize(emptyList())

        assertTrue(summary.isEmpty)
        assertNull(summary.overall.scorePercent)
        assertNull(summary.streak)
        assertTrue(summary.byTimeControl.isEmpty() && summary.byStrength.isEmpty() && summary.recentForm.isEmpty())
    }

    @Test
    fun scoreCountsADrawAsHalfAPoint() {
        val summary = InsightsCalculator.summarize(
            listOf(
                game(InsightOutcome.WIN, at = 1), game(InsightOutcome.WIN, at = 2),
                game(InsightOutcome.DRAW, at = 3), game(InsightOutcome.LOSS, at = 4),
            ),
        )

        assertEquals(InsightRecord(wins = 2, draws = 1, losses = 1), summary.overall)
        assertEquals(4, summary.overall.games)
        assertEquals(63, summary.overall.scorePercent) // 2.5 / 4 = 62.5 -> 63
    }

    @Test
    fun colourRecordsAreSeparate() {
        val summary = InsightsCalculator.summarize(
            listOf(
                game(InsightOutcome.WIN, InsightColor.WHITE, at = 1),
                game(InsightOutcome.LOSS, InsightColor.BLACK, at = 2),
                game(InsightOutcome.DRAW, InsightColor.BLACK, at = 3),
            ),
        )

        assertEquals(InsightRecord(wins = 1), summary.asWhite)
        assertEquals(InsightRecord(draws = 1, losses = 1), summary.asBlack)
    }

    @Test
    fun timeClassesFollowBasePlusFortyIncrements() {
        assertEquals("Bullet", InsightsCalculator.timeClassOf(60_000L, 0L))
        assertEquals("Bullet", InsightsCalculator.timeClassOf(60_000L, 2_000L)) // 60 + 80 = 140 s
        assertEquals("Blitz", InsightsCalculator.timeClassOf(180_000L, 0L))
        assertEquals("Blitz", InsightsCalculator.timeClassOf(180_000L, 2_000L)) // 260 s
        assertEquals("Rapid", InsightsCalculator.timeClassOf(600_000L, 0L))
        assertEquals("Rapid", InsightsCalculator.timeClassOf(300_000L, 10_000L)) // 700 s
        assertNull(InsightsCalculator.timeClassOf(null, null))
        assertNull(InsightsCalculator.timeClassOf(0L, 0L))
    }

    @Test
    fun segmentsAppearInFixedOrderAndOnlyWhenPlayed() {
        val summary = InsightsCalculator.summarize(
            listOf(
                game(InsightOutcome.WIN, base = 600_000L, elo = 2400, at = 1),
                game(InsightOutcome.LOSS, base = 60_000L, elo = 500, at = 2),
                game(InsightOutcome.DRAW, base = 60_000L, elo = null, at = 3),
            ),
        )

        assertEquals(listOf("Bullet", "Rapid"), summary.byTimeControl.map { it.label })
        assertEquals(InsightRecord(draws = 1, losses = 1), summary.byTimeControl.first().record)
        assertEquals(listOf("Under 1000", "2200 and up", "Full strength"), summary.byStrength.map { it.label })
    }

    @Test
    fun strengthBandsUseHalfOpenRanges() {
        assertEquals("Under 1000", InsightsCalculator.strengthBand(999))
        assertEquals("1000–1599", InsightsCalculator.strengthBand(1_000))
        assertEquals("1000–1599", InsightsCalculator.strengthBand(1_599))
        assertEquals("1600–2199", InsightsCalculator.strengthBand(1_600))
        assertEquals("2200 and up", InsightsCalculator.strengthBand(2_200))
        assertEquals("Full strength", InsightsCalculator.strengthBand(null))
    }

    @Test
    fun recentFormIsNewestFirstAndCappedAtTen() {
        val games = (1..14).map { i ->
            game(if (i % 2 == 0) InsightOutcome.WIN else InsightOutcome.LOSS, at = i.toLong())
        }

        val summary = InsightsCalculator.summarize(games.shuffled(java.util.Random(4)))

        assertEquals(InsightsSummary.RECENT_FORM_LENGTH, summary.recentForm.size)
        assertEquals(InsightOutcome.WIN, summary.recentForm.first()) // game 14 is newest
        assertEquals(InsightOutcome.LOSS, summary.recentForm[1])
    }

    @Test
    fun streakCountsTheUnbrokenRunEndingAtTheNewestGame() {
        val summary = InsightsCalculator.summarize(
            listOf(
                game(InsightOutcome.LOSS, at = 1),
                game(InsightOutcome.WIN, at = 2), game(InsightOutcome.WIN, at = 3), game(InsightOutcome.WIN, at = 4),
            ),
        )

        assertEquals(InsightStreak(InsightOutcome.WIN, 3), summary.streak)
    }

    @Test
    fun endingsAreAttributedToTheRightSide() {
        val summary = InsightsCalculator.summarize(
            listOf(
                game(InsightOutcome.WIN, ending = InsightEnding.CHECKMATE, at = 1),
                game(InsightOutcome.LOSS, ending = InsightEnding.CHECKMATE, at = 2),
                game(InsightOutcome.WIN, ending = InsightEnding.TIMEOUT, at = 3),
                game(InsightOutcome.LOSS, ending = InsightEnding.TIMEOUT, at = 4),
                game(InsightOutcome.LOSS, ending = InsightEnding.TIMEOUT, at = 5),
                game(InsightOutcome.WIN, ending = InsightEnding.RESIGNATION, at = 6),
                game(InsightOutcome.LOSS, ending = InsightEnding.RESIGNATION, at = 7),
                game(InsightOutcome.DRAW, ending = InsightEnding.STALEMATE, at = 8),
                game(InsightOutcome.DRAW, ending = InsightEnding.REPETITION, at = 9),
            ),
        )

        assertEquals(
            InsightEndings(
                checkmatesGiven = 1, checkmatesTaken = 1, wonOnTime = 1, lostOnTime = 2,
                opponentResigned = 1, youResigned = 1, drawn = 2,
            ),
            summary.endings,
        )
    }

    @Test
    fun chess960GamesAreCounted() {
        val summary = InsightsCalculator.summarize(
            listOf(game(InsightOutcome.WIN, chess960 = true, at = 1), game(InsightOutcome.WIN, at = 2)),
        )
        assertEquals(1, summary.chess960Games)
    }
}

package dev.lumenchess.insights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightsPresentationTest {
    @Test
    fun streaksReadNaturally() {
        assertEquals("3 wins in a row", streakLabel(InsightStreak(InsightOutcome.WIN, 3)))
        assertEquals("2 losses in a row", streakLabel(InsightStreak(InsightOutcome.LOSS, 2)))
        assertEquals("4 draws in a row", streakLabel(InsightStreak(InsightOutcome.DRAW, 4)))
    }

    @Test
    fun endingRowsListOnlyWhatHappened() {
        val rows = endingRows(
            InsightEndings(
                checkmatesGiven = 3, checkmatesTaken = 0, wonOnTime = 0, lostOnTime = 2,
                opponentResigned = 0, youResigned = 0, drawn = 1,
            ),
        )

        assertEquals(listOf("Won by checkmate" to 3, "Lost on time" to 2, "Drawn" to 1), rows)
        assertTrue(endingRows(InsightEndings(0, 0, 0, 0, 0, 0, 0)).isEmpty())
    }
}

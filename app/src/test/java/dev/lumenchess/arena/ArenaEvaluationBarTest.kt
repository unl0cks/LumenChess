package dev.lumenchess.arena

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArenaEvaluationBarTest {
    @Test
    fun evaluationIsWhiteRelativeAndBounded() {
        assertEquals(.5f, ArenaEvaluation().whiteFraction())
        assertTrue(ArenaEvaluation(whiteCentipawns = 180).whiteFraction() > .5f)
        assertTrue(ArenaEvaluation(whiteCentipawns = -180).whiteFraction() < .5f)
        assertEquals(.92f, ArenaEvaluation(whiteCentipawns = 100_000).whiteFraction())
        assertEquals(.08f, ArenaEvaluation(whiteCentipawns = -100_000).whiteFraction())
    }

    @Test
    fun mateAndCentipawnLabelsRemainUnambiguous() {
        assertEquals("+M3", ArenaEvaluation(whiteMateIn = 3).label())
        assertEquals("-M2", ArenaEvaluation(whiteMateIn = -2).label())
        assertEquals("+0.82", ArenaEvaluation(whiteCentipawns = 82).label())
    }
}

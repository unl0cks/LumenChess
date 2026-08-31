package dev.lumenchess.engine.host

import dev.lumenchess.engine.api.UciInfo
import dev.lumenchess.engine.api.UciScore
import dev.lumenchess.engine.api.UciScoreBound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineEvaluationProjectionTest {
    @Test
    fun onlyRankOneScoredInfoCrossesTheBinderBoundary() {
        assertNull(UciInfo(depth = 12, multiPv = 2, score = UciScore.Centipawns(40)).toRankOneSearchInfo())
        assertNull(UciInfo(depth = 12).toRankOneSearchInfo())

        val projected = UciInfo(
            depth = 18,
            multiPv = 1,
            score = UciScore.Mate(3, UciScoreBound.LOWER),
            nodes = 4_200,
            nodesPerSecond = 21_000,
            principalVariation = listOf("e2e4", "e7e5"),
        ).toRankOneSearchInfo()

        requireNotNull(projected)
        assertEquals(18, projected.depth)
        assertEquals(2, projected.scoreKind)
        assertEquals(3, projected.scoreValue)
        assertEquals(1, projected.scoreBound)
        assertEquals(4_200L, projected.nodes)
        assertEquals(21_000L, projected.nodesPerSecond)
        assertEquals("e2e4 e7e5", projected.principalVariation)
    }
}

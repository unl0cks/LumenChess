package dev.lumenchess.engine.host

import dev.lumenchess.engine.api.EngineCandidateScore
import dev.lumenchess.engine.api.EngineMoveCandidate
import dev.lumenchess.engine.api.UciInfo
import dev.lumenchess.engine.api.UciScore
import dev.lumenchess.engine.api.UciScoreBound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCandidateAccumulatorTest {
    @Test
    fun deepestCompleteSnapshotWinsWithoutMixingDepths() {
        val accumulator = EngineCandidateAccumulator(expectedLines = 2)

        accumulator.observe(info(depth = 8, rank = 1, cp = 30, move = "e2e4"))
        accumulator.observe(info(depth = 8, rank = 2, cp = 20, move = "d2d4"))
        assertEquals(
            listOf(
                EngineMoveCandidate(1, "e2e4", EngineCandidateScore.Centipawns(30)),
                EngineMoveCandidate(2, "d2d4", EngineCandidateScore.Centipawns(20)),
            ),
            accumulator.snapshot(),
        )

        accumulator.observe(info(depth = 9, rank = 1, cp = 40, move = "c2c4"))
        assertEquals(
            "A partial deeper depth must not be mixed with the previous rank-2 line",
            listOf(
                EngineMoveCandidate(1, "e2e4", EngineCandidateScore.Centipawns(30)),
                EngineMoveCandidate(2, "d2d4", EngineCandidateScore.Centipawns(20)),
            ),
            accumulator.snapshot(),
        )

        accumulator.observe(info(depth = 9, rank = 2, cp = 25, move = "g1f3"))
        assertEquals(
            listOf(
                EngineMoveCandidate(1, "c2c4", EngineCandidateScore.Centipawns(40)),
                EngineMoveCandidate(2, "g1f3", EngineCandidateScore.Centipawns(25)),
            ),
            accumulator.snapshot(),
        )
    }

    @Test
    fun missingMultiPvIsLineOne() {
        val accumulator = EngineCandidateAccumulator(expectedLines = 1)
        accumulator.observe(
            UciInfo(
                depth = 5,
                score = UciScore.Centipawns(12),
                principalVariation = listOf("e2e4", "e7e5"),
            ),
        )
        assertEquals(
            listOf(EngineMoveCandidate(1, "e2e4", EngineCandidateScore.Centipawns(12))),
            accumulator.snapshot(),
        )
    }

    @Test
    fun infoWithoutExactScoreAndPrincipalVariationIsIgnored() {
        val accumulator = EngineCandidateAccumulator(expectedLines = 1)
        accumulator.observe(UciInfo(depth = 5, principalVariation = listOf("e2e4")))
        accumulator.observe(UciInfo(depth = 5, score = UciScore.Centipawns(10)))
        accumulator.observe(
            UciInfo(
                depth = 5,
                score = UciScore.Centipawns(10, UciScoreBound.LOWER),
                principalVariation = listOf("e2e4"),
            ),
        )
        assertTrue(accumulator.snapshot().isEmpty())

        accumulator.observe(info(depth = 5, rank = 1, cp = 10, move = "e2e4"))
        assertEquals(1, accumulator.snapshot().size)
    }

    @Test
    fun mateScoresRemainTypedCandidates() {
        val accumulator = EngineCandidateAccumulator(expectedLines = 1)
        accumulator.observe(
            UciInfo(
                depth = 7,
                score = UciScore.Mate(3),
                principalVariation = listOf("f7f8"),
            ),
        )
        assertEquals(
            listOf(EngineMoveCandidate(1, "f7f8", EngineCandidateScore.Mate(3))),
            accumulator.snapshot(),
        )
    }

    @Test
    fun resetPreventsCandidatesLeakingAcrossSearches() {
        val accumulator = EngineCandidateAccumulator(expectedLines = 2)
        accumulator.observe(info(depth = 6, rank = 1, cp = 20, move = "e2e4"))
        accumulator.observe(info(depth = 6, rank = 2, cp = 10, move = "d2d4"))
        assertEquals(2, accumulator.snapshot().size)

        accumulator.reset()
        assertTrue(accumulator.snapshot().isEmpty())

        accumulator.observe(info(depth = 7, rank = 1, cp = 25, move = "g1f3"))
        assertTrue("A single line cannot resurrect stale rank-2 output", accumulator.snapshot().isEmpty())
    }

    private fun info(depth: Int, rank: Int, cp: Int, move: String) = UciInfo(
        depth = depth,
        multiPv = rank,
        score = UciScore.Centipawns(cp),
        principalVariation = listOf(move),
    )
}

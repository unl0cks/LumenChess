package dev.lumenchess.engine.host

import dev.lumenchess.engine.api.EngineCandidateScore
import dev.lumenchess.engine.api.EngineMoveCandidate
import dev.lumenchess.engine.api.UciInfo
import dev.lumenchess.engine.api.UciScore
import dev.lumenchess.engine.api.UciScoreBound

/**
 * Keeps only coherent MultiPV snapshots from one search. A deeper depth does not replace the
 * previous snapshot until every expected line has been observed at that same depth.
 */
internal class EngineCandidateAccumulator(
    private val expectedLines: Int,
) {
    init {
        require(expectedLines > 0) { "Expected MultiPV line count must be positive" }
    }

    private val candidatesByDepth = mutableMapOf<Int, MutableMap<Int, EngineMoveCandidate>>()
    private var completedDepth: Int? = null
    private var completedSnapshot: List<EngineMoveCandidate> = emptyList()

    fun observe(info: UciInfo) {
        val depth = info.depth ?: return
        val score = info.score ?: return
        val move = info.principalVariation.firstOrNull() ?: return
        val rank = info.multiPv ?: 1
        if (rank !in 1..expectedLines) return

        val candidateScore = when (score) {
            is UciScore.Centipawns -> {
                if (score.bound != UciScoreBound.EXACT) return
                EngineCandidateScore.Centipawns(score.value)
            }
            is UciScore.Mate -> {
                if (score.bound != UciScoreBound.EXACT) return
                EngineCandidateScore.Mate(score.moves)
            }
        }

        val candidatesAtDepth = candidatesByDepth.getOrPut(depth) { mutableMapOf() }
        candidatesAtDepth[rank] = EngineMoveCandidate(
            rank = rank,
            moveUci = move,
            score = candidateScore,
        )

        if ((1..expectedLines).all(candidatesAtDepth::containsKey) &&
            (completedDepth == null || depth >= checkNotNull(completedDepth))
        ) {
            completedDepth = depth
            completedSnapshot = (1..expectedLines).map { checkNotNull(candidatesAtDepth[it]) }
            candidatesByDepth.keys.removeAll { it < depth }
        }
    }

    fun snapshot(): List<EngineMoveCandidate> = completedSnapshot.toList()

    fun reset() {
        candidatesByDepth.clear()
        completedDepth = null
        completedSnapshot = emptyList()
    }
}

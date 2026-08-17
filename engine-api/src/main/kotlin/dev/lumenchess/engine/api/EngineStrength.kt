package dev.lumenchess.engine.api

import kotlin.math.abs

/** User-visible finite-strength strategy. Full strength is represented by [EngineStrengthTarget]. */
enum class EngineStrengthModel {
    ENGINE_NATIVE,
    HUMANIZED,
    HYBRID,
}

sealed interface EngineStrengthTarget {
    data object FullStrength : EngineStrengthTarget

    data class Elo(val value: Int) : EngineStrengthTarget {
        init {
            require(value in MIN_ELO..MAX_ELO) {
                "Strength target must be between $MIN_ELO and $MAX_ELO Elo"
            }
        }
    }

    companion object {
        const val MIN_ELO: Int = 400
        const val MAX_ELO: Int = 3000
    }
}

data class EngineStrengthSettings(
    val target: EngineStrengthTarget,
    val model: EngineStrengthModel = EngineStrengthModel.HYBRID,
    val seed: Long = 0L,
) {
    companion object {
        fun fullStrength(seed: Long = 0L): EngineStrengthSettings = EngineStrengthSettings(
            target = EngineStrengthTarget.FullStrength,
            model = EngineStrengthModel.HYBRID,
            seed = seed,
        )
    }
}

sealed interface EngineCandidateScore {
    data class Centipawns(val value: Int) : EngineCandidateScore
    data class Mate(val moves: Int) : EngineCandidateScore
}

data class EngineMoveCandidate(
    val rank: Int,
    val moveUci: String,
    val score: EngineCandidateScore,
) {
    init {
        require(rank > 0) { "Candidate rank must be positive" }
        require(moveUci.isNotBlank()) { "Candidate move cannot be blank" }
    }
}

data class EngineHumanizationProfile(
    val candidateCount: Int,
    val maximumLossCentipawns: Int,
    val temperatureCentipawns: Int,
    val depthCap: Int?,
) {
    init {
        require(candidateCount > 0) { "Candidate count must be positive" }
        require(maximumLossCentipawns >= 0) { "Maximum candidate loss cannot be negative" }
        require(temperatureCentipawns > 0) { "Selection temperature must be positive" }
        require(depthCap == null || depthCap > 0) { "Depth cap must be positive" }
    }
}

data class EngineStrengthPlan(
    val target: EngineStrengthTarget,
    val model: EngineStrengthModel,
    val seed: Long,
    val nativeElo: Int?,
    val humanization: EngineHumanizationProfile?,
    val calibrationVersion: String,
)

sealed interface EngineStrengthPlanning {
    data class Supported(val plan: EngineStrengthPlan) : EngineStrengthPlanning
    data class Unsupported(val reason: String) : EngineStrengthPlanning
}

/**
 * Versioned initial strength curve. The numbers are deliberately treated as calibration data rather
 * than exact real-world Elo claims; later empirical calibration can replace the table under a new
 * version while preserving deterministic replay for recorded settings.
 */
object EngineStrengthPlanner {
    const val CALIBRATION_VERSION: String = "m15-v1-2026-08-17"

    private data class CalibrationAnchor(
        val elo: Int,
        val candidateCount: Int,
        val maximumLossCentipawns: Int,
        val temperatureCentipawns: Int,
        val depthCap: Int,
    )

    private val anchors = listOf(
        CalibrationAnchor(400, 8, 450, 200, 4),
        CalibrationAnchor(800, 8, 340, 170, 6),
        CalibrationAnchor(1200, 7, 240, 130, 8),
        CalibrationAnchor(1600, 6, 150, 95, 10),
        CalibrationAnchor(2000, 5, 90, 65, 14),
        CalibrationAnchor(2200, 4, 65, 50, 16),
        CalibrationAnchor(2400, 4, 45, 38, 18),
        CalibrationAnchor(2600, 3, 30, 28, 20),
        CalibrationAnchor(2800, 2, 18, 18, 24),
        CalibrationAnchor(3000, 2, 10, 10, 28),
    )

    fun plan(
        settings: EngineStrengthSettings,
        capabilities: EngineCapabilities,
    ): EngineStrengthPlanning {
        val target = settings.target
        if (target is EngineStrengthTarget.FullStrength) {
            return supported(settings, nativeElo = null, humanization = null)
        }

        val targetElo = (target as EngineStrengthTarget.Elo).value
        val nativeRange = capabilities.strength as? EngineStrengthCapability.EloRange
        return when (settings.model) {
            EngineStrengthModel.ENGINE_NATIVE -> {
                if (nativeRange == null) {
                    EngineStrengthPlanning.Unsupported(
                        "Engine native strength limiting is unavailable for this engine",
                    )
                } else {
                    supported(
                        settings,
                        nativeElo = targetElo.coerceIn(nativeRange.minElo, nativeRange.maxElo),
                        humanization = null,
                    )
                }
            }

            EngineStrengthModel.HUMANIZED -> supported(
                settings,
                nativeElo = null,
                humanization = calibratedProfile(targetElo),
            )

            EngineStrengthModel.HYBRID -> {
                val nativeElo = nativeRange?.let { targetElo.coerceIn(it.minElo, it.maxElo) }
                val baseProfile = calibratedProfile(targetElo)
                val humanization = if (nativeRange == null) {
                    baseProfile
                } else {
                    scaleForHybrid(baseProfile, targetElo)
                }
                supported(settings, nativeElo = nativeElo, humanization = humanization)
            }
        }
    }

    private fun supported(
        settings: EngineStrengthSettings,
        nativeElo: Int?,
        humanization: EngineHumanizationProfile?,
    ): EngineStrengthPlanning.Supported = EngineStrengthPlanning.Supported(
        EngineStrengthPlan(
            target = settings.target,
            model = settings.model,
            seed = settings.seed,
            nativeElo = nativeElo,
            humanization = humanization,
            calibrationVersion = CALIBRATION_VERSION,
        ),
    )

    private fun calibratedProfile(elo: Int): EngineHumanizationProfile {
        val upperIndex = anchors.indexOfFirst { it.elo >= elo }.coerceAtLeast(0)
        val upper = anchors[upperIndex]
        if (upper.elo == elo || upperIndex == 0) {
            return upper.toProfile()
        }
        val lower = anchors[upperIndex - 1]
        val span = upper.elo - lower.elo
        val offset = elo - lower.elo
        return EngineHumanizationProfile(
            candidateCount = interpolate(lower.candidateCount, upper.candidateCount, offset, span),
            maximumLossCentipawns = interpolate(
                lower.maximumLossCentipawns,
                upper.maximumLossCentipawns,
                offset,
                span,
            ),
            temperatureCentipawns = interpolate(
                lower.temperatureCentipawns,
                upper.temperatureCentipawns,
                offset,
                span,
            ),
            depthCap = interpolate(lower.depthCap, upper.depthCap, offset, span),
        )
    }

    private fun CalibrationAnchor.toProfile(): EngineHumanizationProfile = EngineHumanizationProfile(
        candidateCount = candidateCount,
        maximumLossCentipawns = maximumLossCentipawns,
        temperatureCentipawns = temperatureCentipawns,
        depthCap = depthCap,
    )

    private fun interpolate(lower: Int, upper: Int, offset: Int, span: Int): Int {
        val delta = upper - lower
        val signedHalf = if (delta >= 0) span / 2 else -(span / 2)
        return lower + ((delta * offset + signedHalf) / span)
    }

    private fun scaleForHybrid(
        profile: EngineHumanizationProfile,
        targetElo: Int,
    ): EngineHumanizationProfile? {
        val percent = when {
            targetElo <= 1000 -> 100
            targetElo <= 1600 -> interpolate(100, 75, targetElo - 1000, 600)
            targetElo <= 2000 -> interpolate(75, 50, targetElo - 1600, 400)
            targetElo <= 2400 -> interpolate(50, 30, targetElo - 2000, 400)
            targetElo < 2600 -> interpolate(30, 15, targetElo - 2400, 200)
            else -> 0
        }
        if (percent <= 0) return null

        val extraDepth = ((100 - percent) * 8 + 99) / 100
        return EngineHumanizationProfile(
            candidateCount = 1 + (((profile.candidateCount - 1) * percent + 99) / 100),
            maximumLossCentipawns = ((profile.maximumLossCentipawns * percent + 99) / 100),
            temperatureCentipawns = ((profile.temperatureCentipawns * percent + 99) / 100).coerceAtLeast(1),
            depthCap = profile.depthCap?.plus(extraDepth),
        )
    }
}

object EngineCandidateSelector {
    fun select(
        candidates: List<EngineMoveCandidate>,
        fallbackBestMoveUci: String?,
        plan: EngineStrengthPlan,
        searchId: EngineSearchId,
        positionRevision: PositionRevision,
    ): String? {
        val profile = plan.humanization ?: return fallbackBestMoveUci ?: bestCandidate(candidates)?.moveUci
        val ordered = candidates
            .distinctBy { it.moveUci }
            .sortedWith(
                compareByDescending<EngineMoveCandidate> { normalizedScore(it.score) }
                    .thenBy { it.rank }
                    .thenBy { it.moveUci },
            )
            .take(profile.candidateCount)
        if (ordered.isEmpty()) return fallbackBestMoveUci

        val bestScore = normalizedScore(ordered.first().score)
        val plausible = ordered.filter { candidate ->
            val loss = scoreLoss(bestScore, normalizedScore(candidate.score))
            loss <= profile.maximumLossCentipawns.toLong()
        }
        if (plausible.isEmpty()) return fallbackBestMoveUci ?: ordered.first().moveUci
        if (plausible.size == 1) return plausible.first().moveUci

        val temperature = profile.temperatureCentipawns.toLong()
        val weighted = plausible.map { candidate ->
            val loss = scoreLoss(bestScore, normalizedScore(candidate.score))
            val denominator = temperature + loss
            val weight = (
                (temperature * temperature * WEIGHT_SCALE) /
                    (denominator * denominator)
                ).coerceAtLeast(1L)
            candidate to weight
        }
        val totalWeight = weighted.sumOf { it.second }
        val random = mixedSeed(plan.seed, searchId.value, positionRevision.value)
        var bucket = Math.floorMod(random, totalWeight)
        for ((candidate, weight) in weighted) {
            if (bucket < weight) return candidate.moveUci
            bucket -= weight
        }
        return weighted.last().first.moveUci
    }

    private fun bestCandidate(candidates: List<EngineMoveCandidate>): EngineMoveCandidate? = candidates.maxWithOrNull(
        compareBy<EngineMoveCandidate> { normalizedScore(it.score) }
            .thenByDescending { -it.rank }
            .thenByDescending { it.moveUci },
    )

    private fun scoreLoss(bestScore: Long, candidateScore: Long): Long =
        (bestScore - candidateScore).coerceAtLeast(0L)

    private fun normalizedScore(score: EngineCandidateScore): Long = when (score) {
        is EngineCandidateScore.Centipawns -> score.value.toLong().coerceIn(-MAX_CP_SCORE, MAX_CP_SCORE)
        is EngineCandidateScore.Mate -> {
            val distance = abs(score.moves.toLong()).coerceAtMost(MAX_MATE_DISTANCE)
            when {
                score.moves >= 0 -> MATE_SCORE - distance * MATE_DISTANCE_STEP
                else -> -MATE_SCORE + distance * MATE_DISTANCE_STEP
            }
        }
    }

    private fun mixedSeed(seed: Long, searchId: Long, revision: Long): Long {
        val input = seed xor (searchId * GOLDEN_GAMMA) xor (revision * MIX_CONSTANT_2)
        var value = input + GOLDEN_GAMMA
        value = (value xor (value ushr 30)) * MIX_CONSTANT_1
        value = (value xor (value ushr 27)) * MIX_CONSTANT_2
        return value xor (value ushr 31)
    }

    private const val WEIGHT_SCALE: Long = 1_000_000L
    private const val MAX_CP_SCORE: Long = 90_000L
    private const val MATE_SCORE: Long = 100_000L
    private const val MAX_MATE_DISTANCE: Long = 99L
    private const val MATE_DISTANCE_STEP: Long = 100L
    private const val GOLDEN_GAMMA: Long = -7046029254386353131L
    private const val MIX_CONSTANT_1: Long = -4658895280553007687L
    private const val MIX_CONSTANT_2: Long = -7723592293110705685L
}

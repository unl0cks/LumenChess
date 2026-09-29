package dev.lumenchess.insights

enum class InsightColor { WHITE, BLACK }

enum class InsightOutcome { WIN, DRAW, LOSS }

enum class InsightEnding { CHECKMATE, TIMEOUT, RESIGNATION, AGREEMENT, STALEMATE, REPETITION, FIFTY_MOVE, INSUFFICIENT_MATERIAL, OTHER }

/** One finished game against an engine, from the human's point of view. */
data class InsightGame(
    val humanColor: InsightColor,
    val outcome: InsightOutcome,
    val ending: InsightEnding,
    val baseMillis: Long?,
    val incrementMillis: Long?,
    /** Null when the opponent played at full strength. */
    val opponentElo: Int?,
    val chess960: Boolean,
    val playedAtEpochMillis: Long,
)

data class InsightRecord(val wins: Int = 0, val draws: Int = 0, val losses: Int = 0) {
    val games: Int get() = wins + draws + losses

    /** Points scored as a whole percentage (a draw is half a point); null with no games. */
    val scorePercent: Int?
        get() = if (games == 0) null else Math.round((wins + draws * 0.5) * 100.0 / games).toInt()

    fun plus(outcome: InsightOutcome): InsightRecord = when (outcome) {
        InsightOutcome.WIN -> copy(wins = wins + 1)
        InsightOutcome.DRAW -> copy(draws = draws + 1)
        InsightOutcome.LOSS -> copy(losses = losses + 1)
    }
}

data class InsightSegment(val label: String, val record: InsightRecord)

data class InsightStreak(val outcome: InsightOutcome, val length: Int)

data class InsightEndings(
    val checkmatesGiven: Int,
    val checkmatesTaken: Int,
    val wonOnTime: Int,
    val lostOnTime: Int,
    val opponentResigned: Int,
    val youResigned: Int,
    val drawn: Int,
)

data class InsightsSummary(
    val overall: InsightRecord,
    val asWhite: InsightRecord,
    val asBlack: InsightRecord,
    val byTimeControl: List<InsightSegment>,
    val byStrength: List<InsightSegment>,
    /** Newest first, at most [RECENT_FORM_LENGTH]. */
    val recentForm: List<InsightOutcome>,
    val streak: InsightStreak?,
    val endings: InsightEndings,
    val chess960Games: Int,
) {
    val isEmpty: Boolean get() = overall.games == 0

    companion object {
        const val RECENT_FORM_LENGTH = 10
    }
}

/**
 * Turns finished games into the numbers the Insights screen shows. Deliberately limited to what a
 * result list can honestly support: no accuracy or move-quality claims, which need Game Review.
 */
object InsightsCalculator {
    val TIME_CLASSES = listOf("Bullet", "Blitz", "Rapid")

    fun summarize(games: List<InsightGame>): InsightsSummary {
        val newestFirst = games.sortedByDescending { it.playedAtEpochMillis }

        var overall = InsightRecord()
        var white = InsightRecord()
        var black = InsightRecord()
        val timeClass = linkedMapOf<String, InsightRecord>()
        val strength = linkedMapOf<String, InsightRecord>()
        for (game in newestFirst) {
            overall = overall.plus(game.outcome)
            if (game.humanColor == InsightColor.WHITE) white = white.plus(game.outcome) else black = black.plus(game.outcome)
            timeClassOf(game.baseMillis, game.incrementMillis)?.let { key ->
                timeClass[key] = (timeClass[key] ?: InsightRecord()).plus(game.outcome)
            }
            val band = strengthBand(game.opponentElo)
            strength[band] = (strength[band] ?: InsightRecord()).plus(game.outcome)
        }

        return InsightsSummary(
            overall = overall,
            asWhite = white,
            asBlack = black,
            byTimeControl = TIME_CLASSES.mapNotNull { name -> timeClass[name]?.let { InsightSegment(name, it) } },
            byStrength = STRENGTH_BANDS.mapNotNull { name -> strength[name]?.let { InsightSegment(name, it) } },
            recentForm = newestFirst.take(InsightsSummary.RECENT_FORM_LENGTH).map { it.outcome },
            streak = streakOf(newestFirst),
            endings = endingsOf(newestFirst),
            chess960Games = newestFirst.count { it.chess960 },
        )
    }

    /** Chess.com convention: base + 40 x increment, in seconds. Untimed games are unclassified. */
    fun timeClassOf(baseMillis: Long?, incrementMillis: Long?): String? {
        if (baseMillis == null || baseMillis <= 0L) return null
        val seconds = (baseMillis + 40L * (incrementMillis ?: 0L)) / 1_000L
        return when {
            seconds < 180L -> "Bullet"
            seconds < 600L -> "Blitz"
            else -> "Rapid"
        }
    }

    private val STRENGTH_BANDS = listOf("Under 1000", "1000–1599", "1600–2199", "2200 and up", "Full strength")

    fun strengthBand(elo: Int?): String = when {
        elo == null -> "Full strength"
        elo < 1_000 -> "Under 1000"
        elo < 1_600 -> "1000–1599"
        elo < 2_200 -> "1600–2199"
        else -> "2200 and up"
    }

    private fun streakOf(newestFirst: List<InsightGame>): InsightStreak? {
        val head = newestFirst.firstOrNull()?.outcome ?: return null
        val length = newestFirst.takeWhile { it.outcome == head }.size
        return InsightStreak(head, length)
    }

    private fun endingsOf(games: List<InsightGame>): InsightEndings {
        fun count(outcome: InsightOutcome, ending: InsightEnding) =
            games.count { it.outcome == outcome && it.ending == ending }
        return InsightEndings(
            checkmatesGiven = count(InsightOutcome.WIN, InsightEnding.CHECKMATE),
            checkmatesTaken = count(InsightOutcome.LOSS, InsightEnding.CHECKMATE),
            wonOnTime = count(InsightOutcome.WIN, InsightEnding.TIMEOUT),
            lostOnTime = count(InsightOutcome.LOSS, InsightEnding.TIMEOUT),
            opponentResigned = count(InsightOutcome.WIN, InsightEnding.RESIGNATION),
            youResigned = count(InsightOutcome.LOSS, InsightEnding.RESIGNATION),
            drawn = games.count { it.outcome == InsightOutcome.DRAW },
        )
    }
}

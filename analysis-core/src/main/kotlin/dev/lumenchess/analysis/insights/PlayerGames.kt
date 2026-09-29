package dev.lumenchess.analysis.insights

import dev.lumenchess.analysis.rating.PerformanceEstimate
import dev.lumenchess.analysis.rating.RatedResult
import dev.lumenchess.analysis.rating.RatingState
import dev.lumenchess.analysis.rating.RatingSystem
import dev.lumenchess.analysis.rating.RatingSystems
import dev.lumenchess.analysis.rating.TimeClass
import dev.lumenchess.analysis.review.GamePhase
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import kotlin.math.roundToInt

/** Who the user played. Arena games are engine-vs-engine and have no user side. */
enum class GameKind(val label: String) { HUMAN("Human"), ENGINE("Engine"), ARENA("Arena") }

enum class GameOrigin(val label: String) { LOCAL("Local"), CHESS_COM("Chess.com"), LICHESS("Lichess"), IMPORTED("Imported") }

enum class Outcome { WIN, DRAW, LOSS }

/** The user's own review numbers for one game. */
data class ReviewStats(
    val accuracy: Double?,
    val gameRating: Int?,
    val counts: Map<MoveClassification, Int>,
    val phaseAccuracy: Map<GamePhase, Double?>,
    val averageCentipawnLoss: Int?,
)

/** One game from the user's point of view, as Insights and ratings read it. */
data class PlayerGame(
    val gameId: String,
    val kind: GameKind,
    val origin: GameOrigin,
    /** Null for Arena games. */
    val userColor: Color?,
    /** From the user's side; null when unfinished or for Arena games. */
    val outcome: Outcome?,
    val variant: Variant,
    val timeClass: TimeClass,
    val rated: Boolean,
    val playedAtEpochMillis: Long,
    val opponentName: String? = null,
    /** Engine target Elo, or the opponent's rating from the game's tags. */
    val opponentRating: Int? = null,
    val openingEco: String? = null,
    val openingFamily: String? = null,
    val openingName: String? = null,
    val review: ReviewStats? = null,
)

data class InsightsFilter(
    val kind: GameKind? = null,
    val timeClass: TimeClass? = null,
    val variant: Variant? = null,
    val origin: GameOrigin? = null,
    /** Only games played in the last N days; null = all time. */
    val days: Int? = null,
    val rated: Boolean? = null,
) {
    fun matches(game: PlayerGame, nowEpochMillis: Long): Boolean =
        (kind == null || game.kind == kind) &&
            (timeClass == null || game.timeClass == timeClass) &&
            (variant == null || game.variant == variant) &&
            (origin == null || game.origin == origin) &&
            (rated == null || game.rated == rated) &&
            (days == null || game.playedAtEpochMillis >= nowEpochMillis - days * DAY_MILLIS)

    companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}

data class Record(val wins: Int = 0, val draws: Int = 0, val losses: Int = 0) {
    val games: Int get() = wins + draws + losses
    val scorePercent: Int? get() = if (games == 0) null else ((wins + draws * .5) * 100.0 / games).roundToInt()

    operator fun plus(outcome: Outcome?): Record = when (outcome) {
        Outcome.WIN -> copy(wins = wins + 1)
        Outcome.DRAW -> copy(draws = draws + 1)
        Outcome.LOSS -> copy(losses = losses + 1)
        null -> this
    }
}

data class Overview(
    val games: Int,
    val record: Record,
    val reviewed: Int,
    val averageAccuracy: Double?,
    val averageGameRating: Int?,
    val averageOpponentRating: Int?,
)

/** Average count per reviewed game. */
data class MoveQuality(
    val reviewedGames: Int,
    val inaccuracies: Double,
    val mistakes: Double,
    val misses: Double,
    val blunders: Double,
    val brilliants: Double,
    val greats: Double,
)

data class OpeningStat(
    val name: String,
    val eco: String?,
    val color: Color,
    val record: Record,
    val averageAccuracy: Double?,
    val gameIds: List<String>,
)

data class Segment(val label: String, val record: Record, val averageAccuracy: Double?, val games: Int)

/** Direction of recent change, comparing the newest [Trend.WINDOW] games with the ones before. */
data class Trend(
    val accuracyChange: Double?,
    val gameRatingChange: Int?,
    val errorsPerGameChange: Double?,
    val scoreChange: Int?,
) {
    companion object {
        const val WINDOW = 10
    }
}

data class TrendPoint(val epochMillis: Long, val value: Double, val gameId: String)

object Insights {
    fun filter(games: List<PlayerGame>, filter: InsightsFilter, nowEpochMillis: Long): List<PlayerGame> =
        games.filter { filter.matches(it, nowEpochMillis) }.sortedByDescending { it.playedAtEpochMillis }

    fun overview(games: List<PlayerGame>): Overview {
        val own = games.filter { it.userColor != null }
        val reviewed = own.mapNotNull { it.review }
        return Overview(
            games = games.size,
            record = own.fold(Record()) { acc, game -> acc + game.outcome },
            reviewed = reviewed.size,
            averageAccuracy = reviewed.mapNotNull { it.accuracy }.averageOrNull(),
            averageGameRating = reviewed.mapNotNull { it.gameRating }.averageOrNull()?.let { (it / 10.0).roundToInt() * 10 },
            averageOpponentRating = own.mapNotNull { it.opponentRating }.averageOrNull()?.roundToInt(),
        )
    }

    fun moveQuality(games: List<PlayerGame>): MoveQuality {
        val reviews = games.mapNotNull { it.review }
        fun per(classification: MoveClassification) =
            if (reviews.isEmpty()) 0.0 else reviews.sumOf { it.counts[classification] ?: 0 }.toDouble() / reviews.size
        return MoveQuality(
            reviewedGames = reviews.size,
            inaccuracies = per(MoveClassification.INACCURACY),
            mistakes = per(MoveClassification.MISTAKE),
            misses = per(MoveClassification.MISS),
            blunders = per(MoveClassification.BLUNDER),
            brilliants = per(MoveClassification.BRILLIANT),
            greats = per(MoveClassification.GREAT),
        )
    }

    fun phases(games: List<PlayerGame>): Map<GamePhase, Double?> = GamePhase.entries.associateWith { phase ->
        games.mapNotNull { it.review?.phaseAccuracy?.get(phase) }.averageOrNull()
    }

    /** Openings the user played (by family, per colour), most played first. */
    fun openings(games: List<PlayerGame>): List<OpeningStat> = games
        .filter { it.userColor != null && it.openingFamily != null }
        .groupBy { it.openingFamily!! to it.userColor!! }
        .map { (key, group) ->
            OpeningStat(
                name = key.first,
                eco = group.mapNotNull { it.openingEco }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                color = key.second,
                record = group.fold(Record()) { acc, game -> acc + game.outcome },
                averageAccuracy = group.mapNotNull { it.review?.accuracy }.averageOrNull(),
                gameIds = group.map { it.gameId },
            )
        }
        .sortedWith(compareByDescending<OpeningStat> { it.record.games }.thenBy { it.name })

    /** Best and worst scoring openings with at least [minGames] games. */
    fun bestAndWorst(openings: List<OpeningStat>, minGames: Int = 3): Pair<OpeningStat?, OpeningStat?> {
        val eligible = openings.filter { it.record.games >= minGames }
        if (eligible.size < 2) return eligible.firstOrNull() to null
        val best = eligible.maxWith(compareBy<OpeningStat> { it.record.scorePercent ?: 0 }.thenBy { it.record.games })
        val worst = eligible.filter { it != best }.minWith(compareBy<OpeningStat> { it.record.scorePercent ?: 0 }.thenByDescending { it.record.games })
        return best to worst
    }

    fun byTimeClass(games: List<PlayerGame>): List<Segment> = TimeClass.entries.map { timeClass ->
        segment(timeClass.label, games.filter { it.timeClass == timeClass })
    }

    fun byColor(games: List<PlayerGame>): List<Segment> = listOf(
        segment("White", games.filter { it.userColor == Color.WHITE }),
        segment("Black", games.filter { it.userColor == Color.BLACK }),
    )

    private fun segment(label: String, games: List<PlayerGame>) = Segment(
        label = label,
        record = games.fold(Record()) { acc, game -> acc + game.outcome },
        averageAccuracy = games.mapNotNull { it.review?.accuracy }.averageOrNull(),
        games = games.size,
    )

    /** [newestFirst] games; compares the newest window with the one before it. */
    fun trend(newestFirst: List<PlayerGame>): Trend {
        val own = newestFirst.filter { it.userColor != null }
        val recent = own.take(Trend.WINDOW)
        val earlier = own.drop(Trend.WINDOW).take(Trend.WINDOW)
        if (earlier.isEmpty()) return Trend(null, null, null, null)
        fun accuracy(list: List<PlayerGame>) = list.mapNotNull { it.review?.accuracy }.averageOrNull()
        fun rating(list: List<PlayerGame>) = list.mapNotNull { it.review?.gameRating }.averageOrNull()
        fun errors(list: List<PlayerGame>) = list.mapNotNull { it.review }.takeIf { it.isNotEmpty() }?.map { review ->
            (review.counts[MoveClassification.MISTAKE] ?: 0) + (review.counts[MoveClassification.BLUNDER] ?: 0) +
                (review.counts[MoveClassification.MISS] ?: 0)
        }?.average()
        fun score(list: List<PlayerGame>) = list.fold(Record()) { acc, game -> acc + game.outcome }.scorePercent
        return Trend(
            accuracyChange = difference(accuracy(recent), accuracy(earlier)),
            gameRatingChange = difference(rating(recent), rating(earlier))?.roundToInt(),
            errorsPerGameChange = difference(errors(recent), errors(earlier)),
            scoreChange = score(recent)?.let { now -> score(earlier)?.let { now - it } },
        )
    }

    /** Oldest-first series of the user's single-game ratings (from Game Review). */
    fun strengthSeries(games: List<PlayerGame>): List<TrendPoint> = games
        .filter { it.review?.gameRating != null }
        .sortedBy { it.playedAtEpochMillis }
        .map { TrendPoint(it.playedAtEpochMillis, it.review!!.gameRating!!.toDouble(), it.gameId) }

    /** Current window vs the one before it, e.g. last 30 days vs the previous 30. */
    fun comparePeriods(games: List<PlayerGame>, days: Int, nowEpochMillis: Long): Pair<Overview, Overview> {
        val span = days * InsightsFilter.DAY_MILLIS
        val current = games.filter { it.playedAtEpochMillis >= nowEpochMillis - span }
        val previous = games.filter { it.playedAtEpochMillis < nowEpochMillis - span && it.playedAtEpochMillis >= nowEpochMillis - 2 * span }
        return overview(current) to overview(previous)
    }

    private fun difference(a: Double?, b: Double?): Double? = if (a == null || b == null) null else a - b

    private fun List<Number>.averageOrNull(): Double? = if (isEmpty()) null else sumOf { it.toDouble() } / size
}

/** A local rating pool: separate for each variant and time class. */
data class PoolKey(val variant: Variant, val timeClass: TimeClass)

data class RatingPoint(val gameId: String, val epochMillis: Long, val state: RatingState)

/**
 * Event-sourced local ratings. The canonical library is the event log: every rated, finished
 * local game against an engine at a known strength is one result, replayed in time order. Each
 * rating system keeps its own full state, so switching systems never loses anything.
 */
object RatingReplay {
    fun eligible(game: PlayerGame): Boolean =
        game.kind == GameKind.ENGINE && game.origin == GameOrigin.LOCAL && game.rated &&
            game.outcome != null && game.opponentRating != null

    fun replay(games: List<PlayerGame>, system: RatingSystem): Map<PoolKey, List<RatingPoint>> {
        val pools = LinkedHashMap<PoolKey, MutableList<RatingPoint>>()
        for (game in games.filter(::eligible).sortedBy { it.playedAtEpochMillis }) {
            val key = PoolKey(game.variant, game.timeClass)
            val history = pools.getOrPut(key) { ArrayList() }
            val previous = history.lastOrNull()
            val days = previous?.let { ((game.playedAtEpochMillis - it.epochMillis).coerceAtLeast(0L) / InsightsFilter.DAY_MILLIS.toDouble()) } ?: 0.0
            val score = when (game.outcome) {
                Outcome.WIN -> 1.0
                Outcome.DRAW -> 0.5
                else -> 0.0
            }
            val state = RatingSystems.update(
                system,
                previous?.state ?: RatingSystems.initial(system),
                RatedResult(game.opponentRating!!.toDouble(), score, daysSincePrevious = days),
            )
            history += RatingPoint(game.gameId, game.playedAtEpochMillis, state)
        }
        return pools
    }

    /** Performance Estimate per pool from the user's reviewed single-game ratings. */
    fun performance(games: List<PlayerGame>): Map<PoolKey, PerformanceEstimate.Estimate> = games
        .filter { it.userColor != null && it.review?.gameRating != null }
        .groupBy { PoolKey(it.variant, it.timeClass) }
        .mapNotNull { (key, group) ->
            PerformanceEstimate.of(group.sortedByDescending { it.playedAtEpochMillis }.map { it.review!!.gameRating!! })?.let { key to it }
        }
        .toMap()
}

/** Match Your Elo: a target near the user's rating, drawn once when a game starts. */
object MatchYourElo {
    const val MIN_ELO = 400
    const val MAX_ELO = 3000

    fun target(base: Int, range: Int, random: kotlin.random.Random = kotlin.random.Random.Default): Int {
        val spread = range.coerceAtLeast(0)
        val raw = if (spread == 0) base else base + random.nextInt(-spread, spread + 1)
        return ((raw / 50.0).roundToInt() * 50).coerceIn(MIN_ELO, MAX_ELO)
    }
}

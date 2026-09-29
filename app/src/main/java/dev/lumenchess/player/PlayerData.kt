package dev.lumenchess.player

import android.content.Context
import dev.lumenchess.analysis.insights.MatchYourElo
import dev.lumenchess.analysis.insights.PlayerGame
import dev.lumenchess.analysis.insights.PoolKey
import dev.lumenchess.analysis.insights.RatingPoint
import dev.lumenchess.analysis.insights.RatingReplay
import dev.lumenchess.analysis.rating.PerformanceEstimate
import dev.lumenchess.analysis.rating.RatingSystem
import dev.lumenchess.analysis.rating.TimeClass
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.ReviewState
import dev.lumenchess.games.imports.OnlineSite
import dev.lumenchess.games.imports.UrlConnectionHttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ratings of every pool for one rating system, plus the review-based Performance Estimate. */
data class RatingsOverview(
    val system: RatingSystem,
    val pools: Map<PoolKey, List<RatingPoint>>,
    val performance: Map<PoolKey, PerformanceEstimate.Estimate>,
) {
    fun rated(key: PoolKey): RatingPoint? = pools[key]?.lastOrNull()
}

/** What Match Your Elo will aim at for a pool, and where the number came from. */
data class MatchBase(val rating: Int, val source: MatchSource, val detail: String)

/**
 * Shared, cached view of the user's games for Insights, Ratings and Match Your Elo. The cache is
 * keyed by a cheap signature of the library (game and review counts, linked names), so it is
 * rebuilt only after something that can change the numbers.
 */
object PlayerData {
    private val mutex = Mutex()
    private var cachedSignature: String? = null
    private var cachedGames: List<PlayerGame> = emptyList()

    suspend fun games(context: Context, settings: PlayerSettings, force: Boolean = false): List<PlayerGame> = mutex.withLock {
        val database = AppData.database(context)
        val signature = listOf(
            database.gameDao().countGames(),
            AppData.reviews(context).reviewsInState(ReviewState.COMPLETE).size,
            settings.identities.sorted().joinToString(","),
        ).joinToString("|")
        if (!force && signature == cachedSignature) return cachedGames
        val loaded = PlayerGamesLoader.load(context, settings)
        cachedSignature = signature
        cachedGames = loaded
        loaded
    }

    fun invalidate() {
        cachedSignature = null
    }

    fun ratings(games: List<PlayerGame>, system: RatingSystem): RatingsOverview =
        RatingsOverview(system, RatingReplay.replay(games, system), RatingReplay.performance(games))

    /** The rating Match Your Elo starts from for [variant] and [timeClass], or null with none yet. */
    fun matchBase(settings: PlayerSettings, games: List<PlayerGame>, variant: Variant, timeClass: TimeClass): MatchBase? {
        val key = PoolKey(variant, timeClass)
        val pool = "${if (variant == Variant.CHESS960) "Chess960 " else ""}${timeClass.label}"
        return when (settings.matchSource) {
            MatchSource.LOCAL_PERFORMANCE -> RatingReplay.performance(games)[key]?.let {
                MatchBase(it.rating, MatchSource.LOCAL_PERFORMANCE, "Performance Estimate · $pool${if (it.provisional) " (provisional)" else ""}")
            }
            MatchSource.LOCAL_RATED -> RatingReplay.replay(games, settings.ratingSystem)[key]?.lastOrNull()?.let {
                MatchBase(it.state.rounded, MatchSource.LOCAL_RATED, "${settings.ratingSystem.label} · $pool")
            }
            MatchSource.CHESS_COM -> settings.chessComRatings?.forPool(variant, timeClass)?.let {
                MatchBase(it, MatchSource.CHESS_COM, "Chess.com · $pool")
            }
            MatchSource.LICHESS -> settings.lichessRatings?.forPool(variant, timeClass)?.let {
                MatchBase(it, MatchSource.LICHESS, "Lichess · $pool")
            }
        }
    }

    fun matchTarget(base: MatchBase, range: Int): Int = MatchYourElo.target(base.rating, range)

    /** Refreshes the cached site ratings for the linked accounts; returns an error message, if any. */
    suspend fun refreshRemoteRatings(context: Context): String? {
        val repository = PlayerSettingsRepository.from(context)
        val settings = repository.current()
        val http = UrlConnectionHttpGet(userAgent = "LumenChess (Android; ratings)")
        val errors = ArrayList<String>()
        val chessCom = settings.chessComUsername?.let { name ->
            runCatching { withContext(Dispatchers.IO) { RemoteRatingsClient.fetch(OnlineSite.CHESS_COM, name, http) } }
                .onFailure { errors += it.message ?: "Chess.com ratings unavailable" }.getOrNull()
        }
        val lichess = settings.lichessUsername?.let { name ->
            runCatching { withContext(Dispatchers.IO) { RemoteRatingsClient.fetch(OnlineSite.LICHESS, name, http) } }
                .onFailure { errors += it.message ?: "Lichess ratings unavailable" }.getOrNull()
        }
        repository.update { current ->
            current.copy(
                chessComRatings = chessCom ?: current.chessComRatings,
                lichessRatings = lichess ?: current.lichessRatings,
            )
        }
        return errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}

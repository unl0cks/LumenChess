package dev.lumenchess.insights

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lumenchess.analysis.insights.GameKind
import dev.lumenchess.analysis.insights.Insights
import dev.lumenchess.analysis.insights.InsightsFilter
import dev.lumenchess.analysis.insights.MoveQuality
import dev.lumenchess.analysis.insights.OpeningStat
import dev.lumenchess.analysis.insights.Overview
import dev.lumenchess.analysis.insights.PlayerGame
import dev.lumenchess.analysis.insights.Segment
import dev.lumenchess.analysis.insights.Trend
import dev.lumenchess.analysis.insights.TrendPoint
import dev.lumenchess.analysis.review.GamePhase
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.player.PlayerData
import dev.lumenchess.player.PlayerSettings
import dev.lumenchess.player.PlayerSettingsRepository
import dev.lumenchess.player.RatingsOverview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Games behind a number, for drill-down. */
data class InsightsDrill(val title: String, val games: List<PlayerGame>)

data class InsightsView(
    val games: List<PlayerGame>,
    val overview: Overview,
    val moveQuality: MoveQuality,
    val phases: Map<GamePhase, Double?>,
    val openings: List<OpeningStat>,
    val best: OpeningStat?,
    val worst: OpeningStat?,
    val timeClasses: List<Segment>,
    val colors: List<Segment>,
    val trend: Trend,
    val strength: List<TrendPoint>,
    val compare: Pair<Overview, Overview>,
)

data class InsightsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val filter: InsightsFilter = InsightsFilter(),
    /** True when the default "Human games" view had too little data and fell back to All. */
    val defaultedToAll: Boolean = false,
    val allGames: List<PlayerGame> = emptyList(),
    val view: InsightsView? = null,
    val ratings: RatingsOverview? = null,
    val settings: PlayerSettings = PlayerSettings(),
    val drill: InsightsDrill? = null,
) {
    val isEmpty: Boolean get() = !loading && allGames.none { it.userColor != null }
}

/** Read-only projection of the library, reviews and ratings; it never writes. */
class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = mutableStateOf(InsightsUiState())
    val uiState: State<InsightsUiState> = mutableState
    private var job: Job? = null
    private var userChoseFilter = false

    fun refresh(force: Boolean = false) {
        job?.cancel()
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val settings = PlayerSettingsRepository.from(getApplication()).current()
                val games = PlayerData.games(getApplication(), settings, force)
                val ratings = withContext(Dispatchers.Default) { PlayerData.ratings(games, settings.ratingSystem) }
                // Spec default: Human games when there are enough of them, otherwise All, labelled.
                var filter = mutableState.value.filter
                var defaulted = false
                if (!userChoseFilter) {
                    val human = games.count { it.kind == GameKind.HUMAN }
                    filter = if (human >= MIN_HUMAN_GAMES) InsightsFilter(kind = GameKind.HUMAN) else InsightsFilter()
                    defaulted = human < MIN_HUMAN_GAMES && games.isNotEmpty()
                }
                val view = withContext(Dispatchers.Default) { project(games, filter) }
                mutableState.value = mutableState.value.copy(
                    loading = false, allGames = games, filter = filter, view = view, ratings = ratings,
                    settings = settings, defaultedToAll = defaulted,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = "Could not read your games.")
            }
        }
    }

    fun setFilter(filter: InsightsFilter) {
        userChoseFilter = true
        val games = mutableState.value.allGames
        mutableState.value = mutableState.value.copy(filter = filter, defaultedToAll = false)
        viewModelScope.launch {
            val view = withContext(Dispatchers.Default) { project(games, filter) }
            mutableState.value = mutableState.value.copy(view = view)
        }
    }

    fun drill(title: String, games: List<PlayerGame>) {
        mutableState.value = mutableState.value.copy(drill = InsightsDrill(title, games))
    }

    fun drillErrors(classification: MoveClassification) {
        val games = mutableState.value.view?.games.orEmpty().filter { (it.review?.counts?.get(classification) ?: 0) > 0 }
        drill("${classification.label}s", games)
    }

    fun drillOpening(stat: OpeningStat) {
        val ids = stat.gameIds.toSet()
        drill(stat.name, mutableState.value.view?.games.orEmpty().filter { it.gameId in ids })
    }

    fun closeDrill() {
        mutableState.value = mutableState.value.copy(drill = null)
    }

    private fun project(games: List<PlayerGame>, filter: InsightsFilter): InsightsView {
        val now = System.currentTimeMillis()
        val selected = Insights.filter(games, filter, now)
        val openings = Insights.openings(selected)
        val (best, worst) = Insights.bestAndWorst(openings)
        return InsightsView(
            games = selected,
            overview = Insights.overview(selected),
            moveQuality = Insights.moveQuality(selected),
            phases = Insights.phases(selected),
            openings = openings,
            best = best,
            worst = worst,
            timeClasses = Insights.byTimeClass(selected),
            colors = Insights.byColor(selected),
            trend = Insights.trend(selected),
            strength = Insights.strengthSeries(selected),
            compare = Insights.comparePeriods(Insights.filter(games, filter.copy(days = null), now), 30, now),
        )
    }

    private companion object {
        const val MIN_HUMAN_GAMES = 10
    }
}

package dev.lumenchess.insights

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.lumenchess.data.persistence.GameLibraryRepository
import dev.lumenchess.data.persistence.LibraryCursor
import dev.lumenchess.data.persistence.LibraryFilter
import dev.lumenchess.data.persistence.LibraryQuery
import dev.lumenchess.data.persistence.LumenDatabaseFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InsightsUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val summary: InsightsSummary? = null,
)

/** Read-only projection of the local Library; it never writes and never touches Play or Arena. */
class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = mutableStateOf(InsightsUiState())
    val uiState: State<InsightsUiState> = mutableState
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        // Keep showing the last numbers while re-reading so the screen does not flash empty.
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) { readSummary() }
                mutableState.value = InsightsUiState(loading = false, summary = summary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = "Could not read your games.")
            }
        }
    }

    private suspend fun readSummary(): InsightsSummary {
        val database = LumenDatabaseFactory.open(getApplication<Application>().applicationContext)
        try {
            val library = GameLibraryRepository(database)
            val games = ArrayList<InsightGame>()
            var cursor: LibraryCursor? = null
            var pages = 0
            do {
                val page = library.page(LibraryQuery(LibraryFilter.LOCAL), cursor, limit = PAGE_SIZE)
                page.entries.mapNotNullTo(games) { it.toInsightGame() }
                cursor = page.nextCursor
                pages++
            } while (cursor != null && pages < MAX_PAGES)
            return InsightsCalculator.summarize(games)
        } finally {
            LumenDatabaseFactory.close(database)
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        /** Bounded read: the latest 1,000 local games are more than any summary needs. */
        const val MAX_PAGES = 10
    }
}

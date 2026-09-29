package dev.lumenchess.player

import android.content.Context
import dev.lumenchess.data.AppData
import dev.lumenchess.games.imports.GameImporter
import dev.lumenchess.games.imports.ImportCounts
import dev.lumenchess.games.imports.OnlineGameSources
import dev.lumenchess.games.imports.OnlineSite
import dev.lumenchess.games.imports.PgnImport
import dev.lumenchess.games.imports.UrlConnectionHttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncOutcome(val site: OnlineSite, val counts: ImportCounts?, val error: String?) {
    val summary: String
        get() = error ?: counts?.let {
            buildList {
                add(if (it.added == 1) "1 new game" else "${it.added} new games")
                if (it.existing > 0) add("${it.existing} already here")
                if (it.failed > 0) add("${it.failed} unreadable")
            }.joinToString(" · ")
        } ?: "Nothing to sync"
}

/**
 * Read-only, incremental import from the linked Chess.com and Lichess accounts into the one
 * canonical library. Only the public game exports are used; nothing is ever uploaded. The first
 * sync takes the newest [OnlineGameSources.MAX_GAMES]; later syncs ask only for newer games (with a
 * day of overlap, since the library skips games it already has).
 */
object AccountSync {
    private const val OVERLAP_MILLIS = 24 * 60 * 60 * 1_000L

    suspend fun syncAll(context: Context): List<SyncOutcome> {
        val settings = PlayerSettingsRepository.from(context).current()
        val outcomes = buildList {
            settings.chessComUsername?.let { add(sync(context, OnlineSite.CHESS_COM, it, settings.lastChessComSyncEpochMillis)) }
            settings.lichessUsername?.let { add(sync(context, OnlineSite.LICHESS, it, settings.lastLichessSyncEpochMillis)) }
        }
        if (outcomes.any { it.error == null }) {
            // Fresh site ratings too, while online.
            PlayerData.refreshRemoteRatings(context)
            PlayerData.invalidate()
        }
        return outcomes
    }

    suspend fun sync(context: Context, site: OnlineSite, username: String, lastSync: Long?): SyncOutcome {
        val started = System.currentTimeMillis()
        return try {
            val http = UrlConnectionHttpGet(userAgent = "LumenChess (Android; account sync)")
            val batch = withContext(Dispatchers.IO) {
                PgnImport.parse(
                    OnlineGameSources.fetchRecentPgn(site, username, http, sinceEpochMillis = lastSync?.minus(OVERLAP_MILLIS)),
                    started,
                )
            }
            val counts = GameImporter.importAll(AppData.games(context), batch)
            PlayerSettingsRepository.from(context).update { current ->
                when (site) {
                    OnlineSite.CHESS_COM -> current.copy(lastChessComSyncEpochMillis = started)
                    OnlineSite.LICHESS -> current.copy(lastLichessSyncEpochMillis = started)
                }
            }
            SyncOutcome(site, counts, null)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            SyncOutcome(site, null, error.message ?: "${site.label} sync failed")
        }
    }
}

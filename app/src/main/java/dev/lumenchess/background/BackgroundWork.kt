package dev.lumenchess.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.lumenchess.analysis.review.ReviewSettings
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.HeavyAnalysisRetentionPolicy
import dev.lumenchess.data.persistence.LibraryFilter
import dev.lumenchess.data.persistence.LibraryQuery
import dev.lumenchess.data.persistence.ReviewState
import dev.lumenchess.player.AccountSync
import dev.lumenchess.player.PlayerSettingsRepository
import dev.lumenchess.review.ReviewCoordinator
import dev.lumenchess.settings.StorageSettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * WorkManager orchestration for work that should survive leaving the app: account sync, Game
 * Review of finished games, and cache cleanup. Each unit is small and resumable (reviews checkpoint
 * every position), so a stopped worker loses nothing.
 */
object BackgroundWork {
    private const val SYNC = "lumen-account-sync"
    private const val SYNC_NOW = "lumen-account-sync-now"
    private const val REVIEW = "lumen-auto-review"
    private const val CLEANUP = "lumen-storage-cleanup"

    /** Re-applies the schedule from the current settings. Idempotent; call at start-up and on change. */
    suspend fun reschedule(context: Context) {
        val manager = WorkManager.getInstance(context)
        val settings = PlayerSettingsRepository.from(context).current()
        if (settings.autoSync && (settings.chessComUsername != null || settings.lichessUsername != null)) {
            manager.enqueueUniquePeriodicWork(
                SYNC,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AccountSyncWorker>(1, TimeUnit.DAYS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        } else {
            manager.cancelUniqueWork(SYNC)
        }
        manager.enqueueUniquePeriodicWork(
            CLEANUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<StorageCleanupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresDeviceIdle(true).build())
                .build(),
        )
    }

    fun syncNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SYNC_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AccountSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** Reviews finished, unreviewed games in the background (after a game, when auto-review is on). */
    fun reviewPending(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            REVIEW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutoReviewWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }
}

class AccountSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcomes = AccountSync.syncAll(applicationContext)
        // A site that is down is retried later by the periodic schedule, not in a tight loop.
        return if (outcomes.isNotEmpty() && outcomes.all { it.error != null } && runAttemptCount < 2) Result.retry() else Result.success()
    }
}

class AutoReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = PlayerSettingsRepository.from(applicationContext).current()
        if (!settings.autoReview) return Result.success()
        val reviewed = AppData.reviews(applicationContext).reviewsInState(ReviewState.COMPLETE).map { it.gameId.value }.toSet()
        // Newest finished local games first, a handful per run.
        val page = AppData.library(applicationContext).page(LibraryQuery(LibraryFilter.LOCAL), null, limit = 40)
        val pending = page.entries.filter { it.result != null && it.id.value !in reviewed }.take(MAX_PER_RUN).map { it.id.value }
        for (gameId in pending) {
            withContext(Dispatchers.Main) {
                ReviewCoordinator.request(applicationContext, gameId, ReviewSettings(settings.reviewPreset))
            }
            // Wait for that game's review to end (complete, failed or cancelled).
            withTimeoutOrNull(REVIEW_TIMEOUT_MILLIS) {
                ReviewCoordinator.progress.first { all -> all[gameId]?.active == false }
            } ?: return Result.retry()
            if (isStopped) return Result.retry()
        }
        return Result.success()
    }

    private companion object {
        const val MAX_PER_RUN = 5
        const val REVIEW_TIMEOUT_MILLIS = 15 * 60 * 1_000L
    }
}

class StorageCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val policy = StorageSettingsRepository.from(applicationContext).current().retentionPolicy(System.currentTimeMillis())
            ?: return Result.success()
        val retention = AppData.retention(applicationContext)
        // Batches keep each transaction short.
        repeat(MAX_BATCHES) {
            if (retention.prune(policy) == 0) return Result.success()
        }
        return Result.success()
    }

    private companion object {
        const val MAX_BATCHES = 50
    }
}

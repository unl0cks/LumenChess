package dev.lumenchess.settings

import android.content.Context
import dev.lumenchess.data.persistence.HeavyAnalysisRetentionPolicy

/**
 * Storage lifecycle for disposable data only. Engine lines saved with reviews may be compacted;
 * games, review results (classifications, accuracy) and favorites/protected games never are.
 */
data class StorageSettings(
    /** Engine lines older than this many days are removed; null keeps them forever. */
    val engineLineDays: Int? = 180,
    /** At most this many cached engine evaluations are kept (newest first); null means no cap. */
    val maxEngineLines: Int? = 60_000,
) {
    fun retentionPolicy(nowEpochMillis: Long): HeavyAnalysisRetentionPolicy? {
        if (engineLineDays == null && maxEngineLines == null) return null
        return HeavyAnalysisRetentionPolicy(
            olderThanEpochMillis = engineLineDays?.let { nowEpochMillis - it * DAY_MILLIS },
            maxRetainedCount = maxEngineLines,
        )
    }

    companion object {
        const val DAY_MILLIS = 86_400_000L
        val DAY_CHOICES: List<Int?> = listOf(30, 90, 180, 365, null)
        val SIZE_CHOICES: List<Int?> = listOf(10_000, 60_000, 250_000, null)
    }
}

class StorageSettingsRepository private constructor(private val context: Context) {
    private val preferences get() = context.getSharedPreferences("lumen_storage", Context.MODE_PRIVATE)

    fun current(): StorageSettings = StorageSettings(
        engineLineDays = preferences.getInt("engine_line_days", 180).takeIf { it > 0 },
        maxEngineLines = preferences.getInt("max_engine_lines", 60_000).takeIf { it > 0 },
    )

    fun update(settings: StorageSettings) {
        preferences.edit()
            .putInt("engine_line_days", settings.engineLineDays ?: 0)
            .putInt("max_engine_lines", settings.maxEngineLines ?: 0)
            .apply()
    }

    companion object {
        fun from(context: Context) = StorageSettingsRepository(context.applicationContext)
    }
}

package dev.lumenchess.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.lumenchess.analysis.rating.RatingSystem
import dev.lumenchess.analysis.rating.TimeClass
import dev.lumenchess.analysis.review.ReviewPreset
import dev.lumenchess.core.chess.Variant
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where Match Your Elo reads the user's strength from. */
enum class MatchSource(val label: String) {
    LOCAL_PERFORMANCE("Performance Estimate"),
    LOCAL_RATED("Local rated"),
    CHESS_COM("Chess.com"),
    LICHESS("Lichess"),
}

/** A site's ratings as last fetched; kept so Match Your Elo works offline. */
data class RemoteRatings(
    val bullet: Int? = null,
    val blitz: Int? = null,
    val rapid: Int? = null,
    val chess960: Int? = null,
    val fetchedAtEpochMillis: Long = 0L,
) {
    fun forPool(variant: Variant, timeClass: TimeClass): Int? =
        if (variant == Variant.CHESS960) chess960 else when (timeClass) {
            TimeClass.BULLET -> bullet
            TimeClass.BLITZ -> blitz
            TimeClass.RAPID -> rapid
        }

    fun encode(): String = listOf(bullet, blitz, rapid, chess960).joinToString(",") { it?.toString().orEmpty() } + ";$fetchedAtEpochMillis"

    companion object {
        fun decode(text: String?): RemoteRatings? {
            if (text.isNullOrBlank()) return null
            val values = text.substringBefore(';').split(',').map { it.toIntOrNull() }
            if (values.size != 4) return null
            return RemoteRatings(values[0], values[1], values[2], values[3], text.substringAfter(';', "0").toLongOrNull() ?: 0L)
        }
    }
}

data class PlayerSettings(
    val ratingSystem: RatingSystem = RatingSystem.GLICKO_2,
    val matchSource: MatchSource = MatchSource.LOCAL_PERFORMANCE,
    /** ±Elo spread for Match Your Elo: 50 tight, 100 normal (default), 200 wide, or custom. */
    val matchRange: Int = 100,
    val chessComUsername: String? = null,
    val lichessUsername: String? = null,
    val chessComRatings: RemoteRatings? = null,
    val lichessRatings: RemoteRatings? = null,
    /** Daily background import of new games from the linked accounts. */
    val autoSync: Boolean = false,
    val lastChessComSyncEpochMillis: Long? = null,
    val lastLichessSyncEpochMillis: Long? = null,
    /** Review finished Play games in the background. */
    val autoReview: Boolean = false,
    val reviewPreset: ReviewPreset = ReviewPreset.BALANCED,
    /** Lichess OAuth token (PKCE, read-only scopes), used for Lichess requests when present. */
    val lichessToken: String? = null,
) {
    /** Lowercased names that identify the user in imported games. */
    val identities: Set<String>
        get() = setOfNotNull(chessComUsername?.lowercase(), lichessUsername?.lowercase())
}

object PlayerSettingsCodec {
    fun decode(raw: Map<String, String>): PlayerSettings = PlayerSettings(
        ratingSystem = raw["rating_system"]?.let { name -> RatingSystem.entries.firstOrNull { it.name == name } } ?: RatingSystem.GLICKO_2,
        matchSource = raw["match_source"]?.let { name -> MatchSource.entries.firstOrNull { it.name == name } } ?: MatchSource.LOCAL_PERFORMANCE,
        matchRange = raw["match_range"]?.toIntOrNull()?.coerceIn(0, 600) ?: 100,
        chessComUsername = raw["chess_com_user"]?.takeIf { it.isNotBlank() },
        lichessUsername = raw["lichess_user"]?.takeIf { it.isNotBlank() },
        chessComRatings = RemoteRatings.decode(raw["chess_com_ratings"]),
        lichessRatings = RemoteRatings.decode(raw["lichess_ratings"]),
        autoSync = raw["auto_sync"] == "true",
        lastChessComSyncEpochMillis = raw["chess_com_synced"]?.toLongOrNull(),
        lastLichessSyncEpochMillis = raw["lichess_synced"]?.toLongOrNull(),
        autoReview = raw["auto_review"] == "true",
        reviewPreset = raw["review_preset"]?.let { name -> ReviewPreset.entries.firstOrNull { it.name == name } } ?: ReviewPreset.BALANCED,
        lichessToken = raw["lichess_token"]?.takeIf { it.isNotBlank() },
    )

    fun encode(settings: PlayerSettings): Map<String, String> = buildMap {
        put("rating_system", settings.ratingSystem.name)
        put("match_source", settings.matchSource.name)
        put("match_range", settings.matchRange.toString())
        settings.chessComUsername?.let { put("chess_com_user", it) }
        settings.lichessUsername?.let { put("lichess_user", it) }
        settings.chessComRatings?.let { put("chess_com_ratings", it.encode()) }
        settings.lichessRatings?.let { put("lichess_ratings", it.encode()) }
        put("auto_sync", settings.autoSync.toString())
        settings.lastChessComSyncEpochMillis?.let { put("chess_com_synced", it.toString()) }
        settings.lastLichessSyncEpochMillis?.let { put("lichess_synced", it.toString()) }
        put("auto_review", settings.autoReview.toString())
        put("review_preset", settings.reviewPreset.name)
        settings.lichessToken?.let { put("lichess_token", it) }
    }

    val KEYS = listOf(
        "rating_system", "match_source", "match_range", "chess_com_user", "lichess_user", "chess_com_ratings",
        "lichess_ratings", "auto_sync", "chess_com_synced", "lichess_synced", "auto_review", "review_preset", "lichess_token",
    )
}

private val Context.playerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "player_settings")

class PlayerSettingsRepository private constructor(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<PlayerSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> PlayerSettingsCodec.decode(preferences.raw()) }

    suspend fun current(): PlayerSettings = settings.first()

    suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
        dataStore.edit { preferences ->
            val next = transform(PlayerSettingsCodec.decode(preferences.raw()))
            PlayerSettingsCodec.KEYS.forEach { preferences.remove(stringPreferencesKey(it)) }
            PlayerSettingsCodec.encode(next).forEach { (name, value) -> preferences[stringPreferencesKey(name)] = value }
        }
    }

    private fun Preferences.raw(): Map<String, String> = buildMap {
        PlayerSettingsCodec.KEYS.forEach { name -> this@raw[stringPreferencesKey(name)]?.let { put(name, it) } }
    }

    companion object {
        @Volatile private var instance: PlayerSettingsRepository? = null

        fun from(context: Context): PlayerSettingsRepository = instance ?: synchronized(this) {
            instance ?: PlayerSettingsRepository(context.applicationContext.playerSettingsDataStore).also { instance = it }
        }
    }
}

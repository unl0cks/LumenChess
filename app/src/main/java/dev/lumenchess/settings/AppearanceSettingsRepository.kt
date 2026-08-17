package dev.lumenchess.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.appearanceSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "appearance_settings",
)

interface AppearanceSettingsRepository {
    val settings: Flow<AppearanceSettings>
    suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings)
}

class DataStoreAppearanceSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : AppearanceSettingsRepository {
    override val settings: Flow<AppearanceSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> AppearanceSettingsCodec.decode(preferences.toRawMap()) }

    override suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        dataStore.edit { preferences ->
            val next = transform(AppearanceSettingsCodec.decode(preferences.toRawMap()))
            val encoded = AppearanceSettingsCodec.encode(next)
            ALL_KEYS.forEach(preferences::remove)
            encoded.forEach { (name, value) -> preferences[key(name)] = value }
        }
    }

    companion object {
        fun from(context: Context): AppearanceSettingsRepository =
            DataStoreAppearanceSettingsRepository(context.applicationContext.appearanceSettingsDataStore)
    }
}

private fun Preferences.toRawMap(): Map<String, String> = buildMap {
    ALL_KEY_NAMES.forEach { name -> this@toRawMap[key(name)]?.let { put(name, it) } }
}

private fun key(name: String): Preferences.Key<String> = stringPreferencesKey(name)

private val ALL_KEY_NAMES = listOf(
    AppearanceSettingsCodec.APPEARANCE,
    AppearanceSettingsCodec.ACCENT,
    AppearanceSettingsCodec.BOARD_THEME,
    AppearanceSettingsCodec.PIECE_SET,
    AppearanceSettingsCodec.BACKGROUND,
    AppearanceSettingsCodec.PRESET,
    AppearanceSettingsCodec.CUSTOM_LIGHT,
    AppearanceSettingsCodec.CUSTOM_DARK,
    AppearanceSettingsCodec.FEEDBACK_SOUNDS_ENABLED,
    AppearanceSettingsCodec.FEEDBACK_HAPTICS_ENABLED,
    AppearanceSettingsCodec.FEEDBACK_SOUND_EVENTS,
    AppearanceSettingsCodec.FEEDBACK_HAPTIC_EVENTS,
    AppearanceSettingsCodec.SOUND_PACK,
)
private val ALL_KEYS = ALL_KEY_NAMES.map(::key)

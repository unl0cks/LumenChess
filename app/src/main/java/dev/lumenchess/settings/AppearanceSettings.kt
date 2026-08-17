package dev.lumenchess.settings

import dev.lumenchess.feedback.FeedbackSettings
import dev.lumenchess.feedback.GameFeedbackEvent
import dev.lumenchess.feedback.SoundSourceResolver
import java.util.Locale

enum class AppAppearance {
    SYSTEM,
    DARK,
    OLED_DARK,
    LIGHT,
}

data class AppearanceSettings(
    val appearance: AppAppearance = AppAppearance.SYSTEM,
    val accentArgb: Long = DEFAULT_ACCENT_ARGB,
    val boardThemeId: String = DEFAULT_BOARD_THEME_ID,
    val pieceSetId: String = DEFAULT_PIECE_SET_ID,
    val backgroundId: String = DEFAULT_BACKGROUND_ID,
    val presetId: String? = DEFAULT_PRESET_ID,
    val customLightSquareArgb: Long? = null,
    val customDarkSquareArgb: Long? = null,
    val feedbackSoundsEnabled: Boolean = true,
    val feedbackHapticsEnabled: Boolean = true,
    val feedbackSoundEvents: Set<GameFeedbackEvent> = GameFeedbackEvent.all,
    val feedbackHapticEvents: Set<GameFeedbackEvent> = GameFeedbackEvent.all,
    val soundPackId: String = DEFAULT_SOUND_PACK_ID,
) {
    fun withBoardTheme(id: String): AppearanceSettings = copy(boardThemeId = id, presetId = null)
    fun withPieceSet(id: String): AppearanceSettings = copy(pieceSetId = id, presetId = null)
    fun withBackground(id: String): AppearanceSettings = copy(backgroundId = id, presetId = null)

    fun toFeedbackSettings(): FeedbackSettings = FeedbackSettings(
        soundsEnabled = feedbackSoundsEnabled,
        hapticsEnabled = feedbackHapticsEnabled,
        soundEvents = feedbackSoundEvents,
        hapticEvents = feedbackHapticEvents,
    )

    companion object {
        const val DEFAULT_ACCENT_ARGB: Long = 0xFF4C8DFFL
        const val DEFAULT_BOARD_THEME_ID = "lumen-blue"
        const val DEFAULT_PIECE_SET_ID = "lumen-vector"
        const val DEFAULT_BACKGROUND_ID = "lumen-night"
        const val DEFAULT_PRESET_ID = "lumen-default"
        const val DEFAULT_SOUND_PACK_ID = SoundSourceResolver.BUILT_IN_PACK_ID
    }
}

internal object AppearanceSettingsCodec {
    const val APPEARANCE = "appearance"
    const val ACCENT = "accent_argb"
    const val BOARD_THEME = "board_theme"
    const val PIECE_SET = "piece_set"
    const val BACKGROUND = "background"
    const val PRESET = "preset"
    const val CUSTOM_LIGHT = "custom_light_argb"
    const val CUSTOM_DARK = "custom_dark_argb"
    const val FEEDBACK_SOUNDS_ENABLED = "feedback_sounds_enabled"
    const val FEEDBACK_HAPTICS_ENABLED = "feedback_haptics_enabled"
    const val FEEDBACK_SOUND_EVENTS = "feedback_sound_events"
    const val FEEDBACK_HAPTIC_EVENTS = "feedback_haptic_events"
    const val SOUND_PACK = "feedback_sound_pack"
    private const val NO_PRESET = "__none__"
    private const val NO_EVENTS = "__none__"
    private val stableId = Regex("[a-z0-9][a-z0-9_-]{0,127}")

    fun encode(settings: AppearanceSettings): Map<String, String> = buildMap {
        put(APPEARANCE, settings.appearance.name)
        put(ACCENT, encodeArgb(settings.accentArgb))
        put(BOARD_THEME, settings.boardThemeId)
        put(PIECE_SET, settings.pieceSetId)
        put(BACKGROUND, settings.backgroundId)
        put(PRESET, settings.presetId ?: NO_PRESET)
        settings.customLightSquareArgb?.let { put(CUSTOM_LIGHT, encodeArgb(it)) }
        settings.customDarkSquareArgb?.let { put(CUSTOM_DARK, encodeArgb(it)) }
        put(FEEDBACK_SOUNDS_ENABLED, settings.feedbackSoundsEnabled.toString())
        put(FEEDBACK_HAPTICS_ENABLED, settings.feedbackHapticsEnabled.toString())
        put(FEEDBACK_SOUND_EVENTS, encodeEvents(settings.feedbackSoundEvents))
        put(FEEDBACK_HAPTIC_EVENTS, encodeEvents(settings.feedbackHapticEvents))
        put(SOUND_PACK, settings.soundPackId)
    }

    fun decode(raw: Map<String, String>): AppearanceSettings {
        val defaults = AppearanceSettings()
        return AppearanceSettings(
            appearance = raw[APPEARANCE]
                ?.let { stored -> AppAppearance.entries.firstOrNull { it.name == stored } }
                ?: defaults.appearance,
            accentArgb = raw[ACCENT]?.let(::decodeArgb) ?: defaults.accentArgb,
            boardThemeId = raw[BOARD_THEME].validStableIdOr(defaults.boardThemeId),
            pieceSetId = raw[PIECE_SET].validStableIdOr(defaults.pieceSetId),
            backgroundId = raw[BACKGROUND].validStableIdOr(defaults.backgroundId),
            presetId = when (val stored = raw[PRESET]) {
                null -> defaults.presetId
                NO_PRESET -> null
                else -> stored.takeIf(stableId::matches) ?: defaults.presetId
            },
            customLightSquareArgb = raw[CUSTOM_LIGHT]?.let(::decodeArgb),
            customDarkSquareArgb = raw[CUSTOM_DARK]?.let(::decodeArgb),
            feedbackSoundsEnabled = decodeBoolean(raw[FEEDBACK_SOUNDS_ENABLED], defaults.feedbackSoundsEnabled),
            feedbackHapticsEnabled = decodeBoolean(raw[FEEDBACK_HAPTICS_ENABLED], defaults.feedbackHapticsEnabled),
            feedbackSoundEvents = decodeEvents(raw[FEEDBACK_SOUND_EVENTS], defaults.feedbackSoundEvents),
            feedbackHapticEvents = decodeEvents(raw[FEEDBACK_HAPTIC_EVENTS], defaults.feedbackHapticEvents),
            soundPackId = raw[SOUND_PACK].validStableIdOr(defaults.soundPackId),
        )
    }

    private fun String?.validStableIdOr(fallback: String): String =
        this?.takeIf(stableId::matches) ?: fallback

    private fun encodeArgb(value: Long): String =
        (value and 0xFFFF_FFFFL).toString(16).uppercase(Locale.ROOT).padStart(8, '0')

    private fun decodeArgb(value: String): Long? {
        if (value.length != 8 || value.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
        return value.toLongOrNull(16)?.takeIf { it in 0L..0xFFFF_FFFFL }
    }

    private fun decodeBoolean(value: String?, fallback: Boolean): Boolean = when (value) {
        "true" -> true
        "false" -> false
        else -> fallback
    }

    private fun encodeEvents(events: Set<GameFeedbackEvent>): String {
        if (events.isEmpty()) return NO_EVENTS
        return GameFeedbackEvent.all
            .filter { it in events }
            .joinToString(",", transform = ::eventToken)
    }

    private fun decodeEvents(value: String?, fallback: Set<GameFeedbackEvent>): Set<GameFeedbackEvent> {
        if (value == null) return fallback
        if (value == NO_EVENTS) return emptySet()
        val tokens = value.split(',').filter(String::isNotBlank)
        if (tokens.isEmpty()) return fallback
        val decoded = tokens.map { token -> eventFromToken(token) ?: return fallback }
        return linkedSetOf<GameFeedbackEvent>().apply { addAll(decoded) }
    }

    private fun eventToken(event: GameFeedbackEvent): String = when (event) {
        GameFeedbackEvent.Move -> "MOVE"
        GameFeedbackEvent.Capture -> "CAPTURE"
        GameFeedbackEvent.Check -> "CHECK"
        GameFeedbackEvent.Castle -> "CASTLE"
        GameFeedbackEvent.Promotion -> "PROMOTION"
        GameFeedbackEvent.GameStart -> "GAME_START"
        GameFeedbackEvent.GameEnd -> "GAME_END"
    }

    private fun eventFromToken(token: String): GameFeedbackEvent? = when (token) {
        "MOVE" -> GameFeedbackEvent.Move
        "CAPTURE" -> GameFeedbackEvent.Capture
        "CHECK" -> GameFeedbackEvent.Check
        "CASTLE" -> GameFeedbackEvent.Castle
        "PROMOTION" -> GameFeedbackEvent.Promotion
        "GAME_START" -> GameFeedbackEvent.GameStart
        "GAME_END" -> GameFeedbackEvent.GameEnd
        else -> null
    }
}

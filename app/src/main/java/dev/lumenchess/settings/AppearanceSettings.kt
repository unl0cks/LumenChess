package dev.lumenchess.settings

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
) {
    fun withBoardTheme(id: String): AppearanceSettings = copy(boardThemeId = id, presetId = null)
    fun withPieceSet(id: String): AppearanceSettings = copy(pieceSetId = id, presetId = null)
    fun withBackground(id: String): AppearanceSettings = copy(backgroundId = id, presetId = null)

    companion object {
        const val DEFAULT_ACCENT_ARGB: Long = 0xFF4C8DFFL
        const val DEFAULT_BOARD_THEME_ID = "lumen-blue"
        const val DEFAULT_PIECE_SET_ID = "lumen-vector"
        const val DEFAULT_BACKGROUND_ID = "lumen-night"
        const val DEFAULT_PRESET_ID = "lumen-default"
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
    private const val NO_PRESET = "__none__"
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
}

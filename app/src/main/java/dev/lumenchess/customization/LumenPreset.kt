package dev.lumenchess.customization

import dev.lumenchess.settings.AppearanceSettings

data class LumenPreset(
    val id: String,
    val displayName: String,
    val boardThemeId: String,
    val pieceSetId: String,
    val backgroundId: String,
) {
    fun applyTo(settings: AppearanceSettings): AppearanceSettings = settings.copy(
        boardThemeId = boardThemeId,
        pieceSetId = pieceSetId,
        backgroundId = backgroundId,
        presetId = id,
        customLightSquareArgb = null,
        customDarkSquareArgb = null,
    )
}

object LumenPresetCatalog {
    val Lumen = LumenPreset(
        id = AppearanceSettings.DEFAULT_PRESET_ID,
        displayName = "Lumen",
        boardThemeId = AppearanceSettings.DEFAULT_BOARD_THEME_ID,
        pieceSetId = AppearanceSettings.DEFAULT_PIECE_SET_ID,
        backgroundId = AppearanceSettings.DEFAULT_BACKGROUND_ID,
    )
    val Midnight = LumenPreset(
        id = "midnight",
        displayName = "Midnight",
        boardThemeId = "midnight-oled",
        pieceSetId = "lumen-outline",
        backgroundId = "void",
    )
    val Graphite = LumenPreset(
        id = "graphite-focus",
        displayName = "Graphite Focus",
        boardThemeId = "graphite",
        pieceSetId = AppearanceSettings.DEFAULT_PIECE_SET_ID,
        backgroundId = "graphite-haze",
    )

    val builtIns: List<LumenPreset> = listOf(Lumen, Midnight, Graphite)
    fun definition(id: String?): LumenPreset? = builtIns.firstOrNull { it.id == id }
}

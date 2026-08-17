package dev.lumenchess.customization

import androidx.compose.ui.graphics.Color
import dev.lumenchess.settings.AppearanceSettings

data class BackgroundDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val darkTop: Color,
    val darkBottom: Color,
    val lightTop: Color,
    val lightBottom: Color,
)

object BackgroundCatalog {
    val LumenNight = BackgroundDefinition(
        id = AppearanceSettings.DEFAULT_BACKGROUND_ID,
        displayName = "Lumen Night",
        description = "Neutral graphite with a restrained blue-steel lift.",
        darkTop = Color(0xFF151718),
        darkBottom = Color(0xFF0F1112),
        lightTop = Color(0xFFE8ECEE),
        lightBottom = Color(0xFFF1F3F3),
    )

    val Void = BackgroundDefinition(
        id = "void",
        displayName = "Void",
        description = "True-black OLED field with just enough panel separation.",
        darkTop = Color(0xFF050606),
        darkBottom = Color.Black,
        lightTop = Color(0xFFE5E9EA),
        lightBottom = Color(0xFFF2F4F4),
    )

    val GraphiteHaze = BackgroundDefinition(
        id = "graphite-haze",
        displayName = "Graphite Haze",
        description = "Cool charcoal layers that keep attention on the board.",
        darkTop = Color(0xFF1A1D1F),
        darkBottom = Color(0xFF101213),
        lightTop = Color(0xFFE2E6E7),
        lightBottom = Color(0xFFF1F3F3),
    )

    val builtIns: List<BackgroundDefinition> = listOf(LumenNight, Void, GraphiteHaze)
    val all: List<BackgroundDefinition> get() = builtIns

    fun definition(id: String): BackgroundDefinition = builtIns.firstOrNull { it.id == id } ?: LumenNight
}

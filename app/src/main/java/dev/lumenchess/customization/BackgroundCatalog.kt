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
        description = "Reference-sampled neutral graphite with a restrained steel lift.",
        darkTop = Color(0xFF111213),
        darkBottom = Color(0xFF0D0E0F),
        lightTop = Color(0xFFE9EDEE),
        lightBottom = Color(0xFFF2F4F4),
    )

    val Void = BackgroundDefinition(
        id = "void",
        displayName = "Void",
        description = "True-black OLED field with just enough panel separation.",
        darkTop = Color(0xFF030303),
        darkBottom = Color.Black,
        lightTop = Color(0xFFE5E9EA),
        lightBottom = Color(0xFFF2F4F4),
    )

    val GraphiteHaze = BackgroundDefinition(
        id = "graphite-haze",
        displayName = "Graphite Haze",
        description = "Layered charcoal that keeps attention on the board.",
        darkTop = Color(0xFF181A1B),
        darkBottom = Color(0xFF0E1011),
        lightTop = Color(0xFFE2E6E7),
        lightBottom = Color(0xFFF1F3F3),
    )

    val builtIns: List<BackgroundDefinition> = listOf(LumenNight, Void, GraphiteHaze)
    val all: List<BackgroundDefinition> get() = builtIns

    fun definition(id: String): BackgroundDefinition = builtIns.firstOrNull { it.id == id } ?: LumenNight
}

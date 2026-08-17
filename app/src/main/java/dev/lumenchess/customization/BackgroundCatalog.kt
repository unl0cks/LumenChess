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
        description = "Deep navy layers with a restrained blue lift behind the board.",
        darkTop = Color(0xFF0D1420),
        darkBottom = Color(0xFF090D14),
        lightTop = Color(0xFFE8EFF8),
        lightBottom = Color(0xFFF4F7FB),
    )

    val Void = BackgroundDefinition(
        id = "void",
        displayName = "Void",
        description = "Minimal black field for OLED and distraction-free play.",
        darkTop = Color(0xFF020305),
        darkBottom = Color.Black,
        lightTop = Color(0xFFE5E8EC),
        lightBottom = Color(0xFFF3F4F6),
    )

    val GraphiteHaze = BackgroundDefinition(
        id = "graphite-haze",
        displayName = "Graphite Haze",
        description = "Neutral charcoal layers that keep attention on the board.",
        darkTop = Color(0xFF171B21),
        darkBottom = Color(0xFF0E1116),
        lightTop = Color(0xFFE4E7EB),
        lightBottom = Color(0xFFF3F4F6),
    )

    val builtIns: List<BackgroundDefinition> = listOf(LumenNight, Void, GraphiteHaze)

    fun definition(id: String): BackgroundDefinition = builtIns.firstOrNull { it.id == id } ?: LumenNight
}

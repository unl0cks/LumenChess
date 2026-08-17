package dev.lumenchess.customization

import androidx.compose.ui.graphics.Color

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
    const val LUMEN_NIGHT = "lumen-night"
    const val ENGINE_MESH = "engine-mesh"
    const val WALNUT_STUDY = "walnut-study"
    const val PURE_GRAPHITE = "pure-graphite"

    val builtIns: List<BackgroundDefinition> = listOf(
        BackgroundDefinition(
            id = LUMEN_NIGHT,
            displayName = "Lumen Night",
            description = "Neutral graphite with a restrained lifted edge.",
            darkTop = Color(0xFF151718),
            darkBottom = Color(0xFF0F1112),
            lightTop = Color(0xFFE8ECEE),
            lightBottom = Color(0xFFF1F3F3),
        ),
        BackgroundDefinition(
            id = ENGINE_MESH,
            displayName = "Engine Mesh",
            description = "Cool technical charcoal with a faint blue-steel lift.",
            darkTop = Color(0xFF151B1D),
            darkBottom = Color(0xFF0E1112),
            lightTop = Color(0xFFE2E9EB),
            lightBottom = Color(0xFFF2F5F5),
        ),
        BackgroundDefinition(
            id = WALNUT_STUDY,
            displayName = "Walnut Study",
            description = "Warm brown-black study-room tones.",
            darkTop = Color(0xFF211C19),
            darkBottom = Color(0xFF100E0D),
            lightTop = Color(0xFFF2E9DF),
            lightBottom = Color(0xFFFAF6F1),
        ),
        BackgroundDefinition(
            id = PURE_GRAPHITE,
            displayName = "Pure Graphite",
            description = "Flat near-black graphite for minimal distraction.",
            darkTop = Color(0xFF090A0A),
            darkBottom = Color(0xFF050606),
            lightTop = Color(0xFFE8EAEB),
            lightBottom = Color(0xFFF5F6F6),
        ),
    )
    val all: List<BackgroundDefinition> get() = builtIns

    fun definition(id: String): BackgroundDefinition =
        builtIns.firstOrNull { it.id == id } ?: builtIns.first()
}

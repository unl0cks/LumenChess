package dev.lumenchess.customization

import androidx.compose.ui.graphics.Color
import dev.lumenchess.board.ChessboardPalette
import dev.lumenchess.settings.AppearanceSettings

data class BoardThemeDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val palette: ChessboardPalette,
)

object BoardThemeCatalog {
    val LumenBlue = BoardThemeDefinition(
        id = AppearanceSettings.DEFAULT_BOARD_THEME_ID,
        displayName = "Lumen Blue",
        description = "Cool slate squares with restrained electric-blue interaction accents.",
        palette = ChessboardPalette.default(),
    )

    val MidnightOled = BoardThemeDefinition(
        id = "midnight-oled",
        displayName = "Midnight OLED",
        description = "Near-black navy board tuned for OLED surfaces without losing square separation.",
        palette = ChessboardPalette(
            lightSquare = Color(0xFF34465A), darkSquare = Color(0xFF101A27),
            whitePiece = Color(0xFFF6F8FC), blackPiece = Color(0xFF05080C),
            selected = Color(0xAA4D8DFF), legalMove = Color(0xAA65D6A5), legalCapture = Color(0xAAF2A15B),
            lastMove = Color(0x8AFFD166), check = Color(0xAAFF6677), premove = Color(0xAA7C82FF),
            extraHighlight = Color(0x8A78C6FF), primaryArrow = Color(0xDD5C96FF),
            secondaryArrow = Color(0xDD65D6A5), warningArrow = Color(0xDDEF6674),
        ),
    )

    val Graphite = BoardThemeDefinition(
        id = "graphite",
        displayName = "Graphite",
        description = "Neutral stone board for pieces and highlights to carry the visual hierarchy.",
        palette = ChessboardPalette(
            lightSquare = Color(0xFFC8CED5), darkSquare = Color(0xFF626B76),
            whitePiece = Color(0xFFF9FAFC), blackPiece = Color(0xFF161B21),
            selected = Color(0x994D8DFF), legalMove = Color(0x9955B987), legalCapture = Color(0x99DE8A47),
            lastMove = Color(0x80E9BE55), check = Color(0x99D95562), premove = Color(0x996D73E8),
            extraHighlight = Color(0x8072B4EB), primaryArrow = Color(0xCC367BEA),
            secondaryArrow = Color(0xCC4EA77B), warningArrow = Color(0xCCD5535E),
        ),
    )

    val builtIns: List<BoardThemeDefinition> = listOf(LumenBlue, MidnightOled, Graphite)

    fun definition(id: String): BoardThemeDefinition = builtIns.firstOrNull { it.id == id } ?: LumenBlue

    fun palette(settings: AppearanceSettings): ChessboardPalette {
        val base = definition(settings.boardThemeId).palette
        return base.copy(
            lightSquare = settings.customLightSquareArgb?.let { Color(it.toInt()) } ?: base.lightSquare,
            darkSquare = settings.customDarkSquareArgb?.let { Color(it.toInt()) } ?: base.darkSquare,
        )
    }
}

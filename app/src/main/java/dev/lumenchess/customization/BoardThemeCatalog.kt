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
        description = "Warm ivory and muted steel-blue squares from the Lumen reference.",
        palette = ChessboardPalette.default(),
    )

    val MidnightOled = BoardThemeDefinition(
        id = "midnight-oled",
        displayName = "Midnight OLED",
        description = "Near-black graphite tuned for OLED without losing square separation.",
        palette = ChessboardPalette(
            lightSquare = Color(0xFF394449), darkSquare = Color(0xFF121719),
            whitePiece = Color(0xFFF0EBDD), blackPiece = Color(0xFF07090A),
            selected = Color(0x8A6E9CAA), legalMove = Color(0x806B9E86), legalCapture = Color(0x909D7E55),
            lastMove = Color(0x70B7AA59), check = Color(0x9AD65D62), premove = Color(0x8065778D),
            extraHighlight = Color(0x70628EA0), primaryArrow = Color(0xCC5C91A4),
            secondaryArrow = Color(0xCC649A83), warningArrow = Color(0xCCD46C6C),
        ),
    )

    val Graphite = BoardThemeDefinition(
        id = "graphite",
        displayName = "Graphite",
        description = "Neutral stone squares with the same restrained Lumen highlight language.",
        palette = ChessboardPalette(
            lightSquare = Color(0xFFD7D5CB), darkSquare = Color(0xFF697174),
            whitePiece = Color(0xFFF0EBDD), blackPiece = Color(0xFF202224),
            selected = Color(0x88739CAB), legalMove = Color(0x806A9A83), legalCapture = Color(0x909A7B55),
            lastMove = Color(0x70BEB45C), check = Color(0x99C95D63), premove = Color(0x806A7787),
            extraHighlight = Color(0x70628FA0), primaryArrow = Color(0xCC4F879B),
            secondaryArrow = Color(0xCC649A83), warningArrow = Color(0xCCD46C6C),
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

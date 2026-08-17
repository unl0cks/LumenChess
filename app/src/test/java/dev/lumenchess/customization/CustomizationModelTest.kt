package dev.lumenchess.customization

import androidx.compose.ui.graphics.Color
import dev.lumenchess.settings.AppAppearance
import dev.lumenchess.settings.AppearanceSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CustomizationModelTest {
    @Test
    fun `preset composes board pieces and background without owning app appearance`() {
        val before = AppearanceSettings(appearance = AppAppearance.LIGHT, accentArgb = 0xFF245FCCL)

        val applied = LumenPresetCatalog.Midnight.applyTo(before)

        assertEquals(AppAppearance.LIGHT, applied.appearance)
        assertEquals(0xFF245FCCL, applied.accentArgb)
        assertEquals("midnight-oled", applied.boardThemeId)
        assertEquals("lumen-outline", applied.pieceSetId)
        assertEquals("void", applied.backgroundId)
        assertEquals("midnight", applied.presetId)
    }

    @Test
    fun `individual component override releases preset without resetting siblings`() {
        val preset = LumenPresetCatalog.Midnight.applyTo(AppearanceSettings())

        val overridden = preset.withBoardTheme("graphite")

        assertNull(overridden.presetId)
        assertEquals("graphite", overridden.boardThemeId)
        assertEquals("lumen-outline", overridden.pieceSetId)
        assertEquals("void", overridden.backgroundId)
    }

    @Test
    fun `board catalog falls back and applies deterministic custom argb overrides`() {
        val settings = AppearanceSettings(
            boardThemeId = "missing-theme",
            customLightSquareArgb = 0xFF123456L,
            customDarkSquareArgb = 0xFF010203L,
        )

        val palette = BoardThemeCatalog.palette(settings)

        assertEquals(Color(0xFF123456), palette.lightSquare)
        assertEquals(Color(0xFF010203), palette.darkSquare)
        assertEquals(BoardThemeCatalog.LumenBlue.palette.whitePiece, palette.whitePiece)
    }
}

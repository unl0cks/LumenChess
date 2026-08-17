package dev.lumenchess.settings

import dev.lumenchess.feedback.GameFeedbackEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppearanceSettingsCodecTest {
    @Test
    fun `defaults round trip without coupling accent to board`() {
        val defaults = AppearanceSettings()

        val decoded = AppearanceSettingsCodec.decode(AppearanceSettingsCodec.encode(defaults))

        assertEquals(defaults, decoded)
        assertEquals(0xFF4F879BL, decoded.accentArgb)
        assertEquals("lumen-blue", decoded.boardThemeId)
    }

    @Test
    fun `legacy royal-blue default migrates to P5 steel-blue default`() {
        val decoded = AppearanceSettingsCodec.decode(
            mapOf(AppearanceSettingsCodec.ACCENT to "FF4C8DFF"),
        )

        assertEquals(AppearanceSettings.DEFAULT_ACCENT_ARGB, decoded.accentArgb)
    }

    @Test
    fun `unknown and corrupt stored values fall back defensively`() {
        val decoded = AppearanceSettingsCodec.decode(
            mapOf(
                AppearanceSettingsCodec.APPEARANCE to "SEPIA_BUT_CURSED",
                AppearanceSettingsCodec.ACCENT to "not-a-color",
                AppearanceSettingsCodec.BOARD_THEME to "../../oops",
                AppearanceSettingsCodec.PIECE_SET to "",
                AppearanceSettingsCodec.BACKGROUND to "spaces are invalid",
                AppearanceSettingsCodec.PRESET to "???",
                AppearanceSettingsCodec.CUSTOM_LIGHT to "GGGGGGGG",
                AppearanceSettingsCodec.CUSTOM_DARK to "FFFFFFFFF",
                AppearanceSettingsCodec.FEEDBACK_SOUNDS_ENABLED to "perhaps",
                AppearanceSettingsCodec.FEEDBACK_HAPTICS_ENABLED to "absolutely",
                AppearanceSettingsCodec.FEEDBACK_SOUND_EVENTS to "MOVE,BOGUS",
                AppearanceSettingsCodec.FEEDBACK_HAPTIC_EVENTS to "CAPTURE,NOPE",
                AppearanceSettingsCodec.SOUND_PACK to "../../escape",
            ),
        )

        assertEquals(AppearanceSettings(), decoded)
    }

    @Test
    fun `individual override can persist no preset and deterministic argb`() {
        val customized = AppearanceSettings(
            appearance = AppAppearance.OLED_DARK,
            boardThemeId = "midnight-oled",
            presetId = null,
            customLightSquareArgb = 0xFF123456L,
            customDarkSquareArgb = 0xFF010203L,
        )

        val encoded = AppearanceSettingsCodec.encode(customized)
        val decoded = AppearanceSettingsCodec.decode(encoded)

        assertEquals("FF123456", encoded[AppearanceSettingsCodec.CUSTOM_LIGHT])
        assertEquals("FF010203", encoded[AppearanceSettingsCodec.CUSTOM_DARK])
        assertNull(decoded.presetId)
        assertEquals(customized, decoded)
    }

    @Test
    fun `feedback controls and selected sound pack round trip deterministically`() {
        val customized = AppearanceSettings(
            feedbackSoundsEnabled = false,
            feedbackHapticsEnabled = true,
            feedbackSoundEvents = linkedSetOf(
                GameFeedbackEvent.Move,
                GameFeedbackEvent.Castle,
                GameFeedbackEvent.GameEnd,
            ),
            feedbackHapticEvents = linkedSetOf(
                GameFeedbackEvent.Capture,
                GameFeedbackEvent.Check,
            ),
            soundPackId = "night-pack",
        )

        val encoded = AppearanceSettingsCodec.encode(customized)
        val decoded = AppearanceSettingsCodec.decode(encoded)

        assertEquals(customized, decoded)
        assertEquals("MOVE,CASTLE,GAME_END", encoded[AppearanceSettingsCodec.FEEDBACK_SOUND_EVENTS])
        assertEquals("CAPTURE,CHECK", encoded[AppearanceSettingsCodec.FEEDBACK_HAPTIC_EVENTS])
        assertEquals("night-pack", encoded[AppearanceSettingsCodec.SOUND_PACK])
    }
}

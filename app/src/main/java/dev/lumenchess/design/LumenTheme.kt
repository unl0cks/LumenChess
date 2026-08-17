package dev.lumenchess.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.settings.AppAppearance
import dev.lumenchess.settings.AppearanceSettings

private data class LumenPalette(
    val isLight: Boolean,
    val accentBlue: Color,
    val accentBlueBright: Color,
    val accentBlueSoft: Color,
    val accentBlueGhost: Color,
    val background: Color,
    val backgroundLift: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceHighest: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onSurfaceFaint: Color,
    val destructive: Color,
    val destructiveSoft: Color,
    val success: Color,
    val warning: Color,
)

private val DarkPalette = LumenPalette(
    isLight = false,
    accentBlue = Color(0xFF4D8DFF), accentBlueBright = Color(0xFF6EA5FF),
    accentBlueSoft = Color(0xFF18365F), accentBlueGhost = Color(0x1F6EA5FF),
    background = Color(0xFF090D14), backgroundLift = Color(0xFF0D1420),
    surface = Color(0xFF111925), surfaceRaised = Color(0xFF172231), surfaceHighest = Color(0xFF1D2A3B),
    outline = Color(0xFF2A3A4F), onSurface = Color(0xFFF4F7FB), onSurfaceMuted = Color(0xFF9CAABC),
    onSurfaceFaint = Color(0xFF6E7D91), destructive = Color(0xFFFF6B78), destructiveSoft = Color(0xFF3B1D25),
    success = Color(0xFF65D6A5), warning = Color(0xFFF2BE66),
)

private val OledPalette = DarkPalette.copy(
    background = Color.Black,
    backgroundLift = Color(0xFF020305),
    surface = Color(0xFF05070A),
    surfaceRaised = Color(0xFF0A0E13),
    surfaceHighest = Color(0xFF111821),
    outline = Color(0xFF253241),
    accentBlueSoft = Color(0xFF102B50),
)

private val LightPalette = LumenPalette(
    isLight = true,
    accentBlue = Color(0xFF276FE8), accentBlueBright = Color(0xFF155FCF),
    accentBlueSoft = Color(0xFFDCE9FF), accentBlueGhost = Color(0x1F276FE8),
    background = Color(0xFFF4F7FB), backgroundLift = Color(0xFFEAF0F7),
    surface = Color(0xFFFFFFFF), surfaceRaised = Color(0xFFEAF0F7), surfaceHighest = Color(0xFFDDE6F0),
    outline = Color(0xFFC1CDDA), onSurface = Color(0xFF121923), onSurfaceMuted = Color(0xFF566579),
    onSurfaceFaint = Color(0xFF788697), destructive = Color(0xFFB42335), destructiveSoft = Color(0xFFFFE2E6),
    success = Color(0xFF167A57), warning = Color(0xFF9A6108),
)

private val LocalLumenPalette = staticCompositionLocalOf { DarkPalette }

object LumenColors {
    val AccentBlue: Color @Composable get() = LocalLumenPalette.current.accentBlue
    val AccentBlueBright: Color @Composable get() = LocalLumenPalette.current.accentBlueBright
    val AccentBlueSoft: Color @Composable get() = LocalLumenPalette.current.accentBlueSoft
    val AccentBlueGhost: Color @Composable get() = LocalLumenPalette.current.accentBlueGhost
    val Background: Color @Composable get() = LocalLumenPalette.current.background
    val BackgroundLift: Color @Composable get() = LocalLumenPalette.current.backgroundLift
    val Surface: Color @Composable get() = LocalLumenPalette.current.surface
    val SurfaceRaised: Color @Composable get() = LocalLumenPalette.current.surfaceRaised
    val SurfaceHighest: Color @Composable get() = LocalLumenPalette.current.surfaceHighest
    val Outline: Color @Composable get() = LocalLumenPalette.current.outline
    val OnSurface: Color @Composable get() = LocalLumenPalette.current.onSurface
    val OnSurfaceMuted: Color @Composable get() = LocalLumenPalette.current.onSurfaceMuted
    val OnSurfaceFaint: Color @Composable get() = LocalLumenPalette.current.onSurfaceFaint
    val Destructive: Color @Composable get() = LocalLumenPalette.current.destructive
    val DestructiveSoft: Color @Composable get() = LocalLumenPalette.current.destructiveSoft
    val Success: Color @Composable get() = LocalLumenPalette.current.success
    val Warning: Color @Composable get() = LocalLumenPalette.current.warning
}

private val LumenTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
)

private val LumenShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun LumenTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val base = when (settings.appearance) {
        AppAppearance.SYSTEM -> if (systemDark) DarkPalette else LightPalette
        AppAppearance.DARK -> DarkPalette
        AppAppearance.OLED_DARK -> OledPalette
        AppAppearance.LIGHT -> LightPalette
    }
    val accent = Color(settings.accentArgb.toInt())
    val palette = base.copy(
        accentBlue = accent,
        accentBlueBright = lerp(accent, if (base.isLight) Color.Black else Color.White, if (base.isLight) 0.08f else 0.18f),
        accentBlueSoft = lerp(base.surface, accent, if (base.isLight) 0.16f else 0.28f),
        accentBlueGhost = accent.copy(alpha = 0.12f),
    )
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accentBlue, onPrimary = Color.White,
            primaryContainer = palette.accentBlueSoft, onPrimaryContainer = palette.onSurface,
            background = palette.background, onBackground = palette.onSurface,
            surface = palette.surface, surfaceVariant = palette.surfaceRaised,
            surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceHighest, onSurface = palette.onSurface,
            onSurfaceVariant = palette.onSurfaceMuted, outline = palette.outline,
            error = palette.destructive, onError = Color.White, errorContainer = palette.destructiveSoft,
        )
    } else {
        darkColorScheme(
            primary = palette.accentBlue, onPrimary = Color.White,
            primaryContainer = palette.accentBlueSoft, onPrimaryContainer = Color(0xFFEAF2FF),
            background = palette.background, onBackground = palette.onSurface,
            surface = palette.surface, surfaceVariant = palette.surfaceRaised,
            surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceHighest, onSurface = palette.onSurface,
            onSurfaceVariant = palette.onSurfaceMuted, outline = palette.outline,
            error = palette.destructive, onError = Color.White, errorContainer = palette.destructiveSoft,
        )
    }

    CompositionLocalProvider(LocalLumenPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = LumenTypography, shapes = LumenShapes, content = content)
    }
}

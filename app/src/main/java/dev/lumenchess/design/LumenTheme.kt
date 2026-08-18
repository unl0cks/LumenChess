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
import dev.lumenchess.customization.BackgroundCatalog
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
    val outlineStrong: Color,
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
    accentBlue = Color(0xFF4F879B),
    accentBlueBright = Color(0xFF68A3B7),
    accentBlueSoft = Color(0xFF1D343C),
    accentBlueGhost = Color(0x1F68A3B7),
    background = Color(0xFF0F1112),
    backgroundLift = Color(0xFF141718),
    surface = Color(0xFF181A1C),
    surfaceRaised = Color(0xFF202326),
    surfaceHighest = Color(0xFF272A2D),
    outline = Color(0xFF35393C),
    outlineStrong = Color(0xFF474D51),
    onSurface = Color(0xFFF2F3F3),
    onSurfaceMuted = Color(0xFFA4AAAD),
    onSurfaceFaint = Color(0xFF737A7E),
    destructive = Color(0xFFE56D78),
    destructiveSoft = Color(0xFF351E22),
    success = Color(0xFF69B997),
    warning = Color(0xFFE3B766),
)

private val OledPalette = DarkPalette.copy(
    background = Color.Black,
    backgroundLift = Color(0xFF030404),
    surface = Color(0xFF090A0B),
    surfaceRaised = Color(0xFF111315),
    surfaceHighest = Color(0xFF191C1E),
    outline = Color(0xFF303436),
    outlineStrong = Color(0xFF43484B),
    accentBlueSoft = Color(0xFF162B32),
)

private val LightPalette = LumenPalette(
    isLight = true,
    accentBlue = Color(0xFF3F778B),
    accentBlueBright = Color(0xFF2F687D),
    accentBlueSoft = Color(0xFFDCE9ED),
    accentBlueGhost = Color(0x1F3F778B),
    background = Color(0xFFF1F3F3),
    backgroundLift = Color(0xFFE8ECEE),
    surface = Color(0xFFF8F9F9),
    surfaceRaised = Color(0xFFE6EAEC),
    surfaceHighest = Color(0xFFDCE2E4),
    outline = Color(0xFFC3CBCD),
    outlineStrong = Color(0xFFA6B0B3),
    onSurface = Color(0xFF171A1C),
    onSurfaceMuted = Color(0xFF586164),
    onSurfaceFaint = Color(0xFF778185),
    destructive = Color(0xFFB84B58),
    destructiveSoft = Color(0xFFF6E1E4),
    success = Color(0xFF397B63),
    warning = Color(0xFF8A6422),
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
    val OutlineStrong: Color @Composable get() = LocalLumenPalette.current.outlineStrong
    val OnSurface: Color @Composable get() = LocalLumenPalette.current.onSurface
    val OnSurfaceMuted: Color @Composable get() = LocalLumenPalette.current.onSurfaceMuted
    val OnSurfaceFaint: Color @Composable get() = LocalLumenPalette.current.onSurfaceFaint
    val Destructive: Color @Composable get() = LocalLumenPalette.current.destructive
    val DestructiveSoft: Color @Composable get() = LocalLumenPalette.current.destructiveSoft
    val Success: Color @Composable get() = LocalLumenPalette.current.success
    val Warning: Color @Composable get() = LocalLumenPalette.current.warning
}

object LumenSpacing {
    val Xs = 4.dp
    val Sm = 6.dp
    val Md = 8.dp
    val Lg = 12.dp
    val Xl = 16.dp
    val Section = 18.dp
}

object LumenRadii {
    val Compact = 7.dp
    val Control = 8.dp
    val Panel = 10.dp
    val RaisedPanel = 12.dp
    val Dialog = 14.dp
}

private val LumenTypography = Typography(
    headlineLarge = TextStyle(fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.15).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.sp),
)

private val LumenShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
)

@Composable
fun LumenTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val appearanceBase = when (settings.appearance) {
        AppAppearance.SYSTEM -> if (systemDark) DarkPalette else LightPalette
        AppAppearance.DARK -> DarkPalette
        AppAppearance.OLED_DARK -> OledPalette
        AppAppearance.LIGHT -> LightPalette
    }
    val background = BackgroundCatalog.definition(settings.backgroundId)
    val base = appearanceBase.copy(
        backgroundLift = if (appearanceBase.isLight) background.lightTop else background.darkTop,
        background = if (appearanceBase.isLight) background.lightBottom else background.darkBottom,
    )
    val accent = Color(settings.accentArgb.toInt())
    val palette = base.copy(
        accentBlue = accent,
        accentBlueBright = lerp(accent, if (base.isLight) Color.Black else Color.White, if (base.isLight) 0.10f else 0.17f),
        accentBlueSoft = lerp(base.surface, accent, if (base.isLight) 0.13f else 0.20f),
        accentBlueGhost = accent.copy(alpha = 0.10f),
    )
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accentBlue,
            onPrimary = Color.White,
            primaryContainer = palette.accentBlueSoft,
            onPrimaryContainer = palette.onSurface,
            background = palette.background,
            onBackground = palette.onSurface,
            surface = palette.surface,
            surfaceVariant = palette.surfaceRaised,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceHighest,
            onSurface = palette.onSurface,
            onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.outline,
            error = palette.destructive,
            onError = Color.White,
            errorContainer = palette.destructiveSoft,
        )
    } else {
        darkColorScheme(
            primary = palette.accentBlue,
            onPrimary = Color.White,
            primaryContainer = palette.accentBlueSoft,
            onPrimaryContainer = palette.onSurface,
            background = palette.background,
            onBackground = palette.onSurface,
            surface = palette.surface,
            surfaceVariant = palette.surfaceRaised,
            surfaceContainer = palette.surface,
            surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceHighest,
            onSurface = palette.onSurface,
            onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.outline,
            error = palette.destructive,
            onError = Color.White,
            errorContainer = palette.destructiveSoft,
        )
    }

    CompositionLocalProvider(LocalLumenPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LumenTypography,
            shapes = LumenShapes,
            content = content,
        )
    }
}

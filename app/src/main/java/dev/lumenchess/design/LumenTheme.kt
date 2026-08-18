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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.R
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

/* Values sampled from the supplied blue concept, not Material dark defaults. */
private val DarkPalette = LumenPalette(
    isLight = false,
    accentBlue = Color(0xFF4B849A),
    accentBlueBright = Color(0xFF5A93A9),
    accentBlueSoft = Color(0xFF1B3037),
    accentBlueGhost = Color(0x1D5A93A9),
    background = Color(0xFF0D0E0F),
    backgroundLift = Color(0xFF111213),
    surface = Color(0xFF181919),
    surfaceRaised = Color(0xFF1D1F20),
    surfaceHighest = Color(0xFF242627),
    outline = Color(0xFF343838),
    outlineStrong = Color(0xFF4A5051),
    onSurface = Color(0xFFF1F2F2),
    onSurfaceMuted = Color(0xFFA5AAAB),
    onSurfaceFaint = Color(0xFF73797A),
    destructive = Color(0xFFE06B75),
    destructiveSoft = Color(0xFF341E21),
    success = Color(0xFF69B997),
    warning = Color(0xFFE3B766),
)

private val OledPalette = DarkPalette.copy(
    background = Color.Black,
    backgroundLift = Color(0xFF030303),
    surface = Color(0xFF090A0A),
    surfaceRaised = Color(0xFF111212),
    surfaceHighest = Color(0xFF181A1A),
    outline = Color(0xFF2E3232),
    outlineStrong = Color(0xFF444949),
    accentBlueSoft = Color(0xFF14292F),
)

private val LightPalette = LumenPalette(
    isLight = true,
    accentBlue = Color(0xFF447C91),
    accentBlueBright = Color(0xFF326D83),
    accentBlueSoft = Color(0xFFDCE9ED),
    accentBlueGhost = Color(0x1D447C91),
    background = Color(0xFFF2F4F4),
    backgroundLift = Color(0xFFE9EDEE),
    surface = Color(0xFFF9FAFA),
    surfaceRaised = Color(0xFFE7EBEC),
    surfaceHighest = Color(0xFFDDE3E4),
    outline = Color(0xFFC4CCCE),
    outlineStrong = Color(0xFFA8B1B3),
    onSurface = Color(0xFF171A1B),
    onSurfaceMuted = Color(0xFF596164),
    onSurfaceFaint = Color(0xFF788184),
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
    val Compact = 6.dp
    val Control = 7.dp
    val Panel = 10.dp
    val RaisedPanel = 12.dp
    val Dialog = 14.dp
}

/**
 * Lumen's UI type family. Inter Tight is generated into app resources from a pinned upstream OFL
 * source during the build, so public builds stay reproducible without depending on platform Roboto.
 */
object LumenTypography {
    private val family = FontFamily(
        Font(R.font.inter_tight_regular, FontWeight.Normal),
        Font(R.font.inter_tight_medium, FontWeight.Medium),
        Font(R.font.inter_tight_semibold, FontWeight.SemiBold),
        Font(R.font.inter_tight_bold, FontWeight.Bold),
    )

    val Material = Typography(
        headlineLarge = TextStyle(fontFamily = family, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineMedium = TextStyle(fontFamily = family, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.18).sp),
        titleLarge = TextStyle(fontFamily = family, fontSize = 19.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.16).sp),
        titleMedium = TextStyle(fontFamily = family, fontSize = 13.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.04).sp),
        bodyLarge = TextStyle(fontFamily = family, fontSize = 14.sp, lineHeight = 18.sp),
        bodyMedium = TextStyle(fontFamily = family, fontSize = 12.5.sp, lineHeight = 17.sp),
        bodySmall = TextStyle(fontFamily = family, fontSize = 11.sp, lineHeight = 14.sp),
        labelLarge = TextStyle(fontFamily = family, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontFamily = family, fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontFamily = family, fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = .01.sp),
    )

    val PlayTitle = TextStyle(
        fontFamily = family,
        fontSize = 19.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.16).sp,
    )
    val ModeTitle = TextStyle(
        fontFamily = family,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.08).sp,
    )
    val ModeSubtitle = TextStyle(
        fontFamily = family,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    )
    val SectionTitle = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val Meta = TextStyle(
        fontFamily = family,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
    )
    val QuickPrimary = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val QuickSecondary = TextStyle(
        fontFamily = family,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    )
    val BottomNav = TextStyle(
        fontFamily = family,
        fontSize = 10.5.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Medium,
    )
    val Clock = TextStyle(
        fontFamily = family,
        fontSize = 24.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp,
    )
}

private val LumenShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(9.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
)

@Composable
fun LumenTheme(settings: AppearanceSettings = AppearanceSettings(), content: @Composable () -> Unit) {
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
        accentBlueBright = lerp(accent, if (base.isLight) Color.Black else Color.White, if (base.isLight) .10f else .14f),
        accentBlueSoft = lerp(base.surface, accent, if (base.isLight) .13f else .17f),
        accentBlueGhost = accent.copy(alpha = .09f),
    )
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accentBlue, onPrimary = Color.White,
            primaryContainer = palette.accentBlueSoft, onPrimaryContainer = palette.onSurface,
            background = palette.background, onBackground = palette.onSurface,
            surface = palette.surface, surfaceVariant = palette.surfaceRaised,
            surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceHighest,
            onSurface = palette.onSurface, onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.outline, error = palette.destructive, onError = Color.White, errorContainer = palette.destructiveSoft,
        )
    } else {
        darkColorScheme(
            primary = palette.accentBlue, onPrimary = Color.White,
            primaryContainer = palette.accentBlueSoft, onPrimaryContainer = palette.onSurface,
            background = palette.background, onBackground = palette.onSurface,
            surface = palette.surface, surfaceVariant = palette.surfaceRaised,
            surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceHighest,
            onSurface = palette.onSurface, onSurfaceVariant = palette.onSurfaceMuted,
            outline = palette.outline, error = palette.destructive, onError = Color.White, errorContainer = palette.destructiveSoft,
        )
    }

    CompositionLocalProvider(LocalLumenPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = LumenTypography.Material, shapes = LumenShapes, content = content)
    }
}

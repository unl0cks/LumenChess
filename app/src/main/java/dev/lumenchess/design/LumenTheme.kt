package dev.lumenchess.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LumenColors {
    val AccentBlue = Color(0xFF4D8DFF)
    val AccentBlueBright = Color(0xFF6EA5FF)
    val AccentBlueSoft = Color(0xFF18365F)
    val AccentBlueGhost = Color(0x1F6EA5FF)
    val Background = Color(0xFF090D14)
    val BackgroundLift = Color(0xFF0D1420)
    val Surface = Color(0xFF111925)
    val SurfaceRaised = Color(0xFF172231)
    val SurfaceHighest = Color(0xFF1D2A3B)
    val Outline = Color(0xFF2A3A4F)
    val OnSurface = Color(0xFFF4F7FB)
    val OnSurfaceMuted = Color(0xFF9CAABC)
    val OnSurfaceFaint = Color(0xFF6E7D91)
    val Destructive = Color(0xFFFF6B78)
    val DestructiveSoft = Color(0xFF3B1D25)
    val Success = Color(0xFF65D6A5)
    val Warning = Color(0xFFF2BE66)
}

private val LumenDarkColors = darkColorScheme(
    primary = LumenColors.AccentBlue,
    onPrimary = Color.White,
    primaryContainer = LumenColors.AccentBlueSoft,
    onPrimaryContainer = Color(0xFFEAF2FF),
    background = LumenColors.Background,
    onBackground = LumenColors.OnSurface,
    surface = LumenColors.Surface,
    surfaceVariant = LumenColors.SurfaceRaised,
    surfaceContainer = LumenColors.Surface,
    surfaceContainerHigh = LumenColors.SurfaceRaised,
    surfaceContainerHighest = LumenColors.SurfaceHighest,
    onSurface = LumenColors.OnSurface,
    onSurfaceVariant = LumenColors.OnSurfaceMuted,
    outline = LumenColors.Outline,
    error = LumenColors.Destructive,
    onError = Color.White,
    errorContainer = LumenColors.DestructiveSoft,
)

private val LumenTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    ),
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
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumenDarkColors,
        typography = LumenTypography,
        shapes = LumenShapes,
        content = content,
    )
}

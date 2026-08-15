package dev.lumenchess.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LumenColors {
    val AccentBlue = Color(0xFF4D8DFF)
    val Background = Color(0xFF0D1016)
    val Surface = Color(0xFF151A22)
    val SurfaceRaised = Color(0xFF1D2430)
    val OnSurface = Color(0xFFF2F5FA)
    val OnSurfaceMuted = Color(0xFFAAB4C3)
}

private val LumenDarkColors = darkColorScheme(
    primary = LumenColors.AccentBlue,
    background = LumenColors.Background,
    surface = LumenColors.Surface,
    surfaceVariant = LumenColors.SurfaceRaised,
    onPrimary = Color.White,
    onBackground = LumenColors.OnSurface,
    onSurface = LumenColors.OnSurface,
    onSurfaceVariant = LumenColors.OnSurfaceMuted,
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LumenDarkColors,
        content = content,
    )
}

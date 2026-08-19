package dev.lumenchess.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Native translation tokens for the approved P5 Iteration 3 Settings proof.
 *
 * These values intentionally live beside the existing design system instead of replacing it:
 * Settings is the first bounded screen translated from the approved HTML/CSS/SVG reference.
 */
internal object LumenP5SettingsGeometry {
    val ScreenMargin = 18.dp
    val RootTopPadding = 45.dp
    val TitleHeight = 30.dp
    val TitleToListGap = 56.dp

    val RowHeight = 93.dp
    val RowGap = 10.dp
    val RowRadius = 12.5.dp
    val RowContentHorizontal = 16.dp
    val RowContentVertical = 10.dp
    val RowContentGap = 14.dp

    val IconWellSize = 47.5.dp
    val IconWellRadius = 11.25.dp
    val SettingsIconSize = 25.dp
    val ChevronSize = 20.dp

    val NavHeight = 81.dp
    val NavHorizontalPadding = 11.dp
    val NavTopPadding = 4.5.dp
    val NavBottomPadding = 6.dp
    val NavIndicatorWidth = 20.dp
    val NavIndicatorHeight = 2.dp
    val NavIconSlotWidth = 41.dp
    val NavIconSlotHeight = 34.dp
    val NavIconSlotRadius = 10.dp
    val NavIconSize = 25.dp

    val SettingsTitleSize = 24.sp
    val SettingsTitleLineHeight = 29.sp
    val RowTitleSize = 17.sp
    val RowTitleLineHeight = 20.sp
    val RowSubtitleSize = 13.sp
    val RowSubtitleLineHeight = 17.sp
    val NavLabelSize = 12.sp
    val NavLabelLineHeight = 13.5.sp
}

internal data class LumenP5IdentityPalette(
    val appBackground: Color,
    val appBackgroundLift: Color,
    val rowTop: Color,
    val rowMid: Color,
    val rowBottom: Color,
    val rowPressedTop: Color,
    val rowPressedMid: Color,
    val rowPressedBottom: Color,
    val insetSurface: Color,
    val steel: Color,
    val cyan: Color,
    val cyanMicro: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val rowOutline: Color,
    val rowPressedOutline: Color,
    val navTop: Color,
    val navBottom: Color,
)

private val ApprovedDefaultAccent = Color(0xFF4F879B)
private val ApprovedSteel = Color(0xFF4F8FA7)
private val ApprovedCyan = Color(0xFF69B8D2)
private val ApprovedCyanMicro = Color(0xFF7BCBE4)

@Composable
internal fun lumenP5IdentityPalette(): LumenP5IdentityPalette {
    val currentBackground = LumenColors.Background
    val currentAccent = LumenColors.AccentBlue
    val currentText = LumenColors.OnSurface
    val currentMuted = LumenColors.OnSurfaceMuted
    val isLight = (currentBackground.red + currentBackground.green + currentBackground.blue) > 1.5f

    if (isLight) {
        val steel = currentAccent
        val cyan = lerp(steel, Color.White, .18f)
        return LumenP5IdentityPalette(
            appBackground = currentBackground,
            appBackgroundLift = LumenColors.BackgroundLift,
            rowTop = LumenColors.Surface,
            rowMid = lerp(LumenColors.Surface, LumenColors.SurfaceRaised, .45f),
            rowBottom = LumenColors.SurfaceRaised,
            rowPressedTop = LumenColors.SurfaceRaised,
            rowPressedMid = LumenColors.SurfaceRaised,
            rowPressedBottom = LumenColors.SurfaceHighest,
            insetSurface = LumenColors.Surface,
            steel = steel,
            cyan = cyan,
            cyanMicro = lerp(cyan, Color.White, .18f),
            text = currentText,
            muted = currentMuted,
            faint = LumenColors.OnSurfaceFaint,
            rowOutline = LumenColors.Outline.copy(alpha = .42f),
            rowPressedOutline = steel.copy(alpha = .22f),
            navTop = LumenColors.Surface,
            navBottom = LumenColors.SurfaceRaised,
        )
    }

    val defaultAccent = abs(currentAccent.red - ApprovedDefaultAccent.red) < .015f &&
        abs(currentAccent.green - ApprovedDefaultAccent.green) < .015f &&
        abs(currentAccent.blue - ApprovedDefaultAccent.blue) < .015f
    val steel = if (defaultAccent) ApprovedSteel else currentAccent
    val cyan = if (defaultAccent) ApprovedCyan else lerp(steel, Color.White, .24f)
    val cyanMicro = if (defaultAccent) ApprovedCyanMicro else lerp(cyan, Color.White, .16f)

    return LumenP5IdentityPalette(
        appBackground = Color(0xFF080B0E),
        appBackgroundLift = Color(0xFF0A0E11),
        rowTop = Color(0xFF171C20),
        rowMid = Color(0xFF14191D),
        rowBottom = Color(0xFF101518),
        rowPressedTop = Color(0xFF12181B),
        rowPressedMid = Color(0xFF101619),
        rowPressedBottom = Color(0xFF0D1215),
        insetSurface = Color(0xFF0E1418),
        steel = steel,
        cyan = cyan,
        cyanMicro = cyanMicro,
        text = Color(0xFFF2F5F6),
        muted = Color(0xFFA8B1B6),
        faint = Color(0xFF6E797F),
        rowOutline = Color(0xFF8FAAB5).copy(alpha = .085f),
        rowPressedOutline = cyanMicro.copy(alpha = .095f),
        navTop = Color(0xFF0D1216).copy(alpha = .985f),
        navBottom = Color(0xFF080C0F).copy(alpha = .997f),
    )
}

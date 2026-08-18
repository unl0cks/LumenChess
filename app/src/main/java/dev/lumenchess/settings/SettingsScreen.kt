package dev.lumenchess.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenListRow
import dev.lumenchess.design.LumenPanel
import dev.lumenchess.design.LumenSegment
import dev.lumenchess.design.LumenTopBar

@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onOpenBoardAppearance: () -> Unit,
    onOpenSoundsHaptics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LumenTopBar(title = "Settings")

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Appearance", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
            LumenPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Interface theme", style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LumenSegment(
                            label = "System",
                            selected = settings.appearance == AppAppearance.SYSTEM,
                            onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.SYSTEM)) },
                            modifier = Modifier.weight(1f),
                            testTag = "appearance-system",
                        )
                        LumenSegment(
                            label = "Dark",
                            selected = settings.appearance == AppAppearance.DARK,
                            onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.DARK)) },
                            modifier = Modifier.weight(1f),
                            testTag = "appearance-dark",
                        )
                        LumenSegment(
                            label = "OLED",
                            selected = settings.appearance == AppAppearance.OLED_DARK,
                            onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.OLED_DARK)) },
                            modifier = Modifier.weight(1f),
                            testTag = "appearance-oled_dark",
                        )
                        LumenSegment(
                            label = "Light",
                            selected = settings.appearance == AppAppearance.LIGHT,
                            onClick = { onSettingsChange(settings.copy(appearance = AppAppearance.LIGHT)) },
                            modifier = Modifier.weight(1f),
                            testTag = "appearance-light",
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().testTag("settings-category-list"),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SettingsRow(
                kind = SettingsGlyphKind.ENGINE,
                title = "Engines",
                subtitle = "Stockfish 18 and Reckless 0.9.0",
                enabled = false,
            )
            SettingsRow(
                kind = SettingsGlyphKind.PLAY,
                title = "Play",
                subtitle = "Board, pieces and match presentation",
                tag = "settings-board-pieces",
                onClick = onOpenBoardAppearance,
            )
            SettingsRow(
                kind = SettingsGlyphKind.REVIEW,
                title = "Game Review",
                subtitle = "Analysis and move-classification preferences",
                enabled = false,
            )
            SettingsRow(
                kind = SettingsGlyphKind.SOUND,
                title = "Sounds & Haptics",
                subtitle = "Sound pack, event cues and tactile feedback",
                tag = "settings-sounds-haptics",
                onClick = onOpenSoundsHaptics,
            )
            SettingsRow(
                kind = SettingsGlyphKind.RATING,
                title = "Ratings",
                subtitle = "Rating mode and match options",
                enabled = false,
            )
            SettingsRow(
                kind = SettingsGlyphKind.ACCOUNT,
                title = "Accounts & Sync",
                subtitle = "Chess.com and Lichess connections",
                enabled = false,
            )
            SettingsRow(
                kind = SettingsGlyphKind.ADVANCED,
                title = "Advanced",
                subtitle = "Developer and advanced options",
                enabled = false,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    kind: SettingsGlyphKind,
    title: String,
    subtitle: String,
    tag: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    var modifier: Modifier = Modifier
    if (tag != null) modifier = modifier.testTag(tag)
    LumenListRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        enabled = enabled,
        onClick = if (enabled) onClick else null,
        showChevron = enabled && onClick != null,
        leading = { SettingsGlyph(kind, enabled) },
    )
}

private enum class SettingsGlyphKind { ENGINE, PLAY, REVIEW, SOUND, RATING, ACCOUNT, ADVANCED }

@Composable
private fun SettingsGlyph(kind: SettingsGlyphKind, enabled: Boolean) {
    val tint = if (enabled) LumenColors.AccentBlueBright else LumenColors.OnSurfaceFaint
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (enabled) LumenColors.AccentBlueSoft else LumenColors.SurfaceHighest,
                RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(.58f)) {
            val s = 1.45.dp.toPx()
            val w = size.width
            val h = size.height
            when (kind) {
                SettingsGlyphKind.ENGINE -> {
                    drawCircle(tint, w * .31f, Offset(w * .5f, h * .5f), style = Stroke(s))
                    repeat(4) { i ->
                        val angle = Math.PI * .5 * i
                        val x1 = w * .5f + kotlin.math.cos(angle).toFloat() * w * .31f
                        val y1 = h * .5f + kotlin.math.sin(angle).toFloat() * h * .31f
                        val x2 = w * .5f + kotlin.math.cos(angle).toFloat() * w * .46f
                        val y2 = h * .5f + kotlin.math.sin(angle).toFloat() * h * .46f
                        drawLine(tint, Offset(x1, y1), Offset(x2, y2), s, StrokeCap.Round)
                    }
                }
                SettingsGlyphKind.PLAY -> {
                    drawLine(tint, Offset(w*.25f,h*.5f), Offset(w*.75f,h*.5f), s, StrokeCap.Round)
                    drawLine(tint, Offset(w*.5f,h*.25f), Offset(w*.5f,h*.75f), s, StrokeCap.Round)
                    drawCircle(tint, w*.35f, Offset(w*.5f,h*.5f), style = Stroke(s))
                }
                SettingsGlyphKind.REVIEW -> {
                    drawLine(tint, Offset(w*.2f,h*.72f), Offset(w*.42f,h*.46f), s, StrokeCap.Round)
                    drawLine(tint, Offset(w*.42f,h*.46f), Offset(w*.58f,h*.58f), s, StrokeCap.Round)
                    drawLine(tint, Offset(w*.58f,h*.58f), Offset(w*.82f,h*.25f), s, StrokeCap.Round)
                }
                SettingsGlyphKind.SOUND -> {
                    drawLine(tint, Offset(w*.25f,h*.42f), Offset(w*.44f,h*.42f), s*2f, StrokeCap.Round)
                    drawLine(tint, Offset(w*.44f,h*.42f), Offset(w*.62f,h*.27f), s*2f, StrokeCap.Round)
                    drawLine(tint, Offset(w*.62f,h*.27f), Offset(w*.62f,h*.73f), s*2f, StrokeCap.Round)
                    drawLine(tint, Offset(w*.62f,h*.73f), Offset(w*.44f,h*.58f), s*2f, StrokeCap.Round)
                }
                SettingsGlyphKind.RATING -> {
                    drawCircle(tint, w*.35f, Offset(w*.5f,h*.5f), style = Stroke(s))
                    drawLine(tint, Offset(w*.5f,h*.5f), Offset(w*.68f,h*.31f), s, StrokeCap.Round)
                }
                SettingsGlyphKind.ACCOUNT -> {
                    drawCircle(tint, w*.13f, Offset(w*.5f,h*.34f), style = Stroke(s))
                    drawArc(tint, 200f, 140f, false, topLeft = Offset(w*.2f,h*.42f), size = androidx.compose.ui.geometry.Size(w*.6f,h*.45f), style = Stroke(s))
                }
                SettingsGlyphKind.ADVANCED -> {
                    drawLine(tint, Offset(w*.3f,h*.2f), Offset(w*.7f,h*.8f), s, StrokeCap.Round)
                    drawLine(tint, Offset(w*.7f,h*.2f), Offset(w*.3f,h*.8f), s, StrokeCap.Round)
                }
            }
        }
    }
}

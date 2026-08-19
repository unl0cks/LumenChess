package dev.lumenchess.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.design.LumenTopBar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SETTINGS_ROW_HEIGHT_DP = 70

@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onOpenBoardAppearance: () -> Unit,
    onOpenSoundsHaptics: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlaySettings: () -> Unit = onOpenBoardAppearance,
) {
    // The legacy parameters remain part of the public screen contract for the deeper Play settings
    // route. Root Settings is intentionally category-only; it does not own those controls.
    @Suppress("UNUSED_VARIABLE")
    val retainedSettingsContract = Triple(settings, onSettingsChange, onOpenSoundsHaptics)

    Column(
        modifier.fillMaxSize()
            .testTag("settings-root")
            .background(
                Brush.verticalGradient(
                    listOf(
                        LumenColors.BackgroundLift,
                        LumenColors.Background,
                        LumenColors.Background,
                    ),
                ),
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 13.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LumenTopBar("Settings")
        Column(
            Modifier.fillMaxWidth().testTag("settings-category-list"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsCategoryRow(
                kind = SettingsGlyphKind.ENGINE,
                title = "Engines",
                subtitle = "Manage installed engines",
                uniqueTag = "settings-category-engines",
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.PLAY,
                title = "Play",
                subtitle = "Time controls, themes, sounds, board",
                uniqueTag = "settings-category-play",
                legacyTag = "settings-play",
                onClick = onOpenPlaySettings,
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.REVIEW,
                title = "Game Review",
                subtitle = "Analysis settings, move classification",
                uniqueTag = "settings-category-review",
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.RATING,
                title = "Ratings",
                subtitle = "Rating mode, system, match options",
                uniqueTag = "settings-category-ratings",
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.ACCOUNT,
                title = "Accounts & Sync",
                subtitle = "Chess.com, Lichess",
                uniqueTag = "settings-category-accounts",
            )
            SettingsCategoryRow(
                kind = SettingsGlyphKind.ADVANCED,
                title = "Advanced",
                subtitle = "Developer & advanced",
                uniqueTag = "settings-category-advanced",
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    kind: SettingsGlyphKind,
    title: String,
    subtitle: String,
    uniqueTag: String,
    legacyTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val interactive = onClick != null
    val scale by animateFloatAsState(
        targetValue = if (pressed) .984f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-scale-$title",
    )
    val offset by animateDpAsState(
        targetValue = if (pressed) 1.15.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-offset-$title",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) .15.dp else 2.0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-shadow-$title",
    )
    val lowerEdge by animateDpAsState(
        targetValue = if (pressed) .35.dp else 2.6.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-edge-$title",
    )
    val faceTop by animateColorAsState(
        targetValue = if (pressed) LumenColors.SurfaceHighest.copy(alpha = .80f)
        else LumenColors.SurfaceHighest.copy(alpha = .67f),
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-top-$title",
    )
    val faceBottom by animateColorAsState(
        targetValue = if (pressed) LumenColors.Surface.copy(alpha = .98f)
        else LumenColors.SurfaceRaised.copy(alpha = .96f),
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "settings-row-bottom-$title",
    )
    val chevronTint by animateColorAsState(
        targetValue = when {
            pressed -> LumenColors.OnSurface
            interactive -> LumenColors.OnSurfaceMuted
            else -> LumenColors.OnSurfaceFaint
        },
        animationSpec = LumenMotion.fastTween(),
        label = "settings-row-chevron-$title",
    )
    val iconTint by animateColorAsState(
        targetValue = if (pressed) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
        animationSpec = LumenMotion.fastTween(),
        label = "settings-row-icon-$title",
    )
    val shape = RoundedCornerShape(8.dp)

    Box(
        Modifier.fillMaxWidth()
            .height(SETTINGS_ROW_HEIGHT_DP.dp)
            .testTag(uniqueTag)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset.toPx()
            }
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(LumenColors.Background)
            .drawBehind {
                drawRect(
                    color = LumenColors.OutlineStrong.copy(alpha = if (pressed) .58f else .78f),
                    topLeft = Offset(0f, size.height - lowerEdge.toPx()),
                )
            }
            .padding(bottom = lowerEdge)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(faceTop, faceBottom)))
            .border(
                1.dp,
                if (pressed) LumenColors.OutlineStrong else LumenColors.Outline.copy(alpha = .92f),
                shape,
            ),
    ) {
        // Stable full-bounds geometry target independent of the pressed paint transform.
        Box(Modifier.matchParentSize().testTag("settings-category-row"))

        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            SettingsIconWell(kind = kind, tint = iconTint, pressed = pressed)
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 16.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = LumenColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 12.5.sp),
                    fontWeight = FontWeight.Medium,
                    color = LumenColors.OnSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SettingsChevron(chevronTint)
        }

        if (interactive && onClick != null) {
            var hitTarget = Modifier.matchParentSize()
            if (legacyTag != null) hitTarget = hitTarget.testTag(legacyTag)
            Box(
                hitTarget.clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            )
        }
    }
}

private enum class SettingsGlyphKind { ENGINE, PLAY, REVIEW, RATING, ACCOUNT, ADVANCED }

@Composable
private fun SettingsIconWell(kind: SettingsGlyphKind, tint: Color, pressed: Boolean) {
    val shape = RoundedCornerShape(7.dp)
    val wellBorder = if (pressed) LumenColors.AccentBlue.copy(alpha = .38f)
    else LumenColors.OutlineStrong.copy(alpha = .76f)
    Box(
        Modifier.size(34.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        LumenColors.SurfaceHighest.copy(alpha = if (pressed) .86f else .72f),
                        LumenColors.Background.copy(alpha = .88f),
                    ),
                ),
            )
            .border(1.dp, wellBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        SettingsGlyph(kind, tint, Modifier.size(19.dp))
    }
}

@Composable
private fun SettingsGlyph(kind: SettingsGlyphKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val s = 1.35.dp.toPx()
        when (kind) {
            SettingsGlyphKind.ENGINE -> {
                val chip = Size(w * .50f, h * .50f)
                val origin = Offset(w * .25f, h * .25f)
                drawRoundRect(tint, origin, chip, androidx.compose.ui.geometry.CornerRadius(w * .08f), style = Stroke(s))
                repeat(3) { index ->
                    val p = (index + 1) / 4f
                    drawLine(tint, Offset(w * p, h * .13f), Offset(w * p, h * .25f), s, StrokeCap.Round)
                    drawLine(tint, Offset(w * p, h * .75f), Offset(w * p, h * .87f), s, StrokeCap.Round)
                    drawLine(tint, Offset(w * .13f, h * p), Offset(w * .25f, h * p), s, StrokeCap.Round)
                    drawLine(tint, Offset(w * .75f, h * p), Offset(w * .87f, h * p), s, StrokeCap.Round)
                }
                drawCircle(tint, w * .075f, Offset(w * .50f, h * .50f), style = Stroke(s))
            }
            SettingsGlyphKind.PLAY -> {
                val path = Path().apply {
                    moveTo(w * .36f, h * .25f)
                    lineTo(w * .73f, h * .50f)
                    lineTo(w * .36f, h * .75f)
                    close()
                }
                drawPath(path, tint, style = Stroke(s))
                drawLine(tint, Offset(w * .20f, h * .18f), Offset(w * .20f, h * .82f), s, StrokeCap.Round)
            }
            SettingsGlyphKind.REVIEW -> {
                drawCircle(tint, w * .27f, Offset(w * .43f, h * .43f), style = Stroke(s))
                drawLine(tint, Offset(w * .62f, h * .62f), Offset(w * .82f, h * .82f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .28f, h * .47f), Offset(w * .40f, h * .56f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .40f, h * .56f), Offset(w * .57f, h * .32f), s, StrokeCap.Round)
            }
            SettingsGlyphKind.RATING -> {
                val cx = w * .50f
                val cy = h * .51f
                val outer = w * .34f
                val inner = outer * .43f
                val path = Path()
                repeat(10) { index ->
                    val radius = if (index % 2 == 0) outer else inner
                    val angle = -PI / 2 + index * PI / 5
                    val x = cx + cos(angle).toFloat() * radius
                    val y = cy + sin(angle).toFloat() * radius
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, tint, style = Stroke(s))
            }
            SettingsGlyphKind.ACCOUNT -> {
                drawCircle(tint, w * .13f, Offset(w * .35f, h * .38f), style = Stroke(s))
                drawArc(
                    tint,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * .12f, h * .40f),
                    size = Size(w * .46f, h * .40f),
                    style = Stroke(s),
                )
                drawLine(tint, Offset(w * .58f, h * .38f), Offset(w * .80f, h * .38f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .72f, h * .29f), Offset(w * .80f, h * .38f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .72f, h * .47f), Offset(w * .80f, h * .38f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .80f, h * .64f), Offset(w * .58f, h * .64f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .66f, h * .55f), Offset(w * .58f, h * .64f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .66f, h * .73f), Offset(w * .58f, h * .64f), s, StrokeCap.Round)
            }
            SettingsGlyphKind.ADVANCED -> {
                drawLine(tint, Offset(w * .27f, h * .17f), Offset(w * .27f, h * .83f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .50f, h * .17f), Offset(w * .50f, h * .83f), s, StrokeCap.Round)
                drawLine(tint, Offset(w * .73f, h * .17f), Offset(w * .73f, h * .83f), s, StrokeCap.Round)
                drawCircle(tint, w * .07f, Offset(w * .27f, h * .38f), style = Stroke(s))
                drawCircle(tint, w * .07f, Offset(w * .50f, h * .63f), style = Stroke(s))
                drawCircle(tint, w * .07f, Offset(w * .73f, h * .31f), style = Stroke(s))
            }
        }
    }
}

@Composable
private fun SettingsChevron(tint: Color) {
    Canvas(Modifier.size(15.dp)) {
        val stroke = 1.35.dp.toPx()
        drawLine(
            tint,
            Offset(size.width * .37f, size.height * .25f),
            Offset(size.width * .63f, size.height * .50f),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            tint,
            Offset(size.width * .63f, size.height * .50f),
            Offset(size.width * .37f, size.height * .75f),
            stroke,
            StrokeCap.Round,
        )
    }
}
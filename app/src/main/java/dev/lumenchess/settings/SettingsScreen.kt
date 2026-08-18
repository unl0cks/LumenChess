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
        modifier.fillMaxSize().testTag("settings-root")
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift,LumenColors.Background)))
            .verticalScroll(rememberScrollState()).padding(horizontal=13.dp,vertical=3.dp),
        verticalArrangement=Arrangement.spacedBy(7.dp),
    ) {
        LumenTopBar("Settings")
        Column(
            Modifier.fillMaxWidth().testTag("settings-category-list"),
            verticalArrangement=Arrangement.spacedBy(4.dp),
        ) {
            SettingsRow(SettingsGlyphKind.ENGINE,"Engines","Manage installed engines",enabled=false)
            SettingsRow(SettingsGlyphKind.PLAY,"Play","Time controls, themes, sounds, board",tag="settings-board-pieces",onClick=onOpenBoardAppearance)
            SettingsRow(SettingsGlyphKind.REVIEW,"Game Review","Analysis settings, move classification",enabled=false)
            SettingsRow(SettingsGlyphKind.SOUND,"Sounds & Haptics","Sound pack and tactile feedback",tag="settings-sounds-haptics",onClick=onOpenSoundsHaptics)
            SettingsRow(SettingsGlyphKind.RATING,"Ratings","Rating mode, system, match options",enabled=false)
            SettingsRow(SettingsGlyphKind.ACCOUNT,"Accounts & Sync","Chess.com, Lichess",enabled=false)
            SettingsRow(SettingsGlyphKind.ADVANCED,"Advanced","Developer & advanced",enabled=false)
        }
        Column(
            Modifier.fillMaxWidth().padding(top=5.dp,bottom=8.dp).testTag("settings-appearance-section"),
            verticalArrangement=Arrangement.spacedBy(5.dp),
        ) {
            Text("Appearance",style=MaterialTheme.typography.labelLarge,color=LumenColors.OnSurface)
            LumenPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    LumenSegment("System",settings.appearance==AppAppearance.SYSTEM,{ onSettingsChange(settings.copy(appearance=AppAppearance.SYSTEM)) },Modifier.weight(1f),testTag="appearance-system")
                    LumenSegment("Dark",settings.appearance==AppAppearance.DARK,{ onSettingsChange(settings.copy(appearance=AppAppearance.DARK)) },Modifier.weight(1f),testTag="appearance-dark")
                    LumenSegment("OLED",settings.appearance==AppAppearance.OLED_DARK,{ onSettingsChange(settings.copy(appearance=AppAppearance.OLED_DARK)) },Modifier.weight(1f),testTag="appearance-oled_dark")
                    LumenSegment("Light",settings.appearance==AppAppearance.LIGHT,{ onSettingsChange(settings.copy(appearance=AppAppearance.LIGHT)) },Modifier.weight(1f),testTag="appearance-light")
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    kind: SettingsGlyphKind,
    title: String,
    subtitle: String,
    tag: String?=null,
    enabled: Boolean=true,
    onClick: (() -> Unit)?=null,
) {
    var modifier: Modifier=Modifier
    if (tag!=null) modifier=modifier.testTag(tag)
    LumenListRow(
        title=title,subtitle=subtitle,modifier=modifier,enabled=enabled,
        onClick=if(enabled) onClick else null,showChevron=enabled&&onClick!=null,
        leading={ SettingsGlyph(kind,enabled) },
    )
}

private enum class SettingsGlyphKind { ENGINE,PLAY,REVIEW,SOUND,RATING,ACCOUNT,ADVANCED }

@Composable
private fun SettingsGlyph(kind: SettingsGlyphKind,enabled: Boolean) {
    val tint=if(enabled) LumenColors.AccentBlueBright else LumenColors.OnSurfaceFaint
    Box(
        Modifier.fillMaxSize().background(if(enabled) LumenColors.AccentBlueSoft else LumenColors.SurfaceHighest,RoundedCornerShape(6.dp)),
        contentAlignment=Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(.56f)) {
            val s=1.25.dp.toPx(); val w=size.width; val h=size.height
            when(kind) {
                SettingsGlyphKind.ENGINE -> {
                    drawCircle(tint,w*.31f,Offset(w*.5f,h*.5f),style=Stroke(s))
                    repeat(4) { i ->
                        val a=Math.PI*.5*i
                        drawLine(tint,Offset(w*.5f+kotlin.math.cos(a).toFloat()*w*.31f,h*.5f+kotlin.math.sin(a).toFloat()*h*.31f),Offset(w*.5f+kotlin.math.cos(a).toFloat()*w*.46f,h*.5f+kotlin.math.sin(a).toFloat()*h*.46f),s,StrokeCap.Round)
                    }
                }
                SettingsGlyphKind.PLAY -> {
                    drawCircle(tint,w*.35f,Offset(w*.5f,h*.5f),style=Stroke(s)); drawLine(tint,Offset(w*.25f,h*.5f),Offset(w*.75f,h*.5f),s,StrokeCap.Round); drawLine(tint,Offset(w*.5f,h*.25f),Offset(w*.5f,h*.75f),s,StrokeCap.Round)
                }
                SettingsGlyphKind.REVIEW -> {
                    drawLine(tint,Offset(w*.2f,h*.72f),Offset(w*.42f,h*.46f),s,StrokeCap.Round); drawLine(tint,Offset(w*.42f,h*.46f),Offset(w*.58f,h*.58f),s,StrokeCap.Round); drawLine(tint,Offset(w*.58f,h*.58f),Offset(w*.82f,h*.25f),s,StrokeCap.Round)
                }
                SettingsGlyphKind.SOUND -> {
                    drawLine(tint,Offset(w*.25f,h*.42f),Offset(w*.44f,h*.42f),s*1.8f,StrokeCap.Round); drawLine(tint,Offset(w*.44f,h*.42f),Offset(w*.62f,h*.27f),s*1.8f,StrokeCap.Round); drawLine(tint,Offset(w*.62f,h*.27f),Offset(w*.62f,h*.73f),s*1.8f,StrokeCap.Round); drawLine(tint,Offset(w*.62f,h*.73f),Offset(w*.44f,h*.58f),s*1.8f,StrokeCap.Round)
                }
                SettingsGlyphKind.RATING -> { drawCircle(tint,w*.35f,Offset(w*.5f,h*.5f),style=Stroke(s)); drawLine(tint,Offset(w*.5f,h*.5f),Offset(w*.68f,h*.31f),s,StrokeCap.Round) }
                SettingsGlyphKind.ACCOUNT -> { drawCircle(tint,w*.13f,Offset(w*.5f,h*.34f),style=Stroke(s)); drawArc(tint,200f,140f,false,Offset(w*.2f,h*.42f),androidx.compose.ui.geometry.Size(w*.6f,h*.45f),style=Stroke(s)) }
                SettingsGlyphKind.ADVANCED -> { drawLine(tint,Offset(w*.3f,h*.2f),Offset(w*.7f,h*.8f),s,StrokeCap.Round); drawLine(tint,Offset(w*.7f,h*.2f),Offset(w*.3f,h*.8f),s,StrokeCap.Round) }
            }
        }
    }
}

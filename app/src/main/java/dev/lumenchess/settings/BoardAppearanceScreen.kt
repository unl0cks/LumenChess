package dev.lumenchess.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lumenchess.board.PieceSet
import dev.lumenchess.board.PieceSetCatalog
import dev.lumenchess.core.chess.Color as ChessColor
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.customization.BackgroundCatalog
import dev.lumenchess.customization.BoardThemeCatalog
import dev.lumenchess.customization.LumenPreset
import dev.lumenchess.customization.LumenPresetCatalog
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenTabs
import dev.lumenchess.design.LumenTopBar

private enum class CustomizationTab(val label: String) { BOARD("Board"), PIECES("Pieces"), BACKGROUND("Background"), PRESETS("Presets") }

@Composable
fun BoardAppearanceScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(CustomizationTab.BOARD) }
    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift,LumenColors.Background)))
            .padding(horizontal=13.dp,vertical=3.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp),
    ) {
        LumenTopBar("Board & Pieces",onBack=onBack,backTestTag="customization-back")
        Text(
            settings.presetId?.let { id -> LumenPresetCatalog.definition(id)?.let { "${it.displayName} preset" } } ?: "Custom mix",
            style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted,
            modifier=Modifier.padding(start=2.dp).testTag("customization-status"),
        )
        Box(
            Modifier.fillMaxWidth().background(LumenColors.Surface,RoundedCornerShape(8.dp))
                .border(1.dp,LumenColors.Outline,RoundedCornerShape(8.dp)).padding(6.dp),
        ) { BoardPreview(settings,Modifier.fillMaxWidth().height(248.dp)) }

        LumenTabs(CustomizationTab.entries.map{it.label},tab.ordinal,{ tab=CustomizationTab.entries[it] },testTagPrefix="customization-tab")

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).testTag("customization-options-grid"),
            verticalArrangement=Arrangement.spacedBy(6.dp),
        ) {
            when(tab) {
                CustomizationTab.BOARD -> BoardThemeCatalog.builtIns.chunked(2).forEach { pair ->
                    VisualOptionRow(pair.size) {
                        pair.forEach { d ->
                            VisualOptionCard(d.displayName,d.description,settings.boardThemeId==d.id,"customization-board-${d.id}",Modifier.weight(1f),{ BoardSwatch(d.palette.lightSquare,d.palette.darkSquare) }) {
                                onSettingsChange(settings.withBoardTheme(d.id).copy(customLightSquareArgb=null,customDarkSquareArgb=null))
                            }
                        }
                    }
                }
                CustomizationTab.PIECES -> PieceSetCatalog.builtIns.chunked(2).forEach { pair ->
                    VisualOptionRow(pair.size) {
                        pair.forEach { d ->
                            VisualOptionCard(d.displayName,if(d.id==AppearanceSettings.DEFAULT_PIECE_SET_ID) "Classic shaded" else "Outline",settings.pieceSetId==d.id,"customization-piece-${d.id}",Modifier.weight(1f),{ PieceMiniatures(d) }) {
                                onSettingsChange(settings.withPieceSet(d.id))
                            }
                        }
                    }
                }
                CustomizationTab.BACKGROUND -> BackgroundCatalog.builtIns.chunked(2).forEach { pair ->
                    VisualOptionRow(pair.size) {
                        pair.forEach { d ->
                            VisualOptionCard(d.displayName,d.description,settings.backgroundId==d.id,"customization-background-${d.id}",Modifier.weight(1f),{
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(d.darkTop,d.darkBottom)),RoundedCornerShape(5.dp)))
                            }) { onSettingsChange(settings.withBackground(d.id)) }
                        }
                    }
                }
                CustomizationTab.PRESETS -> LumenPresetCatalog.builtIns.chunked(2).forEach { pair ->
                    VisualOptionRow(pair.size) {
                        pair.forEach { d ->
                            VisualOptionCard(d.displayName,"Board · pieces · background",settings.presetId==d.id,"customization-preset-${d.id}",Modifier.weight(1f),{ PresetMiniature(d) }) {
                                onSettingsChange(d.applyTo(settings))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun VisualOptionRow(itemCount: Int,content: @Composable RowScope.()->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        content(); if(itemCount==1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun VisualOptionCard(
    title:String,subtitle:String,selected:Boolean,tag:String,modifier:Modifier=Modifier,
    preview:@Composable()->Unit,onClick:()->Unit,
) {
    val shape=RoundedCornerShape(7.dp)
    Column(
        modifier.height(96.dp).background(if(selected)LumenColors.AccentBlueGhost else LumenColors.Surface,shape)
            .border(1.dp,if(selected)LumenColors.AccentBlueBright else LumenColors.Outline,shape)
            .clickable(onClick=onClick).testTag(tag).padding(6.dp),
        verticalArrangement=Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(52.dp).background(LumenColors.Background.copy(alpha=.52f),RoundedCornerShape(5.dp)).padding(3.dp),
            contentAlignment=Alignment.Center,
        ) { preview() }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.SemiBold,color=if(selected)LumenColors.AccentBlueBright else LumenColors.OnSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(selected) Text("✓",style=MaterialTheme.typography.labelSmall,color=LumenColors.AccentBlueBright)
        }
        Text(subtitle,style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun BoardSwatch(light:Color,dark:Color) {
    Canvas(Modifier.fillMaxSize()) {
        val cw=size.width/4f; val ch=size.height/3f
        repeat(3){r->repeat(4){f->drawRect(if((f+r)%2==0)light else dark,Offset(f*cw,r*ch),androidx.compose.ui.geometry.Size(cw,ch))}}
    }
}

@Composable
private fun PieceMiniatures(pieceSet:PieceSet) {
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
        listOf(PieceType.KING,PieceType.KNIGHT,PieceType.ROOK).forEach { type ->
            pieceSet.Piece(Piece(ChessColor.WHITE,type),Color(0xFFF0EBDD),Modifier.size(26.dp))
        }
    }
}

@Composable
private fun PresetMiniature(preset:LumenPreset) {
    val board=BoardThemeCatalog.definition(preset.boardThemeId).palette
    val bg=BackgroundCatalog.definition(preset.backgroundId)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg.darkTop,bg.darkBottom)),RoundedCornerShape(5.dp)).padding(5.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxSize(.78f)) { BoardSwatch(board.lightSquare,board.darkSquare) }
    }
}

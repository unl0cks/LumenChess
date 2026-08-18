package dev.lumenchess.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenSecondaryButton
import dev.lumenchess.design.LumenToggle
import dev.lumenchess.design.LumenTopBar
import dev.lumenchess.feedback.AndroidGameFeedbackOutput
import dev.lumenchess.feedback.GameFeedbackEvent
import dev.lumenchess.feedback.SoundPackImporter
import dev.lumenchess.feedback.toSoundEvent
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SoundsHapticsScreen(settings:AppearanceSettings,onSettingsChange:(AppearanceSettings)->Unit,onBack:()->Unit,modifier:Modifier=Modifier) {
    val context=LocalContext.current; val scope=rememberCoroutineScope(); val previewOutput=remember(context.applicationContext){AndroidGameFeedbackOutput(context.applicationContext)}
    var pendingSingleEvent by remember{mutableStateOf<GameFeedbackEvent?>(null)}; var importStatus by remember{mutableStateOf<String?>(null)}
    DisposableEffect(previewOutput){onDispose{previewOutput.close()}}; LaunchedEffect(settings.soundPackId){previewOutput.updateSoundPackId(settings.soundPackId)}
    fun importPack(uri:Uri){scope.launch{val result=runCatching{withContext(Dispatchers.IO){val packId="custom-${System.currentTimeMillis().toString(36)}";val root=File(context.filesDir,"feedback/sound-packs");context.contentResolver.openInputStream(uri).use{input->requireNotNull(input){"Could not open selected ZIP"};SoundPackImporter().importZip(input,root,packId)};packId}};result.onSuccess{packId->onSettingsChange(settings.copy(soundPackId=packId));importStatus="Imported sound pack"}.onFailure{error->importStatus=error.message?:"Sound pack import failed"}}}
    fun importSingle(event:GameFeedbackEvent,uri:Uri){scope.launch{val result=runCatching{withContext(Dispatchers.IO){val name=context.displayName(uri)?:"sound.wav";val root=File(context.filesDir,"feedback/overrides");context.contentResolver.openInputStream(uri).use{input->requireNotNull(input){"Could not open selected sound"};SoundPackImporter().importSingle(input,name,root,event.toSoundEvent())}}};importStatus=result.fold({"Imported ${feedbackEventLabel(event)} override"},{it.message?:"Sound import failed"})}}
    val packLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let(::importPack)}
    val singleLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->val event=pendingSingleEvent;pendingSingleEvent=null;if(uri!=null&&event!=null)importSingle(event,uri)}

    Column(modifier.fillMaxSize().testTag("sounds-haptics-screen").background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift,LumenColors.Background))).verticalScroll(rememberScrollState()).padding(horizontal=13.dp,vertical=3.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        LumenTopBar("Sounds & Haptics",onBack=onBack,backTestTag="sounds-haptics-back")
        CompactPanel(Modifier.fillMaxWidth().testTag("p5-feedback-master-panel")) {
            FeedbackMasterLine("Master sound","Move and game-event audio",settings.feedbackSoundsEnabled,"feedback-sounds-master"){onSettingsChange(settings.copy(feedbackSoundsEnabled=it))}
            FeedbackMasterLine("Haptics","Tactile feedback for selected events",settings.feedbackHapticsEnabled,"feedback-haptics-master"){onSettingsChange(settings.copy(feedbackHapticsEnabled=it))}
        }
        CompactPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Text("Sound pack",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(if(settings.soundPackId==AppearanceSettings.DEFAULT_SOUND_PACK_ID)"Lumen Default" else settings.soundPackId,style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted) }
                LumenSecondaryButton("Import ZIP",{packLauncher.launch(arrayOf("application/zip","application/x-zip-compressed","application/octet-stream"))},Modifier.height(40.dp).testTag("feedback-import-pack"))
            }
            if(settings.soundPackId!=AppearanceSettings.DEFAULT_SOUND_PACK_ID) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){LumenSecondaryButton("Use Lumen",{onSettingsChange(settings.copy(soundPackId=AppearanceSettings.DEFAULT_SOUND_PACK_ID))},Modifier.height(40.dp))}
            importStatus?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)}
        }
        Text("Event feedback",style=MaterialTheme.typography.labelLarge,color=LumenColors.OnSurface)
        Column(Modifier.fillMaxWidth().testTag("p5-feedback-event-list"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            GameFeedbackEvent.all.forEach { event ->
                val key=feedbackEventKey(event);val sound=event in settings.feedbackSoundEvents;val haptic=event in settings.feedbackHapticEvents
                Column(Modifier.fillMaxWidth().background(LumenColors.Surface,RoundedCornerShape(7.dp)).border(1.dp,LumenColors.Outline,RoundedCornerShape(7.dp)).padding(horizontal=9.dp,vertical=5.dp).testTag("p5-feedback-event-$key"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(feedbackEventLabel(event),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        CompactToggleCell("Sound",sound,"feedback-sound-$key"){enabled->onSettingsChange(settings.copy(feedbackSoundEvents=settings.feedbackSoundEvents.toggled(event,enabled)))}
                        CompactToggleCell("Haptic",haptic,"feedback-haptic-$key"){enabled->onSettingsChange(settings.copy(feedbackHapticEvents=settings.feedbackHapticEvents.toggled(event,enabled)))}
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                        LumenSecondaryButton("Preview",{previewOutput.preview(event,settings.feedbackSoundsEnabled&&sound,settings.feedbackHapticsEnabled&&haptic)},Modifier.weight(1f).height(38.dp).testTag("feedback-preview-$key"))
                        LumenSecondaryButton("Custom",{pendingSingleEvent=event;singleLauncher.launch(arrayOf("audio/*"))},Modifier.weight(1f).height(38.dp).testTag("feedback-import-$key"))
                    }
                }
            }
        }
        Box(Modifier.height(6.dp))
    }
}

@Composable private fun CompactPanel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit)=Column(modifier.background(LumenColors.Surface,RoundedCornerShape(7.dp)).border(1.dp,LumenColors.Outline,RoundedCornerShape(7.dp)).padding(horizontal=9.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(3.dp),content=content)
@Composable private fun FeedbackMasterLine(title:String,subtitle:String,checked:Boolean,tag:String,onCheckedChange:(Boolean)->Unit)=Row(Modifier.fillMaxWidth().height(47.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted)};LumenToggle(checked,onCheckedChange,Modifier.testTag(tag),contentDescription=title)}
@Composable private fun CompactToggleCell(label:String,checked:Boolean,tag:String,onCheckedChange:(Boolean)->Unit)=Column(horizontalAlignment=Alignment.CenterHorizontally){Text(label,style=MaterialTheme.typography.labelSmall,color=LumenColors.OnSurfaceMuted);LumenToggle(checked,onCheckedChange,Modifier.testTag(tag),contentDescription=label)}
private fun Set<GameFeedbackEvent>.toggled(event:GameFeedbackEvent,enabled:Boolean):Set<GameFeedbackEvent> = linkedSetOf<GameFeedbackEvent>().apply{addAll(this@toggled);if(enabled)add(event)else remove(event)}
private fun feedbackEventKey(event:GameFeedbackEvent)=when(event){GameFeedbackEvent.Move->"move";GameFeedbackEvent.Capture->"capture";GameFeedbackEvent.Check->"check";GameFeedbackEvent.Castle->"castle";GameFeedbackEvent.Promotion->"promotion";GameFeedbackEvent.GameStart->"game-start";GameFeedbackEvent.GameEnd->"game-end"}
private fun feedbackEventLabel(event:GameFeedbackEvent)=when(event){GameFeedbackEvent.Move->"Move";GameFeedbackEvent.Capture->"Capture";GameFeedbackEvent.Check->"Check";GameFeedbackEvent.Castle->"Castle";GameFeedbackEvent.Promotion->"Promotion";GameFeedbackEvent.GameStart->"Game start";GameFeedbackEvent.GameEnd->"Game end"}
private fun Context.displayName(uri:Uri):String?{contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->if(cursor.moveToFirst())return cursor.getString(0)};return uri.lastPathSegment?.substringAfterLast('/')}

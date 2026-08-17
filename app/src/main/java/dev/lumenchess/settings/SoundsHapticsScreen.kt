package dev.lumenchess.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenListRow
import dev.lumenchess.design.LumenPanel
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
fun SoundsHapticsScreen(
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewOutput = remember(context.applicationContext) { AndroidGameFeedbackOutput(context.applicationContext) }
    var pendingSingleEvent by remember { mutableStateOf<GameFeedbackEvent?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    DisposableEffect(previewOutput) { onDispose { previewOutput.close() } }
    LaunchedEffect(settings.soundPackId) { previewOutput.updateSoundPackId(settings.soundPackId) }

    fun importPack(uri: Uri) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val packId = "custom-${System.currentTimeMillis().toString(36)}"
                    val root = File(context.filesDir, "feedback/sound-packs")
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open selected ZIP" }
                        SoundPackImporter().importZip(input, root, packId)
                    }
                    packId
                }
            }
            result.onSuccess { packId ->
                onSettingsChange(settings.copy(soundPackId = packId))
                importStatus = "Imported sound pack"
            }.onFailure { error -> importStatus = error.message ?: "Sound pack import failed" }
        }
    }

    fun importSingle(event: GameFeedbackEvent, uri: Uri) {
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val name = context.displayName(uri) ?: "sound.wav"
                    val root = File(context.filesDir, "feedback/overrides")
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open selected sound" }
                        SoundPackImporter().importSingle(input, name, root, event.toSoundEvent())
                    }
                }
            }
            importStatus = result.fold(
                onSuccess = { "Imported ${feedbackEventLabel(event)} override" },
                onFailure = { it.message ?: "Sound import failed" },
            )
        }
    }

    val packLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importPack) }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val event = pendingSingleEvent
        pendingSingleEvent = null
        if (uri != null && event != null) importSingle(event, uri)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("sounds-haptics-screen")
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        LumenTopBar(title = "Sounds & Haptics", onBack = onBack, backTestTag = "sounds-haptics-back")

        FeedbackMasterRow(
            title = "Master sound",
            subtitle = "Move and game-event audio",
            checked = settings.feedbackSoundsEnabled,
            tag = "feedback-sounds-master",
            onCheckedChange = { onSettingsChange(settings.copy(feedbackSoundsEnabled = it)) },
        )
        FeedbackMasterRow(
            title = "Haptics",
            subtitle = "Tactile feedback for selected game events",
            checked = settings.feedbackHapticsEnabled,
            tag = "feedback-haptics-master",
            onCheckedChange = { onSettingsChange(settings.copy(feedbackHapticsEnabled = it)) },
        )

        LumenPanel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Sound pack", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
                Text(
                    if (settings.soundPackId == AppearanceSettings.DEFAULT_SOUND_PACK_ID) "Lumen Default" else settings.soundPackId,
                    style = MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    LumenSecondaryButton(
                        label = "Import ZIP",
                        onClick = { packLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        modifier = Modifier.testTag("feedback-import-pack"),
                    )
                    if (settings.soundPackId != AppearanceSettings.DEFAULT_SOUND_PACK_ID) {
                        LumenSecondaryButton(
                            label = "Use Lumen",
                            onClick = { onSettingsChange(settings.copy(soundPackId = AppearanceSettings.DEFAULT_SOUND_PACK_ID)) },
                        )
                    }
                }
                importStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
                }
            }
        }

        Text("Event feedback", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
        GameFeedbackEvent.all.forEach { event ->
            val key = feedbackEventKey(event)
            val soundChecked = event in settings.feedbackSoundEvents
            val hapticChecked = event in settings.feedbackHapticEvents
            LumenPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(feedbackEventLabel(event), style = MaterialTheme.typography.titleMedium)
                    CompactToggleLine(
                        label = "Sound",
                        checked = soundChecked,
                        tag = "feedback-sound-$key",
                    ) { enabled ->
                        onSettingsChange(settings.copy(feedbackSoundEvents = settings.feedbackSoundEvents.toggled(event, enabled)))
                    }
                    CompactToggleLine(
                        label = "Haptic",
                        checked = hapticChecked,
                        tag = "feedback-haptic-$key",
                    ) { enabled ->
                        onSettingsChange(settings.copy(feedbackHapticEvents = settings.feedbackHapticEvents.toggled(event, enabled)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LumenSecondaryButton(
                            label = "Preview",
                            onClick = {
                                previewOutput.preview(
                                    event = event,
                                    sound = settings.feedbackSoundsEnabled && soundChecked,
                                    haptic = settings.feedbackHapticsEnabled && hapticChecked,
                                )
                            },
                            modifier = Modifier.testTag("feedback-preview-$key"),
                        )
                        LumenSecondaryButton(
                            label = "Custom",
                            onClick = {
                                pendingSingleEvent = event
                                singleLauncher.launch(arrayOf("audio/*"))
                            },
                            modifier = Modifier.testTag("feedback-import-$key"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackMasterRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    LumenPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
            }
            LumenToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(tag),
                contentDescription = title,
            )
        }
    }
}

@Composable
private fun CompactToggleLine(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
        LumenToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
            contentDescription = label,
        )
    }
}

private fun Set<GameFeedbackEvent>.toggled(event: GameFeedbackEvent, enabled: Boolean): Set<GameFeedbackEvent> =
    linkedSetOf<GameFeedbackEvent>().apply {
        addAll(this@toggled)
        if (enabled) add(event) else remove(event)
    }

private fun feedbackEventKey(event: GameFeedbackEvent): String = when (event) {
    GameFeedbackEvent.Move -> "move"
    GameFeedbackEvent.Capture -> "capture"
    GameFeedbackEvent.Check -> "check"
    GameFeedbackEvent.Castle -> "castle"
    GameFeedbackEvent.Promotion -> "promotion"
    GameFeedbackEvent.GameStart -> "game-start"
    GameFeedbackEvent.GameEnd -> "game-end"
}

private fun feedbackEventLabel(event: GameFeedbackEvent): String = when (event) {
    GameFeedbackEvent.Move -> "Move"
    GameFeedbackEvent.Capture -> "Capture"
    GameFeedbackEvent.Check -> "Check"
    GameFeedbackEvent.Castle -> "Castle"
    GameFeedbackEvent.Promotion -> "Promotion"
    GameFeedbackEvent.GameStart -> "Game start"
    GameFeedbackEvent.GameEnd -> "Game end"
}

private fun Context.displayName(uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0)
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

package dev.lumenchess.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeRow
import dev.lumenchess.design.LumenDerivativeSectionLabel
import dev.lumenchess.design.LumenDerivativeToggleRow
import dev.lumenchess.design.LumenDerivativeTopBar
import dev.lumenchess.design.LumenDerivativeTray
import dev.lumenchess.design.lumenP5IdentityPalette
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
    var selectedEvent by remember { mutableStateOf<GameFeedbackEvent?>(null) }
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
                { "Imported ${feedbackEventLabel(event)} override" },
                { it.message ?: "Sound import failed" },
            )
        }
    }

    val packLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::importPack) }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val event = pendingSingleEvent
        pendingSingleEvent = null
        if (uri != null && event != null) importSingle(event, uri)
    }

    val event = selectedEvent
    if (event != null) {
        FeedbackEventDetail(
            event = event,
            settings = settings,
            onSettingsChange = onSettingsChange,
            onBack = { selectedEvent = null },
            onPreview = {
                val sound = event in settings.feedbackSoundEvents
                val haptic = event in settings.feedbackHapticEvents
                previewOutput.preview(event, settings.feedbackSoundsEnabled && sound, settings.feedbackHapticsEnabled && haptic)
            },
            onImport = {
                pendingSingleEvent = event
                singleLauncher.launch(arrayOf("audio/*"))
            },
            modifier = modifier,
        )
        return
    }

    val palette = lumenP5IdentityPalette()
    Box(modifier.fillMaxSize().testTag("sounds-haptics-screen")) {
        LumenDerivativePage(
            modifier = Modifier.fillMaxSize(),
            testTag = "derivative-feedback-screen",
            scrollable = true,
            spacing = 8,
        ) {
            LumenDerivativeTopBar("Sounds & Haptics", onBack = onBack, backTestTag = "sounds-haptics-back")
            Box(Modifier.fillMaxWidth().testTag("p5-feedback-master-panel")) {
                LumenDerivativeTray(
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "derivative-feedback-master-tray",
                    spacing = 5,
                ) {
                    LumenDerivativeToggleRow(
                        title = "Master Sound",
                        checked = settings.feedbackSoundsEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(feedbackSoundsEnabled = it)) },
                        testTag = "feedback-sounds-master",
                    )
                    LumenDerivativeToggleRow(
                        title = "Haptics",
                        checked = settings.feedbackHapticsEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(feedbackHapticsEnabled = it)) },
                        testTag = "feedback-haptics-master",
                    )
                }
            }

            LumenDerivativeRow(
                title = "Sound Pack",
                subtitle = if (settings.soundPackId == AppearanceSettings.DEFAULT_SOUND_PACK_ID) "Lumen Default" else settings.soundPackId,
                testTag = "derivative-sound-pack-row",
                showChevron = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LumenDerivativeAction(
                    label = "Import ZIP",
                    onClick = {
                        packLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"),
                        )
                    },
                    modifier = Modifier.height(48.dp),
                    testTag = "feedback-import-pack",
                )
            }
            importStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }

            LumenDerivativeSectionLabel("Event feedback")
            Column(
                Modifier.fillMaxWidth().testTag("p5-feedback-event-list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GameFeedbackEvent.all.forEach { item ->
                    val key = feedbackEventKey(item)
                    val sound = item in settings.feedbackSoundEvents
                    val haptic = item in settings.feedbackHapticEvents
                    val state = when {
                        sound && haptic -> "Sound + haptic"
                        sound -> "Sound"
                        haptic -> "Haptic"
                        else -> "Off"
                    }
                    LumenDerivativeRow(
                        title = feedbackEventLabel(item),
                        subtitle = "Lumen Default · $state",
                        testTag = "p5-feedback-event-$key",
                        onClick = { selectedEvent = item },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackEventDetail(
    event: GameFeedbackEvent,
    settings: AppearanceSettings,
    onSettingsChange: (AppearanceSettings) -> Unit,
    onBack: () -> Unit,
    onPreview: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = feedbackEventKey(event)
    val sound = event in settings.feedbackSoundEvents
    val haptic = event in settings.feedbackHapticEvents
    Box(modifier.fillMaxSize().testTag("p5-feedback-event-detail")) {
        LumenDerivativePage(
            modifier = Modifier.fillMaxSize(),
            testTag = "derivative-feedback-detail",
            spacing = 9,
        ) {
            LumenDerivativeTopBar(feedbackEventLabel(event), onBack = onBack)
            LumenDerivativeTray(
                modifier = Modifier.fillMaxWidth(),
                testTag = "derivative-feedback-detail-tray",
                spacing = 5,
            ) {
                LumenDerivativeToggleRow(
                    title = "Sound",
                    subtitle = "Lumen Default",
                    checked = sound,
                    onCheckedChange = { enabled ->
                        onSettingsChange(
                            settings.copy(feedbackSoundEvents = settings.feedbackSoundEvents.toggled(event, enabled)),
                        )
                    },
                    testTag = "feedback-sound-$key",
                )
                LumenDerivativeToggleRow(
                    title = "Haptic",
                    subtitle = "Enabled for this event",
                    checked = haptic,
                    onCheckedChange = { enabled ->
                        onSettingsChange(
                            settings.copy(feedbackHapticEvents = settings.feedbackHapticEvents.toggled(event, enabled)),
                        )
                    },
                    testTag = "feedback-haptic-$key",
                )
            }
            Row(
                Modifier.fillMaxWidth().testTag("derivative-feedback-actions"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LumenDerivativeAction(
                    label = "Preview",
                    onClick = onPreview,
                    modifier = Modifier.weight(1f).height(48.dp),
                    testTag = "feedback-preview-$key",
                )
                LumenDerivativeAction(
                    label = "Custom…",
                    onClick = onImport,
                    modifier = Modifier.weight(1f).height(48.dp),
                    testTag = "feedback-import-$key",
                )
            }
        }
    }
}

private fun Set<GameFeedbackEvent>.toggled(event: GameFeedbackEvent, enabled: Boolean): Set<GameFeedbackEvent> =
    linkedSetOf<GameFeedbackEvent>().apply { addAll(this@toggled); if (enabled) add(event) else remove(event) }

private fun feedbackEventKey(event: GameFeedbackEvent) = when(event) {
    GameFeedbackEvent.Move -> "move"
    GameFeedbackEvent.Capture -> "capture"
    GameFeedbackEvent.Check -> "check"
    GameFeedbackEvent.Castle -> "castle"
    GameFeedbackEvent.Promotion -> "promotion"
    GameFeedbackEvent.GameStart -> "game-start"
    GameFeedbackEvent.GameEnd -> "game-end"
}

private fun feedbackEventLabel(event: GameFeedbackEvent) = when(event) {
    GameFeedbackEvent.Move -> "Move"
    GameFeedbackEvent.Capture -> "Capture"
    GameFeedbackEvent.Check -> "Check"
    GameFeedbackEvent.Castle -> "Castle"
    GameFeedbackEvent.Promotion -> "Promotion"
    GameFeedbackEvent.GameStart -> "Game Start"
    GameFeedbackEvent.GameEnd -> "Game End"
}

private fun Context.displayName(uri: Uri): String? {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) return cursor.getString(0)
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

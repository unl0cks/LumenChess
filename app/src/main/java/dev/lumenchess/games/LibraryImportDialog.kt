package dev.lumenchess.games

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativeSectionLabel
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.games.imports.OnlineSite

private const val MAX_PGN_FILE_BYTES = 16 * 1024 * 1024

/**
 * Brings games into the library: a PGN file, pasted PGN, or a player's recent games from Chess.com
 * or Lichess. Each imported game is filed under the site it came from, so the library's Chess.com,
 * Lichess and Imported filters show real games.
 */
@Composable
internal fun LibraryImportDialog(ui: GameLibraryUiState, vm: GameLibraryViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pasted by rememberSaveable { mutableStateOf("") }
    var site by rememberSaveable { mutableStateOf(OnlineSite.CHESS_COM) }
    var username by rememberSaveable { mutableStateOf("") }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            vm.importPgnSource {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readNBytes(MAX_PGN_FILE_BYTES + 1)
                    require(bytes.size <= MAX_PGN_FILE_BYTES) { "PGN file is larger than 16 MB" }
                    String(bytes, Charsets.UTF_8)
                } ?: error("Could not open the file")
            }
        }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = LumenColors.OnSurface, unfocusedTextColor = LumenColors.OnSurface,
        focusedBorderColor = LumenColors.AccentBlueBright, unfocusedBorderColor = LumenColors.OutlineStrong,
        focusedLabelColor = LumenColors.AccentBlueBright, unfocusedLabelColor = LumenColors.OnSurfaceMuted,
        cursorColor = LumenColors.AccentBlueBright,
    )

    Dialog(
        onDismissRequest = { if (!ui.importing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LumenDerivativeSurface(
            role = DerivativeSurfaceRole.PREVIEW_PANEL,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("library-import-dialog"),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Import games", style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)

                LumenDerivativeSectionLabel("From your account")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnlineSite.entries.forEach { option ->
                        LumenDerivativeSurface(
                            if (site == option) DerivativeSurfaceRole.SELECTED_FACE else DerivativeSurfaceRole.NEUTRAL_ROW,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            onClick = { site = option },
                            testTag = "library-import-site-${option.name.lowercase()}",
                            contentAlignment = Alignment.Center,
                        ) { Text(option.label, color = LumenColors.OnSurface, style = MaterialTheme.typography.labelLarge) }
                    }
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.take(40) },
                    modifier = Modifier.fillMaxWidth().testTag("library-import-username"),
                    label = { Text("${site.label} username") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    colors = fieldColors,
                )
                LumenDerivativeAction(
                    "Import recent games",
                    { vm.importFromSite(site, username) },
                    Modifier.fillMaxWidth(),
                    enabled = !ui.importing && username.isNotBlank(),
                    testTag = "library-import-online",
                )
                Text(
                    "Downloads up to 100 of your most recent public games. Only the username is sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                )

                LumenDerivativeSectionLabel("From PGN")
                LumenDerivativeAction(
                    "Open PGN file",
                    { openFile.launch(arrayOf("application/x-chess-pgn", "application/vnd.chess-pgn", "text/plain", "application/octet-stream")) },
                    Modifier.fillMaxWidth(),
                    enabled = !ui.importing,
                    testTag = "library-import-file",
                )
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 180.dp).testTag("library-import-paste"),
                    label = { Text("Paste PGN") },
                    colors = fieldColors,
                )
                LumenDerivativeAction(
                    "Import pasted PGN",
                    { vm.importPgnText(pasted) },
                    Modifier.fillMaxWidth(),
                    enabled = !ui.importing && pasted.isNotBlank(),
                    testTag = "library-import-paste-action",
                )

                ui.importStatus?.let {
                    Text(
                        it,
                        modifier = Modifier.testTag("library-import-status"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LumenColors.OnSurface,
                    )
                }
                LumenDerivativeAction(
                    "Done",
                    onDismiss,
                    Modifier.fillMaxWidth(),
                    enabled = !ui.importing,
                    testTag = "library-import-close",
                )
            }
        }
    }
}

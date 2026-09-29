package dev.lumenchess.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.lumenchess.analysis.rating.RatingSystem
import dev.lumenchess.analysis.review.ReviewPreset
import dev.lumenchess.background.BackgroundWork
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.HeavyAnalysisRetentionPolicy
import dev.lumenchess.data.persistence.ReviewState
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSectionLabel
import dev.lumenchess.design.LumenDerivativeSegment
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenDerivativeToggleRow
import dev.lumenchess.design.LumenDerivativeTopBar
import dev.lumenchess.games.imports.OnlineGameSources
import dev.lumenchess.games.imports.OnlineSite
import dev.lumenchess.player.AccountSync
import dev.lumenchess.player.MatchSource
import dev.lumenchess.player.PlayerData
import dev.lumenchess.player.PlayerSettings
import dev.lumenchess.player.PlayerSettingsRepository
import dev.lumenchess.player.lichess.LichessAuth
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun rememberPlayerSettings(): Pair<PlayerSettings, (suspend (PlayerSettings) -> PlayerSettings) -> Unit> {
    val context = LocalContext.current
    val repository = remember { PlayerSettingsRepository.from(context) }
    val settings by repository.settings.collectAsState(initial = PlayerSettings())
    val scope = rememberCoroutineScope()
    return settings to { transform ->
        scope.launch {
            val current = repository.current()
            val next = transform(current)
            repository.update { next }
            BackgroundWork.reschedule(context)
            PlayerData.invalidate()
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
}

// --- Game Review ----------------------------------------------------------------------------------

@Composable
fun GameReviewSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val (settings, update) = rememberPlayerSettings()
    LumenDerivativePage(modifier, testTag = "review-settings-root", scrollable = true, spacing = 10) {
        LumenDerivativeTopBar("Game Review", onBack, backTestTag = "review-settings-back")
        LumenDerivativeSectionLabel("Default depth")
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ReviewPreset.FAST to "Fast", ReviewPreset.BALANCED to "Balanced", ReviewPreset.DEEP to "Deep").forEach { (preset, label) ->
                LumenDerivativeSegment(label, settings.reviewPreset == preset, { update { it.copy(reviewPreset = preset) } }, testTag = "review-settings-${preset.name}")
            }
        }
        Note(
            when (settings.reviewPreset) {
                ReviewPreset.FAST -> "About 0.2 s a position. Good for a quick look."
                ReviewPreset.DEEP -> "1.5–3 s a position; every borderline or special move is searched again, deeper."
                else -> "About half a second a position; up to 16 critical moves are searched again, deeper."
            },
        )
        LumenDerivativeToggleRow(
            title = "Review finished games automatically",
            subtitle = "Games you finish in Play are reviewed in the background (battery not low).",
            checked = settings.autoReview,
            onCheckedChange = { value -> update { it.copy(autoReview = value) } },
            testTag = "review-settings-auto",
        )
        LumenDerivativeSectionLabel("How reviews work")
        Note(
            "Stockfish 18 evaluates every position on this device, then each move is classified by how much of your winning chance it gave up " +
                "(expected points: 0.02 Excellent, 0.05 Good, 0.10 Inaccuracy, 0.20 Mistake, more Blunder), with Book, Great, Brilliant and Miss on top. " +
                "Accuracy follows Lichess's published method. Game Rating is a single-game estimate, not a rating, and not Chess.com's formula. " +
                "Every review stores its engine, settings and model version.",
        )
    }
}

// --- Ratings ---------------------------------------------------------------------------------------

@Composable
fun RatingsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val (settings, update) = rememberPlayerSettings()
    LumenDerivativePage(modifier, testTag = "ratings-settings-root", scrollable = true, spacing = 10) {
        LumenDerivativeTopBar("Ratings", onBack, backTestTag = "ratings-settings-back")
        LumenDerivativeSectionLabel("Local rating system")
        RatingSystem.entries.forEach { system ->
            LumenDerivativeSurface(
                if (settings.ratingSystem == system) DerivativeSurfaceRole.SELECTED_FACE else DerivativeSurfaceRole.NEUTRAL_ROW,
                Modifier.fillMaxWidth(),
                onClick = { update { it.copy(ratingSystem = system) } },
                testTag = "ratings-system-${system.name}",
            ) {
                Column {
                    Text(system.label, style = MaterialTheme.typography.titleSmall, color = LumenColors.OnSurface)
                    Note(
                        when (system) {
                            RatingSystem.GLICKO_2 -> "Lichess-style: rating, deviation and volatility (start 1500 ± 500)."
                            RatingSystem.GLICKO_1 -> "Chess.com-style Glicko: rating and deviation (start 1500 ± 350)."
                            RatingSystem.FIDE -> "FIDE-style Elo with K = 40 / 20 / 10; may go below 1400 (useful for beginners)."
                            RatingSystem.FIDE_STRICT -> "FIDE Elo with the current 1400 publication floor."
                        },
                    )
                }
            }
        }
        Note("Every system is kept in full at all times, so switching never loses a rating. Rated games against the engines are the results; Bullet, Blitz and Rapid, and Standard and Chess960, are separate pools.")
        LumenDerivativeSectionLabel("Match My Elo")
        MatchSource.entries.forEach { source ->
            val available = when (source) {
                MatchSource.CHESS_COM -> settings.chessComUsername != null
                MatchSource.LICHESS -> settings.lichessUsername != null
                else -> true
            }
            LumenDerivativeSurface(
                if (settings.matchSource == source) DerivativeSurfaceRole.SELECTED_FACE else if (available) DerivativeSurfaceRole.NEUTRAL_ROW else DerivativeSurfaceRole.DISABLED_SURFACE,
                Modifier.fillMaxWidth(),
                enabled = available,
                onClick = { update { it.copy(matchSource = source) } },
                testTag = "ratings-source-${source.name}",
            ) {
                Column {
                    Text(source.label, style = MaterialTheme.typography.titleSmall, color = LumenColors.OnSurface)
                    Note(
                        when (source) {
                            MatchSource.LOCAL_PERFORMANCE -> "From your reviewed games (default)."
                            MatchSource.LOCAL_RATED -> "Your local rated rating in the system above."
                            MatchSource.CHESS_COM -> if (available) "Cached; refreshed on sync. Works offline." else "Link a Chess.com account first (Accounts & Sync)."
                            MatchSource.LICHESS -> if (available) "Cached; refreshed on sync. Works offline." else "Link a Lichess account first (Accounts & Sync)."
                        },
                    )
                }
            }
        }
        LumenDerivativeSectionLabel("Match range")
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(50 to "Tight ±50", 100 to "Normal ±100", 200 to "Wide ±200").forEach { (range, label) ->
                LumenDerivativeSegment(label, settings.matchRange == range, { update { it.copy(matchRange = range) } }, testTag = "ratings-range-$range")
            }
        }
        var custom by remember(settings.matchRange) { mutableStateOf(settings.matchRange.toString()) }
        OutlinedTextField(
            value = custom,
            onValueChange = { text ->
                custom = text.filter(Char::isDigit).take(3)
                custom.toIntOrNull()?.takeIf { it in 0..600 }?.let { value -> update { it.copy(matchRange = value) } }
            },
            label = { Text("Custom range (± Elo)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("ratings-range-custom"),
            colors = fieldColors(),
        )
        Note("The engine's strength is drawn once from this range when a game starts and never changes during the game.")
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LumenColors.OnSurface, unfocusedTextColor = LumenColors.OnSurface,
    focusedBorderColor = LumenColors.AccentBlueBright, unfocusedBorderColor = LumenColors.OutlineStrong,
    focusedLabelColor = LumenColors.AccentBlueBright, unfocusedLabelColor = LumenColors.OnSurfaceMuted,
    cursorColor = LumenColors.AccentBlueBright,
)

// --- Accounts & Sync -------------------------------------------------------------------------------

@Composable
fun AccountsSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (settings, update) = rememberPlayerSettings()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LumenDerivativePage(modifier, testTag = "accounts-settings-root", scrollable = true, spacing = 10) {
        LumenDerivativeTopBar("Accounts & Sync", onBack, backTestTag = "accounts-settings-back")
        Note(
            "Link your usernames to bring your games into Games and your ratings into Match My Elo. LumenChess reads the sites' public game " +
                "exports; it never needs a password and never uploads anything.",
        )
        AccountField(OnlineSite.CHESS_COM, settings.chessComUsername, settings.lastChessComSyncEpochMillis, settings.chessComRatings?.let { r ->
            listOfNotNull(r.bullet?.let { "Bullet $it" }, r.blitz?.let { "Blitz $it" }, r.rapid?.let { "Rapid $it" }).joinToString(" · ")
        }) { name -> update { it.copy(chessComUsername = name, lastChessComSyncEpochMillis = if (name != it.chessComUsername) null else it.lastChessComSyncEpochMillis) } }
        AccountField(OnlineSite.LICHESS, settings.lichessUsername, settings.lastLichessSyncEpochMillis, settings.lichessRatings?.let { r ->
            listOfNotNull(r.bullet?.let { "Bullet $it" }, r.blitz?.let { "Blitz $it" }, r.rapid?.let { "Rapid $it" }).joinToString(" · ")
        }) { name -> update { it.copy(lichessUsername = name, lastLichessSyncEpochMillis = if (name != it.lichessUsername) null else it.lastLichessSyncEpochMillis) } }
        LumenDerivativeAction(
            if (busy) "Syncing…" else "Sync now",
            {
                busy = true
                status = null
                scope.launch {
                    val outcomes = AccountSync.syncAll(context)
                    status = if (outcomes.isEmpty()) "Add a username first." else outcomes.joinToString("\n") { "${it.site.label}: ${it.summary}" }
                    busy = false
                }
            },
            Modifier.fillMaxWidth(),
            enabled = !busy && (settings.chessComUsername != null || settings.lichessUsername != null),
            testTag = "accounts-sync-now",
        )
        status?.let { Note(it) }
        LumenDerivativeToggleRow(
            title = "Sync daily in the background",
            subtitle = "On Wi-Fi, when the battery is not low.",
            checked = settings.autoSync,
            onCheckedChange = { value -> update { it.copy(autoSync = value) } },
            enabled = settings.chessComUsername != null || settings.lichessUsername != null,
            testTag = "accounts-auto-sync",
        )
        LumenDerivativeSectionLabel("Lichess sign-in (optional)")
        Note(
            "Signing in with Lichess (OAuth, no password shared with LumenChess) lets Explorer and cloud evaluations use your account's " +
                "higher request limits. Only the \"read preferences\" scope is requested; you can revoke it on lichess.org at any time.",
        )
        if (settings.lichessToken == null) {
            LumenDerivativeAction("Sign in with Lichess", { LichessAuth.begin(context) }, Modifier.fillMaxWidth(), testTag = "accounts-lichess-signin")
        } else {
            LumenDerivativeAction("Sign out of Lichess", { update { it.copy(lichessToken = null) } }, Modifier.fillMaxWidth(), testTag = "accounts-lichess-signout")
        }
    }
}

@Composable
private fun AccountField(site: OnlineSite, username: String?, lastSync: Long?, ratings: String?, onChange: (String?) -> Unit) {
    var text by remember(username) { mutableStateOf(username.orEmpty()) }
    val valid = text.isBlank() || OnlineGameSources.normalizedUsername(site, text) != null
    LumenDerivativeSurface(DerivativeSurfaceRole.NEUTRAL_ROW, Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(site.label, style = MaterialTheme.typography.titleSmall, color = LumenColors.OnSurface)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.trim() },
                label = { Text("${site.label} username") },
                singleLine = true,
                isError = !valid,
                modifier = Modifier.fillMaxWidth().testTag("accounts-username-${site.name}"),
                colors = fieldColors(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LumenDerivativeAction(
                    "Save",
                    { onChange(OnlineGameSources.normalizedUsername(site, text)) },
                    Modifier.weight(1f),
                    enabled = valid && text.isNotBlank() && text != username,
                    testTag = "accounts-save-${site.name}",
                )
                if (username != null) {
                    LumenDerivativeAction("Unlink", { text = ""; onChange(null) }, Modifier.weight(1f), testTag = "accounts-unlink-${site.name}")
                }
            }
            if (username != null) {
                Note(
                    listOfNotNull(
                        lastSync?.let { "Last sync ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}" } ?: "Not synced yet",
                        ratings?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                )
            }
        }
    }
}

// --- Storage ---------------------------------------------------------------------------------------

private data class StorageUsage(val games: Int, val reviews: Int, val lineEntries: Int, val lineBytes: Long, val databaseBytes: Long)

@Composable
fun StorageSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StorageSettingsRepository.from(context) }
    var settings by remember { mutableStateOf(repository.current()) }
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    suspend fun measure(): StorageUsage = withContext(Dispatchers.IO) {
        val database = AppData.database(context)
        val heavy = AppData.retention(context).heavyUsage()
        StorageUsage(
            games = database.gameDao().countGames(),
            reviews = AppData.reviews(context).reviewsInState(ReviewState.COMPLETE).size,
            lineEntries = heavy.entries,
            lineBytes = heavy.payloadBytes,
            databaseBytes = context.getDatabasePath("lumenchess.db").length(),
        )
    }
    LaunchedEffect(Unit) { usage = measure() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-chess-pgn")) { uri: Uri? ->
        if (uri != null) scope.launch {
            message = try {
                val count = LibraryExport.exportAll(context, uri)
                "Exported $count games."
            } catch (error: Exception) {
                "Export failed: ${error.message ?: "storage error"}"
            }
        }
    }
    LumenDerivativePage(modifier, testTag = "storage-settings-root", scrollable = true, spacing = 10) {
        LumenDerivativeTopBar("Storage & Data", onBack, backTestTag = "storage-settings-back")
        usage?.let { u ->
            LumenDerivativeSurface(DerivativeSurfaceRole.RECESSED_TRAY, Modifier.fillMaxWidth().testTag("storage-usage")) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${u.games} games · ${u.reviews} reviews", style = MaterialTheme.typography.titleSmall, color = LumenColors.OnSurface)
                    Note("Engine lines cache: ${u.lineEntries} positions, ${megabytes(u.lineBytes)}")
                    Note("Database file: ${megabytes(u.databaseBytes)}")
                }
            }
        }
        LumenDerivativeSectionLabel("Keep engine lines for")
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StorageSettings.DAY_CHOICES.forEach { days ->
                LumenDerivativeSegment(
                    days?.let { if (it >= 365) "1 year" else "$it d" } ?: "Always",
                    settings.engineLineDays == days,
                    { settings = settings.copy(engineLineDays = days).also(repository::update) },
                    testTag = "storage-days-${days ?: 0}",
                )
            }
        }
        LumenDerivativeSectionLabel("Cache size limit")
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StorageSettings.SIZE_CHOICES.forEach { size ->
                LumenDerivativeSegment(
                    size?.let { "${it / 1000}k" } ?: "None",
                    settings.maxEngineLines == size,
                    { settings = settings.copy(maxEngineLines = size).also(repository::update) },
                    testTag = "storage-size-${size ?: 0}",
                )
            }
        }
        Note(
            "Cleanup runs weekly while the device is idle and only removes engine lines. Games, review results, accuracy and ratings are " +
                "never deleted automatically; favorite and protected games keep their lines.",
        )
        LumenDerivativeAction("Clear engine lines now", { confirmClear = true }, Modifier.fillMaxWidth(), testTag = "storage-clear")
        LumenDerivativeAction("Export all games (PGN)", { exporter.launch("lumenchess-games.pgn") }, Modifier.fillMaxWidth(), testTag = "storage-export")
        message?.let { Note(it) }
    }
    if (confirmClear) {
        Dialog(onDismissRequest = { confirmClear = false }) {
            LumenDerivativeSurface(DerivativeSurfaceRole.PREVIEW_PANEL) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Clear engine lines?", style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
                    Note("Reviews stay readable (classifications, accuracy, best moves) but their engine lines are removed. Favorite and protected games are kept.")
                    LumenDerivativeAction("Cancel", { confirmClear = false }, Modifier.fillMaxWidth(), testTag = "storage-clear-cancel")
                    LumenDerivativeAction(
                        "Clear",
                        {
                            confirmClear = false
                            scope.launch {
                                val retention = AppData.retention(context)
                                var removed = 0
                                while (true) {
                                    val batch = retention.prune(HeavyAnalysisRetentionPolicy(maxRetainedCount = 0))
                                    if (batch == 0) break
                                    removed += batch
                                }
                                message = "Removed $removed cached positions."
                                usage = measure()
                            }
                        },
                        Modifier.fillMaxWidth(),
                        testTag = "storage-clear-confirm",
                    )
                }
            }
        }
    }
}

private fun megabytes(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

/** Opens a URL in the browser (used by About and Accounts). */
internal fun openUrl(context: android.content.Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

package dev.lumenchess.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativeAction
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.RuntimeTerminal
import dev.lumenchess.runtime.clock.ClockConfig

/** Presentation-only sheets for the board-first Live screen. They command [PlayViewModel] only. */
@Composable
private fun LiveDialogPanel(
    testTag: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LumenDerivativeSurface(
            role = DerivativeSurfaceRole.PREVIEW_PANEL,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag(testTag),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun LiveDialogTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = LumenColors.OnSurface)
}

@Composable
private fun LiveDialogNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
}

@Composable
internal fun LiveResignDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    LiveDialogPanel("p5-live-resign-dialog", onCancel) {
        LiveDialogTitle("Resign this game?")
        LiveDialogNote("The game ends now and counts as a loss.")
        LumenDerivativeAction("Keep playing", onCancel, Modifier.fillMaxWidth(), testTag = "p5-live-resign-cancel")
        LumenDerivativeAction("Resign", onConfirm, Modifier.fillMaxWidth(), testTag = "p5-live-resign-confirm")
    }
}

@Composable
internal fun LiveLeaveDialog(onStay: () -> Unit, onLeave: () -> Unit) {
    LiveDialogPanel("p5-live-leave-dialog", onStay) {
        LiveDialogTitle("Leave this game?")
        LiveDialogNote("Your game is saved. You can resume it from Play at any time.")
        LumenDerivativeAction("Stay in game", onStay, Modifier.fillMaxWidth(), testTag = "p5-live-leave-cancel")
        LumenDerivativeAction("Leave game", onLeave, Modifier.fillMaxWidth(), testTag = "p5-live-leave-confirm")
    }
}

@Composable
internal fun LiveMenuDialog(
    setup: ResolvedPlaySetup,
    onCopyPgn: () -> Unit,
    onCopyFen: () -> Unit,
    onLeave: () -> Unit,
    onClose: () -> Unit,
    onReview: (() -> Unit)? = null,
) {
    LiveDialogPanel("p5-live-menu-dialog", onClose) {
        LiveDialogTitle("Game")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            liveGameSummary(setup).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = LumenColors.OnSurfaceMuted)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = LumenColors.OnSurface,
                    )
                }
            }
        }
        if (onReview != null) LumenDerivativeAction("Game Review", onReview, Modifier.fillMaxWidth(), testTag = "p5-live-menu-review")
        LumenDerivativeAction("Copy PGN", onCopyPgn, Modifier.fillMaxWidth(), testTag = "p5-live-menu-copy-pgn")
        LumenDerivativeAction("Copy FEN", onCopyFen, Modifier.fillMaxWidth(), testTag = "p5-live-menu-copy-fen")
        LumenDerivativeAction("Leave game", onLeave, Modifier.fillMaxWidth(), testTag = "p5-live-menu-leave")
        LumenDerivativeAction("Close", onClose, Modifier.fillMaxWidth(), testTag = "p5-live-menu-close")
    }
}

@Composable
internal fun LiveResultDialog(
    terminal: RuntimeTerminal,
    humanSide: Color,
    onRematch: () -> Unit,
    onNewGame: () -> Unit,
    onViewBoard: () -> Unit,
    onReview: (() -> Unit)? = null,
    onAnalyze: (() -> Unit)? = null,
) {
    val summary = liveResultSummary(terminal, humanSide)
    LiveDialogPanel("p5-live-result-dialog", onViewBoard) {
        LiveDialogTitle(summary.title)
        LiveDialogNote(summary.detail)
        if (onReview != null) LumenDerivativeAction("Game Review", onReview, Modifier.fillMaxWidth(), testTag = "p5-live-result-review")
        if (onAnalyze != null) LumenDerivativeAction("Analyze", onAnalyze, Modifier.fillMaxWidth(), testTag = "p5-live-result-analyze")
        LumenDerivativeAction("Rematch", onRematch, Modifier.fillMaxWidth(), testTag = "p5-live-result-rematch")
        LumenDerivativeAction("New game", onNewGame, Modifier.fillMaxWidth(), testTag = "p5-live-result-new")
        LumenDerivativeAction("View board", onViewBoard, Modifier.fillMaxWidth(), testTag = "p5-live-result-close")
    }
}

internal data class LiveResultSummary(val title: String, val detail: String)

/** Result wording from the human player's point of view. */
internal fun liveResultSummary(terminal: RuntimeTerminal, humanSide: Color): LiveResultSummary {
    fun outcome(humanWon: Boolean) = if (humanWon) "You won" else "You lost"
    return when (terminal) {
        is RuntimeTerminal.Checkmate -> LiveResultSummary(outcome(terminal.winner == humanSide), "Checkmate")
        is RuntimeTerminal.Timeout -> LiveResultSummary(
            outcome(terminal.loser != humanSide),
            "${terminal.loser.label()} ran out of time",
        )
        is RuntimeTerminal.Resignation -> LiveResultSummary(
            outcome(terminal.loser != humanSide),
            "${terminal.loser.label()} resigned",
        )
        RuntimeTerminal.DrawAgreement -> LiveResultSummary("Draw", "Draw agreed")
        RuntimeTerminal.Stalemate -> LiveResultSummary("Draw", "Stalemate")
        RuntimeTerminal.InsufficientMaterial -> LiveResultSummary("Draw", "Insufficient material")
        RuntimeTerminal.ThreefoldRepetition -> LiveResultSummary("Draw", "Threefold repetition")
        RuntimeTerminal.FiftyMoveRule -> LiveResultSummary("Draw", "Fifty-move rule")
    }
}

internal fun liveGameSummary(setup: ResolvedPlaySetup): List<Pair<String, String>> = listOf(
    "Opponent" to setup.engine.displayName,
    "Strength" to liveStrengthLabel(setup),
    "Time" to formatLiveTimeControl(setup.clockConfig),
    "Variant" to if (setup.variant == Variant.CHESS960) {
        "Chess960 #${setup.chess960Index ?: 518}"
    } else {
        "Standard"
    },
    "You play" to setup.humanSide.label(),
)

internal fun liveStrengthLabel(setup: ResolvedPlaySetup): String {
    val target = when (val value = setup.strength.target) {
        EngineStrengthTarget.FullStrength -> "Full strength"
        is EngineStrengthTarget.Elo -> "${value.value} Elo"
    }
    if (setup.strength.target == EngineStrengthTarget.FullStrength) return target
    val model = when (setup.strength.model) {
        EngineStrengthModel.ENGINE_NATIVE -> "Native"
        EngineStrengthModel.HUMANIZED -> "Humanized"
        EngineStrengthModel.HYBRID -> "Hybrid"
    }
    return "$target · $model"
}

/** "10+0" style notation; sub-minute bases read "30s+2". */
internal fun formatLiveTimeControl(clock: ClockConfig): String {
    if (!clock.enabled) return "Untimed"
    val base = if (clock.initialMillis % 60_000L == 0L) {
        "${clock.initialMillis / 60_000L}"
    } else {
        "${clock.initialMillis / 1_000L}s"
    }
    return "$base+${clock.incrementMillis / 1_000L}"
}

private fun Color.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

/** Under ten seconds the clock shows tenths, like a real chess clock; long controls show hours. */
internal fun formatLiveClock(millis: Long?): String {
    if (millis == null) return "--:--"
    val safe = millis.coerceAtLeast(0L)
    return when {
        safe < URGENT_CLOCK_MILLIS ->
            String.format(java.util.Locale.ROOT, "0:%02d.%d", safe / 1_000L, (safe % 1_000L) / 100L)
        safe >= 3_600_000L -> String.format(
            java.util.Locale.ROOT, "%d:%02d:%02d", safe / 3_600_000L, (safe % 3_600_000L) / 60_000L, (safe % 60_000L) / 1_000L,
        )
        else -> String.format(java.util.Locale.ROOT, "%d:%02d", safe / 60_000L, (safe % 60_000L) / 1_000L)
    }
}

internal fun isLiveClockUrgent(millis: Long?): Boolean = millis != null && millis < URGENT_CLOCK_MILLIS

private const val URGENT_CLOCK_MILLIS = 10_000L

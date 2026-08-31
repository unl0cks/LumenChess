package dev.lumenchess.arena

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.lumenchess.board.BoardMovePresentation
import dev.lumenchess.board.BoardMovePresentationClassifier
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.DerivativeSurfaceRole
import dev.lumenchess.design.LumenClock
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenDerivativePage
import dev.lumenchess.design.LumenDerivativeSurface
import dev.lumenchess.design.LumenEngineBadge
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.play.presentationLabel
import dev.lumenchess.runtime.RuntimeState

@Composable
fun ArenaRoute(
    viewModel: ArenaViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenStarted()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.onScreenStarted()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenStopped()
        }
    }

    when (ui.mode) {
        ArenaScreenMode.SETUP -> ArenaSetupScreen(ui, viewModel, modifier)
        ArenaScreenMode.LIVE -> ArenaLiveScreen(ui, viewModel, modifier)
    }
}

@Composable
private fun ArenaSetupScreen(ui: ArenaUiState, viewModel: ArenaViewModel, modifier: Modifier) {
    val setup = ui.setup
    val validationReason = when (val validation = ui.setupValidation) {
        ArenaSetupValidation.Valid -> null
        is ArenaSetupValidation.Invalid -> validation.reason
        is ArenaSetupValidation.UnsupportedStrength -> validation.reason
    }
    LumenDerivativePage(
        modifier = modifier,
        testTag = "arena-setup",
        scrollable = true,
        horizontalPadding = 16,
        verticalPadding = 16,
        spacing = 12,
    ) {
        Text(
            "Engine Arena",
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
            color = LumenColors.OnSurface,
        )
        Text(
            "Independent engines, one authoritative game.",
            style = MaterialTheme.typography.bodyMedium,
            color = LumenColors.OnSurfaceMuted,
        )

        ArenaSection("White engine", "arena-white-engine") {
            ArenaEngineControls(Color.WHITE, setup.white, viewModel)
        }
        ArenaSection("Black engine", "arena-black-engine") {
            ArenaEngineControls(Color.BLACK, setup.black, viewModel)
        }
        ArenaSection("Game", "arena-game-options") {
            ArenaChoiceRow(
                choices = listOf(Variant.STANDARD to "Standard", Variant.CHESS960 to "Chess960"),
                selected = setup.variant,
                onSelect = viewModel::updateVariant,
            )
            if (setup.variant == Variant.CHESS960 && setup.opening.mode != ArenaOpeningMode.RANDOM_CHESS960) {
                ArenaNumberField("Chess960 index", setup.chess960Index?.toString().orEmpty()) { raw ->
                    raw.toIntOrNull()?.let(viewModel::updateChess960Index)
                }
            }
            Text("Color assignment", style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)
            ArenaChoiceRow(
                choices = listOf(ArenaColorAssignment.FIXED to "Fixed", ArenaColorAssignment.RANDOM to "Random"),
                selected = setup.colorAssignment,
                onSelect = viewModel::updateColorAssignment,
            )
            Text("Time control", style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)
            ArenaChoiceRow(
                choices = listOf(
                    PlayTimeControl(180_000, 2_000) to "3 + 2",
                    PlayTimeControl(600_000, 0) to "10 min",
                    PlayTimeControl(900_000, 10_000) to "15 + 10",
                ),
                selected = setup.timeControl,
                onSelect = viewModel::updateTimeControl,
            )
        }
        ArenaSection("Opening", "arena-opening-options") {
            ArenaChoiceColumn(
                choices = listOf(
                    ArenaOpeningMode.NORMAL to "Normal start",
                    ArenaOpeningMode.RANDOM_OPENING to "Random opening",
                    ArenaOpeningMode.OPENING_FAMILY to "Opening family",
                    ArenaOpeningMode.CUSTOM_FEN to "Custom FEN",
                    ArenaOpeningMode.RANDOM_CHESS960 to "Random Chess960",
                ),
                selected = setup.opening.mode,
                onSelect = viewModel::updateOpeningMode,
            )
            if (setup.opening.mode == ArenaOpeningMode.RANDOM_OPENING || setup.opening.mode == ArenaOpeningMode.OPENING_FAMILY) {
                Text("Engine handoff", style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)
                ArenaChoiceRow(
                    choices = listOf(4 to "4 ply", 8 to "8 ply", 12 to "12 ply"),
                    selected = setup.opening.handoffPlies,
                    onSelect = viewModel::updateOpeningHandoff,
                )
            }
            if (setup.opening.mode == ArenaOpeningMode.OPENING_FAMILY) {
                ArenaChoiceRow(
                    choices = ArenaOpeningCatalog.families.map { it.key to it.value },
                    selected = setup.opening.familyId,
                    onSelect = viewModel::updateOpeningFamily,
                )
            }
            if (setup.opening.mode == ArenaOpeningMode.CUSTOM_FEN) {
                OutlinedTextField(
                    value = setup.opening.customFen,
                    onValueChange = viewModel::updateCustomFen,
                    modifier = Modifier.fillMaxWidth().testTag("arena-custom-fen"),
                    label = { Text("FEN") },
                    singleLine = false,
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LumenColors.OnSurface,
                        unfocusedTextColor = LumenColors.OnSurface,
                        focusedBorderColor = LumenColors.AccentBlueBright,
                        unfocusedBorderColor = LumenColors.OutlineStrong,
                        focusedLabelColor = LumenColors.AccentBlueBright,
                        unfocusedLabelColor = LumenColors.OnSurfaceMuted,
                        cursorColor = LumenColors.AccentBlueBright,
                    ),
                )
            }
        }

        if (validationReason != null || ui.message != null) {
            Text(
                validationReason ?: ui.message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = LumenColors.Destructive,
                modifier = Modifier.testTag("arena-validation"),
            )
        }
        ui.restorableGame?.let { restored ->
            LumenDerivativeSurface(
                role = DerivativeSurfaceRole.NEUTRAL_ROW,
                onClick = viewModel::resumeLastArena,
                modifier = Modifier.fillMaxWidth(),
                testTag = "arena-resume",
            ) {
                Column {
                    Text("Resume Arena", color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${restored.setup.white.engine.displayName} vs ${restored.setup.black.engine.displayName}",
                        color = LumenColors.OnSurfaceMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        LumenDerivativeSurface(
            role = DerivativeSurfaceRole.ACTION,
            enabled = validationReason == null,
            onClick = viewModel::startNewArena,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            testTag = "arena-start",
            contentAlignment = Alignment.Center,
        ) {
            Text("Start Arena", color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ArenaEngineControls(side: Color, config: ArenaEngineConfig, viewModel: ArenaViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ArenaChoiceRow(
            choices = PlayEngine.entries.map { it to it.displayName },
            selected = config.engine,
            onSelect = { viewModel.updateEngine(side, it) },
        )
        Text("Strength", style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)
        ArenaChoiceRow(
            choices = listOf(
                EngineStrengthTarget.Elo(1200) to "1200",
                EngineStrengthTarget.Elo(1600) to "1600",
                EngineStrengthTarget.Elo(2000) to "2000",
                EngineStrengthTarget.FullStrength to "Full",
            ),
            selected = config.strengthTarget,
            onSelect = { viewModel.updateStrengthTarget(side, it) },
        )
        ArenaChoiceRow(
            choices = listOf(
                EngineStrengthModel.HYBRID to "Hybrid",
                EngineStrengthModel.ENGINE_NATIVE to "Native",
                EngineStrengthModel.HUMANIZED to "Humanized",
            ),
            selected = config.strengthModel,
            onSelect = { viewModel.updateStrengthModel(side, it) },
        )
    }
}

@Composable
private fun ArenaSection(title: String, tag: String, content: @Composable () -> Unit) {
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.PREVIEW_PANEL,
        modifier = Modifier.fillMaxWidth(),
        testTag = tag,
        contentPadding = PaddingValues(13.dp),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun <T> ArenaChoiceRow(choices: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { (value, label) ->
            ArenaChoice(value, label, selected == value, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun <T> ArenaChoiceColumn(choices: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        choices.forEach { (value, label) -> ArenaChoice(value, label, selected == value, onSelect, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun <T> ArenaChoice(value: T, label: String, selected: Boolean, onSelect: (T) -> Unit, modifier: Modifier) {
    LumenDerivativeSurface(
        role = if (selected) DerivativeSurfaceRole.SELECTED_FACE else DerivativeSurfaceRole.NEUTRAL_ROW,
        onClick = { onSelect(value) },
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArenaNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LumenColors.OnSurface,
            unfocusedTextColor = LumenColors.OnSurface,
            focusedBorderColor = LumenColors.AccentBlueBright,
            unfocusedBorderColor = LumenColors.OutlineStrong,
            focusedLabelColor = LumenColors.AccentBlueBright,
            unfocusedLabelColor = LumenColors.OnSurfaceMuted,
            cursorColor = LumenColors.AccentBlueBright,
        ),
    )
}

@Composable
private fun ArenaLiveScreen(ui: ArenaUiState, viewModel: ArenaViewModel, modifier: Modifier) {
    val runtime = ui.runtime ?: return
    val setup = ui.resolvedSetup ?: return
    BackHandler(onBack = viewModel::stopArena)
    val lastMove = runtime.gameTree.mainline().lastOrNull()?.move
    var presentedRevision by remember { mutableLongStateOf(runtime.positionRevision.value) }
    val revisionDelta = (runtime.positionRevision.value - presentedRevision).coerceAtLeast(0L)
    val movePresentation = if (revisionDelta == 0L) BoardMovePresentation.ENGINE else {
        BoardMovePresentationClassifier.classify(revisionDelta, lastMoverIsHuman = false)
    }
    SideEffect { presentedRevision = runtime.positionRevision.value }

    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .testTag("arena-live"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val upperSide = if (ui.orientation == ChessboardOrientation.WHITE) Color.BLACK else Color.WHITE
        val lowerSide = upperSide.opposite
        ArenaParticipantRow(upperSide, ui, runtime, setup)
        ArenaEvaluationBar(ui.evaluation)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .92f))
                .testTag("arena-board-stage"),
        ) {
            LumenChessboard(
                position = runtime.position,
                onMove = {},
                modifier = Modifier.fillMaxSize(),
                orientation = ui.orientation,
                input = ChessboardInput(tapEnabled = false, dragEnabled = false),
                highlights = ChessboardHighlights(
                    lastMove = lastMove,
                    positionRevision = runtime.positionRevision.value,
                    movePresentation = movePresentation,
                ),
            )
        }
        ArenaParticipantRow(lowerSide, ui, runtime, setup)
        ui.message?.let {
            Text(it, color = LumenColors.Destructive, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
        runtime.terminal?.let {
            Text(it.presentationLabel(), color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().height(54.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ArenaAction("Flip", "arena-flip", viewModel::flipBoard, Modifier.weight(1f))
            ArenaAction(
                if (runtime.paused) "Resume" else "Pause",
                "arena-pause",
                if (runtime.paused) viewModel::resume else viewModel::pause,
                Modifier.weight(1f),
            )
            ArenaAction("Stop", "arena-stop", viewModel::stopArena, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArenaParticipantRow(
    side: Color,
    ui: ArenaUiState,
    runtime: RuntimeState,
    setup: ResolvedArenaSetup,
) {
    val engine = if (side == Color.WHITE) setup.white.engine else setup.black.engine
    val status = if (side == Color.WHITE) ui.whiteEngineStatus else ui.blackEngineStatus
    val active = runtime.position.sideToMove == side
    val millis = if (side == Color.WHITE) ui.clock?.whiteRemainingMillis else ui.clock?.blackRemainingMillis
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (active) LumenColors.SurfaceHighest else LumenColors.Surface, shape)
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("arena-${side.name.lowercase()}-row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { LumenEngineBadge(engine.displayName) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(engine.displayName, color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${side.name.lowercase().replaceFirstChar { it.uppercase() }} · ${if (active && !runtime.paused) "Thinking" else status}",
                color = LumenColors.OnSurfaceMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LumenClock(
            arenaClockText(millis),
            active = active && !runtime.paused,
            light = side == Color.WHITE,
            modifier = Modifier.width(94.dp).height(44.dp).testTag("arena-${side.name.lowercase()}-clock"),
        )
    }
}

@Composable
private fun ArenaAction(label: String, tag: String, onClick: () -> Unit, modifier: Modifier) {
    LumenDerivativeSurface(
        role = DerivativeSurfaceRole.ACTION,
        onClick = onClick,
        modifier = modifier,
        testTag = tag,
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold)
    }
}

private fun arenaClockText(millis: Long?): String {
    if (millis == null) return "--:--"
    val safe = millis.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60_000L, (safe % 60_000L) / 1_000L)
}

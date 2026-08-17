package dev.lumenchess.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.design.LumenColors
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.clock.ClockReading
import dev.lumenchess.runtime.clock.ClockSide
import kotlin.math.floor
import kotlin.math.roundToInt

const val PLAY_SETUP_TEST_TAG = "play-setup"
const val PLAY_LIVE_TEST_TAG = "play-live"
const val PLAY_START_TEST_TAG = "play-start"
const val PLAY_RESUME_TEST_TAG = "play-resume"
const val PLAY_ENGINE_STATUS_TEST_TAG = "play-engine-status"
const val PLAY_PREMOVE_OVERLAY_TEST_TAG = "play-premove-overlay"

@Composable
fun PlayRoute(
    modifier: Modifier = Modifier,
    viewModel: PlayViewModel = viewModel(),
) {
    val ui by viewModel.uiState
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, ui.mode) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenStarted()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onScreenStarted()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenStopped()
        }
    }

    when (ui.mode) {
        PlayScreenMode.SETUP -> PlaySetupScreen(ui, viewModel, modifier)
        PlayScreenMode.LIVE -> PlayLiveScreen(ui, viewModel, modifier)
    }
}

@Composable
private fun PlaySetupScreen(
    ui: PlayUiState,
    viewModel: PlayViewModel,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .testTag(PLAY_SETUP_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Play", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Human vs engine",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ui.restorableGame?.let { restored ->
            Card(colors = CardDefaults.cardColors(containerColor = LumenColors.SurfaceRaised)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue game", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${restored.setup.variant.displayName()} · ${restored.setup.engine.displayName}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        modifier = Modifier.testTag(PLAY_RESUME_TEST_TAG),
                        onClick = viewModel::resumeLastGame,
                    ) { Text("Resume") }
                }
            }
        }

        SetupSection("Variant") {
            ChoiceRow {
                ChoiceChip("Standard", ui.setup.variant == Variant.STANDARD) {
                    viewModel.updateVariant(Variant.STANDARD)
                }
                ChoiceChip("Chess960", ui.setup.variant == Variant.CHESS960) {
                    viewModel.updateVariant(Variant.CHESS960)
                }
            }
            if (ui.setup.variant == Variant.CHESS960) {
                val index = ui.setup.chess960Index ?: 518
                Text("Position #$index", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = index.toFloat(),
                    onValueChange = { viewModel.updateChess960Index(it.roundToInt().coerceIn(0, 959)) },
                    valueRange = 0f..959f,
                    steps = 958,
                )
            }
        }

        SetupSection("Engine") {
            ChoiceRow {
                PlayEngine.entries.forEach { engine ->
                    ChoiceChip(engine.displayName, ui.setup.engine == engine) { viewModel.updateEngine(engine) }
                }
            }
        }

        SetupSection("Side") {
            ChoiceRow {
                PlaySide.entries.forEach { side ->
                    ChoiceChip(side.displayName(), ui.setup.side == side) { viewModel.updateSide(side) }
                }
            }
        }

        SetupSection("Strength") {
            ChoiceRow {
                ChoiceChip("Elo", ui.setup.strengthTarget is EngineStrengthTarget.Elo) {
                    viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(1600))
                }
                ChoiceChip("Full strength", ui.setup.strengthTarget == EngineStrengthTarget.FullStrength) {
                    viewModel.updateStrengthTarget(EngineStrengthTarget.FullStrength)
                }
            }
            val elo = (ui.setup.strengthTarget as? EngineStrengthTarget.Elo)?.value
            if (elo != null) {
                Text("$elo Elo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = elo.toFloat(),
                    onValueChange = {
                        val snapped = ((it / 50f).roundToInt() * 50).coerceIn(400, 3000)
                        viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(snapped))
                    },
                    valueRange = 400f..3000f,
                )
                ChoiceRow {
                    EngineStrengthModel.entries.forEach { model ->
                        ChoiceChip(model.displayName(), ui.setup.strengthModel == model) {
                            viewModel.updateStrengthModel(model)
                        }
                    }
                }
            }
        }

        SetupSection("Time control") {
            ChoiceRow {
                TIME_CONTROLS.forEach { option ->
                    ChoiceChip(
                        option.label,
                        ui.setup.timeControl == option.control,
                    ) { viewModel.updateTimeControl(option.control) }
                }
            }
        }

        val validationText = when (val validation = ui.setupValidation) {
            PlaySetupValidation.Valid -> null
            is PlaySetupValidation.Invalid -> validation.reason
            is PlaySetupValidation.UnsupportedStrength -> validation.reason
        }
        if (validationText != null) {
            Text(
                validationText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = viewModel::startNewGame,
            enabled = ui.setupValidation is PlaySetupValidation.Valid,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(PLAY_START_TEST_TAG),
        ) {
            Text("Start game")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PlayLiveScreen(
    ui: PlayUiState,
    viewModel: PlayViewModel,
    modifier: Modifier,
) {
    val runtime = ui.runtime ?: return
    val setup = viewModel.currentCoordinatorForTest()?.setup ?: return
    val humanSide = setup.humanSide
    val engineSide = humanSide.opposite
    val orientation = if (humanSide == Color.WHITE) ChessboardOrientation.WHITE else ChessboardOrientation.BLACK
    val humanTurn = runtime.position.sideToMove == humanSide &&
        runtime.controllers.forSide(humanSide) == RuntimeController.HUMAN
    val inputEnabled = humanTurn && !runtime.paused && runtime.terminal == null
    val premoveEnabled = !humanTurn && !runtime.paused && runtime.terminal == null
    val lastMove = runtime.gameTree.mainline().lastOrNull()?.move
    val queuedPremove = runtime.queuedPremove?.move

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(PLAY_LIVE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerCard(
            name = setup.engine.displayName,
            detail = ui.engineStatus,
            side = engineSide,
            activeSide = runtime.position.sideToMove,
            clock = ui.clock,
            modifier = Modifier.testTag(PLAY_ENGINE_STATUS_TEST_TAG),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth()) {
                LumenChessboard(
                    position = runtime.position,
                    onMove = viewModel::onBoardMove,
                    orientation = orientation,
                    input = ChessboardInput(
                        tapEnabled = inputEnabled,
                        dragEnabled = inputEnabled,
                    ),
                    highlights = ChessboardHighlights(
                        lastMove = lastMove,
                        premoveSquares = queuedPremove?.let { setOf(it.from, it.to) }.orEmpty(),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (premoveEnabled) {
                    PremoveInputOverlay(
                        position = runtime,
                        humanSide = humanSide,
                        orientation = orientation,
                        onPremove = viewModel::queuePremove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (queuedPremove != null) {
            Text(
                "Premove ${queuedPremove.uci} queued · 100 ms if played",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        PlayerCard(
            name = "You",
            detail = humanSide.name.lowercase().replaceFirstChar { it.uppercase() },
            side = humanSide,
            activeSide = runtime.position.sideToMove,
            clock = ui.clock,
        )

        runtime.terminal?.let { terminal ->
            Text(
                terminal.presentationLabel(),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        ui.message?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (queuedPremove != null) {
                OutlinedButton(onClick = viewModel::cancelPremove) { Text("Cancel premove") }
            }
            if (runtime.terminal == null) {
                OutlinedButton(onClick = if (runtime.paused) viewModel::resume else viewModel::pause) {
                    Text(if (runtime.paused) "Resume" else "Pause")
                }
                OutlinedButton(onClick = viewModel::resign) { Text("Resign") }
            }
            OutlinedButton(onClick = viewModel::backToSetup) { Text("Exit") }
        }
    }
}

@Composable
private fun PlayerCard(
    name: String,
    detail: String,
    side: Color,
    activeSide: Color,
    clock: ClockReading?,
    modifier: Modifier = Modifier,
) {
    val millis = when (side) {
        Color.WHITE -> clock?.whiteRemainingMillis
        Color.BLACK -> clock?.blackRemainingMillis
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (side == activeSide) LumenColors.SurfaceRaised else LumenColors.Surface,
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                formatClock(millis),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics {
                    contentDescription = "$name clock ${formatClockForAccessibility(millis)}"
                },
            )
        }
    }
}

@Composable
private fun SetupSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LumenColors.Surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            content()
        }
    }
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier
            .weight(1f, fill = false)
            .sizeIn(minHeight = 48.dp),
    )
}

@Composable
private fun PremoveInputOverlay(
    position: RuntimeState,
    humanSide: Color,
    orientation: ChessboardOrientation,
    onPremove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    var from by remember(position.positionRevision) { mutableStateOf<Square?>(null) }
    LaunchedEffect(position.queuedPremove) {
        if (position.queuedPremove == null) from = null
    }
    Box(
        modifier = modifier
            .semantics { contentDescription = "Premove input board" }
            .testTag(PLAY_PREMOVE_OVERLAY_TEST_TAG)
            .pointerInput(position.positionRevision, orientation, humanSide) {
                detectTapGestures { offset ->
                    val square = premoveSquareFromOffset(offset, size, orientation) ?: return@detectTapGestures
                    val selected = from
                    if (selected == null) {
                        if (position.position[square]?.color == humanSide) from = square
                    } else if (position.position[square]?.color == humanSide) {
                        from = square
                    } else {
                        val piece = position.position[selected]
                        val promotion = if (
                            piece?.type == PieceType.PAWN &&
                            square.rank == if (humanSide == Color.WHITE) 7 else 0
                        ) PieceType.QUEEN else null
                        onPremove(Move(selected, square, promotion))
                        from = null
                    }
                }
            },
    )
}

private fun premoveSquareFromOffset(
    offset: Offset,
    size: IntSize,
    orientation: ChessboardOrientation,
): Square? {
    if (size.width <= 0 || size.height <= 0 || offset.x !in 0f..size.width.toFloat() || offset.y !in 0f..size.height.toFloat()) {
        return null
    }
    val visualFile = floor(offset.x / (size.width / 8f)).toInt().coerceIn(0, 7)
    val visualRank = floor(offset.y / (size.height / 8f)).toInt().coerceIn(0, 7)
    return when (orientation) {
        ChessboardOrientation.WHITE -> Square.of(visualFile, 7 - visualRank)
        ChessboardOrientation.BLACK -> Square.of(7 - visualFile, visualRank)
    }
}

private data class TimeControlOption(val label: String, val control: PlayTimeControl)

private val TIME_CONTROLS = listOf(
    TimeControlOption("1+0", PlayTimeControl(60_000L, 0L)),
    TimeControlOption("3+2", PlayTimeControl(180_000L, 2_000L)),
    TimeControlOption("5+0", PlayTimeControl(300_000L, 0L)),
    TimeControlOption("10+0", PlayTimeControl(600_000L, 0L)),
)

private fun Variant.displayName(): String = if (this == Variant.STANDARD) "Standard" else "Chess960"
private fun PlaySide.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
private fun EngineStrengthModel.displayName(): String = when (this) {
    EngineStrengthModel.ENGINE_NATIVE -> "Native"
    EngineStrengthModel.HUMANIZED -> "Humanized"
    EngineStrengthModel.HYBRID -> "Hybrid"
}

private fun formatClock(millis: Long?): String {
    if (millis == null) return "--:--"
    val safe = millis.coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    return "%d:%02d".format(minutes, seconds)
}

private fun formatClockForAccessibility(millis: Long?): String {
    if (millis == null) return "unavailable"
    val safe = millis.coerceAtLeast(0L)
    return "${safe / 60_000L} minutes ${(safe % 60_000L) / 1_000L} seconds"
}

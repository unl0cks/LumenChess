package dev.lumenchess.play

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun PolishedPlayRoute(
    modifier: Modifier = Modifier,
    viewModel: PlayViewModel,
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
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onScreenStarted()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenStopped()
        }
    }

    when (ui.mode) {
        PlayScreenMode.SETUP -> P2SetupScreen(ui, viewModel, modifier)
        PlayScreenMode.LIVE -> P2LiveScreen(ui, viewModel, modifier)
    }
}

@Composable
private fun P2SetupScreen(
    ui: PlayUiState,
    viewModel: PlayViewModel,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LumenColors.BackgroundLift, LumenColors.Background),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .testTag(PLAY_SETUP_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text(
                "LUMEN PLAY",
                style = MaterialTheme.typography.labelSmall,
                color = LumenColors.AccentBlueBright,
            )
            Text("Human vs Engine", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Configure a clean offline match.",
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.OnSurfaceMuted,
            )

            ui.restorableGame?.let { restored ->
                Surface(
                    color = LumenColors.SurfaceRaised,
                    shape = RoundedCornerShape(17.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Continue game", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${p2VariantLabel(restored.setup.variant)} · ${restored.setup.engine.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = LumenColors.OnSurfaceMuted,
                            )
                        }
                        Button(
                            onClick = viewModel::resumeLastGame,
                            modifier = Modifier.testTag(PLAY_RESUME_TEST_TAG),
                        ) {
                            Text("Resume")
                        }
                    }
                }
            }

            P2SetupGroup("GAME") {
                P2FieldLabel("Variant")
                P2SegmentRow {
                    P2Segment("Standard", ui.setup.variant == Variant.STANDARD) {
                        viewModel.updateVariant(Variant.STANDARD)
                    }
                    P2Segment("Chess960", ui.setup.variant == Variant.CHESS960) {
                        viewModel.updateVariant(Variant.CHESS960)
                    }
                }
                if (ui.setup.variant == Variant.CHESS960) {
                    val index = ui.setup.chess960Index ?: 518
                    P2ValueLine("Starting position", "#$index")
                    Slider(
                        value = index.toFloat(),
                        onValueChange = {
                            viewModel.updateChess960Index(it.roundToInt().coerceIn(0, 959))
                        },
                        valueRange = 0f..959f,
                        steps = 958,
                    )
                }
            }

            P2SetupGroup("OPPONENT") {
                P2FieldLabel("Engine")
                P2SegmentRow {
                    PlayEngine.entries.forEach { engine ->
                        P2Segment(engine.displayName, ui.setup.engine == engine) {
                            viewModel.updateEngine(engine)
                        }
                    }
                }
                P2FieldLabel("Play as")
                P2SegmentRow {
                    PlaySide.entries.forEach { side ->
                        P2Segment(p2SideLabel(side), ui.setup.side == side) {
                            viewModel.updateSide(side)
                        }
                    }
                }
            }

            P2SetupGroup("STRENGTH") {
                P2SegmentRow {
                    P2Segment("Elo", ui.setup.strengthTarget is EngineStrengthTarget.Elo) {
                        viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(1600))
                    }
                    P2Segment(
                        "Full strength",
                        ui.setup.strengthTarget == EngineStrengthTarget.FullStrength,
                    ) {
                        viewModel.updateStrengthTarget(EngineStrengthTarget.FullStrength)
                    }
                }
                (ui.setup.strengthTarget as? EngineStrengthTarget.Elo)?.let { target ->
                    P2ValueLine("Target strength", "${target.value} Elo")
                    Slider(
                        value = target.value.toFloat(),
                        onValueChange = {
                            val snapped = ((it / 50f).roundToInt() * 50).coerceIn(400, 3000)
                            viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(snapped))
                        },
                        valueRange = 400f..3000f,
                    )
                    P2FieldLabel("Strength model")
                    P2SegmentRow {
                        EngineStrengthModel.entries.forEach { model ->
                            P2Segment(p2ModelLabel(model), ui.setup.strengthModel == model) {
                                viewModel.updateStrengthModel(model)
                            }
                        }
                    }
                }
            }

            P2SetupGroup("CLOCK") {
                P2FieldLabel("Time control")
                P2SegmentRow {
                    P2_TIME_CONTROLS.forEach { option ->
                        P2Segment(option.label, ui.setup.timeControl == option.control) {
                            viewModel.updateTimeControl(option.control)
                        }
                    }
                }
            }

            val validationMessage = when (val validation = ui.setupValidation) {
                PlaySetupValidation.Valid -> null
                is PlaySetupValidation.Invalid -> validation.reason
                is PlaySetupValidation.UnsupportedStrength -> validation.reason
            }
            (validationMessage ?: ui.message)?.let { message ->
                Surface(
                    color = LumenColors.DestructiveSoft,
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        color = LumenColors.Destructive,
                    )
                }
            }

            Button(
                onClick = viewModel::startNewGame,
                enabled = ui.setupValidation is PlaySetupValidation.Valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(PLAY_START_TEST_TAG),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.AccentBlue),
            ) {
                Text("Start game", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun P2SetupGroup(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = LumenColors.OnSurfaceFaint,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LumenColors.Surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(17.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun P2FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.OnSurfaceMuted,
    )
}

@Composable
private fun P2ValueLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        P2FieldLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = LumenColors.AccentBlueBright,
        )
    }
}

@Composable
private fun P2SegmentRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun RowScope.P2Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) LumenColors.AccentBlueSoft else LumenColors.SurfaceRaised,
        label = "p2-segment-background",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
        label = "p2-segment-foreground",
    )
    Surface(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        color = background,
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun P2LiveScreen(
    ui: PlayUiState,
    viewModel: PlayViewModel,
    modifier: Modifier,
) {
    val runtime = ui.runtime ?: return
    val setup = ui.resolvedSetup ?: return
    val humanSide = setup.humanSide
    val engineSide = humanSide.opposite
    val orientation = if (humanSide == Color.WHITE) {
        ChessboardOrientation.WHITE
    } else {
        ChessboardOrientation.BLACK
    }
    val humanTurn = runtime.position.sideToMove == humanSide &&
        runtime.controllers.forSide(humanSide) == RuntimeController.HUMAN
    val inputEnabled = humanTurn && !runtime.paused && runtime.terminal == null
    val premoveEnabled = !humanTurn && !runtime.paused && runtime.terminal == null
    val lastMove = runtime.gameTree.mainline().lastOrNull()?.move
    val queuedPremove = runtime.queuedPremove?.move
    val terminal = runtime.terminal
    val status = when {
        ui.message != null -> ui.message
        terminal != null -> terminal.presentationLabel()
        queuedPremove != null -> "Premove ${queuedPremove.uci} queued · 100 ms if played"
        runtime.paused -> "Game paused"
        else -> ""
    }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(LumenColors.BackgroundLift, LumenColors.Background),
                ),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(PLAY_LIVE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        P2ParticipantStrip(
            name = setup.engine.displayName,
            detail = ui.engineStatus,
            side = engineSide,
            activeSide = runtime.position.sideToMove,
            clock = ui.clock,
            engine = true,
            modifier = Modifier.testTag(PLAY_ENGINE_STATUS_TEST_TAG),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .testTag(PLAY_BOARD_STAGE_TEST_TAG),
            ) {
                LumenChessboard(
                    position = runtime.position,
                    onMove = viewModel::onBoardMove,
                    modifier = Modifier.matchParentSize(),
                    orientation = orientation,
                    input = ChessboardInput(
                        tapEnabled = inputEnabled,
                        dragEnabled = inputEnabled,
                    ),
                    highlights = ChessboardHighlights(
                        lastMove = lastMove,
                        premoveSquares = queuedPremove
                            ?.let { setOf(it.from, it.to) }
                            .orEmpty(),
                    ),
                )
                if (premoveEnabled) {
                    P2PremoveOverlay(
                        runtime = runtime,
                        humanSide = humanSide,
                        orientation = orientation,
                        onPremove = viewModel::queuePremove,
                        modifier = Modifier.matchParentSize(),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.message != null) {
                    LumenColors.Destructive
                } else {
                    LumenColors.OnSurfaceMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        P2ParticipantStrip(
            name = "You",
            detail = humanSide.name.lowercase().replaceFirstChar { it.uppercase() },
            side = humanSide,
            activeSide = runtime.position.sideToMove,
            clock = ui.clock,
            engine = false,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (queuedPremove != null) {
                P2ActionButton("Cancel", P2ActionGlyph.CANCEL, onClick = viewModel::cancelPremove)
            }
            if (terminal == null) {
                P2ActionButton(
                    if (runtime.paused) "Resume" else "Pause",
                    if (runtime.paused) P2ActionGlyph.PLAY else P2ActionGlyph.PAUSE,
                    onClick = if (runtime.paused) viewModel::resume else viewModel::pause,
                )
                P2ActionButton(
                    "Resign",
                    P2ActionGlyph.FLAG,
                    destructive = true,
                    onClick = viewModel::resign,
                )
            }
            P2ActionButton("Exit", P2ActionGlyph.EXIT, onClick = viewModel::backToSetup)
        }
    }
}

@Composable
private fun P2ParticipantStrip(
    name: String,
    detail: String,
    side: Color,
    activeSide: Color,
    clock: ClockReading?,
    engine: Boolean,
    modifier: Modifier = Modifier,
) {
    val millis = when (side) {
        Color.WHITE -> clock?.whiteRemainingMillis
        Color.BLACK -> clock?.blackRemainingMillis
    }
    val active = side == activeSide
    val containerColor by animateColorAsState(
        targetValue = if (active) LumenColors.SurfaceRaised else LumenColors.Surface,
        label = "p2-participant",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp),
        color = containerColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                color = if (active) LumenColors.AccentBlueSoft else LumenColors.SurfaceHighest,
                shape = CircleShape,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (engine) "◆" else "●",
                        color = if (active) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = LumenColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                color = if (active) LumenColors.AccentBlueSoft else LumenColors.SurfaceHighest,
                shape = RoundedCornerShape(11.dp),
            ) {
                Text(
                    text = p2ClockText(millis),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .semantics {
                            contentDescription = "$name clock ${p2ClockAccessibility(millis)}"
                        },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private enum class P2ActionGlyph { PLAY, PAUSE, FLAG, EXIT, CANCEL }

@Composable
private fun RowScope.P2ActionButton(
    label: String,
    glyph: P2ActionGlyph,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        color = if (destructive) LumenColors.DestructiveSoft else LumenColors.Surface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            P2ActionIcon(
                glyph = glyph,
                color = if (destructive) LumenColors.Destructive else LumenColors.OnSurfaceMuted,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (destructive) LumenColors.Destructive else LumenColors.OnSurface,
            )
        }
    }
}

@Composable
private fun P2ActionIcon(
    glyph: P2ActionGlyph,
    color: androidx.compose.ui.graphics.Color,
) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = size.minDimension * 0.10f
        when (glyph) {
            P2ActionGlyph.PLAY -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.30f, size.height * 0.20f)
                    lineTo(size.width * 0.78f, size.height * 0.50f)
                    lineTo(size.width * 0.30f, size.height * 0.80f)
                    close()
                }
                drawPath(path, color)
            }
            P2ActionGlyph.PAUSE -> {
                drawLine(
                    color,
                    Offset(size.width * 0.35f, size.height * 0.20f),
                    Offset(size.width * 0.35f, size.height * 0.80f),
                    stroke * 1.7f,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.65f, size.height * 0.20f),
                    Offset(size.width * 0.65f, size.height * 0.80f),
                    stroke * 1.7f,
                    StrokeCap.Round,
                )
            }
            P2ActionGlyph.FLAG -> {
                drawLine(
                    color,
                    Offset(size.width * 0.30f, size.height * 0.15f),
                    Offset(size.width * 0.30f, size.height * 0.85f),
                    stroke,
                    StrokeCap.Round,
                )
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.32f, size.height * 0.20f)
                    lineTo(size.width * 0.78f, size.height * 0.32f)
                    lineTo(size.width * 0.32f, size.height * 0.48f)
                    close()
                }
                drawPath(path, color)
            }
            P2ActionGlyph.EXIT -> {
                drawLine(
                    color,
                    Offset(size.width * 0.20f, size.height * 0.50f),
                    Offset(size.width * 0.76f, size.height * 0.50f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.58f, size.height * 0.32f),
                    Offset(size.width * 0.78f, size.height * 0.50f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.58f, size.height * 0.68f),
                    Offset(size.width * 0.78f, size.height * 0.50f),
                    stroke,
                    StrokeCap.Round,
                )
            }
            P2ActionGlyph.CANCEL -> {
                drawLine(
                    color,
                    Offset(size.width * 0.25f, size.height * 0.25f),
                    Offset(size.width * 0.75f, size.height * 0.75f),
                    stroke,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.75f, size.height * 0.25f),
                    Offset(size.width * 0.25f, size.height * 0.75f),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun P2PremoveOverlay(
    runtime: RuntimeState,
    humanSide: Color,
    orientation: ChessboardOrientation,
    onPremove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    var from by remember(runtime.positionRevision) { mutableStateOf<Square?>(null) }
    LaunchedEffect(runtime.queuedPremove) {
        if (runtime.queuedPremove == null) from = null
    }
    Box(
        modifier = modifier
            .semantics { contentDescription = "Premove input board" }
            .testTag(PLAY_PREMOVE_OVERLAY_TEST_TAG)
            .pointerInput(runtime.positionRevision, orientation, humanSide) {
                detectTapGestures { offset ->
                    val square = p2SquareFromOffset(offset, size, orientation)
                        ?: return@detectTapGestures
                    val selected = from
                    if (selected == null) {
                        if (runtime.position[square]?.color == humanSide) from = square
                    } else if (runtime.position[square]?.color == humanSide) {
                        from = square
                    } else {
                        val piece = runtime.position[selected]
                        val promotion = if (
                            piece?.type == PieceType.PAWN &&
                            square.rank == if (humanSide == Color.WHITE) 7 else 0
                        ) {
                            PieceType.QUEEN
                        } else {
                            null
                        }
                        onPremove(Move(selected, square, promotion))
                        from = null
                    }
                }
            },
    )
}

private fun p2SquareFromOffset(
    offset: Offset,
    size: IntSize,
    orientation: ChessboardOrientation,
): Square? {
    if (
        size.width <= 0 ||
        size.height <= 0 ||
        offset.x !in 0f..size.width.toFloat() ||
        offset.y !in 0f..size.height.toFloat()
    ) {
        return null
    }
    val visualFile = floor(offset.x / (size.width / 8f)).toInt().coerceIn(0, 7)
    val visualRank = floor(offset.y / (size.height / 8f)).toInt().coerceIn(0, 7)
    return when (orientation) {
        ChessboardOrientation.WHITE -> Square.of(visualFile, 7 - visualRank)
        ChessboardOrientation.BLACK -> Square.of(7 - visualFile, visualRank)
    }
}

private data class P2TimeControlOption(
    val label: String,
    val control: PlayTimeControl,
)

private val P2_TIME_CONTROLS = listOf(
    P2TimeControlOption("1+0", PlayTimeControl(60_000L, 0L)),
    P2TimeControlOption("3+2", PlayTimeControl(180_000L, 2_000L)),
    P2TimeControlOption("5+0", PlayTimeControl(300_000L, 0L)),
    P2TimeControlOption("10+0", PlayTimeControl(600_000L, 0L)),
)

private fun p2VariantLabel(value: Variant): String = when (value) {
    Variant.STANDARD -> "Standard"
    Variant.CHESS960 -> "Chess960"
}

private fun p2SideLabel(value: PlaySide): String =
    value.name.lowercase().replaceFirstChar { it.uppercase() }

private fun p2ModelLabel(value: EngineStrengthModel): String = when (value) {
    EngineStrengthModel.ENGINE_NATIVE -> "Native"
    EngineStrengthModel.HUMANIZED -> "Humanized"
    EngineStrengthModel.HYBRID -> "Hybrid"
}

private fun p2ClockText(millis: Long?): String {
    if (millis == null) return "--:--"
    val safe = millis.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60_000L, (safe % 60_000L) / 1_000L)
}

private fun p2ClockAccessibility(millis: Long?): String {
    if (millis == null) return "unavailable"
    val safe = millis.coerceAtLeast(0L)
    return "${safe / 60_000L} minutes ${(safe % 60_000L) / 1_000L} seconds"
}

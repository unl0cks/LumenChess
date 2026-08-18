package dev.lumenchess.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.Path
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
import dev.lumenchess.design.LumenClock
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenEngineBadge
import dev.lumenchess.design.LumenPanel
import dev.lumenchess.design.LumenPrimaryButton
import dev.lumenchess.design.LumenSecondaryButton
import dev.lumenchess.design.LumenSegment
import dev.lumenchess.design.LumenSlider
import dev.lumenchess.design.LumenTopBar
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.clock.ClockReading
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun P5PlayRoute(
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
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.onScreenStarted()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenStopped()
        }
    }

    when (ui.mode) {
        PlayScreenMode.SETUP -> P5SetupScreen(ui, viewModel, modifier)
        PlayScreenMode.LIVE -> P5LiveScreen(ui, viewModel, modifier)
    }
}

private enum class P5SetupGlyph { BOARD, SHUFFLE, WHITE, BLACK, RANDOM, CLOCK }

@Composable
private fun P5SetupScreen(ui: PlayUiState, viewModel: PlayViewModel, modifier: Modifier) {
    var engineExpanded by remember { mutableStateOf(false) }
    var timeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag(PLAY_SETUP_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LumenTopBar(title = "New Game")

        ui.restorableGame?.let { restored ->
            LumenPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Continue game", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${p5VariantLabel(restored.setup.variant)} · ${restored.setup.engine.displayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = LumenColors.OnSurfaceMuted,
                        )
                    }
                    LumenSecondaryButton("Resume", viewModel::resumeLastGame, Modifier.testTag(PLAY_RESUME_TEST_TAG))
                }
            }
        }

        LumenPanel(Modifier.fillMaxWidth().testTag("p5-setup-shell")) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                P5Section("Game Mode") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        P5VisualSegment(
                            label = "Standard",
                            glyph = P5SetupGlyph.BOARD,
                            selected = ui.setup.variant == Variant.STANDARD,
                            onClick = { viewModel.updateVariant(Variant.STANDARD) },
                            modifier = Modifier.weight(1f).testTag("p5-setup-standard"),
                        )
                        P5VisualSegment(
                            label = "Chess960",
                            glyph = P5SetupGlyph.SHUFFLE,
                            selected = ui.setup.variant == Variant.CHESS960,
                            onClick = { viewModel.updateVariant(Variant.CHESS960) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (ui.setup.variant == Variant.CHESS960) {
                        val index = ui.setup.chess960Index ?: 518
                        P5ValueLine("Starting position", "#$index")
                        LumenSlider(
                            value = index.toFloat(),
                            onValueChange = { viewModel.updateChess960Index(it.roundToInt().coerceIn(0, 959)) },
                            valueRange = 0f..959f,
                            steps = 958,
                        )
                    }
                }

                P5Section("Opponent") {
                    P5DropdownSurface(
                        title = ui.setup.engine.displayName,
                        value = "Engine",
                        expanded = engineExpanded,
                        leading = { LumenEngineBadge(ui.setup.engine.displayName) },
                        onClick = { engineExpanded = !engineExpanded },
                    )
                    if (engineExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PlayEngine.entries.forEach { engine ->
                                P5ChoiceRow(engine.displayName, ui.setup.engine == engine) {
                                    viewModel.updateEngine(engine)
                                    engineExpanded = false
                                }
                            }
                        }
                    }
                }

                P5Section("Strength (Elo)") {
                    when (val target = ui.setup.strengthTarget) {
                        is EngineStrengthTarget.Elo -> {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(target.value.toString(), style = MaterialTheme.typography.titleLarge, color = LumenColors.AccentBlueBright, fontWeight = FontWeight.SemiBold)
                                Text("400 — 3000", style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceFaint)
                            }
                            LumenSlider(
                                value = target.value.toFloat(),
                                onValueChange = {
                                    val snapped = ((it / 50f).roundToInt() * 50).coerceIn(400, 3000)
                                    viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(snapped))
                                },
                                valueRange = 400f..3000f,
                            )
                        }
                        EngineStrengthTarget.FullStrength -> Text("Maximum", style = MaterialTheme.typography.titleLarge, color = LumenColors.AccentBlueBright)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LumenSegment(
                            label = "Elo",
                            selected = ui.setup.strengthTarget is EngineStrengthTarget.Elo,
                            onClick = { viewModel.updateStrengthTarget(EngineStrengthTarget.Elo(1600)) },
                            modifier = Modifier.weight(1f),
                        )
                        LumenSegment(
                            label = "Maximum",
                            selected = ui.setup.strengthTarget == EngineStrengthTarget.FullStrength,
                            onClick = { viewModel.updateStrengthTarget(EngineStrengthTarget.FullStrength) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (ui.setup.strengthTarget is EngineStrengthTarget.Elo) {
                    P5Section("Strength Model") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                EngineStrengthModel.HYBRID to "Hybrid",
                                EngineStrengthModel.ENGINE_NATIVE to "Engine Native",
                                EngineStrengthModel.HUMANIZED to "Humanized",
                            ).forEach { (model, label) ->
                                LumenSegment(
                                    label = label,
                                    selected = ui.setup.strengthModel == model,
                                    onClick = { viewModel.updateStrengthModel(model) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Text(
                            when (ui.setup.strengthModel) {
                                EngineStrengthModel.HYBRID -> "Engine limits with restrained humanization."
                                EngineStrengthModel.ENGINE_NATIVE -> "Use the engine's native strength controls."
                                EngineStrengthModel.HUMANIZED -> "Increase Lumen's human-like move selection."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = LumenColors.OnSurfaceMuted,
                        )
                    }
                }

                P5Section("Side") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        PlaySide.entries.forEach { side ->
                            P5VisualSegment(
                                label = p5SideLabel(side),
                                glyph = when (side) {
                                    PlaySide.WHITE -> P5SetupGlyph.WHITE
                                    PlaySide.BLACK -> P5SetupGlyph.BLACK
                                    PlaySide.RANDOM -> P5SetupGlyph.RANDOM
                                },
                                selected = ui.setup.side == side,
                                onClick = { viewModel.updateSide(side) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                P5Section("Time Control") {
                    val currentTime = P5_TIME_CONTROLS.firstOrNull { it.control == ui.setup.timeControl }?.label
                        ?: p5TimeControlLabel(ui.setup.timeControl)
                    P5DropdownSurface(
                        title = currentTime,
                        value = "Rapid",
                        expanded = timeExpanded,
                        leading = { P5SetupIcon(P5SetupGlyph.CLOCK, LumenColors.OnSurfaceMuted) },
                        onClick = { timeExpanded = !timeExpanded },
                    )
                    if (timeExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            P5_TIME_CONTROLS.forEach { option ->
                                P5ChoiceRow(option.label, ui.setup.timeControl == option.control) {
                                    viewModel.updateTimeControl(option.control)
                                    timeExpanded = false
                                }
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(LumenColors.DestructiveSoft, RoundedCornerShape(8.dp))
                            .border(1.dp, LumenColors.Destructive.copy(alpha = .45f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                    ) { Text(message, style = MaterialTheme.typography.bodySmall, color = LumenColors.Destructive) }
                }

                LumenPrimaryButton(
                    label = "Start Game",
                    onClick = viewModel::startNewGame,
                    enabled = ui.setupValidation is PlaySetupValidation.Valid,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = PLAY_START_TEST_TAG,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun P5VisualSegment(
    label: String,
    glyph: P5SetupGlyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .height(62.dp)
            .background(
                if (selected) Brush.verticalGradient(listOf(LumenColors.AccentBlueSoft, LumenColors.SurfaceRaised))
                else Brush.verticalGradient(listOf(LumenColors.SurfaceHighest, LumenColors.SurfaceRaised)),
                shape,
            )
            .border(1.dp, if (selected) LumenColors.AccentBlueBright else LumenColors.OutlineStrong, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        P5SetupIcon(glyph, if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted)
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (selected) LumenColors.OnSurface else LumenColors.OnSurfaceMuted, maxLines = 1)
    }
}

@Composable
private fun P5SetupIcon(glyph: P5SetupGlyph, color: UiColor) {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val s = size.minDimension * .08f
        when (glyph) {
            P5SetupGlyph.BOARD -> repeat(2) { r -> repeat(2) { f ->
                if ((r + f) % 2 == 0) drawRect(color, Offset(w * (.16f + f * .34f), h * (.16f + r * .34f)), androidx.compose.ui.geometry.Size(w*.30f, h*.30f))
                else drawRect(color.copy(alpha = .42f), Offset(w * (.16f + f * .34f), h * (.16f + r * .34f)), androidx.compose.ui.geometry.Size(w*.30f, h*.30f))
            } }
            P5SetupGlyph.SHUFFLE -> {
                drawLine(color, Offset(w*.18f,h*.30f), Offset(w*.75f,h*.30f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.67f,h*.20f), Offset(w*.78f,h*.30f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.67f,h*.40f), Offset(w*.78f,h*.30f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.18f,h*.70f), Offset(w*.75f,h*.70f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.18f,h*.70f), Offset(w*.32f,h*.56f), s, StrokeCap.Round)
            }
            P5SetupGlyph.WHITE, P5SetupGlyph.BLACK -> {
                val fill = if (glyph == P5SetupGlyph.WHITE) UiColor.White else UiColor(0xFF34393C)
                drawCircle(fill, w*.17f, Offset(w*.5f,h*.27f))
                val pawn = Path().apply {
                    moveTo(w*.38f,h*.42f); lineTo(w*.62f,h*.42f); lineTo(w*.69f,h*.68f); lineTo(w*.78f,h*.80f); lineTo(w*.22f,h*.80f); lineTo(w*.31f,h*.68f); close()
                }
                drawPath(pawn, fill)
                drawPath(pawn, color, style = androidx.compose.ui.graphics.drawscope.Stroke(s*.7f))
            }
            P5SetupGlyph.RANDOM -> {
                drawLine(color, Offset(w*.18f,h*.30f), Offset(w*.78f,h*.70f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.18f,h*.70f), Offset(w*.42f,h*.54f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.68f,h*.58f), Offset(w*.78f,h*.70f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.66f,h*.78f), Offset(w*.78f,h*.70f), s, StrokeCap.Round)
            }
            P5SetupGlyph.CLOCK -> {
                drawCircle(color, w*.34f, Offset(w*.5f,h*.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(s))
                drawLine(color, Offset(w*.5f,h*.5f), Offset(w*.5f,h*.30f), s, StrokeCap.Round)
                drawLine(color, Offset(w*.5f,h*.5f), Offset(w*.65f,h*.58f), s, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun P5Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
        content()
    }
}

@Composable
private fun P5DropdownSurface(
    title: String,
    value: String,
    expanded: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Brush.verticalGradient(listOf(LumenColors.SurfaceHighest, LumenColors.SurfaceRaised)), shape)
            .border(1.dp, if (expanded) LumenColors.AccentBlueBright else LumenColors.OutlineStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted)
        Text(if (expanded) "⌃" else "⌄", color = LumenColors.OnSurfaceMuted)
    }
}

@Composable
private fun P5ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    LumenPanel(Modifier.fillMaxWidth().clickable(onClick = onClick), selected = selected) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurface)
            if (selected) Text("✓", color = LumenColors.AccentBlueBright)
        }
    }
}

@Composable
private fun P5ValueLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted)
        Text(value, style = MaterialTheme.typography.labelLarge, color = LumenColors.AccentBlueBright)
    }
}

@Composable
private fun P5LiveScreen(ui: PlayUiState, viewModel: PlayViewModel, modifier: Modifier) {
    val runtime = ui.runtime ?: return
    val setup = ui.resolvedSetup ?: return
    val humanSide = setup.humanSide
    val engineSide = humanSide.opposite
    val orientation = if (humanSide == Color.WHITE) ChessboardOrientation.WHITE else ChessboardOrientation.BLACK
    val humanTurn = runtime.position.sideToMove == humanSide && runtime.controllers.forSide(humanSide) == RuntimeController.HUMAN
    val inputEnabled = humanTurn && !runtime.paused && runtime.terminal == null
    val premoveEnabled = !humanTurn && !runtime.paused && runtime.terminal == null
    val lastMove = runtime.gameTree.mainline().lastOrNull()?.move
    val queuedPremove = runtime.queuedPremove?.move
    val terminal = runtime.terminal
    val status = when {
        ui.message != null -> ui.message
        terminal != null -> terminal.presentationLabel()
        queuedPremove != null -> "Premove ${queuedPremove.uci} queued"
        runtime.paused -> "Game paused"
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(PLAY_LIVE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val shellShape = RoundedCornerShape(10.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(LumenColors.SurfaceRaised, LumenColors.Surface)), shellShape)
                .border(1.dp, LumenColors.OutlineStrong, shellShape)
                .padding(6.dp)
                .testTag("p5-live-shell"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            P5ParticipantRow(
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
                    .aspectRatio(1f)
                    .border(1.dp, LumenColors.OutlineStrong)
                    .testTag(PLAY_BOARD_STAGE_TEST_TAG),
            ) {
                LumenChessboard(
                    position = runtime.position,
                    onMove = viewModel::onBoardMove,
                    modifier = Modifier.fillMaxSize(),
                    orientation = orientation,
                    input = ChessboardInput(tapEnabled = inputEnabled, dragEnabled = inputEnabled),
                    highlights = ChessboardHighlights(
                        lastMove = lastMove,
                        premoveSquares = queuedPremove?.let { setOf(it.from, it.to) }.orEmpty(),
                    ),
                )
                if (premoveEnabled) {
                    P5PremoveOverlay(
                        runtime = runtime,
                        humanSide = humanSide,
                        orientation = orientation,
                        onPremove = viewModel::queuePremove,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            P5ParticipantRow(
                name = "You",
                detail = humanSide.name.lowercase().replaceFirstChar { it.uppercase() },
                side = humanSide,
                activeSide = runtime.position.sideToMove,
                clock = ui.clock,
                engine = false,
            )
        }

        if (!status.isNullOrBlank()) {
            Text(
                status,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.message != null) LumenColors.Destructive else LumenColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LumenPanel(Modifier.fillMaxWidth().testTag("p5-live-action-strip")) {
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                if (queuedPremove != null) P5ActionButton("Cancel", P5ActionGlyph.CANCEL, onClick = viewModel::cancelPremove)
                if (terminal == null) {
                    P5ActionButton(
                        if (runtime.paused) "Resume" else "Pause",
                        if (runtime.paused) P5ActionGlyph.PLAY else P5ActionGlyph.PAUSE,
                        onClick = if (runtime.paused) viewModel::resume else viewModel::pause,
                    )
                    P5ActionButton("Resign", P5ActionGlyph.FLAG, destructive = true, onClick = viewModel::resign)
                }
                P5ActionButton("More", P5ActionGlyph.MORE, onClick = viewModel::backToSetup)
            }
        }
    }
}

@Composable
private fun P5ParticipantRow(
    name: String,
    detail: String,
    side: Color,
    activeSide: Color,
    clock: ClockReading?,
    engine: Boolean,
    modifier: Modifier = Modifier,
) {
    val millis = if (side == Color.WHITE) clock?.whiteRemainingMillis else clock?.blackRemainingMillis
    val active = side == activeSide
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(if (active) LumenColors.SurfaceHighest else LumenColors.Surface)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (engine) {
            LumenEngineBadge(name)
        } else {
            Box(
                Modifier.size(30.dp).background(LumenColors.AccentBlueSoft, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("♟", style = MaterialTheme.typography.titleMedium, color = LumenColors.OnSurface) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LumenClock(
            text = p5ClockText(millis),
            active = active,
            light = !engine,
            modifier = Modifier.semantics { contentDescription = "$name clock ${p5ClockAccessibility(millis)}" },
        )
    }
}

private enum class P5ActionGlyph { PLAY, PAUSE, FLAG, MORE, CANCEL }

@Composable
private fun RowScope.P5ActionButton(
    label: String,
    glyph: P5ActionGlyph,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (destructive) LumenColors.Destructive else LumenColors.OnSurfaceMuted
    Box(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            P5ActionIcon(glyph, tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (destructive) LumenColors.Destructive else LumenColors.OnSurface)
        }
    }
}

@Composable
private fun P5ActionIcon(glyph: P5ActionGlyph, color: UiColor) {
    Canvas(Modifier.size(17.dp)) {
        val stroke = size.minDimension * .10f
        when (glyph) {
            P5ActionGlyph.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width*.30f,size.height*.18f); lineTo(size.width*.78f,size.height*.50f); lineTo(size.width*.30f,size.height*.82f); close()
                }
                drawPath(path,color)
            }
            P5ActionGlyph.PAUSE -> {
                drawLine(color,Offset(size.width*.36f,size.height*.2f),Offset(size.width*.36f,size.height*.8f),stroke*1.6f,StrokeCap.Round)
                drawLine(color,Offset(size.width*.64f,size.height*.2f),Offset(size.width*.64f,size.height*.8f),stroke*1.6f,StrokeCap.Round)
            }
            P5ActionGlyph.FLAG -> {
                drawLine(color,Offset(size.width*.30f,size.height*.14f),Offset(size.width*.30f,size.height*.86f),stroke,StrokeCap.Round)
                val path = Path().apply {
                    moveTo(size.width*.32f,size.height*.2f); lineTo(size.width*.78f,size.height*.32f); lineTo(size.width*.32f,size.height*.48f); close()
                }
                drawPath(path,color)
            }
            P5ActionGlyph.MORE -> repeat(3) { i -> drawCircle(color, stroke, Offset(size.width*(.28f+i*.22f),size.height*.5f)) }
            P5ActionGlyph.CANCEL -> {
                drawLine(color,Offset(size.width*.25f,size.height*.25f),Offset(size.width*.75f,size.height*.75f),stroke,StrokeCap.Round)
                drawLine(color,Offset(size.width*.75f,size.height*.25f),Offset(size.width*.25f,size.height*.75f),stroke,StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun P5PremoveOverlay(
    runtime: RuntimeState,
    humanSide: Color,
    orientation: ChessboardOrientation,
    onPremove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    var from by remember(runtime.positionRevision) { mutableStateOf<Square?>(null) }
    LaunchedEffect(runtime.queuedPremove) { if (runtime.queuedPremove == null) from = null }
    Box(
        modifier = modifier
            .semantics { contentDescription = "Premove input board" }
            .testTag(PLAY_PREMOVE_OVERLAY_TEST_TAG)
            .pointerInput(runtime.positionRevision, orientation, humanSide) {
                detectTapGestures { offset ->
                    val square = p5SquareFromOffset(offset, size, orientation) ?: return@detectTapGestures
                    val selected = from
                    if (selected == null) {
                        if (runtime.position[square]?.color == humanSide) from = square
                    } else if (runtime.position[square]?.color == humanSide) {
                        from = square
                    } else {
                        val piece = runtime.position[selected]
                        val promotion = if (piece?.type == PieceType.PAWN && square.rank == if (humanSide == Color.WHITE) 7 else 0) PieceType.QUEEN else null
                        onPremove(Move(selected, square, promotion))
                        from = null
                    }
                }
            },
    )
}

private fun p5SquareFromOffset(offset: Offset, size: IntSize, orientation: ChessboardOrientation): Square? {
    if (size.width <= 0 || size.height <= 0 || offset.x !in 0f..size.width.toFloat() || offset.y !in 0f..size.height.toFloat()) return null
    val visualFile = floor(offset.x / (size.width / 8f)).toInt().coerceIn(0, 7)
    val visualRank = floor(offset.y / (size.height / 8f)).toInt().coerceIn(0, 7)
    return when (orientation) {
        ChessboardOrientation.WHITE -> Square.of(visualFile, 7 - visualRank)
        ChessboardOrientation.BLACK -> Square.of(7 - visualFile, visualRank)
    }
}

private data class P5TimeControlOption(val label: String, val control: PlayTimeControl)
private val P5_TIME_CONTROLS = listOf(
    P5TimeControlOption("1 min", PlayTimeControl(60_000L, 0L)),
    P5TimeControlOption("3 + 2", PlayTimeControl(180_000L, 2_000L)),
    P5TimeControlOption("5 min", PlayTimeControl(300_000L, 0L)),
    P5TimeControlOption("10 min", PlayTimeControl(600_000L, 0L)),
)

private fun p5VariantLabel(value: Variant): String = if (value == Variant.STANDARD) "Standard" else "Chess960"
private fun p5SideLabel(value: PlaySide): String = value.name.lowercase().replaceFirstChar { it.uppercase() }
private fun p5TimeControlLabel(value: PlayTimeControl): String = "${value.initialMillis / 60_000L} min${if (value.incrementMillis > 0) " + ${value.incrementMillis / 1_000L}" else ""}"
private fun p5ClockText(millis: Long?): String {
    if (millis == null) return "--:--"
    val safe = millis.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60_000L, (safe % 60_000L) / 1_000L)
}
private fun p5ClockAccessibility(millis: Long?): String {
    if (millis == null) return "unavailable"
    val safe = millis.coerceAtLeast(0L)
    return "${safe / 60_000L} minutes ${(safe % 60_000L) / 1_000L} seconds"
}

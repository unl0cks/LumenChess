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
fun PolishedPlayRoute(modifier: Modifier = Modifier, viewModel: PlayViewModel) {
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
        PlayScreenMode.SETUP -> SetupScreen(ui, viewModel, modifier)
        PlayScreenMode.LIVE -> LiveScreen(ui, viewModel, modifier)
    }
}

@Composable
private fun SetupScreen(ui: PlayUiState, vm: PlayViewModel, modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp).testTag(PLAY_SETUP_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Text("LUMEN PLAY", style = MaterialTheme.typography.labelSmall, color = LumenColors.AccentBlueBright)
            Text("Human vs Engine", style = MaterialTheme.typography.headlineLarge)
            Text("Configure a clean offline match.", color = LumenColors.OnSurfaceMuted)

            ui.restorableGame?.let { restored ->
                Surface(color = LumenColors.SurfaceRaised, shape = RoundedCornerShape(17.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Continue game", style = MaterialTheme.typography.titleMedium)
                            Text("${variantLabel(restored.setup.variant)} · ${restored.setup.engine.displayName}", color = LumenColors.OnSurfaceMuted)
                        }
                        Button(vm::resumeLastGame, Modifier.testTag(PLAY_RESUME_TEST_TAG)) { Text("Resume") }
                    }
                }
            }

            SetupGroup("GAME") {
                FieldLabel("Variant")
                SegmentRow {
                    Segment("Standard", ui.setup.variant == Variant.STANDARD) { vm.updateVariant(Variant.STANDARD) }
                    Segment("Chess960", ui.setup.variant == Variant.CHESS960) { vm.updateVariant(Variant.CHESS960) }
                }
                if (ui.setup.variant == Variant.CHESS960) {
                    val index = ui.setup.chess960Index ?: 518
                    ValueLine("Starting position", "#$index")
                    Slider(index.toFloat(), { vm.updateChess960Index(it.roundToInt().coerceIn(0, 959)) }, valueRange = 0f..959f, steps = 958)
                }
            }

            SetupGroup("OPPONENT") {
                FieldLabel("Engine")
                SegmentRow { PlayEngine.entries.forEach { e -> Segment(e.displayName, ui.setup.engine == e) { vm.updateEngine(e) } } }
                FieldLabel("Play as")
                SegmentRow { PlaySide.entries.forEach { s -> Segment(sideLabel(s), ui.setup.side == s) { vm.updateSide(s) } } }
            }

            SetupGroup("STRENGTH") {
                SegmentRow {
                    Segment("Elo", ui.setup.strengthTarget is EngineStrengthTarget.Elo) { vm.updateStrengthTarget(EngineStrengthTarget.Elo(1600)) }
                    Segment("Full strength", ui.setup.strengthTarget == EngineStrengthTarget.FullStrength) { vm.updateStrengthTarget(EngineStrengthTarget.FullStrength) }
                }
                (ui.setup.strengthTarget as? EngineStrengthTarget.Elo)?.let { target ->
                    ValueLine("Target strength", "${target.value} Elo")
                    Slider(target.value.toFloat(), {
                        vm.updateStrengthTarget(EngineStrengthTarget.Elo(((it / 50f).roundToInt() * 50).coerceIn(400, 3000)))
                    }, valueRange = 400f..3000f)
                    FieldLabel("Strength model")
                    SegmentRow { EngineStrengthModel.entries.forEach { m -> Segment(modelLabel(m), ui.setup.strengthModel == m) { vm.updateStrengthModel(m) } } }
                }
            }

            SetupGroup("CLOCK") {
                FieldLabel("Time control")
                SegmentRow { timeControls.forEach { t -> Segment(t.first, ui.setup.timeControl == t.second) { vm.updateTimeControl(t.second) } } }
            }

            val problem = when (val validation = ui.setupValidation) {
                PlaySetupValidation.Valid -> null
                is PlaySetupValidation.Invalid -> validation.reason
                is PlaySetupValidation.UnsupportedStrength -> validation.reason
            } ?: ui.message
            problem?.let {
                Surface(color = LumenColors.DestructiveSoft, shape = RoundedCornerShape(13.dp)) {
                    Text(it, Modifier.fillMaxWidth().padding(12.dp), color = LumenColors.Destructive)
                }
            }

            Button(
                onClick = vm::startNewGame,
                enabled = ui.setupValidation is PlaySetupValidation.Valid,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag(PLAY_START_TEST_TAG),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LumenColors.AccentBlue),
            ) { Text("Start game", fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SetupGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceFaint)
        Surface(Modifier.fillMaxWidth(), color = LumenColors.Surface.copy(alpha = .95f), shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable private fun FieldLabel(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)

@Composable
private fun ValueLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        FieldLabel(label)
        Text(value, style = MaterialTheme.typography.titleMedium, color = LumenColors.AccentBlueBright)
    }
}

@Composable private fun SegmentRow(content: @Composable RowScope.() -> Unit) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)

@Composable
private fun RowScope.Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) LumenColors.AccentBlueSoft else LumenColors.SurfaceRaised, label = "segment")
    val fg by animateColorAsState(if (selected) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted, label = "segmentText")
    Surface(Modifier.weight(1f).heightIn(min = 48.dp).selectable(selected, onClick, role = Role.RadioButton), color = bg, shape = RoundedCornerShape(12.dp)) {
        Box(Modifier.padding(horizontal = 7.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LiveScreen(ui: PlayUiState, vm: PlayViewModel, modifier: Modifier) {
    val runtime = ui.runtime ?: return
    val setup = ui.resolvedSetup ?: return
    val humanSide = setup.humanSide
    val engineSide = humanSide.opposite
    val orientation = if (humanSide == Color.WHITE) ChessboardOrientation.WHITE else ChessboardOrientation.BLACK
    val humanTurn = runtime.position.sideToMove == humanSide && runtime.controllers.forSide(humanSide) == RuntimeController.HUMAN
    val inputEnabled = humanTurn && !runtime.paused && runtime.terminal == null
    val premoveEnabled = !humanTurn && !runtime.paused && runtime.terminal == null
    val lastMove = runtime.gameTree.mainline().lastOrNull()?.move
    val queued = runtime.queuedPremove?.move
    val status = when {
        ui.message != null -> ui.message
        runtime.terminal != null -> runtime.terminal.presentationLabel()
        queued != null -> "Premove ${queued.uci} queued · 100 ms if played"
        runtime.paused -> "Game paused"
        else -> ""
    }.orEmpty()

    Column(
        modifier.fillMaxSize().background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .padding(horizontal = 10.dp, vertical = 8.dp).testTag(PLAY_LIVE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Participant(setup.engine.displayName, ui.engineStatus, engineSide, runtime.position.sideToMove, ui.clock, true, Modifier.testTag(PLAY_ENGINE_STATUS_TEST_TAG))

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)).testTag(PLAY_BOARD_STAGE_TEST_TAG)) {
                LumenChessboard(
                    position = runtime.position,
                    onMove = vm::onBoardMove,
                    modifier = Modifier.matchParentSize(),
                    orientation = orientation,
                    input = ChessboardInput(tapEnabled = inputEnabled, dragEnabled = inputEnabled),
                    highlights = ChessboardHighlights(lastMove = lastMove, premoveSquares = queued?.let { setOf(it.from, it.to) }.orEmpty()),
                )
                if (premoveEnabled) PremoveOverlay(runtime, humanSide, orientation, vm::queuePremove, Modifier.matchParentSize())
            }
        }

        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = if (ui.message != null) LumenColors.Destructive else LumenColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Participant("You", humanSide.name.lowercase().replaceFirstChar { it.uppercase() }, humanSide, runtime.position.sideToMove, ui.clock, false)

        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (queued != null) Action("Cancel", ActionGlyph.CANCEL, onClick = vm::cancelPremove)
            if (runtime.terminal == null) {
                Action(if (runtime.paused) "Resume" else "Pause", if (runtime.paused) ActionGlyph.PLAY else ActionGlyph.PAUSE, onClick = if (runtime.paused) vm::resume else vm::pause)
                Action("Resign", ActionGlyph.FLAG, destructive = true, onClick = vm::resign)
            }
            Action("Exit", ActionGlyph.EXIT, onClick = vm::backToSetup)
        }
    }
}

@Composable
private fun Participant(name: String, detail: String, side: Color, activeSide: Color, clock: ClockReading?, engine: Boolean, modifier: Modifier = Modifier) {
    val millis = if (side == Color.WHITE) clock?.whiteRemainingMillis else clock?.blackRemainingMillis
    val active = side == activeSide
    val bg by animateColorAsState(if (active) LumenColors.SurfaceRaised else LumenColors.Surface, label = "participant")
    Surface(modifier.fillMaxWidth().height(66.dp), color = bg, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.size(38.dp), color = if (active) LumenColors.AccentBlueSoft else LumenColors.SurfaceHighest, shape = CircleShape) {
                Box(contentAlignment = Alignment.Center) { Text(if (engine) "◆" else "●", color = if (active) LumenColors.AccentBlueBright else LumenColors.OnSurfaceMuted) }
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = LumenColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = if (active) LumenColors.AccentBlueSoft else LumenColors.SurfaceHighest, shape = RoundedCornerShape(11.dp)) {
                Text(clockText(millis), Modifier.padding(horizontal = 12.dp, vertical = 7.dp).semantics { contentDescription = "$name clock ${clockA11y(millis)}" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private enum class ActionGlyph { PLAY, PAUSE, FLAG, EXIT, CANCEL }

@Composable
private fun RowScope.Action(label: String, glyph: ActionGlyph, destructive: Boolean = false, onClick: () -> Unit) {
    Surface(Modifier.weight(1f).fillMaxSize().clickable(role = Role.Button, onClick = onClick), color = if (destructive) LumenColors.DestructiveSoft else LumenColors.Surface, shape = RoundedCornerShape(14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(18.dp)) {
                val s = size.minDimension * .1f
                when (glyph) {
                    ActionGlyph.PLAY -> {
                        val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width*.3f,size.height*.2f); lineTo(size.width*.78f,size.height*.5f); lineTo(size.width*.3f,size.height*.8f); close() }
                        drawPath(p, if (destructive) LumenColors.Destructive else LumenColors.OnSurfaceMuted)
                    }
                    ActionGlyph.PAUSE -> {
                        drawLine(LumenColors.OnSurfaceMuted, Offset(size.width*.35f,size.height*.2f), Offset(size.width*.35f,size.height*.8f), s*1.7f, StrokeCap.Round)
                        drawLine(LumenColors.OnSurfaceMuted, Offset(size.width*.65f,size.height*.2f), Offset(size.width*.65f,size.height*.8f), s*1.7f, StrokeCap.Round)
                    }
                    ActionGlyph.FLAG -> {
                        drawLine(LumenColors.Destructive, Offset(size.width*.3f,size.height*.15f), Offset(size.width*.3f,size.height*.85f), s, StrokeCap.Round)
                        drawCircle(LumenColors.Destructive, s*1.8f, Offset(size.width*.57f,size.height*.32f))
                    }
                    ActionGlyph.EXIT -> {
                        drawLine(LumenColors.OnSurfaceMuted, Offset(size.width*.2f,size.height*.5f), Offset(size.width*.78f,size.height*.5f), s, StrokeCap.Round)
                        drawLine(LumenColors.OnSurfaceMuted, Offset(size.width*.58f,size.height*.32f), Offset(size.width*.78f,size.height*.5f), s, StrokeCap.Round)
                        drawLine(LumenColors.OnSurfaceMuted, Offset(size.width*.58f,size.height*.68f), Offset(size.width*.78f,size.height*.5f), s, StrokeCap.Round)
                    }
                    ActionGlyph.CANCEL -> {
                        drawLine(LumenColors.OnSurfaceMuted, Offset(size.width*.25f,size.height*.25f), Offset(size.width*.75f,size.height*.75f), s, StrokeCap.Round)
                        drawLine(LumenColors.OnSurfaceMuted, Offset(size.width*.75f,size.height*.25f), Offset(size.width*.25f,size.height*.75f), s, StrokeCap.Round)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (destructive) LumenColors.Destructive else LumenColors.OnSurface)
        }
    }
}

@Composable
private fun PremoveOverlay(runtime: RuntimeState, humanSide: Color, orientation: ChessboardOrientation, onPremove: (Move) -> Unit, modifier: Modifier) {
    var from by remember(runtime.positionRevision) { mutableStateOf<Square?>(null) }
    LaunchedEffect(runtime.queuedPremove) { if (runtime.queuedPremove == null) from = null }
    Box(modifier.semantics { contentDescription = "Premove input board" }.testTag(PLAY_PREMOVE_OVERLAY_TEST_TAG).pointerInput(runtime.positionRevision, orientation, humanSide) {
        detectTapGestures { offset ->
            val square = squareAt(offset, size, orientation) ?: return@detectTapGestures
            val selected = from
            if (selected == null) {
                if (runtime.position[square]?.color == humanSide) from = square
            } else if (runtime.position[square]?.color == humanSide) from = square else {
                val piece = runtime.position[selected]
                val promotion = if (piece?.type == PieceType.PAWN && square.rank == if (humanSide == Color.WHITE) 7 else 0) PieceType.QUEEN else null
                onPremove(Move(selected, square, promotion)); from = null
            }
        }
    })
}

private fun squareAt(offset: Offset, size: IntSize, orientation: ChessboardOrientation): Square? {
    if (size.width <= 0 || size.height <= 0 || offset.x !in 0f..size.width.toFloat() || offset.y !in 0f..size.height.toFloat()) return null
    val file = floor(offset.x / (size.width / 8f)).toInt().coerceIn(0,7)
    val rank = floor(offset.y / (size.height / 8f)).toInt().coerceIn(0,7)
    return if (orientation == ChessboardOrientation.WHITE) Square.of(file, 7-rank) else Square.of(7-file, rank)
}

private val timeControls = listOf(
    "1+0" to PlayTimeControl(60_000, 0),
    "3+2" to PlayTimeControl(180_000, 2_000),
    "5+0" to PlayTimeControl(300_000, 0),
    "10+0" to PlayTimeControl(600_000, 0),
)
private fun variantLabel(v: Variant) = if (v == Variant.STANDARD) "Standard" else "Chess960"
private fun sideLabel(v: PlaySide) = v.name.lowercase().replaceFirstChar { it.uppercase() }
private fun modelLabel(v: EngineStrengthModel) = when (v) {
    EngineStrengthModel.ENGINE_NATIVE -> "Native"
    EngineStrengthModel.HUMANIZED -> "Humanized"
    EngineStrengthModel.HYBRID -> "Hybrid"
}
private fun clockText(ms: Long?): String {
    if (ms == null) return "--:--"
    val s = ms.coerceAtLeast(0)
    return "%d:%02d".format(s / 60_000, (s % 60_000) / 1_000)
}
private fun clockA11y(ms: Long?): String {
    if (ms == null) return "unavailable"
    val s = ms.coerceAtLeast(0)
    return "${s/60_000} minutes ${(s%60_000)/1_000} seconds"
}

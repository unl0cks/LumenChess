package dev.lumenchess.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.clock.ClockReading
import kotlin.math.floor

@Composable
internal fun ReferenceLiveScreen(ui: PlayUiState, viewModel: PlayViewModel, modifier: Modifier) {
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
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LumenColors.BackgroundLift, LumenColors.Background)))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(PLAY_LIVE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val shape = RoundedCornerShape(9.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .background(LumenColors.Surface, shape)
                .border(1.dp, LumenColors.OutlineStrong, shape)
                .padding(5.dp)
                .testTag("p5-live-shell"),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ReferenceParticipantRow(
                setup.engine.displayName,
                ui.engineStatus,
                engineSide,
                runtime.position.sideToMove,
                ui.clock,
                engine = true,
                Modifier.testTag(PLAY_ENGINE_STATUS_TEST_TAG),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .border(1.dp, LumenColors.OutlineStrong)
                    .testTag(PLAY_BOARD_STAGE_TEST_TAG),
            ) {
                LumenChessboard(
                    runtime.position,
                    viewModel::onBoardMove,
                    Modifier.fillMaxSize(),
                    orientation,
                    ChessboardInput(tapEnabled = inputEnabled, dragEnabled = inputEnabled),
                    ChessboardHighlights(
                        lastMove = lastMove,
                        premoveSquares = queuedPremove?.let { setOf(it.from, it.to) }.orEmpty(),
                    ),
                )
                if (premoveEnabled) {
                    ReferencePremoveOverlay(
                        runtime,
                        humanSide,
                        orientation,
                        viewModel::queuePremove,
                        Modifier.fillMaxSize(),
                    )
                }
            }
            ReferenceParticipantRow(
                "You",
                humanSide.name.lowercase().replaceFirstChar { it.uppercase() },
                humanSide,
                runtime.position.sideToMove,
                ui.clock,
                engine = false,
            )
        }

        if (!status.isNullOrBlank()) {
            Text(
                status,
                Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (ui.message != null) LumenColors.Destructive else LumenColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ReferenceMovePanel(
            runtime = runtime,
            setup = setup,
            modifier = Modifier.fillMaxWidth().height(270.dp).testTag("p5-live-lower-region"),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "In-game",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            style = MaterialTheme.typography.labelSmall,
            color = LumenColors.AccentBlueBright,
        )
        ReferenceActionStrip(
            runtime,
            queuedPremove != null,
            viewModel,
            Modifier.fillMaxWidth().height(52.dp).testTag("p5-live-action-strip"),
        )
    }
}

@Composable
private fun ReferenceParticipantRow(
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
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(if (active) LumenColors.SurfaceRaised else LumenColors.Surface)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (engine) {
            LumenEngineBadge(name)
        } else {
            Box(
                Modifier.size(28.dp).background(LumenColors.SurfaceHighest, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("♟", style = MaterialTheme.typography.labelLarge, color = LumenColors.OnSurface)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LumenClock(
            referenceClockText(millis),
            active = active,
            light = !engine,
            modifier = Modifier.semantics { contentDescription = "$name clock ${referenceClockAccessibility(millis)}" },
        )
    }
}

@Composable
private fun ReferenceMovePanel(runtime: RuntimeState, setup: ResolvedPlaySetup, modifier: Modifier = Modifier) {
    val mainline = runtime.gameTree.mainline()
    val rows = mainline.chunked(2).takeLast(4)
    val firstMoveNumber = ((mainline.size + 1) / 2 - rows.size + 1).coerceAtLeast(1)
    val shape = RoundedCornerShape(8.dp)
    val minutes = setup.clockConfig.initialMillis / 60_000L
    val increment = setup.clockConfig.incrementMillis / 1_000L
    val variantLabel = if (setup.variant == Variant.STANDARD) "Standard" else "Chess960"

    Column(
        modifier
            .background(LumenColors.Surface.copy(alpha = .92f), shape)
            .border(1.dp, LumenColors.Outline, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Moves", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                if (runtime.position.sideToMove == Color.WHITE) "White to move" else "Black to move",
                style = MaterialTheme.typography.labelSmall,
                color = LumenColors.OnSurfaceMuted,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(116.dp)
                .background(LumenColors.SurfaceRaised.copy(alpha = .58f), RoundedCornerShape(5.dp))
                .padding(horizontal = 9.dp, vertical = 7.dp),
        ) {
            if (rows.isEmpty()) {
                Column(
                    Modifier.align(Alignment.CenterStart),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("No moves yet", style = MaterialTheme.typography.labelMedium, color = LumenColors.OnSurfaceMuted)
                    Text(
                        "Your moves and the engine replies will appear here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = LumenColors.OnSurfaceFaint,
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    rows.forEachIndexed { index, pair ->
                        Row(
                            Modifier.fillMaxWidth().height(23.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text("${firstMoveNumber + index}.", Modifier.weight(.18f), style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceFaint)
                            Text(
                                pair.getOrNull(0)?.san ?: pair.getOrNull(0)?.move?.uci.orEmpty(),
                                Modifier.weight(.41f),
                                style = MaterialTheme.typography.labelMedium,
                                color = LumenColors.OnSurface,
                            )
                            Text(
                                pair.getOrNull(1)?.san ?: pair.getOrNull(1)?.move?.uci.orEmpty(),
                                Modifier.weight(.41f),
                                style = MaterialTheme.typography.labelMedium,
                                color = LumenColors.OnSurfaceMuted,
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReferenceGameFact("Mode", variantLabel, Modifier.weight(1f))
            ReferenceGameFact("Time", "$minutes+$increment", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReferenceGameFact("Opponent", setup.engine.displayName, Modifier.weight(1f))
            ReferenceGameFact("Side", setup.humanSide.name.lowercase().replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReferenceGameFact(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(42.dp)
            .background(LumenColors.SurfaceRaised.copy(alpha = .72f), RoundedCornerShape(5.dp))
            .border(1.dp, LumenColors.Outline.copy(alpha = .75f), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurfaceFaint)
        Text(value, style = MaterialTheme.typography.labelSmall, color = LumenColors.OnSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class RefActionGlyph { PLAY, PAUSE, FLAG, MORE, CANCEL }

@Composable
private fun ReferenceActionStrip(runtime: RuntimeState, hasPremove: Boolean, viewModel: PlayViewModel, modifier: Modifier) =
    Row(
        modifier
            .background(LumenColors.Surface, RoundedCornerShape(8.dp))
            .border(1.dp, LumenColors.Outline, RoundedCornerShape(8.dp)),
    ) {
        if (hasPremove) ReferenceAction("Cancel", RefActionGlyph.CANCEL, onClick = viewModel::cancelPremove)
        if (runtime.terminal == null) {
            ReferenceAction(
                if (runtime.paused) "Resume" else "Pause",
                if (runtime.paused) RefActionGlyph.PLAY else RefActionGlyph.PAUSE,
                onClick = if (runtime.paused) viewModel::resume else viewModel::pause,
            )
            ReferenceAction("Resign", RefActionGlyph.FLAG, destructive = true, onClick = viewModel::resign)
        }
        ReferenceAction("More", RefActionGlyph.MORE, onClick = viewModel::backToSetup)
    }

@Composable
private fun RowScope.ReferenceAction(label: String, glyph: RefActionGlyph, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) LumenColors.Destructive else LumenColors.OnSurfaceMuted
    Column(
        Modifier.weight(1f).fillMaxSize().clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(15.dp)) {
            val s = size.minDimension * .10f
            when (glyph) {
                RefActionGlyph.PLAY -> {
                    val path = Path().apply {
                        moveTo(size.width * .30f, size.height * .18f)
                        lineTo(size.width * .78f, size.height * .50f)
                        lineTo(size.width * .30f, size.height * .82f)
                        close()
                    }
                    drawPath(path, tint)
                }
                RefActionGlyph.PAUSE -> {
                    drawLine(tint, Offset(size.width * .36f, size.height * .2f), Offset(size.width * .36f, size.height * .8f), s * 1.6f, StrokeCap.Round)
                    drawLine(tint, Offset(size.width * .64f, size.height * .2f), Offset(size.width * .64f, size.height * .8f), s * 1.6f, StrokeCap.Round)
                }
                RefActionGlyph.FLAG -> {
                    drawLine(tint, Offset(size.width * .30f, size.height * .14f), Offset(size.width * .30f, size.height * .86f), s, StrokeCap.Round)
                    val path = Path().apply {
                        moveTo(size.width * .32f, size.height * .20f)
                        lineTo(size.width * .78f, size.height * .32f)
                        lineTo(size.width * .32f, size.height * .48f)
                        close()
                    }
                    drawPath(path, tint)
                }
                RefActionGlyph.MORE -> repeat(3) { i -> drawCircle(tint, s, Offset(size.width * (.28f + i * .22f), size.height * .5f)) }
                RefActionGlyph.CANCEL -> {
                    drawLine(tint, Offset(size.width * .25f, size.height * .25f), Offset(size.width * .75f, size.height * .75f), s, StrokeCap.Round)
                    drawLine(tint, Offset(size.width * .75f, size.height * .25f), Offset(size.width * .25f, size.height * .75f), s, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (destructive) LumenColors.Destructive else LumenColors.OnSurface)
    }
}

@Composable
private fun ReferencePremoveOverlay(
    runtime: RuntimeState,
    humanSide: Color,
    orientation: ChessboardOrientation,
    onPremove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    var from by remember(runtime.positionRevision) { mutableStateOf<Square?>(null) }
    LaunchedEffect(runtime.queuedPremove) { if (runtime.queuedPremove == null) from = null }
    Box(
        modifier
            .semantics { contentDescription = "Premove input board" }
            .testTag(PLAY_PREMOVE_OVERLAY_TEST_TAG)
            .pointerInput(runtime.positionRevision, orientation, humanSide) {
                detectTapGestures { offset ->
                    val square = referenceSquareFromOffset(offset, size, orientation) ?: return@detectTapGestures
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
                        ) PieceType.QUEEN else null
                        onPremove(Move(selected, square, promotion))
                        from = null
                    }
                }
            },
    )
}

private fun referenceSquareFromOffset(offset: Offset, size: IntSize, orientation: ChessboardOrientation): Square? {
    if (size.width <= 0 || size.height <= 0 || offset.x !in 0f..size.width.toFloat() || offset.y !in 0f..size.height.toFloat()) return null
    val visualFile = floor(offset.x / (size.width / 8f)).toInt().coerceIn(0, 7)
    val visualRank = floor(offset.y / (size.height / 8f)).toInt().coerceIn(0, 7)
    return when (orientation) {
        ChessboardOrientation.WHITE -> Square.of(visualFile, 7 - visualRank)
        ChessboardOrientation.BLACK -> Square.of(7 - visualFile, visualRank)
    }
}

private fun referenceClockText(millis: Long?): String {
    if (millis == null) return "--:--"
    val safe = millis.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60_000L, (safe % 60_000L) / 1_000L)
}

private fun referenceClockAccessibility(millis: Long?): String {
    if (millis == null) return "unavailable"
    val safe = millis.coerceAtLeast(0L)
    return "${safe / 60_000L} minutes ${(safe % 60_000L) / 1_000L} seconds"
}
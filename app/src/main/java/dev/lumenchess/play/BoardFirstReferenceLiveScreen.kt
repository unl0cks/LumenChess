package dev.lumenchess.play

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lumenchess.board.ChessboardHighlights
import dev.lumenchess.board.ChessboardInput
import dev.lumenchess.board.BoardMovePresentation
import dev.lumenchess.board.BoardMovePresentationClassifier
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Square
import dev.lumenchess.design.LumenClock
import dev.lumenchess.design.LumenColors
import dev.lumenchess.design.LumenEngineBadge
import dev.lumenchess.design.LumenMotion
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.clock.ClockReading
import kotlin.math.floor

/**
 * Sparse default Human-vs-Engine presentation.
 *
 * Runtime ownership is unchanged: this observes [PlayUiState] and commands [PlayViewModel].
 * Optional analysis/history surfaces remain in the richer reference implementation for the later
 * presentation-settings milestone, but are not emitted (and therefore reserve no space) by default.
 */
@Composable
internal fun BoardFirstReferenceLiveScreen(
    ui: PlayUiState,
    viewModel: PlayViewModel,
    modifier: Modifier,
    visibility: LivePresentationVisibility = DefaultLivePresentationVisibility,
) {
    val runtime = ui.runtime ?: return
    val setup = ui.resolvedSetup ?: return
    val humanSide = setup.humanSide
    val engineSide = humanSide.opposite
    val orientation = if (humanSide == Color.WHITE) ChessboardOrientation.WHITE else ChessboardOrientation.BLACK
    val humanTurn = runtime.position.sideToMove == humanSide &&
        runtime.controllers.forSide(humanSide) == RuntimeController.HUMAN
    val inputEnabled = humanTurn && !runtime.paused && runtime.terminal == null
    val premoveEnabled = !humanTurn && !runtime.paused && runtime.terminal == null
    val lastMove = runtime.gameTree.mainline().lastOrNull()?.move
    val queuedPremove = runtime.queuedPremove?.move
    var presentedRevision by remember { mutableLongStateOf(runtime.positionRevision.value) }
    val revisionDelta = (runtime.positionRevision.value - presentedRevision).coerceAtLeast(0L)
    val lastMover = runtime.position.sideToMove.opposite
    val movePresentation = if (revisionDelta == 0L) {
        BoardMovePresentation.ENGINE
    } else {
        BoardMovePresentationClassifier.classify(
            revisionDelta = revisionDelta,
            lastMoverIsHuman = runtime.controllers.forSide(lastMover) == RuntimeController.HUMAN,
        )
    }
    SideEffect { presentedRevision = runtime.positionRevision.value }
    var pendingPremoveOrigin by remember(runtime.positionRevision) { mutableStateOf<Square?>(null) }
    LaunchedEffect(runtime.queuedPremove) {
        if (runtime.queuedPremove == null) pendingPremoveOrigin = null
    }
    val status = when {
        ui.message != null -> ui.message
        runtime.terminal != null -> "Game over"
        queuedPremove != null -> "Premove ${queuedPremove.uci} queued"
        runtime.paused -> "Game paused"
        else -> null
    }

    Column(
        modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        LumenColors.BackgroundLift,
                        LumenColors.Background,
                        LumenColors.Background,
                    ),
                ),
            )
            .padding(horizontal = 7.dp, vertical = 5.dp)
            .testTag(PLAY_LIVE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val shellShape = RoundedCornerShape(7.dp)
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            LumenColors.SurfaceRaised.copy(alpha = .96f),
                            LumenColors.Surface,
                        ),
                    ),
                    shellShape,
                )
                .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .90f), shellShape)
                .padding(4.dp)
                .testTag("p5-live-shell"),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BoardFirstParticipantRow(
                name = boardFirstEngineTitle(setup),
                detail = boardFirstEngineDetail(ui.engineStatus, engineSide, runtime.position.sideToMove),
                side = engineSide,
                activeSide = runtime.position.sideToMove,
                clock = ui.clock,
                engine = true,
                rowTag = "p5-live-opponent-row",
                clockTag = "p5-live-opponent-clock",
                legacyStatusTag = PLAY_ENGINE_STATUS_TEST_TAG,
            )
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .92f))
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
                        premove = queuedPremove,
                        pendingPremoveOrigin = pendingPremoveOrigin,
                        positionRevision = runtime.positionRevision.value,
                        movePresentation = movePresentation,
                    ),
                )
                if (premoveEnabled) {
                    BoardFirstPremoveOverlay(
                        runtime = runtime,
                        humanSide = humanSide,
                        orientation = orientation,
                        from = pendingPremoveOrigin,
                        onFromChange = { pendingPremoveOrigin = it },
                        onPremove = viewModel::queuePremove,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            BoardFirstParticipantRow(
                name = "You",
                detail = boardFirstHumanDetail(humanSide, runtime.position.sideToMove, runtime.paused),
                side = humanSide,
                activeSide = runtime.position.sideToMove,
                clock = ui.clock,
                engine = false,
                rowTag = "p5-live-player-row",
                clockTag = "p5-live-player-clock",
            )
        }

        if (!status.isNullOrBlank()) {
            Text(
                status,
                Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = if (ui.message != null) LumenColors.Destructive else LumenColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The visibility contract controls emitted UI, not runtime state. Defaults omit every
        // analysis/history surface completely, so no empty panel or invisible height can move the board.
        if (visibility.showMoves || visibility.showInfo || visibility.showEvaluation || visibility.showEngineLines) {
            ReferenceLiveScreen(ui, viewModel, Modifier.fillMaxSize())
        } else {
            Spacer(Modifier.weight(1f))
            BoardFirstEssentialActions(
                runtime = runtime,
                hasPremove = queuedPremove != null,
                showPauseButton = visibility.showPauseButton,
                viewModel = viewModel,
                modifier = Modifier.fillMaxWidth().height(72.dp).testTag("p5-live-action-strip"),
            )
        }
    }
}

@Composable
private fun BoardFirstParticipantRow(
    name: String,
    detail: String,
    side: Color,
    activeSide: Color,
    clock: ClockReading?,
    engine: Boolean,
    rowTag: String,
    clockTag: String,
    legacyStatusTag: String? = null,
    modifier: Modifier = Modifier,
) {
    val millis = if (side == Color.WHITE) clock?.whiteRemainingMillis else clock?.blackRemainingMillis
    val active = side == activeSide
    val rowShape = RoundedCornerShape(5.dp)
    Row(
        modifier.fillMaxWidth().height(56.dp)
            .background(
                if (active) LumenColors.SurfaceHighest.copy(alpha = .74f)
                else LumenColors.Surface.copy(alpha = .82f),
                rowShape,
            )
            .drawBehind {
                drawLine(
                    color = UiColor.White.copy(alpha = if (active) .055f else .03f),
                    start = Offset(7.dp.toPx(), 1.dp.toPx()),
                    end = Offset(size.width - 7.dp.toPx(), 1.dp.toPx()),
                    strokeWidth = .6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .testTag(rowTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (engine) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                LumenEngineBadge(name)
            }
        } else {
            BoardFirstHumanBadge()
        }
        var identityModifier = Modifier.weight(1f)
        if (legacyStatusTag != null) identityModifier = identityModifier.testTag(legacyStatusTag)
        Column(identityModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp, lineHeight = 17.sp),
                fontWeight = FontWeight.SemiBold,
                color = LumenColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = LumenColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LumenClock(
            boardFirstClockText(millis),
            active = active,
            light = !engine && active,
            modifier = Modifier
                .size(width = 94.dp, height = 44.dp)
                .testTag(clockTag)
                .semantics { contentDescription = "$name clock ${boardFirstClockAccessibility(millis)}" },
        )
    }
}

@Composable
private fun BoardFirstHumanBadge() {
    val shape = RoundedCornerShape(7.dp)
    val tint = LumenColors.OnSurface.copy(alpha = .94f)
    val accent = LumenColors.AccentBlueBright.copy(alpha = .72f)
    Box(
        Modifier.size(32.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(LumenColors.SurfaceHighest, LumenColors.SurfaceRaised)))
            .border(1.dp, LumenColors.OutlineStrong.copy(alpha = .82f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(19.dp)) {
            drawCircle(tint, radius = size.minDimension * .16f, center = Offset(center.x, size.height * .27f))
            val body = Path().apply {
                moveTo(size.width * .30f, size.height * .77f)
                quadraticTo(size.width * .32f, size.height * .47f, center.x, size.height * .45f)
                quadraticTo(size.width * .68f, size.height * .47f, size.width * .70f, size.height * .77f)
                close()
            }
            drawPath(body, tint)
            drawLine(
                accent,
                Offset(size.width * .26f, size.height * .82f),
                Offset(size.width * .74f, size.height * .82f),
                1.1.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun BoardFirstPremoveOverlay(
    runtime: RuntimeState,
    humanSide: Color,
    orientation: ChessboardOrientation,
    from: Square?,
    onFromChange: (Square?) -> Unit,
    onPremove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.semantics { contentDescription = "Premove input board" }
            .testTag(PLAY_PREMOVE_OVERLAY_TEST_TAG)
            .pointerInput(runtime.positionRevision, orientation, humanSide) {
                detectTapGestures { offset ->
                    val visualFile = floor(offset.x / (size.width / 8f)).toInt().coerceIn(0, 7)
                    val visualRank = floor(offset.y / (size.height / 8f)).toInt().coerceIn(0, 7)
                    val square = when (orientation) {
                        ChessboardOrientation.WHITE -> Square.of(visualFile, 7 - visualRank)
                        ChessboardOrientation.BLACK -> Square.of(7 - visualFile, visualRank)
                    }
                    val selected = from
                    when {
                        selected == null -> if (runtime.position[square]?.color == humanSide) onFromChange(square)
                        selected == square -> onFromChange(null)
                        runtime.position[square]?.color == humanSide -> onFromChange(square)
                        else -> {
                            val piece = runtime.position[selected]
                            val promotionRank = if (humanSide == Color.WHITE) 7 else 0
                            val promotion = if (piece?.type == PieceType.PAWN && square.rank == promotionRank) {
                                PieceType.QUEEN
                            } else null
                            onPremove(Move(selected, square, promotion))
                            onFromChange(null)
                        }
                    }
                }
            },
    )
}

private enum class BoardFirstActionGlyph { PAUSE, PLAY, FLAG, EXIT, CANCEL }

@Composable
private fun BoardFirstEssentialActions(
    runtime: RuntimeState,
    hasPremove: Boolean,
    showPauseButton: Boolean,
    viewModel: PlayViewModel,
    modifier: Modifier,
) {
    val stripShape = RoundedCornerShape(7.dp)
    Row(
        modifier
            .background(LumenColors.SurfaceRaised.copy(alpha = .91f), stripShape)
            .border(1.dp, LumenColors.Outline.copy(alpha = .70f), stripShape)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasPremove) {
            BoardFirstAction(
                label = "Cancel",
                glyph = BoardFirstActionGlyph.CANCEL,
                testTag = "p5-live-action-cancel",
                onClick = viewModel::cancelPremove,
            )
        }
        if (showPauseButton && runtime.terminal == null) {
            BoardFirstAction(
                label = if (runtime.paused) "Resume" else "Pause",
                glyph = if (runtime.paused) BoardFirstActionGlyph.PLAY else BoardFirstActionGlyph.PAUSE,
                testTag = "p5-live-action-pause",
                onClick = if (runtime.paused) viewModel::resume else viewModel::pause,
            )
        }
        if (runtime.terminal == null) {
            BoardFirstAction(
                label = "Resign",
                glyph = BoardFirstActionGlyph.FLAG,
                destructive = true,
                testTag = "p5-live-action-resign",
                onClick = viewModel::resign,
            )
        }
        BoardFirstAction(
            label = "Exit",
            glyph = BoardFirstActionGlyph.EXIT,
            testTag = "p5-live-action-exit",
            onClick = viewModel::backToSetup,
        )
    }
}

@Composable
private fun RowScope.BoardFirstAction(
    label: String,
    glyph: BoardFirstActionGlyph,
    destructive: Boolean = false,
    testTag: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .955f else 1f,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "board-first-action-scale-$label",
    )
    val offset by animateDpAsState(
        targetValue = if (pressed) 1.2.dp else 0.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "board-first-action-offset-$label",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) .2.dp else 1.8.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "board-first-action-shadow-$label",
    )
    val lowerEdge by animateDpAsState(
        targetValue = if (pressed) .4.dp else 2.dp,
        animationSpec = if (pressed) LumenMotion.pressTween() else LumenMotion.releaseTween(),
        label = "board-first-action-edge-$label",
    )
    val shape = RoundedCornerShape(5.dp)
    val tint = if (destructive) LumenColors.Destructive else LumenColors.OnSurfaceMuted
    val faceTop = if (destructive) {
        LumenColors.DestructiveSoft.copy(alpha = if (pressed) .36f else .24f)
    } else if (pressed) {
        LumenColors.SurfaceHighest.copy(alpha = .82f)
    } else {
        LumenColors.SurfaceHighest.copy(alpha = .66f)
    }
    val faceBottom = if (pressed) LumenColors.Surface.copy(alpha = .98f) else LumenColors.SurfaceRaised
    val lowerEdgeColor = if (destructive) {
        LumenColors.Destructive.copy(alpha = .23f)
    } else {
        LumenColors.OutlineStrong.copy(alpha = .72f)
    }

    Box(
        Modifier.weight(1f).fillMaxSize().testTag(testTag)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offset.toPx()
            }
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(LumenColors.Background)
            .drawBehind {
                drawRect(
                    color = lowerEdgeColor,
                    topLeft = Offset(0f, size.height - lowerEdge.toPx()),
                )
            }
            .padding(bottom = lowerEdge)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(faceTop, faceBottom)))
            .border(
                1.dp,
                if (destructive) LumenColors.Destructive.copy(alpha = .46f)
                else LumenColors.OutlineStrong.copy(alpha = if (pressed) .92f else .76f),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BoardFirstActionGlyph(glyph, if (pressed && !destructive) LumenColors.OnSurface else tint)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                fontWeight = FontWeight.Medium,
                color = if (pressed && !destructive) LumenColors.OnSurface else tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoardFirstActionGlyph(glyph: BoardFirstActionGlyph, tint: UiColor) {
    Canvas(Modifier.size(17.dp)) {
        val stroke = 1.35.dp.toPx()
        when (glyph) {
            BoardFirstActionGlyph.PAUSE -> {
                drawLine(tint, Offset(size.width * .36f, size.height * .24f), Offset(size.width * .36f, size.height * .76f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .64f, size.height * .24f), Offset(size.width * .64f, size.height * .76f), stroke, StrokeCap.Round)
            }
            BoardFirstActionGlyph.PLAY -> {
                val path = Path().apply {
                    moveTo(size.width * .36f, size.height * .24f)
                    lineTo(size.width * .73f, size.height * .50f)
                    lineTo(size.width * .36f, size.height * .76f)
                    close()
                }
                drawPath(path, tint)
            }
            BoardFirstActionGlyph.FLAG -> {
                drawLine(tint, Offset(size.width * .31f, size.height * .18f), Offset(size.width * .31f, size.height * .82f), stroke, StrokeCap.Round)
                val flag = Path().apply {
                    moveTo(size.width * .32f, size.height * .22f)
                    lineTo(size.width * .72f, size.height * .30f)
                    lineTo(size.width * .56f, size.height * .49f)
                    lineTo(size.width * .32f, size.height * .44f)
                    close()
                }
                drawPath(flag, tint)
            }
            BoardFirstActionGlyph.EXIT -> {
                drawLine(tint, Offset(size.width * .22f, size.height * .20f), Offset(size.width * .22f, size.height * .80f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .22f, size.height * .20f), Offset(size.width * .53f, size.height * .20f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .22f, size.height * .80f), Offset(size.width * .53f, size.height * .80f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .43f, size.height * .50f), Offset(size.width * .79f, size.height * .50f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .65f, size.height * .36f), Offset(size.width * .79f, size.height * .50f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .65f, size.height * .64f), Offset(size.width * .79f, size.height * .50f), stroke, StrokeCap.Round)
            }
            BoardFirstActionGlyph.CANCEL -> {
                drawLine(tint, Offset(size.width * .27f, size.height * .27f), Offset(size.width * .73f, size.height * .73f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .73f, size.height * .27f), Offset(size.width * .27f, size.height * .73f), stroke, StrokeCap.Round)
            }
        }
    }
}

private fun boardFirstEngineTitle(setup: ResolvedPlaySetup): String {
    val strength = when (val target = setup.strength.target) {
        EngineStrengthTarget.FullStrength -> "Max"
        is EngineStrengthTarget.Elo -> target.value.toString()
    }
    return "${setup.engine.displayName} ($strength)"
}

private fun boardFirstEngineDetail(status: String, side: Color, activeSide: Color): String {
    val sideLabel = side.name.lowercase().replaceFirstChar { it.uppercase() }
    val cleanStatus = status.trim()
    return when {
        cleanStatus.isNotBlank() -> "$sideLabel · $cleanStatus"
        side == activeSide -> "$sideLabel · Thinking"
        else -> "$sideLabel · Waiting"
    }
}

private fun boardFirstHumanDetail(side: Color, activeSide: Color, paused: Boolean): String {
    val sideLabel = side.name.lowercase().replaceFirstChar { it.uppercase() }
    return when {
        paused -> "$sideLabel · Paused"
        side == activeSide -> "$sideLabel · Your move"
        else -> "$sideLabel · Waiting"
    }
}

private fun boardFirstClockText(millis: Long?): String {
    if (millis == null) return "--:--"
    val safe = millis.coerceAtLeast(0L)
    return "%d:%02d".format(safe / 60_000L, (safe % 60_000L) / 1_000L)
}

private fun boardFirstClockAccessibility(millis: Long?): String {
    if (millis == null) return "unavailable"
    val safe = millis.coerceAtLeast(0L)
    return "${safe / 60_000L} minutes ${(safe % 60_000L) / 1_000L} seconds"
}

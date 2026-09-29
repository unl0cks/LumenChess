package dev.lumenchess.play

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
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
    // Keyed on the whole resolved setup so a rematch (colours swapped) starts unflipped.
    var boardFlipped by remember(setup) { mutableStateOf(false) }
    var dialog by remember(setup) { mutableStateOf(LiveDialog.NONE) }
    var resultDismissed by remember(setup) { mutableStateOf(false) }
    val context = LocalContext.current
    val terminal = runtime.terminal
    val baseOrientation = if (humanSide == Color.WHITE) ChessboardOrientation.WHITE else ChessboardOrientation.BLACK
    val orientation = if (boardFlipped) {
        if (baseOrientation == ChessboardOrientation.WHITE) ChessboardOrientation.BLACK else ChessboardOrientation.WHITE
    } else {
        baseOrientation
    }
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
        ui.notice != null -> ui.notice
        terminal != null -> terminal.presentationLabel()
        queuedPremove != null -> "Premove ${queuedPremove.uci} queued"
        runtime.paused -> "Game paused"
        else -> null
    }

    // System back never drops the player out of the app mid-game: it asks first, and a finished
    // game simply returns to setup. Open dialogs are separate windows and consume back themselves.
    BackHandler(enabled = dialog == LiveDialog.NONE) {
        if (terminal != null) viewModel.backToSetup() else dialog = LiveDialog.LEAVE
    }

    val analysisVisible = visibility.showMoves || visibility.showInfo ||
        visibility.showEvaluation || visibility.showEngineLines
    val emphasizeStatus = terminal != null && ui.message == null && ui.notice == null

    BoxWithConstraints(
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
            .testTag(PLAY_LIVE_TEST_TAG),
    ) {
        if (analysisVisible) {
            // Optional analysis/history surfaces are not emitted by default; when a future setting
            // enables them they own the space below the board, so the group is top-aligned.
            Column(
                Modifier.fillMaxSize().padding(horizontal = LIVE_H_PADDING, vertical = LIVE_V_PADDING),
                verticalArrangement = Arrangement.spacedBy(GROUP_GAP),
            ) {
                BoardFirstShell(
                    ui, setup, runtime, viewModel, orientation, inputEnabled, premoveEnabled, lastMove,
                    queuedPremove, pendingPremoveOrigin, { pendingPremoveOrigin = it }, movePresentation,
                    Modifier.fillMaxWidth(),
                )
                BoardFirstStatusSlot(status, ui.message != null, emphasizeStatus)
                ReferenceLiveScreen(ui, viewModel, Modifier.fillMaxSize())
            }
        } else {
            // One deliberate composition: the board is sized from the real constraints (width-led on
            // phones, height-led on short screens so nothing is ever clipped) and the whole group of
            // opponent card, board, player card, status slot and actions is centred as a unit. It
            // depends only on the viewport, never on game state, so the board cannot move.
            val widthLimit = maxWidth - LIVE_H_PADDING * 2 - SHELL_PADDING * 2
            val chrome = SHELL_PADDING * 2 + PARTICIPANT_ROW_HEIGHT * 2 + SHELL_GAP * 2 +
                STATUS_SLOT_HEIGHT + ACTION_STRIP_HEIGHT + GROUP_GAP * 2
            val heightLimit = maxHeight - LIVE_V_PADDING * 2 - chrome
            val boardSide = minOf(widthLimit, heightLimit).coerceAtLeast(MIN_BOARD_SIDE)
            Column(
                Modifier.align(Alignment.Center).width(boardSide + SHELL_PADDING * 2),
                verticalArrangement = Arrangement.spacedBy(GROUP_GAP),
            ) {
                BoardFirstShell(
                    ui, setup, runtime, viewModel, orientation, inputEnabled, premoveEnabled, lastMove,
                    queuedPremove, pendingPremoveOrigin, { pendingPremoveOrigin = it }, movePresentation,
                    Modifier.fillMaxWidth(),
                )
                BoardFirstStatusSlot(status, ui.message != null, emphasizeStatus)
                BoardFirstEssentialActions(
                    runtime = runtime,
                    hasPremove = queuedPremove != null,
                    showPauseButton = visibility.showPauseButton,
                    onFlipBoard = { boardFlipped = !boardFlipped },
                    onResign = { dialog = LiveDialog.RESIGN },
                    onMenu = { dialog = LiveDialog.MENU },
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxWidth().height(ACTION_STRIP_HEIGHT).testTag("p5-live-action-strip"),
                )
            }
        }
    }

    when (dialog) {
        LiveDialog.NONE -> Unit
        LiveDialog.RESIGN -> LiveResignDialog(
            onCancel = { dialog = LiveDialog.NONE },
            onConfirm = {
                dialog = LiveDialog.NONE
                viewModel.resign()
            },
        )
        LiveDialog.LEAVE -> LiveLeaveDialog(
            onStay = { dialog = LiveDialog.NONE },
            onLeave = {
                dialog = LiveDialog.NONE
                viewModel.backToSetup()
            },
        )
        LiveDialog.MENU -> LiveMenuDialog(
            setup = setup,
            onCopyPgn = {
                viewModel.currentPgn()?.let { pgn ->
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("LumenChess PGN", pgn))
                    viewModel.showNotice("PGN copied")
                }
                dialog = LiveDialog.NONE
            },
            onCopyFen = {
                viewModel.currentFen()?.let { fen ->
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("LumenChess FEN", fen))
                    viewModel.showNotice("FEN copied")
                }
                dialog = LiveDialog.NONE
            },
            onLeave = {
                dialog = LiveDialog.NONE
                viewModel.backToSetup()
            },
            onClose = { dialog = LiveDialog.NONE },
        )
    }

    if (terminal != null && !resultDismissed && dialog == LiveDialog.NONE) {
        LiveResultDialog(
            terminal = terminal,
            humanSide = humanSide,
            onRematch = viewModel::rematch,
            onNewGame = viewModel::backToSetup,
            onViewBoard = { resultDismissed = true },
        )
    }
}

private enum class LiveDialog { NONE, RESIGN, LEAVE, MENU }

private val LIVE_H_PADDING = 7.dp
private val LIVE_V_PADDING = 5.dp
private val SHELL_PADDING = 4.dp
private val SHELL_GAP = 2.dp
private val GROUP_GAP = 6.dp
private val PARTICIPANT_ROW_HEIGHT = 60.dp
private val STATUS_SLOT_HEIGHT = 22.dp
private val ACTION_STRIP_HEIGHT = 76.dp
private val MIN_BOARD_SIDE = 200.dp

/** Opponent card, board and player card as one raised plane. Presentation and input only. */
@Composable
private fun BoardFirstShell(
    ui: PlayUiState,
    setup: ResolvedPlaySetup,
    runtime: RuntimeState,
    viewModel: PlayViewModel,
    orientation: ChessboardOrientation,
    inputEnabled: Boolean,
    premoveEnabled: Boolean,
    lastMove: Move?,
    queuedPremove: Move?,
    pendingPremoveOrigin: Square?,
    onPendingPremoveOrigin: (Square?) -> Unit,
    movePresentation: BoardMovePresentation,
    modifier: Modifier,
) {
    val humanSide = setup.humanSide
    val engineSide = humanSide.opposite
    val shellShape = RoundedCornerShape(7.dp)
    Column(
        modifier
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
            .padding(SHELL_PADDING)
            .testTag("p5-live-shell"),
        verticalArrangement = Arrangement.spacedBy(SHELL_GAP),
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
                    onFromChange = onPendingPremoveOrigin,
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
}

/**
 * Fixed-height slot: the board group is vertically centred, so a status line that appeared and
 * disappeared would otherwise nudge the board by half its height.
 */
@Composable
private fun BoardFirstStatusSlot(status: String?, isError: Boolean, emphasized: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(STATUS_SLOT_HEIGHT).padding(horizontal = 3.dp).testTag("p5-live-status-slot"),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (!status.isNullOrBlank()) {
            Text(
                status,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isError -> LumenColors.Destructive
                    emphasized -> LumenColors.OnSurface
                    else -> LumenColors.OnSurfaceMuted
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        modifier.fillMaxWidth().height(PARTICIPANT_ROW_HEIGHT)
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
            formatLiveClock(millis),
            active = active,
            light = !engine && active,
            urgent = active && isLiveClockUrgent(millis),
            modifier = Modifier
                .size(width = 100.dp, height = 48.dp)
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

private enum class BoardFirstActionGlyph { PAUSE, PLAY, FLAG, CANCEL, FLIP, DRAW, MENU, NEW_GAME, REMATCH }

@Composable
private fun BoardFirstEssentialActions(
    runtime: RuntimeState,
    hasPremove: Boolean,
    showPauseButton: Boolean,
    onFlipBoard: () -> Unit,
    onResign: () -> Unit,
    onMenu: () -> Unit,
    viewModel: PlayViewModel,
    modifier: Modifier,
) {
    val stripShape = RoundedCornerShape(7.dp)
    val playing = runtime.terminal == null
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
        if (showPauseButton && playing) {
            BoardFirstAction(
                label = if (runtime.paused) "Resume" else "Pause",
                glyph = if (runtime.paused) BoardFirstActionGlyph.PLAY else BoardFirstActionGlyph.PAUSE,
                testTag = "p5-live-action-pause",
                onClick = if (runtime.paused) viewModel::resume else viewModel::pause,
            )
        }
        if (playing) {
            BoardFirstAction(
                label = "Resign",
                glyph = BoardFirstActionGlyph.FLAG,
                destructive = true,
                testTag = "p5-live-action-resign",
                onClick = onResign,
            )
            BoardFirstAction(
                label = "Draw",
                glyph = BoardFirstActionGlyph.DRAW,
                testTag = "p5-live-action-draw",
                onClick = viewModel::offerDraw,
            )
        } else {
            // "New game" keeps the historical exit tag: it is the way out of a finished game.
            BoardFirstAction(
                label = "New game",
                glyph = BoardFirstActionGlyph.NEW_GAME,
                testTag = "p5-live-action-exit",
                onClick = viewModel::backToSetup,
            )
            BoardFirstAction(
                label = "Rematch",
                glyph = BoardFirstActionGlyph.REMATCH,
                testTag = "p5-live-action-rematch",
                onClick = viewModel::rematch,
            )
        }
        BoardFirstAction(
            label = "Flip",
            glyph = BoardFirstActionGlyph.FLIP,
            testTag = "p5-live-action-flip",
            onClick = onFlipBoard,
        )
        BoardFirstAction(
            label = "Menu",
            glyph = BoardFirstActionGlyph.MENU,
            testTag = "p5-live-action-menu",
            onClick = onMenu,
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = FontWeight.Medium,
                color = if (pressed && !destructive) LumenColors.OnSurface else tint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BoardFirstActionGlyph(glyph: BoardFirstActionGlyph, tint: UiColor) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
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
            BoardFirstActionGlyph.CANCEL -> {
                drawLine(tint, Offset(size.width * .27f, size.height * .27f), Offset(size.width * .73f, size.height * .73f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .73f, size.height * .27f), Offset(size.width * .27f, size.height * .73f), stroke, StrokeCap.Round)
            }
            BoardFirstActionGlyph.FLIP -> {
                // Two opposed vertical arrows: the board turns over.
                drawLine(tint, Offset(size.width * .32f, size.height * .78f), Offset(size.width * .32f, size.height * .22f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .32f, size.height * .22f), Offset(size.width * .20f, size.height * .36f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .32f, size.height * .22f), Offset(size.width * .44f, size.height * .36f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .68f, size.height * .22f), Offset(size.width * .68f, size.height * .78f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .68f, size.height * .78f), Offset(size.width * .56f, size.height * .64f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .68f, size.height * .78f), Offset(size.width * .80f, size.height * .64f), stroke, StrokeCap.Round)
            }
            BoardFirstActionGlyph.DRAW -> {
                // "=": the result of a drawn game.
                drawLine(tint, Offset(size.width * .24f, size.height * .38f), Offset(size.width * .76f, size.height * .38f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .24f, size.height * .62f), Offset(size.width * .76f, size.height * .62f), stroke, StrokeCap.Round)
            }
            BoardFirstActionGlyph.MENU -> {
                val radius = stroke * .85f
                listOf(.25f, .5f, .75f).forEach { x ->
                    drawCircle(tint, radius = radius, center = Offset(size.width * x, size.height * .5f))
                }
            }
            BoardFirstActionGlyph.NEW_GAME -> {
                drawLine(tint, Offset(size.width * .24f, size.height * .5f), Offset(size.width * .76f, size.height * .5f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .5f, size.height * .24f), Offset(size.width * .5f, size.height * .76f), stroke, StrokeCap.Round)
            }
            BoardFirstActionGlyph.REMATCH -> {
                // Opposed horizontal arrows: same opponent, colours swapped.
                drawLine(tint, Offset(size.width * .22f, size.height * .36f), Offset(size.width * .78f, size.height * .36f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .78f, size.height * .36f), Offset(size.width * .62f, size.height * .24f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .78f, size.height * .36f), Offset(size.width * .62f, size.height * .48f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .78f, size.height * .64f), Offset(size.width * .22f, size.height * .64f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .22f, size.height * .64f), Offset(size.width * .38f, size.height * .52f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(size.width * .22f, size.height * .64f), Offset(size.width * .38f, size.height * .76f), stroke, StrokeCap.Round)
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

private fun boardFirstClockAccessibility(millis: Long?): String {
    if (millis == null) return "unavailable"
    val safe = millis.coerceAtLeast(0L)
    return "${safe / 60_000L} minutes ${(safe % 60_000L) / 1_000L} seconds"
}

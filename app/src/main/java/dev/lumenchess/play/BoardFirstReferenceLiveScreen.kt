package dev.lumenchess.play

import dev.lumenchess.analysis.ui.AnalysisRequest
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
import androidx.compose.runtime.getValue
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
import dev.lumenchess.board.BoardMovePresentationTracker
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.board.LumenChessboard
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Square
import dev.lumenchess.design.LumenActionGlyph
import dev.lumenchess.design.LumenActionStrip
import dev.lumenchess.design.LumenActionTile
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
 * Analysis, move-list and information surfaces are not part of this screen at all, so they reserve
 * no space; when the presentation settings add them they will be composed here, below the board.
 */
@Composable
internal fun BoardFirstReferenceLiveScreen(
    ui: PlayUiState,
    viewModel: PlayViewModel,
    modifier: Modifier,
    visibility: LivePresentationVisibility = DefaultLivePresentationVisibility,
    onOpenAnalysis: (AnalysisRequest) -> Unit = {},
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
    val lastMover = runtime.position.sideToMove.opposite
    val presentationTracker = remember { BoardMovePresentationTracker(runtime.positionRevision.value) }
    val movePresentation = presentationTracker.presentationFor(
        revision = runtime.positionRevision.value,
        lastMoverIsHuman = runtime.controllers.forSide(lastMover) == RuntimeController.HUMAN,
    )
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
            onReview = if (terminal != null) {
                {
                    dialog = LiveDialog.NONE
                    viewModel.whenGamePersisted { id -> onOpenAnalysis(AnalysisRequest.LibraryGame(id, review = true)) }
                }
            } else null,
        )
    }

    if (terminal != null && !resultDismissed && dialog == LiveDialog.NONE) {
        LiveResultDialog(
            terminal = terminal,
            humanSide = humanSide,
            onRematch = viewModel::rematch,
            onNewGame = viewModel::backToSetup,
            onViewBoard = { resultDismissed = true },
            onReview = {
                resultDismissed = true
                viewModel.whenGamePersisted { id -> onOpenAnalysis(AnalysisRequest.LibraryGame(id, review = true)) }
            },
            onAnalyze = {
                resultDismissed = true
                viewModel.whenGamePersisted { id -> onOpenAnalysis(AnalysisRequest.LibraryGame(id)) }
            },
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
                onIllegalDrop = viewModel::onIllegalMoveAttempt,
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
    val playing = runtime.terminal == null
    LumenActionStrip(modifier) {
        if (hasPremove) {
            LumenActionTile("Cancel", LumenActionGlyph.CANCEL, "p5-live-action-cancel", viewModel::cancelPremove)
        }
        if (showPauseButton && playing) {
            LumenActionTile(
                label = if (runtime.paused) "Resume" else "Pause",
                glyph = if (runtime.paused) LumenActionGlyph.PLAY else LumenActionGlyph.PAUSE,
                testTag = "p5-live-action-pause",
                onClick = if (runtime.paused) viewModel::resume else viewModel::pause,
            )
        }
        if (playing) {
            LumenActionTile("Resign", LumenActionGlyph.FLAG, "p5-live-action-resign", onResign, destructive = true)
            LumenActionTile("Draw", LumenActionGlyph.DRAW, "p5-live-action-draw", viewModel::offerDraw)
        } else {
            // "New game" keeps the historical exit tag: it is the way out of a finished game.
            LumenActionTile("New game", LumenActionGlyph.NEW_GAME, "p5-live-action-exit", viewModel::backToSetup)
            LumenActionTile("Rematch", LumenActionGlyph.REMATCH, "p5-live-action-rematch", viewModel::rematch)
        }
        LumenActionTile("Flip", LumenActionGlyph.FLIP, "p5-live-action-flip", onFlipBoard)
        LumenActionTile("Menu", LumenActionGlyph.MENU, "p5-live-action-menu", onMenu)
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

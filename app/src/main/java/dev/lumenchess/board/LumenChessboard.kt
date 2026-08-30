package dev.lumenchess.board

import androidx.compose.animation.core.Animatable
import android.animation.ValueAnimator
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square
import dev.lumenchess.design.LumenMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.sqrt

private data class ActiveBoardMotion(
    val plan: BoardMotionPlan,
    val identity: BoardMotionIdentity,
)

private sealed interface InputSubmission {
    data object Rejected : InputSubmission
    data object PromotionPending : InputSubmission
    data class Committed(val move: Move) : InputSubmission
}

@Composable
fun LumenChessboard(
    position: Position,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ChessboardOrientation = ChessboardOrientation.WHITE,
    input: ChessboardInput = ChessboardInput(),
    highlights: ChessboardHighlights = ChessboardHighlights(),
    arrows: List<ChessboardArrow> = emptyList(),
    palette: ChessboardPalette? = null,
    pieceSet: PieceSet? = null,
) {
    val presentation = LocalChessboardPresentationStyle.current
    val resolvedPalette = palette ?: presentation.palette
    val resolvedPieceSet = pieceSet ?: presentation.pieceSet
    val legalMoves = remember(position) { MoveGenerator.legalMoves(position) }
    val coroutineScope = rememberCoroutineScope()
    val moveProgress = remember { Animatable(1f) }
    val promotionProgress = remember { Animatable(1f) }
    val density = LocalDensity.current

    var selectedSquare by remember(position) { mutableStateOf<Square?>(null) }
    var pendingPromotion by remember(position) { mutableStateOf<List<Move>>(emptyList()) }
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    // Drag/settle state deliberately survives the authoritative position recomposition so a legal
    // release can finish its presentation-only settle after onMove commits the new board.
    var dragFrom by remember { mutableStateOf<Square?>(null) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var draggedPiece by remember { mutableStateOf<Piece?>(null) }
    var snappingTarget by remember { mutableStateOf<Square?>(null) }
    var snappingPosition by remember { mutableStateOf<Offset?>(null) }
    var snappingPiece by remember { mutableStateOf<Piece?>(null) }
    var snappingCapturedPiece by remember { mutableStateOf<Piece?>(null) }
    var snappingCapturedSquare by remember { mutableStateOf<Square?>(null) }
    var snappingProgress by remember { mutableFloatStateOf(0f) }
    var dragHeldFraction by remember { mutableFloatStateOf(0f) }
    var dragMotionJob by remember { mutableStateOf<Job?>(null) }
    var suppressNextTravel by remember { mutableStateOf(false) }
    var previousPosition by remember { mutableStateOf(position) }
    var previousMotionIdentity by remember {
        mutableStateOf(
            BoardMotionIdentity(
                revision = highlights.positionRevision ?: position.hashCode().toLong(),
                orientation = orientation,
            ),
        )
    }
    var activeBoardMotion by remember { mutableStateOf<ActiveBoardMotion?>(null) }

    val checkSquare = remember(position, highlights.showCheck) {
        if (!highlights.showCheck || !MoveGenerator.isInCheck(position, position.sideToMove)) {
            null
        } else {
            position.board.indexOfFirst { piece -> piece == Piece(position.sideToMove, PieceType.KING) }
                .takeIf { it >= 0 }?.let(Square::fromIndex)
        }
    }

    fun submitCandidates(candidates: List<Move>): InputSubmission {
        if (candidates.isEmpty()) return InputSubmission.Rejected
        val promotionCandidates = candidates.filter { it.promotion != null }
        return when {
            promotionCandidates.isEmpty() -> candidates.first().let { move ->
                onMove(move)
                InputSubmission.Committed(move)
            }
            input.promotionPolicy == PromotionPolicy.AUTO_QUEEN -> {
                val move = promotionCandidates.firstOrNull { it.promotion == PieceType.QUEEN }
                    ?: promotionCandidates.first()
                onMove(move)
                InputSubmission.Committed(move)
            }
            else -> {
                pendingPromotion = promotionCandidates
                InputSubmission.PromotionPending
            }
        }
    }

    fun submitInput(from: Square, target: Square): InputSubmission {
        if (pendingPromotion.isNotEmpty()) return InputSubmission.Rejected
        val candidates = ChessboardMoveResolver.candidates(position, legalMoves, from, target)
        val submission = submitCandidates(candidates)
        if (submission != InputSubmission.Rejected) selectedSquare = null
        return submission
    }

    fun handleTap(target: Square) {
        if (!input.tapEnabled || pendingPromotion.isNotEmpty()) return
        val selected = selectedSquare
        if (selected == null) {
            if (position[target]?.color == position.sideToMove) selectedSquare = target
            return
        }
        if (submitInput(selected, target) != InputSubmission.Rejected) return
        if (position[target]?.color == position.sideToMove) selectedSquare = target
    }

    fun clearDrag() {
        dragFrom = null
        dragPosition = null
        draggedPiece = null
        snappingTarget = null
        snappingPosition = null
        snappingPiece = null
        snappingCapturedPiece = null
        snappingCapturedSquare = null
        snappingProgress = 0f
        dragHeldFraction = 0f
        selectedSquare = null
    }

    fun animatePickup() {
        dragMotionJob?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            dragHeldFraction = 1f
            dragMotionJob = null
            return
        }
        dragMotionJob = coroutineScope.launch {
            val progress = Animatable(dragHeldFraction)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GroundedPrecisionBoardMotion.pickupDurationMillis,
                    easing = LumenMotion.CrispEase,
                ),
            ) {
                dragHeldFraction = value
            }
            dragHeldFraction = 1f
            dragMotionJob = null
        }
    }

    fun animateDragBack() {
        val from = dragFrom ?: return clearDrag()
        val start = dragPosition ?: return clearDrag()
        if (boardSize.width <= 0) return clearDrag()
        if (!ValueAnimator.areAnimatorsEnabled()) return clearDrag()
        val target = squareCenter(from, boardSize.width.toFloat(), orientation)
        val startHeldFraction = dragHeldFraction
        dragMotionJob?.cancel()
        dragMotionJob = coroutineScope.launch {
            val progress = Animatable(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GroundedPrecisionBoardMotion.illegalDropDurationMillis,
                    easing = LumenMotion.CrispEase,
                ),
            ) {
                dragPosition = lerpOffset(start, target, value)
                dragHeldFraction = startHeldFraction * (1f - value)
            }
            clearDrag()
            dragMotionJob = null
        }
    }

    fun animateLegalDrop(target: Square, move: Move, sourcePosition: Position) {
        val piece = draggedPiece ?: return clearDrag()
        val start = dragPosition ?: return clearDrag()
        if (boardSize.width <= 0) return clearDrag()
        if (!ValueAnimator.areAnimatorsEnabled()) return clearDrag()
        val center = squareCenter(target, boardSize.width.toFloat(), orientation)
        val capturedSquare = BoardMotionPlanner.capturedSquare(sourcePosition, move, piece)
        val startHeldFraction = dragHeldFraction
        snappingTarget = target
        snappingPosition = start
        snappingPiece = piece
        snappingCapturedSquare = capturedSquare
        snappingCapturedPiece = capturedSquare?.let(sourcePosition::get)
        snappingProgress = 0f
        dragMotionJob?.cancel()
        dragMotionJob = coroutineScope.launch {
            val progress = Animatable(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GroundedPrecisionBoardMotion.legalDropDurationMillis,
                    easing = LumenMotion.CrispEase,
                ),
            ) {
                snappingPosition = lerpOffset(start, center, value)
                snappingProgress = value
                dragHeldFraction = startHeldFraction * (1f - value)
            }
            clearDrag()
            dragMotionJob = null
        }
    }

    val motionIdentity = BoardMotionIdentity(
        revision = highlights.positionRevision ?: position.hashCode().toLong(),
        orientation = orientation,
    )

    // The authoritative position can arrive one draw before LaunchedEffect starts. Resolve that
    // pending presentation synchronously so tap/engine/capture moves never flash on the final
    // square before their presentation-only travel begins.
    val pendingMotion = if (
        previousPosition != position &&
        !suppressNextTravel &&
        previousMotionIdentity.orientation == motionIdentity.orientation
    ) {
        highlights.lastMove?.let { move ->
            BoardMotionPlanner.plan(
                previous = previousPosition,
                current = position,
                move = move,
                presentation = highlights.movePresentation,
                animationsEnabled = ValueAnimator.areAnimatorsEnabled(),
            ).takeUnless { it == BoardMotionPlan.Atomic }
                ?.let { plan -> ActiveBoardMotion(plan = plan, identity = motionIdentity) }
        }
    } else {
        null
    }
    val visibleMotion = activeBoardMotion
        ?.takeIf { it.identity == motionIdentity }
        ?: pendingMotion

    LaunchedEffect(motionIdentity, position, highlights.lastMove, highlights.movePresentation) {
        val oldIdentity = previousMotionIdentity
        val previous = previousPosition
        val orientationChanged = oldIdentity.orientation != motionIdentity.orientation
        previousMotionIdentity = motionIdentity

        // A new authoritative revision or orientation owns the presentation. Compose cancels the
        // previous LaunchedEffect before entering here, and the old overlay is cleared first.
        activeBoardMotion = null
        moveProgress.snapTo(1f)
        promotionProgress.snapTo(1f)
        if (orientationChanged) {
            dragMotionJob?.cancel()
            dragMotionJob = null
            clearDrag()
        }

        if (previous == position) return@LaunchedEffect
        previousPosition = position

        if (suppressNextTravel) {
            suppressNextTravel = false
            return@LaunchedEffect
        }

        dragMotionJob?.cancel()
        dragMotionJob = null
        clearDrag()
        val move = highlights.lastMove ?: return@LaunchedEffect
        val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
        val plan = BoardMotionPlanner.plan(
            previous = previous,
            current = position,
            move = move,
            presentation = highlights.movePresentation,
            animationsEnabled = animationsEnabled,
        )
        if (plan == BoardMotionPlan.Atomic) return@LaunchedEffect

        val motion = ActiveBoardMotion(plan = plan, identity = motionIdentity)
        activeBoardMotion = motion
        moveProgress.snapTo(0f)
        promotionProgress.snapTo(0f)
        try {
            when (plan) {
                BoardMotionPlan.Atomic -> Unit
                is BoardMotionPlan.Castling -> moveProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = plan.durationMillis,
                        easing = LumenMotion.CrispEase,
                    ),
                )
                is BoardMotionPlan.Travel -> {
                    moveProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = plan.durationMillis,
                            easing = LumenMotion.CrispEase,
                        ),
                    )
                    plan.promotion?.let { promotion ->
                        promotionProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = promotion.durationMillis,
                                easing = LumenMotion.CrispEase,
                            ),
                        )
                    }
                }
            }
        } finally {
            if (activeBoardMotion == motion) activeBoardMotion = null
        }
    }

    val dragModifier = if (input.dragEnabled) {
        Modifier.pointerInput(position, orientation, legalMoves, pendingPromotion) {
            detectDragGestures(
                onDragStart = { offset ->
                    val square = squareFromOffset(offset, size, orientation)
                    val piece = position[square]
                    dragFrom = square.takeIf { piece?.color == position.sideToMove }
                    draggedPiece = piece.takeIf { dragFrom != null }
                    dragPosition = offset.takeIf { dragFrom != null }
                    if (dragFrom != null) {
                        selectedSquare = dragFrom
                        animatePickup()
                    }
                },
                onDrag = { change, _ ->
                    if (dragFrom != null) dragPosition = change.position
                    change.consume()
                },
                onDragEnd = {
                    val from = dragFrom
                    val target = dragPosition?.let { squareFromOffset(it, size, orientation) }
                    if (from != null && target != null) {
                        suppressNextTravel = true
                        when (val submission = submitInput(from, target)) {
                            is InputSubmission.Committed -> {
                                if (MoveGenerator.castlingSide(position, submission.move) != null) {
                                    suppressNextTravel = false
                                    dragMotionJob?.cancel()
                                    dragMotionJob = null
                                    clearDrag()
                                } else if (submission.move.promotion != null) {
                                    suppressNextTravel = false
                                    dragMotionJob?.cancel()
                                    dragMotionJob = null
                                    clearDrag()
                                } else {
                                    animateLegalDrop(target, submission.move, position)
                                }
                            }
                            InputSubmission.PromotionPending -> {
                                suppressNextTravel = false
                                dragMotionJob?.cancel()
                                dragMotionJob = null
                                clearDrag()
                            }
                            InputSubmission.Rejected -> {
                                suppressNextTravel = false
                                animateDragBack()
                            }
                        }
                    } else {
                        animateDragBack()
                    }
                },
                onDragCancel = { animateDragBack() },
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .testTag(CHESSBOARD_TEST_TAG)
            .onSizeChanged { boardSize = it }
            .then(dragModifier),
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(8) { visualRow ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    repeat(8) { visualColumn ->
                        val square = squareAtVisual(visualColumn, visualRow, orientation)
                        val hiddenByDrag = square == dragFrom || square == snappingTarget
                        val hiddenByMotion = when (val plan = visibleMotion?.plan) {
                            is BoardMotionPlan.Travel -> plan.move.to == square
                            is BoardMotionPlan.Castling -> square in plan.suppressedSquares
                            BoardMotionPlan.Atomic, null -> false
                        }
                        val piece = position[square].takeUnless { hiddenByDrag || hiddenByMotion }
                        val selected = selectedSquare
                        val candidates = if (selected != null && highlights.showLegalMoves) {
                            ChessboardMoveResolver.candidates(position, legalMoves, selected, square)
                        } else {
                            emptyList()
                        }
                        val legalTarget = candidates.isNotEmpty()
                        val captureTarget = candidates.any { ChessboardMoveResolver.isCapture(position, it) }
                        val feedback = highlights.feedbackFor(square)
                        ChessboardSquare(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            square = square,
                            piece = piece,
                            dark = (square.file + square.rank) % 2 == 0,
                            selected = square == selectedSquare,
                            legalTarget = legalTarget,
                            captureTarget = captureTarget,
                            feedback = feedback,
                            check = square == checkSquare,
                            extraHighlight = square in highlights.extraSquares,
                            tapEnabled = input.tapEnabled && pendingPromotion.isEmpty(),
                            palette = resolvedPalette,
                            pieceSet = resolvedPieceSet,
                            onClick = { handleTap(square) },
                        )
                    }
                }
            }
        }

        if (boardSize.width > 0) {
            val cellPx = boardSize.width / 8f
            val cellDp = with(density) { cellPx.toDp() }
            val activeDragPiece = snappingPiece ?: draggedPiece
            val activeDragPosition = snappingPosition ?: dragPosition
            if (activeDragPiece != null && activeDragPosition != null) {
                val visuals = GroundedPrecisionBoardMotion.dragVisuals(dragHeldFraction)
                HeldPieceOverlay(
                    piece = activeDragPiece,
                    position = activeDragPosition,
                    cellPx = cellPx,
                    cellDp = cellDp,
                    palette = resolvedPalette,
                    pieceSet = resolvedPieceSet,
                    visuals = visuals,
                    modifier = Modifier.align(Alignment.TopStart).testTag("dragged-piece"),
                )

                val captured = snappingCapturedPiece
                val capturedSquare = snappingCapturedSquare
                if (captured != null && capturedSquare != null) {
                    val elapsedMillis = snappingProgress * GroundedPrecisionBoardMotion.legalDropDurationMillis
                    val alpha = (1f - elapsedMillis / GroundedPrecisionBoardMotion.captureFadeDurationMillis)
                        .coerceIn(0f, 1f)
                    if (alpha > 0f) {
                        PieceOverlay(
                            piece = captured,
                            position = squareCenter(capturedSquare, boardSize.width.toFloat(), orientation),
                            cellPx = cellPx,
                            cellDp = cellDp,
                            palette = resolvedPalette,
                            pieceSet = resolvedPieceSet,
                            alpha = alpha,
                            scale = 0.90f,
                            modifier = Modifier.testTag("captured-piece-drop-fade"),
                        )
                    }
                }
            }

            visibleMotion?.let { motion ->
                val progress = if (motion === pendingMotion) 0f else moveProgress.value
                when (val plan = motion.plan) {
                    BoardMotionPlan.Atomic -> Unit
                    is BoardMotionPlan.Castling -> {
                        listOf(plan.rook, plan.king).forEach { leg ->
                            if (!leg.isStatic) {
                                PieceOverlay(
                                    piece = leg.piece,
                                    position = lerpOffset(
                                        squareCenter(leg.from, boardSize.width.toFloat(), orientation),
                                        squareCenter(leg.to, boardSize.width.toFloat(), orientation),
                                        progress,
                                    ),
                                    cellPx = cellPx,
                                    cellDp = cellDp,
                                    palette = resolvedPalette,
                                    pieceSet = resolvedPieceSet,
                                    alpha = 1f,
                                    scale = 0.90f,
                                    overlayZIndex = leg.zIndex,
                                    modifier = Modifier.testTag(
                                        if (leg.piece.type == PieceType.KING) "castling-king" else "castling-rook",
                                    ),
                                )
                            }
                        }
                    }
                    is BoardMotionPlan.Travel -> {
                        val start = squareCenter(plan.move.from, boardSize.width.toFloat(), orientation)
                        val end = squareCenter(plan.move.to, boardSize.width.toFloat(), orientation)
                        plan.capturedPiece?.let { captured ->
                            val elapsedMillis = progress * plan.durationMillis
                            val alpha = (1f - elapsedMillis / plan.captureFadeDurationMillis)
                                .coerceIn(0f, 1f)
                            if (alpha > 0f) {
                                PieceOverlay(
                                    piece = captured,
                                    position = squareCenter(
                                        plan.capturedSquare ?: plan.move.to,
                                        boardSize.width.toFloat(),
                                        orientation,
                                    ),
                                    cellPx = cellPx,
                                    cellDp = cellDp,
                                    palette = resolvedPalette,
                                    pieceSet = resolvedPieceSet,
                                    alpha = alpha,
                                    scale = 0.90f,
                                    modifier = Modifier.testTag("captured-piece-fade"),
                                )
                            }
                        }
                        val promotion = plan.promotion
                        if (promotion != null && progress >= 1f) {
                            val replacementProgress = if (motion === pendingMotion) 0f else promotionProgress.value
                            val promotedScale = 0.90f * (
                                promotion.initialScale + (1f - promotion.initialScale) * replacementProgress
                            )
                            val destination = squareCenter(
                                plan.move.to,
                                boardSize.width.toFloat(),
                                orientation,
                            )
                            if (replacementProgress < 1f) {
                                PieceOverlay(
                                    piece = promotion.outgoingPiece,
                                    position = destination,
                                    cellPx = cellPx,
                                    cellDp = cellDp,
                                    palette = resolvedPalette,
                                    pieceSet = resolvedPieceSet,
                                    alpha = 1f - replacementProgress,
                                    scale = 0.90f,
                                    modifier = Modifier.testTag("promotion-outgoing-piece"),
                                )
                            }
                            PieceOverlay(
                                piece = promotion.promotedPiece,
                                position = destination,
                                cellPx = cellPx,
                                cellDp = cellDp,
                                palette = resolvedPalette,
                                pieceSet = resolvedPieceSet,
                                alpha = replacementProgress,
                                scale = promotedScale,
                                modifier = Modifier.testTag("promotion-promoted-piece"),
                            )
                        } else {
                            PieceOverlay(
                                piece = plan.piece,
                                position = lerpOffset(start, end, progress),
                                cellPx = cellPx,
                                cellDp = cellDp,
                                palette = resolvedPalette,
                                pieceSet = resolvedPieceSet,
                                alpha = 1f,
                                scale = 0.90f,
                                modifier = Modifier.testTag("traveling-piece"),
                            )
                        }
                    }
                }
            }
        }

        if (arrows.isNotEmpty()) {
            ChessboardArrows(
                arrows = arrows,
                orientation = orientation,
                palette = resolvedPalette,
                modifier = Modifier.fillMaxSize().testTag(CHESSBOARD_ARROWS_TEST_TAG),
            )
        }
        if (pendingPromotion.isNotEmpty()) {
            PromotionPicker(
                moves = pendingPromotion,
                color = position.sideToMove,
                palette = resolvedPalette,
                pieceSet = resolvedPieceSet,
                onChoose = { move ->
                    pendingPromotion = emptyList()
                    selectedSquare = null
                    onMove(move)
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
internal fun HeldPieceOverlay(
    piece: Piece,
    position: Offset,
    cellPx: Float,
    cellDp: androidx.compose.ui.unit.Dp,
    palette: ChessboardPalette,
    pieceSet: PieceSet,
    visuals: BoardDragVisuals,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val liftPx = with(density) { visuals.liftDp.dp.toPx() }
    val shadowOffsetPx = with(density) { visuals.shadowOffsetDp.dp.toPx() }
    val shadowBlurPx = with(density) { GroundedPrecisionBoardMotion.heldShadowBlurDp.dp.toPx() }
    val shadowCanvasDp = cellDp * 1.24f
    val shadowCanvasPx = with(density) { shadowCanvasDp.toPx() }
    val tint = if (piece.color == Color.WHITE) palette.whitePiece else palette.blackPiece

    Box(modifier = modifier.fillMaxSize()) {
        if (visuals.shadowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(shadowCanvasDp)
                    .graphicsLayer {
                        translationX = position.x - shadowCanvasPx / 2f
                        translationY = position.y - shadowCanvasPx / 2f + liftPx + shadowOffsetPx
                        alpha = visuals.shadowAlpha
                        colorFilter = ColorFilter.tint(UiColor.Black)
                        renderEffect = BlurEffect(shadowBlurPx, shadowBlurPx, TileMode.Decal)
                    }
                    .zIndex(4.8f),
                contentAlignment = Alignment.Center,
            ) {
                pieceSet.Piece(
                    piece = piece,
                    tint = tint,
                    modifier = Modifier
                        .size(cellDp * .90f)
                        .graphicsLayer {
                            scaleX = visuals.scale
                            scaleY = visuals.scale
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(cellDp)
                .graphicsLayer {
                    translationX = position.x - cellPx / 2f
                    translationY = position.y - cellPx / 2f + liftPx
                    scaleX = visuals.scale
                    scaleY = visuals.scale
                }
                .zIndex(5f),
            contentAlignment = Alignment.Center,
        ) {
            pieceSet.Piece(
                piece = piece,
                tint = tint,
                modifier = Modifier.fillMaxSize(.90f),
            )
        }
    }
}

@Composable
private fun PieceOverlay(
    piece: Piece,
    position: Offset,
    cellPx: Float,
    cellDp: androidx.compose.ui.unit.Dp,
    palette: ChessboardPalette,
    pieceSet: PieceSet,
    alpha: Float,
    scale: Float,
    overlayZIndex: Float = 4f,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(cellDp)
            .graphicsLayer {
                translationX = position.x - cellPx / 2f
                translationY = position.y - cellPx / 2f
                this.alpha = alpha
            }
            .zIndex(overlayZIndex),
        contentAlignment = Alignment.Center,
    ) {
        pieceSet.Piece(
            piece = piece,
            tint = if (piece.color == Color.WHITE) palette.whitePiece else palette.blackPiece,
            modifier = Modifier.fillMaxSize(scale),
        )
    }
}

@Composable
private fun ChessboardSquare(
    square: Square,
    piece: Piece?,
    dark: Boolean,
    selected: Boolean,
    legalTarget: Boolean,
    captureTarget: Boolean,
    feedback: BoardSquareFeedback,
    check: Boolean,
    extraHighlight: Boolean,
    tapEnabled: Boolean,
    palette: ChessboardPalette,
    pieceSet: PieceSet,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val states = buildList {
        if (selected) add("selected")
        if (captureTarget) add("capture") else if (legalTarget) add("legal move")
        when (feedback.history) {
            BoardHistoryRole.NONE -> Unit
            BoardHistoryRole.ORIGIN -> add("last move origin")
            BoardHistoryRole.DESTINATION -> add("last move destination")
        }
        if (check) add("check")
        when (feedback.premove) {
            BoardPremoveRole.NONE -> Unit
            BoardPremoveRole.ORIGIN -> add("premove origin")
            BoardPremoveRole.DESTINATION -> add("premove destination")
            BoardPremoveRole.PENDING_ORIGIN -> add("pending premove origin")
        }
        if (extraHighlight) add("highlighted")
    }
    var squareModifier = modifier
        .testTag("square-${square.algebraic}")
        .background(if (dark) palette.darkSquare else palette.lightSquare)
        .semantics {
            contentDescription = squareDescription(square, piece)
            stateDescription = states.joinToString().ifEmpty { "idle" }
        }
    if (tapEnabled) squareModifier = squareModifier.clickable(onClick = onClick)

    Box(modifier = squareModifier, contentAlignment = Alignment.Center) {
        BoardFeedbackUnderPiece(
            darkSquare = dark,
            feedback = feedback,
            selected = selected,
            legalTarget = legalTarget,
            captureTarget = captureTarget,
            extraHighlight = palette.extraHighlight.takeIf { extraHighlight },
            modifier = Modifier.fillMaxSize(),
        )
        if (piece != null) {
            pieceSet.Piece(
                piece = piece,
                tint = if (piece.color == Color.WHITE) palette.whitePiece else palette.blackPiece,
                modifier = Modifier.fillMaxSize(0.90f).testTag("piece-${square.algebraic}-${pieceSet.id}"),
            )
        }
        BoardFeedbackCheckFrame(
            visible = check,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ChessboardArrows(
    arrows: List<ChessboardArrow>,
    orientation: ChessboardOrientation,
    palette: ChessboardPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cell = size.width / 8f
        val stroke = cell * 0.16f
        arrows.forEach { arrow ->
            val start = squareCenter(arrow.from, size.width, orientation)
            val end = squareCenter(arrow.to, size.width, orientation)
            val dx = end.x - start.x
            val dy = end.y - start.y
            val length = sqrt(dx * dx + dy * dy)
            if (length <= 0f) return@forEach
            val ux = dx / length
            val uy = dy / length
            val headLength = cell * 0.38f
            val headWidth = cell * 0.22f
            val lineEnd = Offset(end.x - ux * headLength * 0.55f, end.y - uy * headLength * 0.55f)
            val color = when (arrow.style) {
                ChessboardArrowStyle.PRIMARY -> palette.primaryArrow
                ChessboardArrowStyle.SECONDARY -> palette.secondaryArrow
                ChessboardArrowStyle.WARNING -> palette.warningArrow
            }
            drawLine(color, start, lineEnd, stroke, StrokeCap.Round)
            val base = Offset(end.x - ux * headLength, end.y - uy * headLength)
            val perpendicularX = -uy
            val perpendicularY = ux
            val path = Path().apply {
                moveTo(end.x, end.y)
                lineTo(base.x + perpendicularX * headWidth, base.y + perpendicularY * headWidth)
                lineTo(base.x - perpendicularX * headWidth, base.y - perpendicularY * headWidth)
                close()
            }
            drawPath(path, color)
        }
    }
}

@Composable
private fun PromotionPicker(
    moves: List<Move>,
    color: Color,
    palette: ChessboardPalette,
    pieceSet: PieceSet,
    onChoose: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedTypes = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), tonalElevation = 0.dp, shadowElevation = 4.dp) {
        Row {
            orderedTypes.forEach { type ->
                val move = moves.firstOrNull { it.promotion == type } ?: return@forEach
                val label = type.name.lowercase()
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("promotion-choice-$label")
                        .semantics { contentDescription = "Promote to ${type.displayName()}" }
                        .clickable { onChoose(move) },
                    contentAlignment = Alignment.Center,
                ) {
                    pieceSet.Piece(
                        piece = Piece(color, type),
                        tint = if (color == Color.WHITE) palette.whitePiece else palette.blackPiece,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
        }
    }
}

private fun squareAtVisual(column: Int, row: Int, orientation: ChessboardOrientation): Square = when (orientation) {
    ChessboardOrientation.WHITE -> Square.of(column, 7 - row)
    ChessboardOrientation.BLACK -> Square.of(7 - column, row)
}

private fun squareFromOffset(offset: Offset, size: IntSize, orientation: ChessboardOrientation): Square {
    val cell = size.width / 8f
    val column = floor(offset.x / cell).toInt().coerceIn(0, 7)
    val row = floor(offset.y / cell).toInt().coerceIn(0, 7)
    return squareAtVisual(column, row, orientation)
}

private fun squareCenter(square: Square, boardWidth: Float, orientation: ChessboardOrientation): Offset {
    val cell = boardWidth / 8f
    val visualColumn: Int
    val visualRow: Int
    when (orientation) {
        ChessboardOrientation.WHITE -> {
            visualColumn = square.file
            visualRow = 7 - square.rank
        }
        ChessboardOrientation.BLACK -> {
            visualColumn = 7 - square.file
            visualRow = square.rank
        }
    }
    return Offset((visualColumn + 0.5f) * cell, (visualRow + 0.5f) * cell)
}

private fun lerpOffset(start: Offset, end: Offset, fraction: Float): Offset = Offset(
    x = start.x + (end.x - start.x) * fraction,
    y = start.y + (end.y - start.y) * fraction,
)

private fun squareDescription(square: Square, piece: Piece?): String = if (piece == null) {
    "${square.algebraic}, empty"
} else {
    "${square.algebraic}, ${piece.color.displayName()} ${piece.type.displayName().lowercase()}"
}

private fun Color.displayName(): String = when (this) {
    Color.WHITE -> "White"
    Color.BLACK -> "Black"
}

private fun PieceType.displayName(): String = name.lowercase().replaceFirstChar(Char::uppercase)

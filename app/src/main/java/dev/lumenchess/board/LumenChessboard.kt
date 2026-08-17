package dev.lumenchess.board

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square
import kotlin.math.floor
import kotlin.math.sqrt

@Composable
fun LumenChessboard(
    position: Position,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    orientation: ChessboardOrientation = ChessboardOrientation.WHITE,
    input: ChessboardInput = ChessboardInput(),
    highlights: ChessboardHighlights = ChessboardHighlights(),
    arrows: List<ChessboardArrow> = emptyList(),
    palette: ChessboardPalette = ChessboardPalette.default(),
    pieceSet: PieceSet = LumenVectorPieceSet,
) {
    val legalMoves = remember(position) { MoveGenerator.legalMoves(position) }
    var selectedSquare by remember(position) { mutableStateOf<Square?>(null) }
    var pendingPromotion by remember(position) { mutableStateOf<List<Move>>(emptyList()) }

    val checkSquare = remember(position, highlights.showCheck) {
        if (!highlights.showCheck || !MoveGenerator.isInCheck(position, position.sideToMove)) {
            null
        } else {
            position.board.indexOfFirst { piece -> piece == Piece(position.sideToMove, PieceType.KING) }
                .takeIf { it >= 0 }?.let(Square::fromIndex)
        }
    }

    fun submitCandidates(candidates: List<Move>) {
        if (candidates.isEmpty()) return
        val promotionCandidates = candidates.filter { it.promotion != null }
        when {
            promotionCandidates.isEmpty() -> onMove(candidates.first())
            input.promotionPolicy == PromotionPolicy.AUTO_QUEEN -> onMove(
                promotionCandidates.firstOrNull { it.promotion == PieceType.QUEEN } ?: promotionCandidates.first(),
            )
            else -> pendingPromotion = promotionCandidates
        }
    }

    fun submitInput(from: Square, target: Square): Boolean {
        if (pendingPromotion.isNotEmpty()) return false
        val candidates = ChessboardMoveResolver.candidates(position, legalMoves, from, target)
        if (candidates.isEmpty()) return false
        submitCandidates(candidates)
        selectedSquare = null
        return true
    }

    fun handleTap(target: Square) {
        if (!input.tapEnabled || pendingPromotion.isNotEmpty()) return
        val selected = selectedSquare
        if (selected == null) {
            if (position[target]?.color == position.sideToMove) selectedSquare = target
            return
        }
        if (submitInput(selected, target)) return
        if (position[target]?.color == position.sideToMove) selectedSquare = target
    }

    val dragModifier = if (input.dragEnabled) {
        Modifier.pointerInput(position, orientation, legalMoves, pendingPromotion) {
            var dragFrom: Square? = null
            var dragPosition: Offset? = null
            detectDragGestures(
                onDragStart = { offset ->
                    val square = squareFromOffset(offset, size, orientation)
                    dragFrom = square.takeIf { position[it]?.color == position.sideToMove }
                    dragPosition = offset
                    if (dragFrom != null) selectedSquare = dragFrom
                },
                onDrag = { change, _ -> dragPosition = change.position; change.consume() },
                onDragEnd = {
                    val from = dragFrom
                    val target = dragPosition?.let { squareFromOffset(it, size, orientation) }
                    if (from != null && target != null) {
                        if (!submitInput(from, target)) selectedSquare = null
                    } else selectedSquare = null
                    dragFrom = null; dragPosition = null
                },
                onDragCancel = { selectedSquare = null; dragFrom = null; dragPosition = null },
            )
        }
    } else Modifier

    Box(modifier = modifier.aspectRatio(1f).testTag(CHESSBOARD_TEST_TAG).then(dragModifier)) {
        Column(Modifier.fillMaxSize()) {
            repeat(8) { visualRow ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    repeat(8) { visualColumn ->
                        val square = squareAtVisual(visualColumn, visualRow, orientation)
                        val piece = position[square]
                        val selected = selectedSquare
                        val candidates = if (selected != null && highlights.showLegalMoves) {
                            ChessboardMoveResolver.candidates(position, legalMoves, selected, square)
                        } else emptyList()
                        val legalTarget = candidates.isNotEmpty()
                        val captureTarget = candidates.any { ChessboardMoveResolver.isCapture(position, it) }
                        val lastMove = highlights.lastMove
                        ChessboardSquare(
                            modifier = Modifier.weight(1f).fillMaxHeight(), square = square, piece = piece,
                            dark = (square.file + square.rank) % 2 == 0, selected = square == selectedSquare,
                            legalTarget = legalTarget, captureTarget = captureTarget,
                            lastMove = lastMove != null && (square == lastMove.from || square == lastMove.to),
                            check = square == checkSquare, premove = square in highlights.premoveSquares,
                            extraHighlight = square in highlights.extraSquares,
                            tapEnabled = input.tapEnabled && pendingPromotion.isEmpty(), palette = palette,
                            pieceSet = pieceSet, onClick = { handleTap(square) },
                        )
                    }
                }
            }
        }
        if (arrows.isNotEmpty()) {
            ChessboardArrows(arrows, orientation, palette, Modifier.fillMaxSize().testTag(CHESSBOARD_ARROWS_TEST_TAG))
        }
        if (pendingPromotion.isNotEmpty()) {
            PromotionPicker(
                moves = pendingPromotion, color = position.sideToMove, palette = palette, pieceSet = pieceSet,
                onChoose = { move -> pendingPromotion = emptyList(); selectedSquare = null; onMove(move) },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun ChessboardSquare(
    square: Square, piece: Piece?, dark: Boolean, selected: Boolean, legalTarget: Boolean,
    captureTarget: Boolean, lastMove: Boolean, check: Boolean, premove: Boolean,
    extraHighlight: Boolean, tapEnabled: Boolean, palette: ChessboardPalette, pieceSet: PieceSet,
    onClick: () -> Unit, modifier: Modifier = Modifier,
) {
    val states = buildList {
        if (selected) add("selected")
        if (captureTarget) add("capture") else if (legalTarget) add("legal move")
        if (lastMove) add("last move"); if (check) add("check"); if (premove) add("premove")
        if (extraHighlight) add("highlighted")
    }
    var squareModifier = modifier.testTag("square-${square.algebraic}")
        .background(if (dark) palette.darkSquare else palette.lightSquare)
        .semantics {
            contentDescription = squareDescription(square, piece)
            stateDescription = states.joinToString().ifEmpty { "idle" }
        }
    if (tapEnabled) squareModifier = squareModifier.clickable(onClick = onClick)

    Box(modifier = squareModifier, contentAlignment = Alignment.Center) {
        if (lastMove) HighlightOverlay(palette.lastMove)
        if (premove) HighlightOverlay(palette.premove)
        if (extraHighlight) HighlightOverlay(palette.extraHighlight)
        if (selected) HighlightOverlay(palette.selected)
        if (check) HighlightOverlay(palette.check)
        when {
            captureTarget -> HighlightOverlay(palette.legalCapture)
            legalTarget -> Box(Modifier.size(14.dp).background(palette.legalMove, CircleShape))
        }
        if (piece != null) {
            pieceSet.Piece(
                piece = piece,
                tint = if (piece.color == Color.WHITE) palette.whitePiece else palette.blackPiece,
                modifier = Modifier.fillMaxSize(0.78f).testTag("piece-${square.algebraic}-${pieceSet.id}"),
            )
        }
    }
}

@Composable
private fun HighlightOverlay(color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxSize().background(color))
}

@Composable
private fun ChessboardArrows(
    arrows: List<ChessboardArrow>, orientation: ChessboardOrientation, palette: ChessboardPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cell = size.width / 8f
        val stroke = cell * 0.16f
        arrows.forEach { arrow ->
            val start = squareCenter(arrow.from, size.width, orientation)
            val end = squareCenter(arrow.to, size.width, orientation)
            val dx = end.x - start.x; val dy = end.y - start.y
            val length = sqrt(dx * dx + dy * dy)
            if (length <= 0f) return@forEach
            val ux = dx / length; val uy = dy / length
            val headLength = cell * 0.38f; val headWidth = cell * 0.22f
            val lineEnd = Offset(end.x - ux * headLength * 0.55f, end.y - uy * headLength * 0.55f)
            val color = when (arrow.style) {
                ChessboardArrowStyle.PRIMARY -> palette.primaryArrow
                ChessboardArrowStyle.SECONDARY -> palette.secondaryArrow
                ChessboardArrowStyle.WARNING -> palette.warningArrow
            }
            drawLine(color, start, lineEnd, stroke, StrokeCap.Round)
            val base = Offset(end.x - ux * headLength, end.y - uy * headLength)
            val perpendicularX = -uy; val perpendicularY = ux
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
    moves: List<Move>, color: Color, palette: ChessboardPalette, pieceSet: PieceSet,
    onChoose: (Move) -> Unit, modifier: Modifier = Modifier,
) {
    val orderedTypes = listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), tonalElevation = 8.dp, shadowElevation = 8.dp) {
        Row {
            orderedTypes.forEach { type ->
                val move = moves.firstOrNull { it.promotion == type } ?: return@forEach
                val label = type.name.lowercase()
                Box(
                    modifier = Modifier.size(56.dp).testTag("promotion-choice-$label")
                        .semantics { contentDescription = "Promote to ${type.displayName()}" }
                        .clickable { onChoose(move) },
                    contentAlignment = Alignment.Center,
                ) {
                    pieceSet.Piece(
                        piece = Piece(color, type),
                        tint = if (color == Color.WHITE) palette.whitePiece else palette.blackPiece,
                        modifier = Modifier.size(42.dp),
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
        ChessboardOrientation.WHITE -> { visualColumn = square.file; visualRow = 7 - square.rank }
        ChessboardOrientation.BLACK -> { visualColumn = 7 - square.file; visualRow = square.rank }
    }
    return Offset((visualColumn + 0.5f) * cell, (visualRow + 0.5f) * cell)
}

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

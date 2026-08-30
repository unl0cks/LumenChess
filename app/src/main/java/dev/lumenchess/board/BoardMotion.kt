package dev.lumenchess.board

import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.CastleSide
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square

/** Presentation-only source used to select the approved travel duration. */
enum class BoardMovePresentation {
    HUMAN_TAP,
    ENGINE,
    PREMOVE,
}

data class BoardDragVisuals(
    val scale: Float,
    val liftDp: Float,
    val shadowAlpha: Float,
    val shadowOffsetDp: Float,
)

/** Frozen Grounded Precision motion values. Geometry remains outside board measurement. */
object GroundedPrecisionBoardMotion {
    const val pickupDurationMillis = 70
    const val legalDropDurationMillis = 90
    const val illegalDropDurationMillis = 120
    const val humanMoveDurationMillis = 145
    const val engineMoveDurationMillis = 155
    const val premoveDurationMillis = 110
    const val captureFadeDurationMillis = 55
    const val castlingDurationMillis = 165
    const val promotionDurationMillis = 80
    const val promotionInitialScale = .96f

    const val pickupScale = 1.04f
    const val pickupLiftDp = -2f
    const val heldShadowAlpha = .20f
    const val heldShadowBlurDp = 1.3f
    const val heldShadowOffsetDp = 1.5f

    fun durationFor(presentation: BoardMovePresentation): Int = when (presentation) {
        BoardMovePresentation.HUMAN_TAP -> humanMoveDurationMillis
        BoardMovePresentation.ENGINE -> engineMoveDurationMillis
        BoardMovePresentation.PREMOVE -> premoveDurationMillis
    }

    /**
     * [heldFraction] is one while held and reaches zero only when the piece reaches rest. This
     * keeps scale, lift, and shadow continuous for the complete legal/illegal return.
     */
    fun dragVisuals(heldFraction: Float): BoardDragVisuals {
        val fraction = heldFraction.coerceIn(0f, 1f)
        return BoardDragVisuals(
            scale = 1f + (pickupScale - 1f) * fraction,
            liftDp = pickupLiftDp * fraction,
            shadowAlpha = heldShadowAlpha * fraction,
            shadowOffsetDp = heldShadowOffsetDp * fraction,
        )
    }
}

sealed interface BoardMotionPlan {
    data object Atomic : BoardMotionPlan

    data class Castling(
        val move: Move,
        val color: Color,
        val durationMillis: Int,
        val king: CastlingLeg,
        val rook: CastlingLeg,
        val suppressedSquares: Set<Square>,
    ) : BoardMotionPlan

    data class Travel(
        val move: Move,
        val piece: Piece,
        val durationMillis: Int,
        val capturedPiece: Piece?,
        val capturedSquare: Square?,
        val captureFadeDurationMillis: Int,
        val promotion: PromotionBridge? = null,
    ) : BoardMotionPlan
}

data class CastlingLeg(
    val piece: Piece,
    val from: Square,
    val to: Square,
    val zIndex: Float,
) {
    val isStatic: Boolean get() = from == to
}

data class PromotionBridge(
    val outgoingPiece: Piece,
    val promotedPiece: Piece,
    val durationMillis: Int = GroundedPrecisionBoardMotion.promotionDurationMillis,
    val initialScale: Float = GroundedPrecisionBoardMotion.promotionInitialScale,
)

internal object BoardMotionPlanner {
    fun plan(
        previous: Position,
        current: Position,
        move: Move,
        presentation: BoardMovePresentation,
        animationsEnabled: Boolean,
    ): BoardMotionPlan {
        if (!animationsEnabled || previous == current) return BoardMotionPlan.Atomic
        val castlingSide = MoveGenerator.castlingSide(previous, move)
        if (castlingSide != null) return castlingPlan(previous, current, move, castlingSide)

        val sourcePiece = previous[move.from] ?: return BoardMotionPlan.Atomic
        val destinationPiece = current[move.to] ?: return BoardMotionPlan.Atomic
        if (sourcePiece.color != destinationPiece.color) return BoardMotionPlan.Atomic

        val capturedSquare = capturedSquare(previous, move, sourcePiece)
        return BoardMotionPlan.Travel(
            move = move,
            piece = sourcePiece,
            durationMillis = GroundedPrecisionBoardMotion.durationFor(presentation),
            capturedPiece = capturedSquare?.let(previous::get),
            capturedSquare = capturedSquare,
            captureFadeDurationMillis = GroundedPrecisionBoardMotion.captureFadeDurationMillis,
            promotion = promotionBridge(sourcePiece, destinationPiece, move),
        )
    }

    private fun castlingPlan(
        previous: Position,
        current: Position,
        move: Move,
        side: CastleSide,
    ): BoardMotionPlan {
        val king = previous[move.from]
            ?.takeIf { it.type == PieceType.KING }
            ?: return BoardMotionPlan.Atomic
        val rookFrom = previous.castlingRights.rookSquare(king.color, side)
            ?: return BoardMotionPlan.Atomic
        val rook = previous[rookFrom]
            ?.takeIf { it == Piece(king.color, PieceType.ROOK) }
            ?: return BoardMotionPlan.Atomic
        val homeRank = if (king.color == Color.WHITE) 0 else 7
        val kingTo = Square.of(if (side == CastleSide.KING_SIDE) 6 else 2, homeRank)
        val rookTo = Square.of(if (side == CastleSide.KING_SIDE) 5 else 3, homeRank)
        if (current[kingTo] != king || current[rookTo] != rook) return BoardMotionPlan.Atomic

        val kingLeg = CastlingLeg(
            piece = king,
            from = move.from,
            to = kingTo,
            zIndex = 4.1f,
        )
        val rookLeg = CastlingLeg(
            piece = rook,
            from = rookFrom,
            to = rookTo,
            zIndex = 4f,
        )
        return BoardMotionPlan.Castling(
            move = move,
            color = king.color,
            durationMillis = GroundedPrecisionBoardMotion.castlingDurationMillis,
            king = kingLeg,
            rook = rookLeg,
            suppressedSquares = buildSet {
                if (!kingLeg.isStatic) add(kingTo)
                if (!rookLeg.isStatic) add(rookTo)
            },
        )
    }

    private fun promotionBridge(
        sourcePiece: Piece,
        destinationPiece: Piece,
        move: Move,
    ): PromotionBridge? {
        val promotion = move.promotion ?: return null
        if (sourcePiece.type != PieceType.PAWN || destinationPiece.type != promotion) return null
        return PromotionBridge(
            outgoingPiece = sourcePiece,
            promotedPiece = destinationPiece,
        )
    }

    fun capturedSquare(previous: Position, move: Move, sourcePiece: Piece): Square? {
        val direct = previous[move.to]
        if (direct != null && direct.color != sourcePiece.color) return move.to
        if (
            sourcePiece.type == PieceType.PAWN &&
            move.from.file != move.to.file &&
            move.to == previous.enPassantSquare
        ) {
            return Square.of(move.to.file, move.from.rank)
        }
        return null
    }
}

data class BoardMotionIdentity(
    val revision: Long,
    val orientation: ChessboardOrientation,
) {
    fun isStaleAgainst(incoming: BoardMotionIdentity): Boolean = this != incoming
}

object BoardMovePresentationClassifier {
    fun classify(revisionDelta: Long, lastMoverIsHuman: Boolean): BoardMovePresentation = when {
        revisionDelta > 1L -> BoardMovePresentation.PREMOVE
        lastMoverIsHuman -> BoardMovePresentation.HUMAN_TAP
        else -> BoardMovePresentation.ENGINE
    }
}

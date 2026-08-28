package dev.lumenchess.board

import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
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

    data class Travel(
        val move: Move,
        val piece: Piece,
        val durationMillis: Int,
        val capturedPiece: Piece?,
        val capturedSquare: Square?,
        val captureFadeDurationMillis: Int,
    ) : BoardMotionPlan
}

internal object BoardMotionPlanner {
    fun plan(
        previous: Position,
        current: Position,
        move: Move,
        presentation: BoardMovePresentation,
        animationsEnabled: Boolean,
    ): BoardMotionPlan {
        if (!animationsEnabled || previous == current) return BoardMotionPlan.Atomic
        if (MoveGenerator.castlingSide(previous, move) != null) return BoardMotionPlan.Atomic

        val sourcePiece = previous[move.from] ?: return BoardMotionPlan.Atomic
        val destinationPiece = current[move.to] ?: return BoardMotionPlan.Atomic
        if (sourcePiece.color != destinationPiece.color) return BoardMotionPlan.Atomic

        val capturedSquare = capturedSquare(previous, move, sourcePiece)
        return BoardMotionPlan.Travel(
            move = move,
            piece = destinationPiece,
            durationMillis = GroundedPrecisionBoardMotion.durationFor(presentation),
            capturedPiece = capturedSquare?.let(previous::get),
            capturedSquare = capturedSquare,
            captureFadeDurationMillis = GroundedPrecisionBoardMotion.captureFadeDurationMillis,
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

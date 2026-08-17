package dev.lumenchess.feedback

import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.runtime.RuntimeState

/**
 * Converts committed runtime transitions into presentation-only feedback.
 *
 * A null previous state is a baseline (for example after process restore) and intentionally emits
 * nothing so historical moves/start/end events cannot replay. Position-revision equality is the
 * duplicate guard for move feedback; start/end use their own state transitions so non-move runtime
 * events remain independently deduplicated.
 */
class GameFeedbackProjector {
    fun project(previous: RuntimeState?, current: RuntimeState): List<GameFeedbackEvent> {
        if (previous == null) return emptyList()

        val events = mutableListOf<GameFeedbackEvent>()

        if (!previous.started && current.started) {
            events += GameFeedbackEvent.GameStart
        }

        if (
            previous.positionRevision != current.positionRevision &&
            previous.currentNodeId != current.currentNodeId
        ) {
            val node = current.gameTree.node(current.currentNodeId)
            val parent = current.gameTree.parentOf(current.currentNodeId)
            val move = node.move
            if (parent != null && move != null) {
                events += primaryMoveEvent(parent.position, move)
                if (MoveGenerator.isInCheck(current.position, current.position.sideToMove)) {
                    events += GameFeedbackEvent.Check
                }
            }
        }

        if (previous.terminal == null && current.terminal != null) {
            events += GameFeedbackEvent.GameEnd
        }

        return events
    }

    private fun primaryMoveEvent(position: Position, move: Move): GameFeedbackEvent = when {
        MoveGenerator.castlingSide(position, move) != null -> GameFeedbackEvent.Castle
        move.promotion != null -> GameFeedbackEvent.Promotion
        isCapture(position, move) -> GameFeedbackEvent.Capture
        else -> GameFeedbackEvent.Move
    }

    private fun isCapture(position: Position, move: Move): Boolean {
        if (position[move.to] != null) return true
        val movingPiece = position[move.from] ?: return false
        return movingPiece.type == PieceType.PAWN &&
            position.enPassantSquare == move.to &&
            move.from.file != move.to.file
    }
}

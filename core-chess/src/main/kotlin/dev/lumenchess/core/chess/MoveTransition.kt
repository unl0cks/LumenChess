package dev.lumenchess.core.chess

/**
 * Immutable make/unmake carrier. The core keeps Position immutable, so unmake restores the exact
 * pre-move state rather than attempting to reverse mutations field by field.
 */
class MoveTransition private constructor(
    val before: Position,
    val move: Move,
    val after: Position,
) {
    fun unmake(): Position = before

    companion object {
        fun make(position: Position, move: Move): MoveTransition =
            MoveTransition(
                before = position,
                move = move,
                after = MoveGenerator.applyLegalMove(position, move),
            )
    }
}

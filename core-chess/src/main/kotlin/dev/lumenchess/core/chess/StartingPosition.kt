package dev.lumenchess.core.chess

/** M24 reusable, validated starting-position inputs. It contains no Android or persistence state. */
sealed interface StartingPositionInput {
    data object Normal : StartingPositionInput
    data class FenText(val text: String, val variant: Variant = Variant.STANDARD) : StartingPositionInput
    data class Saved(val position: Position) : StartingPositionInput
    data class Odds(val base: StartingPositionInput = Normal, val removed: Set<Square> = emptySet()) : StartingPositionInput
}

object StartingPositionResolver {
    fun resolve(input: StartingPositionInput): Position = when (input) {
        StartingPositionInput.Normal -> Position.initial()
        is StartingPositionInput.FenText -> Fen.parse(input.text, input.variant)
        is StartingPositionInput.Saved -> input.position
        is StartingPositionInput.Odds -> applyOdds(resolve(input.base), input.removed)
    }.also(::validate)

    fun importPgn(text: String): GameTree = Pgn.parseGame(text)
    fun exportPgn(tree: GameTree): String = Pgn.serialize(tree)

    private fun applyOdds(position: Position, removed: Set<Square>): Position {
        val board = position.board.toMutableList()
        removed.forEach { square ->
            require(board[square.index]?.type != PieceType.KING) { "Odds cannot remove a king" }
            board[square.index] = null
        }
        return Position(board, position.sideToMove, position.castlingRights, position.enPassantSquare,
            position.halfmoveClock, position.fullmoveNumber, position.variant)
    }

    private fun validate(position: Position) {
        require(position.board.count { it == Piece(Color.WHITE, PieceType.KING) } == 1) { "Position must contain exactly one white king" }
        require(position.board.count { it == Piece(Color.BLACK, PieceType.KING) } == 1) { "Position must contain exactly one black king" }
        require(!MoveGenerator.isInCheck(position, position.sideToMove.opposite) || MoveGenerator.legalMoves(position).isNotEmpty()) {
            "Position leaves the non-moving side in an impossible check state"
        }
    }
}

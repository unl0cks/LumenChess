package dev.lumenchess.core.chess

object MoveGenerator {
    private val knightDeltas = arrayOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val kingDeltas = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    private val bishopDirs = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val rookDirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    fun legalMoves(position: Position): List<Move> = pseudoLegalMoves(position).filter { move ->
        val mover = position.sideToMove
        val next = applyUnchecked(position, move)
        !isInCheck(next, mover)
    }

    fun applyLegalMove(position: Position, candidate: Move): Position {
        val legal = legalMoves(position).firstOrNull { it == candidate }
            ?: throw IllegalArgumentException("Illegal move ${candidate.uci} for ${Fen.serialize(position)}")
        return applyUnchecked(position, legal)
    }

    fun isInCheck(position: Position, color: Color): Boolean {
        val kingIndex = position.board.indexOf(Piece(color, PieceType.KING))
        if (kingIndex < 0) return true
        return isSquareAttacked(position, Square.fromIndex(kingIndex), color.opposite)
    }

    fun hasLegalEnPassantCapture(position: Position): Boolean {
        val ep = position.enPassantSquare ?: return false
        val side = position.sideToMove
        val sourceRank = if (side == Color.WHITE) ep.rank - 1 else ep.rank + 1
        if (sourceRank !in 0..7) return false
        for (sourceFile in intArrayOf(ep.file - 1, ep.file + 1)) {
            if (sourceFile !in 0..7) continue
            val from = Square.of(sourceFile, sourceRank)
            if (position[from] == Piece(side, PieceType.PAWN)) {
                val move = Move(from, ep)
                val next = applyUnchecked(position, move)
                if (!isInCheck(next, side)) return true
            }
        }
        return false
    }

    internal fun isSquareAttacked(position: Position, target: Square, byColor: Color): Boolean {
        val pawnSourceRank = if (byColor == Color.WHITE) target.rank - 1 else target.rank + 1
        if (pawnSourceRank in 0..7) {
            for (file in intArrayOf(target.file - 1, target.file + 1)) {
                if (file in 0..7 && position[Square.of(file, pawnSourceRank)] == Piece(byColor, PieceType.PAWN)) return true
            }
        }

        for ((df, dr) in knightDeltas) {
            val f = target.file + df
            val r = target.rank + dr
            if (f in 0..7 && r in 0..7 && position[Square.of(f, r)] == Piece(byColor, PieceType.KNIGHT)) return true
        }

        for ((df, dr) in kingDeltas) {
            val f = target.file + df
            val r = target.rank + dr
            if (f in 0..7 && r in 0..7 && position[Square.of(f, r)] == Piece(byColor, PieceType.KING)) return true
        }

        if (rayAttacked(position, target, byColor, bishopDirs, setOf(PieceType.BISHOP, PieceType.QUEEN))) return true
        if (rayAttacked(position, target, byColor, rookDirs, setOf(PieceType.ROOK, PieceType.QUEEN))) return true
        return false
    }

    private fun rayAttacked(position: Position, target: Square, color: Color, dirs: Array<Pair<Int, Int>>, attackers: Set<PieceType>): Boolean {
        for ((df, dr) in dirs) {
            var f = target.file + df
            var r = target.rank + dr
            while (f in 0..7 && r in 0..7) {
                val piece = position[Square.of(f, r)]
                if (piece != null) {
                    if (piece.color == color && piece.type in attackers) return true
                    break
                }
                f += df
                r += dr
            }
        }
        return false
    }

    private fun pseudoLegalMoves(position: Position): List<Move> {
        val moves = ArrayList<Move>(64)
        position.board.forEachIndexed { index, piece ->
            if (piece == null || piece.color != position.sideToMove) return@forEachIndexed
            val from = Square.fromIndex(index)
            when (piece.type) {
                PieceType.PAWN -> addPawnMoves(position, from, piece.color, moves)
                PieceType.KNIGHT -> addJumpMoves(position, from, piece.color, knightDeltas, moves)
                PieceType.BISHOP -> addSlidingMoves(position, from, piece.color, bishopDirs, moves)
                PieceType.ROOK -> addSlidingMoves(position, from, piece.color, rookDirs, moves)
                PieceType.QUEEN -> {
                    addSlidingMoves(position, from, piece.color, bishopDirs, moves)
                    addSlidingMoves(position, from, piece.color, rookDirs, moves)
                }
                PieceType.KING -> {
                    addJumpMoves(position, from, piece.color, kingDeltas, moves)
                    addCastlingMoves(position, piece.color, moves)
                }
            }
        }
        return moves
    }

    private fun addPawnMoves(position: Position, from: Square, color: Color, moves: MutableList<Move>) {
        val direction = if (color == Color.WHITE) 1 else -1
        val startRank = if (color == Color.WHITE) 1 else 6
        val promotionRank = if (color == Color.WHITE) 7 else 0
        val oneRank = from.rank + direction
        if (oneRank in 0..7) {
            val one = Square.of(from.file, oneRank)
            if (position[one] == null) {
                addPawnMove(from, one, promotionRank, moves)
                if (from.rank == startRank) {
                    val two = Square.of(from.file, from.rank + 2 * direction)
                    if (position[two] == null) moves += Move(from, two)
                }
            }
            for (file in intArrayOf(from.file - 1, from.file + 1)) {
                if (file !in 0..7) continue
                val to = Square.of(file, oneRank)
                val target = position[to]
                if (target != null && target.color != color && target.type != PieceType.KING) {
                    addPawnMove(from, to, promotionRank, moves)
                } else if (to == position.enPassantSquare) {
                    moves += Move(from, to)
                }
            }
        }
    }

    private fun addPawnMove(from: Square, to: Square, promotionRank: Int, moves: MutableList<Move>) {
        if (to.rank == promotionRank) {
            moves += Move(from, to, PieceType.QUEEN)
            moves += Move(from, to, PieceType.ROOK)
            moves += Move(from, to, PieceType.BISHOP)
            moves += Move(from, to, PieceType.KNIGHT)
        } else moves += Move(from, to)
    }

    private fun addJumpMoves(position: Position, from: Square, color: Color, deltas: Array<Pair<Int, Int>>, moves: MutableList<Move>) {
        for ((df, dr) in deltas) {
            val f = from.file + df
            val r = from.rank + dr
            if (f !in 0..7 || r !in 0..7) continue
            val to = Square.of(f, r)
            val target = position[to]
            if (target == null || (target.color != color && target.type != PieceType.KING)) moves += Move(from, to)
        }
    }

    private fun addSlidingMoves(position: Position, from: Square, color: Color, dirs: Array<Pair<Int, Int>>, moves: MutableList<Move>) {
        for ((df, dr) in dirs) {
            var f = from.file + df
            var r = from.rank + dr
            while (f in 0..7 && r in 0..7) {
                val to = Square.of(f, r)
                val target = position[to]
                if (target == null) {
                    moves += Move(from, to)
                } else {
                    if (target.color != color && target.type != PieceType.KING) moves += Move(from, to)
                    break
                }
                f += df
                r += dr
            }
        }
    }

    private fun addCastlingMoves(position: Position, color: Color, moves: MutableList<Move>) {
        if (position.variant != Variant.STANDARD) return
        val rank = if (color == Color.WHITE) 0 else 7
        val king = Square.of(4, rank)
        if (position[king] != Piece(color, PieceType.KING) || isInCheck(position, color)) return
        val rights = position.castlingRights
        val kingSide = if (color == Color.WHITE) rights.whiteKingSide else rights.blackKingSide
        val queenSide = if (color == Color.WHITE) rights.whiteQueenSide else rights.blackQueenSide
        val opponent = color.opposite

        if (kingSide) {
            val f = Square.of(5, rank)
            val g = Square.of(6, rank)
            val rook = Square.of(7, rank)
            if (position[f] == null && position[g] == null && position[rook] == Piece(color, PieceType.ROOK) &&
                !isSquareAttacked(position, f, opponent) && !isSquareAttacked(position, g, opponent)) {
                moves += Move(king, g)
            }
        }
        if (queenSide) {
            val d = Square.of(3, rank)
            val c = Square.of(2, rank)
            val b = Square.of(1, rank)
            val rook = Square.of(0, rank)
            if (position[d] == null && position[c] == null && position[b] == null && position[rook] == Piece(color, PieceType.ROOK) &&
                !isSquareAttacked(position, d, opponent) && !isSquareAttacked(position, c, opponent)) {
                moves += Move(king, c)
            }
        }
    }

    private fun applyUnchecked(position: Position, move: Move): Position {
        val board = position.board.toMutableList()
        val moving = board[move.from.index] ?: throw IllegalArgumentException("No piece on ${move.from.algebraic}")
        var captured = board[move.to.index]
        board[move.from.index] = null

        if (moving.type == PieceType.PAWN && move.to == position.enPassantSquare && captured == null && move.from.file != move.to.file) {
            val capturedSquare = Square.of(move.to.file, move.from.rank)
            captured = board[capturedSquare.index]
            board[capturedSquare.index] = null
        }

        if (moving.type == PieceType.KING && kotlin.math.abs(move.to.file - move.from.file) == 2 && position.variant == Variant.STANDARD) {
            val rank = move.from.rank
            if (move.to.file == 6) {
                val rookFrom = Square.of(7, rank)
                val rookTo = Square.of(5, rank)
                board[rookTo.index] = board[rookFrom.index]
                board[rookFrom.index] = null
            } else if (move.to.file == 2) {
                val rookFrom = Square.of(0, rank)
                val rookTo = Square.of(3, rank)
                board[rookTo.index] = board[rookFrom.index]
                board[rookFrom.index] = null
            }
        }

        board[move.to.index] = if (move.promotion != null) Piece(moving.color, move.promotion) else moving

        var rights = position.castlingRights
        rights = updateCastlingRights(rights, moving, move.from, move.to, captured)

        val newEp = if (moving.type == PieceType.PAWN && kotlin.math.abs(move.to.rank - move.from.rank) == 2) {
            Square.of(move.from.file, (move.from.rank + move.to.rank) / 2)
        } else null
        val newHalfmove = if (moving.type == PieceType.PAWN || captured != null) 0 else position.halfmoveClock + 1
        val newFullmove = if (position.sideToMove == Color.BLACK) position.fullmoveNumber + 1 else position.fullmoveNumber

        return Position(
            board = board,
            sideToMove = position.sideToMove.opposite,
            castlingRights = rights,
            enPassantSquare = newEp,
            halfmoveClock = newHalfmove,
            fullmoveNumber = newFullmove,
            variant = position.variant,
        )
    }

    private fun updateCastlingRights(rights: CastlingRights, moving: Piece, from: Square, to: Square, captured: Piece?): CastlingRights {
        var wk = rights.whiteKingSide
        var wq = rights.whiteQueenSide
        var bk = rights.blackKingSide
        var bq = rights.blackQueenSide

        if (moving.type == PieceType.KING) {
            if (moving.color == Color.WHITE) { wk = false; wq = false } else { bk = false; bq = false }
        }
        if (moving.type == PieceType.ROOK) {
            when (from.algebraic) {
                "h1" -> wk = false; "a1" -> wq = false; "h8" -> bk = false; "a8" -> bq = false
            }
        }
        if (captured?.type == PieceType.ROOK) {
            when (to.algebraic) {
                "h1" -> wk = false; "a1" -> wq = false; "h8" -> bk = false; "a8" -> bq = false
            }
        }
        return CastlingRights(wk, wq, bk, bq)
    }
}

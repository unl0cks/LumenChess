package dev.lumenchess.core.chess

import kotlin.math.abs

object MoveGenerator {
    private val knightDeltas = arrayOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
    private val kingDeltas = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    private val bishopDirs = arrayOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val rookDirs = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private data class CastlingSpec(
        val side: CastleSide,
        val kingFrom: Square,
        val rookFrom: Square,
        val kingTo: Square,
        val rookTo: Square,
    )

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

    fun castlingSide(position: Position, move: Move): CastleSide? {
        val moving = position[move.from] ?: return null
        if (moving.type != PieceType.KING || moving.color != position.sideToMove) return null
        return castlingSpecForMove(position, move, moving)?.side
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
                    addCastlingMoves(position, from, piece.color, moves)
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

    private fun addCastlingMoves(position: Position, kingFrom: Square, color: Color, moves: MutableList<Move>) {
        val homeRank = if (color == Color.WHITE) 0 else 7
        if (kingFrom.rank != homeRank || position[kingFrom] != Piece(color, PieceType.KING)) return
        if (position.variant == Variant.STANDARD && kingFrom.file != 4) return
        if (isInCheck(position, color)) return

        for (side in CastleSide.entries) {
            val spec = castlingSpec(position, color, side, kingFrom) ?: continue
            if (!canCastle(position, spec, color)) continue
            val encodedTo = if (position.variant == Variant.CHESS960) spec.rookFrom else spec.kingTo
            moves += Move(kingFrom, encodedTo)
        }
    }

    private fun castlingSpec(position: Position, color: Color, side: CastleSide, kingFrom: Square): CastlingSpec? {
        val rookFrom = position.castlingRights.rookSquare(color, side) ?: return null
        val homeRank = if (color == Color.WHITE) 0 else 7
        if (kingFrom.rank != homeRank || rookFrom.rank != homeRank) return null
        if (side == CastleSide.KING_SIDE && rookFrom.file <= kingFrom.file) return null
        if (side == CastleSide.QUEEN_SIDE && rookFrom.file >= kingFrom.file) return null
        return CastlingSpec(
            side = side,
            kingFrom = kingFrom,
            rookFrom = rookFrom,
            kingTo = Square.of(if (side == CastleSide.KING_SIDE) 6 else 2, homeRank),
            rookTo = Square.of(if (side == CastleSide.KING_SIDE) 5 else 3, homeRank),
        )
    }

    private fun castlingSpecForMove(position: Position, move: Move, moving: Piece): CastlingSpec? {
        if (moving.type != PieceType.KING || move.promotion != null) return null
        if (move.from.rank != if (moving.color == Color.WHITE) 0 else 7) return null
        for (side in CastleSide.entries) {
            val spec = castlingSpec(position, moving.color, side, move.from) ?: continue
            val encodedTo = if (position.variant == Variant.CHESS960) spec.rookFrom else spec.kingTo
            if (move.to == encodedTo) return spec
        }
        return null
    }

    private fun canCastle(position: Position, spec: CastlingSpec, color: Color): Boolean {
        if (position[spec.rookFrom] != Piece(color, PieceType.ROOK)) return false

        val pathSquares = (squaresAfterStart(spec.kingFrom, spec.kingTo) + squaresAfterStart(spec.rookFrom, spec.rookTo)).toSet()
        for (square in pathSquares) {
            if (square == spec.kingFrom || square == spec.rookFrom) continue
            if (position[square] != null) return false
        }

        val opponent = color.opposite
        for (square in squaresAfterStart(spec.kingFrom, spec.kingTo)) {
            if (isSquareAttacked(position, square, opponent)) return false
        }
        return true
    }

    private fun squaresAfterStart(from: Square, to: Square): List<Square> {
        if (from == to) return emptyList()
        require(from.rank == to.rank) { "Castling path must stay on one rank" }
        val step = if (to.file > from.file) 1 else -1
        val result = ArrayList<Square>(abs(to.file - from.file))
        var file = from.file + step
        while (true) {
            result += Square.of(file, from.rank)
            if (file == to.file) break
            file += step
        }
        return result
    }

    private fun applyUnchecked(position: Position, move: Move): Position {
        val board = position.board.toMutableList()
        val moving = board[move.from.index] ?: throw IllegalArgumentException("No piece on ${move.from.algebraic}")
        val castling = castlingSpecForMove(position, move, moving)
        var captured: Piece? = null
        var capturedSquare: Square? = null

        if (castling != null) {
            val rook = board[castling.rookFrom.index]
                ?: throw IllegalArgumentException("No castling rook on ${castling.rookFrom.algebraic}")
            board[castling.kingFrom.index] = null
            board[castling.rookFrom.index] = null
            board[castling.kingTo.index] = moving
            board[castling.rookTo.index] = rook
        } else {
            captured = board[move.to.index]
            capturedSquare = move.to
            board[move.from.index] = null

            if (moving.type == PieceType.PAWN && move.to == position.enPassantSquare && captured == null && move.from.file != move.to.file) {
                val epCapturedSquare = Square.of(move.to.file, move.from.rank)
                captured = board[epCapturedSquare.index]
                capturedSquare = epCapturedSquare
                board[epCapturedSquare.index] = null
            }

            board[move.to.index] = if (move.promotion != null) Piece(moving.color, move.promotion) else moving
        }

        var rights = position.castlingRights
        rights = updateCastlingRights(rights, moving, move.from, captured, capturedSquare)

        val newEp = if (castling == null && moving.type == PieceType.PAWN && abs(move.to.rank - move.from.rank) == 2) {
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

    private fun updateCastlingRights(
        rights: CastlingRights,
        moving: Piece,
        from: Square,
        captured: Piece?,
        capturedSquare: Square?,
    ): CastlingRights {
        var updated = rights
        if (moving.type == PieceType.KING) {
            updated = updated.withoutColor(moving.color)
        } else if (moving.type == PieceType.ROOK) {
            updated = updated.withoutRook(moving.color, from)
        }
        if (captured?.type == PieceType.ROOK && capturedSquare != null) {
            updated = updated.withoutRook(captured.color, capturedSquare)
        }
        return updated
    }
}

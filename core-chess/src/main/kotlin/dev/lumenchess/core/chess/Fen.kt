package dev.lumenchess.core.chess

object Fen {
    fun parse(fen: String): Position {
        val fields = fen.trim().split(Regex("\\s+"))
        require(fields.size == 6) { "FEN must contain 6 fields" }

        val board = parseBoard(fields[0])
        val side = when (fields[1]) {
            "w" -> Color.WHITE
            "b" -> Color.BLACK
            else -> throw IllegalArgumentException("Invalid active color: ${fields[1]}")
        }
        val castling = parseCastling(fields[2])
        val ep = parseEnPassant(fields[3], side, board)
        val halfmove = fields[4].toIntOrNull() ?: throw IllegalArgumentException("Invalid halfmove clock")
        val fullmove = fields[5].toIntOrNull() ?: throw IllegalArgumentException("Invalid fullmove number")
        require(halfmove >= 0) { "Halfmove clock cannot be negative" }
        require(fullmove >= 1) { "Fullmove number must be at least 1" }

        validateKings(board)
        validateStandardCastling(board, castling)

        return Position(board, side, castling, ep, halfmove, fullmove)
    }

    fun serialize(position: Position): String {
        val boardField = (7 downTo 0).joinToString("/") { rank ->
            buildString {
                var empty = 0
                for (file in 0..7) {
                    val piece = position.board[rank * 8 + file]
                    if (piece == null) {
                        empty++
                    } else {
                        if (empty > 0) append(empty)
                        empty = 0
                        append(piece.toFen())
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        val side = if (position.sideToMove == Color.WHITE) "w" else "b"
        val ep = position.enPassantSquare?.algebraic ?: "-"
        return "$boardField $side ${position.castlingRights.toFen()} $ep ${position.halfmoveClock} ${position.fullmoveNumber}"
    }

    private fun parseBoard(value: String): List<Piece?> {
        val ranks = value.split('/')
        require(ranks.size == 8) { "FEN board must contain 8 ranks" }
        val board = MutableList<Piece?>(64) { null }
        ranks.forEachIndexed { fenRank, token ->
            var file = 0
            token.forEach { char ->
                if (char.isDigit()) {
                    val count = char.digitToInt()
                    require(count in 1..8) { "Invalid empty-square count" }
                    file += count
                } else {
                    require(file < 8) { "Too many squares in rank" }
                    val color = if (char.isUpperCase()) Color.WHITE else Color.BLACK
                    board[(7 - fenRank) * 8 + file] = Piece(color, PieceType.fromFen(char))
                    file++
                }
            }
            require(file == 8) { "Rank ${8 - fenRank} does not contain 8 squares" }
        }
        return board
    }

    private fun parseCastling(value: String): CastlingRights {
        if (value == "-") return CastlingRights()
        require(value.isNotEmpty()) { "Invalid castling field" }
        require(value.toSet().size == value.length) { "Duplicate castling right" }
        require(value.all { it in "KQkq" }) { "Invalid castling right: $value" }
        return CastlingRights('K' in value, 'Q' in value, 'k' in value, 'q' in value)
    }

    private fun parseEnPassant(value: String, side: Color, board: List<Piece?>): Square? {
        if (value == "-") return null
        val square = Square.parse(value)
        val requiredRank = if (side == Color.WHITE) 5 else 2
        require(square.rank == requiredRank) { "Invalid en-passant target rank" }
        val pawnRank = if (side == Color.WHITE) square.rank - 1 else square.rank + 1
        val pawn = board[Square.of(square.file, pawnRank).index]
        require(pawn == Piece(side.opposite, PieceType.PAWN)) { "En-passant target has no matching pawn" }
        require(board[square.index] == null) { "En-passant target must be empty" }
        return square
    }

    private fun validateKings(board: List<Piece?>) {
        val whiteKings = board.count { it == Piece(Color.WHITE, PieceType.KING) }
        val blackKings = board.count { it == Piece(Color.BLACK, PieceType.KING) }
        require(whiteKings == 1 && blackKings == 1) { "Position must contain exactly one king per side" }
    }

    private fun validateStandardCastling(board: List<Piece?>, rights: CastlingRights) {
        if (rights.whiteKingSide || rights.whiteQueenSide) {
            require(board[Square.parse("e1").index] == Piece(Color.WHITE, PieceType.KING)) { "White castling right requires king on e1" }
        }
        if (rights.blackKingSide || rights.blackQueenSide) {
            require(board[Square.parse("e8").index] == Piece(Color.BLACK, PieceType.KING)) { "Black castling right requires king on e8" }
        }
        if (rights.whiteKingSide) require(board[Square.parse("h1").index] == Piece(Color.WHITE, PieceType.ROOK)) { "White K right requires rook h1" }
        if (rights.whiteQueenSide) require(board[Square.parse("a1").index] == Piece(Color.WHITE, PieceType.ROOK)) { "White Q right requires rook a1" }
        if (rights.blackKingSide) require(board[Square.parse("h8").index] == Piece(Color.BLACK, PieceType.ROOK)) { "Black k right requires rook h8" }
        if (rights.blackQueenSide) require(board[Square.parse("a8").index] == Piece(Color.BLACK, PieceType.ROOK)) { "Black q right requires rook a8" }
    }
}

package dev.lumenchess.core.chess

object Fen {
    fun parse(fen: String, variant: Variant = Variant.STANDARD): Position {
        val fields = fen.trim().split(Regex("\\s+"))
        require(fields.size == 6) { "FEN must contain 6 fields" }

        val board = parseBoard(fields[0])
        val side = when (fields[1]) {
            "w" -> Color.WHITE
            "b" -> Color.BLACK
            else -> throw IllegalArgumentException("Invalid active color: ${fields[1]}")
        }
        validateKings(board)
        val castling = parseCastling(fields[2], board, variant)
        val ep = parseEnPassant(fields[3], side, board)
        val halfmove = fields[4].toIntOrNull() ?: throw IllegalArgumentException("Invalid halfmove clock")
        val fullmove = fields[5].toIntOrNull() ?: throw IllegalArgumentException("Invalid fullmove number")
        require(halfmove >= 0) { "Halfmove clock cannot be negative" }
        require(fullmove >= 1) { "Fullmove number must be at least 1" }

        return Position(board, side, castling, ep, halfmove, fullmove, variant)
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
        val castling = when (position.variant) {
            Variant.STANDARD -> position.castlingRights.toFen()
            Variant.CHESS960 -> serializeChess960Castling(position.castlingRights)
        }
        val ep = position.enPassantSquare?.algebraic ?: "-"
        return "$boardField $side $castling $ep ${position.halfmoveClock} ${position.fullmoveNumber}"
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

    private fun parseCastling(value: String, board: List<Piece?>, variant: Variant): CastlingRights = when (variant) {
        Variant.STANDARD -> parseStandardCastling(value, board)
        Variant.CHESS960 -> parseChess960Castling(value, board)
    }

    private fun parseStandardCastling(value: String, board: List<Piece?>): CastlingRights {
        if (value == "-") return CastlingRights()
        require(value.isNotEmpty()) { "Invalid castling field" }
        require(value.toSet().size == value.length) { "Duplicate castling right" }
        require(value.all { it in "KQkq" }) { "Invalid castling right: $value" }
        val rights = CastlingRights('K' in value, 'Q' in value, 'k' in value, 'q' in value)
        validateStandardCastling(board, rights)
        return rights
    }

    private fun parseChess960Castling(value: String, board: List<Piece?>): CastlingRights {
        if (value == "-") return CastlingRights()
        require(value.isNotEmpty()) { "Invalid castling field" }
        require(value.length <= 4) { "A position cannot have more than four castling rights" }
        require(value.toSet().size == value.length) { "Duplicate castling right" }

        var whiteKingSide: Square? = null
        var whiteQueenSide: Square? = null
        var blackKingSide: Square? = null
        var blackQueenSide: Square? = null

        fun assign(color: Color, side: CastleSide, rook: Square) {
            when (color to side) {
                Color.WHITE to CastleSide.KING_SIDE -> {
                    require(whiteKingSide == null) { "Duplicate white kingside castling right" }
                    whiteKingSide = rook
                }
                Color.WHITE to CastleSide.QUEEN_SIDE -> {
                    require(whiteQueenSide == null) { "Duplicate white queenside castling right" }
                    whiteQueenSide = rook
                }
                Color.BLACK to CastleSide.KING_SIDE -> {
                    require(blackKingSide == null) { "Duplicate black kingside castling right" }
                    blackKingSide = rook
                }
                Color.BLACK to CastleSide.QUEEN_SIDE -> {
                    require(blackQueenSide == null) { "Duplicate black queenside castling right" }
                    blackQueenSide = rook
                }
                else -> error("Unreachable color/castle-side combination")
            }
        }

        for (token in value) {
            val color = if (token.isUpperCase()) Color.WHITE else Color.BLACK
            val upper = token.uppercaseChar()
            require(upper == 'K' || upper == 'Q' || upper in 'A'..'H') { "Invalid Chess960 castling right: $token" }
            val king = kingSquare(board, color)
            val homeRank = if (color == Color.WHITE) 0 else 7
            require(king.rank == homeRank) { "Chess960 castling right requires the king on its home rank" }

            val rook = when (upper) {
                'K' -> findXFenRook(board, color, king, CastleSide.KING_SIDE)
                'Q' -> findXFenRook(board, color, king, CastleSide.QUEEN_SIDE)
                else -> {
                    val candidate = Square.of(upper - 'A', homeRank)
                    require(board[candidate.index] == Piece(color, PieceType.ROOK)) {
                        "Chess960 castling right $token requires a ${color.name.lowercase()} rook on ${candidate.algebraic}"
                    }
                    candidate
                }
            }

            val side = when {
                rook.file > king.file -> CastleSide.KING_SIDE
                rook.file < king.file -> CastleSide.QUEEN_SIDE
                else -> throw IllegalArgumentException("Castling rook cannot share the king file")
            }
            if (upper == 'K') require(side == CastleSide.KING_SIDE) { "K/k castling right has no kingside rook" }
            if (upper == 'Q') require(side == CastleSide.QUEEN_SIDE) { "Q/q castling right has no queenside rook" }
            assign(color, side, rook)
        }

        return CastlingRights(
            whiteKingSideRook = whiteKingSide,
            whiteQueenSideRook = whiteQueenSide,
            blackKingSideRook = blackKingSide,
            blackQueenSideRook = blackQueenSide,
        )
    }

    private fun findXFenRook(board: List<Piece?>, color: Color, king: Square, side: CastleSide): Square {
        val homeRank = if (color == Color.WHITE) 0 else 7
        val files: IntProgression = when (side) {
            CastleSide.KING_SIDE -> 7 downTo (king.file + 1)
            CastleSide.QUEEN_SIDE -> 0 until king.file
        }
        for (file in files) {
            val square = Square.of(file, homeRank)
            if (board[square.index] == Piece(color, PieceType.ROOK)) return square
        }
        throw IllegalArgumentException("X-FEN ${if (side == CastleSide.KING_SIDE) "K" else "Q"} right has no matching rook")
    }

    private fun serializeChess960Castling(rights: CastlingRights): String = buildString {
        rights.whiteKingSideRook?.let { append(('A'.code + it.file).toChar()) }
        rights.whiteQueenSideRook?.let { append(('A'.code + it.file).toChar()) }
        rights.blackKingSideRook?.let { append(('a'.code + it.file).toChar()) }
        rights.blackQueenSideRook?.let { append(('a'.code + it.file).toChar()) }
    }.ifEmpty { "-" }

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

    private fun kingSquare(board: List<Piece?>, color: Color): Square =
        Square.fromIndex(board.indexOf(Piece(color, PieceType.KING)).also { require(it >= 0) })

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

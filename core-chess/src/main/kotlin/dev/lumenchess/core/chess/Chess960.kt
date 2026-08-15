package dev.lumenchess.core.chess

/** Programmatic Chess960 starting positions using the conventional 0..959 Scharnagl numbering. */
object Chess960 {
    const val POSITION_COUNT: Int = 960
    const val STANDARD_POSITION_INDEX: Int = 518

    private val knightPairs = arrayOf(
        0 to 1,
        0 to 2,
        0 to 3,
        0 to 4,
        1 to 2,
        1 to 3,
        1 to 4,
        2 to 3,
        2 to 4,
        3 to 4,
    )

    fun startingPosition(index: Int): Position {
        require(index in 0 until POSITION_COUNT) { "Chess960 position index must be in 0..959: $index" }
        val backRank = backRank(index)
        val board = MutableList<Piece?>(64) { null }

        for (file in 0..7) {
            board[Square.of(file, 0).index] = Piece(Color.WHITE, backRank[file])
            board[Square.of(file, 1).index] = Piece(Color.WHITE, PieceType.PAWN)
            board[Square.of(file, 6).index] = Piece(Color.BLACK, PieceType.PAWN)
            board[Square.of(file, 7).index] = Piece(Color.BLACK, backRank[file])
        }

        val kingFile = backRank.indexOf(PieceType.KING)
        val rookFiles = backRank.indices.filter { backRank[it] == PieceType.ROOK }
        val queenSideRookFile = rookFiles.single { it < kingFile }
        val kingSideRookFile = rookFiles.single { it > kingFile }

        return Position(
            board = board,
            sideToMove = Color.WHITE,
            castlingRights = CastlingRights(
                whiteKingSideRook = Square.of(kingSideRookFile, 0),
                whiteQueenSideRook = Square.of(queenSideRookFile, 0),
                blackKingSideRook = Square.of(kingSideRookFile, 7),
                blackQueenSideRook = Square.of(queenSideRookFile, 7),
            ),
            enPassantSquare = null,
            halfmoveClock = 0,
            fullmoveNumber = 1,
            variant = Variant.CHESS960,
        )
    }

    fun backRank(index: Int): List<PieceType> {
        require(index in 0 until POSITION_COUNT) { "Chess960 position index must be in 0..959: $index" }
        var code = index
        val pieces = arrayOfNulls<PieceType>(8)

        val lightSquareBishop = (code % 4) * 2 + 1
        code /= 4
        val darkSquareBishop = (code % 4) * 2
        code /= 4
        pieces[lightSquareBishop] = PieceType.BISHOP
        pieces[darkSquareBishop] = PieceType.BISHOP

        var emptyFiles = pieces.indices.filter { pieces[it] == null }
        val queenSlot = code % 6
        code /= 6
        pieces[emptyFiles[queenSlot]] = PieceType.QUEEN

        emptyFiles = pieces.indices.filter { pieces[it] == null }
        val (firstKnightSlot, secondKnightSlot) = knightPairs[code]
        pieces[emptyFiles[firstKnightSlot]] = PieceType.KNIGHT
        pieces[emptyFiles[secondKnightSlot]] = PieceType.KNIGHT

        emptyFiles = pieces.indices.filter { pieces[it] == null }
        pieces[emptyFiles[0]] = PieceType.ROOK
        pieces[emptyFiles[1]] = PieceType.KING
        pieces[emptyFiles[2]] = PieceType.ROOK

        return pieces.map { it ?: error("Incomplete Chess960 back rank") }
    }
}

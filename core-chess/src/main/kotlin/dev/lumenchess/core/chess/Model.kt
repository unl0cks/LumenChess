package dev.lumenchess.core.chess

enum class Color { WHITE, BLACK;
    val opposite: Color get() = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(val fen: Char) {
    PAWN('p'), KNIGHT('n'), BISHOP('b'), ROOK('r'), QUEEN('q'), KING('k');

    companion object {
        fun fromFen(char: Char): PieceType = entries.firstOrNull { it.fen == char.lowercaseChar() }
            ?: throw IllegalArgumentException("Unknown piece: $char")
    }
}

data class Piece(val color: Color, val type: PieceType) {
    fun toFen(): Char = if (color == Color.WHITE) type.fen.uppercaseChar() else type.fen
}

@JvmInline
value class Square private constructor(val index: Int) {
    init { require(index in 0..63) { "Square index out of range: $index" } }

    val file: Int get() = index and 7
    val rank: Int get() = index ushr 3
    val algebraic: String get() = "${('a'.code + file).toChar()}${rank + 1}"

    companion object {
        fun fromIndex(index: Int): Square = Square(index)
        fun of(file: Int, rank: Int): Square {
            require(file in 0..7 && rank in 0..7) { "Invalid square coordinates: $file,$rank" }
            return Square(rank * 8 + file)
        }
        fun parse(value: String): Square {
            require(value.length == 2) { "Invalid square: $value" }
            val file = value[0].lowercaseChar() - 'a'
            val rank = value[1] - '1'
            return of(file, rank)
        }
    }
}

data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
) {
    init {
        require(promotion == null || promotion in setOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
            "Invalid promotion piece: $promotion"
        }
    }

    val uci: String
        get() = buildString {
            append(from.algebraic)
            append(to.algebraic)
            promotion?.let { append(it.fen) }
        }

    companion object {
        fun parseUci(value: String): Move {
            require(value.length == 4 || value.length == 5) { "Invalid UCI move: $value" }
            val promotion = if (value.length == 5) {
                when (value[4].lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> throw IllegalArgumentException("Invalid UCI promotion: $value")
                }
            } else null
            return Move(Square.parse(value.substring(0, 2)), Square.parse(value.substring(2, 4)), promotion)
        }
    }
}

enum class CastleSide { KING_SIDE, QUEEN_SIDE }

data class CastlingRights(
    val whiteKingSideRook: Square?,
    val whiteQueenSideRook: Square?,
    val blackKingSideRook: Square?,
    val blackQueenSideRook: Square?,
) {
    constructor(
        whiteKingSide: Boolean = false,
        whiteQueenSide: Boolean = false,
        blackKingSide: Boolean = false,
        blackQueenSide: Boolean = false,
    ) : this(
        whiteKingSideRook = if (whiteKingSide) Square.parse("h1") else null,
        whiteQueenSideRook = if (whiteQueenSide) Square.parse("a1") else null,
        blackKingSideRook = if (blackKingSide) Square.parse("h8") else null,
        blackQueenSideRook = if (blackQueenSide) Square.parse("a8") else null,
    )

    val whiteKingSide: Boolean get() = whiteKingSideRook != null
    val whiteQueenSide: Boolean get() = whiteQueenSideRook != null
    val blackKingSide: Boolean get() = blackKingSideRook != null
    val blackQueenSide: Boolean get() = blackQueenSideRook != null

    fun rookSquare(color: Color, side: CastleSide): Square? = when (color to side) {
        Color.WHITE to CastleSide.KING_SIDE -> whiteKingSideRook
        Color.WHITE to CastleSide.QUEEN_SIDE -> whiteQueenSideRook
        Color.BLACK to CastleSide.KING_SIDE -> blackKingSideRook
        Color.BLACK to CastleSide.QUEEN_SIDE -> blackQueenSideRook
        else -> error("Unreachable color/castle-side combination")
    }

    fun withoutColor(color: Color): CastlingRights = when (color) {
        Color.WHITE -> copy(whiteKingSideRook = null, whiteQueenSideRook = null)
        Color.BLACK -> copy(blackKingSideRook = null, blackQueenSideRook = null)
    }

    fun withoutRook(color: Color, square: Square): CastlingRights = when (color) {
        Color.WHITE -> copy(
            whiteKingSideRook = whiteKingSideRook.takeUnless { it == square },
            whiteQueenSideRook = whiteQueenSideRook.takeUnless { it == square },
        )
        Color.BLACK -> copy(
            blackKingSideRook = blackKingSideRook.takeUnless { it == square },
            blackQueenSideRook = blackQueenSideRook.takeUnless { it == square },
        )
    }

    fun toFen(): String = buildString {
        if (whiteKingSide) append('K')
        if (whiteQueenSide) append('Q')
        if (blackKingSide) append('k')
        if (blackQueenSide) append('q')
    }.ifEmpty { "-" }
}

enum class Variant { STANDARD, CHESS960 }

enum class Termination { CHECKMATE, STALEMATE }

enum class GameResult { WHITE_WIN, BLACK_WIN, DRAW }

class Position(
    board: List<Piece?>,
    val sideToMove: Color,
    val castlingRights: CastlingRights,
    val enPassantSquare: Square?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int,
    val variant: Variant = Variant.STANDARD,
) {
    val board: List<Piece?> = board.toList()

    init {
        require(this.board.size == 64) { "Board must contain 64 squares" }
        require(halfmoveClock >= 0) { "Halfmove clock cannot be negative" }
        require(fullmoveNumber >= 1) { "Fullmove number must be at least 1" }
    }

    operator fun get(square: Square): Piece? = board[square.index]

    val repetitionKey: Long get() = PositionKey.compute(this)

    override fun equals(other: Any?): Boolean =
        other is Position &&
            board == other.board &&
            sideToMove == other.sideToMove &&
            castlingRights == other.castlingRights &&
            enPassantSquare == other.enPassantSquare &&
            halfmoveClock == other.halfmoveClock &&
            fullmoveNumber == other.fullmoveNumber &&
            variant == other.variant

    override fun hashCode(): Int {
        var result = board.hashCode()
        result = 31 * result + sideToMove.hashCode()
        result = 31 * result + castlingRights.hashCode()
        result = 31 * result + (enPassantSquare?.hashCode() ?: 0)
        result = 31 * result + halfmoveClock
        result = 31 * result + fullmoveNumber
        result = 31 * result + variant.hashCode()
        return result
    }

    override fun toString(): String = Fen.serialize(this)

    companion object {
        fun initial(): Position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }
}

internal object PositionKey {
    private const val SEED = 0x4C554D454E434853L // "LUMENCHS"
    private val pieceKeys = LongArray(12 * 64)
    private val castlingRookKeys = LongArray(4 * 64)
    private val epFileKeys = LongArray(8)
    private val blackToMoveKey: Long
    private val chess960Key: Long

    init {
        var state = SEED
        fun next(): Long {
            state += -7046029254386353131L
            var z = state
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            return z xor (z ushr 31)
        }
        for (i in pieceKeys.indices) pieceKeys[i] = next()
        for (i in castlingRookKeys.indices) castlingRookKeys[i] = next()
        for (i in epFileKeys.indices) epFileKeys[i] = next()
        blackToMoveKey = next()
        chess960Key = next()
    }

    fun compute(position: Position): Long {
        var key = 0L
        position.board.forEachIndexed { square, piece ->
            if (piece != null) {
                val pieceIndex = piece.color.ordinal * 6 + piece.type.ordinal
                key = key xor pieceKeys[pieceIndex * 64 + square]
            }
        }
        if (position.sideToMove == Color.BLACK) key = key xor blackToMoveKey
        if (position.variant == Variant.CHESS960) key = key xor chess960Key
        key = key xor castlingKey(0, position.castlingRights.whiteKingSideRook)
        key = key xor castlingKey(1, position.castlingRights.whiteQueenSideRook)
        key = key xor castlingKey(2, position.castlingRights.blackKingSideRook)
        key = key xor castlingKey(3, position.castlingRights.blackQueenSideRook)
        val ep = position.enPassantSquare
        if (ep != null && MoveGenerator.hasLegalEnPassantCapture(position)) {
            key = key xor epFileKeys[ep.file]
        }
        return key
    }

    private fun castlingKey(slot: Int, square: Square?): Long =
        square?.let { castlingRookKeys[slot * 64 + it.index] } ?: 0L
}

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

data class CastlingRights(
    val whiteKingSide: Boolean = false,
    val whiteQueenSide: Boolean = false,
    val blackKingSide: Boolean = false,
    val blackQueenSide: Boolean = false,
) {
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
    private val castlingKeys = LongArray(4)
    private val epFileKeys = LongArray(8)
    private val blackToMoveKey: Long

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
        for (i in castlingKeys.indices) castlingKeys[i] = next()
        for (i in epFileKeys.indices) epFileKeys[i] = next()
        blackToMoveKey = next()
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
        if (position.castlingRights.whiteKingSide) key = key xor castlingKeys[0]
        if (position.castlingRights.whiteQueenSide) key = key xor castlingKeys[1]
        if (position.castlingRights.blackKingSide) key = key xor castlingKeys[2]
        if (position.castlingRights.blackQueenSide) key = key xor castlingKeys[3]
        val ep = position.enPassantSquare
        if (ep != null && MoveGenerator.hasLegalEnPassantCapture(position)) {
            key = key xor epFileKeys[ep.file]
        }
        return key
    }
}

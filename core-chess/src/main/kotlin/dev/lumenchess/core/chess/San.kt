package dev.lumenchess.core.chess

open class SanException(message: String) : IllegalArgumentException(message)
class InvalidSanException(message: String) : SanException(message)
class IllegalSanException(message: String) : SanException(message)
class AmbiguousSanException(message: String) : SanException(message)

/**
 * Standard Algebraic Notation backed exclusively by the core legality engine.
 *
 * Parsing never constructs a move and trusts it. It describes a candidate and resolves that
 * description against [MoveGenerator.legalMoves].
 */
object San {
    fun generate(position: Position, move: Move): String {
        val legalMoves = MoveGenerator.legalMoves(position)
        if (move !in legalMoves) {
            throw IllegalSanException("Cannot generate SAN for illegal move ${move.uci} in ${Fen.serialize(position)}")
        }

        val castleSide = MoveGenerator.castlingSide(position, move)
        val base = if (castleSide != null) {
            if (castleSide == CastleSide.KING_SIDE) "O-O" else "O-O-O"
        } else {
            generateOrdinary(position, move, legalMoves)
        }
        return base + checkSuffix(position, move)
    }

    fun parse(position: Position, text: String): Move {
        val original = text.trim()
        if (original.isEmpty()) throw InvalidSanException("SAN cannot be empty")

        val (core, suffix) = splitCheckSuffix(original)
        parseCastle(position, core, suffix)?.let { return it }

        val parsed = parseOrdinarySyntax(core, original)
        val legalMoves = MoveGenerator.legalMoves(position)
        val candidates = legalMoves.filter { move ->
            if (MoveGenerator.castlingSide(position, move) != null) return@filter false
            val piece = position[move.from] ?: return@filter false
            piece.type == parsed.pieceType &&
                move.to == parsed.destination &&
                move.promotion == parsed.promotion &&
                isCapture(position, move) == parsed.capture &&
                (parsed.fromFile == null || move.from.file == parsed.fromFile) &&
                (parsed.fromRank == null || move.from.rank == parsed.fromRank)
        }

        val move = when (candidates.size) {
            0 -> throw IllegalSanException("Illegal SAN '$original' in ${Fen.serialize(position)}")
            1 -> candidates.single()
            else -> throw AmbiguousSanException(
                "Ambiguous SAN '$original' in ${Fen.serialize(position)}: ${candidates.joinToString { it.uci }}",
            )
        }
        validateExplicitSuffix(position, move, suffix, original)
        return move
    }

    private fun generateOrdinary(position: Position, move: Move, legalMoves: List<Move>): String {
        val moving = position[move.from]
            ?: throw IllegalSanException("No piece on ${move.from.algebraic}")
        val capture = isCapture(position, move)

        return buildString {
            if (moving.type == PieceType.PAWN) {
                if (capture) append(fileChar(move.from.file))
            } else {
                append(pieceLetter(moving.type))
                val competitors = legalMoves.filter { candidate ->
                    candidate != move &&
                        candidate.to == move.to &&
                        MoveGenerator.castlingSide(position, candidate) == null &&
                        position[candidate.from]?.let { it.color == moving.color && it.type == moving.type } == true
                }
                if (competitors.isNotEmpty()) {
                    val sameFile = competitors.any { it.from.file == move.from.file }
                    val sameRank = competitors.any { it.from.rank == move.from.rank }
                    when {
                        !sameFile -> append(fileChar(move.from.file))
                        !sameRank -> append(move.from.rank + 1)
                        else -> append(move.from.algebraic)
                    }
                }
            }

            if (capture) append('x')
            append(move.to.algebraic)
            move.promotion?.let {
                append('=')
                append(pieceLetter(it))
            }
        }
    }

    private fun checkSuffix(position: Position, move: Move): String {
        val next = MoveGenerator.applyLegalMove(position, move)
        if (!MoveGenerator.isInCheck(next, next.sideToMove)) return ""
        return if (MoveGenerator.legalMoves(next).isEmpty()) "#" else "+"
    }

    private fun parseCastle(position: Position, core: String, suffix: Char?): Move? {
        val normalized = core.replace('0', 'O').uppercase()
        val side = when (normalized) {
            "O-O" -> CastleSide.KING_SIDE
            "O-O-O" -> CastleSide.QUEEN_SIDE
            else -> return null
        }
        val candidates = MoveGenerator.legalMoves(position).filter { MoveGenerator.castlingSide(position, it) == side }
        val move = when (candidates.size) {
            0 -> throw IllegalSanException("Illegal SAN '$core' in ${Fen.serialize(position)}")
            1 -> candidates.single()
            else -> throw AmbiguousSanException(
                "Ambiguous castling SAN '$core' in ${Fen.serialize(position)}: ${candidates.joinToString { it.uci }}",
            )
        }
        validateExplicitSuffix(position, move, suffix, core)
        return move
    }

    private data class ParsedOrdinary(
        val pieceType: PieceType,
        val destination: Square,
        val promotion: PieceType?,
        val capture: Boolean,
        val fromFile: Int?,
        val fromRank: Int?,
    )

    private fun parseOrdinarySyntax(coreInput: String, original: String): ParsedOrdinary {
        var core = coreInput
        var promotion: PieceType? = null
        if (core.length >= 2 && core[core.length - 2] == '=') {
            promotion = promotionPiece(core.last(), original)
            core = core.dropLast(2)
        }

        if (core.length < 2) throw InvalidSanException("Invalid SAN '$original'")
        val destinationText = core.takeLast(2)
        val destination = try {
            Square.parse(destinationText)
        } catch (_: IllegalArgumentException) {
            throw InvalidSanException("Invalid SAN destination '$destinationText' in '$original'")
        }
        var prefix = core.dropLast(2)

        val pieceType = prefix.firstOrNull()?.let(::pieceTypeFromLetter)
        val actualPieceType = pieceType ?: PieceType.PAWN
        if (pieceType != null) prefix = prefix.drop(1)

        val captureCount = prefix.count { it == 'x' }
        if (captureCount > 1 || (captureCount == 1 && !prefix.endsWith('x'))) {
            throw InvalidSanException("Invalid capture marker in SAN '$original'")
        }
        val capture = captureCount == 1
        val sourceHint = if (capture) prefix.dropLast(1) else prefix

        if (promotion != null && actualPieceType != PieceType.PAWN) {
            throw InvalidSanException("Only pawns can promote in SAN '$original'")
        }

        var fromFile: Int? = null
        var fromRank: Int? = null
        if (actualPieceType == PieceType.PAWN) {
            if (capture) {
                if (sourceHint.length != 1 || sourceHint[0] !in 'a'..'h') {
                    throw InvalidSanException("Pawn capture SAN must contain its source file: '$original'")
                }
                fromFile = sourceHint[0] - 'a'
            } else if (sourceHint.isNotEmpty()) {
                throw InvalidSanException("Invalid pawn SAN '$original'")
            }
        } else {
            when (sourceHint.length) {
                0 -> Unit
                1 -> when (val hint = sourceHint[0]) {
                    in 'a'..'h' -> fromFile = hint - 'a'
                    in '1'..'8' -> fromRank = hint - '1'
                    else -> throw InvalidSanException("Invalid SAN disambiguation '$sourceHint' in '$original'")
                }
                2 -> {
                    val file = sourceHint[0]
                    val rank = sourceHint[1]
                    if (file !in 'a'..'h' || rank !in '1'..'8') {
                        throw InvalidSanException("Invalid SAN source square '$sourceHint' in '$original'")
                    }
                    fromFile = file - 'a'
                    fromRank = rank - '1'
                }
                else -> throw InvalidSanException("Invalid SAN disambiguation '$sourceHint' in '$original'")
            }
        }

        return ParsedOrdinary(actualPieceType, destination, promotion, capture, fromFile, fromRank)
    }

    private fun splitCheckSuffix(text: String): Pair<String, Char?> {
        val suffix = text.lastOrNull().takeIf { it == '+' || it == '#' }
        val core = if (suffix == null) text else text.dropLast(1)
        if (core.endsWith('+') || core.endsWith('#')) {
            throw InvalidSanException("Invalid check suffix in SAN '$text'")
        }
        return core to suffix
    }

    private fun validateExplicitSuffix(position: Position, move: Move, suffix: Char?, original: String) {
        if (suffix == null) return
        val generated = generate(position, move)
        val actual = generated.lastOrNull().takeIf { it == '+' || it == '#' }
        if (actual != suffix) {
            throw IllegalSanException("Incorrect check suffix in SAN '$original'; canonical SAN is '$generated'")
        }
    }

    private fun isCapture(position: Position, move: Move): Boolean {
        val moving = position[move.from] ?: return false
        val target = position[move.to]
        if (target != null && target.color != moving.color) return true
        return moving.type == PieceType.PAWN &&
            move.from.file != move.to.file &&
            move.to == position.enPassantSquare
    }

    private fun promotionPiece(char: Char, original: String): PieceType = when (char.uppercaseChar()) {
        'Q' -> PieceType.QUEEN
        'R' -> PieceType.ROOK
        'B' -> PieceType.BISHOP
        'N' -> PieceType.KNIGHT
        else -> throw InvalidSanException("Invalid promotion piece '$char' in SAN '$original'")
    }

    private fun pieceTypeFromLetter(char: Char): PieceType? = when (char) {
        'K' -> PieceType.KING
        'Q' -> PieceType.QUEEN
        'R' -> PieceType.ROOK
        'B' -> PieceType.BISHOP
        'N' -> PieceType.KNIGHT
        else -> null
    }

    private fun pieceLetter(type: PieceType): Char = when (type) {
        PieceType.KING -> 'K'
        PieceType.QUEEN -> 'Q'
        PieceType.ROOK -> 'R'
        PieceType.BISHOP -> 'B'
        PieceType.KNIGHT -> 'N'
        PieceType.PAWN -> throw IllegalArgumentException("Pawns have no SAN piece letter")
    }

    private fun fileChar(file: Int): Char = ('a'.code + file).toChar()
}

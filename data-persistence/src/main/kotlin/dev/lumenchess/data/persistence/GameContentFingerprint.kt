package dev.lumenchess.data.persistence

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.PieceType
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Deterministic fingerprint of chess semantics used only as a candidate-match signal.
 *
 * Equality does not prove that two separately recorded real-world games are the same event.
 */
object GameContentFingerprint {
    private const val VERSION = 1
    private const val DOMAIN = "LumenChess.GameContent"
    const val PREFIX = "gcf1:"

    private val format = Regex("^gcf1:[0-9a-f]{64}$")

    fun compute(tree: GameTree): String {
        val encoded = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                writeUtf8(output, DOMAIN)
                output.writeInt(VERSION)
                writeUtf8(output, tree.startPosition.variant.name)
                writeUtf8(output, Fen.serialize(tree.startPosition))

                val moves = tree.mainline().map { node ->
                    requireNotNull(node.move) { "Mainline node must contain a move" }
                }
                output.writeInt(moves.size)
                for (move in moves) {
                    output.writeByte(move.from.index)
                    output.writeByte(move.to.index)
                    output.writeByte(promotionCode(move.promotion))
                }
            }
            bytes.toByteArray()
        }
        return PREFIX + MessageDigest.getInstance("SHA-256").digest(encoded).toHexLowercase()
    }

    fun isValid(value: String): Boolean = format.matches(value)

    private fun writeUtf8(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun promotionCode(type: PieceType?): Int = when (type) {
        null -> 0
        PieceType.QUEEN -> 1
        PieceType.ROOK -> 2
        PieceType.BISHOP -> 3
        PieceType.KNIGHT -> 4
        PieceType.PAWN, PieceType.KING -> error("Invalid promotion piece $type")
    }

    private fun ByteArray.toHexLowercase(): String {
        val alphabet = "0123456789abcdef"
        val result = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            result[index * 2] = alphabet[value ushr 4]
            result[index * 2 + 1] = alphabet[value and 0x0f]
        }
        return result.concatToString()
    }
}

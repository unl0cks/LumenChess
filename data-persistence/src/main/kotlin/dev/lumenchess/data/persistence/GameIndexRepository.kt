package dev.lumenchess.data.persistence

import androidx.room3.withReadTransaction
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant

/** A stored game's mainline as raw moves, for indexes (Explorer, ratings) that never need the tree. */
data class MainlineRecord(
    val gameId: PersistentGameId,
    val variant: Variant,
    val startFen: String,
    val result: String?,
    val playedAtEpochMillis: Long?,
    val createdAtEpochMillis: Long,
    val moves: List<Move>,
)

class GameIndexRepository(private val database: LumenDatabase) {
    /** Newest first, at most [limit] games. Rows that cannot be decoded are skipped, never guessed. */
    suspend fun mainlines(limit: Int = 2_000): List<MainlineRecord> = database.withReadTransaction {
        database.gameDao().listGames().take(limit).mapNotNull { row ->
            val game = database.gameDao().gameById(row.id) ?: return@mapNotNull null
            val variant = Variant.entries.firstOrNull { it.name == game.variant } ?: return@mapNotNull null
            val children = database.gameDao().nodesForGame(game.id).groupBy { it.parentNodeId }
            val moves = ArrayList<Move>()
            var parent: String? = null
            while (true) {
                val next = children[parent].orEmpty().minByOrNull { it.siblingOrder } ?: break
                moves += decode(next) ?: break
                parent = next.id
            }
            MainlineRecord(
                gameId = PersistentGameId(game.id),
                variant = variant,
                startFen = game.startFen,
                result = game.result,
                playedAtEpochMillis = game.playedAtEpochMillis,
                createdAtEpochMillis = game.createdAtEpochMillis,
                moves = moves,
            )
        }
    }

    private fun decode(row: GameNodeEntity): Move? {
        if (row.fromSquare !in 0..63 || row.toSquare !in 0..63) return null
        val promotion = when (row.promotionCode) {
            null -> null
            1 -> PieceType.QUEEN
            2 -> PieceType.ROOK
            3 -> PieceType.BISHOP
            4 -> PieceType.KNIGHT
            else -> return null
        }
        return Move(Square.fromIndex(row.fromSquare), Square.fromIndex(row.toSquare), promotion)
    }
}

package dev.lumenchess.data.persistence

import androidx.room3.withWriteTransaction
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameTree
import java.util.UUID

/**
 * M19 live-game writer. A live game keeps one canonical UUID while its authoritative mainline grows.
 * The replace is one Room transaction: REPLACE of the parent game cascades the prior tree/source rows,
 * then the current canonical tree and LOCAL restore metadata are inserted before commit.
 *
 * Live Play has no analysis variations yet, so its authoritative GameTree is a mainline. Import and
 * analysis persistence continues to use [GamePersistenceRepository], which retains full structural
 * variation support.
 */
class LiveGamePersistenceRepository(
    private val database: LumenDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun persist(
        existingId: PersistentGameId?,
        tree: GameTree,
        metadata: GamePersistenceMetadata,
        restoreMetadata: Map<String, String>,
    ): PersistentGameId = database.withWriteTransaction {
        val id = existingId ?: PersistentGameId(newId())
        val game = GameEntity(
            id = id.value,
            variant = tree.startPosition.variant.name,
            startFen = Fen.serialize(tree.startPosition),
            result = tree.result?.name,
            termination = metadata.termination?.name,
            createdAtEpochMillis = metadata.createdAtEpochMillis,
            importedAtEpochMillis = metadata.importedAtEpochMillis,
            playedAtEpochMillis = metadata.playedAtEpochMillis,
            rated = metadata.rated,
            timeControlBaseMillis = metadata.timeControl?.baseMillis,
            timeControlIncrementMillis = metadata.timeControl?.incrementMillis,
            timeControlRaw = metadata.timeControl?.raw,
            whiteParticipantId = null,
            blackParticipantId = null,
            contentFingerprint = GameContentFingerprint.compute(tree),
        )
        database.gameDao().replaceGame(game)

        val nodes = mutableListOf<GameNodeEntity>()
        var parentPersistentId: String? = null
        for (node in tree.mainline()) {
            val move = requireNotNull(node.move)
            val san = requireNotNull(node.san)
            val persistentId = newId()
            nodes += GameNodeEntity(
                id = persistentId,
                gameId = id.value,
                parentNodeId = parentPersistentId,
                siblingOrder = 0,
                fromSquare = move.from.index,
                toSquare = move.to.index,
                promotionCode = when (move.promotion) {
                    null -> null
                    dev.lumenchess.core.chess.PieceType.QUEEN -> 1
                    dev.lumenchess.core.chess.PieceType.ROOK -> 2
                    dev.lumenchess.core.chess.PieceType.BISHOP -> 3
                    dev.lumenchess.core.chess.PieceType.KNIGHT -> 4
                    else -> error("Invalid promotion piece ${move.promotion}")
                },
                san = san,
            )
            parentPersistentId = persistentId
        }
        if (nodes.isNotEmpty()) database.gameDao().insertNodes(nodes)

        val sourceId = newId()
        database.sourceDao().insertSources(
            listOf(
                GameSourceEntity(
                    id = sourceId,
                    gameId = id.value,
                    sourceType = GameSourceType.LOCAL.name,
                    externalGameId = null,
                    externalUrl = null,
                    importedAtEpochMillis = null,
                    lastSyncedAtEpochMillis = System.currentTimeMillis(),
                    sourceAccountId = null,
                ),
            ),
        )
        if (restoreMetadata.isNotEmpty()) {
            database.sourceDao().insertMetadata(
                restoreMetadata.entries.sortedBy { it.key }.map { (key, value) ->
                    GameSourceMetadataEntity(sourceId, key, value)
                },
            )
        }
        id
    }

    suspend fun load(id: PersistentGameId): LoadedCanonicalGame? =
        GamePersistenceRepository(database).loadGame(id)
}

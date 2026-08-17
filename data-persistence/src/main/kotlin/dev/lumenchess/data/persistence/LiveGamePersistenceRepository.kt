package dev.lumenchess.data.persistence

import androidx.room3.withWriteTransaction
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameNode
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import java.util.UUID

/**
 * M19 live-game writer. One live game keeps one canonical UUID while its authoritative mainline
 * grows. Existing canonical data is never replaced: repeated live saves append only missing
 * authoritative mainline plies and update the parent game's mutable live metadata in place.
 *
 * This matters even before Play exposes editing. A canonical game can already carry headers,
 * comments, variations, sources or other user-owned data from the M8/M9 repository boundary.
 * Replacing the parent [GameEntity] would cascade-delete those rows. Instead, an existing canonical
 * mainline must be an exact prefix of the runtime mainline; a stale or contradictory snapshot fails
 * transactionally rather than rewriting canonical history.
 */
class LiveGamePersistenceRepository(
    private val database: LumenDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val canonicalRepository = GamePersistenceRepository(database, newId)

    suspend fun persist(
        existingId: PersistentGameId?,
        tree: GameTree,
        metadata: GamePersistenceMetadata,
        restoreMetadata: Map<String, String>,
    ): PersistentGameId {
        if (existingId == null) {
            return canonicalRepository.saveGame(
                PersistGameRequest(
                    tree = tree,
                    metadata = metadata,
                    sources = listOf(
                        GameSourceDraft(
                            type = GameSourceType.LOCAL,
                            lastSyncedAtEpochMillis = System.currentTimeMillis(),
                            metadata = restoreMetadata,
                        ),
                    ),
                ),
            )
        }

        val existing = canonicalRepository.loadGame(existingId)
            ?: throw PersistenceConflictException("Cannot update missing live game ${existingId.value}")
        requireSameGameIdentity(existing.tree, tree, existingId)

        val existingMainline = existing.tree.mainline()
        val incomingMainline = tree.mainline()
        requireMainlinePrefix(existingMainline, incomingMainline, existingId)

        var mergedTree = existing.tree
        var currentNodeId = existingMainline.lastOrNull()?.id ?: mergedTree.rootId
        for (incoming in incomingMainline.drop(existingMainline.size)) {
            val addition = mergedTree.addMove(
                parentId = currentNodeId,
                move = requireNotNull(incoming.move),
                leadingComments = incoming.leadingComments,
                comments = incoming.comments,
                nags = incoming.nags,
                annotations = incoming.annotations,
            )
            mergedTree = addition.tree
            currentNodeId = addition.nodeId
        }
        mergedTree = mergedTree.withResult(tree.result ?: existing.tree.result)

        val existingFingerprint = GameContentFingerprint.compute(existing.tree)
        val mergedFingerprint = GameContentFingerprint.compute(mergedTree)
        database.withWriteTransaction {
            val storedGame = database.gameDao().gameById(existingId.value)
                ?: throw PersistenceConflictException("Live game ${existingId.value} disappeared while saving")
            if (
                storedGame.variant != tree.startPosition.variant.name ||
                storedGame.startFen != Fen.serialize(tree.startPosition)
            ) {
                throw PersistenceConflictException(
                    "Live snapshot identity does not match canonical game ${existingId.value}",
                )
            }
            val storedFingerprint = storedGame.contentFingerprint
            if (storedFingerprint != null && storedFingerprint != existingFingerprint) {
                throw PersistenceConflictException(
                    "Canonical game ${existingId.value} changed while a live snapshot was being prepared",
                )
            }

            val persistedMainline = persistedMainline(database.gameDao().nodesForGame(existingId.value))
            if (persistedMainline.size != existingMainline.size) {
                throw PersistenceConflictException(
                    "Canonical mainline changed while live game ${existingId.value} was being saved",
                )
            }
            existingMainline.forEachIndexed { index, node ->
                val move = requireNotNull(node.move)
                if (!persistedMainline[index].matches(move)) {
                    throw PersistenceConflictException(
                        "Canonical mainline changed at ply ${index + 1} while live game ${existingId.value} was being saved",
                    )
                }
            }

            var parentPersistentId = persistedMainline.lastOrNull()?.id
            val missingRows = incomingMainline.drop(existingMainline.size).map { node ->
                val move = requireNotNull(node.move)
                val persistentId = newId()
                GameNodeEntity(
                    id = persistentId,
                    gameId = existingId.value,
                    parentNodeId = parentPersistentId,
                    siblingOrder = 0,
                    fromSquare = move.from.index,
                    toSquare = move.to.index,
                    promotionCode = encodePromotion(move.promotion),
                    san = requireNotNull(node.san),
                ).also { parentPersistentId = persistentId }
            }
            if (missingRows.isNotEmpty()) database.gameDao().insertNodes(missingRows)

            check(
                database.gameDao().updateLiveSnapshot(
                    id = existingId.value,
                    result = mergedTree.result?.name,
                    termination = metadata.termination?.name,
                    playedAtEpochMillis = metadata.playedAtEpochMillis,
                    rated = metadata.rated,
                    timeControlBaseMillis = metadata.timeControl?.baseMillis,
                    timeControlIncrementMillis = metadata.timeControl?.incrementMillis,
                    timeControlRaw = metadata.timeControl?.raw,
                    contentFingerprint = mergedFingerprint,
                ) == 1,
            ) { "Live game ${existingId.value} disappeared while updating metadata" }

            val now = System.currentTimeMillis()
            val localSource = database.sourceDao().forGame(existingId.value)
                .firstOrNull { it.sourceType == GameSourceType.LOCAL.name }
                ?: GameSourceEntity(
                    id = newId(),
                    gameId = existingId.value,
                    sourceType = GameSourceType.LOCAL.name,
                    externalGameId = null,
                    externalUrl = null,
                    importedAtEpochMillis = null,
                    lastSyncedAtEpochMillis = now,
                    sourceAccountId = null,
                ).also { database.sourceDao().insertSources(listOf(it)) }

            database.sourceDao().refreshSource(
                id = localSource.id,
                externalUrl = null,
                importedAtEpochMillis = null,
                lastSyncedAtEpochMillis = now,
            )
            if (restoreMetadata.isNotEmpty()) {
                database.sourceDao().upsertMetadata(
                    restoreMetadata.entries.sortedBy { it.key }.map { (key, value) ->
                        GameSourceMetadataEntity(localSource.id, key, value)
                    },
                )
            }
        }
        return existingId
    }

    suspend fun load(id: PersistentGameId): LoadedCanonicalGame? = canonicalRepository.loadGame(id)

    private fun requireSameGameIdentity(
        existing: GameTree,
        incoming: GameTree,
        id: PersistentGameId,
    ) {
        if (
            existing.startPosition.variant != incoming.startPosition.variant ||
            Fen.serialize(existing.startPosition) != Fen.serialize(incoming.startPosition)
        ) {
            throw PersistenceConflictException(
                "Live snapshot start position does not match canonical game ${id.value}",
            )
        }
    }

    private fun requireMainlinePrefix(
        existing: List<GameNode>,
        incoming: List<GameNode>,
        id: PersistentGameId,
    ) {
        if (incoming.size < existing.size) {
            throw PersistenceConflictException(
                "Live snapshot for ${id.value} is older than its canonical mainline",
            )
        }
        existing.forEachIndexed { index, existingNode ->
            if (existingNode.move != incoming[index].move) {
                throw PersistenceConflictException(
                    "Live snapshot for ${id.value} diverges from canonical history at ply ${index + 1}",
                )
            }
        }
    }

    private fun persistedMainline(rows: List<GameNodeEntity>): List<GameNodeEntity> {
        val mainlineByParent = rows
            .filter { it.siblingOrder == 0 }
            .associateBy { it.parentNodeId }
        val result = mutableListOf<GameNodeEntity>()
        var parentId: String? = null
        while (true) {
            val node = mainlineByParent[parentId] ?: break
            result += node
            parentId = node.id
        }
        return result
    }

    private fun GameNodeEntity.matches(move: Move): Boolean =
        fromSquare == move.from.index &&
            toSquare == move.to.index &&
            promotionCode == encodePromotion(move.promotion)

    private fun encodePromotion(type: PieceType?): Int? = when (type) {
        null -> null
        PieceType.QUEEN -> 1
        PieceType.ROOK -> 2
        PieceType.BISHOP -> 3
        PieceType.KNIGHT -> 4
        PieceType.PAWN, PieceType.KING -> throw PersistenceMappingException("Invalid promotion piece $type")
    }
}

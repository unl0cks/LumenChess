package dev.lumenchess.data.persistence

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.GameResult
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import java.util.UUID

class GamePersistenceRepository(
    private val database: LumenDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun saveGame(request: PersistGameRequest): PersistentGameId = database.withWriteTransaction {
        val gameId = PersistentGameId(newId())
        val white = request.whiteParticipant?.let { insertParticipant(it) }
        val black = request.blackParticipant?.let { insertParticipant(it) }
        val metadata = request.metadata
        database.gameDao().insertGame(
            GameEntity(
                id = gameId.value,
                variant = request.tree.startPosition.variant.name,
                startFen = Fen.serialize(request.tree.startPosition),
                result = request.tree.result?.name,
                termination = metadata.termination?.name,
                createdAtEpochMillis = metadata.createdAtEpochMillis,
                importedAtEpochMillis = metadata.importedAtEpochMillis,
                playedAtEpochMillis = metadata.playedAtEpochMillis,
                rated = metadata.rated,
                timeControlBaseMillis = metadata.timeControl?.baseMillis,
                timeControlIncrementMillis = metadata.timeControl?.incrementMillis,
                timeControlRaw = metadata.timeControl?.raw,
                whiteParticipantId = white?.value,
                blackParticipantId = black?.value,
            ),
        )
        val headers = request.tree.headers.entries.mapIndexed { index, (name, value) ->
            GameHeaderEntity(gameId.value, name, value, index)
        }
        if (headers.isNotEmpty()) database.gameDao().insertHeaders(headers)

        val persistedNodeIds = LinkedHashMap<GameNodeId, String>()
        val nodeRows = ArrayList<GameNodeEntity>()
        val commentRows = ArrayList<GameNodeCommentEntity>()
        val nagRows = ArrayList<GameNodeNagEntity>()
        val annotationRows = ArrayList<GameNodeAnnotationEntity>()

        request.tree.rootComments.forEachIndexed { index, text ->
            commentRows += GameNodeCommentEntity(newId(), gameId.value, null, COMMENT_ROOT, index, text)
        }

        fun visit(parentDomainId: GameNodeId, parentPersistentId: String?) {
            request.tree.childrenOf(parentDomainId).forEachIndexed { siblingOrder, node ->
                val move = requireNotNull(node.move) { "Non-root game node must have a move" }
                val san = requireNotNull(node.san) { "Non-root game node must have SAN" }
                val persistentId = newId()
                persistedNodeIds[node.id] = persistentId
                nodeRows += GameNodeEntity(
                    id = persistentId,
                    gameId = gameId.value,
                    parentNodeId = parentPersistentId,
                    siblingOrder = siblingOrder,
                    fromSquare = move.from.index,
                    toSquare = move.to.index,
                    promotionCode = encodePromotion(move.promotion),
                    san = san,
                )
                node.leadingComments.forEachIndexed { index, text ->
                    commentRows += GameNodeCommentEntity(newId(), gameId.value, persistentId, COMMENT_LEADING, index, text)
                }
                node.comments.forEachIndexed { index, text ->
                    commentRows += GameNodeCommentEntity(newId(), gameId.value, persistentId, COMMENT_TRAILING, index, text)
                }
                node.nags.forEachIndexed { index, nag ->
                    nagRows += GameNodeNagEntity(gameId.value, persistentId, index, nag.value)
                }
                node.annotations.toSortedMap().forEach { (key, value) ->
                    annotationRows += GameNodeAnnotationEntity(gameId.value, persistentId, key, value)
                }
                visit(node.id, persistentId)
            }
        }
        visit(request.tree.rootId, null)

        if (nodeRows.isNotEmpty()) database.gameDao().insertNodes(nodeRows)
        if (commentRows.isNotEmpty()) database.gameDao().insertComments(commentRows)
        if (nagRows.isNotEmpty()) database.gameDao().insertNags(nagRows)
        if (annotationRows.isNotEmpty()) database.gameDao().insertAnnotations(annotationRows)

        val sourceRows = request.sources.map { source ->
            val id = newId()
            id to GameSourceEntity(
                id = id,
                gameId = gameId.value,
                sourceType = source.type.name,
                externalGameId = source.externalGameId,
                externalUrl = source.externalUrl,
                importedAtEpochMillis = source.importedAtEpochMillis,
                lastSyncedAtEpochMillis = source.lastSyncedAtEpochMillis,
                sourceAccountId = source.sourceAccountId,
            )
        }
        if (sourceRows.isNotEmpty()) database.sourceDao().insertSources(sourceRows.map { it.second })
        val sourceMetadata = sourceRows.flatMapIndexed { index, pair ->
            request.sources[index].metadata.toSortedMap().map { (key, value) ->
                GameSourceMetadataEntity(pair.first, key, value)
            }
        }
        if (sourceMetadata.isNotEmpty()) database.sourceDao().insertMetadata(sourceMetadata)
        gameId
    }

    suspend fun loadGame(id: PersistentGameId): LoadedCanonicalGame? = database.withReadTransaction {
        val game = database.gameDao().gameById(id.value) ?: return@withReadTransaction null
        val variant = enumValueOrMappingError<Variant>(game.variant, "variant")
        val startPosition = try {
            Fen.parse(game.startFen, variant)
        } catch (error: IllegalArgumentException) {
            throw PersistenceMappingException("Persisted game ${game.id} contains invalid start FEN", error)
        }
        val result = game.result?.let { enumValueOrMappingError<GameResult>(it, "result") }
        val headers = linkedMapOf<String, String>()
        database.gameDao().headersForGame(game.id).forEach { header ->
            if (headers.put(header.name, header.value) != null) {
                throw PersistenceMappingException("Duplicate persisted PGN header '${header.name}' for game ${game.id}")
            }
        }

        val comments = database.gameDao().commentsForGame(game.id)
        val rootComments = comments.filter { it.nodeId == null }.also { roots ->
            if (roots.any { it.kind != COMMENT_ROOT }) {
                throw PersistenceMappingException("Non-root comment row has no node for game ${game.id}")
            }
        }.sortedBy { it.orderIndex }.map { it.text }

        var tree = GameTree.create(startPosition, headers, result, rootComments)
        val nodes = database.gameDao().nodesForGame(game.id)
        val nodeById = nodes.associateBy { it.id }
        if (nodeById.size != nodes.size) throw PersistenceMappingException("Duplicate persistent node id in game ${game.id}")
        nodes.forEach { row ->
            if (row.parentNodeId != null && row.parentNodeId !in nodeById) {
                throw PersistenceMappingException("Node ${row.id} references missing parent ${row.parentNodeId}")
            }
        }
        val children = nodes.groupBy { it.parentNodeId }.mapValues { (_, rows) ->
            val ordered = rows.sortedBy { it.siblingOrder }
            if (ordered.map { it.siblingOrder } != ordered.indices.toList()) {
                throw PersistenceMappingException("Non-contiguous sibling order in game ${game.id}")
            }
            ordered
        }
        val commentGroups = comments.filter { it.nodeId != null }.groupBy { requireNotNull(it.nodeId) }
        val nags = database.gameDao().nagsForGame(game.id).groupBy { it.nodeId }
        val annotations = database.gameDao().annotationsForGame(game.id).groupBy { it.nodeId }
        val visited = HashSet<String>()

        fun rebuild(parentPersistentId: String?, parentDomainId: GameNodeId) {
            for (row in children[parentPersistentId].orEmpty()) {
                if (!visited.add(row.id)) throw PersistenceMappingException("Cycle or duplicate visit at node ${row.id}")
                val move = decodeMove(row)
                val nodeComments = commentGroups[row.id].orEmpty()
                val leading = orderedComments(nodeComments, COMMENT_LEADING, row.id)
                val trailing = orderedComments(nodeComments, COMMENT_TRAILING, row.id)
                val nagValues = nags[row.id].orEmpty().sortedBy { it.orderIndex }.map { dev.lumenchess.core.chess.Nag(it.value) }
                val annotationMap = linkedMapOf<String, String>()
                annotations[row.id].orEmpty().sortedBy { it.key }.forEach { annotation ->
                    if (annotationMap.put(annotation.key, annotation.value) != null) {
                        throw PersistenceMappingException("Duplicate annotation '${annotation.key}' on node ${row.id}")
                    }
                }
                val added = try {
                    tree.addMove(parentDomainId, move, leading, trailing, nagValues, annotationMap)
                } catch (error: IllegalArgumentException) {
                    throw PersistenceMappingException("Persisted move ${move.uci} is illegal at node ${row.id}", error)
                }
                tree = added.tree
                val generatedSan = tree.node(added.nodeId).san
                if (generatedSan != row.san) {
                    throw PersistenceMappingException("Persisted SAN '${row.san}' disagrees with canonical SAN '$generatedSan' at node ${row.id}")
                }
                rebuild(row.id, added.nodeId)
            }
        }
        rebuild(null, tree.rootId)
        if (visited.size != nodes.size) {
            throw PersistenceMappingException("Persisted game ${game.id} contains disconnected or cyclic nodes")
        }

        val sourceEntities = database.sourceDao().forGame(game.id)
        val sourceMeta = if (sourceEntities.isEmpty()) emptyMap() else
            database.sourceDao().metadataForSources(sourceEntities.map { it.id }).groupBy { it.sourceId }
        val sources = sourceEntities.map { entity ->
            GameSourceRecord(
                id = PersistentSourceId(entity.id),
                type = enumValueOrMappingError(entity.sourceType, "source type"),
                externalGameId = entity.externalGameId,
                externalUrl = entity.externalUrl,
                importedAtEpochMillis = entity.importedAtEpochMillis,
                lastSyncedAtEpochMillis = entity.lastSyncedAtEpochMillis,
                sourceAccountId = entity.sourceAccountId,
                metadata = sourceMeta[entity.id].orEmpty().associate { it.key to it.value },
            )
        }

        LoadedCanonicalGame(
            id = id,
            tree = tree,
            metadata = GamePersistenceMetadata(
                createdAtEpochMillis = game.createdAtEpochMillis,
                importedAtEpochMillis = game.importedAtEpochMillis,
                playedAtEpochMillis = game.playedAtEpochMillis,
                rated = game.rated,
                termination = game.termination?.let { enumValueOrMappingError<PersistedTermination>(it, "termination") },
                timeControl = if (game.timeControlBaseMillis != null || game.timeControlIncrementMillis != null || game.timeControlRaw != null) {
                    TimeControlMetadata(game.timeControlBaseMillis, game.timeControlIncrementMillis, game.timeControlRaw)
                } else null,
            ),
            whiteParticipant = game.whiteParticipantId?.let { loadParticipant(it) },
            blackParticipant = game.blackParticipantId?.let { loadParticipant(it) },
            sources = sources,
        )
    }

    suspend fun deleteGame(id: PersistentGameId): Boolean = database.withWriteTransaction {
        database.gameDao().deleteGame(id.value) > 0
    }

    suspend fun listGames(): List<GameListEntry> = database.gameDao().listGames().map { row ->
        GameListEntry(
            id = PersistentGameId(row.id),
            variant = enumValueOrMappingError(row.variant, "variant"),
            result = row.result,
            createdAtEpochMillis = row.createdAtEpochMillis,
            playedAtEpochMillis = row.playedAtEpochMillis,
            rated = row.rated,
            whiteName = row.whiteName,
            blackName = row.blackName,
        )
    }

    suspend fun saveSavedPosition(draft: SavedPositionDraft): PersistentSavedPositionId = database.withWriteTransaction {
        val parsed = try { Fen.parse(draft.fen, draft.variant) } catch (error: IllegalArgumentException) {
            throw PersistenceMappingException("Cannot persist invalid saved-position FEN", error)
        }
        val id = PersistentSavedPositionId(newId())
        database.savedPositionDao().insert(
            SavedPositionEntity(
                id.value,
                draft.variant.name,
                Fen.serialize(parsed),
                draft.title,
                draft.notes,
                draft.createdAtEpochMillis,
                draft.updatedAtEpochMillis,
            ),
        )
        id
    }

    suspend fun loadSavedPosition(id: PersistentSavedPositionId): SavedPositionRecord? =
        database.savedPositionDao().byId(id.value)?.let { entity ->
            val variant = enumValueOrMappingError<Variant>(entity.variant, "saved-position variant")
            try { Fen.parse(entity.fen, variant) } catch (error: IllegalArgumentException) {
                throw PersistenceMappingException("Persisted saved position ${entity.id} has invalid FEN", error)
            }
            SavedPositionRecord(id, variant, entity.fen, entity.title, entity.notes, entity.createdAtEpochMillis, entity.updatedAtEpochMillis)
        }

    private suspend fun insertParticipant(draft: ParticipantDraft): PersistentParticipantId {
        val id = PersistentParticipantId(newId())
        database.participantDao().insert(
            ParticipantEntity(id.value, draft.kind.name, draft.displayName, draft.engineName, draft.engineVersion, System.currentTimeMillis()),
        )
        return id
    }

    private suspend fun loadParticipant(id: String): ParticipantRecord {
        val entity = database.participantDao().byId(id)
            ?: throw PersistenceMappingException("Game references missing participant $id")
        return ParticipantRecord(
            PersistentParticipantId(entity.id),
            enumValueOrMappingError(entity.kind, "participant kind"),
            entity.displayName,
            entity.engineName,
            entity.engineVersion,
        )
    }

    private fun decodeMove(row: GameNodeEntity): Move = try {
        Move(Square.fromIndex(row.fromSquare), Square.fromIndex(row.toSquare), decodePromotion(row.promotionCode))
    } catch (error: IllegalArgumentException) {
        throw PersistenceMappingException("Invalid move encoding at persistent node ${row.id}", error)
    }

    private fun orderedComments(rows: List<GameNodeCommentEntity>, kind: String, nodeId: String): List<String> {
        val matching = rows.filter { it.kind == kind }.sortedBy { it.orderIndex }
        if (rows.any { it.kind != COMMENT_LEADING && it.kind != COMMENT_TRAILING }) {
            throw PersistenceMappingException("Invalid comment kind on node $nodeId")
        }
        if (matching.map { it.orderIndex } != matching.indices.toList()) {
            throw PersistenceMappingException("Non-contiguous $kind comment order on node $nodeId")
        }
        return matching.map { it.text }
    }

    private inline fun <reified T : Enum<T>> enumValueOrMappingError(value: String, label: String): T =
        try { enumValueOf<T>(value) } catch (error: IllegalArgumentException) {
            throw PersistenceMappingException("Unknown persisted $label '$value'", error)
        }

    private fun encodePromotion(type: PieceType?): Int? = when (type) {
        null -> null
        PieceType.QUEEN -> 1
        PieceType.ROOK -> 2
        PieceType.BISHOP -> 3
        PieceType.KNIGHT -> 4
        PieceType.PAWN, PieceType.KING -> throw PersistenceMappingException("Invalid promotion piece $type")
    }

    private fun decodePromotion(code: Int?): PieceType? = when (code) {
        null -> null
        1 -> PieceType.QUEEN
        2 -> PieceType.ROOK
        3 -> PieceType.BISHOP
        4 -> PieceType.KNIGHT
        else -> throw PersistenceMappingException("Unknown promotion code $code")
    }

    private companion object {
        const val COMMENT_ROOT = "ROOT"
        const val COMMENT_LEADING = "LEADING"
        const val COMMENT_TRAILING = "TRAILING"
    }
}

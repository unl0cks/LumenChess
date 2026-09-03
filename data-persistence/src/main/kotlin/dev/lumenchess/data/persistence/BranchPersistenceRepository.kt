package dev.lumenchess.data.persistence

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameNode
import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.PieceType
import java.util.UUID

/** Persistent UUID anchor; a null node ID denotes the canonical game's root position. */
data class BranchOrigin(val gameId: PersistentGameId, val nodeId: String?, val fen: String)

/**
 * Explicit, source-neutral attachment of a sandbox's mainline to its canonical origin.
 * Existing rows are never updated or replaced, including the source's mainline fingerprint.
 * A separate sandbox game remains independent until the caller invokes [saveAsVariation].
 */
class BranchPersistenceRepository(
    private val database: LumenDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val canonical = GamePersistenceRepository(database, newId)

    suspend fun captureOrigin(gameId: PersistentGameId, mainlinePly: Int): BranchOrigin =
        database.withReadTransaction {
            val source = loadSource(gameId)
            val mainline = source.tree.mainline()
            require(mainlinePly in 0..mainline.size) { "Origin ply is outside the canonical mainline" }
            val node = if (mainlinePly == 0) source.tree.root else mainline[mainlinePly - 1]
            BranchOrigin(gameId, source.persistentIds[node.id], Fen.serialize(node.position))
        }

    /** Returns the number of appended move rows; a repeated or empty branch returns zero. */
    suspend fun saveAsVariation(origin: BranchOrigin, branch: GameTree): Int = database.withWriteTransaction {
        val source = loadSource(origin.gameId)
        val anchorId = if (origin.nodeId == null) {
            source.tree.rootId
        } else {
            source.domainIds[origin.nodeId]
                ?: throw PersistenceConflictException("Origin node does not belong to canonical game ${origin.gameId.value}")
        }
        if (
            source.tree.startPosition.variant != branch.startPosition.variant ||
            Fen.serialize(source.tree.node(anchorId).position) != origin.fen ||
            Fen.serialize(branch.startPosition) != origin.fen
        ) {
            throw PersistenceConflictException("Branch origin FEN or variant no longer matches the canonical position")
        }
        val incoming = branch.mainline()
        if (incoming.isEmpty()) return@withWriteTransaction 0
        val historicalMainline = source.tree.mainline()
        if (historicalMainline.isEmpty()) {
            throw PersistenceConflictException("An empty canonical game has no historical move to hold a PGN variation")
        }

        var tree = source.tree
        val historicalIds = historicalMainline.mapTo(HashSet()) { it.id }
        val historicalLeaf = historicalMainline.last().id
        val persistentIds = source.persistentIds.toMutableMap()
        val rows = mutableListOf<GameNodeEntity>()
        val comments = mutableListOf<GameNodeCommentEntity>()
        val nags = mutableListOf<GameNodeNagEntity>()
        val annotations = mutableListOf<GameNodeAnnotationEntity>()

        fun append(parent: GameNodeId, node: GameNode): GameNodeId {
            val move = requireNotNull(node.move)
            val addition = tree.addMove(parent, move, node.leadingComments, node.comments, node.nags, node.annotations)
            val persistentId = newId()
            rows += GameNodeEntity(
                id = persistentId,
                gameId = origin.gameId.value,
                parentNodeId = persistentIds[parent],
                siblingOrder = tree.childrenOf(parent).size,
                fromSquare = move.from.index,
                toSquare = move.to.index,
                promotionCode = encodePromotion(move.promotion),
                san = requireNotNull(addition.tree.node(addition.nodeId).san),
            )
            fun addComments(kind: String, values: List<String>) {
                values.forEachIndexed { index, text ->
                    comments += GameNodeCommentEntity(newId(), origin.gameId.value, persistentId, kind, index, text)
                }
            }
            addComments("LEADING", node.leadingComments)
            addComments("TRAILING", node.comments)
            node.nags.forEachIndexed { index, nag ->
                nags += GameNodeNagEntity(origin.gameId.value, persistentId, index, nag.value)
            }
            node.annotations.toSortedMap().forEach { (key, value) ->
                annotations += GameNodeAnnotationEntity(origin.gameId.value, persistentId, key, value)
            }
            tree = addition.tree
            persistentIds[addition.nodeId] = persistentId
            return addition.nodeId
        }

        // Search all matching sibling paths, not just the first equal move. Repeated saves must
        // find a previously attached RAV even when the historical move has the same encoding.
        fun matchingLength(node: GameNode, index: Int): Int {
            if (index >= incoming.size || node.move != incoming[index].move) return 0
            return 1 + (tree.childrenOf(node.id).maxOfOrNull { matchingLength(it, index + 1) } ?: 0)
        }

        fun bestContinuation(children: List<GameNode>, index: Int): GameNode? = children
            .filter { it.move == incoming[index].move }
            .maxWithOrNull(
                compareBy<GameNode> { matchingLength(it, index) }
                    .thenBy { if (it.id in historicalIds) 0 else 1 },
            )

        var parent = anchorId
        incoming.forEachIndexed { index, node ->
            if (parent == historicalLeaf) {
                // Adding beneath the source leaf would silently lengthen historical mainline.
                // PGN instead expresses this as 1.e4 (1.e4 c5), with the incoming leaf move
                // repeated under its original parent. Its existing annotations stay untouched.
                val leaf = tree.node(parent)
                val leafParent = requireNotNull(leaf.parentId)
                val existing = tree.childrenOf(leafParent)
                    .filter { it.id != historicalLeaf && it.move == leaf.move }
                    .maxByOrNull { sibling ->
                        tree.childrenOf(sibling.id).maxOfOrNull { matchingLength(it, index) } ?: 0
                    }
                parent = existing?.id ?: append(
                    leafParent,
                    leaf.copy(leadingComments = emptyList(), comments = emptyList(), nags = emptyList(), annotations = emptyMap()),
                )
            }
            parent = bestContinuation(tree.childrenOf(parent), index)?.id ?: append(parent, node)
        }

        if (rows.isNotEmpty()) database.gameDao().insertNodes(rows)
        if (comments.isNotEmpty()) database.gameDao().insertComments(comments)
        if (nags.isNotEmpty()) database.gameDao().insertNags(nags)
        if (annotations.isNotEmpty()) database.gameDao().insertAnnotations(annotations)
        rows.size
    }

    /** Reuses canonical core-backed validation, then associates UUIDs by validated sibling order. */
    private suspend fun loadSource(gameId: PersistentGameId): SourceTree {
        val tree = canonical.loadGameInternal(gameId)?.tree
            ?: throw PersistenceConflictException("Cannot branch from missing canonical game ${gameId.value}")
        val children = database.gameDao().nodesForGame(gameId.value)
            .groupBy { it.parentNodeId }
            .mapValues { (_, rows) -> rows.sortedBy { it.siblingOrder } }
        val persistentIds = mutableMapOf<GameNodeId, String>()
        val domainIds = mutableMapOf<String, GameNodeId>()
        fun associate(parentPersistentId: String?, parentDomainId: GameNodeId) {
            children[parentPersistentId].orEmpty().zip(tree.childrenOf(parentDomainId)).forEach { (row, node) ->
                persistentIds[node.id] = row.id
                domainIds[row.id] = node.id
                associate(row.id, node.id)
            }
        }
        associate(null, tree.rootId)
        return SourceTree(tree, persistentIds, domainIds)
    }

    private fun encodePromotion(type: PieceType?): Int? = when (type) {
        null -> null
        PieceType.QUEEN -> 1
        PieceType.ROOK -> 2
        PieceType.BISHOP -> 3
        PieceType.KNIGHT -> 4
        PieceType.PAWN, PieceType.KING -> throw PersistenceMappingException("Invalid promotion piece $type")
    }

    private data class SourceTree(
        val tree: GameTree,
        val persistentIds: Map<GameNodeId, String>,
        val domainIds: Map<String, GameNodeId>,
    )
}

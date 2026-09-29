package dev.lumenchess.analysis.tree

import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move

/**
 * Variation editing for Analysis. [GameTree] is append-only, so edits rebuild the tree through its
 * own legality-checked [GameTree.addMove]; node ids are re-issued and returned as a mapping.
 */
object TreeEditing {
    data class Edit(val tree: GameTree, val ids: Map<GameNodeId, GameNodeId>) {
        fun map(id: GameNodeId): GameNodeId? = ids[id]
    }

    /** Plays [move] after [parent], reusing an existing child with the same move instead of duplicating it. */
    fun play(tree: GameTree, parent: GameNodeId, move: Move): Pair<GameTree, GameNodeId> {
        tree.childrenOf(parent).firstOrNull { it.move == move }?.let { return tree to it.id }
        val added = tree.addMove(parent, move)
        return added.tree to added.nodeId
    }

    /** Removes [nodeId] and everything after it. The root cannot be deleted. */
    fun delete(tree: GameTree, nodeId: GameNodeId): Edit {
        require(nodeId != tree.rootId) { "The start position cannot be deleted" }
        return rebuild(tree) { _, children -> children.filterNot { it == nodeId } }
    }

    /** Makes [nodeId] its parent's first child (the main continuation there). */
    fun promote(tree: GameTree, nodeId: GameNodeId): Edit {
        val parent = tree.node(nodeId).parentId ?: return Edit(tree, tree.nodes.keys.associateWith { it })
        return rebuild(tree) { id, children ->
            if (id == parent) listOf(nodeId) + children.filterNot { it == nodeId } else children
        }
    }

    /** Path of child indices from the root to [nodeId]. */
    fun pathTo(tree: GameTree, nodeId: GameNodeId): List<Int> {
        val path = ArrayList<Int>()
        var current = tree.node(nodeId)
        while (true) {
            val parentId = current.parentId ?: break
            path += tree.childrenOf(parentId).indexOfFirst { it.id == current.id }
            current = tree.node(parentId)
        }
        return path.asReversed()
    }

    /** Nodes from the root's first move down to [nodeId]. */
    fun lineTo(tree: GameTree, nodeId: GameNodeId): List<GameNodeId> {
        val out = ArrayList<GameNodeId>()
        var current: GameNodeId? = nodeId
        while (current != null && current != tree.rootId) {
            out += current
            current = tree.node(current).parentId
        }
        return out.asReversed()
    }

    private fun rebuild(tree: GameTree, order: (GameNodeId, List<GameNodeId>) -> List<GameNodeId>): Edit {
        var rebuilt = GameTree.create(tree.startPosition, tree.headers, tree.result, tree.rootComments)
        val ids = HashMap<GameNodeId, GameNodeId>()
        ids[tree.rootId] = rebuilt.rootId
        fun visit(oldParent: GameNodeId) {
            val children = order(oldParent, tree.childrenOf(oldParent).map { it.id })
            for (child in children) {
                val node = tree.node(child)
                val added = rebuilt.addMove(
                    ids.getValue(oldParent), requireNotNull(node.move),
                    node.leadingComments, node.comments, node.nags, node.annotations,
                )
                rebuilt = added.tree
                ids[child] = added.nodeId
                visit(child)
            }
        }
        visit(tree.rootId)
        return Edit(rebuilt, ids)
    }
}

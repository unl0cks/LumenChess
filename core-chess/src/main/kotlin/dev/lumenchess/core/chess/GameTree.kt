package dev.lumenchess.core.chess

@JvmInline
value class GameNodeId(val value: Long)

@JvmInline
value class Nag(val value: Int) {
    init {
        require(value in 0..255) { "PGN NAG must be in 0..255, got $value" }
    }
}

data class GameNode(
    val id: GameNodeId,
    val parentId: GameNodeId?,
    val move: Move?,
    val san: String?,
    val position: Position,
    val leadingComments: List<String> = emptyList(),
    val comments: List<String> = emptyList(),
    val nags: List<Nag> = emptyList(),
    val annotations: Map<String, String> = emptyMap(),
)

data class MoveAddition(val tree: GameTree, val nodeId: GameNodeId)

/**
 * Canonical immutable in-memory game tree.
 *
 * A node's [GameNode.position] is the exact position *after* its move; the root contains the start
 * position. Child order is semantic: child 0 is the mainline and later children are sibling
 * variations. All mutation-style operations return a new tree, so adding an analysis branch cannot
 * accidentally rewrite the original mainline.
 */
class GameTree private constructor(
    val startPosition: Position,
    headers: Map<String, String>,
    val result: GameResult?,
    rootComments: List<String>,
    nodes: Map<GameNodeId, GameNode>,
    children: Map<GameNodeId, List<GameNodeId>>,
    private val nextNodeValue: Long,
) {
    val rootId: GameNodeId = ROOT_ID
    val headers: Map<String, String> = headers.toMap()
    val rootComments: List<String> = rootComments.toList()
    val nodes: Map<GameNodeId, GameNode> = nodes.toMap()
    private val childIds: Map<GameNodeId, List<GameNodeId>> = children.mapValues { it.value.toList() }

    val root: GameNode get() = node(rootId)

    fun node(id: GameNodeId): GameNode = nodes[id]
        ?: throw IllegalArgumentException("Unknown game node ${id.value}")

    fun parentOf(id: GameNodeId): GameNode? = node(id).parentId?.let(::node)

    fun childrenOf(id: GameNodeId): List<GameNode> {
        node(id)
        return childIds[id].orEmpty().map(::node)
    }

    fun mainlineChildOf(id: GameNodeId): GameNode? = childrenOf(id).firstOrNull()

    fun mainline(): List<GameNode> {
        val result = ArrayList<GameNode>()
        var current = mainlineChildOf(rootId)
        while (current != null) {
            result += current
            current = mainlineChildOf(current.id)
        }
        return result
    }

    fun addMove(
        parentId: GameNodeId,
        move: Move,
        leadingComments: List<String> = emptyList(),
        comments: List<String> = emptyList(),
        nags: List<Nag> = emptyList(),
        annotations: Map<String, String> = emptyMap(),
    ): MoveAddition {
        val parent = node(parentId)
        val san = San.generate(parent.position, move)
        val position = MoveGenerator.applyLegalMove(parent.position, move)
        val id = GameNodeId(nextNodeValue)
        val newNode = GameNode(
            id = id,
            parentId = parentId,
            move = move,
            san = san,
            position = position,
            leadingComments = leadingComments.toList(),
            comments = comments.toList(),
            nags = nags.toList(),
            annotations = annotations.toMap(),
        )

        val newNodes = LinkedHashMap(nodes)
        newNodes[id] = newNode
        val newChildren = LinkedHashMap(childIds)
        newChildren[parentId] = childIds[parentId].orEmpty() + id
        newChildren.putIfAbsent(id, emptyList())

        return MoveAddition(
            GameTree(
                startPosition = startPosition,
                headers = headers,
                result = result,
                rootComments = rootComments,
                nodes = newNodes,
                children = newChildren,
                nextNodeValue = nextNodeValue + 1,
            ),
            id,
        )
    }

    fun withNodeMetadata(
        id: GameNodeId,
        leadingComments: List<String>? = null,
        comments: List<String>? = null,
        nags: List<Nag>? = null,
        annotations: Map<String, String>? = null,
    ): GameTree {
        val current = node(id)
        val replacement = current.copy(
            leadingComments = leadingComments?.toList() ?: current.leadingComments,
            comments = comments?.toList() ?: current.comments,
            nags = nags?.toList() ?: current.nags,
            annotations = annotations?.toMap() ?: current.annotations,
        )
        val newNodes = LinkedHashMap(nodes)
        newNodes[id] = replacement
        return copy(nodes = newNodes)
    }

    fun withHeaders(headers: Map<String, String>): GameTree = copy(headers = headers)

    fun withResult(result: GameResult?): GameTree = copy(result = result)

    fun withRootComments(comments: List<String>): GameTree = copy(rootComments = comments)

    private fun copy(
        headers: Map<String, String> = this.headers,
        result: GameResult? = this.result,
        rootComments: List<String> = this.rootComments,
        nodes: Map<GameNodeId, GameNode> = this.nodes,
    ): GameTree = GameTree(
        startPosition = startPosition,
        headers = headers,
        result = result,
        rootComments = rootComments,
        nodes = nodes,
        children = childIds,
        nextNodeValue = nextNodeValue,
    )

    companion object {
        private val ROOT_ID = GameNodeId(0)

        fun create(
            startPosition: Position = Position.initial(),
            headers: Map<String, String> = emptyMap(),
            result: GameResult? = null,
            rootComments: List<String> = emptyList(),
        ): GameTree {
            val root = GameNode(
                id = ROOT_ID,
                parentId = null,
                move = null,
                san = null,
                position = startPosition,
            )
            return GameTree(
                startPosition = startPosition,
                headers = headers,
                result = result,
                rootComments = rootComments,
                nodes = linkedMapOf(ROOT_ID to root),
                children = linkedMapOf(ROOT_ID to emptyList()),
                nextNodeValue = 1,
            )
        }
    }
}

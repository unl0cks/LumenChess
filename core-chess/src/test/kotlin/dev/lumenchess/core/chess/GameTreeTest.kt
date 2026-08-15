package dev.lumenchess.core.chess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GameTreeTest {
    @Test
    fun rootCarriesStartPositionHeadersAndResult() {
        val start = Position.initial()
        val tree = GameTree.create(
            startPosition = start,
            headers = linkedMapOf("Event" to "M7", "White" to "Alpha"),
            result = null,
            rootComments = listOf("root note"),
        )

        assertEquals(start, tree.root.position)
        assertEquals(null, tree.root.move)
        assertEquals(null, tree.root.san)
        assertEquals(mapOf("Event" to "M7", "White" to "Alpha"), tree.headers)
        assertEquals(listOf("root note"), tree.rootComments)
        assertEquals(null, tree.result)
    }

    @Test
    fun firstChildIsMainlineAndLaterChildrenAreOrderedVariations() {
        val tree0 = GameTree.create()
        val e4 = tree0.addMove(tree0.rootId, Move.parseUci("e2e4"))
        val d4 = e4.tree.addMove(e4.tree.rootId, Move.parseUci("d2d4"))
        val c4 = d4.tree.addMove(d4.tree.rootId, Move.parseUci("c2c4"))

        assertEquals(listOf(e4.nodeId, d4.nodeId, c4.nodeId), c4.tree.childrenOf(c4.tree.rootId).map { it.id })
        assertEquals(e4.nodeId, c4.tree.mainlineChildOf(c4.tree.rootId)?.id)
        assertEquals("e4", c4.tree.node(e4.nodeId).san)
        assertEquals("d4", c4.tree.node(d4.nodeId).san)
    }

    @Test
    fun branchesAndNestedBranchesUseTheExactParentPosition() {
        var tree = GameTree.create()
        val e4 = tree.addMove(tree.rootId, Move.parseUci("e2e4")); tree = e4.tree
        val e5 = tree.addMove(e4.nodeId, Move.parseUci("e7e5")); tree = e5.tree
        val nf3 = tree.addMove(e5.nodeId, Move.parseUci("g1f3")); tree = nf3.tree

        val c5 = tree.addMove(e4.nodeId, Move.parseUci("c7c5")); tree = c5.tree
        val nf6 = tree.addMove(c5.nodeId, Move.parseUci("g8f6")); tree = nf6.tree

        assertEquals(e4.nodeId, tree.parentOf(c5.nodeId)?.id)
        assertEquals(c5.nodeId, tree.parentOf(nf6.nodeId)?.id)
        assertEquals(MoveGenerator.applyLegalMove(tree.node(e4.nodeId).position, Move.parseUci("c7c5")), tree.node(c5.nodeId).position)
        assertNotEquals(tree.node(e5.nodeId).position, tree.node(c5.nodeId).position)
        assertEquals(listOf(e5.nodeId, c5.nodeId), tree.childrenOf(e4.nodeId).map { it.id })
    }

    @Test
    fun addingVariationReturnsNewTreeWithoutMutatingOriginalMainline() {
        val base = GameTree.create()
        val e4 = base.addMove(base.rootId, Move.parseUci("e2e4"))
        val afterE4 = e4.tree
        val d4Variation = afterE4.addMove(afterE4.rootId, Move.parseUci("d2d4"))

        assertEquals(listOf(e4.nodeId), afterE4.childrenOf(afterE4.rootId).map { it.id })
        assertEquals(2, d4Variation.tree.childrenOf(d4Variation.tree.rootId).size)
        assertFalse(afterE4.nodes.containsKey(d4Variation.nodeId))
        assertTrue(d4Variation.tree.nodes.containsKey(d4Variation.nodeId))
    }

    @Test
    fun commentsNagsAndAnnotationsAreStructuredAndImmutable() {
        val start = GameTree.create()
        val added = start.addMove(
            parentId = start.rootId,
            move = Move.parseUci("e2e4"),
            leadingComments = listOf("before"),
            comments = listOf("after"),
            nags = listOf(Nag(1), Nag(5)),
            annotations = mapOf("source" to "fixture"),
        )
        val node = added.tree.node(added.nodeId)

        assertEquals(listOf("before"), node.leadingComments)
        assertEquals(listOf("after"), node.comments)
        assertEquals(listOf(Nag(1), Nag(5)), node.nags)
        assertEquals(mapOf("source" to "fixture"), node.annotations)

        val updated = added.tree.withNodeMetadata(added.nodeId, comments = node.comments + "later", nags = node.nags + Nag(2))
        assertEquals(listOf("after"), added.tree.node(added.nodeId).comments)
        assertEquals(listOf("after", "later"), updated.node(added.nodeId).comments)
    }

    @Test
    fun illegalBranchMoveIsRejectedByCoreLegality() {
        val tree = GameTree.create()
        assertThrows(IllegalArgumentException::class.java) {
            tree.addMove(tree.rootId, Move.parseUci("e2e5"))
        }
    }

    @Test
    fun mainlineTraversalFollowsFirstChildOnly() {
        var tree = GameTree.create()
        val e4 = tree.addMove(tree.rootId, Move.parseUci("e2e4")); tree = e4.tree
        val e5 = tree.addMove(e4.nodeId, Move.parseUci("e7e5")); tree = e5.tree
        val c5 = tree.addMove(e4.nodeId, Move.parseUci("c7c5")); tree = c5.tree
        val nf3 = tree.addMove(e5.nodeId, Move.parseUci("g1f3")); tree = nf3.tree

        assertEquals(listOf(e4.nodeId, e5.nodeId, nf3.nodeId), tree.mainline().map { it.id })
        assertFalse(tree.mainline().any { it.id == c5.nodeId })
    }

    @Test
    fun nagRangeMatchesPgnNumericAnnotationGlyphRange() {
        assertEquals(0, Nag(0).value)
        assertEquals(255, Nag(255).value)
        assertThrows(IllegalArgumentException::class.java) { Nag(-1) }
        assertThrows(IllegalArgumentException::class.java) { Nag(256) }
    }
}

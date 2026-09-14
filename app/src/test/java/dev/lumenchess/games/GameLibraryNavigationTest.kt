package dev.lumenchess.games

import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.core.chess.San
import kotlin.test.Test
import kotlin.test.assertEquals

class GameLibraryNavigationTest {
    @Test fun childIndexPathSurvivesDifferentRuntimeNodeAllocationOrder() {
        val parsed = Pgn.parseGame("1. e4 e5 (1... c5) 2. Nf3 *")
        var reordered = GameTree.create()
        val e4 = reordered.addMove(reordered.rootId, San.parse(reordered.root.position, "e4"))
        reordered = e4.tree
        val e5 = reordered.addMove(e4.nodeId, San.parse(reordered.node(e4.nodeId).position, "e5"))
        reordered = e5.tree
        reordered = reordered.addMove(e5.nodeId, San.parse(reordered.node(e5.nodeId).position, "Nf3")).tree
        reordered = reordered.addMove(e4.nodeId, San.parse(reordered.node(e4.nodeId).position, "c5")).tree
        assertEquals("c5", libraryNodeAtPath(parsed, listOf(0, 1)).san)
        assertEquals("c5", libraryNodeAtPath(reordered, listOf(0, 1)).san)
        assertEquals(listOf(0, 0, 0), libraryMainlineEndPath(parsed))
    }

    @Test fun invalidRestoredPathFallsBackToRootWithoutGuessingANode() {
        val tree = Pgn.parseGame("1. e4 e5 *")
        assertEquals(tree.rootId, libraryNodeAtPath(tree, listOf(0, 3)).id)
        assertEquals(tree.rootId, libraryNodeAtPath(tree, listOf(-1)).id)
    }
}

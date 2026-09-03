package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Nag
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BranchPersistenceRepositoryTest {
    private lateinit var database: LumenDatabase
    private lateinit var canonical: GamePersistenceRepository
    private lateinit var branches: BranchPersistenceRepository

    @Before
    fun setUp() {
        database = LumenDatabaseFactory.inMemory(ApplicationProvider.getApplicationContext<Context>())
        canonical = GamePersistenceRepository(database)
        branches = BranchPersistenceRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun rootBranchAppendsAlternativeWithoutChangingHistoricalMainline() = runBlocking {
        val original = Pgn.parseGame("1. e4 e5 2. Nf3 1-0")
        val id = canonical.saveGame(PersistGameRequest(original))
        val before = database.gameDao().nodesForGame(id.value)
        val branch = Pgn.parseGame("1. d4 d5 *")

        assertEquals(2, branches.saveAsVariation(BranchOrigin(id, null, Fen.serialize(original.startPosition)), branch))

        val loaded = requireNotNull(canonical.loadGame(id))
        assertEquals(listOf("e4", "e5", "Nf3"), loaded.tree.mainline().map { it.san })
        assertEquals(listOf("e4", "d4"), loaded.tree.childrenOf(loaded.tree.rootId).map { it.san })
        assertEquals(original.result, loaded.tree.result)
        assertTrue(database.gameDao().nodesForGame(id.value).containsAll(before))
        assertEquals(Pgn.serialize(loaded.tree), Pgn.serialize(Pgn.parseGame(Pgn.serialize(loaded.tree))))
    }

    @Test
    fun captureUsesPersistentUuidAndExactFenAtRootEarlierAndLeafPositions() = runBlocking {
        val original = Pgn.parseGame("1. e4 e5 2. Nf3 *")
        val id = canonical.saveGame(PersistGameRequest(original))
        val rows = database.gameDao().nodesForGame(id.value)

        assertEquals(BranchOrigin(id, null, Fen.serialize(Position.initial())), branches.captureOrigin(id, 0))
        for (ply in 1..3) {
            val node = original.mainline()[ply - 1]
            val origin = branches.captureOrigin(id, ply)
            assertEquals(id, origin.gameId)
            assertEquals(rows.single { it.san == node.san }.id, origin.nodeId)
            assertNotEquals(node.id.value.toString(), origin.nodeId)
            assertEquals(Fen.serialize(node.position), origin.fen)
        }
        expectRejected { branches.captureOrigin(id, -1) }
        expectRejected { branches.captureOrigin(id, 4) }
        expectRejected { branches.captureOrigin(PersistentGameId("missing"), 0) }
    }

    @Test
    fun savingSeparateSandboxDoesNotChangeOriginalBeforeExplicitAttachment() = runBlocking {
        val id = saveRichOriginal()
        val before = snapshot(id)
        val origin = branches.captureOrigin(id, 1)
        val branch = line(origin, "c7c5", "g1f3")

        val sandboxId = canonical.saveGame(
            PersistGameRequest(branch, sources = listOf(GameSourceDraft(GameSourceType.BRANCH))),
        )

        assertNotEquals(id, sandboxId)
        assertEquals(before, snapshot(id))
        assertEquals(2, database.gameDao().countGames())
        assertEquals(1, branches.saveAsVariation(origin, branch))
        assertExistingRowsPreserved(before, snapshot(id))
        assertEquals(listOf("e4", "e5", "Nf3"), requireNotNull(canonical.loadGame(id)).tree.mainline().map { it.san })
    }

    @Test
    fun earlierAnchorReusesVariationAndOnlyAppendsMissingContinuationOnExtendedSave() = runBlocking {
        val id = saveRichOriginal()
        val origin = branches.captureOrigin(id, 1)
        val initial = line(origin, "c7c5", "g1f3")
        val before = snapshot(id)

        assertEquals(1, branches.saveAsVariation(origin, initial))
        val first = snapshot(id)
        assertEquals(0, branches.saveAsVariation(origin, initial))
        assertEquals(first, snapshot(id))
        assertEquals(1, branches.saveAsVariation(origin, line(origin, "c7c5", "g1f3", "d7d6")))
        assertExistingRowsPreserved(before, snapshot(id))
        val tree = requireNotNull(canonical.loadGame(id)).tree
        val siblings = tree.childrenOf(tree.mainline().first().id)
        assertEquals(listOf("e5", "c5"), siblings.map { it.san })
        assertEquals(listOf("Nf3"), tree.childrenOf(siblings[1].id).map { it.san })
    }

    @Test
    fun leafContinuationDuplicatesIncomingMoveAsSiblingRavWithoutExtendingHistory() = runBlocking {
        val original = Pgn.parseGame("1. e4 {historical leaf} 1-0")
        val id = canonical.saveGame(PersistGameRequest(original))
        val before = snapshot(id)
        val origin = branches.captureOrigin(id, 1)
        val branch = line(origin, "c7c5")

        assertEquals(2, branches.saveAsVariation(origin, branch))
        val first = snapshot(id)
        assertEquals(0, branches.saveAsVariation(origin, branch))
        assertEquals(first, snapshot(id))
        assertEquals(1, branches.saveAsVariation(origin, line(origin, "c7c5", "g1f3")))
        assertExistingRowsPreserved(before, snapshot(id))
        val tree = requireNotNull(canonical.loadGame(id)).tree
        assertEquals(listOf("e4"), tree.mainline().map { it.san })
        assertTrue(tree.childrenOf(tree.mainline().single().id).isEmpty())
        val roots = tree.childrenOf(tree.rootId)
        assertEquals(listOf("e4", "e4"), roots.map { it.san })
        assertEquals(listOf("c5"), tree.childrenOf(roots[1].id).map { it.san })
        assertEquals(Pgn.serialize(tree), Pgn.serialize(Pgn.parseGame(Pgn.serialize(tree))))
    }

    @Test
    fun earlierBranchFollowingHistoryReusesMatchingLeafSiblingOnRepeatedAndExtendedSaves() = runBlocking {
        val id = canonical.saveGame(PersistGameRequest(Pgn.parseGame("1. e4 e5 2. Nf3 *")))
        val origin = branches.captureOrigin(id, 0)
        val branch = line(origin, "e2e4", "e7e5", "g1f3", "b8c6")

        assertEquals(2, branches.saveAsVariation(origin, branch))
        assertEquals(0, branches.saveAsVariation(origin, branch))
        assertEquals(1, branches.saveAsVariation(origin, line(origin, "e2e4", "e7e5", "g1f3", "b8c6", "f1b5")))

        val tree = requireNotNull(canonical.loadGame(id)).tree
        assertEquals(listOf("e4", "e5", "Nf3"), tree.mainline().map { it.san })
        assertEquals(listOf("Nf3", "Nf3"), tree.childrenOf(tree.mainline()[1].id).map { it.san })
        assertEquals(6, database.gameDao().countNodes(id.value))
    }

    @Test
    fun newBranchNodeMetadataIsStoredWithoutOverwritingExistingMetadata() = runBlocking {
        val id = saveRichOriginal()
        val origin = branches.captureOrigin(id, 0)
        var branch = line(origin, "d2d4", "d7d5")
        branch = branch.withNodeMetadata(
            branch.mainline().first().id,
            leadingComments = listOf("branch introduction"),
            comments = listOf("new analysis"),
            nags = listOf(Nag(5)),
            annotations = mapOf("branch.annotation" to "value"),
        )
        val before = snapshot(id)

        assertEquals(2, branches.saveAsVariation(origin, branch))
        assertExistingRowsPreserved(before, snapshot(id))

        val tree = requireNotNull(canonical.loadGame(id)).tree
        val added = tree.childrenOf(tree.rootId).single { it.san == "d4" }
        assertEquals(listOf("branch introduction"), added.leadingComments)
        assertEquals(listOf("new analysis"), added.comments)
        assertEquals(listOf(Nag(5)), added.nags)
        assertEquals(mapOf("branch.annotation" to "value"), added.annotations)
    }

    @Test
    fun specialMoveVariationsPreserveExactEncodingAndPositionAcrossStandardAndChess960() = runBlocking {
        val cases = listOf(
            SpecialMove("4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1", Variant.STANDARD, "a1a2", "e1g1"),
            SpecialMove("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", Variant.STANDARD, "e1f1", "e5d6"),
            SpecialMove("k7/4P3/8/8/8/8/8/7K w - - 0 1", Variant.STANDARD, "h1g1", "e7e8n", 4),
            SpecialMove("7k/8/8/8/8/8/8/RK2R3 w EA - 0 1", Variant.CHESS960, "a1a2", "b1e1"),
            SpecialMove("7k/8/8/8/8/8/8/RK2R3 w EA - 0 1", Variant.CHESS960, "a1a2", "b1a1"),
            SpecialMove("7k/8/8/8/8/8/8/6KR w H - 0 1", Variant.CHESS960, "h1h2", "g1h1"),
            SpecialMove("7k/8/8/8/8/8/8/4KR2 w F - 0 1", Variant.CHESS960, "f1f2", "e1f1"),
        )
        for (case in cases) {
            val root = GameTree.create(Fen.parse(case.fen, case.variant))
            val original = root.addMove(root.rootId, Move.parseUci(case.originalMove)).tree
            val id = canonical.saveGame(PersistGameRequest(original))
            val origin = branches.captureOrigin(id, 0)
            val branch = line(origin, case.branchMove, variant = case.variant)

            assertEquals(case.branchMove, 1, branches.saveAsVariation(origin, branch))

            val loaded = requireNotNull(canonical.loadGame(id)).tree
            val variation = loaded.childrenOf(loaded.rootId)[1]
            assertEquals(case.branchMove, variation.move?.uci)
            assertEquals(branch.mainline().single().position, variation.position)
            assertEquals(case.variant, loaded.startPosition.variant)
            val row = database.gameDao().nodesForGame(id.value).single { it.siblingOrder == 1 }
            assertEquals(case.promotionCode, row.promotionCode)
            assertEquals(Pgn.serialize(loaded), Pgn.serialize(Pgn.parseGame(Pgn.serialize(loaded))))
        }
    }

    @Test
    fun invalidMissingCrossGameAnchorsAndFenOrVariantMismatchesFailWithoutWrites() = runBlocking {
        val id = saveRichOriginal()
        val other = canonical.saveGame(PersistGameRequest(Pgn.parseGame("1. d4 *")))
        val origin = branches.captureOrigin(id, 1)
        val branch = line(origin, "c7c5")
        val before = snapshot(id)
        val invalidOrigins = listOf(
            origin.copy(gameId = PersistentGameId("missing")),
            origin.copy(nodeId = "missing"),
            origin.copy(nodeId = branches.captureOrigin(other, 1).nodeId),
            origin.copy(nodeId = branches.captureOrigin(id, 2).nodeId),
            origin.copy(fen = origin.fen.replace("0 1", "1 1")),
            origin.copy(nodeId = null),
        )
        for (invalid in invalidOrigins) {
            expectRejected { branches.saveAsVariation(invalid, branch) }
            assertEquals(before, snapshot(id))
        }
        expectRejected { branches.saveAsVariation(origin, Pgn.parseGame("1. d4 *")) }
        val wrongVariant = GameTree.create(Fen.parse(origin.fen, Variant.CHESS960))
            .let { it.addMove(it.rootId, Move.parseUci("c7c5")).tree }
        expectRejected { branches.saveAsVariation(origin, wrongVariant) }
        assertEquals(before, snapshot(id))
    }

    @Test
    fun failedAppendRollsBackEveryNewNodeAndPreservesOriginalRows() = runBlocking {
        val id = saveRichOriginal()
        val origin = branches.captureOrigin(id, 0)
        val before = snapshot(id)
        val duplicateId = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
        val faulty = BranchPersistenceRepository(database, newId = { duplicateId })

        expectRejected { faulty.saveAsVariation(origin, line(origin, "d2d4", "d7d5")) }

        assertEquals(before, snapshot(id))
    }

    @Test
    fun emptyBranchIsNoOpAndEmptyOriginalCannotExpressVariation() = runBlocking {
        val id = saveRichOriginal()
        val origin = branches.captureOrigin(id, 1)
        val before = snapshot(id)
        assertEquals(0, branches.saveAsVariation(origin, GameTree.create(Fen.parse(origin.fen))))
        assertEquals(before, snapshot(id))
        val emptyId = canonical.saveGame(PersistGameRequest(GameTree.create()))
        val emptyOrigin = branches.captureOrigin(emptyId, 0)
        assertNull(emptyOrigin.nodeId)
        expectRejected { branches.saveAsVariation(emptyOrigin, Pgn.parseGame("1. d4 *")) }
        assertEquals(0, database.gameDao().countNodes(emptyId.value))
    }

    private suspend fun saveRichOriginal(): PersistentGameId {
        var tree = Pgn.parseGame("[Event \"Untouched\"]\n\n{root note} 1. e4 {main note} e5 (1... c5 {old variation}) 2. Nf3 1-0")
        tree = tree.withNodeMetadata(tree.mainline().first().id, nags = listOf(Nag(1)), annotations = mapOf("analysis" to "keep"))
        return canonical.saveGame(
            PersistGameRequest(
                tree,
                metadata = GamePersistenceMetadata(100, 110, 120, true, PersistedTermination.RESIGNATION, TimeControlMetadata(60_000, 1_000, "60+1")),
                whiteParticipant = ParticipantDraft(ParticipantKind.ENGINE, "White engine", "White", "1"),
                blackParticipant = ParticipantDraft(ParticipantKind.HUMAN_LOCAL, "Black player"),
                sources = listOf(GameSourceDraft(GameSourceType.ENGINE_ARENA, metadata = mapOf("keep" to "source"))),
            ),
        )
    }

    private fun line(origin: BranchOrigin, vararg moves: String, variant: Variant = Variant.STANDARD): GameTree {
        var tree = GameTree.create(Fen.parse(origin.fen, variant))
        var cursor = tree.rootId
        for (move in moves) {
            val added = tree.addMove(cursor, Move.parseUci(move))
            tree = added.tree
            cursor = added.nodeId
        }
        return tree
    }

    private suspend fun expectRejected(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected persistence to reject invalid branch without writes")
        } catch (error: Exception) {
            // Domain conflicts, invalid arguments, and Room constraint failures are all atomic failures.
        }
    }

    private suspend fun snapshot(id: PersistentGameId): Snapshot {
        val loaded = requireNotNull(canonical.loadGame(id))
        val sources = database.sourceDao().forGame(id.value)
        return Snapshot(
            requireNotNull(database.gameDao().gameById(id.value)),
            database.gameDao().headersForGame(id.value),
            database.gameDao().nodesForGame(id.value),
            database.gameDao().commentsForGame(id.value),
            database.gameDao().nagsForGame(id.value),
            database.gameDao().annotationsForGame(id.value),
            sources,
            database.sourceDao().metadataForSources(sources.map { it.id }),
            loaded.whiteParticipant,
            loaded.blackParticipant,
        )
    }

    private fun assertExistingRowsPreserved(before: Snapshot, after: Snapshot) {
        assertEquals(before.game, after.game)
        assertEquals(before.headers, after.headers)
        assertTrue(after.nodes.containsAll(before.nodes))
        assertTrue(after.comments.containsAll(before.comments))
        assertTrue(after.nags.containsAll(before.nags))
        assertTrue(after.annotations.containsAll(before.annotations))
        assertEquals(before.sources, after.sources)
        assertEquals(before.sourceMetadata, after.sourceMetadata)
        assertEquals(before.white, after.white)
        assertEquals(before.black, after.black)
    }

    private data class Snapshot(
        val game: GameEntity,
        val headers: List<GameHeaderEntity>,
        val nodes: List<GameNodeEntity>,
        val comments: List<GameNodeCommentEntity>,
        val nags: List<GameNodeNagEntity>,
        val annotations: List<GameNodeAnnotationEntity>,
        val sources: List<GameSourceEntity>,
        val sourceMetadata: List<GameSourceMetadataEntity>,
        val white: ParticipantRecord?,
        val black: ParticipantRecord?,
    )

    private data class SpecialMove(
        val fen: String,
        val variant: Variant,
        val originalMove: String,
        val branchMove: String,
        val promotionCode: Int? = null,
    )
}

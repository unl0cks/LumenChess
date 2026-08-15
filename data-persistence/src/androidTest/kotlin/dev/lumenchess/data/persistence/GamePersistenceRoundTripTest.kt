package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.GameNodeId
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Nag
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.core.chess.Variant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GamePersistenceRoundTripTest {
    private lateinit var database: LumenDatabase
    private lateinit var repository: GamePersistenceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = LumenDatabaseFactory.inMemory(context)
        repository = GamePersistenceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun standardInitialGameRoundTripsWithMetadataParticipantsSourcesAndUnknownHeaders() = runBlocking {
        val tree = Pgn.parseGame(
            """
            [Event "Persistence"]
            [White "Alpha"]
            [Black "Beta"]
            [X-Lumen-Custom "keep-me"]
            [Result "1-0"]

            1. f3 e5 2. g4 Qh4# 1-0
            """.trimIndent(),
        )
        val id = repository.saveGame(
            PersistGameRequest(
                tree = tree,
                metadata = GamePersistenceMetadata(
                    createdAtEpochMillis = 100L,
                    importedAtEpochMillis = 200L,
                    playedAtEpochMillis = 50L,
                    rated = true,
                    termination = PersistedTermination.CHECKMATE,
                    timeControl = TimeControlMetadata(baseMillis = 600_000L, incrementMillis = 0L, raw = "600"),
                ),
                whiteParticipant = ParticipantDraft(ParticipantKind.HUMAN, "Alpha"),
                blackParticipant = ParticipantDraft(ParticipantKind.ENGINE, "Beta", engineName = "FixtureEngine", engineVersion = "1.0"),
                sources = listOf(
                    GameSourceDraft(
                        type = GameSourceType.PGN_IMPORT,
                        externalGameId = "fixture-1",
                        externalUrl = "https://example.invalid/game/fixture-1",
                        importedAtEpochMillis = 200L,
                        metadata = linkedMapOf("format" to "pgn", "source-note" to "fixture"),
                    ),
                ),
            ),
        )

        val loaded = repository.loadGame(id)
        assertNotNull(loaded)
        loaded!!
        assertEquals(id, loaded.id)
        assertEquals(true, loaded.metadata.rated)
        assertEquals(PersistedTermination.CHECKMATE, loaded.metadata.termination)
        assertEquals("Alpha", loaded.whiteParticipant?.displayName)
        assertEquals(ParticipantKind.ENGINE, loaded.blackParticipant?.kind)
        assertEquals("FixtureEngine", loaded.blackParticipant?.engineName)
        assertEquals("fixture-1", loaded.sources.single().externalGameId)
        assertEquals("fixture", loaded.sources.single().metadata["source-note"])
        assertEquals("keep-me", loaded.tree.headers["X-Lumen-Custom"])
        assertSemanticTreeEquals(tree, loaded.tree)
    }

    @Test
    fun chess960CustomFenAndCastlingRoundTripSemantically() = runBlocking {
        val tree = Pgn.parseGame(
            """
            [Variant "Chess960"]
            [SetUp "1"]
            [FEN "7k/8/8/8/8/8/8/RK2R3 w EA - 0 1"]
            [Result "*"]

            1. O-O *
            """.trimIndent(),
        )

        val id = repository.saveGame(PersistGameRequest(tree = tree))
        val loaded = requireNotNull(repository.loadGame(id))

        assertEquals(Variant.CHESS960, loaded.tree.startPosition.variant)
        assertSemanticTreeEquals(tree, loaded.tree)
    }

    @Test
    fun nestedVariationsCommentsNagsAndAnnotationsRoundTripInDeterministicOrder() = runBlocking {
        var tree = Pgn.parseGame(
            """
            [Result "*"]

            1. e4 e5
            (1... c5 {Sicilian} $1 2. Nf3 (2. d4 {center} $5) Nc6)
            (1... e6?!)
            2. Nf3 *
            """.trimIndent(),
        )
        val c5 = tree.childrenOf(tree.mainline().first().id)[1]
        tree = tree.withNodeMetadata(
            c5.id,
            annotations = linkedMapOf("source" to "fixture", "eval" to "+0.30"),
        )

        val id = repository.saveGame(PersistGameRequest(tree = tree))
        val loaded = requireNotNull(repository.loadGame(id)).tree

        assertEquals(listOf("e5", "c5", "e6"), loaded.childrenOf(loaded.mainline().first().id).map { it.san })
        val loadedC5 = loaded.childrenOf(loaded.mainline().first().id)[1]
        assertEquals(listOf(Nag(1)), loadedC5.nags)
        assertEquals(listOf("Sicilian"), loadedC5.comments)
        assertEquals(mapOf("source" to "fixture", "eval" to "+0.30"), loadedC5.annotations)
        assertSemanticTreeEquals(tree, loaded)
    }

    @Test
    fun persistentIdsAreStableDistinctAndIndependentFromDomainGameNodeIds() = runBlocking {
        val first = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")))
        val second = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. d4 *")))

        assertNotEquals(first, second)
        val firstNodeIdsBefore = database.gameDao().nodeIdsForGame(first.value)
        val firstReloaded = requireNotNull(repository.loadGame(first))
        val firstNodeIdsAfter = database.gameDao().nodeIdsForGame(first.value)

        assertEquals(firstNodeIdsBefore, firstNodeIdsAfter)
        assertTrue(firstNodeIdsBefore.all { it.isNotBlank() })
        assertTrue(firstNodeIdsBefore.none { it == firstReloaded.tree.mainline().first().id.value.toString() })
    }

    @Test
    fun deletingGameCascadesOwnedRowsButLeavesParticipants() = runBlocking {
        val id = repository.saveGame(
            PersistGameRequest(
                tree = Pgn.parseGame("[Result \"*\"]\n\n1. e4 *"),
                whiteParticipant = ParticipantDraft(ParticipantKind.HUMAN_LOCAL, "Local"),
                blackParticipant = ParticipantDraft(ParticipantKind.ENGINE, "Stockfish", engineName = "Stockfish", engineVersion = "18"),
                sources = listOf(GameSourceDraft(GameSourceType.LOCAL)),
            ),
        )
        val participantCount = database.participantDao().countAll()
        assertTrue(database.gameDao().countNodes(id.value) > 0)
        assertTrue(database.sourceDao().countForGame(id.value) > 0)

        repository.deleteGame(id)

        assertNull(repository.loadGame(id))
        assertEquals(0, database.gameDao().countNodes(id.value))
        assertEquals(0, database.sourceDao().countForGame(id.value))
        assertEquals(participantCount, database.participantDao().countAll())
    }

    private fun assertSemanticTreeEquals(expected: GameTree, actual: GameTree) {
        assertEquals(expected.startPosition, actual.startPosition)
        assertEquals(expected.result, actual.result)
        assertEquals(expected.headers, actual.headers)
        assertEquals(expected.rootComments, actual.rootComments)
        compareChildren(expected, expected.rootId, actual, actual.rootId)
    }

    private fun compareChildren(
        expected: GameTree,
        expectedParent: GameNodeId,
        actual: GameTree,
        actualParent: GameNodeId,
    ) {
        val expectedChildren = expected.childrenOf(expectedParent)
        val actualChildren = actual.childrenOf(actualParent)
        assertEquals(expectedChildren.size, actualChildren.size)
        expectedChildren.zip(actualChildren).forEach { (left, right) ->
            assertEquals(left.move, right.move)
            assertEquals(left.san, right.san)
            assertEquals(left.position, right.position)
            assertEquals(left.leadingComments, right.leadingComments)
            assertEquals(left.comments, right.comments)
            assertEquals(left.nags, right.nags)
            assertEquals(left.annotations, right.annotations)
            compareChildren(expected, left.id, actual, right.id)
        }
    }
}

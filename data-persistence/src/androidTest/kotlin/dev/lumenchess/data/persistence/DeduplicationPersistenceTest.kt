package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Pgn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeduplicationPersistenceTest {
    private lateinit var database: LumenDatabase
    private lateinit var repository: GamePersistenceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = LumenDatabaseFactory.inMemory(context)
        repository = GamePersistenceRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun repeatedStrongSourceIdentityIsIdempotentAndPreservesCanonicalUuidAndUserData() = runBlocking {
        val tree = Pgn.parseGame(
            """
            [Event "Local rich metadata"]
            [X-Local "keep-header"]
            [Result "*"]

            1. e4 {keep comment} e5 (1... c5 $1) *
            """.trimIndent(),
        )
        val remoteRefreshTree = Pgn.parseGame(
            "[Event \"Remote sparse metadata\"]\n[Result \"*\"]\n\n1. e4 e5 *",
        )
        val source = GameSourceDraft(
            type = GameSourceType.CHESS_COM,
            externalGameId = "game-42",
            sourceAccountId = "account-a",
            importedAtEpochMillis = 100,
            metadata = linkedMapOf("remote" to "first", "preserve" to "yes"),
        )

        val first = repository.persistExternalGame(PersistGameRequest(tree), source)
        val second = repository.persistExternalGame(
            PersistGameRequest(remoteRefreshTree),
            source.copy(lastSyncedAtEpochMillis = 200, metadata = linkedMapOf("remote" to "updated")),
        )

        assertEquals(first, second)
        assertEquals(1, database.gameDao().countGames())
        assertEquals(1, database.sourceDao().countForGame(first.value))
        val loaded = requireNotNull(repository.loadGame(first))
        assertEquals("updated", loaded.sources.single().metadata["remote"])
        assertEquals("yes", loaded.sources.single().metadata["preserve"])
        assertEquals("Local rich metadata", loaded.tree.headers["Event"])
        assertEquals("keep-header", loaded.tree.headers["X-Local"])
        assertEquals("keep comment", loaded.tree.mainline().first().comments.single())
        assertEquals(listOf("e5", "c5"), loaded.tree.childrenOf(loaded.tree.mainline().first().id).map { it.san })
        assertEquals(listOf(1), loaded.tree.childrenOf(loaded.tree.mainline().first().id)[1].nags.map { it.value })
    }

    @Test
    fun sameExternalIdInDifferentSourceNamespacesDoesNotCollide() = runBlocking {
        val tree = Pgn.parseGame("[Result \"*\"]\n\n1. d4 d5 *")
        val chessCom = repository.persistExternalGame(
            PersistGameRequest(tree),
            GameSourceDraft(GameSourceType.CHESS_COM, externalGameId = "same-id", sourceAccountId = "acct"),
        )
        val lichess = repository.persistExternalGame(
            PersistGameRequest(tree),
            GameSourceDraft(GameSourceType.LICHESS, externalGameId = "same-id", sourceAccountId = "acct"),
        )
        val otherAccount = repository.persistExternalGame(
            PersistGameRequest(tree),
            GameSourceDraft(GameSourceType.CHESS_COM, externalGameId = "same-id", sourceAccountId = "other"),
        )

        assertEquals(3, setOf(chessCom, lichess, otherAccount).size)
        assertEquals(3, database.gameDao().countGames())
    }

    @Test
    fun identicalContentAloneNeverAutoMergesIndependentCanonicalSaves() = runBlocking {
        val firstTree = Pgn.parseGame("[Event \"one\"]\n[Result \"*\"]\n\n1. Nf3 d5 *")
        val secondTree = Pgn.parseGame("[Event \"two\"]\n[Result \"*\"]\n\n1. Nf3 d5 *")
        val first = repository.saveGame(PersistGameRequest(firstTree))
        val second = repository.saveGame(PersistGameRequest(secondTree))

        assertNotEquals(first, second)
        val candidates = repository.findContentCandidates(firstTree)
        assertEquals(setOf(first, second), candidates.toSet())
        assertEquals(2, database.gameDao().countGames())
    }

    @Test
    fun attachingSecondVerifiedProvenanceKeepsGameIdentityAndLocalTreeMetadata() = runBlocking {
        var tree = Pgn.parseGame(
            """
            [Result "*"]

            1. e4 {local comment} e5 (1... c5 $1) 2. Nf3 *
            """.trimIndent(),
        )
        val c5 = tree.childrenOf(tree.mainline().first().id)[1]
        tree = tree.withNodeMetadata(c5.id, annotations = linkedMapOf("local" to "keep"))
        val gameId = repository.saveGame(PersistGameRequest(tree))

        val attached = repository.attachVerifiedSource(
            gameId,
            GameSourceDraft(
                GameSourceType.LICHESS,
                externalGameId = "lichess-1",
                sourceAccountId = "acct-l",
                metadata = linkedMapOf("remote" to "value"),
            ),
        )
        repository.attachVerifiedSource(
            gameId,
            GameSourceDraft(
                GameSourceType.CHESS_COM,
                externalGameId = "chesscom-1",
                sourceAccountId = "acct-c",
            ),
        )

        assertEquals(gameId, attached)
        val loaded = requireNotNull(repository.loadGame(gameId))
        assertEquals(2, loaded.sources.size)
        assertEquals("local comment", loaded.tree.mainline().first().comments.single())
        val loadedC5 = loaded.tree.childrenOf(loaded.tree.mainline().first().id)[1]
        assertEquals(listOf(1), loadedC5.nags.map { it.value })
        assertEquals("keep", loadedC5.annotations["local"])
    }

    @Test
    fun conflictingContentUnderSameStrongIdentityFailsWithoutRewritingExistingGame() = runBlocking {
        val original = Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 *")
        val conflict = Pgn.parseGame("[Result \"*\"]\n\n1. d4 d5 *")
        val source = GameSourceDraft(GameSourceType.CHESS_COM, externalGameId = "strong-1", sourceAccountId = "acct")
        val gameId = repository.persistExternalGame(PersistGameRequest(original), source)

        val error = runCatching {
            repository.persistExternalGame(PersistGameRequest(conflict), source.copy(metadata = mapOf("bad" to "must-not-commit")))
        }.exceptionOrNull()

        assertTrue(error is PersistenceConflictException)
        assertEquals(1, database.gameDao().countGames())
        val loaded = requireNotNull(repository.loadGame(gameId))
        assertEquals(original.mainline().map { it.move }, loaded.tree.mainline().map { it.move })
        assertEquals(null, loaded.sources.single().metadata["bad"])
    }

    @Test
    fun sourceAlreadyMappedToAnotherGameCannotBeReassignedAndOperationRollsBack() = runBlocking {
        val tree = Pgn.parseGame("[Result \"*\"]\n\n1. c4 e5 *")
        val source = GameSourceDraft(GameSourceType.LICHESS, externalGameId = "locked", sourceAccountId = "acct")
        val first = repository.persistExternalGame(PersistGameRequest(tree), source)
        val second = repository.saveGame(PersistGameRequest(tree))

        val error = runCatching {
            repository.attachVerifiedSource(second, source.copy(metadata = mapOf("x" to "y")))
        }.exceptionOrNull()

        assertTrue(error is PersistenceConflictException)
        assertEquals(1, database.sourceDao().countForGame(first.value))
        assertEquals(0, database.sourceDao().countForGame(second.value))
        assertEquals(2, database.gameDao().countGames())
    }

    @Test
    fun databaseConstraintPreventsConcurrentDuplicateStrongMappings() = runBlocking {
        val tree = Pgn.parseGame("[Result \"*\"]\n\n1. e4 c5 *")
        val source = GameSourceDraft(GameSourceType.CHESS_COM, externalGameId = "race", sourceAccountId = "account-race")

        val ids = coroutineScope {
            listOf(
                async { repository.persistExternalGame(PersistGameRequest(tree), source) },
                async { repository.persistExternalGame(PersistGameRequest(tree), source) },
            ).awaitAll()
        }

        assertEquals(1, ids.toSet().size)
        assertEquals(1, database.gameDao().countGames())
        assertEquals(1, database.sourceDao().countForGame(ids.first().value))
        assertNotNull(database.sourceDao().byStrongIdentity(GameSourceType.CHESS_COM.name, "account-race", "race"))
    }

    @Test
    fun indexedCandidateAndStrongIdentityLookupsRemainBoundedAcrossModestLibrary() = runBlocking {
        val targetTree = Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 *")
        repeat(24) { index ->
            val tree = if (index % 2 == 0) {
                Pgn.parseGame("[Result \"*\"]\n\n1. d4 d5 2. c4 *")
            } else {
                Pgn.parseGame("[Result \"*\"]\n\n1. Nf3 Nf6 2. g3 *")
            }
            repository.saveGame(PersistGameRequest(tree))
        }
        val first = repository.saveGame(PersistGameRequest(targetTree))
        val second = repository.saveGame(PersistGameRequest(targetTree))
        repository.persistExternalGame(
            PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. c4 e5 *")),
            GameSourceDraft(GameSourceType.LICHESS, externalGameId = "bounded-source", sourceAccountId = "acct"),
        )

        assertEquals(setOf(first, second), repository.findContentCandidates(targetTree).toSet())
        assertNotNull(database.sourceDao().byStrongIdentity(GameSourceType.LICHESS.name, "acct", "bounded-source"))
    }
}

package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Pgn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceIntegrityTest {
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
    fun customFenStandardAndUnfinishedGameRoundTripAndListDeterministically() = runBlocking {
        val custom = Pgn.parseGame(
            "[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/K7 b - - 0 12\"]\n[Result \"*\"]\n\n12... Kg7 *",
        )
        val customId = repository.saveGame(
            PersistGameRequest(custom, GamePersistenceMetadata(createdAtEpochMillis = 20L, playedAtEpochMillis = 10L)),
        )
        val unfinishedId = repository.saveGame(
            PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *"), GamePersistenceMetadata(createdAtEpochMillis = 30L)),
        )

        val loaded = requireNotNull(repository.loadGame(customId))
        assertEquals(custom.startPosition, loaded.tree.startPosition)
        assertEquals(null, loaded.tree.result)
        assertEquals(listOf(unfinishedId, customId), repository.listGames().map { it.id })
    }

    @Test
    fun foreignKeyRejectsParentNodeFromAnotherGame() = runBlocking {
        val first = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")))
        val second = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n*")))
        val firstNode = database.gameDao().nodeIdsForGame(first.value).single()

        val error = runCatching {
            database.gameDao().insertNodes(
                listOf(
                    GameNodeEntity(
                        id = "cross-game-node",
                        gameId = second.value,
                        parentNodeId = firstNode,
                        siblingOrder = 0,
                        fromSquare = Move.parseUci("e2e4").from.index,
                        toSquare = Move.parseUci("e2e4").to.index,
                        promotionCode = null,
                        san = "e4",
                    ),
                ),
            )
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(0, database.gameDao().countNodes(second.value))
    }

    @Test
    fun malformedPersistedIllegalMoveFailsMappingRatherThanManufacturingTree() = runBlocking {
        val id = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n*")))
        val illegal = Move.parseUci("e2e5")
        database.gameDao().insertNodes(
            listOf(
                GameNodeEntity(
                    id = "illegal-node",
                    gameId = id.value,
                    parentNodeId = null,
                    siblingOrder = 0,
                    fromSquare = illegal.from.index,
                    toSquare = illegal.to.index,
                    promotionCode = null,
                    san = "e5",
                ),
            ),
        )

        val error = runCatching { repository.loadGame(id) }.exceptionOrNull()
        assertTrue(error is PersistenceMappingException)
    }

    @Test
    fun failedMultiNodeSaveRollsBackGameAndPartialTree() = runBlocking {
        val ids = ArrayDeque(listOf("game-id", "node-id", "node-id"))
        val collidingRepository = GamePersistenceRepository(database) { ids.removeFirst() }
        val tree = Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 *")

        val error = runCatching { collidingRepository.saveGame(PersistGameRequest(tree)) }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(0, database.gameDao().countGames())
        assertEquals(0, database.gameDao().countNodes("game-id"))
    }

    @Test
    fun hundredPlyMainlineRoundTripsWithoutPerNodePersistenceQueries() = runBlocking {
        var tree = GameTree.create()
        val cycle = listOf("g1f3", "g8f6", "f3g1", "f6g8")
        repeat(25) {
            for (uci in cycle) {
                val parent = tree.mainline().lastOrNull()?.id ?: tree.rootId
                tree = tree.addMove(parent, Move.parseUci(uci)).tree
            }
        }

        val id = repository.saveGame(PersistGameRequest(tree))
        val loaded = requireNotNull(repository.loadGame(id)).tree
        assertEquals(100, loaded.mainline().size)
        assertEquals(tree.mainline().map { it.move }, loaded.mainline().map { it.move })
    }
}

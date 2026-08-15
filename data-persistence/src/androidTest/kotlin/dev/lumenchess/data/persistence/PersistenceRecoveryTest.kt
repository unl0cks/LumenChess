package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class PersistenceRecoveryTest {
    private lateinit var context: Context
    private lateinit var database: LumenDatabase
    private lateinit var repository: GamePersistenceRepository
    private val fileDatabaseName = "m9-recovery.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(fileDatabaseName)
        database = LumenDatabaseFactory.inMemory(context)
        repository = GamePersistenceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(fileDatabaseName)
    }

    @Test
    fun malformedOrMismatchedStoredFingerprintFailsLoudly() = runBlocking {
        val id = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")))

        database.gameDao().overwriteFingerprint(id.value, "not-a-fingerprint")
        assertTrue(runCatching { repository.loadGame(id) }.exceptionOrNull() is PersistenceMappingException)

        database.gameDao().overwriteFingerprint(id.value, "gcf1:" + "0".repeat(64))
        assertTrue(runCatching { repository.loadGame(id) }.exceptionOrNull() is PersistenceMappingException)
    }

    @Test
    fun migratedNullFingerprintCanBeBackfilledDeterministically() = runBlocking {
        val tree = Pgn.parseGame("[Result \"*\"]\n\n1. Nf3 d5 *")
        val id = repository.saveGame(PersistGameRequest(tree))
        database.gameDao().overwriteFingerprint(id.value, null)

        assertEquals(1, repository.backfillMissingContentFingerprints())
        assertEquals(GameContentFingerprint.compute(tree), database.gameDao().gameById(id.value)!!.contentFingerprint)
        assertNotNull(repository.loadGame(id))
        assertEquals(0, repository.backfillMissingContentFingerprints())
    }

    @Test
    fun malformedSourceEnumOrAccountScopeFailsRatherThanBeingRepaired() = runBlocking {
        val enumGame = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")))
        database.sourceDao().insertSources(
            listOf(GameSourceEntity("bad-enum", enumGame.value, "NOT_A_SOURCE", null, null, null, null, null, "")),
        )
        assertTrue(runCatching { repository.loadGame(enumGame) }.exceptionOrNull() is PersistenceMappingException)

        val scopeGame = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. d4 *")))
        database.sourceDao().insertSources(
            listOf(GameSourceEntity("bad-scope", scopeGame.value, GameSourceType.LICHESS.name, "id", null, null, null, "account-a", "account-b")),
        )
        assertTrue(runCatching { repository.loadGame(scopeGame) }.exceptionOrNull() is PersistenceMappingException)
    }

    @Test
    fun foreignKeysRejectOrphanedSourceMetadata() = runBlocking {
        val error = runCatching {
            database.sourceDao().insertMetadata(listOf(GameSourceMetadataEntity("missing-source", "key", "value")))
        }.exceptionOrNull()
        assertNotNull(error)
    }

    @Test
    fun duplicateRootSiblingOrderAndCyclesStillFailLoudly() = runBlocking {
        val duplicateGame = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")))
        val d4 = Move.parseUci("d2d4")
        database.gameDao().insertNodes(
            listOf(
                GameNodeEntity("duplicate-root-order", duplicateGame.value, null, 0, d4.from.index, d4.to.index, null, "d4"),
            ),
        )
        assertTrue(runCatching { repository.loadGame(duplicateGame) }.exceptionOrNull() is PersistenceMappingException)

        val cycleGame = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 *")))
        val rows = database.gameDao().nodesForGame(cycleGame.value)
        val root = rows.single { it.parentNodeId == null }
        val child = rows.single { it.parentNodeId == root.id }
        database.gameDao().overwriteParent(root.id, child.id)
        assertTrue(runCatching { repository.loadGame(cycleGame) }.exceptionOrNull() is PersistenceMappingException)
    }

    @Test
    fun completedOperationSurvivesDatabaseCloseAndReopen() = runBlocking {
        database.close()
        database = LumenDatabaseFactory.open(context, fileDatabaseName)
        repository = GamePersistenceRepository(database)
        val tree = Pgn.parseGame("[Result \"*\"]\n\n1. e4 c5 2. Nf3 *")
        val expectedFingerprint = GameContentFingerprint.compute(tree)
        val id = repository.persistExternalGame(
            PersistGameRequest(tree),
            GameSourceDraft(GameSourceType.LICHESS, externalGameId = "reopen", sourceAccountId = "acct"),
        )
        val fingerprintBeforeClose = database.gameDao().gameById(id.value)!!.contentFingerprint
        assertEquals(expectedFingerprint, fingerprintBeforeClose)
        database.close()

        database = LumenDatabaseFactory.open(context, fileDatabaseName)
        repository = GamePersistenceRepository(database)
        val fingerprintAfterReopen = database.gameDao().gameById(id.value)!!.contentFingerprint
        val loaded = requireNotNull(repository.loadGame(id))
        assertEquals(fingerprintBeforeClose, fingerprintAfterReopen)
        assertEquals(expectedFingerprint, fingerprintAfterReopen)
        assertEquals(tree.mainline().map { it.move }, loaded.tree.mainline().map { it.move })
        assertEquals(id, repository.persistExternalGame(PersistGameRequest(tree), GameSourceDraft(GameSourceType.LICHESS, externalGameId = "reopen", sourceAccountId = "acct")))
    }

    @Test
    fun failedCanonicalSaveLeavesNoHalfStateAfterDatabaseReopen() = runBlocking {
        database.close()
        database = LumenDatabaseFactory.open(context, fileDatabaseName)
        val ids = ArrayDeque(listOf("game-id", "node-id", "node-id"))
        repository = GamePersistenceRepository(database) { ids.removeFirst() }
        val error = runCatching {
            repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 *")))
        }.exceptionOrNull()
        assertNotNull(error)
        database.close()

        database = LumenDatabaseFactory.open(context, fileDatabaseName)
        repository = GamePersistenceRepository(database)
        assertEquals(0, database.gameDao().countGames())
        assertEquals(0, database.gameDao().countNodes("game-id"))
    }
}

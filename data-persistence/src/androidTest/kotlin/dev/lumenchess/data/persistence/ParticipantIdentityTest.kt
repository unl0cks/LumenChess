package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Pgn
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
class ParticipantIdentityTest {
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
    fun identicalDisplayNamesInIndependentGamesRemainDistinctParticipants() = runBlocking {
        val firstGame = repository.saveGame(
            PersistGameRequest(
                Pgn.parseGame("[Result \"*\"]\n\n1. e4 *"),
                whiteParticipant = ParticipantDraft(ParticipantKind.HUMAN, "Timeless"),
            ),
        )
        val secondGame = repository.saveGame(
            PersistGameRequest(
                Pgn.parseGame("[Result \"*\"]\n\n1. d4 *"),
                whiteParticipant = ParticipantDraft(ParticipantKind.HUMAN, "Timeless"),
            ),
        )

        val first = requireNotNull(repository.loadGame(firstGame)).whiteParticipant
        val second = requireNotNull(repository.loadGame(secondGame)).whiteParticipant
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first!!.id, second!!.id)
    }

    @Test
    fun localHumanAndEngineNeverMergeWithImportedPlayerSharingName() = runBlocking {
        val local = repository.saveGame(
            PersistGameRequest(
                Pgn.parseGame("[Result \"*\"]\n\n1. e4 *"),
                whiteParticipant = ParticipantDraft(ParticipantKind.HUMAN_LOCAL, "SameName"),
                blackParticipant = ParticipantDraft(ParticipantKind.ENGINE, "SameName", "FixtureEngine", "1"),
            ),
        )
        val external = repository.resolveExternalParticipant(
            ParticipantDraft(ParticipantKind.EXTERNAL, "SameName"),
            ParticipantExternalIdentity(GameSourceType.LICHESS, "player-1", "acct"),
        )

        val loaded = requireNotNull(repository.loadGame(local))
        assertNotEquals(loaded.whiteParticipant!!.id, external)
        assertNotEquals(loaded.blackParticipant!!.id, external)
        assertEquals(3, database.participantDao().countAll())
    }

    @Test
    fun sameExplicitExternalIdentityReusesParticipantButDifferentNamespaceDoesNot() = runBlocking {
        val first = repository.resolveExternalParticipant(
            ParticipantDraft(ParticipantKind.EXTERNAL, "Old label"),
            ParticipantExternalIdentity(GameSourceType.CHESS_COM, "player-7", "account-a"),
        )
        val repeat = repository.resolveExternalParticipant(
            ParticipantDraft(ParticipantKind.EXTERNAL, "New remote label"),
            ParticipantExternalIdentity(GameSourceType.CHESS_COM, "player-7", "account-a"),
        )
        val otherAccount = repository.resolveExternalParticipant(
            ParticipantDraft(ParticipantKind.EXTERNAL, "Old label"),
            ParticipantExternalIdentity(GameSourceType.CHESS_COM, "player-7", "account-b"),
        )
        val otherSource = repository.resolveExternalParticipant(
            ParticipantDraft(ParticipantKind.EXTERNAL, "Old label"),
            ParticipantExternalIdentity(GameSourceType.LICHESS, "player-7", "account-a"),
        )

        assertEquals(first, repeat)
        assertEquals(3, setOf(first, otherAccount, otherSource).size)
        assertEquals(3, database.participantDao().countAll())
        assertEquals(1, database.participantDao().externalIdentityCount(first.value))
    }

    @Test
    fun deletingGameAndItsSourcesDoesNotDeleteUnrelatedExternalParticipantIdentity() = runBlocking {
        val external = repository.resolveExternalParticipant(
            ParticipantDraft(ParticipantKind.EXTERNAL, "Remote"),
            ParticipantExternalIdentity(GameSourceType.LICHESS, "remote-person", "acct"),
        )
        val game = repository.persistExternalGame(
            PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. Nf3 *")),
            GameSourceDraft(GameSourceType.LICHESS, externalGameId = "remote-game", sourceAccountId = "acct"),
        )

        repository.deleteGame(game)

        assertNotNull(database.participantDao().byId(external.value))
        assertNotNull(database.participantDao().externalIdentity(GameSourceType.LICHESS.name, "acct", "remote-person"))
        assertTrue(database.participantDao().countAll() >= 1)
    }
}

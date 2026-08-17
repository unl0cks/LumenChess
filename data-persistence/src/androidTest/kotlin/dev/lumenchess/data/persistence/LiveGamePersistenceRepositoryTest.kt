package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Pgn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveGamePersistenceRepositoryTest {
    private lateinit var database: LumenDatabase
    private lateinit var canonical: GamePersistenceRepository
    private lateinit var live: LiveGamePersistenceRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = LumenDatabaseFactory.inMemory(context)
        canonical = GamePersistenceRepository(database)
        live = LiveGamePersistenceRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun repeatedLiveSavesKeepStableUuidAndDoNotDuplicateMainlineNodes() = runBlocking {
        val initial = Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")
        val later = Pgn.parseGame("[Result \"*\"]\n\n1. e4 e5 2. Nf3 *")

        val id = live.persist(
            existingId = null,
            tree = initial,
            metadata = GamePersistenceMetadata(createdAtEpochMillis = 10L),
            restoreMetadata = mapOf("runtime.revision" to "1"),
        )
        val second = live.persist(
            existingId = id,
            tree = later,
            metadata = GamePersistenceMetadata(createdAtEpochMillis = 10L),
            restoreMetadata = mapOf("runtime.revision" to "3"),
        )
        val third = live.persist(
            existingId = id,
            tree = later,
            metadata = GamePersistenceMetadata(createdAtEpochMillis = 10L),
            restoreMetadata = mapOf("runtime.revision" to "3"),
        )

        assertEquals(id, second)
        assertEquals(id, third)
        assertEquals(1, database.gameDao().countGames())
        assertEquals(3, database.gameDao().countNodes(id.value))
        val loaded = requireNotNull(canonical.loadGame(id))
        assertEquals(listOf("e2e4", "e7e5", "g1f3"), loaded.tree.mainline().map { it.move!!.uci })
    }

    @Test
    fun liveSavePreservesExistingVariationsCommentsAndSourceMetadata() = runBlocking {
        val annotated = Pgn.parseGame(
            """
            [Event "Annotated live game"]
            [Result "*"]

            1. e4 {keep this comment} e5 (1... c5 {keep this variation}) 2. Nf3 *
            """.trimIndent(),
        )
        val id = canonical.saveGame(
            PersistGameRequest(
                tree = annotated,
                metadata = GamePersistenceMetadata(createdAtEpochMillis = 20L),
                sources = listOf(
                    GameSourceDraft(
                        type = GameSourceType.LOCAL,
                        metadata = mapOf("user-note" to "keep-me"),
                    ),
                ),
            ),
        )
        val runtimeMainline = Pgn.parseGame(
            """
            [Result "*"]

            1. e4 e5 2. Nf3 Nc6 *
            """.trimIndent(),
        )

        live.persist(
            existingId = id,
            tree = runtimeMainline,
            metadata = GamePersistenceMetadata(createdAtEpochMillis = 20L),
            restoreMetadata = mapOf("runtime.revision" to "4"),
        )

        val loaded = requireNotNull(canonical.loadGame(id))
        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6"), loaded.tree.mainline().map { it.move!!.uci })
        assertEquals("Annotated live game", loaded.tree.headers["Event"])
        val pgn = Pgn.serialize(loaded.tree)
        assertTrue(pgn.contains("keep this comment"))
        assertTrue(pgn.contains("keep this variation"))
        assertTrue(loaded.sources.any { it.metadata["user-note"] == "keep-me" })
    }
}

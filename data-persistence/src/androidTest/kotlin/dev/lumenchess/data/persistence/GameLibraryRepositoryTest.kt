package dev.lumenchess.data.persistence

import android.content.Context
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Pgn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameLibraryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "m23-library-test.db"
    private lateinit var database: LumenDatabase
    private lateinit var library: GameLibraryRepository

    @Before fun setUp() {
        context.deleteDatabase(name)
        database = LumenDatabaseFactory.open(context, name)
        library = GameLibraryRepository(database)
    }

    @After fun tearDown() { database.close(); context.deleteDatabase(name) }

    // Catches source joins duplicating canonical cards or mapping a filter to the wrong source.
    @Test fun mixedSourcesHaveOneCanonicalCardAndAllEightFiltersWork() = runBlocking {
        val alpha = save("Alpha", 30, GameSourceType.LOCAL)
        val beta = save("Beta", 20, GameSourceType.ENGINE_ARENA, GameSourceType.LOCAL)
        val imported = save("100% Real", 10, GameSourceType.PGN_IMPORT)
        assertEquals(listOf(alpha, beta, imported), library.page().entries.map { it.id })
        assertEquals(setOf(GameSourceType.LOCAL, GameSourceType.ENGINE_ARENA), library.page().entries[1].sources)
        val chess = save("Chess", 9, GameSourceType.CHESS_COM)
        val lichess = save("Lichess", 8, GameSourceType.LICHESS)
        val branch = save("Branch", 7, GameSourceType.BRANCH)
        assertTrue(library.setFavorite(beta, true))
        val expected = mapOf(
            LibraryFilter.ALL to listOf(alpha, beta, imported, chess, lichess, branch),
            LibraryFilter.LOCAL to listOf(alpha, beta), LibraryFilter.ENGINE_ARENA to listOf(beta),
            LibraryFilter.CHESS_COM to listOf(chess), LibraryFilter.LICHESS to listOf(lichess),
            LibraryFilter.IMPORTED to listOf(imported), LibraryFilter.BRANCHES to listOf(branch),
            LibraryFilter.FAVORITES to listOf(beta),
        )
        expected.forEach { (filter, ids) -> assertEquals(filter.name, ids, library.page(LibraryQuery(filter)).entries.map { it.id }) }
    }

    // Catches SQL wildcard interpretation, injection, wrong search fields, or filtering blank search.
    @Test fun searchTreatsPunctuationLiterallyAndFindsNamesEnginesAndUsefulHeaders() = runBlocking {
        val alpha = save("Alpha", 30)
        val percent = save("100% Real", 20)
        val underscore = save("under_score", 10)
        val quote = save("O'Brien", 5)
        assertEquals(listOf(percent), library.page(LibraryQuery(search = "%")).entries.map { it.id })
        assertEquals(listOf(underscore), library.page(LibraryQuery(search = "_")).entries.map { it.id })
        assertEquals(listOf(quote), library.page(LibraryQuery(search = "'" )).entries.map { it.id })
        assertEquals(listOf(alpha), library.page(LibraryQuery(search = "aLpHa")).entries.map { it.id })
        assertEquals(4, library.page(LibraryQuery(search = "  ")).entries.size)
        assertTrue(library.page(LibraryQuery(search = "' OR 1=1 --")).entries.isEmpty())
        val engine = GamePersistenceRepository(database).saveGame(PersistGameRequest(
            Pgn.parseGame("[Event \"Cup\"]\n[Opening \"Sicilian\"]\n[ECO \"B20\"]\n\n1. e4 *"),
            whiteParticipant = ParticipantDraft(ParticipantKind.ENGINE, engineName = "FixtureEngine"),
        ))
        for (search in listOf("FixtureEngine", "cup", "sicilian", "b20")) {
            assertEquals(listOf(engine), library.page(LibraryQuery(search = search)).entries.map { it.id })
        }
    }

    // Catches unstable tie ordering, off-by-one cursors, unlimited page sizes, and tree reconstruction.
    @Test fun keysetPagesAreBoundedStableAndDoNotReadMalformedTrees() = runBlocking {
        database.withWriteTransaction {
            for (index in 0 until 205) {
                database.gameDao().insertGame(GameEntity(
                    id = "00000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
                    variant = "STANDARD", startFen = "deliberately not a legal tree", result = null,
                    termination = null, createdAtEpochMillis = 10, importedAtEpochMillis = null,
                    playedAtEpochMillis = null, rated = null, timeControlBaseMillis = null,
                    timeControlIncrementMillis = null, timeControlRaw = null,
                    whiteParticipantId = null, blackParticipantId = null,
                ))
            }
        }
        assertEquals(40, library.page().entries.size)
        val first = library.page(limit = Int.MAX_VALUE)
        val second = library.page(cursor = first.nextCursor, limit = 100)
        val third = library.page(cursor = second.nextCursor, limit = 100)
        assertEquals(listOf(100, 100, 5), listOf(first, second, third).map { it.entries.size })
        assertEquals("00000000-0000-0000-0000-000000000000", first.entries.first().id.value)
        assertEquals("00000000-0000-0000-0000-000000000100", second.entries.first().id.value)
        assertEquals(205, (first.entries + second.entries + third.entries).map { it.id }.toSet().size)
        assertNull(third.nextCursor)
        assertTrue(runCatching { library.page(limit = 0) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun twoEntryPageReturnsRemainingOneAndEmptyLibraryHasNoCursor() = runBlocking {
        assertEquals(LibraryPage(emptyList(), null), library.page())
        val alpha = save("Alpha", 30)
        val beta = save("Beta", 20)
        val gamma = save("Gamma", 10)
        val first = library.page(limit = 2)
        assertEquals(listOf(alpha, beta), first.entries.map { it.id })
        val last = library.page(cursor = first.nextCursor, limit = 2)
        assertEquals(listOf(gamma), last.entries.map { it.id })
        assertNull(last.nextCursor)
    }

    // Catches flag replacement resetting the other flag, persistence loss and orphan writes.
    @Test fun independentFlagsSurviveReopenAndMissingIdsReturnFalse() = runBlocking {
        val id = save("Keep", 1)
        assertTrue(library.setFavorite(id, true))
        assertTrue(library.setProtected(id, true))
        database.close()
        database = LumenDatabaseFactory.open(context, name)
        library = GameLibraryRepository(database)
        assertTrue(library.page().entries.single().isFavorite)
        assertTrue(library.page().entries.single().isProtected)
        library.setFavorite(id, false)
        assertFalse(library.page().entries.single().isFavorite)
        assertTrue(library.page().entries.single().isProtected)
        library.setProtected(id, false)
        assertFalse(library.page().entries.single().isProtected)
        val missing = PersistentGameId("missing")
        assertFalse(library.setFavorite(missing, true))
        assertFalse(library.setProtected(missing, true))
        assertFalse(library.delete(missing))
    }

    // Catches retention pruning flagged games or deletion crossing canonical ownership.
    @Test fun flagsExcludeAgeAndCountRetentionAndManualDeleteCascadesOnlyTarget() = runBlocking {
        val favorite = save("Favorite", 1, GameSourceType.LOCAL)
        val protected = save("Protected", 2, GameSourceType.LOCAL)
        val ordinary = save("Ordinary", 3, GameSourceType.LOCAL)
        for (id in listOf(favorite, protected, ordinary)) addReview(id)
        library.setFavorite(favorite, true)
        library.setProtected(protected, true)
        assertEquals(1, PersistenceRetention(database).prune(HeavyAnalysisRetentionPolicy(olderThanEpochMillis = 100)))
        assertEquals(0, PersistenceRetention(database).prune(HeavyAnalysisRetentionPolicy(maxRetainedCount = 0)))
        assertEquals(2, database.reviewDao().countHeavy())
        assertEquals(ReviewState.COMPLETE, library.page().entries.first().latestReviewState)
        val unchanged = requireNotNull(GamePersistenceRepository(database).loadGame(protected))
        assertTrue(library.delete(favorite))
        assertNull(GamePersistenceRepository(database).loadGame(favorite))
        assertEquals(0, database.gameDao().countNodes(favorite.value))
        assertEquals(0, database.sourceDao().countForGame(favorite.value))
        assertEquals(0, database.reviewDao().countReviewsForGame(favorite.value))
        assertEquals(0, database.reviewDao().countReviewPliesForGame(favorite.value))
        assertEquals(1, database.reviewDao().countHeavy())
        val after = requireNotNull(GamePersistenceRepository(database).loadGame(protected))
        assertEquals(Pgn.serialize(unchanged.tree), Pgn.serialize(after.tree))
        assertEquals(unchanged.sources, after.sources)
        assertEquals(2, library.page().entries.size)
    }

    private suspend fun save(name: String, time: Long, vararg types: GameSourceType): PersistentGameId =
        GamePersistenceRepository(database).saveGame(PersistGameRequest(
            Pgn.parseGame("1. e4 {keep} e5 *"), GamePersistenceMetadata(createdAtEpochMillis = time),
            whiteParticipant = ParticipantDraft(ParticipantKind.HUMAN, displayName = name),
            sources = types.map { GameSourceDraft(it) },
        ))

    private suspend fun addReview(id: PersistentGameId) {
        val node = database.gameDao().nodeIdsForGame(id.value).first()
        database.reviewDao().insertReview(ReviewEntity("r-${id.value}", id.value, "model", "engine", null, null, "COMPLETE", 1, 1, 2, 2))
        database.reviewDao().insertPly(ReviewPlyEntity("p-${id.value}", id.value, "r-${id.value}", node, null, null, null, null, null, null, null, null, null, null))
        database.reviewDao().insertHeavy(ReviewHeavyAnalysisEntity("h-${id.value}", "p-${id.value}", "pv", "e4", 1))
    }
}

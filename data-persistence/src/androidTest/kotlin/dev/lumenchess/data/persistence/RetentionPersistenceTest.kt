package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class RetentionPersistenceTest {
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
    fun retentionClassesExplicitlySeparateCanonicalSummaryAndDisposableData() {
        assertEquals(RetentionClass.CANONICAL_DURABLE, PersistenceRetention.classFor("games"))
        assertEquals(RetentionClass.CANONICAL_DURABLE, PersistenceRetention.classFor("game_nodes"))
        assertEquals(RetentionClass.CANONICAL_DURABLE, PersistenceRetention.classFor("game_sources"))
        assertEquals(RetentionClass.CANONICAL_DURABLE, PersistenceRetention.classFor("saved_positions"))
        assertEquals(RetentionClass.CANONICAL_DURABLE, PersistenceRetention.classFor("rating_events"))
        assertEquals(RetentionClass.LIGHTWEIGHT_DURABLE, PersistenceRetention.classFor("reviews"))
        assertEquals(RetentionClass.LIGHTWEIGHT_DURABLE, PersistenceRetention.classFor("review_plies"))
        assertEquals(RetentionClass.HEAVYWEIGHT_DISPOSABLE, PersistenceRetention.classFor("review_heavy_analysis"))
    }

    @Test
    fun agePruningDeletesOnlyHeavyAnalysisAndPreservesCanonicalAndLightweightState() = runBlocking {
        val fixture = createAnnotatedFixture()
        addHeavyRows(fixture.reviewPlyId, listOf(10L, 20L, 30L))

        val deleted = repository.pruneDisposableAnalysis(
            HeavyAnalysisRetentionPolicy(olderThanEpochMillis = 25L, maxRetainedCount = null, batchSize = 100),
        )

        assertEquals(2, deleted)
        assertEquals(listOf("heavy-30"), database.reviewDao().heavyIdsOldestFirst())
        assertEquals(1, database.reviewDao().countReviewsForGame(fixture.gameId.value))
        assertEquals(1, database.reviewDao().countReviewPliesForGame(fixture.gameId.value))
        assertDurableFixtureSurvives(fixture)
    }

    @Test
    fun maxCountPruningKeepsNewestHeavyRowsWithDeterministicTieBreak() = runBlocking {
        val fixture = createAnnotatedFixture()
        database.reviewDao().insertHeavy(ReviewHeavyAnalysisEntity("a-old", fixture.reviewPlyId, "pv", "a", 10L))
        database.reviewDao().insertHeavy(ReviewHeavyAnalysisEntity("b-old", fixture.reviewPlyId, "pv", "b", 10L))
        database.reviewDao().insertHeavy(ReviewHeavyAnalysisEntity("c-new", fixture.reviewPlyId, "pv", "c", 20L))
        database.reviewDao().insertHeavy(ReviewHeavyAnalysisEntity("d-new", fixture.reviewPlyId, "pv", "d", 30L))

        val deleted = repository.pruneDisposableAnalysis(
            HeavyAnalysisRetentionPolicy(olderThanEpochMillis = null, maxRetainedCount = 2, batchSize = 100),
        )

        assertEquals(2, deleted)
        assertEquals(listOf("c-new", "d-new"), database.reviewDao().heavyIdsOldestFirst())
        assertDurableFixtureSurvives(fixture)
    }

    @Test
    fun failedRetentionTransactionRollsBackDeletedHeavyRows() = runBlocking {
        val fixture = createAnnotatedFixture()
        addHeavyRows(fixture.reviewPlyId, listOf(10L, 20L, 30L))
        val failing = PersistenceRetention(database) { throw IllegalStateException("forced failure after delete") }

        val error = runCatching {
            failing.prune(HeavyAnalysisRetentionPolicy(olderThanEpochMillis = 25L, batchSize = 100))
        }.exceptionOrNull()

        assertNotNull(error)
        assertEquals(listOf("heavy-10", "heavy-20", "heavy-30"), database.reviewDao().heavyIdsOldestFirst())
        assertDurableFixtureSurvives(fixture)
    }

    private suspend fun createAnnotatedFixture(): Fixture {
        val tree = Pgn.parseGame(
            "[X-Keep \"header\"]\n[Result \"*\"]\n\n1. e4 {keep comment} e5 (1... c5 $1) *",
        )
        val gameId = repository.persistExternalGame(
            PersistGameRequest(tree),
            GameSourceDraft(GameSourceType.CHESS_COM, externalGameId = "retention-game", sourceAccountId = "acct", metadata = mapOf("keep" to "source")),
        )
        val nodeId = database.gameDao().nodeIdsForGame(gameId.value).first()
        val review = ReviewEntity("review", gameId.value, "model", "engine", "1", "profile", ReviewState.COMPLETE.name, 1, 1, 2, 2)
        val ply = ReviewPlyEntity("review-ply", gameId.value, review.id, nodeId, 10, null, null, null, null, "summary", 0.1, 12, 100, 5)
        database.reviewDao().insertReview(review)
        database.reviewDao().insertPly(ply)

        val participant = ParticipantEntity("rating-person", ParticipantKind.HUMAN_LOCAL.name, "Local", null, null, 1)
        database.participantDao().insert(participant)
        database.ratingDao().insert(RatingEventEntity("rating", participant.id, RatingSystemType.GLICKO_2.name, "STANDARD", "rapid", 3, 1500.0, 60.0, 0.06, gameId.value))
        repository.saveSavedPosition(SavedPositionDraft(dev.lumenchess.core.chess.Variant.STANDARD, dev.lumenchess.core.chess.Fen.serialize(dev.lumenchess.core.chess.Position.initial()), "Keep"))
        return Fixture(gameId, ply.id, participant.id)
    }

    private suspend fun addHeavyRows(reviewPlyId: String, timestamps: List<Long>) {
        timestamps.forEach { timestamp ->
            database.reviewDao().insertHeavy(
                ReviewHeavyAnalysisEntity("heavy-$timestamp", reviewPlyId, "pv", "payload-$timestamp", timestamp),
            )
        }
    }

    private suspend fun assertDurableFixtureSurvives(fixture: Fixture) {
        val loaded = requireNotNull(repository.loadGame(fixture.gameId))
        assertEquals("header", loaded.tree.headers["X-Keep"])
        assertEquals("keep comment", loaded.tree.mainline().first().comments.single())
        assertTrue(loaded.tree.childrenOf(loaded.tree.mainline().first().id).size >= 2)
        assertEquals("source", loaded.sources.single().metadata["keep"])
        assertEquals(1, database.savedPositionDao().listAll().size)
        assertEquals(1, database.ratingDao().forParticipant(fixture.ratingParticipantId).size)
    }

    private data class Fixture(
        val gameId: PersistentGameId,
        val reviewPlyId: String,
        val ratingParticipantId: String,
    )
}

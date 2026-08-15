package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.core.chess.Variant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuxiliaryPersistenceTest {
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
    fun savedStandardAndChess960PositionsRoundTripAndInvalidFenFails() = runBlocking {
        val standard = SavedPositionDraft(
            Variant.STANDARD,
            Fen.serialize(dev.lumenchess.core.chess.Position.initial()),
            title = "Start",
            notes = "standard",
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 20L,
        )
        val chess960 = SavedPositionDraft(
            Variant.CHESS960,
            "7k/8/8/8/8/8/8/RK2R3 w EA - 0 1",
            title = "960 castle",
            notes = "EA rights",
            createdAtEpochMillis = 30L,
            updatedAtEpochMillis = 40L,
        )

        val standardId = repository.saveSavedPosition(standard)
        val chess960Id = repository.saveSavedPosition(chess960)

        assertEquals("Start", requireNotNull(repository.loadSavedPosition(standardId)).title)
        val loaded960 = requireNotNull(repository.loadSavedPosition(chess960Id))
        assertEquals(Variant.CHESS960, loaded960.variant)
        assertEquals(Fen.serialize(Fen.parse(chess960.fen, Variant.CHESS960)), loaded960.fen)

        val invalid = runCatching {
            repository.saveSavedPosition(SavedPositionDraft(Variant.STANDARD, "not-a-fen", "broken"))
        }.exceptionOrNull()
        assertTrue(invalid is PersistenceMappingException)
    }

    @Test
    fun heavyReviewDataCanBeDeletedWithoutGameOrSummaryAndGameDeletionCascadesReview() = runBlocking {
        val gameId = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")))
        val nodeId = database.gameDao().nodeIdsForGame(gameId.value).single()
        val review = ReviewEntity(
            id = "review-1",
            gameId = gameId.value,
            modelVersion = "m8-fixture",
            engineName = "FixtureEngine",
            engineVersion = "1",
            profile = "fixture",
            state = ReviewState.COMPLETE.name,
            progressPly = 1,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L,
            completedAtEpochMillis = 2L,
        )
        val ply = ReviewPlyEntity(
            id = "review-ply-1",
            gameId = gameId.value,
            reviewId = review.id,
            nodeId = nodeId,
            playedEvalCp = 20,
            playedMateIn = null,
            bestMoveFrom = 12,
            bestMoveTo = 28,
            bestMovePromotionCode = null,
            classification = "fixture-only",
            expectedPointsLoss = 0.05,
            depth = 15,
            nodes = 1000,
            timeMillis = 25,
        )
        database.reviewDao().insertReview(review)
        database.reviewDao().insertPly(ply)
        database.reviewDao().insertHeavy(
            ReviewHeavyAnalysisEntity("heavy-1", ply.id, "uci-pv", "e2e4 e7e5", 3L),
        )

        assertEquals(1, database.reviewDao().countReviewsForGame(gameId.value))
        assertEquals(1, database.reviewDao().countHeavy())
        database.reviewDao().deleteHeavyForReview(review.id)
        assertEquals(0, database.reviewDao().countHeavy())
        assertEquals(1, database.reviewDao().countReviewsForGame(gameId.value))
        assertNotNull(repository.loadGame(gameId))

        database.reviewDao().insertHeavy(
            ReviewHeavyAnalysisEntity("heavy-2", ply.id, "uci-pv", "e2e4", 4L),
        )
        repository.deleteGame(gameId)
        assertEquals(0, database.reviewDao().countReviewsForGame(gameId.value))
        assertEquals(0, database.reviewDao().countHeavy())
        assertNull(repository.loadGame(gameId))
    }

    @Test
    fun ratingEventsFormOrderedHistoryAndRelatedGameMayDisappear() = runBlocking {
        val participant = ParticipantEntity("rating-person", ParticipantKind.HUMAN_LOCAL.name, "Local", null, null, 1L)
        database.participantDao().insert(participant)
        val gameId = repository.saveGame(PersistGameRequest(Pgn.parseGame("[Result \"*\"]\n\n1. e4 *")))
        database.ratingDao().insert(
            RatingEventEntity(
                id = "rating-2",
                participantId = participant.id,
                ratingSystem = RatingSystemType.GLICKO_2.name,
                variant = Variant.STANDARD.name,
                timeControlPool = "rapid",
                recordedAtEpochMillis = 20L,
                ratingValue = 1510.0,
                deviation = 60.0,
                volatility = 0.06,
                gameId = gameId.value,
            ),
        )
        database.ratingDao().insert(
            RatingEventEntity(
                id = "rating-1",
                participantId = participant.id,
                ratingSystem = RatingSystemType.GLICKO_2.name,
                variant = Variant.STANDARD.name,
                timeControlPool = "rapid",
                recordedAtEpochMillis = 10L,
                ratingValue = 1500.0,
                deviation = 70.0,
                volatility = 0.06,
                gameId = null,
            ),
        )

        assertEquals(listOf("rating-1", "rating-2"), database.ratingDao().forParticipant(participant.id).map { it.id })
        repository.deleteGame(gameId)
        val history = database.ratingDao().forParticipant(participant.id)
        assertEquals(2, history.size)
        assertNull(history.last().gameId)
    }
}

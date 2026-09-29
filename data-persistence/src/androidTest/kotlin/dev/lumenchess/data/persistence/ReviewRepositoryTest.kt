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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewRepositoryTest {
    private lateinit var database: LumenDatabase
    private lateinit var games: GamePersistenceRepository
    private lateinit var reviews: ReviewRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = LumenDatabaseFactory.inMemory(context)
        games = GamePersistenceRepository(database)
        reviews = ReviewRepository(database, clock = { now })
    }

    @After
    fun tearDown() = database.close()

    private fun tree(): GameTree = Pgn.parseGame("1. e4 e5 (1... c5 2. Nf3) 2. Nf3 Nc6 *")

    @Test
    fun plyRowsFollowTheCanonicalMainlineAndVariationsAreIgnored() = runBlocking {
        val gameId = games.saveGame(PersistGameRequest(tree()))
        val mainline = reviews.mainlineNodeIds(gameId)
        assertEquals(4, mainline.size)
        assertEquals(4, games.loadGame(gameId)!!.tree.mainline().size)

        val review = reviews.startReview(gameId, "lumen-review-1", "Stockfish 18", null, "balanced")
        assertEquals(ReviewState.RUNNING, review.state)
        // Written out of order, partially, then completed: the resume path.
        reviews.savePly(review, ReviewPlyRecord(ply = 1, nodeId = mainline[1], bestMove = Move.parseUci("g1f3"), heavy = mapOf("lumen-eval-1" to "a")))
        reviews.savePly(review, ReviewPlyRecord(ply = 0, nodeId = mainline[0], playedEvalCp = -25, heavy = mapOf("lumen-eval-1" to "b")))
        reviews.savePly(review, ReviewPlyRecord(ply = 0, nodeId = mainline[0], playedEvalCp = -30, classification = "BOOK", expectedPointsLoss = 0.0, heavy = mapOf("lumen-eval-1" to "c")))

        val rows = reviews.plies(review)
        assertEquals(listOf(0, 1), rows.map { it.ply })
        assertEquals(-30, rows[0].playedEvalCp)
        assertEquals("BOOK", rows[0].classification)
        assertEquals(mapOf("lumen-eval-1" to "c"), rows[0].heavy)
        assertEquals(Move.parseUci("g1f3"), rows[1].bestMove)

        reviews.updateState(review.id, ReviewState.COMPLETE, 4)
        val complete = reviews.latestReview(gameId)!!
        assertEquals(ReviewState.COMPLETE, complete.state)
        assertEquals(1_000L, complete.completedAtEpochMillis)
        assertEquals(1, reviews.reviewsInState(ReviewState.COMPLETE).size)
        assertEquals(2, reviews.lightweightPlies(listOf(complete)).getValue(complete.id).size)
    }

    @Test
    fun compactionRemovesOnlyEngineLinesAndARestartReplacesTheOldReview() = runBlocking {
        val gameId = games.saveGame(PersistGameRequest(tree()))
        val mainline = reviews.mainlineNodeIds(gameId)
        val first = reviews.startReview(gameId, "lumen-review-1", "Stockfish 18", null, "fast")
        mainline.forEachIndexed { ply, node ->
            reviews.savePly(first, ReviewPlyRecord(ply, node, playedEvalCp = ply, classification = "BEST", expectedPointsLoss = 0.0, heavy = mapOf("lumen-eval-1" to "x$ply")))
        }
        assertTrue(PersistenceRetention.forDatabase(database).heavyUsage().entries == 4)
        now = 10_000L
        assertEquals(4, PersistenceRetention.forDatabase(database).prune(HeavyAnalysisRetentionPolicy(olderThanEpochMillis = 5_000L)))
        val compacted = reviews.plies(first)
        assertEquals(4, compacted.size)
        assertTrue(compacted.all { it.heavy.isEmpty() && it.classification == "BEST" })

        val second = reviews.startReview(gameId, "lumen-review-1", "Stockfish 18", null, "deep")
        assertNull(reviews.review(first.id))
        assertEquals(second.id, reviews.latestReview(gameId)!!.id)
        assertTrue(reviews.plies(second).isEmpty())
        assertFalse(reviews.deleteReview(first.id))
        assertTrue(reviews.deleteReview(second.id))
        assertNull(reviews.latestReview(gameId))
    }
}

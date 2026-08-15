package dev.lumenchess.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Pgn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MovePersistenceTest {
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
    fun explicitMoveEncodingPreservesNormalCapturePromotionEnPassantAndCastlingForms() = runBlocking {
        val cases = listOf(
            MoveCase("normal", "[Result \"*\"]\n\n1. e4 *", "e2e4"),
            MoveCase(
                "capture",
                "[SetUp \"1\"]\n[FEN \"7k/8/8/3p4/8/8/8/3QK3 w - - 0 1\"]\n[Result \"*\"]\n\n1. Qxd5 *",
                "d1d5",
            ),
            MoveCase(
                "promotion",
                "[SetUp \"1\"]\n[FEN \"k7/4P3/8/8/8/8/8/7K w - - 0 1\"]\n[Result \"*\"]\n\n1. e8=Q+ *",
                "e7e8q",
                promotionCode = 1,
            ),
            MoveCase(
                "en-passant",
                "[SetUp \"1\"]\n[FEN \"4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2\"]\n[Result \"*\"]\n\n2. exd6 *",
                "e5d6",
            ),
            MoveCase(
                "standard-castle",
                "[SetUp \"1\"]\n[FEN \"4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1\"]\n[Result \"*\"]\n\n1. O-O *",
                "e1g1",
            ),
            MoveCase(
                "chess960-kingside",
                "[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/RK2R3 w EA - 0 1\"]\n[Result \"*\"]\n\n1. O-O *",
                "b1e1",
            ),
            MoveCase(
                "chess960-queenside",
                "[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/RK2R3 w EA - 0 1\"]\n[Result \"*\"]\n\n1. O-O-O *",
                "b1a1",
            ),
            MoveCase(
                "chess960-king-stays",
                "[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/6KR w H - 0 1\"]\n[Result \"*\"]\n\n1. O-O *",
                "g1h1",
            ),
            MoveCase(
                "chess960-rook-stays",
                "[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/4KR2 w F - 0 1\"]\n[Result \"*\"]\n\n1. O-O *",
                "e1f1",
            ),
        )

        for (case in cases) {
            val tree = Pgn.parseGame(case.pgn)
            val expected = Move.parseUci(case.expectedUci)
            assertEquals(case.label, expected, tree.mainline().single().move)

            val gameId = repository.saveGame(PersistGameRequest(tree))
            val row = database.gameDao().nodesForGame(gameId.value).single()
            assertEquals(case.label, expected.from.index, row.fromSquare)
            assertEquals(case.label, expected.to.index, row.toSquare)
            assertEquals(case.label, case.promotionCode, row.promotionCode)

            val loaded = requireNotNull(repository.loadGame(gameId))
            assertEquals(case.label, expected, loaded.tree.mainline().single().move)
            assertEquals(case.label, tree.startPosition, loaded.tree.startPosition)
            assertEquals(case.label, tree.mainline().single().san, loaded.tree.mainline().single().san)
        }
    }

    private data class MoveCase(
        val label: String,
        val pgn: String,
        val expectedUci: String,
        val promotionCode: Int? = null,
    )
}

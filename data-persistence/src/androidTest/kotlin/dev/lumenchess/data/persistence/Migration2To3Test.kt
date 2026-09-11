package dev.lumenchess.data.persistence

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Pgn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val name = "m23-migration.db"
    @get:Rule val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = LumenDatabase::class,
        driver = AndroidSQLiteDriver(),
        file = instrumentation.targetContext.getDatabasePath(name),
    )
    @Before fun before() { instrumentation.targetContext.deleteDatabase(name) }
    @After fun after() { instrumentation.targetContext.deleteDatabase(name) }

    @Test fun populatedV2MigratesWithCanonicalIdentityContentsAndLineageIntact() = verify(2)
    @Test fun populatedV1MigratesThroughEveryVersionToLatest() = verify(1)

    // Catches destructive/rebuilding migrations, schema mismatch, lost source identity or changed FEN/tree data.
    private fun verify(version: Int) = runBlocking {
        val old = helper.createDatabase(version)
        old.execSQL("INSERT INTO participants VALUES ('person','HUMAN','White',NULL,NULL,1)")
        val standardFingerprint = GameContentFingerprint.compute(Pgn.parseGame("1. e4 e5 (1... c5 {keep note}) 1-0"))
        val chess960Fingerprint = GameContentFingerprint.compute(Pgn.parseGame("[Variant \"Chess960\"]\n[SetUp \"1\"]\n[FEN \"7k/8/8/8/8/8/8/RK2R3 w EA - 0 1\"]\n\n*"))
        val standardFingerprintColumn = if (version == 2) ", '$standardFingerprint'" else ""
        val chess960FingerprintColumn = if (version == 2) ", '$chess960Fingerprint'" else ""
        old.execSQL("INSERT INTO games VALUES ('standard','STANDARD','rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1','WHITE_WIN','RESIGNATION',30,NULL,20,1,60000,1000,'60+1','person',NULL$standardFingerprintColumn)")
        old.execSQL("INSERT INTO games VALUES ('chess960','CHESS960','7k/8/8/8/8/8/8/RK2R3 w EA - 0 1',NULL,NULL,10,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL$chess960FingerprintColumn)")
        old.execSQL("INSERT INTO game_headers VALUES ('standard','Event','Keep Cup',0)")
        old.execSQL("INSERT INTO game_nodes VALUES ('node-e4','standard',NULL,0,12,28,NULL,'e4')")
        old.execSQL("INSERT INTO game_nodes VALUES ('node-e5','standard','node-e4',0,52,36,NULL,'e5')")
        old.execSQL("INSERT INTO game_nodes VALUES ('node-c5','standard','node-e4',1,50,34,NULL,'c5')")
        old.execSQL("INSERT INTO game_node_comments VALUES ('comment','standard','node-c5','TRAILING',0,'keep note')")
        old.execSQL("INSERT INTO game_node_nags VALUES ('standard','node-c5',0,1)")
        old.execSQL("INSERT INTO game_node_annotations VALUES ('standard','node-c5','eval','+0.2')")
        val scope = if (version == 2) ", 'account'" else ""
        old.execSQL("INSERT INTO game_sources VALUES ('source','standard','BRANCH','external',NULL,40,50,'account'$scope)")
        old.execSQL("INSERT INTO game_source_metadata VALUES ('source','originGameId','parent-uuid')")
        old.execSQL("INSERT INTO game_source_metadata VALUES ('source','originNodeId','parent-node-uuid')")
        old.execSQL("INSERT INTO reviews VALUES ('review','standard','model','Engine',NULL,NULL,'COMPLETE',2,50,60,60)")
        old.execSQL("INSERT INTO review_plies VALUES ('ply','standard','review','node-e4',20,NULL,NULL,NULL,NULL,'good',0.0,12,100,10)")
        old.execSQL("INSERT INTO review_heavy_analysis VALUES ('heavy','ply','pv','e5',60)")
        if (version == 2) old.execSQL("INSERT INTO participant_external_identities VALUES ('LICHESS','account','remote-person','person')")
        old.close()
        val latest = helper.runMigrationsAndValidate(3, listOf(MIGRATION_1_2, MIGRATION_2_3))
        assertEquals("node-e4,node-e5,node-c5", text(latest, "SELECT group_concat(id) FROM (SELECT id FROM game_nodes ORDER BY rowid)"))
        assertEquals("ply", text(latest, "SELECT id FROM review_plies WHERE reviewId='review' AND nodeId='node-e4'"))
        assertEquals("heavy", text(latest, "SELECT id FROM review_heavy_analysis WHERE reviewPlyId='ply'"))
        assertEquals("account", text(latest, "SELECT sourceAccountScope FROM game_sources WHERE id='source'"))
        assertEquals("+0.2", text(latest, "SELECT value FROM game_node_annotations WHERE nodeId='node-c5'"))
        if (version == 2) {
            assertEquals(standardFingerprint, text(latest, "SELECT contentFingerprint FROM games WHERE id='standard'"))
            assertEquals(chess960Fingerprint, text(latest, "SELECT contentFingerprint FROM games WHERE id='chess960'"))
            assertEquals("person", text(latest, "SELECT participantId FROM participant_external_identities WHERE externalParticipantId='remote-person'"))
        }
        latest.close()
        val database = LumenDatabaseFactory.open(instrumentation.targetContext, name)
        try {
            val canonical = GamePersistenceRepository(database)
            val game = requireNotNull(canonical.loadGame(PersistentGameId("standard")))
            assertEquals(listOf("e4", "e5"), game.tree.mainline().map { it.san })
            assertEquals(listOf("e5", "c5"), game.tree.childrenOf(game.tree.mainline().first().id).map { it.san })
            assertEquals(listOf("keep note"), game.tree.childrenOf(game.tree.mainline().first().id)[1].comments)
            assertEquals("Keep Cup", game.tree.headers["Event"])
            assertEquals("parent-uuid", game.sources.single().metadata["originGameId"])
            assertEquals("parent-node-uuid", game.sources.single().metadata["originNodeId"])
            assertEquals("7k/8/8/8/8/8/8/RK2R3 w EA - 0 1", Fen.serialize(requireNotNull(canonical.loadGame(PersistentGameId("chess960"))).tree.startPosition))
            val library = GameLibraryRepository(database)
            assertEquals(listOf("standard", "chess960"), library.page().entries.map { it.id.value })
            assertTrue(library.page().entries.none { it.isFavorite || it.isProtected })
            assertTrue(library.setFavorite(game.id, true))
            assertEquals(listOf(game.id), library.page(LibraryQuery(LibraryFilter.FAVORITES)).entries.map { it.id })
        } finally { database.close() }
    }

    private fun text(connection: SQLiteConnection, sql: String): String = connection.prepare(sql).use {
        assertTrue(it.step()); it.getText(0)
    }
}

package dev.lumenchess.data.persistence

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.room3.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val databaseName = "m9-migration-v1-v2.db"

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        databaseClass = LumenDatabase::class,
        driver = AndroidSQLiteDriver(),
        file = instrumentation.targetContext.getDatabasePath(databaseName),
    )

    @Test
    fun migration1To2PreservesCanonicalV1DataAndAddsM9IdentityStructures() = runBlocking {
        val v1 = helper.createDatabase(1)
        v1.execSQL("INSERT INTO participants VALUES ('p-white','HUMAN','White',NULL,NULL,1)")
        v1.execSQL("INSERT INTO participants VALUES ('p-black','ENGINE','Engine','Fixture','1.0',2)")

        v1.execSQL(
            "INSERT INTO games VALUES ('g-standard','STANDARD','rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1','WHITE_WIN','CHECKMATE',10,11,12,1,600000,0,'600','p-white','p-black')",
        )
        v1.execSQL(
            "INSERT INTO games VALUES ('g-960','CHESS960','7k/8/8/8/8/8/8/RK2R3 w EA - 0 1',NULL,NULL,20,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)",
        )
        v1.execSQL("INSERT INTO game_headers VALUES ('g-standard','Event','Migration fixture',0)")
        v1.execSQL("INSERT INTO game_headers VALUES ('g-standard','X-Unknown','keep-me',1)")

        v1.execSQL("INSERT INTO game_nodes VALUES ('n-e4','g-standard',NULL,0,12,28,NULL,'e4')")
        v1.execSQL("INSERT INTO game_nodes VALUES ('n-e5','g-standard','n-e4',0,52,36,NULL,'e5')")
        v1.execSQL("INSERT INTO game_nodes VALUES ('n-c5','g-standard','n-e4',1,50,34,NULL,'c5')")
        v1.execSQL("INSERT INTO game_nodes VALUES ('n-nf3','g-standard','n-c5',0,6,21,NULL,'Nf3')")
        v1.execSQL("INSERT INTO game_node_comments VALUES ('c-root','g-standard',NULL,'ROOT',0,'root note')")
        v1.execSQL("INSERT INTO game_node_comments VALUES ('c-c5','g-standard','n-c5','TRAILING',0,'Sicilian')")
        v1.execSQL("INSERT INTO game_node_nags VALUES ('g-standard','n-c5',0,1)")
        v1.execSQL("INSERT INTO game_node_annotations VALUES ('g-standard','n-c5','eval','+0.20')")

        v1.execSQL(
            "INSERT INTO game_sources VALUES ('src-1','g-standard','CHESS_COM','external-42','https://example.invalid/42',13,14,'acct-1')",
        )
        v1.execSQL("INSERT INTO game_source_metadata VALUES ('src-1','remote-key','remote-value')")

        v1.execSQL("INSERT INTO reviews VALUES ('review-1','g-standard','fixture','FixtureEngine','1','default','COMPLETE',2,30,31,31)")
        v1.execSQL("INSERT INTO review_plies VALUES ('review-ply-1','g-standard','review-1','n-e4',20,NULL,52,36,NULL,'fixture',0.05,12,1000,10)")
        v1.execSQL("INSERT INTO review_heavy_analysis VALUES ('heavy-1','review-ply-1','uci-pv','e7e5',32)")

        v1.execSQL("INSERT INTO saved_positions VALUES ('saved-1','CHESS960','7k/8/8/8/8/8/8/RK2R3 w EA - 0 1','Castle','note',40,41)")
        v1.execSQL("INSERT INTO rating_events VALUES ('rating-1','p-white','GLICKO_2','STANDARD','rapid',50,1500.0,70.0,0.06,'g-standard')")
        v1.close()

        val v2 = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))

        assertEquals(2L, scalarLong(v2, "SELECT COUNT(*) FROM games"))
        assertEquals(4L, scalarLong(v2, "SELECT COUNT(*) FROM game_nodes"))
        assertEquals(2L, scalarLong(v2, "SELECT COUNT(*) FROM game_headers"))
        assertEquals(2L, scalarLong(v2, "SELECT COUNT(*) FROM game_node_comments"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM game_node_nags"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM game_node_annotations"))
        assertEquals(2L, scalarLong(v2, "SELECT COUNT(*) FROM participants"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM game_sources"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM game_source_metadata"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM reviews"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM review_plies"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM review_heavy_analysis"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM saved_positions"))
        assertEquals(1L, scalarLong(v2, "SELECT COUNT(*) FROM rating_events"))
        assertEquals("acct-1", scalarText(v2, "SELECT sourceAccountScope FROM game_sources WHERE id='src-1'"))
        assertTrue(scalarIsNull(v2, "SELECT contentFingerprint FROM games WHERE id='g-standard'"))
        assertEquals(0L, scalarLong(v2, "SELECT COUNT(*) FROM participant_external_identities"))

        val uniqueSourceIndex = scalarLong(
            v2,
            "SELECT COUNT(*) FROM pragma_index_list('game_sources') WHERE name='index_game_sources_sourceType_sourceAccountScope_externalGameId' AND \"unique\"=1",
        )
        assertEquals(1L, uniqueSourceIndex)

        assertEquals("keep-me", scalarText(v2, "SELECT value FROM game_headers WHERE gameId='g-standard' AND name='X-Unknown'"))
        assertEquals("remote-value", scalarText(v2, "SELECT value FROM game_source_metadata WHERE sourceId='src-1' AND key='remote-key'"))
        assertFalse(scalarIsNull(v2, "SELECT gameId FROM rating_events WHERE id='rating-1'"))
        v2.close()
    }

    @Test
    fun migrationRefusesAmbiguousPreexistingStrongSourceIdentityInsteadOfGuessing() = runBlocking {
        val v1 = helper.createDatabase(1)
        v1.execSQL(
            "INSERT INTO games VALUES ('g-a','STANDARD','rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',NULL,NULL,1,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)",
        )
        v1.execSQL(
            "INSERT INTO games VALUES ('g-b','STANDARD','rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',NULL,NULL,2,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)",
        )
        v1.execSQL("INSERT INTO game_sources VALUES ('source-a','g-a','LICHESS','same-external-id',NULL,1,NULL,'same-account')")
        v1.execSQL("INSERT INTO game_sources VALUES ('source-b','g-b','LICHESS','same-external-id',NULL,2,NULL,'same-account')")
        v1.close()

        val error = runCatching {
            helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2)).close()
        }.exceptionOrNull()

        assertNotNull(error)
    }

    private fun scalarLong(connection: androidx.sqlite.SQLiteConnection, sql: String): Long =
        connection.prepare(sql).use { statement ->
            assertTrue(statement.step())
            statement.getLong(0)
        }

    private fun scalarText(connection: androidx.sqlite.SQLiteConnection, sql: String): String =
        connection.prepare(sql).use { statement ->
            assertTrue(statement.step())
            statement.getText(0)
        }

    private fun scalarIsNull(connection: androidx.sqlite.SQLiteConnection, sql: String): Boolean =
        connection.prepare(sql).use { statement ->
            assertTrue(statement.step())
            statement.isNull(0)
        }
}

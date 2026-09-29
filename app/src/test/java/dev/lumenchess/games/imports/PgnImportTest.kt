package dev.lumenchess.games.imports

import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.PersistedTermination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PgnImportTest {
    private val chessCom = """
        [Event "Live Chess"]
        [Site "Chess.com"]
        [Date "2024.03.15"]
        [Round "-"]
        [White "timelord"]
        [Black "agreeable"]
        [Result "1-0"]
        [CurrentPosition "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq -"]
        [Timezone "UTC"]
        [UTCDate "2024.03.15"]
        [UTCTime "18:22:05"]
        [WhiteElo "1500"]
        [BlackElo "1480"]
        [TimeControl "180+2"]
        [Termination "timelord won by checkmate"]
        [Link "https://www.chess.com/game/live/104857600"]

        1. e4 {[%clk 0:02:59.1]} 1... e5 {[%clk 0:02:58.3]} 2. Qh5 {[%clk 0:02:57]} 2... Nc6 {[%clk 0:02:55]}
        3. Bc4 {[%clk 0:02:54]} 3... Nf6 {[%clk 0:02:50]} 4. Qxf7# {[%clk 0:02:53]} 1-0
    """.trimIndent()

    private val lichess = """
        [Event "Rated Blitz game"]
        [Site "https://lichess.org/AbCdEfGh"]
        [Date "2024.05.01"]
        [White "agreeable"]
        [Black "timelord"]
        [Result "0-1"]
        [UTCDate "2024.05.01"]
        [UTCTime "09:00:00"]
        [TimeControl "300+0"]
        [Termination "Normal"]

        1. f3 e5 2. g4 Qh4# 0-1
    """.trimIndent()

    private val plain = """
        [Event "Club night"]
        [White "A. Player"]
        [Black "B. Player"]
        [Result "1/2-1/2"]
        [TimeControl "-"]

        1. e4 e5 2. Nf3 Nc6 1/2-1/2
    """.trimIndent()

    @Test fun eachGameIsFiledUnderTheSiteItCameFrom() {
        val batch = PgnImport.parse(listOf(chessCom, lichess, plain).joinToString("\n\n"), importedAtEpochMillis = 7L)
        assertEquals(0, batch.unreadable)
        assertEquals(listOf(GameSourceType.CHESS_COM, GameSourceType.LICHESS, GameSourceType.PGN_IMPORT), batch.candidates.map { it.sourceType })

        val (fromChessCom, fromLichess, fromFile) = batch.candidates
        assertEquals("live/104857600", fromChessCom.externalGameId)
        assertEquals("https://www.chess.com/game/live/104857600", fromChessCom.externalUrl)
        assertEquals("AbCdEfGh", fromLichess.externalGameId)
        assertEquals("https://lichess.org/AbCdEfGh", fromLichess.externalUrl)
        assertNull(fromFile.externalGameId, "plain PGN is identified by its content when stored")
        assertEquals("timelord", fromChessCom.whiteName)
        assertEquals(7L, fromChessCom.metadata.importedAtEpochMillis)
    }

    @Test fun clockCommentsAndResultsSurviveParsing() {
        val candidate = PgnImport.parse(chessCom, 0L).candidates.single()
        assertEquals(7, candidate.tree.mainline().size)
        assertEquals(PersistedTermination.CHECKMATE, candidate.metadata.termination)
        assertEquals(180_000L, candidate.metadata.timeControl?.baseMillis)
        assertEquals(2_000L, candidate.metadata.timeControl?.incrementMillis)
        assertEquals(java.time.Instant.parse("2024-03-15T18:22:05Z").toEpochMilli(), candidate.metadata.playedAtEpochMillis)
    }

    @Test fun terminationReadsTheReasonNotTheUsername() {
        val tree = PgnImport.parse(plain, 0L).candidates.single().tree
        assertEquals(PersistedTermination.TIMEOUT, PgnImport.termination("agreeable won on time", tree))
        assertEquals(PersistedTermination.RESIGNATION, PgnImport.termination("timelord won by resignation", tree))
        assertEquals(PersistedTermination.AGREEMENT, PgnImport.termination("Game drawn by agreement", tree))
        assertEquals(PersistedTermination.THREEFOLD_REPETITION, PgnImport.termination("Game drawn by repetition", tree))
        assertEquals(PersistedTermination.INSUFFICIENT_MATERIAL, PgnImport.termination("Game drawn by timeout vs insufficient material", tree))
        assertEquals(PersistedTermination.FIFTY_MOVE_RULE, PgnImport.termination("Game drawn by 50-move rule", tree))
        assertEquals(PersistedTermination.TIMEOUT, PgnImport.termination("Time forfeit", tree))
    }

    @Test fun lichessNormalTerminationIsResolvedFromTheFinalPosition() {
        assertEquals(PersistedTermination.CHECKMATE, PgnImport.parse(lichess, 0L).candidates.single().metadata.termination)
    }

    @Test fun oneBadGameIsCountedWithoutSinkingTheBatch() {
        val broken = "[Event \"Broken\"]\n[Result \"*\"]\n\n1. e4 e5 2. Ke3?? Qxz9 *"
        val batch = PgnImport.parse(listOf(chessCom, broken, lichess).joinToString("\n\n"), 0L)
        assertEquals(2, batch.candidates.size)
        assertEquals(1, batch.unreadable)
    }

    @Test fun tagLikeTextInsideCommentsDoesNotSplitAGame() {
        val commented = "[Event \"x\"]\n[Result \"*\"]\n\n1. e4 {a note\n[not a tag]\n still the note} e5 *"
        assertEquals(1, PgnImport.split(commented).size)
        assertEquals(3, PgnImport.split(listOf(chessCom, lichess, plain).joinToString("\n")).size)
    }

    @Test fun timeControlsBecomeMilliseconds() {
        assertEquals(600_000L, PgnImport.timeControl("600")?.baseMillis)
        assertEquals(0L, PgnImport.timeControl("600")?.incrementMillis)
        assertEquals(0L, PgnImport.timeControl("-")?.baseMillis)
        assertEquals("1/86400", PgnImport.timeControl("1/86400")?.raw)
        assertNull(PgnImport.timeControl("1/86400")?.baseMillis)
        assertNull(PgnImport.timeControl("?"))
    }
}

class OnlineGameSourcesTest {
    private val game = { n: Int -> "[Event \"g$n\"]\n[Site \"Chess.com\"]\n[Result \"*\"]\n\n1. e4 *" }

    @Test fun chessComTakesTheNewestMonthsUntilItHasEnoughGames() {
        val requested = ArrayList<String>()
        val http = HttpGet { url, _ ->
            requested += url
            when {
                url.endsWith("/archives") -> HttpResult.Ok(
                    """{"archives":["https://api.chess.com/pub/player/timelord/games/2024/01",""" +
                        """"https://api.chess.com/pub/player/timelord/games/2024/02",""" +
                        """"https://api.chess.com/pub/player/timelord/games/2024/03"]}""",
                )
                url.endsWith("2024/03/pgn") -> HttpResult.Ok((1..3).joinToString("\n\n") { game(30 + it) })
                url.endsWith("2024/02/pgn") -> HttpResult.Ok((1..3).joinToString("\n\n") { game(20 + it) })
                else -> error("unexpected $url")
            }
        }
        val pgn = OnlineGameSources.fetchRecentPgn(OnlineSite.CHESS_COM, " TimeLord ", http, maxGames = 5)
        val events = PgnImport.split(pgn).map { Regex("g\\d+").find(it)!!.value }
        assertEquals(listOf("g22", "g23", "g31", "g32", "g33"), events, "the five newest games, oldest first")
        assertTrue(requested.none { it.endsWith("2024/01/pgn") }, "older months are not downloaded once there are enough games")
        assertTrue(requested.first().contains("/player/timelord/"), "chess.com usernames are case-insensitive")
    }

    @Test fun siteErrorsBecomePlainMessages() {
        fun failing(code: Int) = HttpGet { _, _ -> HttpResult.Status(code) }
        assertEquals(
            "No Lichess account named ghost",
            assertFailsWith<OnlineImportException> { OnlineGameSources.fetchRecentPgn(OnlineSite.LICHESS, "ghost", failing(404)) }.message,
        )
        assertTrue(
            assertFailsWith<OnlineImportException> { OnlineGameSources.fetchRecentPgn(OnlineSite.CHESS_COM, "someone", failing(429)) }
                .message!!.contains("limiting"),
        )
        assertTrue(
            assertFailsWith<OnlineImportException> {
                OnlineGameSources.fetchRecentPgn(OnlineSite.LICHESS, "someone", HttpGet { _, _ -> HttpResult.Failed("dns") })
            }.message!!.startsWith("Could not reach Lichess"),
        )
    }

    @Test fun usernamesAreValidatedBeforeAnyRequest() {
        val http = HttpGet { _, _ -> error("no request expected") }
        assertFailsWith<OnlineImportException> { OnlineGameSources.fetchRecentPgn(OnlineSite.CHESS_COM, "a/b", http) }
        assertFailsWith<OnlineImportException> { OnlineGameSources.fetchRecentPgn(OnlineSite.LICHESS, "", http) }
        assertEquals("DrNykterstein", OnlineGameSources.normalizedUsername(OnlineSite.LICHESS, "@DrNykterstein"))
    }

    @Test fun lichessGamesArriveOldestFirst() {
        val http = HttpGet { url, accept ->
            assertTrue(url.startsWith("https://lichess.org/api/games/user/someone?max=100"))
            assertEquals("application/x-chess-pgn", accept)
            HttpResult.Ok((3 downTo 1).joinToString("\n\n") { game(it) })
        }
        val events = PgnImport.split(OnlineGameSources.fetchRecentPgn(OnlineSite.LICHESS, "someone", http))
            .map { Regex("g\\d+").find(it)!!.value }
        assertEquals(listOf("g1", "g2", "g3"), events)
    }
}

package dev.lumenchess.player

import dev.lumenchess.analysis.insights.GameKind
import dev.lumenchess.analysis.insights.GameOrigin
import dev.lumenchess.analysis.insights.Outcome
import dev.lumenchess.analysis.rating.RatingSystem
import dev.lumenchess.analysis.rating.TimeClass
import dev.lumenchess.analysis.review.ReviewPreset
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.persistence.GamePersistenceMetadata
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.LibraryEntry
import dev.lumenchess.data.persistence.PersistentGameId
import dev.lumenchess.data.persistence.TimeControlMetadata
import dev.lumenchess.player.lichess.LichessClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteRatingsTest {
    @Test fun chessComStatsReadTheLastRatingOfEachPool() {
        val json = """{"chess_bullet":{"last":{"rating":1210}},"chess_blitz":{"last":{"rating":1344}},"chess_rapid":{"last":{"rating":1502}},"chess960_daily":{"last":{"rating":1400}}}"""
        val ratings = RemoteRatingsClient.parseChessCom(json, 5L)
        assertEquals(RemoteRatings(1_210, 1_344, 1_502, 1_400, 5L), ratings)
        assertEquals(1_344, ratings.forPool(Variant.STANDARD, TimeClass.BLITZ))
        assertEquals(1_400, ratings.forPool(Variant.CHESS960, TimeClass.RAPID))
    }

    @Test fun lichessPerfsSkipPoolsWithoutGames() {
        val json = """{"perfs":{"bullet":{"games":0,"rating":1500},"blitz":{"games":120,"rating":1790},"classical":{"games":3,"rating":1880}}}"""
        val ratings = RemoteRatingsClient.parseLichess(json, 0L)
        assertNull(ratings.bullet)
        assertEquals(1_790, ratings.blitz)
        assertEquals(1_880, ratings.rapid, "classical stands in when there is no rapid rating")
    }

    @Test fun cachedRatingsRoundTrip() {
        val ratings = RemoteRatings(null, 1_300, 1_450, null, 99L)
        assertEquals(ratings, RemoteRatings.decode(ratings.encode()))
        assertNull(RemoteRatings.decode("garbage"))
    }
}

class PlayerSettingsCodecTest {
    @Test fun everyFieldRoundTripsAndBadValuesFallBackToDefaults() {
        val settings = PlayerSettings(
            ratingSystem = RatingSystem.FIDE_STRICT,
            matchSource = MatchSource.LICHESS,
            matchRange = 150,
            chessComUsername = "Hikaru",
            lichessUsername = "DrNykterstein",
            chessComRatings = RemoteRatings(3_000, 3_100, 2_900, null, 1L),
            autoSync = true,
            lastLichessSyncEpochMillis = 42L,
            autoReview = true,
            reviewPreset = ReviewPreset.DEEP,
            lichessToken = "lip_token",
        )
        assertEquals(settings, PlayerSettingsCodec.decode(PlayerSettingsCodec.encode(settings)))
        assertEquals(PlayerSettings(), PlayerSettingsCodec.decode(mapOf("rating_system" to "NOPE", "match_range" to "x")))
        assertEquals(setOf("hikaru", "drnykterstein"), settings.identities)
    }
}

class PlayerGamesDescribeTest {
    private fun entry(
        white: String,
        black: String,
        result: String?,
        sources: Set<GameSourceType> = setOf(GameSourceType.LOCAL),
        tags: Map<String, String> = emptyMap(),
        control: TimeControlMetadata? = TimeControlMetadata(180_000L, 2_000L),
    ) = LibraryEntry(
        id = PersistentGameId("g"),
        variant = Variant.STANDARD,
        result = result,
        metadata = GamePersistenceMetadata(createdAtEpochMillis = 10L, rated = true, timeControl = control),
        whiteName = null, blackName = null, whiteEngineName = null, blackEngineName = null,
        headers = mapOf("White" to white, "Black" to black) + tags,
        sources = sources,
        latestReviewState = null,
        isFavorite = false,
        isProtected = false,
    )

    @Test fun localPlayGamesAreEngineGamesFromTheYouSide() {
        val game = PlayerGamesLoader.describe(entry("Stockfish 18", "You", "BLACK_WIN", tags = mapOf("WhiteElo" to "1650")), emptySet())!!
        assertEquals(GameKind.ENGINE, game.kind)
        assertEquals(Color.BLACK, game.userColor)
        assertEquals(Outcome.WIN, game.outcome)
        assertEquals(1_650, game.opponentRating)
        assertEquals(TimeClass.BLITZ, game.timeClass)
    }

    @Test fun siteGamesNeedALinkedNameAndArenaGamesHaveNoSide() {
        val site = entry("hikaru", "someone", "DRAW", setOf(GameSourceType.CHESS_COM), mapOf("BlackElo" to "2800"), control = null)
        assertNull(PlayerGamesLoader.describe(site, emptySet()), "not the user's game without a linked name")
        val mine = PlayerGamesLoader.describe(site, setOf("hikaru"))!!
        assertEquals(GameKind.HUMAN, mine.kind)
        assertEquals(GameOrigin.CHESS_COM, mine.origin)
        assertEquals(Outcome.DRAW, mine.outcome)
        assertEquals(2_800, mine.opponentRating)

        val arena = PlayerGamesLoader.describe(entry("Stockfish 18", "Reckless 0.9.0", "WHITE_WIN", setOf(GameSourceType.ENGINE_ARENA)), emptySet())!!
        assertEquals(GameKind.ARENA, arena.kind)
        assertNull(arena.userColor)
        assertNull(arena.outcome)
    }

    @Test fun pgnTimeControlTagsGiveTheTimeClass() {
        val bullet = entry("You", "x", "WHITE_WIN", control = null, tags = mapOf("TimeControl" to "60+0"))
        assertEquals(TimeClass.BULLET, PlayerGamesLoader.describe(bullet, emptySet())!!.timeClass)
    }
}

class LichessParsingTest {
    @Test fun explorerMovesAndCloudEvalParse() {
        val explorer = LichessClient.parseExplorer(
            """{"white":10,"draws":5,"black":3,"moves":[{"uci":"e2e4","san":"e4","white":6,"draws":2,"black":1,"averageRating":2010}],"opening":{"eco":"A00","name":"Start"}}""",
            "lichess",
        )
        assertEquals(1, explorer.moves.size)
        assertEquals(9L, explorer.moves.single().games)
        assertEquals("Start", explorer.openingName)
        val cloud = LichessClient.parseCloudEval("""{"pvs":[{"moves":"e2e4 e7e5","cp":20},{"moves":"d2d4","mate":-3}]}""")
        assertEquals(listOf("e2e4", "e7e5"), cloud[0].moves)
        assertEquals(20, cloud[0].centipawns)
        assertEquals(-3, cloud[1].mate)
    }
}

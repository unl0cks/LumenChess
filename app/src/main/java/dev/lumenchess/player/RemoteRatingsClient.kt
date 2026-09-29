package dev.lumenchess.player

import dev.lumenchess.games.imports.HttpGet
import dev.lumenchess.games.imports.HttpResult
import dev.lumenchess.games.imports.OnlineGameSources
import dev.lumenchess.games.imports.OnlineImportException
import dev.lumenchess.games.imports.OnlineSite
import org.json.JSONObject

/**
 * Current ratings from the sites' public, no-login APIs. Only the username is sent. Results are
 * cached in [PlayerSettings] so Match Your Elo keeps working offline.
 */
object RemoteRatingsClient {
    fun fetch(site: OnlineSite, username: String, http: HttpGet, nowEpochMillis: Long = System.currentTimeMillis()): RemoteRatings {
        val name = OnlineGameSources.normalizedUsername(site, username)
            ?: throw OnlineImportException("\"${username.trim()}\" is not a valid ${site.label} username")
        return when (site) {
            OnlineSite.CHESS_COM -> parseChessCom(body(site, name, http.get("https://api.chess.com/pub/player/${name.lowercase()}/stats", "application/json")), nowEpochMillis)
            OnlineSite.LICHESS -> parseLichess(body(site, name, http.get("https://lichess.org/api/user/$name", "application/json")), nowEpochMillis)
        }
    }

    /** `chess_bullet.last.rating` etc.; Chess960 from the daily 960 pool, the only one published. */
    fun parseChessCom(json: String, nowEpochMillis: Long): RemoteRatings {
        val root = JSONObject(json)
        fun rating(key: String): Int? = root.optJSONObject(key)?.optJSONObject("last")?.optInt("rating", 0)?.takeIf { it > 0 }
        return RemoteRatings(
            bullet = rating("chess_bullet"),
            blitz = rating("chess_blitz"),
            rapid = rating("chess_rapid"),
            chess960 = rating("chess960_daily"),
            fetchedAtEpochMillis = nowEpochMillis,
        )
    }

    /** `perfs.<pool>.rating`; provisional ratings are still used (they are the best estimate). */
    fun parseLichess(json: String, nowEpochMillis: Long): RemoteRatings {
        val perfs = JSONObject(json).optJSONObject("perfs") ?: JSONObject()
        fun rating(key: String): Int? = perfs.optJSONObject(key)?.let { perf ->
            perf.optInt("rating", 0).takeIf { it > 0 && perf.optInt("games", 0) > 0 }
        }
        return RemoteRatings(
            bullet = rating("bullet"),
            blitz = rating("blitz"),
            rapid = rating("rapid") ?: rating("classical"),
            chess960 = rating("chess960"),
            fetchedAtEpochMillis = nowEpochMillis,
        )
    }

    private fun body(site: OnlineSite, name: String, result: HttpResult): String = when (result) {
        is HttpResult.Ok -> result.body
        is HttpResult.Status -> throw OnlineImportException(
            when (result.code) {
                404 -> "No ${site.label} account named $name"
                429 -> "${site.label} is limiting requests right now. Try again in a minute."
                else -> "${site.label} could not provide ratings (error ${result.code})"
            },
        )
        is HttpResult.Failed -> throw OnlineImportException("Could not reach ${site.label}. Check the connection and try again.")
    }
}

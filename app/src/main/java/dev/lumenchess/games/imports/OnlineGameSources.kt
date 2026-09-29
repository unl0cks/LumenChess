package dev.lumenchess.games.imports

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface HttpResult {
    data class Ok(val body: String) : HttpResult
    data class Status(val code: Int) : HttpResult
    data class Failed(val reason: String) : HttpResult
}

fun interface HttpGet {
    fun get(url: String, accept: String): HttpResult
}

enum class OnlineSite(val label: String) { CHESS_COM("Chess.com"), LICHESS("Lichess") }

class OnlineImportException(message: String) : Exception(message)

/**
 * Downloads a player's recent games as PGN from the sites' public, no-login game exports. Nothing
 * is sent except the username in the request path; no account, token or password is involved.
 */
object OnlineGameSources {
    const val MAX_GAMES = 100
    private const val MAX_CHESS_COM_MONTHS = 12
    private val CHESS_COM_USER = Regex("[A-Za-z0-9_-]{3,25}")
    private val LICHESS_USER = Regex("[A-Za-z0-9_-]{2,30}")
    private val CHESS_COM_ARCHIVE = Regex("""https://api\.chess\.com/pub/player/[^"/]+/games/\d{4}/\d{2}""")
    private const val PGN = "application/x-chess-pgn"

    fun normalizedUsername(site: OnlineSite, input: String): String? {
        val name = input.trim().removePrefix("@")
        val pattern = if (site == OnlineSite.CHESS_COM) CHESS_COM_USER else LICHESS_USER
        return name.takeIf(pattern::matches)
    }

    /** PGN of up to [maxGames] of the player's most recent games, oldest first. */
    fun fetchRecentPgn(site: OnlineSite, username: String, http: HttpGet, maxGames: Int = MAX_GAMES): String {
        val name = normalizedUsername(site, username)
            ?: throw OnlineImportException("\"${username.trim()}\" is not a valid ${site.label} username")
        return when (site) {
            OnlineSite.CHESS_COM -> chessCom(name.lowercase(), http, maxGames)
            OnlineSite.LICHESS -> lichess(name, http, maxGames)
        }
    }

    private fun chessCom(name: String, http: HttpGet, maxGames: Int): String {
        val index = body(OnlineSite.CHESS_COM, name, http.get("https://api.chess.com/pub/player/$name/games/archives", "application/json"))
        val months = CHESS_COM_ARCHIVE.findAll(index).map { it.value }.distinct().toList()
        val newestGamesFirst = ArrayList<String>()
        for (month in months.asReversed().take(MAX_CHESS_COM_MONTHS)) {
            val pgn = body(OnlineSite.CHESS_COM, name, http.get("$month/pgn", PGN))
            newestGamesFirst += PgnImport.split(pgn).asReversed()
            if (newestGamesFirst.size >= maxGames) break
        }
        return newestGamesFirst.take(maxGames).asReversed().joinToString("\n\n")
    }

    private fun lichess(name: String, http: HttpGet, maxGames: Int): String {
        val url = "https://lichess.org/api/games/user/$name?max=$maxGames&moves=true&tags=true" +
            "&clocks=false&evals=false&opening=false&literate=false"
        return PgnImport.split(body(OnlineSite.LICHESS, name, http.get(url, PGN))).asReversed().joinToString("\n\n")
    }

    private fun body(site: OnlineSite, name: String, result: HttpResult): String = when (result) {
        is HttpResult.Ok -> result.body
        is HttpResult.Status -> throw OnlineImportException(
            when (result.code) {
                404 -> "No ${site.label} account named $name"
                429 -> "${site.label} is limiting requests right now. Try again in a minute."
                else -> "${site.label} could not provide the games (error ${result.code})"
            },
        )
        is HttpResult.Failed -> throw OnlineImportException("Could not reach ${site.label}. Check the connection and try again.")
    }
}

/** Plain java.net client: bounded size and time, identifies the app, follows no cross-host tricks. */
class UrlConnectionHttpGet(
    private val userAgent: String,
    private val maxBytes: Int = 16 * 1024 * 1024,
) : HttpGet {
    override fun get(url: String, accept: String): HttpResult {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (error: IOException) {
            return HttpResult.Failed(error.message ?: "connection failed")
        }
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", userAgent)
            val code = connection.responseCode
            if (code !in 200..299) return HttpResult.Status(code)
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (out.size() + read > maxBytes) return HttpResult.Failed("response too large")
                    out.write(buffer, 0, read)
                }
                HttpResult.Ok(out.toString(Charsets.UTF_8.name()))
            }
        } catch (error: IOException) {
            HttpResult.Failed(error.message ?: "connection failed")
        } finally {
            connection.disconnect()
        }
    }
}

package dev.lumenchess.player.lichess

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import dev.lumenchess.player.PlayerSettingsRepository
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One continuation in the Lichess opening explorer, results from White's side. */
data class LichessExplorerMove(
    val uci: String,
    val san: String,
    val white: Long,
    val draws: Long,
    val black: Long,
    val averageRating: Int?,
) {
    val games: Long get() = white + draws + black
}

data class LichessExplorer(
    val database: String,
    val white: Long,
    val draws: Long,
    val black: Long,
    val moves: List<LichessExplorerMove>,
    val openingName: String?,
)

/** A Lichess cloud evaluation line: UCI moves and a White-relative score. */
data class LichessCloudLine(val moves: List<String>, val centipawns: Int?, val mate: Int?)

class LichessException(message: String) : IOException(message)

/**
 * Lichess online enrichment: opening explorer and cloud evaluations. Requests are strictly serial
 * with at least a second between them (Lichess's API guidance), a 429 pauses everything for a
 * minute, and every answer is cached on disk first, so the same position is never asked twice.
 * The OAuth token (optional) is sent only to lichess.org hosts.
 */
object LichessClient {
    private val mutex = Mutex()
    private var lastRequestAt = 0L
    private var pausedUntil = 0L
    private const val SPACING_MILLIS = 1_100L
    private const val RATE_LIMIT_PAUSE_MILLIS = 60_000L
    private const val CACHE_DAYS = 30L

    suspend fun explorer(context: Context, fen: String, database: String = "lichess"): LichessExplorer {
        val query = when (database) {
            "masters" -> "https://explorer.lichess.ovh/masters?fen=${encode(fen)}&moves=12&topGames=0"
            else -> "https://explorer.lichess.ovh/lichess?variant=standard&fen=${encode(fen)}&moves=12&topGames=0&recentGames=0" +
                "&speeds=blitz,rapid,classical&ratings=1600,1800,2000,2200,2500"
        }
        val json = cachedGet(context, "explorer-$database", query)
        return parseExplorer(json, database)
    }

    suspend fun cloudEval(context: Context, fen: String, multiPv: Int = 3): List<LichessCloudLine>? {
        val url = "https://lichess.org/api/cloud-eval?fen=${encode(fen)}&multiPv=$multiPv"
        val json = try {
            cachedGet(context, "cloud", url)
        } catch (error: LichessException) {
            if (error.message == NOT_FOUND) return null else throw error
        }
        return parseCloudEval(json)
    }

    fun parseExplorer(json: String, database: String): LichessExplorer {
        val root = JSONObject(json)
        val moves = root.optJSONArray("moves")
        val list = ArrayList<LichessExplorerMove>()
        if (moves != null) {
            for (i in 0 until moves.length()) {
                val move = moves.getJSONObject(i)
                list += LichessExplorerMove(
                    uci = move.getString("uci"),
                    san = move.getString("san"),
                    white = move.optLong("white"),
                    draws = move.optLong("draws"),
                    black = move.optLong("black"),
                    averageRating = move.optInt("averageRating", 0).takeIf { it > 0 },
                )
            }
        }
        return LichessExplorer(
            database = database,
            white = root.optLong("white"),
            draws = root.optLong("draws"),
            black = root.optLong("black"),
            moves = list,
            openingName = root.optJSONObject("opening")?.optString("name")?.takeIf { it.isNotBlank() },
        )
    }

    fun parseCloudEval(json: String): List<LichessCloudLine> {
        val pvs = JSONObject(json).optJSONArray("pvs") ?: return emptyList()
        return (0 until pvs.length()).map { i ->
            val pv = pvs.getJSONObject(i)
            LichessCloudLine(
                moves = pv.optString("moves").split(' ').filter { it.isNotBlank() },
                centipawns = if (pv.has("cp")) pv.getInt("cp") else null,
                mate = if (pv.has("mate")) pv.getInt("mate") else null,
            )
        }
    }

    private suspend fun cachedGet(context: Context, bucket: String, url: String): String {
        val file = File(File(context.cacheDir, "lichess/$bucket"), sha256(url))
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_DAYS * 86_400_000L) {
            return withContext(Dispatchers.IO) { file.readText() }
        }
        val token = PlayerSettingsRepository.from(context).current().lichessToken
        val body = serial { get(url, token) }
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(body)
        }
        return body
    }

    private suspend fun <T> serial(block: suspend () -> T): T = mutex.withLock {
        val now = System.currentTimeMillis()
        if (now < pausedUntil) throw LichessException("Lichess asked us to slow down. Try again in a minute.")
        val wait = lastRequestAt + SPACING_MILLIS - now
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }

    private suspend fun get(url: String, token: String?): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "LumenChess (Android)")
            val host = connection.url.host
            if (token != null && (host == "lichess.org" || host.endsWith(".lichess.ovh"))) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            when (val code = connection.responseCode) {
                in 200..299 -> connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                404 -> throw LichessException(NOT_FOUND)
                429 -> {
                    pausedUntil = System.currentTimeMillis() + RATE_LIMIT_PAUSE_MILLIS
                    throw LichessException("Lichess asked us to slow down. Try again in a minute.")
                }
                401 -> throw LichessException("Lichess needs you to sign in (Settings › Accounts & Sync).")
                else -> throw LichessException("Lichess could not answer (error $code).")
            }
        } catch (error: LichessException) {
            throw error
        } catch (error: IOException) {
            throw LichessException("Could not reach Lichess. Check the connection.")
        } finally {
            connection.disconnect()
        }
    }

    internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    const val NOT_FOUND = "not-found"
}

/**
 * Lichess OAuth2 with PKCE: no client secret, no password. The browser shows Lichess's own consent
 * page; the app receives a one-time code on dev.lumenchess://oauth/lichess and exchanges it for a
 * token with the verifier that never left the device.
 */
object LichessAuth {
    const val REDIRECT = "dev.lumenchess://oauth/lichess"
    private const val CLIENT_ID = "lumenchess"
    private const val SCOPE = "preference:read"
    private const val PREFERENCES = "lumen_lichess_oauth"

    fun begin(context: Context) {
        val random = SecureRandom()
        val verifier = base64Url(ByteArray(48).also(random::nextBytes))
        val state = base64Url(ByteArray(16).also(random::nextBytes))
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("verifier", verifier).putString("state", state).apply()
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = "https://lichess.org/oauth?response_type=code&client_id=$CLIENT_ID" +
            "&redirect_uri=${LichessClient.encode(REDIRECT)}&code_challenge_method=S256&code_challenge=$challenge" +
            "&scope=${LichessClient.encode(SCOPE)}&state=$state"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun isCallback(uri: Uri?): Boolean = uri != null && uri.scheme == "dev.lumenchess" && uri.host == "oauth" && uri.path == "/lichess"

    /** Completes sign-in from the redirect. Returns an error message, or null on success. */
    suspend fun complete(context: Context, uri: Uri): String? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val verifier = preferences.getString("verifier", null) ?: return "Sign-in was not started from this app."
        val expectedState = preferences.getString("state", null)
        preferences.edit().clear().apply()
        uri.getQueryParameter("error")?.let { return "Lichess sign-in was cancelled." }
        if (uri.getQueryParameter("state") != expectedState) return "Sign-in could not be verified. Try again."
        val code = uri.getQueryParameter("code") ?: return "Lichess did not return a sign-in code."
        return try {
            val token = withContext(Dispatchers.IO) { exchange(code, verifier) }
            PlayerSettingsRepository.from(context).update { it.copy(lichessToken = token) }
            null
        } catch (error: IOException) {
            error.message ?: "Could not finish Lichess sign-in."
        }
    }

    private fun exchange(code: String, verifier: String): String {
        val connection = URL("https://lichess.org/api/token").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val form = listOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "code_verifier" to verifier,
                "redirect_uri" to REDIRECT,
                "client_id" to CLIENT_ID,
            ).joinToString("&") { (key, value) -> "$key=${LichessClient.encode(value)}" }
            connection.outputStream.use { it.write(form.toByteArray()) }
            if (connection.responseCode !in 200..299) throw LichessException("Lichess refused the sign-in (error ${connection.responseCode}).")
            val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            return JSONObject(body).optString("access_token").takeIf { it.isNotBlank() }
                ?: throw LichessException("Lichess returned no token.")
        } finally {
            connection.disconnect()
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

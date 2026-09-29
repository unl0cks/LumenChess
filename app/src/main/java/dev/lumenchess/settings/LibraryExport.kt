package dev.lumenchess.settings

import android.content.Context
import android.net.Uri
import dev.lumenchess.core.chess.Pgn
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.PersistentGameId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes every game in the library to one PGN file the user picked (Storage Access Framework). */
internal object LibraryExport {
    suspend fun exportAll(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val games = AppData.games(context)
        val ids = AppData.database(context).gameDao().listGames().map { it.id }
        var written = 0
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the file")
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            for (id in ids) {
                val game = runCatching { games.loadGame(PersistentGameId(id)) }.getOrNull() ?: continue
                writer.write(Pgn.serialize(game.tree).trimEnd())
                writer.write("\n\n")
                written += 1
            }
        }
        written
    }
}

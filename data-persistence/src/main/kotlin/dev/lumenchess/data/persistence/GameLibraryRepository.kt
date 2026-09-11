package dev.lumenchess.data.persistence

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.lumenchess.core.chess.Variant

class GameLibraryRepository(private val database: LumenDatabase) {
    /** Three bounded metadata queries in one snapshot; never loads a canonical move tree. */
    suspend fun page(query: LibraryQuery = LibraryQuery(), cursor: LibraryCursor? = null, limit: Int = 40): LibraryPage {
        require(limit > 0) { "Page size must be positive" }
        val pageSize = limit.coerceAtMost(100)
        val source = when (query.filter) {
            LibraryFilter.ALL, LibraryFilter.FAVORITES -> null
            LibraryFilter.IMPORTED -> GameSourceType.PGN_IMPORT
            LibraryFilter.BRANCHES -> GameSourceType.BRANCH
            else -> GameSourceType.valueOf(query.filter.name)
        }
        return database.withReadTransaction {
            val dao = database.gameLibraryDao()
            val rows = dao.page(source?.name, query.filter == LibraryFilter.FAVORITES, query.search.trim(), cursor?.createdAtEpochMillis, cursor?.id?.value, pageSize + 1)
            val cards = rows.take(pageSize)
            if (cards.isEmpty()) return@withReadTransaction LibraryPage(emptyList(), null)
            val ids = cards.map { it.game.id }
            val sources = dao.sources(ids).groupBy { it.gameId }
            val headers = dao.headers(ids).groupBy { it.gameId }
            val entries = cards.map { row ->
                val g = row.game
                LibraryEntry(
                    id = PersistentGameId(g.id), variant = Variant.valueOf(g.variant), result = g.result,
                    metadata = GamePersistenceMetadata(
                        createdAtEpochMillis = g.createdAtEpochMillis, importedAtEpochMillis = g.importedAtEpochMillis,
                        playedAtEpochMillis = g.playedAtEpochMillis, rated = g.rated,
                        termination = g.termination?.let(PersistedTermination::valueOf),
                        timeControl = if (g.timeControlBaseMillis == null && g.timeControlIncrementMillis == null && g.timeControlRaw == null) null
                            else TimeControlMetadata(g.timeControlBaseMillis, g.timeControlIncrementMillis, g.timeControlRaw),
                    ),
                    whiteName = row.whiteName, blackName = row.blackName,
                    whiteEngineName = row.whiteEngineName, blackEngineName = row.blackEngineName,
                    headers = headers[g.id].orEmpty().associate { it.name to it.value },
                    sources = sources[g.id].orEmpty().map { GameSourceType.valueOf(it.sourceType) }.toSet(),
                    latestReviewState = row.latestReviewState?.let(ReviewState::valueOf),
                    isFavorite = row.isFavorite, isProtected = row.isProtected,
                )
            }
            LibraryPage(entries, if (rows.size > pageSize) cards.last().game.let { LibraryCursor(it.createdAtEpochMillis, PersistentGameId(it.id)) } else null)
        }
    }

    suspend fun setFavorite(id: PersistentGameId, value: Boolean): Boolean = updateFlags(id) {
        database.gameLibraryDao().setFavorite(id.value, value)
    }

    suspend fun setProtected(id: PersistentGameId, value: Boolean): Boolean = updateFlags(id) {
        database.gameLibraryDao().setProtected(id.value, value)
    }

    /** Explicit user deletion; active Play/Arena ownership must be checked by the caller. */
    suspend fun delete(id: PersistentGameId): Boolean = database.gameDao().deleteGame(id.value) > 0

    private suspend fun updateFlags(id: PersistentGameId, update: suspend () -> Int): Boolean = database.withWriteTransaction {
        if (database.gameDao().gameById(id.value) == null) return@withWriteTransaction false
        database.gameLibraryDao().insertFlags(GameLibraryFlagsEntity(id.value))
        update() > 0
    }
}

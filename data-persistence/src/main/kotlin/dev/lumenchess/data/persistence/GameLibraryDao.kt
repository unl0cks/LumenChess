package dev.lumenchess.data.persistence

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

data class LibraryCardRow(
    @Embedded val game: GameEntity,
    val whiteName: String?, val blackName: String?,
    val whiteEngineName: String?, val blackEngineName: String?,
    val latestReviewState: String?,
    val isFavorite: Boolean, val isProtected: Boolean,
)

data class LibrarySourceRow(val gameId: String, val sourceType: String)

@Dao
interface GameLibraryDao {
    @Query("""
        SELECT g.*, wp.displayName AS whiteName, bp.displayName AS blackName,
            wp.engineName AS whiteEngineName, bp.engineName AS blackEngineName,
            COALESCE(f.isFavorite, 0) AS isFavorite, COALESCE(f.isProtected, 0) AS isProtected,
            (SELECT state FROM reviews r WHERE r.gameId = g.id ORDER BY r.updatedAtEpochMillis DESC, r.id LIMIT 1) AS latestReviewState
        FROM games g
        LEFT JOIN participants wp ON wp.id = g.whiteParticipantId
        LEFT JOIN participants bp ON bp.id = g.blackParticipantId
        LEFT JOIN game_library_flags f ON f.gameId = g.id
        WHERE (:sourceType IS NULL OR EXISTS (SELECT 1 FROM game_sources s WHERE s.gameId = g.id AND s.sourceType = :sourceType))
          AND (:favoritesOnly = 0 OR f.isFavorite = 1)
          AND (:search = '' OR instr(lower(COALESCE(wp.displayName, '')), lower(:search)) > 0
            OR instr(lower(COALESCE(bp.displayName, '')), lower(:search)) > 0
            OR instr(lower(COALESCE(wp.engineName, '')), lower(:search)) > 0
            OR instr(lower(COALESCE(bp.engineName, '')), lower(:search)) > 0
            OR EXISTS (SELECT 1 FROM game_headers h WHERE h.gameId = g.id
                AND h.name IN ('White', 'Black', 'Event', 'Site', 'Opening', 'Variation', 'ECO')
                AND instr(lower(h.value), lower(:search)) > 0))
          AND (:cursorTime IS NULL OR g.createdAtEpochMillis < :cursorTime OR (g.createdAtEpochMillis = :cursorTime AND g.id > :cursorId))
        ORDER BY g.createdAtEpochMillis DESC, g.id ASC LIMIT :limit
    """)
    suspend fun page(sourceType: String?, favoritesOnly: Boolean, search: String, cursorTime: Long?, cursorId: String?, limit: Int): List<LibraryCardRow>

    @Query("SELECT DISTINCT gameId, sourceType FROM game_sources WHERE gameId IN (:ids) ORDER BY gameId, sourceType")
    suspend fun sources(ids: List<String>): List<LibrarySourceRow>

    @Query("SELECT * FROM game_headers WHERE gameId IN (:ids) AND name IN ('White', 'Black', 'WhiteElo', 'BlackElo', 'WhiteTitle', 'BlackTitle', 'Event', 'Site', 'Date', 'Round', 'Opening', 'Variation', 'ECO', 'TimeControl') ORDER BY gameId, orderIndex")
    suspend fun headers(ids: List<String>): List<GameHeaderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFlags(flags: GameLibraryFlagsEntity)
    @Query("UPDATE game_library_flags SET isFavorite = :value WHERE gameId = :id")
    suspend fun setFavorite(id: String, value: Boolean): Int
    @Query("UPDATE game_library_flags SET isProtected = :value WHERE gameId = :id")
    suspend fun setProtected(id: String, value: Boolean): Int
}

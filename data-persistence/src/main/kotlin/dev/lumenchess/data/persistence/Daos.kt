package dev.lumenchess.data.persistence

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface ParticipantDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entity: ParticipantEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExternalIdentity(entity: ParticipantExternalIdentityEntity)
    @Query("SELECT * FROM participants WHERE id = :id") suspend fun byId(id: String): ParticipantEntity?
    @Query(
        """
        SELECT * FROM participant_external_identities
        WHERE sourceType = :sourceType AND sourceAccountScope = :sourceAccountScope AND externalParticipantId = :externalParticipantId
        """,
    )
    suspend fun externalIdentity(
        sourceType: String,
        sourceAccountScope: String,
        externalParticipantId: String,
    ): ParticipantExternalIdentityEntity?
    @Query("SELECT COUNT(*) FROM participant_external_identities WHERE participantId = :participantId") suspend fun externalIdentityCount(participantId: String): Int
    @Query("SELECT COUNT(*) FROM participants") suspend fun countAll(): Int
}

data class GameListRow(
    val id: String,
    val variant: String,
    val result: String?,
    val createdAtEpochMillis: Long,
    val playedAtEpochMillis: Long?,
    val rated: Boolean?,
    val whiteName: String?,
    val blackName: String?,
)

@Dao
interface GameDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGame(entity: GameEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHeaders(entities: List<GameHeaderEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNodes(entities: List<GameNodeEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertComments(entities: List<GameNodeCommentEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertNags(entities: List<GameNodeNagEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertAnnotations(entities: List<GameNodeAnnotationEntity>)

    @Query("SELECT * FROM games WHERE id = :id") suspend fun gameById(id: String): GameEntity?
    @Query("SELECT id FROM games WHERE contentFingerprint = :fingerprint ORDER BY createdAtEpochMillis, id") suspend fun gameIdsByFingerprint(fingerprint: String): List<String>
    @Query("SELECT id FROM games WHERE contentFingerprint IS NULL ORDER BY id") suspend fun gameIdsMissingFingerprint(): List<String>
    @Query("UPDATE games SET contentFingerprint = :fingerprint WHERE id = :id AND contentFingerprint IS NULL") suspend fun setFingerprintIfMissing(id: String, fingerprint: String): Int
    @Query("SELECT * FROM game_headers WHERE gameId = :gameId ORDER BY orderIndex") suspend fun headersForGame(gameId: String): List<GameHeaderEntity>
    @Query("SELECT * FROM game_nodes WHERE gameId = :gameId ORDER BY parentNodeId, siblingOrder, id") suspend fun nodesForGame(gameId: String): List<GameNodeEntity>
    @Query("SELECT * FROM game_node_comments WHERE gameId = :gameId ORDER BY nodeId, kind, orderIndex, id") suspend fun commentsForGame(gameId: String): List<GameNodeCommentEntity>
    @Query("SELECT * FROM game_node_nags WHERE gameId = :gameId ORDER BY nodeId, orderIndex") suspend fun nagsForGame(gameId: String): List<GameNodeNagEntity>
    @Query("SELECT * FROM game_node_annotations WHERE gameId = :gameId ORDER BY nodeId, key") suspend fun annotationsForGame(gameId: String): List<GameNodeAnnotationEntity>
    @Query("SELECT id FROM game_nodes WHERE gameId = :gameId ORDER BY id") suspend fun nodeIdsForGame(gameId: String): List<String>
    @Query("SELECT COUNT(*) FROM games") suspend fun countGames(): Int
    @Query("SELECT COUNT(*) FROM game_nodes WHERE gameId = :gameId") suspend fun countNodes(gameId: String): Int
    @Query("DELETE FROM games WHERE id = :id") suspend fun deleteGame(id: String): Int
    @Query(
        """
        SELECT g.id, g.variant, g.result, g.createdAtEpochMillis, g.playedAtEpochMillis, g.rated,
               wp.displayName AS whiteName, bp.displayName AS blackName
        FROM games g
        LEFT JOIN participants wp ON wp.id = g.whiteParticipantId
        LEFT JOIN participants bp ON bp.id = g.blackParticipantId
        ORDER BY g.createdAtEpochMillis DESC, g.id
        """,
    )
    suspend fun listGames(): List<GameListRow>
}

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSources(entities: List<GameSourceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMetadata(entities: List<GameSourceMetadataEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMetadata(entities: List<GameSourceMetadataEntity>)
    @Query("SELECT * FROM game_sources WHERE gameId = :gameId ORDER BY id") suspend fun forGame(gameId: String): List<GameSourceEntity>
    @Query("SELECT * FROM game_source_metadata WHERE sourceId IN (:sourceIds) ORDER BY sourceId, key") suspend fun metadataForSources(sourceIds: List<String>): List<GameSourceMetadataEntity>
    @Query(
        """
        SELECT * FROM game_sources
        WHERE sourceType = :sourceType AND sourceAccountScope = :sourceAccountScope AND externalGameId = :externalGameId
        LIMIT 1
        """,
    )
    suspend fun byStrongIdentity(sourceType: String, sourceAccountScope: String, externalGameId: String): GameSourceEntity?
    @Query(
        """
        UPDATE game_sources
        SET externalUrl = COALESCE(:externalUrl, externalUrl),
            importedAtEpochMillis = CASE
                WHEN importedAtEpochMillis IS NULL THEN :importedAtEpochMillis
                WHEN :importedAtEpochMillis IS NULL THEN importedAtEpochMillis
                ELSE MIN(importedAtEpochMillis, :importedAtEpochMillis)
            END,
            lastSyncedAtEpochMillis = CASE
                WHEN lastSyncedAtEpochMillis IS NULL THEN :lastSyncedAtEpochMillis
                WHEN :lastSyncedAtEpochMillis IS NULL THEN lastSyncedAtEpochMillis
                ELSE MAX(lastSyncedAtEpochMillis, :lastSyncedAtEpochMillis)
            END
        WHERE id = :id
        """,
    )
    suspend fun refreshSource(
        id: String,
        externalUrl: String?,
        importedAtEpochMillis: Long?,
        lastSyncedAtEpochMillis: Long?,
    ): Int
    @Query("SELECT COUNT(*) FROM game_sources WHERE gameId = :gameId") suspend fun countForGame(gameId: String): Int
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReview(entity: ReviewEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPly(entity: ReviewPlyEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHeavy(entity: ReviewHeavyAnalysisEntity)
    @Query("DELETE FROM review_heavy_analysis WHERE reviewPlyId IN (SELECT id FROM review_plies WHERE reviewId = :reviewId)") suspend fun deleteHeavyForReview(reviewId: String): Int
    @Query("SELECT id FROM review_heavy_analysis ORDER BY createdAtEpochMillis, id") suspend fun heavyIdsOldestFirst(): List<String>
    @Query(
        """
        SELECT id FROM review_heavy_analysis
        WHERE createdAtEpochMillis < :cutoffEpochMillis
        ORDER BY createdAtEpochMillis, id
        LIMIT :limit
        """,
    )
    suspend fun heavyIdsOlderThan(cutoffEpochMillis: Long, limit: Int): List<String>
    @Query(
        """
        SELECT id FROM review_heavy_analysis
        ORDER BY createdAtEpochMillis DESC, id DESC
        LIMIT :limit OFFSET :maxRetainedCount
        """,
    )
    suspend fun heavyIdsBeyondNewest(maxRetainedCount: Int, limit: Int): List<String>
    @Query("DELETE FROM review_heavy_analysis WHERE id IN (:ids)") suspend fun deleteHeavyByIds(ids: List<String>): Int
    @Query("SELECT COUNT(*) FROM reviews WHERE gameId = :gameId") suspend fun countReviewsForGame(gameId: String): Int
    @Query("SELECT COUNT(*) FROM review_plies WHERE gameId = :gameId") suspend fun countReviewPliesForGame(gameId: String): Int
    @Query("SELECT COUNT(*) FROM review_heavy_analysis") suspend fun countHeavy(): Int
}

@Dao
interface SavedPositionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entity: SavedPositionEntity)
    @Query("SELECT * FROM saved_positions WHERE id = :id") suspend fun byId(id: String): SavedPositionEntity?
    @Query("SELECT * FROM saved_positions ORDER BY updatedAtEpochMillis DESC, id") suspend fun listAll(): List<SavedPositionEntity>
}

@Dao
interface RatingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entity: RatingEventEntity)
    @Query("SELECT * FROM rating_events WHERE participantId = :participantId ORDER BY recordedAtEpochMillis, id") suspend fun forParticipant(participantId: String): List<RatingEventEntity>
}

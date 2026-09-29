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
    @Query("SELECT * FROM participant_external_identities WHERE sourceType = :sourceType AND sourceAccountScope = :sourceAccountScope AND externalParticipantId = :externalParticipantId")
    suspend fun externalIdentity(sourceType: String, sourceAccountScope: String, externalParticipantId: String): ParticipantExternalIdentityEntity?
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
    @Query("UPDATE games SET contentFingerprint = :fingerprint WHERE id = :id") suspend fun overwriteFingerprint(id: String, fingerprint: String?): Int
    @Query(
        """
        UPDATE games
        SET result = :result,
            termination = :termination,
            playedAtEpochMillis = :playedAtEpochMillis,
            rated = :rated,
            timeControlBaseMillis = :timeControlBaseMillis,
            timeControlIncrementMillis = :timeControlIncrementMillis,
            timeControlRaw = :timeControlRaw,
            contentFingerprint = :contentFingerprint
        WHERE id = :id
        """,
    )
    suspend fun updateLiveSnapshot(
        id: String,
        result: String?,
        termination: String?,
        playedAtEpochMillis: Long?,
        rated: Boolean?,
        timeControlBaseMillis: Long?,
        timeControlIncrementMillis: Long?,
        timeControlRaw: String?,
        contentFingerprint: String,
    ): Int
    @Query("UPDATE game_nodes SET parentNodeId = :parentNodeId WHERE id = :id") suspend fun overwriteParent(id: String, parentNodeId: String?): Int
    @Query("SELECT * FROM game_headers WHERE gameId = :gameId ORDER BY orderIndex") suspend fun headersForGame(gameId: String): List<GameHeaderEntity>
    @Query("SELECT * FROM game_nodes WHERE gameId = :gameId ORDER BY parentNodeId, siblingOrder, id") suspend fun nodesForGame(gameId: String): List<GameNodeEntity>
    @Query("SELECT * FROM game_node_comments WHERE gameId = :gameId ORDER BY nodeId, kind, orderIndex, id") suspend fun commentsForGame(gameId: String): List<GameNodeCommentEntity>
    @Query("SELECT * FROM game_node_nags WHERE gameId = :gameId ORDER BY nodeId, orderIndex") suspend fun nagsForGame(gameId: String): List<GameNodeNagEntity>
    @Query("SELECT * FROM game_node_annotations WHERE gameId = :gameId ORDER BY nodeId, key") suspend fun annotationsForGame(gameId: String): List<GameNodeAnnotationEntity>
    @Query("SELECT id FROM game_nodes WHERE gameId = :gameId ORDER BY id") suspend fun nodeIdsForGame(gameId: String): List<String>
    @Query("SELECT COUNT(*) FROM games") suspend fun countGames(): Int
    @Query("SELECT COUNT(*) FROM game_nodes WHERE gameId = :gameId") suspend fun countNodes(gameId: String): Int
    @Query("DELETE FROM games WHERE id = :id") suspend fun deleteGame(id: String): Int
    @Query("SELECT g.id, g.variant, g.result, g.createdAtEpochMillis, g.playedAtEpochMillis, g.rated, wp.displayName AS whiteName, bp.displayName AS blackName FROM games g LEFT JOIN participants wp ON wp.id = g.whiteParticipantId LEFT JOIN participants bp ON bp.id = g.blackParticipantId ORDER BY g.createdAtEpochMillis DESC, g.id")
    suspend fun listGames(): List<GameListRow>
}

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSources(entities: List<GameSourceEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMetadata(entities: List<GameSourceMetadataEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMetadata(entities: List<GameSourceMetadataEntity>)
    @Query("SELECT * FROM game_sources WHERE gameId = :gameId ORDER BY id") suspend fun forGame(gameId: String): List<GameSourceEntity>
    @Query("SELECT * FROM game_source_metadata WHERE sourceId IN (:sourceIds) ORDER BY sourceId, key") suspend fun metadataForSources(sourceIds: List<String>): List<GameSourceMetadataEntity>
    @Query("SELECT * FROM game_sources WHERE sourceType = :sourceType AND sourceAccountScope = :sourceAccountScope AND externalGameId = :externalGameId LIMIT 1")
    suspend fun byStrongIdentity(sourceType: String, sourceAccountScope: String, externalGameId: String): GameSourceEntity?
    @Query("UPDATE game_sources SET externalUrl = COALESCE(:externalUrl, externalUrl), importedAtEpochMillis = CASE WHEN importedAtEpochMillis IS NULL THEN :importedAtEpochMillis WHEN :importedAtEpochMillis IS NULL THEN importedAtEpochMillis ELSE MIN(importedAtEpochMillis, :importedAtEpochMillis) END, lastSyncedAtEpochMillis = CASE WHEN lastSyncedAtEpochMillis IS NULL THEN :lastSyncedAtEpochMillis WHEN :lastSyncedAtEpochMillis IS NULL THEN lastSyncedAtEpochMillis ELSE MAX(lastSyncedAtEpochMillis, :lastSyncedAtEpochMillis) END WHERE id = :id")
    suspend fun refreshSource(id: String, externalUrl: String?, importedAtEpochMillis: Long?, lastSyncedAtEpochMillis: Long?): Int
    @Query("SELECT COUNT(*) FROM game_sources WHERE gameId = :gameId") suspend fun countForGame(gameId: String): Int
}

@Dao
interface ReviewDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReview(entity: ReviewEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPly(entity: ReviewPlyEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHeavy(entity: ReviewHeavyAnalysisEntity)
    @Query("DELETE FROM review_heavy_analysis WHERE reviewPlyId IN (SELECT id FROM review_plies WHERE reviewId = :reviewId)") suspend fun deleteHeavyForReview(reviewId: String): Int
    @Query("SELECT id FROM review_heavy_analysis ORDER BY createdAtEpochMillis, id") suspend fun heavyIdsOldestFirst(): List<String>
    @Query("SELECT h.id FROM review_heavy_analysis h WHERE h.createdAtEpochMillis < :cutoffEpochMillis AND NOT EXISTS (SELECT 1 FROM review_plies p JOIN game_library_flags f ON f.gameId = p.gameId WHERE p.id = h.reviewPlyId AND (f.isFavorite = 1 OR f.isProtected = 1)) ORDER BY h.createdAtEpochMillis, h.id LIMIT :limit") suspend fun heavyIdsOlderThan(cutoffEpochMillis: Long, limit: Int): List<String>
    @Query("SELECT h.id FROM review_heavy_analysis h WHERE NOT EXISTS (SELECT 1 FROM review_plies p JOIN game_library_flags f ON f.gameId = p.gameId WHERE p.id = h.reviewPlyId AND (f.isFavorite = 1 OR f.isProtected = 1)) ORDER BY h.createdAtEpochMillis DESC, h.id DESC LIMIT :limit OFFSET :maxRetainedCount") suspend fun heavyIdsBeyondNewest(maxRetainedCount: Int, limit: Int): List<String>
    @Query("DELETE FROM review_heavy_analysis WHERE id IN (:ids)") suspend fun deleteHeavyByIds(ids: List<String>): Int
    @Query("SELECT COUNT(*) FROM reviews WHERE gameId = :gameId") suspend fun countReviewsForGame(gameId: String): Int
    @Query("SELECT COUNT(*) FROM review_plies WHERE gameId = :gameId") suspend fun countReviewPliesForGame(gameId: String): Int
    @Query("SELECT COUNT(*) FROM review_heavy_analysis") suspend fun countHeavy(): Int
    @Query("SELECT COALESCE(SUM(LENGTH(payload)), 0) FROM review_heavy_analysis") suspend fun heavyPayloadBytes(): Long

    @Query("SELECT * FROM reviews WHERE id = :id") suspend fun reviewById(id: String): ReviewEntity?
    @Query("SELECT * FROM reviews WHERE gameId = :gameId ORDER BY updatedAtEpochMillis DESC, id DESC") suspend fun reviewsForGame(gameId: String): List<ReviewEntity>
    @Query("SELECT * FROM reviews WHERE state = :state ORDER BY updatedAtEpochMillis DESC, id DESC") suspend fun reviewsInState(state: String): List<ReviewEntity>
    @Query("UPDATE reviews SET state = :state, progressPly = :progressPly, updatedAtEpochMillis = :updatedAtEpochMillis, completedAtEpochMillis = :completedAtEpochMillis WHERE id = :id")
    suspend fun updateReviewState(id: String, state: String, progressPly: Int, updatedAtEpochMillis: Long, completedAtEpochMillis: Long?): Int
    @Query("DELETE FROM reviews WHERE id = :id") suspend fun deleteReview(id: String): Int

    @Query("SELECT * FROM review_plies WHERE reviewId = :reviewId") suspend fun pliesForReview(reviewId: String): List<ReviewPlyEntity>
    @Query("SELECT * FROM review_plies WHERE reviewId IN (:reviewIds)") suspend fun pliesForReviews(reviewIds: List<String>): List<ReviewPlyEntity>
    @Query("SELECT * FROM review_plies WHERE reviewId = :reviewId AND nodeId = :nodeId") suspend fun plyForNode(reviewId: String, nodeId: String): ReviewPlyEntity?
    @Query(
        "UPDATE review_plies SET playedEvalCp = :playedEvalCp, playedMateIn = :playedMateIn, bestMoveFrom = :bestMoveFrom, " +
            "bestMoveTo = :bestMoveTo, bestMovePromotionCode = :bestMovePromotionCode, classification = :classification, " +
            "expectedPointsLoss = :expectedPointsLoss, depth = :depth, nodes = :nodes, timeMillis = :timeMillis WHERE id = :id",
    )
    suspend fun updatePly(
        id: String,
        playedEvalCp: Int?,
        playedMateIn: Int?,
        bestMoveFrom: Int?,
        bestMoveTo: Int?,
        bestMovePromotionCode: Int?,
        classification: String?,
        expectedPointsLoss: Double?,
        depth: Int?,
        nodes: Long?,
        timeMillis: Long?,
    ): Int

    @Query("SELECT h.* FROM review_heavy_analysis h JOIN review_plies p ON p.id = h.reviewPlyId WHERE p.reviewId = :reviewId ORDER BY h.createdAtEpochMillis, h.id")
    suspend fun heavyForReview(reviewId: String): List<ReviewHeavyAnalysisEntity>
    @Query("DELETE FROM review_heavy_analysis WHERE reviewPlyId = :reviewPlyId AND format = :format") suspend fun deleteHeavyForPly(reviewPlyId: String, format: String): Int
}

@Dao
interface SavedPositionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entity: SavedPositionEntity)
    @Query("SELECT * FROM saved_positions WHERE id = :id") suspend fun byId(id: String): SavedPositionEntity?
    @Query("SELECT * FROM saved_positions ORDER BY updatedAtEpochMillis DESC, id") suspend fun listAll(): List<SavedPositionEntity>
    @Query("UPDATE saved_positions SET title = :title, notes = :notes, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun rename(id: String, title: String, notes: String?, updatedAtEpochMillis: Long): Int
    @Query("DELETE FROM saved_positions WHERE id = :id") suspend fun delete(id: String): Int
}

@Dao
interface RatingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entity: RatingEventEntity)
    @Query("SELECT * FROM rating_events WHERE participantId = :participantId ORDER BY recordedAtEpochMillis, id") suspend fun forParticipant(participantId: String): List<RatingEventEntity>
}

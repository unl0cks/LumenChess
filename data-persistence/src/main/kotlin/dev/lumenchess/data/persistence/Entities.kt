package dev.lumenchess.data.persistence

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "participants")
data class ParticipantEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val displayName: String?,
    val engineName: String?,
    val engineVersion: String?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "games",
    foreignKeys = [
        ForeignKey(entity = ParticipantEntity::class, parentColumns = ["id"], childColumns = ["whiteParticipantId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ParticipantEntity::class, parentColumns = ["id"], childColumns = ["blackParticipantId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("whiteParticipantId"), Index("blackParticipantId"), Index("createdAtEpochMillis"), Index("playedAtEpochMillis")],
)
data class GameEntity(
    @PrimaryKey val id: String,
    val variant: String,
    val startFen: String,
    val result: String?,
    val termination: String?,
    val createdAtEpochMillis: Long,
    val importedAtEpochMillis: Long?,
    val playedAtEpochMillis: Long?,
    val rated: Boolean?,
    val timeControlBaseMillis: Long?,
    val timeControlIncrementMillis: Long?,
    val timeControlRaw: String?,
    val whiteParticipantId: String?,
    val blackParticipantId: String?,
)

@Entity(
    tableName = "game_headers",
    primaryKeys = ["gameId", "name"],
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gameId"), Index(value = ["gameId", "orderIndex"], unique = true)],
)
data class GameHeaderEntity(val gameId: String, val name: String, val value: String, val orderIndex: Int)

@Entity(
    tableName = "game_nodes",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(
            entity = GameNodeEntity::class,
            parentColumns = ["gameId", "id"],
            childColumns = ["gameId", "parentNodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("gameId"),
        Index("parentNodeId"),
        Index(value = ["gameId", "id"], unique = true),
        Index(value = ["gameId", "parentNodeId", "siblingOrder"], unique = true),
    ],
)
data class GameNodeEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val parentNodeId: String?,
    val siblingOrder: Int,
    val fromSquare: Int,
    val toSquare: Int,
    val promotionCode: Int?,
    val san: String,
)

@Entity(
    tableName = "game_node_comments",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GameNodeEntity::class, parentColumns = ["gameId", "id"], childColumns = ["gameId", "nodeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("gameId"), Index("nodeId"), Index(value = ["gameId", "nodeId", "kind", "orderIndex"], unique = true)],
)
data class GameNodeCommentEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val nodeId: String?,
    val kind: String,
    val orderIndex: Int,
    val text: String,
)

@Entity(
    tableName = "game_node_nags",
    primaryKeys = ["gameId", "nodeId", "orderIndex"],
    foreignKeys = [ForeignKey(entity = GameNodeEntity::class, parentColumns = ["gameId", "id"], childColumns = ["gameId", "nodeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("nodeId")],
)
data class GameNodeNagEntity(val gameId: String, val nodeId: String, val orderIndex: Int, val value: Int)

@Entity(
    tableName = "game_node_annotations",
    primaryKeys = ["gameId", "nodeId", "key"],
    foreignKeys = [ForeignKey(entity = GameNodeEntity::class, parentColumns = ["gameId", "id"], childColumns = ["gameId", "nodeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("nodeId")],
)
data class GameNodeAnnotationEntity(val gameId: String, val nodeId: String, val key: String, val value: String)

@Entity(
    tableName = "game_sources",
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gameId"), Index(value = ["sourceType", "externalGameId"])],
)
data class GameSourceEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val sourceType: String,
    val externalGameId: String?,
    val externalUrl: String?,
    val importedAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
    val sourceAccountId: String?,
)

@Entity(
    tableName = "game_source_metadata",
    primaryKeys = ["sourceId", "key"],
    foreignKeys = [ForeignKey(entity = GameSourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("sourceId")],
)
data class GameSourceMetadataEntity(val sourceId: String, val key: String, val value: String)

@Entity(
    tableName = "reviews",
    foreignKeys = [ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("gameId"), Index(value = ["gameId", "id"], unique = true)],
)
data class ReviewEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val modelVersion: String,
    val engineName: String,
    val engineVersion: String?,
    val profile: String?,
    val state: String,
    val progressPly: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
)

@Entity(
    tableName = "review_plies",
    foreignKeys = [
        ForeignKey(entity = ReviewEntity::class, parentColumns = ["gameId", "id"], childColumns = ["gameId", "reviewId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GameNodeEntity::class, parentColumns = ["gameId", "id"], childColumns = ["gameId", "nodeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["gameId", "reviewId"]),
        Index(value = ["gameId", "nodeId"]),
        Index(value = ["reviewId", "nodeId"], unique = true),
    ],
)
data class ReviewPlyEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val reviewId: String,
    val nodeId: String,
    val playedEvalCp: Int?,
    val playedMateIn: Int?,
    val bestMoveFrom: Int?,
    val bestMoveTo: Int?,
    val bestMovePromotionCode: Int?,
    val classification: String?,
    val expectedPointsLoss: Double?,
    val depth: Int?,
    val nodes: Long?,
    val timeMillis: Long?,
)

@Entity(
    tableName = "review_heavy_analysis",
    foreignKeys = [ForeignKey(entity = ReviewPlyEntity::class, parentColumns = ["id"], childColumns = ["reviewPlyId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reviewPlyId")],
)
data class ReviewHeavyAnalysisEntity(
    @PrimaryKey val id: String,
    val reviewPlyId: String,
    val format: String,
    val payload: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "saved_positions", indices = [Index("updatedAtEpochMillis")])
data class SavedPositionEntity(
    @PrimaryKey val id: String,
    val variant: String,
    val fen: String,
    val title: String,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "rating_events",
    foreignKeys = [
        ForeignKey(entity = ParticipantEntity::class, parentColumns = ["id"], childColumns = ["participantId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("participantId"), Index("gameId"), Index(value = ["ratingSystem", "variant", "timeControlPool", "recordedAtEpochMillis"])],
)
data class RatingEventEntity(
    @PrimaryKey val id: String,
    val participantId: String,
    val ratingSystem: String,
    val variant: String,
    val timeControlPool: String,
    val recordedAtEpochMillis: Long,
    val ratingValue: Double,
    val deviation: Double?,
    val volatility: Double?,
    val gameId: String?,
)

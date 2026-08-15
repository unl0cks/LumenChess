package dev.lumenchess.data.persistence

import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Variant

@JvmInline value class PersistentGameId(val value: String)
@JvmInline value class PersistentParticipantId(val value: String)
@JvmInline value class PersistentSourceId(val value: String)
@JvmInline value class PersistentReviewId(val value: String)
@JvmInline value class PersistentSavedPositionId(val value: String)
@JvmInline value class PersistentRatingEventId(val value: String)

enum class PersistedTermination {
    CHECKMATE,
    STALEMATE,
    RESIGNATION,
    TIMEOUT,
    AGREEMENT,
    INSUFFICIENT_MATERIAL,
    FIFTY_MOVE_RULE,
    SEVENTY_FIVE_MOVE_RULE,
    THREEFOLD_REPETITION,
    FIVEFOLD_REPETITION,
    ABANDONED,
    OTHER,
}

enum class ParticipantKind { HUMAN_LOCAL, HUMAN, ENGINE, EXTERNAL, UNKNOWN }
enum class GameSourceType { LOCAL, PGN_IMPORT, CHESS_COM, LICHESS, ENGINE_ARENA, BRANCH, OTHER }
enum class ReviewState { PENDING, RUNNING, PARTIAL, COMPLETE, FAILED, CANCELLED }
enum class RatingSystemType { PERFORMANCE_ESTIMATE, GLICKO_2, GLICKO_1, FIDE_ELO, OTHER }

data class TimeControlMetadata(
    val baseMillis: Long? = null,
    val incrementMillis: Long? = null,
    val raw: String? = null,
)

data class GamePersistenceMetadata(
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val importedAtEpochMillis: Long? = null,
    val playedAtEpochMillis: Long? = null,
    val rated: Boolean? = null,
    val termination: PersistedTermination? = null,
    val timeControl: TimeControlMetadata? = null,
)

data class ParticipantDraft(
    val kind: ParticipantKind,
    val displayName: String? = null,
    val engineName: String? = null,
    val engineVersion: String? = null,
)

data class ParticipantExternalIdentity(
    val sourceType: GameSourceType,
    val externalParticipantId: String,
    val sourceAccountId: String? = null,
)

data class ParticipantRecord(
    val id: PersistentParticipantId,
    val kind: ParticipantKind,
    val displayName: String?,
    val engineName: String?,
    val engineVersion: String?,
)

data class GameSourceDraft(
    val type: GameSourceType,
    val externalGameId: String? = null,
    val externalUrl: String? = null,
    val importedAtEpochMillis: Long? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val sourceAccountId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class GameSourceRecord(
    val id: PersistentSourceId,
    val type: GameSourceType,
    val externalGameId: String?,
    val externalUrl: String?,
    val importedAtEpochMillis: Long?,
    val lastSyncedAtEpochMillis: Long?,
    val sourceAccountId: String?,
    val metadata: Map<String, String>,
)

data class PersistGameRequest(
    val tree: GameTree,
    val metadata: GamePersistenceMetadata = GamePersistenceMetadata(),
    val whiteParticipant: ParticipantDraft? = null,
    val blackParticipant: ParticipantDraft? = null,
    val sources: List<GameSourceDraft> = emptyList(),
)

data class LoadedCanonicalGame(
    val id: PersistentGameId,
    val tree: GameTree,
    val metadata: GamePersistenceMetadata,
    val whiteParticipant: ParticipantRecord?,
    val blackParticipant: ParticipantRecord?,
    val sources: List<GameSourceRecord>,
)

data class GameListEntry(
    val id: PersistentGameId,
    val variant: Variant,
    val result: String?,
    val createdAtEpochMillis: Long,
    val playedAtEpochMillis: Long?,
    val rated: Boolean?,
    val whiteName: String?,
    val blackName: String?,
)

data class SavedPositionDraft(
    val variant: Variant,
    val fen: String,
    val title: String,
    val notes: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = createdAtEpochMillis,
)

data class SavedPositionRecord(
    val id: PersistentSavedPositionId,
    val variant: Variant,
    val fen: String,
    val title: String,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

class PersistenceMappingException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
class PersistenceConflictException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

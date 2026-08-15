package dev.lumenchess.data.persistence

import androidx.room3.withWriteTransaction

enum class RetentionClass {
    CANONICAL_DURABLE,
    LIGHTWEIGHT_DURABLE,
    HEAVYWEIGHT_DISPOSABLE,
}

data class HeavyAnalysisRetentionPolicy(
    val olderThanEpochMillis: Long? = null,
    val maxRetainedCount: Int? = null,
    val batchSize: Int = 500,
) {
    init {
        require(olderThanEpochMillis != null || maxRetainedCount != null) {
            "At least one heavyweight-retention limit must be provided"
        }
        require(maxRetainedCount == null || maxRetainedCount >= 0) { "maxRetainedCount must be non-negative" }
        require(batchSize > 0) { "batchSize must be positive" }
    }
}

class PersistenceRetention internal constructor(
    private val database: LumenDatabase,
    private val afterDeleteForTesting: suspend () -> Unit = {},
) {
    suspend fun prune(policy: HeavyAnalysisRetentionPolicy): Int = database.withWriteTransaction {
        val selected = linkedSetOf<String>()
        policy.olderThanEpochMillis?.let { cutoff ->
            selected += database.reviewDao().heavyIdsOlderThan(cutoff, policy.batchSize)
        }
        if (selected.size < policy.batchSize) {
            policy.maxRetainedCount?.let { maxRetained ->
                val remaining = policy.batchSize - selected.size
                selected += database.reviewDao().heavyIdsBeyondNewest(maxRetained, remaining)
            }
        }
        val ids = selected.take(policy.batchSize)
        if (ids.isEmpty()) return@withWriteTransaction 0
        val deleted = database.reviewDao().deleteHeavyByIds(ids)
        afterDeleteForTesting()
        deleted
    }

    companion object {
        private val classes = mapOf(
            "participants" to RetentionClass.CANONICAL_DURABLE,
            "participant_external_identities" to RetentionClass.CANONICAL_DURABLE,
            "games" to RetentionClass.CANONICAL_DURABLE,
            "game_headers" to RetentionClass.CANONICAL_DURABLE,
            "game_nodes" to RetentionClass.CANONICAL_DURABLE,
            "game_node_comments" to RetentionClass.CANONICAL_DURABLE,
            "game_node_nags" to RetentionClass.CANONICAL_DURABLE,
            "game_node_annotations" to RetentionClass.CANONICAL_DURABLE,
            "game_sources" to RetentionClass.CANONICAL_DURABLE,
            "game_source_metadata" to RetentionClass.CANONICAL_DURABLE,
            "saved_positions" to RetentionClass.CANONICAL_DURABLE,
            "rating_events" to RetentionClass.CANONICAL_DURABLE,
            "reviews" to RetentionClass.LIGHTWEIGHT_DURABLE,
            "review_plies" to RetentionClass.LIGHTWEIGHT_DURABLE,
            "review_heavy_analysis" to RetentionClass.HEAVYWEIGHT_DISPOSABLE,
        )

        fun classFor(tableName: String): RetentionClass =
            classes[tableName] ?: throw IllegalArgumentException("No retention class registered for table '$tableName'")

        fun classifiedTables(): Map<String, RetentionClass> = classes.toMap()
    }
}

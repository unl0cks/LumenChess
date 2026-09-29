package dev.lumenchess.data.persistence

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Square
import java.util.UUID

data class ReviewRecord(
    val id: PersistentReviewId,
    val gameId: PersistentGameId,
    val modelVersion: String,
    val engineName: String,
    val engineVersion: String?,
    val profile: String?,
    val state: ReviewState,
    val progressPly: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val completedAtEpochMillis: Long?,
)

/**
 * One reviewed mainline move. [ply] is its 0-based mainline index, resolved from the canonical
 * node order (persistent node UUIDs are never confused with in-memory tree ids).
 */
data class ReviewPlyRecord(
    val ply: Int,
    val nodeId: String,
    val playedEvalCp: Int? = null,
    val playedMateIn: Int? = null,
    val bestMove: Move? = null,
    val classification: String? = null,
    val expectedPointsLoss: Double? = null,
    val depth: Int? = null,
    val nodes: Long? = null,
    val timeMillis: Long? = null,
    /** Disposable heavy analysis by format; may be empty after cache compaction. */
    val heavy: Map<String, String> = emptyMap(),
)

/**
 * Review storage over the existing v3 tables: `reviews` and `review_plies` are durable, lightweight
 * results; `review_heavy_analysis` holds disposable engine lines that storage cleanup may remove.
 */
class ReviewRepository(
    private val database: LumenDatabase,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Persistent node ids of the mainline (first child at every step), in move order. */
    suspend fun mainlineNodeIds(gameId: PersistentGameId): List<String> = database.withReadTransaction {
        mainlineInternal(gameId.value)
    }

    suspend fun latestReview(gameId: PersistentGameId): ReviewRecord? =
        database.reviewDao().reviewsForGame(gameId.value).firstOrNull()?.toRecord()

    suspend fun review(id: PersistentReviewId): ReviewRecord? = database.reviewDao().reviewById(id.value)?.toRecord()

    suspend fun reviewsInState(state: ReviewState): List<ReviewRecord> =
        database.reviewDao().reviewsInState(state.name).map { it.toRecord() }

    /** Starts a new review, replacing any earlier review of the same game. */
    suspend fun startReview(
        gameId: PersistentGameId,
        modelVersion: String,
        engineName: String,
        engineVersion: String?,
        profile: String?,
    ): ReviewRecord = database.withWriteTransaction {
        if (database.gameDao().gameById(gameId.value) == null) {
            throw PersistenceMappingException("Cannot review missing game ${gameId.value}")
        }
        database.reviewDao().reviewsForGame(gameId.value).forEach { database.reviewDao().deleteReview(it.id) }
        val now = clock()
        val entity = ReviewEntity(
            id = newId(), gameId = gameId.value, modelVersion = modelVersion, engineName = engineName,
            engineVersion = engineVersion, profile = profile, state = ReviewState.RUNNING.name, progressPly = 0,
            createdAtEpochMillis = now, updatedAtEpochMillis = now, completedAtEpochMillis = null,
        )
        database.reviewDao().insertReview(entity)
        entity.toRecord()
    }

    suspend fun updateState(id: PersistentReviewId, state: ReviewState, progressPly: Int) {
        val now = clock()
        database.reviewDao().updateReviewState(
            id.value, state.name, progressPly, now,
            completedAtEpochMillis = if (state == ReviewState.COMPLETE) now else null,
        )
    }

    suspend fun deleteReview(id: PersistentReviewId): Boolean = database.reviewDao().deleteReview(id.value) > 0

    /** Plies of a review in mainline order, with whatever heavy analysis is still cached. */
    suspend fun plies(review: ReviewRecord): List<ReviewPlyRecord> = database.withReadTransaction {
        val order = mainlineInternal(review.gameId.value).withIndex().associate { (index, id) -> id to index }
        val heavy = database.reviewDao().heavyForReview(review.id.value).groupBy { it.reviewPlyId }
        database.reviewDao().pliesForReview(review.id.value)
            .mapNotNull { row -> order[row.nodeId]?.let { ply -> row.toRecord(ply, heavy[row.id].orEmpty()) } }
            .sortedBy { it.ply }
    }

    /** Lightweight plies of many reviews at once (Insights, ratings); heavy analysis is not read. */
    suspend fun lightweightPlies(reviews: List<ReviewRecord>): Map<PersistentReviewId, List<ReviewPlyRecord>> =
        database.withReadTransaction {
            if (reviews.isEmpty()) return@withReadTransaction emptyMap()
            val rows = reviews.chunked(400).flatMap { chunk ->
                database.reviewDao().pliesForReviews(chunk.map { it.id.value })
            }.groupBy { it.reviewId }
            reviews.associate { review ->
                val order = mainlineInternal(review.gameId.value).withIndex().associate { (index, id) -> id to index }
                review.id to rows[review.id.value].orEmpty()
                    .mapNotNull { row -> order[row.nodeId]?.let { ply -> row.toRecord(ply, emptyList()) } }
                    .sortedBy { it.ply }
            }
        }

    /**
     * Writes one ply's lightweight columns (all of them, as given) and replaces the listed heavy
     * formats. Inserts the ply row on first write.
     */
    suspend fun savePly(review: ReviewRecord, ply: ReviewPlyRecord) = database.withWriteTransaction {
        val dao = database.reviewDao()
        val existing = dao.plyForNode(review.id.value, ply.nodeId)
        val plyId = if (existing == null) {
            val id = newId()
            dao.insertPly(
                ReviewPlyEntity(
                    id = id, gameId = review.gameId.value, reviewId = review.id.value, nodeId = ply.nodeId,
                    playedEvalCp = ply.playedEvalCp, playedMateIn = ply.playedMateIn,
                    bestMoveFrom = ply.bestMove?.from?.index, bestMoveTo = ply.bestMove?.to?.index,
                    bestMovePromotionCode = encodePromotion(ply.bestMove?.promotion),
                    classification = ply.classification, expectedPointsLoss = ply.expectedPointsLoss,
                    depth = ply.depth, nodes = ply.nodes, timeMillis = ply.timeMillis,
                ),
            )
            id
        } else {
            dao.updatePly(
                existing.id, ply.playedEvalCp, ply.playedMateIn, ply.bestMove?.from?.index, ply.bestMove?.to?.index,
                encodePromotion(ply.bestMove?.promotion), ply.classification, ply.expectedPointsLoss,
                ply.depth, ply.nodes, ply.timeMillis,
            )
            existing.id
        }
        val now = clock()
        for ((format, payload) in ply.heavy) {
            dao.deleteHeavyForPly(plyId, format)
            dao.insertHeavy(ReviewHeavyAnalysisEntity(newId(), plyId, format, payload, now))
        }
    }

    private suspend fun mainlineInternal(gameId: String): List<String> {
        val children = database.gameDao().nodesForGame(gameId).groupBy { it.parentNodeId }
        val out = ArrayList<String>()
        var parent: String? = null
        while (true) {
            val next = children[parent].orEmpty().minByOrNull { it.siblingOrder } ?: break
            out += next.id
            parent = next.id
        }
        return out
    }

    private fun ReviewEntity.toRecord() = ReviewRecord(
        id = PersistentReviewId(id),
        gameId = PersistentGameId(gameId),
        modelVersion = modelVersion,
        engineName = engineName,
        engineVersion = engineVersion,
        profile = profile,
        state = ReviewState.entries.firstOrNull { it.name == state } ?: ReviewState.FAILED,
        progressPly = progressPly,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        completedAtEpochMillis = completedAtEpochMillis,
    )

    private fun ReviewPlyEntity.toRecord(ply: Int, heavy: List<ReviewHeavyAnalysisEntity>) = ReviewPlyRecord(
        ply = ply,
        nodeId = nodeId,
        playedEvalCp = playedEvalCp,
        playedMateIn = playedMateIn,
        bestMove = decodeMove(bestMoveFrom, bestMoveTo, bestMovePromotionCode),
        classification = classification,
        expectedPointsLoss = expectedPointsLoss,
        depth = depth,
        nodes = nodes,
        timeMillis = timeMillis,
        heavy = heavy.associate { it.format to it.payload },
    )

    private fun decodeMove(from: Int?, to: Int?, promotion: Int?): Move? {
        if (from == null || to == null || from !in 0..63 || to !in 0..63) return null
        val piece = when (promotion) {
            null -> null
            1 -> PieceType.QUEEN
            2 -> PieceType.ROOK
            3 -> PieceType.BISHOP
            4 -> PieceType.KNIGHT
            else -> return null
        }
        return Move(Square.fromIndex(from), Square.fromIndex(to), piece)
    }

    private fun encodePromotion(type: PieceType?): Int? = when (type) {
        null -> null
        PieceType.QUEEN -> 1
        PieceType.ROOK -> 2
        PieceType.BISHOP -> 3
        PieceType.KNIGHT -> 4
        PieceType.PAWN, PieceType.KING -> null
    }
}

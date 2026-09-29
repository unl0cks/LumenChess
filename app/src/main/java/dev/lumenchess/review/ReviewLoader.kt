package dev.lumenchess.review

import android.content.Context
import dev.lumenchess.analysis.openings.Opening
import dev.lumenchess.analysis.review.EvaluationCodec
import dev.lumenchess.analysis.review.MoveClassification
import dev.lumenchess.analysis.review.PositionEvaluation
import dev.lumenchess.analysis.review.ReviewModelVersion
import dev.lumenchess.analysis.review.ReviewReconstruction
import dev.lumenchess.analysis.review.ReviewSession
import dev.lumenchess.analysis.review.ReviewSummary
import dev.lumenchess.analysis.review.ReviewSummarizer
import dev.lumenchess.analysis.review.ReviewedMove
import dev.lumenchess.analysis.review.StoredReviewPly
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position
import dev.lumenchess.data.AppData
import dev.lumenchess.data.persistence.LoadedCanonicalGame
import dev.lumenchess.data.persistence.ReviewPlyRecord
import dev.lumenchess.data.persistence.ReviewRecord
import dev.lumenchess.data.persistence.ReviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A finished review, ready for display. */
data class LoadedReview(
    val record: ReviewRecord,
    val moves: List<ReviewedMove>,
    val summary: ReviewSummary,
    /** Cached engine evaluations by position index (0 = start); empty after cache compaction. */
    val evaluations: Map<Int, PositionEvaluation>,
    val opening: Opening?,
    /** Mainline index of the last move that is still in the opening book. */
    val lastBookPly: Int?,
    /** False when the engine lines were compacted away and only durable results remain. */
    val hasEngineLines: Boolean,
)

object ReviewLoader {
    /** The latest review of [game]: finished ([LoadedReview]) or null with its record for progress. */
    suspend fun load(context: Context, game: LoadedCanonicalGame): Pair<ReviewRecord?, LoadedReview?> {
        val reviews = AppData.reviews(context)
        val record = reviews.latestReview(game.id) ?: return null to null
        if (record.state != ReviewState.COMPLETE) return record to null
        val plies = reviews.plies(record)
        val loaded = withContext(Dispatchers.Default) { assemble(game, record, plies) }
        return record to loaded
    }

    internal fun assemble(game: LoadedCanonicalGame, record: ReviewRecord, plies: List<ReviewPlyRecord>): LoadedReview? {
        val moves: List<Move> = game.tree.mainline().mapNotNull { it.move }
        if (moves.isEmpty()) return null
        val book = AppData.openingBook
        val session = ReviewSession(game.tree.startPosition, moves, openingBook = book)
        val positions: List<Position> = session.positions
        val cached = HashMap<Int, PositionEvaluation>()
        for (row in plies) {
            if (row.ply !in moves.indices) continue
            row.heavy[ReviewCoordinator.FORMAT_EVAL]?.let { EvaluationCodec.decode(it, positions[row.ply]) }?.let {
                cached[row.ply] = it.evaluation
                session.restore(row.ply, it.evaluation, it.pass)
            }
            row.heavy[ReviewCoordinator.FORMAT_FINAL]?.let { EvaluationCodec.decode(it, positions[moves.size]) }?.let {
                cached[moves.size] = it.evaluation
                session.restore(moves.size, it.evaluation, it.pass)
            }
        }
        val stored = plies.map { row ->
            StoredReviewPly(
                ply = row.ply,
                scoreAfter = EvaluationCodec.fromColumns(row.playedEvalCp, row.playedMateIn),
                bestMove = row.bestMove,
                classification = MoveClassification.fromName(row.classification),
                expectedPointsLoss = row.expectedPointsLoss,
                depth = row.depth,
                nodes = row.nodes,
                timeMillis = row.timeMillis,
            )
        }
        val storedByPly = stored.associateBy { it.ply }
        val complete = session.evaluations().size == positions.size
        val reviewed: List<ReviewedMove>
        val summary: ReviewSummary
        if (complete && record.modelVersion == ReviewModelVersion.CURRENT) {
            // Full data: rebuild with engine lines, keeping each stored verdict exactly as made.
            reviewed = session.reviewedMoves().map { move ->
                val row = storedByPly[move.ply]
                val kept = row?.classification ?: move.classification
                val loss = row?.expectedPointsLoss ?: move.expectedPointsLoss
                if (kept == move.classification && loss == move.expectedPointsLoss) move
                else move.copy(classification = kept, expectedPointsLoss = loss)
            }
            val startWhite = dev.lumenchess.analysis.eval.ExpectedPoints.forWhite(
                session.evaluations().getValue(0).first.score, positions[0].sideToMove,
            )
            summary = ReviewSummarizer.summarize(reviewed, startWhite)
        } else {
            val rebuilt = ReviewReconstruction.fromLightweight(game.tree.startPosition, moves, stored) ?: return null
            reviewed = rebuilt.moves
            summary = rebuilt.summary()
        }
        val lastBook = book.lastBookIndex(positions).takeIf { it > 0 }
        return LoadedReview(
            record = record,
            moves = reviewed,
            summary = summary,
            evaluations = cached,
            opening = book.identify(positions),
            lastBookPly = lastBook?.minus(1),
            hasEngineLines = cached.isNotEmpty(),
        )
    }
}

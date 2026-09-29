package dev.lumenchess.analysis.explorer

import dev.lumenchess.core.chess.GameResult
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position

/** A game's mainline for explorer indexing. */
data class IndexedGame(
    val start: Position,
    val moves: List<Move>,
    val result: GameResult?,
    val playedAtEpochMillis: Long? = null,
)

/** How one move has scored from a position. Counts are always from White's point of view. */
data class ExplorerMove(
    val move: Move,
    val games: Int,
    val whiteWins: Int,
    val draws: Int,
    val blackWins: Int,
    val lastPlayedEpochMillis: Long?,
) {
    val whiteShare: Double get() = if (games == 0) 0.0 else whiteWins.toDouble() / games
    val drawShare: Double get() = if (games == 0) 0.0 else draws.toDouble() / games
    val blackShare: Double get() = if (games == 0) 0.0 else blackWins.toDouble() / games
}

/**
 * Offline move statistics over a set of games (the user's own library), keyed by position so
 * transpositions merge. Games without a result count as played but not as wins, draws or losses.
 */
class ExplorerIndex private constructor(
    private val byPosition: Map<Long, Map<Move, ExplorerMove>>,
    val gameCount: Int,
) {
    fun movesAt(position: Position): List<ExplorerMove> =
        byPosition[position.repetitionKey]?.values.orEmpty().sortedWith(
            compareByDescending<ExplorerMove> { it.games }.thenByDescending { it.lastPlayedEpochMillis ?: 0L },
        )

    fun gamesAt(position: Position): Int = movesAt(position).sumOf { it.games }

    companion object {
        const val DEFAULT_MAX_PLY = 40

        fun build(games: Iterable<IndexedGame>, maxPly: Int = DEFAULT_MAX_PLY): ExplorerIndex {
            val table = HashMap<Long, HashMap<Move, ExplorerMove>>()
            var count = 0
            for (game in games) {
                count += 1
                var position = game.start
                for ((ply, move) in game.moves.withIndex()) {
                    if (ply >= maxPly) break
                    if (MoveGenerator.legalMoves(position).none { it == move }) break
                    val moves = table.getOrPut(position.repetitionKey) { HashMap() }
                    val previous = moves[move]
                    moves[move] = ExplorerMove(
                        move = move,
                        games = (previous?.games ?: 0) + 1,
                        whiteWins = (previous?.whiteWins ?: 0) + if (game.result == GameResult.WHITE_WIN) 1 else 0,
                        draws = (previous?.draws ?: 0) + if (game.result == GameResult.DRAW) 1 else 0,
                        blackWins = (previous?.blackWins ?: 0) + if (game.result == GameResult.BLACK_WIN) 1 else 0,
                        lastPlayedEpochMillis = maxOfNullable(previous?.lastPlayedEpochMillis, game.playedAtEpochMillis),
                    )
                    position = MoveGenerator.applyLegalMove(position, move)
                }
            }
            return ExplorerIndex(table, count)
        }

        private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
            a == null -> b
            b == null -> a
            else -> maxOf(a, b)
        }
    }
}

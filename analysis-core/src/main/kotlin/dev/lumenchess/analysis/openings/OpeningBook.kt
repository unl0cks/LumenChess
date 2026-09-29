package dev.lumenchess.analysis.openings

import dev.lumenchess.core.chess.GameTree
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.San
import dev.lumenchess.core.chess.Variant

/** A named opening: ECO code, English name and the move sequence that defines it. */
data class Opening(
    val eco: String,
    val name: String,
    val moves: List<Move>,
) {
    /** "Sicilian Defense" from "Sicilian Defense: Najdorf Variation". */
    val family: String get() = name.substringBefore(':').trim()

    /** "Najdorf Variation" (or null for a family-level entry). */
    val variation: String? get() = name.substringAfter(':', "").trim().takeIf { it.isNotEmpty() }
}

/**
 * Offline opening identification from the CC0 Lichess `chess-openings` data set.
 *
 * Lookup is by position, not by move prefix, so transpositions resolve to the same name (1.d4 Nf6
 * 2.c4 e6 and 1.c4 e6 2.d4 Nf6 are one opening). Only Standard chess is identified; Chess960 start
 * positions are not in the data set.
 */
class OpeningBook private constructor(
    private val byPosition: Map<Long, Opening>,
    val size: Int,
) {
    /** The opening whose defining position this is, if any. */
    fun at(position: Position): Opening? =
        if (position.variant != Variant.STANDARD) null else byPosition[position.repetitionKey]

    fun isBookPosition(position: Position): Boolean = at(position) != null

    /**
     * Walks a sequence of positions (a game's mainline) and returns the most specific opening
     * reached, i.e. the last position on the line that the data set names.
     */
    fun identify(positions: List<Position>): Opening? {
        var found: Opening? = null
        for (position in positions) {
            at(position)?.let { found = it }
        }
        return found
    }

    fun identify(tree: GameTree): Opening? =
        identify(listOf(tree.root.position) + tree.mainline().map { it.position })

    /** Index (in [positions]) of the last position that is still a named book position, or -1. */
    fun lastBookIndex(positions: List<Position>): Int {
        var last = -1
        positions.forEachIndexed { index, position -> if (isBookPosition(position)) last = index }
        return last
    }

    /** Every opening, e.g. for an "opening family" picker. */
    fun openings(): Collection<Opening> = byPosition.values

    companion object {
        private val RESOURCES = listOf("a", "b", "c", "d", "e").map { "/openings/$it.tsv" }

        /** Loads the bundled data set from the classpath (works on the JVM and inside the APK). */
        fun bundled(): OpeningBook {
            val texts = RESOURCES.map { path ->
                val stream = OpeningBook::class.java.getResourceAsStream(path)
                    ?: error("Opening data $path is missing from the build")
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            return fromTsv(texts)
        }

        /**
         * Builds the index from `eco \t name \t pgn` files. When several entries reach the same
         * position the longest (most specific) definition wins; ties keep the first.
         */
        fun fromTsv(texts: List<String>): OpeningBook {
            val best = HashMap<Long, Pair<Opening, Int>>()
            var count = 0
            for (text in texts) {
                for (line in text.lineSequence()) {
                    val fields = line.split('\t')
                    if (fields.size < 3 || fields[0] == "eco") continue
                    val moves = parseMoves(fields[2]) ?: continue
                    val opening = Opening(fields[0].trim(), fields[1].trim(), moves)
                    var position = Position.initial()
                    for (move in moves) position = MoveGenerator.applyLegalMove(position, move)
                    val key = position.repetitionKey
                    val existing = best[key]
                    if (existing == null || moves.size > existing.second) best[key] = opening to moves.size
                    count += 1
                }
            }
            return OpeningBook(best.mapValues { it.value.first }, count)
        }

        private fun parseMoves(pgn: String): List<Move>? {
            var position = Position.initial()
            val moves = ArrayList<Move>()
            for (token in pgn.split(' ')) {
                val san = token.trim()
                if (san.isEmpty() || san.endsWith('.')) continue
                val move = runCatching { San.parse(position, san) }.getOrNull() ?: return null
                moves += move
                position = MoveGenerator.applyLegalMove(position, move)
            }
            return moves
        }
    }
}

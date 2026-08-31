package dev.lumenchess.arena

import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position

data class ArenaOpeningLine(
    val id: String,
    val name: String,
    val familyId: String,
    val moves: List<String>,
)

object ArenaOpeningCatalog {
    val lines: List<ArenaOpeningLine> = listOf(
        ArenaOpeningLine(
            "ruy-lopez",
            "Ruy Lopez",
            "kings-pawn",
            listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6", "e1g1", "f8e7", "f1e1", "b7b5"),
        ),
        ArenaOpeningLine(
            "sicilian-najdorf",
            "Sicilian Defence",
            "kings-pawn",
            listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6", "b1c3", "a7a6", "f2f3", "e7e5"),
        ),
        ArenaOpeningLine(
            "french-classical",
            "French Defence",
            "kings-pawn",
            listOf("e2e4", "e7e6", "d2d4", "d7d5", "b1c3", "g8f6", "c1g5", "f8e7", "e4e5", "f6d7", "g5e7", "d8e7"),
        ),
        ArenaOpeningLine(
            "queens-gambit",
            "Queen's Gambit",
            "queens-pawn",
            listOf("d2d4", "d7d5", "c2c4", "e7e6", "b1c3", "g8f6", "c1g5", "f8e7", "e2e3", "e8g8", "g1f3", "b8d7"),
        ),
        ArenaOpeningLine(
            "slav",
            "Slav Defence",
            "queens-pawn",
            listOf("d2d4", "d7d5", "c2c4", "c7c6", "g1f3", "g8f6", "b1c3", "d5c4", "e2e4", "b7b5", "e4e5", "f6d5"),
        ),
        ArenaOpeningLine(
            "kings-indian",
            "King's Indian Defence",
            "queens-pawn",
            listOf("d2d4", "g8f6", "c2c4", "g7g6", "b1c3", "f8g7", "e2e4", "d7d6", "g1f3", "e8g8", "f1e2", "e7e5"),
        ),
    )

    val families: Map<String, String> = linkedMapOf(
        "kings-pawn" to "King's Pawn",
        "queens-pawn" to "Queen's Pawn",
    )

    fun byFamily(familyId: String?): List<ArenaOpeningLine> =
        lines.filter { it.familyId == familyId }

    fun resolveRandom(handoffPlies: Int, randomInt: (Int) -> Int): ResolvedArenaOpening =
        resolve(lines[randomInt(lines.size)], handoffPlies, ArenaOpeningMode.RANDOM_OPENING)

    fun resolveFamily(
        familyId: String,
        handoffPlies: Int,
        randomInt: (Int) -> Int,
    ): ResolvedArenaOpening {
        val choices = byFamily(familyId)
        require(choices.isNotEmpty()) { "Unknown opening family '$familyId'" }
        return resolve(choices[randomInt(choices.size)], handoffPlies, ArenaOpeningMode.OPENING_FAMILY)
    }

    private fun resolve(
        line: ArenaOpeningLine,
        handoffPlies: Int,
        mode: ArenaOpeningMode,
    ): ResolvedArenaOpening {
        var position = Position.initial()
        val applied = line.moves.take(handoffPlies).map { token ->
            val move = Move.parseUci(token)
            position = MoveGenerator.applyLegalMove(position, move)
            move
        }
        return ResolvedArenaOpening(
            mode = mode,
            label = line.name,
            familyId = line.familyId,
            position = position,
            appliedMoves = applied,
        )
    }
}

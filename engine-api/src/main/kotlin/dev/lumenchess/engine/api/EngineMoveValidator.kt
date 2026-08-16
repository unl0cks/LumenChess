package dev.lumenchess.engine.api

import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Position

sealed interface EngineMoveValidation {
    data class Accepted(val move: Move) : EngineMoveValidation
    data object StaleSearch : EngineMoveValidation
    data object StalePosition : EngineMoveValidation
    data object NoMove : EngineMoveValidation
    data class MalformedMove(val uci: String) : EngineMoveValidation
    data class IllegalMove(val uci: String) : EngineMoveValidation
}

object EngineMoveValidator {
    fun validate(
        position: Position,
        expectedSearchId: EngineSearchId,
        expectedPositionRevision: PositionRevision,
        result: EngineSearchResult,
    ): EngineMoveValidation {
        if (result.searchId != expectedSearchId) return EngineMoveValidation.StaleSearch
        if (result.positionRevision != expectedPositionRevision) return EngineMoveValidation.StalePosition

        val legalMoves = MoveGenerator.legalMoves(position)
        val bestMoveUci = result.bestMoveUci
        if (bestMoveUci == null) {
            return if (legalMoves.isEmpty()) EngineMoveValidation.NoMove else EngineMoveValidation.IllegalMove("0000")
        }

        val candidate = try {
            Move.parseUci(bestMoveUci)
        } catch (_: IllegalArgumentException) {
            return EngineMoveValidation.MalformedMove(bestMoveUci)
        }

        val legal = legalMoves.firstOrNull { it == candidate }
        return if (legal != null) {
            EngineMoveValidation.Accepted(legal)
        } else {
            EngineMoveValidation.IllegalMove(bestMoveUci)
        }
    }
}

package dev.lumenchess.engine.api

import dev.lumenchess.core.chess.Fen
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineMoveValidatorRegressionTest {
    @Test
    fun noMoveFromNonTerminalPositionIsRejected() {
        val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val result = EngineSearchResult(
            searchId = EngineSearchId(1),
            positionRevision = PositionRevision(0),
            bestMoveUci = null,
        )

        assertEquals(
            EngineMoveValidation.IllegalMove("0000"),
            EngineMoveValidator.validate(position, EngineSearchId(1), PositionRevision(0), result),
        )
    }

    @Test
    fun malformedUciIsRejectedBeforeLegalityComparison() {
        val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val result = EngineSearchResult(
            searchId = EngineSearchId(2),
            positionRevision = PositionRevision(3),
            bestMoveUci = "not-a-move",
        )

        assertEquals(
            EngineMoveValidation.MalformedMove("not-a-move"),
            EngineMoveValidator.validate(position, EngineSearchId(2), PositionRevision(3), result),
        )
    }
}

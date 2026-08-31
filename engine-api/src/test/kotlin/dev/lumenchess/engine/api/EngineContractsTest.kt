package dev.lumenchess.engine.api

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineContractsTest {
    @Test
    fun searchInfoCarriesTheSearchCorrelationIdentity() {
        val info = EngineSearchInfo(
            searchId = EngineSearchId(41),
            positionRevision = PositionRevision(7),
            depth = 18,
            score = UciScore.Centipawns(73),
            nodes = 12_345,
            nodesPerSecond = 987_000,
            principalVariation = listOf("e2e4", "e7e5"),
        )

        assertEquals(EngineSearchId(41), info.searchId)
        assertEquals(PositionRevision(7), info.positionRevision)
        assertEquals(UciScore.Centipawns(73), info.score)
        assertEquals(listOf("e2e4", "e7e5"), info.principalVariation)
    }

    @Test
    fun capabilitiesAreTypedAndVariantAware() {
        val capabilities = EngineCapabilities(
            variants = setOf(Variant.STANDARD, Variant.CHESS960),
            multiPv = EngineMultiPvCapability(maxLines = 8),
            supportsPonder = true,
            strength = EngineStrengthCapability.EloRange(400, 3000),
        )

        assertTrue(capabilities.supports(Variant.STANDARD))
        assertTrue(capabilities.supports(Variant.CHESS960))
        assertEquals(8, capabilities.multiPv?.maxLines)
        assertEquals(EngineStrengthCapability.EloRange(400, 3000), capabilities.strength)
    }

    @Test
    fun searchIdentityRejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { EngineSearchId(0) }
        assertFailsWith<IllegalArgumentException> { PositionRevision(-1) }
        assertEquals(1L, EngineSearchId(1).value)
        assertEquals(0L, PositionRevision(0).value)
    }

    @Test
    fun searchRequestCarriesExactPositionAndRevision() {
        val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val request = EngineSearchRequest(
            searchId = EngineSearchId(9),
            positionRevision = PositionRevision(42),
            position = position,
            limits = EngineSearchLimits(depth = 16, nodes = 100_000),
            multiPv = 3,
        )

        assertEquals(position, request.position)
        assertEquals(PositionRevision(42), request.positionRevision)
        assertEquals(16, request.limits.depth)
        assertEquals(3, request.multiPv)
    }

    @Test
    fun invalidSearchLimitsAndMultiPvAreRejected() {
        assertFailsWith<IllegalArgumentException> { EngineSearchLimits(depth = 0) }
        assertFailsWith<IllegalArgumentException> { EngineSearchLimits(nodes = 0) }
        assertFailsWith<IllegalArgumentException> { EngineSearchLimits(moveTimeMillis = 0) }
        assertFailsWith<IllegalArgumentException> {
            EngineSearchRequest(
                searchId = EngineSearchId(1),
                positionRevision = PositionRevision(0),
                position = Fen.parse("8/8/8/8/8/8/4K3/7k w - - 0 1"),
                limits = EngineSearchLimits(depth = 1),
                multiPv = 0,
            )
        }
    }

    @Test
    fun validatorRejectsStaleOrIllegalEngineMovesAndAcceptsLegalMove() {
        val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val expectedSearch = EngineSearchId(7)
        val expectedRevision = PositionRevision(11)

        val legal = EngineSearchResult(
            searchId = expectedSearch,
            positionRevision = expectedRevision,
            bestMoveUci = "e2e4",
        )
        assertEquals(
            EngineMoveValidation.Accepted(Move.parseUci("e2e4")),
            EngineMoveValidator.validate(position, expectedSearch, expectedRevision, legal),
        )

        val staleSearch = legal.copy(searchId = EngineSearchId(8))
        assertEquals(EngineMoveValidation.StaleSearch, EngineMoveValidator.validate(position, expectedSearch, expectedRevision, staleSearch))

        val staleRevision = legal.copy(positionRevision = PositionRevision(12))
        assertEquals(EngineMoveValidation.StalePosition, EngineMoveValidator.validate(position, expectedSearch, expectedRevision, staleRevision))

        val illegal = legal.copy(bestMoveUci = "e2e5")
        assertEquals(EngineMoveValidation.IllegalMove("e2e5"), EngineMoveValidator.validate(position, expectedSearch, expectedRevision, illegal))
    }

    @Test
    fun validatorTreatsNoMoveAsTypedNoMove() {
        val position = Fen.parse("7k/5Q2/7K/8/8/8/8/8 b - - 0 1")
        val result = EngineSearchResult(
            searchId = EngineSearchId(1),
            positionRevision = PositionRevision(3),
            bestMoveUci = null,
        )

        assertEquals(
            EngineMoveValidation.NoMove,
            EngineMoveValidator.validate(position, EngineSearchId(1), PositionRevision(3), result),
        )
    }

    @Test
    fun chess960CastlingValidationUsesCoreUciChess960Representation() {
        val position = Fen.parse(
            "4k3/8/8/8/8/8/8/R3KR2 w FA - 0 1",
            Variant.CHESS960,
        )
        val result = EngineSearchResult(
            searchId = EngineSearchId(2),
            positionRevision = PositionRevision(5),
            bestMoveUci = "e1f1",
        )

        val validation = EngineMoveValidator.validate(position, EngineSearchId(2), PositionRevision(5), result)
        assertTrue(validation is EngineMoveValidation.Accepted)
        assertEquals(Move.parseUci("e1f1"), validation.move)
        assertFalse(validation.move.to.algebraic == "g1")
    }
}

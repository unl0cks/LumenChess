package dev.lumenchess.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StartingPositionTest {
    @Test fun fenAndPgnRoundTripThroughOneValidatedBoundary() {
        val tree = StartingPositionResolver.importPgn("[Event \"M24\"]\n\n1. e4 e5 2. Nf3 *")
        val exported = StartingPositionResolver.exportPgn(tree)
        val roundTrip = StartingPositionResolver.importPgn(exported)
        assertEquals(tree.mainline().map { it.move }, roundTrip.mainline().map { it.move })
        assertEquals(tree.startPosition, roundTrip.startPosition)
        assertEquals(Position.initial(), StartingPositionResolver.resolve(StartingPositionInput.Normal))
    }

    @Test fun oddsRemoveMaterialButNeverKings() {
        val pawn = Square.parse("a2")
        val position = StartingPositionResolver.resolve(StartingPositionInput.Odds(removed = setOf(pawn)))
        assertEquals(null, position[pawn])
        assertFailsWith<IllegalArgumentException> {
            StartingPositionResolver.resolve(StartingPositionInput.Odds(removed = setOf(Square.parse("e1"))))
        }
    }

    @Test fun malformedFenAndIllegalPgnAreRejectedAtomically() {
        assertFailsWith<IllegalArgumentException> {
            StartingPositionResolver.resolve(StartingPositionInput.FenText("8/8/8/8/8/8/8/8 w - - 0 1"))
        }
        assertFailsWith<PgnParseException> { StartingPositionResolver.importPgn("1. e5 *") }
    }
}

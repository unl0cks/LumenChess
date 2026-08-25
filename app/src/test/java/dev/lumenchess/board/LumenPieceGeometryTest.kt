package dev.lumenchess.board

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LumenPieceGeometryTest {
    private val approvedPaintedBoundsAt160 = mapOf(
        LumenPieceKind.PAWN to (76f to 116f),
        LumenPieceKind.ROOK to (94f to 126f),
        LumenPieceKind.KNIGHT to (86f to 133f),
        LumenPieceKind.BISHOP to (84f to 137f),
        LumenPieceKind.QUEEN to (92f to 143f),
        LumenPieceKind.KING to (90f to 146f),
    )

    @Test
    fun approvedAS2MastersProduceTheApprovedPaintedBounds() {
        approvedPaintedBoundsAt160.forEach { (kind, expected) ->
            val bounds = LumenPieceGeometry.spec(kind).paintedBounds(squarePx = 160f)
            assertTrue("$kind width should reproduce A-S2: $bounds", abs(bounds.width - expected.first) <= 2f)
            assertTrue("$kind height should reproduce A-S2: $bounds", abs(bounds.height - expected.second) <= 2f)
            assertTrue("$kind must remain inside its square: $bounds", bounds.left >= 0f && bounds.top >= 0f)
            assertTrue("$kind must remain inside its square: $bounds", bounds.right <= 160f && bounds.bottom <= 160f)
        }
    }

    @Test
    fun mastersStayInsideAllObservedRoundedCellWidths() {
        listOf(159f, 160f, 161f).forEach { squarePx ->
            LumenPieceKind.entries.forEach { kind ->
                val bounds = LumenPieceGeometry.spec(kind).paintedBounds(squarePx)
                assertTrue("$kind left overflow at $squarePx px: $bounds", bounds.left >= 0f)
                assertTrue("$kind top overflow at $squarePx px: $bounds", bounds.top >= 0f)
                assertTrue("$kind right overflow at $squarePx px: $bounds", bounds.right <= squarePx)
                assertTrue("$kind bottom overflow at $squarePx px: $bounds", bounds.bottom <= squarePx)
            }
        }
    }

    @Test
    fun royalAndMajorHierarchyRemainsClassical() {
        val heights = LumenPieceKind.entries.associateWith {
            LumenPieceGeometry.spec(it).paintedBounds(160f).height
        }
        assertTrue(heights.getValue(LumenPieceKind.PAWN) < heights.getValue(LumenPieceKind.ROOK))
        assertTrue(heights.getValue(LumenPieceKind.ROOK) < heights.getValue(LumenPieceKind.KNIGHT))
        assertTrue(heights.getValue(LumenPieceKind.KNIGHT) < heights.getValue(LumenPieceKind.BISHOP))
        assertTrue(heights.getValue(LumenPieceKind.BISHOP) < heights.getValue(LumenPieceKind.QUEEN))
        assertTrue(heights.getValue(LumenPieceKind.QUEEN) < heights.getValue(LumenPieceKind.KING))
    }

    @Test
    fun knightCarriesOnlyTheApprovedSmallOpticalOffset() {
        assertEquals(2f, LumenPieceGeometry.spec(LumenPieceKind.KNIGHT).optics.xOffsetAt160, 0.001f)
        LumenPieceKind.entries.filterNot { it == LumenPieceKind.KNIGHT }.forEach { kind ->
            assertEquals("$kind should stay mathematically centered", 0f, LumenPieceGeometry.spec(kind).optics.xOffsetAt160, 0.001f)
        }
    }

    @Test
    fun everyMasterIsAClosedResolutionIndependentContour() {
        LumenPieceKind.entries.forEach { kind ->
            val contours = LumenPieceGeometry.spec(kind).contours
            assertTrue("$kind requires at least one contour", contours.isNotEmpty())
            contours.forEachIndexed { index, contour ->
                assertTrue("$kind contour $index requires at least three points", contour.size >= 6)
                assertEquals("$kind contour $index must contain x/y pairs", 0, contour.size % 2)
                contour.forEach { coordinate ->
                    assertTrue("$kind contour $index coordinate must be normalized: $coordinate", coordinate in 0f..1f)
                }
            }
        }
    }
}

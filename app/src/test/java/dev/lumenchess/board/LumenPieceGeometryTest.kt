package dev.lumenchess.board

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            assertTrue(abs(bounds.width - expected.first) <= 2f, "$kind width should reproduce A-S2: $bounds")
            assertTrue(abs(bounds.height - expected.second) <= 2f, "$kind height should reproduce A-S2: $bounds")
            assertTrue(bounds.left >= 0f && bounds.top >= 0f, "$kind must remain inside its square: $bounds")
            assertTrue(bounds.right <= 160f && bounds.bottom <= 160f, "$kind must remain inside its square: $bounds")
        }
    }

    @Test
    fun mastersStayInsideAllObservedRoundedCellWidths() {
        listOf(159f, 160f, 161f).forEach { squarePx ->
            LumenPieceKind.entries.forEach { kind ->
                val bounds = LumenPieceGeometry.spec(kind).paintedBounds(squarePx)
                assertTrue(bounds.left >= 0f, "$kind left overflow at $squarePx px: $bounds")
                assertTrue(bounds.top >= 0f, "$kind top overflow at $squarePx px: $bounds")
                assertTrue(bounds.right <= squarePx, "$kind right overflow at $squarePx px: $bounds")
                assertTrue(bounds.bottom <= squarePx, "$kind bottom overflow at $squarePx px: $bounds")
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
            assertEquals(0f, LumenPieceGeometry.spec(kind).optics.xOffsetAt160, 0.001f, "$kind should stay mathematically centered")
        }
    }

    @Test
    fun everyMasterIsAClosedResolutionIndependentContour() {
        LumenPieceKind.entries.forEach { kind ->
            val contours = LumenPieceGeometry.spec(kind).contours
            assertTrue(contours.isNotEmpty(), "$kind requires at least one contour")
            contours.forEachIndexed { index, contour ->
                assertTrue(contour.size >= 6, "$kind contour $index requires at least three points")
                assertEquals(0, contour.size % 2, "$kind contour $index must contain x/y pairs")
                contour.forEach { coordinate ->
                    assertTrue(coordinate in 0f..1f, "$kind contour $index coordinate must be normalized: $coordinate")
                }
            }
        }
    }

    @Test
    fun contourRetainsOnePhysicalPixelOfPreviewSeparation() {
        assertEquals(2f, lumenPieceEdgeWidthPx(93f), 0.001f)
        assertEquals(2f, lumenPieceEdgeWidthPx(160f), 0.001f)
        assertEquals(2.5f, lumenPieceEdgeWidthPx(200f), 0.001f)
    }
}

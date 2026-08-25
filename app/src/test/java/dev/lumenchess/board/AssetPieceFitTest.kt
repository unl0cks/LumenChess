package dev.lumenchess.board

import dev.lumenchess.core.chess.PieceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssetPieceFitTest {
    @Test
    fun `fit uses alpha bounds and preserves source aspect ratio`() {
        val source = PixelBounds(left = 20, top = 10, rightExclusive = 100, bottomExclusive = 130)

        val fit = AssetPieceFitter.fit(source, slotWidth = 160, slotHeight = 160, PieceType.QUEEN)

        assertEquals(source.width.toFloat() / source.height, fit.width / fit.height, 0.0001f)
        assertTrue(fit.left >= 0f)
        assertTrue(fit.top >= 0f)
        assertTrue(fit.right <= 160f)
        assertTrue(fit.bottom <= 160f)
        assertTrue(fit.bottom > 150f, "asset art should sit on a planted baseline")
    }

    @Test
    fun `all square rounding widths remain bounded and isotropic`() {
        val source = PixelBounds(left = 7, top = 14, rightExclusive = 143, bottomExclusive = 147)

        for (size in 159..161) {
            for (type in PieceType.entries) {
                val fit = AssetPieceFitter.fit(source, size, size, type)
                assertTrue(fit.left >= 0f, "$type left at $size")
                assertTrue(fit.top >= 0f, "$type top at $size")
                assertTrue(fit.right <= size, "$type right at $size")
                assertTrue(fit.bottom <= size, "$type bottom at $size")
                assertEquals(source.width.toFloat() / source.height, fit.width / fit.height, 0.0001f)
            }
        }
    }

    @Test
    fun `pawn remains smaller while major pieces own the slot`() {
        val source = PixelBounds(left = 0, top = 0, rightExclusive = 100, bottomExclusive = 140)

        val pawn = AssetPieceFitter.fit(source, 160, 160, PieceType.PAWN)
        val king = AssetPieceFitter.fit(source, 160, 160, PieceType.KING)

        assertTrue(pawn.height < king.height)
        assertTrue(pawn.height >= 124f)
        assertTrue(king.height >= 150f)
    }
}

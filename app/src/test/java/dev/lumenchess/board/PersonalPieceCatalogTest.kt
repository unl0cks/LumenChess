package dev.lumenchess.board

import dev.lumenchess.BuildConfig
import dev.lumenchess.settings.AppearanceSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PersonalPieceCatalogTest {
    @Test
    fun `runtime catalog mirrors only generated complete private styles`() {
        val privateStyles = PieceSetCatalog.builtIns.filter { it.id.startsWith("private.chesscom.") }

        assertEquals(listOf("lumen-vector", "lumen-outline"), PieceSetCatalog.builtIns.take(2).map { it.id })
        if (BuildConfig.LUMEN_PERSONAL_ASSETS) {
            assertEquals(39, privateStyles.size)
            assertEquals(39, privateStyles.map { it.id }.distinct().size)
            assertEquals("private.chesscom.ejgfv", privateStyles.first().id)
            assertEquals("private.chesscom.3d_plastic", privateStyles.last().id)
        } else {
            assertTrue(privateStyles.isEmpty())
        }
    }

    @Test
    fun `generated metadata decodes stable available styles in build order`() {
        val encoded = listOf(
            "private.chesscom.ejgfv|ejgfv|Neo",
            "private.chesscom.3d_staunton|3d_staunton|3D Staunton",
        ).joinToString(";")

        val decoded = PersonalPieceMetadataCodec.decode(encoded)

        assertEquals(listOf("Neo", "3D Staunton"), decoded.map { it.displayName })
        assertEquals("private.chesscom.ejgfv", decoded.first().id)
        assertEquals("pieces/ejgfv", decoded.first().assetDirectory)
    }

    @Test
    fun `invalid duplicate and traversal-like generated metadata is ignored`() {
        val encoded = listOf(
            "private.chesscom.ejgfv|ejgfv|Neo",
            "private.chesscom.ejgfv|wood|Duplicate",
            "private.chesscom.escape|../escape|Escape",
            "bad id|classic|Bad",
            "private.chesscom.blank|classic|",
        ).joinToString(";")

        val decoded = PersonalPieceMetadataCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals("private.chesscom.ejgfv", decoded.single().id)
    }

    @Test
    fun `unavailable private selection renders public default without mutating stored id`() {
        val stored = AppearanceSettings(pieceSetId = "private.chesscom.3d_plastic")
        val available = listOf("lumen-vector", "lumen-outline")

        val effective = PieceSelectionResolver.effectiveId(stored.pieceSetId, available)

        assertEquals(AppearanceSettings.DEFAULT_PIECE_SET_ID, effective)
        assertEquals("private.chesscom.3d_plastic", stored.pieceSetId)
    }

    @Test
    fun `private selection restores when the same stable id becomes available again`() {
        val storedId = "private.chesscom.3d_plastic"
        val unavailable = listOf("lumen-vector", "lumen-outline")
        val restored = unavailable + storedId

        assertEquals("lumen-vector", PieceSelectionResolver.effectiveId(storedId, unavailable))
        assertEquals(storedId, PieceSelectionResolver.effectiveId(storedId, restored))
    }

    @Test
    fun `legacy personal ids map only when their replacement is available`() {
        val available = listOf("lumen-vector", "lumen-outline", "private.chesscom.3d_staunton")

        assertEquals(
            "private.chesscom.3d_staunton",
            PieceSelectionResolver.effectiveId("personal-3d-staunton", available),
        )
        assertEquals(
            "lumen-vector",
            PieceSelectionResolver.effectiveId("personal-marble", available),
        )
    }

    @Test
    fun `private namespace is stable and does not depend on list position`() {
        val first = PersonalPieceMetadataCodec.decode(
            "private.chesscom.wood|wood|Wood;private.chesscom.classic|classic|Classic",
        )
        val reversed = PersonalPieceMetadataCodec.decode(
            "private.chesscom.classic|classic|Classic;private.chesscom.wood|wood|Wood",
        )

        assertTrue(first.any { it.id == "private.chesscom.wood" })
        assertTrue(reversed.any { it.id == "private.chesscom.wood" })
        assertFalse(first.any { it.id.endsWith(".0") || it.id.endsWith(".1") })
    }
}

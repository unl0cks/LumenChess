package dev.lumenchess.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.lumenchess.BuildConfig
import dev.lumenchess.core.chess.Piece

interface PieceSet {
    val id: String
    val displayName: String

    @Composable
    fun Piece(piece: Piece, tint: Color, modifier: Modifier = Modifier)
}

object PieceSetCatalog {
    private val publicBuiltIns: List<PieceSet> = listOf(LumenVectorPieceSet, LumenOutlinePieceSet)
    private val personalBuiltIns: List<PieceSet> = PersonalPieceMetadataCodec
        .decode(BuildConfig.LUMEN_PERSONAL_PIECE_STYLES)
        .map { metadata ->
            AssetPieceSet(
                id = metadata.id,
                displayName = metadata.displayName,
                assetDirectory = metadata.assetDirectory,
                assetFingerprint = BuildConfig.LUMEN_PERSONAL_ASSET_FINGERPRINT,
            )
        }

    val builtIns: List<PieceSet> = publicBuiltIns + personalBuiltIns

    fun effectiveId(id: String): String = PieceSelectionResolver.effectiveId(id, builtIns.map(PieceSet::id))

    fun definition(id: String): PieceSet {
        val effectiveId = effectiveId(id)
        return builtIns.firstOrNull { it.id == effectiveId } ?: LumenVectorPieceSet
    }
}

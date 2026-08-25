package dev.lumenchess.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.lumenchess.BuildConfig
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.settings.AppearanceSettings

interface PieceSet {
    val id: String
    val displayName: String
    val boardSlotFraction: Float get() = 0.90f

    @Composable
    fun Piece(piece: Piece, tint: Color, modifier: Modifier = Modifier)
}

object PieceSetCatalog {
    private val PersonalTournament = AssetPieceSet(
        id = AppearanceSettings.DEFAULT_PIECE_SET_ID,
        displayName = "Tournament",
        assetDirectory = "pieces/tournament",
    )
    private val PersonalClassic = AssetPieceSet("personal-classic", "Classic", "pieces/classic")
    private val PersonalClub = AssetPieceSet("personal-club", "Club", "pieces/club")
    private val PersonalBases = AssetPieceSet("personal-bases", "Bases", "pieces/bases")
    private val PersonalStaunton3d = AssetPieceSet("personal-3d-staunton", "3D Staunton", "pieces/3d_staunton")
    private val PersonalWood = AssetPieceSet("personal-wood", "Wood", "pieces/wood")
    private val PersonalGameRoom = AssetPieceSet("personal-game-room", "Game Room", "pieces/game_room")
    private val PersonalMarble = AssetPieceSet("personal-marble", "Marble", "pieces/marble")

    private val publicBuiltIns: List<PieceSet> = listOf(LumenVectorPieceSet, LumenOutlinePieceSet)
    private val personalBuiltIns: List<PieceSet> = listOf(
        PersonalTournament,
        PersonalClassic,
        PersonalClub,
        PersonalBases,
        PersonalStaunton3d,
        PersonalWood,
        PersonalGameRoom,
        PersonalMarble,
        LumenOutlinePieceSet,
    )

    val builtIns: List<PieceSet> = if (BuildConfig.LUMEN_PERSONAL_ASSETS) personalBuiltIns else publicBuiltIns

    fun definition(id: String): PieceSet = builtIns.firstOrNull { it.id == id }
        ?: if (BuildConfig.LUMEN_PERSONAL_ASSETS) PersonalTournament else LumenVectorPieceSet
}

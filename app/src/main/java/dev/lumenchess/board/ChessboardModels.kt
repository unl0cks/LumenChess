package dev.lumenchess.board

import androidx.compose.ui.graphics.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Square

enum class ChessboardOrientation {
    WHITE,
    BLACK,
}

enum class PromotionPolicy {
    ALWAYS_ASK,
    AUTO_QUEEN,
}

data class ChessboardInput(
    val tapEnabled: Boolean = true,
    val dragEnabled: Boolean = true,
    val promotionPolicy: PromotionPolicy = PromotionPolicy.ALWAYS_ASK,
)

data class ChessboardHighlights(
    val lastMove: Move? = null,
    val premove: Move? = null,
    val pendingPremoveOrigin: Square? = null,
    val extraSquares: Set<Square> = emptySet(),
    val showLegalMoves: Boolean = true,
    val showCheck: Boolean = true,
) {
    internal fun feedbackFor(square: Square): BoardSquareFeedback = BoardSquareFeedback(
        history = when (square) {
            lastMove?.from -> BoardHistoryRole.ORIGIN
            lastMove?.to -> BoardHistoryRole.DESTINATION
            else -> BoardHistoryRole.NONE
        },
        premove = when (square) {
            premove?.from -> BoardPremoveRole.ORIGIN
            premove?.to -> BoardPremoveRole.DESTINATION
            pendingPremoveOrigin -> BoardPremoveRole.PENDING_ORIGIN
            else -> BoardPremoveRole.NONE
        },
    )
}

enum class ChessboardArrowStyle {
    PRIMARY,
    SECONDARY,
    WARNING,
}

data class ChessboardArrow(
    val from: Square,
    val to: Square,
    val style: ChessboardArrowStyle = ChessboardArrowStyle.PRIMARY,
)

data class ChessboardPalette(
    val lightSquare: Color,
    val darkSquare: Color,
    val whitePiece: Color,
    val blackPiece: Color,
    val selected: Color,
    val legalMove: Color,
    val legalCapture: Color,
    val lastMove: Color,
    val check: Color,
    val premove: Color,
    val extraHighlight: Color,
    val primaryArrow: Color,
    val secondaryArrow: Color,
    val warningArrow: Color,
) {
    companion object {
        fun default(): ChessboardPalette = ChessboardPalette(
            lightSquare = Color(0xFFE7E6C8),
            darkSquare = Color(0xFF4E8191),
            whitePiece = Color(0xFFF0EBDD),
            blackPiece = Color(0xFF202224),
            selected = Color(0x8AD7C867),
            legalMove = Color(0x8A4A777D),
            legalCapture = Color(0xA05A7F86),
            lastMove = Color(0x72D7D16D),
            check = Color(0x9AD65D62),
            premove = Color(0x805F758F),
            extraHighlight = Color(0x705E9AAF),
            primaryArrow = Color(0xCC4F879B),
            secondaryArrow = Color(0xCC649A83),
            warningArrow = Color(0xCCD46C6C),
        )
    }
}

const val CHESSBOARD_TEST_TAG = "lumen-chessboard"
const val CHESSBOARD_ARROWS_TEST_TAG = "chessboard-arrows"

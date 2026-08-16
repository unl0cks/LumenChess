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
    val premoveSquares: Set<Square> = emptySet(),
    val extraSquares: Set<Square> = emptySet(),
    val showLegalMoves: Boolean = true,
    val showCheck: Boolean = true,
)

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
            lightSquare = Color(0xFFAFC0D2),
            darkSquare = Color(0xFF526A82),
            whitePiece = Color(0xFFF7F8FA),
            blackPiece = Color(0xFF111820),
            selected = Color(0x994D8DFF),
            legalMove = Color(0x995DCC8A),
            legalCapture = Color(0x99F0A35A),
            lastMove = Color(0x80FFD166),
            check = Color(0x99E45B5B),
            premove = Color(0x996F7DFF),
            extraHighlight = Color(0x8078C6FF),
            primaryArrow = Color(0xCC4D8DFF),
            secondaryArrow = Color(0xCC63C58B),
            warningArrow = Color(0xCCE56A6A),
        )
    }
}

const val CHESSBOARD_TEST_TAG = "lumen-chessboard"
const val CHESSBOARD_ARROWS_TEST_TAG = "chessboard-arrows"

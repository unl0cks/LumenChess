package dev.lumenchess.board

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.lumenchess.core.chess.Color as ChessColor
import dev.lumenchess.core.chess.Piece
import dev.lumenchess.core.chess.PieceType
import kotlin.math.min
import kotlin.math.roundToInt

/** Optional local/private piece renderer. Missing files always fall back to the public Lumen set. */
class AssetPieceSet(
    override val id: String,
    override val displayName: String,
    private val assetDirectory: String,
    private val assetFingerprint: String = "",
    private val fallback: PieceSet = LumenVectorPieceSet,
) : PieceSet {
    @Composable
    override fun Piece(piece: Piece, tint: Color, modifier: Modifier) {
        val assetPath = "$assetDirectory/${piece.assetToken()}.png"
        val context = androidx.compose.ui.platform.LocalContext.current
        val cached = remember(assetFingerprint, assetPath) {
            AssetPieceBitmapCache.load(
                assets = context.applicationContext.assets,
                key = "$assetFingerprint|$id|${piece.assetToken()}",
                path = assetPath,
            )
        }
        if (cached == null) {
            fallback.Piece(piece, tint, modifier)
            return
        }

        Canvas(modifier) {
            val destination = AssetPieceFitter.fit(
                source = cached.alphaBounds,
                slotWidth = size.width.roundToInt(),
                slotHeight = size.height.roundToInt(),
                pieceType = piece.type,
            )
            drawImage(
                image = cached.image,
                srcOffset = IntOffset(cached.alphaBounds.left, cached.alphaBounds.top),
                srcSize = IntSize(cached.alphaBounds.width, cached.alphaBounds.height),
                dstOffset = IntOffset(destination.left.roundToInt(), destination.top.roundToInt()),
                dstSize = IntSize(destination.width.roundToInt(), destination.height.roundToInt()),
                filterQuality = FilterQuality.High,
            )
        }
    }
}

internal data class PixelBounds(
    val left: Int,
    val top: Int,
    val rightExclusive: Int,
    val bottomExclusive: Int,
) {
    val width: Int get() = rightExclusive - left
    val height: Int get() = bottomExclusive - top
}

internal object AssetPieceFitter {
    private val heightByType = mapOf(
        PieceType.PAWN to 0.80f,
        PieceType.ROOK to 0.89f,
        PieceType.KNIGHT to 0.92f,
        PieceType.BISHOP to 0.94f,
        PieceType.QUEEN to 0.96f,
        PieceType.KING to 0.98f,
    )
    private const val MaxWidthFraction = 0.96f
    private const val BaselineFraction = 0.985f

    fun fit(source: PixelBounds, slotWidth: Int, slotHeight: Int, pieceType: PieceType): FitBounds {
        require(source.width > 0 && source.height > 0)
        require(slotWidth > 0 && slotHeight > 0)
        val targetHeight = slotHeight * heightByType.getValue(pieceType)
        val heightScale = targetHeight / source.height
        val widthScale = (slotWidth * MaxWidthFraction) / source.width
        val scale = min(heightScale, widthScale)
        val width = source.width * scale
        val height = source.height * scale
        val left = (slotWidth - width) / 2f
        val bottom = slotHeight * BaselineFraction
        return FitBounds(left = left, top = bottom - height, right = left + width, bottom = bottom)
    }
}

internal data class FitBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private data class CachedPieceBitmap(
    val image: ImageBitmap,
    val alphaBounds: PixelBounds,
    val byteCount: Int,
)

private object AssetPieceBitmapCache {
    private const val MaxCacheBytes = 32 * 1024 * 1024
    private val missingKeys = mutableSetOf<String>()
    private val cache = object : LruCache<String, CachedPieceBitmap>(MaxCacheBytes) {
        override fun sizeOf(key: String, value: CachedPieceBitmap): Int = value.byteCount
    }

    @Synchronized
    fun load(assets: AssetManager, key: String, path: String): CachedPieceBitmap? {
        cache.get(key)?.let { return it }
        if (key in missingKeys) return null
        val decoded = runCatching {
            assets.open(path).use(BitmapFactory::decodeStream)
        }.getOrNull()
        if (decoded == null) {
            missingKeys += key
            return null
        }
        val alphaBounds = decoded.findAlphaBounds()
        if (alphaBounds == null) {
            missingKeys += key
            return null
        }
        return CachedPieceBitmap(
            image = decoded.asImageBitmap(),
            alphaBounds = alphaBounds,
            byteCount = decoded.allocationByteCount,
        ).also { cache.put(key, it) }
    }
}

private fun Bitmap.findAlphaBounds(): PixelBounds? {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    pixels.forEachIndexed { index, pixel ->
        if ((pixel ushr 24) != 0) {
            val x = index % width
            val y = index / width
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            if (y > bottom) bottom = y
        }
    }
    return if (right < left || bottom < top) null else PixelBounds(left, top, right + 1, bottom + 1)
}

private fun Piece.assetToken(): String {
    val colorPrefix = if (color == ChessColor.WHITE) "w" else "b"
    val typeToken = when (type) {
        PieceType.KING -> "k"
        PieceType.QUEEN -> "q"
        PieceType.ROOK -> "r"
        PieceType.BISHOP -> "b"
        PieceType.KNIGHT -> "n"
        PieceType.PAWN -> "p"
    }
    return colorPrefix + typeToken
}

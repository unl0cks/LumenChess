package dev.lumenchess.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Central motion vocabulary for Lumen presentation.
 *
 * Layout-critical containers (especially the chessboard) must never consume these
 * tokens for size or position changes. Motion belongs to paint/translation state.
 */
object LumenMotion {
    const val InstantMs = 75
    const val PressMs = 70
    const val FastMs = 130
    const val ReleaseMs = 140
    const val NormalMs = 190
    const val LargeMs = 240

    const val PressScale = 0.98f
    const val PlayCardPressScale = 0.982f
    const val IconPressScale = 0.94f
    const val SelectedIconScale = 1.045f

    val CrispEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEase = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun <T> pressTween() = tween<T>(durationMillis = PressMs, easing = CrispEase)
    fun <T> fastTween() = tween<T>(durationMillis = FastMs, easing = CrispEase)
    fun <T> releaseTween() = tween<T>(durationMillis = ReleaseMs, easing = CrispEase)
    fun <T> normalTween() = tween<T>(durationMillis = NormalMs, easing = CrispEase)
    fun <T> largeTween() = tween<T>(durationMillis = LargeMs, easing = CrispEase)

    fun <T> releaseSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

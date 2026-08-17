package dev.lumenchess.feedback

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

/** Output-only Android haptic adapter. It never owns or mutates chess/runtime state. */
class HapticFeedbackPlayer(context: Context) {
    private val vibrator = context.applicationContext
        .getSystemService(VibratorManager::class.java)
        ?.defaultVibrator

    fun play(event: GameFeedbackEvent) {
        val target = vibrator ?: return
        if (!target.hasVibrator()) return
        val (durationMillis, amplitude) = when (event) {
            GameFeedbackEvent.Move -> 18L to 90
            GameFeedbackEvent.Capture -> 28L to 145
            GameFeedbackEvent.Check -> 36L to 180
            GameFeedbackEvent.Castle -> 42L to 155
            GameFeedbackEvent.Promotion -> 55L to 190
            GameFeedbackEvent.GameStart -> 30L to 105
            GameFeedbackEvent.GameEnd -> 65L to 170
        }
        target.vibrate(VibrationEffect.createOneShot(durationMillis, amplitude))
    }
}

package dev.lumenchess.feedback

import android.content.Context

/** Android presentation sink. It owns only sound/vibration side effects. */
class AndroidGameFeedbackOutput(context: Context) : GameFeedbackOutput {
    private val soundPlayer = SoundPlayer(context)
    private val hapticPlayer = HapticFeedbackPlayer(context)

    @Volatile
    private var soundPackId: String = SoundSourceResolver.BUILT_IN_PACK_ID

    fun updateSoundPackId(id: String) {
        soundPackId = id
    }

    override fun playSound(event: GameFeedbackEvent) {
        soundPlayer.play(event.toSoundEvent(), soundPackId)
    }

    override fun playHaptic(event: GameFeedbackEvent) {
        hapticPlayer.play(event)
    }

    fun preview(event: GameFeedbackEvent, sound: Boolean, haptic: Boolean) {
        if (sound) playSound(event)
        if (haptic) playHaptic(event)
    }

    fun close() {
        soundPlayer.close()
    }
}

fun GameFeedbackEvent.toSoundEvent(): SoundEvent = when (this) {
    GameFeedbackEvent.Move -> SoundEvent.MOVE
    GameFeedbackEvent.Capture -> SoundEvent.CAPTURE
    GameFeedbackEvent.Check -> SoundEvent.CHECK
    GameFeedbackEvent.Castle -> SoundEvent.CASTLE
    GameFeedbackEvent.Promotion -> SoundEvent.PROMOTION
    GameFeedbackEvent.GameStart -> SoundEvent.GAME_START
    GameFeedbackEvent.GameEnd -> SoundEvent.GAME_END
}

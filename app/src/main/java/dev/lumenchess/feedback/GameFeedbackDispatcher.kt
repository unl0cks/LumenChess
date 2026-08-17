package dev.lumenchess.feedback

data class FeedbackSettings(
    val soundsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val soundEvents: Set<GameFeedbackEvent> = GameFeedbackEvent.all,
    val hapticEvents: Set<GameFeedbackEvent> = GameFeedbackEvent.all,
)

interface GameFeedbackOutput {
    fun playSound(event: GameFeedbackEvent)
    fun playHaptic(event: GameFeedbackEvent)
}

class GameFeedbackDispatcher(
    private val output: GameFeedbackOutput,
) {
    fun dispatch(events: List<GameFeedbackEvent>, settings: FeedbackSettings) {
        events.forEach { event ->
            if (settings.soundsEnabled && event in settings.soundEvents) {
                isolateOutputFailure { output.playSound(event) }
            }
            if (settings.hapticsEnabled && event in settings.hapticEvents) {
                isolateOutputFailure { output.playHaptic(event) }
            }
        }
    }

    private inline fun isolateOutputFailure(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // Presentation-only feedback failures must never escape into runtime authority.
        }
    }
}

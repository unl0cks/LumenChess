package dev.lumenchess.feedback

/**
 * Presentation-only feedback emitted after authoritative runtime state has already committed.
 * These events must never be used to drive legality, clocks, engine application, or persistence.
 */
sealed interface GameFeedbackEvent {
    data object Move : GameFeedbackEvent
    data object Capture : GameFeedbackEvent
    data object Check : GameFeedbackEvent
    data object Castle : GameFeedbackEvent
    data object Promotion : GameFeedbackEvent
    data object GameStart : GameFeedbackEvent
    data object GameEnd : GameFeedbackEvent

    companion object {
        val all: Set<GameFeedbackEvent> = linkedSetOf(
            Move,
            Capture,
            Check,
            Castle,
            Promotion,
            GameStart,
            GameEnd,
        )
    }
}

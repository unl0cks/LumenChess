package dev.lumenchess.feedback

/**
 * Presentation-only feedback. Move, game and clock events are emitted after authoritative runtime
 * state has already committed; [IllegalMove] reports a rejected drop that never reached the runtime.
 * These events must never be used to drive legality, clocks, engine application, or persistence.
 */
sealed interface GameFeedbackEvent {
    data object Move : GameFeedbackEvent
    data object Capture : GameFeedbackEvent
    data object Check : GameFeedbackEvent
    data object Castle : GameFeedbackEvent
    data object Promotion : GameFeedbackEvent
    /** A dragged piece was dropped on a square it cannot legally reach. */
    data object IllegalMove : GameFeedbackEvent
    /** The player's clock just fell to ten seconds or less. */
    data object LowTime : GameFeedbackEvent
    data object GameStart : GameFeedbackEvent
    data object GameEnd : GameFeedbackEvent

    companion object {
        val all: Set<GameFeedbackEvent> = linkedSetOf(
            Move,
            Capture,
            Check,
            Castle,
            Promotion,
            IllegalMove,
            LowTime,
            GameStart,
            GameEnd,
        )
    }
}

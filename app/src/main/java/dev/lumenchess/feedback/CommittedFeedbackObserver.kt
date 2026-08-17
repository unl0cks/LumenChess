package dev.lumenchess.feedback

import dev.lumenchess.runtime.RuntimeState

/**
 * Presentation-only observer for states that have already been committed by the serialized runtime.
 * It owns only a projection baseline and cannot mutate runtime/chess/persistence state.
 */
class CommittedFeedbackObserver(
    private val dispatcher: GameFeedbackDispatcher,
    private val projector: GameFeedbackProjector = GameFeedbackProjector(),
) {
    private var previous: RuntimeState? = null

    fun resetBaseline(state: RuntimeState?) {
        previous = state
    }

    fun onCommitted(state: RuntimeState, settings: FeedbackSettings) {
        val before = previous
        previous = state
        val events = projector.project(before, state)
        if (events.isNotEmpty()) dispatcher.dispatch(events, settings)
    }
}

package dev.lumenchess.feedback

import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.runtime.GameRuntime
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeControllers
import dev.lumenchess.runtime.RuntimeEvent
import dev.lumenchess.runtime.RuntimeEventId
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals

class CommittedFeedbackObserverTest {
    private class FakeTime : MonotonicTimeSource {
        override fun nowMillis(): Long = 1_000L
    }

    private class RecordingOutput : GameFeedbackOutput {
        val sounds = mutableListOf<GameFeedbackEvent>()
        val haptics = mutableListOf<GameFeedbackEvent>()

        override fun playSound(event: GameFeedbackEvent) {
            sounds += event
        }

        override fun playHaptic(event: GameFeedbackEvent) {
            haptics += event
        }
    }

    private fun runtime(): GameRuntime = GameRuntime.create(
        initialPosition = Fen.parse("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1"),
        clockConfig = ClockConfig(initialMillis = 60_000L, incrementMillis = 0L),
        timeSource = FakeTime(),
        controllers = RuntimeControllers(
            white = RuntimeController.HUMAN,
            black = RuntimeController.HUMAN,
        ),
    )

    @Test
    fun baselineThenCommittedTransitionsDispatchExactlyOnce() {
        val runtime = runtime()
        val output = RecordingOutput()
        val observer = CommittedFeedbackObserver(GameFeedbackDispatcher(output))
        observer.resetBaseline(runtime.state)

        runtime.dispatch(RuntimeEvent.Start(RuntimeEventId(1)))
        observer.onCommitted(runtime.state, FeedbackSettings())
        observer.onCommitted(runtime.state, FeedbackSettings())

        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("e2e4")))
        val committedMove = runtime.state
        observer.onCommitted(committedMove, FeedbackSettings())
        observer.onCommitted(
            committedMove.copy(
                clock = committedMove.clock.copy(
                    whiteRemainingMillis = (committedMove.clock.whiteRemainingMillis - 1L).coerceAtLeast(0L),
                ),
            ),
            FeedbackSettings(),
        )

        assertEquals(
            listOf(GameFeedbackEvent.GameStart, GameFeedbackEvent.Move),
            output.sounds,
        )
        assertEquals(output.sounds, output.haptics)
    }

    @Test
    fun restoredCommittedStateCanBecomeBaselineWithoutHistoricalReplay() {
        val runtime = runtime()
        runtime.dispatch(RuntimeEvent.Start(RuntimeEventId(1)))
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("e2e4")))
        val output = RecordingOutput()
        val observer = CommittedFeedbackObserver(GameFeedbackDispatcher(output))

        observer.resetBaseline(runtime.state)
        observer.onCommitted(runtime.state, FeedbackSettings())

        assertEquals(emptyList<GameFeedbackEvent>(), output.sounds)
        assertEquals(emptyList<GameFeedbackEvent>(), output.haptics)
    }
}

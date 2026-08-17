package dev.lumenchess.feedback

import kotlin.test.Test
import kotlin.test.assertEquals

class GameFeedbackDispatcherTest {
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

    @Test
    fun defaultsDispatchBothChannelsForEveryEvent() {
        val output = RecordingOutput()
        val dispatcher = GameFeedbackDispatcher(output)
        val events = listOf(
            GameFeedbackEvent.Move,
            GameFeedbackEvent.Capture,
            GameFeedbackEvent.Check,
            GameFeedbackEvent.Castle,
            GameFeedbackEvent.Promotion,
            GameFeedbackEvent.GameStart,
            GameFeedbackEvent.GameEnd,
        )

        dispatcher.dispatch(events, FeedbackSettings())

        assertEquals(events, output.sounds)
        assertEquals(events, output.haptics)
    }

    @Test
    fun masterChannelSwitchesAreIndependent() {
        val output = RecordingOutput()
        val dispatcher = GameFeedbackDispatcher(output)

        dispatcher.dispatch(
            listOf(GameFeedbackEvent.Move),
            FeedbackSettings(soundsEnabled = false, hapticsEnabled = true),
        )

        assertEquals(emptyList(), output.sounds)
        assertEquals(listOf(GameFeedbackEvent.Move), output.haptics)
    }

    @Test
    fun perEventSwitchesDoNotDisableOtherEvents() {
        val output = RecordingOutput()
        val dispatcher = GameFeedbackDispatcher(output)
        val settings = FeedbackSettings(
            soundEvents = GameFeedbackEvent.all - GameFeedbackEvent.Check,
            hapticEvents = GameFeedbackEvent.all - GameFeedbackEvent.Capture,
        )

        dispatcher.dispatch(
            listOf(GameFeedbackEvent.Move, GameFeedbackEvent.Capture, GameFeedbackEvent.Check),
            settings,
        )

        assertEquals(
            listOf(GameFeedbackEvent.Move, GameFeedbackEvent.Capture),
            output.sounds,
        )
        assertEquals(
            listOf(GameFeedbackEvent.Move, GameFeedbackEvent.Check),
            output.haptics,
        )
    }

    @Test
    fun emptyEventBatchProducesNoSideEffects() {
        val output = RecordingOutput()
        GameFeedbackDispatcher(output).dispatch(emptyList(), FeedbackSettings())

        assertEquals(emptyList(), output.sounds)
        assertEquals(emptyList(), output.haptics)
    }
}

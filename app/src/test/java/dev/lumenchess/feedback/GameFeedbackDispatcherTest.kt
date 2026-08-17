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

        assertEquals(emptyList<GameFeedbackEvent>(), output.sounds)
        assertEquals(listOf<GameFeedbackEvent>(GameFeedbackEvent.Move), output.haptics)
    }

    @Test
    fun perEventSwitchesDoNotDisableOtherEvents() {
        val output = RecordingOutput()
        val dispatcher = GameFeedbackDispatcher(output)
        val settings = FeedbackSettings(
            soundEvents = GameFeedbackEvent.all.filterNot { it == GameFeedbackEvent.Check }.toSet(),
            hapticEvents = GameFeedbackEvent.all.filterNot { it == GameFeedbackEvent.Capture }.toSet(),
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
    fun outputFailuresCannotEscapeOrBlockOtherFeedback() {
        val sounds = mutableListOf<GameFeedbackEvent>()
        val haptics = mutableListOf<GameFeedbackEvent>()
        val output = object : GameFeedbackOutput {
            override fun playSound(event: GameFeedbackEvent) {
                sounds += event
                if (event == GameFeedbackEvent.Move) error("audio device failed")
            }

            override fun playHaptic(event: GameFeedbackEvent) {
                haptics += event
                if (event == GameFeedbackEvent.Capture) error("vibrator failed")
            }
        }
        val events = listOf(GameFeedbackEvent.Move, GameFeedbackEvent.Capture, GameFeedbackEvent.Check)

        GameFeedbackDispatcher(output).dispatch(events, FeedbackSettings())

        assertEquals(events, sounds)
        assertEquals(events, haptics)
    }

    @Test
    fun emptyEventBatchProducesNoSideEffects() {
        val output = RecordingOutput()
        GameFeedbackDispatcher(output).dispatch(emptyList(), FeedbackSettings())

        assertEquals(emptyList<GameFeedbackEvent>(), output.sounds)
        assertEquals(emptyList<GameFeedbackEvent>(), output.haptics)
    }
}

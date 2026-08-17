package dev.lumenchess.feedback

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.runtime.GameRuntime
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeControllers
import dev.lumenchess.runtime.RuntimeEvent
import dev.lumenchess.runtime.RuntimeEventId
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals

class GameFeedbackProjectorTest {
    private class FakeTime(private var now: Long = 1_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
    }

    private fun runtime(fen: String = "rn1qkbnr/pppbpppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"): GameRuntime =
        GameRuntime.create(
            initialPosition = Fen.parse(fen),
            clockConfig = ClockConfig(initialMillis = 60_000L, incrementMillis = 0L),
            timeSource = FakeTime(),
            controllers = RuntimeControllers(
                white = RuntimeController.HUMAN,
                black = RuntimeController.HUMAN,
            ),
        )

    private fun started(runtime: GameRuntime): dev.lumenchess.runtime.RuntimeState {
        runtime.dispatch(RuntimeEvent.Start(RuntimeEventId(1)))
        return runtime.state
    }

    @Test
    fun startProjectsExactlyOnceWithoutInventingMoveFeedback() {
        val runtime = runtime()
        val before = runtime.state
        val after = started(runtime)
        val projector = GameFeedbackProjector()

        assertEquals(listOf(GameFeedbackEvent.GameStart), projector.project(before, after))
        assertEquals(emptyList(), projector.project(after, after))
    }

    @Test
    fun ordinaryCommittedRevisionProjectsMoveOnly() {
        val runtime = runtime("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
        val before = started(runtime)
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("e2e4")))
        val after = runtime.state

        assertEquals(PositionRevision(1), after.positionRevision)
        assertEquals(listOf(GameFeedbackEvent.Move), GameFeedbackProjector().project(before, after))
    }

    @Test
    fun captureUsesCaptureAsPrimaryFeedbackInsteadOfStackingMove() {
        val runtime = runtime("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        val before = started(runtime)
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("e4d5")))
        val after = runtime.state

        assertEquals(listOf(GameFeedbackEvent.Capture), GameFeedbackProjector().project(before, after))
    }

    @Test
    fun castleAndPromotionReceiveDedicatedPrimaryFeedback() {
        val castleRuntime = runtime("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        val beforeCastle = started(castleRuntime)
        castleRuntime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("e1g1")))
        val afterCastle = castleRuntime.state

        val promotionRuntime = runtime("8/P7/7k/8/8/8/8/4K3 w - - 0 1")
        val beforePromotion = started(promotionRuntime)
        promotionRuntime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("a7a8q")))
        val afterPromotion = promotionRuntime.state

        val projector = GameFeedbackProjector()
        assertEquals(listOf(GameFeedbackEvent.Castle), projector.project(beforeCastle, afterCastle))
        assertEquals(listOf(GameFeedbackEvent.Promotion), projector.project(beforePromotion, afterPromotion))
    }

    @Test
    fun checkCanLayerOnTopOfPrimaryMoveFeedback() {
        val runtime = runtime("4k3/8/8/8/8/8/R7/4K3 w - - 0 1")
        val before = started(runtime)
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("a2e2")))
        val after = runtime.state

        assertEquals(
            listOf(GameFeedbackEvent.Move, GameFeedbackEvent.Check),
            GameFeedbackProjector().project(before, after),
        )
    }

    @Test
    fun terminalTransitionProjectsGameEndWithoutReplayingLastMove() {
        val runtime = runtime("4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        val before = started(runtime)
        runtime.dispatch(RuntimeEvent.Resign(RuntimeEventId(2), Color.WHITE))
        val after = runtime.state

        assertEquals(listOf(GameFeedbackEvent.GameEnd), GameFeedbackProjector().project(before, after))
        assertEquals(emptyList(), GameFeedbackProjector().project(after, after))
    }

    @Test
    fun sameRevisionProjectionRefreshCannotReplayMoveFeedback() {
        val runtime = runtime("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
        started(runtime)
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("e2e4")))
        val committed = runtime.state
        val clockOnlyRefresh = committed.copy(
            clock = committed.clock.copy(
                whiteRemainingMillis = (committed.clock.whiteRemainingMillis - 1L).coerceAtLeast(0L),
            ),
        )

        assertEquals(
            emptyList(),
            GameFeedbackProjector().project(committed, clockOnlyRefresh),
        )
    }

    @Test
    fun nullBaselineNeverReplaysHistoricalFeedbackAfterRestore() {
        val runtime = runtime("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
        started(runtime)
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), Move.parseUci("e2e4")))

        assertEquals(emptyList(), GameFeedbackProjector().project(null, runtime.state))
    }
}

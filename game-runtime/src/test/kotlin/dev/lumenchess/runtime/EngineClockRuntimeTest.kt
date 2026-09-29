package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Chess960
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.ClockSide
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The engine is a clocked opponent: its search runs on its own clock and can lose on time. */
class EngineClockRuntimeTest {
    private class FakeTime(var now: Long = 10_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
        fun advanceBy(millis: Long) { now += millis }
    }

    private class Game(
        val time: FakeTime = FakeTime(),
        position: Position = Position.initial(),
        initialMillis: Long = 60_000L,
        incrementMillis: Long = 2_000L,
        white: RuntimeController = RuntimeController.HUMAN,
        black: RuntimeController = RuntimeController.ENGINE,
    ) {
        private var nextId = 1L
        val runtime = GameRuntime.create(
            initialPosition = position,
            clockConfig = ClockConfig(initialMillis, incrementMillis),
            timeSource = time,
            controllers = RuntimeControllers(white, black),
        )

        fun send(build: (RuntimeEventId) -> RuntimeEvent): RuntimeDispatchResult =
            runtime.dispatch(build(RuntimeEventId(nextId++)))

        fun start() = send { RuntimeEvent.Start(it) }
        fun human(uci: String) = send { RuntimeEvent.HumanMove(it, Move.parseUci(uci)) }
        fun clockCheck() = send { RuntimeEvent.ClockCheck(it) }
        fun engineReplies(search: RuntimeEffect.StartEngineSearch, uci: String?) = send {
            RuntimeEvent.EngineCompleted(it, EngineSearchResult(search.searchId, search.positionRevision, uci))
        }

        val state: RuntimeState get() = runtime.state
    }

    private fun RuntimeDispatchResult.search(): RuntimeEffect.StartEngineSearch =
        effects.filterIsInstance<RuntimeEffect.StartEngineSearch>().single()

    @Test
    fun engineThinkTimeIsChargedToTheEnginesClockAndIncrementFollowsItsMove() {
        val game = Game()
        game.start()
        val search = game.human("e2e4").search()
        // The human move already earned its increment and started the engine's clock.
        assertEquals(62_000L, game.state.clock.whiteRemainingMillis)
        assertEquals(ClockSide.BLACK, game.state.clock.activeSide)

        game.time.advanceBy(7_000L)
        // While the engine thinks, its clock is the one running and it is visibly ticking down.
        val clocks = game.clockCheck().state.clock
        assertEquals(62_000L, clocks.whiteRemainingMillis)
        assertTrue(clocks.running)

        val afterEngine = game.engineReplies(search, "e7e5").state
        assertEquals(listOf("e2e4", "e7e5"), afterEngine.gameTree.mainline().map { it.move!!.uci })
        assertEquals(60_000L - 7_000L + 2_000L, afterEngine.clock.blackRemainingMillis)
        assertEquals(62_000L, afterEngine.clock.whiteRemainingMillis)
        assertEquals(ClockSide.WHITE, afterEngine.clock.activeSide)
    }

    @Test
    fun engineThatOutthinksItsClockLosesOnTimeAndItsSearchIsCancelled() {
        val game = Game(initialMillis = 5_000L, incrementMillis = 0L)
        game.start()
        val search = game.human("e2e4").search()

        game.time.advanceBy(5_001L)
        val flagged = game.clockCheck()

        assertEquals(RuntimeTerminal.Timeout(Color.BLACK), flagged.state.terminal)
        assertEquals(RuntimeDisposition.TERMINAL, flagged.disposition)
        assertTrue(
            flagged.effects.any { it == RuntimeEffect.CancelEngineSearch(search.searchId) },
            "the engine's in-flight search must be cancelled when it flags",
        )
        assertNull(flagged.state.pendingEngineSearch)
        assertEquals(0L, flagged.state.clock.blackRemainingMillis)
        assertFalse(flagged.state.clock.running)
    }

    @Test
    fun searchThatFinishesAfterTheFlagCannotMakeAMove() {
        val game = Game(initialMillis = 5_000L, incrementMillis = 1_000L)
        game.start()
        val search = game.human("e2e4").search()
        game.time.advanceBy(5_001L)
        game.clockCheck()
        assertIs<RuntimeTerminal.Timeout>(game.state.terminal)

        val late = game.engineReplies(search, "e7e5")

        assertEquals(RuntimeDisposition.TERMINAL, late.disposition)
        assertEquals(listOf("e2e4"), game.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(RuntimeTerminal.Timeout(Color.BLACK), game.state.terminal)
    }

    @Test
    fun resultArrivingBeforeAnyClockCheckIsStillRefusedIfTheFlagAlreadyFell() {
        val game = Game(initialMillis = 5_000L, incrementMillis = 1_000L)
        game.start()
        val search = game.human("e2e4").search()
        game.time.advanceBy(6_000L)

        // No ClockCheck tick ran: the engine's answer and its flag land on the same boundary. The
        // flag wins, the move is not played, and no increment resurrects the clock.
        val late = game.engineReplies(search, "e7e5")

        assertEquals(RuntimeTerminal.Timeout(Color.BLACK), late.state.terminal)
        assertEquals(listOf("e2e4"), late.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(0L, late.state.clock.blackRemainingMillis)
    }

    @Test
    fun staleSearchAfterTimeoutOrNewGameStateNeverApplies() {
        val game = Game(initialMillis = 60_000L, incrementMillis = 0L)
        game.start()
        val first = game.human("e2e4").search()
        game.time.advanceBy(1_000L)
        game.engineReplies(first, "e7e5")
        val second = game.human("g1f3").search()
        assertNotEquals(first.searchId, second.searchId)

        // A duplicate/late answer for the first search must not touch the position or the clocks.
        val clocksBefore = game.state.clock
        val stale = game.engineReplies(first, "b8c6")

        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, stale.disposition)
        assertEquals(clocksBefore, game.state.clock)
        assertEquals(3, game.state.gameTree.mainline().size)
    }

    @Test
    fun humanFlagEndsTheGameBeforeTheMoveAndNoEngineSearchStarts() {
        val game = Game(initialMillis = 5_000L, incrementMillis = 0L)
        game.start()
        game.time.advanceBy(5_500L)

        val late = game.human("e2e4")

        assertEquals(RuntimeTerminal.Timeout(Color.WHITE), late.state.terminal)
        assertTrue(late.effects.filterIsInstance<RuntimeEffect.StartEngineSearch>().isEmpty())
        assertTrue(late.state.gameTree.mainline().isEmpty())
    }

    @Test
    fun incrementIsAddedAfterTheMoveNeverBefore() {
        val game = Game(initialMillis = 10_000L, incrementMillis = 3_000L)
        game.start()
        game.time.advanceBy(4_000L)

        game.human("e2e4")

        // 10s - 4s spent + 3s increment; not 10s + 3s - 4s applied in a different order that could
        // have let a nearly-flagged player survive.
        assertEquals(9_000L, game.state.clock.whiteRemainingMillis)
        assertEquals(10_000L, game.state.clock.blackRemainingMillis)
    }

    @Test
    fun veryShortControlFlagsThePlayerWhoNeverMoves() {
        val game = Game(initialMillis = 1_000L, incrementMillis = 0L, white = RuntimeController.ENGINE, black = RuntimeController.HUMAN)
        val started = game.start()
        val search = started.search()

        game.time.advanceBy(1_001L)
        val flagged = game.clockCheck()

        assertEquals(RuntimeTerminal.Timeout(Color.WHITE), flagged.state.terminal)
        assertTrue(flagged.effects.contains(RuntimeEffect.CancelEngineSearch(search.searchId)))
    }

    @Test
    fun restoringMidThinkKeepsTheSettledClocksAndRestartsTheSearchWithoutChargingTheGap() {
        val time = FakeTime()
        val game = Game(time = time, initialMillis = 60_000L, incrementMillis = 2_000L)
        game.start()
        val oldSearch = game.human("e2e4").search()
        time.advanceBy(3_000L)

        val snapshot = game.runtime.snapshotForRestore()
        assertEquals(57_000L, snapshot.clock.blackRemainingMillis, "think time so far is settled into the snapshot")
        assertEquals(62_000L, snapshot.clock.whiteRemainingMillis)

        // The process is gone for a while; nothing may be charged for the gap.
        time.advanceBy(120_000L)
        val restored = GameRuntime.restore(snapshot, time)
        assertEquals(57_000L, restored.state.clock.blackRemainingMillis)
        assertFalse(restored.state.clock.running)
        assertNull(restored.state.pendingEngineSearch)

        restored.dispatch(RuntimeEvent.Resume(RuntimeEventId(1_000L)))
        val recovered = restored.dispatch(RuntimeEvent.EngineHostRecovered(RuntimeEventId(1_001L)))
        val newSearch = recovered.search()
        assertNotEquals(oldSearch.searchId, newSearch.searchId)

        // The old search's late answer is refused; the fresh one plays, on the restored clock.
        val stale = restored.dispatch(
            RuntimeEvent.EngineCompleted(
                RuntimeEventId(1_002L),
                EngineSearchResult(oldSearch.searchId, oldSearch.positionRevision, "e7e5"),
            ),
        )
        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, stale.disposition)

        time.advanceBy(2_000L)
        val played = restored.dispatch(
            RuntimeEvent.EngineCompleted(
                RuntimeEventId(1_003L),
                EngineSearchResult(newSearch.searchId, newSearch.positionRevision, "e7e5"),
            ),
        ).state
        assertEquals(listOf("e2e4", "e7e5"), played.gameTree.mainline().map { it.move!!.uci })
        assertEquals(57_000L - 2_000L + 2_000L, played.clock.blackRemainingMillis)
    }

    @Test
    fun chess960EngineGameUsesTheSameClockSemantics() {
        val start = Chess960.startingPosition(321)
        val game = Game(position = start, initialMillis = 30_000L, incrementMillis = 1_000L)
        game.start()
        val firstMove = dev.lumenchess.core.chess.MoveGenerator.legalMoves(start).first()
        val search = game.send { RuntimeEvent.HumanMove(it, firstMove) }.search()
        assertEquals(Variant.CHESS960, search.position.variant)

        game.time.advanceBy(4_000L)
        val reply = dev.lumenchess.core.chess.MoveGenerator.legalMoves(search.position).first()
        val after = game.engineReplies(search, reply.uci).state

        assertEquals(30_000L - 4_000L + 1_000L, after.clock.blackRemainingMillis)
        assertEquals(31_000L, after.clock.whiteRemainingMillis)
        assertEquals(2, after.gameTree.mainline().size)
    }
}

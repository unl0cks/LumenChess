package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.PieceType
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Square
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PremoveRuntimeTest {
    private class FakeTime(var now: Long = 10_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
        fun advanceBy(millis: Long) { now += millis }
    }

    private data class Fixture(val runtime: GameRuntime, val time: FakeTime)

    private fun fixture(
        position: Position = Position.initial(),
        initialMillis: Long = 5_000L,
        incrementMillis: Long = 0L,
        controllers: RuntimeControllers = RuntimeControllers(
            white = RuntimeController.ENGINE,
            black = RuntimeController.HUMAN,
        ),
    ): Fixture {
        val time = FakeTime()
        val runtime = GameRuntime.create(
            initialPosition = position,
            clockConfig = ClockConfig(initialMillis, incrementMillis),
            timeSource = time,
            controllers = controllers,
        )
        runtime.dispatch(RuntimeEvent.Start(RuntimeEventId(1)))
        return Fixture(runtime, time)
    }

    private fun move(uci: String): Move = Move.parseUci(uci)

    private fun completeCurrentEngine(
        runtime: GameRuntime,
        eventId: Long,
        uci: String,
    ): RuntimeDispatchResult {
        val pending = requireNotNull(runtime.state.pendingEngineSearch)
        return runtime.dispatch(
            RuntimeEvent.EngineCompleted(
                RuntimeEventId(eventId),
                EngineSearchResult(
                    searchId = pending.searchId,
                    positionRevision = pending.positionRevision,
                    bestMoveUci = uci,
                ),
            ),
        )
    }

    @Test
    fun legalPremoveExecutesImmediatelyAfterOpponentAuthoritativeMove() {
        val (runtime, _) = fixture()
        val queued = runtime.dispatch(
            RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")),
        )

        val result = completeCurrentEngine(runtime, 3, "e2e4")

        assertEquals(move("e7e5"), queued.state.queuedPremove?.move)
        assertEquals(listOf("e2e4", "e7e5"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(2L, result.state.positionRevision.value)
        assertNull(result.state.queuedPremove)
        assertEquals(Color.WHITE, result.state.position.sideToMove)
    }

    @Test
    fun queueDoesNotRequireMoveToBeLegalInCurrentPosition() {
        val position = Fen.parse("7k/4p3/8/7N/8/8/8/K7 w - - 0 1")
        val (runtime, _) = fixture(position)

        val queued = runtime.dispatch(
            RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7f6")),
        )

        assertEquals(RuntimeDisposition.APPLIED, queued.disposition)
        assertEquals("e7f6", queued.state.queuedPremove?.move?.uci)
    }

    @Test
    fun queuedMoveWhoseSourceDisappearsIsDiscardedWithoutClockCharge() {
        val position = Fen.parse("7k/4p3/8/8/8/8/8/K3R3 w - - 0 1")
        val (runtime, _) = fixture(position, initialMillis = 1_000L)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))

        val result = completeCurrentEngine(runtime, 3, "e1e7")

        assertEquals(listOf("e1e7"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertNull(result.state.queuedPremove)
        assertEquals(1_000L, result.state.clock.blackRemainingMillis)
    }

    @Test
    fun captureThatAppearsMakesPreviouslyIllegalPremoveExecutable() {
        val position = Fen.parse("7k/4p3/8/7N/8/8/8/K7 w - - 0 1")
        val (runtime, _) = fixture(position)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7f6")))

        val result = completeCurrentEngine(runtime, 3, "h5f6")

        assertEquals(listOf("h5f6", "e7f6"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(PieceType.PAWN, result.state.position[Square.parse("f6")]?.type)
        assertEquals(Color.BLACK, result.state.position[Square.parse("f6")]?.color)
    }

    @Test
    fun captureThatDisappearsMakesPremoveIllegalAndDiscarded() {
        val position = Fen.parse("7k/4p3/5N2/8/8/8/8/K7 w - - 0 1")
        val (runtime, _) = fixture(position, initialMillis = 1_000L)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7f6")))

        val result = completeCurrentEngine(runtime, 3, "f6h5")

        assertEquals(listOf("f6h5"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertNull(result.state.queuedPremove)
        assertEquals(1_000L, result.state.clock.blackRemainingMillis)
    }

    @Test
    fun promotionPremoveUsesCorePromotionLegality() {
        val position = Fen.parse("7k/8/8/8/8/6N1/1p6/K7 w - - 0 1")
        val (runtime, _) = fixture(position)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("b2b1q")))

        val result = completeCurrentEngine(runtime, 3, "g3h5")

        assertEquals(listOf("g3h5", "b2b1q"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(PieceType.QUEEN, result.state.position[Square.parse("b1")]?.type)
        assertEquals(Color.BLACK, result.state.position[Square.parse("b1")]?.color)
    }

    @Test
    fun standardCastlingPremoveExecutesUsingCoreRepresentation() {
        val position = Fen.parse("r3k2r/8/8/8/8/8/6N1/K7 w kq - 0 1")
        val (runtime, _) = fixture(position)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e8g8")))

        val result = completeCurrentEngine(runtime, 3, "g2h4")

        assertEquals(listOf("g2h4", "e8g8"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(PieceType.KING, result.state.position[Square.parse("g8")]?.type)
        assertEquals(PieceType.ROOK, result.state.position[Square.parse("f8")]?.type)
    }

    @Test
    fun chess960CastlingPremovePreservesRookSquareInputEncoding() {
        val position = Fen.parse("rk2r3/8/8/8/8/8/6N1/K7 w ea - 0 1", Variant.CHESS960)
        val (runtime, _) = fixture(position)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("b8e8")))

        val result = completeCurrentEngine(runtime, 3, "g2h4")

        assertEquals(listOf("g2h4", "b8e8"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(PieceType.KING, result.state.position[Square.parse("g8")]?.type)
        assertEquals(PieceType.ROOK, result.state.position[Square.parse("f8")]?.type)
    }

    @Test
    fun gameEndingOpponentMoveClearsPremoveWithoutExecutingIt() {
        val position = Fen.parse("7k/5K2/6Q1/8/8/8/7p/8 w - - 0 1")
        val (runtime, _) = fixture(position)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("h2h1q")))

        val result = completeCurrentEngine(runtime, 3, "g6g7")

        assertIs<RuntimeTerminal.Checkmate>(result.state.terminal)
        assertEquals(listOf("g6g7"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertNull(result.state.queuedPremove)
    }

    @Test
    fun timeoutBeforeOpponentMovePreventsPremoveAndChargesNothingToQueuedSide() {
        val (runtime, time) = fixture(initialMillis = 100L)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))
        time.advanceBy(100L)

        val result = completeCurrentEngine(runtime, 3, "e2e4")

        assertIs<RuntimeTerminal.Timeout>(result.state.terminal)
        assertTrue(result.state.gameTree.mainline().isEmpty())
        assertNull(result.state.queuedPremove)
        assertEquals(100L, result.state.clock.blackRemainingMillis)
    }

    @Test
    fun userCanCancelQueuedPremove() {
        val (runtime, _) = fixture()
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))

        val cancelled = runtime.dispatch(RuntimeEvent.CancelPremove(RuntimeEventId(3), Color.BLACK))
        val result = completeCurrentEngine(runtime, 4, "e2e4")

        assertNull(cancelled.state.queuedPremove)
        assertEquals(listOf("e2e4"), result.state.gameTree.mainline().map { it.move!!.uci })
    }

    @Test
    fun controllerChangeInvalidatesQueuedPremove() {
        val (runtime, _) = fixture()
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))

        val changed = runtime.dispatch(
            RuntimeEvent.ChangeController(RuntimeEventId(3), Color.BLACK, RuntimeController.ENGINE),
        )

        assertNull(changed.state.queuedPremove)
    }

    @Test
    fun premoveCostIsExactlyOneHundredMillisecondsAndAppliedExactlyOnce() {
        val (runtime, _) = fixture(initialMillis = 1_000L)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))
        val pendingBefore = requireNotNull(runtime.state.pendingEngineSearch)

        val first = completeCurrentEngine(runtime, 3, "e2e4")
        val duplicateTransport = runtime.dispatch(
            RuntimeEvent.EngineCompleted(
                RuntimeEventId(4),
                EngineSearchResult(
                    pendingBefore.searchId,
                    pendingBefore.positionRevision,
                    "e2e4",
                ),
            ),
        )

        assertEquals(900L, first.state.clock.blackRemainingMillis)
        assertEquals(2, first.state.gameTree.mainline().size)
        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, duplicateTransport.disposition)
        assertEquals(900L, duplicateTransport.state.clock.blackRemainingMillis)
        assertEquals(2, duplicateTransport.state.gameTree.mainline().size)
    }

    @Test
    fun premoveCostCanTimeoutQueuedSideBeforeMoveBecomesAuthoritative() {
        val (runtime, _) = fixture(initialMillis = 100L)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))

        val result = completeCurrentEngine(runtime, 3, "e2e4")

        val timeout = assertIs<RuntimeTerminal.Timeout>(result.state.terminal)
        assertEquals(Color.BLACK, timeout.loser)
        assertEquals(listOf("e2e4"), result.state.gameTree.mainline().map { it.move!!.uci })
        assertEquals(0L, result.state.clock.blackRemainingMillis)
        assertNull(result.state.queuedPremove)
    }

    @Test
    fun discardedPremoveNeverSurvivesToUnrelatedLaterPosition() {
        val position = Fen.parse("7k/4p3/8/8/8/8/8/K3R3 w - - 0 1")
        val (runtime, _) = fixture(position)
        runtime.dispatch(RuntimeEvent.QueuePremove(RuntimeEventId(2), Color.BLACK, move("e7e5")))

        val afterCapture = completeCurrentEngine(runtime, 3, "e1e7")

        assertNull(afterCapture.state.queuedPremove)
        assertEquals(1L, afterCapture.state.positionRevision.value)
        assertEquals(listOf("e1e7"), afterCapture.state.gameTree.mainline().map { it.move!!.uci })
    }
}

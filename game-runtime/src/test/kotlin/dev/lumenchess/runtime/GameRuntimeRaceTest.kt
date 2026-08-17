package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Position
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.runtime.clock.ClockConfig
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameRuntimeRaceTest {
    private class FakeTime(var now: Long = 1_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
        fun advanceBy(millis: Long) { now += millis }
    }

    private data class Fixture(val runtime: GameRuntime, val time: FakeTime)

    private fun fixture(
        white: RuntimeController = RuntimeController.HUMAN,
        black: RuntimeController = RuntimeController.ENGINE,
        initialMillis: Long = 60_000L,
    ): Fixture {
        val time = FakeTime()
        val runtime = GameRuntime.create(
            initialPosition = Position.initial(),
            clockConfig = ClockConfig(initialMillis, 0L),
            timeSource = time,
            controllers = RuntimeControllers(white = white, black = black),
        )
        runtime.dispatch(RuntimeEvent.Start(RuntimeEventId(1)))
        return Fixture(runtime, time)
    }

    private fun move(uci: String): Move = Move.parseUci(uci)

    private fun pendingStart(result: RuntimeDispatchResult): RuntimeEffect.StartEngineSearch =
        result.effects.filterIsInstance<RuntimeEffect.StartEngineSearch>().single()

    private fun engineResult(
        search: RuntimeEffect.StartEngineSearch,
        uci: String?,
    ) = EngineSearchResult(
        searchId = search.searchId,
        positionRevision = search.positionRevision,
        bestMoveUci = uci,
    )

    @Test
    fun legalHumanMoveIsAuthoritativeExactlyOnceAndStartsEngineForNewRevision() {
        val (runtime, _) = fixture()

        val first = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))
        val duplicate = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))

        assertEquals(PositionRevision(1), first.state.positionRevision)
        assertEquals(listOf("e2e4"), first.state.gameTree.mainline().map { it.move!!.uci })
        val search = pendingStart(first)
        assertEquals(PositionRevision(1), search.positionRevision)
        assertEquals(first.state.position, search.position)
        assertEquals(RuntimeDisposition.DUPLICATE_EVENT, duplicate.disposition)
        assertEquals(PositionRevision(1), duplicate.state.positionRevision)
        assertEquals(1, duplicate.state.gameTree.mainline().size)
    }

    @Test
    fun humanMoveThenEngineCompletionAppliesInSerializedOrder() {
        val (runtime, _) = fixture()
        val afterHuman = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))
        val search = pendingStart(afterHuman)

        val afterEngine = runtime.dispatch(
            RuntimeEvent.EngineCompleted(RuntimeEventId(3), engineResult(search, "e7e5")),
        )

        assertEquals(RuntimeDisposition.APPLIED, afterEngine.disposition)
        assertEquals(PositionRevision(2), afterEngine.state.positionRevision)
        assertEquals(listOf("e2e4", "e7e5"), afterEngine.state.gameTree.mainline().map { it.move!!.uci })
    }

    @Test
    fun staleEngineCompletionAfterNewerRevisionCannotUpdateState() {
        val (runtime, _) = fixture()
        val search = pendingStart(runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4"))))
        runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(3), engineResult(search, "e7e5")))
        val beforeLate = runtime.state

        val late = runtime.dispatch(
            RuntimeEvent.EngineCompleted(RuntimeEventId(4), engineResult(search, "d7d5")),
        )

        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, late.disposition)
        assertEquals(beforeLate.position, late.state.position)
        assertEquals(beforeLate.positionRevision, late.state.positionRevision)
        assertEquals(2, late.state.gameTree.mainline().size)
    }

    @Test
    fun legalButWrongRevisionEngineResultIsRejected() {
        val (runtime, _) = fixture()
        val search = pendingStart(runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4"))))
        val wrongRevision = EngineSearchResult(
            searchId = search.searchId,
            positionRevision = PositionRevision(0),
            bestMoveUci = "e7e5",
        )

        val result = runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(3), wrongRevision))

        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, result.disposition)
        assertEquals(PositionRevision(1), result.state.positionRevision)
        assertEquals(1, result.state.gameTree.mainline().size)
    }

    @Test
    fun illegalEngineMoveIsRejectedWithoutMutatingPosition() {
        val (runtime, _) = fixture()
        val afterHuman = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))
        val search = pendingStart(afterHuman)

        val rejected = runtime.dispatch(
            RuntimeEvent.EngineCompleted(RuntimeEventId(3), engineResult(search, "e7e4")),
        )

        assertEquals(RuntimeDisposition.ILLEGAL_ENGINE_RESULT, rejected.disposition)
        assertEquals(afterHuman.state.position, rejected.state.position)
        assertEquals(PositionRevision(1), rejected.state.positionRevision)
        assertEquals(1, rejected.state.gameTree.mainline().size)
    }

    @Test
    fun pauseCancelsSearchAndLateCompletionCannotApplyAfterResumeRestart() {
        val (runtime, _) = fixture()
        val oldSearch = pendingStart(runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4"))))

        val paused = runtime.dispatch(RuntimeEvent.Pause(RuntimeEventId(3)))
        assertTrue(paused.state.paused)
        assertTrue(paused.effects.any { it == RuntimeEffect.CancelEngineSearch(oldSearch.searchId) })
        assertNull(paused.state.pendingEngineSearch)

        val whilePaused = runtime.dispatch(
            RuntimeEvent.EngineCompleted(RuntimeEventId(4), engineResult(oldSearch, "e7e5")),
        )
        assertEquals(RuntimeDisposition.PAUSED, whilePaused.disposition)
        assertEquals(PositionRevision(1), whilePaused.state.positionRevision)

        val resumed = runtime.dispatch(RuntimeEvent.Resume(RuntimeEventId(5)))
        val newSearch = pendingStart(resumed)
        assertFalse(resumed.state.paused)
        assertNotEquals(oldSearch.searchId, newSearch.searchId)
        assertEquals(oldSearch.positionRevision, newSearch.positionRevision)

        val lateOld = runtime.dispatch(
            RuntimeEvent.EngineCompleted(RuntimeEventId(6), engineResult(oldSearch, "e7e5")),
        )
        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, lateOld.disposition)
    }

    @Test
    fun timeoutWinsOverMoveArrivalAtSameSerializedBoundary() {
        val (runtime, time) = fixture(initialMillis = 100L)
        time.advanceBy(100L)

        val result = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))

        val terminal = assertIs<RuntimeTerminal.Timeout>(result.state.terminal)
        assertEquals(Color.WHITE, terminal.loser)
        assertEquals(RuntimeDisposition.TERMINAL, result.disposition)
        assertEquals(PositionRevision(0), result.state.positionRevision)
        assertTrue(result.state.gameTree.mainline().isEmpty())
        assertEquals(0L, result.state.clock.whiteRemainingMillis)
    }

    @Test
    fun duplicateEngineCompletionNeverAddsSecondMove() {
        val (runtime, _) = fixture()
        val search = pendingStart(runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4"))))
        val first = runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(3), engineResult(search, "e7e5")))
        val duplicate = runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(3), engineResult(search, "e7e5")))

        assertEquals(PositionRevision(2), first.state.positionRevision)
        assertEquals(RuntimeDisposition.DUPLICATE_EVENT, duplicate.disposition)
        assertEquals(2, duplicate.state.gameTree.mainline().size)
    }

    @Test
    fun controllerChangeCancelsPendingSearchAndLateOutputIsStale() {
        val (runtime, _) = fixture()
        val search = pendingStart(runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4"))))

        val changed = runtime.dispatch(
            RuntimeEvent.ChangeController(RuntimeEventId(3), Color.BLACK, RuntimeController.HUMAN),
        )

        assertEquals(RuntimeController.HUMAN, changed.state.controllers.forSide(Color.BLACK))
        assertNull(changed.state.pendingEngineSearch)
        assertTrue(changed.effects.contains(RuntimeEffect.CancelEngineSearch(search.searchId)))

        val late = runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(4), engineResult(search, "e7e5")))
        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, late.disposition)
        assertEquals(PositionRevision(1), late.state.positionRevision)
    }

    @Test
    fun engineHostDeathCannotMutatePositionAndRecoveryUsesNewSearchId() {
        val (runtime, _) = fixture()
        val oldSearch = pendingStart(runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4"))))
        val beforeDeath = runtime.state.position

        val died = runtime.dispatch(RuntimeEvent.EngineHostDied(RuntimeEventId(3)))
        assertFalse(died.state.engineHostAvailable)
        assertNull(died.state.pendingEngineSearch)
        assertEquals(beforeDeath, died.state.position)

        val recovered = runtime.dispatch(RuntimeEvent.EngineHostRecovered(RuntimeEventId(4)))
        val replacement = pendingStart(recovered)
        assertTrue(recovered.state.engineHostAvailable)
        assertNotEquals(oldSearch.searchId, replacement.searchId)
        assertEquals(PositionRevision(1), replacement.positionRevision)

        val oldLate = runtime.dispatch(
            RuntimeEvent.EngineCompleted(RuntimeEventId(5), engineResult(oldSearch, "e7e5")),
        )
        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, oldLate.disposition)
        assertEquals(beforeDeath, oldLate.state.position)
    }

    @Test
    fun terminalResignationIsIdempotentAndFurtherEventsCannotChangeState() {
        val (runtime, _) = fixture()

        val resigned = runtime.dispatch(RuntimeEvent.Resign(RuntimeEventId(2), Color.WHITE))
        val afterTerminalMove = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(3), move("e2e4")))
        val secondResign = runtime.dispatch(RuntimeEvent.Resign(RuntimeEventId(4), Color.BLACK))

        assertEquals(RuntimeTerminal.Resignation(Color.WHITE), resigned.state.terminal)
        assertEquals(RuntimeDisposition.TERMINAL, afterTerminalMove.disposition)
        assertEquals(RuntimeDisposition.TERMINAL, secondResign.disposition)
        assertEquals(resigned.state.position, secondResign.state.position)
        assertEquals(PositionRevision(0), secondResign.state.positionRevision)
    }

    @Test
    fun drawAgreementIsTerminalAndIdempotent() {
        val (runtime, _) = fixture()

        val first = runtime.dispatch(RuntimeEvent.AgreeDraw(RuntimeEventId(2)))
        val second = runtime.dispatch(RuntimeEvent.AgreeDraw(RuntimeEventId(3)))

        assertEquals(RuntimeTerminal.DrawAgreement, first.state.terminal)
        assertEquals(RuntimeDisposition.TERMINAL, second.disposition)
        assertEquals(RuntimeTerminal.DrawAgreement, second.state.terminal)
    }

    @Test
    fun restoreBoundaryDropsInFlightSearchAndDoesNotManufactureMove() {
        val (runtime, time) = fixture()
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))
        val snapshot = runtime.snapshotForRestore()

        val restored = GameRuntime.restore(snapshot, time)

        assertEquals(PositionRevision(1), restored.state.positionRevision)
        assertEquals(listOf("e2e4"), restored.state.gameTree.mainline().map { it.move!!.uci })
        assertNull(restored.state.pendingEngineSearch)
        assertTrue(restored.state.paused)
        assertFalse(restored.state.clock.running)
    }

    @Test
    fun cancelRestartOrderingMakesOldSearchStaleEvenAtSamePositionRevision() {
        val (runtime, _) = fixture()
        val old = pendingStart(runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4"))))
        runtime.dispatch(RuntimeEvent.Pause(RuntimeEventId(3)))
        val replacement = pendingStart(runtime.dispatch(RuntimeEvent.Resume(RuntimeEventId(4))))

        val oldResult = runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(5), engineResult(old, "e7e5")))
        val newResult = runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(6), engineResult(replacement, "e7e5")))

        assertEquals(RuntimeDisposition.STALE_ENGINE_RESULT, oldResult.disposition)
        assertEquals(RuntimeDisposition.APPLIED, newResult.disposition)
        assertEquals(PositionRevision(2), newResult.state.positionRevision)
        assertEquals(2, newResult.state.gameTree.mainline().size)
    }

    @Test
    fun sameOrderedEventSequenceIsDeterministicWithoutCoroutineTiming() {
        fun execute(): List<String> {
            val (runtime, _) = fixture()
            val afterHuman = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))
            val search = pendingStart(afterHuman)
            runtime.dispatch(RuntimeEvent.EngineCompleted(RuntimeEventId(3), engineResult(search, "c7c5")))
            runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(4), move("g1f3")))
            return runtime.state.gameTree.mainline().map { it.move!!.uci }
        }

        assertEquals(listOf("e2e4", "c7c5", "g1f3"), execute())
        assertEquals(execute(), execute())
    }

    @Test
    fun wrongControllerCannotInjectHumanMoveOnEngineTurn() {
        val (runtime, _) = fixture()
        runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(2), move("e2e4")))

        val result = runtime.dispatch(RuntimeEvent.HumanMove(RuntimeEventId(3), move("e7e5")))

        assertEquals(RuntimeDisposition.WRONG_CONTROLLER, result.disposition)
        assertEquals(PositionRevision(1), result.state.positionRevision)
    }
}

package dev.lumenchess.arena

import dev.lumenchess.core.chess.Color
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchInfo
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.engine.api.UciScore
import dev.lumenchess.play.PlayEngineGateway
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArenaRuntimeCoordinatorTest {
    private class FakeTime(var now: Long = 1_000L) : MonotonicTimeSource {
        override fun nowMillis(): Long = now
    }

    private class FakeEngine : PlayEngineGateway {
        val started = mutableListOf<EngineSearchRequest>()
        val cancelled = mutableListOf<EngineSearchId>()
        override fun startSearch(request: EngineSearchRequest) { started += request }
        override fun cancelSearch(searchId: EngineSearchId) { cancelled += searchId }
    }

    private class FakePersistence : ArenaPersistenceGateway {
        val snapshots = mutableListOf<RuntimeSnapshot>()
        override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedArenaSetup) {
            snapshots += snapshot
        }
    }

    private fun coordinator(
        white: FakeEngine = FakeEngine(),
        black: FakeEngine = FakeEngine(),
        onEvaluation: (ArenaEvaluation) -> Unit = {},
    ): Triple<ArenaRuntimeCoordinator, FakeEngine, FakeEngine> {
        val resolved = ArenaSetupResolver.resolve(ArenaSetupConfig(), randomInt = { 0 })
        return Triple(
            ArenaRuntimeCoordinator.create(
                setup = resolved,
                timeSource = FakeTime(),
                whiteEngine = white,
                blackEngine = black,
                persistence = FakePersistence(),
                onEvaluation = onEvaluation,
            ),
            white,
            black,
        )
    }

    @Test
    fun bothControllersAreEnginesAndBothHostsMustBeReady() {
        val (coordinator, white, black) = coordinator()
        coordinator.start()
        coordinator.onEngineHostRecovered(Color.WHITE)

        assertEquals(RuntimeController.ENGINE, coordinator.state.controllers.white)
        assertEquals(RuntimeController.ENGINE, coordinator.state.controllers.black)
        assertTrue(white.started.isEmpty())
        assertTrue(black.started.isEmpty())

        coordinator.onEngineHostRecovered(Color.BLACK)

        assertEquals(1, white.started.size)
        assertTrue(black.started.isEmpty())
        assertEquals(PositionRevision(0), white.started.single().positionRevision)
    }

    @Test
    fun authoritativeSideToMoveSelectsTheMatchingEngineAndStrength() {
        val (coordinator, white, black) = coordinator()
        coordinator.start()
        coordinator.onEngineHostRecovered(Color.WHITE)
        coordinator.onEngineHostRecovered(Color.BLACK)
        val whiteSearch = white.started.single()

        coordinator.onEngineResult(
            Color.WHITE,
            EngineSearchResult(whiteSearch.searchId, whiteSearch.positionRevision, "e2e4"),
        )

        assertEquals(1, black.started.size)
        assertEquals(PositionRevision(1), black.started.single().positionRevision)
        assertEquals(coordinator.setup.black.strength, black.started.single().strength)
        assertEquals(listOf("e2e4"), coordinator.state.gameTree.mainline().map { it.move!!.uci })
    }

    @Test
    fun oneHostDeathCancelsTheActiveSearchAndLateOutputStaysStale() {
        val (coordinator, white, _) = coordinator()
        coordinator.start()
        coordinator.onEngineHostRecovered(Color.WHITE)
        coordinator.onEngineHostRecovered(Color.BLACK)
        val search = white.started.single()

        coordinator.onEngineHostDied(Color.BLACK)
        coordinator.onEngineResult(
            Color.WHITE,
            EngineSearchResult(search.searchId, search.positionRevision, "e2e4"),
        )

        assertFalse(coordinator.state.engineHostAvailable)
        assertTrue(search.searchId in white.cancelled)
        assertTrue(coordinator.state.gameTree.mainline().isEmpty())
    }

    @Test
    fun evaluationIsWhiteRelativeAndRejectedAfterRevisionChanges() {
        val evaluations = mutableListOf<ArenaEvaluation>()
        val (coordinator, white, _) = coordinator(onEvaluation = evaluations::add)
        coordinator.start()
        coordinator.onEngineHostRecovered(Color.WHITE)
        coordinator.onEngineHostRecovered(Color.BLACK)
        val search = white.started.single()

        coordinator.onEngineInfo(
            Color.WHITE,
            EngineSearchInfo(
                search.searchId,
                search.positionRevision,
                depth = 15,
                score = UciScore.Centipawns(82),
            ),
        )
        coordinator.onEngineResult(
            Color.WHITE,
            EngineSearchResult(search.searchId, search.positionRevision, "e2e4"),
        )
        coordinator.onEngineInfo(
            Color.WHITE,
            EngineSearchInfo(search.searchId, search.positionRevision, score = UciScore.Centipawns(900)),
        )

        assertEquals(1, evaluations.size)
        assertEquals(82, evaluations.single().whiteCentipawns)
        assertEquals(15, evaluations.single().depth)
    }

    @Test
    fun blackSearchScoreIsNegatedToWhitePerspective() {
        val evaluations = mutableListOf<ArenaEvaluation>()
        val (coordinator, white, black) = coordinator(onEvaluation = evaluations::add)
        coordinator.start()
        coordinator.onEngineHostRecovered(Color.WHITE)
        coordinator.onEngineHostRecovered(Color.BLACK)
        val first = white.started.single()
        coordinator.onEngineResult(Color.WHITE, EngineSearchResult(first.searchId, first.positionRevision, "e2e4"))
        val blackSearch = black.started.single()

        coordinator.onEngineInfo(
            Color.BLACK,
            EngineSearchInfo(blackSearch.searchId, blackSearch.positionRevision, score = UciScore.Centipawns(35)),
        )

        assertEquals(-35, evaluations.last().whiteCentipawns)
    }

    @Test
    fun pauseAndResumeRemainRuntimeOwned() {
        val (coordinator, white, _) = coordinator()
        coordinator.start()
        coordinator.onEngineHostRecovered(Color.WHITE)
        coordinator.onEngineHostRecovered(Color.BLACK)
        val first = white.started.single()

        coordinator.pause()
        assertTrue(coordinator.state.paused)
        assertTrue(first.searchId in white.cancelled)

        coordinator.resume()
        assertFalse(coordinator.state.paused)
        assertNotNull(coordinator.state.pendingEngineSearch)
    }
}

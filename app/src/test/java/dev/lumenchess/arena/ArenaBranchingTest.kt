package dev.lumenchess.arena

import dev.lumenchess.core.chess.*
import dev.lumenchess.data.persistence.*
import dev.lumenchess.engine.api.*
import dev.lumenchess.play.*
import dev.lumenchess.runtime.*
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import kotlin.test.*

class ArenaBranchingTest {
    private class Engine : PlayEngineGateway {
        val requests = mutableListOf<EngineSearchRequest>()
        override fun startSearch(request: EngineSearchRequest) { requests += request }
        override fun cancelSearch(searchId: EngineSearchId) = Unit
    }
    private object Persistence : ArenaPersistenceGateway {
        override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedArenaSetup) = Unit
    }

    @Test fun sourceConfigurationIsCopiedWithoutMutatingOriginalAndAnchorIsExact() {
        val source = ArenaSetupResolver.resolve(ArenaSetupConfig(
            white = ArenaEngineConfig(strengthTarget = EngineStrengthTarget.Elo(2000)),
            black = ArenaEngineConfig(PlayEngine.RECKLESS_0_9_0, EngineStrengthModel.HUMANIZED, EngineStrengthTarget.Elo(1200)),
        ))
        val position = MoveGenerator.applyLegalMove(source.initialPosition, San.parse(source.initialPosition, "e4"))
        val origin = BranchOrigin(PersistentGameId("game-uuid"), "node-uuid", Fen.serialize(position))
        val branch = source.forBranch(origin)
        assertEquals(EngineStrengthTarget.Elo(2000), branch.white.strengthTarget)
        assertEquals(EngineStrengthTarget.Elo(1200), branch.black.strengthTarget)
        assertEquals(ArenaManualSide.BOTH, branch.manualOpening.sides)
        assertEquals(position, ArenaSetupResolver.resolve(branch).initialPosition)
        assertEquals(Position.initial(), source.initialPosition)
        assertEquals(null, source.branchOrigin)
    }

    @Test fun untimedBranchSupportsBothVariantsAndEveryEnginePairWithoutChangingRouting() {
        for (variant in Variant.entries) for (whiteEngine in PlayEngine.entries) for (blackEngine in PlayEngine.entries) {
            val setup = ArenaSetupResolver.resolve(ArenaSetupConfig(
                variant = variant, chess960Index = if (variant == Variant.CHESS960) 37 else null,
                white = ArenaEngineConfig(whiteEngine), black = ArenaEngineConfig(blackEngine), untimed = true,
            ))
            val white = Engine(); val black = Engine()
            val coordinator = ArenaRuntimeCoordinator.create(setup, MonotonicTimeSource { 100_000 }, white, black, Persistence)
            coordinator.start()
            coordinator.onEngineHostRecovered(Color.WHITE); coordinator.onEngineHostRecovered(Color.BLACK)
            val request = white.requests.single()
            assertEquals(1500L, request.limits.moveTimeMillis)
            assertFalse(coordinator.state.clock.running)
            val move = MoveGenerator.legalMoves(coordinator.state.position).first()
            coordinator.onEngineResult(Color.WHITE, EngineSearchResult(request.searchId, request.positionRevision, move.uci))
            assertEquals(setup.black.strength, black.requests.single().strength)
            assertNull(coordinator.state.terminal)
            assertFalse(coordinator.state.clock.enabled)
            assertEquals(1L, coordinator.state.positionRevision.value)
        }
    }
}

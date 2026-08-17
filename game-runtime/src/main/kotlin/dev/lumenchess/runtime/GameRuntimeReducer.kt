package dev.lumenchess.runtime

import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.GameResult
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.MoveGenerator
import dev.lumenchess.core.chess.Rules
import dev.lumenchess.core.chess.Termination
import dev.lumenchess.engine.api.EngineMoveValidation
import dev.lumenchess.engine.api.EngineMoveValidator
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.runtime.clock.ClockSide
import dev.lumenchess.runtime.clock.DeterministicGameClock

internal object GameRuntimeReducer {
    fun reduce(
        state: RuntimeState,
        event: RuntimeEvent,
        clock: DeterministicGameClock,
    ): RuntimeTransition {
        if (event is RuntimeEvent.Start) return start(state, clock)

        val boundary = settleBoundary(state, clock)
        if (boundary != null) return boundary
        if (state.terminal != null) return RuntimeTransition(state, disposition = RuntimeDisposition.TERMINAL)

        val settledState = settleState(state, clock)
        if (settledState.terminal != null) {
            return terminalTransition(settledState, settledState.terminal, persist = true)
        }

        return when (event) {
            is RuntimeEvent.Start -> error("handled above")
            is RuntimeEvent.HumanMove -> humanMove(settledState, event.move, clock)
            is RuntimeEvent.EngineCompleted -> engineCompleted(settledState, event, clock)
            is RuntimeEvent.Pause -> pause(settledState)
            is RuntimeEvent.Resume -> resume(settledState, clock)
            is RuntimeEvent.ChangeController -> changeController(settledState, event)
            is RuntimeEvent.EngineHostDied -> hostDied(settledState)
            is RuntimeEvent.EngineHostRecovered -> hostRecovered(settledState)
            is RuntimeEvent.Resign -> terminalTransition(
                settledState,
                RuntimeTerminal.Resignation(event.loser),
                persist = true,
            )
            is RuntimeEvent.AgreeDraw -> terminalTransition(
                settledState,
                RuntimeTerminal.DrawAgreement,
                persist = true,
            )
        }
    }

    fun settleForSnapshot(state: RuntimeState, clock: DeterministicGameClock): RuntimeState {
        if (!state.started || state.paused || state.terminal != null || !state.clock.running) return state
        val transition = clock.settle(state.clock)
        val settled = state.copy(clock = transition.state)
        val timedOut = transition.timeoutOccurred ?: return settled
        return terminalState(settled, RuntimeTerminal.Timeout(timedOut.toColor()))
    }

    private fun start(state: RuntimeState, clock: DeterministicGameClock): RuntimeTransition {
        if (state.terminal != null) return RuntimeTransition(state, disposition = RuntimeDisposition.TERMINAL)
        if (state.started && !state.paused) return RuntimeTransition(state, disposition = RuntimeDisposition.IGNORED)

        val startedClock = clock.start(state.clock)
        if (startedClock.timeoutOccurred != null) {
            return terminalTransition(
                state.copy(
                    clock = startedClock.state,
                    started = true,
                    paused = false,
                ),
                RuntimeTerminal.Timeout(startedClock.timeoutOccurred.toColor()),
                persist = true,
            )
        }

        val started = state.copy(
            clock = startedClock.state,
            started = true,
            paused = false,
        )
        val scheduled = maybeStartEngine(started)
        return RuntimeTransition(
            state = scheduled.state,
            effects = scheduled.effects,
            disposition = RuntimeDisposition.APPLIED,
            persist = true,
        )
    }

    private fun humanMove(
        state: RuntimeState,
        move: Move,
        clock: DeterministicGameClock,
    ): RuntimeTransition {
        if (state.paused) return RuntimeTransition(state, disposition = RuntimeDisposition.PAUSED)
        if (state.controllers.forSide(state.position.sideToMove) != RuntimeController.HUMAN) {
            return RuntimeTransition(state, disposition = RuntimeDisposition.WRONG_CONTROLLER)
        }
        val legal = MoveGenerator.legalMoves(state.position).firstOrNull { it == move }
            ?: return RuntimeTransition(state, disposition = RuntimeDisposition.ILLEGAL_HUMAN_MOVE)
        return applyMove(state, legal, clock)
    }

    private fun engineCompleted(
        state: RuntimeState,
        event: RuntimeEvent.EngineCompleted,
        clock: DeterministicGameClock,
    ): RuntimeTransition {
        if (state.paused) return RuntimeTransition(state, disposition = RuntimeDisposition.PAUSED)
        val pending = state.pendingEngineSearch
            ?: return RuntimeTransition(state, disposition = RuntimeDisposition.STALE_ENGINE_RESULT)
        if (state.controllers.forSide(state.position.sideToMove) != RuntimeController.ENGINE) {
            return RuntimeTransition(state, disposition = RuntimeDisposition.STALE_ENGINE_RESULT)
        }

        return when (
            val validation = EngineMoveValidator.validate(
                position = state.position,
                expectedSearchId = pending.searchId,
                expectedPositionRevision = pending.positionRevision,
                result = event.result,
            )
        ) {
            is EngineMoveValidation.Accepted -> applyMove(
                state.copy(pendingEngineSearch = null),
                validation.move,
                clock,
            )
            EngineMoveValidation.StaleSearch,
            EngineMoveValidation.StalePosition,
            -> RuntimeTransition(state, disposition = RuntimeDisposition.STALE_ENGINE_RESULT)
            EngineMoveValidation.NoMove,
            is EngineMoveValidation.MalformedMove,
            is EngineMoveValidation.IllegalMove,
            -> RuntimeTransition(state, disposition = RuntimeDisposition.ILLEGAL_ENGINE_RESULT)
        }
    }

    private fun pause(state: RuntimeState): RuntimeTransition {
        if (state.paused) return RuntimeTransition(state, disposition = RuntimeDisposition.IGNORED)
        val effects = buildList {
            state.pendingEngineSearch?.let { add(RuntimeEffect.CancelEngineSearch(it.searchId)) }
        }
        return RuntimeTransition(
            state = state.copy(
                clock = state.clock.copy(running = false, lastSampleMillis = null),
                pendingEngineSearch = null,
                paused = true,
            ),
            effects = effects,
            persist = true,
        )
    }

    private fun resume(
        state: RuntimeState,
        clock: DeterministicGameClock,
    ): RuntimeTransition {
        if (!state.paused) return RuntimeTransition(state, disposition = RuntimeDisposition.IGNORED)
        val resumedClock = clock.resume(state.clock)
        if (resumedClock.timeoutOccurred != null) {
            return terminalTransition(
                state.copy(clock = resumedClock.state, paused = false),
                RuntimeTerminal.Timeout(resumedClock.timeoutOccurred.toColor()),
                persist = true,
            )
        }
        val resumed = state.copy(clock = resumedClock.state, paused = false)
        val scheduled = maybeStartEngine(resumed)
        return RuntimeTransition(scheduled.state, scheduled.effects, persist = true)
    }

    private fun changeController(
        state: RuntimeState,
        event: RuntimeEvent.ChangeController,
    ): RuntimeTransition {
        if (state.controllers.forSide(event.side) == event.controller) {
            return RuntimeTransition(state, disposition = RuntimeDisposition.IGNORED)
        }

        var next = state.copy(controllers = state.controllers.withSide(event.side, event.controller))
        val effects = mutableListOf<RuntimeEffect>()
        if (event.side == state.position.sideToMove) {
            next.pendingEngineSearch?.let {
                effects += RuntimeEffect.CancelEngineSearch(it.searchId)
                next = next.copy(pendingEngineSearch = null)
            }
            val scheduled = maybeStartEngine(next)
            next = scheduled.state
            effects += scheduled.effects
        }
        return RuntimeTransition(next, effects, persist = true)
    }

    private fun hostDied(state: RuntimeState): RuntimeTransition {
        if (!state.engineHostAvailable && state.pendingEngineSearch == null) {
            return RuntimeTransition(state, disposition = RuntimeDisposition.IGNORED)
        }
        return RuntimeTransition(
            state = state.copy(
                engineHostAvailable = false,
                pendingEngineSearch = null,
            ),
            persist = true,
        )
    }

    private fun hostRecovered(state: RuntimeState): RuntimeTransition {
        if (state.engineHostAvailable) return RuntimeTransition(state, disposition = RuntimeDisposition.IGNORED)
        val recovered = state.copy(engineHostAvailable = true)
        val scheduled = maybeStartEngine(recovered)
        return RuntimeTransition(scheduled.state, scheduled.effects, persist = true)
    }

    private fun applyMove(
        state: RuntimeState,
        move: Move,
        clock: DeterministicGameClock,
    ): RuntimeTransition {
        val addition = state.gameTree.addMove(state.currentNodeId, move)
        val nextPosition = addition.tree.node(addition.nodeId).position
        val switchedClock = clock.switchTurn(state.clock)
        if (switchedClock.timeoutOccurred != null) {
            return terminalTransition(
                state.copy(clock = switchedClock.state),
                RuntimeTerminal.Timeout(switchedClock.timeoutOccurred.toColor()),
                persist = true,
            )
        }

        var next = state.copy(
            position = nextPosition,
            gameTree = addition.tree,
            currentNodeId = addition.nodeId,
            clock = switchedClock.state,
            positionRevision = PositionRevision(state.positionRevision.value + 1L),
            pendingEngineSearch = null,
        )

        val terminal = when (Rules.termination(nextPosition)) {
            Termination.CHECKMATE -> RuntimeTerminal.Checkmate(nextPosition.sideToMove.opposite)
            Termination.STALEMATE -> RuntimeTerminal.Stalemate
            null -> null
        }
        if (terminal != null) return terminalTransition(next, terminal, persist = true)

        val scheduled = maybeStartEngine(next)
        next = scheduled.state
        return RuntimeTransition(next, scheduled.effects, persist = true)
    }

    private fun settleBoundary(
        state: RuntimeState,
        clock: DeterministicGameClock,
    ): RuntimeTransition? {
        if (!state.started || state.paused || state.terminal != null || !state.clock.running) return null
        val transition = clock.settle(state.clock)
        val timedOut = transition.timeoutOccurred ?: return null
        return terminalTransition(
            state.copy(clock = transition.state),
            RuntimeTerminal.Timeout(timedOut.toColor()),
            persist = true,
        )
    }

    private fun settleState(
        state: RuntimeState,
        clock: DeterministicGameClock,
    ): RuntimeState {
        if (!state.started || state.paused || state.terminal != null || !state.clock.running) return state
        return state.copy(clock = clock.settle(state.clock).state)
    }

    private fun terminalTransition(
        state: RuntimeState,
        terminal: RuntimeTerminal,
        persist: Boolean,
    ): RuntimeTransition {
        val effects = buildList {
            state.pendingEngineSearch?.let { add(RuntimeEffect.CancelEngineSearch(it.searchId)) }
        }
        return RuntimeTransition(
            state = terminalState(state, terminal),
            effects = effects,
            disposition = RuntimeDisposition.TERMINAL,
            persist = persist,
        )
    }

    private fun terminalState(state: RuntimeState, terminal: RuntimeTerminal): RuntimeState = state.copy(
        gameTree = state.gameTree.withResult(terminal.toGameResult()),
        clock = state.clock.copy(running = false, lastSampleMillis = null),
        pendingEngineSearch = null,
        terminal = terminal,
    )

    private data class Scheduled(
        val state: RuntimeState,
        val effects: List<RuntimeEffect>,
    )

    private fun maybeStartEngine(state: RuntimeState): Scheduled {
        if (!state.started || state.paused || state.terminal != null || !state.engineHostAvailable) {
            return Scheduled(state, emptyList())
        }
        if (state.controllers.forSide(state.position.sideToMove) != RuntimeController.ENGINE) {
            return Scheduled(state, emptyList())
        }
        if (state.pendingEngineSearch != null) return Scheduled(state, emptyList())

        val id = EngineSearchId(state.nextEngineSearchId)
        val pending = PendingEngineSearch(id, state.positionRevision)
        return Scheduled(
            state = state.copy(
                pendingEngineSearch = pending,
                nextEngineSearchId = state.nextEngineSearchId + 1L,
            ),
            effects = listOf(
                RuntimeEffect.StartEngineSearch(
                    searchId = id,
                    positionRevision = state.positionRevision,
                    position = state.position,
                ),
            ),
        )
    }

    private fun RuntimeTerminal.toGameResult(): GameResult = when (this) {
        is RuntimeTerminal.Timeout -> loser.lossResult()
        is RuntimeTerminal.Resignation -> loser.lossResult()
        RuntimeTerminal.DrawAgreement -> GameResult.DRAW
        is RuntimeTerminal.Checkmate -> if (winner == Color.WHITE) GameResult.WHITE_WIN else GameResult.BLACK_WIN
        RuntimeTerminal.Stalemate -> GameResult.DRAW
    }

    private fun Color.lossResult(): GameResult =
        if (this == Color.WHITE) GameResult.BLACK_WIN else GameResult.WHITE_WIN

    private fun ClockSide.toColor(): Color =
        if (this == ClockSide.WHITE) Color.WHITE else Color.BLACK
}

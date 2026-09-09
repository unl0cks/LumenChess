package dev.lumenchess.arena

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import dev.lumenchess.board.ChessboardOrientation
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.data.persistence.BranchOrigin
import dev.lumenchess.engine.api.EngineSearchInfo
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineSessionId
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.engine.host.transport.EngineSlot
import dev.lumenchess.play.AndroidPlayEngineGateway
import dev.lumenchess.play.PlayEngine
import dev.lumenchess.play.PlayTimeControl
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.RuntimeController
import dev.lumenchess.runtime.ManualClockPolicy
import dev.lumenchess.runtime.ManualControlLease
import dev.lumenchess.runtime.RuntimeManualControl
import dev.lumenchess.runtime.clock.ClockReading
import dev.lumenchess.runtime.clock.DeterministicGameClock
import dev.lumenchess.runtime.clock.MonotonicTimeSource
import java.util.UUID

private const val ARENA_CLOCK_REFRESH_MILLIS = 100L

enum class ArenaScreenMode { SETUP, LIVE }

data class ArenaUiState(
    val mode: ArenaScreenMode = ArenaScreenMode.SETUP,
    val setup: ArenaSetupConfig = ArenaSetupConfig(),
    val setupValidation: ArenaSetupValidation = ArenaSetupValidation.Valid,
    val resolvedSetup: ResolvedArenaSetup? = null,
    val restorableGame: RestoredArenaGame? = null,
    val runtime: RuntimeState? = null,
    val clock: ClockReading? = null,
    val evaluation: ArenaEvaluation? = null,
    val whiteEngineStatus: String = "Not connected",
    val blackEngineStatus: String = "Not connected",
    val orientation: ChessboardOrientation = ChessboardOrientation.WHITE,
    val gameId: String? = null,
    val message: String? = null,
    val sessionGeneration: Long = 0L,
    val lastMoveWasHuman: Boolean = false,
    val historyPly: Int? = null,
    val branchDraft: BranchOrigin? = null,
    val branchOperationPending: Boolean = false,
)

/** Android presentation bridge for Arena. Canonical chess state remains inside [ArenaRuntimeCoordinator]. */
class ArenaViewModel(application: Application) : AndroidViewModel(application) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeSource = MonotonicTimeSource { SystemClock.elapsedRealtime() }
    private val clockReader = DeterministicGameClock(timeSource)
    private val mutableUiState = mutableStateOf(ArenaUiState())

    val uiState: State<ArenaUiState> = mutableUiState

    private var coordinator: ArenaRuntimeCoordinator? = null
    private var whiteGateway: AndroidPlayEngineGateway? = null
    private var blackGateway: AndroidPlayEngineGateway? = null
    private var persistenceGateway: AndroidArenaPersistenceGateway? = null
    private var restoreProbe: AndroidArenaPersistenceGateway? = null
    private var screenStarted = false
    private var pausedForLifecycle = false
    private var sessionGeneration = 0L
    private var pendingBranchCapture: Any? = null
    private var pendingOriginalReturn: AndroidArenaPersistenceGateway? = null

    private val clockTicker = object : Runnable {
        override fun run() {
            refreshRuntimeProjection(checkTimeout = true)
            if (mutableUiState.value.mode == ArenaScreenMode.LIVE) {
                mainHandler.postDelayed(this, ARENA_CLOCK_REFRESH_MILLIS)
            }
        }
    }

    init {
        loadRestorableArena()
    }

    fun updateVariant(variant: Variant) = updateSetup {
        copy(
            variant = variant,
            chess960Index = if (variant == Variant.CHESS960) chess960Index ?: 518 else null,
            opening = if (variant == Variant.STANDARD && opening.mode == ArenaOpeningMode.RANDOM_CHESS960) {
                opening.copy(mode = ArenaOpeningMode.NORMAL)
            } else {
                opening
            },
        )
    }

    fun updateChess960Index(index: Int) = updateSetup { copy(chess960Index = index) }
    fun updateColorAssignment(value: ArenaColorAssignment) = updateSetup { copy(colorAssignment = value) }
    fun updateTimeControl(value: PlayTimeControl) = updateSetup { copy(timeControl = value) }
    fun updateUntimed(value: Boolean) = updateSetup { copy(untimed = value) }
    fun updateOpeningMode(value: ArenaOpeningMode) = updateSetup {
        copy(
            variant = if (value == ArenaOpeningMode.RANDOM_CHESS960) Variant.CHESS960 else variant,
            chess960Index = if (value == ArenaOpeningMode.RANDOM_CHESS960) chess960Index ?: 518 else chess960Index,
            opening = opening.copy(mode = value),
        )
    }
    fun updateOpeningFamily(value: String) = updateSetup { copy(opening = opening.copy(familyId = value)) }
    fun updateOpeningHandoff(plies: Int) = updateSetup { copy(opening = opening.copy(handoffPlies = plies)) }
    fun updateCustomFen(value: String) = updateSetup { copy(opening = opening.copy(customFen = value)) }
    fun updateManualSide(value: ArenaManualSide) = updateSetup {
        copy(manualOpening = manualOpening.copy(sides = value))
    }
    fun updateManualLimitMode(value: ArenaManualLimitMode) = updateSetup {
        copy(manualOpening = manualOpening.copy(limitMode = value))
    }
    fun updateManualMoveLimitText(value: String) = updateSetup {
        copy(manualOpening = manualOpening.copy(moveLimitText = value))
    }
    fun updateManualClockPolicy(value: ManualClockPolicy) = updateSetup {
        copy(manualOpening = manualOpening.copy(clockPolicy = value))
    }

    fun updateEngine(side: Color, engine: PlayEngine) = updateEngineConfig(side) { copy(engine = engine) }
    fun updateStrengthModel(side: Color, model: EngineStrengthModel) =
        updateEngineConfig(side) { copy(strengthModel = model) }
    fun updateStrengthTarget(side: Color, target: EngineStrengthTarget) =
        updateEngineConfig(side) { copy(strengthTarget = target) }

    fun startNewArena() {
        val config = mutableUiState.value.setup
        if (ArenaSetupValidator.validate(config) !is ArenaSetupValidation.Valid) return
        startResolvedArena(ArenaSetupResolver.resolve(config).copy(branchOrigin = mutableUiState.value.branchDraft), restored = null)
    }

    fun resumeLastArena() {
        val restored = mutableUiState.value.restorableGame ?: return
        startResolvedArena(restored.setup, restored)
    }

    fun pause() {
        pausedForLifecycle = false
        coordinator?.pause()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun resume() {
        cancelBranchNavigation()
        pausedForLifecycle = false
        mutableUiState.value = mutableUiState.value.copy(historyPly = null)
        coordinator?.resume()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun agreeDraw() {
        coordinator?.agreeDraw()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun takeOver(side: ArenaManualSide, clockPolicy: ManualClockPolicy = ManualClockPolicy.LOCKED) {
        val existing = coordinator?.state?.manualControl ?: RuntimeManualControl()
        val control = RuntimeManualControl(
            white = if (side == ArenaManualSide.WHITE || side == ArenaManualSide.BOTH) {
                existing.white ?: ManualControlLease()
            } else {
                existing.white
            },
            black = if (side == ArenaManualSide.BLACK || side == ArenaManualSide.BOTH) {
                existing.black ?: ManualControlLease()
            } else {
                existing.black
            },
            clockPolicy = clockPolicy,
        )
        coordinator?.setManualControl(control)
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun returnToEngine(side: ArenaManualSide = ArenaManualSide.BOTH) {
        val current = coordinator?.state?.manualControl ?: return
        val control = when (side) {
            ArenaManualSide.WHITE -> current.copy(white = null)
            ArenaManualSide.BLACK -> current.copy(black = null)
            ArenaManualSide.BOTH, ArenaManualSide.NONE -> RuntimeManualControl()
        }
        coordinator?.setManualControl(control)
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun onBoardMove(move: Move, expectedSession: Long, expectedRevision: Long) {
        val current = mutableUiState.value
        val runtime = current.runtime ?: return
        if (
            current.mode != ArenaScreenMode.LIVE ||
            current.historyPly != null ||
            current.sessionGeneration != expectedSession ||
            runtime.positionRevision.value != expectedRevision ||
            runtime.paused || runtime.terminal != null ||
            runtime.controllers.forSide(runtime.position.sideToMove) != RuntimeController.HUMAN
        ) return
        val result = coordinator?.humanMove(move) ?: return
        // A committed mating move is TERMINAL, while a timeout can be TERMINAL without a move.
        if (result.state.positionRevision != runtime.positionRevision) {
            mutableUiState.value = mutableUiState.value.copy(lastMoveWasHuman = true)
        }
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun stopArena() {
        restoreProbe?.setListener(null)
        restoreProbe?.close()
        restoreProbe = null
        val wasSandbox = mutableUiState.value.resolvedSetup?.branchOrigin != null || mutableUiState.value.branchDraft != null
        coordinator?.let { current ->
            if (current.state.started && !current.state.paused && current.state.terminal == null) current.pause()
        }
        refreshRuntimeProjection(checkTimeout = false)
        stopLiveAdapters()
        mutableUiState.value = mutableUiState.value.copy(
            mode = ArenaScreenMode.SETUP,
            setup = if (wasSandbox) ArenaSetupConfig() else mutableUiState.value.setup,
            setupValidation = ArenaSetupValidation.Valid,
            resolvedSetup = null,
            runtime = null,
            clock = null,
            evaluation = null,
            whiteEngineStatus = "Not connected",
            blackEngineStatus = "Not connected",
            gameId = null,
            message = null,
            historyPly = null,
            branchDraft = null,
            branchOperationPending = false,
        )
        loadRestorableArena()
    }

    fun flipBoard() {
        mutableUiState.value = mutableUiState.value.copy(
            orientation = if (mutableUiState.value.orientation == ChessboardOrientation.WHITE) {
                ChessboardOrientation.BLACK
            } else {
                ChessboardOrientation.WHITE
            },
        )
    }

    fun browseHistory() {
        val current = coordinator ?: return
        cancelBranchNavigation()
        pause()
        mutableUiState.value = mutableUiState.value.copy(
            historyPly = current.state.gameTree.mainline().size, message = null,
        )
    }

    fun stepHistory(delta: Int) {
        cancelBranchNavigation()
        val ui = mutableUiState.value
        val ply = ui.historyPly ?: return
        val size = ui.runtime?.gameTree?.mainline()?.size ?: return
        mutableUiState.value = ui.copy(historyPly = (ply + delta).coerceIn(0, size))
    }

    fun closeHistory() {
        cancelBranchNavigation()
        mutableUiState.value = mutableUiState.value.copy(historyPly = null)
    }

    fun branchHere() {
        val ui = mutableUiState.value
        val ply = ui.historyPly ?: return
        if (ui.branchOperationPending) return
        val persistence = persistenceGateway ?: return
        val setup = ui.resolvedSetup ?: return
        val request = Any()
        pendingBranchCapture = request
        mutableUiState.value = ui.copy(branchOperationPending = true, message = "Preparing sandbox…")
        persistence.captureBranchOrigin(ply) { result ->
            // A newer selection, Resume, or lifecycle/session transition owns the screen now.
            if (persistenceGateway !== persistence || pendingBranchCapture !== request) return@captureBranchOrigin
            pendingBranchCapture = null
            result.fold(
                onSuccess = { origin ->
                    val config = setup.forBranch(origin)
                    stopLiveAdapters()
                    mutableUiState.value = mutableUiState.value.copy(
                        mode = ArenaScreenMode.SETUP, setup = config,
                        setupValidation = ArenaSetupValidator.validate(config), branchDraft = origin,
                        branchOperationPending = false, restorableGame = null, runtime = null,
                        resolvedSetup = null, clock = null, evaluation = null, historyPly = null, gameId = null, message = null,
                    )
                },
                onFailure = { error ->
                    mutableUiState.value = mutableUiState.value.copy(
                        branchOperationPending = false,
                        message = "Could not prepare sandbox: ${error.message.orEmpty()}",
                    )
                },
            )
        }
    }

    fun saveVariation() {
        val ui = mutableUiState.value
        val origin = ui.resolvedSetup?.branchOrigin ?: return
        val tree = coordinator?.state?.gameTree ?: return
        if (ui.branchOperationPending) return
        if (tree.mainline().isEmpty()) {
            mutableUiState.value = ui.copy(message = "Play a move before saving a variation.")
            return
        }
        mutableUiState.value = ui.copy(branchOperationPending = true, message = "Saving variation…")
        persistenceGateway?.saveVariation(origin, tree)
    }

    fun returnToOriginal() {
        val ui = mutableUiState.value
        val origin = ui.branchDraft ?: ui.resolvedSetup?.branchOrigin ?: return
        if (ui.branchOperationPending) return
        pause()
        mutableUiState.value = mutableUiState.value.copy(branchOperationPending = true, message = "Loading original…")
        val generation = sessionGeneration
        restoreProbe?.setListener(null)
        restoreProbe?.close()
        val probe = AndroidArenaPersistenceGateway(getApplication())
        restoreProbe = probe
        pendingOriginalReturn = probe
        probe.setListener(object : AndroidArenaPersistenceGateway.Listener {
            override fun onRestoreLoaded(game: RestoredArenaGame?) {
                if (restoreProbe !== probe || pendingOriginalReturn !== probe || sessionGeneration != generation) return
                finishOriginalReturn(probe)
                if (game == null) {
                    mutableUiState.value = mutableUiState.value.copy(branchOperationPending = false, message = "Original game is unavailable.")
                } else startResolvedArena(game.setup, game, resumeRestored = false, resetNewGameSetup = true)
            }
            override fun onPersistenceFailure(error: Throwable) {
                if (restoreProbe !== probe || pendingOriginalReturn !== probe || sessionGeneration != generation) return
                finishOriginalReturn(probe)
                mutableUiState.value = mutableUiState.value.copy(branchOperationPending = false, message = "Could not load original: ${error.message.orEmpty()}")
            }
        })
        probe.loadArena(origin.gameId.value)
    }

    fun onScreenStarted() {
        if (screenStarted) return
        screenStarted = true
        if (pausedForLifecycle) {
            pausedForLifecycle = false
            coordinator?.resume()
            refreshRuntimeProjection(checkTimeout = false)
        }
    }

    fun onScreenStopped() {
        if (!screenStarted) return
        cancelBranchNavigation()
        screenStarted = false
        val current = coordinator ?: return
        if (current.state.started && !current.state.paused && current.state.terminal == null) {
            pausedForLifecycle = true
            current.pause()
            refreshRuntimeProjection(checkTimeout = false)
        }
    }

    internal fun currentCoordinatorForTest(): ArenaRuntimeCoordinator? = coordinator
    internal fun restartEngineHostForTest(side: Color) {
        if (side == Color.WHITE) whiteGateway?.restartHostForDiagnostics() else blackGateway?.restartHostForDiagnostics()
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(clockTicker)
        stopLiveAdapters()
        restoreProbe?.setListener(null)
        restoreProbe?.close()
        restoreProbe = null
        super.onCleared()
    }

    private fun startResolvedArena(setup: ResolvedArenaSetup, restored: RestoredArenaGame?, resumeRestored: Boolean = true, resetNewGameSetup: Boolean = false) {
        sessionGeneration += 1L
        val generation = sessionGeneration
        stopLiveAdapters()
        restoreProbe?.setListener(null)
        restoreProbe?.close()
        restoreProbe = null

        val persistence = AndroidArenaPersistenceGateway(
            context = getApplication(),
            existingGameId = restored?.gameId,
            createdAtEpochMillis = restored?.createdAtEpochMillis ?: System.currentTimeMillis(),
        )
        val sessionToken = UUID.randomUUID().toString()
        val white = AndroidPlayEngineGateway(
            getApplication(),
            setup.white.engine,
            EngineSessionId("arena-white-$sessionToken"),
            EngineSlot.A,
        )
        val black = AndroidPlayEngineGateway(
            getApplication(),
            setup.black.engine,
            EngineSessionId("arena-black-$sessionToken"),
            EngineSlot.B,
        )
        val runtimeCoordinator = if (restored == null) {
            ArenaRuntimeCoordinator.create(setup, timeSource, white, black, persistence, ::onEvaluation)
        } else {
            ArenaRuntimeCoordinator.restore(setup, restored.snapshot, timeSource, white, black, persistence, ::onEvaluation)
        }

        coordinator = runtimeCoordinator
        whiteGateway = white
        blackGateway = black
        persistenceGateway = persistence
        persistence.setListener(object : AndroidArenaPersistenceGateway.Listener {
            override fun onPersisted(gameId: String) {
                if (persistenceGateway !== persistence) return
                mutableUiState.value = mutableUiState.value.copy(gameId = gameId)
            }

            override fun onPersistenceFailure(error: Throwable) {
                if (persistenceGateway !== persistence) return
                mutableUiState.value = mutableUiState.value.copy(
                    message = "Could not save Arena game: ${error.message.orEmpty()}",
                    branchOperationPending = false,
                )
            }

            override fun onVariationSaved(appendedNodes: Int) {
                if (persistenceGateway !== persistence || sessionGeneration != generation) return
                mutableUiState.value = mutableUiState.value.copy(
                    branchOperationPending = false,
                    message = if (appendedNodes == 0) "Saved variation is already up to date." else "Saved $appendedNodes move(s) as a variation. Original mainline unchanged.",
                )
            }
        })
        white.setListener(engineListener(Color.WHITE, setup.white.engine, generation))
        black.setListener(engineListener(Color.BLACK, setup.black.engine, generation))

        mutableUiState.value = mutableUiState.value.copy(
            mode = ArenaScreenMode.LIVE,
            setup = if (resetNewGameSetup) ArenaSetupConfig() else mutableUiState.value.setup,
            resolvedSetup = setup,
            restorableGame = null,
            runtime = runtimeCoordinator.state,
            clock = clockReader.read(runtimeCoordinator.state.clock),
            evaluation = null,
            whiteEngineStatus = "Connecting ${setup.white.engine.displayName}…",
            blackEngineStatus = "Connecting ${setup.black.engine.displayName}…",
            gameId = restored?.gameId,
            message = null,
            sessionGeneration = sessionGeneration,
            lastMoveWasHuman = false,
            historyPly = null,
            branchDraft = null,
            branchOperationPending = false,
        )
        if (restored == null) runtimeCoordinator.start() else if (resumeRestored && restored.snapshot.terminal == null) runtimeCoordinator.resume()
        white.connect()
        black.connect()
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.post(clockTicker)
        refreshRuntimeProjection(checkTimeout = false)
    }

    private fun engineListener(side: Color, engine: PlayEngine, generation: Long) = object : AndroidPlayEngineGateway.Listener {
        override fun onEngineHostRecovered() {
            if (generation != sessionGeneration || coordinator == null) return
            setEngineStatus(side, "${engine.displayName} ready")
            coordinator?.onEngineHostRecovered(side)
            refreshRuntimeProjection(checkTimeout = false)
        }

        override fun onEngineHostDied() {
            if (generation != sessionGeneration || coordinator == null) return
            setEngineStatus(side, "${engine.displayName} restarting…")
            coordinator?.onEngineHostDied(side)
            refreshRuntimeProjection(checkTimeout = false)
        }

        override fun onEngineResult(result: EngineSearchResult) {
            if (generation != sessionGeneration || coordinator == null) return
            val previousRevision = coordinator?.state?.positionRevision
            val dispatchResult = coordinator?.onEngineResult(side, result)
            if (dispatchResult != null && dispatchResult.state.positionRevision != previousRevision) {
                mutableUiState.value = mutableUiState.value.copy(lastMoveWasHuman = false)
            }
            refreshRuntimeProjection(checkTimeout = false)
        }

        override fun onEngineInfo(info: EngineSearchInfo) {
            if (generation != sessionGeneration || coordinator == null) return
            coordinator?.onEngineInfo(side, info)
        }

        override fun onEngineFailure(failure: EngineHostFailure) {
            if (generation != sessionGeneration || coordinator == null) return
            setEngineStatus(side, "${engine.displayName}: ${failure.code.name.lowercase()}")
            mutableUiState.value = mutableUiState.value.copy(message = failure.message)
        }
    }

    private fun onEvaluation(evaluation: ArenaEvaluation) {
        mutableUiState.value = mutableUiState.value.copy(evaluation = evaluation)
    }

    private fun setEngineStatus(side: Color, status: String) {
        mutableUiState.value = if (side == Color.WHITE) {
            mutableUiState.value.copy(whiteEngineStatus = status)
        } else {
            mutableUiState.value.copy(blackEngineStatus = status)
        }
    }

    private fun refreshRuntimeProjection(checkTimeout: Boolean) {
        val current = coordinator ?: return
        var state = current.state
        var reading = clockReader.read(state.clock)
        if (checkTimeout && state.terminal == null && state.clock.running && reading.timedOutSide != null) {
            current.clockCheck()
            state = current.state
            reading = clockReader.read(state.clock)
        }
        mutableUiState.value = mutableUiState.value.copy(runtime = state, clock = reading)
    }

    private fun updateEngineConfig(side: Color, transform: ArenaEngineConfig.() -> ArenaEngineConfig) = updateSetup {
        if (side == Color.WHITE) copy(white = white.transform()) else copy(black = black.transform())
    }

    private fun updateSetup(transform: ArenaSetupConfig.() -> ArenaSetupConfig) {
        if (mutableUiState.value.mode != ArenaScreenMode.SETUP) return
        val updated = mutableUiState.value.setup.transform()
        mutableUiState.value = mutableUiState.value.copy(
            setup = updated,
            setupValidation = ArenaSetupValidator.validate(updated),
            message = null,
        )
    }

    private fun loadRestorableArena() {
        if (restoreProbe != null || mutableUiState.value.mode != ArenaScreenMode.SETUP) return
        val probe = AndroidArenaPersistenceGateway(getApplication())
        restoreProbe = probe
        probe.setListener(object : AndroidArenaPersistenceGateway.Listener {
            override fun onRestoreLoaded(game: RestoredArenaGame?) {
                if (restoreProbe === probe && mutableUiState.value.mode == ArenaScreenMode.SETUP) {
                    mutableUiState.value = mutableUiState.value.copy(restorableGame = game)
                }
            }

            override fun onPersistenceFailure(error: Throwable) {
                if (restoreProbe === probe) {
                    mutableUiState.value = mutableUiState.value.copy(
                        message = "Could not restore last Arena: ${error.message.orEmpty()}",
                    )
                }
            }
        })
        probe.loadLastRestorableArena()
    }

    private fun finishOriginalReturn(probe: AndroidArenaPersistenceGateway) {
        pendingOriginalReturn = null
        if (restoreProbe === probe) restoreProbe = null
        probe.setListener(null)
        probe.close()
    }

    private fun cancelBranchNavigation() {
        if (pendingBranchCapture == null && pendingOriginalReturn == null) return
        pendingBranchCapture = null
        pendingOriginalReturn?.let(::finishOriginalReturn)
        mutableUiState.value = mutableUiState.value.copy(branchOperationPending = false, message = null)
    }

    private fun stopLiveAdapters() {
        cancelBranchNavigation()
        mainHandler.removeCallbacks(clockTicker)
        whiteGateway?.setListener(null)
        blackGateway?.setListener(null)
        persistenceGateway?.setListener(null)
        whiteGateway?.close()
        blackGateway?.close()
        persistenceGateway?.close()
        whiteGateway = null
        blackGateway = null
        persistenceGateway = null
        coordinator = null
        pausedForLifecycle = false
    }
}

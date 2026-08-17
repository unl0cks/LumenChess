package dev.lumenchess.play

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import dev.lumenchess.core.chess.Color
import dev.lumenchess.core.chess.Move
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.runtime.RuntimeState
import dev.lumenchess.runtime.RuntimeTerminal
import dev.lumenchess.runtime.clock.ClockReading
import dev.lumenchess.runtime.clock.DeterministicGameClock
import dev.lumenchess.runtime.clock.MonotonicTimeSource

private const val CLOCK_REFRESH_MILLIS = 100L

enum class PlayScreenMode { SETUP, LIVE }

data class PlayUiState(
    val mode: PlayScreenMode = PlayScreenMode.SETUP,
    val setup: PlaySetupConfig = PlaySetupConfig(),
    val setupValidation: PlaySetupValidation = PlaySetupValidation.Valid,
    val resolvedSetup: ResolvedPlaySetup? = null,
    val restorableGame: RestoredPlayGame? = null,
    val runtime: RuntimeState? = null,
    val clock: ClockReading? = null,
    val engineStatus: String = "Not connected",
    val gameId: String? = null,
    val message: String? = null,
)

/**
 * Android lifecycle/presentation bridge. The ViewModel owns adapters and presentation state, not the
 * game. [PlayRuntimeCoordinator] remains the only route into the serialized M17 runtime owner.
 */
class PlayViewModel(application: Application) : AndroidViewModel(application) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeSource = MonotonicTimeSource { SystemClock.elapsedRealtime() }
    private val clockReader = DeterministicGameClock(timeSource)
    private val mutableUiState = mutableStateOf(PlayUiState())

    val uiState: State<PlayUiState> = mutableUiState

    private var coordinator: PlayRuntimeCoordinator? = null
    private var engineGateway: AndroidPlayEngineGateway? = null
    private var persistenceGateway: AndroidPlayPersistenceGateway? = null
    private var restoreProbe: AndroidPlayPersistenceGateway? = null
    private var screenStarted = false

    private val clockTicker = object : Runnable {
        override fun run() {
            refreshRuntimeProjection(checkTimeout = true)
            if (mutableUiState.value.mode == PlayScreenMode.LIVE) {
                mainHandler.postDelayed(this, CLOCK_REFRESH_MILLIS)
            }
        }
    }

    init {
        loadRestorableGame()
    }

    fun updateVariant(variant: Variant) = updateSetup {
        copy(
            variant = variant,
            chess960Index = if (variant == Variant.CHESS960) chess960Index ?: 518 else null,
        )
    }

    fun updateChess960Index(index: Int) = updateSetup { copy(chess960Index = index) }
    fun updateEngine(engine: PlayEngine) = updateSetup { copy(engine = engine) }
    fun updateSide(side: PlaySide) = updateSetup { copy(side = side) }
    fun updateStrengthModel(model: EngineStrengthModel) = updateSetup { copy(strengthModel = model) }
    fun updateStrengthTarget(target: EngineStrengthTarget) = updateSetup { copy(strengthTarget = target) }
    fun updateTimeControl(control: PlayTimeControl) = updateSetup { copy(timeControl = control) }

    fun startNewGame() {
        val config = mutableUiState.value.setup
        if (PlaySetupValidator.validate(config) !is PlaySetupValidation.Valid) return
        startResolvedGame(PlaySetupResolver.resolve(config), restored = null)
    }

    fun resumeLastGame() {
        val restored = mutableUiState.value.restorableGame ?: return
        startResolvedGame(restored.setup, restored)
    }

    fun backToSetup() {
        stopLiveAdapters()
        mutableUiState.value = mutableUiState.value.copy(
            mode = PlayScreenMode.SETUP,
            resolvedSetup = null,
            runtime = null,
            clock = null,
            engineStatus = "Not connected",
            gameId = null,
            message = null,
        )
        loadRestorableGame()
    }

    fun onBoardMove(move: Move) {
        val current = coordinator ?: return
        if (current.state.terminal != null) return
        current.humanMove(move)
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun queuePremove(move: Move) {
        coordinator?.queuePremove(move)
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun cancelPremove() {
        coordinator?.cancelPremove()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun pause() {
        coordinator?.pause()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun resume() {
        coordinator?.resume()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun resign() {
        coordinator?.resign()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun agreeDraw() {
        coordinator?.agreeDraw()
        refreshRuntimeProjection(checkTimeout = false)
    }

    fun onScreenStarted() {
        if (screenStarted) return
        screenStarted = true
        val current = coordinator ?: return
        if (current.state.started && current.state.paused && current.state.terminal == null) {
            current.resume()
            refreshRuntimeProjection(checkTimeout = false)
        }
    }

    fun onScreenStopped() {
        if (!screenStarted) return
        screenStarted = false
        val current = coordinator ?: return
        if (current.state.started && !current.state.paused && current.state.terminal == null) {
            current.pause()
            refreshRuntimeProjection(checkTimeout = false)
        }
    }

    internal fun restartEngineHostForTest() {
        engineGateway?.restartHostForDiagnostics()
    }

    internal fun currentCoordinatorForTest(): PlayRuntimeCoordinator? = coordinator
    internal fun currentPersistenceForTest(): AndroidPlayPersistenceGateway? = persistenceGateway

    override fun onCleared() {
        mainHandler.removeCallbacks(clockTicker)
        stopLiveAdapters()
        restoreProbe?.setListener(null)
        restoreProbe?.close()
        restoreProbe = null
        super.onCleared()
    }

    private fun startResolvedGame(setup: ResolvedPlaySetup, restored: RestoredPlayGame?) {
        stopLiveAdapters()
        restoreProbe?.setListener(null)
        restoreProbe?.close()
        restoreProbe = null

        val persistence = AndroidPlayPersistenceGateway(
            context = getApplication(),
            existingGameId = restored?.gameId,
            createdAtEpochMillis = restored?.createdAtEpochMillis ?: System.currentTimeMillis(),
        )
        val engine = AndroidPlayEngineGateway(getApplication(), setup.engine)
        val runtimeCoordinator = if (restored == null) {
            PlayRuntimeCoordinator.create(
                setup = setup,
                timeSource = timeSource,
                engine = engine,
                persistence = persistence,
            )
        } else {
            PlayRuntimeCoordinator.restore(
                setup = setup,
                snapshot = restored.snapshot,
                timeSource = timeSource,
                engine = engine,
                persistence = persistence,
            )
        }

        coordinator = runtimeCoordinator
        engineGateway = engine
        persistenceGateway = persistence
        persistence.setListener(
            object : AndroidPlayPersistenceGateway.Listener {
                override fun onPersisted(gameId: String) {
                    mutableUiState.value = mutableUiState.value.copy(gameId = gameId)
                }

                override fun onPersistenceFailure(error: Throwable) {
                    mutableUiState.value = mutableUiState.value.copy(
                        message = "Could not save game: ${error.message.orEmpty()}",
                    )
                }
            },
        )
        engine.setListener(
            object : AndroidPlayEngineGateway.Listener {
                override fun onEngineHostRecovered() {
                    mutableUiState.value = mutableUiState.value.copy(engineStatus = "${setup.engine.displayName} ready")
                    coordinator?.onEngineHostRecovered()
                    refreshRuntimeProjection(checkTimeout = false)
                }

                override fun onEngineHostDied() {
                    mutableUiState.value = mutableUiState.value.copy(engineStatus = "Engine restarting…")
                    coordinator?.onEngineHostDied()
                    refreshRuntimeProjection(checkTimeout = false)
                }

                override fun onEngineResult(result: EngineSearchResult) {
                    coordinator?.onEngineResult(result)
                    refreshRuntimeProjection(checkTimeout = false)
                }

                override fun onEngineFailure(failure: EngineHostFailure) {
                    mutableUiState.value = mutableUiState.value.copy(
                        engineStatus = "${setup.engine.displayName}: ${failure.code.name.lowercase()}",
                        message = failure.message,
                    )
                }
            },
        )

        mutableUiState.value = mutableUiState.value.copy(
            mode = PlayScreenMode.LIVE,
            resolvedSetup = setup,
            restorableGame = null,
            runtime = runtimeCoordinator.state,
            engineStatus = "Connecting ${setup.engine.displayName}…",
            gameId = restored?.gameId,
            message = null,
        )
        if (restored == null) {
            runtimeCoordinator.start()
        } else if (restored.snapshot.terminal == null) {
            runtimeCoordinator.resume()
        }
        engine.connect()
        mainHandler.removeCallbacks(clockTicker)
        mainHandler.post(clockTicker)
        refreshRuntimeProjection(checkTimeout = false)
    }

    private fun refreshRuntimeProjection(checkTimeout: Boolean) {
        val current = coordinator ?: return
        var state = current.state
        var reading = clockReader.read(state.clock)
        if (
            checkTimeout &&
            state.terminal == null &&
            state.clock.running &&
            reading.timedOutSide != null
        ) {
            current.clockCheck()
            state = current.state
            reading = clockReader.read(state.clock)
        }
        mutableUiState.value = mutableUiState.value.copy(runtime = state, clock = reading)
    }

    private fun updateSetup(transform: PlaySetupConfig.() -> PlaySetupConfig) {
        if (mutableUiState.value.mode != PlayScreenMode.SETUP) return
        val updated = mutableUiState.value.setup.transform()
        mutableUiState.value = mutableUiState.value.copy(
            setup = updated,
            setupValidation = PlaySetupValidator.validate(updated),
            message = null,
        )
    }

    private fun loadRestorableGame() {
        if (restoreProbe != null || mutableUiState.value.mode != PlayScreenMode.SETUP) return
        val probe = AndroidPlayPersistenceGateway(getApplication())
        restoreProbe = probe
        probe.setListener(
            object : AndroidPlayPersistenceGateway.Listener {
                override fun onRestoreLoaded(game: RestoredPlayGame?) {
                    if (restoreProbe !== probe || mutableUiState.value.mode != PlayScreenMode.SETUP) return
                    mutableUiState.value = mutableUiState.value.copy(restorableGame = game)
                }

                override fun onPersistenceFailure(error: Throwable) {
                    if (restoreProbe === probe) {
                        mutableUiState.value = mutableUiState.value.copy(
                            message = "Could not restore last game: ${error.message.orEmpty()}",
                        )
                    }
                }
            },
        )
        probe.loadLastRestorableGame()
    }

    private fun stopLiveAdapters() {
        mainHandler.removeCallbacks(clockTicker)
        engineGateway?.setListener(null)
        persistenceGateway?.setListener(null)
        engineGateway?.close()
        persistenceGateway?.close()
        engineGateway = null
        persistenceGateway = null
        coordinator = null
    }
}

fun RuntimeState.humanSideFromControllers(): Color? = when {
    controllers.white == dev.lumenchess.runtime.RuntimeController.HUMAN &&
        controllers.black == dev.lumenchess.runtime.RuntimeController.ENGINE -> Color.WHITE
    controllers.black == dev.lumenchess.runtime.RuntimeController.HUMAN &&
        controllers.white == dev.lumenchess.runtime.RuntimeController.ENGINE -> Color.BLACK
    else -> null
}

fun RuntimeTerminal.presentationLabel(): String = when (this) {
    is RuntimeTerminal.Timeout -> "${loser.name.lowercase().replaceFirstChar { it.uppercase() }} lost on time"
    is RuntimeTerminal.Resignation -> "${loser.name.lowercase().replaceFirstChar { it.uppercase() }} resigned"
    RuntimeTerminal.DrawAgreement -> "Draw by agreement"
    is RuntimeTerminal.Checkmate -> "Checkmate · ${winner.name.lowercase().replaceFirstChar { it.uppercase() }} wins"
    RuntimeTerminal.Stalemate -> "Draw by stalemate"
}

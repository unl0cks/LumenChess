package dev.lumenchess.play

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.lumenchess.data.persistence.GamePersistenceMetadata
import dev.lumenchess.data.persistence.LiveGamePersistenceRepository
import dev.lumenchess.data.persistence.LumenDatabase
import dev.lumenchess.data.persistence.LumenDatabaseFactory
import dev.lumenchess.data.persistence.PersistedTermination
import dev.lumenchess.data.persistence.PersistentGameId
import dev.lumenchess.data.persistence.TimeControlMetadata
import dev.lumenchess.runtime.RuntimeSnapshot
import dev.lumenchess.runtime.RuntimeTerminal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/**
 * Serial Android IO adapter for runtime persistence effects. Room never receives a mutation path back
 * into the runtime. A SharedPreferences value stores only the UUID pointer used to find the canonical
 * Room game after process recreation; canonical chess/runtime restoration data remains in Room.
 */
class AndroidPlayPersistenceGateway(
    context: Context,
    existingGameId: String? = null,
    private val createdAtEpochMillis: Long = System.currentTimeMillis(),
    private val database: LumenDatabase = LumenDatabaseFactory.open(context),
) : PlayPersistenceGateway, AutoCloseable {
    interface Listener {
        fun onPersisted(gameId: String) {}
        fun onRestoreLoaded(game: RestoredPlayGame?) {}
        fun onPersistenceFailure(error: Throwable) {}
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "lumen-play-persistence")
    }
    private val liveRepository = LiveGamePersistenceRepository(database)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val closed = AtomicBoolean(false)

    @Volatile
    private var listener: Listener? = null

    @Volatile
    var gameId: String? = existingGameId
        private set

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedPlaySetup) {
        if (closed.get()) return
        executor.execute {
            try {
                val id = runSuspendBlocking {
                    liveRepository.persist(
                        existingId = gameId?.let(::PersistentGameId),
                        tree = snapshot.gameTree,
                        metadata = GamePersistenceMetadata(
                            createdAtEpochMillis = createdAtEpochMillis,
                            playedAtEpochMillis = createdAtEpochMillis,
                            rated = false,
                            termination = snapshot.terminal?.toPersistedTermination(),
                            timeControl = TimeControlMetadata(
                                baseMillis = setup.clockConfig.initialMillis,
                                incrementMillis = setup.clockConfig.incrementMillis,
                                raw = "${setup.clockConfig.initialMillis}+${setup.clockConfig.incrementMillis}",
                            ),
                        ),
                        restoreMetadata = PlaySnapshotCodec.encode(snapshot, setup),
                    )
                }
                gameId = id.value
                preferences.edit().putString(KEY_LAST_LIVE_GAME_ID, id.value).apply()
                handler.post { listener?.onPersisted(id.value) }
            } catch (error: Throwable) {
                handler.post { listener?.onPersistenceFailure(error) }
            }
        }
    }

    fun loadLastRestorableGame() {
        if (closed.get()) return
        executor.execute {
            try {
                val rawId = preferences.getString(KEY_LAST_LIVE_GAME_ID, null)
                val restored = rawId?.let { id ->
                    runSuspendBlocking { liveRepository.load(PersistentGameId(id)) }
                        ?.let(PlaySnapshotCodec::decode)
                }
                handler.post { listener?.onRestoreLoaded(restored) }
            } catch (error: Throwable) {
                handler.post {
                    listener?.onPersistenceFailure(error)
                    listener?.onRestoreLoaded(null)
                }
            }
        }
    }

    /** Ensures all prior persistence effects have finished before a test inspects Room. */
    internal fun flushForTest(onFlushed: () -> Unit) {
        if (closed.get()) return
        executor.execute { handler.post(onFlushed) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        // Queue close behind all accepted writes so a ViewModel/lifecycle teardown cannot close Room
        // underneath a snapshot transaction that was already emitted by the authoritative runtime.
        executor.execute { LumenDatabaseFactory.close(database) }
        executor.shutdown()
    }

    private fun RuntimeTerminal.toPersistedTermination(): PersistedTermination = when (this) {
        is RuntimeTerminal.Timeout -> PersistedTermination.TIMEOUT
        is RuntimeTerminal.Resignation -> PersistedTermination.RESIGNATION
        RuntimeTerminal.DrawAgreement -> PersistedTermination.AGREEMENT
        is RuntimeTerminal.Checkmate -> PersistedTermination.CHECKMATE
        RuntimeTerminal.Stalemate -> PersistedTermination.STALEMATE
    }

    private fun <T> runSuspendBlocking(block: suspend () -> T): T {
        val latch = CountDownLatch(1)
        val result = AtomicReference<Result<T>>()
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(value: Result<T>) {
                    result.set(value)
                    latch.countDown()
                }
            },
        )
        latch.await()
        return requireNotNull(result.get()).getOrThrow()
    }

    companion object {
        private const val PREFERENCES_NAME = "lumen-play"
        private const val KEY_LAST_LIVE_GAME_ID = "last-live-game-id"
    }
}

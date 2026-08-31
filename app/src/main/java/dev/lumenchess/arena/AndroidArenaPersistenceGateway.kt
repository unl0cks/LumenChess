package dev.lumenchess.arena

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.lumenchess.data.persistence.GamePersistenceMetadata
import dev.lumenchess.data.persistence.GameSourceType
import dev.lumenchess.data.persistence.LiveGamePersistenceRepository
import dev.lumenchess.data.persistence.LumenDatabase
import dev.lumenchess.data.persistence.LumenDatabaseFactory
import dev.lumenchess.data.persistence.ParticipantDraft
import dev.lumenchess.data.persistence.ParticipantKind
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

class AndroidArenaPersistenceGateway(
    context: Context,
    existingGameId: String? = null,
    private val createdAtEpochMillis: Long = System.currentTimeMillis(),
    private val database: LumenDatabase = LumenDatabaseFactory.open(context),
) : ArenaPersistenceGateway, AutoCloseable {
    interface Listener {
        fun onPersisted(gameId: String) {}
        fun onRestoreLoaded(game: RestoredArenaGame?) {}
        fun onPersistenceFailure(error: Throwable) {}
    }

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "lumen-arena-persistence") }
    private val liveRepository = LiveGamePersistenceRepository(database)
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val closed = AtomicBoolean(false)
    @Volatile private var listener: Listener? = null
    @Volatile var gameId: String? = existingGameId
        private set

    fun setListener(listener: Listener?) { this.listener = listener }

    override fun persist(snapshot: RuntimeSnapshot, setup: ResolvedArenaSetup) {
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
                        restoreMetadata = ArenaSnapshotCodec.encode(snapshot, setup),
                        sourceType = GameSourceType.ENGINE_ARENA,
                        whiteParticipant = setup.white.participant(),
                        blackParticipant = setup.black.participant(),
                    )
                }
                gameId = id.value
                preferences.edit().putString(KEY_LAST_ARENA_GAME_ID, id.value).apply()
                handler.post { listener?.onPersisted(id.value) }
            } catch (error: Throwable) {
                handler.post { listener?.onPersistenceFailure(error) }
            }
        }
    }

    fun loadLastRestorableArena() {
        if (closed.get()) return
        executor.execute {
            try {
                val restored = preferences.getString(KEY_LAST_ARENA_GAME_ID, null)?.let { raw ->
                    runSuspendBlocking { liveRepository.load(PersistentGameId(raw)) }?.let(ArenaSnapshotCodec::decode)
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

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute { LumenDatabaseFactory.close(database) }
        executor.shutdown()
    }

    private fun ResolvedArenaEngine.participant() = ParticipantDraft(
        kind = ParticipantKind.ENGINE,
        displayName = engine.displayName,
        engineName = engine.displayName.substringBeforeLast(' '),
        engineVersion = engine.displayName.substringAfterLast(' '),
    )

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
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) {
                result.set(value)
                latch.countDown()
            }
        })
        latch.await()
        return requireNotNull(result.get()).getOrThrow()
    }

    companion object {
        private const val PREFERENCES_NAME = "lumen-arena"
        private const val KEY_LAST_ARENA_GAME_ID = "last-arena-game-id"
    }
}

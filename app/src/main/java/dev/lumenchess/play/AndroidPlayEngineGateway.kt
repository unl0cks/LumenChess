package dev.lumenchess.play

import android.content.Context
import android.os.Handler
import android.os.Looper
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchInfo
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineSession
import dev.lumenchess.engine.api.EngineSessionCommand
import dev.lumenchess.engine.api.EngineSessionId
import dev.lumenchess.engine.host.transport.EngineHostConnection
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.engine.host.transport.EngineHostFailureCode
import dev.lumenchess.engine.host.transport.EngineHostListener
import dev.lumenchess.engine.host.transport.EngineSlot
import java.util.UUID

/**
 * Android transport adapter for Human-vs-Engine Play.
 *
 * This class owns Binder/session lifecycle only. It never applies a move or mutates a Position. Every
 * callback is serialized onto the main looper and delivered to [Listener], which must feed it back
 * through [PlayRuntimeCoordinator]. The M12 transport still performs session/host-generation
 * correlation before a result reaches this adapter, and M17/EngineMoveValidator remains the final
 * authority for search ID, PositionRevision and legality.
 */
class AndroidPlayEngineGateway(
    context: Context,
    private val engine: PlayEngine,
    val sessionId: EngineSessionId = EngineSessionId("play-${UUID.randomUUID()}"),
    private val slot: EngineSlot = EngineSlot.A,
) : PlayEngineGateway, AutoCloseable {
    interface Listener {
        fun onEngineHostRecovered()
        fun onEngineHostDied()
        fun onEngineResult(result: EngineSearchResult)
        fun onEngineInfo(info: EngineSearchInfo) {}
        fun onEngineFailure(failure: EngineHostFailure)
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    @Volatile
    private var listener: Listener? = null

    private var connection: EngineHostConnection? = null
    private var session: EngineSession? = null
    private var connectionToken: Long = 0L
    private var closed: Boolean = false

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun connect() {
        handler.post {
            synchronized(lock) {
                if (!closed && connection == null) bindFreshConnectionLocked()
            }
        }
    }

    override fun startSearch(request: EngineSearchRequest) {
        val current = synchronized(lock) { session }
        if (current == null) {
            listener?.onEngineFailure(
                EngineHostFailure(
                    code = EngineHostFailureCode.TRANSPORT,
                    message = "Engine search requested before isolated host session was available",
                ),
            )
            return
        }
        current.submit(EngineSessionCommand.StartSearch(request))
    }

    override fun cancelSearch(searchId: EngineSearchId) {
        synchronized(lock) { session }?.submit(EngineSessionCommand.StopSearch(searchId))
    }

    /**
     * Diagnostic hook used by M19 device tests and manual validation. It intentionally follows the
     * same death/rebind path as Binder failure while preserving the logical game/session identity.
     */
    fun restartHostForDiagnostics() {
        handler.post { replaceHost(notifyDeath = true) }
    }

    override fun close() {
        val oldConnection: EngineHostConnection?
        val oldSession: EngineSession?
        synchronized(lock) {
            if (closed) return
            closed = true
            connectionToken += 1L
            oldConnection = connection
            oldSession = session
            connection = null
            session = null
        }
        try {
            oldSession?.submit(EngineSessionCommand.Close)
        } catch (_: RuntimeException) {
            // A dying Binder/session is already non-authoritative; teardown must remain idempotent.
        }
        oldConnection?.close()
    }

    private fun bindFreshConnectionLocked() {
        check(connection == null)
        connectionToken += 1L
        val token = connectionToken
        val candidate = EngineHostConnection(
            context = appContext,
            slot = slot,
            listener = object : EngineHostListener {
                override fun onConnected(slot: EngineSlot, processId: Int, hostGeneration: Long) {
                    handler.post { handleConnected(token, candidateReference(token), processId, hostGeneration) }
                }

                override fun onSearchResult(sessionId: EngineSessionId, result: EngineSearchResult) {
                    handler.post {
                        if (isActive(token) && sessionId == this@AndroidPlayEngineGateway.sessionId) {
                            listener?.onEngineResult(result)
                        }
                    }
                }

                override fun onSearchInfo(sessionId: EngineSessionId, info: EngineSearchInfo) {
                    handler.post {
                        if (isActive(token) && sessionId == this@AndroidPlayEngineGateway.sessionId) {
                            listener?.onEngineInfo(info)
                        }
                    }
                }

                override fun onSessionFailure(sessionId: EngineSessionId?, failure: EngineHostFailure) {
                    handler.post {
                        if (isActive(token)) listener?.onEngineFailure(failure)
                    }
                }

                override fun onHostDied(slot: EngineSlot, hostGeneration: Long) {
                    handler.post {
                        if (isActive(token)) replaceHost(notifyDeath = true)
                    }
                }
            },
        )
        connection = candidate
        if (!candidate.bind()) {
            connection = null
            listener?.onEngineFailure(
                EngineHostFailure(
                    code = EngineHostFailureCode.TRANSPORT,
                    message = "Could not bind isolated engine host",
                ),
            )
        }
    }

    /** Resolve the active connection only after the asynchronous callback returns to the main owner. */
    private fun candidateReference(token: Long): EngineHostConnection? = synchronized(lock) {
        if (!closed && token == connectionToken) connection else null
    }

    private fun handleConnected(
        token: Long,
        candidate: EngineHostConnection?,
        processId: Int,
        hostGeneration: Long,
    ) {
        if (candidate == null || !isActive(token)) return
        try {
            val opened = candidate.openSession(
                sessionId = sessionId,
                engineId = engine.id,
                capabilities = engine.capabilities,
            )
            opened.submit(EngineSessionCommand.NewGame)
            val accepted = synchronized(lock) {
                if (!closed && token == connectionToken) {
                    session = opened
                    true
                } else {
                    false
                }
            }
            if (!accepted) {
                try {
                    opened.submit(EngineSessionCommand.Close)
                } catch (_: RuntimeException) {
                    // Stale connection was already superseded.
                }
                return
            }
            listener?.onEngineHostRecovered()
        } catch (error: RuntimeException) {
            listener?.onEngineFailure(
                EngineHostFailure(
                    code = EngineHostFailureCode.TRANSPORT,
                    message = "Could not open ${engine.displayName} session in host $processId/$hostGeneration: ${error.message.orEmpty()}",
                ),
            )
            replaceHost(notifyDeath = true)
        }
    }

    private fun replaceHost(notifyDeath: Boolean) {
        val oldConnection: EngineHostConnection?
        val oldSession: EngineSession?
        synchronized(lock) {
            if (closed) return
            connectionToken += 1L
            oldConnection = connection
            oldSession = session
            connection = null
            session = null
        }
        if (notifyDeath) listener?.onEngineHostDied()
        try {
            oldSession?.submit(EngineSessionCommand.Close)
        } catch (_: RuntimeException) {
            // Expected for genuine Binder death.
        }
        oldConnection?.close()
        synchronized(lock) {
            if (!closed && connection == null) bindFreshConnectionLocked()
        }
    }

    private fun isActive(token: Long): Boolean = synchronized(lock) {
        !closed && token == connectionToken && connection != null
    }
}

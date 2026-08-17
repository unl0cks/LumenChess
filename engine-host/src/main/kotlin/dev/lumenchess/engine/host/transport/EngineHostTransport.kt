package dev.lumenchess.engine.host.transport

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.engine.api.EngineCapabilities
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineSession
import dev.lumenchess.engine.api.EngineSessionCommand
import dev.lumenchess.engine.api.EngineSessionId
import dev.lumenchess.engine.api.EngineStrengthPlanner
import dev.lumenchess.engine.api.EngineStrengthPlanning
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.host.EngineSlotAService
import dev.lumenchess.engine.host.EngineSlotBService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Logical engine slots are intentionally backed by different isolated Android services/processes. */
enum class EngineSlot {
    A,
    B,
}

enum class EngineHostFailureCode(val wireValue: Int) {
    PROTOCOL(1),
    BACKEND(2),
    BUSY(3),
    SESSION(4),
    STALE_TRANSPORT(5),
    TRANSPORT(6),
    ;

    companion object {
        fun fromWire(value: Int): EngineHostFailureCode = entries.firstOrNull { it.wireValue == value } ?: TRANSPORT
    }
}

data class EngineHostFailure(
    val code: EngineHostFailureCode,
    val message: String,
)

interface EngineHostListener {
    fun onConnected(slot: EngineSlot, processId: Int, hostGeneration: Long) {}
    fun onSearchResult(sessionId: EngineSessionId, result: EngineSearchResult) {}
    fun onSessionFailure(sessionId: EngineSessionId?, failure: EngineHostFailure) {}
    fun onHostDied(slot: EngineSlot, hostGeneration: Long) {}
}

/**
 * App-side Binder owner. This class knows about Android process lifecycle but not UCI text or native
 * engines. A reconnect obtains a new host generation so callbacks from an older host cannot be
 * mistaken for output from the replacement process.
 */
class EngineHostConnection(
    context: Context,
    val slot: EngineSlot,
    private val listener: EngineHostListener,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val closing = AtomicBoolean(false)
    private val deathNotified = AtomicBoolean(false)

    @Volatile
    private var host: IEngineHost? = null

    @Volatile
    private var hostBinder: IBinder? = null

    @Volatile
    private var generation: Long = 0L

    @Volatile
    private var bound = false

    private val deathRecipient = IBinder.DeathRecipient { notifyHostDeath() }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val remote = IEngineHost.Stub.asInterface(service)
            try {
                service.linkToDeath(deathRecipient, 0)
                val connectedGeneration = remote.hostGeneration
                hostBinder = service
                host = remote
                generation = connectedGeneration
                deathNotified.set(false)
                listener.onConnected(slot, remote.processId, connectedGeneration)
            } catch (error: RemoteException) {
                notifyTransportFailure(null, "Engine host died while binding: ${error.message.orEmpty()}")
                notifyHostDeath()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            notifyHostDeath()
        }

        override fun onBindingDied(name: ComponentName) {
            notifyHostDeath()
        }

        override fun onNullBinding(name: ComponentName) {
            notifyTransportFailure(null, "Engine host returned a null Binder")
        }
    }

    fun bind(): Boolean {
        check(!bound) { "Engine host connection is already bound" }
        check(!closing.get()) { "Engine host connection is closed" }
        val serviceClass = when (slot) {
            EngineSlot.A -> EngineSlotAService::class.java
            EngineSlot.B -> EngineSlotBService::class.java
        }
        bound = appContext.bindService(Intent(appContext, serviceClass), serviceConnection, Context.BIND_AUTO_CREATE)
        return bound
    }

    fun openSession(
        sessionId: EngineSessionId,
        engineId: String,
        capabilities: EngineCapabilities,
    ): EngineSession {
        val remote = host ?: error("Engine host is not connected")
        val session = RemoteEngineSession(
            remote = remote,
            expectedGeneration = generation,
            sessionId = sessionId,
            capabilities = capabilities,
            listener = listener,
        )
        try {
            val returnedId = remote.openSession(sessionId.value, engineId, session.callback)
            check(returnedId == sessionId.value) {
                "Engine host changed session identity from '${sessionId.value}' to '$returnedId'"
            }
        } catch (error: RemoteException) {
            notifyTransportFailure(sessionId, "Could not open engine session: ${error.message.orEmpty()}")
            throw IllegalStateException("Could not open engine session", error)
        }
        return session
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) return
        val remote = host
        if (remote != null) {
            try {
                remote.shutdownHost()
            } catch (_: RemoteException) {
                // A dead isolated process is already torn down from the caller's perspective.
            }
        }
        hostBinder?.let {
            try {
                it.unlinkToDeath(deathRecipient, 0)
            } catch (_: NoSuchElementException) {
                // The Binder may already have removed the recipient during process death.
            }
        }
        if (bound) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // Binding already disappeared with the isolated process.
            }
        }
        bound = false
        host = null
        hostBinder = null
    }

    private fun notifyHostDeath() {
        host = null
        hostBinder = null
        if (!closing.get() && deathNotified.compareAndSet(false, true)) {
            listener.onHostDied(slot, generation)
        }
    }

    private fun notifyTransportFailure(sessionId: EngineSessionId?, message: String) {
        listener.onSessionFailure(sessionId, EngineHostFailure(EngineHostFailureCode.TRANSPORT, message))
    }
}

private class RemoteEngineSession(
    private val remote: IEngineHost,
    private val expectedGeneration: Long,
    override val sessionId: EngineSessionId,
    override val capabilities: EngineCapabilities,
    private val listener: EngineHostListener,
) : EngineSession {
    private val closed = AtomicBoolean(false)
    private val inFlight = ConcurrentHashMap<Long, Long>()

    val callback: IEngineHostCallback = object : IEngineHostCallback.Stub() {
        override fun onSearchResult(
            callbackSessionId: String,
            hostGeneration: Long,
            searchId: Long,
            positionRevision: Long,
            bestMoveUci: String,
        ) {
            if (closed.get()) return
            if (callbackSessionId != sessionId.value || hostGeneration != expectedGeneration) {
                stale("Result belonged to a different engine session or host generation")
                return
            }
            val expectedRevision = inFlight.remove(searchId)
            if (expectedRevision == null || expectedRevision != positionRevision) {
                stale("Result did not match an in-flight search/revision")
                return
            }
            listener.onSearchResult(
                sessionId,
                EngineSearchResult(
                    searchId = dev.lumenchess.engine.api.EngineSearchId(searchId),
                    positionRevision = dev.lumenchess.engine.api.PositionRevision(positionRevision),
                    bestMoveUci = bestMoveUci.ifEmpty { null },
                ),
            )
        }

        override fun onHostFailure(
            callbackSessionId: String,
            hostGeneration: Long,
            code: Int,
            message: String,
        ) {
            if (closed.get()) return
            if (callbackSessionId != sessionId.value || hostGeneration != expectedGeneration) {
                stale("Failure callback belonged to a different engine session or host generation")
                return
            }
            listener.onSessionFailure(sessionId, EngineHostFailure(EngineHostFailureCode.fromWire(code), message))
        }
    }

    override fun submit(command: EngineSessionCommand) {
        check(!closed.get()) { "Engine session is closed" }
        try {
            when (command) {
                EngineSessionCommand.NewGame -> {
                    inFlight.clear()
                    remote.newGame(sessionId.value)
                }
                is EngineSessionCommand.StartSearch -> {
                    val request = command.request
                    val strengthPlanning = EngineStrengthPlanner.plan(request.strength, capabilities)
                    if (strengthPlanning is EngineStrengthPlanning.Unsupported) {
                        listener.onSessionFailure(
                            sessionId,
                            EngineHostFailure(EngineHostFailureCode.SESSION, strengthPlanning.reason),
                        )
                        return
                    }
                    val targetElo = when (val target = request.strength.target) {
                        EngineStrengthTarget.FullStrength -> 0
                        is EngineStrengthTarget.Elo -> target.value
                    }
                    inFlight[request.searchId.value] = request.positionRevision.value
                    try {
                        remote.startSearch(
                            sessionId.value,
                            request.searchId.value,
                            request.positionRevision.value,
                            Fen.serialize(request.position),
                            request.position.variant.name,
                            request.limits.depth ?: 0,
                            request.limits.nodes ?: 0L,
                            request.limits.moveTimeMillis ?: 0L,
                            request.multiPv,
                            request.strength.model.name,
                            targetElo,
                            request.strength.seed,
                        )
                    } catch (error: RemoteException) {
                        inFlight.remove(request.searchId.value)
                        throw error
                    }
                }
                is EngineSessionCommand.StopSearch -> {
                    inFlight.remove(command.searchId.value)
                    remote.stopSearch(sessionId.value, command.searchId.value)
                }
                EngineSessionCommand.Close -> {
                    inFlight.clear()
                    remote.closeSession(sessionId.value)
                    closed.set(true)
                }
            }
        } catch (error: RemoteException) {
            listener.onSessionFailure(
                sessionId,
                EngineHostFailure(EngineHostFailureCode.TRANSPORT, "Binder call failed: ${error.message.orEmpty()}"),
            )
        }
    }

    private fun stale(message: String) {
        listener.onSessionFailure(sessionId, EngineHostFailure(EngineHostFailureCode.STALE_TRANSPORT, message))
    }
}

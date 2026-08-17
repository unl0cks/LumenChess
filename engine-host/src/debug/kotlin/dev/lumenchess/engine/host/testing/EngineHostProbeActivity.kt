package dev.lumenchess.engine.host.testing

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.ResultReceiver
import dev.lumenchess.core.chess.Chess960
import dev.lumenchess.core.chess.Fen
import dev.lumenchess.core.chess.Position
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineCapabilities
import dev.lumenchess.engine.api.EngineMoveValidation
import dev.lumenchess.engine.api.EngineMoveValidator
import dev.lumenchess.engine.api.EngineMultiPvCapability
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchLimits
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineSessionCommand
import dev.lumenchess.engine.api.EngineSessionId
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.engine.host.transport.EngineHostConnection
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.engine.host.transport.EngineHostFailureCode
import dev.lumenchess.engine.host.transport.EngineHostListener
import dev.lumenchess.engine.host.transport.EngineSlot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Debug-only bridge used by instrumentation to exercise production non-exported engine services from
 * the target package UID. The bridge itself is absent from release builds.
 */
class EngineHostProbeActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val receiver = intent.getParcelableExtra(EXTRA_RECEIVER, ResultReceiver::class.java)
            ?: error("Missing M12 probe result receiver")
        val scenario = intent.getStringExtra(EXTRA_SCENARIO) ?: error("Missing M12 probe scenario")

        executor.execute {
            val result = try {
                runScenario(scenario)
            } catch (error: Throwable) {
                ProbeResult(
                    passed = false,
                    details = "${error::class.java.simpleName}: ${error.message.orEmpty()}",
                )
            }
            val bundle = Bundle().apply {
                putBoolean(KEY_PASSED, result.passed)
                putString(KEY_DETAILS, result.details)
            }
            receiver.send(if (result.passed) RESULT_OK else RESULT_CANCELED, bundle)
            mainHandler.post {
                finish()
                executor.shutdownNow()
            }
        }
    }

    private fun runScenario(scenario: String): ProbeResult = when (scenario) {
        SCENARIO_DUAL_SLOT -> dualSlotScenario()
        SCENARIO_CANCEL_LATE -> cancelLateScenario()
        SCENARIO_MALFORMED -> malformedScenario()
        SCENARIO_CRASH_RECOVERY -> crashRecoveryScenario()
        SCENARIO_TEARDOWN_REBIND -> teardownRebindScenario()
        SCENARIO_STOCKFISH18_STANDARD -> stockfish18Scenario(position, 61, 15)
        SCENARIO_STOCKFISH18_CHESS960 -> stockfish18Scenario(Chess960.startingPosition(0), 62, 16)
        else -> error("Unknown engine-host probe scenario '$scenario'")
    }

    private fun dualSlotScenario(): ProbeResult {
        val aListener = RecordingListener()
        val bListener = RecordingListener()
        val a = connect(EngineSlot.A, aListener)
        val b = connect(EngineSlot.B, bListener)
        try {
            check(aListener.processId != Process.myPid()) { "Slot A was not isolated from caller PID" }
            check(bListener.processId != Process.myPid()) { "Slot B was not isolated from caller PID" }
            check(aListener.processId != bListener.processId) { "Slot A and Slot B shared a PID" }

            val aSessionId = EngineSessionId("slot-a-session")
            val bSessionId = EngineSessionId("slot-b-session")
            val aSession = a.openSession(aSessionId, "mock", capabilities)
            val bSession = b.openSession(bSessionId, "mock", capabilities)
            val aRequest = request(101, 7)
            val bRequest = request(202, 19)

            aSession.submit(EngineSessionCommand.StartSearch(aRequest))
            bSession.submit(EngineSessionCommand.StartSearch(bRequest))

            val aResult = aListener.takeResult()
            val bResult = bListener.takeResult()
            check(aResult.first == aSessionId) { "Slot A session identity changed in transport" }
            check(bResult.first == bSessionId) { "Slot B session identity changed in transport" }
            check(aResult.second.searchId == aRequest.searchId) { "Slot A search identity changed" }
            check(aResult.second.positionRevision == aRequest.positionRevision) { "Slot A revision changed" }
            check(bResult.second.searchId == bRequest.searchId) { "Slot B search identity changed" }
            check(bResult.second.positionRevision == bRequest.positionRevision) { "Slot B revision changed" }
            check(
                EngineMoveValidator.validate(position, aRequest.searchId, aRequest.positionRevision, aResult.second) is
                    EngineMoveValidation.Accepted,
            ) { "Slot A result did not pass the existing core legality boundary" }
            check(
                EngineMoveValidator.validate(position, bRequest.searchId, bRequest.positionRevision, bResult.second) is
                    EngineMoveValidation.Accepted,
            ) { "Slot B result did not pass the existing core legality boundary" }

            aSession.submit(EngineSessionCommand.Close)
            bSession.submit(EngineSessionCommand.Close)
            return ProbeResult(
                true,
                "caller=${Process.myPid()} slotA=${aListener.processId} slotB=${bListener.processId}; correlation/core validation passed",
            )
        } finally {
            a.close()
            b.close()
        }
    }

    private fun cancelLateScenario(): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            val session = connection.openSession(EngineSessionId("cancel-session"), "mock", capabilities)
            val cancelled = request(10, 4)
            val replacement = request(11, 5)

            session.submit(EngineSessionCommand.StartSearch(cancelled))
            session.submit(EngineSessionCommand.StopSearch(cancelled.searchId))
            session.submit(EngineSessionCommand.StartSearch(replacement))

            val delivered = listener.takeResult()
            check(delivered.second.searchId == replacement.searchId) { "Cancelled search was delivered or relabelled" }
            check(delivered.second.positionRevision == replacement.positionRevision) { "Replacement revision was corrupted" }
            Thread.sleep(300)
            check(listener.results.poll() == null) { "Unexpected late result escaped transport" }
            check(listener.failures.poll() == null) { "Unexpected failure after cancel/replacement" }
            session.submit(EngineSessionCommand.Close)
            return ProbeResult(true, "cancelled search 10 discarded; replacement search 11 delivered once")
        } finally {
            connection.close()
        }
    }

    private fun malformedScenario(): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            val session = connection.openSession(EngineSessionId("malformed-session"), "mock-malformed", capabilities)
            session.submit(EngineSessionCommand.StartSearch(request(30, 8)))

            val failure = listener.takeFailure()
            check(failure.first == EngineSessionId("malformed-session")) { "Malformed-output failure lost session identity" }
            check(failure.second.code == EngineHostFailureCode.PROTOCOL) { "Malformed UCI was not a protocol failure" }
            check(listener.results.poll() == null) { "Malformed UCI escaped as a move result" }
            return ProbeResult(true, "malformed known UCI became typed PROTOCOL failure")
        } finally {
            connection.close()
        }
    }

    private fun crashRecoveryScenario(): ProbeResult {
        val crashListener = RecordingListener()
        val survivorListener = RecordingListener()
        val crashing = connect(EngineSlot.A, crashListener)
        val survivor = connect(EngineSlot.B, survivorListener)
        val oldGeneration = crashListener.hostGeneration
        try {
            val crashSession = crashing.openSession(EngineSessionId("crash-session"), "mock-crash", capabilities)
            val survivorSession = survivor.openSession(EngineSessionId("survivor-session"), "mock", capabilities)
            crashSession.submit(EngineSessionCommand.StartSearch(request(40, 9)))
            check(crashListener.death.await(8, TimeUnit.SECONDS)) { "Isolated Slot A death was not observed" }

            survivorSession.submit(EngineSessionCommand.StartSearch(request(41, 10)))
            val survivorResult = survivorListener.takeResult()
            check(survivorResult.second.searchId == EngineSearchId(41)) { "Slot B was corrupted by Slot A death" }
            check(Process.myPid() > 0) { "Caller process did not survive isolated engine death" }
            survivorSession.submit(EngineSessionCommand.Close)
        } finally {
            crashing.close()
            survivor.close()
        }

        val recoveredListener = RecordingListener()
        val recovered = connect(EngineSlot.A, recoveredListener)
        try {
            check(recoveredListener.hostGeneration != oldGeneration) { "Restarted host reused stale generation" }
            val recoveredSession = recovered.openSession(EngineSessionId("recovered-session"), "mock", capabilities)
            recoveredSession.submit(EngineSessionCommand.StartSearch(request(42, 11)))
            val result = recoveredListener.takeResult()
            check(result.second.searchId == EngineSearchId(42)) { "Recovered host could not search" }
            recoveredSession.submit(EngineSessionCommand.Close)
            return ProbeResult(
                true,
                "Slot A crash isolated; Slot B/caller survived; host generation $oldGeneration -> ${recoveredListener.hostGeneration}",
            )
        } finally {
            recovered.close()
        }
    }

    private fun teardownRebindScenario(): ProbeResult {
        val firstListener = RecordingListener()
        val first = connect(EngineSlot.A, firstListener)
        val firstGeneration = firstListener.hostGeneration
        try {
            val firstSession = first.openSession(EngineSessionId("first-session"), "mock", capabilities)
            firstSession.submit(EngineSessionCommand.StartSearch(request(51, 12)))
            check(firstListener.takeResult().second.searchId == EngineSearchId(51)) { "Initial session search failed" }
            firstSession.submit(EngineSessionCommand.Close)

            val secondSession = first.openSession(EngineSessionId("second-session"), "mock", capabilities)
            secondSession.submit(EngineSessionCommand.StartSearch(request(52, 13)))
            check(firstListener.takeResult().second.searchId == EngineSearchId(52)) { "Session resources were not released" }
            secondSession.submit(EngineSessionCommand.Close)
        } finally {
            first.close()
        }

        Thread.sleep(250)
        val reboundListener = RecordingListener()
        val rebound = connect(EngineSlot.A, reboundListener)
        try {
            check(reboundListener.hostGeneration != firstGeneration) { "Full connection teardown reused old host generation" }
            val session = rebound.openSession(EngineSessionId("rebound-session"), "mock", capabilities)
            session.submit(EngineSessionCommand.StartSearch(request(53, 14)))
            check(reboundListener.takeResult().second.searchId == EngineSearchId(53)) { "Rebound host search failed" }
            session.submit(EngineSessionCommand.Close)
            return ProbeResult(
                true,
                "session close/reopen and full unbind/rebind succeeded; generation $firstGeneration -> ${reboundListener.hostGeneration}",
            )
        } finally {
            rebound.close()
        }
    }

    private fun stockfish18Scenario(searchPosition: Position, searchId: Long, revision: Long): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            check(listener.processId != Process.myPid()) { "Stockfish was not isolated from caller PID" }
            val sessionId = EngineSessionId("stockfish-18-${searchPosition.variant.name.lowercase()}")
            val session = connection.openSession(sessionId, "stockfish-18", capabilities)
            val request = EngineSearchRequest(
                searchId = EngineSearchId(searchId),
                positionRevision = PositionRevision(revision),
                position = searchPosition,
                limits = EngineSearchLimits(depth = 1),
            )
            session.submit(EngineSessionCommand.StartSearch(request))
            val delivered = listener.takeResult()
            check(delivered.first == sessionId) { "Stockfish session identity changed in transport" }
            check(delivered.second.searchId == request.searchId) { "Stockfish search identity changed in transport" }
            check(delivered.second.positionRevision == request.positionRevision) { "Stockfish revision changed in transport" }
            check(
                EngineMoveValidator.validate(
                    searchPosition,
                    request.searchId,
                    request.positionRevision,
                    delivered.second,
                ) is EngineMoveValidation.Accepted,
            ) { "Stockfish result did not pass the existing core legality boundary: ${delivered.second.bestMoveUci}" }
            session.submit(EngineSessionCommand.Close)
            return ProbeResult(
                true,
                "Stockfish 18 ${searchPosition.variant.name} result ${delivered.second.bestMoveUci} passed correlation/core validation in isolated PID ${listener.processId}",
            )
        } finally {
            connection.close()
        }
    }

    private fun connect(slot: EngineSlot, listener: RecordingListener): EngineHostConnection {
        val connection = EngineHostConnection(this, slot, listener)
        check(connection.bind()) { "bindService failed for $slot" }
        check(listener.connected.await(8, TimeUnit.SECONDS)) { "$slot did not connect" }
        return connection
    }

    private fun request(searchId: Long, revision: Long): EngineSearchRequest = EngineSearchRequest(
        searchId = EngineSearchId(searchId),
        positionRevision = PositionRevision(revision),
        position = position,
        limits = EngineSearchLimits(depth = 1),
    )

    private data class ProbeResult(val passed: Boolean, val details: String)

    private class RecordingListener : EngineHostListener {
        val connected = CountDownLatch(1)
        val death = CountDownLatch(1)
        val results = LinkedBlockingQueue<Pair<EngineSessionId, EngineSearchResult>>()
        val failures = LinkedBlockingQueue<Pair<EngineSessionId?, EngineHostFailure>>()
        @Volatile var processId: Int = -1
        @Volatile var hostGeneration: Long = 0L

        override fun onConnected(slot: EngineSlot, processId: Int, hostGeneration: Long) {
            this.processId = processId
            this.hostGeneration = hostGeneration
            connected.countDown()
        }

        override fun onSearchResult(sessionId: EngineSessionId, result: EngineSearchResult) {
            results.put(sessionId to result)
        }

        override fun onSessionFailure(sessionId: EngineSessionId?, failure: EngineHostFailure) {
            failures.put(sessionId to failure)
        }

        override fun onHostDied(slot: EngineSlot, hostGeneration: Long) {
            death.countDown()
        }

        fun takeResult(): Pair<EngineSessionId, EngineSearchResult> =
            requireNotNull(results.poll(8, TimeUnit.SECONDS)) { "Timed out waiting for engine result" }

        fun takeFailure(): Pair<EngineSessionId?, EngineHostFailure> =
            requireNotNull(failures.poll(8, TimeUnit.SECONDS)) { "Timed out waiting for engine-host failure" }
    }

    companion object {
        const val EXTRA_SCENARIO = "dev.lumenchess.engine.host.testing.SCENARIO"
        const val EXTRA_RECEIVER = "dev.lumenchess.engine.host.testing.RECEIVER"
        const val KEY_PASSED = "passed"
        const val KEY_DETAILS = "details"

        const val SCENARIO_DUAL_SLOT = "dual-slot"
        const val SCENARIO_CANCEL_LATE = "cancel-late"
        const val SCENARIO_MALFORMED = "malformed"
        const val SCENARIO_CRASH_RECOVERY = "crash-recovery"
        const val SCENARIO_TEARDOWN_REBIND = "teardown-rebind"
        const val SCENARIO_STOCKFISH18_STANDARD = "stockfish18-standard"
        const val SCENARIO_STOCKFISH18_CHESS960 = "stockfish18-chess960"

        private val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        private val capabilities = EngineCapabilities(
            variants = setOf(Variant.STANDARD, Variant.CHESS960),
            multiPv = EngineMultiPvCapability(4),
        )
    }
}

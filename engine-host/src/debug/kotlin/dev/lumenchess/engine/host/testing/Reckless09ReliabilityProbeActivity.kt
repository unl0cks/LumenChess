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
import dev.lumenchess.engine.api.EngineMoveValidation
import dev.lumenchess.engine.api.EngineMoveValidator
import dev.lumenchess.engine.api.EngineSearchId
import dev.lumenchess.engine.api.EngineSearchLimits
import dev.lumenchess.engine.api.EngineSearchRequest
import dev.lumenchess.engine.api.EngineSearchResult
import dev.lumenchess.engine.api.EngineSessionCommand
import dev.lumenchess.engine.api.EngineSessionId
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.engine.host.Reckless09Engine
import dev.lumenchess.engine.host.transport.EngineHostConnection
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.engine.host.transport.EngineHostFailureCode
import dev.lumenchess.engine.host.transport.EngineHostListener
import dev.lumenchess.engine.host.transport.EngineSlot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Debug-only target-UID probe for the real pinned Reckless 0.9.0 adapter lifecycle. */
class Reckless09ReliabilityProbeActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val receiver = intent.getParcelableExtra(EXTRA_RECEIVER, ResultReceiver::class.java)
            ?: error("Missing Reckless 0.9.0 probe result receiver")
        val scenario = intent.getStringExtra(EXTRA_SCENARIO) ?: error("Missing Reckless 0.9.0 probe scenario")

        executor.execute {
            val result = try {
                when (scenario) {
                    SCENARIO_STANDARD -> searchScenario(standardPosition, 101, 51)
                    SCENARIO_CHESS960 -> searchScenario(Chess960.startingPosition(0), 102, 52)
                    SCENARIO_CANCEL_REPLACEMENT -> cancelReplacement()
                    SCENARIO_SESSION_REOPEN -> sessionReopen()
                    SCENARIO_DUAL_SLOT -> dualSlot()
                    else -> error("Unknown Reckless 0.9.0 reliability scenario '$scenario'")
                }
            } catch (error: Throwable) {
                ProbeResult(false, "${error::class.java.simpleName}: ${error.message.orEmpty()}")
            }
            receiver.send(
                if (result.passed) RESULT_OK else RESULT_CANCELED,
                Bundle().apply {
                    putBoolean(KEY_PASSED, result.passed)
                    putString(KEY_DETAILS, result.details)
                },
            )
            mainHandler.post {
                finish()
                executor.shutdownNow()
            }
        }
    }

    private fun searchScenario(searchPosition: Position, searchId: Long, revision: Long): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            check(listener.processId != Process.myPid()) { "Reckless was not isolated from caller PID" }
            val sessionId = EngineSessionId("reckless-09-${searchPosition.variant.name.lowercase()}")
            val session = connection.openSession(
                sessionId,
                Reckless09Engine.ID,
                Reckless09Engine.capabilities,
            )
            val request = EngineSearchRequest(
                searchId = EngineSearchId(searchId),
                positionRevision = PositionRevision(revision),
                position = searchPosition,
                limits = EngineSearchLimits(depth = 1),
            )
            session.submit(EngineSessionCommand.StartSearch(request))
            val delivered = listener.takeResult(15)
            check(delivered.first == sessionId) { "Reckless session identity changed in transport" }
            check(delivered.second.searchId == request.searchId) { "Reckless search identity changed in transport" }
            check(delivered.second.positionRevision == request.positionRevision) { "Reckless revision changed in transport" }
            check(
                EngineMoveValidator.validate(
                    searchPosition,
                    request.searchId,
                    request.positionRevision,
                    delivered.second,
                ) is EngineMoveValidation.Accepted,
            ) { "Reckless result failed the existing core legality boundary: ${delivered.second.bestMoveUci}" }
            session.submit(EngineSessionCommand.Close)
            return ProbeResult(
                true,
                "Reckless 0.9.0 ${searchPosition.variant.name} result ${delivered.second.bestMoveUci} passed correlation/core validation in isolated PID ${listener.processId}",
            )
        } finally {
            connection.close()
        }
    }

    private fun cancelReplacement(): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            val session = connection.openSession(
                EngineSessionId("reckless-cancel"),
                Reckless09Engine.ID,
                Reckless09Engine.capabilities,
            )

            val warmup = request(111, 61, EngineSearchLimits(depth = 1))
            session.submit(EngineSessionCommand.StartSearch(warmup))
            check(listener.takeResult(15).second.searchId == warmup.searchId) { "Reckless warmup search failed" }

            val cancelled = request(112, 62, EngineSearchLimits(nodes = 50_000_000L))
            val replacement = request(113, 63, EngineSearchLimits(depth = 1))
            session.submit(EngineSessionCommand.StartSearch(cancelled))
            Thread.sleep(50)
            session.submit(EngineSessionCommand.StopSearch(cancelled.searchId))
            session.submit(EngineSessionCommand.StartSearch(replacement))

            val delivered = listener.takeResult(20)
            check(delivered.second.searchId == replacement.searchId) {
                "Cancelled Reckless result escaped or was relabelled: ${delivered.second.searchId}"
            }
            check(
                EngineMoveValidator.validate(
                    standardPosition,
                    replacement.searchId,
                    replacement.positionRevision,
                    delivered.second,
                ) is EngineMoveValidation.Accepted,
            ) { "Replacement Reckless result failed core validation" }
            Thread.sleep(250)
            check(listener.results.poll() == null) { "A late cancelled Reckless result escaped transport" }
            check(listener.failures.poll() == null) { "Unexpected Reckless failure after cancel/replacement" }
            session.submit(EngineSessionCommand.Close)
            return ProbeResult(true, "real Reckless cancel terminal output discarded before replacement")
        } finally {
            connection.close()
        }
    }

    private fun sessionReopen(): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            val first = connection.openSession(
                EngineSessionId("reckless-first"),
                Reckless09Engine.ID,
                Reckless09Engine.capabilities,
            )
            val firstRequest = request(121, 71, EngineSearchLimits(depth = 1))
            first.submit(EngineSessionCommand.StartSearch(firstRequest))
            check(listener.takeResult(15).second.searchId == firstRequest.searchId) { "First Reckless session failed" }
            first.submit(EngineSessionCommand.Close)

            val second = connection.openSession(
                EngineSessionId("reckless-second"),
                Reckless09Engine.ID,
                Reckless09Engine.capabilities,
            )
            val secondRequest = request(122, 72, EngineSearchLimits(depth = 1))
            second.submit(EngineSessionCommand.StartSearch(secondRequest))
            val delivered = listener.takeResult(20)
            check(delivered.first == EngineSessionId("reckless-second")) { "Reopened Reckless session identity changed" }
            check(delivered.second.searchId == secondRequest.searchId) { "Reopened Reckless session failed" }
            second.submit(EngineSessionCommand.Close)
            return ProbeResult(true, "real Reckless native session closed and reopened in the same isolated host")
        } finally {
            connection.close()
        }
    }

    private fun dualSlot(): ProbeResult {
        val aListener = RecordingListener()
        val bListener = RecordingListener()
        val a = connect(EngineSlot.A, aListener)
        val b = connect(EngineSlot.B, bListener)
        try {
            check(aListener.processId != Process.myPid()) { "Reckless Slot A was not isolated" }
            check(bListener.processId != Process.myPid()) { "Reckless Slot B was not isolated" }
            check(aListener.processId != bListener.processId) { "Real Reckless slots shared a process" }

            val aSession = a.openSession(
                EngineSessionId("reckless-real-a"),
                Reckless09Engine.ID,
                Reckless09Engine.capabilities,
            )
            val bSession = b.openSession(
                EngineSessionId("reckless-real-b"),
                Reckless09Engine.ID,
                Reckless09Engine.capabilities,
            )
            val aRequest = request(131, 81, EngineSearchLimits(depth = 1))
            val bRequest = request(132, 82, EngineSearchLimits(depth = 1))
            aSession.submit(EngineSessionCommand.StartSearch(aRequest))
            bSession.submit(EngineSessionCommand.StartSearch(bRequest))
            val aResult = aListener.takeResult(20)
            val bResult = bListener.takeResult(20)
            check(
                EngineMoveValidator.validate(
                    standardPosition,
                    aRequest.searchId,
                    aRequest.positionRevision,
                    aResult.second,
                ) is EngineMoveValidation.Accepted,
            ) { "Real Reckless Slot A result failed core validation" }
            check(
                EngineMoveValidator.validate(
                    standardPosition,
                    bRequest.searchId,
                    bRequest.positionRevision,
                    bResult.second,
                ) is EngineMoveValidation.Accepted,
            ) { "Real Reckless Slot B result failed core validation" }
            aSession.submit(EngineSessionCommand.Close)
            bSession.submit(EngineSessionCommand.Close)
            return ProbeResult(
                true,
                "real Reckless simultaneous slots isolated at PIDs ${aListener.processId}/${bListener.processId}",
            )
        } finally {
            a.close()
            b.close()
        }
    }

    private fun connect(slot: EngineSlot, listener: RecordingListener): EngineHostConnection {
        val connection = EngineHostConnection(this, slot, listener)
        check(connection.bind()) { "bindService failed for $slot" }
        check(listener.connected.await(8, TimeUnit.SECONDS)) { "$slot did not connect" }
        return connection
    }

    private fun request(searchId: Long, revision: Long, limits: EngineSearchLimits) = EngineSearchRequest(
        searchId = EngineSearchId(searchId),
        positionRevision = PositionRevision(revision),
        position = standardPosition,
        limits = limits,
    )

    private data class ProbeResult(val passed: Boolean, val details: String)

    private class RecordingListener : EngineHostListener {
        val connected = CountDownLatch(1)
        val results = LinkedBlockingQueue<Pair<EngineSessionId, EngineSearchResult>>()
        val failures = LinkedBlockingQueue<Pair<EngineSessionId?, EngineHostFailure>>()
        @Volatile var processId: Int = -1

        override fun onConnected(slot: EngineSlot, processId: Int, hostGeneration: Long) {
            this.processId = processId
            connected.countDown()
        }

        override fun onSearchResult(sessionId: EngineSessionId, result: EngineSearchResult) {
            results.put(sessionId to result)
        }

        override fun onSessionFailure(sessionId: EngineSessionId?, failure: EngineHostFailure) {
            failures.put(sessionId to failure)
        }

        override fun onHostDied(slot: EngineSlot, hostGeneration: Long) {
            failures.offer(null to EngineHostFailure(EngineHostFailureCode.TRANSPORT, "host died"))
        }

        fun takeResult(seconds: Long): Pair<EngineSessionId, EngineSearchResult> =
            requireNotNull(results.poll(seconds, TimeUnit.SECONDS)) { "Timed out waiting for real Reckless result" }
    }

    companion object {
        const val EXTRA_SCENARIO = "dev.lumenchess.engine.host.testing.RECKLESS09_SCENARIO"
        const val EXTRA_RECEIVER = "dev.lumenchess.engine.host.testing.RECKLESS09_RECEIVER"
        const val KEY_PASSED = "passed"
        const val KEY_DETAILS = "details"
        const val SCENARIO_STANDARD = "reckless09-standard"
        const val SCENARIO_CHESS960 = "reckless09-chess960"
        const val SCENARIO_CANCEL_REPLACEMENT = "reckless09-cancel-replacement"
        const val SCENARIO_SESSION_REOPEN = "reckless09-session-reopen"
        const val SCENARIO_DUAL_SLOT = "reckless09-dual-slot"

        private val standardPosition = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }
}

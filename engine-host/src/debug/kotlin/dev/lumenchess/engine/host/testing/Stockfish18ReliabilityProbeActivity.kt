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
import dev.lumenchess.engine.api.EngineStrengthModel
import dev.lumenchess.engine.api.EngineStrengthSettings
import dev.lumenchess.engine.api.EngineStrengthTarget
import dev.lumenchess.engine.api.PositionRevision
import dev.lumenchess.engine.host.Stockfish18Engine
import dev.lumenchess.engine.host.transport.EngineHostConnection
import dev.lumenchess.engine.host.transport.EngineHostFailure
import dev.lumenchess.engine.host.transport.EngineHostFailureCode
import dev.lumenchess.engine.host.transport.EngineHostListener
import dev.lumenchess.engine.host.transport.EngineSlot
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Debug-only target-UID probe for the real Stockfish 18 adapter lifecycle. */
class Stockfish18ReliabilityProbeActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val receiver = intent.getParcelableExtra(EXTRA_RECEIVER, ResultReceiver::class.java)
            ?: error("Missing Stockfish 18 probe result receiver")
        val scenario = intent.getStringExtra(EXTRA_SCENARIO) ?: error("Missing Stockfish 18 probe scenario")

        executor.execute {
            val result = try {
                when (scenario) {
                    SCENARIO_CANCEL_REPLACEMENT -> cancelReplacement()
                    SCENARIO_SESSION_REOPEN -> sessionReopen()
                    SCENARIO_DUAL_SLOT -> dualSlot()
                    SCENARIO_STRENGTH_MODELS -> strengthModels()
                    else -> error("Unknown Stockfish 18 reliability scenario '$scenario'")
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

    private fun cancelReplacement(): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            val session = connection.openSession(
                EngineSessionId("stockfish-cancel"),
                Stockfish18Engine.ID,
                Stockfish18Engine.capabilities,
            )

            val warmup = request(71, 21, EngineSearchLimits(depth = 1))
            session.submit(EngineSessionCommand.StartSearch(warmup))
            check(listener.takeResult().second.searchId == warmup.searchId) { "Stockfish warmup search failed" }

            val cancelled = request(72, 22, EngineSearchLimits(nodes = 50_000_000L))
            val replacement = request(73, 23, EngineSearchLimits(depth = 1))
            session.submit(EngineSessionCommand.StartSearch(cancelled))
            Thread.sleep(50)
            session.submit(EngineSessionCommand.StopSearch(cancelled.searchId))
            session.submit(EngineSessionCommand.StartSearch(replacement))

            val delivered = listener.takeResult()
            check(delivered.second.searchId == replacement.searchId) {
                "Cancelled Stockfish result escaped or was relabelled: ${delivered.second.searchId}"
            }
            check(
                EngineMoveValidator.validate(
                    position,
                    replacement.searchId,
                    replacement.positionRevision,
                    delivered.second,
                ) is EngineMoveValidation.Accepted,
            ) { "Replacement Stockfish result failed core validation" }
            Thread.sleep(250)
            check(listener.results.poll() == null) { "A late cancelled Stockfish result escaped transport" }
            check(listener.failures.poll() == null) { "Unexpected Stockfish failure after cancel/replacement" }
            session.submit(EngineSessionCommand.Close)
            return ProbeResult(true, "real Stockfish cancel terminal output discarded before replacement")
        } finally {
            connection.close()
        }
    }

    private fun sessionReopen(): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        try {
            val first = connection.openSession(
                EngineSessionId("stockfish-first"),
                Stockfish18Engine.ID,
                Stockfish18Engine.capabilities,
            )
            val firstRequest = request(81, 31, EngineSearchLimits(depth = 1))
            first.submit(EngineSessionCommand.StartSearch(firstRequest))
            check(listener.takeResult().second.searchId == firstRequest.searchId) { "First Stockfish session failed" }
            first.submit(EngineSessionCommand.Close)

            val second = connection.openSession(
                EngineSessionId("stockfish-second"),
                Stockfish18Engine.ID,
                Stockfish18Engine.capabilities,
            )
            val secondRequest = request(82, 32, EngineSearchLimits(depth = 1))
            second.submit(EngineSessionCommand.StartSearch(secondRequest))
            val delivered = listener.takeResult(15)
            check(delivered.first == EngineSessionId("stockfish-second")) { "Reopened session identity changed" }
            check(delivered.second.searchId == secondRequest.searchId) { "Reopened Stockfish session failed" }
            second.submit(EngineSessionCommand.Close)
            return ProbeResult(true, "real Stockfish native session closed and reopened in the same isolated host")
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
            check(aListener.processId != Process.myPid()) { "Stockfish Slot A was not isolated" }
            check(bListener.processId != Process.myPid()) { "Stockfish Slot B was not isolated" }
            check(aListener.processId != bListener.processId) { "Real Stockfish slots shared a process" }

            val aSession = a.openSession(
                EngineSessionId("stockfish-real-a"),
                Stockfish18Engine.ID,
                Stockfish18Engine.capabilities,
            )
            val bSession = b.openSession(
                EngineSessionId("stockfish-real-b"),
                Stockfish18Engine.ID,
                Stockfish18Engine.capabilities,
            )
            val aRequest = request(91, 41, EngineSearchLimits(depth = 1))
            val bRequest = request(92, 42, EngineSearchLimits(depth = 1))
            aSession.submit(EngineSessionCommand.StartSearch(aRequest))
            bSession.submit(EngineSessionCommand.StartSearch(bRequest))
            val aResult = aListener.takeResult(15)
            val bResult = bListener.takeResult(15)
            check(
                EngineMoveValidator.validate(position, aRequest.searchId, aRequest.positionRevision, aResult.second) is
                    EngineMoveValidation.Accepted,
            ) { "Real Slot A result failed core validation" }
            check(
                EngineMoveValidator.validate(position, bRequest.searchId, bRequest.positionRevision, bResult.second) is
                    EngineMoveValidation.Accepted,
            ) { "Real Slot B result failed core validation" }
            aSession.submit(EngineSessionCommand.Close)
            bSession.submit(EngineSessionCommand.Close)
            return ProbeResult(
                true,
                "real Stockfish simultaneous slots isolated at PIDs ${aListener.processId}/${bListener.processId}",
            )
        } finally {
            a.close()
            b.close()
        }
    }

    private fun strengthModels(): ProbeResult {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        val hybrid = EngineStrengthSettings(
            target = EngineStrengthTarget.Elo(1200),
            model = EngineStrengthModel.HYBRID,
            seed = 0x51A7L,
        )
        try {
            val first = connection.openSession(
                EngineSessionId("stockfish-strength-first"),
                Stockfish18Engine.ID,
                Stockfish18Engine.capabilities,
            )
            val firstRequest = strengthRequest(141, 91, position, hybrid)
            first.submit(EngineSessionCommand.StartSearch(firstRequest))
            val firstResult = listener.takeResult(20).second
            validate(position, firstRequest, firstResult, "First finite Hybrid Stockfish result")
            first.submit(EngineSessionCommand.Close)

            val second = connection.openSession(
                EngineSessionId("stockfish-strength-second"),
                Stockfish18Engine.ID,
                Stockfish18Engine.capabilities,
            )
            val repeatedRequest = strengthRequest(141, 91, position, hybrid)
            second.submit(EngineSessionCommand.StartSearch(repeatedRequest))
            val repeatedResult = listener.takeResult(20).second
            validate(position, repeatedRequest, repeatedResult, "Repeated finite Hybrid Stockfish result")
            check(repeatedResult.bestMoveUci == firstResult.bestMoveUci) {
                "Same Stockfish seed/search/revision was not deterministic: ${firstResult.bestMoveUci} vs ${repeatedResult.bestMoveUci}"
            }

            val chess960Position = Chess960.startingPosition(0)
            val chess960Request = strengthRequest(142, 92, chess960Position, hybrid)
            second.submit(EngineSessionCommand.StartSearch(chess960Request))
            val chess960Result = listener.takeResult(20).second
            validate(chess960Position, chess960Request, chess960Result, "Finite Hybrid Chess960 Stockfish result")

            val nativeRequest = strengthRequest(
                searchId = 143,
                revision = 93,
                searchPosition = position,
                strength = EngineStrengthSettings(
                    target = EngineStrengthTarget.Elo(1600),
                    model = EngineStrengthModel.ENGINE_NATIVE,
                    seed = 0xA11CEL,
                ),
            )
            second.submit(EngineSessionCommand.StartSearch(nativeRequest))
            val nativeResult = listener.takeResult(20).second
            validate(position, nativeRequest, nativeResult, "Finite Engine Native Stockfish result")
            check(listener.failures.poll() == null) { "Stockfish strength scenario emitted an unexpected typed failure" }
            second.submit(EngineSessionCommand.Close)

            return ProbeResult(
                true,
                "Stockfish finite Hybrid deterministic across fresh sessions; Standard/Chess960 core-valid; Engine Native finite accepted",
            )
        } finally {
            connection.close()
        }
    }

    private fun validate(
        searchPosition: Position,
        request: EngineSearchRequest,
        result: EngineSearchResult,
        label: String,
    ) {
        check(
            EngineMoveValidator.validate(
                searchPosition,
                request.searchId,
                request.positionRevision,
                result,
            ) is EngineMoveValidation.Accepted,
        ) { "$label failed core legality validation: ${result.bestMoveUci}" }
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
        position = position,
        limits = limits,
    )

    private fun strengthRequest(
        searchId: Long,
        revision: Long,
        searchPosition: Position,
        strength: EngineStrengthSettings,
    ) = EngineSearchRequest(
        searchId = EngineSearchId(searchId),
        positionRevision = PositionRevision(revision),
        position = searchPosition,
        limits = EngineSearchLimits(depth = 4),
        strength = strength,
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

        fun takeResult(seconds: Long = 8): Pair<EngineSessionId, EngineSearchResult> =
            requireNotNull(results.poll(seconds, TimeUnit.SECONDS)) { "Timed out waiting for real Stockfish result" }
    }

    companion object {
        const val EXTRA_SCENARIO = "dev.lumenchess.engine.host.testing.STOCKFISH18_SCENARIO"
        const val EXTRA_RECEIVER = "dev.lumenchess.engine.host.testing.STOCKFISH18_RECEIVER"
        const val KEY_PASSED = "passed"
        const val KEY_DETAILS = "details"
        const val SCENARIO_CANCEL_REPLACEMENT = "stockfish18-cancel-replacement"
        const val SCENARIO_SESSION_REOPEN = "stockfish18-session-reopen"
        const val SCENARIO_DUAL_SLOT = "stockfish18-dual-slot"
        const val SCENARIO_STRENGTH_MODELS = "stockfish18-strength-models"

        private val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    }
}

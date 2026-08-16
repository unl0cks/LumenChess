package dev.lumenchess.engine.host

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.core.chess.Fen
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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineHostIsolationTest {
    private val connections = mutableListOf<EngineHostConnection>()
    private val position = Fen.parse("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
    private val capabilities = EngineCapabilities(
        variants = setOf(Variant.STANDARD, Variant.CHESS960),
        multiPv = EngineMultiPvCapability(4),
    )

    @After
    fun tearDown() {
        connections.reversed().forEach { it.close() }
        connections.clear()
    }

    @Test
    fun twoSlotsAreIndependentIsolatedProcessesAndPreserveCorrelation() {
        val aListener = RecordingListener()
        val bListener = RecordingListener()
        val a = connect(EngineSlot.A, aListener)
        val b = connect(EngineSlot.B, bListener)

        assertNotEquals(Process.myPid(), aListener.processId)
        assertNotEquals(Process.myPid(), bListener.processId)
        assertNotEquals(aListener.processId, bListener.processId)

        val aSession = a.openSession(EngineSessionId("slot-a-session"), "mock", capabilities)
        val bSession = b.openSession(EngineSessionId("slot-b-session"), "mock", capabilities)
        val aRequest = request(101, 7)
        val bRequest = request(202, 19)

        aSession.submit(EngineSessionCommand.StartSearch(aRequest))
        bSession.submit(EngineSessionCommand.StartSearch(bRequest))

        val aResult = aListener.takeResult()
        val bResult = bListener.takeResult()
        assertEquals(EngineSessionId("slot-a-session"), aResult.first)
        assertEquals(EngineSessionId("slot-b-session"), bResult.first)
        assertEquals(aRequest.searchId, aResult.second.searchId)
        assertEquals(aRequest.positionRevision, aResult.second.positionRevision)
        assertEquals(bRequest.searchId, bResult.second.searchId)
        assertEquals(bRequest.positionRevision, bResult.second.positionRevision)
        assertTrue(
            EngineMoveValidator.validate(position, aRequest.searchId, aRequest.positionRevision, aResult.second) is
                EngineMoveValidation.Accepted,
        )
        assertTrue(
            EngineMoveValidator.validate(position, bRequest.searchId, bRequest.positionRevision, bResult.second) is
                EngineMoveValidation.Accepted,
        )
    }

    @Test
    fun cancelledLateOutputCannotBeRelabelledAsTheNextSearch() {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        val session = connection.openSession(EngineSessionId("cancel-session"), "mock", capabilities)
        val cancelled = request(10, 4)
        val replacement = request(11, 5)

        session.submit(EngineSessionCommand.StartSearch(cancelled))
        session.submit(EngineSessionCommand.StopSearch(cancelled.searchId))
        session.submit(EngineSessionCommand.StartSearch(replacement))

        val delivered = listener.takeResult()
        assertEquals(replacement.searchId, delivered.second.searchId)
        assertEquals(replacement.positionRevision, delivered.second.positionRevision)
        Thread.sleep(300)
        assertNull(listener.results.poll())
        assertNull(listener.failures.poll())
    }

    @Test
    fun malformedKnownUciOutputBecomesTypedFailureInsteadOfSearchResult() {
        val listener = RecordingListener()
        val connection = connect(EngineSlot.A, listener)
        val session = connection.openSession(EngineSessionId("malformed-session"), "mock-malformed", capabilities)

        session.submit(EngineSessionCommand.StartSearch(request(30, 8)))

        val failure = listener.takeFailure()
        assertEquals(EngineSessionId("malformed-session"), failure.first)
        assertEquals(EngineHostFailureCode.PROTOCOL, failure.second.code)
        assertNull(listener.results.poll())
    }

    @Test
    fun crashedSlotDoesNotKillCallerOrOtherSlotAndCanBeRecovered() {
        val crashListener = RecordingListener()
        val survivorListener = RecordingListener()
        val crashing = connect(EngineSlot.A, crashListener)
        val survivor = connect(EngineSlot.B, survivorListener)
        val oldGeneration = crashListener.hostGeneration

        val crashSession = crashing.openSession(EngineSessionId("crash-session"), "mock-crash", capabilities)
        val survivorSession = survivor.openSession(EngineSessionId("survivor-session"), "mock", capabilities)
        crashSession.submit(EngineSessionCommand.StartSearch(request(40, 9)))

        assertTrue("isolated slot death was not observed", crashListener.death.await(8, TimeUnit.SECONDS))

        survivorSession.submit(EngineSessionCommand.StartSearch(request(41, 10)))
        val survivorResult = survivorListener.takeResult()
        assertEquals(EngineSearchId(41), survivorResult.second.searchId)
        assertTrue(Process.myPid() > 0)

        crashing.close()
        connections.remove(crashing)

        val recoveredListener = RecordingListener()
        val recovered = connect(EngineSlot.A, recoveredListener)
        assertNotEquals(oldGeneration, recoveredListener.hostGeneration)
        val recoveredSession = recovered.openSession(EngineSessionId("recovered-session"), "mock", capabilities)
        recoveredSession.submit(EngineSessionCommand.StartSearch(request(42, 11)))
        assertEquals(EngineSearchId(42), recoveredListener.takeResult().second.searchId)
    }

    private fun connect(slot: EngineSlot, listener: RecordingListener): EngineHostConnection {
        // Use the target package context so the test exercises the production non-exported service.
        // ApplicationProvider returns the instrumentation APK context here, which cannot bind to a
        // non-exported target service and previously hid the actual isolation/lifecycle assertions.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connection = EngineHostConnection(context, slot, listener)
        connections += connection
        assertTrue("bindService failed for $slot", connection.bind())
        assertTrue("$slot did not connect", listener.connected.await(8, TimeUnit.SECONDS))
        return connection
    }

    private fun request(searchId: Long, revision: Long): EngineSearchRequest = EngineSearchRequest(
        searchId = EngineSearchId(searchId),
        positionRevision = PositionRevision(revision),
        position = position,
        limits = EngineSearchLimits(depth = 1),
    )

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
}

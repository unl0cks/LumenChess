package dev.lumenchess.engine.host

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.lumenchess.core.chess.Variant
import dev.lumenchess.engine.api.EngineStrengthCapability
import dev.lumenchess.engine.host.testing.EngineHostProbeActivity
import dev.lumenchess.engine.host.testing.Reckless09ReliabilityProbeActivity
import dev.lumenchess.engine.host.testing.Stockfish18ReliabilityProbeActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineHostIsolationTest {
    @Test
    fun twoSlotsAreIndependentIsolatedProcessesAndPreserveCorrelation() {
        val details = runEngineHostScenario(EngineHostProbeActivity.SCENARIO_DUAL_SLOT)
        assertTrue(details.contains("correlation/core validation passed"))
    }

    @Test
    fun cancelledLateOutputCannotBeRelabelledAsTheNextSearch() {
        val details = runEngineHostScenario(EngineHostProbeActivity.SCENARIO_CANCEL_LATE)
        assertTrue(details.contains("discarded"))
    }

    @Test
    fun malformedKnownUciOutputBecomesTypedFailureInsteadOfSearchResult() {
        val details = runEngineHostScenario(EngineHostProbeActivity.SCENARIO_MALFORMED)
        assertTrue(details.contains("PROTOCOL"))
    }

    @Test
    fun crashedSlotDoesNotKillCallerOrOtherSlotAndCanBeRecovered() {
        val details = runEngineHostScenario(EngineHostProbeActivity.SCENARIO_CRASH_RECOVERY)
        assertTrue(details.contains("crash isolated"))
    }

    @Test
    fun closeReleasesSessionAndFullUnbindCanRebindFreshHost() {
        val details = runEngineHostScenario(EngineHostProbeActivity.SCENARIO_TEARDOWN_REBIND)
        assertTrue(details.contains("unbind/rebind succeeded"))
    }

    @Test
    fun stockfish18CapabilitiesMatchPinnedRelease() {
        val capabilities = Stockfish18Engine.capabilities
        assertEquals(setOf(Variant.STANDARD, Variant.CHESS960), capabilities.variants)
        assertEquals(256, capabilities.multiPv?.maxLines)
        assertTrue(capabilities.supportsPonder)
        val strength = capabilities.strength as EngineStrengthCapability.EloRange
        assertEquals(1320, strength.minElo)
        assertEquals(3190, strength.maxElo)
        assertEquals("sf_18", Stockfish18Engine.SOURCE_TAG)
        assertEquals("cb3d4ee9b47d0c5aae855b12379378ea1439675c", Stockfish18Engine.SOURCE_COMMIT)
    }

    @Test
    fun stockfish18StandardSearchRunsInIsolatedHostAndPassesCoreValidation() {
        val details = runEngineHostScenario(EngineHostProbeActivity.SCENARIO_STOCKFISH18_STANDARD)
        assertTrue(details.contains("Stockfish 18 STANDARD"))
        assertTrue(details.contains("correlation/core validation"))
    }

    @Test
    fun stockfish18Chess960SearchRunsInIsolatedHostAndPassesCoreValidation() {
        val details = runEngineHostScenario(EngineHostProbeActivity.SCENARIO_STOCKFISH18_CHESS960)
        assertTrue(details.contains("Stockfish 18 CHESS960"))
        assertTrue(details.contains("correlation/core validation"))
    }

    @Test
    fun stockfish18CancellationDiscardsRealTerminalOutputBeforeReplacement() {
        val details = runStockfishScenario(Stockfish18ReliabilityProbeActivity.SCENARIO_CANCEL_REPLACEMENT)
        assertTrue(details.contains("cancel terminal output discarded"))
    }

    @Test
    fun stockfish18NativeSessionCanCloseAndReopenInSameIsolatedHost() {
        val details = runStockfishScenario(Stockfish18ReliabilityProbeActivity.SCENARIO_SESSION_REOPEN)
        assertTrue(details.contains("closed and reopened"))
    }

    @Test
    fun stockfish18CanRunSimultaneouslyInBothIsolatedSlots() {
        val details = runStockfishScenario(Stockfish18ReliabilityProbeActivity.SCENARIO_DUAL_SLOT)
        assertTrue(details.contains("simultaneous slots isolated"))
    }

    @Test
    fun stockfish18StrengthModelsAreDeterministicLegalAndNativeAware() {
        val details = runStockfishScenario(Stockfish18ReliabilityProbeActivity.SCENARIO_STRENGTH_MODELS)
        assertTrue(details.contains("finite Hybrid deterministic"))
        assertTrue(details.contains("Standard/Chess960 core-valid"))
        assertTrue(details.contains("Engine Native finite accepted"))
    }

    @Test
    fun reckless09CapabilitiesMatchPinnedRelease() {
        val capabilities = Reckless09Engine.capabilities
        assertEquals(setOf(Variant.STANDARD, Variant.CHESS960), capabilities.variants)
        assertEquals(256, capabilities.multiPv?.maxLines)
        assertTrue(!capabilities.supportsPonder)
        assertNull(capabilities.strength)
        assertEquals("v0.9.0", Reckless09Engine.SOURCE_TAG)
        assertEquals("0e92358f5acd66e5ac77b1bf558202e47c515435", Reckless09Engine.SOURCE_COMMIT)
    }

    @Test
    fun reckless09StandardSearchRunsInIsolatedHostAndPassesCoreValidation() {
        val details = runRecklessScenario(Reckless09ReliabilityProbeActivity.SCENARIO_STANDARD)
        assertTrue(details.contains("Reckless 0.9.0 STANDARD"))
        assertTrue(details.contains("correlation/core validation"))
    }

    @Test
    fun reckless09Chess960SearchRunsInIsolatedHostAndPassesCoreValidation() {
        val details = runRecklessScenario(Reckless09ReliabilityProbeActivity.SCENARIO_CHESS960)
        assertTrue(details.contains("Reckless 0.9.0 CHESS960"))
        assertTrue(details.contains("correlation/core validation"))
    }

    @Test
    fun reckless09CancellationDiscardsRealTerminalOutputBeforeReplacement() {
        val details = runRecklessScenario(Reckless09ReliabilityProbeActivity.SCENARIO_CANCEL_REPLACEMENT)
        assertTrue(details.contains("cancel terminal output discarded"))
    }

    @Test
    fun reckless09NativeSessionCanCloseAndReopenInSameIsolatedHost() {
        val details = runRecklessScenario(Reckless09ReliabilityProbeActivity.SCENARIO_SESSION_REOPEN)
        assertTrue(details.contains("closed and reopened"))
    }

    @Test
    fun reckless09CanRunSimultaneouslyInBothIsolatedSlots() {
        val details = runRecklessScenario(Reckless09ReliabilityProbeActivity.SCENARIO_DUAL_SLOT)
        assertTrue(details.contains("simultaneous slots isolated"))
    }

    @Test
    fun reckless09StrengthModelsAreDeterministicLegalAndRejectUnsupportedNativeMode() {
        val details = runRecklessScenario(Reckless09ReliabilityProbeActivity.SCENARIO_STRENGTH_MODELS)
        assertTrue(details.contains("finite Hybrid deterministic"))
        assertTrue(details.contains("Standard/Chess960 core-valid"))
        assertTrue(details.contains("Engine Native rejected as SESSION failure"))
    }

    private fun runEngineHostScenario(scenario: String): String = runTargetProcessScenario(
        activityClassName = EngineHostProbeActivity::class.java.name,
        scenarioKey = EngineHostProbeActivity.EXTRA_SCENARIO,
        receiverKey = EngineHostProbeActivity.EXTRA_RECEIVER,
        passedKey = EngineHostProbeActivity.KEY_PASSED,
        detailsKey = EngineHostProbeActivity.KEY_DETAILS,
        scenario = scenario,
    )

    private fun runStockfishScenario(scenario: String): String = runTargetProcessScenario(
        activityClassName = Stockfish18ReliabilityProbeActivity::class.java.name,
        scenarioKey = Stockfish18ReliabilityProbeActivity.EXTRA_SCENARIO,
        receiverKey = Stockfish18ReliabilityProbeActivity.EXTRA_RECEIVER,
        passedKey = Stockfish18ReliabilityProbeActivity.KEY_PASSED,
        detailsKey = Stockfish18ReliabilityProbeActivity.KEY_DETAILS,
        scenario = scenario,
    )

    private fun runRecklessScenario(scenario: String): String = runTargetProcessScenario(
        activityClassName = Reckless09ReliabilityProbeActivity::class.java.name,
        scenarioKey = Reckless09ReliabilityProbeActivity.EXTRA_SCENARIO,
        receiverKey = Reckless09ReliabilityProbeActivity.EXTRA_RECEIVER,
        passedKey = Reckless09ReliabilityProbeActivity.KEY_PASSED,
        detailsKey = Reckless09ReliabilityProbeActivity.KEY_DETAILS,
        scenario = scenario,
    )

    private fun runTargetProcessScenario(
        activityClassName: String,
        scenarioKey: String,
        receiverKey: String,
        passedKey: String,
        detailsKey: String,
        scenario: String,
    ): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val latch = CountDownLatch(1)
        val resultCode = AtomicInteger(Int.MIN_VALUE)
        val resultData = AtomicReference<Bundle?>()
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                resultCode.set(code)
                resultData.set(data)
                latch.countDown()
            }
        }

        val intent = Intent().apply {
            component = ComponentName(instrumentation.targetContext.packageName, activityClassName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(scenarioKey, scenario)
            putExtra(receiverKey, receiver)
        }
        instrumentation.context.startActivity(intent)

        assertTrue("Timed out waiting for target-process probe '$scenario'", latch.await(35, TimeUnit.SECONDS))
        val bundle = resultData.get()
        assertNotNull("Target-process probe '$scenario' returned no result bundle", bundle)
        val details = bundle?.getString(detailsKey).orEmpty()
        assertEquals("Target-process probe '$scenario' failed: $details", Activity.RESULT_OK, resultCode.get())
        assertTrue("Target-process probe '$scenario' reported failure: $details", bundle?.getBoolean(passedKey) == true)
        return details
    }
}

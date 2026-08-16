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
import dev.lumenchess.engine.host.testing.EngineHostProbeActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineHostIsolationTest {
    @Test
    fun twoSlotsAreIndependentIsolatedProcessesAndPreserveCorrelation() {
        val details = runTargetProcessScenario(EngineHostProbeActivity.SCENARIO_DUAL_SLOT)
        assertTrue(details.contains("correlation/core validation passed"))
    }

    @Test
    fun cancelledLateOutputCannotBeRelabelledAsTheNextSearch() {
        val details = runTargetProcessScenario(EngineHostProbeActivity.SCENARIO_CANCEL_LATE)
        assertTrue(details.contains("discarded"))
    }

    @Test
    fun malformedKnownUciOutputBecomesTypedFailureInsteadOfSearchResult() {
        val details = runTargetProcessScenario(EngineHostProbeActivity.SCENARIO_MALFORMED)
        assertTrue(details.contains("PROTOCOL"))
    }

    @Test
    fun crashedSlotDoesNotKillCallerOrOtherSlotAndCanBeRecovered() {
        val details = runTargetProcessScenario(EngineHostProbeActivity.SCENARIO_CRASH_RECOVERY)
        assertTrue(details.contains("crash isolated"))
    }

    @Test
    fun closeReleasesSessionAndFullUnbindCanRebindFreshHost() {
        val details = runTargetProcessScenario(EngineHostProbeActivity.SCENARIO_TEARDOWN_REBIND)
        assertTrue(details.contains("unbind/rebind succeeded"))
    }

    private fun runTargetProcessScenario(scenario: String): String {
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
            component = ComponentName(
                instrumentation.targetContext.packageName,
                EngineHostProbeActivity::class.java.name,
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EngineHostProbeActivity.EXTRA_SCENARIO, scenario)
            putExtra(EngineHostProbeActivity.EXTRA_RECEIVER, receiver)
        }
        instrumentation.context.startActivity(intent)

        assertTrue("Timed out waiting for target-process M12 probe '$scenario'", latch.await(25, TimeUnit.SECONDS))
        val bundle = resultData.get()
        assertNotNull("M12 probe '$scenario' returned no result bundle", bundle)
        val details = bundle?.getString(EngineHostProbeActivity.KEY_DETAILS).orEmpty()
        assertEquals("M12 probe '$scenario' failed: $details", Activity.RESULT_OK, resultCode.get())
        assertTrue("M12 probe '$scenario' reported failure: $details", bundle?.getBoolean(EngineHostProbeActivity.KEY_PASSED) == true)
        return details
    }
}

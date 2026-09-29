package dev.lumenchess.feedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LowTimeWarningTest {
    @Test fun warnsOnceWhenTheClockCrossesTenSeconds() {
        val warning = LowTimeWarning()
        val fired = listOf(60_000L, 10_400L, 10_000L, 9_900L, 5_000L, 100L).map { warning.update(it, clockRunning = true) }
        assertEquals(listOf(false, false, true, false, false, false), fired)
    }

    @Test fun aGameThatStartsInTimeTroubleDoesNotWarn() {
        val warning = LowTimeWarning()
        assertFalse(warning.update(3_000L, clockRunning = true))
        assertFalse(warning.update(2_000L, clockRunning = true))
    }

    @Test fun anIncrementBackAboveTheThresholdRearms() {
        val warning = LowTimeWarning()
        warning.update(30_000L, clockRunning = true)
        assertTrue(warning.update(9_000L, clockRunning = true))
        assertFalse(warning.update(11_000L, clockRunning = true))
        assertTrue(warning.update(9_500L, clockRunning = true))
    }

    @Test fun aStoppedClockDoesNotWarnAndResetDisarms() {
        val warning = LowTimeWarning()
        warning.update(20_000L, clockRunning = true)
        assertFalse(warning.update(9_000L, clockRunning = false))
        warning.reset()
        assertFalse(warning.update(8_000L, clockRunning = true))
    }
}

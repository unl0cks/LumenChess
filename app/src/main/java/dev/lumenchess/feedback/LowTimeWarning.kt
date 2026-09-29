package dev.lumenchess.feedback

/**
 * Decides when the player's clock has just run low. Presentation only: it reads committed clock
 * readings and never touches the clock itself.
 *
 * It arms only after seeing the clock above the threshold, so a game that starts (or is restored)
 * already in time trouble does not warn, and fires once per crossing. An increment that lifts the
 * clock back above the threshold re-arms it.
 */
class LowTimeWarning(private val thresholdMillis: Long = DEFAULT_THRESHOLD_MILLIS) {
    private var armed = false

    fun reset() {
        armed = false
    }

    /** Returns true exactly when this reading is the first at or under the threshold. */
    fun update(remainingMillis: Long, clockRunning: Boolean): Boolean {
        if (remainingMillis > thresholdMillis) {
            armed = true
            return false
        }
        if (!armed || !clockRunning) return false
        armed = false
        return true
    }

    companion object {
        const val DEFAULT_THRESHOLD_MILLIS = 10_000L
    }
}

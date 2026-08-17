package dev.lumenchess.runtime.clock

/** A monotonically non-decreasing elapsed-time source. */
fun interface MonotonicTimeSource {
    fun nowMillis(): Long
}

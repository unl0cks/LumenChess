package dev.lumenchess.engine.api

internal fun <T> List<T>.indexOf(element: T, startIndex: Int): Int {
    require(startIndex >= 0) { "startIndex cannot be negative" }
    for (index in startIndex until size) {
        if (this[index] == element) return index
    }
    return -1
}

package dev.lumenchess.engine.host.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchInfoCorrelationTest {
    @Test
    fun currentAnalysisIsDelivered() {
        assertEquals(
            SearchInfoDisposition.DELIVER,
            correlateSearchInfo(expectedRevision = 63L, callbackRevision = 63L),
        )
    }

    @Test
    fun cancelledOrSupersededAnalysisIsDiscarded() {
        assertEquals(
            SearchInfoDisposition.DISCARD,
            correlateSearchInfo(expectedRevision = null, callbackRevision = 63L),
        )
        assertEquals(
            SearchInfoDisposition.DISCARD,
            correlateSearchInfo(expectedRevision = 64L, callbackRevision = 63L),
        )
    }
}

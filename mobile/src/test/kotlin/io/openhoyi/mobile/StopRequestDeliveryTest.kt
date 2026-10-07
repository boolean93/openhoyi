package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class StopRequestDeliveryTest {
    @Test fun stopPrecedesOptionalReport() {
        val order=mutableListOf<String>()
        StopRequestDelivery.request({order+="stop"},{order+="report"})
        assertEquals(listOf("stop","report"),order)
    }
    @Test fun ordinaryReportFailureCannotBlockOrFailSubmittedStop() {
        var dispatched=0
        StopRequestDelivery.request({dispatched++},{throw IllegalStateException("display unavailable")})
        assertEquals(1,dispatched)
    }
    @Test fun dispatchFailureRemainsVisibleAndDoesNotReportSuccess() {
        val failure=IllegalStateException("transport failure")
        var reports=0
        val actual=assertThrows(IllegalStateException::class.java) {
            StopRequestDelivery.request({throw failure},{reports++})
        }
        assertSame(failure,actual)
        assertEquals(0,reports)
    }
    @Test fun fatalReportErrorIsNotSwallowedButStopAlreadySubmitted() {
        var dispatched=0
        val failure=AssertionError("fatal report fault")
        val actual=assertThrows(AssertionError::class.java) {
            StopRequestDelivery.request({dispatched++},{throw failure})
        }
        assertSame(failure,actual)
        assertEquals(1,dispatched)
    }
}

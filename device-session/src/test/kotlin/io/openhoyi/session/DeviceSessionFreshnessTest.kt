package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class DeviceSessionFreshnessTest {
    @Test fun invalidIdleCannotSendSleepOrPreheatCancelAndNewValidIdleRestoresEligibility() {
        for ((at, now) in listOf(-1L to 0L, Long.MIN_VALUE to Long.MAX_VALUE)) {
            val f = CoffeeSessionFixture()
            f.idle(at); f.now = now
            val before = f.calls.size
            var sleep: OperationResult? = null
            var cancel: OperationResult? = null
            f.session.enterSleep { sleep = it }
            f.session.setBrewWait(0, { true }) { cancel = it }
            assertTrue(sleep is OperationResult.Failed)
            assertTrue(cancel is OperationResult.Failed)
            assertEquals(before, f.calls.size)
            f.idle(500)
            f.session.enterSleep { sleep = it }
            assertEquals(before + 1, f.calls.size)
            f.complete(); assertTrue(sleep is OperationResult.Success)
            f.session.setBrewWait(0, { true }) { cancel = it }
            assertEquals(before + 2, f.calls.size)
            f.complete(); assertTrue(cancel is OperationResult.Success)
            f.session.disconnect()
        }
    }
    @Test fun queuedSleepRechecksIdleAgeBeforeDriverExecution() {
        val f = CoffeeSessionFixture()
        f.session.setBrewWait(0, { true }) { }
        val before = f.calls.size
        var result: OperationResult? = null
        f.session.enterSleep { result = it }
        assertNull(result)
        assertEquals(before, f.calls.size)
        f.idle(-1); f.now = 0
        f.complete()
        assertTrue(result is OperationResult.Failed)
        assertEquals(before, f.calls.size)
        f.session.disconnect()
    }
}

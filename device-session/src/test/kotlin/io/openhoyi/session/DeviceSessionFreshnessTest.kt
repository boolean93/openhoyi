package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class DeviceSessionFreshnessTest {
    @Test fun expiredQueuedIdleCannotAuthorizeAWriteButStopStillDispatches() {
        val f = CoffeeSessionFixture(trackCoffeeDelivery = true)
        f.now = 3_000
        val before = f.calls.size
        val idle = f.hex("400024BF2F1C770B00000000000000190321AF")
        f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify, idle, 100)
        var result: OperationResult? = null
        f.session.enterSleep { result = it }
        assertTrue(result is OperationResult.Failed)
        assertEquals(before, f.calls.size)

        f.session.stopExtraction(1) { result = it }
        assertEquals(before + 1, f.calls.size)
        f.complete()
        assertTrue(result is OperationResult.Success)

        f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify, idle, 2_900)
        f.session.enterSleep { result = it }
        assertEquals(before + 2, f.calls.size)
        f.complete()
        assertTrue(result is OperationResult.Success)
    }

    @Test fun invalidIdleCannotSendSleepOrPreheatCancelAndNewValidIdleRestoresEligibility() {
        for (at in listOf(-1L, Long.MIN_VALUE, 2_001L)) {
            val f = CoffeeSessionFixture()
            f.now = 2_000
            val idle=f.hex("400024BF2F1C770B00000000000000190321AF")
            f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify, idle, at)
            val before = f.calls.size
            var sleep: OperationResult? = null
            var cancel: OperationResult? = null
            f.session.enterSleep { sleep = it }
            f.session.setBrewWait(0, { true }) { cancel = it }
            assertTrue(sleep is OperationResult.Failed)
            assertTrue(cancel is OperationResult.Failed)
            assertEquals(before, f.calls.size)
            f.idle(2_100)
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
        f.now = 1_501
        f.complete()
        assertTrue(result is OperationResult.Failed)
        assertEquals(before, f.calls.size)
        f.session.disconnect()
    }
}

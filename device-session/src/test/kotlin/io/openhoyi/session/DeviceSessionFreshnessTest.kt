package io.openhoyi.session

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class DeviceSessionFreshnessTest {
    private class Fixture {
        var now = 0L
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        val driver = object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += Triple(generation, token, operation)
                return true
            }
            override fun close(generation: Long) = Unit
        }
        val session = DeviceSession(DeviceRole.COFFEE, driver, { now })
        fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun complete(result: OperationResult = OperationResult.Success()) {
            val (generation, token, _) = calls.last()
            session.onComplete(generation, token, result)
        }
        fun idle(at: Long) {
            now = at
            session.onNotification(session.generation, KnownGatt.coffeeNotify,
                hex("400024BF2F1C770B00000000000000190321AF"))
        }
        init {
            session.connect("device", CoffeeAuthentication(LocalDateTime.of(2026, 9, 20, 12, 0), "123456"))
            complete()
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite, true, false, false, false),
                CharacteristicInfo(KnownGatt.coffeeNotify, false, false, true, false))))
            complete(); complete()
            session.onNotification(session.generation, KnownGatt.coffeeNotify, hex("830113FD5C007D0F350019006E"))
            check(session.state == DeviceState.READY)
            idle(0)
        }
    }
    @Test fun invalidIdleCannotSendSleepOrPreheatCancelAndNewValidIdleRestoresEligibility() {
        for ((at, now) in listOf(-1L to 0L, Long.MIN_VALUE to Long.MAX_VALUE)) {
            val f = Fixture()
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
        val f = Fixture()
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

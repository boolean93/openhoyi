package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TerminalOwnerTest {
    private class Fixture(val role: DeviceRole, observer: (DeviceSession, DeviceState) -> Unit = { _, _ -> }) {
        var now = 0L
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        val closes = mutableListOf<Long>()
        lateinit var session: DeviceSession
        init {
            session = DeviceSession(role, object : GattDriver {
                override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                    calls += Triple(generation, token, operation); return true
                }
                override fun close(generation: Long) { closes += generation }
            }, { now }, { observer(session, it) })
        }
        fun connect(address: String) = session.connect(address, if (role == DeviceRole.COFFEE)
            CoffeeAuthentication(LocalDateTime.of(2026, 10, 6, 0, 0), "123456") else null)
        fun complete(discovery: Boolean = false) {
            val (gen, token, _) = calls.last()
            val write = if (role == DeviceRole.COFFEE) KnownGatt.coffeeWrite else KnownGatt.bookooWrite
            val notify = if (role == DeviceRole.COFFEE) KnownGatt.coffeeNotify else KnownGatt.bookooNotify
            session.onComplete(gen, token, OperationResult.Success(if (discovery) listOf(
                CharacteristicInfo(write, true, false, false, false),
                CharacteristicInfo(notify, false, false, true, false)) else emptyList()))
        }
        fun subscribe() { complete(); complete(true); complete() }
        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun ready() {
            subscribe()
            if (role == DeviceRole.COFFEE) {
                complete()
                session.onNotification(session.generation, KnownGatt.coffeeNotify, hex("830113FD5C007D0F350019006E"))
                session.onNotification(session.generation, KnownGatt.coffeeNotify, hex("400024BF2F1C770B00000000000000190321AF"))
            } else {
                repeat(BookooCodec.initializationCommands().size) { now += 500; session.tick(); complete() }
                session.onNotification(session.generation, KnownGatt.bookooNotify,
                    hex("030B000000012D007A3A2D03424600C803010084"))
            }
            assertEquals(DeviceState.READY, session.state)
        }
        fun terminate(terminal: DeviceState) {
            if (terminal == DeviceState.DISCONNECTED) session.disconnect()
            else session.onDisconnected(session.generation, "synthetic link loss")
        }
        fun sleepReadback(): WeeklySleepSchedule {
            val first = hex("8340FE0A00071E0A00071E0A00071E0A00071E3D")
            val second = hex("83800A00071E0A00071E0A00071E10")
            session.onNotification(session.generation, KnownGatt.coffeeNotify, first)
            session.onNotification(session.generation, KnownGatt.coffeeNotify, second)
            return requireNotNull(WeeklySleepSchedule.fromReadback(
                (HoyiCodec.decode(first) as DecodeResult.Valid).value as SleepPart,
                (HoyiCodec.decode(second) as DecodeResult.Valid).value as SleepPart))
        }
    }
    private fun reconnect(role: DeviceRole, terminal: DeviceState, throws: Boolean = false) {
        var enabled = false
        val error = IllegalStateException("observer after explicit reconnect")
        val f = Fixture(role) { session, state ->
            if (enabled && state == terminal) {
                enabled = false
                session.connect("new", if (role == DeviceRole.COFFEE)
                    CoffeeAuthentication(LocalDateTime.of(2026, 10, 6, 0, 0), "123456") else null)
                if (throws) throw error
            }
        }
        f.connect("old")
        val old = f.session.generation
        enabled = true
        val failure = runCatching { f.terminate(terminal) }.exceptionOrNull()
        if (throws) assertSame(error, failure) else assertNull(failure)
        val fresh = f.session.generation
        assertTrue(fresh > old)
        assertEquals("new", f.session.address)
        assertEquals(DeviceState.CONNECTING, f.session.state)
        assertEquals(listOf(old), f.closes)
        f.complete()
        assertEquals(DeviceState.DISCOVERING, f.session.state)
        assertEquals(GattOperation.Discover, f.calls.last().third)
        assertEquals(fresh, f.calls.last().first)
    }
    @Test fun coffeeDisconnectObserverReconnects() = reconnect(DeviceRole.COFFEE, DeviceState.DISCONNECTED)
    @Test fun coffeeFailureObserverReconnects() = reconnect(DeviceRole.COFFEE, DeviceState.FAILED)
    @Test fun scaleDisconnectObserverReconnects() = reconnect(DeviceRole.BOOKOO, DeviceState.DISCONNECTED)
    @Test fun scaleFailureObserverReconnects() = reconnect(DeviceRole.BOOKOO, DeviceState.FAILED)
    @Test fun reconnectThenObserverThrowsStillPreservesNewOwner() {
        for (role in DeviceRole.entries) for (terminal in listOf(DeviceState.DISCONNECTED, DeviceState.FAILED))
            reconnect(role, terminal, true)
    }

    @Test fun newSleepTransactionSurvivesBothOldTerminalCleanups() {
        for (terminal in listOf(DeviceState.DISCONNECTED, DeviceState.FAILED)) {
            var enabled = false
            val oldResults = mutableListOf<OperationResult>()
            val newResults = mutableListOf<OperationResult>()
            lateinit var f: Fixture
            f = Fixture(DeviceRole.COFFEE) { _, state ->
                if (enabled && state == terminal) {
                    enabled = false
                    f.connect("new"); f.ready()
                    val plan = f.sleepReadback()
                    f.session.writeSleepSchedule(plan, plan) { newResults += it }
                }
            }
            f.connect("old"); f.ready()
            val oldGen = f.session.generation
            val plan = f.sleepReadback()
            f.session.writeSleepSchedule(plan, plan) { oldResults += it }
            f.complete() // first old frame completed; second still pending in session
            enabled = true
            f.terminate(terminal)
            assertEquals(1, oldResults.size)
            assertTrue(oldResults.single() is OperationResult.Unknown)
            assertTrue("new sleep must remain pending", newResults.isEmpty())
            assertEquals(listOf(oldGen), f.closes)
            f.complete()
            val before = f.calls.size
            f.now += 499; f.session.tick(); assertEquals(before, f.calls.size)
            f.now++; f.session.tick(); assertEquals(before + 1, f.calls.size)
            f.complete()
            assertEquals(1, newResults.size)
            assertTrue(newResults.single() is OperationResult.Success)
            assertEquals(DeviceState.READY, f.session.state)
        }
    }

    @Test fun oldTareObserverReconnectCannotResetNewScaleInitializationBusy() {
        val f = Fixture(DeviceRole.BOOKOO)
        f.connect("old"); f.ready()
        val old = f.session.generation
        var unknowns = 0
        f.session.tare({ true }) { result ->
            assertTrue(result is OperationResult.Unknown); unknowns++
            f.connect("new"); f.subscribe()
            f.now += 500; f.session.tick() // new first initialization write remains in flight
        }
        f.session.disconnect()
        val count = f.calls.size
        assertEquals(1, unknowns)
        assertEquals(listOf(old), f.closes)
        f.session.tick() // must not enqueue duplicate first command
        f.complete()
        assertEquals("no duplicate dispatch after completing first init", count, f.calls.size)
        f.now += 500; f.session.tick()
        assertEquals(count + 1, f.calls.size)
        assertArrayEquals(BookooCodec.initializationCommands()[1].frame.toByteArray(),
            (f.calls.last().third as GattOperation.Write).bytes)
    }
}

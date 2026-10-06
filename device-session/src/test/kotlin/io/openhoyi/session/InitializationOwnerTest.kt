package io.openhoyi.session

import io.openhoyi.protocol.BookooCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class InitializationOwnerTest {
    private class Fixture(val role: DeviceRole, observer: (DeviceSession, DeviceState) -> Unit) {
        var now = 0L
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        lateinit var session: DeviceSession
        val oldAuth = CoffeeAuthentication(LocalDateTime.of(2026, 9, 20, 12, 0), "123456")
        val newAuth = CoffeeAuthentication(LocalDateTime.of(2026, 9, 20, 12, 0), "654321")
        init {
            session = DeviceSession(role, object : GattDriver {
                override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                    calls += Triple(generation, token, operation)
                    return true
                }
                override fun close(generation: Long) {}
            }, { now }, { observer(session, it) })
        }
        fun connect(address: String, replacement: Boolean = false) = session.connect(address,
            if (role == DeviceRole.COFFEE) (if (replacement) newAuth else oldAuth) else null)
        fun complete(discovery: Boolean = false) {
            val (gen, token, _) = calls.last()
            val write = if (role == DeviceRole.COFFEE) KnownGatt.coffeeWrite else KnownGatt.bookooWrite
            val notify = if (role == DeviceRole.COFFEE) KnownGatt.coffeeNotify else KnownGatt.bookooNotify
            session.onComplete(gen, token, OperationResult.Success(if (discovery) listOf(
                CharacteristicInfo(write, true, false, false, false),
                CharacteristicInfo(notify, false, false, true, false)) else emptyList()))
        }
        fun advanceTo(phase: DeviceState) {
            connect("old")
            if (phase == DeviceState.CONNECTING) return
            complete()
            if (phase == DeviceState.DISCOVERING) return
            complete(discovery = true)
            if (phase == DeviceState.SUBSCRIBING) return
            complete()
            if (phase == DeviceState.INITIALIZING) return
            complete()
        }
    }
    private val coffeePhases = listOf(DeviceState.CONNECTING, DeviceState.DISCOVERING,
        DeviceState.SUBSCRIBING, DeviceState.INITIALIZING, DeviceState.SYNCHRONIZING)

    @Test fun reconnectWhileConnecting() = coffeeReconnect(DeviceState.CONNECTING)
    @Test fun reconnectWhileDiscovering() = coffeeReconnect(DeviceState.DISCOVERING)
    @Test fun reconnectWhileSubscribing() = coffeeReconnect(DeviceState.SUBSCRIBING)
    @Test fun reconnectWhileInitializing() = coffeeReconnect(DeviceState.INITIALIZING)
    @Test fun reconnectWhileSynchronizing() = coffeeReconnect(DeviceState.SYNCHRONIZING)

    private fun coffeeReconnect(phase: DeviceState) {
        run {
            var triggered = false
            val f = Fixture(DeviceRole.COFFEE) { session, state ->
                if (!triggered && state == phase) {
                    triggered = true
                    session.connect("new", CoffeeAuthentication(LocalDateTime.of(2026, 9, 20, 12, 0), "654321"))
                }
            }
            f.advanceTo(phase)
            assertTrue("$phase observer", triggered)
            assertEquals("new", f.session.address)
            val start = f.calls.indexOfLast { it.third == GattOperation.Connect("new") }
            assertTrue(start >= 0)
            val newGen = f.session.generation
            f.complete()
            assertEquals("$phase old continuation after new connect", GattOperation.Discover, f.calls.last().third)
            f.complete(discovery = true)
            assertTrue("$phase new subscribe", f.calls.last().third is GattOperation.Subscribe)
            f.complete()
            val write = f.calls.last().third as GattOperation.Write
            assertArrayEquals("$phase new authentication", f.newAuth.encode().frame.toByteArray(), write.bytes)
            f.complete()
            assertEquals(DeviceState.SYNCHRONIZING, f.session.state)
            assertEquals("$phase exact new stage count", 4, f.calls.size - start)
            assertTrue(f.calls.drop(start).all { it.first == newGen })
        }
    }

    @Test fun coffeeDisconnectObserversCannotResumeInitialization() {
        for (phase in coffeePhases) {
            var triggered = false
            val f = Fixture(DeviceRole.COFFEE) { session, state ->
                if (!triggered && state == phase) { triggered = true; session.disconnect() }
            }
            f.advanceTo(phase)
            val count = f.calls.size
            assertTrue(triggered)
            assertEquals(DeviceState.DISCONNECTED, f.session.state)
            assertNull(f.session.address)
            f.now = 30_000
            f.session.tick()
            assertEquals(count, f.calls.size)
            assertEquals(DeviceState.DISCONNECTED, f.session.state)
        }
    }

    @Test fun bookooReconnectAtSynchronizationKeepsNewConnectDeadline() {
        var triggered = false
        lateinit var f: Fixture
        f = Fixture(DeviceRole.BOOKOO) { session, state ->
            if (!triggered && state == DeviceState.SYNCHRONIZING) {
                triggered = true; session.connect("new")
                f.complete(); f.complete(discovery = true); f.complete()
            }
        }
        f.connect("old"); f.complete(); f.complete(discovery = true); f.complete()
        for (command in BookooCodec.initializationCommands()) {
            f.now += 500; f.session.tick()
            val write = f.calls.last().third as GattOperation.Write
            assertArrayEquals(command.frame.toByteArray(), write.bytes)
            f.complete()
        }
        assertTrue(triggered)
        assertEquals(DeviceState.INITIALIZING, f.session.state)
        // This connection's initialization deadline is 10s; the old SYNCHRONIZING
        // deadline must not reduce it to 5s.
        f.now += 5_000
        f.session.tick()
        assertEquals(DeviceState.INITIALIZING, f.session.state)
        assertEquals("new", f.session.address)
    }

    @Test fun coffeeReplacementKeepsTwentyTwoSecondConnectTimeout() {
        var triggered = false
        val f = Fixture(DeviceRole.COFFEE) { session, state ->
            if (!triggered && state == DeviceState.SYNCHRONIZING) {
                triggered = true; session.connect("new", CoffeeAuthentication(LocalDateTime.of(2026, 9, 20, 12, 0), "654321"))
            }
        }
        f.advanceTo(DeviceState.SYNCHRONIZING)
        f.now = 10_000
        f.session.tick()
        assertEquals(DeviceState.CONNECTING, f.session.state)
        assertEquals("new", f.session.address)
        f.now = 21_999; f.session.tick()
        assertEquals(DeviceState.CONNECTING, f.session.state)
        f.now = 22_000; f.session.tick()
        assertEquals(DeviceState.FAILED, f.session.state)
        assertNull(f.session.address)
    }
}

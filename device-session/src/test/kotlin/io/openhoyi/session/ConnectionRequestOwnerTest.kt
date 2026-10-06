package io.openhoyi.session

import io.openhoyi.protocol.BookooCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class ConnectionRequestOwnerTest {
    private class Fixture(val role: DeviceRole) {
        var now = 0L
        var stateObserver: (DeviceState) -> Unit = {}
        var closeObserver: () -> Unit = {}
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        val closes = mutableListOf<Long>()
        val session = DeviceSession(role, object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += Triple(generation, token, operation); return true
            }
            override fun close(generation: Long) { closes += generation; closeObserver() }
        }, { now }, { stateObserver(it) })
        fun auth(password: String = "123456") = CoffeeAuthentication(LocalDateTime.of(2026,10,6,0,0),password)
        fun connect(address: String) = session.connect(address, if (role == DeviceRole.COFFEE) auth() else null)
        fun complete(discovery: Boolean = false) {
            val (gen, token, _) = calls.last()
            val write = if (role == DeviceRole.COFFEE) KnownGatt.coffeeWrite else KnownGatt.bookooWrite
            val notify = if (role == DeviceRole.COFFEE) KnownGatt.coffeeNotify else KnownGatt.bookooNotify
            session.onComplete(gen, token, OperationResult.Success(if (discovery) listOf(
                CharacteristicInfo(write,true,false,false,false),
                CharacteristicInfo(notify,false,false,true,false)) else emptyList()))
        }
        fun readyScale() {
            connect("old"); complete(); complete(true); complete()
            repeat(BookooCodec.initializationCommands().size) { now += 500; session.tick(); complete() }
            session.onNotification(session.generation, KnownGatt.bookooNotify,
                "030B000000012D007A3A2D03424600C803010084".chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            assertEquals(DeviceState.READY,session.state)
        }
        fun assertLatestSurvives(old: Long, start: Int) {
            assertEquals("latest",session.address)
            assertEquals(DeviceState.CONNECTING,session.state)
            assertEquals(listOf(old),closes)
            assertEquals(listOf(GattOperation.Connect("latest")),calls.drop(start).map { it.third })
            complete()
            assertEquals(DeviceState.DISCOVERING,session.state)
            assertEquals(GattOperation.Discover,calls.last().third)
        }
    }
    private fun newerStateRequest(role: DeviceRole) {
        val f = Fixture(role)
        f.connect("old")
        val old = f.session.generation
        val start = f.calls.size
        var armed = true
        f.stateObserver = { state ->
            if (armed && state == DeviceState.DISCONNECTED) { armed = false; f.connect("latest") }
        }
        f.connect("superseded")
        f.assertLatestSurvives(old,start)
    }
    @Test fun coffeeNewerConnectDuringCleanupWins() = newerStateRequest(DeviceRole.COFFEE)
    @Test fun bookooNewerConnectDuringCleanupWins() = newerStateRequest(DeviceRole.BOOKOO)

    @Test fun explicitDisconnectDuringStateCleanupCancelsWithoutNewGeneration() {
        for (role in DeviceRole.entries) {
            val f = Fixture(role); f.connect("old")
            val old = f.session.generation; val count = f.calls.size
            var armed = true
            f.stateObserver = { state ->
                if (armed && state == DeviceState.DISCONNECTED) { armed = false; f.session.disconnect() }
            }
            f.connect("superseded")
            assertEquals(DeviceState.DISCONNECTED,f.session.state)
            assertNull(f.session.address)
            assertEquals(old,f.session.generation)
            assertEquals(count,f.calls.size)
            assertEquals(listOf(old),f.closes)
        }
    }
    @Test fun newerConnectFromTransportCloseWins() {
        for (role in DeviceRole.entries) {
            val f = Fixture(role); f.connect("old")
            val old = f.session.generation; val start = f.calls.size
            var armed = true
            f.closeObserver = { if (armed) { armed = false; f.connect("latest") } }
            f.connect("superseded")
            f.assertLatestSurvives(old,start)
        }
    }
    @Test fun newerConnectFromOldTareUnknownWins() {
        val f = Fixture(DeviceRole.BOOKOO); f.readyScale()
        val old = f.session.generation
        var unknowns = 0
        f.session.tare({ true }) { result ->
            assertTrue(result is OperationResult.Unknown); unknowns++; f.connect("latest")
        }
        val start = f.calls.size
        f.connect("superseded")
        assertEquals(1,unknowns)
        f.assertLatestSurvives(old,start)
    }
    @Test fun explicitDisconnectFromOldTareUnknownCancelsOuterConnect() {
        val f = Fixture(DeviceRole.BOOKOO); f.readyScale()
        val old = f.session.generation
        var unknowns = 0
        f.session.tare({ true }) { result ->
            assertTrue(result is OperationResult.Unknown); unknowns++; f.session.disconnect()
        }
        val count = f.calls.size
        f.connect("superseded")
        assertEquals(1,unknowns)
        assertEquals(old,f.session.generation)
        assertEquals(count,f.calls.size)
        assertEquals(DeviceState.DISCONNECTED,f.session.state)
        assertNull(f.session.address)
    }
    @Test fun invalidNestedCredentialsDoNotCancelValidatedOuterConnect() {
        val f = Fixture(DeviceRole.COFFEE); f.connect("old")
        var armed = true
        var rejected = false
        f.stateObserver = { state ->
            if (armed && state == DeviceState.DISCONNECTED) {
                armed = false
                rejected = runCatching { f.session.connect("invalid",f.auth("bad")) }.exceptionOrNull() is IllegalArgumentException
            }
        }
        f.connect("outer")
        assertTrue(rejected)
        assertEquals("outer",f.session.address)
        assertEquals(GattOperation.Connect("outer"),f.calls.last().third)
        assertFalse(f.calls.any { it.third == GattOperation.Connect("invalid") })
    }
    @Test fun sameAddressScaleNoOpAndCoffeeReauthenticationRemainUnchanged() {
        val scale = Fixture(DeviceRole.BOOKOO); scale.connect("old")
        val generation = scale.session.generation; val count = scale.calls.size
        scale.connect("old")
        assertEquals(generation,scale.session.generation)
        assertEquals(count,scale.calls.size); assertTrue(scale.closes.isEmpty())
        val coffee = Fixture(DeviceRole.COFFEE); coffee.connect("old")
        val old = coffee.session.generation
        val auth = coffee.auth("654321")
        coffee.session.connect("old",auth)
        assertTrue(coffee.session.generation > old)
        assertEquals(listOf(old),coffee.closes)
        coffee.complete(); coffee.complete(true); coffee.complete()
        assertArrayEquals(auth.encode().frame.toByteArray(),(coffee.calls.last().third as GattOperation.Write).bytes)
    }
}

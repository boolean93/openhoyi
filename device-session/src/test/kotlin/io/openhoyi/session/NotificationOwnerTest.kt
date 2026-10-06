package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NotificationOwnerTest {
    private class Fixture(val role: DeviceRole) {
        var now = 0L
        var observer: (DeviceState) -> Unit = {}
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        val frames = mutableListOf<Pair<ByteFrame, Long>>()
        lateinit var session: DeviceSession
        init {
            session = DeviceSession(role, object : GattDriver {
                override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                    calls += Triple(generation, token, operation); return true
                }
                override fun close(generation: Long) {}
            }, { now }, { observer(it) }, { frame, at ->
                if (frame is Settings) frames += frame.raw to at
            }, { sample, at -> frames += sample.raw to at })
        }
        val endpoint get() = if (role == DeviceRole.COFFEE) KnownGatt.coffeeNotify else KnownGatt.bookooNotify
        fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun packet(replacement: Boolean = false): ByteArray {
            val frame = bytes(if (role == DeviceRole.COFFEE) "830113FD5C007D0F350019006E"
                else "030B000000012D007A3A2D03424600C803010084")
            if (replacement) {
                val index = if (role == DeviceRole.COFFEE) 4 else 9
                frame[index] = (frame[index].toInt() xor 1).toByte()
                frame[frame.lastIndex] = (frame.last().toInt() xor 1).toByte()
            }
            return frame
        }
        fun connect(address: String) = session.connect(address, if (role == DeviceRole.COFFEE)
            CoffeeAuthentication(LocalDateTime.of(2026, 10, 6, 0, 0), "123456") else null)
        fun complete(discovery: Boolean = false) {
            val (gen, token, _) = calls.last()
            val write = if (role == DeviceRole.COFFEE) KnownGatt.coffeeWrite else KnownGatt.bookooWrite
            session.onComplete(gen, token, OperationResult.Success(if (discovery) listOf(
                CharacteristicInfo(write, true, false, false, false),
                CharacteristicInfo(endpoint, false, false, true, false)) else emptyList()))
        }
        fun synchronize() {
            complete(); complete(true); complete()
            if (role == DeviceRole.COFFEE) complete()
            else repeat(BookooCodec.initializationCommands().size) { now += 500; session.tick(); complete() }
            assertEquals(DeviceState.SYNCHRONIZING, session.state)
        }
        fun notify(replacement: Boolean = false) = session.onNotification(session.generation, endpoint, packet(replacement))
    }

    private fun reconnectAtReady(role: DeviceRole, alreadyReady: Boolean = false) {
        val f = Fixture(role)
        f.connect("old"); f.synchronize()
        if (alreadyReady) f.notify()
        f.frames.clear()
        val oldGen = f.session.generation
        var triggered = false
        f.observer = { state ->
            if (!triggered && state == DeviceState.READY) {
                triggered = true
                f.connect("new"); f.synchronize(); f.now += 100; f.notify(true)
            }
        }
        f.notify()
        assertTrue(triggered)
        assertTrue(f.session.generation > oldGen)
        assertEquals(DeviceState.READY, f.session.state)
        assertEquals("new", f.session.address)
        assertEquals("old notification must not enter new product state", 1, f.frames.size)
        assertArrayEquals(f.packet(true), f.frames.single().first.toByteArray())
        assertEquals(f.now, f.frames.single().second)
        if (role == DeviceRole.COFFEE) {
            // The surviving new Settings must also remain the session readback.
            f.session.onNotification(f.session.generation, f.endpoint,
                f.bytes("4000238C2F1C770B00000000000000190321AF"))
            assertEquals(CoffeeFirmware(1, 1, 3), f.session.observedFirmware)
            val profile = StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0)
            val context = requireNotNull(f.session.captureStartContext(profile))
            assertEquals("new readback temperature must survive old notification", 93, context.brewTemperatureC)
            assertEquals(f.session.generation, context.generation)
            assertEquals("new", context.address)
        }
    }
    @Test fun coffeeFirstReadyObserverCannotPublishOldSettings() = reconnectAtReady(DeviceRole.COFFEE)
    @Test fun coffeeRepeatedReadyObserverCannotPublishOldSettings() = reconnectAtReady(DeviceRole.COFFEE, true)
    @Test fun bookooFirstReadyObserverCannotPublishOldWeight() = reconnectAtReady(DeviceRole.BOOKOO)

    @Test fun disconnectOrUnfinishedReconnectSuppressesOldFirstNotification() {
        for (role in DeviceRole.entries) for (replace in listOf(false, true)) {
            val f = Fixture(role)
            f.connect("old"); f.synchronize()
            var triggered = false
            f.observer = { state ->
                if (!triggered && state == DeviceState.READY) {
                    triggered = true
                    if (replace) f.connect("new") else f.session.disconnect()
                }
            }
            f.notify()
            assertTrue(triggered)
            assertTrue(f.frames.isEmpty())
            assertEquals(if (replace) DeviceState.CONNECTING else DeviceState.DISCONNECTED, f.session.state)
        }
    }
    @Test fun ordinaryFirstAndFollowingReadyNotificationsStillPublishOnce() {
        for (role in DeviceRole.entries) {
            val f = Fixture(role)
            f.connect("old"); f.synchronize()
            f.notify(); f.now++; f.notify(true)
            assertEquals(DeviceState.READY, f.session.state)
            assertEquals(2, f.frames.size)
            assertArrayEquals(f.packet(), f.frames[0].first.toByteArray())
            assertArrayEquals(f.packet(true), f.frames[1].first.toByteArray())
            assertEquals(f.now - 1, f.frames[0].second)
            assertEquals(f.now, f.frames[1].second)
        }
    }
    @Test fun oldGenerationAndWrongEndpointNotificationsRemainIgnored() {
        for (role in DeviceRole.entries) {
            val f = Fixture(role)
            f.connect("old"); f.synchronize(); f.notify()
            val old = f.session.generation
            f.connect("new"); f.synchronize(); f.notify(true)
            f.frames.clear()
            f.session.onNotification(old, f.endpoint, f.packet())
            f.session.onNotification(f.session.generation, Endpoint("unknown", "unknown"), f.packet())
            assertTrue(f.frames.isEmpty())
            assertEquals("new", f.session.address)
            assertEquals(DeviceState.READY, f.session.state)
        }
    }
}

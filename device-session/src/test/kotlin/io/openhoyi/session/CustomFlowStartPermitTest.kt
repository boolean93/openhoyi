package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** Offline GATT queue checks only. No device or physical extraction is involved. */
class CustomFlowStartPermitTest {
    private val p = StartParameters(true, true, 3, 7, 93, 70, true, 4, 25, 35, 20, 0, 150, 7, 400, 150, 0)
    private class Fixture(permit: (StartParameters) -> Boolean = { false }, pressurePermit: (StartParameters) -> Boolean = { false }) {
        var now = 0L
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        val session = DeviceSession(DeviceRole.COFFEE, object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += Triple(generation, token, operation); return true
            }
            override fun close(generation: Long) = Unit
        }, { now }, customPressureStartPermit = pressurePermit, customCurveStartPermit = permit)
        fun bytes(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        fun complete(result: OperationResult = OperationResult.Success()) {
            val call = calls.last(); session.onComplete(call.first, call.second, result)
        }
        fun fresh() = session.onNotification(session.generation, KnownGatt.coffeeNotify,
            bytes("400023F02F1C770B00000000000000190321AF"))
        fun firmware(patch: Int) {
            val frame = bytes("830113FD5C007D0F350019006E")
            frame[2] = (0x10 or patch).toByte()
            frame[frame.lastIndex] = frame.dropLast(1).fold(0) { acc, b -> acc xor (b.toInt() and 255) }.toByte()
            session.onNotification(session.generation, KnownGatt.coffeeNotify, frame)
        }
        init {
            session.connect("device", CoffeeAuthentication(LocalDateTime.of(2026, 9, 20, 12, 0), "123456"))
            complete()
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite, true, false, false, false),
                CharacteristicInfo(KnownGatt.coffeeNotify, false, false, true, false))))
            complete(); complete()
            firmware(4)
            check(session.state == DeviceState.READY); fresh()
        }
    }

    @Test fun completeFlowNeedsItsOwnExplicitHostPermitAndDispatchesExactBytes() {
        assertNull(Fixture().session.captureStartContext(p))
        assertNull(Fixture(pressurePermit = { true }).session.captureStartContext(p))
        val f = Fixture(permit = { it == p })
        assertNotNull(f.session.captureStartContext(p))
        var result: OperationResult? = null
        val size = f.calls.size
        f.session.startExtraction(p) { result = it }
        assertEquals(size + 1, f.calls.size)
        val write = f.calls.last().third as GattOperation.Write
        assertEquals(KnownGatt.coffeeWrite, write.endpoint)
        assertArrayEquals(CoffeeCommands.start(p).frame.toByteArray(), write.bytes)
        f.complete(); assertTrue(result is OperationResult.Success)
    }

    @Test fun broadHostPermitCannotBypassKnownFlowSafetyBounds() {
        val f = Fixture(permit = { true })
        for (bad in listOf(p.copy(segmentCount = 1), p.copy(target1 = 9), p.copy(target2 = 101),
            p.copy(firstSegmentFlowMode = false, target1 = 4), p.copy(firstSegmentFlowMode = false, target1 = 101),
            p.copy(firstFlowTenths = 0, firstDurationSeconds = 0), p.copy(firstDurationSeconds = 255),
            p.copy(secondFlowTenths = 0), p.copy(firstFlowTenths = 151), p.copy(secondFlowTenths = 10000),
            p.copy(preinfusionSeconds = 128), p.copy(slot = 1), p.copy(maximumWaterMl = 0))) {
            assertFalse(bad.toString(), CustomCurveStartPolicy.permits(bad))
            assertNull(bad.toString(), f.session.captureStartContext(bad))
        }
        assertTrue(CustomCurveStartPolicy.permits(p.copy(firstFlowTenths = 0, firstDurationSeconds = 7)))
        assertTrue(CustomCurveStartPolicy.permits(p.copy(firstFlowTenths = 150, firstDurationSeconds = 0)))
        assertFalse(CustomPressureStartPolicy.permits(p))
    }

    @Test fun revocationOrPermitExceptionAtQueuedDispatchNeverSendsFlowStart() {
        for (throwOnRevoke in listOf(false, true)) {
            var allowed = true
            val f = Fixture(permit = { if (!allowed && throwOnRevoke) error("unavailable") else allowed && it == p })
            val context = requireNotNull(f.session.captureStartContext(p))
            val queue = DeviceSession::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(f.session) as GattQueue
            queue.enqueue(GattOperation.Discover, 5000) {}
            val barrier = f.calls.last(); val size = f.calls.size
            var result: OperationResult? = null
            f.session.startExtraction(p, context) { result = it }
            allowed = false
            f.session.onComplete(barrier.first, barrier.second, OperationResult.Success())
            assertEquals(size, f.calls.size); assertTrue(result is OperationResult.Failed)
            f.session.onComplete(barrier.first, barrier.second, OperationResult.Success()); f.session.tick()
            assertEquals(size, f.calls.size)
        }
    }

    @Test fun flowPermitStillRequiresFreshIdleAndSupportedFirmware() {
        val f = Fixture(permit = { it == p })
        assertNotNull(f.session.captureStartContext(p))
        f.now = 1501; assertNull(f.session.captureStartContext(p))
        f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify, f.bytes("830115FD5C007D0F3500190068"))
        f.fresh(); assertNull(f.session.captureStartContext(p))
    }

    @Test fun newFlowCombinationsRequire114AndFirmwareChangeRevokesQueuedStart() {
        val f = Fixture(permit = { true })
        val hsPressureFirst = p.copy(firstSegmentFlowMode = false, target1 = 30)
        val lsPressureFirst = hsPressureFirst.copy(pressureLogic = false)
        f.firmware(3); f.fresh()
        assertNotNull(f.session.captureStartContext(hsPressureFirst))
        assertNull(f.session.captureStartContext(lsPressureFirst))
        assertNull(f.session.captureStartContext(p))
        f.firmware(4); f.fresh()
        assertNotNull(f.session.captureStartContext(hsPressureFirst))
        assertNotNull(f.session.captureStartContext(lsPressureFirst))
        val context = requireNotNull(f.session.captureStartContext(p))
        val queue = DeviceSession::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(f.session) as GattQueue
        queue.enqueue(GattOperation.Discover, 5000) {}
        val barrier = f.calls.last(); val size = f.calls.size
        var result: OperationResult? = null
        f.session.startExtraction(p, context) { result = it }
        f.firmware(3); f.fresh()
        f.session.onComplete(barrier.first, barrier.second, OperationResult.Success())
        assertEquals(size, f.calls.size); assertTrue(result is OperationResult.Failed)
    }
}

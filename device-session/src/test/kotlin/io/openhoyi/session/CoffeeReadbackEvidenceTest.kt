package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class CoffeeReadbackEvidenceTest {
    private val address = "AA:BB:CC:DD:EE:01"
    private val settings = "830113FD5C007D0F350019006E"
    private val first = "8340FE0A00071E0A00071E0A00071E0A00071E3D"
    private val second = "83800A00071E0A00071E0A00071E10"
    private fun packet(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun sleep(hex: String) = (HoyiCodec.decode(packet(hex)) as DecodeResult.Valid).value as SleepPart

    private class Driver : GattDriver {
        val submitted = mutableListOf<Triple<Long, Long, GattOperation>>()
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            submitted += Triple(generation, token, operation)
            return true
        }
        override fun close(generation: Long) = Unit
    }

    private inner class Fixture {
        val driver = Driver()
        var now = 100L
        val legacy = mutableListOf<HoyiMessage>()
        val session = DeviceSession(DeviceRole.COFFEE, driver, { now }, coffeeFrame = { f, _ -> legacy += f })
        fun connect(device: String = address, early: Boolean = false) {
            session.connect(device, CoffeeAuthentication(LocalDateTime.of(2026, 11, 1, 0, 0), "123456"))
            assertNull(session.coffeeReadbackCursor)
            complete()
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite, true, false, false, false),
                CharacteristicInfo(KnownGatt.coffeeNotify, false, false, true, false))))
            complete()
            if (early) receive(settings)
            complete()
            if (!early) receive(settings)
            assertEquals(DeviceState.READY, session.state)
            assertNull(session.latestCoffeeReadbackFrame)
        }
        fun complete(result: OperationResult = OperationResult.Success()) {
            val (generation, token, _) = driver.submitted.last()
            session.onComplete(generation, token, result)
        }
        fun receive(hex: String, generation: Long = session.generation, endpoint: Endpoint = KnownGatt.coffeeNotify) {
            session.onNotification(generation, endpoint, packet(hex))
        }
        fun settingsWithDisabled(bit: Int) = packet(settings).also { bytes ->
            bytes[3] = (bytes[3].toInt() and bit.inv()).toByte()
            bytes[bytes.lastIndex] = bytes.dropLast(1).fold(0) { a, b -> a xor (b.toInt() and 255) }.toByte()
        }.joinToString("") { "%02X".format(it.toInt() and 255) }
        fun latest() = requireNotNull(session.latestCoffeeReadbackFrame)
        fun cursor() = requireNotNull(session.coffeeReadbackCursor)
    }

    @Test fun realIngressMarksOnlyAuthenticatedLiveReadbackWithoutChangingLegacyCallback() {
        for (early in listOf(false, true)) {
            val f = Fixture()
            f.connect(early = early)
            assertTrue(f.legacy.single() is Settings)
            val original = f.cursor()
            f.receive(settings, endpoint = KnownGatt.coffeeWrite)
            assertEquals(original.sequence, f.cursor().sequence)
            f.now++
            f.receive(settings)
            val frame = f.latest() as CoffeeReadbackFrame.OrdinarySettings
            assertSame(f.legacy.last(), frame.value)
            assertSame(original.source, frame.cursor.source)
            assertEquals(f.now, frame.receivedAtMs)
            assertEquals(original.sequence + 1, frame.cursor.sequence)
            assertEquals(address, frame.cursor.source.address)
            assertEquals(f.session.generation, frame.cursor.source.generation)
        }
    }

    @Test fun exactBoilerWaitsForPostTransportMatchingFieldAndRejectsOldConnection() {
        val f = Fixture().apply { connect(); receive(settings) }
        val original = f.latest() as CoffeeReadbackFrame.OrdinarySettings
        assertTrue(original.value.brewHeating && original.value.steamHeating)
        val requested = MachineSettingChange.BrewHeating(false)
        // Use the real GATT write path; frames seen before completion are below the cut.
        f.receive("400024BF2F1C770B00000000000000190321AF")
        var beforeDispatch: CoffeeReadbackCursor? = null
        var transportSucceeded = false
        var afterSuccess: CoffeeReadbackCursor? = null
        f.session.writeSetting(requested, { beforeDispatch = f.cursor(); true }) {
            transportSucceeded = it is OperationResult.Success
            if (transportSucceeded) afterSuccess = f.cursor()
        }
        assertTrue(f.driver.submitted.last().third is GattOperation.Write)
        f.receive(f.settingsWithDisabled(0x20))
        val preAck = f.latest()
        f.complete()
        assertTrue(transportSucceeded)
        val before = requireNotNull(beforeDispatch)
        val afterWrite = requireNotNull(afterSuccess)
        val evidence = requireNotNull(CoffeeReadbackEvidence.Boiler.start(requested, original, before, afterWrite, f.now))
        assertNull(CoffeeReadbackEvidence.Boiler.start(requested, original, afterWrite, afterWrite, f.now))
        assertFalse(evidence.matches(preAck, f.cursor(), f.now))
        assertNull(CoffeeReadbackEvidence.Boiler.start(MachineSettingChange.BrewHeating(true), original, before, afterWrite, f.now))
        f.receive(f.settingsWithDisabled(0x10)) // other boiler changes; requested field is still ON
        assertFalse(evidence.matches(f.latest(), f.cursor(), f.now))
        f.receive(second)
        assertFalse(evidence.matches(f.latest(), f.cursor(), f.now))
        f.receive(f.settingsWithDisabled(0x20))
        val matching = f.latest()
        assertTrue(evidence.matches(matching, f.cursor(), f.now))
        f.receive(settings)
        assertFalse(evidence.matches(matching, f.cursor(), f.now)) // newer conflicting readback wins
        val oldGeneration = f.session.generation
        f.session.onDisconnected(oldGeneration, "test")
        assertNull(f.session.latestCoffeeReadbackFrame)
        assertFalse(evidence.matches(matching, f.session.coffeeReadbackCursor, f.now))
        f.connect()
        f.receive(f.settingsWithDisabled(0x20), generation = oldGeneration)
        assertNull(f.session.latestCoffeeReadbackFrame)
        f.receive(f.settingsWithDisabled(0x20))
        assertFalse(evidence.matches(f.latest(), f.cursor(), f.now))
    }

    @Test fun sameNumbersFromAnotherHubCannotConfirmAndScheduleCannotReuseOldHalf() {
        val f = Fixture().apply { connect() }
        val other = Fixture().apply { connect() }
        val baseline = f.cursor()
        val sameAddress = other.cursor()
        assertEquals(baseline.source.address, sameAddress.source.address)
        assertEquals(baseline.source.generation, sameAddress.source.generation)
        assertNotSame(baseline.source, sameAddress.source)
        val week = requireNotNull(WeeklySleepSchedule.fromReadback(sleep(first), sleep(second)))
        val evidence = CoffeeReadbackEvidence.Weekly(week, baseline)
        f.receive(second)
        val earlySecond = f.latest()
        assertFalse(evidence.observe(earlySecond, f.cursor(), f.now))
        f.receive(first)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        assertFalse(evidence.observe(earlySecond, f.cursor(), f.now))
        other.receive(second)
        assertFalse(evidence.observe(other.latest(), f.cursor(), f.now))
        f.receive(settings)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        f.receive(second)
        assertTrue(evidence.observe(f.latest(), f.cursor(), f.now))
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
    }
    @Test fun staleScheduleFirstHalfCannotCombineWithFreshSecond() {
        val f = Fixture().apply { connect() }
        val week = requireNotNull(WeeklySleepSchedule.fromReadback(sleep(first), sleep(second)))
        val evidence = CoffeeReadbackEvidence.Weekly(week, f.cursor())
        f.receive(first)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        f.now += SleepScheduleFreshness.MAX_PAIR_GAP_MS + 1
        f.receive(second)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        f.receive(first)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        f.receive(second)
        assertTrue(evidence.observe(f.latest(), f.cursor(), f.now))
    }

    @Test fun scheduleCutTakenOnlyAfterActualSecondWriteAndPairMustMatchWholeTarget() {
        val f = Fixture().apply {
            connect()
            receive("400024BF2F1C770B00000000000000190321AF")
            receive(first)
            receive(second)
        }
        val expected = requireNotNull(WeeklySleepSchedule.fromReadback(sleep(first), sleep(second)))
        val changedFirst = first.replace("8340FE", "83407E")
        val target = requireNotNull(WeeklySleepSchedule.fromReadback(sleep(changedFirst), sleep(second)))
        val initialWrites = f.driver.submitted.size
        var finished: CoffeeReadbackCursor? = null
        f.session.writeSleepSchedule(target, expected) {
            assertTrue(it is OperationResult.Success)
            finished = f.cursor()
        }
        assertEquals(initialWrites + 1, f.driver.submitted.size)
        f.complete()
        assertNull(finished)
        f.now += 500
        f.session.tick()
        assertEquals(initialWrites + 2, f.driver.submitted.size)
        f.receive(changedFirst)
        val preSecondAck = f.latest()
        f.complete()
        val evidence = CoffeeReadbackEvidence.Weekly(target, requireNotNull(finished))
        assertFalse(evidence.observe(preSecondAck, f.cursor(), f.now))
        f.receive(second)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        f.receive(first)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        f.receive(second)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now)) // complete pair, wrong Sunday enable
        f.receive(changedFirst)
        assertFalse(evidence.observe(f.latest(), f.cursor(), f.now))
        f.receive(second)
        assertTrue(evidence.observe(f.latest(), f.cursor(), f.now))
    }
}

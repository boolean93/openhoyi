package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
import io.openhoyi.session.DetachedCoffeeCommandHost.Phase
import io.openhoyi.session.SplitMachineWriteJournal.Kind

class DetachedCoffeeCommandHostTest {
    private val settings = "830113FD5C007D0F350019006E"
    private val idle = "400024BF2F1C770B00000000000000190321AF"
    private val first = "8340FE0A00071E0A00071E0A00071E0A00071E3D"
    private val targetFirst = first.replace("8340FE", "83407E")
    private val second = "83800A00071E0A00071E0A00071E10"
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
    private fun plan(part: String): WeeklySleepSchedule {
        fun decode(raw: String) = (HoyiCodec.decode(bytes(raw)) as DecodeResult.Valid).value as SleepPart
        return requireNotNull(WeeklySleepSchedule.fromReadback(decode(part), decode(second)))
    }
    private val expected = plan(first)
    private val target = plan(targetFirst)
    private val expectedScheduleWrites = CoffeeCommands.sleepSchedule(target).map { hex(it.frame.toByteArray()) }
    private val brewOff = hex(CoffeeCommands.setting(MachineSettingChange.BrewHeating(false)).frame.toByteArray())

    private class Store : SplitMachineWriteJournal.Store {
        var entries = emptyList<SplitMachineWriteJournal.Entry>()
        var failPersistence = false
        override fun read() = if (entries.isEmpty()) SplitMachineWriteJournal.Snapshot.Empty else SplitMachineWriteJournal.Snapshot.Pending(entries)
        override fun replace(entries: List<SplitMachineWriteJournal.Entry>): Boolean {
            if (failPersistence) return false
            this.entries = entries.toList()
            return true
        }
        override fun clear(): Boolean {
            if (failPersistence) return false
            entries = emptyList()
            return true
        }
    }

    private inner class Fixture(optIn: Boolean = true) {
        var now = 100L
        var lease = true
        var environmentSafe = true
        val store = Store()
        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
        val writeOwners = mutableListOf<Pair<String, Set<Kind>>>()
        val driver = object : GattDriver {
            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                calls += Triple(generation, token, operation)
                if (operation is GattOperation.Write) writeOwners += hex(operation.bytes) to kinds()
                return true
            }
            override fun close(generation: Long) = Unit
        }
        val session = DeviceSession(DeviceRole.COFFEE, driver, { now })
        val host = newHost(optIn)
        fun newHost(optIn: Boolean = true) =
            DetachedCoffeeCommandHost(optIn, session, SplitMachineWriteJournal(store), { now }) { lease && environmentSafe }

        init { connect() }
        fun connect(address: String = "AA:BB:CC:DD:EE:01") {
            session.connect(address, CoffeeAuthentication(LocalDateTime.of(2026, 11, 22, 12, 0), "123456"))
            ack()
            ack(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite, true, false, false, false),
                CharacteristicInfo(KnownGatt.coffeeNotify, false, false, true, false))))
            ack(); ack()
            receive(settings); receive(idle); receive(first); receive(second); receive(settings)
            assertEquals(DeviceState.READY, session.state)
        }
        fun receive(frame: String, generation: Long = session.generation) {
            session.onNotification(generation, KnownGatt.coffeeNotify, bytes(frame))
            host.poll()
        }
        fun ack(result: OperationResult = OperationResult.Success()) {
            val (generation, token, _) = calls.last()
            session.onComplete(generation, token, result)
            host.poll()
        }
        fun advance(ms: Long) { now += ms; session.tick(); host.poll() }
        fun kinds() = store.entries.map { it.kind }.toSet()
        fun relevantWrites() = writeOwners.filter { it.first in expectedScheduleWrites || it.first == brewOff }
        fun scheduleWaiting() {
            assertTrue(host.requestWeekly(expected, target))
            assertEquals(Phase.WRITING, host.schedulePhase)
            assertFalse(host.requestBrewHeatOff())
            ack()
            assertFalse(host.requestBrewHeatOff())
            advance(499)
            assertEquals(1, relevantWrites().size)
            advance(1)
            assertEquals(2, relevantWrites().size)
            assertFalse(host.requestBrewHeatOff())
            receive(targetFirst)  // target before second ACK is not evidence for this operation
            ack()
            assertEquals(Phase.WAITING, host.schedulePhase)
            receive(settings)
        }
        fun off(bit: Int): String {
            val result = bytes(settings)
            result[3] = (result[3].toInt() and bit.inv()).toByte()
            return hex(result)
        }
    }

    @Test fun actualQueueWritesTwoSchedulePacketsThenOneBoilerAndSettlesOnlyOwnSlotInEitherOrder() {
        for (boilerFirst in listOf(false, true)) {
            val f = Fixture()
            f.scheduleWaiting()
            assertEquals(setOf(Kind.SCHEDULE), f.kinds())
            assertTrue(f.host.requestBrewHeatOff())
            f.receive(f.off(0x20)) // pre-ACK ordinary settings cannot settle the boiler
            f.ack()
            assertEquals(Phase.WAITING, f.host.boilerPhase)
            assertFalse(f.host.requestBrewHeatOff())
            assertEquals(expectedScheduleWrites + brewOff, f.relevantWrites().map { it.first })
            assertEquals(listOf(setOf(Kind.SCHEDULE), setOf(Kind.SCHEDULE), setOf(Kind.SCHEDULE, Kind.BOILER)),
                f.relevantWrites().map { it.second })
            f.receive(second); f.receive(first); f.receive(second) // pre-ACK first discarded, then complete wrong target
            f.receive(f.off(0x10)) // steam only
            assertEquals(Phase.WAITING, f.host.schedulePhase)
            assertEquals(Phase.WAITING, f.host.boilerPhase)
            if (boilerFirst) {
                f.receive(f.off(0x20))
                assertEquals(Phase.OBSERVED, f.host.boilerPhase)
                assertEquals(setOf(Kind.SCHEDULE), f.kinds())
                f.receive(targetFirst); f.receive(second)
            } else {
                f.receive(targetFirst); f.receive(second)
                assertEquals(Phase.OBSERVED, f.host.schedulePhase)
                assertEquals(setOf(Kind.BOILER), f.kinds())
                f.receive(f.off(0x20))
            }
            assertEquals(Phase.OBSERVED, f.host.schedulePhase)
            assertEquals(Phase.OBSERVED, f.host.boilerPhase)
            assertTrue(f.kinds().isEmpty())
            assertEquals(3, f.relevantWrites().size)
        }
    }

    @Test fun optInAndDurableRegistrationAreRequiredBeforeAnyCommand() {
        val off = Fixture(false)
        assertFalse(off.host.requestWeekly(expected, target))
        assertTrue(off.relevantWrites().isEmpty())
        val unwritable = Fixture()
        unwritable.store.failPersistence = true
        assertFalse(unwritable.host.requestWeekly(expected, target))
        assertTrue(unwritable.relevantWrites().isEmpty())
        assertEquals(Phase.IDLE, unwritable.host.schedulePhase)
    }

    @Test fun revokedBeforeScheduledSecondWriteCannotCrossQueueGuardOrRearm() {
        for (leaseLoss in listOf(true, false)) {
            val f = Fixture()
            assertTrue(f.host.requestWeekly(expected, target))
            f.ack()
            if (leaseLoss) f.lease = false else f.environmentSafe = false
            f.advance(500)
            assertEquals(Phase.UNKNOWN, f.host.schedulePhase)
            assertFalse(f.host.requestBrewHeatOff())
            assertEquals(1, f.relevantWrites().size)
            assertEquals(setOf(Kind.SCHEDULE), f.kinds())
            f.lease = true; f.environmentSafe = true
            f.advance(1)
            assertFalse(f.host.requestWeekly(expected, target))
            assertFalse(f.newHost().requestWeekly(expected, target))
            assertEquals(1, f.relevantWrites().size)
        }
    }

    @Test fun queuedBoilerRevalidatesLeaseAtActualDispatch() {
        val f = Fixture()
        f.scheduleWaiting()
        // Place a harmless fake fixture write ahead of the queued boiler on the same queue.
        f.session.writeSetting(MachineSettingChange.Light(false)) { }
        assertTrue(f.host.requestBrewHeatOff())
        assertEquals(2, f.relevantWrites().size)
        f.lease = false
        f.ack()
        assertEquals(2, f.relevantWrites().size)
        assertEquals(Phase.UNKNOWN, f.host.boilerPhase)
        assertEquals(setOf(Kind.SCHEDULE, Kind.BOILER), f.kinds())
    }

    @Test fun existingUrgentStopCancelsUnfinishedScheduleAndLeavesItsFence() {
        val f = Fixture()
        assertTrue(f.host.requestWeekly(expected, target))
        f.ack()
        var stopped = false
        f.session.stopExtraction(7) { stopped = it is OperationResult.Success }
        f.ack()
        assertTrue(stopped)
        assertEquals(hex(CoffeeCommands.stop(7).frame.toByteArray()), f.writeOwners.last().first)
        assertEquals(Phase.UNKNOWN, f.host.schedulePhase)
        assertEquals(setOf(Kind.SCHEDULE), f.kinds())
        f.advance(500)
        assertEquals(listOf(expectedScheduleWrites.first()), f.relevantWrites().map { it.first })
        assertFalse(f.host.requestBrewHeatOff())
    }

    @Test fun disconnectAndTimeoutDoNotClearOrReplayEitherOwner() {
        for (disconnect in listOf(true, false)) {
            val f = Fixture()
            f.scheduleWaiting()
            assertTrue(f.host.requestBrewHeatOff()); f.ack()
            val old = f.session.generation
            if (disconnect) f.session.onDisconnected(old, "fake link gone")
            else f.advance(DetachedCoffeeCommandHost.TIMEOUT_MS)
            f.host.poll()
            assertEquals(Phase.UNKNOWN, f.host.boilerPhase)
            assertEquals(Phase.UNKNOWN, f.host.schedulePhase)
            assertEquals(setOf(Kind.SCHEDULE, Kind.BOILER), f.kinds())
            if (disconnect) {
                f.connect()
                f.receive(targetFirst, old); f.receive(second, old); f.receive(f.off(0x20), old)
            }
            f.receive(targetFirst); f.receive(second); f.receive(f.off(0x20))
            assertFalse(f.host.requestBrewHeatOff())
            assertFalse(f.newHost().requestWeekly(expected, target))
            assertEquals(3, f.relevantWrites().size)
            assertEquals(setOf(Kind.SCHEDULE, Kind.BOILER), f.kinds())
        }
    }
}

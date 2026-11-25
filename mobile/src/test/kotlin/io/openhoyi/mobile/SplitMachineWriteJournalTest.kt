package io.openhoyi.mobile

import io.openhoyi.session.*
import io.openhoyi.session.SplitMachineWriteJournal
import io.openhoyi.session.MachineWriteRecoveryState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SplitMachineWriteJournalTest {
    private val address = "AA:BB:CC:DD:EE:01"
    private val schedule = SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.SCHEDULE, address)
    private val boiler = SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.BOILER, address)
    private enum class StopAt { NEVER, BEFORE, AFTER_CACHE_CLEAR, AFTER }

    /** Atomic marker / legacy record contract, not an Android filesystem durability test. */
    private inner class Disk : SplitMachineWriteJournal.Store {
        var marker: String? = null
        var cached = MachineWriteRecoveryState.Record(null, null)
        var stopAt = StopAt.NEVER
        var commits = 0
        override fun read() = marker?.let { raw ->
            runCatching { SplitMachineWriteMarkerCodec.decode(JSONObject(raw)) }.getOrDefault(SplitMachineWriteJournal.Snapshot.Blocked)
        } ?: if (cached.pending || cached.address != null) SplitMachineWriteJournal.Snapshot.Blocked else SplitMachineWriteJournal.Snapshot.Empty

        override fun replace(entries: List<SplitMachineWriteJournal.Entry>): Boolean {
            commits++
            if (stopAt == StopAt.BEFORE) return false
            marker = SplitMachineWriteMarkerCodec.encode(entries).toString()
            return stopAt != StopAt.AFTER
        }

        override fun clear(): Boolean {
            commits++
            if (stopAt == StopAt.BEFORE) return false
            cached = MachineWriteRecoveryState.Record(null, null)
            if (stopAt == StopAt.AFTER_CACHE_CLEAR) return false
            marker = null
            return stopAt != StopAt.AFTER
        }

        fun legacy() = MachineWriteRecoveryState(object : MachineWriteRecoveryState.Storage {
            override fun read(): MachineWriteRecoveryState.Record = RecoveryPersistenceBarrier(object : RecoveryPersistenceBarrier.RecordStorage<MachineWriteRecoveryState.Record> {
                override fun read() = cached
                override fun write(value: MachineWriteRecoveryState.Record) = error("Unexpected legacy write")
            }, object : RecoveryPersistenceBarrier.Marker<MachineWriteRecoveryState.Record> {
                override fun exists() = marker != null
                override fun read() = marker?.let { RecoveryPreferenceStorage.decodeMachineMarker(JSONObject(it)) }
                override fun write(value: MachineWriteRecoveryState.Record) = error("Unexpected legacy write")
                override fun clear() = error("Unexpected legacy clear")
            }) { it.pending || it.address != null }.read()
            override fun write(record: MachineWriteRecoveryState.Record) = error("Unexpected recovery write")
        })
    }

    private fun assertFenced(disk: Disk) {
        val old = disk.legacy()
        assertTrue(old.pending)
        assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN, old.kind)
        assertFalse(old.arm(MachineWriteRecoveryState.Kind.SETTING, address))
        val scheduleEvidence = MachineWriteRecoveryState.ScheduleEvidence(address, true, 10, 10, true, 999, 1000, false)
        val settingEvidence = MachineWriteRecoveryState.SettingEvidence(address, true, 10, true, 999, 1000, false)
        assertEquals(MachineWriteAcknowledgement.Result.WAITING,
            MachineWriteAcknowledgement.acknowledge(old, true, MachineWriteAcknowledgement.Request.Schedule(scheduleEvidence, 1, 1)))
        assertEquals(MachineWriteAcknowledgement.Result.WAITING,
            MachineWriteAcknowledgement.acknowledge(old, true, MachineWriteAcknowledgement.Request.Setting(settingEvidence, 1)))
    }

    @Test fun separateOwnershipClearsOnlyMatchingOperationAndNeverSurvivesRestart() {
        val disk = Disk()
        val one = SplitMachineWriteJournal(disk)
        val s = requireNotNull(one.reserve(schedule))
        assertFenced(disk)
        assertNull(one.reserve(schedule))
        assertNull(one.reserve(boiler.copy(address = "AA:BB:CC:DD:EE:02")))
        val b = requireNotNull(one.reserve(boiler))
        assertFenced(disk)
        val restarted = SplitMachineWriteJournal(disk)
        assertFalse(restarted.owns(s))
        assertFalse(restarted.settle(s))
        assertNull(restarted.reserve(boiler))
        assertTrue(one.settle(s))
        assertFalse(one.owns(s))
        assertTrue(one.owns(b))
        assertEquals(SplitMachineWriteJournal.Snapshot.Pending(listOf(boiler)), disk.read())
        assertFenced(disk)
        assertTrue(one.settle(b))
        assertEquals(SplitMachineWriteJournal.Snapshot.Empty, disk.read())
        assertFalse(disk.legacy().pending)
    }

    @Test fun everyAtomicReplacementCrashPointRetainsOldOrNewFence() {
        for (phase in 0..2) for (failure in listOf(StopAt.BEFORE, StopAt.AFTER)) {
            val disk = Disk()
            val journal = SplitMachineWriteJournal(disk)
            val s = if (phase >= 1) requireNotNull(journal.reserve(schedule)) else null
            if (phase == 2) requireNotNull(journal.reserve(boiler))
            val old = disk.read()
            disk.stopAt = failure
            if (phase < 2) assertNull(journal.reserve(if (phase == 0) schedule else boiler))
            else assertFalse(journal.settle(requireNotNull(s)))
            assertEquals(SplitMachineWriteJournal.Snapshot.Blocked, journal.pending)
            assertFalse(journal.owns(s))
            val entries = when (phase) { 0 -> listOf(schedule); 1 -> listOf(schedule, boiler); else -> listOf(boiler) }
            assertEquals(if (failure == StopAt.BEFORE) old else SplitMachineWriteJournal.Snapshot.Pending(entries), disk.read())
            if (disk.marker != null) assertFenced(disk)
            if (disk.read() is SplitMachineWriteJournal.Snapshot.Pending) assertNull(SplitMachineWriteJournal(disk).reserve(boiler))
        }
    }

    @Test fun finalClearKeepsLegacyFenceUntilCacheIsCleared() {
        for (failure in listOf(StopAt.BEFORE, StopAt.AFTER_CACHE_CLEAR, StopAt.AFTER)) {
            val disk = Disk()
            val journal = SplitMachineWriteJournal(disk)
            val owner = requireNotNull(journal.reserve(schedule))
            disk.cached = MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.UNKNOWN, null)
            disk.stopAt = failure
            assertFalse(journal.settle(owner))
            assertEquals(SplitMachineWriteJournal.Snapshot.Blocked, journal.pending)
            assertFalse(journal.owns(owner))
            if (failure == StopAt.AFTER) {
                assertEquals(SplitMachineWriteJournal.Snapshot.Empty, disk.read())
                assertFalse(disk.legacy().pending)
            } else assertFenced(disk)
        }
    }

    @Test fun legacyPendingCachesAndMalformedMarkersAreNotMigratedOrOverwritten() {
        val disk = Disk()
        disk.cached = MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE, address)
        assertNull(SplitMachineWriteJournal(disk).reserve(schedule))
        assertEquals(MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE, disk.legacy().kind)
        for (text in listOf("{", """{"kind":"SLEEP_SCHEDULE","address":"AA:BB:CC:DD:EE:01"}""",
            SplitMachineWriteMarkerCodec.encode(listOf(schedule)).toString().replace("\"version\":1", "\"version\":2"))) {
            disk.marker = text
            assertEquals(SplitMachineWriteJournal.Snapshot.Blocked, SplitMachineWriteJournal(disk).pending)
            assertNull(SplitMachineWriteJournal(disk).reserve(boiler))
            assertEquals(text, disk.marker)
            assertTrue(disk.legacy().pending)
        }
        assertEquals(0, disk.commits)
    }
}

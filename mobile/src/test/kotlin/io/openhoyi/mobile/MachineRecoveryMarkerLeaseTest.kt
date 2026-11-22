package io.openhoyi.mobile

import java.io.File
import java.nio.file.Files
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.MachineWriteRegistration
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SplitMachineWriteJournal
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MachineRecoveryMarkerLeaseTest {
    @get:Rule val temporary = TemporaryFolder()
    private val address = "AA:BB:CC:DD:EE:01"
    private val entry = SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.SCHEDULE, address)
    private val markerName = "pending_machine_write.json"

    // Native Os calls are outside this JVM test; the real shared lease and inspection are not.
    private val fileOps = object : SplitMachineWriteFileStore.FileOps {
        override fun exists(file: File) = file.exists()
        override fun readBounded(file: File, max: Int) = file.readBytes().also { require(it.size <= max) }
        override fun replace(file: File, bytes: ByteArray) { file.writeBytes(bytes) }
        override fun unlink(file: File) { check(file.delete()) }
    }

    private class Legacy(private val marker: File) : MachineWriteRecoveryState.Storage {
        var commits = 0
        override fun read(): MachineWriteRecoveryState.Record =
            if (marker.exists()) RecoveryPreferenceStorage.decodeMachineMarker(JSONObject(marker.readText()))
            else MachineWriteRecoveryState.Record(null, null)
        override fun write(record: MachineWriteRecoveryState.Record): Boolean =
            MachineRecoveryMarkerLease.withLegacyWrite(marker) {
                commits++
                if (record.pending) marker.writeText(JSONObject().put("kind", record.kind?.name)
                    .put("address", record.address ?: JSONObject.NULL).toString())
                else if (marker.exists()) check(marker.delete())
                true
            }
    }

    @Test fun defaultLegacyRegistrationAndOwnedClearStillUseSameOnDiskShape() {
        val marker = File(temporary.newFolder(), markerName)
        val storage = Legacy(marker)
        val legacy = MachineWriteRecoveryState(storage)
        val change = MachineSettingChange.BrewHeating(false)
        val admitted = MachineWriteRegistration.begin(legacy, address,
            MachineWriteRegistration.Request.Setting(SettingsWriteTracker(), change, 10))
        assertTrue(admitted is MachineWriteRegistration.Result.Registered)
        val owner = requireNotNull(legacy.captureOwnership())
        assertEquals(setOf("kind", "address"), JSONObject(marker.readText()).keys().asSequence().toSet())
        assertEquals(MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.SETTING, address), storage.read())
        requireNotNull(SplitMachineWriteFileStore.tryOpen(marker.parentFile!!, { false }, { true }, fileOps)).use {
            assertEquals(SplitMachineWriteJournal.Snapshot.Blocked, it.read())
            assertFalse(it.replace(listOf(entry)))
        }
        assertTrue(legacy.clear(owner))
        assertFalse(marker.exists())
        assertEquals(2, storage.commits)
    }

    @Test fun splitLeaseAndPersistedFenceBothRejectLegacyStateThatWasCreatedEarlier() {
        val marker = File(temporary.newFolder(), markerName)
        val storage = Legacy(marker)
        val stale = MachineWriteRecoveryState(storage)
        val folderAlias = File(marker.parentFile, ".")
        requireNotNull(SplitMachineWriteFileStore.tryOpen(folderAlias, { false }, { true }, fileOps)).use { split ->
            assertFalse(stale.arm(MachineWriteRecoveryState.Kind.SETTING, address)) // even before the first split marker
            assertNotNull(SplitMachineWriteJournal(split).reserve(entry))
            // A diagnostic/update reader is not a writer and can see the old-compatible fence.
            assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN, storage.read().kind)
            assertFalse(stale.arm(MachineWriteRecoveryState.Kind.SETTING, address))
        }
        val before = marker.readBytes()
        assertFalse(stale.arm(MachineWriteRecoveryState.Kind.SETTING, address))
        assertFalse(storage.write(MachineWriteRecoveryState.Record(null, null)))
        assertArrayEquals(before, marker.readBytes())
        assertEquals(0, storage.commits)
    }

    @Test fun legacyLeaseBlocksSplitAndReleasesEvenOnException() {
        val marker = File(temporary.newFolder(), markerName)
        var legacyEntered = false
        var splitOpened = false
        assertFalse(MachineRecoveryMarkerLease.withLegacyWrite(marker) {
            legacyEntered = true
            SplitMachineWriteFileStore.tryOpen(File(marker.parentFile, "."), { false }, { true }, fileOps)?.use {
                splitOpened = true
            }
            error("synthetic cache write exception")
        })
        assertTrue(legacyEntered)
        assertFalse(splitOpened)
        requireNotNull(SplitMachineWriteFileStore.tryOpen(marker.parentFile!!, { false }, { true }, fileOps)).close()
        assertTrue(MachineRecoveryMarkerLease.withLegacyWrite(marker) { true })
    }

    @Test fun uncertainMarkerDoesNotAllowLegacyOverwriteAndDifferentPathIsIndependent() {
        val folder = temporary.newFolder()
        val marker = File(folder, markerName)
        val separatePath = File(folder, "pending_temperature_confirmation.json")
        val target = File(temporary.newFolder(), "other").apply { writeText("do not read or alter") }
        val checkRefusal = {
            var writes = 0
            assertFalse(MachineRecoveryMarkerLease.withLegacyWrite(marker) { writes++; true })
            assertEquals(0, writes)
        }
        marker.writeText("{"); checkRefusal()
        marker.writeText("""{"kind":"UNKNOWN","address":null}"""); checkRefusal()
        marker.writeText("""{"kind":"UNKNOWN","address":"AA:BB:CC:DD:EE:01"}"""); checkRefusal()
        marker.writeText("""{"kind":"FUTURE_KIND","address":"AA:BB:CC:DD:EE:01"}"""); checkRefusal()
        marker.writeText("""{"kind":null,"address":"AA:BB:CC:DD:EE:01"}"""); checkRefusal()
        marker.writeText("""{"kind":"UNKNOWN","kind":"SETTING","address":"AA:BB:CC:DD:EE:01"}"""); checkRefusal()
        marker.writeText("""{"kind":"UNKNOWN","address":null,"future":3}"""); checkRefusal()
        marker.writeBytes(ByteArray(4097)); checkRefusal()
        check(marker.delete())
        Files.createSymbolicLink(marker.toPath(), target.toPath())
        checkRefusal()
        assertEquals("do not read or alter", target.readText())
        requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)).use {
            assertTrue(MachineRecoveryMarkerLease.withLegacyWrite(separatePath) { true })
        }
    }

    @Test fun pinnedServiceWriterSurvivesLegacyAndSplitContendersAndStaleInstances() {
        val root=Files.createTempDirectory("openhoyi-pinned-machine").toFile()
        val marker=File(root,"pending_machine_write.json")
        try {
            val record=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.SETTING,"AA:BB:CC:DD:EE:01")
            var saved=MachineWriteRecoveryState.Record(null,null)
            var reads=0; var writes=0
            val underlying=object:MachineWriteRecoveryState.Storage {
                override fun read():MachineWriteRecoveryState.Record { reads++;return saved }
                override fun write(record:MachineWriteRecoveryState.Record):Boolean {
                    writes++
                    if(record.pending) marker.writeText("""{"kind":"SETTING","address":"AA:BB:CC:DD:EE:01"}""")
                    else marker.delete()
                    saved=record;return true
                }
            }
            val pin=RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),underlying)
            pin.use {
                assertEquals(saved,pin.storage.read())
                assertTrue(pin.storage.write(record))
                assertNull(MachineRecoveryMarkerLease.tryAcquire(marker))
                assertFalse(MachineRecoveryMarkerLease.withLegacyWrite(marker) { error("competing writer") })
                val contested=RecoveryPreferenceStorage.PinnedMachine(null,underlying)
                assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN,contested.storage.read().kind)
                assertFalse(contested.storage.write(saved))
                assertEquals(1,reads)
                assertEquals(1,writes)
            }
            assertEquals(MachineWriteRecoveryState.Kind.UNKNOWN,pin.storage.read().kind)
            assertFalse(pin.storage.write(MachineWriteRecoveryState.Record(null,null)))
            RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),underlying).use { resumed ->
                assertEquals(record,resumed.storage.read())
                assertTrue(resumed.storage.write(MachineWriteRecoveryState.Record(null,null)))
            }
            assertEquals(2,writes)
        } finally { root.deleteRecursively() }
    }

    @Test fun pinnedLegacyNeverOverwritesSplitDowngradeFence() {
        val root=Files.createTempDirectory("openhoyi-pinned-fence").toFile()
        val marker=File(root,"pending_machine_write.json")
        try {
            val known="""{"kind":"UNKNOWN","address":null,"split":[{"kind":"SCHEDULE","address":"AA:BB:CC:DD:EE:01"}]}"""
            marker.writeText(known)
            var changes=0
            val fence=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.UNKNOWN,null)
            val mem=object:MachineWriteRecoveryState.Storage {
                override fun read()=fence
                override fun write(record:MachineWriteRecoveryState.Record):Boolean {changes++;return true}
            }
            RecoveryPreferenceStorage.PinnedMachine(requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)),mem).use {
                assertEquals(fence,it.storage.read())
                assertFalse(it.storage.write(MachineWriteRecoveryState.Record(null,null)))
            }
            assertEquals(0,changes)
            assertEquals(known,marker.readText())
        } finally { root.deleteRecursively() }
    }
}

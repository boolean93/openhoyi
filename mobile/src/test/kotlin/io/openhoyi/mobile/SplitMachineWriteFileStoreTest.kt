package io.openhoyi.mobile

import io.openhoyi.session.SplitMachineWriteJournal
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SplitMachineWriteFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val entry = SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.SCHEDULE, "AA:BB:CC:DD:EE:01")

    private class MemoryOps : SplitMachineWriteFileStore.FileOps {
        var current: ByteArray? = null
        var lateFailure = false
        val events = mutableListOf<String>()
        override fun exists(file: File) = current != null
        override fun readBounded(file: File, max: Int) = requireNotNull(current).also { require(it.size <= max) }
        override fun replace(file: File, bytes: ByteArray) {
            events += "replace"
            current = bytes.copyOf()
            if (lateFailure) error("after rename before directory sync")
        }
        override fun unlink(file: File) {
            events += "unlink"
            current = null
            if (lateFailure) error("after unlink before directory sync")
        }
    }

    @Test fun leaseIdentityUsesCanonicalPathAndCannotAdoptRestartedFence() {
        val ops = MemoryOps()
        val parent = temporary.newFolder("marker")
        val alias = File(parent, ".")
        val open = requireNotNull(SplitMachineWriteFileStore.tryOpen(parent, {false}, {true}, ops))
        assertNull(SplitMachineWriteFileStore.tryOpen(alias, {false}, {true}, ops))
        val journal = SplitMachineWriteJournal(open)
        assertNotNull(journal.reserve(entry))
        open.close()
        assertFalse(open.replace(listOf(entry)))
        requireNotNull(SplitMachineWriteFileStore.tryOpen(alias, {false}, {true}, ops)).use {
            assertTrue(it.read() is SplitMachineWriteJournal.Snapshot.Pending)
            assertFalse(it.replace(listOf(entry)))
            assertFalse(it.clear())
        }
        assertEquals(listOf("replace"), ops.events)
    }

    @Test fun clearNeverUnlinksWithUncertainOrPendingLegacyCache() {
        for (clears in listOf(false, true)) {
            val ops = MemoryOps()
            var cached = false
            val parent = temporary.newFolder()
            requireNotNull(SplitMachineWriteFileStore.tryOpen(parent, {cached}, {
                ops.events += "cache"
                clears // even true is insufficient when the subsequent cache read still says pending
            }, ops)).use {
                assertTrue(it.replace(listOf(entry)))
                cached = true
                assertFalse(it.clear())
                assertFalse(it.clear())
                assertEquals(listOf("replace", "cache"), ops.events)
                assertNotNull(ops.current)
            }
        }
    }

    @Test fun failedPostRenameSyncLatchesAndNeverClearsPendingMarker() {
        val ops = MemoryOps().apply { lateFailure = true }
        requireNotNull(SplitMachineWriteFileStore.tryOpen(temporary.newFolder(), {false}, {true}, ops)).use {
            assertFalse(it.replace(listOf(entry)))
            assertFalse(it.replace(listOf(entry)))
            assertFalse(it.clear())
            assertEquals(listOf("replace"), ops.events)
            assertTrue(it.read() is SplitMachineWriteJournal.Snapshot.Pending)
        }
    }

    @Test fun boundedStrictDecoderRejectsDuplicateUnknownAndCoercibleFields() {
        val valid = SplitMachineWriteMarkerCodec.encode(listOf(entry)).toString()
        assertTrue(SplitMachineWriteMarkerCodec.decode(valid.toByteArray()) is SplitMachineWriteJournal.Snapshot.Pending)
        for (bad in listOf(
            "{\"kind\":\"SETTING\"," + valid.drop(1), "{\"unexpected\":null," + valid.drop(1),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            valid.replace("\"version\":1", "\"version\":1.0"),
            valid.replace("AA:BB:CC:DD:EE:01", "AA:bb:CC:DD:EE:01"),
            valid.replace("\"operation\":", "\"other\":\"SCHEDULE\",\"operation\":"),
            valid.replace("SCHEDULE", "SCHEDULÉ"), valid + "{}", valid + " ".repeat(4096)
        )) assertEquals(bad, SplitMachineWriteJournal.Snapshot.Blocked, SplitMachineWriteMarkerCodec.decode(bad.toByteArray()))
    }
}

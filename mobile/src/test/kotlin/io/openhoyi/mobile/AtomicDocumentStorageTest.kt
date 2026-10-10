package io.openhoyi.mobile

import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class AtomicDocumentStorageTest {
    @Test fun failedSyncKeepsOldDiskAndUnpublishedUsage() {
        val folder = Files.createTempDirectory("hoyi-storage-sync")
        try {
            val file = folder.resolve("usage")
            val normal = AtomicDocumentStorage(file)
            val original = CurveUsageLedger(storage(normal))
            original.record("a", "curve", 100)
            val before = Files.readAllBytes(file)
            val broken = AtomicDocumentStorage(file, sync = { throw IOException("sync failed") })
            val ledger = CurveUsageLedger(storage(broken))
            assertThrows(IOException::class.java) { ledger.record("b", "curve", 200) }
            assertEquals(1L, ledger.stats("curve").count)
            assertArrayEquals(before, Files.readAllBytes(file))
        } finally { folder.toFile().deleteRecursively() }
    }
    @Test fun failedRenameKeepsOldDiskAndDoesNotClaimSuccess() {
        val folder = Files.createTempDirectory("hoyi-storage-move")
        try {
            val file = folder.resolve("document")
            AtomicDocumentStorage(file).write("old".toByteArray())
            val broken = AtomicDocumentStorage(file, replace = { _, _ -> throw IOException("rename failed") })
            assertThrows(IOException::class.java) { broken.write("new".toByteArray()) }
            assertEquals("old", String(Files.readAllBytes(file)))
        } finally { folder.toFile().deleteRecursively() }
    }
    private fun storage(file: AtomicDocumentStorage) = object : CurveUsageLedger.Storage {
        override fun read() = file.read()?.toString(Charsets.UTF_8) ?: ""
        override fun write(value: String) = file.write(value.toByteArray(Charsets.UTF_8))
    }
}

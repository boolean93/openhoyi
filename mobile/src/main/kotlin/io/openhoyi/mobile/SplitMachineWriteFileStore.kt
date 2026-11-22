package io.openhoyi.mobile

import android.system.Os
import android.system.OsConstants
import io.openhoyi.session.SplitMachineWriteJournal
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/**
 * Candidate Android marker backend. Do not wire into the existing service until the legacy
 * service takes the shared lease across the control lifetime and Android integration is tested.
 */
internal class SplitMachineWriteFileStore private constructor(
    private val lease: MachineRecoveryMarkerLease.Lease,
    private val cachedLegacyPending: () -> Boolean,
    private val clearCachedLegacy: () -> Boolean,
    private val ops: FileOps,
) : SplitMachineWriteJournal.Store, Closeable {
    internal interface FileOps {
        fun exists(file: File): Boolean
        fun readBounded(file: File, max: Int): ByteArray
        /** Same-directory temp, file sync, atomic replace, then parent directory sync. */
        fun replace(file: File, bytes: ByteArray)
        /** Unlink the fence, then sync the parent directory. */
        fun unlink(file: File)
    }

    private val marker = lease.marker
    private var active = true
    private val initial = inspect()
    private var lastWritten: ByteArray? = null

    @Synchronized override fun read(): SplitMachineWriteJournal.Snapshot =
        if (active) inspect() else SplitMachineWriteJournal.Snapshot.Blocked

    private fun inspect(): SplitMachineWriteJournal.Snapshot = runCatching {
        if (ops.exists(marker)) SplitMachineWriteMarkerCodec.decode(
            ops.readBounded(marker, SplitMachineWriteMarkerCodec.MAX_BYTES))
        else if (cachedLegacyPending()) SplitMachineWriteJournal.Snapshot.Blocked
        else SplitMachineWriteJournal.Snapshot.Empty
    }.getOrDefault(SplitMachineWriteJournal.Snapshot.Blocked)

    private fun ownsCurrentMarker(): Boolean {
        val known = lastWritten
        return if (known == null) initial == SplitMachineWriteJournal.Snapshot.Empty &&
            !ops.exists(marker) && !cachedLegacyPending()
        else ops.exists(marker) && ops.readBounded(marker, SplitMachineWriteMarkerCodec.MAX_BYTES).contentEquals(known)
    }

    @Synchronized override fun replace(entries: List<SplitMachineWriteJournal.Entry>): Boolean = mutate {
        val bytes = SplitMachineWriteMarkerCodec.encode(entries).toString().toByteArray(Charsets.US_ASCII)
        ops.replace(marker, bytes)
        lastWritten = bytes
        true
    }

    @Synchronized override fun clear(): Boolean = mutate {
        if (lastWritten == null || !clearCachedLegacy() || cachedLegacyPending()) return@mutate false
        ops.unlink(marker)
        lastWritten = null
        true
    }

    private fun mutate(operation: () -> Boolean): Boolean {
        if (!active || failed) return false
        val ok = runCatching { ownsCurrentMarker() && operation() }.getOrDefault(false)
        if (!ok) failed = true // Keep this lease until close; no retries against stale bytes.
        return ok
    }

    private var failed = false

    @Synchronized override fun close() {
        if (!active) return
        active = false
        lease.close()
    }

    companion object {
        private const val MARKER = "pending_machine_write.json"

        /** The caller must own an Android app-private directory (normally noBackupFilesDir). */
        fun tryOpen(directory: File, cachedLegacyPending: () -> Boolean, clearCachedLegacy: () -> Boolean,
            ops: FileOps = AndroidFileOps): SplitMachineWriteFileStore? {
            val lease = MachineRecoveryMarkerLease.tryAcquire(File(directory, MARKER)) ?: return null
            return try {
                SplitMachineWriteFileStore(lease, cachedLegacyPending, clearCachedLegacy, ops)
            } catch (error: Throwable) {
                lease.close()
                throw error
            }
        }
    }

    private object AndroidFileOps : FileOps {
        override fun exists(file: File): Boolean {
            try { Os.lstat(file.path); return true }
            catch (error: android.system.ErrnoException) {
                if (error.errno == OsConstants.ENOENT) return false
                throw error
            }
        }

        override fun readBounded(file: File, max: Int): ByteArray {
            val fd = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
            try {
                require(OsConstants.S_ISREG(Os.fstat(fd).st_mode))
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(512)
                while (true) {
                    val n = Os.read(fd, chunk, 0, chunk.size)
                    if (n == 0) return out.toByteArray()
                    require(out.size() + n <= max)
                    out.write(chunk, 0, n)
                }
            } finally { Os.close(fd) }
        }

        override fun replace(file: File, bytes: ByteArray) {
            val parent = requireNotNull(file.parentFile)
            val temp = File.createTempFile("split-marker-", ".tmp", parent)
            try {
                FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
                Os.rename(temp.path, file.path) // Same directory; never remove the old file first.
                syncDirectory(parent)
            } finally {
                if (temp.exists()) temp.delete()
            }
        }

        override fun unlink(file: File) {
            require(OsConstants.S_ISREG(Os.lstat(file.path).st_mode))
            Os.remove(file.path)
            syncDirectory(requireNotNull(file.parentFile))
        }

        private fun syncDirectory(directory: File) {
            val fd = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK, 0)
            try {
                require(OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
                Os.fsync(fd)
            } finally { Os.close(fd) }
        }
    }
}

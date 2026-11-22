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
    private val ownsLease:Boolean=true,
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
    private var mutating = false
    private data class Capture(val snapshot:SplitMachineWriteJournal.Snapshot,val bytes:ByteArray?=null)
    private val initialCapture = capture()
    private val initial=initialCapture.snapshot
    private var lastWritten: ByteArray? = null
    private var liveEntries:List<SplitMachineWriteJournal.Entry> = emptyList()

    // Bytes loaded at startup are not dispatch bytes. A recovery-specific compare-and-swap
    // can remove one acknowledged entry, but cannot reserve or obtain a live send owner.
    private var recoveryBytes=initialCapture.bytes?.takeIf {initial is SplitMachineWriteJournal.Snapshot.Pending}
    private var recoveryEntries=(initial as? SplitMachineWriteJournal.Snapshot.Pending)?.entries

    @Synchronized override fun read(): SplitMachineWriteJournal.Snapshot =
        if (active && !mutating && canUseLease()) capture().snapshot else SplitMachineWriteJournal.Snapshot.Blocked

    private fun canUseLease()=lease.held && (ownsLease || lease.isOwnerThread)

    private fun capture():Capture = runCatching {
        if (ops.exists(marker)) {
            val bytes=ops.readBounded(marker, SplitMachineWriteMarkerCodec.MAX_BYTES).copyOf()
            Capture(SplitMachineWriteMarkerCodec.decode(bytes),bytes)
        } else if (cachedLegacyPending()) Capture(SplitMachineWriteJournal.Snapshot.Blocked)
        else Capture(SplitMachineWriteJournal.Snapshot.Empty)
    }.getOrDefault(Capture(SplitMachineWriteJournal.Snapshot.Blocked))

    private fun ownsCurrentMarker(): Boolean {
        val known = lastWritten
        return if (known == null) initial == SplitMachineWriteJournal.Snapshot.Empty &&
            !ops.exists(marker) && !cachedLegacyPending()
        else ops.exists(marker) && ops.readBounded(marker, SplitMachineWriteMarkerCodec.MAX_BYTES).contentEquals(known)
    }

    /** Ambient methods remain for the original standalone backend tests, never the borrowed view. */
    @Synchronized override fun replace(entries:List<SplitMachineWriteJournal.Entry>):Boolean =
        ownsLease && replaceExpected(liveEntries,entries)

    @Synchronized override fun clear():Boolean = ownsLease && clearExpected(liveEntries)

    @Synchronized override fun replaceExpected(expected:List<SplitMachineWriteJournal.Entry>,
        replacement:List<SplitMachineWriteJournal.Entry>):Boolean {
        // A second journal using this same store may hold a cached Empty or previous revision.
        if(expected!=liveEntries)return false
        return mutate {
            val bytes=SplitMachineWriteMarkerCodec.encode(replacement).toString().toByteArray(Charsets.US_ASCII)
            ops.replace(marker,bytes)
            lastWritten=bytes
            liveEntries=replacement.toList()
            true
        }
    }

    @Synchronized override fun clearExpected(expected:List<SplitMachineWriteJournal.Entry>):Boolean {
        if(expected!=liveEntries)return false
        return mutate {
            if (lastWritten == null || !clearCachedLegacy() || cachedLegacyPending()) return@mutate false
            ops.unlink(marker)
            lastWritten=null
            liveEntries=emptyList()
            true
        }
    }

    @Synchronized override fun settleRecovered(expected:List<SplitMachineWriteJournal.Entry>,
        entry:SplitMachineWriteJournal.Entry):Boolean {
        if(!active || !canUseLease() || failed || mutating || lastWritten!=null || expected!=recoveryEntries || entry !in expected) return false
        val bytes=recoveryBytes ?: return false
        return performMutation {
            if (!ops.exists(marker) || !ops.readBounded(marker,SplitMachineWriteMarkerCodec.MAX_BYTES).contentEquals(bytes))
                return@performMutation false
            val remaining=expected.filterNot {it==entry}
            if(remaining.isEmpty()) {
                // Keep the legacy-visible fence through the complete cache-clear boundary.
                if (!clearCachedLegacy() || cachedLegacyPending()) return@performMutation false
                ops.unlink(marker)
                recoveryBytes=null
                recoveryEntries=null
            } else {
                val next=SplitMachineWriteMarkerCodec.encode(remaining).toString().toByteArray(Charsets.US_ASCII)
                ops.replace(marker,next)
                recoveryBytes=next
                recoveryEntries=remaining
            }
            true
        }
    }

    private fun mutate(operation: () -> Boolean): Boolean {
        if (!active || !canUseLease() || failed || mutating) return false
        return performMutation { ownsCurrentMarker() && operation() }
    }

    private fun performMutation(operation:()->Boolean):Boolean {
        mutating=true
        try {
            val ok=runCatching(operation).getOrDefault(false)
            if(!ok)failed=true // Keep the lease; do not retry against uncertain bytes.
            return ok
        } finally {
            mutating=false
        }
    }

    private var failed = false

    @Synchronized override fun close() {
        if(!ownsLease)check(lease.isOwnerThread) { "Borrowed writer must retire on its service owner thread" }
        if (!active) return
        check(!mutating) { "Writer mutation is still in progress" }
        active = false
        if(ownsLease)lease.close()
    }

    companion object {
        private const val MARKER = "pending_machine_write.json"

        /** The service lifetime remains the sole owner; closing this view never unpins it. */
        fun fromPinnedLease(lease:MachineRecoveryMarkerLease.Lease,cachedLegacyPending:()->Boolean,
            clearCachedLegacy:()->Boolean,ops:FileOps=AndroidFileOps):SplitMachineWriteFileStore? =
            if(lease.isOwnerThread && lease.held) SplitMachineWriteFileStore(lease,cachedLegacyPending,clearCachedLegacy,ops,ownsLease=false)
            else null

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

package io.openhoyi.mobile

import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import org.json.JSONObject
import io.openhoyi.session.MachineWriteRecoveryState

/** One writer per canonical marker path in this process. No claim of cross-process locking. */
internal object MachineRecoveryMarkerLease {
    private val active = mutableSetOf<String>()

    internal class Lease internal constructor(val marker: File, private val release: () -> Unit) : Closeable {
        private var closed = false
        @Synchronized override fun close() {
            if (closed) return
            closed = true
            release()
        }
    }

    fun tryAcquire(marker: File): Lease? {
        // Canonicalize only the parent: resolving a leaf symlink would change the fenced identity.
        val location = runCatching { File(requireNotNull(marker.parentFile).canonicalFile, marker.name) }.getOrNull()
            ?: return null
        val key = location.path
        if (!synchronized(active) { active.add(key) }) return null
        return Lease(location) { synchronized(active) { active.remove(key) } }
    }

    /** Legacy writer may not replace a split/unknown-schema marker even after a split lease closes. */
    fun withLegacyWrite(marker: File, write: () -> Boolean): Boolean =
        tryAcquire(marker)?.use { lease ->
            runCatching { permitsLegacy(lease.marker) && write() }.getOrDefault(false)
        } ?: false

    // The legacy writer emits exactly these ASCII fields. Do not trust JSONObject's
    // last-key-wins or trailing-data behavior on the marker claimed by another writer.
    private val kindField = """"kind"[ \r\n\t]*:[ \r\n\t]*"[A-Z_]+""""
    private val addressField = """"address"[ \r\n\t]*:[ \r\n\t]*"([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}""""
    private const val WS = "[ \r\n\t]*"
    private val legacyShape = Regex("""$WS\{$WS(?:$kindField$WS,$WS$addressField|$addressField$WS,$WS$kindField)$WS\}$WS""")

    private fun permitsLegacy(marker: File): Boolean {
        val path = marker.toPath()
        val noFollow = LinkOption.NOFOLLOW_LINKS
        if (Files.notExists(path, noFollow)) return true
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, noFollow)
        if (!attributes.isRegularFile || attributes.size() !in 1..4096) return false
        val bytes = ByteArray(4097)
        val n = Files.newInputStream(path, noFollow).use { input ->
            var offset = 0
            while (offset < bytes.size) {
                val size = input.read(bytes, offset, bytes.size - offset)
                if (size < 0) break
                if (size == 0) return false
                offset += size
            }
            offset
        }
        if (n !in 1..4096) return false
        val text = String(bytes, 0, n, Charsets.UTF_8)
        if (!legacyShape.matches(text)) return false
        val record = RecoveryPreferenceStorage.decodeMachineMarker(JSONObject(text))
        return record.kind != null && record.kind != MachineWriteRecoveryState.Kind.UNKNOWN
    }
}

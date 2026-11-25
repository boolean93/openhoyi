package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.Build
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.SplitMachineWriteJournal
import java.io.File
import java.util.UUID
import org.json.JSONObject

/** Exercise the actual Android Os backend only in a fresh private fixture directory. */
internal class SplitMachineWriteFileChecks(private val instrumentation: Instrumentation) {
    fun run(disposableCiEmulator: Boolean) {
        val context = instrumentation.targetContext
        check(disposableCiEmulator && BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock" &&
            Build.HARDWARE in setOf("ranchu", "goldfish"))
        val folder = File(context.cacheDir, "split-recovery-fixture-${UUID.randomUUID()}")
        check(folder.mkdir())
        var legacyCache = false
        fun open() = SplitMachineWriteFileStore.tryOpen(folder, { legacyCache }, { legacyCache = false; true })
        val marker = File(folder, "pending_machine_write.json")
        fun leakedMarkerDescriptors(): Int =
            requireNotNull(File("/proc/self/fd").listFiles()).count {
                runCatching { android.system.Os.readlink(it.path) }.getOrNull() == marker.canonicalPath
            }
        val address = "AA:BB:CC:DD:EE:01"
        try {
            requireNotNull(open()).use { store ->
                check(open() == null)
                val journal = SplitMachineWriteJournal(store)
                val a = requireNotNull(journal.reserve(SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.SCHEDULE, address)))
                val b = requireNotNull(journal.reserve(SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.BOILER, address)))
                check(RecoveryPreferenceStorage.decodeMachineMarker(JSONObject(marker.readText())) ==
                    MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.UNKNOWN, null))
                check(journal.settle(a) && marker.isFile)
                check(folder.list().orEmpty().contentEquals(arrayOf(marker.name)))
                legacyCache = true
                check(journal.settle(b) && !legacyCache && !marker.exists())
                check(store.read() == SplitMachineWriteJournal.Snapshot.Empty)
            }
            // A valid marker survives a close/reopen; a new process would likewise have no tokens.
            requireNotNull(open()).use { store ->
                check(SplitMachineWriteJournal(store).reserve(
                    SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.SCHEDULE, address)) != null)
            }
            requireNotNull(open()).use { store ->
                repeat(32) { check(store.read() is SplitMachineWriteJournal.Snapshot.Pending) }
                check(leakedMarkerDescriptors() == 0)
                check(!store.clear())
            }
            // Oversize rejection must close the descriptor too.
            marker.writeBytes(ByteArray(SplitMachineWriteMarkerCodec.MAX_BYTES + 1) { 'x'.code.toByte() })
            requireNotNull(open()).use { store ->
                repeat(32) { check(store.read() == SplitMachineWriteJournal.Snapshot.Blocked) }
                check(leakedMarkerDescriptors() == 0)
            }
        } finally {
            check(folder.deleteRecursively())
        }
    }
}

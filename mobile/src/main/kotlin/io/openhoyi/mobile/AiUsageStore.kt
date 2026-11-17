package io.openhoyi.mobile

import android.content.Context
import android.util.AtomicFile
import java.io.File

/** No credentials/content/account identifiers. noBackupFilesDir also excludes device transfer. */
internal object AiUsageStore {
    private var ledger: AiUsageLedger? = null
    @Synchronized fun get(context: Context): AiUsageLedger {
        ledger?.let { return it }
        val file = try { AtomicFile(File(context.applicationContext.noBackupFilesDir, "ai-usage-v1.json")) }
        catch (_: Exception) { return AiUsageLedger(AiUsageSnapshot(incomplete = true)).also { ledger = it } }
        var writable = true
        val initial = try {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) AiUsageSnapshot()
            else {
                val bytes = file.openRead().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) { val n = input.read(buffer); if (n < 0) break
                        require(output.size() + n <= 65536); output.write(buffer, 0, n) }
                    output.toByteArray()
                }
                AiUsageCodec.decode(bytes.toString(Charsets.UTF_8))
            }
        } catch (_: Exception) { writable = false; AiUsageSnapshot(incomplete = true) }
        return AiUsageLedger(initial) { state ->
            if (!writable) false else {
                var stream: java.io.FileOutputStream? = null
                try { stream = file.startWrite(); stream.write(AiUsageCodec.encode(state).toByteArray()); file.finishWrite(stream); true }
                catch (_: Exception) { file.failWrite(stream); false }
            }
        }.also { ledger = it }
    }
}

package io.openhoyi.mobile

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.openhoyi.trace.TraceStore
import java.io.File

/** Process-owned writer, independent of Activity and service restarts. */
class MobileApplication : Application() {
    val logs: TraceStore by lazy { TraceStore(File(filesDir, "traces")) }
    val curves: CurveLibrary by lazy {
        val factory = FactoryCurveCatalog.load(assets.open("factory_curves_v3.tsv"))
        val proof = runCatching {
            FactoryWireProof.load(assets.open("factory_wire_v1.tsv"), factory,
                assets.open("factory_slot_wire_v1.tsv"))
        }
            .onFailure { logs.record("factory.wire_proof_failed", mapOf("type" to it.javaClass.simpleName)) }
            .getOrNull()
        CurveLibrary(factory, proof)
    }
    val samples: ShotSamplesRepository by lazy {
        ShotSamplesRepository(File(filesDir, "shot_samples")) { error ->
            logs.record("shot.samples_error", mapOf("type" to error.javaClass.simpleName))
        }
    }
    val history: ShotHistory by lazy {
        val prefs = getSharedPreferences("shot_history", MODE_PRIVATE)
        ShotHistory(object : ShotHistory.Storage {
            override fun read(): String = prefs.getString("entries_v1", "") ?: ""
            override fun write(value: String) { prefs.edit().putString("entries_v1", value).apply() }
        }).also { samples.prune(it.entries.map(ShotHistory.Entry::id).toSet()) }
    }
    fun export(uri: Uri) {
        logs.record("ui.export")
        try {
            logs.export({ contentResolver.openOutputStream(uri, "wt") ?: error("No output stream") }) { error ->
                val message = if (error == null) "操作记录已导出" else "导出失败：${error.javaClass.simpleName}"
                logs.record("export.finished", mapOf("message" to message))
                Handler(Looper.getMainLooper()).post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            }
        } catch (error: RuntimeException) {
            Toast.makeText(this, "导出未开始：${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}

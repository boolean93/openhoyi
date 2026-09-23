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

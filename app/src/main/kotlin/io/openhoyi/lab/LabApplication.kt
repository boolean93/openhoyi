package io.openhoyi.lab

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.File
import io.openhoyi.trace.TraceStore
import io.openhoyi.trace.TraceAppInfo

/** Process-wide writer: stopped/restarted services cannot race over the same journal files. */
class LabApplication : Application() {
    val logs: TraceStore by lazy { TraceStore(File(filesDir, "traces"), appInfo = TraceAppInfo(
        BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(), BuildConfig.BUILD_TYPE,
    )) }
    fun export(uri: Uri) {
        logs.record("ui.export")
        try {
            logs.export({ contentResolver.openOutputStream(uri, "wt") ?: error("No output stream") }) { error ->
                val message = if (error == null) "日志导出完成" else "日志导出失败：${error.javaClass.simpleName}"
                logs.record("export.finished", mapOf("message" to message))
                Handler(Looper.getMainLooper()).post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
            }
        } catch (error: RuntimeException) {
            Toast.makeText(this, "导出未开始：${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }
}

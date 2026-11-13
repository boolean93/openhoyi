package io.openhoyi.mobile

import android.os.Handler
import android.os.Looper

/** Only the diagnostic journal is exposed here; no recipes, history, exports or APKs. */
internal class DiagnosticStorageOwner(app: MobileApplication) {
    private val handler = Handler(Looper.getMainLooper())
    val controller = DiagnosticStorageController { clear, done ->
        val callback: (io.openhoyi.trace.TraceStore.StorageInfo?, Throwable?) -> Unit = { info, failure ->
            handler.post {
                done(if (failure != null) Result.failure(failure)
                    else if (info != null) Result.success(info.bytes)
                    else Result.failure(IllegalStateException("Missing diagnostic storage result")))
            }
        }
        if (clear) app.logs.clear(callback) else app.logs.storageInfo(callback)
    }
}

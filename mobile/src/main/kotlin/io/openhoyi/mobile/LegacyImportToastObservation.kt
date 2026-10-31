package io.openhoyi.mobile

import android.widget.Toast

/** Null in production. Instrumentation observes only the original import Toast objects. */
internal object LegacyImportToastObservation {
    var observer: ((Toast, String) -> Unit)? = null

    fun beforeShow(toast: Toast, source: String) {
        observer?.invoke(toast, source)
    }
}

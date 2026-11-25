package io.openhoyi.mobile

import android.view.View
import android.widget.TextView

/** Shared native Home/Settings projection; never submits a command during render. */
internal object MachineSettingsSyncView {
    fun render(status:TextView,owner:MobileService?) {
        val result = owner?.machineSettingsSyncStatus
        status.visibility = if (result != null) View.VISIBLE else View.GONE
        val statusText = result?.let { status.context.getString(it) }.orEmpty()
        if (status.text.toString() != statusText) status.text = statusText
    }
}

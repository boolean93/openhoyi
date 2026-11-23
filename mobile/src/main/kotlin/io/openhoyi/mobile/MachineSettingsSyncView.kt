package io.openhoyi.mobile

import android.view.View
import android.widget.TextView

/** Shared native Home/Settings projection; never submits a command during render. */
internal object MachineSettingsSyncView {
    fun render(action: TextView, status: TextView, owner: MobileService?, showAction: Boolean) {
        action.visibility = if (showAction) View.VISIBLE else View.GONE
        action.isEnabled = owner?.canSyncStaleMachineSettings == true
        val actionText = action.context.getString(if (owner?.machineSettingsSyncPending == true)
            R.string.machine_settings_sync_pending else R.string.machine_settings_sync_clock)
        if (action.text.toString() != actionText) action.text = actionText
        val result = owner?.machineSettingsSyncStatus
        status.visibility = if (result != null) View.VISIBLE else View.GONE
        val statusText = result?.let { status.context.getString(it) }.orEmpty()
        if (status.text.toString() != statusText) status.text = statusText
    }
}

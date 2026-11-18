package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.widget.Toast

/** Shared, non-navigating entry for the existing explicit preheat acknowledgement. */
internal class BrewWaitRecoveryDialog(private val host: Activity, private val current: () -> MobileService?) {
    private var shown: AlertDialog? = null

    fun dismiss() { shown?.dismiss(); shown = null }

    fun show(): Boolean {
        if (shown?.isShowing == true) return true
        val owner = current() ?: return false
        val confirm = owner.brewWaitRecoveryConfirmation() ?: return false
        val dialog = AppDialog.show(AlertDialog.Builder(host)
            .setTitle(R.string.home_recovery_preheat_title)
            .setMessage(R.string.home_recovery_preheat_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.home_recovery_clear, null)
            .create())
        shown = dialog
        val button = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
        val instructions = host.getString(R.string.home_recovery_preheat_message)
        val refresh = object : Runnable {
            private var previousReady: Boolean? = null
            override fun run() {
                if (!dialog.isShowing) return
                val ready = current() === owner && owner.canConfirmBrewWaitRecovery
                if (ready != previousReady) {
                    previousReady = ready
                    button.isEnabled = ready
                    dialog.setMessage(instructions + "\n\n" +
                        host.getString(if (ready) R.string.home_recovery_preheat_can_confirm
                            else R.string.recovery_event_brew_wait_recovery_waiting))
                }
                button.postDelayed(this, 400)
            }
        }
        button.setOnClickListener {
            val error = if (current() === owner) confirm()
                else host.getString(R.string.recovery_event_brew_wait_recovery_waiting)
            if (error == null) dialog.dismiss()
            else Toast.makeText(host, error, Toast.LENGTH_SHORT).show()
        }
        dialog.setOnDismissListener {
            button.removeCallbacks(refresh)
            if (shown === dialog) shown = null
        }
        refresh.run()
        return true
    }
}

package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.WindowManager

/** Native tablet dialog minimum widths can exceed a freeform task. Use the owner window, not display metrics. */
internal object AppFontSizeDialogBounds {
    fun width(ownerWidthPx: Int, density: Float): Int =
        minOf((560 * density).toInt(), (ownerWidthPx - 32 * density).toInt()).coerceAtLeast(1)

    fun bind(activity: Activity, dialog: AlertDialog) {
        val host = activity.window.decorView
        val density = activity.resources.displayMetrics.density
        fun update() {
            val ownerWidth = host.width.takeIf { it > 0 }
                ?: (activity.resources.configuration.screenWidthDp * density).toInt()
            val expected = width(ownerWidth, density)
            dialog.window?.let { window ->
                if (window.attributes.width != expected) window.setLayout(expected, WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> update() }
        host.addOnLayoutChangeListener(listener)
        dialog.setOnDismissListener { host.removeOnLayoutChangeListener(listener) }
        update()
    }
}

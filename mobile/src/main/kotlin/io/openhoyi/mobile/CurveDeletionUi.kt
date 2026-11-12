package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/** A local undo row survives navigation/rotation through the process owner, without a recycle-bin UI. */
internal class CurveDeletionUi(private val activity: Activity, parent: LinearLayout, private val changed: () -> Unit) {
    private val app get() = activity.application as MobileApplication
    private var dialog: AlertDialog? = null
    private val style = TaskProductVisualStyle(activity)
    private val row = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        tag = "curve-delete-undo"
        visibility = View.GONE
    }
    init {
        parent.addView(row, 0, LinearLayout.LayoutParams(-1, -2))
        row.addView(style.text(activity.getString(R.string.curve_deleted), TaskProductVisualStyle.auxiliary),
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(activity).apply {
            text = activity.getString(R.string.curve_delete_undo)
            tag = "curve-delete-undo-action"
            style.button(this)
            setOnClickListener {
                val deletion = app.pendingCurveDeletion ?: return@setOnClickListener
                val result = runCatching { app.customCurvesResult.getOrThrow().undo(deletion) }
                if (result.getOrNull() == true) {
                    app.pendingCurveDeletion = null
                    changed()
                } else {
                    if (result.getOrNull() == false) app.pendingCurveDeletion = null
                    toast(R.string.curve_delete_undo_failed)
                }
                refresh()
            }
        }, LinearLayout.LayoutParams(-2, -2))
        refresh()
    }
    fun refresh() { row.visibility = if (app.pendingCurveDeletion != null) View.VISIBLE else View.GONE }
    fun pause() { dialog?.dismiss(); dialog = null }
    fun confirm(item: CurveLibraryItem) {
        if (item.customDocument == null || app.curves.find(item.id) != item) return
        if (app.curveDeletionGuards.values.any { it(item.id) }) { toast(R.string.curve_delete_in_use); return }
        val prefs = activity.getSharedPreferences("presets", Activity.MODE_PRIVATE)
        val hasSlots = (1..5).any { slot -> PresetSlots.curveId(slot,
            prefs.getString(PresetSlots.key(slot), null), app.curves) == item.id }
        val message = activity.getString(R.string.curve_delete_confirm, item.name) +
            if (hasSlots) "\n" + activity.getString(R.string.curve_delete_slots) else ""
        pause()
        dialog = AppDialog.confirm(activity, activity.getString(R.string.curve_delete), message,
            AppDialog.Action(activity.getString(R.string.curve_delete), danger = true) {
                // Recheck at commit time: a confirmation dialog grants no stale execution permission.
                if (app.curveDeletionGuards.values.any { it(item.id) }) toast(R.string.curve_delete_in_use)
                else if (runCatching { app.deleteCurve(item) }.isSuccess) {
                    changed()
                    refresh()
                } else toast(R.string.curve_delete_failed)
            })
    }
    private fun toast(message: Int) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
}

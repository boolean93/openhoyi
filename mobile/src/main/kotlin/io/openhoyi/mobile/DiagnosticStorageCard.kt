package io.openhoyi.mobile

import android.app.Activity
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

/** Compact, wrapping row. Scope remains explicit so clearing logs is not confused with clearing history. */
internal class DiagnosticStorageCard(private val activity: Activity, parent: LinearLayout, visual: SettingsVisualStyle) {
    private val controller = (activity.application as MobileApplication).diagnosticStorage.controller
    private val style = visual.palette
    private val size = style.text(activity.getString(R.string.diagnostic_storage_calculating), TaskProductVisualStyle.body,
        "product/settings/app/diagnostic-storage/size")
    private val action = style.button(Button(activity)).apply {
        text = activity.getString(R.string.diagnostic_storage_clear)
        tag = "product/settings/app/diagnostic-storage/clear"
        setOnClickListener { controller.clear() }
    }
    private val result = style.text("", TaskProductVisualStyle.auxiliary, "product/settings/app/diagnostic-storage/result", true).apply {
        visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private var observing = false
    private val observer: (DiagnosticStorageController.State) -> Unit = { render(it) }
    init {
        style.add(parent, visual.pair(size, action, "product/settings/app/diagnostic-storage/row"), 16)
        style.add(parent, style.text(activity.getString(R.string.diagnostic_storage_scope), TaskProductVisualStyle.auxiliary,
            "product/settings/app/diagnostic-storage/scope", true), 8)
        style.add(parent, result, 8)
    }
    private fun render(state: DiagnosticStorageController.State) {
        action.isEnabled = !state.busy
        action.setText(if (state.clearing && state.busy) R.string.diagnostic_storage_clearing else R.string.diagnostic_storage_clear)
        size.text = when {
            state.busy -> activity.getString(if (state.clearing) R.string.diagnostic_storage_clearing else R.string.diagnostic_storage_calculating)
            state.bytes == null -> activity.getString(R.string.diagnostic_storage_unavailable)
            else -> activity.getString(R.string.diagnostic_storage_size, Formatter.formatFileSize(activity, state.bytes))
        }
        val text = when {
            state.outcome == DiagnosticStorageController.Outcome.FAILED -> activity.getString(R.string.diagnostic_storage_failed)
            state.readFailed && !state.busy -> activity.getString(R.string.diagnostic_storage_read_failed)
            state.outcome == DiagnosticStorageController.Outcome.CLEARED -> activity.getString(R.string.diagnostic_storage_cleared)
            else -> ""
        }
        result.text = text
        result.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        result.setTextColor(if (state.readFailed || state.outcome == DiagnosticStorageController.Outcome.FAILED) activity.getColor(R.color.mobile_danger) else style.muted)
    }
    fun resume() {
        if (!observing) { observing = true; controller.observe(observer) }
        controller.refresh()
    }
    fun pause() { if (observing) controller.unobserve(observer); observing = false }
}

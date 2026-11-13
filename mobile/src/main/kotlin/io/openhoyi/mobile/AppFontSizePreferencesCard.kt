package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup

/** Preview is isolated; only Apply persists and requests a configuration rebuild. */
internal class AppFontSizePreferencesCard(
    private val activity: Activity,
    parent: LinearLayout,
    private val visual: SettingsVisualStyle,
    private val onApplied: () -> Unit,
) {
    private val preferences = (activity.application as MobileApplication).fontSizePreferences
    private var dialog: AlertDialog? = null
    init {
        val style = visual.palette
        val button = visual.action(Button(activity).apply {
            text = activity.getString(preferences.current.label)
            tag = "product/settings/app/font-size/current"
            contentDescription = activity.getString(R.string.font_size_title) + ": " + text
            setOnClickListener { choose() }
        }, icon = "chevron", atEnd = true)
        style.add(parent, visual.pair(style.text(activity.getString(R.string.font_size_title)), button,
            "product/settings/app/font-size/row"))
    }
    private fun choose() {
        close()
        var selected = preferences.current
        val style = visual.palette
        val content = style.column().apply { setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8)) }
        val choices = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            tag = "product/settings/app/font-size/choices"
        }
        val preview = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            tag = "product/settings/app/font-size/preview"
            setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
            background = style.shape(style.surface, outline = true)
        }
        fun renderPreview() {
            preview.removeAllViews()
            val configuration = Configuration(activity.resources.configuration).apply { fontScale = selected.scale }
            val context = android.view.ContextThemeWrapper(activity.createConfigurationContext(configuration), R.style.MobileTheme)
            val sample = TaskProductVisualStyle(context)
            sample.add(preview, sample.text(activity.getString(R.string.font_size_preview), TaskProductVisualStyle.title))
            sample.add(preview, sample.text(activity.getString(R.string.font_size_preview_detail), TaskProductVisualStyle.body), 8)
            sample.add(preview, sample.text("36.0 g · 28 s", TaskProductVisualStyle.auxiliary, secondary = true), 8)
        }
        AppFontSize.entries.forEach { size ->
            choices.addView(RadioButton(activity).apply {
                id = View.generateViewId()
                text = activity.getString(size.label)
                style.apply(this, TaskProductVisualStyle.body)
                minimumHeight = style.dp(48)
                isChecked = size == selected
                setOnCheckedChangeListener { _, checked -> if (checked) { selected = size; renderPreview() } }
            }, RadioGroup.LayoutParams(-1, -2))
        }
        style.add(content, choices)
        style.add(content, preview, 12)
        val error = style.text("", TaskProductVisualStyle.auxiliary).apply {
            setTextColor(activity.getColor(R.color.mobile_danger)); visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        style.add(content, error, 8)
        renderPreview()
        val viewport = SettingsDialogViewport(activity).apply { addView(content) }
        dialog = AlertDialog.Builder(activity).setTitle(R.string.font_size_title)
            .setView(viewport).setNegativeButton(R.string.font_size_cancel, null)
            .setPositiveButton(R.string.font_size_apply, null).create().also { value ->
                value.setOnShowListener {
                    AppFontSizeDialogBounds.bind(activity, value)
                    visual.dialog(value, viewport)
                    // A quiet Cancel and one primary Apply, aligned and separated even in freeform.
                    listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE).forEach { which ->
                        val action = value.getButton(which)
                        style.button(action, primary = which == AlertDialog.BUTTON_POSITIVE)
                        action.minHeight = style.dp(48); action.minimumHeight = style.dp(48)
                        action.minWidth = style.dp(88); action.minimumWidth = style.dp(88)
                        action.setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8))
                        (action.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { params ->
                            // Native AlertDialog button bars own their vertical spacing; child
                            // margins can exceed an OEM fixed-height bar and clip the button.
                            params.topMargin = 0; params.bottomMargin = 0
                            params.marginStart = if (which == AlertDialog.BUTTON_POSITIVE) style.dp(12) else 0
                            action.layoutParams = params
                        }
                    }
                    value.getButton(AlertDialog.BUTTON_POSITIVE).tag = "product/settings/app/font-size/apply"
                    value.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        when (preferences.select(selected)) {
                            AppFontSizePreference.Selection.APPLIED -> { value.dismiss(); onApplied() }
                            AppFontSizePreference.Selection.UNCHANGED -> value.dismiss()
                            AppFontSizePreference.Selection.SAVE_FAILED -> {
                                error.setText(R.string.font_size_save_failed); error.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                value.show()
            }
    }
    fun close() { dialog?.dismiss(); dialog = null }
}

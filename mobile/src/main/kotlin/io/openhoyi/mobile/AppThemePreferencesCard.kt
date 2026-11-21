package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

/** Both settings surfaces use the same storage, appearance controls and preview. */
internal class AppThemePreferencesCard(
    private val activity: ThemedActivity,
    parent: LinearLayout,
    private val visual: SettingsVisualStyle,
    private val inlineSwatches: Boolean = false,
    private val onApplied: () -> Unit,
) {
    private val preferences = (activity.application as MobileApplication).appearancePreferences
    private val style = visual.palette
    private var restoringToggle = false
    internal var writeDark: (Boolean) -> Boolean = { preferences.selectDark(it) != AppAppearancePreference.Selection.SAVE_FAILED }
    internal var writePalette: (AppThemePalette) -> AppAppearancePreference.Selection = preferences::selectPalette
    internal var dialog: AlertDialog? = null
        private set
    val themeCheckBox = HoyiSwitch(activity).apply {
        text = activity.getString(R.string.app_settings_dark)
        isChecked = preferences.current.dark
        tag = "product/settings/app/theme/toggle"
        visual.switch(this, 52)
    }
    val themeError = errorLabel("product/settings/app/theme/error")

    init {
        if (inlineSwatches) {
            val modes = style.row().apply {
                tag = "settings/appearance/modes"
                val pad = style.dp(3)
                setPadding(pad, pad, pad, pad)
                background = style.shape(style.surface, outline = true, radius = 10)
            }
            for ((dark, name) in listOf(false to R.string.theme_palette_light_preview, true to R.string.theme_palette_dark_preview)) {
                val selected = preferences.current.dark == dark
                modes.addView(settingsPreviewButton(activity, style, activity.getString(name), selected).apply {
                    minimumWidth = style.dp(64)
                    minWidth = minimumWidth
                    tag = "settings/appearance/mode/" + if (dark) "dark" else "light"
                    if (selected) {
                        background = style.shape(style.accent, radius = 8)
                        setTextColor(style.onAccent)
                    }
                    setOnClickListener {
                        // Own no second preference: invoke the exact narrow switch validation/save path.
                        if (themeCheckBox.isChecked != dark) themeCheckBox.isChecked = dark
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (!dark) marginEnd = style.dp(4)
                })
            }
            val modePanel = SettingsAppearanceSurface(activity, style,
                activity.getString(R.string.settings_appearance_mode), modes, minWideDp = 460,
                maxContentDp = 192).apply {
                tag = "settings/appearance/mode-surface"
                // The wrapper must remain measurable to re-enable the mode on resize.
                minimumHeight = 0
            }
            themeCheckBox.visibility = View.GONE // Original validation remains the owner of the mode change.
            style.add(parent, themeCheckBox)
            style.add(parent, modePanel)

            val choices = SettingsPreviewOptions(activity, AppThemePalette.entries.size, style).apply {
                tag = "settings/appearance/palette-swatches"
            }
            for (palette in AppThemePalette.entries) {
                val selected = preferences.current.palette == palette
                val name = activity.getString(palette.label)
                choices.addView(settingsPreviewButton(activity, style, name, selected).apply {
                    tag = if (selected) "product/settings/app/palette/current" else "settings/appearance/palette/${palette.key}"
                    if (selected) contentDescription = activity.getString(R.string.theme_palette_current, name)
                    setCompoundDrawablesRelative(GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(AppPalette(activity, palette).accent)
                        val side = style.dp(16)
                        setBounds(0, 0, side, side)
                    }, null, null, null)
                    compoundDrawablePadding = style.dp(4)
                    setOnClickListener { choosePalette(palette) }
                })
            }
            style.add(parent, SettingsAppearanceSurface(activity, style,
                activity.getString(R.string.theme_palette_title), choices), 8)
        } else {
            style.add(parent, themeCheckBox)
            val paletteName = activity.getString(preferences.current.palette.label)
            val choose = visual.action(Button(activity).apply {
                text = paletteName
                contentDescription = activity.getString(R.string.theme_palette_current, paletteName)
                tag = "product/settings/app/palette/current"
                setOnClickListener { choosePalette() }
            }, icon = "chevron", atEnd = true)
            style.add(parent, visual.pair(style.text(activity.getString(R.string.theme_palette_title)), choose,
                "product/settings/app/palette/row"), 8)
        }
        if (!inlineSwatches) style.add(parent, style.text(activity.getString(R.string.app_settings_subtitle),
            TaskProductVisualStyle.auxiliary, secondary = true), 8)
        style.add(parent, themeError, 8)
        themeCheckBox.setOnCheckedChangeListener { button, dark ->
            val previous = preferences.current.dark
            if (restoringToggle || dark == previous) return@setOnCheckedChangeListener
            if (runCatching { writeDark(dark) }.getOrDefault(false)) {
                themeError.visibility = View.GONE
                onApplied()
            } else {
                restoringToggle = true
                button.isChecked = previous
                restoringToggle = false
                showError(themeError, R.string.app_settings_theme_failed)
            }
        }
    }

    private fun errorLabel(id: String): TextView = style.text("", TaskProductVisualStyle.auxiliary, id).apply {
        setTextColor(activity.getColor(R.color.mobile_danger))
        visibility = View.GONE
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun showError(view: TextView, message: Int) {
        view.setText(message)
        view.visibility = View.VISIBLE
        view.post { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), false) }
    }

    private fun choosePalette(initial: AppThemePalette? = null) {
        close()
        val applied = preferences.current
        var selected = initial ?: applied.palette
        val content = style.column().apply { setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8)) }
        val choices = RadioGroup(activity).apply {
            orientation = RadioGroup.VERTICAL
            tag = "product/settings/app/palette/choices"
        }
        val ids = AppThemePalette.entries.associateBy { View.generateViewId() }
        for ((id, palette) in ids) choices.addView(RadioButton(activity).apply {
            this.id = id
            text = activity.getString(palette.label)
            tag = "product/settings/app/palette/${palette.key}"
            style.apply(this, TaskProductVisualStyle.body)
            setPaddingRelative(style.dp(4), style.dp(4), style.dp(8), style.dp(4))
            minHeight = style.dp(48); minimumHeight = minHeight
            compoundDrawablePadding = style.dp(8)
            val swatch = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AppPalette(activity, palette).accent)
                setBounds(0, 0, style.dp(20), style.dp(20))
            }
            setCompoundDrawablesRelative(null, null, swatch, null)
        }, RadioGroup.LayoutParams(-1, -2))
        choices.check(ids.entries.single { it.value == selected }.key)
        val sampleColumn = style.column()
        val previewTitle = style.text("", TaskProductVisualStyle.action)
        style.add(sampleColumn, previewTitle, 8)
        val preview = style.column("product/settings/app/palette/preview").apply {
            // A noninteractive sample. TalkBack can read its named preview and the radio choice above.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        style.add(sampleColumn, preview, 4)
        style.add(content, SettingsColumns(activity, style, choices, sampleColumn, org.json.JSONObject(),
            firstFraction = .48f, minimumFirstWidth = { style.dp(172) }, minimumSecondWidth = { style.dp(166) }))
        val error = errorLabel("product/settings/app/palette/error")
        style.add(content, error, 8)
        fun render() {
            previewTitle.text = activity.getString(R.string.theme_palette_preview,
                activity.getString(if (applied.dark) R.string.theme_palette_dark_preview else R.string.theme_palette_light_preview))
            renderPreview(preview, applied.copy(palette = selected))
        }
        choices.setOnCheckedChangeListener { _, id ->
            selected = ids[id] ?: return@setOnCheckedChangeListener
            error.visibility = View.GONE
            render()
        }
        render()

        val viewport = SettingsDialogViewport(activity).apply { addView(content) }
        val picker = AlertDialog.Builder(activity).setTitle(R.string.theme_palette_title)
            .setView(viewport).setNegativeButton(R.string.theme_palette_cancel, null)
            .setPositiveButton(R.string.theme_palette_apply, null).create()
        dialog = picker
        picker.setOnShowListener {
            AppFontSizeDialogBounds.bind(activity, picker) { if (dialog === picker) dialog = null }
            visual.dialog(picker, viewport)
            for (which in listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE)) {
                picker.getButton(which).apply {
                    minHeight = style.dp(48); minimumHeight = minHeight
                    setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
                }
            }
            picker.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                tag = "product/settings/app/palette/apply"
                setOnClickListener {
                    when (writePalette(selected)) {
                        AppAppearancePreference.Selection.UNCHANGED -> picker.dismiss()
                        AppAppearancePreference.Selection.APPLIED -> { picker.dismiss(); onApplied() }
                        AppAppearancePreference.Selection.SAVE_FAILED -> showError(error, R.string.theme_palette_save_failed)
                    }
                }
            }
        }
        picker.show()
    }

    private fun renderPreview(preview: LinearLayout, appearance: AppAppearance) {
        // Clone the theme before overriding the native accent. Never mutate the owner's theme.
        val sampleTheme: Resources.Theme = activity.resources.newTheme().apply {
            setTo(activity.theme)
            applyStyle(appearance.palette.nativeOverlay, true)
        }
        val context = ContextThemeWrapper(AppAppearanceContext(activity, appearance), sampleTheme)
        val sample = TaskProductVisualStyle(context)
        val nav = TaskShellVisualStyle(context)
        preview.removeAllViews()
        preview.setPadding(sample.dp(8), sample.dp(8), sample.dp(8), sample.dp(8))
        preview.background = sample.shape(sample.page, outline = true)
        val selectedTab = sample.row("product/settings/app/palette/preview/nav").apply {
            background = nav.navigationBackground(true)
            minimumHeight = sample.dp(48)
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(sample.dp(12), 0, sample.dp(8), 0)
            addView(HomeVisualIcon(context, "curve", nav.selectedInk), LinearLayout.LayoutParams(sample.dp(20), sample.dp(20)))
            addView(sample.text(activity.getString(R.string.ui_curves), TaskProductVisualStyle.action).apply {
                setTextColor(nav.selectedInk)
            }, LinearLayout.LayoutParams(-1, -2).apply { marginStart = sample.dp(8) })
        }
        sample.add(preview, selectedTab)
        sample.add(preview, sample.button(Button(context), primary = true).apply {
            tag = "product/settings/app/palette/preview/action"
            text = activity.getString(R.string.theme_palette_preview_action)
            isClickable = false; isFocusable = false
        }, 4)
        sample.add(preview, HoyiSwitch(context, interactive = false).apply {
            tag = "product/settings/app/palette/preview/switch"
            sample.apply(this, TaskProductVisualStyle.body)
            text = activity.getString(R.string.theme_palette_preview_switch)
            isChecked = true
            isClickable = false; isFocusable = false
            minimumHeight = sample.dp(48); minHeight = minimumHeight
        })
    }

    fun close() { dialog?.dismiss(); dialog = null }
}

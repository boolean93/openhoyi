package io.openhoyi.mobile

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider

private const val BACKGROUND_CHOOSE_PHOTO = "io.openhoyi.mobile.background.CHOOSE_PHOTO"
private const val BACKGROUND_PREVIEW_PRESET = "io.openhoyi.mobile.background.PREVIEW_PRESET"

internal fun addBackgroundSettingsRow(activity: ThemedActivity, parent: LinearLayout, visual: SettingsVisualStyle,
    inlinePresets: Boolean = false) {
    val style = visual.palette
    val button = visual.action(Button(activity).apply {
        text = activity.getString(R.string.background_customize)
        tag = "product/settings/background/open"
        setOnClickListener { activity.startActivity(Intent(activity, BackgroundSettingsActivity::class.java)) }
    }, icon = "chevron", atEnd = true)
    val compactRow = visual.pair(style.text(activity.getString(R.string.background_title)), button,
        "product/settings/background/row")
    style.add(parent, compactRow, 8)
    if (!inlinePresets) return

    val store = (activity.application as MobileApplication).pageBackgrounds
    val applied = store.current.config
    val options = SettingsPreviewOptions(activity, 3, style).apply { tag = "settings/appearance/background-presets" }
    val tiles = linkedMapOf<AppBackground, SettingsBackgroundPreviewButton>()
    for ((label, preset) in listOf(R.string.background_solid to AppBackground.Solid,
        R.string.background_mist to AppBackground.Mist, R.string.background_slate to AppBackground.Slate)) {
        val selected = applied.choice == preset
        val realPreset = PageBackdrop(style.page, HoyiUi.dark(activity)).apply {
            snapshot = BackgroundSnapshot(AppBackgroundConfig(preset, AppBackgroundConfig.MAX_STRENGTH))
            protectedAreas = emptyList()
        }
        val border = style.shape(Color.TRANSPARENT, outline = true, radius = 0)
        val preview = LayerDrawable(arrayOf(realPreset, border))
        val tile = SettingsBackgroundPreviewButton(activity, style, preview)
        tiles[preset] = tile
        settingsPreviewButton(activity, style, activity.getString(label), selected, tile).apply {
            tag = "settings/appearance/background/${preset.key}"
            compoundDrawablePadding = style.dp(5)
            setOnClickListener {
                activity.startActivity(Intent(activity, BackgroundSettingsActivity::class.java)
                    .putExtra(BACKGROUND_PREVIEW_PRESET, preset.key))
            }
        }
        options.addView(tile)
    }
    val photo = settingsPreviewButton(activity, style, activity.getString(R.string.background_choose_photo),
        applied.choice is AppBackground.Photo).apply {
        setOnClickListener {
            // Re-open the current photo editor to keep mask/apply/cancel easy to reach.
            val hasPhoto = store.current.config.choice is AppBackground.Photo
            activity.startActivity(Intent(activity, BackgroundSettingsActivity::class.java)
                .putExtra(BACKGROUND_CHOOSE_PHOTO, !hasPhoto))
        }
    }
    options.addView(photo)
    fun updateSelection(choice: AppBackground) {
        tiles.forEach { (preset, tile) ->
            val selected = choice == preset
            if (tile.isSelected != selected) tile.settingsPreviewSelection(style, selected)
        }
        val selected = choice is AppBackground.Photo
        if (photo.isSelected != selected) photo.settingsPreviewSelection(style, selected)
        val label = activity.getString(if (selected) R.string.background_photo else R.string.background_choose_photo)
        if (photo.text.toString() != label) photo.text = label
    }
    updateSelection(applied.choice)
    val panel = SettingsAppearanceSurface(activity, style, activity.getString(R.string.background_title),
        options, narrowLabel = false) { wide ->
        compactRow.visibility = if (wide) View.GONE else View.VISIBLE
        photo.visibility = if (wide) View.VISIBLE else View.GONE
        button.tag = if (wide) null else "product/settings/background/open"
        photo.tag = if (wide) "product/settings/background/open" else null
        tiles.values.forEach { it.large = wide }
    }
    var releaseObservation: (() -> Unit)? = null
    panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            releaseObservation?.invoke()
            releaseObservation = store.observe { updateSelection(it.config.choice) }
        }
        override fun onViewDetachedFromWindow(v: View) {
            releaseObservation?.invoke()
            releaseObservation = null
        }
    })
    style.add(parent, panel, 8)
}

private class SettingsBackgroundPreviewButton(context: android.content.Context,
    private val style: TaskProductVisualStyle, private val preview: LayerDrawable) : Button(context) {
    var large = false
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(1)
        val w = minOf(available, style.dp(if (large) 180 else 64))
        val h = if (large) (w * .55f).toInt().coerceIn(1, style.dp(96)) else style.dp(32)
        if (preview.bounds.width() != w || preview.bounds.height() != h) {
            preview.setBounds(0, 0, w, h)
            setCompoundDrawablesRelative(null, preview, null, null)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

class BackgroundSettingsActivity : ThemedActivity() {
    internal lateinit var editor: BackgroundEditorModel
        private set
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> editor.pickerResult(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = ViewModelProvider(this)[BackgroundEditorModel::class.java]
        val style = TaskProductVisualStyle(this)
        val root = style.column("product/background/root").apply {
            setBackgroundColor(style.page)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        window.statusBarColor = style.page
        window.navigationBarColor = style.surface
        val body = style.column("product/background/body").apply {
            setPadding(style.dp(16), style.dp(12), style.dp(16), style.dp(12))
        }
        val scroll = ScrollView(this).apply {
            id = R.id.page_background_editor_scroll
            tag = "product/background/scroll"
            addView(body)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        style.add(body, style.text(getString(R.string.background_title), TaskProductVisualStyle.title).apply {
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
        style.add(body, style.text(getString(R.string.background_hint), TaskProductVisualStyle.auxiliary, secondary = true), 6)

        val sample = style.column("product/background/preview").apply {
            setBackgroundColor(style.page)
            setPadding(style.dp(20), style.dp(16), style.dp(20), style.dp(16))
            minimumHeight = style.dp(162)
            gravity = Gravity.CENTER_VERTICAL
        }
        val sampleCard = style.column("product/background/preview/card").apply {
            background = style.shape(style.surface, outline = true)
            setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
            protectBackdropContent()
        }
        style.add(sampleCard, style.text(getString(R.string.background_preview), TaskProductVisualStyle.section))
        style.add(sampleCard, style.text(getString(R.string.background_preview_detail), TaskProductVisualStyle.auxiliary, secondary = true))
        style.add(sample, sampleCard)
        style.add(body, sample, 12)
        val backdrop = PageBackdropBinding(sample, style.page, HoyiUi.dark(this), preview = editor::currentPreview)

        val choices = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            tag = "product/background/choices"
        }
        var rendering = false
        var photo: AppBackground.Photo? = null
        data class Option(val label: Int, val key: String, val choice: () -> AppBackground?)
        val options = listOf(
            Option(R.string.background_solid, "solid") { AppBackground.Solid },
            Option(R.string.background_mist, "mist") { AppBackground.Mist },
            Option(R.string.background_slate, "slate") { AppBackground.Slate },
            Option(R.string.background_photo, "photo") { photo },
        ).map { option ->
            val control = RadioButton(this).apply {
                id = View.generateViewId()
                tag = "product/background/choice/${option.key}"
                text = getString(option.label)
                style.apply(this, TaskProductVisualStyle.body)
                minHeight = style.dp(48)
                minimumHeight = minHeight
                setPaddingRelative(style.dp(4), style.dp(4), style.dp(8), style.dp(4))
            }
            choices.addView(control, RadioGroup.LayoutParams(-1, -2))
            option to control
        }
        choices.setOnCheckedChangeListener { _, id ->
            if (!rendering) options.firstOrNull { it.second.id == id }?.first?.choice()?.let(editor::choose)
        }
        style.add(body, choices, 8)
        val import = style.action(body, getString(R.string.background_choose_photo), "product/background/import", outline = true) {
            if (editor.beginPicker()) {
                try { picker.launch("image/*") }
                catch (_: android.content.ActivityNotFoundException) { editor.pickerFailedToLaunch() }
            }
        }
        val mask = style.text("", TaskProductVisualStyle.body, "product/background/mask/label")
        style.add(body, mask, 12)
        val slider = SeekBar(this).apply {
            tag = "product/background/mask"
            min = 100 - AppBackgroundConfig.MAX_STRENGTH
            max = 100 - AppBackgroundConfig.MIN_STRENGTH
            minimumHeight = style.dp(48)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser && !rendering) editor.strength(100 - progress)
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        style.add(body, slider)
        val status = style.text("", TaskProductVisualStyle.auxiliary, "product/background/status").apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        style.add(body, status, 4)
        val reset = style.action(body, getString(R.string.background_restore), "product/background/restore") {
            editor.choose(AppBackground.Solid)
        }

        val actions = style.column("product/background/actions").apply {
            setBackgroundColor(style.surface)
            setPadding(style.dp(16), style.dp(4), style.dp(16), style.dp(8))
        }
        val cancel = style.button(Button(this)).apply {
            text = getString(R.string.background_cancel); tag = "product/background/cancel"
            setOnClickListener { if (editor.cancel()) finish() }
        }
        val apply = style.button(Button(this), primary = true).apply {
            text = getString(R.string.background_apply); tag = "product/background/apply"
            setOnClickListener { editor.apply() }
        }
        val row = style.row().apply {
            addView(cancel, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = style.dp(8) })
            addView(apply, LinearLayout.LayoutParams(0, -2, 1f))
        }
        style.add(actions, row)
        style.add(root, actions)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (editor.cancel()) finish() }
        })
        setContentView(root)
        editor.bind((application as MobileApplication).pageBackgrounds)
        if (savedInstanceState == null) {
            // Only built-in presets may be preselected; no file access or pointer commit here.
            when (intent.getStringExtra(BACKGROUND_PREVIEW_PRESET)) {
                AppBackground.Solid.key -> editor.choose(AppBackground.Solid)
                AppBackground.Mist.key -> editor.choose(AppBackground.Mist)
                AppBackground.Slate.key -> editor.choose(AppBackground.Slate)
            }
        }
        editor.render = { state ->
            if (state.done) finish() else {
                rendering = true
                photo = state.photo
                for ((option, control) in options) {
                    val value = option.choice()
                    control.visibility = if (value == null) View.GONE else View.VISIBLE
                    control.isEnabled = !state.applying
                }
                val selected = options.firstOrNull { it.first.choice() == state.preview.config.choice }
                if (selected != null) choices.check(selected.second.id) else choices.clearCheck()
                val maskLevel = 100 - state.preview.config.strength
                mask.text = getString(R.string.background_mask, maskLevel)
                slider.contentDescription = mask.text
                slider.progress = maskLevel
                slider.isEnabled = !state.applying && state.preview.config.choice != AppBackground.Solid
                mask.visibility = if (state.preview.config.choice == AppBackground.Solid) View.GONE else View.VISIBLE
                slider.visibility = mask.visibility
                val message = state.error ?: when {
                    state.preview.unavailable -> R.string.background_photo_unavailable
                    state.applying -> R.string.background_saving
                    state.busy -> R.string.background_preparing
                    else -> null
                }
                status.visibility = if (message == null) View.GONE else View.VISIBLE
                status.text = message?.let(::getString).orEmpty()
                status.setTextColor(if (state.error != null) getColor(R.color.mobile_danger) else style.muted)
                import.isEnabled = !state.applying
                reset.isEnabled = !state.applying && state.preview.config.choice != AppBackground.Solid
                cancel.isEnabled = !state.applying
                apply.isEnabled = !state.busy && !state.applying && state.canApply
                backdrop.show(state.preview)
                rendering = false
            }
        }
        if (savedInstanceState == null && intent.getBooleanExtra(BACKGROUND_CHOOSE_PHOTO, false)) {
            // Explicit user action launches the same editor picker once, without applying anything.
            import.post { if (!isFinishing && !isDestroyed) import.performClick() }
        }
    }

    override fun onDestroy() {
        editor.render = null
        super.onDestroy()
    }
}

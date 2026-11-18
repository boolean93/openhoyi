package io.openhoyi.mobile

import android.content.Intent
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

internal fun addBackgroundSettingsRow(activity: ThemedActivity, parent: LinearLayout, visual: SettingsVisualStyle) {
    val style = visual.palette
    val button = visual.action(Button(activity).apply {
        text = activity.getString(R.string.background_customize)
        tag = "product/settings/background/open"
        setOnClickListener { activity.startActivity(Intent(activity, BackgroundSettingsActivity::class.java)) }
    }, icon = "chevron", atEnd = true)
    style.add(parent, visual.pair(style.text(activity.getString(R.string.background_title)), button,
        "product/settings/background/row"), 8)
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
    }

    override fun onDestroy() {
        editor.render = null
        super.onDestroy()
    }
}

package io.openhoyi.mobile

import android.app.Activity
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import android.widget.TextView
import android.view.View
import kotlin.random.Random

/** App-local controls stay usable while disconnected and never enter machine controlButtons. */
internal class BrewFeedbackPreferencesCard(private val activity: Activity, parent: LinearLayout, private val visual: SettingsVisualStyle? = null, private val grouped: Boolean = false) {
    private val prefs = (activity.application as MobileApplication).feedbackPreferences
    private val audioOwner = lazy { BrewFeedbackAudio(AndroidBrewFeedbackAudio(activity.applicationContext)) }
    private val audio by audioOwner
    private fun stopAudio() { if (audioOwner.isInitialized()) audio.stop() }
    private var updating = false
    val enabledSwitch: Switch
    private val preview: android.widget.Button
    internal var errorStatus: TextView? = null
        private set
    init {
        val card = if (grouped) parent else settingsGroup(activity, parent, activity.getString(R.string.feedback_enabled), visual)
        enabledSwitch = HoyiSwitch(activity).apply {
            text = activity.getString(if (grouped) R.string.settings_hig_feedback_complete else R.string.settings_enable_feedback)
            textSize = TaskVisualTokens.BODY_SP.toFloat()
            typeface = TaskTypography.face(activity, 400)
            setTextColor(activity.getColor(R.color.mobile_text))
            minHeight = HoyiUi.dp(activity, 56)
            isChecked = prefs.enabled
            card.addView(this, LinearLayout.LayoutParams(-1, -2))
            setOnCheckedChangeListener { _, checked ->
                if (!updating) {
                    if (!prefs.setEnabled(checked)) {
                        refresh()
                        if (visual==null) runCatching { Toast.makeText(activity, R.string.feedback_save_failed, Toast.LENGTH_SHORT).show() }
                        else errorStatus?.apply {text=activity.getString(R.string.feedback_save_failed);visibility=View.VISIBLE}
                    } else {
                        clearError()
                        if (!checked) stopAudio()
                    }
                }
            }
        }
        visual?.let {it.switch(enabledSwitch);enabledSwitch.tag="product/settings/app/feedback/toggle"}
        if (!grouped) HoyiUi.label(activity, card, activity.getString(R.string.feedback_enabled_description), 14, muted = true).also {label->
            visual?.palette?.apply {apply(label,TaskProductVisualStyle.auxiliary,true);label.setPadding(0,dp(8),0,0)}
        }
        errorStatus=visual?.palette?.let {style->
            style.text("",TaskProductVisualStyle.auxiliary,"product/settings/app/feedback/error").apply {
                setTextColor(activity.getColor(R.color.mobile_danger));visibility=View.GONE
                accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
            }.also {style.add(card,it,8)}
        }
        if (!grouped) settingsDivider(activity, card, visual)
        preview = HoyiUi.button(activity, card, activity.getString(R.string.feedback_preview)) {
            try {
                if (audio.playing) audio.stop() else audio.play(BrewFeedbackClips.Level.BRAVO, Random.nextInt(4))
            } catch (_: RuntimeException) { stopAudio() }
            refresh()
        }.apply {
            asTaskTextAction(); gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            visual?.action(this);visual?.let {tag="product/settings/app/feedback/preview"}
        }
        if (grouped && visual != null) {
            card.removeView(preview)
            preview.text = activity.getString(R.string.settings_category_preview)
            preview.contentDescription = activity.getString(R.string.feedback_preview)
            val style = visual.palette
            style.divider(card, top = 0, bottom = 0)
            visual.action(preview, outline = true, icon = "play")
            preview.minimumHeight = style.dp(52)
            preview.minHeight = style.dp(52)
            preview.setPadding(style.dp(10), 0, style.dp(10), 0)
            style.add(card, visual.pair(style.text(activity.getString(R.string.settings_hig_feedback_sound),
                TaskProductVisualStyle.action), preview, "settings/brewing/feedback-preview-row"))
        } else HoyiUi.label(activity, card, activity.getString(R.string.feedback_preview_description),
            TaskVisualTokens.SUPPORTING_SP, muted = true).also {label->
            visual?.palette?.apply {apply(label,TaskProductVisualStyle.auxiliary,true);label.setPadding(0,dp(8),0,0)}
        }
    }
    fun refresh() {
        updating = true
        try {
            enabledSwitch.isChecked = prefs.enabled
            val playing = audioOwner.isInitialized() && audio.playing
            preview.text = activity.getString(if (playing) R.string.feedback_stop_preview
                else if (grouped) R.string.settings_category_preview else R.string.feedback_preview)
            preview.contentDescription = activity.getString(if (playing) R.string.feedback_stop_preview else R.string.feedback_preview)
            if (grouped) HomeVisualIcon.buttonIcon(preview, if (playing) "pausePreview" else "play")
        } catch (_: RuntimeException) { /* Local card must not interrupt machine settings rendering. */ }
        finally { updating = false }
    }
    fun start() { prefs.observe(this) { enabled -> if (!enabled) stopAudio(); refresh() } }
    fun pausePreview() { stopAudio(); refresh() }
    private fun clearError() {errorStatus?.apply {text="";visibility=View.GONE}}
    fun stop() { prefs.unobserve(this); stopAudio();clearError() }
}

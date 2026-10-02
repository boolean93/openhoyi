package io.openhoyi.mobile

import android.app.Activity
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import kotlin.random.Random

/** App-local controls stay usable while disconnected and never enter machine controlButtons. */
internal class BrewFeedbackPreferencesCard(private val activity: Activity, parent: LinearLayout) {
    private val prefs = (activity.application as MobileApplication).feedbackPreferences
    private val audioOwner = lazy { BrewFeedbackAudio(AndroidBrewFeedbackAudio(activity.applicationContext)) }
    private val audio by audioOwner
    private fun stopAudio() { if (audioOwner.isInitialized()) audio.stop() }
    private var updating = false
    val enabledSwitch: Switch
    private val preview: android.widget.Button
    init {
        val card = HoyiUi.card(activity, parent, activity.getString(R.string.feedback_preferences_title))
        enabledSwitch = Switch(activity).apply {
            text = activity.getString(R.string.feedback_enabled)
            textSize = 17f
            setTextColor(activity.getColor(R.color.mobile_text))
            minHeight = HoyiUi.dp(activity, 56)
            isChecked = prefs.enabled
            card.addView(this, LinearLayout.LayoutParams(-1, -2))
            setOnCheckedChangeListener { _, checked ->
                if (!updating) {
                    if (!prefs.setEnabled(checked)) {
                        refresh()
                        runCatching { Toast.makeText(activity, R.string.feedback_save_failed, Toast.LENGTH_SHORT).show() }
                    } else if (!checked) stopAudio()
                }
            }
        }
        HoyiUi.label(activity, card, activity.getString(R.string.feedback_enabled_description), 14, muted = true)
        preview = HoyiUi.button(activity, card, activity.getString(R.string.feedback_preview)) {
            try {
                if (audio.playing) audio.stop() else audio.play(BrewFeedbackClips.Level.BRAVO, Random.nextInt(4))
            } catch (_: RuntimeException) { stopAudio() }
            refresh()
        }
        HoyiUi.label(activity, card, activity.getString(R.string.feedback_preview_description), 13, muted = true)
    }
    fun refresh() {
        updating = true
        try {
            enabledSwitch.isChecked = prefs.enabled
            preview.text = activity.getString(if (audioOwner.isInitialized() && audio.playing) R.string.feedback_stop_preview else R.string.feedback_preview)
        } catch (_: RuntimeException) { /* Local card must not interrupt machine settings rendering. */ }
        finally { updating = false }
    }
    fun start() { prefs.observe(this) { enabled -> if (!enabled) stopAudio(); refresh() } }
    fun stop() { prefs.unobserve(this); stopAudio() }
}

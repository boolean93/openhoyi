package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import kotlin.random.Random

/** One Activity's display/audio owner. Configuration restoration restores text, never playback. */
internal class BrewFeedbackDialog(private val activity: Activity, saved: Bundle?) {
    private val prefs = (activity.application as MobileApplication).feedbackPreferences
    private val audioOwner = lazy { BrewFeedbackAudio(AndroidBrewFeedbackAudio(activity.applicationContext)) }
    private val audio by audioOwner
    private fun stopAudio() { if (audioOwner.isInitialized()) audio.stop() }
    private var dialog: AlertDialog? = null
    private var shownId: String? = null
    private var variant = 0
    private var restoreId = saved?.getString(SHOT)
    private var restoreVariant = saved?.getInt(VARIANT, 0)?.coerceIn(0, 3) ?: 0
    val isShowing: Boolean get() = dialog?.isShowing == true

    fun update(owner: MobileService?, visible: Boolean) {
        try { updateDisplay(owner, visible) } catch (_: RuntimeException) { stop() }
    }
    private fun updateDisplay(owner: MobileService?, visible: Boolean) {
        if (!visible || activity.isFinishing || activity.isDestroyed) { stop(); return }
        if (owner == null) { stop(); return }
        val result = owner.brewFeedbackResult
        val snapshot = owner.snapshot
        val eligible = prefs.enabled && result != null && snapshot.coffeeState == DeviceState.READY &&
            snapshot.coffee is IdleTelemetry && snapshot.alarmBits == 0 &&
            owner.manualSafetyMessage == null && owner.machineWriteSafetyMessage == null
        if (!eligible) {
            stop(); restoreId = null; owner.discardBrewFeedback(); return
        }
        if (isShowing && shownId == result!!.shotId) return
        stop()
        val restored = restoreId == result!!.shotId
        restoreId = null
        val value = if (restored) result else owner.claimBrewFeedback() ?: return
        show(value, if (restored) restoreVariant else Random.nextInt(4), autoplay = !restored)
    }
    fun start() {
        prefs.observe(this) { enabled -> if (!enabled) { restoreId = null; stop() } }
    }
    fun leave(changingConfigurations: Boolean) {
        prefs.unobserve(this)
        if (changingConfigurations && isShowing) { restoreId = shownId; restoreVariant = variant }
        stop()
    }
    fun save(out: Bundle) {
        val id = if (isShowing) shownId else restoreId
        if (id != null) { out.putString(SHOT, id); out.putInt(VARIANT, if (isShowing) variant else restoreVariant) }
    }
    fun stop() {
        val previous = dialog
        dialog = null; shownId = null
        stopAudio()
        try { previous?.dismiss() } catch (_: RuntimeException) { /* Local window cleanup only. */ }
    }
    private fun show(result: BrewFeedbackResult, voice: Int, autoplay: Boolean) {
        variant = voice
        val message = activity.getString(when (result.level) {
            BrewFeedbackClips.Level.BRAVO -> R.string.feedback_bravo
            BrewFeedbackClips.Level.HIGH_FLOW -> R.string.feedback_high_flow
            BrewFeedbackClips.Level.LOW_FLOW -> R.string.feedback_low_flow
        }) + "\n\n" + activity.getString(R.string.feedback_note)
        val created = AlertDialog.Builder(activity).setTitle(R.string.feedback_title).setMessage(message)
            .setPositiveButton(R.string.feedback_done, null).setNeutralButton(R.string.feedback_replay, null).create()
        dialog = created; shownId = result.shotId
        created.setOnDismissListener {
            // A delayed dismissal from an old dialog cannot cancel a newer request.
            if (dialog === created) { dialog = null; shownId = null; stopAudio() }
        }
        created.setOnShowListener {
            created.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                variant = Random.nextInt(4)
                try { audio.play(result.level, variant) } catch (_: RuntimeException) { stopAudio() }
            }
        }
        try {
            created.show()
            if (autoplay) {
                if (BuildConfig.MOCK_MODE) Log.i("OpenHoyiFeedback", "dialog.autoplay:${result.shotId}")
                audio.play(result.level, variant)
            }
        } catch (_: RuntimeException) { stop() }
    }
    private companion object {
        const val SHOT = "localFeedback.shot"
        const val VARIANT = "localFeedback.voice"
    }
}

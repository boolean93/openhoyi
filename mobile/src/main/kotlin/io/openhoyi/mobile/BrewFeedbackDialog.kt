package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** One Activity's display/audio owner. Configuration restoration restores text, never playback. */
internal class BrewFeedbackDialog(private val activity: Activity, saved: Bundle?) {
    private val prefs = (activity.application as MobileApplication).feedbackPreferences
    private val audioOwner = lazy { BrewFeedbackAudio(AndroidBrewFeedbackAudio(activity.applicationContext)) }
    private val audio by audioOwner
    private fun stopAudio() { if (audioOwner.isInitialized()) audio.stop() }
    private val reviewAccess get() = (activity.application as MobileApplication).completionShotReviewAccess
    private var reviewObserver: Job? = null
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
        reviewObserver?.cancel(); reviewObserver = null
        stopAudio()
        try { previous?.dismiss() } catch (_: RuntimeException) { /* Local window cleanup only. */ }
    }
    private fun show(result: BrewFeedbackResult, voice: Int, autoplay: Boolean) {
        variant = voice
        val card = BrewCompletionDialog(activity, result.level) {
            val ticket = reviewAccess.issue(result) ?: return@BrewCompletionDialog
            stop()
            try { activity.startActivity(CompletionReviewChatActivity.intent(activity, ticket)) }
            catch (_: RuntimeException) { reviewAccess.abandon(ticket) }
        }
        val created = card.dialog
        var saveTimedOut = false
        fun refreshReview() {
            if (dialog !== created) return
            val hasKey = reviewAccess.hasConfiguredKey()
            val availability = reviewAccess.availability(result)
            card.refresh(hasKey, availability == CompletionShotReview.Availability.READY,
                availability == CompletionShotReview.Availability.SAVING && !saveTimedOut)
        }
        dialog = created; shownId = result.shotId
        created.setOnDismissListener {
            // A delayed dismissal from an old dialog cannot cancel a newer request.
            if (dialog === created) {
                dialog = null; shownId = null
                reviewObserver?.cancel(); reviewObserver = null
                stopAudio()
            }
        }
        try {
            card.show()
            refreshReview()
            reviewObserver = CoroutineScope(Dispatchers.Main.immediate).launch {
                launch { reviewAccess.changes.collect { refreshReview() } }
                delay(5_000)
                saveTimedOut = true
                refreshReview()
            }
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

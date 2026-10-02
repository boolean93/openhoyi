package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.Context
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Built-in Android instrumentation, no external test framework. Mock only, no BLE. */
class BrewAudioInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        try {
            check(BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            val home = startActivitySync(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
            waitForIdleSync()
            var driver: AndroidBrewFeedbackAudio? = null
            runOnMainSync { driver = AndroidBrewFeedbackAudio(targetContext) }
            val cancelledCallbacks = AtomicInteger()
            runOnMainSync {
                driver!!.start("brew-feedback/1.mp3") { cancelledCallbacks.incrementAndGet() }.cancel()
            }
            Thread.sleep(300)
            check(cancelledCallbacks.get() == 0) { "Cancelled prepare delivered a callback" }
            val clips = BrewFeedbackClips.Level.entries.flatMap { level ->
                (0..3).flatMap { BrewFeedbackClips.sequence(level, it) }
            }.toSet()
            for(path in clips) {
                val latch = CountDownLatch(1)
                val result = AtomicReference<BrewFeedbackAudio.Result>()
                val callbacks = AtomicInteger()
                var cancel: BrewFeedbackAudio.Cancel? = null
                runOnMainSync {
                    cancel = driver!!.start(path) { result.set(it); callbacks.incrementAndGet(); latch.countDown() }
                }
                val completed = latch.await(20, TimeUnit.SECONDS)
                runOnMainSync { cancel?.cancel() }
                check(completed && callbacks.get() == 1 && result.get() == BrewFeedbackAudio.Result.COMPLETED) {
                    "$path decode/playback: completed=$completed callbacks=${callbacks.get()} result=${result.get()}"
                }
            }
            val latch = CountDownLatch(1)
            val results = mutableListOf<BrewFeedbackAudio.Result>()
            var audio: BrewFeedbackAudio? = null
            runOnMainSync {
                audio = BrewFeedbackAudio(object : BrewFeedbackAudio.Driver {
                    override fun start(path: String, done: (BrewFeedbackAudio.Result) -> Unit) =
                        driver!!.start(path) { result ->
                            results.add(result); done(result)
                            if (audio?.playing == false) latch.countDown()
                        }
                })
                audio!!.play(BrewFeedbackClips.Level.BRAVO, 0)
            }
            val finished = latch.await(30, TimeUnit.SECONDS)
            runOnMainSync { audio?.stop() }
            check(finished && results == listOf(BrewFeedbackAudio.Result.COMPLETED, BrewFeedbackAudio.Result.COMPLETED))
            verifyFeedbackService(home)
            report.putString("stream", "LOCAL_AUDIO_CHECKS_PASSED clips=${clips.size} cancelledPrepare=true sequence=true\n" +
                "LOCAL_FEEDBACK_SERVICE_CHECKS_PASSED observedEnd=true telemetryPhase=8 clearedOnNextCup=true earlyStopSuppressed=true\n" +
                "LOCAL_FEEDBACK_UI_CHECKS_PASSED switchPersisted=true recreatedWithoutReplay=true newCupDismissed=true\n")
            finish(Activity.RESULT_OK, report)
        } catch (error: Throwable) {
            report.putString("stream", "LOCAL_AUDIO_CHECKS_FAILED ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, report)
        }
    }
    /** Calls only the guarded Mock service's real product start/stop APIs. */
    private fun verifyFeedbackService(initialHome: HomeActivity) {
        var home = initialHome
        check(BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
        val connected = CountDownLatch(1)
        val owner = AtomicReference<MobileService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                owner.set((binder as MobileService.LocalBinder).service); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) { owner.set(null) }
        }
        var bound = false
        val prefs = targetContext.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val previous = prefs.getString("selected", null)
        val feedbackPrefs = (targetContext.applicationContext as MobileApplication).feedbackPreferences
        val previouslyEnabled = feedbackPrefs.enabled
        fun autoplayCount(): Int = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            uiAutomation.executeShellCommand("logcat -d -v brief -s OpenHoyiFeedback:I '*:S'"))
            .bufferedReader().use { it.readLines().count { line -> line.contains("dialog.autoplay:") } }
        fun awaitMain(timeoutMs: Long, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (true) {
                var success = false
                runOnMainSync { success = condition() }
                if (success) return
                check(SystemClock.elapsedRealtime() < deadline) { "Mock feedback condition timed out" }
                Thread.sleep(100)
            }
        }
        try {
            runOnMainSync { check(feedbackPrefs.setEnabled(false)) }
            val settings = startActivitySync(Intent(targetContext, MachineSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MachineSettingsActivity
            waitForIdleSync()
            runOnMainSync {
                check(!settings.feedbackCard.enabledSwitch.isChecked)
                settings.feedbackCard.enabledSwitch.performClick()
                check(feedbackPrefs.enabled)
                check(targetContext.getSharedPreferences("brew_feedback", Context.MODE_PRIVATE).getBoolean("enabled", false))
                settings.finish()
            }
            waitForIdleSync()
            runOnMainSync {
                bound = targetContext.bindService(Intent(targetContext, MobileService::class.java),
                    connection, Context.BIND_AUTO_CREATE)
            }
            check(bound && connected.await(10, TimeUnit.SECONDS))
            check(prefs.edit().putString("selected", "capture-2").commit())
            awaitMain(5000) { owner.get()?.machineSettingsFresh == true }
            runOnMainSync { check(owner.get()!!.startShot("capture-2", null, 7) == null) }
            awaitMain(5000) { (owner.get()?.snapshot?.coffee as? ExtractionTelemetry)?.slotOrPhase == 8 }
            awaitMain(40000) {
                owner.get()?.shotState == ExtractionState.ENDED_OBSERVED && owner.get()?.brewFeedbackResult != null
            }
            awaitMain(5000) { home.feedbackUi.isShowing }
            val beforeRecreation = autoplayCount()
            check(beforeRecreation > 0)
            val monitor = addMonitor(HomeActivity::class.java.name, null, false)
            runOnMainSync { home.recreate() }
            home = waitForMonitorWithTimeout(monitor, 10000) as? HomeActivity
                ?: error("Home recreation not observed")
            removeMonitor(monitor)
            awaitMain(5000) { home.feedbackUi.isShowing }
            check(autoplayCount() == beforeRecreation) { "Recreation replayed feedback" }
            runOnMainSync {
                val result = requireNotNull(owner.get()!!.brewFeedbackResult)
                check(result.machineSeconds > 14 && result.earlyWaterTenthsMl <= 60)
                check(result.level == BrewFeedbackClips.Level.LOW_FLOW)
                check(owner.get()!!.startShot("capture-2", null, 7) == null)
                check(owner.get()!!.brewFeedbackResult == null)
            }
            awaitMain(5000) { owner.get()?.snapshot?.coffee is ExtractionTelemetry && !home.feedbackUi.isShowing }
            Thread.sleep(250)
            runOnMainSync { owner.get()!!.stopShot() }
            awaitMain(5000) { owner.get()?.shotState == ExtractionState.ENDED_OBSERVED &&
                owner.get()?.snapshot?.coffee !is ExtractionTelemetry }
            runOnMainSync { check(owner.get()!!.brewFeedbackResult == null) }
        } finally {
            runOnMainSync {
                owner.get()?.stopShot()
                check(feedbackPrefs.setEnabled(previouslyEnabled))
                if (bound) targetContext.unbindService(connection)
            }
            val edit = prefs.edit()
            if (previous == null) edit.remove("selected") else edit.putString("selected", previous)
            check(edit.commit())
        }
    }

}

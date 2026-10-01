package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
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
            startActivitySync(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
            report.putString("stream", "LOCAL_AUDIO_CHECKS_PASSED clips=${clips.size} cancelledPrepare=true sequence=true\n")
            finish(Activity.RESULT_OK, report)
        } catch (error: Throwable) {
            report.putString("stream", "LOCAL_AUDIO_CHECKS_FAILED ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, report)
        }
    }
}

package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import java.lang.ref.WeakReference

/** Actual Mock host/lifecycle, synthetic in-memory transfer only: no network, APK or installer. */
internal class ReleaseDownloadRoutingChecks(private val test: Instrumentation) {
    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val host = MainPageTestHost.launch(test, TaskDestination.SETTINGS, timeoutMs = 10_000)
        var activity = host.activity
        val owner = (activity.application as MobileApplication).releaseUpdates
        val controller = owner.downloads.controller
        val stateField = ReleaseDownloadController::class.java.getDeclaredField("state").apply { isAccessible = true }
        val oldState = controller.state
        check(!controller.working) { "Do not replace an active transfer" }
        fun field(name: String) = ReleaseUpdateOwner::class.java.getDeclaredField(name).apply { isAccessible = true }
        val render = ReleaseUpdateOwner::class.java.getDeclaredMethod("renderProgress", android.app.Activity::class.java).apply { isAccessible = true }
        val transfer = ReleaseDownloadController.State(ReleaseDownloadController.Phase.DOWNLOADING, received = 10)
        fun arm() {
            field("progressOwner").set(owner, WeakReference(activity))
            field("progressPage").set(owner, WeakReference(activity.supportFragmentManager.primaryNavigationFragment ?: activity))
            field("progressSuppressed").setBoolean(owner, false)
            render.invoke(owner, activity)
        }
        fun unchanged() { check(controller.state === transfer); check(!owner.downloads.needsSafetyWatch) }
        try {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (BoundedInstrumentationMain.run("Wait for update host") { owner.currentActivity() !== activity || owner.updateBlocked() }) {
                check(SystemClock.elapsedRealtime() < deadline) { "Mock update host not ready" }; SystemClock.sleep(50)
            }
            BoundedInstrumentationMain.run("Settings inline progress") {
                stateField.set(controller, transfer)
                // Re-enter the real card observer with the synthetic shared controller state.
                activity.select(TaskDestination.BREW); activity.select(TaskDestination.SETTINGS)
                arm()
                check(field("progressPrompt").get(owner) == null)
                val progress = activity.window.decorView.findViewWithTag<View>("product/settings/app/update/progress")
                check(progress != null && progress.visibility == View.VISIBLE)
                unchanged()
                activity.select(TaskDestination.BREW); arm()
                check(field("progressPrompt").get(owner) != null) { "Other-page progress missing" }
                activity.select(TaskDestination.SETTINGS)
                check(field("progressPrompt").get(owner) == null); unchanged()
                activity.select(TaskDestination.BREW); render.invoke(owner, activity)
                check(field("progressPrompt").get(owner) == null) { "Navigation resurrected progress" }; unchanged()
                arm(); check(field("progressPrompt").get(owner) != null)
            }
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                BoundedInstrumentationMain.run("Recreate download host") { activity.recreate() }
                activity = monitor.waitForActivityWithTimeout(10_000) as? MainActivity ?: error("Recreation timed out")
            } finally { test.removeMonitor(monitor) }
            BoundedInstrumentationMain.run("Recreated host retains transfer only") {
                render.invoke(owner, activity)
                check(field("progressPrompt").get(owner) == null); unchanged()
                activity.select(TaskDestination.SETTINGS)
                val progress = activity.window.decorView.findViewWithTag<View>("product/settings/app/update/progress")
                check(progress != null && progress.visibility == View.VISIBLE)
                unchanged()
            }
        } finally {
            BoundedInstrumentationMain.run("Restore synthetic transfer") {
                owner.interruptInstallLease()
                stateField.set(controller, oldState)
                activity.finish()
            }
        }
    }
}

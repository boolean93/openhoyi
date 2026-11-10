package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Intent
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle

/** Test entry to the real task host. A redirected root Activity is never a page fixture. */
internal class MainPageTestHost(private val test: Instrumentation, val activity: MainActivity) {
    companion object {
        fun launch(test: Instrumentation, destination: TaskDestination = TaskDestination.BREW,
            extraction: Boolean = false, timeoutMs:Long? = null): MainPageTestHost {
            val intent = Intent(test.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, destination.name)
                .putExtra(MainActivity.EXTRA_OPEN_EXTRACTION, extraction)
            val tag=if(extraction) "extraction" else "root:${destination.name}"
            if(timeoutMs==null) {
                val activity = test.startActivitySync(intent) as MainActivity
                test.waitForIdleSync()
                return MainPageTestHost(test, activity).also { it.active(tag) }
            }
            // startActivitySync/waitForIdleSync can wait forever on a continuously drawing page.
            // Only callers opting into this bounded fixture route use the monitor instead.
            val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
            val activity=try {
                test.targetContext.startActivity(intent)
                monitor.waitForActivityWithTimeout(timeoutMs) as? MainActivity
                    ?: error("MainActivity launch timed out after ${timeoutMs}ms")
            }finally {test.removeMonitor(monitor)}
            val host=MainPageTestHost(test,activity)
            val deadline=android.os.SystemClock.elapsedRealtime()+timeoutMs
            var lastFailure:Throwable?=null
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                val remaining=(deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)
                val ready=BoundedInstrumentationMain.run("MainActivity fragment readiness",remaining) {
                    runCatching {host.active(tag)}.also {lastFailure=it.exceptionOrNull()}.isSuccess
                }
                if(ready)return host
                android.os.SystemClock.sleep(25)
            }
            BoundedInstrumentationMain.run("MainActivity launch cleanup") {activity.finish()}
            throw AssertionError("MainActivity fragment did not become ready within ${timeoutMs}ms",lastFailure)
        }
    }

    fun select(destination: TaskDestination): Fragment {
        test.runOnMainSync { activity.select(destination) }
        test.waitForIdleSync()
        return active("root:${destination.name}")
    }

    fun extraction(): ExtractionFragment {
        test.runOnMainSync { activity.showExtraction() }
        test.waitForIdleSync()
        return active("extraction") as ExtractionFragment
    }

    fun active(tag: String): Fragment {
        val manager = activity.supportFragmentManager
        val fragment = requireNotNull(manager.findFragmentByTag(tag)) { "Missing actual Fragment $tag" }
        check(manager.primaryNavigationFragment === fragment && !fragment.isHidden && fragment.isVisible &&
            fragment.lifecycle.currentState == Lifecycle.State.RESUMED && fragment.view?.isAttachedToWindow == true) {
            "$tag is not the active rendered Fragment"
        }
        check(manager.backStackEntryCount == 0) { "Root navigation added a Fragment back stack entry" }
        return fragment
    }

    fun <T : View> viewField(fragment: Fragment, name: String): T {
        val field = fragment.javaClass.getDeclaredField(name).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val view = field.get(fragment) as T
        check(view.isAttachedToWindow && view.rootView === activity.window.decorView.rootView) {
            "$name is detached from the actual MainActivity window"
        }
        return view
    }
}

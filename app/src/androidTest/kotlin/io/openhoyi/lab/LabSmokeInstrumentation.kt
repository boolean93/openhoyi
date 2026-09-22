package io.openhoyi.lab

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.widget.TextView

/** Offline smoke test only: never requests permission, scans, or connects hardware. */
class LabSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        var activity: Activity? = null
        try {
            activity = startActivitySync(Intent(targetContext, LabActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val current = activity
            runOnMainSync {
                val root = current.window.decorView
                check(root.findViewWithTag<TextView>("coffee_data").text.contains("尚无数据"))
                check(root.findViewWithTag<TextView>("scale_data").text.contains("尚无数据"))
                check(!root.findViewWithTag<android.view.View>("disconnect_coffee").isEnabled)
                check(!root.findViewWithTag<android.view.View>("export").isEnabled)
                current.finish()
            }
            waitForIdleSync()
            // Cold activity recreation must continue to show unknown, not simulated readings.
            activity = startActivitySync(Intent(targetContext, LabActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            val reopened = activity
            runOnMainSync { check(reopened.window.decorView.findViewWithTag<TextView>("coffee_data").text.contains("尚无数据")) }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS offline native UI startup and reopening; no hardware used\n") })
        } catch (failure: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL ${failure.javaClass.simpleName}: ${failure.message}\n") })
        } finally { activity?.let { runOnMainSync { it.finish() } } }
    }
}

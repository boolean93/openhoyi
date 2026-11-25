package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import java.io.File

/** Verifies the native result dialog in the disposable Mock UI, without a service or AI key. */
class BrewCompletionDialogInstrumentation : Instrumentation() {
    private var authorized = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        authorized = arguments?.getString("disposableEmulator") == "true"
        start()
    }

    override fun onStart() {
        var host: Activity? = null
        var popup: AlertDialog? = null
        val report = Bundle()
        var resultCode = Activity.RESULT_CANCELED
        try {
            check(authorized && BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            check(Build.HARDWARE in setOf("goldfish", "ranchu"))
            val owner = startActivitySync(Intent().setClassName(targetContext,
                "io.openhoyi.mobile.AiChatPreviewActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            host = owner
            var reviews = 0
            runOnMainSync {
                val result = BrewCompletionDialog(owner, BrewFeedbackClips.Level.HIGH_FLOW,
                    outputHundredthsGram = 3620, endCupCount = 128) { reviews++ }
                val dialog = result.dialog
                popup = dialog
                result.show()
                val review = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                val root = requireNotNull(dialog.window).decorView
                result.refresh(false, false)
                check(review.visibility == View.GONE)
                result.refresh(true, false, saving = true)
                check(review.isShown && !review.isEnabled)
                val status = root.findViewWithTag<TextView>("completion-card/status")
                check(status.isShown && status.text == owner.getString(R.string.completion_card_saving))
                result.refresh(true, false)
                check(review.isShown && !review.isEnabled)
                check(status.isShown && status.text == owner.getString(R.string.completion_card_no_samples))
                result.refresh(true, true)
                check(review.isEnabled && reviews == 0)
                check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isShown != true)

                check(!status.isShown)
                fun reading(tag: String) = root.findViewWithTag<TextView>("completion-card/$tag").text.toString()
                check(reading("weight/number") == "36.2" && reading("weight/unit") == "g")
                check(reading("cups/number") == "128")
                check(reading("flow") == owner.getString(R.string.completion_card_flow_fast))
                check(reading("note") == owner.getString(R.string.feedback_note))
            }

            check(waitForWindow(owner.packageName))
            waitForIdleSync()
            runOnMainSync {
                val dialog = requireNotNull(popup)
                val review = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                val close = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val root = requireNotNull(dialog.window).decorView
                val weight = root.findViewWithTag<View>("completion-card/weight")
                val cups = root.findViewWithTag<View>("completion-card/cups")
                val rect = Rect()
                val minimumTarget = 48 * owner.resources.displayMetrics.density
                check(listOf(review, close).all { it.width >= minimumTarget && it.height >= minimumTarget })
                for (view in listOf(weight, cups, review, close)) {
                    check(view.getGlobalVisibleRect(rect) && rect.width() == view.width && rect.height() == view.height)
                }
            }
            val image = requireNotNull(uiAutomation.takeScreenshot())
            try {
                File(targetContext.filesDir, "porcelain-result-card.png").outputStream().use {
                    check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { image.recycle() }
            runOnMainSync {
                val dialog = requireNotNull(popup)
                val review = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                val close = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                check(review.performClick() && reviews == 1)
                check(close.performClick())
            }
            waitForIdleSync()
            runOnMainSync { check(popup?.isShowing == false) }
            report.putString("stream",
                "BREW_COMPLETION_DIALOG_CHECKS_PASSED porcelainReadings=true conditionalReview=true onlyResultActions=true close=true noBle=true\n")
            resultCode = Activity.RESULT_OK
        } catch (failure: Throwable) {
            report.putString("stream", "BREW_COMPLETION_DIALOG_CHECKS_FAILED ${failure.stackTraceToString()}\n")
        } finally {
            runOnMainSync { popup?.dismiss(); host?.finish() }
        }
        finish(resultCode, report)
    }

    private fun waitForWindow(appPackage: String): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (uiAutomation.rootInActiveWindow?.packageName?.toString() == appPackage) return true
            SystemClock.sleep(100)
        }
        return false
    }
}

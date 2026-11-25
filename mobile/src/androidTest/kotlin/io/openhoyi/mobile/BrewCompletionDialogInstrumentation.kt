package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView

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
                val result = BrewCompletionDialog(owner, BrewFeedbackClips.Level.BRAVO) { reviews++ }
                val dialog = result.dialog
                popup = dialog
                result.show()
                val review = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                val body = dialog.findViewById<TextView>(android.R.id.message)
                result.refresh(false, false)
                check(review.visibility == View.GONE)
                result.refresh(true, false, saving = true)
                check(review.isShown && !review.isEnabled)
                check(body.text.contains(owner.getString(R.string.completion_card_saving)))
                result.refresh(true, false)
                check(review.isShown && !review.isEnabled)
                check(body.text.contains(owner.getString(R.string.completion_card_no_samples)))
                result.refresh(true, true)
                check(review.isEnabled && reviews == 0)
                check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isShown != true)

                val prompt = owner.getString(R.string.feedback_bravo)
                val note = owner.getString(R.string.feedback_note)
                check(body.text.toString() == "$prompt\n\n$note")
            }

            check(waitForWindow(owner.packageName))
            waitForIdleSync()
            runOnMainSync {
                val dialog = requireNotNull(popup)
                val review = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                val close = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val body = dialog.findViewById<TextView>(android.R.id.message)
                val rect = Rect()
                for (view in listOf(body, review, close)) {
                    check(view.getGlobalVisibleRect(rect) && rect.width() == view.width && rect.height() == view.height)
                }
                check(review.performClick() && reviews == 1)
                check(close.performClick())
            }
            waitForIdleSync()
            runOnMainSync { check(popup?.isShowing == false) }
            report.putString("stream",
                "BREW_COMPLETION_DIALOG_CHECKS_PASSED legacyFeedback=true conditionalReview=true onlyResultActions=true close=true noBle=true\n")
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

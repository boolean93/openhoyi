package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import java.util.concurrent.atomic.AtomicReference

/** Opt-in, disposable Android emulator only. Never selects a share target. */
class BrewCompletionShareInstrumentation : Instrumentation() {
    private var authorized = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        authorized = arguments?.getString("disposableEmulator") == "true"
        start()
    }

    override fun onStart() {
        var host: Activity? = null
        var popup: AlertDialog? = null
        var activeMonitor: ActivityMonitor? = null
        val report = Bundle()
        var resultCode = Activity.RESULT_CANCELED
        try {
            check(authorized && BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            check(Build.HARDWARE in setOf("goldfish", "ranchu"))
            val owner = startActivitySync(Intent().setClassName(targetContext,
                "io.openhoyi.mobile.AiChatPreviewActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            host = owner
            val captured = AtomicReference<Intent>()
            val monitor = object : ActivityMonitor() {
                override fun onStartActivity(intent: Intent): ActivityResult? {
                    if (captured.compareAndSet(null, Intent(intent)) && chooserText(intent) != null) return null
                    return ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
            activeMonitor = monitor
            addMonitor(monitor)

            val summary = BrewCompletionSummary(
                BrewFeedbackResult("fixture", 28, 2, 26, 500, 1.6, BrewFeedbackClips.Level.BRAVO),
                averageScaleFlowGramsPerSecond = 1.5,
                outputGrams = 36.2,
                averagePressureBar = 8.9,
                endCupCount = 128,
            )
            var reviews = 0
            var expectedText: String? = null
            runOnMainSync {
                val result = BrewCompletionDialog(owner, summary) { reviews++ }
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
                result.refresh(true, true)
                check(review.isEnabled)
                check(reviews == 0 && captured.get() == null)
                result.refresh(false, false)
                val locale = owner.resources.configuration.locales[0]
                val number = java.text.NumberFormat.getNumberInstance(locale)
                check(body.text.lines() == listOf(
                    owner.getString(R.string.completion_average_flow, "${number.format(1.5)} g/s"),
                    owner.getString(R.string.completion_yield, "${number.format(36.2)} g"),
                    owner.getString(R.string.completion_average_pressure, "${number.format(8.9)} bar"),
                    owner.getString(R.string.completion_cup_number, number.format(128)),
                ))
                expectedText = owner.getString(R.string.feedback_title) + "\n" + body.text
            }
            check(waitForWindow { it == owner.packageName })
            waitForIdleSync()
            runOnMainSync {
                val dialog = requireNotNull(popup)
                val share = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                val close = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val rect = android.graphics.Rect()
                check(share.getGlobalVisibleRect(rect) && rect.width() == share.width && rect.height() == share.height)
                check(close.getGlobalVisibleRect(rect) && rect.width() == close.width && rect.height() == close.height)
                check(share.performClick())
            }
            check(chooserText(requireNotNull(captured.get())) == expectedText)
            val systemPackages = setOf("android", "com.android.intentresolver", "com.google.android.intentresolver")
            check(waitForWindow { it in systemPackages }) { "System chooser did not become active" }
            ParcelFileDescriptor.AutoCloseInputStream(
                uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
            ).use { it.readBytes() }
            check(waitForWindow { it == owner.packageName }) { "Back did not return to the app" }
            runOnMainSync {
                val dialog = requireNotNull(popup)
                check(!owner.isFinishing && dialog.isShowing)
                check(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick())
            }
            waitForIdleSync()
            runOnMainSync { check(popup?.isShowing == false) }
            report.putString("stream",
                "BREW_COMPLETION_SHARE_CHECKS_PASSED textPlain=true sameSummary=true noFiles=true systemBack=true close=true externalSend=false noBle=true\n")
            resultCode = Activity.RESULT_OK
        } catch (failure: Throwable) {
            report.putString("stream", "BREW_COMPLETION_SHARE_CHECKS_FAILED ${failure.stackTraceToString()}\n")
        } finally {
            activeMonitor?.let(::removeMonitor)
            runOnMainSync { popup?.dismiss(); host?.finish() }
        }
        finish(resultCode, report)
    }

    @Suppress("DEPRECATION")
    private fun chooserText(chooser: Intent): String? {
        if (chooser.action != Intent.ACTION_CHOOSER ||
            chooser.extras?.keySet() != setOf(Intent.EXTRA_INTENT, Intent.EXTRA_TITLE)) return null
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return null
        if (send.action != Intent.ACTION_SEND || send.type != "text/plain" ||
            send.extras?.keySet() != setOf(Intent.EXTRA_TEXT)) return null
        val text = send.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val writeUriFlags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        for (intent in listOf(chooser, send)) {
            if (intent.component != null || intent.`package` != null || intent.data != null ||
                intent.flags and writeUriFlags != 0) return null
            val clip = intent.clipData
            if (clip == null) {
                if (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) return null
            } else {
                if (clip.itemCount != 1) return null
                val item = clip.getItemAt(0)
                if (item.uri != null || item.intent != null || item.htmlText != null || item.text?.toString() != text)
                    return null
            }
        }
        return text
    }

    private fun waitForWindow(matches: (String) -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val packageName = uiAutomation.rootInActiveWindow?.packageName?.toString()
            if (packageName != null && matches(packageName)) return true
            SystemClock.sleep(100)
        }
        return false
    }
}

package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in, disposable Android emulator only. No recipient is selected and no external share is sent. */
class BrewCompletionShareInstrumentation : Instrumentation() {
    private var authorized = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        authorized = arguments?.getString("disposableEmulator") == "true"
        start()
    }

    override fun onStart() {
        var host: Activity? = null
        var popup: BrewCompletionCardDialog? = null
        var activeMonitor: ActivityMonitor? = null
        val report = Bundle()
        var resultCode = Activity.RESULT_CANCELED
        try {
            check(authorized && BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            check(Build.HARDWARE in setOf("goldfish", "ranchu"))
            // Same process as ActivityMonitor; this display-only host has no service or Key store.
            val owner = startActivitySync(Intent().setClassName(targetContext,
                "io.openhoyi.mobile.AiChatPreviewActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            host = owner
            val expected = ByteArrayOutputStream()
            val card = AtomicReference<BrewCompletionCardDialog>()
            val queued = AtomicReference<Runnable>()
            var holdExport = false
            runOnMainSync {
                val value = BrewCompletionCardDialog(owner,
                    ShotShareCardRenderer.Model("本次萃取", "SOE 浅烘", 28.4, 36.2, 18.1,
                        listOf(ShotShareCardRenderer.Sample(0.0, 2.0), ShotShareCardRenderer.Sample(28.4, 8.0))),
                    onShare = { bitmap ->
                        val origin = requireNotNull(card.get()).dialog
                        if (holdExport) {
                            ShotShareCardShare.launch(owner, bitmap, origin,
                                encoder = Executor { check(queued.compareAndSet(null, it)) })
                        } else {
                            ShotShareCardShare.launch(owner, bitmap, origin)
                        }
                    },
                    onReview = {})
                value.refresh(false, false)
                value.dialog.show()
                check(value.writePreviewPng(expected))
                card.set(value)
            }
            val shown = requireNotNull(card.get())
            popup = shown
            val resolver = owner.contentResolver
            fun clickShare() {
                check(waitForWindow { it == owner.packageName }) { "App is not active" }
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    var hasFocus = false
                    runOnMainSync { hasFocus = shown.dialog.window?.decorView?.hasWindowFocus() == true }
                    if (hasFocus) break
                    SystemClock.sleep(50)
                }
                runOnMainSync {
                    check(shown.dialog.isShowing)
                    val decor = requireNotNull(shown.dialog.window).decorView
                    check(decor.hasWindowFocus())
                    check(decor.findViewWithTag<View>("completion-card-share").performClick())
                }
            }
            fun intercept(allowSystemChooser: Boolean): Pair<ActivityMonitor, Pair<CountDownLatch, AtomicReference<Intent>>> {
                val captured = AtomicReference<Intent>()
                val signal = CountDownLatch(1)
                val monitor = object : ActivityMonitor() {
                    override fun onStartActivity(intent: Intent): ActivityResult? {
                        val first = captured.compareAndSet(null, Intent(intent))
                        signal.countDown()
                        if (first && allowSystemChooser && safeChooser(intent, owner.packageName) != null) return null
                        return ActivityResult(Activity.RESULT_CANCELED, null)
                    }
                }
                return monitor to (signal to captured)
            }
            val (blocking, first) = intercept(allowSystemChooser = false)
            activeMonitor = blocking
            addMonitor(blocking)
            clickShare()
            check(first.first.await(10, TimeUnit.SECONDS)) { "No async share chooser dispatch" }
            val uri = requireNotNull(safeChooser(requireNotNull(first.second.get()), owner.packageName))
            check(resolver.getType(uri) == "image/png")
            val provider = requireNotNull(owner.packageManager.resolveContentProvider(requireNotNull(uri.authority), 0))
            check(provider.packageName == owner.packageName && !provider.exported && provider.grantUriPermissions)
            val png = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
            check(png.contentEquals(expected.toByteArray())) { "Share is not the exact preview bitmap" }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
            check(bounds.outWidth == ShotShareCardRenderer.WIDTH && bounds.outHeight == ShotShareCardRenderer.HEIGHT)
            check(shown.dialog.isShowing)
            removeMonitor(blocking)
            activeMonitor = null

            // Exercise actual Android chooser presentation and Back, but never touch a destination.
            val (passing, second) = intercept(allowSystemChooser = true)
            activeMonitor = passing
            addMonitor(passing)
            clickShare()
            check(second.first.await(10, TimeUnit.SECONDS))
            check(safeChooser(requireNotNull(second.second.get()), owner.packageName) != null)
            val systemPackages = setOf("android", "com.android.intentresolver", "com.google.android.intentresolver")
            check(waitForWindow { it in systemPackages }) { "System chooser did not become active" }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
            ).use { it.readBytes() }
            check(waitForWindow { it == owner.packageName }) { "Back did not return to the app" }
            runOnMainSync { check(!owner.isFinishing && shown.dialog.isShowing) }
            removeMonitor(passing)
            val (abortMonitor, aborted) = intercept(allowSystemChooser = false)
            activeMonitor = abortMonitor
            addMonitor(abortMonitor)
            runOnMainSync { holdExport = true }
            clickShare()
            val export = requireNotNull(queued.getAndSet(null)) { "Share did not start encoding" }
            val exportCache = java.io.File(owner.cacheDir, "shot-share")
            val beforeFiles = exportCache.list()?.toSet().orEmpty()
            runOnMainSync { shown.dismiss() }
            // Encoding occurs after close. Drain its posted result before checking chooser/cleanup.
            export.run()
            waitForIdleSync()
            val cleanupDeadline = SystemClock.elapsedRealtime() + 5_000
            while (exportCache.list()?.toSet().orEmpty() != beforeFiles &&
                SystemClock.elapsedRealtime() < cleanupDeadline) SystemClock.sleep(50)
            check(aborted.second.get() == null) { "Closed dialog opened a stale chooser" }
            check(exportCache.list()?.toSet().orEmpty() == beforeFiles) { "Abandoned share file remained in cache" }
            report.putString("stream", "BREW_COMPLETION_SHARE_CHECKS_PASSED imagePng=true exactBitmap=true readUriOnly=true systemBack=true dismissedExport=true externalSend=false noBle=true\n")
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
    private fun safeChooser(chooser: Intent, appPackage: String): Uri? {
        if (chooser.action != Intent.ACTION_CHOOSER || chooser.component != null || chooser.`package` != null ||
            chooser.data != null || chooser.extras?.keySet() != setOf(Intent.EXTRA_INTENT, Intent.EXTRA_TITLE)) return null
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return null
        if (send.action != Intent.ACTION_SEND || send.type != "image/png" ||
            send.component != null || send.`package` != null || send.data != null ||
            send.extras?.keySet() != setOf(Intent.EXTRA_STREAM)) return null
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: return null
        if (uri.scheme != "content" || uri.authority != "$appPackage.shot-share") return null
        val denied = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        return uri.takeIf { listOf(chooser, send).all { intent ->
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 && intent.flags and denied == 0 &&
                intent.clipData?.itemCount == 1 && intent.clipData?.getItemAt(0)?.uri == uri &&
                intent.clipData?.getItemAt(0)?.text == null
        } }
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

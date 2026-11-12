package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Point
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import io.openhoyi.session.ExtractionState
import java.io.File

/** One real Mock Service cup, real stop listener. Creates a retained, explicitly reported test shot. */
internal class ExtractionScaleWindowChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("live scale window", 5000, action)
    private fun await(stage: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 12000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < until) { "Live scale timeout: $stage" }
            SystemClock.sleep(30)
        }
    }
    private fun shell(command: String): String = test.uiAutomation.executeShellCommand(command).use {
        java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText().trim()
    }
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        // MIUI may ignore Activity orientation and letterbox instead of rotating the display.
        // These are the only two system settings changed, and their exact values are restored.
        val oldRotation = shell("settings get system user_rotation")
        val oldAccelerometer = shell("settings get system accelerometer_rotation")
        check(oldRotation in setOf("null", "0", "1", "2", "3"))
        check(oldAccelerometer in setOf("null", "0", "1"))
        val size = checkNotNull(Regex("Physical size: (\\d+)x(\\d+)").find(shell("wm size")))
        val shortSide = minOf(size.groupValues[1].toInt(), size.groupValues[2].toInt())
        val longSide = maxOf(size.groupValues[1].toInt(), size.groupValues[2].toInt())
        var portraitDisplay = false
        fun expectedSize() = if (portraitDisplay) Point(shortSide, longSide) else Point(longSide, shortSide)
        val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldDark = prefs.all["dark"] as? Boolean
        val curves = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val oldSelected = curves.getString("selected", null)
        val app = context.applicationContext as MobileApplication
        val beforeIds = app.history.entries.map { it.id }.toSet()
        val directory = File(context.getExternalFilesDir(null), "live-scale-window-checks").apply { check(isDirectory || mkdirs()) }
        var activity: MainActivity? = null
        var service: MobileService? = null
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var testShotId: String? = null
        var stopped = false
        fun host() = checkNotNull(activity)
        fun root() = checkNotNull(host().supportFragmentManager.findFragmentByTag("extraction")?.view)
        fun reading() = checkNotNull(root().findViewWithTag<TextView>("product/extraction/scale-readout/value"))
        fun ensureNoBle(owner: MobileService) {
            check(MobileService::class.java.getDeclaredField("hub").apply { isAccessible = true }.get(owner) == null)
        }
        fun settled() = host().hasWindowFocus() && host().currentService === service &&
            host().supportFragmentManager.findFragmentByTag("extraction")?.isVisible == true &&
            root().width > 0 && !host().window.decorView.isLayoutRequested
        fun changeWindow(action: () -> Unit) {
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                main(action)
                activity = checkNotNull(monitor.waitForActivityWithTimeout(12000) as? MainActivity)
                await("recreated extraction") { settled() }
            } finally { test.removeMonitor(monitor) }
        }
        fun fullDisplayWindow(): Boolean {
            val expected = expectedSize()
            val actual = Point().also { host().windowManager.defaultDisplay.getRealSize(it) }
            val decor = host().window.decorView
            val location = IntArray(2).also(decor::getLocationOnScreen)
            return actual == expected && root().width >= expected.x * .9 && decor.width >= expected.x * .9 && decor.height >= expected.y * .85 &&
                location[0] >= 0 && location[1] >= 0 &&
                location[0] + decor.width <= expected.x && location[1] + decor.height <= expected.y
        }
        fun orient(portrait: Boolean) {
            portraitDisplay = portrait
            val desired = if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                main { host().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
                shell("settings put system accelerometer_rotation 0")
                shell("settings put system user_rotation ${if (portrait) 0 else 3}")
                await("physical display rotation, full window") {
                    (monitor.lastActivity as? MainActivity)?.let { activity = it }
                    host().resources.configuration.orientation == desired && settled() && fullDisplayWindow()
                }
            } finally { test.removeMonitor(monitor) }
        }
        fun capture(name: String) {
            await("visible complete reading on physical display") {
                val view = reading(); val rect = Rect()
                checkNotNull(service).shotState.let { it == ExtractionState.RUNNING || it == ExtractionState.ENDED_OBSERVED } &&
                    settled() && fullDisplayWindow() && view.text.toString() != "— g" &&
                    view.getGlobalVisibleRect(rect) && rect.width() == view.width && rect.height() == view.height &&
                    rect.left >= 0 && rect.top >= 0 && rect.right <= expectedSize().x && rect.bottom <= expectedSize().y
            }
            if (name.contains("stopped")) SystemClock.sleep(3000) // Let completion toast clear the readout explanation.
            SystemClock.sleep(500)
            val bitmap = checkNotNull(test.uiAutomation.takeScreenshot())
            try {
                val expected = expectedSize()
                check(bitmap.width == expected.x && bitmap.height == expected.y) {
                    "Reject cropped/letterboxed capture ${bitmap.width}x${bitmap.height}; physical expected ${expected.x}x${expected.y}"
                }
                main { check(fullDisplayWindow()) }
                File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                File(directory, "$name-geometry.txt").writeText("Physical screenshot=${bitmap.width}x${bitmap.height}; expected=${expected.x}x${expected.y}; fullDisplayWindow=true; readout entirely on display.\n")
            }
            finally { bitmap.recycle() }
        }
        try {
            check(app.beanPreparationResult.getOrThrow().current()?.shotId != null ||
                app.beanPreparationResult.getOrThrow().current() == null) { "Do not consume a pending user bean preparation" }
            check(curves.edit().putString("selected", "factory-v3-002").commit())
            check(prefs.edit().putBoolean("dark", false).commit())
            val page = MainPageTestHost.launch(test, extraction = true, timeoutMs = 15000)
            activity = page.activity
            originalOrientation = host().requestedOrientation
            await("idle Mock service") { host().currentService?.running == true }
            service = checkNotNull(host().currentService)
            val owner = checkNotNull(service)
            main {
                ensureNoBle(owner)
                check(owner.shotState == ExtractionState.IDLE || owner.shotState == ExtractionState.ENDED_OBSERVED) { "Do not interrupt an existing cup" }
            }
            orient(false)
            main {
                check(owner.startShot("factory-v3-002", owner.snapshot.scaleState == io.openhoyi.session.DeviceState.READY, 7) == null) { "Mock start blocked; user settings were not changed" }
                testShotId = owner.chartTraceIdentity
                check(testShotId != null && testShotId !in beforeIds)
            }
            File(directory, "scope.txt").writeText("Retained Mock QA shotId=$testShotId; factory-v3-002. Started by liveScaleReadoutChecks; checks pending. No BLE, no original history deletion.\n")
            await("live raw grams") { owner.shotState == ExtractionState.RUNNING && reading().text.toString() != "— g" }
            SystemClock.sleep(3500) // A few observed seconds, not a nearly empty first-frame chart.
            capture("landscape-light-running")
            orient(true)
            check(prefs.edit().putBoolean("dark", true).commit())
            changeWindow { host().recreate() }
            main { check(host().resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) }
            capture("portrait-dark-running")
            main {
                ensureNoBle(owner)
                val stop = checkNotNull(root().findViewWithTag<View>("product/extraction/stop"))
                check(stop.isShown && stop.isEnabled && stop.performClick())
            }
            await("actual Mock stop frozen") { owner.shotState == ExtractionState.ENDED_OBSERVED && owner.extractionScaleValue(MobileMonotonicClock.elapsedRealtime()).ended }
            stopped = true
            var frozen: ExtractionScaleReadout.Value? = null
            main { frozen = owner.extractionScaleValue(MobileMonotonicClock.elapsedRealtime()); check(frozen?.hundredthsGram != null) }
            SystemClock.sleep(1800)
            main {
                check(owner.chartTraceIdentity == testShotId)
                check(owner.extractionScaleValue(MobileMonotonicClock.elapsedRealtime()) == frozen)
                check(reading().text.toString() == "${checkNotNull(frozen).number} g")
                check(app.history.entries.map { it.id }.toSet().containsAll(beforeIds))
                check(app.history.entries.any { it.id == testShotId })
            }
            capture("portrait-dark-stopped-frozen")
            File(directory, "scope.txt").writeText("Real MainActivity/Mock Service, actual stop button listener. No BLE. Retained test shotId=$testShotId; factory-v3-002; no original history deleted. Theme/physical rotation restoration runs in finally; see final instrumentation result. Not hardware acceptance.\n")
        } finally {
            try {
                main {
                    service?.let { owner ->
                        ensureNoBle(owner)
                        // Stop only the cup this test successfully created, never a prior/other cup.
                        if (!stopped && testShotId != null && owner.chartTraceIdentity == testShotId && ShotGate.active(owner.shotState)) owner.stopShot()
                    }
                    activity?.let { it.requestedOrientation = originalOrientation; it.finish() }
                }
            } finally {
                try {
                    check(prefs.edit().apply { if (oldDark == null) remove("dark") else putBoolean("dark", oldDark) }.commit())
                    check(curves.edit().apply { if (oldSelected == null) remove("selected") else putString("selected", oldSelected) }.commit())
                } finally {
                    fun restore(key: String, value: String) {
                        shell(if (value == "null") "settings delete system $key" else "settings put system $key $value")
                        check(shell("settings get system $key") == value) { "Failed to restore $key" }
                    }
                    try { restore("user_rotation", oldRotation) }
                    finally { restore("accelerometer_rotation", oldAccelerometer) }
                }
            }
        }
    }
}

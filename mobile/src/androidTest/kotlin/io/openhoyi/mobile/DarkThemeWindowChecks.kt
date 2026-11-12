package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import java.io.File

/** Focused actual-window theme check; chart samples are view-only, never a machine session. */
internal class DarkThemeWindowChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("theme window", 5000, action)
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 5000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < until) { "Theme window did not settle" }
            SystemClock.sleep(25)
        }
    }
    fun run(dark: Boolean) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldDark = preferences.getBoolean("dark", false)
        val existed = preferences.contains("dark")
        var host: MainPageTestHost? = null
        var fragment: ExtractionFragment? = null
        var handler: Handler? = null
        var refresh: Runnable? = null
        var chart: ExtractionOverlayView? = null
        var oldPoints: List<ShotPoint>? = null
        val fixturePoints = (0..30).map { index -> ShotPoint(index * 800L,
            if (index < 5) index * 18 else 90 - (index - 5), 20, index * 18,
            9300 + index * 2, index * 120, 20, true) }
        try {
            check(preferences.edit().putBoolean("dark", dark).commit())
            val page = MainPageTestHost.launch(test, timeoutMs = 5000); host = page
            val activity = page.activity
            await { activity.hasWindowFocus() && activity.window.decorView.width > 0 && !activity.window.decorView.isLayoutRequested }
            val orientation = if (activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape"
            val name = "$orientation-${if (dark) "dark" else "light"}"
            val directory = File(context.getExternalFilesDir(null), "dark-theme-window-checks").apply { check(isDirectory || mkdirs()) }
            fun capture(suffix: String) {
                val image = checkNotNull(test.uiAutomation.takeScreenshot())
                try { File(directory, "$name-$suffix.png").outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                finally { image.recycle() }
            }
            main {
                val home = page.active("root:BREW") as HomeFragment
                val palette = TaskProductVisualStyle(activity)
                check((home.requireView().background as ColorDrawable).color == palette.homePage)
                for (slot in 1..5) {
                    val tile = checkNotNull(home.requireView().findViewWithTag<View>("product/home/direct-preset/$slot"))
                    val fill = (tile.background as android.graphics.drawable.GradientDrawable).color?.defaultColor
                    check(fill == palette.surface || fill == palette.homeSelected)
                }
            }
            SystemClock.sleep(650) // Capture after the window enter animation, not during its dimmed transition.
            capture("home")
            main { activity.showExtraction() }
            await { runCatching { page.active("extraction") }.isSuccess && activity.currentService?.running == true && !activity.window.decorView.isLayoutRequested }
            main {
                val target = page.active("extraction") as ExtractionFragment; fragment = target
                fun field(name: String) = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
                handler = field("handler") as Handler; refresh = field("refresh") as Runnable
                handler!!.removeCallbacks(refresh!!)
                val actual = field("chart") as ExtractionOverlayView; chart = actual; oldPoints = actual.points
                actual.points = fixturePoints
            }
            // Let the existing chart animation complete; this does not alter its timing or acceptance threshold.
            SystemClock.sleep(650)
            main { check(chart?.points == fixturePoints) { "View fixture was replaced before capture" }; SingleScreenPageChecks.verify(activity) }
            capture("extraction-view-fixture")
            File(directory, "$name-scope.txt").writeText("Actual MainActivity windows; chart samples injected only into attached view; no start/stop/device command. Theme assertions passed.\n")
        } finally {
            try {
                main {
                    try {
                        if (fragment?.isAdded == true) {
                            try { oldPoints?.let { chart?.points = it } }
                            finally {
                                if (handler != null && refresh != null) { handler!!.removeCallbacks(refresh!!); handler!!.post(refresh!!) }
                            }
                        }
                    } finally { host?.activity?.finish() }
                }
            } finally {
                val edit = preferences.edit()
                if (existed) edit.putBoolean("dark", oldDark) else edit.remove("dark")
                check(edit.commit())
            }
        }
    }
}

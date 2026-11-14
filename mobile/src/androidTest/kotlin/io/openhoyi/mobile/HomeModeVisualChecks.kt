package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.openhoyi.session.DeviceState
import java.io.File

/** Native Mock UI only: injected connection states, no BLE or user recipe mutation. */
internal class HomeModeVisualChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (t: Throwable) { error = t } }
        error?.let { throw it }
    }
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    fun run(args: Bundle) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(args.getString("coordinatedIdleWindow") == "true")
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val previousTheme = if (appearance.contains("dark")) appearance.getBoolean("dark", false) else null
        var host: MainPageTestHost? = null
        var owner: MobileService? = null
        var previous: MobileSnapshot? = null
        var handler: Handler? = null
        var tick: Runnable? = null
        val field = MobileService::class.java.getDeclaredField("snapshot").apply { isAccessible = true }
        try {
            check(appearance.edit().putBoolean("dark", args.getString("dark") == "true").commit())
            host = MainPageTestHost.launch(test, timeoutMs = 10000)
            val home = host.select(TaskDestination.BREW) as HomeFragment
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (owner == null && SystemClock.elapsedRealtime() < deadline) {
                main { owner = HomeFragment::class.java.getDeclaredField("service").apply { isAccessible = true }.get(home) as? MobileService }
                Thread.sleep(30)
            }
            val service = requireNotNull(owner)
            val controls = requireNotNull(home.view?.findViewWithTag<MachineModeControls>("machine-mode-controls"))
            main {
                previous = service.snapshot
                check(previous!!.settings != null)
                handler = MobileService::class.java.getDeclaredField("handler").apply { isAccessible = true }.get(service) as Handler
                tick = MobileService::class.java.getDeclaredField("mockTick").apply { isAccessible = true }.get(service) as Runnable
                handler!!.removeCallbacks(tick!!)
            }
            val folder = File(context.getExternalFilesDir(null), "home-mode-ui").apply { mkdirs() }
            for ((name, flags, connected) in listOf(Triple("pressure", 0x00, true), Triple("flow", 0xc0, true), Triple("disconnected", 0xc0, false))) {
                main {
                    val now = SystemClock.elapsedRealtime()
                    field.set(service, previous!!.copy(coffeeState = if (connected) DeviceState.READY else DeviceState.DISCONNECTED,
                        settings = previous!!.settings!!.copy(flags = (previous!!.settings!!.flags and 0x3f) or flags),
                        settingsAt = now, coffeeAt = now))
                    controls.render(service)
                }
                Thread.sleep(350)
                main {
                    val segments = descendants(controls).filterIsInstance<CurveEditorSegments>()
                    check(segments.size == 2 && segments.all { it.isEnabled == connected })
                    segments.forEach { segment ->
                        val visible = Rect(); check(segment.getGlobalVisibleRect(visible))
                        check(visible.width() == segment.width && visible.height() == segment.height)
                        check(visible.height() >= 48 * context.resources.displayMetrics.density - 1)
                        descendants(segment).filterIsInstance<TextView>().forEach { check(it.lineCount == 1 && it.layout.getEllipsisCount(0) == 0) }
                        if (!connected) { val selected = segment.checked; segment.selectFromUser(!selected); check(segment.checked == selected) }
                    }
                }
                val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                File(folder, "${args.getString("fixture", "current")}-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally {
            main {
                if (owner != null && previous != null) field.set(owner, previous)
                if (handler != null && tick != null) handler!!.post(tick!!)
                host?.activity?.finish()
            }
            appearance.edit().apply { if (previousTheme == null) remove("dark") else putBoolean("dark", previousTheme) }.commit()
        }
    }
}

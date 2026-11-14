package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.view.InputDevice
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import java.io.File

/** Focused real-window checks. Refuses physical devices and non-Mock packages. */
class CurveFilterPopupInstrumentation : Instrumentation() {
    private var disposable = false
    override fun onCreate(arguments: Bundle?) {
        disposable = arguments?.getString("disposableCiEmulator") == "true"
        super.onCreate(arguments)
        start()
    }
    private fun main(action: () -> Unit) = BoundedInstrumentationMain.run("curve filter popup", 5000, action)
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Curve filter window timed out" }
            SystemClock.sleep(25)
        }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(owner)
    private fun capture(directory: File, name: String) {
        val bitmap = requireNotNull(uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
    private fun popupNode(label: String): AccessibilityNodeInfo? {
        val root = uiAutomation.rootInActiveWindow ?: return null
        try {
            return root.findAccessibilityNodeInfosByText(label).firstOrNull {
                it.text?.toString() == label && it.isVisibleToUser
            }
        } finally { root.recycle() }
    }
    private fun clickPopup(label: String) {
        await { popupNode(label)?.let { it.recycle(); true } == true }
        val node = requireNotNull(popupNode(label))
        val bounds = Rect()
        try {
            check(node.packageName?.toString() == targetContext.packageName)
            node.getBoundsInScreen(bounds)
            check(!bounds.isEmpty)
        } finally { node.recycle() }
        // Native ListView menu rows need not advertise ACTION_CLICK. Tap the actual visible text bounds.
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try { check(uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        waitForIdleSync()
    }
    override fun onStart() {
        val report = Bundle()
        try {
            check(disposable && BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            val app = targetContext.applicationContext as MobileApplication
            val document = CustomCurveDocument("draft-00000000-0000-4000-8000-000000000091", "Filter popup fixture", 92, 1800,
                CustomCurveDocument.ControlMode.PRESSURE,
                listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
            app.customCurvesResult.getOrThrow().save(document)
            val preferences = targetContext.getSharedPreferences("curves", Context.MODE_PRIVATE)
            val output = File(targetContext.getExternalFilesDir(null), "curve-filter-popup").apply { check(isDirectory || mkdirs()) }
            for (dark in listOf(false, true)) for (embedded in listOf(false, true)) {
                check(targetContext.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).commit())
                check(preferences.edit().remove("customOnly").putString("sort", CurveSortMode.RECENT.name).commit())
                var activity: Activity
                var owner: Any
                if (embedded) {
                    val host = MainPageTestHost.launch(this)
                    activity = host.activity
                    owner = host.select(TaskDestination.CURVES)
                } else {
                    val monitor = addMonitor(MainActivity::class.java.name, null, false)
                    try {
                        startActivitySync(Intent(targetContext, CurveActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        activity = requireNotNull(waitForMonitorWithTimeout(monitor, 5000))
                        waitForIdleSync()
                        owner = (activity as MainActivity).supportFragmentManager.primaryNavigationFragment!!
                        check(owner is CurveFragment)
                    } finally { removeMonitor(monitor) }
                }
                val name = "${if (dark) "dark" else "light"}-${if (embedded) "fragment" else "legacy-redirect"}"
                fun menu() = activity.window.decorView.findViewWithTag<CurveLibraryMenu>("product/curve-library/sort")
                fun open() {
                    main { check(menu().isShown && menu().performClick()) }
                    await { popupNode(activity.getString(R.string.curve_filter_custom))?.let { it.recycle(); true } == true }
                }
                fun verify(sort: CurveSortMode, custom: Boolean) = main {
                    check(field(owner, "sortMode") == sort && field(owner, "customOnly") == custom)
                    check(preferences.getString("sort", null) == sort.name && preferences.getBoolean("customOnly", false) == custom)
                }
                try {
                    await { activity.hasWindowFocus() }
                    open(); capture(output, "$name-menu")
                    clickPopup(activity.getString(R.string.curve_filter_custom))
                    await { activity.hasWindowFocus() }; verify(CurveSortMode.RECENT, true)
                    main {
                        val visible = field(owner, "visibleItems") as List<*>
                        check(visible.isNotEmpty() && visible.all { (it as CurveLibraryItem).customDocument != null })
                        val chip = views(activity.window.decorView).filterIsInstance<TextView>().single {
                            it.isClickable && it.text.toString() == CurveCategoryFilter.DARK.label(activity) }
                        check(chip.performClick())
                        check(field(owner, "category") == CurveCategoryFilter.DARK)
                        check((field(owner, "visibleItems") as List<*>).isEmpty())
                        (field(owner, "search") as EditText).setText("no-such-curve-for-filter")
                        check((field(owner, "visibleItems") as List<*>).isEmpty())
                        check((field(owner, "resultCount") as TextView).text.toString() == activity.getString(R.string.curve_filter_empty))
                    }
                    capture(output, "$name-empty")
                    open(); clickPopup(activity.getString(R.string.curve_sort_most_used))
                    await { activity.hasWindowFocus() }; verify(CurveSortMode.MOST_USED, true)
                    main { check((field(owner, "search") as EditText).text.toString() == "no-such-curve-for-filter") }
                    val monitor = addMonitor(activity.javaClass.name, null, false)
                    try {
                        main { activity.recreate() }
                        activity = requireNotNull(waitForMonitorWithTimeout(monitor, 5000))
                        await { activity.hasWindowFocus() }
                        owner = (activity as MainActivity).supportFragmentManager.primaryNavigationFragment!!
                        verify(CurveSortMode.MOST_USED, true)
                        main { check(field(owner, "category") == CurveCategoryFilter.DARK) }
                    } finally { removeMonitor(monitor) }
                    open(); capture(output, "$name-restored-menu")
                    clickPopup(activity.getString(R.string.curve_filter_custom))
                    await { activity.hasWindowFocus() }; verify(CurveSortMode.MOST_USED, false)
                    main {
                        (field(owner, "search") as EditText).setText("")
                        check(views(activity.window.decorView).filterIsInstance<TextView>().single {
                            it.isClickable && it.text.toString() == CurveCategoryFilter.ALL.label(activity) }.performClick())
                        check((field(owner, "visibleItems") as List<*>).size == app.curves.items.size)
                    }
                    capture(output, "$name-all")
                } catch (error: Throwable) {
                    runCatching { capture(output, "$name-failure") }
                    throw error
                } finally {
                    main { activity.finish() }
                    await { activity.isDestroyed }
                }
            }
            report.putString("stream", "CURVE_FILTER_POPUP_PASSED realPopup=true themes=2 entryPoints=2 recreated=true noBle=true\n")
            finish(Activity.RESULT_OK, report)
        } catch (error: Throwable) {
            report.putString("stream", error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, report)
        }
    }
}

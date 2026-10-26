package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import io.openhoyi.session.ExtractionState

/** Exercises the rendered six-tab host, never the redirect Activities. */
internal class MainNavigationChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun compactHomeGeometry(host: MainPageTestHost, activeCup: Boolean) {
        val activity = host.activity
        val real = android.graphics.Point()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealSize(real)
        val density = activity.resources.displayMetrics.density
        if (kotlin.math.abs(real.x / density - 720f) > 1f ||
            kotlin.math.abs(real.y / density - 360f) > 1f) return
        val home = host.select(TaskDestination.BREW) as HomeFragment
        test.runOnMainSync {
            val root = requireNotNull(home.view)
            val viewport = Rect()
            check(root.getGlobalVisibleRect(viewport) && !viewport.isEmpty)
            val tags = listOf("machine-status", "brew-boiler", "steam-boiler") +
                (if (activeCup) emptyList() else listOf("quick-launch") + (1..5).map { "direct-preset/$it" })
            val bounds = tags.associateWith { name ->
                val view = requireNotNull(descendants(root).singleOrNull { it.tag == "product/home/$name" }) {
                    "Missing actual Home view product/home/$name"
                }
                val visible = Rect()
                check(view.isShown && view.getGlobalVisibleRect(visible) &&
                    visible.width() == view.width && visible.height() == view.height &&
                    visible.top >= viewport.top && visible.bottom <= viewport.bottom) {
                    "720x360 Home $name outside page viewport: $visible viewport=$viewport view=${view.width}x${view.height}"
                }
                visible
            }
            if (activeCup) {
                val stop = descendants(root).singleOrNull { it.tag == "product/home/dock" && it.isShown }
                    ?: descendants(root).filterIsInstance<android.widget.Button>().singleOrNull {
                        it.text.toString() == activity.getString(R.string.home_stop) && it.isShown
                    }
                    ?: error("Active Mock cup has no fixed Home action")
                val stopBounds = Rect()
                check(stop.getGlobalVisibleRect(stopBounds) && stopBounds.height() > 0)
                for (name in tags) check(!Rect.intersects(stopBounds, bounds.getValue(name))) {
                    "Active Home action overlaps $name"
                }
            }
        }
    }

    private fun awaitPage(activity: MainActivity, tag: String) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            var active = false
            test.runOnMainSync { active = activity.supportFragmentManager.primaryNavigationFragment?.tag == tag }
            if (active) break
            SystemClock.sleep(50)
        }
        MainPageTestHost(test, activity).active(tag)
    }

    private fun startupAndRestore(destination: TaskDestination) {
        val tag = "root:${destination.name}"
        var activity = MainPageTestHost.launch(test, destination).activity
        fun assertAttached(): Int {
            val pageId = MainActivity::class.java.getDeclaredField("pageId")
                .apply { isAccessible = true }.getInt(activity)
            val container = requireNotNull(activity.findViewById<FrameLayout>(pageId)) {
                "$destination page container missing from MainActivity window"
            }
            check(container.tag == "task-tab-content" && container.isAttachedToWindow)
            val fragment = MainPageTestHost(test, activity).active(tag)
            check(fragment.id == pageId && fragment.view?.parent === container) {
                "$destination Fragment is not attached to its saved page container"
            }
            return pageId
        }
        try {
            val initialPageId = assertAttached()
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                test.runOnMainSync { activity.recreate() }
                activity = test.waitForMonitorWithTimeout(monitor, 10000) as? MainActivity
                    ?: error("$destination MainActivity recreation was not observed")
                test.waitForIdleSync()
            } finally { test.removeMonitor(monitor) }
            check(assertAttached() == initialPageId) {
                "$destination page container ID changed across Fragment restoration"
            }
        } finally { test.runOnMainSync { activity.finish() }; test.waitForIdleSync() }
    }

    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        startupAndRestore(TaskDestination.BREW)
        startupAndRestore(TaskDestination.SCALE)
        val host = MainPageTestHost.launch(test)
        var activity = host.activity
        try {
            compactHomeGeometry(host, activeCup = false)
            val ordered = TaskDestination.entries
            val initial = mutableMapOf<TaskDestination, androidx.fragment.app.Fragment>()
            val activityMonitor = test.addMonitor(null as String?, null, false)
            try { for (round in 0 until 20) for (destination in ordered) {
                test.runOnMainSync {
                    val tab = descendants(activity.window.decorView).single {
                        it.tag == "task-nav:${destination.name}" && it.isShown
                    }
                    check(tab.performClick()) { "$destination Tab rejected actual click" }
                }
                test.waitForIdleSync()
                val fragment = host.active("root:${destination.name}")
                initial.putIfAbsent(destination, fragment)
                check(initial[destination] === fragment) { "$destination was recreated on tab switch $round" }
                check(activity.supportFragmentManager.fragments.count { it.tag == "root:${destination.name}" } == 1)
                check(activity.supportFragmentManager.backStackEntryCount == 0)
                check(activityMonitor.hits == 0) { "Tab switch started another Activity" }
                check(!activity.isFinishing && activity.hasWindowFocus()) {
                    "Tab switch moved focus away from the single MainActivity"
                }
            } } finally { test.removeMonitor(activityMonitor) }
            val curve = host.select(TaskDestination.CURVES)
            var search: EditText? = null
            test.runOnMainSync {
                search = descendants(requireNotNull(curve.view)).filterIsInstance<EditText>().firstOrNull()
                check(search != null) { "Curve filter search is absent" }
                search!!.setText("navigation-state")
            }
            val history = host.select(TaskDestination.HISTORY)
            var historyList: ListView? = null
            var historyPosition = 0
            test.runOnMainSync {
                historyList = descendants(requireNotNull(history.view)).filterIsInstance<ListView>().firstOrNull()
                historyPosition = historyList?.firstVisiblePosition ?: 0
            }
            val beans = host.select(TaskDestination.BEANS)
            val beanView = requireNotNull(beans.view)
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (activity.currentService == null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
            }
            val service = requireNotNull(activity.currentService) { "Mock service was not bound by MainActivity" }
            check(service.running)
            val curvePrefs = activity.getSharedPreferences("curves", android.content.Context.MODE_PRIVATE)
            val previousCurve = curvePrefs.getString("selected", null)
            try {
                val readyDeadline = SystemClock.elapsedRealtime() + 10000
                while (!service.machineSettingsFresh && SystemClock.elapsedRealtime() < readyDeadline) SystemClock.sleep(50)
                check(service.machineSettingsFresh) { "Mock machine settings never became fresh" }
                check(curvePrefs.edit().putString("selected", "capture-2").commit())
                test.runOnMainSync { check(service.startShot("capture-2", null, 7) == null) }
                val shotDeadline = SystemClock.elapsedRealtime() + 5000
                while (service.shotState != ExtractionState.RUNNING && SystemClock.elapsedRealtime() < shotDeadline)
                    SystemClock.sleep(50)
                check(service.shotState == ExtractionState.RUNNING) { "Mock extraction did not start" }
                val trace = requireNotNull(service.chartTraceIdentity)
                val points = service.chartPoints.size
                compactHomeGeometry(host, activeCup = true)
                host.extraction()
                host.select(TaskDestination.CURVES)
                host.select(TaskDestination.HISTORY)
                host.select(TaskDestination.BEANS)
                check(beans.view === beanView) { "Bean editor page was rebuilt by tab navigation" }
                host.extraction()
                check(activity.currentService === service && service.running) { "Tab switch restarted the service" }
                check(service.chartTraceIdentity == trace && service.chartPoints.size >= points) {
                    "Tab switch reset the extraction series"
                }
            } finally {
                test.runOnMainSync { service.stopShot() }
                val edit = curvePrefs.edit()
                if (previousCurve == null) edit.remove("selected") else edit.putString("selected", previousCurve)
                check(edit.commit())
            }
            host.select(TaskDestination.CURVES)
            check(search?.text?.toString() == "navigation-state") { "Curve filter state was lost" }
            host.select(TaskDestination.HISTORY)
            if (historyPosition > 0) check(historyList?.firstVisiblePosition == historyPosition) {
                "Nonzero History list position was lost"
            }
            // Inject only the Mock service's existing confirmed-manual-ID gate. This checks the
            // real MainActivity polling/navigation path; PassiveShotDetector is tested separately.
            val manual = MobileService::class.java.getDeclaredField("manualPresentation")
                .apply { isAccessible = true }.get(service) as ManualShotPresentationGate
            try {
                host.select(TaskDestination.CURVES)
                test.runOnMainSync { manual.observedIdle(); manual.started("shell-manual-1") }
                awaitPage(activity, "extraction")
                host.select(TaskDestination.CURVES)
                SystemClock.sleep(600)
                host.active("root:CURVES") // Same ID must not steal the tab again.
                val manualMonitor = test.addMonitor(MainActivity::class.java.name, null, false)
                try {
                    test.runOnMainSync { activity.recreate() }
                    activity = test.waitForMonitorWithTimeout(manualMonitor, 10000) as? MainActivity
                        ?: error("Manual-shot host recreation was not observed")
                    test.waitForIdleSync()
                    SystemClock.sleep(600)
                    MainPageTestHost(test, activity).active("root:CURVES")
                } finally { test.removeMonitor(manualMonitor) }
                test.runOnMainSync { manual.ended(); manual.started("shell-manual-2") }
                awaitPage(activity, "extraction")
            } finally { test.runOnMainSync { manual.ended() } }
            MainPageTestHost(test, activity).select(TaskDestination.SETTINGS)
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                test.runOnMainSync { activity.recreate() }
                activity = test.waitForMonitorWithTimeout(monitor, 10000) as? MainActivity
                    ?: error("MainActivity recreation was not observed")
                test.waitForIdleSync()
                MainPageTestHost(test, activity).active("root:SETTINGS")
                val reboundDeadline = SystemClock.elapsedRealtime() + 5000
                while (activity.currentService == null && SystemClock.elapsedRealtime() < reboundDeadline)
                    SystemClock.sleep(50)
                check(activity.currentService === service) { "Configuration rebuild replaced the running service" }
            } finally { test.removeMonitor(monitor) }
            test.sendStatus(0, Bundle().apply {
                putString("stream", "MAIN_NAVIGATION_CHECKS_PASSED rounds=20 tabs=6 singleHost=true curveFilterRetained=true serviceContinuous=true manualShell=true noBle=true\n")
            })
        } finally { test.runOnMainSync { activity.finish() }; test.waitForIdleSync() }
    }
}

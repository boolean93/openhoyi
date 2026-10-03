package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Real Mock windows only. Visits pages/scrolls/local tabs; never clicks a machine control. */
internal class LanguagePageChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Page condition timed out" }
            Thread.sleep(25)
        }
    }
    private fun owner(activity: Activity): MobileService? = activity.javaClass.getDeclaredField("service")
        .apply { isAccessible = true }.get(activity) as? MobileService
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): LinearLayout =
        (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout)

    fun run(profile: String) {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(profile == "compact" || profile == "wideFont")
        val expectedWidth = if (profile == "compact") 360 else 1280
        val expectedScale = if (profile == "compact") 1f else 1.3f
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history", "brew_feedback")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        var home = test.startActivitySync(Intent(context, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
        test.waitForIdleSync()
        awaitMain { owner(home)?.running == true && home.window.decorView.isLaidOut }
        var service: MobileService? = null
        var message: SnapshotMessage? = null
        onMain { service = owner(home); message = service!!.snapshot.message; negativeFixture(home) }
        var currentPage: Activity? = null
        val detailChecks = LanguageDetailChecks(test)
        var completed = 0
        var failure: Throwable? = null
        fun rebuildHome() {
            val monitor = test.addMonitor(HomeActivity::class.java.name, null, false)
            try {
                onMain { home.recreate() }
                home = test.waitForMonitorWithTimeout(monitor, 10000) as? HomeActivity
                    ?: error("Page Home recreation not observed")
                test.waitForIdleSync()
                awaitMain { owner(home) != null && home.window.decorView.isLaidOut }
            } finally { test.removeMonitor(monitor) }
        }
        fun assertState() = onMain {
            check(service!!.running && service!!.snapshot.message === message) { "Page navigation changed the service/event" }
            preserved.forEach { (name, values) ->
                check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) { "Page layout changed $name" }
            }
        }
        try {
            for (language in AppLanguage.entries) for (dark in listOf(false, true)) {
                onMain {
                    check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                    check(appearance.edit().putBoolean("dark", dark).commit())
                }
                rebuildHome()
                onMain { check(owner(home) === service) }
                val pages = listOf(HomeActivity::class.java, CurveActivity::class.java, ExtractionActivity::class.java,
                    HistoryActivity::class.java, MachineSettingsActivity::class.java)
                for (page in pages) {
                    val fixture = "$profile ${language.tag} dark=$dark page=${page.simpleName}"
                    android.util.Log.i("OpenHoyiLanguage", "PAGE_START $fixture")
                    test.sendStatus(0, Bundle().apply { putString("stream", "LANGUAGE_PAGE_START $fixture\n") })
                    val activity = if (page == HomeActivity::class.java) home else {
                        check(currentPage == null)
                        test.startActivitySync(Intent(context, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also { currentPage = it }
                    }
                    test.waitForIdleSync()
                    awaitMain {
                        activity.window.decorView.isLaidOut && root(activity).height > 0 && !root(activity).isLayoutRequested &&
                            (activity !is ExtractionActivity && activity !is MachineSettingsActivity || owner(activity) === service)
                    }
                    onMain {
                        val config = activity.resources.configuration
                        check(config.screenWidthDp == expectedWidth && kotlin.math.abs(config.fontScale - expectedScale) < .001f) {
                            "$fixture unexpected real window configuration: width=${config.screenWidthDp} font=${config.fontScale}"
                        }
                        check(config.locales[0] == language.locale && HoyiUi.dark(activity) == dark)
                        check(activity.window.decorView.layoutDirection == if (language.rightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
                    }
                    checkPage(activity, fixture)
                    when (activity) {
                        is HomeActivity -> detailChecks.warnings(activity, fixture) { checkPage(activity, it) }
                        is CurveActivity -> detailChecks.curves(activity, fixture) { checkPage(activity, it) }
                        is ExtractionActivity -> detailChecks.cancelStart(activity, fixture)
                    }
                    if (activity is MachineSettingsActivity) {
                        val tabs = MachineSettingsActivity::class.java.getDeclaredField("settingTabs")
                            .apply { isAccessible = true }.get(activity) as List<*>
                        val labels = listOf(R.string.machine_settings_tab_temperature, R.string.machine_settings_tab_functions,
                            R.string.machine_settings_tab_standby, R.string.machine_settings_tab_schedule, R.string.machine_settings_tab_maintenance)
                        check(tabs.size == labels.size)
                        for ((index, tab) in tabs.withIndex()) {
                            onMain {
                                val localTab = tab as TextView
                                check(localTab.text.toString() == activity.getString(labels[index]))
                                check(localTab.performClick()) // Only showSettingsSection: no write/confirmation callback.
                            }
                            test.waitForIdleSync()
                            checkPage(activity, "$fixture section=$index")
                            assertState()
                        }
                    }
                    assertState()
                    completed++
                    if (currentPage != null) {
                        onMain { activity.finish() }
                        awaitMain { activity.isDestroyed && home.hasWindowFocus() && owner(home) === service }
                        currentPage = null
                    }
                }
            }
            check(completed == 80) { "Page matrix incomplete: $completed" }
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) {
                try { action() } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            cleanup {
                val closing = currentPage
                if (closing != null) {
                    onMain { closing.finish() }
                    awaitMain { closing.isDestroyed && home.hasWindowFocus() && owner(home) === service }
                    currentPage = null
                }
            }
            cleanup { onMain { check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED) } }
            cleanup { onMain { restoreMockPreferences(languagePrefs, oldLanguageStorage) } }
            cleanup { onMain { restoreMockPreferences(appearance, oldAppearance) } }
            cleanup { rebuildHome() }
            cleanup { onMain { check(owner(home) === service) }; assertState() }
        }
        failure?.let { throw it }
    }

    private fun checkPage(activity: Activity, fixture: String) {
        // Idle callbacks can precede VSYNC: a newly visible settings section needs real layout.
        awaitMain {
            !root(activity).isLayoutRequested && descendants(root(activity)).filterIsInstance<TextView>()
                .filter { it.isShown && !it.text.isNullOrEmpty() }.all { it.isLaidOut && it.layout != null && !it.isLayoutRequested }
        }
        val scrolls = mutableListOf<ScrollView>()
        onMain {
            val views = descendants(root(activity))
            scrolls += views.filterIsInstance<ScrollView>().filter { it.isShown }
            checkGeometry(activity, fixture)
            var checked = 0
            for (view in views.filterIsInstance<TextView>()) {
                if (!view.isShown || view is EditText || view.text.isNullOrEmpty()) continue
                // Curve/list summaries explicitly allow ellipsis; controls and other labels do not.
                if (view !is Button && view.ellipsize != null) continue
                LanguageUiLayoutChecks.textFits(view, fixture)
                textWidthFits(view, fixture)
                checked++
            }
            check(checked >= 5) { "$fixture no real page text inspected" }
        }
        for (scroll in scrolls) {
            var maximum = 0
            var step = 0
            onMain {
                maximum = (scroll.getChildAt(0).height - (scroll.height - scroll.paddingTop - scroll.paddingBottom)).coerceAtLeast(0)
                step = (scroll.height / 2).coerceAtLeast(1)
                scroll.scrollTo(0, 0)
            }
            var position = 0
            while (true) {
                onMain { scroll.scrollTo(0, position) }
                test.waitForIdleSync()
                onMain { checkGeometry(activity, "$fixture scroll=$position") }
                if (position == maximum) break
                position = (position + step).coerceAtMost(maximum)
            }
            onMain {
                check(scroll.scrollY == maximum) { "$fixture scroll end unreachable: ${scroll.scrollY}/$maximum" }
                scroll.scrollTo(0, 0)
            }
            test.waitForIdleSync()
        }
    }

    private fun checkGeometry(activity: Activity, fixture: String) {
        val root = root(activity)
        val bar = root.getChildAt(root.childCount - 1) as LinearLayout
        check(bar.childCount == 5)
        fullyVisible(bar, fixture)
        check(bar.bottom <= root.height - root.paddingBottom && bar.top >= root.paddingTop)
        val labels = listOf(R.string.ui_home, R.string.ui_curves, R.string.ui_extraction, R.string.ui_history, R.string.ui_settings)
        for (index in 0 until bar.childCount) {
            val item = bar.getChildAt(index) as ViewGroup
            check(item.isClickable && item.isFocusable && item.width >= HoyiUi.dp(activity, 48) && item.height >= HoyiUi.dp(activity, 48))
            fullyVisible(item, fixture)
            val label = descendants(item).filterIsInstance<TextView>().single()
            check(label.text.toString() == activity.getString(labels[index]))
            LanguageUiLayoutChecks.textFits(label, fixture)
            fullyVisible(label, fixture)
        }
        for (index in 0 until root.childCount - 1) {
            val child = root.getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            check(child.height > 0 && child.bottom <= bar.top) { "$fixture content/fixed action overlaps navigation or has no height" }
            if (child is Button) {
                fullyVisible(child, fixture)
                LanguageUiLayoutChecks.textFits(child, fixture)
                check(child.height >= HoyiUi.dp(activity, 48))
            }
        }
        if (activity is ExtractionActivity) {
            val start = ExtractionActivity::class.java.getDeclaredField("start").apply { isAccessible = true }.get(activity) as Button
            check(start.visibility == View.VISIBLE && start.text.toString() == activity.getString(R.string.extraction_start))
            fullyVisible(start, fixture)
        }
    }

    private fun fullyVisible(view: View, fixture: String) {
        val visible = Rect()
        check(view.isShown && view.getGlobalVisibleRect(visible) && visible.width() == view.width && visible.height() == view.height) {
            "$fixture incomplete visible control: ${if (view is TextView) view.text else view.javaClass.simpleName} size=${view.width}x${view.height} visible=$visible"
        }
    }
    private fun textWidthFits(view: TextView, fixture: String) {
        val availableWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        val layout = requireNotNull(view.layout)
        for (line in 0 until layout.lineCount) check(layout.getLineRight(line) - layout.getLineLeft(line) <= availableWidth + 1) {
            "$fixture text exceeds actual view width: ${view.text}"
        }
    }
    private fun negativeFixture(activity: Activity) {
        val overflow = TextView(activity).apply { text = "STOP STOP STOP STOP STOP STOP STOP"; textSize = 18f; setSingleLine(true) }
        overflow.measure(View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, 40), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        overflow.layout(0, 0, overflow.measuredWidth, overflow.measuredHeight)
        val layout = requireNotNull(overflow.layout)
        check(layout.getLineRight(0) - layout.getLineLeft(0) > overflow.width - overflow.compoundPaddingLeft - overflow.compoundPaddingRight + 1) {
            "Negative actual-view-width fixture did not overflow"
        }
        check(runCatching { textWidthFits(overflow, "intentional actual width overflow") }.exceptionOrNull() is IllegalStateException) {
            "Actual-width checker accepted single-line overflow"
        }
    }
}

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
    private fun awaitMain(diagnostic: () -> String = { "unspecified condition" }, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            if (SystemClock.elapsedRealtime() >= deadline) {
                var detail = ""
                onMain { detail = diagnostic() }
                error("Page condition timed out: $detail")
            }
            Thread.sleep(25)
        }
    }
    private fun owner(activity: Activity): MobileService? = if (activity is MainActivity) activity.currentService else
        activity.javaClass.getDeclaredField("service").apply { isAccessible = true }.get(activity) as? MobileService
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): LinearLayout =
        (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as LinearLayout)

    fun run(profile: String) {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(profile in listOf("compact", "landscape", "wideFont"))
        val expectedWidth = if (profile == "compact") 360 else 1280
        val expectedScale = if (profile == "wideFont") 1.3f else 1f
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history", "brew_feedback")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        var home = MainPageTestHost.launch(test).activity
        awaitMain { owner(home)?.running == true && home.window.decorView.isLaidOut }
        val service = requireNotNull(owner(home))
        val message = service.snapshot.message
        onMain { negativeFixture(home) }
        var currentPage: MachineSettingsActivity? = null
        val detailChecks = LanguageDetailChecks(test)
        var completed = 0
        var failure: Throwable? = null
        fun rebuildHome() {
            val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
            try {
                onMain { home.recreate() }
                home = test.waitForMonitorWithTimeout(monitor, 10000) as? MainActivity
                    ?: error("Main recreation not observed")
                test.waitForIdleSync()
                awaitMain { owner(home) === service && home.window.decorView.isLaidOut }
                MainPageTestHost(test, home).active("root:BREW")
            } finally { test.removeMonitor(monitor) }
        }
        fun assertState() = onMain {
            check(service.running && service.snapshot.message === message) { "Page navigation changed the service/event" }
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
                val pages = listOf("Home", "Curve", "Extraction", "History", "AppSettings", "MachineSettings")
                for (page in pages) {
                    val fixture = "$profile ${language.tag} dark=$dark page=$page"
                    android.util.Log.i("OpenHoyiLanguage", "PAGE_START $fixture")
                    test.sendStatus(0, Bundle().apply { putString("stream", "LANGUAGE_PAGE_START $fixture\n") })
                    val activity: Activity = if (page == "MachineSettings") {
                        check(currentPage == null)
                        test.startActivitySync(Intent(context, MachineSettingsActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also { currentPage = it as MachineSettingsActivity }
                    } else {
                        val host = MainPageTestHost(test, home)
                        when (page) {
                            "Home" -> host.select(TaskDestination.BREW)
                            "Curve" -> host.select(TaskDestination.CURVES)
                            "Extraction" -> host.extraction()
                            "History" -> host.select(TaskDestination.HISTORY)
                            "AppSettings" -> host.select(TaskDestination.SETTINGS)
                        }
                        home
                    }
                    test.waitForIdleSync()
                    awaitMain {
                        activity.window.decorView.isLaidOut && root(activity).height > 0 && !root(activity).isLayoutRequested &&
                            owner(activity) === service
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
                    when (page) {
                        "Home" -> detailChecks.warnings(home, fixture) { checkPage(home, it) }
                        "Curve" -> detailChecks.curves(home, fixture) { checkPage(home, it) }
                        "Extraction" -> {
                            onMain { TrendChartTextChecks.run(home, fixture) }
                            detailChecks.cancelStart(home, fixture)
                        }
                        "AppSettings" -> {
                            home = LanguageSelectorUiChecks(test).run(home) { home = it }
                            checkPage(home, "$fixture selectorRestored")
                        }
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
                                check(localTab.performClick())
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
                    } else if (page != "Home") MainPageTestHost(test, home).select(TaskDestination.BREW)
                }
            }
            check(completed == 96) { "Page matrix incomplete: $completed" }
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) {
                try { action() } catch (error: Throwable) {
                    if (failure == null) failure = error else failure!!.addSuppressed(error)
                }
            }
            cleanup { currentPage?.let { page -> onMain { page.finish() }; currentPage = null } }
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
            checkGeometry(activity, fixture, requireSecondaryAction = true)
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
                onMain { checkGeometry(activity, "$fixture scroll=$position", requireSecondaryAction = position == 0) }
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

    private fun checkGeometry(activity: Activity, fixture: String, requireSecondaryAction: Boolean) {
        val root = root(activity)
        val frame = descendants(root).filterIsInstance<TaskPageFrame>().single()
        fullyVisible(frame, fixture)
        fullyVisible(frame.pageContent, fixture)
        val expectedMode = if (activity.resources.configuration.screenWidthDp >= 600)
            TaskNavigationMode.RAIL else TaskNavigationMode.BOTTOM
        check(frame.mode == expectedMode) { "$fixture wrong navigation mode" }
        val targets = descendants(frame.navigation).filter { it.tag?.toString()?.startsWith("task-nav:") == true }
        val labels = listOf(R.string.task_home_tab, R.string.ui_curves, R.string.ui_history, R.string.ui_beans)
        check(targets.take(4).map { it.tag } == TaskNavigationModel.primary.map { "task-nav:${it.name}" })
        val content = frame.pageContent
        val contentBounds = Rect().also { check(content.getGlobalVisibleRect(it)) }
        if (activity is MainActivity) {
            val selected = when (activity.supportFragmentManager.primaryNavigationFragment) {
                is HomeFragment, is ExtractionFragment -> TaskDestination.BREW
                is CurveFragment -> TaskDestination.CURVES
                is HistoryFragment -> TaskDestination.HISTORY
                is BeanInventoryFragment -> TaskDestination.BEANS
                is ScaleFragment -> TaskDestination.SCALE
                is AppSettingsFragment -> TaskDestination.SETTINGS
                else -> error("Unknown active task Fragment")
            }
            targets.take(4).forEachIndexed { index, item ->
                check(item.isClickable && item.isFocusable && item.width >= HoyiUi.dp(activity, 48) && item.height >= HoyiUi.dp(activity, 48))
                check(item.isSelected == (TaskNavigationModel.primary[index] == selected))
                fullyVisible(item, fixture)
                val label = descendants(item).filterIsInstance<TextView>().single()
                check(label.text.toString() == activity.getString(labels[index]))
                LanguageUiLayoutChecks.textFits(label, fixture)
                fullyVisible(label, fixture)
            }
            if (frame.toolsInTopBar) {
                val menu = descendants(content).single { it.tag == "task-tools-menu" && it.isShown }
                fullyVisible(menu, fixture)
                check(menu.width >= HoyiUi.dp(activity, 48) && menu.height >= HoyiUi.dp(activity, 48))
            } else {
                check(targets.drop(4).map { it.tag } == TaskNavigationModel.tools.map { "task-nav:${it.name}" })
                targets.drop(4).forEach { fullyVisible(it, fixture) }
            }
            // Root content and fixed actions may not cross the visible task navigation.
            val navigationBounds = Rect().also { check(frame.navigation.getGlobalVisibleRect(it)) }
            check(!Rect.intersects(contentBounds, navigationBounds)) { "$fixture content overlaps navigation" }
        } else {
            check(activity is MachineSettingsActivity) { "$fixture unexpected secondary page: ${activity.javaClass.simpleName}" }
            check(!frame.navigation.isShown && targets.none { it.isShown }) { "$fixture secondary page exposed the root task rail" }
            if (requireSecondaryAction) {
                val minimum = HoyiUi.dp(activity, 48)
                val controls = descendants(content).filter { view ->
                    view.isShown && view.isEnabled && view.isClickable &&
                        (!view.contentDescription.isNullOrBlank() || (view as? TextView)?.text?.isNotBlank() == true)
                }
                val control = controls.firstOrNull { it.contentDescription == activity.getString(R.string.ui_back) }
                    ?: controls.firstOrNull { it.width >= minimum && it.height >= minimum }
                    ?: error("$fixture no visible secondary-page action")
                check(control.width >= minimum && control.height >= minimum) { "$fixture small secondary action: ${control.tag}" }
                fullyVisible(control, fixture)
            }
        }
        // Content and fixed actions remain inside the frame in both root and secondary pages.
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            if (child.visibility != View.VISIBLE) continue
            check(child.height > 0 && child.top >= 0 && child.bottom <= content.height) {
                "$fixture content/fixed action overlaps navigation or has no height"
            }
            if (child is Button) {
                fullyVisible(child, fixture)
                LanguageUiLayoutChecks.textFits(child, fixture)
                check(child.height >= HoyiUi.dp(activity, 48))
            }
        }
        if (activity is MainActivity && activity.supportFragmentManager.primaryNavigationFragment is ExtractionFragment) {
            val page = activity.supportFragmentManager.primaryNavigationFragment as ExtractionFragment
            val start = ExtractionFragment::class.java.getDeclaredField("start").apply { isAccessible = true }.get(page) as Button
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

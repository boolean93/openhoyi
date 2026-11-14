package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Rect
import android.graphics.Bitmap
import java.io.File
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

    private fun compactHomeGeometry(host: MainPageTestHost, activeCup: Boolean, lastCurveRecorded: Boolean = false) {
        val activity = host.activity
        val real = android.graphics.Point()
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.getRealSize(real)
        val density = activity.resources.displayMetrics.density
        val widthDp = real.x / density
        val heightDp = real.y / density
        val state = if (activeCup) "running" else "idle"
        val geometry = "state=$state displayPx=${real.x}x${real.y} density=$density dp=${widthDp}x${heightDp} lastCurveRecorded=$lastCurveRecorded fontScale=${activity.resources.configuration.fontScale}"
        if (kotlin.math.abs(widthDp - 720f) > 1f ||
            kotlin.math.abs(heightDp - 360f) > 1f) {
            test.sendStatus(0, Bundle().apply { putString("stream", "MAIN_HOME_720_GEOMETRY_SKIP $geometry\n") })
            return
        }
        val home = host.select(TaskDestination.BREW) as HomeFragment
        if (lastCurveRecorded) {
            val deadline = SystemClock.elapsedRealtime() + 5000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                test.runOnMainSync {
                    ready = home.view?.findViewWithTag<View>("product/home/last-curve/preview")?.isShown == true
                }
                if (!ready) SystemClock.sleep(50)
            }
            check(ready) { "Observed Mock use did not render the last-curve preview" }
        }
        val directory = File(requireNotNull(test.targetContext.getExternalFilesDir(null)), "compact-home-navigation")
            .apply { check(mkdirs() || isDirectory) }
        val fixture = if (lastCurveRecorded) "idle-last-curve" else state
        val committedFrame = NativeWindowFrameEvidence(test).committedFrame(requireNotNull(home.view))
        File(directory, "$fixture-frame.json").writeText(committedFrame.toString(2))
        val screenshot = requireNotNull(test.uiAutomation.takeScreenshot())
        try { File(directory, "$fixture.png").outputStream().use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { screenshot.recycle() }
        var geometryFailure: Throwable? = null
        test.runOnMainSync { try {
            val root = requireNotNull(home.view)
            val viewport = Rect()
            check(root.getGlobalVisibleRect(viewport) && !viewport.isEmpty)
            val tags = listOf("machine-status", "connect", "brew-boiler", "steam-boiler",
                "brew-boiler/toggle", "steam-boiler/toggle", "machine-mode/control", "machine-mode/logic") +
                (if (activeCup) emptyList() else listOf("quick-launch") + (1..5).flatMap {
                    listOf("direct-preset/$it", "direct-preset/$it/preview")
                }) + (if (lastCurveRecorded) listOf("last-curve/preview", "quick-recipe") else emptyList())
            val bounds = tags.associateWith { name ->
                val viewTag = if (name.startsWith("machine-mode/")) name else "product/home/$name"
                val view = requireNotNull(descendants(root).singleOrNull { it.tag == viewTag }) {
                    "Missing actual Home view $viewTag"
                }
                val visible = Rect()
                val shown = view.isShown
                val globalVisible = view.getGlobalVisibleRect(visible)
                check(shown && globalVisible &&
                    visible.width() == view.width && visible.height() == view.height &&
                    visible.top >= viewport.top && visible.bottom <= viewport.bottom) {
                    val ancestors = generateSequence(view as View?) { it.parent as? View }.joinToString { ancestor ->
                        val rect = Rect(); val visibleResult = ancestor.getGlobalVisibleRect(rect)
                        val location = IntArray(2); ancestor.getLocationOnScreen(location)
                        val clipping = (ancestor as? ViewGroup)?.let { "clipChildren=${it.clipChildren},clipPadding=${it.clipToPadding}" }
                        "${ancestor.javaClass.simpleName}(tag=${ancestor.tag},visibility=${ancestor.visibility},bounds=${ancestor.left}/${ancestor.top}/${ancestor.right}/${ancestor.bottom},padding=${ancestor.paddingLeft}/${ancestor.paddingTop}/${ancestor.paddingRight}/${ancestor.paddingBottom},scroll=${ancestor.scrollX}/${ancestor.scrollY},screen=${location.toList()},global=$visibleResult:$rect,$clipping)"
                    }
                    "720x360 Home $name outside page viewport: $visible viewport=$viewport view=${view.width}x${view.height} shown=$shown globalVisible=$globalVisible attached=${view.isAttachedToWindow} ancestors=$ancestors"
                }
                if (view.isClickable) check(view.width >= 48 * density && view.height >= 48 * density) {
                    "720x360 Home $name has a touch target smaller than 48dp: ${view.width}x${view.height} density=$density"
                }
                if (name.startsWith("machine-mode/")) {
                    val toggle = view as HoyiSwitch
                    check(toggle.text.isNotBlank()) { "Machine mode label missing: $name" }
                    if (activeCup) check(!toggle.isEnabled) { "Machine mode must remain disabled during a cup: $name" }
                }
                visible
            }
            check(!Rect.intersects(bounds.getValue("machine-mode/control"), bounds.getValue("machine-mode/logic"))) {
                "Machine mode switches overlap"
            }
            if (!activeCup) {
                val quickHeading = requireNotNull(root.findViewWithTag<View>("product/home/compact-quick-heading"))
                val modes = requireNotNull(root.findViewWithTag<View>("product/home/compact-mode-group"))
                val quickRect = Rect(); val modeRect = Rect()
                check(quickHeading.getGlobalVisibleRect(quickRect) && modes.getGlobalVisibleRect(modeRect))
                if (quickRect.top < modeRect.bottom && modeRect.top < quickRect.bottom)
                    check(modeRect.left - quickRect.right >= 16 * density - 1) { "Compact heading groups lost their 16dp gap: $quickRect / $modeRect" }
            }
            val isolatedModes = MachineModeControls(activity) { error("No writes in label stability check") }
            isolatedModes.render(null)
            isolatedModes.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            isolatedModes.layout(0, 0, isolatedModes.measuredWidth, isolatedModes.measuredHeight)
            val modeLabels = descendants(isolatedModes).filterIsInstance<HoyiSwitch>()
            val labelLayouts = modeLabels.map { requireNotNull(it.layout) }
            repeat(20) { isolatedModes.render(null) }
            check(modeLabels.none { it.isLayoutRequested }) { "Unchanged machine labels requested layout" }
            check(modeLabels.map { it.layout } == labelLayouts) { "Unchanged machine labels discarded their text layouts" }
            File(directory, "$fixture.txt").writeText(geometry + "\n" +
                bounds.entries.joinToString("\n") { (tag, rect) -> "$tag=$rect" } + "\n")
            if (activeCup) {
                val stop = descendants(root).singleOrNull { it.tag == "product/home/dock" && it.isShown }
                    ?: descendants(root).filterIsInstance<android.widget.Button>().singleOrNull {
                        it.text.toString() == activity.getString(R.string.home_stop) && it.isShown
                    }
                    ?: error("Active Mock cup has no fixed Home action")
                val stopBounds = Rect()
                check(stop.getGlobalVisibleRect(stopBounds) && stopBounds.height() > 0)
                check(stopBounds.width() == stop.width && stopBounds.height() == stop.height &&
                    stop.width >= 48 * density && stop.height >= 48 * density)
                check(home.view?.findViewWithTag<View>("product/home/last-curve")?.isShown == false) {
                    "Last-curve action must hide with presets during an active cup"
                }
                for (name in tags) check(!Rect.intersects(stopBounds, bounds.getValue(name))) {
                    "Active Home action overlaps $name"
                }
            }
        } catch (failure: Throwable) { geometryFailure = failure } }
        geometryFailure?.let { throw it }
        test.sendStatus(0, Bundle().apply { putString("stream", "MAIN_HOME_720_GEOMETRY_PASS $geometry\n") })
    }

    /** Read-only compact viewport check against existing user presets; never seeds or starts a cup. */
    fun runHomeOnly() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
        var activity: MainActivity? = null
        try {
            test.runOnMainSync {
                test.targetContext.startActivity(android.content.Intent(test.targetContext, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, TaskDestination.BREW.name))
            }
            activity = test.waitForMonitorWithTimeout(monitor, 10000) as? MainActivity
                ?: error("Home Activity launch timed out after 10s")
            test.waitForIdleSync()
            compactHomeGeometry(MainPageTestHost(test, requireNotNull(activity)), activeCup = false)
        } finally {
            test.removeMonitor(monitor)
            activity?.let { page -> test.runOnMainSync { page.finish() } }
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

    private fun shellThemeStates(activity: MainActivity) {
        for (night in listOf(false, true)) {
            val config = android.content.res.Configuration(activity.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            val context = android.view.ContextThemeWrapper(activity.createConfigurationContext(config), R.style.MobileTheme)
            val chrome = TaskShellVisualStyle(context)
            val action = android.widget.Button(context)
            chrome.action(action)
            action.isEnabled = true
            val enabledInk = action.currentTextColor
            action.isEnabled = false
            check(action.currentTextColor != enabledInk && action.currentTextColor != chrome.surface) {
                "Device action needs a distinct readable disabled state: night=$night"
            }
            action.isEnabled = true
            check(action.currentTextColor == enabledInk)
            val size = HoyiUi.dp(activity, 24)
            fun paintedPixels(icon: HomeVisualIcon): Int {
                icon.layout(0, 0, size, size)
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                try {
                    icon.draw(android.graphics.Canvas(bitmap))
                    return (0 until size).sumOf { x -> (0 until size).count { y -> bitmap.getPixel(x,y) ushr 24 > 0 } }
                } finally { bitmap.recycle() }
            }
            val regular = paintedPixels(HomeVisualIcon(context, "bean", chrome.ink))
            val shell = paintedPixels(HomeVisualIcon(context, "bean", chrome.ink, chrome.iconStrokeDp, chrome.solidIcons))
            check(shell > regular) { "Shell glyph should have more visual weight: night=$night" }
        }
    }

    /** Same view tree crosses rail/short-rail/phone/rail without touching device controls. */
    private fun adaptiveRailPlacement(activity: MainActivity) {
        val frame = TaskPageFrame.host(activity, TaskProductVisualStyle(activity), { TaskDestination.BREW }, {})
        val status = TaskShellStatusView(activity, { error("Unexpected sleep") }, { error("Unexpected scale") },
            { error("Unexpected reconnect") }, { error("Unexpected disconnect") })
        frame.installShellStatus(status)
        val draft = EditText(activity).apply { setText("unsent draft"); setSelection(3) }
        frame.pageContent.addView(draft)
        for ((widthDp, heightDp) in listOf(1137 to 668, 1137 to 240, 360 to 640, 1137 to 668)) {
            val width = HoyiUi.dp(activity, widthDp)
            val height = HoyiUi.dp(activity, heightDp)
            frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, width, height)
            check(draft.parent === frame.pageContent && draft.text.toString() == "unsent draft" && draft.selectionStart == 3)
            check(descendants(frame.navigation).count { it.tag?.toString()?.startsWith("task-nav:") == true } == 6)
            fun font(view: android.widget.TextView, sizeSp: Int) {
                val expected = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                    sizeSp.toFloat(), activity.resources.displayMetrics)
                check(kotlin.math.abs(view.textSize - expected) < 0.5f) { "${view.tag} incorrect size after $widthDp/$heightDp" }
            }
            val rail = widthDp >= 600
            descendants(frame.navigation).filterIsInstance<android.widget.TextView>().forEach { font(it, if (rail) 16 else 14) }
            for (tag in listOf("task-shell-coffee-name", "task-shell-scale-name", "task-shell-sleep",
                "task-shell-scale-reconnect", "task-shell-scale-disconnect")) {
                font(requireNotNull(status.findViewWithTag<android.widget.TextView>(tag)), if (rail) 16 else 14)
            }
            for (tag in listOf("task-shell-coffee-status", "task-shell-scale")) {
                font(requireNotNull(status.findViewWithTag<android.widget.TextView>(tag)), if (rail) 15 else 14)
            }
            if (widthDp >= 600) {
                check(frame.mode == TaskNavigationMode.RAIL)
                val scroll = frame.findViewWithTag<android.widget.ScrollView>("task-rail-scroll")
                val column = scroll.getChildAt(0) as android.widget.LinearLayout
                val inset = if (activity.resources.configuration.smallestScreenWidthDp >= 600) HoyiUi.dp(activity, 4) else 0
                val contentWidth = HoyiUi.dp(activity, 104)
                check(scroll.paddingLeft == inset && scroll.paddingRight == inset)
                check(scroll.width == contentWidth + 2 * inset && column.width == contentWidth)
                check(frame.navigation.width == contentWidth && status.width == contentWidth) { "Rail content width changed" }
                check(status.parent === column && frame.navigation.parent === column) { "Devices left the navigation rail" }
                check(status.top >= frame.navigation.bottom && scroll.right <= frame.pageContent.left)
                if (heightDp == 668) check(column.height <= scroll.height) { "Tablet rail should fit without scrolling" }
                else check(column.height > scroll.height) { "Short rail must scroll as a whole" }
                val targets = descendants(column).filter {
                    it.tag?.toString()?.startsWith("task-nav:") == true ||
                        it.tag in setOf("task-shell-coffee-facts", "task-shell-scale-facts", "task-shell-sleep",
                            "task-shell-scale-reconnect", "task-shell-scale-disconnect") && it.visibility == View.VISIBLE
                }
                for (target in targets) {
                    val bounds = Rect(0, 0, target.width, target.height)
                    column.offsetDescendantRectToMyCoords(target, bounds)
                    scroll.scrollTo(0, bounds.top)
                    check(bounds.top >= scroll.scrollY && bounds.bottom <= scroll.scrollY + scroll.height) {
                        "${target.tag} is unreachable in ${widthDp}x$heightDp"
                    }
                }
                scroll.scrollTo(0, 0)
            } else {
                check(frame.mode == TaskNavigationMode.BOTTOM && status.parent === frame.pageContent)
                check(frame.navigation.parent === frame)
            }
        }
    }

    /** Small UI regression: no extraction or device action is invoked. */
    fun runInlineOnly() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val host = MainPageTestHost.launch(test)
        val activity = host.activity
        try {
            test.runOnMainSync {
                shellThemeStates(activity)
                adaptiveRailPlacement(activity)
                for ((mode, compact, widthDp) in listOf(
                    Triple(TaskNavigationMode.RAIL, false, 80),
                    Triple(TaskNavigationMode.RAIL, true, 104),
                    Triple(TaskNavigationMode.BOTTOM, false, 360))) {
                    var selected = TaskDestination.BREW
                    val nav = TaskNavigationView(activity, TaskProductVisualStyle(activity),
                        destinationProvider = { selected }, onDestination = { selected = it })
                    nav.render(mode, includeTools = true, compactRail = compact)
                    val width = HoyiUi.dp(activity, widthDp)
                    nav.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    nav.layout(0, 0, width, nav.measuredHeight)
                    for (item in descendants(nav).filterIsInstance<android.widget.LinearLayout>()
                        .filter { it.tag?.toString()?.startsWith("task-nav:") == true }) {
                        val glyph = item.getChildAt(0)
                        val label = item.getChildAt(1) as android.widget.TextView
                        if (mode == TaskNavigationMode.RAIL) {
                            check(item.orientation == android.widget.LinearLayout.HORIZONTAL)
                            check(glyph.right <= label.left && label.right <= item.width) { "${item.tag} overlaps or overflows" }
                            check(kotlin.math.abs((glyph.top + glyph.bottom) - (label.top + label.bottom)) <= 2)
                        } else {
                            check(item.orientation == android.widget.LinearLayout.VERTICAL) { "${item.tag} is not stacked in bottom mode" }
                            check(glyph.bottom <= label.top && label.bottom <= item.height)
                            check(kotlin.math.abs((glyph.left + glyph.right) - (label.left + label.right)) <= 2)
                        }
                        check(label.width > 0 && label.layout.getEllipsisCount(label.lineCount - 1) == 0)
                        check(label.layout.height <= label.height && label.layout.getLineEnd(label.lineCount - 1) == label.text.length)
                        val destination = TaskDestination.valueOf(item.tag.toString().substringAfter(':'))
                        val fullLabel = if (destination == TaskDestination.SCALE) activity.getString(R.string.scale_title) else label.text
                        check(item.isFocusable && item.contentDescription == fullLabel)
                        if (destination == TaskDestination.SCALE) {
                            val expected = if (mode == TaskNavigationMode.BOTTOM && activity.resources.configuration.locales[0].language == "zh")
                                "秤" else activity.getString(R.string.scale_title)
                            check(label.text.toString() == expected) { "Wrong scale label in $mode: ${label.text}" }
                        }
                        check(item.isSelected == (destination == TaskDestination.BREW))
                        check(item.performClick() && selected == destination)
                    }
                }
            }
            for ((device, textTag) in listOf("coffee" to "name", "coffee" to "status", "scale" to "name", "scale" to "")) {
                host.select(TaskDestination.BEANS)
                test.runOnMainSync {
                    val root = activity.window.decorView
                    val suffix = if (textTag.isEmpty()) "" else "-$textTag"
                    val text = descendants(root).single { it.tag == "task-shell-$device$suffix" }
                    val facts = text.parent as android.widget.LinearLayout
                    check(facts.tag == "task-shell-$device-facts" && facts.isClickable && facts.isFocusable)
                    check(facts.orientation == android.widget.LinearLayout.VERTICAL && facts.childCount == 2)
                    check(facts.height >= HoyiUi.dp(activity, 48))
                    check(facts.performClick())
                }
                test.waitForIdleSync()
                val settings = host.active("root:SETTINGS") as AppSettingsFragment
                fun assertDevicesAnchor() {
                    test.runOnMainSync {
                        val root = requireNotNull(settings.view)
                        val heading = descendants(root).single { it.tag == "settings/heading/devices" }
                        val scroll = descendants(root).single { it.tag == "settings/document/scroll" } as android.widget.ScrollView
                        val visible = Rect()
                        check(heading.getGlobalVisibleRect(visible) && scroll.scrollY == 0) { "$device text did not target devices" }
                    }
                }
                assertDevicesAnchor()
                test.runOnMainSync { settings.openSection("machine") }
                test.waitForIdleSync()
                test.runOnMainSync {
                    val scroll = requireNotNull(settings.view).findViewWithTag<android.widget.ScrollView>("settings/document/scroll")
                    check(scroll.scrollY > 0) { "Machine section did not move away from devices before repeated entry" }
                }
                test.runOnMainSync {
                    check(descendants(activity.window.decorView).single { it.tag == "task-shell-$device-facts" }.performClick())
                }
                test.waitForIdleSync()
                assertDevicesAnchor()
                test.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
                test.waitForIdleSync()
                host.active("root:BEANS")
            }
            test.sendStatus(0, Bundle().apply { putString("stream", "NAV_INLINE_CHECKS_PASSED adaptiveRail=true draftRetained=true layouts=3 deviceTextEntries=4 repeatedAnchor=true backOrigin=true noExtraction=true\n") })
        } finally { test.runOnMainSync { activity.finish() }; test.waitForIdleSync() }
    }

    /** Bounded, non-extracting settings regression for a user's Mock tablet. */
    fun runSettingsOnly() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        test.sendStatus(0, Bundle().apply { putString("stream", "SETTINGS_NAVIGATION_START boundedLaunch=true noExtraction=true\n") })
        val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
        var activity: MainActivity? = null
        val prefs = test.targetContext.getSharedPreferences("scale_tool", android.content.Context.MODE_PRIVATE)
        val originalPrecision = prefs.getBoolean("one_decimal", false)
        try {
            test.runOnMainSync {
                test.targetContext.startActivity(android.content.Intent(test.targetContext, MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, TaskDestination.SETTINGS.name))
            }
            activity = test.waitForMonitorWithTimeout(monitor, 10000) as? MainActivity
                ?: error("Settings Activity launch timed out after 10s")
            val page = requireNotNull(activity)
            test.waitForIdleSync()
            test.sendStatus(0, Bundle().apply { putString("stream", "SETTINGS_NAVIGATION_LAUNCHED\n") })
            test.runOnMainSync {
                val settings = page.supportFragmentManager.primaryNavigationFragment as AppSettingsFragment
                val root = requireNotNull(settings.view)
                val machineIndex = descendants(root).single { it.tag == "settings/index/machine" }
                val scaleIndex = descendants(root).single { it.tag == "settings/index/scale" }
                val summary = descendants(root).single { it.tag == "settings-machine-summary" }
                val input = descendants(root).filterIsInstance<EditText>().first()
                val originalDraft = input.text.toString()
                input.setText("94")
                scaleIndex.performClick(); check(summary.isAttachedToWindow)
                machineIndex.performClick(); check(input.text.toString() == "94")
                val precision = descendants(root).filterIsInstance<android.widget.Switch>()
                    .single { it.tag == "product/settings/scale/precision" }
                precision.performClick()
                check(prefs.getBoolean("one_decimal", false) == !originalPrecision)
                page.openScaleFromSettings(); page.showExtraction()
                page.onBackPressedDispatcher.onBackPressed()
                check(page.supportFragmentManager.primaryNavigationFragment is ScaleFragment)
                page.onBackPressedDispatcher.onBackPressed()
                check(page.supportFragmentManager.primaryNavigationFragment is AppSettingsFragment)
                input.setText(originalDraft)
            }
            test.sendStatus(0, Bundle().apply { putString("stream", "SETTINGS_NAVIGATION_PASSED anchorDraft=true persistentStatus=true precisionSaved=true overlayFirst=true scaleOrigin=true noExtraction=true\n") })
        } finally {
            prefs.edit().putBoolean("one_decimal", originalPrecision).commit()
            test.removeMonitor(monitor)
            activity?.let { page -> test.runOnMainSync { page.finish() } }
        }
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
                check(curvePrefs.edit().putString("selected", "factory-v3-002").commit())
                DirectStartChecks(test).startOnce(host, service)
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
                check(beans.view === beanView) { "Bean inventory tab was rebuilt by tab navigation" }
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
            val idleDeadline = SystemClock.elapsedRealtime() + 5000
            while (ShotGate.active(service.shotState) && SystemClock.elapsedRealtime() < idleDeadline)
                SystemClock.sleep(50)
            check(!ShotGate.active(service.shotState)) { "Mock stop did not return to idle" }
            compactHomeGeometry(host, activeCup = false, lastCurveRecorded = true)
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
            test.runOnMainSync {
                val settings = activity.supportFragmentManager.primaryNavigationFragment as AppSettingsFragment
                val root = requireNotNull(settings.view)
                val machineIndex = descendants(root).single { it.tag == "settings/index/machine" }
                val scaleIndex = descendants(root).single { it.tag == "settings/index/scale" }
                val machineSummary = descendants(root).single { it.tag == "settings-machine-summary" }
                val inputs = descendants(root).filterIsInstance<EditText>()
                check(inputs.isNotEmpty())
                inputs.first().setText("94")
                scaleIndex.performClick()
                check(machineSummary.isAttachedToWindow) { "Document must retain connection/readback/write feedback" }
                machineIndex.performClick()
                check(inputs.first().text.toString() == "94") { "Anchor navigation discarded unsent draft" }
                activity.openScaleFromSettings()
                activity.showExtraction()
                activity.onBackPressedDispatcher.onBackPressed()
                check(activity.supportFragmentManager.primaryNavigationFragment is ScaleFragment) { "Back must dismiss overlay first" }
                activity.onBackPressedDispatcher.onBackPressed()
                check(activity.supportFragmentManager.primaryNavigationFragment is AppSettingsFragment) { "Scale tool must return to settings origin" }
            }
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

package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Rect
import android.graphics.Bitmap
import java.io.File
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView

import io.openhoyi.session.ExtractionState

/** Exercises the rendered six-tab host, never the redirect Activities. */
internal class MainNavigationChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun checkHomeBoilerTargetSteps(host: MainPageTestHost, service: MobileService) {
        val home = host.select(TaskDestination.BREW)
        val root = checkNotNull(home.view)
        val initial = checkNotNull(service.snapshot.settings)
        fun step(key: String, delta: Int, brew: Int, steam: Int) {
            val action = if (delta < 0) "decrease" else "increase"
            val button = checkNotNull(root.findViewWithTag<Button>("product/home/$key/target-$action"))
            val deadline = SystemClock.elapsedRealtime() + 5000
            var clicked = false
            while (!clicked && SystemClock.elapsedRealtime() < deadline) {
                test.runOnMainSync {
                    if (button.isShown && button.isEnabled) clicked = button.performClick()
                }
                if (!clicked) SystemClock.sleep(50)
            }
            check(clicked) { "$key target $action never accepted a Mock input" }
            test.runOnMainSync {
                val observed = checkNotNull(service.snapshot.settings)
                check(observed.brewTemperatureC == brew && observed.steamTemperatureC == steam) {
                    "$key target $action changed the wrong Mock temperature: $observed"
                }
                check(observed.flags == initial.flags) { "Temperature changes must not toggle heating" }
                val target = checkNotNull(root.findViewWithTag<TextView>("product/home/$key/target"))
                val expected = if (key == "brew-boiler") brew else steam
                check(target.text.endsWith("$expected °C")) { "$key did not display the observed target" }
            }
        }
        val brew = initial.brewTemperatureC
        val steam = initial.steamTemperatureC
        val brewDelta = if (brew == BoilerTargetSteps.Boiler.BREW.limits.last) -1 else 1
        val steamDelta = if (steam == BoilerTargetSteps.Boiler.STEAM.limits.first) 1 else -1
        val brewAction = if (brewDelta < 0) "decrease" else "increase"
        val brewButton = checkNotNull(root.findViewWithTag<HomeBoilerTargetButton>("product/home/brew-boiler/target-$brewAction"))
        test.runOnMainSync {
            val baseline = checkNotNull(service.homeBoilerTargetInteraction(BoilerTargetSteps.Boiler.BREW, brewDelta))
            fun replaceBaseline() {
                brewButton.renderedIdentity = Any()
                brewButton.renderedIdentity = baseline
            }
            brewButton.renderedIdentity = baseline
            val now = SystemClock.uptimeMillis()
            val touch = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, 20f, 20f, 0)
            try {
                brewButton.onTouchEvent(touch)
                replaceBaseline()
                check(!brewButton.performClick()) { "An old touch must not submit a target for the new baseline" }
                touch.action = android.view.MotionEvent.ACTION_CANCEL
                brewButton.onTouchEvent(touch)
            } finally { touch.recycle() }
            val confirm = android.view.KeyEvent.KEYCODE_ENTER
            brewButton.onKeyDown(confirm, android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, confirm))
            replaceBaseline()
            check(!brewButton.performClick()) { "An old keyboard press must not submit a target for the new baseline" }
            brewButton.onKeyUp(confirm, android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, confirm))
            check(service.snapshot.settings?.brewTemperatureC == brew)
        }
        step("brew-boiler", brewDelta, brew + brewDelta, steam)
        step("brew-boiler", -brewDelta, brew, steam)
        step("steam-boiler", steamDelta, brew, steam + steamDelta)
        step("steam-boiler", -steamDelta, brew, steam)
    }

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
            check(root.findViewWithTag<View>("product/home/connect") == null) {
                "Home must not duplicate the shell connection/retry controls"
            }
            val duplicateStatus = requireNotNull(root.findViewWithTag<TextView>("product/home/service-status"))
            check(!duplicateStatus.isShown || duplicateStatus.text.toString() ==
                home.getString(R.string.home_manual_shot_status)) { "Home duplicated the shell machine status" }
            val tags = listOf("brew-boiler", "steam-boiler",
                "brew-boiler/toggle", "steam-boiler/toggle", "machine-mode/control", "machine-mode/logic") +
                (if (activeCup) emptyList() else listOf("quick-launch") + (1..4).map { "direct-preset/$it" }) + (if (lastCurveRecorded) listOf("quick-recipe") else emptyList())
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
                    val toggle = view as CurveEditorSegments
                    check(toggle.text.isNotBlank()) { "Machine mode label missing: $name" }
                    if (activeCup) check(!toggle.isEnabled) { "Machine mode must remain disabled during a cup: $name" }
                }
                visible
            }
            check(!Rect.intersects(bounds.getValue("machine-mode/control"), bounds.getValue("machine-mode/logic"))) {
                "Machine mode switches overlap"
            }
            check(root.findViewWithTag<View>("product/home/quick-title") == null)
            check(root.findViewWithTag<View>("product/home/preview-legend") == null)
            val modeGroup = requireNotNull(root.findViewWithTag<View>("product/home/compact-mode-group"))
            val quickRow = requireNotNull(root.findViewWithTag<View>("product/home/quick-extraction"))
            check(modeGroup.isShown && quickRow.isShown == !activeCup)
            val isolatedModes = MachineModeControls(activity) { error("No writes in label stability check") }
            isolatedModes.render(null)
            isolatedModes.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            isolatedModes.layout(0, 0, isolatedModes.measuredWidth, isolatedModes.measuredHeight)
            val modeLabels = descendants(isolatedModes).filterIsInstance<CurveEditorSegments>()
            check(modeLabels.map { it.text.toString() } == listOf(
                activity.getString(R.string.curve_control_pressure), activity.getString(R.string.curve_control_constant)))
            check(modeLabels.all { !it.isEnabled && it.alpha < 1f })
            check(modeLabels.all { it.contentDescription == activity.getString(R.string.machine_mode_missing) })
            if (android.os.Build.VERSION.SDK_INT >= 30) check(modeLabels.all {
                it.stateDescription == activity.getString(R.string.machine_mode_missing)
            })
            val labels = modeLabels.flatMap { descendants(it).filterIsInstance<android.widget.TextView>() }
            check(labels.size == 4 && labels.all { !it.isEnabled && !it.isClickable })
            modeLabels.forEach { it.selectFromUser(!it.checked) }
            check(modeLabels.none { it.checked }) { "Disabled mode changed selection" }
            val labelLayouts = labels.map { requireNotNull(it.layout) }
            repeat(20) { isolatedModes.render(null) }
            check(modeLabels.none { it.isLayoutRequested }) { "Unchanged machine labels requested layout" }
            check(labels.map { it.layout } == labelLayouts) { "Unchanged machine labels discarded their text layouts" }
            val owner = HomeFragment::class.java.getDeclaredField("service")
                .apply { isAccessible = true }.get(home) as? MobileService
            val original = owner?.snapshot
            if (owner != null && original != null) {
                val snapshotField = MobileService::class.java.getDeclaredField("snapshot").apply { isAccessible = true }
                try {
                    for (state in listOf(io.openhoyi.session.DeviceState.DISCONNECTED,
                        io.openhoyi.session.DeviceState.CONNECTING, io.openhoyi.session.DeviceState.SYNCHRONIZING)) {
                        snapshotField.set(owner, original.copy(coffeeState = state, settings = null))
                        isolatedModes.render(owner)
                        check(modeLabels.all { !it.isEnabled && it.text.toString() != "—" })
                        check(modeLabels.all { it.contentDescription == activity.getString(R.string.machine_mode_missing) })
                    }
                    original.settings?.let { settings ->
                        for ((bits, labels) in listOf(
                            0x00 to listOf(R.string.curve_control_pressure, R.string.curve_control_constant),
                            0x80 to listOf(R.string.curve_control_pressure, R.string.curve_control_follow),
                            0x40 to listOf(R.string.curve_control_flow, R.string.curve_control_slow),
                            0xC0 to listOf(R.string.curve_control_flow, R.string.curve_control_fast))) {
                            snapshotField.set(owner, original.copy(settings = settings.copy(flags = bits)))
                            isolatedModes.render(owner)
                            check(modeLabels.map { it.text.toString() } == labels.map(activity::getString))
                            check(modeLabels.map { it.checked } == listOf(bits and 0x40 != 0, bits and 0x80 != 0))
                            check(modeLabels.all { it.isEnabled == MachineSettingsEditGate.allowed(owner) })
                            // Switch restores its platform on/off state description when the override is cleared.
                            if (android.os.Build.VERSION.SDK_INT >= 30) check(modeLabels.all {
                                it.stateDescription != activity.getString(R.string.machine_mode_missing)
                            })
                        }
                    }
                } finally {
                    snapshotField.set(owner, original)
                    isolatedModes.render(owner)
                }
            }
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
            test.runOnMainSync {
                val page = requireNotNull(activity)
                val controls = MachineModeControls(page) { error("Disabled controls must not dispatch") }
                controls.render(null)
                val width = (page.resources.displayMetrics.density * 240).toInt()
                controls.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                controls.layout(0, 0, controls.measuredWidth, controls.measuredHeight)
                val segments = descendants(controls).filterIsInstance<CurveEditorSegments>()
                check(segments.size == 2 && segments.none { it.isEnabled })
                segments.forEach { segment ->
                    segment.selectFromUser(true)
                    check(!segment.checked)
                    val labels = descendants(segment).filterIsInstance<android.widget.TextView>()
                    check(labels.size == 2 && labels.all { !it.isEnabled && !it.isClickable && it.lineCount == 1 })
                }
                var calls = 0
                val segment = CurveEditorSegments(page, TaskProductVisualStyle(page), "压力", "流量")
                segment.onSelected = { calls++ }
                segment.selectFromUser(true); check(segment.checked && calls == 1)
                segment.select(false); check(calls == 1)
                segment.isEnabled = false; segment.selectFromUser(true); check(!segment.checked && calls == 1)
                segment.isEnabled = true; segment.selectFromUser(true); check(segment.checked && calls == 2)
            }
            compactHomeGeometry(MainPageTestHost(test, requireNotNull(activity)), activeCup = false)
        } finally {
            test.removeMonitor(monitor)
            activity?.let { page -> test.runOnMainSync { page.finish() } }
        }
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
                    val root = requireNotNull(settings.view)
                    val machine = root.findViewWithTag<View>("settings/heading/machine")
                    val devices = root.findViewWithTag<View>("settings/heading/devices")
                    check(machine.getGlobalVisibleRect(Rect()) && !devices.getGlobalVisibleRect(Rect())) {
                        "Machine deep link must switch the active category before repeated Device entry"
                    }
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
                val machineIndex = descendants(root).single { it.tag == "settings/category/machine" }
                val scaleIndex = descendants(root).single { it.tag == "settings/category/device" }
                val summary = descendants(root).single { it.tag == "settings-machine-summary" }
                check(SettingsCategory.entries.all { root.findViewWithTag<View>("settings/category/${it.key}").isShown })
                for (boiler in listOf("brew", "steam")) {
                    val row = root.findViewWithTag<View>("product/settings/machine/$boiler/row") as SettingsBoilerTargetRow
                    val controls = descendants(row)
                    check(controls.none { it is EditText })
                    check(controls.count { it.tag == "settings/machine/target-step/-1" } == 1 &&
                        controls.count { it.tag == "settings/machine/target-step/1" } == 1)
                }
                val explanation = root.findViewWithTag<Button>("settings/details/scale/action")
                val tablet = root.findViewWithTag<View>("settings/document/category-sidebar").isShown &&
                    page.resources.configuration.smallestScreenWidthDp >= 600
                check(explanation.text.isEmpty() == tablet && !explanation.contentDescription.isNullOrBlank())
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

    private fun checkHistoryLanding(host: MainPageTestHost, history: HistoryFragment, previousId: String? = null): String? {
        var selectedId: String? = null
        test.runOnMainSync {
            val page = requireNotNull(history.view)
            val collection = requireNotNull(page.findViewWithTag<TaskCollectionLayout>("task-collection"))
            val list = requireNotNull(page.findViewWithTag<ListView>("product/history/list"))
            val detail = host.viewField<View>(history, "detailContent")
            check(collection.browserBody.isShown && !collection.detailRequested)
            val entries = (0 until list.count).map { list.getItemAtPosition(it) as ShotHistory.Entry }
            val restored = entries.firstOrNull { it.id == previousId }
            val expected = restored ?: if (collection.detailBody.isShown) entries.firstOrNull() else null
            selectedId = expected?.id
            check(detail.tag == expected?.let { "history-selection-${it.id}" })
            for (index in 0 until list.childCount) {
                val entry = entries[list.firstVisiblePosition + index]
                val row = list.getChildAt(index)
                check(row.isActivated == (entry.id == selectedId))
                val prefix = "product/history/list/${entry.id}"
                val time = requireNotNull(row.findViewWithTag<TextView>("$prefix/time"))
                check(Regex("[0-9]{2}:[0-9]{2}:[0-9]{2}$").containsMatchIn(time.text))
                val timing = requireNotNull(row.findViewWithTag<TextView>("$prefix/metadata/timing"))
                check(timing.text.toString().replace('\u00a0', ' ') == host.activity.shotTimingSummary(entry))
                val scale = requireNotNull(row.findViewWithTag<TextView>("$prefix/metadata/scale"))
                check(scale.visibility == View.VISIBLE && (entry.weightHundredthsGram == null) == scale.text.endsWith("—"))
                val status = requireNotNull(row.findViewWithTag<TextView>("$prefix/status"))
                check((entry.status == ShotHistory.Status.ENDED) == (status.visibility == View.GONE))
            }
        }
        return selectedId
    }

    private fun checkHistoryBrowser(host: MainPageTestHost, history: HistoryFragment) {
        val page = requireNotNull(history.view)
        val activity = host.activity

        fun source(label: Int) = descendants(page).filterIsInstance<TextView>()
            .single { it.isShown && it.text.toString() == activity.getString(label) }

        fun checkSource(label: Int, rules: Int) {
            test.runOnMainSync {
                val collection = requireNotNull(page.findViewWithTag<TaskCollectionLayout>("task-collection"))
                val controls = requireNotNull(collection.browserBody.findViewWithTag<ViewGroup>("product/history/sources"))
                check(page.findViewWithTag<View>("product/history/header") == null)
                check(collection.browserBody.findViewWithTag<View>("product/history/compare-entry") == null)
                check(source(label).isSelected)
                val count = requireNotNull(controls.findViewWithTag<TextView>("product/history/count"))
                val list = requireNotNull(collection.browserBody.findViewWithTag<ListView>("product/history/list"))
                check(count.text.toString() == list.count.toString() || count.text.toString() == "—")
                val visible = Rect()
                val minimum = HoyiUi.dp(activity, 48)
                check(count.isShown && count.getGlobalVisibleRect(visible) &&
                    visible.width() >= minimum && visible.height() >= minimum)
                check(controls.findViewWithTag<View>("product/history/io-menu")?.isShown == true)
                check(count.performClick())
                val tracked = MainActivity::class.java.getDeclaredField("transientDialogs").apply { isAccessible = true }
                val dialog = (tracked.get(activity) as List<*>)
                    .filterIsInstance<android.app.AlertDialog>().last { it.isShowing }
                check(dialog.findViewById<TextView>(android.R.id.message).text.toString()
                    .contains(activity.getString(rules)))
                dialog.dismiss()
                if (list.count == 0) {
                    val empty = requireNotNull(collection.browserBody.findViewWithTag<Button>("product/history/empty-compare"))
                    check(empty.isShown)
                    check(empty.performClick())
                }
            }
            test.waitForIdleSync()
            test.runOnMainSync {
                val list = requireNotNull(page.findViewWithTag<ListView>("product/history/list"))
                if (list.count == 0) {
                    val picker = activity.supportFragmentManager.findFragmentByTag("history-cup-picker") as? androidx.fragment.app.DialogFragment
                    checkNotNull(picker) { "Empty history did not open the cup picker" }
                    picker.dismiss()
                }
            }
            test.waitForIdleSync()
        }

        checkSource(R.string.history_local_tab, R.string.history_browser_local_retention)
        test.runOnMainSync { check(source(R.string.journal_tab).performClick()) }
        test.waitForIdleSync()
        checkSource(R.string.journal_tab, R.string.history_browser_journal_retention)
        test.runOnMainSync { check(source(R.string.history_local_tab).performClick()) }
        test.waitForIdleSync()
    }

    private fun checkSettingsLabelsStayIdle(host: MainPageTestHost) {
        val root = requireNotNull(host.select(TaskDestination.SETTINGS).view)
        val scanTitles = listOf(R.string.settings_device_nearby, R.string.settings_category_scan_nearby)
            .map(host.activity::getString)
        val watched = test.run {
            lateinit var children: List<TextView>
            runOnMainSync {
                children = listOf(descendants(root).filterIsInstance<TextView>()
                    .single { it.isShown && it.text.toString() in scanTitles },
                    requireNotNull(root.findViewWithTag("product/devices/coffee/retry")))
            }
            children
        }
        var sameValueWrites = 0
        val watchers = watched.map { view ->
            object : android.text.TextWatcher {
                private var before = ""
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
                    before = text.toString()
                }
                override fun onTextChanged(text: CharSequence?, start: Int, beforeCount: Int, count: Int) {
                    if (text.toString() == before) sameValueWrites++
                }
                override fun afterTextChanged(text: android.text.Editable?) = Unit
            }.also { watcher -> test.runOnMainSync { view.addTextChangedListener(watcher) } }
        }
        val contentEvents = java.util.concurrent.atomic.AtomicInteger()
        var layouts = 0
        val layoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { layouts++ }
        val automation = test.uiAutomation
        try {
            test.runOnMainSync { root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener) }
            automation.setOnAccessibilityEventListener { event ->
                if (event.packageName?.toString() == test.targetContext.packageName &&
                    event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    contentEvents.incrementAndGet()
                }
            }
            // The visible Settings observer polls at 500 ms. Wait through at least two callbacks.
            SystemClock.sleep(1200)
            test.runOnMainSync { check(sameValueWrites == 0) { "Idle Settings rewrote unchanged label text $sameValueWrites times" } }
        } finally {
            automation.setOnAccessibilityEventListener(null)
            test.runOnMainSync {
                root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                watched.zip(watchers).forEach { (view, watcher) -> view.removeTextChangedListener(watcher) }
            }
        }
        test.sendStatus(0, Bundle().apply { putString("stream", "SETTINGS_IDLE_LABELS_PASSED sameValueWrites=0 contentEvents=${contentEvents.get()} globalLayouts=$layouts windowMs=1200\n") })
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
            checkSettingsLabelsStayIdle(host)
            val curve = host.select(TaskDestination.CURVES)
            var search: EditText? = null
            test.runOnMainSync {
                search = descendants(requireNotNull(curve.view)).filterIsInstance<EditText>().firstOrNull()
                check(search != null) { "Curve filter search is absent" }
                search!!.setText("navigation-state")
            }
            val history = host.select(TaskDestination.HISTORY) as HistoryFragment
            checkHistoryBrowser(host, history)
            val initialHistorySelection = checkHistoryLanding(host, history)
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
                checkHomeBoilerTargetSteps(host, service)
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
            checkHistoryLanding(host, history, initialHistorySelection)
            if (historyPosition > 0) check(historyList?.firstVisiblePosition == historyPosition) {
                "Nonzero History list position was lost"
            }
            ManualLiveNavigationChecks(test).run(MainPageTestHost(test, activity), service, includeDraftEditors = false)
            MainPageTestHost(test, activity).select(TaskDestination.SETTINGS)
            test.runOnMainSync {
                val settings = activity.supportFragmentManager.primaryNavigationFragment as AppSettingsFragment
                val root = requireNotNull(settings.view)
                val machineIndex = descendants(root).single { it.tag == "settings/category/machine" }
                val scaleIndex = descendants(root).single { it.tag == "settings/category/device" }
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

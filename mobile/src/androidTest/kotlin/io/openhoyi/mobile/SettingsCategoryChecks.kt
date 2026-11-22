package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Representative real phone/tablet Settings views; never runs a device or update action. */
internal class SettingsCategoryChecks(private val test: Instrumentation) {
    private fun <T> main(block: () -> T): T = BoundedInstrumentationMain.run("Settings category proof", 4000, block)
    private fun await(label: String, ready: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 4500
        while (!main(ready)) {
            check(SystemClock.elapsedRealtime() < until) { "Expected Settings state: $label" }
            SystemClock.sleep(25)
        }
        SystemClock.sleep(180)
    }

    fun run(wide: Boolean, dark: Boolean, short: Boolean) {
        val context = test.targetContext
        val app = context.applicationContext as MobileApplication
        val out = File(context.filesDir, "settings-categories").apply { check(mkdirs() || isDirectory) }
        val receipts = JSONObject()
        try {
            if (wide) {
                receipts.put("modeAccentContrast", JSONObject(main { settingsAccentContrast(context) }))
                receipts.put("weeklyTileContrast", JSONObject(main { settingsWeeklyTileContrast(context) }))
            }
            check(SettingsTemperatureDraft.differs(3, 25, 0..5))
            check(!SettingsTemperatureDraft.differs(3, 30, 0..5))
            check(!SettingsTemperatureDraft.differs(3, null, 0..5))
            check(SettingsTemperatureDraft.stepped(null, 25, 0..5, -1) == 2)
            check(SettingsTemperatureDraft.stepped(null, 25, 0..5, 1) == 3)
            check(SettingsTemperatureDraft.stepped(5, 25, 0..5, 1) == null)
            check(settingsObservedModel(AiSessionLoad.Empty) == AiModelPolicy.known(AiProtocolHistory.empty().modelId).label)
            check(settingsObservedModel(AiSessionLoad.Unavailable) == null)
            check(settingsObservedModel(AiSessionLoad.Ready(AiSession("fixture", emptyList(),
                AiProtocolHistory.empty(AiModelPolicy.PRO), "", AiUsageSnapshot()))) ==
                AiModelPolicy.known(AiModelPolicy.PRO).label)
            main {
                check(app.appearancePreferences.selectDark(dark) != AppAppearancePreference.Selection.SAVE_FAILED)
                check(app.languagePreferences.select(if (short) AppLanguage.ENGLISH else AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(app.fontSizePreferences.select(if (short) AppFontSize.LARGE else AppFontSize.MEDIUM) != AppFontSizePreference.Selection.SAVE_FAILED)
            }
            val page = MainPageTestHost.launch(test, TaskDestination.SETTINGS, timeoutMs = 15_000)
            val activity = page.activity
            try {
                fun settings() = page.active("root:SETTINGS").requireView() as SettingsDocumentLayout
                fun button(category: SettingsCategory): Button =
                    requireNotNull(settings().findViewWithTag("settings/category/${category.key}"))
                fun heading(section: String): View =
                    requireNotNull(settings().findViewWithTag("settings/heading/$section"))
                fun rect(view: View) = Rect().also { check(view.getGlobalVisibleRect(it)) { "Not visible: ${view.tag}" } }
                fun capture(name: String, owner: MainActivity = activity) {
                    val committed = CountDownLatch(1)
                    main {
                        val decor = owner.window.decorView
                        if (android.os.Build.VERSION.SDK_INT >= 29 && decor.isHardwareAccelerated) {
                            decor.viewTreeObserver.registerFrameCommitCallback {
                                decor.post {
                                    val frame = android.view.Choreographer.getInstance()
                                    frame.postFrameCallback { frame.postFrameCallback { committed.countDown() } }
                                }
                            }
                            decor.postInvalidateOnAnimation()
                        } else decor.postOnAnimation { committed.countDown() }
                    }
                    check(committed.await(4, TimeUnit.SECONDS)) { "Settings window did not submit a frame: $name" }
                    val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                    try {
                        File(out, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
                val landingDebug = JSONObject()
                fun checkRenderedLanding(category: SettingsCategory, phase: String) {
                    val doc = settings()
                    val view = heading(category.sections.first())
                    val viewport = rect(doc.scroll)
                    val title = IntArray(2).also(view::getLocationOnScreen)
                    val visible = Rect()
                    val allVisible = view.isShown && view.getGlobalVisibleRect(visible) &&
                        Rect.intersects(viewport, visible)
                    val preceding = doc.findViewWithTag<View>("settings/machine/function-grid")
                    val precedingBounds = Rect()
                    val precedingVisible = preceding != null && preceding.isShown &&
                        preceding.getGlobalVisibleRect(precedingBounds) && Rect.intersects(viewport, precedingBounds)
                    landingDebug.put("${category.key}/$phase", JSONObject()
                        .put("viewport", viewport.toShortString()).put("heading", title[1])
                        .put("visibleHeading", visible.toShortString()).put("scrollY", doc.scroll.scrollY)
                        .put("precedingMachine", precedingBounds.toShortString()).put("machineVisible", precedingVisible))
                    File(out, "landing-debug.json").writeText(landingDebug.toString(2))
                    check(button(category).isSelected && doc.isAtSectionLanding(category.sections.first()) && allVisible) {
                        "Unstable category at $phase: ${category.key}, y=${doc.scroll.scrollY}, heading=$visible, body=$viewport"
                    }
                    if (category == SettingsCategory.BREWING) {
                        val chapter = doc.sectionAnchor("shortcuts")
                        val chapterPosition = IntArray(2).also(chapter::getLocationOnScreen)
                        check(kotlin.math.abs(chapterPosition[1] - viewport.top) <= HoyiUi.dp(activity, 2) && !precedingVisible) {
                            "Brewing must begin at the viewport top with no previous Machine card: title=${chapterPosition[1]}, body=$viewport, machine=$precedingBounds"
                        }
                    }
                }
                fun select(category: SettingsCategory) {
                    main { check(button(category).performClick()) }
                    await(category.key) { button(category).isSelected && settings().isAtSectionLanding(category.sections.first()) &&
                        heading(category.sections.first()).let { it.isShown && it.getGlobalVisibleRect(Rect()) } }
                }
                await("default Devices") { button(SettingsCategory.DEVICE).isSelected && heading("devices").getGlobalVisibleRect(Rect()) }
                capture("device") // Retain actual first-view pixels even if later geometry checks fail.
                val originalIndex = main {
                    val settings = settings()
                    check(settings.scroll.height > 0)
                    if (short) check(activity.resources.configuration.fontScale > 1f &&
                        activity.resources.configuration.locales[0].language == "en")
                    check(settings.findViewWithTag<View>("settings/document/category-sidebar").isShown == wide)
                    check(settings.findViewWithTag<View>("settings/document/category-grid").isShown != wide)
                    SettingsCategory.entries.map { category ->
                        val b = button(category)
                        val r = rect(b)
                        val text = b.layout
                        val unit = activity.resources.displayMetrics.density
                        check(r.width() == b.width && r.height() == b.height && r.width() >= 48 * unit && r.height() >= 48 * unit) {
                            "Unreachable settings cell: ${category.key}, $r vs ${b.width}×${b.height}"
                        }
                        check(text != null && (0 until text.lineCount).all { line ->
                            text.getEllipsisCount(line) == 0 &&
                                text.getLineWidth(line) <= b.width - b.compoundPaddingStart - b.compoundPaddingEnd + 1
                        } && text.height <= b.height - b.compoundPaddingTop - b.compoundPaddingBottom) {
                            "Clipped settings label: ${category.key}, layout=${text?.height}, view=${b.height}"
                        }
                        r
                    }.also { rows ->
                        if (wide) check(rows.map { it.left }.distinct().size == 1 && rows.map { it.top }.distinct().size == 6)
                        else check(rows.map { it.left }.distinct().size == 3 && rows.map { it.top }.distinct().size == 2 &&
                            rows.chunked(3).all { row -> row.map { it.height() }.distinct().size == 1 }) {
                            "Expected aligned two rows of three touch targets: $rows"
                        }
                    }
                }
                val deviceFrames = JSONObject()
                if (wide && activity.resources.configuration.smallestScreenWidthDp >= 600) {
                    check(BuildConfig.MOCK_MODE)
                    fun scan() = settings().findViewWithTag<Button>("settings/devices/scan-button")
                    fun caption() = settings().findViewWithTag<android.widget.TextView>("settings/devices/message")
                    fun candidates() = settings().findViewWithTag<android.view.ViewGroup>("settings/devices/candidates")
                    val fragment = page.active("root:SETTINGS") as AppSettingsFragment
                    await("Mock device rows ready") {
                        activity.currentService?.snapshot?.let {
                            it.coffeeState == io.openhoyi.session.DeviceState.READY &&
                                it.scaleState == io.openhoyi.session.DeviceState.READY
                        } == true
                    }
                    fun captureDevice(key: String, label: String) {
                        capture("extra-device-$key")
                        deviceFrames.put("extra-device-$key.png", label)
                    }
                    val cRole = io.openhoyi.session.DeviceRole.COFFEE
                    val sRole = io.openhoyi.session.DeviceRole.BOOKOO
                    val alternative = "02:00:00:00:CA:23"
                    val badScale = "02:00:00:00:CA:24"
                    val injected = listOf(
                        io.openhoyi.bluetooth.DiscoveredDevice("02:00:00:00:CA:22", "New Mock Coffee", -42, cRole),
                        io.openhoyi.bluetooth.DiscoveredDevice(alternative, "New Mock Scale", -45, sRole,
                            io.openhoyi.session.BookooScaleProtocolAdapter.id),
                        io.openhoyi.bluetooth.DiscoveredDevice(badScale, "Unsupported Mock Scale", -45, sRole, "unsupported-fixture"))
                    main {
                        val group = settings().findViewWithTag<android.view.ViewGroup>("settings/devices/overview")
                        val coffee = settings().findViewWithTag<View>("settings/devices/COFFEE")
                        val scale = settings().findViewWithTag<android.view.ViewGroup>("settings/devices/BOOKOO")
                        check(group.background != null && group.childCount == 2 && rect(coffee).bottom <= rect(scale).top)
                        val scaleActions = scale.getChildAt(1) as android.view.ViewGroup
                        val controls = (0 until scaleActions.childCount).map(scaleActions::getChildAt).filter { it.isShown }
                        val normal = controls.map(::rect)
                        val min = HoyiUi.dp(activity, 48)
                        check(controls.size == 4 && normal.all { it.height() >= min && it.width() >= min })
                        check(normal.maxOf { it.centerY() } - normal.minOf { it.centerY() } <= HoyiUi.dp(activity, 2))
                        val narrowWidth = HoyiUi.dp(activity, 260)
                        // Exercise the same live control strip's reflow without changing device/window settings.
                        scaleActions.measure(View.MeasureSpec.makeMeasureSpec(narrowWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        scaleActions.layout(0, 0, narrowWidth, scaleActions.measuredHeight)
                        check(controls.map { it.top }.distinct().size > 1 &&
                            controls.all { it.width >= min && it.height >= min && it.left >= 0 && it.right <= narrowWidth })
                        scaleActions.requestLayout()
                        val owner = checkNotNull(activity.currentService)
                        val old = owner.snapshot
                        // List from before this screen's scan may not appear or be called "empty".
                        fragment.renderMockNearby(old.copy(nearbyScanPhase = NearbyScanPhase.COMPLETE, candidates = injected))
                        check(candidates().childCount == 0 && !caption().isShown)
                        check(scan().performClick()) // unchanged scan control activates the actual Mock service
                        val scanned = owner.snapshot
                        check(scanned.nearbyScanGeneration > old.nearbyScanGeneration)
                        fragment.renderMockNearby(scanned.copy(scanning = true, nearbyScanPhase = NearbyScanPhase.IDLE,
                            candidates = emptyList()))
                        check(!scan().isEnabled && settings().findViewWithTag<View>("settings/devices/scan-progress").isShown)
                        check(candidates().childCount == 0 && caption().text.toString() == activity.getString(R.string.settings_device_nearby_hint))
                    }
                    captureDevice("scanning", "平板附近设备：Mock 扫描中")
                    main {
                        val scanned = requireNotNull(activity.currentService).snapshot
                        val mockError = activity.getString(R.string.home_permission_expired)
                        fragment.renderMockNearby(scanned.copy(scanning = false, nearbyScanPhase = NearbyScanPhase.FAILED,
                            nearbyScanFailure = SnapshotMessage.raw(mockError), candidates = injected))
                        check(candidates().childCount == 0 && caption().text.toString() == mockError && scan().isEnabled)
                        fragment.renderMockNearby(scanned)
                        check(candidates().childCount == 0 && caption().text.toString() ==
                            activity.getString(R.string.settings_device_nearby_empty))
                        check(scan().text == activity.getString(R.string.settings_device_scan_again))
                    }
                    captureDevice("empty", "平板附近设备：Mock 无新候选")
                    main {
                        val owner = requireNotNull(activity.currentService)
                        val scanned = owner.snapshot
                        check(scan().performClick())
                        val rescanned = owner.snapshot
                        check(rescanned.nearbyScanGeneration > scanned.nearbyScanGeneration)
                        fragment.renderMockNearby(rescanned.copy(scaleState = io.openhoyi.session.DeviceState.CONNECTING,
                            candidates = rescanned.candidates + injected))
                        check(candidates().findViewWithTag<View>("settings/devices/candidate-group/BOOKOO") == null)
                        fragment.renderMockNearby(rescanned.copy(candidates = rescanned.candidates + injected))
                        check(candidates().findViewWithTag<View>("settings/devices/candidate-group/COFFEE")?.isShown == true &&
                            candidates().findViewWithTag<View>("settings/devices/candidate-group/BOOKOO")?.isShown == true &&
                            rescanned.candidates.all { candidates().findViewWithTag<View>(
                                "product/devices/candidate/${it.address}/connect") == null })
                    }
                    captureDevice("results", "平板附近设备：Mock 测试候选按设备类型分组")
                    main {
                        val rescanned = requireNotNull(activity.currentService).snapshot
                        check(candidates().findViewWithTag<Button>("product/devices/candidate/$badScale/connect").performClick())
                        check(caption().text.toString() == activity.getString(R.string.scale_protocol_unsupported))
                        fragment.renderMockNearby(rescanned.copy(candidates = injected))
                        check(candidates().findViewWithTag<Button>("product/devices/candidate/$alternative/connect").performClick())
                        check(caption().text.toString() == activity.getString(R.string.service_scale_mock))
                        val deviceState = io.openhoyi.session.DeviceState.FAILED
                        fragment.renderMockNearby(rescanned.copy(scaleState = deviceState))
                        check(settings().findViewWithTag<android.widget.TextView>("settings/devices/status/BOOKOO").text.toString() ==
                            DeviceStatusText.label(activity, deviceState))
                        check(caption().text.toString() != activity.getString(R.string.service_scale_mock))
                        fragment.renderMockNearby(rescanned.copy(candidates = injected))
                        val stale = candidates().findViewWithTag<Button>("product/devices/candidate/$alternative/connect")
                        val owner = requireNotNull(activity.currentService)
                        owner.scan() // a different entrance supersedes the page-owned result
                        val foreign = owner.snapshot
                        val before = foreign.message
                        stale.performClick()
                        check(owner.snapshot.message === before)
                        fragment.renderMockNearby(foreign.copy(candidates = injected))
                        check(candidates().childCount == 0 && !caption().isShown)
                        fragment.renderMockNearby(foreign.copy(scanning = true))
                        check(!settings().findViewWithTag<View>("settings/devices/scan-progress").isShown &&
                            !scan().isEnabled && !caption().isShown)
                        fragment.renderMockNearby(foreign.copy(nearbyScanPhase = NearbyScanPhase.FAILED,
                            nearbyScanFailure = SnapshotMessage.raw("foreign-fixture")))
                        check(candidates().childCount == 0 && !caption().isShown)
                        fragment.renderMockNearby(null)
                    }
                    receipts.put("deviceCompact", JSONObject().put("singleTwoRowSurface", true)
                        .put("scaleActionsSingleBand", true).put("narrowMeasuredWrap", true)
                        .put("scanningAndFailureDistinct", true).put("emptyRetryDispatched", true)
                        .put("hidesPriorAndCurrentDevice", true).put("candidateErrorRendered", true)
                        .put("rolesGrouped", true).put("mockScaleConnectRoute", true)
                        .put("scanOwnerIsolation", true).put("roleFailureProjection", true))
                }
                val savedText = main { settings().findViewsByClass(EditText::class.java).first() }
                val previous = main { savedText.text.toString().also { savedText.setText("94") } }
                val precision = context.getSharedPreferences("scale_tool", android.content.Context.MODE_PRIVATE)
                val originalPrecision = precision.getBoolean("one_decimal", false)
                for (category in SettingsCategory.entries) {
                    select(category)
                    if (!short || category in listOf(SettingsCategory.DEVICE, SettingsCategory.BREWING,
                            SettingsCategory.APPEARANCE)) {
                        test.waitForIdleSync()
                        main { checkRenderedLanding(category, "before") }
                        capture(category.key)
                        main { checkRenderedLanding(category, "after") }
                    }
                    if (!wide && !short && category == SettingsCategory.BREWING) main {
                        val doc = settings()
                        val visible = Rect().also { check(doc.scroll.getGlobalVisibleRect(it)) }
                        val controls = listOf("product/settings/shortcuts/direct-start", "product/settings/chart/fixed-axes",
                            "product/settings/app/feedback/toggle", "product/settings/app/feedback/preview").map { tag ->
                            doc.findViewWithTag<View>(tag).let { it to Rect().also { bounds -> check(it.getGlobalVisibleRect(bounds)) } }
                        }
                        val measured = controls.map { (target, bounds) ->
                            check(visible.contains(bounds) && bounds.height() == target.height && bounds.width() == target.width &&
                                target.height >= HoyiUi.dp(activity, 48) && target.width >= HoyiUi.dp(activity, 48)) {
                                "Clipped phone brewing control: ${target.tag}, $bounds in $visible"
                            }
                            bounds.top
                        }
                        check(measured == measured.sorted() && measured.distinct().size == 4)
                    }
                    main {
                        check(SettingsCategory.entries.count { button(it).isSelected } == 1)
                        check(SettingsCategory.entries.all { other ->
                            other.sections.all { heading(it).isShown && heading(it).isAttachedToWindow }
                        }) { "Category landing must retain the whole continuous document" }
                        if (wide) when (category) {
                            SettingsCategory.MACHINE -> {
                                val temperature = settings().findViewWithTag<android.view.ViewGroup>("settings/machine/temperature-grid")
                                val functions = settings().findViewWithTag<android.view.ViewGroup>("settings/machine/function-grid")
                                val columns = settings().findViewWithTag<android.view.ViewGroup>("settings/machine/main-columns")
                                val rows = (0 until temperature.childCount).map(temperature::getChildAt)
                                    .filter { it is SettingsTemperatureRow || it is SettingsBoilerTargetRow }
                                check(rows.size == 3 && rows.map { it.top }.distinct().size == 3)
                                val thermalCard = settings().findViewWithTag<View>("settings/machine/temperature-card")
                                val standby = settings().findViewWithTag<View>("settings/machine/standby-delay")
                                val planCard = settings().findViewWithTag<View>("settings/machine/standby-and-plan")
                                check(heading("schedule").parent === planCard) { "Wide standby and plan heading must be inside the group" }
                                val bodyViewport = Rect().also { check(settings().scroll.getGlobalVisibleRect(it)) }
                                val standbyBounds = Rect().also { check(standby.getGlobalVisibleRect(it)) }
                                check(thermalCard.height <= HoyiUi.dp(activity, 245) &&
                                    bodyViewport.contains(standbyBounds) && standbyBounds.height() == standby.height) {
                                    "Thermal card or standby outside density budget: ${thermalCard.height}, viewport=$bodyViewport standby=$standbyBounds"
                                }
                                check(rows.all { it.height <= HoyiUi.dp(activity, 72) })
                                val week = settings().findViewWithTag<android.view.ViewGroup>("settings/machine/weekly-grid")
                                if (week.isShown) {
                                    val weekLocation = IntArray(2).also(week::getLocationOnScreen)
                                    val fullWeek = Rect(weekLocation[0], weekLocation[1], weekLocation[0] + week.width,
                                        weekLocation[1] + week.height)
                                    check(bodyViewport.contains(fullWeek) && week.childCount == 7) {
                                        "The loaded weekly row must fit the tablet machine viewport: $fullWeek vs $bodyViewport"
                                    }
                                    val tiles = (0 until week.childCount).map { week.getChildAt(it) as android.view.ViewGroup }
                                    check(tiles.map { it.height }.distinct().size == 1)
                                    check(tiles.all { tile ->
                                        val heading = tile.getChildAt(0) as android.widget.TextView
                                        val time = tile.getChildAt(1) as android.widget.TextView
                                        val fill = (tile.background as android.graphics.drawable.GradientDrawable)
                                            .color!!.defaultColor
                                        time.lineCount == 1 && time.layout?.getEllipsisCount(0) == 0 &&
                                            listOf(heading, time).all { label ->
                                                androidx.core.graphics.ColorUtils.calculateContrast(label.currentTextColor, fill) >= 4.5
                                            }
                                    }) { "Normal tablet weekly labels must fit naturally on one line" }
                                }
                                check(columns.getChildAt(0).top == columns.getChildAt(1).top &&
                                    columns.getChildAt(0).right <= columns.getChildAt(1).left)
                                check(functions.childCount == 3 && functions.getChildAt(0).top == functions.getChildAt(1).top)
                                // Same parent-y is not enough: labels and controls must actually
                                // occupy the same optical band within each row.
                                fun center(view: View): Int = IntArray(2).also(view::getLocationOnScreen)[1] + view.height / 2
                                val choices = settings().findViewWithTag<android.view.ViewGroup>("settings/machine/setting-choices")
                                listOf(functions, choices).forEach { grid ->
                                    val rowsInGrid = (0 until grid.childCount).map { grid.getChildAt(it) as android.view.ViewGroup }
                                    check(rowsInGrid.all { row ->
                                        kotlin.math.abs(center(row.getChildAt(0)) - center(row.getChildAt(1))) <= HoyiUi.dp(activity, 2)
                                    }) { "Machine labels and controls not in a single band: ${grid.tag}" }
                                }
                                for ((i, row) in rows.withIndex()) {
                                    val kind = if (row is SettingsBoilerTargetRow) "target-step" else "draft-step"
                                    val minus = row.findViewWithTag<Button>("settings/machine/$kind/-1")
                                    val plus = row.findViewWithTag<Button>("settings/machine/$kind/1")
                                    if (row is SettingsBoilerTargetRow) {
                                        check(row.findViewById<EditText>(android.R.id.edit) == null &&
                                            row.findViewsByClass(EditText::class.java).isEmpty() &&
                                            row.findViewsByClass(Button::class.java).count { it.visibility == View.VISIBLE } == 2)
                                    }
                                    check(listOf(minus, plus).all { it.isShown && it.width >= HoyiUi.dp(activity, 48) &&
                                        it.height >= HoyiUi.dp(activity, 48) }) {
                                        "Tablet draft steppers must be present, row $i=${row.width}×${row.height}; " +
                                            listOf(minus, plus).joinToString {
                                                "${it.tag}: shown=${it.isShown}, visibility=${it.visibility}, ${it.width}×${it.height}"
                                            }
                                    }
                                }
                                val summary = settings().findViewWithTag<android.widget.TextView>("settings-machine-summary")
                                check(!summary.text.contains(activity.getString(R.string.machine_settings_unchanged)))
                                check(listOf("brew", "steam", "light", "schedule").all {
                                    val rendered = settings().findViewWithTag<View>("settings/machine/switch/$it")
                                    rendered is HoyiSwitch && rendered.parent is SettingsObservedSwitch
                                })
                                val sample = HoyiSwitch(activity).apply { contentDescription = "Test observed state" }
                                val requests = mutableListOf<Boolean>()
                                val observed = SettingsObservedSwitch(sample, TaskProductVisualStyle(activity)) { requests.add(it) }
                                val unknown = observed.findViewWithTag<View>("settings/machine/switch-unknown")
                                check(!sample.isEnabled && sample.visibility == View.INVISIBLE && unknown.visibility == View.VISIBLE)
                                observed.present(false, true)
                                check(sample.isEnabled && !sample.isChecked && sample.visibility == View.VISIBLE && unknown.visibility == View.INVISIBLE)
                                // A native Switch drag ends in setChecked(), with no click callback.
                                sample.isChecked = true
                                check(!sample.isChecked && requests == listOf(true))
                                sample.performClick()
                                check(!sample.isChecked && requests == listOf(true, true))
                                observed.present(true, false)
                                check(requests == listOf(true, true))
                                check(!sample.isEnabled && sample.isChecked && sample.visibility == View.VISIBLE && unknown.visibility == View.INVISIBLE)
                                observed.present(null, true)
                                check(!sample.isEnabled && sample.visibility == View.INVISIBLE && unknown.visibility == View.VISIBLE)
                                observed.present(false, true)
                                val width = HoyiUi.dp(activity, 72)
                                val height = HoyiUi.dp(activity, 48)
                                sample.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                                sample.layout(0, 0, width, height)
                                val fixture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try { sample.draw(android.graphics.Canvas(fixture)) }
                                finally { fixture.recycle() }
                                val thumb = sample.thumbDrawable.bounds
                                val track = sample.trackDrawable.bounds
                                check(!thumb.isEmpty && !track.isEmpty)
                                val t0 = SystemClock.uptimeMillis()
                                val x0 = thumb.exactCenterX()
                                val y = thumb.exactCenterY()
                                val xEnd = track.right + HoyiUi.dp(activity, 4).toFloat()
                                fun finger(action: Int, x: Float, elapsed: Long) {
                                    val event = MotionEvent.obtain(t0, t0 + elapsed, action, x, y, 0)
                                    try { sample.onTouchEvent(event) } finally { event.recycle() }
                                }
                                finger(MotionEvent.ACTION_DOWN, x0, 0)
                                finger(MotionEvent.ACTION_MOVE, x0 + HoyiUi.dp(activity, 11), 75)
                                finger(MotionEvent.ACTION_MOVE, xEnd, 125)
                                finger(MotionEvent.ACTION_UP, xEnd, 165)
                                check(!sample.isChecked && requests == listOf(true, true, true)) {
                                    "Native thumb drag did not request exactly one change while retaining readback: $requests"
                                }
                                check(listOf("supply", "run-mode").all {
                                    settings().findViewWithTag<View>("settings/machine/choice/$it") is SettingsBinaryChoice
                                })
                            }
                            SettingsCategory.BREWING -> {
                                val group = settings().findViewWithTag<android.view.ViewGroup>("settings/brewing/group")
                                check(group.background != null && group.childCount == 5)
                                val audition = settings().findViewWithTag<Button>("product/settings/app/feedback/preview")
                                check(audition.text == activity.getString(R.string.settings_category_preview))
                                val completion = settings().findViewWithTag<View>("product/settings/app/feedback/toggle")
                                check(audition.parent !== completion.parent && audition.minimumHeight >= HoyiUi.dp(activity, 48))
                            }
                            SettingsCategory.AI -> {
                                val card = settings().findViewWithTag<View>("settings/ai/config")
                                check(listOf("key", "model", "reasoning", "usage").all {
                                    card.findViewWithTag<View>("settings/ai/$it").isClickable
                                })
                                check(card.findViewWithTag<View>("settings/ai/review") == null)
                            }
                            else -> Unit
                        }
                        check(SettingsCategory.entries.map { rect(button(it)) } == originalIndex) { "Index moved by changing category" }
                    }

                }
                main { check(savedText.text.toString() == "94") { "Category change lost unsent editor draft" }; savedText.setText(previous) }
                if (wide) {
                    check(BuildConfig.MOCK_MODE) { "Target click acceptance is Mock-only" }
                    select(SettingsCategory.MACHINE)
                    fun target(key: String) = settings().findViewWithTag<SettingsBoilerTargetRow>("product/settings/machine/$key/row")
                    fun shownTarget(key: String) = target(key)
                        .findViewWithTag<android.widget.TextView>("settings/machine/target-value").text.toString().toIntOrNull()
                    fun observedTarget(key: String): Int? = activity.currentService?.snapshot?.settings?.let {
                        if (key == "brew") it.brewTemperatureC else it.steamTemperatureC
                    }
                    fun touch(view: () -> View) {
                        val position = main {
                            val v = view()
                            val box = rect(v)
                            check(v.isEnabled && box.height() == v.height && box.width() == v.width)
                            box.exactCenterX() to box.exactCenterY()
                        }
                        val t = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event = MotionEvent.obtain(t, SystemClock.uptimeMillis(), action, position.first, position.second, 0)
                            try {
                                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                                check(test.uiAutomation.injectInputEvent(event, true))
                            } finally { event.recycle() }
                        }
                    }
                    fun clickStep(key: String, delta: Int) =
                        touch { target(key).findViewWithTag<Button>("settings/machine/target-step/$delta") }
                    val brew = main { checkNotNull(observedTarget("brew")) }
                    val steam = main { checkNotNull(observedTarget("steam")) }
                    fun expect(key: String, value: Int) {
                        await("Mock $key target $value without Apply") {
                            shownTarget(key) == value && observedTarget(key) == value && activity.hasWindowFocus()
                        }
                    }
                    clickStep("brew", 1); expect("brew", brew + 1)
                    clickStep("brew", 1); expect("brew", brew + 2)
                    clickStep("brew", -1); expect("brew", brew + 1)
                    clickStep("steam", -1); expect("steam", steam - 1)
                    touch { button(SettingsCategory.BREWING) }
                    await("Switch to Brewing after target clicks") { button(SettingsCategory.BREWING).isSelected &&
                        settings().isAtSectionLanding("shortcuts") && activity.hasWindowFocus() }
                    touch { button(SettingsCategory.MACHINE) }
                    await("Return to Machine after target clicks") { button(SettingsCategory.MACHINE).isSelected &&
                        settings().isAtSectionLanding("machine") && activity.hasWindowFocus() }
                    expect("steam", steam - 1); expect("brew", brew + 1)
                    capture("machine-target-confirmed")
                    clickStep("steam", 1); expect("steam", steam)
                    clickStep("brew", -1); expect("brew", brew)
                    receipts.put("boilerTargetSteps", JSONObject().put("touches", 6)
                        .put("bothObserved", true).put("noApplyOrModal", true)
                        .put("categoryRoundTrip", true).put("mockValuesRestored", true))
                }

                for (category in SettingsCategory.entries) for (section in category.sections) {
                    main { (page.active("root:SETTINGS") as AppSettingsFragment).openSection(section) }
                    await("exact $section heading") { button(category).isSelected && heading(section).getGlobalVisibleRect(Rect()) && heading(section).hasFocus() }
                }
                fun dragUp(viewport: Rect) {
                    val now = SystemClock.uptimeMillis()
                    val x = viewport.left + 6f
                    val low = viewport.top + viewport.height() * .72f
                    val high = viewport.top + viewport.height() * .27f
                    fun input(action: Int, y: Float) {
                        val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
                        try { check(test.uiAutomation.injectInputEvent(event, true)) }
                        finally { event.recycle() }
                    }
                    input(MotionEvent.ACTION_DOWN, low)
                    for (step in 1..10) {
                        SystemClock.sleep(22)
                        input(MotionEvent.ACTION_MOVE, low + (high - low) * step / 10f)
                    }
                    repeat(6) {
                        SystemClock.sleep(25)
                        input(MotionEvent.ACTION_MOVE, high)
                    }
                    input(MotionEvent.ACTION_UP, high)
                }
                main { (page.active("root:SETTINGS") as AppSettingsFragment).openSection("machine") }
                await("machine focus before scroll gesture") { heading("machine").hasFocus() }
                val boundary = main {
                    val document = settings()
                    val box = Rect()
                    document.body.offsetDescendantRectToMyCoords(document.sectionAnchor("shortcuts"), box)
                    document.scroll.scrollTo(0, box.top - minOf(HoyiUi.dp(activity, 70), document.scroll.height / 4))
                    box.top
                }
                val (before, viewport) = main {
                    val document = settings()
                    check(button(SettingsCategory.MACHINE).isSelected)
                    document.scroll.scrollY to rect(document.scroll)
                }
                dragUp(viewport)
                await("continuous drag into adjacent category") {
                    val newY = settings().scroll.scrollY
                    newY > before && newY > boundary && button(SettingsCategory.BREWING).isSelected &&
                        heading("machine").hasFocus()
                }
                val settled = main { settings().scroll.scrollY }
                SystemClock.sleep(280)
                main { check(settings().scroll.scrollY == settled && SettingsCategory.entries.map { rect(button(it)) } == originalIndex) }
                val end = main {
                    val doc = settings()
                    val limit = (doc.body.height - doc.scroll.height + doc.scroll.paddingTop +
                        doc.scroll.paddingBottom).coerceAtLeast(0)
                    // ScrollView clamps using viewport + requestedY; MAX_VALUE overflows there.
                    doc.scroll.scrollTo(0, limit)
                    check(doc.scroll.scrollY == limit && !doc.scroll.canScrollVertically(1) &&
                        button(SettingsCategory.MAINTENANCE).isSelected) {
                        "Expected true end $limit, got ${doc.scroll.scrollY}"
                    }
                    limit
                }
                dragUp(viewport)
                await("end remains pinned after upward motion") {
                    settings().scroll.scrollY == end && !settings().scroll.canScrollVertically(1) &&
                        button(SettingsCategory.MAINTENANCE).isSelected
                }
                main { check(settings().scroll.scrollY == end && button(SettingsCategory.MAINTENANCE).isSelected) {
                    "End boundary moved from $end to ${settings().scroll.scrollY}"
                } }
                if (!short) {
                    val fragment = main { page.active("root:SETTINGS") as AppSettingsFragment }
                    main { fragment.openSection("appearance") }
                    await("appearance presets") { heading("appearance").isShown && heading("appearance").hasFocus() }
                    val appliedAppearance = app.appearancePreferences.current
                    val appliedWallpaper = app.pageBackgrounds.current.config
                    val swatches = main { settings().findViewWithTag<android.view.ViewGroup>("settings/appearance/palette-swatches") }
                    val previews = main { settings().findViewWithTag<android.view.ViewGroup>("settings/appearance/background-presets") }
                    val swatch = main {
                        check(swatches.childCount == AppThemePalette.entries.size)
                        check(listOf("solid", "mist", "slate").all {
                            previews.findViewWithTag<Button>("settings/appearance/background/$it")?.isShown == true
                        })
                        val photoRoute = requireNotNull(settings().findViewWithTag<Button>("product/settings/background/open"))
                        check(photoRoute.isShown && photoRoute.isClickable &&
                            (photoRoute.parent === previews) == wide)
                        val alternate = swatches.findViewWithTag<Button>("settings/appearance/palette/sea-salt")
                        alternate.requestRectangleOnScreen(Rect(0, 0, alternate.width, alternate.height), true)
                        alternate
                    }
                    await("actual swatch reachable") { swatch.getGlobalVisibleRect(Rect()) && swatch.isShown }
                    val chooser = main {
                        check(swatch.performClick())
                        requireNotNull(fragment.appearanceCard?.dialog)
                    }
                    await("pre-selected color in original chooser") {
                        val radio = chooser.window?.decorView?.findViewWithTag<android.widget.RadioButton>("product/settings/app/palette/sea-salt")
                        chooser.isShowing && radio?.isChecked == true && app.appearancePreferences.current == appliedAppearance
                    }
                    main { chooser.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick() }
                    val tile = main {
                        val mist = previews.findViewWithTag<Button>("settings/appearance/background/mist")
                        mist.requestRectangleOnScreen(Rect(0, 0, mist.width, mist.height), true)
                        mist
                    }
                    await("actual preset reachable") { tile.isShown && tile.getGlobalVisibleRect(Rect()) }
                    val previewMonitor = test.addMonitor(BackgroundSettingsActivity::class.java.name, null, false)
                    var editor: BackgroundSettingsActivity? = null
                    try {
                        main { check(tile.performClick()) }
                        val launched = test.waitForMonitorWithTimeout(previewMonitor, 10_000) as? BackgroundSettingsActivity
                            ?: error("Background preset did not open the original editor")
                        editor = launched
                        await("pre-selected Mist without apply") {
                            launched.hasWindowFocus() && launched.editor.currentPreview()?.config?.choice == AppBackground.Mist &&
                                app.pageBackgrounds.current.config == appliedWallpaper
                        }
                        main { launched.findViewById<View>(android.R.id.content)
                            .findViewWithTag<Button>("product/background/cancel").performClick() }
                        await("returned to same Appearance") { activity.hasWindowFocus() && app.pageBackgrounds.current.config == appliedWallpaper }
                    } finally {
                        test.removeMonitor(previewMonitor)
                        editor?.let { main { if (!it.isFinishing) it.finish() } }
                    }
                }
                val supplemental = if (wide) runCatching { SettingsSupplementalCapture(test, page, out).run() }
                    else null
                // Exact return route, not just a successful navigation to Settings.
                main { activity.select(TaskDestination.BEANS); activity.openSettingsSection("scale") }
                await("external scale link") { button(SettingsCategory.DEVICE).isSelected && heading("scale").getGlobalVisibleRect(Rect()) && heading("scale").hasFocus() }
                main { activity.onBackPressedDispatcher.onBackPressed() }
                await("return to caller") { activity.supportFragmentManager.primaryNavigationFragment is BeanInventoryFragment }
                main { activity.openSettingsSection("scale") }
                await("scale heading before tool") { heading("scale").hasFocus() && heading("scale").getGlobalVisibleRect(Rect()) }
                main { activity.openScaleFromSettings() }
                await("real scale tool") { activity.supportFragmentManager.primaryNavigationFragment is ScaleFragment }
                main { activity.onBackPressedDispatcher.onBackPressed() }
                await("scale tool returned to Devices") { button(SettingsCategory.DEVICE).isSelected && heading("scale").getGlobalVisibleRect(Rect()) }
                main { activity.openSettingsSection("about") }

                await("updates anchor") { heading("about").getGlobalVisibleRect(Rect()) && heading("about").hasFocus() }
                val update = main {
                    settings().findViewWithTag<View>("product/settings/app/update/check").also {
                        it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height), true)
                    }
                }
                await("update control reachable in the local right scroll") {
                    update.isEnabled && update.isShown && Rect().let {
                        update.getGlobalVisibleRect(it) && it.height() == update.height && it.width() == update.width
                    }
                }
                main { (page.active("root:SETTINGS") as AppSettingsFragment).openSection("maintenance") }
                await("maintenance control heading") { heading("maintenance").getGlobalVisibleRect(Rect()) }
                val action = main { settings().findViewWithTag<Button>("settings/details/maintenance/action") }
                main { action.requestRectangleOnScreen(Rect(0, 0, action.width, action.height), true) }
                await("maintenance detail control reachable") { action.isShown && Rect().let { action.getGlobalVisibleRect(it) && it.height() == action.height } }
                main {
                    val details = settings().findViewWithTag<View>("settings/details/maintenance/content")
                    check(!details.isShown)
                    check(action.performClick() && details.isShown)
                    check(action.performClick() && !details.isShown)
                    check(precision.getBoolean("one_decimal", false) == originalPrecision)
                    (page.active("root:SETTINGS") as AppSettingsFragment).openSection("about")
                }
                await("about reselected") { heading("about").getGlobalVisibleRect(Rect()) && heading("about").hasFocus() }
                val monitor = test.addMonitor(MainActivity::class.java.name, null, false)
                var restored: MainActivity? = null
                try {
                    main {
                        if (dark || wide) check(app.appearancePreferences.selectDark(wide) != AppAppearancePreference.Selection.SAVE_FAILED)
                        activity.recreate()
                    }
                    val recreated = test.waitForMonitorWithTimeout(monitor, 12_000) as? MainActivity
                        ?: error("Settings Activity recreation did not return")
                    restored = recreated
                    await("same category after recreation") {
                        val fragment = recreated.supportFragmentManager.primaryNavigationFragment as? AppSettingsFragment
                        val view = fragment?.view
                        view?.findViewWithTag<View>("settings/category/maintenance")?.isSelected == true &&
                            view.findViewWithTag<View>("settings/heading/about")?.getGlobalVisibleRect(Rect()) == true
                    }
                    if (wide) {
                        for (cat in SettingsCategory.entries) {
                            main {
                                val fragment = recreated.supportFragmentManager.primaryNavigationFragment as AppSettingsFragment
                                fragment.openSection(cat.sections.first())
                            }
                            await("wide dark ${cat.key}") {
                                val fragment = recreated.supportFragmentManager.primaryNavigationFragment as? AppSettingsFragment
                                val view = fragment?.view
                                val night = recreated.resources.configuration.uiMode and
                                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                                night && (view as? SettingsDocumentLayout)?.isAtSectionLanding(cat.sections.first()) == true &&
                                    view.findViewWithTag<View>("settings/category/${cat.key}")?.isSelected == true &&
                                    view.findViewWithTag<View>("settings/heading/${cat.sections.first()}")?.getGlobalVisibleRect(Rect()) == true
                            }
                            if (cat == SettingsCategory.APPEARANCE) main {
                                val fragment = recreated.supportFragmentManager.primaryNavigationFragment as AppSettingsFragment
                                val darkChoice = fragment.requireView()
                                    .findViewWithTag<Button>("settings/appearance/mode/dark")
                                val fill = (darkChoice.background as android.graphics.drawable.GradientDrawable)
                                    .color!!.defaultColor
                                val ratio = androidx.core.graphics.ColorUtils.calculateContrast(darkChoice.currentTextColor, fill)
                                check(darkChoice.isShown && ratio >= 4.5)
                                receipts.put("visibleDarkModeContrast", ratio)
                            }
                            capture("dark-${cat.key}", recreated)
                        }
                    }
                    if (dark) {
                        main {
                            val fragment = recreated.supportFragmentManager.primaryNavigationFragment as AppSettingsFragment
                            fragment.openSection("appearance")
                        }
                        await("matching Chinese light phone appearance") {
                            val fragment = recreated.supportFragmentManager.primaryNavigationFragment as? AppSettingsFragment
                            val view = fragment?.view
                            val light = recreated.resources.configuration.uiMode and
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_NO
                            light && view?.findViewWithTag<View>("settings/category/appearance")?.isSelected == true &&
                                view.findViewWithTag<View>("settings/heading/appearance")?.getGlobalVisibleRect(Rect()) == true
                        }
                        capture("appearance-light", recreated)
                    }
                } finally {
                    test.removeMonitor(monitor)
                    restored?.let { main { it.finish() } }
                }
                supplemental?.getOrThrow()?.let { details ->
                    deviceFrames.keys().forEach { key -> details.put(key, deviceFrames.getString(key)) }
                    receipts.put("supplemental", details)
                }
                receipts.put("wide", wide).put("dark", dark).put("shortEnglishLarge", short).put("sixCategories", true).put("exactRoutes", 11)
                    .put("categoryRailFixed", true).put("continuousDrag", true).put("recreatedCategory", true).put("appearancePreviewNoWrite", !short).put("wideDark", wide).put("draftRetained", true).put("returnedToCaller", true).put("scaleToolReturned", true)
                    .put("rects", JSONArray(originalIndex.map { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) }))
                    .put("noBleOrDestructiveActions", true)
            } finally {
                main { if (!activity.isDestroyed) activity.finish() }
            }
        } finally {
            File(out, "receipt.json").writeText(receipts.toString(2) + "\n")
        }
    }

    private fun <T : View> View.findViewsByClass(type: Class<T>): List<T> {
        val found = mutableListOf<T>()
        fun walk(view: View) {
            if (type.isInstance(view)) found.add(type.cast(view)!!)
            if (view is android.view.ViewGroup)
                for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(this)
        return found
    }
}

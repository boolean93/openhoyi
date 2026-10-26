package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.LinearLayout
import java.io.File

/** Real windows, isolated Mock only. Checks the frame, not completion of the page redesign. */
internal class TaskFrameChecks(private val test: Instrumentation) {
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun verifySettingsScroll(activity: Activity, directory: File) {
        fun main(action: () -> Unit) {
            var failure: Throwable? = null
            test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
            failure?.let { throw it }
        }
        fun frame() {
            test.waitForIdleSync()
            val drawn = java.util.concurrent.CountDownLatch(1)
            main { activity.window.decorView.postOnAnimation {
                activity.window.decorView.postOnAnimation { drawn.countDown() }
            } }
            check(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Settings scroll frame timed out" }
        }
        val scaleSettings = activity is ScaleSettingsActivity
        val machineSettings = activity is MachineSettingsActivity
        var inputValues = emptyList<String>()
        val preferenceNames = if (scaleSettings) listOf("scale_tool") else listOf("appearance", "app_language", "brew_feedback")
        fun preferences() = preferenceNames.associateWith {
            activity.getSharedPreferences(it, Activity.MODE_PRIVATE).all.toMap()
        }
        val before = preferences()
        lateinit var scroll: android.widget.ScrollView
        var originalY = 0
        main {
            val page = descendants(activity.window.decorView).filterIsInstance<TaskPageFrame>().single().pageContent
            scroll = descendants(page).filterIsInstance<android.widget.ScrollView>().single()
            originalY = scroll.scrollY
            inputValues = descendants(scroll).filterIsInstance<EditText>().map { it.text.toString() }
        }
        fun steamGroup(): List<android.widget.TextView> {
            val row = descendants(scroll).single { it.tag == "product/settings/machine/steam/row" }
            check(row.isShown) { "Steam temperature section is not visible" }
            val views = descendants(row)
            val input = views.filterIsInstance<EditText>().single {
                it.hint.toString() == activity.getString(R.string.machine_settings_steam_input)
            }
            val label = views.filterIsInstance<android.widget.TextView>().single { it.labelFor == input.id }
            val action = views.filterIsInstance<android.widget.TextView>().single {
                it.tag == "product/settings/machine/steam/action" &&
                    it.text.toString() == activity.getString(R.string.machine_settings_steam_set)
            }
            return listOf(label, input, action)
        }
        val targets = if (machineSettings) listOf(
            "temperature-bottom" to activity.getString(R.string.machine_settings_steam_set)
        ) else if (scaleSettings) listOf(
            "protocol-support" to activity.getString(R.string.scale_offline_evidence)
        ) else listOf(
            "app-info" to BuildConfig.APPLICATION_ID,
            "diagnostics" to activity.getString(R.string.app_settings_machine)
        )
        try {
            for ((name, label) in targets) {
                main {
                    val target = descendants(scroll).filterIsInstance<android.widget.TextView>().single { it.text.toString() == label }
                    if (machineSettings) {
                        val content = scroll.getChildAt(0) as ViewGroup
                        val group = steamGroup()
                        check(group.last() === target) { "Missing steam temperature group" }
                        val requested = Rect()
                        group.forEach { view ->
                            val bounds = Rect(0, 0, view.width, view.height)
                            content.offsetDescendantRectToMyCoords(view, bounds)
                            requested.union(bounds)
                        }
                        content.requestRectangleOnScreen(requested, true)
                    } else target.requestRectangleOnScreen(Rect(0, 0, target.width, target.height), true)
                }
                frame()
                // Layout callbacks precede compositor submission; retain geometry on both sides of capture.
                fun recordGeometry(phase: String) = main {
                    val visibleWindow = Rect()
                    activity.window.decorView.getWindowVisibleDisplayFrame(visibleWindow)
                    val lines = (if (machineSettings) steamGroup() else descendants(scroll).filterIsInstance<android.widget.TextView>().filter {
                        it.text.toString() == label
                    }).map { view ->
                        val bounds = Rect()
                        view.getGlobalVisibleRect(bounds)
                        "${view.javaClass.simpleName} text=${view.text} visible=$bounds size=${view.width}x${view.height}"
                    }
                    File(directory, "${activity.javaClass.simpleName}-$name-geometry.txt").appendText(
                        "$phase scrollY=${scroll.scrollY} window=$visibleWindow\n${lines.joinToString("\n")}\n")
                }
                recordGeometry("before-settle")
                SystemClock.sleep(100)
                frame()
                recordGeometry("before-capture")
                // Capture before assertions so a clipped target leaves reviewable evidence.
                val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                try {
                    File(directory, "${activity.javaClass.simpleName}-$name.png").outputStream().use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { bitmap.recycle() }
                recordGeometry("after-capture")
                main {
                    val labels = descendants(scroll).filterIsInstance<android.widget.TextView>()
                    val required = if (name == "diagnostics") listOf(label, activity.getString(R.string.app_settings_export)) else listOf(label)
                    required.forEach { text ->
                        val target = labels.single { it.text.toString() == text }
                        val visible = Rect()
                        check(target.getGlobalVisibleRect(visible) && visible.width() == target.width && visible.height() == target.height) {
                            "Settings content is not fully reachable: $text"
                        }
                        LanguageUiLayoutChecks.textFits(target, "settings/$name")
                    }
                    if (machineSettings) {
                        val steamViews = steamGroup().take(2)
                        check(steamViews.size == 2) { "Missing steam temperature label/input" }
                        steamViews.forEach { view ->
                            val bounds = Rect()
                            check(view.getGlobalVisibleRect(bounds) && bounds.width() == view.width && bounds.height() == view.height) {
                                "Steam temperature field is clipped after scrolling: ${view.javaClass.simpleName} text=${view.text} visible=$bounds size=${view.width}x${view.height} scrollY=${scroll.scrollY}"
                            }
                            val visibleWindow = Rect()
                            activity.window.decorView.getWindowVisibleDisplayFrame(visibleWindow)
                            check(visibleWindow.contains(bounds)) { "Steam field overlaps system window: $bounds outside $visibleWindow" }
                            LanguageUiLayoutChecks.textFits(view, "settings/steam-field")
                        }
                        check(labels.filterIsInstance<EditText>().map { it.text.toString() } == inputValues) {
                            "Reading settings changed input values"
                        }
                    }
                    if (name == "app-info") check(labels.any {
                        it.text.toString() == activity.getString(R.string.settings_info_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                    }) { "Settings omitted actual build version" }
                }

            }
            check(preferences() == before) { "Reading settings changed saved preferences" }
            if (machineSettings) {
                File(directory, "machine-settings-scroll.txt").writeText(
                    "states=1 steamFieldFullyVisible=true inputsUnchanged=true controlsNotInvoked=true noBle=true\n")
            } else if (scaleSettings) {
                File(directory, "scale-settings-scroll.txt").writeText(
                    "states=1 protocolTextFullyVisible=true preferencesUnchanged=true noBle=true\n")
            } else {
                File(directory, "settings-scroll.txt").writeText("states=2 actualBuildInfo=true exportNotInvoked=true preferencesUnchanged=true noBle=true\n")
            }
        } finally {
            main { scroll.scrollTo(0, originalY) }
            frame()
        }
    }

    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val app = test.targetContext.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val appearance = test.targetContext.getSharedPreferences("appearance", Activity.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        val pages = listOf(
            HomeActivity::class.java, ExtractionActivity::class.java,
            CurveActivity::class.java, CurveEditorActivity::class.java, CurveShareActivity::class.java,
            CurveImportActivity::class.java, CurveImportPreviewActivity::class.java, LegacyCurveActivity::class.java,
            HistoryActivity::class.java, HistoryDetailActivity::class.java, BrewComparisonActivity::class.java,
            BrewReviewActivity::class.java, LegacyHistoryActivity::class.java, LegacyHistoryDetailActivity::class.java,
            BeanInventoryActivity::class.java, BeanEntryActivity::class.java, BeanBatchActivity::class.java,
            BeanLedgerActivity::class.java, BeanPreparationActivity::class.java,
            ScaleActivity::class.java, ScaleConnectionActivity::class.java, ScaleSettingsActivity::class.java,
            MachineSettingsActivity::class.java, AppSettingsActivity::class.java
        )
        val directory = File(test.targetContext.getExternalFilesDir(null), "task-frame-checks").apply { mkdirs() }
        try {
            test.runOnMainSync {
                check(app.languagePreferences.select(AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(appearance.edit().putBoolean("dark", false).commit())
            }
            for (page in pages) {
                test.sendStatus(0, Bundle().apply { putString("stream", "TASK_FRAME_START ${page.simpleName}\n") })
                val previewDocument = if (page == CurveImportPreviewActivity::class.java) CustomCurveDocument(
                    CustomCurveDocument.newId(), "经典浓缩", 93, 3600, CustomCurveDocument.ControlMode.PRESSURE,
                    listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 250),
                        CustomCurveDocument.Stage(70, 150), CustomCurveDocument.Stage(50, 50))) else null
                val previewStore = if (previewDocument != null) app.customCurvesResult.getOrThrow() else null
                val beforePreview = previewStore?.list()
                if (previewDocument != null) check(previewStore!!.find(previewDocument.id) == null)
                val shareSource = if (page == CurveShareActivity::class.java) app.curves.items.firstOrNull {
                    it.controlProfile != null && runCatching { CustomCurveDocument.fromLibraryItem(it) }.isSuccess
                } ?: error("No captured share fixture") else null
                val shareDocument = shareSource?.let(CustomCurveDocument::fromLibraryItem)
                val shareBefore = if (shareSource != null) app.customCurvesResult.getOrThrow().list() else null
                val rootDestination = when (page) {
                    HomeActivity::class.java, ExtractionActivity::class.java -> TaskDestination.BREW
                    CurveActivity::class.java -> TaskDestination.CURVES
                    HistoryActivity::class.java -> TaskDestination.HISTORY
                    BeanInventoryActivity::class.java -> TaskDestination.BEANS
                    ScaleActivity::class.java -> TaskDestination.SCALE
                    AppSettingsActivity::class.java -> TaskDestination.SETTINGS
                    else -> null
                }
                val intent = Intent(test.targetContext, if (rootDestination == null) page else MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                rootDestination?.let { intent.putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, it.name) }
                if (page == ExtractionActivity::class.java) intent.putExtra(MainActivity.EXTRA_OPEN_EXTRACTION, true)
                shareSource?.let { intent.putExtra(CurveShareActivity.CURVE_ID, it.id) }
                previewDocument?.let { intent.putExtra(CurveImportPreviewActivity.DOCUMENT, it.encode()) }
                val activity = test.startActivitySync(intent)
                try {
                    if (rootDestination != null) {
                        check(activity is MainActivity) { "${page.simpleName} did not open the real host" }
                        val tag = if (page == ExtractionActivity::class.java) "extraction" else "root:${rootDestination.name}"
                        MainPageTestHost(test, activity).active(tag)
                    }
                    val deadline = SystemClock.elapsedRealtime() + 10000
                    while (true) {
                        var ready = false
                        test.runOnMainSync {
                            ready = activity.hasWindowFocus() && activity.window.decorView.height > 0 &&
                                !activity.window.decorView.isLayoutRequested
                        }
                        if (ready) break
                        check(SystemClock.elapsedRealtime() < deadline) { "${page.simpleName}: window layout timeout" }
                        SystemClock.sleep(50)
                    }
                    test.runOnMainSync {
                        val all = descendants(activity.window.decorView)
                        val frame = all.filterIsInstance<TaskPageFrame>().single()
                        val expected = if (activity.resources.configuration.screenWidthDp >= 600) TaskNavigationMode.RAIL else TaskNavigationMode.BOTTOM
                        check(frame.mode == expected) { "${page.simpleName}: ${frame.mode} != $expected" }
                        val targets = descendants(frame.navigation).filter { it.tag?.toString()?.startsWith("task-nav:") == true }
                        val primary = targets.take(4)
                        check(primary.map { it.tag } == TaskNavigationModel.primary.map { "task-nav:${it.name}" })
                        val selected = TaskNavigationModel.forPage(page.simpleName)
                        primary.forEachIndexed { index, view ->
                            check(view.isSelected == (TaskNavigationModel.primary[index] == selected)) { "${page.simpleName}: selected ${view.tag}" }
                            check(view.isClickable && !view.contentDescription.isNullOrBlank())
                            val minimum = HoyiUi.dp(activity, 48)
                            check(view.width >= minimum && view.height >= minimum) { "${page.simpleName}: small target ${view.tag} ${view.width}x${view.height}" }
                            val bounds = Rect()
                            check(view.getGlobalVisibleRect(bounds) && bounds.width() == view.width && bounds.height() == view.height) { "${page.simpleName}: clipped ${view.tag}" }
                        }
                        if (frame.toolsInTopBar) {
                            val menu = all.single { it.tag == "task-tools-menu" && it.isShown }
                            val bounds = Rect()
                            check(menu.getGlobalVisibleRect(bounds) && bounds.width() == menu.width && bounds.height() == menu.height)
                            check(menu.width >= HoyiUi.dp(activity, 48) && menu.height >= HoyiUi.dp(activity, 48))
                        } else {
                            check(targets.drop(4).map { it.tag } == TaskNavigationModel.tools.map { "task-nav:${it.name}" })
                            check(targets.drop(4).all { it.isShown })
                        }
                        if (appSettingsPage(activity) && frame.toolsInTopBar) {
                            // The original settings page scrolls. Its global tool entry must not.
                            descendants(frame.pageContent).filterIsInstance<android.widget.ScrollView>().forEach { scroll ->
                                val maximum = (scroll.getChildAt(0).height - scroll.height + scroll.paddingTop + scroll.paddingBottom).coerceAtLeast(0)
                                scroll.scrollTo(0, maximum)
                                val menu = descendants(frame.pageContent).single { it.tag == "task-tools-menu" && it.isShown }
                                val visible = Rect()
                                check(menu.getGlobalVisibleRect(visible) && visible.width() == menu.width && visible.height() == menu.height) {
                                    "Settings scrolling hid global tools"
                                }
                                scroll.scrollTo(0, 0)
                            }
                        }
                        // A standalone installation proves identity/order/LP preservation and idempotence.
                        val parent = LinearLayout(activity)
                        val child = View(activity)
                        val params = LinearLayout.LayoutParams(37, 41)
                        parent.addView(child, params)
                        val installed = TaskPageFrame.install(activity, parent)
                        check(TaskPageFrame.install(activity, parent) === installed)
                        check(installed.pageContent.getChildAt(0) === child && child.layoutParams === params)
                    }
                    var typographyFailure: Throwable? = null
                    test.runOnMainSync {
                        try { TaskTypographyEvidence.capture(activity, directory) }
                        catch (failure: Throwable) { typographyFailure = failure }
                    }
                    typographyFailure?.let { throw it }
                    val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                    try {
                        File(directory, "${page.simpleName}.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                    if (shareDocument != null || previewDocument != null) {
                        var tableFailure: Throwable? = null
                        test.runOnMainSync {
                            try {
                                val all = descendants(activity.window.decorView)
                                val tag = if (shareDocument != null) "share-stage-table" else "import-stage-table"
                                val table = all.single { it.tag == tag }
                                val chart = all.filterIsInstance<CurveStageView>().single()
                                val config = activity.resources.configuration
                                File(directory, "${page.simpleName}-table-space.txt").writeText(
                                    "screenDp=${config.screenWidthDp}x${config.screenHeightDp} fontScale=${config.fontScale} tableShown=${table.isShown} tableMeasured=${table.measuredWidth}x${table.measuredHeight} chart=${chart.width}x${chart.height}\n")
                                if (config.screenWidthDp >= 1000 && config.screenHeightDp >= 600 && config.fontScale <= 1.1f) {
                                    check(table.isShown) { "Tablet preview must show its parameter table: $tag" }
                                    val visible = Rect()
                                    check(table.getGlobalVisibleRect(visible) && visible.width() == table.width && visible.height() == table.height) {
                                        "Tablet parameter table is clipped: $tag $visible size=${table.width}x${table.height}"
                                    }
                                }
                            } catch (failure: Throwable) { tableFailure = failure }
                        }
                        tableFailure?.let { throw it }
                    }
                    if (shareDocument != null) {
                        test.runOnMainSync {
                            val labels = descendants(activity.window.decorView).filterIsInstance<android.widget.TextView>()
                            check(labels.any { it.text.toString() == shareDocument.name }) { "Share fixture missing actual name" }
                            check(labels.any { it.isShown && it.isClickable && it.text.toString() == activity.getString(R.string.profiles_copy) })
                            check(!labels.single { it.text.toString() == activity.getString(R.string.profiles_share_file) }.isEnabled)
                        }
                        check(app.customCurvesResult.getOrThrow().list() == shareBefore) { "Viewing share changed curves" }
                        File(directory, "share-fixture.txt").writeText(
                            "source=${shareSource!!.id} stages=${shareDocument.stages.size} actionsNotInvoked=true storeUnchanged=true noBle=true\n")
                    }
                    if (previewDocument != null) {
                        test.runOnMainSync {
                            val all = descendants(activity.window.decorView)
                            check(all.filterIsInstance<CurveStageView>().single().targets == previewDocument.stages.map { it.target })
                            check(all.filterIsInstance<android.widget.Button>().any {
                                it.isShown && it.isEnabled && it.text.toString() == activity.getString(R.string.profiles_import_save)
                            }) { "Valid import preview did not expose save action" }
                        }
                        check(previewStore!!.list() == beforePreview && previewStore.find(previewDocument.id) == null) {
                            "Viewing import preview changed curve storage"
                        }
                        File(directory, "import-preview-fixture.txt").writeText(
                            "validDocument=true stages=4 conflict=false saveNotInvoked=true storeUnchanged=true noBle=true\n")
                    }
                    if (appSettingsPage(activity) || activity is ScaleSettingsActivity || activity is MachineSettingsActivity) verifySettingsScroll(activity, directory)
                } finally {
                    test.runOnMainSync { activity.finish() }
                    test.waitForIdleSync()
                }
            }
            verifyRoutesAndFocus()
        } finally {
            test.runOnMainSync {
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                val edit = appearance.edit().clear()
                oldAppearance.forEach { (key, value) ->
                    when (value) {
                        is Boolean -> edit.putBoolean(key, value)
                        is String -> edit.putString(key, value)
                        is Int -> edit.putInt(key, value)
                        is Long -> edit.putLong(key, value)
                        is Float -> edit.putFloat(key, value)
                        is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                check(edit.commit())
            }
        }
    }

    private fun appSettingsPage(activity: Activity) = activity is MainActivity &&
        activity.supportFragmentManager.primaryNavigationFragment is AppSettingsFragment

    private fun verifyRoutesAndFocus() {
        val host = MainPageTestHost.launch(test)
        val home = host.activity
        try {
            test.runOnMainSync {
                val frame = descendants(home.window.decorView).filterIsInstance<TaskPageFrame>().single()
                if (frame.mode == TaskNavigationMode.RAIL) {
                    val input = EditText(home)
                    frame.pageContent.addView(input)
                    input.requestFocus()
                    check(input.hasFocus())
                    frame.measure(View.MeasureSpec.makeMeasureSpec(frame.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(home, 200), View.MeasureSpec.EXACTLY))
                    check(input.hasFocus()) { "Moving tools to header cleared input focus" }
                    frame.pageContent.removeView(input)
                    frame.requestLayout()
                }
            }
            test.waitForIdleSync()
            for (destination in TaskDestination.entries.filter { it != TaskDestination.BREW }) {
                var popup = false
                test.runOnMainSync {
                    val all = descendants(home.window.decorView)
                    val direct = all.singleOrNull { it.tag == "task-nav:${destination.name}" && it.isShown }
                    if (direct != null) check(direct.performClick()) else {
                        check(all.single { it.tag == "task-tools-menu" && it.isShown }.performClick())
                        popup = true
                    }
                }
                if (popup) {
                    val label = home.getString(if (destination == TaskDestination.SCALE) R.string.scale_title else R.string.ui_settings)
                    val deadline = SystemClock.elapsedRealtime() + 5000
                    var clicked = false
                    while (!clicked && SystemClock.elapsedRealtime() < deadline) {
                        val root = test.uiAutomation.rootInActiveWindow
                        for (node in root?.findAccessibilityNodeInfosByText(label).orEmpty()) {
                            var candidate: AccessibilityNodeInfo? = node
                            while (candidate != null && !candidate.isClickable) candidate = candidate.parent
                            if (candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { clicked = true; break }
                        }
                        if (!clicked) SystemClock.sleep(50)
                    }
                    check(clicked) { "Tool menu missing $label" }
                }
                test.waitForIdleSync()
                host.active("root:${destination.name}")
                check(!home.isFinishing) { "Tab navigation finished MainActivity" }
            }
            test.runOnMainSync {
                check(descendants(home.window.decorView).single { it.tag == "task-nav:BREW" }.performClick())
            }
            test.waitForIdleSync()
            host.active("root:BREW")
        } finally { test.runOnMainSync { home.finish() }; test.waitForIdleSync() }
    }
}

package io.openhoyi.mobile

import android.app.Activity
import android.app.Application
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import io.openhoyi.bean.InventoryEventKind
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** UI writes only to this run's local fixtures. Never clicks machine/BLE, external share or SAF. */
internal class AdvancedBusinessUiChecks(private val test: Instrumentation) {
    data class Result(val paths: List<String>, val inventoryEvents: Int, val newDrafts: Int)
    private val context get() = test.targetContext
    private val opened = mutableListOf<Activity>()
    private var rootHost: MainActivity? = null
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
            check(SystemClock.elapsedRealtime() < deadline) { "Advanced business UI condition timed out" }
            Thread.sleep(25)
        }
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    /** Exercise the native background through a real Button, including later state changes. */
    private fun checkPrimaryButtonStates(button: Button, fixture: String, enabledColor: Int, disabledColor: Int,
        disabledTextColor: Int, focusBorderColor: Int) {
        check(button.width > 0 && button.height > 0 && button.isEnabled) { "$fixture missing enabled button layout" }
        val background = button.background as? android.graphics.drawable.StateListDrawable
            ?: error("$fixture background is not stateful at the Button")
        fun renderedColor(): Int {
            val bitmap = android.graphics.Bitmap.createBitmap(button.width, button.height,
                android.graphics.Bitmap.Config.ARGB_8888)
            return try {
                button.jumpDrawablesToCurrentState()
                button.draw(android.graphics.Canvas(bitmap))
                bitmap.getPixel(button.width / 10, button.height / 5)
            } finally { bitmap.recycle() }
        }
        fun checkState(label: String, color: Int, textColor: Int, ripple: Boolean,
            checkPixel: Boolean = true, checkFocusBorder: Boolean = false) {
            val current = background.current
            check((current is android.graphics.drawable.RippleDrawable) == ripple) {
                "$fixture $label wrong ripple/disabled branch: ${current?.javaClass?.simpleName}"
            }
            val fill = (if (current is android.graphics.drawable.RippleDrawable) current.getDrawable(0) else current)
                as? android.graphics.drawable.GradientDrawable
                ?: error("$fixture $label missing native background shape")
            if (checkFocusBorder) {
                val bitmap = android.graphics.Bitmap.createBitmap(button.width, button.height,
                    android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    fill.draw(android.graphics.Canvas(bitmap))
                    check((0 until minOf(button.height / 8,
                        (4 * button.resources.displayMetrics.density).toInt())).any {
                        bitmap.getPixel(button.width / 2, it) == focusBorderColor
                    }) { "$fixture $label focused outline was not drawn" }
                } finally { bitmap.recycle() }
            }
            val pixel = if (checkPixel) renderedColor() else null // Focus feedback may legitimately tint drawn pixels.
            check(fill.color?.defaultColor == color && (pixel == null || pixel == color) && button.currentTextColor == textColor) {
                "$fixture $label background/text mismatch: shape=${fill.color?.defaultColor} " +
                    "draw=$pixel text=${button.currentTextColor} expected=$color/$textColor"
            }
        }
        checkState("initial", enabledColor, android.graphics.Color.WHITE, true)
        val initial = background.current
        button.isEnabled = false
        checkState("disabled", disabledColor, disabledTextColor, false)
        button.isEnabled = true
        checkState("reenabled", enabledColor, android.graphics.Color.WHITE, true)
        check(background.current === initial) { "$fixture reenabled branch changed unexpectedly" }
        val wasFocusableInTouchMode = button.isFocusableInTouchMode
        try {
            button.isFocusableInTouchMode = true
            check(button.requestFocus() && button.isFocused) { "$fixture could not enter focus state" }
            checkState("focused", enabledColor, android.graphics.Color.WHITE, true,
                checkPixel = false, checkFocusBorder = true)
            check(background.current !== initial) { "$fixture focused branch did not activate" }
            button.isEnabled = false
            checkState("disabled after focus", disabledColor, disabledTextColor, false)
        } finally {
            button.isEnabled = true
            button.clearFocus()
            button.isFocusableInTouchMode = wasFocusableInTouchMode
        }
        checkState("focus cleared", enabledColor, android.graphics.Color.WHITE, true)
    }
    private fun checkNativePrimaryStyles(activity: Activity) {
        val fixtureParent = android.widget.LinearLayout(activity)
        val hoyi = HoyiUi.button(activity, fixtureParent, "Primary", primary = true) {}
        fixtureParent.measure(View.MeasureSpec.makeMeasureSpec(HoyiUi.dp(activity, 240), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        fixtureParent.layout(0, 0, fixtureParent.measuredWidth, fixtureParent.measuredHeight)
        checkPrimaryButtonStates(hoyi, "HoyiUi primary", activity.getColor(R.color.mobile_primary_button),
            activity.getColor(R.color.mobile_background), activity.getColor(R.color.mobile_muted),
            activity.getColor(android.R.color.white))
        val confirm = views(root(activity)).filterIsInstance<Button>().single {
            it.isShown && it.text.toString() == activity.getString(R.string.dose_confirm)
        }
        checkPrimaryButtonStates(confirm, "TaskProductVisualStyle dose confirm", android.graphics.Color.parseColor("#087A78"),
            android.graphics.Color.parseColor("#ECEFF1"), android.graphics.Color.parseColor("#8A95A3"),
            android.graphics.Color.parseColor(if ((activity.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                "#3B4B4C" else "#DDE3E7"))
    }
    /** Capture the already exercised state, not a new fixture or a replacement for assertions. */
    private fun captureState(activity: Activity, name: String, requiredActions: List<Int>, targetRoot: View = root(activity)) {
        test.waitForIdleSync()
        awaitMain { targetRoot.isAttachedToWindow && targetRoot.hasWindowFocus() &&
            targetRoot.isLaidOut && !targetRoot.isLayoutRequested }
        onMain {
            val labels = views(targetRoot).filterIsInstance<TextView>()
            requiredActions.forEach { id ->
                check(labels.any { it.isShown && it.isClickable && it.text.toString() == activity.getString(id) }) {
                    "$name missing expected action ${activity.getString(id)}"
                }
            }
        }
        awaitUnobscuredContent(activity, name, targetRoot)
        onMain { check(targetRoot.isAttachedToWindow && targetRoot.hasWindowFocus()) { "$name target window lost focus before capture" } }
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "advanced-page-screenshots/business")
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = requireNotNull(test.uiAutomation.takeScreenshot()) { "$name screenshot missing" }
        try {
            File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
        if (name == "dose-pending-confirmation") {
            var diagnostic = ""
            onMain {
                diagnostic = runCatching {
                    val button = views(targetRoot).filterIsInstance<Button>().single {
                        it.isShown && it.text.toString() == activity.getString(R.string.dose_confirm)
                    }
                    fun color(value: Int) = String.format(java.util.Locale.ROOT, "#%08X", value)
                    fun drawable(value: android.graphics.drawable.Drawable?, depth: Int = 0): String {
                        if (value == null) return "null"
                        val currentColor = (value as? android.graphics.drawable.GradientDrawable)?.color
                            ?.getColorForState(value.state, android.graphics.Color.TRANSPARENT)
                        val detail = "${value.javaClass.simpleName}(state=${value.state.joinToString()},alpha=${value.alpha}," +
                            "solid=${currentColor?.let(::color)})"
                        if (depth >= 3) return detail
                        val children = when (value) {
                            is android.graphics.drawable.StateListDrawable -> listOf(value.current)
                            is android.graphics.drawable.LayerDrawable -> (0 until value.numberOfLayers).map(value::getDrawable)
                            else -> emptyList()
                        }.filter { it !== value }
                        return detail + children.joinToString(prefix = "[", postfix = "]") { drawable(it, depth + 1) }
                    }
                    val bitmap = android.graphics.Bitmap.createBitmap(button.width, button.height,
                        android.graphics.Bitmap.Config.ARGB_8888)
                    val pixels = try {
                        button.draw(android.graphics.Canvas(bitmap))
                        val samples = listOf(1 to 2, 1 to 5, 1 to 8, 5 to 5, 8 to 5).map { (x, y) ->
                            val px = ((button.width - 1) * x / 10).coerceIn(0, button.width - 1)
                            val py = ((button.height - 1) * y / 10).coerceIn(0, button.height - 1)
                            "($px,$py)=${color(bitmap.getPixel(px, py))}"
                        }
                        val counts = mutableMapOf<Int, Int>()
                        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                            val pixel = bitmap.getPixel(x, y)
                            counts[pixel] = (counts[pixel] ?: 0) + 1
                        }
                        "samples=${samples.joinToString()} topColors=" + counts.entries.sortedByDescending { it.value }
                            .take(8).joinToString { "${color(it.key)}:${it.value}" }
                    } finally { bitmap.recycle() }
                    val parents = generateSequence(button as View?) { it.parent as? View }
                        .joinToString(" > ") { "${it.javaClass.simpleName}(alpha=${it.alpha},visibility=${it.visibility})" }
                    "enabled=${button.isEnabled} focused=${button.isFocused} pressed=${button.isPressed} " +
                        "drawableState=${button.drawableState.joinToString()} " +
                        "stateEnabled=${android.R.attr.state_enabled in button.drawableState} " +
                        "currentTextColor=${color(button.currentTextColor)} " +
                        "backgroundTint=${button.backgroundTintList?.getColorForState(button.drawableState, 0)?.let(::color)}\n" +
                        "background=${drawable(button.background)}\nparents=$parents\nlocalDraw=$pixels\n"
                }.getOrElse { "diagnosticFailure=${it.stackTraceToString()}\n" }
            }
            File(directory, "$name-button.txt").writeText("postScreenshot=true\n" + diagnostic)
        }
    }
    /** Read-only: let transient clipboard controls expire; never dismiss UI or alter clipboard data. */
    private fun awaitUnobscuredContent(activity: Activity, name: String, targetRoot: View) {
        val automation = test.uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        try {
            info.flags = originalFlags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = info
            val deadline = SystemClock.elapsedRealtime() + 15000
            while (true) {
                val contentBounds = android.graphics.Rect()
                onMain {
                    check(targetRoot.getGlobalVisibleRect(contentBounds))
                    // GlobalVisibleRect is rooted at this Window's decor; accessibility
                    // bounds are on screen. Include the real dialog/window offset.
                    val rootOnScreen = IntArray(2)
                    targetRoot.rootView.getLocationOnScreen(rootOnScreen)
                    contentBounds.offset(rootOnScreen[0], rootOnScreen[1])
                }
                val overlaps = mutableListOf<String>()
                fun inspect(node: android.view.accessibility.AccessibilityNodeInfo) {
                    try {
                        if (node.isVisibleToUser && node.isClickable) {
                            val bounds = android.graphics.Rect()
                            node.getBoundsInScreen(bounds)
                            if (android.graphics.Rect.intersects(bounds, contentBounds))
                                overlaps += "${node.packageName}/${node.className} bounds=$bounds"
                        }
                        for (index in 0 until node.childCount) node.getChild(index)?.let(::inspect)
                    } finally { node.recycle() }
                }
                val windows = automation.windows
                var appWindowObserved = false
                try {
                    for (window in windows) {
                        val node = window.root ?: continue
                        if (node.packageName?.toString() == activity.packageName) {
                            appWindowObserved = true
                            node.recycle()
                        } else inspect(node)
                    }
                } finally { windows.forEach { it.recycle() } }
                if (appWindowObserved && overlaps.isEmpty()) return
                check(SystemClock.elapsedRealtime() < deadline) {
                    "$name screenshot not ready: appWindowObserved=$appWindowObserved systemControls=${overlaps.joinToString()}"
                }
                Thread.sleep(100)
            }
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }
    private fun click(activity: Activity, label: Int) = click(root(activity), activity.getString(label))
    private fun click(root: View, text: String) {
        if (CurveMenuChecks.clickIfNeeded(test, root, text)) return
        onMain {
            val button = views(root).filterIsInstance<TextView>().single { it.isShown && it.isClickable && it.text.toString() == text }
            check(button.isEnabled) { "Disabled UI action: $text" }
            button.requestRectangleOnScreen(android.graphics.Rect(0, 0, button.width, button.height), true)
            check(button.performClick()) { "Action not handled: $text" }
        }
        test.waitForIdleSync()
    }

    /** Resolve every field by its actual label, and select its live visible section before access. */
    private fun visibleInput(root: View, label: String, index: Int = 0): EditText {
        lateinit var input: EditText
        var sections: Spinner? = null
        var shown = false
        var sectionCount = 0
        onMain {
            val accessibleInput = views(root).filterIsInstance<EditText>()
                .filter { it.contentDescription?.toString() == label }.getOrNull(index)
            input = accessibleInput ?: run {
                val title = views(root).filterIsInstance<TextView>()
                    .filter { it.text.toString() == label && it.labelFor != View.NO_ID }
                    .getOrNull(index) ?: error("Input label not found: $label index=$index")
                root.findViewById(title.labelFor)
            }
            sections = views(root).filterIsInstance<Spinner>().singleOrNull { it.tag == "bean-sections" }
            shown = input.isShown
            sectionCount = sections?.count ?: 0
        }
        if (!shown && sections != null) for (position in 0 until sectionCount) {
            onMain { sections!!.setSelection(position) }
            test.waitForIdleSync()
            awaitMain { sections!!.selectedItemPosition == position && !root.isLayoutRequested }
            onMain { shown = input.isShown }
            if (shown) break
        }
        onMain {
            input.requestRectangleOnScreen(android.graphics.Rect(0, 0, input.width, input.height), true)
        }
        test.waitForIdleSync()
        awaitMain { !root.isLayoutRequested }
        onMain {
            val rect = android.graphics.Rect()
            check(input.isShown && input.isEnabled && input.getGlobalVisibleRect(rect) &&
                rect.width() == input.width && rect.height() == input.height && input.width > 0 && input.height > 0) {
                "Input section is not fully visible/enabled: $label index=$index"
            }
        }
        return input
    }
    private fun fill(root: View, label: String, value: String, index: Int = 0) {
        val input = visibleInput(root, label, index)
        onMain { input.setText(value); check(input.text.toString() == value) { "Input rejected fixture: $label" } }
    }
    private fun fill(activity: Activity, label: Int, value: String, index: Int = 0) = fill(root(activity), activity.getString(label), value, index)
    private fun assertInput(activity: Activity, label: Int, expected: String, index: Int = 0) {
        val input = visibleInput(root(activity), activity.getString(label), index)
        onMain { check(input.text.toString() == expected) { "Restored field differs: ${activity.getString(label)} index=$index expected=$expected actual=${input.text}" } }
    }
    private fun assertReviewFields(activity: Activity, notes: BrewJournal.Notes) {
        assertInput(activity, R.string.journal_dose, BeanQuantity.formatGrams(requireNotNull(notes.doseMg)))
        onMain {
            val bean = views(root(activity)).filterIsInstance<Spinner>().single { it.contentDescription == activity.getString(R.string.journal_bean) }
            check(bean.isShown && bean.selectedItem.toString() == notes.beanName) { "Restored journal bean selection differs" }
        }
        assertInput(activity, R.string.journal_grind, notes.grind)
        assertInput(activity, R.string.journal_expectation, notes.curveExpectation)
        assertInput(activity, R.string.journal_taste, notes.taste)
        assertInput(activity, R.string.journal_next, notes.nextAdjustment)
    }
    private fun assertCurveFields(activity: Activity, name: String, temperature: Int, cup: Int, stages: List<CustomCurveDocument.Stage>) {
        assertInput(activity, R.string.profiles_name, name)
        assertInput(activity, R.string.profiles_temperature, temperature.toString())
        assertInput(activity, R.string.profiles_cup, CurveDraftNumber.format(cup, 2))
        onMain {
            val count = views(root(activity)).filterIsInstance<CurveStageCountTabs>().single()
            check(count.isShown && count.selectedCount == stages.size) { "Restored curve stage count differs" }
        }
        stages.forEachIndexed { index, stage ->
            assertInput(activity, R.string.profiles_pressure, CurveDraftNumber.format(stage.target, 1), index)
            assertInput(activity, R.string.profiles_water, CurveDraftNumber.format(stage.waterTenthsMl, 1), index)
        }
    }
    private fun persistedBytes(name: String): ByteArray = requireNotNull(
        AtomicDocumentStorage(File(context.filesDir, name).toPath()).read()) { "Saved document missing on disk: $name" }
    private fun persistedInventory(): BeanInventory {
        val bytes = persistedBytes("bean_inventory_v1.bin")
        return BeanInventory(object : InventoryStorage {
            override fun read(): ByteArray = bytes.copyOf()
            override fun write(bytes: ByteArray) { error("Read-only inventory verification cannot write") }
        })
    }
    private fun persistedJournal(): BrewJournal {
        val document = persistedBytes("brew_journal_v1.json").toString(Charsets.UTF_8)
        return BrewJournal(object : BrewJournal.Storage {
            override fun read(): String = document
            override fun write(value: String) { error("Read-only journal verification cannot write") }
        })
    }
    private fun persistedCurves(): CustomCurveStore {
        val document = persistedBytes("custom_curves_v1.json").toString(Charsets.UTF_8)
        return CustomCurveStore(object : CustomCurveStore.Storage {
            override fun read(): String = document
            override fun write(value: String) { error("Read-only curve verification cannot write") }
        })
    }
    private fun start(type: Class<out Activity>, extras: (Intent) -> Unit = {}): Activity {
        if (type == HomeActivity::class.java) {
            val activity = MainPageTestHost.launch(test).activity
            rootHost = activity
            opened += activity
            return activity
        }
        if (type == BeanInventoryActivity::class.java) {
            val activity = requireNotNull(rootHost) { "Bean inventory needs the actual MainActivity host" }
            MainPageTestHost(test, activity).select(TaskDestination.BEANS)
            return activity
        }
        val intent = Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also(extras)
        val activity = test.startActivitySync(intent).also { opened += it }
        test.waitForIdleSync()
        awaitMain { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
        return activity
    }
    private fun close(activity: Activity) {
        if (activity === rootHost) {
            MainPageTestHost(test, activity as MainActivity).select(TaskDestination.BREW)
            return
        }
        onMain { if (!activity.isDestroyed) activity.finish() }; awaitMain { activity.isDestroyed }; opened.remove(activity)
    }
    private fun recreate(activity: Activity): Activity {
        val monitor = test.addMonitor(activity.javaClass.name, null, false)
        try {
            onMain { activity.recreate() }
            val replacement = test.waitForMonitorWithTimeout(monitor, 10000) ?: error("Recreation missing")
            opened.remove(activity); opened += replacement
            if (activity === rootHost) rootHost = replacement as MainActivity
            test.waitForIdleSync()
            awaitMain { replacement.hasWindowFocus() && root(replacement).isLaidOut && !root(replacement).isLayoutRequested }
            return replacement
        } finally { test.removeMonitor(monitor) }
    }
    private fun dialog(activity: Activity): AlertDialog {
        var result: AlertDialog? = null
        onMain {
            result = when (activity) {
                is BeanBatchActivity -> (BeanBatchActivity::class.java.getDeclaredField("dialogs").apply { isAccessible = true }.get(activity) as List<*>)
                    .filterIsInstance<AlertDialog>().last { it.isShowing }
                is BeanPreparationActivity -> BeanPreparationActivity::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(activity) as AlertDialog
                else -> error("Unsupported local dialog owner")
            }
        }
        return requireNotNull(result)
    }
    private fun dialogClick(dialog: AlertDialog, which: Int, expected: String) {
        onMain {
            val button = dialog.getButton(which)
            check(button.isEnabled && button.text.toString() == expected)
            check(button.performClick())
        }
        test.waitForIdleSync()
    }
    private fun owner(home: Activity): MobileService? = (home as? MainActivity)?.currentService
    private fun inventoryPage(activity: MainActivity): BeanInventoryFragment =
        MainPageTestHost(test, activity).active("root:BEANS") as BeanInventoryFragment

    /** M1 isolated caller owns every store; provider outputs stay in the signature-guarded test APK.
     * Cancellation/failure completion are observed, never inferred from a still-disabled button. */
    fun verifyM1ExportContract(initial: CurveShareActivity, document: CustomCurveDocument, directory: File): CurveShareActivity {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(context.checkSelfPermission(CurveFixtureProvider.PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        check(context.packageManager.checkSignatures(context.packageName, test.context.packageName) == android.content.pm.PackageManager.SIGNATURE_MATCH)
        val session = UUID.randomUUID().toString()
        val base = android.net.Uri.parse("content://${CurveFixtureProvider.AUTHORITY}")
        fun call(method: String, data: android.os.Bundle? = null) = requireNotNull(context.contentResolver.call(base, method, session, data))
        val report = org.json.JSONArray()
        var activity = initial
        fun exported(): android.net.Uri? = CurveShareActivity::class.java.getDeclaredField("exportedUri").apply { isAccessible = true }.get(activity) as? android.net.Uri
        fun fileAction(): TextView = root(activity).findViewWithTag("product/share/file")
        fun picker(code: Int, uri: android.net.Uri?) {
            var observed: Intent? = null; var count = 0
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
                    observed = Intent(intent); count++
                    return Instrumentation.ActivityResult(code, uri?.let { Intent().setData(it)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) })
                }
            }
            test.addMonitor(monitor)
            try {
                click(activity, R.string.profiles_export_file)
                onMain {
                    val intent = requireNotNull(observed)
                    check(count == 1 && intent.action == Intent.ACTION_CREATE_DOCUMENT && intent.type == "application/json")
                    check(intent.categories == setOf(Intent.CATEGORY_OPENABLE) && intent.component == null && intent.`package` == null)
                    check(intent.getStringExtra(Intent.EXTRA_TITLE) == "openhoyi-${document.id}.json")
                }
            } finally { test.removeMonitor(monitor) }
        }
        fun failedExport(uri: android.net.Uri, expected: android.net.Uri?) {
            val toast = test.uiAutomation.executeAndWaitForEvent({ picker(Activity.RESULT_OK, uri) }, { event ->
                event.eventType == android.view.accessibility.AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                    event.packageName?.toString() == activity.packageName && event.text.any { it.toString() == activity.getString(R.string.profiles_export_failed) }
            }, 10000)
            try { check(toast.text.any { it.toString() == activity.getString(R.string.profiles_export_failed) }) }
            finally { toast.recycle() }
            test.waitForIdleSync()
            onMain { check(exported() == expected && fileAction().isEnabled == (expected != null)) }
            report.put(org.json.JSONObject().put("state", "failure").put("completion", "actual_export_failed_toast")
                .put("expectedUri", expected ?: org.json.JSONObject.NULL).put("shareEnabled", expected != null))
        }
        call("create", android.os.Bundle().apply { putString("document", document.encode()) })
        try {
            onMain { check(exported() == null && !fileAction().isEnabled) }
            picker(Activity.RESULT_CANCELED, null)
            onMain { check(exported() == null && !fileAction().isEnabled) }
            check(call("stats").getInt("write:output.json") == 0)
            report.put(org.json.JSONObject().put("state", "cancel").put("writes", 0).put("shareEnabled", false))
            // The provider explicitly rejects writing its read-only valid fixture: a real output-stream failure.
            failedExport(CurveFixtureProvider.uri(session, "valid.json"), null)
            val output = CurveFixtureProvider.uri(session, "output.json")
            picker(Activity.RESULT_OK, output)
            awaitMain { exported() == output && fileAction().isEnabled }
            val bytes = requireNotNull(context.contentResolver.openInputStream(output)).use { it.readBytes() }
            check(bytes.contentEquals(document.encode().toByteArray(Charsets.UTF_8)))
            check(CustomCurveDocument.decode(bytes.toString(Charsets.UTF_8)) == document && call("stats").getInt("write:output.json") == 1)
            report.put(org.json.JSONObject().put("state", "success").put("exactUtf8", true).put("writes", 1).put("uri", output))
            activity = recreate(activity) as CurveShareActivity
            onMain { check(exported() == output && fileAction().isEnabled) }
            report.put(org.json.JSONObject().put("state", "recreated").put("restoredUri", output).put("shareEnabled", true))
            picker(Activity.RESULT_CANCELED, null)
            onMain { check(exported() == output && fileAction().isEnabled) }
            failedExport(CurveFixtureProvider.uri(session, "valid.json"), output)
            check(call("stats").getInt("write:output.json") == 1)
            // Intercept all launches, including a regressed direct send, before external dispatch.
            var sent: Intent? = null
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
                    sent = Intent(intent); return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
            test.addMonitor(monitor)
            try {
                click(activity, R.string.profiles_share_file)
                onMain {
                    val chooser = requireNotNull(sent); check(chooser.action == Intent.ACTION_CHOOSER)
                    @Suppress("DEPRECATION") val intent = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                    @Suppress("DEPRECATION") val stream = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                    check(intent.action == Intent.ACTION_SEND && intent.type == "application/json" && stream == output)
                    check(intent.clipData?.itemCount == 1 && intent.clipData?.getItemAt(0)?.uri == output)
                    check(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 && intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
                    check(chooser.clipData?.getItemAt(0)?.uri == output && chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                }
            } finally { test.removeMonitor(monitor) }
            report.put(org.json.JSONObject().put("state", "share-intercepted").put("readOnlyUriGrant", true).put("externalSend", false))
            return activity
        } finally {
            File(directory, "saf-export-business.json").writeText(report.toString(2))
            call("cleanup") // Only this provider session; never target-app business files.
        }
    }

    /** Separate legal three-event M1 state, after the two-event main image. No refunds or event deletion. */
    fun verifyM1InventoryThreeEvents(initial: MainActivity, inventory: BeanInventory, directory: File): MainActivity {
        val batchId = "m1-three-event-batch"
        inventory.addBatch("m1-three-add", io.openhoyi.bean.BeanBatch(batchId, io.openhoyi.bean.Bean("m1-three-bean", "三流水核验豆"), 100000))
        inventory.consume("m1-three-consume", batchId, 18000)
        inventory.adjust("m1-three-adjust", batchId, 8000, "盘点补差")
        val before = inventory.events()
        val expected = before.filter { it.batchId == batchId }
        check(expected.map { it.kind } == listOf(InventoryEventKind.ADD, InventoryEventKind.CONSUME, InventoryEventKind.ADJUST))
        check(expected.map { it.deltaMg } == listOf(100000L,-18000L,8000L) && expected.last().balanceMg == 90000L)
        var activity = initial
        onMain {
            BeanInventoryFragment::class.java.getDeclaredMethod("render").apply { isAccessible = true }
                .invoke(inventoryPage(activity))
        }
        // render() installs a new TaskPageFrame. Its pageContent is added to the
        // attached hierarchy on the first onMeasure, not synchronously by render.
        // Wait for actual draw commits and layout/tag readiness, never sleep or
        // retry a failed business assertion. The original click/assertions follow.
        val readiness = org.json.JSONArray()
        val frame = NativeWindowFrameEvidence(test)
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 5000
        fun readinessSnapshot(): org.json.JSONObject {
            val content = root(activity)
            val batch = content.findViewWithTag<View>("inventory-batch-$batchId")
            return org.json.JSONObject().put("atElapsedMs", SystemClock.elapsedRealtime())
                .put("requestedBatchTag", "inventory-batch-$batchId")
                .put("rootAttached", content.isAttachedToWindow).put("rootLaidOut", content.isLaidOut)
                .put("rootLayoutRequested", content.isLayoutRequested).put("hasWindowFocus", activity.hasWindowFocus())
                .put("batchPresent", batch != null).put("batchAttached", batch?.isAttachedToWindow ?: false)
                .put("batchLaidOut", batch?.isLaidOut ?: false).put("batchLayoutRequested", batch?.isLayoutRequested ?: true)
                .put("batchSizePx", batch?.let { org.json.JSONArray(listOf(it.width,it.height)) } ?: org.json.JSONObject.NULL)
                .put("availableBatchTags", org.json.JSONArray(views(content).mapNotNull { it.tag?.toString() }
                    .filter { it.startsWith("inventory-batch-") }))
        }
        try {
            while (true) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                check(remaining > 0) { "Three-event batch layout/tag did not become ready within 5000ms" }
                val committed = frame.committedFrame(root(activity), remaining)
                var ready = false
                onMain {
                    val observedAt = SystemClock.elapsedRealtime()
                    val withinBudget = observedAt <= deadline
                    val sample = readinessSnapshot().put("committedFrame", committed)
                        .put("elapsedMs", observedAt - started).put("withinBudget", withinBudget)
                    readiness.put(sample)
                    check(withinBudget) { "Three-event batch layout readiness exceeded 5000ms total budget" }
                    ready = sample.getBoolean("rootAttached") && sample.getBoolean("rootLaidOut") &&
                        !sample.getBoolean("rootLayoutRequested") && sample.getBoolean("hasWindowFocus") &&
                        sample.getBoolean("batchPresent") && sample.getBoolean("batchAttached") &&
                        sample.getBoolean("batchLaidOut") && !sample.getBoolean("batchLayoutRequested") &&
                        sample.getJSONArray("batchSizePx").let { it.getInt(0)>0 && it.getInt(1)>0 }
                }
                if (ready) {
                    check(SystemClock.elapsedRealtime() <= deadline) { "Three-event batch readiness accepted after deadline" }
                    break
                }
            }
            onMain {
                val batch = requireNotNull(root(activity).findViewWithTag<View>("inventory-batch-$batchId"))
                check(batch.isEnabled && batch.performClick())
            }
        } catch (failure: Throwable) {
            lateinit var before: org.json.JSONObject
            onMain { before = readinessSnapshot() }
            File(directory, "inventory-three-event-layout-readiness.json").writeText(org.json.JSONObject()
                .put("timeoutMs",5000).put("samples",readiness).put("failureSnapshot",before)
                .put("failure",failure.stackTraceToString()).put("status","FAIL").toString(2))
            // Preserve the failed fixture before Scope cleanup. This is explicitly
            // an unsynchronized diagnostic, not an accepted protocol-2 product PNG.
            try {
                val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
                try { File(directory,"inventory-three-event-layout-failure.png").outputStream().use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))
                } } finally { bitmap.recycle() }
                onMain { File(directory,"inventory-three-event-layout-readiness.json").writeText(org.json.JSONObject()
                    .put("timeoutMs",5000).put("samples",readiness).put("failureSnapshot",before)
                    .put("afterDiagnosticPngSnapshot",readinessSnapshot()).put("pngFrameSynchronization","unknown_diagnostic_only")
                    .put("failure",failure.stackTraceToString()).put("status","FAIL").toString(2)) }
            } catch (captureFailure: Throwable) { failure.addSuppressed(captureFailure) }
            throw failure
        }
        File(directory,"inventory-three-event-layout-readiness.json").writeText(org.json.JSONObject()
            .put("timeoutMs",5000).put("samples",readiness).put("status","PASS_LAYOUT_READY_ONLY_NOT_BUSINESS_RESULT").toString(2))
        test.waitForIdleSync()
        val records = org.json.JSONArray()
        expected.asReversed().forEach { event ->
            lateinit var row: View
            onMain {
                row = root(activity).findViewWithTag("product/inventory/events/row-${event.eventId}")
                check(row.isEnabled && row.isClickable && row.height >= HoyiUi.dp(activity,48))
                row.requestRectangleOnScreen(android.graphics.Rect(0,0,row.width,row.height),true)
            }
            test.waitForIdleSync()
            onMain {
                val bounds = android.graphics.Rect()
                check(row.getGlobalVisibleRect(bounds) && bounds.width()==row.width && bounds.height()==row.height && row.width>0)
                val cells = listOf("type","change","reason").map { key -> row.findViewWithTag<TextView>("product/inventory/events/${event.eventId}/$key") }
                check(cells.all { it.isShown && it.text.isNotBlank() })
                val delta=(if(event.deltaMg>0) "+" else "") + BeanQuantity.formatGrams(event.deltaMg) + " g"
                check(cells[1].text.toString()==delta && cells[2].text.toString()==(event.reason ?: "—"))
                cells.forEach { LanguageUiLayoutChecks.textFits(it,"M1-three-event-${event.eventId}") }
                records.put(org.json.JSONObject().put("eventId",event.eventId).put("kind",event.kind).put("deltaMg",event.deltaMg)
                    .put("balanceMg",event.balanceMg).put("heightPx",row.height).put("minimumTouchPx",HoyiUi.dp(activity,48))
                    .put("visibleRectPx",org.json.JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom))))
                check(row.performClick())
            }
            test.waitForIdleSync()
            lateinit var dialogRoot: View
            awaitMain {
                val candidates=android.view.inspector.WindowInspector.getGlobalWindowViews().filter { it!==activity.window.decorView && it.hasWindowFocus() }
                dialogRoot=candidates.singleOrNull() ?: return@awaitMain false
                views(dialogRoot).filterIsInstance<TextView>().any { it.text.toString().contains(activity.getString(R.string.beans_event_balance,BeanQuantity.formatGrams(event.balanceMg))) }
            }
            onMain {
                val text=views(dialogRoot).filterIsInstance<TextView>().joinToString("\n") { it.text.toString() }
                check(text.contains(BeanQuantity.formatGrams(event.deltaMg)+" g") && text.contains(event.reason ?: "—"))
                check(dialogRoot.findViewById<View>(android.R.id.button1).performClick())
            }
            test.waitForIdleSync()
            check(inventory.events()==before && inventory.batches().single { it.batch.id==batchId }.balanceMg==90000L)
        }
        var oldScroll=0
        onMain { oldScroll=(BeanInventoryFragment::class.java.getDeclaredField("listScroll").apply { isAccessible=true }
            .get(inventoryPage(activity)) as? android.widget.ScrollView)?.scrollY ?: 0 }
        activity=recreate(activity) as MainActivity
        awaitMain {
            val fragment = inventoryPage(activity)
            val selected=BeanInventoryFragment::class.java.getDeclaredField("selectedBatchId").apply { isAccessible=true }.get(fragment)
            val scroll=BeanInventoryFragment::class.java.getDeclaredField("listScroll").apply { isAccessible=true }.get(fragment) as? android.widget.ScrollView
            selected==batchId && scroll?.scrollY==oldScroll
        }
        check(inventory.events()==before && inventory.batches().single { it.batch.id==batchId }.balanceMg==90000L)
        File(directory,"inventory-three-event-business.json").writeText(org.json.JSONObject().put("fixture","ADD100_CONSUME18_ADJUST8_balance90")
            .put("events",records).put("allDetailsOpened",true).put("inventoryUnchangedByBrowsing",true)
            .put("selectedBatchRestored",true).put("listScrollRestoredPx",oldScroll).put("noEventRemovalOrRefund",true).toString(2))
        return activity
    }

    /** Real M2 callbacks on the caller's independently isolated stores; never starts a machine/service. */
    fun verifyM2FormContract(initial: Activity, page: String, directory: File, rejectWrites: (Boolean) -> Unit, unavailable: (Boolean) -> Unit,
        capture: (Activity,String) -> Unit) {
        val app=context.applicationContext as MobileApplication
        val inventory=app.beanInventoryResult.getOrThrow()
        val journal=app.journalResult.getOrThrow()
        val custom=app.customCurvesResult.getOrThrow()
        val usage=app.curveUsageResult.getOrThrow()
        val inventoryBefore=inventory.events().toList()
        val batchesBefore=inventory.batches().toList()
        val journalBefore=journal.entries().toList()
        val curvesBefore=custom.list().toList()
        val usageBefore=curvesBefore.associate { it.id to usage.stats(it.id) }
        val records=org.json.JSONArray()
        var activity=initial
        fun record(path: String) {
            records.put(org.json.JSONObject().put("path",path).put("inventoryEventIds",org.json.JSONArray(inventory.events().map { it.eventId }))
                .put("journal",org.json.JSONObject(journal.exportJson())).put("curveIds",org.json.JSONArray(custom.list().map { it.id })))
            File(directory,"form-business.json").writeText(org.json.JSONObject().put("page",page).put("completed",false).put("paths",records).toString(2))
        }
        @Suppress("UNCHECKED_CAST")
        fun fields(owner: Activity=activity) = owner.javaClass.getDeclaredField("fields").apply { isAccessible=true }.get(owner) as Map<String,EditText>
        fun input(key: String, value: String) = onMain { fields().getValue(key).setText(value); check(fields().getValue(key).text.toString()==value) }
        fun snapshot(transitionProbe:ImeViewportTransitionProbe?=null,source:String="snapshot",frameIndex:Int?=null): org.json.JSONObject {
            lateinit var result: org.json.JSONObject
            onMain {
                result=org.json.JSONObject().put("fields",org.json.JSONObject(fields().mapValues { (_,v) ->
                    org.json.JSONObject().put("value",v.text.toString()).put("selection",org.json.JSONArray(listOf(v.selectionStart,v.selectionEnd)))
                })).put("focusedFieldKey",fields().entries.firstOrNull { it.value.hasFocus() }?.key ?: org.json.JSONObject.NULL)
                    .put("imeVisible",root(activity).rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) ?: false)
                    .put("contentSizePx",org.json.JSONArray(listOf(root(activity).width,root(activity).height)))
                    .put("scrolls",org.json.JSONArray(views(root(activity)).filterIsInstance<android.widget.ScrollView>().map {
                    org.json.JSONObject().put("tag",it.tag).put("x",it.scrollX).put("y",it.scrollY)
                }))
                val state=activity.javaClass.getDeclaredMethod("onSaveInstanceState",android.os.Bundle::class.java).apply { isAccessible=true }
                val bundle=android.os.Bundle(); state.invoke(activity,bundle)
                result.put("savedIdentity",org.json.JSONObject(listOf("draftId","stageCount","selectedBeanId","rememberedBeanId","existingMode","newNameDraft","eventId","batchId","newBeanId")
                    .filter { bundle.containsKey(it) }.associateWith { bundle.get(it) ?: org.json.JSONObject.NULL }))
                transitionProbe?.sample(source,frameIndex,force=false)
            }
            return result
        }
        fun unchangedExceptNotesOrDraft() {
            check(inventory.events()==inventoryBefore && inventory.batches()==batchesBefore) { "M2 editing changed inventory" }
            check(journal.entries().map { it.observation }==journalBefore.map { it.observation }) { "M2 editing changed observations" }
            check(curvesBefore.associate { it.id to usage.stats(it.id) }==usageBefore) { "M2 editing changed use counts" }
        }
        fun stableSnapshot(label: String,transitionProbe:ImeViewportTransitionProbe?=null): org.json.JSONObject {
            val samples=org.json.JSONArray()
            val deadline=SystemClock.elapsedRealtime()+5000
            var previous: String?=null; var unchanged=0
            try {
                while(SystemClock.elapsedRealtime()<deadline) {
                    NativeWindowFrameEvidence(test).committedFrame(root(activity))
                    val value=snapshot(transitionProbe,"stable-frame-$label",samples.length()+1)
                    var pending=false
                    onMain { pending=views(root(activity)).any { it.isShown && it.isLayoutRequested } }
                    val signature=value.toString()
                    unchanged=if(!pending && signature==previous) unchanged+1 else if(!pending) 1 else 0
                    val within=SystemClock.elapsedRealtime()<=deadline
                    samples.put(org.json.JSONObject().put("actualCommittedDrawIndex",samples.length()+1).put("pendingLayout",pending)
                        .put("consecutiveUnchanged",unchanged).put("withinBudget",within).put("state",value))
                    previous=signature
                    if(unchanged>=3 && within) return value
                }
                error("M2 $label did not reach three stable actual frames within 5000ms")
            } finally {
                File(directory,"$label-stability.json").writeText(org.json.JSONObject().put("samples",samples)
                    .put("policy","real_committed_frames; state_stability_only_not_expected_state_matching; no_sleep").toString(2))
            }
        }
        fun roundTrip(label: String) {
            val key=when(page) { "bean-entry" -> "opened"; "review" -> "next"; else -> "target-3" }
            val causalSite="AdvancedBusinessUiChecks.verifyM2FormContract.editor-stage-hidden-values"
            val causal=if(page=="curve-editor" && label=="editor-stage-hidden-values" &&
                directory.parentFile?.name=="tablet-user-landscape-light-font-1.0")
                ImeRecreationTrace(test,directory,"CurveEditor.editor-stage-hidden-values.recreate",causalSite,null) else null
            val transitionProbe=if(causal!=null) ImeViewportTransitionProbe("CurveEditor.editor-stage-hidden-values.recreate",{ owner,attempt ->
                attempt.value("targetCount",null);attempt.value("targetCountStatus","not_evaluated")
                attempt.value("hostCount",null);attempt.value("hostCountStatus","not_evaluated")
                attempt.stage("page-root-read");val pageRoot=root(owner);attempt.viewReference("pageRoot",pageRoot)
                attempt.stage("field-map-read");attempt.value("fieldKey",key)
                val target=fields(owner)[key];attempt.value("targetNull",target==null);attempt.viewReference("target",target)
                attempt.stage("target-count");val targetCount=if(target!=null) views(pageRoot).count { it===target } else null
                if(targetCount!=null) {attempt.value("targetCount",targetCount);attempt.value("targetCountStatus","evaluated")}
                attempt.stage("first-check");check(target!=null && targetCount==1)
                attempt.stage("form-reflection");val form=owner.javaClass.getDeclaredField("form").apply { isAccessible=true }.get(owner)
                attempt.reference("form",form)
                attempt.stage("host-reflection");val host=form.javaClass.getDeclaredField("host").apply { isAccessible=true }.get(form) as View
                attempt.viewReference("host",host)
                attempt.stage("host-count");val hostCount=views(pageRoot).count { it===host }
                attempt.value("hostCount",hostCount);attempt.value("hostCountStatus","evaluated")
                attempt.stage("second-check");check(hostCount==1)
                attempt.stage("targets");val scrolls=views(pageRoot).filterIsInstance<android.widget.ScrollView>()
                ImeViewportTransitionProbe.Targets(pageRoot,host,target,scrolls)
            },{ owner ->
                val form=owner.javaClass.getDeclaredField("form").apply { isAccessible=true }.get(owner)
                (form as? TaskCurveEditorForm)?.productRestoreEvidence
            }) else null
            var causalError:Throwable?=null
            fun diagnose(action:()->Unit) { try { action() } catch(error:Throwable) {
                val previous=causalError
                if(previous==null) causalError=error else previous.addSuppressed(error)
            } }
            val observedDecors=mutableListOf<Pair<View,View.OnAttachStateChangeListener>>()
            fun observeDecor(owner:Activity) {
                val decor=owner.window.decorView
                if(observedDecors.any { (view,_) -> view===decor }) return
                val listener=object:View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(view:View) { diagnose { causal?.observe("decor_attached",owner,view,causalSite,"decor") } }
                    override fun onViewDetachedFromWindow(view:View) { diagnose { causal?.observe("decor_detached",owner,view,causalSite,"decor") } }
                }
                decor.addOnAttachStateChangeListener(listener);observedDecors.add(decor to listener)
            }
            val lifecycle=object:Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(owner:Activity,state:Bundle?) { if(owner is CurveEditorActivity) diagnose {
                    causal?.observe("activity_created",owner,callerSite=causalSite) } }
                override fun onActivityPostCreated(owner:Activity,state:Bundle?) { if(owner is CurveEditorActivity) {
                    diagnose { causal?.observe("activity_post_created",owner,callerSite=causalSite)
                        transitionProbe?.install(owner,"replacement-post-created");observeDecor(owner) } } }
                override fun onActivityStarted(owner:Activity) { if(owner is CurveEditorActivity) diagnose { causal?.observe("activity_started",owner,callerSite=causalSite) } }
                override fun onActivityResumed(owner:Activity) { if(owner is CurveEditorActivity) diagnose { causal?.observe("activity_resumed",owner,callerSite=causalSite) } }
                override fun onActivityPaused(owner:Activity) { if(owner is CurveEditorActivity) diagnose { causal?.observe("activity_paused",owner,callerSite=causalSite) } }
                override fun onActivityStopped(owner:Activity) { if(owner is CurveEditorActivity) diagnose { causal?.observe("activity_stopped",owner,callerSite=causalSite) } }
                override fun onActivitySaveInstanceState(owner:Activity,state:Bundle) { if(owner is CurveEditorActivity) diagnose { causal?.observe("activity_save_state",owner,callerSite=causalSite) } }
                override fun onActivityDestroyed(owner:Activity) { if(owner is CurveEditorActivity) diagnose { causal?.observe("activity_destroyed",owner,callerSite=causalSite) } }
            }
            var observersRegistered=false
            var oldReference:ImeRecreationTrace.Snapshot?=null
            var roundTripFailure:Throwable?=null
            try {
            diagnose { causal?.start() }
            onMain {
                val target=fields().getValue(key)
                check(target.requestFocus())
                check(target.length()>0)
                target.setSelection(if(target.length()>3) 1 else 0, minOf(4,target.length()))
                diagnose { causal?.observe("fixture_setup_show_before",activity,target,causalSite,"field") }
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(target,0)
                diagnose { causal?.observe("fixture_setup_show_returned",activity,target,causalSite,"field") }
            }
            // Wait only for actual IME presence, never for a restore/visibility assertion to become true.
            val imeDeadline=SystemClock.elapsedRealtime()+5000
            var ime=false
            while(SystemClock.elapsedRealtime()<imeDeadline) {
                NativeWindowFrameEvidence(test).committedFrame(root(activity))
                onMain { ime=root(activity).rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==true }
                if(ime && SystemClock.elapsedRealtime()<=imeDeadline) break
            }
            check(ime && SystemClock.elapsedRealtime()<=imeDeadline) { "M2 $label IME not actually observed" }
            stableSnapshot("$label-ime-settled")
            val scrollFixture=org.json.JSONArray()
            fun finiteRange(scroll: android.widget.ScrollView): Int {
                val child=scroll.getChildAt(0) ?: return 0
                val margins=child.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                val content=child.height.toLong()+(margins?.topMargin ?: 0).toLong()+(margins?.bottomMargin ?: 0).toLong()
                val viewport=scroll.height.toLong()-scroll.paddingTop.toLong()-scroll.paddingBottom.toLong()
                check(viewport>=0L && content>=0L) { "M2 $label invalid measured scroll geometry" }
                val range=(content-viewport).coerceAtLeast(0L)
                check(range<=Int.MAX_VALUE.toLong()) { "M2 $label scroll range exceeds Int coordinates: $range" }
                return range.toInt()
            }
            fun assertFiniteScrolls(phase: String) = onMain {
                views(root(activity)).filterIsInstance<android.widget.ScrollView>().forEach { scroll ->
                    val range=finiteRange(scroll)
                    scrollFixture.put(org.json.JSONObject().put("phase",phase).put("tag",scroll.tag)
                        .put("rangePx",range).put("actualY",scroll.scrollY))
                    File(directory,"$label-finite-scroll-fixture.json").writeText(org.json.JSONObject()
                        .put("policy","measured_child_height_plus_margins_minus_padded_viewport; Long_arithmetic; no_sentinel")
                        .put("samples",scrollFixture).toString(2))
                    check(scroll.scrollY in 0..range) { "M2 $label $phase scroll outside measured range: ${scroll.tag} y=${scroll.scrollY} range=$range" }
                }
            }
            onMain {
                val target=fields().getValue(key)
                target.requestRectangleOnScreen(android.graphics.Rect(0,0,target.width,target.height),true)
                val scrolls=generateSequence(target.parent) { it.parent }.filterIsInstance<android.widget.ScrollView>().toList()
                check(scrolls.any { finiteRange(it)>0 }) { "M2 $label has no positive measured scroll range" }
                scrolls.forEach { it.scrollTo(it.scrollX,finiteRange(it)) }
            }
            val before=stableSnapshot("$label-before-recreate")
            assertFiniteScrolls("before-recreate")
            check(before.getString("focusedFieldKey")==key)
            check(before.getJSONObject("fields").getJSONObject(key).getJSONArray("selection").getInt(1)>0)
            check(before.getJSONArray("scrolls").let { values -> (0 until values.length()).any { values.getJSONObject(it).getInt("y")>0 } }) {
                "M2 $label did not establish a real nonzero scroll fixture; do not claim scroll restoration"
            }
            if(causal!=null) onMain { diagnose {
                transitionProbe?.install(activity,"old-before-recreate")
                transitionProbe?.sample("before-state-assertion")
                oldReference=causal.capture("old_before_recreate",activity,fields().getValue(key),activity.window.decorView,causalSite)
                observeDecor(activity);app.registerActivityLifecycleCallbacks(lifecycle);observersRegistered=true
            } }
            diagnose { causal?.mark("recreate_call_before",causalSite) }
            activity=recreate(activity)
            diagnose { causal?.mark("replacement_returned",causalSite) }
            if(causal!=null) onMain { diagnose {
                val replacement=causal.capture("new_after_replacement_returned",activity,fields().getValue(key),activity.window.decorView,causalSite)
                observeDecor(activity);oldReference?.let { causal.compare(it,replacement) }
            } }
            val after=stableSnapshot("$label-after-recreate",transitionProbe)
            assertFiniteScrolls("after-recreate")
            val equal=before.toString()==after.toString()
            lateinit var restoreDiagnostic: org.json.JSONObject
            onMain {
                transitionProbe?.sample("after-state-assertion")
                val form=activity.javaClass.getDeclaredField("form").apply { isAccessible=true }.get(activity)
                val evidence=if(form is TaskCurveEditorForm) org.json.JSONObject(form.productRestoreEvidence.toString())
                    else org.json.JSONObject().put("status","unknown").put("reason","form_has_no_productRestoreEvidence_interface")
                restoreDiagnostic=org.json.JSONObject().put("productRestoreEvidence",evidence)
                    .put("semantics","page_self_report_not_OS_confirmation; diagnostic_not_in_compared_before_after_objects")
                    .put("actualState",org.json.JSONObject()
                        .put("imeVisible",root(activity).rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) ?: org.json.JSONObject.NULL)
                        .put("hasWindowFocus",activity.hasWindowFocus())
                        .put("focusedFieldKey",fields().entries.firstOrNull { it.value.hasFocus() }?.key ?: org.json.JSONObject.NULL)
                        .put("contentSizePx",org.json.JSONArray(listOf(root(activity).width,root(activity).height)))
                        .put("scrolls",org.json.JSONArray(views(root(activity)).filterIsInstance<android.widget.ScrollView>().map {
                            org.json.JSONObject().put("tag",it.tag).put("x",it.scrollX).put("y",it.scrollY)
                        })))
            }
            File(directory,"$label-restore.json").writeText(org.json.JSONObject().put("before",before).put("after",after).put("equal",equal)
                .put("restoreDiagnosticBeforeAssertion",restoreDiagnostic)
                .put("afterObservedBeforeAnyCaptureUnfocus",true).toString(2))
            diagnose { causal?.mark("after_comparison_before",causalSite) }
            onMain { transitionProbe?.sample("strict-assertion")
                check(equal) { "M2 form recreation changed draft/focus/selection/identity/scroll: $label" } }
            unchangedExceptNotesOrDraft(); record(label)
            diagnose { causal?.mark("post_assert_capture_call_before","$causalSite.capture") }
            capture(activity,"$label-recreated") // Only after the untouched restored state has been asserted.
            diagnose { causal?.mark("post_assert_capture_returned","$causalSite.capture") }
            } catch(error:Throwable) { roundTripFailure=error;throw error }
            finally { if(causal!=null) {
                var probeFinalizationFailure:Throwable?=null
                fun preserveFinalization(error:Throwable,reportName:String) {
                    val primary=roundTripFailure ?: probeFinalizationFailure
                    if(primary!=null) primary.addSuppressed(error)
                    else try { File(directory,reportName).writeText(error.stackTraceToString()) }
                    catch(writeError:Throwable) { probeFinalizationFailure=error;error.addSuppressed(writeError) }
                }
                try { onMain { transitionProbe?.closeAndWrite(File(directory,"ime-viewport-transitions.json")) } }
                catch(secondary:Throwable) { if(roundTripFailure!=null)roundTripFailure.addSuppressed(secondary)
                    else probeFinalizationFailure=secondary }
                try {
                    var product:org.json.JSONObject?=null
                    onMain {
                        observedDecors.forEach { (view,listener) -> view.removeOnAttachStateChangeListener(listener) }
                        if(observersRegistered) app.unregisterActivityLifecycleCallbacks(lifecycle)
                        val form=activity.javaClass.getDeclaredField("form").apply { isAccessible=true }.get(activity)
                        if(form is TaskCurveEditorForm) product=org.json.JSONObject(form.productRestoreEvidence.toString())
                        causal.observe("observers_removed",activity,callerSite=causalSite)
                    }
                    product?.let(causal::productEvidence)
                } catch(secondary:Throwable) { preserveFinalization(secondary,"m2-ime-causal-finalization-error.txt") }
                try { causal.finish() } catch(secondary:Throwable) {
                    preserveFinalization(secondary,"m2-ime-trace-finalization-error.txt")
                }
                causalError?.let { diagnostic -> runCatching {
                    File(directory,"m2-ime-causal-observation-error.txt").writeText(diagnostic.stackTraceToString()) } }
                probeFinalizationFailure?.let { throw it }
            } }
        }
        fun error(label: String, save: Int, expected: Int) {
            click(activity,save)
            capture(activity,label)
            onMain {
                check(!activity.isFinishing && views(root(activity)).filterIsInstance<TextView>().any { it.isShown && it.text.toString()==activity.getString(expected) }) {
                    "M2 callback did not show expected error $label"
                }
            }
            unchangedExceptNotesOrDraft()
            check(journal.entries()==journalBefore && custom.list()==curvesBefore) { "Rejected M2 form wrote a model" }
            record(label)
        }
        try {
            when(page) {
                "bean-entry" -> {
                    fun mode(existing: Boolean) {
                        onMain { check(root(activity).findViewWithTag<View>(if(existing) "bean-mode-existing" else "bean-mode-new").performClick()) }
                        NativeWindowFrameEvidence(test).committedFrame(root(activity))
                    }
                    mode(true); roundTrip("bean-existing-identity")
                    mode(false); onMain { check(fields().getValue("name").text.toString()=="哥伦比亚 水洗") }
                    roundTrip("bean-new-draft")
                    input("name","哥伦比亚 水洗 ".repeat(30)); capture(activity,"bean-long-name")
                    input("name","哥伦比亚 水洗")
                    listOf("purchased","roasted","opened").forEach { key ->
                        var original=""
                        onMain { original=fields().getValue(key).text.toString(); check(root(activity).findViewWithTag<View>("bean-date-picker-$key").performClick()) }
                        val picker=BeanEntryActivity::class.java.getDeclaredField("dateDialog").apply { isAccessible=true }.get(activity) as android.app.DatePickerDialog
                        onMain { picker.datePicker.updateDate(2026,9,11); picker.getButton(AlertDialog.BUTTON_NEGATIVE).performClick() }
                        onMain { check(fields().getValue(key).text.toString()==original) }
                        onMain { check(root(activity).findViewWithTag<View>("bean-date-picker-$key").performClick()) }
                        val accepted=BeanEntryActivity::class.java.getDeclaredField("dateDialog").apply { isAccessible=true }.get(activity) as android.app.DatePickerDialog
                        onMain { accepted.datePicker.updateDate(2026,9,11); accepted.getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
                        onMain { check(fields().getValue(key).text.toString()=="2026-10-11") }
                        input(key,original); record("calendar-$key-cancel-confirm")
                    }
                    input("name",""); error("bean-empty-name",R.string.beans_save,R.string.beans_name_required); input("name","哥伦比亚 水洗")
                    input("quantity","0"); error("bean-zero",R.string.beans_save,R.string.beans_positive_quantity)
                    input("quantity","18.0001"); error("bean-invalid-quantity",R.string.beans_save,R.string.beans_bad_quantity); input("quantity","250")
                    input("opened","2026-02-30"); error("bean-invalid-date",R.string.beans_save,R.string.beans_bad_date); input("opened","2026-10-10")
                    rejectWrites(true)
                    try { error("bean-write-failed",R.string.beans_save,R.string.beans_save_failed) } finally { rejectWrites(false) }
                    // Cancellation after real draft entry writes nothing, then open a new real form and refill.
                    click(activity,R.string.beans_cancel); awaitMain { activity.isDestroyed }
                    check(inventory.events()==inventoryBefore && inventory.batches()==batchesBefore); record("bean-cancel-zero-events")
                    activity=start(BeanEntryActivity::class.java)
                    onMain { mapOf("name" to "哥伦比亚 水洗","quantity" to "250","purchased" to "2026-10-06","roasted" to "2026-10-04","opened" to "2026-10-10").forEach { (key,value) -> fields().getValue(key).setText(value) } }
                    roundTrip("bean-pre-save")
                    onMain {
                        val save=views(root(activity)).filterIsInstance<Button>().single { it.text.toString()==activity.getString(R.string.beans_save) }
                        check(save.performClick()); save.performClick() // actual saved guard must prevent duplicate addBatch
                    }
                    awaitMain { activity.isDestroyed }
                    val added=inventory.events().filter { it.eventId !in inventoryBefore.map { e -> e.eventId } }
                    check(added.size==1 && added.single().kind==InventoryEventKind.ADD && added.single().deltaMg==250000L)
                    val batch=inventory.batches().single { it.batch.id==added.single().batchId }.batch
                    check(batch.bean.name=="哥伦比亚 水洗" && batch.initialMg==250000L && batch.purchasedOn==LocalDate.of(2026,10,6) && batch.roastedOn==LocalDate.of(2026,10,4) && batch.openedOn==LocalDate.of(2026,10,10))
                    check(journal.entries()==journalBefore && custom.list()==curvesBefore); record("bean-save-exactly-one-event-duplicate-click-guard")
                }
                "review" -> {
                    val id=activity.intent.getStringExtra(BrewReviewActivity.SHOT_ID)!!
                    val before=requireNotNull(journal.find(id))
                    input("dose","0"); error("review-invalid-dose",R.string.journal_save,R.string.journal_bad_dose); input("dose","18")
                    rejectWrites(true)
                    try { input("taste",before.notes.taste+" 再次核对。"); error("review-write-failed",R.string.journal_save,R.string.journal_save_failed) } finally { rejectWrites(false) }
                    roundTrip("review-filled-draft")
                    click(activity,R.string.beans_cancel); awaitMain { activity.isDestroyed }
                    check(journal.entries()==journalBefore); record("review-cancel-unchanged-notes")
                    activity=start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID,id) }
                    input("taste",before.notes.taste+" 再次核对。")
                    click(activity,R.string.journal_save); awaitMain { activity.isDestroyed }
                    check(journal.find(id)==before.copy(notes=before.notes.copy(taste=before.notes.taste+" 再次核对。")))
                    unchangedExceptNotesOrDraft(); check(custom.list()==curvesBefore); record("review-save-notes-only")
                    // Missing telemetry and UNKNOWN are legitimate observations, never fabricated ENDED output.
                    val unknown=before.observation.copy(id="m2-review-unknown",endedAtMs=null,elapsedMs=null,status="UNKNOWN",weightHundredthsGram=null)
                    journal.observe(unknown)
                    activity=start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID,unknown.id) }
                    capture(activity,"review-unknown-missing-no-bean")
                    onMain {
                        check(views(root(activity)).filterIsInstance<TextView>().any { it.isShown && it.text.toString()==activity.getString(R.string.history_unknown_warning) })
                        check(views(root(activity)).filterIsInstance<Spinner>().single { it.contentDescription==activity.getString(R.string.journal_bean) }.selectedItemPosition==0)
                    }
                    click(activity,R.string.beans_cancel); awaitMain { activity.isDestroyed }
                    check(journal.find(unknown.id)?.observation==unknown); record("review-unknown-null-telemetry-no-bean")
                }
                "curve-editor" -> {
                    val id=activity.intent.getStringExtra(CurveEditorActivity.CURVE_ID)!!
                    onMain {
                        val selector=views(root(activity)).filterIsInstance<CurveStageCountTabs>().single()
                        listOf(1,4,1,4).forEach { count -> check(selector.findViewWithTag<Button>("curve-stage-count-$count").performClick()) }
                        check(selector.selectedCount==4)
                    }
                    roundTrip("editor-stage-hidden-values")
                    input("name","经典浓缩 四阶段 ".repeat(8)); capture(activity,"editor-long-name")
                    input("name","经典浓缩")
                    input("name",""); error("editor-empty-name",R.string.profiles_save,R.string.profiles_invalid); input("name","经典浓缩")
                    input("temperature","1000"); error("editor-out-of-range",R.string.profiles_save,R.string.profiles_invalid); input("temperature","93")
                    input("target-3","")
                    capture(activity,"editor-invalid-preview")
                    onMain { check(views(root(activity)).filterIsInstance<CurveStageView>().single().targets.isEmpty()) }
                    error("editor-invalid-number",R.string.profiles_save,R.string.profiles_invalid); input("target-3","5")
                    rejectWrites(true)
                    try { input("name","经典浓缩 调整"); error("editor-write-failed",R.string.profiles_save,R.string.profiles_save_failed) } finally { rejectWrites(false) }
                    roundTrip("editor-full-draft")
                    click(activity,R.string.beans_cancel); awaitMain { activity.isDestroyed }
                    check(custom.list()==curvesBefore); record("editor-cancel-no-write")
                    activity=start(CurveEditorActivity::class.java) { it.putExtra(CurveEditorActivity.CURVE_ID,id) }
                    input("name","经典浓缩 调整")
                    click(activity,R.string.profiles_save); awaitMain { activity.isDestroyed }
                    check(custom.list().size==curvesBefore.size && custom.find(id)==curvesBefore.single { it.id==id }.copy(name="经典浓缩 调整"))
                    unchangedExceptNotesOrDraft(); check(journal.entries()==journalBefore); record("editor-save-real-document-no-start")
                }
            }
            // Actual unavailable-store state remains within the same installed scoped closure.
            val postBusinessEvents=inventory.events().toList()
            val postBusinessJournal=journal.entries().toList()
            val postBusinessCurves=custom.list().toList()
            if(!activity.isDestroyed) close(activity)
            if(page=="bean-entry") {
                unavailable(true)
                activity=start(BeanEntryActivity::class.java)
                capture(activity,"bean-store-unavailable")
                onMain { check(views(root(activity)).filterIsInstance<TextView>().any { it.isShown && it.text.toString()==activity.getString(R.string.beans_unavailable) }) }
            } else {
                activity=if(page=="review") start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID,journalBefore.first().observation.id) }
                    else start(CurveEditorActivity::class.java) { it.putExtra(CurveEditorActivity.CURVE_ID,curvesBefore.first().id) }
                unavailable(true)
                val saveId=if(page=="review") R.string.journal_save else R.string.profiles_save
                click(activity,saveId)
                capture(activity,"$page-store-unavailable-disabled")
                onMain {
                    val save=views(root(activity)).filterIsInstance<Button>().single { it.text.toString()==activity.getString(saveId) }
                    check(!save.isEnabled && !activity.isFinishing)
                    check(views(root(activity)).filterIsInstance<TextView>().any { it.isShown && it.text.toString()==activity.getString(if(page=="review") R.string.journal_unavailable else R.string.profiles_unavailable) })
                }
            }
            check(inventory.events()==postBusinessEvents && journal.entries()==postBusinessJournal && custom.list()==postBusinessCurves)
            close(activity); unavailable(false); record("scoped-store-unavailable-no-write")
            File(directory,"form-business.json").writeText(org.json.JSONObject().put("page",page).put("completed",true)
                .put("paths",records).put("noMachineOrServiceCommands",true).put("source","real_Activity_callbacks_to_scoped_legal_Storage").toString(2))
        } finally { rejectWrites(false); unavailable(false) }
    }

    fun run(): Result {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val inventory = app.beanInventoryResult.getOrThrow()
        val preparation = app.beanPreparationResult.getOrThrow()
        val journal = app.journalResult.getOrThrow()
        val custom = app.customCurvesResult.getOrThrow()
        // Warm bounded-history reconciliation without replacing any unresolved preparation.
        app.history
        check(preparation.current() == null) { "Do not replace an existing or unknown preparation; isolated Mock store required" }
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        val oldAppearance = appearance.all.toMap()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        var oldClip: ClipData? = null
        var clipboardCaptured = false
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history", "brew_feedback", "scale_tool")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val initialBatches = inventory.batches()
        val initialEvents = inventory.events()
        val initialJournal = journal.entries()
        val initialCurves = custom.list()
        val token = UUID.randomUUID().toString()
        val beanName = "UI beans $token"
        val shotId = "ui-flow-shot-$token"
        val taste = "UI taste $token"
        val grind = "UI fine $token"
        val expectation = "UI expectation $token"
        val nextAdjustment = "UI coarser $token"
        val purchased = LocalDate.of(2026, 10, 2)
        val roasted = LocalDate.of(2026, 9, 28)
        val openedDate = LocalDate.of(2026, 10, 3)
        val observation = BrewJournal.Observation(shotId, "capture-1", 1700000000000L, 1700000030000L, 30000L, "ENDED", null, 3600)
        val paths = mutableListOf<String>()
        var batchId: String? = null
        val ownCurveIds = mutableSetOf<String>()
        var home: Activity? = null
        var service: MobileService? = null
        var message: SnapshotMessage? = null
        var failure: Throwable? = null
        fun record(name: String) {
            paths += name
            test.sendStatus(0, android.os.Bundle().apply { putString("stream", "ADVANCED_BUSINESS_UI_PATH $name\n") })
        }
        fun balance() = inventory.batches().single { it.batch.id == batchId }.balanceMg
        fun ownEvents() = inventory.events().filter { it.batchId == batchId }
        fun assertLockedBatchContext(page: Activity, expectedBalance: String) = onMain {
            val row = root(page).findViewWithTag<View>("dose-batch-summary-$batchId")
            check(row != null && row.isSelected && !row.isClickable && !row.isLongClickable && row.height >= HoyiUi.dp(page,72)) {
                "Saved preparation lost its read-only selected batch context"
            }
            val batchLabel=page.getString(R.string.beans_batch_id, (page as BeanPreparationActivity).beanBatchNumber(inventory, requireNotNull(batchId)))
            check(row.contentDescription.toString() == page.getString(R.string.dose_batch_choice, beanName, expectedBalance,batchLabel)) {
                "Saved preparation batch context showed stale inventory"
            }
            check(row.findViewWithTag<TextView>("product/preparation/choice/$batchId/name").text.toString()==beanName &&
                row.findViewWithTag<TextView>("product/preparation/choice/$batchId/batch").text.toString()==batchLabel &&
                row.findViewWithTag<TextView>("product/preparation/choice/$batchId/balance/number").text.toString()==expectedBalance &&
                row.findViewWithTag<TextView>("product/preparation/choice/$batchId/balance/unit").text.toString()=="g") {
                "Saved preparation actual name/batch/number/unit differs from the authoritative context"
            }
        }
        fun captureHomeDose(returnTo: Activity, name: String, state: Int) {
            val beforeDose = preparation.current()
            val beforeEvents = inventory.events()
            val beforeBalance = balance()
            val page = requireNotNull(rootHost)
            onMain { returnTo.startActivity(Intent(returnTo, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
            awaitMain { page.hasWindowFocus() && !page.isDestroyed }
            MainPageTestHost(test, page).select(TaskDestination.BREW)
            var details: android.app.AlertDialog? = null
            try {
                onMain {
                    val homeFragment = MainPageTestHost(test, page).active("root:BREW") as HomeFragment
                    check(root(page).findViewWithTag<View>("product/home/connect").performClick())
                    details = HomeFragment::class.java.getDeclaredField("deviceDialog")
                        .apply { isAccessible = true }.get(homeFragment) as? android.app.AlertDialog
                    check(details?.isShowing == true) { "Home details did not open from the visible connect action" }
                }
                val detailRoot = requireNotNull(details?.window).decorView
                awaitMain { details?.isShowing == true && owner(page) === service && views(detailRoot).filterIsInstance<TextView>().any {
                    it.isShown && it.text.toString() == page.getString(state)
                } }
                onMain {
                    val quantity = detailRoot.findViewWithTag<TextView>("home-dose-quantity")
                    val unit = detailRoot.findViewWithTag<TextView>("product/home/dose/unit")
                    check(quantity.isShown && quantity.text.toString() == "18" && unit.isShown && unit.text.toString() == "g") {
                        "Home did not display the selected 18 g dose"
                    }
                    val numberOrigin = IntArray(2); quantity.getLocationOnScreen(numberOrigin)
                    val unitOrigin = IntArray(2); unit.getLocationOnScreen(unitOrigin)
                    check(quantity.baseline >= 0 && unit.baseline >= 0 &&
                        numberOrigin[1] + quantity.baseline == unitOrigin[1] + unit.baseline) {
                        "Home dose number and unit do not share their actual TextView baseline"
                    }
                    check(views(detailRoot).filterIsInstance<TextView>().any {
                        it.isShown && it.text.toString().contains(beanName)
                    }) { "Home dose lost its bean identity" }
                }
                captureState(page, name, emptyList(), detailRoot)
                check(preparation.current() == beforeDose && inventory.events() == beforeEvents && balance() == beforeBalance) {
                    "Viewing the home dose changed preparation or inventory"
                }
            } finally {
                onMain { details?.dismiss(); page.startActivity(Intent(page, returnTo.javaClass)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
                awaitMain { !returnTo.isDestroyed && returnTo.hasWindowFocus() }
            }
        }
        fun invariants() = onMain {
            check(service!!.running && service!!.snapshot.message === message) { "Local UI changed machine service/event" }
            preserved.forEach { (name, values) -> check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) { "Local UI changed $name" } }
            check(inventory.batches().filter { it.batch.id != batchId } == initialBatches)
            check(inventory.events().filter { it.batchId != batchId } == initialEvents)
            check(journal.entries().filter { it.observation.id != shotId } == initialJournal)
            check(custom.list().filter { it.id !in ownCurveIds } == initialCurves)
            opened.filterIsInstance<BeanPreparationActivity>().filter { !it.isDestroyed && it.hasWindowFocus() }.forEach {
                val bound = ScaleOwnerActivity::class.java.getDeclaredField("owner").apply { isAccessible = true }.get(it)
                check(bound === service) { "Preparation bound another service" }
            }
        }
        try {
            onMain {
                check(app.languagePreferences.select(AppLanguage.CHINESE) != AppLanguagePreference.Selection.SAVE_FAILED)
                check(appearance.edit().putBoolean("dark", false).commit())
            }
            home = start(HomeActivity::class.java)
            awaitMain { owner(requireNotNull(home))?.running == true }
            onMain {
                service = owner(requireNotNull(home)); message = service!!.snapshot.message
                oldClip = clipboard.primaryClip; clipboardCaptured = true
                journal.observe(observation)
            }
            var entry = start(BeanEntryActivity::class.java)
            fill(entry, R.string.beans_name, beanName)
            for (invalid in listOf("0", "-1", "18.0001", "9223372036854776")) {
                fill(entry, R.string.beans_initial_input, invalid)
                click(entry, R.string.beans_save)
                check(!entry.isFinishing && inventory.batches() == initialBatches && inventory.events() == initialEvents)
            }
            record("entry-invalid-four")
            fill(entry, R.string.beans_initial_input, "100")
            fill(entry, R.string.beans_purchased, purchased.toString())
            fun purchasePicker(): android.app.DatePickerDialog {
                var picker: android.app.DatePickerDialog? = null
                onMain {
                    check(root(entry).findViewWithTag<View>("bean-date-picker-purchased").performClick())
                    picker = BeanEntryActivity::class.java.getDeclaredField("dateDialog").apply { isAccessible = true }
                        .get(entry) as android.app.DatePickerDialog
                    check(requireNotNull(picker).isShowing)
                }
                test.waitForIdleSync()
                return requireNotNull(picker)
            }
            val cancelledDate = purchasePicker()
            onMain {
                check(cancelledDate.datePicker.year == purchased.year && cancelledDate.datePicker.month == purchased.monthValue - 1 &&
                    cancelledDate.datePicker.dayOfMonth == purchased.dayOfMonth)
                cancelledDate.datePicker.updateDate(2026, 9, 3)
                cancelledDate.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            assertInput(entry, R.string.beans_purchased, purchased.toString())
            val confirmedDate = purchasePicker()
            onMain {
                confirmedDate.datePicker.updateDate(2026, 9, 3)
                confirmedDate.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            assertInput(entry, R.string.beans_purchased, "2026-10-03")
            fill(entry, R.string.beans_purchased, purchased.toString())
            fill(entry, R.string.beans_roasted, roasted.toString())
            fill(entry, R.string.beans_opened, openedDate.toString())
            entry = recreate(entry)
            assertInput(entry, R.string.beans_name, beanName)
            assertInput(entry, R.string.beans_initial_input, "100")
            assertInput(entry, R.string.beans_purchased, purchased.toString())
            assertInput(entry, R.string.beans_roasted, roasted.toString())
            assertInput(entry, R.string.beans_opened, openedDate.toString())
            click(entry, R.string.beans_save)
            awaitMain { entry.isDestroyed }
            opened.remove(entry)
            batchId = inventory.batches().single { it.batch.bean.name == beanName }.batch.id
            check(balance() == 100000L && ownEvents().single().kind == InventoryEventKind.ADD)
            val savedBatch = inventory.batches().single { it.batch.id == batchId }.batch
            check(savedBatch.initialMg == 100000L && savedBatch.bean.name == beanName &&
                savedBatch.purchasedOn == purchased && savedBatch.roastedOn == roasted && savedBatch.openedOn == openedDate) { "Saved bean batch lost a date/field" }
            check(persistedInventory().batches().single { it.batch.id == batchId }.batch == savedBatch) { "Bean batch differs after disk reload" }
            record("entry-save")
            var modeEntry = start(BeanEntryActivity::class.java)
            fill(modeEntry, R.string.beans_name, "Unsaved new bean")
            fun selectIdentityMode(existing: Boolean) {
                onMain {
                    check(root(modeEntry).findViewWithTag<View>(if (existing) "bean-mode-existing" else "bean-mode-new").performClick())
                }
                test.waitForIdleSync()
            }
            fun assertExistingIdentity() {
                onMain {
                    val choice = root(modeEntry).findViewWithTag<android.widget.Spinner>("bean-existing-choice")
                    check(choice.isShown && choice.selectedItem.toString() == beanName)
                    val selected = BeanEntryActivity::class.java.getDeclaredField("selectedBeanId").apply { isAccessible = true }
                    check(selected.get(modeEntry) == savedBatch.bean.id)
                    val nameLabel = views(root(modeEntry)).filterIsInstance<TextView>().single {
                        it.text.toString() == modeEntry.getString(R.string.beans_name) && it.labelFor != View.NO_ID
                    }
                    val nameField = root(modeEntry).findViewById<EditText>(nameLabel.labelFor)
                    check(!nameLabel.isShown && !nameField.isShown && !nameField.isEnabled && nameField.text.toString() == beanName)
                }
            }
            fill(modeEntry, R.string.beans_initial_input, "250")
            selectIdentityMode(true)
            onMain {
                val choice = root(modeEntry).findViewWithTag<android.widget.Spinner>("bean-existing-choice")
                val position = (0 until choice.count).single { choice.getItemAtPosition(it).toString() == beanName }
                choice.setSelection(position)
            }
            test.waitForIdleSync()
            modeEntry = recreate(modeEntry)
            assertExistingIdentity()
            captureState(modeEntry, "bean-entry-existing-mode", listOf(R.string.beans_save, R.string.beans_cancel))
            selectIdentityMode(false)
            assertInput(modeEntry, R.string.beans_name, "Unsaved new bean")
            captureState(modeEntry, "bean-entry-new-mode", listOf(R.string.beans_save, R.string.beans_cancel))
            modeEntry = recreate(modeEntry)
            selectIdentityMode(true)
            assertExistingIdentity()
            click(modeEntry, R.string.beans_cancel)
            awaitMain { modeEntry.isDestroyed }
            opened.remove(modeEntry)
            check(balance() == 100000L && ownEvents().size == 1)
            record("entry-mode-draft-recreate-cancel")
            val batch = start(BeanBatchActivity::class.java) { it.putExtra(BeanBatchActivity.BATCH_ID, batchId) }
            click(batch, R.string.beans_adjust)
            var form = dialog(batch)
            fill(form.window!!.decorView, batch.getString(R.string.beans_target), "90")
            fill(form.window!!.decorView, batch.getString(R.string.beans_reason), "UI recount")
            dialogClick(form, AlertDialog.BUTTON_NEGATIVE, batch.getString(R.string.beans_cancel))
            check(balance() == 100000L && ownEvents().size == 1)
            record("adjust-cancel")
            click(batch, R.string.beans_adjust); form = dialog(batch)
            fill(form.window!!.decorView, batch.getString(R.string.beans_target), "90")
            fill(form.window!!.decorView, batch.getString(R.string.beans_reason), "UI recount")
            dialogClick(form, AlertDialog.BUTTON_POSITIVE, batch.getString(R.string.beans_preview))
            val preview = dialog(batch)
            onMain { check(views(preview.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("100") && it.text.toString().contains("90") }) }
            dialogClick(preview, AlertDialog.BUTTON_POSITIVE, batch.getString(R.string.beans_confirm_adjust))
            check(balance() == 90000L && ownEvents().size == 2 && ownEvents().last().let { it.kind == InventoryEventKind.ADJUST && it.deltaMg == -10000L && it.reason == "UI recount" })
            captureState(batch, "batch-after-adjustment", listOf(R.string.beans_adjust, R.string.beans_ledger))
            record("adjust-confirm-preview"); close(batch)
            val inventoryPage = start(BeanInventoryActivity::class.java)
            onMain {
                val row = root(inventoryPage).findViewWithTag<View>("inventory-batch-$batchId")
                check(row.isShown && row.isEnabled)
                row.requestRectangleOnScreen(android.graphics.Rect(0, 0, row.width, row.height), true)
                check(row.performClick())
            }
            test.waitForIdleSync()
            onMain {
                val metadata = root(inventoryPage).findViewWithTag<android.view.ViewGroup>("inventory-batch-metadata")
                val labels = views(metadata).filterIsInstance<TextView>().map { it.text.toString() }
                check(listOf("2026-10-02", "2026-09-28", "2026-10-03", "UI recount", "-10 g", "+100 g", "90%").all { it in labels }) {
                    "Inventory selected detail omitted dates, ratio or real ledger events"
                }
            }
            captureState(inventoryPage, "inventory-selected-after-adjustment", listOf(R.string.dose_title))
            onMain {
                val metadata = root(inventoryPage).findViewWithTag<android.view.ViewGroup>("inventory-batch-metadata")
                val config = inventoryPage.resources.configuration
                val geometry = StringBuilder("screenDp=${config.screenWidthDp}x${config.screenHeightDp} fontScale=${config.fontScale}\n")
                val complete = listOf("-10 g", "+100 g").map { delta ->
                    val label = views(metadata).filterIsInstance<TextView>().single { it.text.toString() == delta }
                    val row = label.parent as View
                    val bounds = android.graphics.Rect()
                    val visible = row.getGlobalVisibleRect(bounds) && bounds.width() == row.width &&
                        bounds.height() == row.height && row.height > 0
                    geometry.append("delta=$delta complete=$visible bounds=$bounds size=${row.width}x${row.height}\n")
                    visible
                }
                File(requireNotNull(context.getExternalFilesDir(null)),
                    "advanced-page-screenshots/business/inventory-ledger-first-screen.txt").writeText(geometry.toString())
                if (config.screenWidthDp >= 1000 && config.screenHeightDp >= 600 && config.fontScale <= 1.1f) {
                    check(complete.all { it }) { "Tablet inventory recent ledger is clipped on first screen: $geometry" }
                }
            }
            onMain {
                val metadata = root(inventoryPage).findViewWithTag<android.view.ViewGroup>("inventory-batch-metadata")
                val last = views(metadata).filterIsInstance<TextView>().last()
                last.requestRectangleOnScreen(android.graphics.Rect(0, 0, last.width, last.height), true)
            }
            test.waitForIdleSync()
            captureState(inventoryPage, "inventory-ledger-after-adjustment", listOf(R.string.dose_title))
            check(balance() == 90000L && ownEvents().size == 2 && preparation.current() == null) {
                "Inspecting inventory details changed inventory or preparation"
            }
            val prepMonitor = test.addMonitor(BeanPreparationActivity::class.java.name, null, false)
            var prep: Activity
            try {
                click(inventoryPage, R.string.dose_title)
                prep = test.waitForMonitorWithTimeout(prepMonitor, 10000) ?: error("Inventory preparation entry missing")
                opened += prep
                test.waitForIdleSync()
                awaitMain { prep.hasWindowFocus() && root(prep).isLaidOut && !root(prep).isLayoutRequested }
            } finally { test.removeMonitor(prepMonitor) }
            check(prep.intent.getStringExtra(BeanPreparationActivity.EXTRA_BATCH_ID) == batchId)
            check(preparation.current() == null && balance() == 90000L && ownEvents().size == 2) { "Inventory navigation consumed or saved a dose" }
            awaitMain { ScaleOwnerActivity::class.java.getDeclaredField("owner").apply { isAccessible = true }.get(prep) === service }
            onMain {
                val choice = root(prep).findViewWithTag<View>("dose-batch-$batchId")
                check(choice.isShown && choice.isSelected && choice.isEnabled && choice.isClickable && choice.height >= HoyiUi.dp(prep,72)) { "Inventory entry lost preselected batch" }
                check(choice.contentDescription.toString()==prep.getString(R.string.dose_batch_choice,beanName,BeanQuantity.formatGrams(balance()),
                    prep.getString(R.string.beans_batch_id,(prep as BeanPreparationActivity).beanBatchNumber(inventory,requireNotNull(batchId)))))
                check(choice.performClick())
            }
            test.waitForIdleSync()
            fill(prep, R.string.dose_amount, "18.0001")
            click(prep, R.string.dose_save_selection)
            check(preparation.current() == null && balance() == 90000L && ownEvents().size == 2)
            record("dose-invalid-no-write")
            fun assertDoseError(visible: Boolean) = onMain {
                val shown = views(root(prep)).filterIsInstance<TextView>().any {
                    it.isShown && it.text.toString() == prep.getString(R.string.dose_bad_amount)
                }
                check(shown == visible) { "Dose validation feedback disagrees with corrected input" }
            }
            assertDoseError(true)
            fill(prep, R.string.dose_amount, "0")
            assertDoseError(true)
            fill(prep, R.string.dose_amount, "18")
            assertDoseError(false)
            onMain {
                val preview = root(prep).findViewWithTag<TextView>("dose-balance-preview")
                check(preview.text.toString() == prep.getString(R.string.dose_balance_preview, "90", "72"))
            }
            check(balance() == 90000L && ownEvents().size == 2)
            captureState(prep, "dose-selected-draft", listOf(R.string.dose_save_selection))
            click(prep, R.string.dose_save_selection)
            val selected = requireNotNull(preparation.current())
            check(selected.batchId == batchId && selected.amountMg == 18000L && !selected.confirmed && balance() == 90000L && ownEvents().size == 2)
            onMain {
                val preview = root(prep).findViewWithTag<TextView>("dose-balance-preview")
                check(preview.text.toString() == prep.getString(R.string.dose_balance_preview, "90", "72"))
                check(root(prep).findViewWithTag<TextView>("dose-balance-receipt") == null)
            }
            captureState(prep, "dose-pending-confirmation", listOf(R.string.dose_confirm))
            onMain { checkNativePrimaryStyles(prep) }
            assertLockedBatchContext(prep, "90")
            captureHomeDose(prep, "home-dose-selected", R.string.task_brew_dose_selected)
            record("dose-select-no-deduction")
            // Hold the real button before it is detached by rendering; repeated dispatch must remain idempotent.
            var confirm: TextView? = null
            onMain { confirm = views(root(prep)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString() == prep.getString(R.string.dose_confirm) } }
            click(prep, R.string.dose_confirm)
            onMain { confirm!!.performClick() }
            prep = recreate(prep)
            check(preparation.current()?.eventId == selected.eventId && preparation.current()?.confirmed == true)
            check(balance() == 72000L && ownEvents().count { it.eventId == selected.eventId && it.kind == InventoryEventKind.CONSUME } == 1 && ownEvents().size == 3)
            onMain {
                val receipt = root(prep).findViewWithTag<TextView>("dose-balance-receipt")
                check(receipt.text.toString() == prep.getString(R.string.dose_balance_receipt, "90", "72"))
            }
            captureState(prep, "dose-confirmed-receipt", listOf(R.string.dose_discard))
            assertLockedBatchContext(prep, "72")
            captureHomeDose(prep, "home-dose-confirmed", R.string.task_brew_dose_confirmed)
            record("dose-confirm-repeat-recreate")
            click(prep, R.string.dose_discard)
            dialogClick(dialog(prep), AlertDialog.BUTTON_POSITIVE, prep.getString(R.string.dose_discard_confirm))
            check(preparation.current() == null && balance() == 72000L && ownEvents().size == 3)
            record("dose-discard-no-refund"); close(prep)
            var review = start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID, shotId) }
            onMain {
                val selector = views(root(review)).filterIsInstance<Spinner>().single { it.contentDescription == review.getString(R.string.journal_bean) }
                selector.setSelection(inventory.batches().map { it.batch.bean }.distinctBy { it.id }.indexOfFirst { it.name == beanName } + 1)
            }
            test.waitForIdleSync()
            fill(review, R.string.journal_dose, "18")
            fill(review, R.string.journal_grind, grind)
            fill(review, R.string.journal_expectation, expectation)
            fill(review, R.string.journal_taste, taste)
            fill(review, R.string.journal_next, nextAdjustment)
            val expectedNotes = BrewJournal.Notes(savedBatch.bean.id, beanName, 18000,
                grind, expectation, taste, nextAdjustment)
            review = recreate(review)
            assertReviewFields(review, expectedNotes)
            click(review, R.string.journal_save); awaitMain { review.isDestroyed }; opened.remove(review)
            check(journal.find(shotId)?.observation == observation)
            check(journal.find(shotId)?.notes == expectedNotes) { "Journal save lost a notes field" }
            check(persistedJournal().find(shotId) == BrewJournal.Entry(observation, expectedNotes)) { "Journal differs after disk reload" }
            review = start(BrewReviewActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID, shotId) }
            captureState(review, "review-saved-notes", listOf(R.string.journal_save))
            assertReviewFields(review, expectedNotes)
            review = recreate(review)
            assertReviewFields(review, expectedNotes)
            check(journal.find(shotId)?.notes == expectedNotes)
            click(review, R.string.beans_cancel)
            awaitMain { review.isDestroyed }
            opened.remove(review)
            check(journal.find(shotId)?.notes == expectedNotes) { "Cancelling review changed saved notes" }
            record("journal-edit-reopen-recreate")
            // Own UUID fixture only: filled review plus synthetic telemetry, not hardware evidence.
            val detailSamples = (0..30).map { second ->
                ShotPoint(second * 1000L, if (second == 30) 0 else minOf(second * 18, 90),
                    20, second * 20, 9300, second * 120, brewing = second < 30)
            }
            check(app.samples.load(shotId).isEmpty()) { "Do not overwrite existing history samples" }
            app.samples.save(shotId, detailSamples)
            val detail = start(HistoryDetailActivity::class.java) { it.putExtra(BrewReviewActivity.SHOT_ID, shotId) }
            awaitMain {
                views(root(detail)).filterIsInstance<ShotChartView>().singleOrNull()?.points == detailSamples
            }
            val chartBounds = android.graphics.Rect()
            onMain {
                val labels = views(root(detail)).filterIsInstance<TextView>()
                listOf(beanName, grind, expectation, taste, nextAdjustment).forEach { value ->
                    check(labels.any { it.text.toString() == value }) { "History detail omitted saved review text" }
                }
                check(views(root(detail)).filterIsInstance<ShotChartView>().single().getGlobalVisibleRect(chartBounds))
            }
            captureState(detail, "history-detail-filled-top-fixture", listOf(R.string.journal_review))
            val expandedFields = listOf(R.string.journal_bean to beanName, R.string.journal_grind to grind,
                R.string.journal_expectation to expectation, R.string.journal_taste to taste,
                R.string.journal_next to nextAdjustment).map { (label, value) ->
                detail.getString(R.string.journal_field_value, detail.getString(label), value)
            }
            val expandedLast = expandedFields.last()
            onMain {
                val disclosure = requireNotNull(root(detail).findViewWithTag<Button>("product/history/details"))
                check(disclosure.isShown && disclosure.isEnabled && disclosure.isClickable && disclosure.performClick()) {
                    "History detail notes disclosure is not reachable"
                }
            }
            var detailsRoot: View? = null
            awaitMain {
                detailsRoot = android.view.inspector.WindowInspector.getGlobalWindowViews().singleOrNull {
                    it !== detail.window.decorView && it.hasWindowFocus() &&
                        views(it).filterIsInstance<TextView>().any { label -> label.text.toString() == expandedLast }
                }
                detailsRoot != null
            }
            val visibleDetails = requireNotNull(detailsRoot)
            onMain {
                val labels = views(visibleDetails).filterIsInstance<TextView>()
                expandedFields.forEach { value ->
                    check(labels.any { it.text.toString() == value }) { "Expanded history detail omitted saved review text" }
                }
                val last = labels.single { it.text.toString() == expandedLast }
                last.requestRectangleOnScreen(android.graphics.Rect(0, 0, last.width, last.height), true)
            }
            test.waitForIdleSync()
            onMain {
                val last = views(visibleDetails).filterIsInstance<TextView>().single { it.text.toString() == expandedLast }
                val visible = android.graphics.Rect()
                check(last.getGlobalVisibleRect(visible) && visible.height() == last.height) { "Last review field is not fully reachable" }
                val afterScroll = android.graphics.Rect()
                check(views(root(detail)).filterIsInstance<ShotChartView>().single().getGlobalVisibleRect(afterScroll) &&
                    afterScroll == chartBounds) { "Review scrolling moved or resized the history chart" }
            }
            captureState(detail, "history-detail-filled-bottom-fixture", listOf(android.R.string.ok), visibleDetails)
            onMain {
                val closeDetails = views(visibleDetails).filterIsInstance<Button>().single {
                    it.isShown && it.text.toString() == detail.getString(android.R.string.ok)
                }
                check(closeDetails.performClick()) { "History detail disclosure could not close" }
            }
            awaitMain { !visibleDetails.isAttachedToWindow }
            File(requireNotNull(context.getExternalFilesDir(null)), "advanced-page-screenshots/business/history-detail-filled-source.txt")
                .writeText("synthetic=true shotId=$shotId samples=31 endMs=30000 endWeightHundredths=3600 notesFields=7 noBle=true\n")
            close(detail)
            check(journal.find(shotId) == BrewJournal.Entry(observation, expectedNotes) &&
                app.samples.load(shotId) == detailSamples && preparation.current() == null && balance() == 72000L && ownEvents().size == 3) {
                "Viewing filled history changed its source, preparation or inventory"
            }
            var editor = start(CurveEditorActivity::class.java)
            val recipeName = "UI recipe $token"
            fill(editor, R.string.profiles_name, recipeName)
            fill(editor, R.string.profiles_temperature, "200")
            click(editor, R.string.profiles_save)
            check(!editor.isFinishing && custom.list() == initialCurves)
            record("curve-editor-invalid")
            val temperature = 94
            val cup = 4170
            val stages = listOf(CustomCurveDocument.Stage(24, 120), CustomCurveDocument.Stage(57, 230),
                CustomCurveDocument.Stage(83, 340), CustomCurveDocument.Stage(69, 450))
            fill(editor, R.string.profiles_temperature, temperature.toString())
            fill(editor, R.string.profiles_cup, CurveDraftNumber.format(cup, 2))
            onMain {
                val selector = views(root(editor)).filterIsInstance<CurveStageCountTabs>().single()
                check(selector.isShown && selector.isEnabled) { "Stage count selector is not visible" }
                check(selector.findViewWithTag<Button>("curve-stage-count-4").performClick())
            }
            test.waitForIdleSync()
            awaitMain {
                (0..3).all { index -> views(root(editor)).single { it.tag == "curve-editor-stage-$index" }.isShown }
            }
            stages.forEachIndexed { index, stage ->
                fill(editor, R.string.profiles_pressure, CurveDraftNumber.format(stage.target, 1), index)
                fill(editor, R.string.profiles_water, CurveDraftNumber.format(stage.waterTenthsMl, 1), index)
            }
            onMain {
                val selector = views(root(editor)).filterIsInstance<CurveStageCountTabs>().single()
                check(selector.findViewWithTag<Button>("curve-stage-count-2").performClick())
                check(!views(root(editor)).single { it.tag == "curve-editor-stage-3" }.isShown)
                check(selector.findViewWithTag<Button>("curve-stage-count-4").performClick())
            }
            test.waitForIdleSync()
            assertCurveFields(editor, recipeName, temperature, cup, stages)
            editor = recreate(editor)
            assertCurveFields(editor, recipeName, temperature, cup, stages)
            onMain { views(root(editor)).filterIsInstance<android.widget.ScrollView>().forEach { it.scrollTo(0, 0) } }
            captureState(editor, "curve-editor-four-stages", listOf(R.string.profiles_save))
            click(editor, R.string.profiles_save); awaitMain { editor.isDestroyed }; opened.remove(editor)
            val draft = custom.list().single { it.name == recipeName }; ownCurveIds += draft.id
            val expectedDraft = CustomCurveDocument(draft.id, recipeName, temperature, cup, CustomCurveDocument.ControlMode.PRESSURE, stages)
            check(draft == expectedDraft && persistedCurves().find(draft.id) == expectedDraft) { "Curve document lost a saved field/stage or differs on disk" }
            check(app.curves.canStart(requireNotNull(app.curves.find(draft.id))))
            var reopenedEditor = start(CurveEditorActivity::class.java) { it.putExtra(CurveEditorActivity.CURVE_ID, draft.id) }
            assertCurveFields(reopenedEditor, recipeName, temperature, cup, stages)
            reopenedEditor = recreate(reopenedEditor)
            assertCurveFields(reopenedEditor, recipeName, temperature, cup, stages)
            close(reopenedEditor)
            record("curve-editor-save-compatible-no-auto-start")
            val share = start(CurveShareActivity::class.java) { it.putExtra(CurveShareActivity.CURVE_ID, draft.id) }
            captureState(share, "curve-share-four-stages", listOf(R.string.profiles_copy, R.string.profiles_share_text))
            onMain {
                val table = root(share).findViewWithTag<android.widget.LinearLayout>("share-stage-table")
                val chart = views(root(share)).filterIsInstance<CurveStageView>().single()
                val config = share.resources.configuration
                File(requireNotNull(context.getExternalFilesDir(null)),
                    "advanced-page-screenshots/business/curve-share-four-stages-space.txt").writeText(
                    "screenDp=${config.screenWidthDp}x${config.screenHeightDp} fontScale=${config.fontScale} " +
                        "stages=${stages.size} tableShown=${table.isShown} tableMeasured=${table.measuredWidth}x${table.measuredHeight} " +
                        "chart=${chart.width}x${chart.height}\n")
                check(table.isShown && table.childCount == stages.size + 1) { "Four-stage sharing table missing" }
                (0 until table.childCount).forEach { index ->
                    val row = table.getChildAt(index)
                    val bounds = android.graphics.Rect()
                    check(row.getGlobalVisibleRect(bounds) && bounds.width() == row.width && bounds.height() == row.height && row.height > 0) {
                        "Sharing table row $index is clipped: $bounds / ${row.width}x${row.height}"
                    }
                }
                val fileAction = views(root(share)).filterIsInstance<android.widget.Button>().single {
                    it.text.toString() == share.getString(R.string.profiles_share_file)
                }
                check(!fileAction.isEnabled) { "Unexported file must not be shareable" }
            }
            click(share, R.string.profiles_copy)
            var copied = ""
            onMain { copied = clipboard.primaryClip!!.getItemAt(0).coerceToText(share).toString() }
            check(CustomCurveDocument.decode(copied) == draft && !copied.contains(beanName) && !copied.contains(taste) && !copied.contains(shotId))
            check(org.json.JSONObject(copied).keys().asSequence().toSet() == setOf("schema", "kind", "id", "name", "temperatureC", "targetHundredthsGram", "controlMode", "stages"))
            record("curve-copy-parameters-only"); close(share)
            val imported = draft.copy(id = CustomCurveDocument.newId(), name = "UI imported $token")
            fun importPreview(document: String): Pair<Activity, Activity> {
                val beforePreview = custom.list()
                val input = start(CurveImportActivity::class.java)
                onMain { clipboard.setPrimaryClip(ClipData.newPlainText("UI fixture", document)) }
                click(input, R.string.profiles_paste)
                onMain { check(views(root(input)).filterIsInstance<EditText>().single().text.toString() == document) }
                val monitor = test.addMonitor(CurveImportPreviewActivity::class.java.name, null, false)
                val result: Activity
                try {
                    click(input, R.string.profiles_import_preview)
                    result = test.waitForMonitorWithTimeout(monitor, 10000) ?: error("Import preview missing")
                    opened += result
                    test.waitForIdleSync(); awaitMain { result.hasWindowFocus() && root(result).isLaidOut }
                } finally { test.removeMonitor(monitor) }
                check(custom.list() == beforePreview) { "Preview changed a draft before explicit save" }
                return input to result
            }
            var importPages = importPreview(imported.encode())
            captureState(importPages.second, "import-new", listOf(R.string.profiles_import_save))
            click(importPages.second, R.string.profiles_import_save)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            ownCurveIds += imported.id
            check(custom.find(imported.id) == imported)
            record("curve-paste-preview-save"); close(importPages.first)
            val beforeRepeat = custom.list()
            importPages = importPreview(imported.encode())
            var repeatButton: TextView? = null
            onMain { repeatButton = views(root(importPages.second)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString() == importPages.second.getString(R.string.profiles_import_save) } }
            click(importPages.second, R.string.profiles_import_save)
            onMain { repeatButton!!.performClick() }
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            check(custom.list() == beforeRepeat)
            record("curve-import-repeat-idempotent"); close(importPages.first)
            val conflict = imported.copy(name = "UI conflict $token")
            importPages = importPreview(conflict.encode())
            captureState(importPages.second, "import-conflict", listOf(R.string.profiles_existing,
                R.string.profiles_keep_existing, R.string.profiles_save_copy))
            click(importPages.second, R.string.profiles_keep_existing)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            check(custom.list() == beforeRepeat)
            record("curve-conflict-keep-existing"); close(importPages.first)
            importPages = importPreview(conflict.encode())
            click(importPages.second, R.string.profiles_save_copy)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            val savedCopy = custom.list().single { it.name == conflict.name }
            ownCurveIds += savedCopy.id
            check(savedCopy.id != imported.id && custom.find(imported.id) == imported && savedCopy.copy(id = conflict.id) == conflict)
            record("curve-conflict-save-copy"); close(importPages.first)
            var invalidImport = start(CurveImportActivity::class.java)
            val beforeInvalid = custom.list()
            val rawImportDraft = "  {未完成的原文\n  保留空格}  "
            fill(invalidImport, R.string.profiles_json, rawImportDraft)
            onMain {
                val field = views(root(invalidImport)).filterIsInstance<EditText>().single()
                check(field.showSoftInputOnFocus && field.isFocusableInTouchMode) { "Import raw text must edit inline" }
                check(field.requestFocus())
                field.setSelection(3, 8)
            }
            invalidImport = recreate(invalidImport)
            onMain {
                val field = views(root(invalidImport)).filterIsInstance<EditText>().single()
                check(field.text.toString() == rawImportDraft && field.hasFocus() &&
                    field.selectionStart == 3 && field.selectionEnd == 8) { "Import recreation changed raw draft/focus/caret" }
                check(custom.list() == beforeInvalid) { "Import editing or recreation wrote a curve" }
                // Return to a non-IME geometry before the unchanged invalid-preview scenarios.
                check((root(invalidImport) as ViewGroup).getChildAt(0).requestFocus())
                (invalidImport.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                    .hideSoftInputFromWindow(field.windowToken, 0)
            }
            for (invalid in listOf("{bad", imported.encode().replace("\"schema\":1", "\"schema\":99"), imported.copy(temperatureC = 200).toJson().toString())) {
                check(runCatching { CustomCurveDocument.decode(invalid) }.isFailure) { "Negative import fixture is valid" }
                fill(invalidImport, R.string.profiles_json, invalid)
                click(invalidImport, R.string.profiles_import_preview)
                check(!invalidImport.isFinishing && custom.list() == beforeInvalid)
                onMain {
                    check(invalidImport.hasWindowFocus()) { "Invalid import navigated to another page" }
                    check(views(root(invalidImport)).filterIsInstance<TextView>().any { it.text.toString() == invalidImport.getString(R.string.profiles_import_invalid) })
                }
            }
            record("curve-import-invalid-three"); close(invalidImport)
            check(preparation.current() == null && ownEvents().size == 3 && ownCurveIds.size == 3 && paths.size == 18)
            ownCurveIds.forEach {
                val item=requireNotNull(app.curves.find(it))
                check(app.curves.canStart(item) == (CustomPressureCurveAdapter.profile(requireNotNull(item.customDocument),false)!=null))
            }
            invariants()
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) { try { action() } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) } }
            cleanup { opened.toList().asReversed().filter { it !== home }.forEach(::close) }
            cleanup { onMain { if (clipboardCaptured) { if (oldClip == null) clipboard.clearPrimaryClip() else clipboard.setPrimaryClip(requireNotNull(oldClip)) } } }
            cleanup { onMain { check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED); restoreMockPreferences(languagePrefs, oldLanguageStorage); restoreMockPreferences(appearance, oldAppearance) } }
            cleanup {
                home?.let { original -> home = recreate(original); awaitMain { owner(requireNotNull(home)) === service }; invariants() }
            }
        }
        failure?.let { throw it }
        test.sendStatus(0, android.os.Bundle().apply { putString("stream",
            "ADVANCED_BUSINESS_UI_FULL_FIELDS_PASSED beanDates=3 notesFields=7 curveFields=12 stages=4 durableStores=3 unsavedRecreation=true reopenedRecreation=true paths=18\n") })
        return Result(paths.toList(), ownEvents().size, ownCurveIds.size)
    }
}

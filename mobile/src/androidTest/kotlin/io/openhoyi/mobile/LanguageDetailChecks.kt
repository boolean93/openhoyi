package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import java.io.File
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Mock-only synthetic UI states; no positive confirmation, machine control or curve assignment. */
internal class LanguageDetailChecks(private val test: Instrumentation) {
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun captureFailure(activity: Activity, fixture: String, action: () -> Unit) {
        try { action() } catch (failure: Throwable) {
            try {
                val directory = File(activity.getExternalFilesDir(null), "language-detail-failures").apply { mkdirs() }
                File(directory, "failure.txt").writeText("$fixture\n${failure.stackTraceToString()}")
                val screenshot = requireNotNull(test.uiAutomation.takeScreenshot())
                try {
                    File(directory, "failure.png").outputStream().use {
                        check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { screenshot.recycle() }
            } catch (captureError: Throwable) { failure.addSuppressed(captureError) }
            throw failure
        }
    }
    private fun page(activity: MainActivity): androidx.fragment.app.Fragment =
        requireNotNull(activity.supportFragmentManager.primaryNavigationFragment).also {
            check(it.isVisible && !it.isHidden && it.view?.isAttachedToWindow == true)
        }
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun value(owner: MainActivity, name: String): Any? =
        if (name == "service") owner.currentService else field(page(owner), name).get(page(owner))
    private fun invoke(owner: MainActivity, name: String) = page(owner).let {
        it.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(it)
    }
    private fun successfulFooterGeometry(activity: MainActivity, fixture: String): String {
        check(activity.hasWindowFocus()) { "$fixture footer capture is not the foreground window" }
        val select = value(activity, "select") as Button
        val parameter = value(activity, "parameterShortcut") as Button
        val footer = select.parent as LinearLayout
        val window = Rect()
        activity.window.decorView.getWindowVisibleDisplayFrame(window)
        val actions = listOf(parameter, select).joinToString("\n") { action ->
            val visible = Rect()
            val position = IntArray(2)
            action.getLocationOnScreen(position)
            val full = Rect(position[0], position[1], position[0] + action.width, position[1] + action.height)
            check(action.isShown && action.getGlobalVisibleRect(visible) &&
                visible.width() == action.width && visible.height() == action.height &&
                action.top >= 0 && action.bottom <= footer.height) {
                "$fixture successful footer capture is clipped: full=$full visible=$visible"
            }
            "text=${action.text} enabled=${action.isEnabled} full=$full visible=$visible " +
                "size=${action.width}x${action.height} local=${action.left},${action.top}..${action.right},${action.bottom}"
        }
        // Doubled integer centers may differ by 1 for odd/even heights: <=0.5px
        // center quantization only, never a clipping allowance (full size is strict above).
        check(kotlin.math.abs((parameter.top + parameter.bottom) - (select.top + select.bottom)) <= 1) {
            "$fixture successful footer capture is not vertically centered: $actions"
        }
        return "$fixture actualWindow=true screenshotMethod=UiAutomation.takeScreenshot window=$window " +
            "row=${footer.width}x${footer.height} font=${activity.resources.configuration.fontScale} " +
            "density=${activity.resources.configuration.densityDpi} " +
            "screenDp=${activity.resources.configuration.screenWidthDp}x${activity.resources.configuration.screenHeightDp}\n$actions\n"
    }
    private fun captureSuccessfulFooterWindow(activity: MainActivity, fixture: String, kind: String) {
        val drawn = java.util.concurrent.CountDownLatch(1)
        onMain { activity.window.decorView.postOnAnimation {
            activity.window.decorView.postOnAnimation { drawn.countDown() }
        } }
        check(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "$fixture footer window did not draw" }
        // Animation callbacks precede RenderThread submission; let the compositor receive the frame.
        Thread.sleep(100)
        test.waitForIdleSync()
        val directory = File(activity.getExternalFilesDir(null), "language-detail-success").apply { mkdirs() }
        val name = "footer-${fixture.replace(' ', '_')}-$kind-window"
        val geometry = File(directory, "$name.txt")
        onMain { geometry.writeText("before-capture\n" + successfulFooterGeometry(activity, "$fixture detail=$kind")) }
        // Capture the real full display, not a drawn/remeasured collection bitmap.
        val screenshot = requireNotNull(test.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            geometry.appendText("screenshot=${screenshot.width}x${screenshot.height}\n")
        } finally { screenshot.recycle() }
        onMain { geometry.appendText("after-capture\n" + successfulFooterGeometry(activity, "$fixture detail=$kind")) }
    }
    private fun restoreAfter(action: () -> Unit, restore: () -> Unit) {
        var failure: Throwable? = null
        try { action() } catch (error: Throwable) { failure = error }
        finally {
            try { restore() } catch (error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
    private fun cleanup(vararg actions: () -> Unit) {
        var failure: Throwable? = null
        for (action in actions) try { action() } catch (error: Throwable) {
            if (failure == null) failure = error else failure!!.addSuppressed(error)
        }
        failure?.let { throw it }
    }
    fun curves(activity: MainActivity, fixture: String, checkLayout: (String) -> Unit) {
        check(BuildConfig.MOCK_MODE && activity.packageName == "io.openhoyi.mobile.mock")
        val library = (activity.application as MobileApplication).curves
        val display = CurveDetailsText(activity)
        val factory = library.items.filter { it.factoryCurve != null }
            .maxBy { display.render(it, library.canStart(it)).length }
        val captured = library.items.first { it.controlProfile != null }
        val noPressure = factory.takeIf { CurveStageParameters.pressureTargets(it).isEmpty() }
            ?: library.items.first { it.factoryCurve != null && CurveStageParameters.pressureTargets(it).isEmpty() }
        val fourStage = CurveFooterFixtures.fourStage(library)
        val unavailable = CurveLibraryItem("language-readonly-fixture", "Read-only UI fixture", "其它",
            activity.getString(R.string.curve_unavailable), null)
        val show = CurveFragment::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java).apply { isAccessible = true }
        for ((kind, item) in listOf("longFactory" to factory, "captured" to captured, "unavailable" to unavailable,
            "noPressure" to noPressure, "fourStage" to fourStage)) {
            onMain { show.invoke(page(activity), item) }
            test.waitForIdleSync()
            checkLayout("$fixture detail=$kind")
            captureFailure(activity, "$fixture detail=$kind") { onMain {
                val details = value(activity, "details") as TextView
                val availability = value(activity, "availability") as TextView
                val select = value(activity, "select") as Button
                val scroll = value(activity, "detailScroll") as ScrollView
                val allowed = library.canStart(item)
                check(details.text.toString() == display.render(item, allowed))
                check(availability.text.toString() == activity.getString(if (allowed) R.string.curve_verified else R.string.curve_unavailable))
                check(select.isEnabled == allowed && select.text.toString() == activity.getString(if (allowed) R.string.curve_use else R.string.curve_cannot_start))
                val chart = value(activity, "stageChart") as CurveStageView
                if (chart.isShown) {
                    check(chart.targets == CurveStageParameters.pressureTargets(item) && chart.height >= HoyiUi.dp(activity, 96)) {
                        "$fixture detail=$kind unusable or incorrect pressure schematic"
                    }
                    check(scroll.isShown && scroll.height > 0)
                } else {
                    val fallback = value(activity, "previewFallbackScroll") as ScrollView
                    val text = value(activity, "previewFallback") as TextView
                    check(fallback.isShown && fallback.height > 0 && text.text.isNotBlank()) {
                        "$fixture detail=$kind missing explicit preview fallback"
                    }
                    if (CurveStageParameters.pressureTargets(item).isEmpty()) {
                        check(text.text.contains(activity.getString(R.string.task_curve_library_no_pressure)))
                    } else {
                        CurveStageParameters.pressureTargets(item).forEachIndexed { index, target ->
                            check(text.text.contains(activity.getString(R.string.reference_stage, (index + 1).toString(),
                                String.format(java.util.Locale.ROOT, "%.1f", target / 10.0))))
                        }
                    }
                    LanguageUiLayoutChecks.textFits(text, "$fixture detail=$kind explicit fallback")
                }
                val visible = Rect()
                check(select.isShown && select.getGlobalVisibleRect(visible) && visible.width() == select.width && visible.height() == select.height) {
                    "$fixture detail=$kind action is clipped: shown=${select.isShown} visible=$visible " +
                        "size=${select.width}x${select.height} measured=${select.measuredWidth}x${select.measuredHeight} " +
                        "screenDp=${activity.resources.configuration.screenWidthDp}x${activity.resources.configuration.screenHeightDp} " +
                        "font=${activity.resources.configuration.fontScale} density=${activity.resources.configuration.densityDpi} " +
                        "parent=${(select.parent as View).width}x${(select.parent as View).height}"
                }
                LanguageUiLayoutChecks.textFits(select, "$fixture detail=$kind")
                val parameter = value(activity, "parameterShortcut") as Button
                val footer = select.parent as LinearLayout
                val geometry = listOf(parameter, select).joinToString("\n") { action ->
                    val bounds = Rect()
                    action.getGlobalVisibleRect(bounds)
                    "text=${action.text} visible=$bounds size=${action.width}x${action.height} " +
                        "local=${action.left},${action.top}..${action.right},${action.bottom} " +
                        "baseline=${action.baseline}"
                }
                File(activity.getExternalFilesDir(null), "language-detail-failures").apply { mkdirs() }
                    .resolve("footer-${fixture.replace(' ', '_')}-$kind.txt").writeText(
                        "$fixture detail=$kind row=${footer.width}x${footer.height} baselineAligned=${footer.isBaselineAligned} " +
                            "font=${activity.resources.configuration.fontScale} density=${activity.resources.configuration.densityDpi} " +
                            "screenDp=${activity.resources.configuration.screenWidthDp}x${activity.resources.configuration.screenHeightDp}\n$geometry\n")
                for (action in listOf(parameter, select)) {
                    val bounds = Rect()
                    check(action.isShown && action.getGlobalVisibleRect(bounds) &&
                        bounds.width() == action.width && bounds.height() == action.height &&
                        action.top >= 0 && action.bottom <= footer.height) {
                        "$fixture detail=$kind footer action is clipped: $geometry"
                    }
                    LanguageUiLayoutChecks.textFits(action, "$fixture detail=$kind footer")
                }
                // Doubled integer centers differ by <=1 only for odd/even heights:
                // <=0.5px center quantization, not a clipping tolerance. Full width/height remain exact.
                check(kotlin.math.abs((parameter.top + parameter.bottom) - (select.top + select.bottom)) <= 1) {
                    "$fixture detail=$kind footer bounds are not vertically centered: $geometry"
                }
            } }
            if (kind == "longFactory" || kind == "noPressure") {
                captureFailure(activity, "$fixture detail=$kind successfulWindow") {
                    captureSuccessfulFooterWindow(activity, fixture, kind)
                }
            }
        }
    }
    fun warnings(home: MainActivity, fixture: String, checkLayout: (String) -> Unit) {
        check(BuildConfig.MOCK_MODE && home.packageName == "io.openhoyi.mobile.mock")
        val service = value(home, "service") as MobileService
        onMain { check(field(service, "hub").get(service) == null && field(service, "mock").get(service) != null) }
        val resource = field(service, "manualSafetyResource")
        val original = resource.get(service)
        val state = service.shotState
        val message = service.snapshot.message
        restoreAfter({
            for (id in listOf(R.string.service_event_manual_disconnect_warning, R.string.machine_recovery_shot_restart, R.string.service_event_shot_clear_failed)) {
                onMain { resource.set(service, id); invoke(home, "render") }
                test.waitForIdleSync()
                checkLayout("$fixture warning=$id")
                onMain {
                    val warning = value(home, "safetyWarning") as TextView
                    check(warning.isShown && warning.text.toString() == home.getString(id)) {
                        "$fixture warning=$id expected=${home.getString(id)} actual=${warning.text} " +
                            "shown=${warning.isShown} visibility=${warning.visibility} " +
                            "attached=${warning.isAttachedToWindow} homeFocus=${home.hasWindowFocus()} " +
                            "homeDestroyed=${home.isDestroyed} sameService=${value(home, "service") === service} " +
                            "manualResource=${resource.get(service)} shot=${service.shotState} coffee=${service.snapshot.coffeeState} " +
                            "safetyResource=${ShotSafetyAlert.resource(service.shotState, service.snapshot.coffeeState)}"
                    }
                    LanguageUiLayoutChecks.textFits(warning, "$fixture warning=$id")
                    check(service.shotState == state && service.snapshot.message === message)
                }
            }
        }, { onMain { resource.set(service, original); invoke(home, "render") } })
    }
    fun cancelStart(activity: MainActivity, fixture: String) {
        check(BuildConfig.MOCK_MODE && activity.packageName == "io.openhoyi.mobile.mock")
        val service = value(activity, "service") as MobileService
        onMain { check(field(service, "hub").get(service) == null && field(service, "mock").get(service) != null) }
        val prefs = activity.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val profile = CurveCatalog.find("capture-2")!!
        val state = service.shotState
        val message = service.snapshot.message
        var dialogMayBeOpen = false
        val automation = test.uiAutomation
        val serviceInfo = requireNotNull(automation.serviceInfo)
        val originalFlags = serviceInfo.flags
        val title = activity.getString(R.string.extraction_mock_start_title)
        var expectedPositive = ""
        var expectedNegative = ""
        onMain {
            // The public native dialog layout applies its theme's real button transformation.
            // Accessibility exposes displayed (e.g. ALL CAPS) text, not the raw string resource.
            val probe = AlertDialog.Builder(activity)
                .setPositiveButton(R.string.extraction_mock_start, null)
                .setNegativeButton(R.string.machine_settings_cancel, null).create()
            probe.create() // Create detached content only: never show, attach or click this probe.
            fun displayed(button: Button): String = button.transformationMethod
                ?.getTransformation(button.text, button)?.toString() ?: button.text.toString()
            expectedPositive = displayed(probe.getButton(AlertDialog.BUTTON_POSITIVE))
            expectedNegative = displayed(probe.getButton(AlertDialog.BUTTON_NEGATIVE))
            check(expectedPositive.isNotBlank() && expectedNegative.isNotBlank())
            probe.dismiss()
        }
        restoreAfter({
            serviceInfo.flags = originalFlags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            automation.serviceInfo = serviceInfo
            onMain {
                check(prefs.edit().putString("selected", profile.id).commit())
                invoke(activity, "render")
                dialogMayBeOpen = true
                invoke(activity, "confirmStart")
            }
            val expectedMessage = activity.getString(R.string.extraction_start_message, profile.name,
                activity.getString(R.string.home_current_curve), profile.temperatureC.toString(), profile.maximumWaterMl.toString(),
                activity.getString(R.string.extraction_no_weight_target), activity.getString(R.string.extraction_mock_effect))
            val deadline = SystemClock.elapsedRealtime() + 10000
            while (true) {
                var cancelled = false
                val root = test.uiAutomation.rootInActiveWindow
                if (root != null) {
                    try {
                        if (root.packageName?.toString() == activity.packageName && hasText(root, title) && hasText(root, expectedMessage)) {
                            val positive = root.findAccessibilityNodeInfosByViewId("android:id/button1")
                            try {
                                val button = positive.singleOrNull() ?: error("$fixture missing positive action")
                                check(button.text?.toString() == expectedPositive && button.isVisibleToUser && button.isEnabled) {
                                    "$fixture incorrect positive action: actual=${button.text} expected=$expectedPositive"
                                }
                            } finally { positive.forEach { it.recycle() } }
                            val negative = root.findAccessibilityNodeInfosByViewId("android:id/button2")
                            try {
                                val button = negative.singleOrNull() ?: error("$fixture missing cancel action")
                                check(button.text.toString() == expectedNegative && button.isVisibleToUser && button.isEnabled) {
                                    "$fixture incorrect cancel action: actual=${button.text} expected=$expectedNegative"
                                }
                                val bounds = Rect(); button.getBoundsInScreen(bounds)
                                check(bounds.width() > 0 && bounds.height() > 0)
                                check(button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "$fixture cancel action rejected" }
                                cancelled = true
                            } finally { negative.forEach { it.recycle() } }
                        }
                    } finally { root.recycle() }
                }
                if (cancelled) break
                check(SystemClock.elapsedRealtime() < deadline) { "$fixture confirmation title/message not observed" }
                Thread.sleep(50)
            }
            test.waitForIdleSync()
            val focusDeadline = SystemClock.elapsedRealtime() + 10000
            while (true) {
                var focused = false
                onMain { focused = activity.hasWindowFocus() }
                if (focused) break
                check(SystemClock.elapsedRealtime() < focusDeadline) { "$fixture cancel did not return to page" }
                Thread.sleep(25)
            }
            dialogMayBeOpen = false
            onMain { check(service.shotState == state && service.snapshot.message === message) { "$fixture cancel started a cup" } }
        }, {
            // Back dismisses a remaining dialog; never invoke the positive action during cleanup.
            cleanup({
                if (dialogMayBeOpen) {
                    val root = automation.rootInActiveWindow
                    if (root != null) try {
                        if (root.packageName?.toString() == activity.packageName && hasText(root, title))
                            check(automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                    } finally { root.recycle() }
                }
            }, { onMain { restoreMockPreferences(prefs, original) } },
                { onMain { invoke(activity, "render") } },
                { serviceInfo.flags = originalFlags; automation.serviceInfo = serviceInfo })
        })
    }
    private fun hasText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.toString() == text) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try { if (hasText(child, text)) return true } finally { child.recycle() }
        }
        return false
    }
}

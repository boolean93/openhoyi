package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView

/** Mock-only synthetic UI states; no positive confirmation, machine control or curve assignment. */
internal class LanguageDetailChecks(private val test: Instrumentation) {
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun invoke(owner: Any, name: String) = owner.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(owner)
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
    fun curves(activity: CurveActivity, fixture: String, checkLayout: (String) -> Unit) {
        check(BuildConfig.MOCK_MODE && activity.packageName == "io.openhoyi.mobile.mock")
        val library = (activity.application as MobileApplication).curves
        val display = CurveDetailsText(activity)
        val factory = library.items.filter { it.factoryCurve != null }
            .maxBy { display.render(it, library.canStart(it)).length }
        val captured = library.items.first { it.controlProfile != null }
        val unavailable = CurveLibraryItem("language-readonly-fixture", "Read-only UI fixture", "其它",
            activity.getString(R.string.curve_unavailable), null)
        val show = CurveActivity::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java).apply { isAccessible = true }
        for ((kind, item) in listOf("longFactory" to factory, "captured" to captured, "unavailable" to unavailable)) {
            onMain { show.invoke(activity, item) }
            test.waitForIdleSync()
            checkLayout("$fixture detail=$kind")
            onMain {
                val details = field(activity, "details").get(activity) as TextView
                val availability = field(activity, "availability").get(activity) as TextView
                val select = field(activity, "select").get(activity) as Button
                val scroll = field(activity, "detailScroll").get(activity) as ScrollView
                val allowed = library.canStart(item)
                check(details.text.toString() == display.render(item, allowed))
                check(availability.text.toString() == activity.getString(if (allowed) R.string.curve_verified else R.string.curve_unavailable))
                check(select.isEnabled == allowed && select.text.toString() == activity.getString(if (allowed) R.string.curve_use else R.string.curve_cannot_start))
                check(scroll.height > 0) { "$fixture detail=$kind no scroll viewport" }
                val visible = Rect()
                check(select.isShown && select.getGlobalVisibleRect(visible) && visible.width() == select.width && visible.height() == select.height) {
                    "$fixture detail=$kind action is clipped"
                }
                LanguageUiLayoutChecks.textFits(select, "$fixture detail=$kind")
            }
        }
    }
    fun warnings(home: HomeActivity, fixture: String, checkLayout: (String) -> Unit) {
        check(BuildConfig.MOCK_MODE && home.packageName == "io.openhoyi.mobile.mock")
        val service = field(home, "service").get(home) as MobileService
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
                    val warning = field(home, "safetyWarning").get(home) as TextView
                    check(warning.isShown && warning.text.toString() == home.getString(id))
                    LanguageUiLayoutChecks.textFits(warning, "$fixture warning=$id")
                    check(service.shotState == state && service.snapshot.message === message)
                }
            }
        }, { onMain { resource.set(service, original); invoke(home, "render") } })
    }
    fun cancelStart(activity: ExtractionActivity, fixture: String) {
        check(BuildConfig.MOCK_MODE && activity.packageName == "io.openhoyi.mobile.mock")
        val service = field(activity, "service").get(activity) as MobileService
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
                            check(hasText(root, activity.getString(R.string.extraction_mock_start))) { "$fixture missing positive action" }
                            val negative = root.findAccessibilityNodeInfosByViewId("android:id/button2")
                            try {
                                val button = negative.singleOrNull() ?: error("$fixture missing cancel action")
                                check(button.text.toString() == activity.getString(R.string.machine_settings_cancel) && button.isVisibleToUser && button.isEnabled)
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

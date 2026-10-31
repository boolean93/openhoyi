package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/** Uses the actual native popup, never invokes hidden buttons or private production callbacks. */
internal object CurveMenuChecks {
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    fun clickIfNeeded(test: Instrumentation, root: View, text: String): Boolean {
        var handled = false
        var failure: Throwable? = null
        test.runOnMainSync {
            try {
                val all = views(root)
                if (all.filterIsInstance<TextView>().any { it.isShown && it.isClickable && it.text.toString() == text }) return@runOnMainSync
                val context = root.context
                val management = listOf(R.string.profiles_new, R.string.profiles_import, R.string.curve_legacy).map(context::getString)
                val detail = listOf(R.string.curve_detail_title, R.string.profiles_share, R.string.profiles_edit,
                    R.string.profiles_edit_copy, R.string.curve_assign_slot).map(context::getString)
                val tag = when (text) {
                    in management -> "curve-library-management"
                    in detail -> "curve-detail-actions"
                    else -> return@runOnMainSync
                }
                val menu = all.singleOrNull { it.tag == tag && it.isShown } ?: return@runOnMainSync
                check(menu.isEnabled && menu.isClickable && menu.height >= (48 * context.resources.displayMetrics.density).toInt())
                check(menu.performClick())
                handled = true
            } catch (error: Throwable) { failure = error }
        }
        failure?.let { throw it }
        if (!handled) return false
        test.waitForIdleSync()
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (true) {
            val nodes = test.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(text).orEmpty()
            var clicked = false
            try {
                for (node in nodes.filter { it.text?.toString() == text }) {
                    val parents = mutableListOf<AccessibilityNodeInfo>()
                    try {
                        var candidate: AccessibilityNodeInfo? = node
                        while (candidate != null && !candidate.isClickable) {
                            candidate = candidate.parent?.also(parents::add)
                        }
                        if (candidate != null) {
                            check(candidate.isEnabled && candidate.isVisibleToUser) { "Disabled native curve action: $text" }
                            if (candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { clicked = true; break }
                        }
                    } finally { parents.forEach { it.recycle() } }
                }
            } finally { nodes.forEach { it.recycle() } }
            if (clicked) { test.waitForIdleSync(); return true }
            check(SystemClock.elapsedRealtime() < deadline) { "Native curve menu action missing: $text" }
            SystemClock.sleep(50)
        }
    }
}

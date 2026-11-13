package io.openhoyi.mobile

import android.view.View

/** Real add/remove affordances, never a hidden stage-count selector. Call on the main thread. */
internal fun selectCurveStageCount(root: View, count: Int) {
    require(count in 1..4)
    fun visibleCount() = (0..3).count { root.findViewWithTag<View>("curve-editor-stage-$it")?.visibility == View.VISIBLE }
    while (visibleCount() < count) check(requireNotNull(root.findViewWithTag<View>("curve-stage-add")).performClick())
    while (visibleCount() > count) check(requireNotNull(root.findViewWithTag<View>("curve-stage-remove-${visibleCount() - 1}")).performClick())
}

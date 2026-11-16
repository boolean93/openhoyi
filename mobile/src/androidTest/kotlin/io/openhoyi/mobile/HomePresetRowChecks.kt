package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/** Detached production component regression; no preferences, service, library or real actions.
 * Call on the main thread. This does not establish system-window or touch acceptance.
 */
internal object HomePresetRowChecks {
    fun verify(base: Context) {
        for ((widthDp, heightDp, scale) in listOf(Triple(980, 680, 1f), Triple(684, 360, .9f),
            Triple(276, 240, 1.1f), Triple(980, 180, 1.1f), Triple(480, 120, 1.1f))) {
            val config = Configuration(base.resources.configuration).apply {
                fontScale = scale; orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = widthDp; screenHeightDp = heightDp
            }
            val context = base.createConfigurationContext(config)
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            var inspectedName: String? = null
            val grid = HomePresetGrid(context, TaskProductVisualStyle(context),
                false, false, false, false, true, onNameLongClick = { inspectedName = it }) {
                error("No real slot action allowed")
            }
            grid.visuals.forEach { it.name.setTextColor(android.graphics.Color.GREEN) }
            grid.updateLandscapeViewport((heightDp * density).toInt())
            grid.visuals.forEach {
                check(it.name.currentTextColor == android.graphics.Color.GREEN)
                check(it.name.gravity == android.view.Gravity.CENTER)
                check((it.button.parent as View).minimumHeight == (48 * density).toInt()) {
                    "Landscape card reserves blank height beyond the touch minimum"
                }
            }
            fun measure() {
                // First layout establishes width and can reparent cards into responsive rows.
                repeat(3) {
                    grid.requestLayout()
                    grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    grid.layout(0, 0, width, grid.measuredHeight)
                }
            }
            fun verifyRows(lever: Boolean) {
                val slots = HomeDirectPresetSlots.visible(lever)
                val tiles = slots.map { grid.visuals[it].button.parent as View }
                val inLayout = if (grid.orientation == LinearLayout.HORIZONTAL)
                    (0 until grid.childCount).map { grid.getChildAt(it) }
                else (0 until grid.childCount).flatMap { index ->
                    val row = grid.getChildAt(index) as ViewGroup
                    (0 until row.childCount).map { row.getChildAt(it) }
                }
                check(inLayout == tiles) { "Slot identity/order changed" }
                tiles.groupBy { it.parent }.values.forEach { row ->
                    check(row.map { it.height }.distinct().size == 1) { "Unequal row heights: $widthDp/$scale" }
                    val naturalHeight = row.maxOf { tile ->
                        tile as ViewGroup
                        maxOf(tile.minimumHeight, tile.getChildAt(1).measuredHeight)
                    }
                    check(row.first().height == naturalHeight) { "Row did not use its own natural maximum" }
                    row.forEach { tile ->
                        check(tile.width >= 48 * density && tile.height >= 48 * density)
                    }
                }
                slots.forEach { slot ->
                    val visual = grid.visuals[slot]
                    val name = visual.name
                    check(name.maxLines == 2 && name.lineCount <= 2)
                    check(name.layout.height <= name.height - name.compoundPaddingTop - name.compoundPaddingBottom)
                    var bottom = name.bottom
                    var parent = name.parent as View
                    while (parent !== visual.button.parent) {
                        bottom += parent.top
                        parent = parent.parent as View
                    }
                    check(bottom <= parent.height) { "Long name clipped in slot $slot" }
                    check(visual.button.height == parent.height) { "Touch background does not fill card" }
                }
            }
            for (lever in listOf(false, true, false)) {
                grid.showLever(lever)
                grid.visuals.forEachIndexed { slot, visual ->
                    visual.name.text = if (slot == 5) HomePresetName.resolve(null, null, "添加曲线") else "Espresso"
                    visual.sparkline.visibility = if (slot == 5) View.GONE else View.VISIBLE
                }
                measure()
                val shortHeight = grid.height
                grid.visuals[4].name.text = "HOYI Limit Extraction"
                measure()
                verifyRows(lever)
                check(grid.visuals[4].name.layout.getEllipsisCount(grid.visuals[4].name.lineCount - 1) == 0) {
                    "Ordinary two-line name was truncated"
                }
                val fullName = "HOYI Limit Extraction 长名称完整换行 ".repeat(4)
                grid.visuals[4].name.text = fullName
                measure()
                verifyRows(lever)
                check(grid.visuals[4].name.lineCount > 1)
                check(grid.height >= shortHeight) { "Row shrank for long text" }
                check(grid.visuals[4].name.layout.getEllipsisCount(1) > 0)
                check(grid.visuals[4].button.performLongClick())
                check(inspectedName == fullName) { "Full name unavailable without invoking slot action" }
                grid.visuals[4].name.text = "Espresso"
                measure()
                verifyRows(lever)
                check(grid.height == shortHeight) { "Stale row height after shorter name" }
            }
            // Component overflow only: no system display changes or full Home claim.
            val scroll = android.widget.ScrollView(context).apply { addView(grid) }
            repeat(3) {
                scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((heightDp * density).toInt(), View.MeasureSpec.EXACTLY))
                scroll.layout(0, 0, width, scroll.measuredHeight)
            }
            if (widthDp == 480) check(scroll.canScrollVertically(1)) { "Flat multi-row grid lost scroll fallback" }
        }
        val portrait = HomePresetGrid(base, TaskProductVisualStyle(base), true, false, false, false, false) {}
        val label = portrait.visuals[1].name
        val size = label.textSize
        portrait.updateLandscapeViewport(200)
        check(label.textSize == size && label.maxLines == Int.MAX_VALUE && label.ellipsize == null)
        check(!portrait.visuals[1].button.isLongClickable)
        check((portrait.visuals[1].button.parent as View).minimumHeight ==
            (80 * base.resources.displayMetrics.density).toInt())
        check((portrait.visuals[1].button.parent as View).layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}

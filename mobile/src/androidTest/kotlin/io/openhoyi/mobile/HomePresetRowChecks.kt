package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup

/** Detached production component checks; no preferences, service, library or real actions. */
internal object HomePresetRowChecks {
    fun verify(base: Context) {
        for ((widthDp, portrait, scale) in listOf(Triple(980, false, 1f), Triple(684, false, 1f),
            Triple(276, false, 1.1f), Triple(322, true, 1f), Triple(280, true, 1.1f))) {
            val config = Configuration(base.resources.configuration).apply {
                fontScale = scale
                orientation = if (portrait) Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = widthDp
                setLocale(java.util.Locale.SIMPLIFIED_CHINESE)
            }
            val context = base.createConfigurationContext(config)
            val density = context.resources.displayMetrics.density
            val width = (widthDp * density).toInt()
            val calls = mutableListOf<Int>()
            val grid = HomePresetGrid(context, TaskProductVisualStyle(context), portrait, short = false,
                onSlotClick = { calls += it }, onLastClick = { calls += HomeDirectPresetSlots.LAST })
            val visuals = grid.visuals + grid.last
            fun measure() {
                grid.requestLayout()
                grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                grid.layout(0, 0, width, grid.measuredHeight)
            }
            fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
                (0 until view.childCount).flatMap { all(view.getChildAt(it)) }
            } else emptyList()
            fun verifyRows(lever: Boolean, legacy: Boolean) {
                val slots = HomeDirectPresetSlots.visible(lever) + if (legacy) listOf(5) else emptyList()
                check(all(grid).filter { v -> visuals.any { it.button === v } } == slots.map { visuals[it].button })
                for (index in slots) {
                    val visual = visuals[index]
                    val tile = visual.button.parent as View
                    check(tile.width >= (48 * density).toInt() && tile.height >= (48 * density).toInt())
                    check(visual.button.width == tile.width && visual.button.height == tile.height)
                    val name = visual.name
                    check(name.layout.height <= name.height - name.compoundPaddingTop - name.compoundPaddingBottom)
                    check((0 until name.lineCount).all { name.layout.getEllipsisCount(it) == 0 })
                }
            }
            val long = "HOYI Limit Extraction"
            for ((lever, legacy) in listOf(false to false, true to false, true to true, false to false)) {
                grid.showSlots(lever, legacy)
                visuals.forEachIndexed { index, visual ->
                    visual.name.text = if (index == 3 || index == HomeDirectPresetSlots.LAST) long else "Espresso"
                    visual.sparkline.visibility = View.VISIBLE
                }
                measure()
                verifyRows(lever, legacy)
                if (widthDp in listOf(980, 322) && scale == 1f) {
                    check(visuals[3].name.lineCount == 1 && grid.last.name.lineCount == 1) {
                        "Longest factory curve should fit the approved widths"
                    }
                }
                val shortHeight = grid.height
                grid.visuals[4].name.text = "Morning espresso 完整的自定义配方 ".repeat(4)
                measure()
                verifyRows(lever, legacy)
                check(grid.visuals[4].name.lineCount > 1 && grid.height > shortHeight)
                grid.visuals[4].name.text = "Espresso"
                measure()
                check(grid.height == shortHeight) { "Stale row height after shorter text" }
                val visible = HomeDirectPresetSlots.visible(lever) + if (legacy) listOf(5) else emptyList()
                calls.clear()
                visible.forEach { check(visuals[it].button.performClick()) }
                check(calls == visible) { "Shortcut callbacks changed order or identity" }
                check((grid.findViewWithTag<View>("product/home/preset-group") != null) == (!portrait &&
                    HomeDirectPresetSlots.columns(widthDp, HomeDirectPresetSlots.visible(lever).size, portrait, scale) ==
                    HomeDirectPresetSlots.visible(lever).size))
            }
        }
    }
}

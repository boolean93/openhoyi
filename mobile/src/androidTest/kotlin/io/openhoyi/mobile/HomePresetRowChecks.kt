package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup

/** Detached production component checks; no preferences, service, library or real actions. */
internal object HomePresetRowChecks {
    /** Run in the hosted tablet screenshot lane on the same Android View implementation. */
    fun verifyWideReparent(base: Context) {
        val density = base.resources.displayMetrics.density
        val grid = HomePresetGrid(base, TaskProductVisualStyle(base), portrait = false, short = false,
            onSlotClick = { error("No shortcut action during reflow") },
            onLastClick = { error("No history action during reflow") })
        (grid.visuals + grid.last).forEach { it.name.text = "Espresso" }
        fun layout(widthDp: Int) {
            val width = (widthDp * density).toInt()
            grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.AT_MOST))
            grid.layout(0, 0, width, grid.measuredHeight)
        }
        val groupTag = "product/home/preset-group"
        layout(980)
        val group = checkNotNull(grid.findViewWithTag<ViewGroup>(groupTag))
        fun checkWide(lever: Boolean, legacy: Boolean = false) {
            val oldStrip = group.parent
            grid.showSlots(lever, legacy)
            layout(980)
            check(grid.findViewWithTag<ViewGroup>(groupTag) === group) { "Preset group identity changed" }
            val strip = checkNotNull(group.parent as? ViewGroup)
            check(strip !== oldStrip && strip.parent === grid) { "Preset group remained attached to a retired strip" }
            check((oldStrip as? View)?.parent == null) { "Retired strip still active" }
            check((grid.findViewWithTag<View>("product/home/direct-preset/0") != null) == lever)
            check((grid.findViewWithTag<View>("product/home/direct-preset/5") != null) == legacy)
            check(group.height == (66 * density).toInt())
        }
        checkWide(lever = true)
        checkWide(lever = false)
        val retiredStrip = checkNotNull(group.parent as? ViewGroup)
        layout(276)
        check(group.parent == null && retiredStrip.parent == null && grid.findViewWithTag<View>(groupTag) == null)
        checkWide(lever = false)
        checkWide(lever = true, legacy = true)
        checkWide(lever = false)
    }

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
            // Match a real ScrollView's bounded viewport remeasure rather than only measuring
            // the detached component with UNSPECIFIED height (which masks divider stretch).
            fun measure(heightMode: Int = View.MeasureSpec.AT_MOST) {
                grid.requestLayout()
                grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(if (heightMode == View.MeasureSpec.UNSPECIFIED) 0
                        else (720 * density).toInt(), heightMode))
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
                if (widthDp == 980 && !portrait && lever && !legacy) {
                    val expected = (66 * density).toInt()
                    check(grid.height == expected) { "Grouped shortcut strip must match 66dp, was ${grid.height / density}dp" }
                    check((grid.last.button.parent as View).height == expected)
                    measure(View.MeasureSpec.UNSPECIFIED)
                    check(grid.height == expected) { "Grouped shortcut height depends on parent measure mode" }
                    measure()
                }
                if (widthDp == 980 && !portrait && lever) {
                    grid.visuals[3].name.text = "Espresso"
                    grid.visuals[4].name.text = long
                    measure()
                    check(grid.visuals[4].name.lineCount == 1) { "Default longest name in D should borrow room from C" }
                    grid.visuals[3].name.text = long
                    grid.visuals[4].name.text = "Espresso"
                    measure()
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

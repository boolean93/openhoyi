package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Shared production layout. Callers own data and actions; this component never reads storage. */
internal class HomePresetGrid(
    context: Context,
    private val productStyle: TaskProductVisualStyle,
    private val phoneHome: Boolean,
    private val compactType: Boolean,
    private val shortHome: Boolean,
    private val shortWideHome: Boolean,
    private val spaciousHome: Boolean,
    private val onNameLongClick: (String) -> Unit = {},
    onSlotClick: (Int) -> Unit,
) : LinearLayout(context) {
    data class Visual(val button: Button, val name: TextView, val sparkline: CurveLibrarySparklineView)
    val visuals: List<Visual>
    private var arrange: (Boolean) -> Unit = {}
    private var landscapePresetBudget: HomeLandscapeBudget.Presets? = null
    fun showLever(show: Boolean) = arrange(show)
    fun updateLandscapeViewport(heightPx: Int) {
        if (phoneHome) return
        val budget = HomeLandscapeBudget.presets((heightPx / resources.displayMetrics.density).toInt())
        if (budget == landscapePresetBudget) return
        landscapePresetBudget = budget
        visuals.forEach { visual ->
            (visual.button.parent as View).minimumHeight = dp(budget.minimumHeight)
            val colors = visual.name.textColors
            val gravity = visual.name.gravity
            productStyle.apply(visual.name, TaskProductVisualStyle.Type(budget.textSp, 500, budget.textSp + 7))
            visual.name.setTextColor(colors)
            visual.name.gravity = gravity
            visual.sparkline.layoutParams = visual.sparkline.layoutParams.apply { height = dp(budget.previewHeight) }
        }
        requestLayout()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    init {
        tag = "product/home/direct-presets"
        orientation = if (phoneHome) VERTICAL else HORIZONTAL
        val presetRow = this
        visuals = (0..5).map { slot ->
            val tile = FrameLayout(context).apply { minimumHeight = dp(if (phoneHome) 80 else 48) }
            val button = productStyle.button(Button(context), outline = spaciousHome).apply {
                tag = "product/home/direct-preset/$slot"
                // Static theme chrome belongs to view creation, not the 250ms telemetry render.
                if (slot == 0) background = productStyle.shape(productStyle.surface, true)
                setOnClickListener { onSlotClick(slot) }
            }
            tile.addView(button, FrameLayout.LayoutParams(-1, -1))
            val contents = object : LinearLayout(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    if (phoneHome && compactType && childCount >= 2) {
                        val cardWidth = (View.MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density).toInt()
                        val compact = HomePortraitBudget.compactPreset(cardWidth, resources.configuration.fontScale)
                        getChildAt(childCount - 2).layoutParams.height = dp(if (compact) 28 else 40)
                        // The entire tile is the touch target; its label needs no separate 48dp target.
                        val label = getChildAt(childCount - 1)
                        val labelHeight = dp(if (compact) 32 else 48)
                        if (label.minimumHeight != labelHeight) label.minimumHeight = labelHeight
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }.apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.TOP
                setPadding(dp(if (shortHome) 6 else 10), dp(if (shortHome) 1 else 6), dp(if (shortHome) 6 else 10), dp(if (shortHome) 1 else 6))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            tile.addView(contents, FrameLayout.LayoutParams(-1, -2))
            val slotLabel = productStyle.text(if (slot == 0) "${context.getString(R.string.lever_title)} · ${context.getString(R.string.lever_recent)}" else context.getString(R.string.home_slot, slot.toString()),
                TaskProductVisualStyle.Type(if (slot == 0) 12 else 14, 400, if (slot == 0) 18 else 20),
                secondary = true).apply { gravity = android.view.Gravity.START }
            val previewHeader = if (shortWideHome) productStyle.row().apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                contents.addView(this, LinearLayout.LayoutParams(-1, -2))
            } else contents
            // Slot numbers belong to management; the Home tile is identified by its curve name.
            if (slot == 0) previewHeader.addView(slotLabel, LinearLayout.LayoutParams(if (shortWideHome) -2 else -1, -2))
            val sparkline = CurveLibrarySparklineView(context, productStyle).apply {
                visibility = View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tag = "product/home/direct-preset/$slot/preview"
            }
            previewHeader.addView(sparkline, if (shortWideHome)
                LinearLayout.LayoutParams(0, dp(24), 1f).apply { marginStart = dp(4) }
            else LinearLayout.LayoutParams(-1, dp(if (shortHome) 24 else 40)))
            val name = productStyle.text("", TaskProductVisualStyle.Type(if (compactType) 15 else if (shortHome) 14 else 17, 500, if (compactType) 22 else if (shortHome) 20 else 24)).apply {
                gravity = android.view.Gravity.CENTER
                tag = "product/home/direct-preset/$slot/name"
                maxLines = if (phoneHome) Int.MAX_VALUE else 2
                ellipsize = if (phoneHome) null else android.text.TextUtils.TruncateAt.END
                if(phoneHome) minimumHeight=dp(48)
            }
            if (slot == 0) name.setTextColor(productStyle.homePresetText)
            contents.addView(name, LinearLayout.LayoutParams(-1, -2))
            if (!phoneHome) button.setOnLongClickListener {
                onNameLongClick(name.text.toString())
                true // Consume the gesture; never dispatch the slot's normal click/action.
            }
            Visual(button, name, sparkline)
        }
        // Keep stable slot identities; only reparent when visibility changes, not on telemetry ticks.
        val presetTiles = visuals.map { it.button.parent as View }
        var arrangedWithLever: Boolean? = null
        var arrangedColumns = 0
        arrange = { showLever ->
            val slots = HomeDirectPresetSlots.visible(showLever)
            val columns = HomeDirectPresetSlots.columns(
                (presetRow.width / resources.displayMetrics.density).toInt(), slots.size,
                phoneHome, resources.configuration.fontScale)
            if (arrangedWithLever != showLever || arrangedColumns != columns) {
                presetTiles.forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
                presetRow.removeAllViews()
                presetRow.orientation = if (columns < slots.size) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                if (columns < slots.size) {
                    slots.chunked(columns).forEachIndexed { rowIndex, rowSlots ->
                        val row = productStyle.row()
                        presetRow.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                            if (rowIndex > 0) topMargin = dp(10)
                        })
                        rowSlots.forEachIndexed { column, slot ->
                            // A wrap-content horizontal row first finds its tallest natural child,
                            // then LinearLayout uniformly remeasures MATCH_PARENT children to it.
                            row.addView(presetTiles[slot], LinearLayout.LayoutParams(0, if (phoneHome) -2 else LayoutParams.MATCH_PARENT, 1f).apply {
                                if (column > 0) marginStart = dp(10)
                            })
                        }
                    }
                } else {
                    slots.forEachIndexed { index, slot ->
                        presetRow.addView(presetTiles[slot], LinearLayout.LayoutParams(0,
                            if (phoneHome) -2 else LayoutParams.MATCH_PARENT, 1f).apply {
                            if (index > 0) marginStart = dp(if (spaciousHome) 8 else 4)
                        })
                    }
                }
                arrangedWithLever = showLever
                arrangedColumns = columns
            }
        }
        presetRow.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) arrange(arrangedWithLever ?: false)
        }
        arrange(false)
    }
}

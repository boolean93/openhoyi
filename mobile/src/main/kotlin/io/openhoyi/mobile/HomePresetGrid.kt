package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

/** The shortcut strip owns presentation only; callers retain all recipe and device actions. */
internal class HomePresetGrid(
    context: Context,
    private val style: TaskProductVisualStyle,
    private val portrait: Boolean,
    private val short: Boolean,
    private val onSlotClick: (Int) -> Unit,
    private val onNameLongClick: ((String) -> Unit)? = null,
    onLastClick: () -> Unit,
) : LinearLayout(context) {
    data class Visual(val button: Button, val name: TextView, val sparkline: CurveLibrarySparklineView, val label: TextView)
    private val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val labelInk = if (dark) style.ink else 0xff101938.toInt()
    private val surface = wallpaperTint(style.surface, if (dark) 219 else 184)
    private val separator = wallpaperTint(style.ink, if (dark) 48 else 23)
    private val radius = if (portrait) 10 else 15
    private val airyHeight = if (portrait) 59 else 66
    private val minHeightDp = if (short) 48 else airyHeight
    private val inner = if (portrait) 12 else if (short) 6 else 10
    private fun dp(value: Int) = style.dp(value)
    private fun LayoutParams.withStart(gap: Int) = apply { marginStart = dp(gap) }

    private val slots = (0..5).map { slot ->
        val title = if (slot == 0) context.getString(R.string.lever_title)
            else context.getString(if (slot == 5) R.string.home_shortcut_legacy
                else R.string.home_shortcut_preset, ('A' + slot - 1).toString())
        makeTile("product/home/direct-preset/$slot", title, if (slot == 0) Glyph.LEVER else Glyph.BOOKMARK) {
            onSlotClick(slot)
        }
    }
    private val previous = makeTile("product/home/last-curve", context.getString(R.string.home_shortcut_previous),
        Glyph.CLOCK, "product/home/quick-launch", "product/home/quick-recipe", onLastClick)
    val visuals = slots.map(Tile::visual)
    val last = previous.visual
    private var hasLever = false
    private var hasLegacy = false
    private var arranged: List<Int> = emptyList()
    private var columns = 0
    private val group = row().apply {
        tag = "product/home/preset-group"
        background = style.shape(surface, radius = radius)
        clipToOutline = true
        elevation = dp(2).toFloat()
    }

    init {
        tag = "product/home/direct-presets"
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        keepOwnBackdropTreatment()
    }

    fun showSlots(lever: Boolean, legacy: Boolean) {
        if (hasLever == lever && hasLegacy == legacy) return
        hasLever = lever
        hasLegacy = legacy
        requestLayout()
    }

    private fun row() = style.row().apply {
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private fun tile(slot: Int) = if (slot == HomeDirectPresetSlots.LAST) previous else slots[slot]

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visible = HomeDirectPresetSlots.visible(hasLever)
        val availableDp = (MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density).toInt()
        val fit = HomeDirectPresetSlots.columns(availableDp, visible.size, portrait, resources.configuration.fontScale)
        val newLayout = visible + if (hasLegacy) listOf(5) else emptyList()
        if (arranged != newLayout || columns != fit) {
            arrange(visible, fit)
            arranged = newLayout
            columns = fit
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun arrange(visible: List<Int>, fit: Int) {
        (slots + previous).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        group.removeAllViews()
        removeAllViews()
        if (!portrait && fit == visible.size) {
            val strip = row()
            addView(strip, LayoutParams(-1, -2))
            val gap = if (short) 7 else 17
            if (hasLever) {
                slots[0].standalone(true)
                strip.addView(slots[0], LayoutParams(0, -1, 1.02f))
            }
            strip.addView(group, LayoutParams(0, -1, 3.95f).withStart(if (hasLever) gap else 0))
            (1..4).forEach { index ->
                if (index > 1) group.addView(View(context).apply { setBackgroundColor(separator) }, LayoutParams(dp(1), -1))
                slots[index].standalone(false)
                group.addView(slots[index], LayoutParams(0, -1, when (index) { 3 -> 1.15f; 4 -> 1.03f; else -> .91f }))
            }
            previous.standalone(true)
            strip.addView(previous, LayoutParams(0, -1, 1.19f).withStart(gap))
        } else {
            visible.chunked(fit).forEachIndexed { rowIndex, entries ->
                val strip = row()
                addView(strip, LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = dp(5) })
                entries.forEachIndexed { column, index ->
                    tile(index).standalone(true)
                    strip.addView(tile(index), LayoutParams(0, -1, 1f).withStart(if (column == 0) 0 else 7))
                }
                // Keep the history cell at the right edge when the optional lever is absent.
                if (entries.size < fit) repeat(fit - entries.size) {
                    strip.addView(Space(context), if (entries.last() == HomeDirectPresetSlots.LAST) 0 else strip.childCount,
                        LayoutParams(0, 1, 1f).withStart(7))
                }
            }
        }
        if (hasLegacy) {
            slots[5].standalone(true)
            addView(slots[5], LayoutParams(-1, -2).apply { topMargin = dp(5) })
        }
    }

    private inner class Tile(
        id: String, title: String, glyph: Glyph, actionId: String, nameId: String, click: () -> Unit,
    ) : FrameLayout(context) {
        private val mask = style.shape(Color.WHITE, radius = 0)
        private val ripple = ColorStateList.valueOf(wallpaperTint(style.ink, if (dark) 40 else 24))
        val visual: Visual
        init {
            if (glyph == Glyph.CLOCK) tag = id
            minimumHeight = dp(minHeightDp)
            minimumWidth = dp(48)
            clipToOutline = true
            val words = style.column().apply {
                gravity = Gravity.CENTER_VERTICAL
                val vertical = if (short) 3 else if (portrait) 7 else 9
                setPadding(dp(inner), dp(vertical), dp(inner), dp(vertical))
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            val label = style.text(title, TaskProductVisualStyle.Type(12, 500, 17), "$id/label").apply { setTextColor(labelInk) }
            val spark = CurveLibrarySparklineView(context, style, compact = true).apply {
                tag = "$id/preview"; visibility = GONE
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val header = object : LinearLayout(context) {
                init { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
                override fun onMeasure(w: Int, h: Int) {
                    val natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    label.measure(natural, natural)
                    val total = MeasureSpec.getSize(w)
                    val available = total - label.measuredWidth - dp(21)
                    // Decorative previews have lower priority than the full slot label.
                    val max = dp(if (portrait) 38 else 43)
                    val target = if (available >= dp(18)) (available - dp(4)).coerceIn(0, max) else 0
                    (spark.layoutParams as LinearLayout.LayoutParams).apply { width = target; marginStart = if (target > 0) dp(4) else 0 }
                    super.onMeasure(w, h)
                }
            }
            header.addView(PresetGlyph(context, glyph, labelInk), LinearLayout.LayoutParams(dp(16), dp(16)))
            header.addView(label, LinearLayout.LayoutParams(0, -2, 1f).withStart(5))
            header.addView(spark, LinearLayout.LayoutParams(dp(38), dp(10)))
            words.addView(header, LinearLayout.LayoutParams(-1, -2))
            // The reference longest name is 133dp wide on a 360dp screen.
            // Noto Sans SC 500 measures ~131dp at 13sp; preserve app font scaling.
            val titleSp = if (portrait) 13 else if (short) 14 else 15
            val name = style.text("", TaskProductVisualStyle.Type(titleSp, 500, titleSp + 5), nameId).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            words.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (short) 0 else 3) })
            val button = Button(context).apply {
                tag = actionId
                setPadding(0, 0, 0, 0)
                minHeight = 0; minWidth = 0; minimumHeight = 0; minimumWidth = 0
                elevation = 0f; stateListAnimator = null; backgroundTintList = null
                setOnClickListener { click() }
                if (!portrait && glyph != Glyph.CLOCK && onNameLongClick != null) setOnLongClickListener {
                    onNameLongClick.invoke(name.text.toString())
                    true
                }
                // The content and accessibility name are both provided by the native card above.
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused),
                        RippleDrawable(ripple, style.shape(wallpaperTint(style.accent, 44), radius = 0), style.shape(Color.WHITE, radius = 0)))
                    addState(intArrayOf(), RippleDrawable(ripple, null, mask))
                }
            }
            // Visual content first, real keyboard/TalkBack button overlays the full tile.
            addView(words, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
            addView(button, FrameLayout.LayoutParams(-1, -1))
            visual = Visual(button, name, spark, label)
        }
        fun standalone(value: Boolean) {
            background = if (value) style.shape(surface, radius = radius).apply {
                setStroke(dp(1), if (dark) separator else 0x35ffffff)
            } else null
            elevation = if (value) dp(2).toFloat() else 0f
        }
    }

    private enum class Glyph { LEVER, BOOKMARK, CLOCK }
    private class PresetGlyph(context: Context, val glyph: Glyph, color: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 1.65f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        private val outline = Path()
        override fun onDraw(canvas: Canvas) {
            val restore = canvas.save()
            canvas.scale(width / 20f, height / 20f)
            when (glyph) {
                Glyph.BOOKMARK -> {
                    outline.rewind(); outline.moveTo(4.5f, 3f); outline.lineTo(15.5f, 3f)
                    outline.lineTo(15.5f, 17f); outline.lineTo(10f, 13.5f); outline.lineTo(4.5f, 17f)
                    outline.close(); canvas.drawPath(outline, paint)
                }
                Glyph.CLOCK -> {
                    canvas.drawCircle(10f, 10f, 7.5f, paint)
                    canvas.drawLine(10f, 6f, 10f, 10f, paint)
                    canvas.drawLine(10f, 10f, 13f, 12.5f, paint)
                }
                Glyph.LEVER -> {
                    canvas.drawLine(1.5f, 16.5f, 17f, 16.5f, paint)
                    canvas.drawArc(5f, 11f, 12f, 22f, 180f, 180f, false, paint)
                    canvas.drawLine(9.5f, 11.5f, 15.5f, 5f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(16.5f, 4f, 2.5f, paint)
                    paint.style = Paint.Style.STROKE
                }
            }
            canvas.restoreToCount(restore)
        }
    }
}

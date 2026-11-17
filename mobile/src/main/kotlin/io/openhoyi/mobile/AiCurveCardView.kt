package io.openhoyi.mobile

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.view.Gravity

/** No storage or controller dependencies. Callbacks are native host actions, never model links. */
internal class AiCurveCardView(
    context: Context,
    card: AiCurveCard,
    style: TaskProductVisualStyle,
    english: Boolean,
    onOpen: (() -> Unit)? = null,
    onReview: (() -> Unit)? = null,
    referenceAvailable: Boolean = true,
    onViewCurve: (() -> Unit)? = null,
    libraryReferenceAvailable: Boolean = false,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        tag = "ai/curve-card/${card.id}"
        setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12))
        background = style.shape(style.surface, true, 12)
        fun words(en: String, zh: String) = if (english) en else zh
        fun label(text: String, type: TaskProductVisualStyle.Type = TaskProductVisualStyle.body) =
            style.text(text, type).also { addView(it, LayoutParams(-1, -2)) }
        val item = card.item
        val badge = style.text(when (card.kind) {
            AiCurveCard.Kind.RECOMMENDATION -> if (card.source == "factory-v3") words("Factory · read only", "内置 · 只读") else words("Custom", "自定义")
            AiCurveCard.Kind.CREATE -> words("New draft", "新建草稿")
            AiCurveCard.Kind.REPLACE -> words("Edit draft", "修改草稿")
            AiCurveCard.Kind.COPY_FACTORY -> words("Save-as draft", "另存草稿")
        }, TaskProductVisualStyle.auxiliary).apply {
            setTextColor(style.accent); background = style.shape(style.selected, radius = 20)
            setPadding(style.dp(10), style.dp(4), style.dp(10), style.dp(4))
        }
        val identity = object : LinearLayout(context) {
            init { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                for (index in 0 until childCount) getChildAt(index).measure(free, free)
                val inline = View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED ||
                    getChildAt(0).measuredWidth + getChildAt(1).measuredWidth + style.dp(8) <= View.MeasureSpec.getSize(widthMeasureSpec)
                orientation = if (inline) HORIZONTAL else VERTICAL
                (getChildAt(0).layoutParams as LayoutParams).width = if (inline) -2 else -1
                (getChildAt(1).layoutParams as LayoutParams).apply {
                    marginStart = if (inline) style.dp(8) else 0
                    topMargin = if (inline) 0 else style.dp(4)
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            addView(style.text(item.name, TaskProductVisualStyle.compactMetric), LayoutParams(-2, -2))
            addView(badge, LayoutParams(-2, -2))
        }
        val header = object : LinearLayout(context) {
            init { isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                for (index in 0 until childCount) getChildAt(index).measure(free, free)
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                val inline = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
                    getChildAt(0).measuredWidth + getChildAt(1).measuredWidth + style.dp(12) <= width
                orientation = if (inline) HORIZONTAL else VERTICAL
                (getChildAt(0).layoutParams as LayoutParams).apply { this.width = if (inline) 0 else -1; weight = if (inline) 1f else 0f }
                (getChildAt(1).layoutParams as LayoutParams).apply { marginStart = if (inline) style.dp(12) else 0; topMargin = if (inline) 0 else style.dp(4) }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        header.addView(identity, LayoutParams(-1, -2))
        val diagram = AiCurveRecipeDiagram.from(item)
        val quantity = if (diagram.waterLimitFromStageSum) CurveDraftNumber.format(diagram.assignedWaterTenthsMl, 1) else diagram.waterLimitMl?.toString() ?: "—"
        header.addView(style.text((diagram.temperatureC?.let { "$it °C" } ?: "—") + " · " + diagram.stages.size + words(" stages", " 段") + " · " +
            (if (diagram.waterLimitFromStageSum) words("sum ", "合计 ") else "") + quantity + " mL", TaskProductVisualStyle.auxiliary).apply { setTextColor(style.muted) }, LayoutParams(-2, -2))
        addView(header, LayoutParams(-1, -2))
        if (diagram.renderable) {
            val plot = AiCurveRecipeDiagramView(context, diagram, style)
            val scroll = AiCurveRecipeDiagramScrollView(context, plot).apply {
                contentDescription = words("Ordinal recipe stage targets, not elapsed time. Scroll horizontally if needed.", "配方段序目标，非时间轴；空间不足时可横向滚动。")
            }
            addView(scroll, LayoutParams(-1, -2).apply { topMargin = style.dp(8); bottomMargin = style.dp(4) })
            val hint = label(words("Swipe for more stages →", "横滑查看其余分段 →"), TaskProductVisualStyle.auxiliary).apply { visibility = View.GONE }
            scroll.viewTreeObserver.addOnGlobalLayoutListener {
                // Wait for the entire child layout: a scroll-view layout callback can run before
                // its child acquires the final width, especially after a narrow-window resize.
                val next = if (scroll.canScrollHorizontally(1) || scroll.canScrollHorizontally(-1)) View.VISIBLE else View.GONE
                if (hint.visibility != next) hint.visibility = next
            }
        }
        if (diagram.stages.map { it.unit }.distinct().size > 1)
            label(words("bar / mL/s · independent scales", "bar / mL/s · 分别定标"), TaskProductVisualStyle.auxiliary)
        val actions = object : LinearLayout(context) {
            private val rows = mutableListOf<List<View>>()
            private val gap = style.dp(8)
            init { isBaselineAligned = false }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
                    Int.MAX_VALUE else View.MeasureSpec.getSize(widthMeasureSpec)
                rows.clear()
                var row = mutableListOf<View>()
                var rowWidth = 0
                var widest = 0
                for (index in 0 until childCount) {
                    val child = getChildAt(index)
                    // A long translated label wraps inside the viewport, never clips its hit area.
                    child.measure(View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    val needed = child.measuredWidth + if (row.isEmpty()) 0 else gap
                    if (row.isNotEmpty() && rowWidth + needed > available) {
                        rows += row; widest = maxOf(widest, rowWidth)
                        row = mutableListOf(); rowWidth = 0
                    }
                    rowWidth += child.measuredWidth + if (row.isEmpty()) 0 else gap
                    row += child
                }
                if (row.isNotEmpty()) { rows += row; widest = maxOf(widest, rowWidth) }
                setMeasuredDimension(View.resolveSize(widest, widthMeasureSpec),
                    rows.sumOf { line -> line.maxOf { it.measuredHeight } })
            }
            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                var y = 0
                rows.forEach { line ->
                    val height = line.maxOf { it.measuredHeight }
                    val rowWidth = line.sumOf { it.measuredWidth } + gap * (line.size - 1)
                    var x = width - rowWidth
                    line.forEach { child ->
                        val childTop = y + (height - child.measuredHeight) / 2
                        child.layout(x, childTop, x + child.measuredWidth, childTop + child.measuredHeight)
                        x += child.measuredWidth + gap
                    }
                    y += height
                }
            }
        }
        addView(actions, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
        fun button(text: String, primary: Boolean = false, action: () -> Unit): Button =
            style.action(actions, text, "ai/curve-card/${card.id}/" + if (primary) "primary" else "view-curve", primary = primary, outline = false, top = 0, onClick = action).apply {
                val actionColors = textColors
                style.apply(this, TaskProductVisualStyle.Type(14, 500, 22))
                setTextColor(actionColors)
                gravity = Gravity.CENTER
                isAllCaps = false; setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
                minHeight = style.dp(48); minimumHeight = style.dp(48)
            }
        if (onViewCurve != null) {
            // Browsing a source curve never substitutes the proposed draft.
            button(words("View curve", "查看曲线"), action = onViewCurve).apply {
                tag = "ai/curve-card/${card.id}/view-curve"
                isEnabled = libraryReferenceAvailable
            }
            if (!libraryReferenceAvailable) label(if (card.kind == AiCurveCard.Kind.CREATE)
                words("Not saved to the library yet.", "尚未保存到曲线库。") else
                words("Source curve unavailable or changed.", "来源曲线不可用或已变化。"), TaskProductVisualStyle.auxiliary)
        }
        if (card.kind == AiCurveCard.Kind.RECOMMENDATION && onOpen != null) {
            button(words("Open extraction", "进入萃取页"), primary = true, action = onOpen).apply {
                isEnabled = referenceAvailable
                contentDescription = words("Open extraction page; does not start extraction.", "进入萃取页，不会自动萃取。")
            }
            if (!referenceAvailable) label(words("Historical snapshot. The current curve is unavailable or has changed.", "历史快照，当前曲线不可用或内容已变化。"), TaskProductVisualStyle.auxiliary)
        } else if (card.kind == AiCurveCard.Kind.RECOMMENDATION) label(words("Historical snapshot. Opening is unavailable.", "历史快照，当前不可打开。"), TaskProductVisualStyle.auxiliary)
        else if (onReview != null) button(words("Confirm", "确定"), primary = true, action = onReview)
            .apply { contentDescription = words("Confirm: open prefilled editor; save there yourself.", "确定：进入预填编辑页，由你手动保存。") }
        else label(words("View only. No current editor action is available.", "仅供查看，当前没有可用的编辑操作。"), TaskProductVisualStyle.auxiliary)
    }
}

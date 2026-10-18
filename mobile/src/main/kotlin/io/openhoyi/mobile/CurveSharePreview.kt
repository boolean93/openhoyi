package io.openhoyi.mobile

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** Share-only composition; dialogs keep their independent preview layout. */
internal fun ThemedActivity.sharePreview(workspace: TaskWorkspaceLayout, doc: CustomCurveDocument) {
    val style = TaskProductVisualStyle(this)
    val preview = style.column("product/share/preview-body")
    workspace.primaryBody.addView(ScrollView(this).apply {
        tag = "product/share/preview-scroll"; isFillViewport = true; addView(preview)
    }, LinearLayout.LayoutParams(-1, -1))
    val name = style.text(doc.name, TaskProductVisualStyle.title, "full-text-details").apply {
        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    val mode = style.text(getString(if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE)
        R.string.task_chart_pressure else R.string.home_lever_flow), TaskProductVisualStyle.auxiliary,
        "product/share/mode").apply {
        setTextColor(style.accent); setPadding(style.dp(8), 0, style.dp(8), 0)
        background = style.shape(style.selected); contentDescription = doc.controlMode.name
    }
    val identity = object : LinearLayout(this) {
        init {
            isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL
            addView(name, LayoutParams(-2, -2))
            addView(mode, LayoutParams(-2, -2).apply { marginStart = style.dp(16) })
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            name.measure(natural, natural); mode.measure(natural, natural)
            val wraps = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
                name.measuredWidth + mode.measuredWidth + style.dp(16) > View.MeasureSpec.getSize(widthMeasureSpec)
            (name.layoutParams as LayoutParams).apply { width = if (wraps) 0 else -2; weight = if (wraps) 1f else 0f }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    style.add(preview, identity)
    val metrics = style.row("product/share/summary")
    listOf(
        Triple(R.string.curve_preview_temperature, doc.temperatureC.toString(), "°C"),
        Triple(R.string.curve_preview_weight, if (doc.targetHundredthsGram == 0) getString(R.string.curve_details_no_weight)
            else CurveDraftNumber.format(doc.targetHundredthsGram, 2), if (doc.targetHundredthsGram == 0) null else "g")
    ).forEachIndexed { index, (label, value, unit) ->
        val card = style.column().apply {
            setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8))
            minimumHeight = style.dp(74); background = style.shape(style.surface, true)
        }
        style.add(card, style.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true))
        val metric = style.quantity(value, unit, TaskProductVisualStyle.shareMetric, "product/share/metric-$index")
        (metric.getChildAt(0).layoutParams as LinearLayout.LayoutParams).apply { width = 0; weight = 1f }
        style.add(card, metric)
        metrics.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = style.dp(24) })
    }
    style.add(preview, metrics, 8)
    var chart: CurveStageView? = null
    if (doc.controlMode == CustomCurveDocument.ControlMode.PRESSURE) {
        style.add(preview, style.text(getString(R.string.profiles_preview), TaskProductVisualStyle.section), 8)
        chart = CurveStageView(this).apply { targets = doc.stages.map { it.target }; productVisualStyle = style; tag = "product/share/stage" }
        preview.addView(chart, LinearLayout.LayoutParams(-1, style.dp(160)))
    } else style.add(preview, style.text(getString(R.string.profiles_flow_raw), TaskProductVisualStyle.body, secondary = true), 8)
    val caption = if (chart != null) style.text(getString(R.string.task_form_stage_caption), TaskProductVisualStyle.auxiliary,
        "product/share/stage-caption", true) else null
    val count = style.text(getString(R.string.profiles_stage_summary, doc.stages.size), TaskProductVisualStyle.auxiliary.copy(weight = 500), "product/share/stage-count")
    val metadata = object : android.widget.FrameLayout(this) {
        var inline = false
            set(value) { if (field != value) { field = value; forceLayout() } }
        private var resolvedInline = false
        init { tag = "product/share/stage-metadata"; caption?.let { addView(it) }; addView(count) }
        fun inlineFits(width: Int): Boolean {
            fun naturalWidth(text: android.widget.TextView) = kotlin.math.ceil(android.text.Layout.getDesiredWidth(text.text, text.paint).toDouble()).toInt() + text.paddingLeft + text.paddingRight
            return caption != null && naturalWidth(count) + naturalWidth(caption) + style.dp(16) <= width
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = View.MeasureSpec.getSize(widthMeasureSpec)
            val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            resolvedInline = inline && inlineFits(width)
            if (resolvedInline) {
                count.measure(natural, natural); caption!!.measure(natural, natural)
                // Both original 14sp texts retain their natural width and common baseline.
                val baseline = maxOf(count.baseline, caption!!.baseline)
                val below = maxOf(count.measuredHeight - count.baseline, caption.measuredHeight - caption.baseline)
                setMeasuredDimension(width, baseline + below)
            } else {
                count.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), natural)
                caption?.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), natural)
                setMeasuredDimension(width, count.measuredHeight + (caption?.let { it.measuredHeight + style.dp(8) } ?: 0))
            }
        }
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            if (resolvedInline && caption != null) {
                val baseline = maxOf(count.baseline, caption.baseline)
                val rtl = layoutDirection == View.LAYOUT_DIRECTION_RTL
                val countX = if (rtl) width - count.measuredWidth else 0
                val captionX = if (rtl) 0 else width - caption.measuredWidth
                count.layout(countX, baseline - count.baseline, countX + count.measuredWidth, baseline - count.baseline + count.measuredHeight)
                caption.layout(captionX, baseline - caption.baseline, captionX + caption.measuredWidth, baseline - caption.baseline + caption.measuredHeight)
            } else {
                caption?.layout(0, 0, width, caption.measuredHeight)
                val y = caption?.let { it.measuredHeight + style.dp(8) } ?: 0
                count.layout(0, y, width, y + count.measuredHeight)
            }
        }
    }
    style.add(preview, metadata, 8)
    val table = style.column("share-stage-table")
    style.add(preview, table, 8)
    fun row(values: List<String>, heading: Boolean, stageIndex: Int? = null) {
        val line = style.row(if (heading) "product/share/table/header" else "product/share/table/row${requireNotNull(stageIndex) + 1}")
        table.addView(line, LinearLayout.LayoutParams(-1, -2))
        values.forEachIndexed { column, value ->
            val label = style.text(value, TaskProductVisualStyle.auxiliary.copy(weight = if (heading) 500 else 400), secondary = false)
            val cell: View = if (column == 0 && stageIndex != null) {
                style.row().apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(View(this@sharePreview).apply {
                        tag = "product/share/table/row${stageIndex + 1}/dot"
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(style.stageColors[stageIndex])
                        }
                    }, LinearLayout.LayoutParams(style.dp(12), style.dp(12)))
                    addView(label, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) })
                }
            } else label
            cell.setPaddingRelative(style.dp(8), 0, style.dp(8), 0)
            cell.background = style.shape(if (heading) style.disabled else style.surface, true, 0)
            line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }
    // Raw flow values are kept raw and labelled; they never become pressure targets.
    row(listOf(getString(R.string.import_table_stage), getString(if (chart != null) R.string.import_table_pressure else R.string.profiles_flow_raw),
        getString(R.string.import_table_water)), true)
    doc.stages.forEachIndexed { index, stage ->
        row(listOf(getString(R.string.profiles_stage, index + 1),
            if (chart != null) CurveDraftNumber.format(stage.target, 1) else stage.target.toString(),
            CurveDraftNumber.format(stage.waterTenthsMl, 1)), false, index)
    }
    workspace.onPrimarySpaceAvailable = { width, height ->
        val contentWidth = (width - workspace.primaryBody.paddingLeft - workspace.primaryBody.paddingRight).coerceAtLeast(0)
        val exactWidth = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY)
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        // Preserve the original vertical composition unless its measured natural
        // budget exceeds the actual viewport with the unchanged 160dp graph.
        metadata.inline = false
        var fixedHeight = 0
        for (index in 0 until preview.childCount) {
            val child = preview.getChildAt(index)
            if (child !== chart) {
                child.measure(exactWidth, unspecified)
                val params = child.layoutParams as LinearLayout.LayoutParams
                fixedHeight += child.measuredHeight + params.topMargin + params.bottomMargin
            }
        }
        val available = height - workspace.primaryBody.paddingTop - workspace.primaryBody.paddingBottom
        if (chart != null && fixedHeight + style.dp(160) > available && metadata.inlineFits(contentWidth)) {
            val verticalHeight = metadata.measuredHeight
            metadata.inline = true
            metadata.measure(exactWidth, unspecified)
            fixedHeight += metadata.measuredHeight - verticalHeight
        }
        chart?.let {
            val params = it.layoutParams as LinearLayout.LayoutParams
            params.height = maxOf(style.dp(160), available - fixedHeight)
            params.weight = 0f
        }
        fixedHeight + (chart?.layoutParams?.height ?: 0) + workspace.primaryBody.paddingTop + workspace.primaryBody.paddingBottom
    }
}

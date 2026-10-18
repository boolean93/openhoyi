package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Explicit M1 page input, not a theme override. No dependency on Mock specimens. */
internal class TaskProductVisualStyle(val context: Context) {
    data class Type(val sizeSp: Int, val weight: Int, val lineSp: Int)
    companion object {
        const val FONT_SHA = "a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da"
        val body = Type(16, 400, 24)
        val action = Type(16, 500, 24)
        val auxiliary = Type(14, 400, 22)
        val section = Type(20, 500, 30)
        val title = Type(24, 500, 36)
        val compactMetric = Type(16, 500, 24)
        val shareMetric = Type(20, 400, 30)
        val largeValue = Type(40, 700, 60)
    }
    private val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private fun pick(light: String, night: String) = Color.parseColor(if (dark) night else light)
    val page = pick("#F9FAF9", "#11191A")
    val surface = pick("#FFFFFF", "#1B2526")
    val ink = pick("#101820", "#EDF3F2")
    val muted = pick("#657183", "#A9B9B8")
    val border = pick("#DDE3E7", "#3B4B4C")
    val primary = Color.parseColor("#087A78")
    val accent = pick("#087A78", "#83D6D0")
    val selected = pick("#E2F0ED", "#203D3B")
    val disabled = pick("#ECEFF1", "#263234")
    val onDisabled = pick("#8A95A3", "#7D908F")
    val pressure = pick("#176BD1", "#8CB8FF")
    val stageColors = listOf(accent, pressure, pick("#A46500", "#F2C16D"), pick("#7941A5", "#C7A3EB"))
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    fun sp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), context.resources.displayMetrics)
    fun shape(fill: Int, outline: Boolean = false, radius: Int = 12) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat()
        if (outline) setStroke(dp(1), border)
    }
    fun apply(view: TextView, type: Type, secondary: Boolean = false) = view.apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, type.sizeSp.toFloat())
        typeface = TaskTypography.face(context, type.weight)
        includeFontPadding = false; letterSpacing = 0f
        paint.isFakeBoldText = false
        setTextColor(if (secondary) muted else ink)
        val line = sp(type.lineSp).toInt()
        if (android.os.Build.VERSION.SDK_INT >= 28) setLineHeight(line)
        else setLineSpacing((line - paint.fontMetricsInt.descent + paint.fontMetricsInt.ascent).coerceAtLeast(0).toFloat(), 1f)
        minHeight = sp(type.lineSp).toInt()
        gravity = Gravity.CENTER_VERTICAL
    }
    fun text(value: CharSequence, type: Type = body, id: String? = null, secondary: Boolean = false) =
        apply(TextView(context), type, secondary).apply { text = value; tag = id }
    fun column(id: String? = null) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; tag = id }
    fun row(id: String? = null) = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; tag = id }
    fun add(parent: LinearLayout, child: View, top: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    fun divider(parent: LinearLayout, top: Int = 16, bottom: Int = 16) {
        parent.addView(View(context).apply { setBackgroundColor(border) }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        })
    }
    fun button(view: Button, primary: Boolean = false, outline: Boolean = false): Button = view.apply {
        apply(this, action)
        isAllCaps = false; gravity = Gravity.CENTER
        minHeight = dp(if (primary) 56 else 48); minimumHeight = minHeight; minimumWidth = dp(48)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        elevation = 0f; stateListAnimator = null; backgroundTintList = null
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(onDisabled, if (primary) Color.WHITE else accent)))
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(disabled))
            addState(intArrayOf(android.R.attr.state_focused), shape(if (primary) this@TaskProductVisualStyle.primary else selected, true))
            addState(intArrayOf(), shape(if (primary) this@TaskProductVisualStyle.primary else Color.TRANSPARENT, outline))
        }
        background = RippleDrawable(ColorStateList.valueOf(if (primary) 0x33FFFFFF else selected), states, shape(Color.WHITE))
    }
    fun action(parent: LinearLayout, value: String, id: String, primary: Boolean = false, outline: Boolean = false,
        top: Int = 8, onClick: () -> Unit): Button = button(Button(context), primary, outline).apply {
        text = value; tag = id; setOnClickListener { onClick() }
        add(parent, this, top)
    }
    /** Independent number/unit TextViews, one baseline; no relative-size span. */
    fun quantity(number: String, unit: String?, type: Type, id: String, secondary: Boolean = false) = row(id).apply {
        isBaselineAligned = true; gravity = Gravity.BOTTOM
        addView(text(number, type, "$id/number", secondary), LinearLayout.LayoutParams(-2, -2))
        if (unit != null) addView(text(unit, auxiliary, "$id/unit", true), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
    }
    fun clickable(view: View) {
        view.minimumHeight = dp(48); view.minimumWidth = dp(48); view.isFocusable = true
        view.background = RippleDrawable(ColorStateList.valueOf(selected), null, shape(Color.WHITE, radius = 0))
    }
    /** Explicit three-page shell. The existing beanPage and all other callers keep their defaults. */
    fun page(activity: ThemedActivity, title: String, subtitle: String? = null,
        navigation: Class<out Activity>? = null): LinearLayout {
        activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = column("product/page/root").apply {
            setBackgroundColor(page); isFocusableInTouchMode = true
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        val shell = column().apply { setPadding(dp(16), dp(16), dp(16), dp(16)) }
        root.addView(shell, LinearLayout.LayoutParams(-1, 0, 1f))
        val header = HoyiUi.header(activity, shell, title, back = navigation == null)
        subtitle?.let { description ->
            header.contentDescription = "$title. $description"
            button(HoyiUi.button(activity, header, "ⓘ") { activity.beanDetails(title, description) }).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), -2)
                contentDescription = activity.getString(R.string.ui_full_details)
            }
        }
        styleHeader(header)
        // Explicit product shell only. HoyiUi.header and its legacy callers are untouched.
        if (navigation == null) (header.getChildAt(0) as? android.widget.ImageView)?.apply {
            setImageDrawable(HomeVisualIcon.drawable(activity, "back", accent)); imageTintList = null
            background = RippleDrawable(ColorStateList.valueOf(selected), null, shape(Color.WHITE))
        }
        val body = column()
        shell.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = BeanActionRows(activity)
        shell.addView(actions, LinearLayout.LayoutParams(-1, -2))
        body.tag = BeanPageRegions(actions, header)
        TaskPageFrame.install(activity, root, this)
        activity.window.statusBarColor = page; activity.window.navigationBarColor = surface
        activity.setContentView(root)
        return body
    }
    fun styleHeader(view: View) {
        if (view is Button) button(view)
        else if (view is TextView) apply(view,
            if (view.textSize / context.resources.displayMetrics.scaledDensity >= 20f) title else auxiliary)
        if (view is ViewGroup) for (index in 0 until view.childCount) styleHeader(view.getChildAt(index))
    }
}

/** Home-only compact summary; the library's CurvePreviewMetrics is unchanged. */
internal class TaskProductRecipeMetrics(private val activity: ThemedActivity, private val style: TaskProductVisualStyle) : LinearLayout(activity) {
    private var rendered: List<String?>? = null
    private val metricBodies = (0..2).map { style.row("product/home/summary/$it").apply { gravity = Gravity.CENTER_VERTICAL } }
    init {
        orientation = HORIZONTAL; setPadding(0, style.dp(8), 0, style.dp(8))
        metricBodies.forEachIndexed { index, group ->
            if (index > 0) addView(View(context).apply { setBackgroundColor(style.border) },
                LayoutParams(style.dp(1), style.dp(24)).apply { marginStart = style.dp(8); marginEnd = style.dp(8) })
            addView(group, LayoutParams(0, -2, 1f))
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        metricBodies.forEach { it.measure(natural, natural) }
        val required = metricBodies.sumOf { it.measuredWidth } + style.dp(34)
        val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val stacked = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED && required > available
        orientation = if (stacked) VERTICAL else HORIZONTAL
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as LayoutParams
            if (child in metricBodies) {
                params.width = if (stacked) -1 else 0; params.height = -2; params.weight = if (stacked) 0f else 1f
            } else {
                params.width = if (stacked) -1 else style.dp(1)
                params.height = if (stacked) style.dp(1) else style.dp(24)
                params.marginStart = if (stacked) 0 else style.dp(8); params.marginEnd = params.marginStart
                params.topMargin = if (stacked) style.dp(8) else 0; params.bottomMargin = params.topMargin
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    fun present(item: CurveLibraryItem) {
        val draft = item.customDocument; val factory = item.factoryCurve; val captured = item.controlProfile
        val temperature = draft?.temperatureC ?: factory?.temperatureC ?: captured?.temperatureC
        val weight = draft?.targetHundredthsGram ?: factory?.weightTenthsGram?.times(10) ?: captured?.targetHundredthsGram
        val stages = draft?.stages?.size ?: factory?.segmentCount ?: captured?.parameters?.segmentCount
        val values = listOf(temperature?.toString() ?: "—", weight?.let {
            if (it == 0) activity.getString(R.string.curve_details_no_weight) else CurveDraftNumber.format(it, 2)
        } ?: "—", stages?.toString() ?: "—")
        val units = listOf(if (temperature != null) "°C" else null, if (weight != null && weight != 0) "g" else null, null)
        val signature = values + units
        if (rendered == signature) return
        rendered = signature
        val titles = listOf(R.string.curve_preview_temperature, R.string.curve_preview_weight, R.string.curve_preview_stages)
        metricBodies.forEachIndexed { index, column ->
            column.removeAllViews()
            column.addView(HomeVisualIcon(activity, listOf("temperature", "coffee", "stages")[index], style.muted),
                LayoutParams(style.dp(24), style.dp(24)).apply { marginEnd = style.dp(8) })
            val line = style.row("product/home/summary/$index/values").apply { isBaselineAligned = true; gravity = Gravity.BOTTOM }
            line.addView(style.text(activity.getString(titles[index]), TaskProductVisualStyle.auxiliary,
                "product/home/summary/$index/label", true), LayoutParams(-2, -2).apply { marginEnd = style.dp(8) })
            line.addView(style.text(values[index], TaskProductVisualStyle.compactMetric, "product/home/summary/$index/values/number"))
            units[index]?.let { unit -> line.addView(style.text(unit, TaskProductVisualStyle.auxiliary,
                "product/home/summary/$index/values/unit", true), LayoutParams(-2, -2).apply { marginStart = style.dp(8) }) }
            column.addView(line, LayoutParams(-2, -2))
        }
        contentDescription = titles.indices.joinToString("\n") { "${activity.getString(titles[it])}: ${values[it]} ${units[it].orEmpty()}" }
    }
}

/** Actual production Canvas adapter; legacy CurveStageView Paint never enters this branch. */
internal class TaskProductStageRenderer(private val view: CurveStageView, private val palette: TaskProductVisualStyle) {
    private val context get() = view.context
    private val resources get() = view.resources
    private val width get() = view.width
    private val height get() = view.height
    private val tag get() = view.tag
    var evidence = JSONArray()
        private set
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun dp(value: Float) = (value * resources.displayMetrics.density).toInt().toFloat()
    private fun paintJson() = JSONObject().put("colorArgb", String.format(Locale.ROOT, "#%08X", paint.color))
        .put("alpha", paint.alpha).put("strokeWidthPx", paint.strokeWidth).put("strokeCap", paint.strokeCap.name).put("style", paint.style.name)
        .put("textSizePx", paint.textSize).put("fakeBold", paint.isFakeBoldText)
        .put("weight", if (Build.VERSION.SDK_INT >= 28) paint.typeface?.weight else JSONObject.NULL)
        .put("fontSha256", TaskProductVisualStyle.FONT_SHA).put("textAlign", paint.textAlign.name)
    private fun label(canvas: Canvas, value: String, x: Float, baseline: Float, color: Int, align: Paint.Align, role: String, targetIndex: Int? = null) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color
        paint.textSize = palette.sp(14)
        paint.typeface = TaskTypography.face(context, 400); paint.textAlign = align
        val bounds = Rect(); paint.getTextBounds(value, 0, value.length, bounds)
        val advance = paint.measureText(value)
        val origin = x - when (align) { Paint.Align.CENTER -> advance / 2; Paint.Align.RIGHT -> advance; else -> 0f }
        canvas.drawText(value, x, baseline, paint)
        evidence.put(JSONObject().put("operation", "text").put("role", role).put("text", value).put("xPx", x).put("baselinePx", baseline)
            .put("advancePx", advance).put("glyphBoundsPx", JSONArray(listOf(origin + bounds.left,
                baseline + bounds.top, origin + bounds.right, baseline + bounds.bottom)))
            .put("fontMetrics", JSONObject().put("top", paint.fontMetrics.top).put("bottom", paint.fontMetrics.bottom)
                .put("ascent", paint.fontMetrics.ascent).put("descent", paint.fontMetrics.descent))
            .put("paint", paintJson()).apply { if (targetIndex != null) put("targetIndex", targetIndex) })
    }
    private fun line(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, strokeDp: Float, role: String = "data", gridId: String? = null) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color; paint.strokeWidth = dp(strokeDp)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(x1, y1, x2, y2, paint)
        evidence.put(JSONObject().put("operation", "line").put("role", role)
            .put("endpointsPx", JSONArray(listOf(x1, y1, x2, y2))).put("paint", paintJson())
            .apply { if (gridId != null) put("gridId", gridId) })
    }
    private data class TargetLabel(val index: Int, val value: String, val x: Float,
        val baseline: Float, val color: Int, val glyph: RectF, val protection: RectF)
    private fun boundsJson(rect: RectF) = JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))
    /** Subtract protection intervals from actual grid strokes; axes/data/backgrounds never use this path. */
    private fun grid(canvas: Canvas, id: String, x1: Float, y1: Float, x2: Float, y2: Float, targets: List<TargetLabel>) {
        val horizontal = y1 == y2
        require(horizontal && x1 < x2 || x1 == x2 && y1 < y2)
        val strokeWidth = dp(1f)
        val halfStroke = strokeWidth / 2
        val start = if (horizontal) x1 else y1
        val end = if (horizontal) x2 else y2
        val cross = if (horizontal) y1 else x1
        val excluded = mutableListOf<Int>()
        val intervals = targets.mapNotNull { target ->
            val rect = target.protection
            val perpendicularStart = (if (horizontal) rect.top else rect.left) - halfStroke
            val perpendicularEnd = (if (horizontal) rect.bottom else rect.right) + halfStroke
            if (cross <= perpendicularStart || cross >= perpendicularEnd) return@mapNotNull null
            val low = maxOf(start, (if (horizontal) rect.left else rect.top) - halfStroke)
            val high = minOf(end, (if (horizontal) rect.right else rect.bottom) + halfStroke)
            if (high <= low) null else { excluded.add(target.index); low to high }
        }.sortedBy { it.first }
        val visible = mutableListOf<List<Float>>()
        var cursor = start
        fun append(low: Float, high: Float) {
            if (high > low) visible.add(if (horizontal) listOf(low, y1, high, y1) else listOf(x1, low, x1, high))
        }
        intervals.forEach { (low, high) ->
            if (low > cursor) append(cursor, low)
            cursor = maxOf(cursor, high)
        }
        append(cursor, end)
        evidence.put(JSONObject().put("operation", "gridSegmentPlan").put("gridId", id)
            .put("originalEndpointsPx", JSONArray(listOf(x1, y1, x2, y2)))
            .put("visibleSegmentsPx", JSONArray(visible.map { JSONArray(it) }))
            .put("strokeWidthPx", strokeWidth).put("strokeCap", Paint.Cap.BUTT.name)
            .put("strokeGuardPx", halfStroke).put("excludedProtectionIndices", JSONArray(excluded))
            .put("measurement", "actual_drawLine_segments; original_is_audit_only; half_stroke_guard_keeps_visible_ink_outside_2dp_protection"))
        visible.forEach { segment -> line(canvas, segment[0], segment[1], segment[2], segment[3], palette.border, 1f, "grid", id) }
    }
    fun draw(canvas: Canvas) {
        evidence = JSONArray()
        val values = view.targets.take(4)
        if (values.isEmpty() || values.any { it !in 0..120 }) return
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = palette.sp(14); typeface = TaskTypography.face(context, 400) }
        val metrics = textPaint.fontMetrics
        fun ink(value: String) = Rect().also { textPaint.getTextBounds(value, 0, value.length, it) }
        val titleBaseline = -metrics.top
        val titleInkBottom = titleBaseline + ink(context.getString(R.string.import_table_pressure)).bottom
        val tickBaselineOffset = -(metrics.ascent + metrics.descent) / 2
        val highestTickInkTopRelative = tickBaselineOffset + ink("12").top
        val stageBaseline = height - metrics.bottom
        val stageInkTop = values.indices.minOf { stageBaseline + ink(context.getString(R.string.profiles_stage, it + 1)).top }
        // Round the plot inward to whole physical pixels, leaving the label bands
        // outward of it. This guarantees the minimum gap rather than tolerating
        // a Float subtraction that produces slightly less than the required 8dp.
        val plot = RectF(dp(40f), kotlin.math.ceil(maxOf(dp(32f), titleInkBottom + dp(8f) - highestTickInkTopRelative)),
            width - dp(16f), kotlin.math.floor(minOf(height - dp(38f), stageInkTop - dp(8f))))
        check(plot.width() > 0 && plot.height() > 0) { "Product chart has no plot area" }
        evidence.put(JSONObject().put("operation", "plotGeometry").put("projection", "CurveStageParameters.pressureFraction")
            .put("semanticId", "$tag/plot").put("plotBoundsPx", JSONArray(listOf(plot.left, plot.top, plot.right, plot.bottom)))
            .put("targetsTenthsBar", JSONArray(values)))
        evidence.put(JSONObject().put("operation", "labelBands").put("titleInkBottomPx", titleInkBottom)
            .put("highestTickInkTopPx", plot.top + highestTickInkTopRelative)
            .put("titleToHighestTickGapPx", plot.top + highestTickInkTopRelative - titleInkBottom)
            .put("stageInkTopPx", stageInkTop).put("plotToStageInkGapPx", stageInkTop - plot.bottom)
            .put("requiredGapPx", dp(8f)).put("minimumChartHeightPx", dp(160f)))
        label(canvas, context.getString(R.string.import_table_pressure), 0f, titleBaseline, palette.muted, Paint.Align.LEFT, "axisTitle")
        val share = !view.pressureColor
        val step = plot.width() / values.size
        // Same Paint, formatting, alignment and coordinates used by the eventual drawText calls.
        // Keep these boxes before grid drawing; no opaque eraser ever touches the stage backgrounds.
        val protectionPadding = 2f * resources.displayMetrics.density
        val targetLabels = values.mapIndexed { index, target ->
            val value = String.format(Locale.ROOT, "%.1f%s", target / 10.0, if (share) "" else " bar")
            val center = plot.left + step * (index + .5f)
            val y = plot.bottom - plot.height() * CurveStageParameters.pressureFraction(target)
            val baseline = y - dp(8f)
            val local = ink(value)
            val origin = center - textPaint.measureText(value) / 2
            val glyph = RectF(origin + local.left, baseline + local.top, origin + local.right, baseline + local.bottom)
            val protection = RectF(glyph).apply { inset(-protectionPadding, -protectionPadding) }
            TargetLabel(index, value, center, baseline, if (share) palette.stageColors[index] else palette.pressure, glyph, protection)
        }
        targetLabels.forEach { target ->
            evidence.put(JSONObject().put("operation", "targetLabelProtection").put("targetIndex", target.index)
                .put("text", target.value).put("glyphBoundsPx", boundsJson(target.glyph))
                .put("protectedBoundsPx", boundsJson(target.protection)).put("paddingDp", 2)
                .put("paddingPx", protectionPadding).put("measurement", "same_actual_Paint_getTextBounds_and_measureText_as_targetLabel_draw"))
        }
        if (share) for (index in values.indices) {
            paint.reset(); paint.color = palette.stageColors[index]; paint.alpha = (255 * .06f).toInt()
            val rect = RectF(plot.left + index * step, plot.top, plot.left + (index + 1) * step, plot.bottom)
            canvas.drawRect(rect, paint)
            evidence.put(JSONObject().put("operation", "stageBackground").put("requestedOpacity", .06)
                .put("boundsPx", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom))).put("paint", paintJson()))
        }
        for (tick in listOf(0, 6, 12)) {
            val y = plot.bottom - plot.height() * tick / 12f
            grid(canvas, "horizontal-$tick", plot.left, y, plot.right, y, targetLabels)
            label(canvas, "$tick", plot.left - dp(8f), y - (metrics.ascent + metrics.descent) / 2, palette.muted, Paint.Align.RIGHT, "axisTick")
        }
        // Actual left/bottom axes and stage boundaries; grid strokes remain distinct from data strokes.
        for (index in 1 until values.size) {
            val x = plot.left + step * index
            grid(canvas, "stage-$index", x, plot.top, x, plot.bottom, targetLabels)
        }
        line(canvas, plot.left, plot.top, plot.left, plot.bottom, palette.muted, 1f, "axis")
        line(canvas, plot.left, plot.bottom, plot.right, plot.bottom, palette.muted, 1f, "axis")
        values.forEachIndexed { index, target ->
            val color = if (share) palette.stageColors[index] else palette.pressure
            val center = plot.left + step * (index + .5f)
            val y = plot.bottom - plot.height() * CurveStageParameters.pressureFraction(target)
            line(canvas, center - step * (if (share) .5f else .34f), y,
                center + step * (if (share) .5f else .34f), y, color, 2f)
            paint.reset(); paint.isAntiAlias = true; paint.color = color
            canvas.drawCircle(center, y, dp(4f), paint)
            evidence.put(JSONObject().put("operation", "targetPoint").put("centerPx", JSONArray(listOf(center, y)))
                .put("radiusPx", dp(4f)).put("paint", paintJson()))
            val targetLabel = targetLabels[index]
            label(canvas, targetLabel.value, targetLabel.x, targetLabel.baseline, targetLabel.color, Paint.Align.CENTER, "targetLabel", index)
            label(canvas, context.getString(R.string.profiles_stage, index + 1), center, stageBaseline, palette.muted, Paint.Align.CENTER, "stageLabel")
        }
    }
}

package io.openhoyi.mobile

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Read-only recipe values, not live telemetry or a start eligibility decision. */
internal class CurvePreviewMetrics(private val activity:ThemedActivity,
    private val productVisualStyle: TaskProductVisualStyle? = null,
    private val waterSummary: Boolean = false):LinearLayout(activity) {
    private val titles=listOf(R.string.curve_preview_temperature,
        if (waterSummary) R.string.curve_plan_total_water else R.string.curve_preview_weight,
        R.string.curve_preview_stages)
    private val values:List<TextView>
    private val units = mutableListOf<TextView>()
    init {
        setWillNotDraw(false)
        orientation=HORIZONTAL
        setPadding(0,HoyiUi.dp(activity,16),0,HoyiUi.dp(activity,16))
        values=titles.map { title ->
            val column=LinearLayout(activity).apply { orientation=VERTICAL }
            addView(column,LayoutParams(0,-2,1f))
            if (productVisualStyle == null) {
                HoyiUi.label(activity,column,activity.getString(title),14,muted=true).apply {
                    setPadding(0,0,HoyiUi.dp(activity,8),HoyiUi.dp(activity,4))
                }
                HoyiUi.label(activity,column,"—",TaskVisualTokens.RECIPE_VALUE_SP,true).apply {
                    typeface=TaskTypography.face(activity,700)
                    gravity=Gravity.START
                    setPadding(0,0,HoyiUi.dp(activity,8),0)
                }
            } else {
                val index = titles.indexOf(title)
                column.setPaddingRelative(if (index == 0) 0 else HoyiUi.dp(activity,16), 0,
                    if (index == 2) 0 else HoyiUi.dp(activity,16), 0)
                productVisualStyle.add(column, productVisualStyle.text(activity.getString(title),
                    TaskProductVisualStyle.auxiliary, "product/curve-library/summary/$index/label", true))
                val quantity = productVisualStyle.quantity("—", if (index == 0) "°C" else if (index == 1)
                    if (waterSummary) "mL" else "g" else null,
                    TaskProductVisualStyle.shareMetric, "product/curve-library/summary/$index")
                productVisualStyle.add(column, quantity, 8)
                if (index < 2) units += quantity.getChildAt(1) as TextView
                quantity.getChildAt(0) as TextView
            }
        }
    }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint().apply {
            color = productVisualStyle?.border ?: activity.getColor(R.color.mobile_border)
            strokeWidth = resources.displayMetrics.density
        }
        val inset = paint.strokeWidth / 2
        canvas.drawLine(0f, inset, width.toFloat(), inset, paint)
        canvas.drawLine(0f, height - inset, width.toFloat(), height - inset, paint)
        if (productVisualStyle != null) for (index in 1..2) {
            val column = getChildAt(index)
            val x = if (layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) column.right.toFloat() else column.left.toFloat()
            canvas.drawLine(x, HoyiUi.dp(activity,16).toFloat(), x, (height - HoyiUi.dp(activity,16)).toFloat(), paint)
        }
    }
    fun present(item:CurveLibraryItem) {
        val draft=item.customDocument
        val factory=item.factoryCurve
        val captured=item.controlProfile
        val waterLimit = if (waterSummary) CurveRecipePlan.from(item).totalWaterLimitMl else null
        val temperature=draft?.temperatureC ?: factory?.temperatureC ?: captured?.temperatureC
        val weight=draft?.targetHundredthsGram ?: factory?.weightTenthsGram?.times(10) ?: captured?.targetHundredthsGram
        val stages=draft?.stages?.size ?: factory?.segmentCount ?: captured?.parameters?.segmentCount
        val formatted=listOf(temperature?.let { "$it °C" } ?: "—",
            if (waterSummary) waterLimit?.let { "$it mL" } ?: "—" else
                weight?.let { if(it==0) activity.getString(R.string.curve_details_no_weight)
                    else String.format(Locale.getDefault(),"%.2f g",it/100.0) } ?: "—",
            stages?.toString() ?: "—")
        values.zip(formatted).forEachIndexed { index, (view,value) ->
            val unit = when {
                index == 0 && temperature != null -> "°C"
                index == 1 && waterSummary -> if (waterLimit != null) "mL" else null
                index == 1 && weight != null && weight != 0 -> "g"
                else -> null
            }
            if (productVisualStyle == null) view.text = curveMetricText(activity, value, unit)
            else {
                view.text = when (index) {
                    0 -> temperature?.toString() ?: "—"
                    1 -> if (waterSummary) waterLimit?.toString() ?: "—" else
                        weight?.let { if (it == 0) activity.getString(R.string.curve_details_no_weight)
                            else String.format(Locale.getDefault(), "%.2f", it / 100.0) } ?: "—"
                    else -> stages?.toString() ?: "—"
                }
                units.getOrNull(index)?.visibility = if (unit == null) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
        contentDescription=titles.zip(formatted).joinToString("\n") { (title,value) -> "${activity.getString(title)}: $value" }
    }
}

package io.openhoyi.mobile

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Read-only recipe values, not live telemetry or a start eligibility decision. */
internal class CurvePreviewMetrics(private val activity:ThemedActivity):LinearLayout(activity) {
    private val titles=listOf(R.string.curve_preview_temperature,R.string.curve_preview_weight,R.string.curve_preview_stages)
    private val values:List<TextView>
    init {
        setWillNotDraw(false)
        orientation=HORIZONTAL
        setPadding(0,HoyiUi.dp(activity,16),0,HoyiUi.dp(activity,16))
        values=titles.map { title ->
            val column=LinearLayout(activity).apply { orientation=VERTICAL }
            addView(column,LayoutParams(0,-2,1f))
            HoyiUi.label(activity,column,activity.getString(title),14,muted=true).apply {
                setPadding(0,0,HoyiUi.dp(activity,8),HoyiUi.dp(activity,4))
            }
            HoyiUi.label(activity,column,"—",24,true).apply {
                typeface=TaskTypography.face(activity,700)
                gravity=Gravity.START
                setPadding(0,0,HoyiUi.dp(activity,8),0)
            }
        }
    }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val paint = android.graphics.Paint().apply {
            color = activity.getColor(R.color.mobile_border)
            strokeWidth = resources.displayMetrics.density
        }
        val inset = paint.strokeWidth / 2
        canvas.drawLine(0f, inset, width.toFloat(), inset, paint)
        canvas.drawLine(0f, height - inset, width.toFloat(), height - inset, paint)
    }
    fun present(item:CurveLibraryItem) {
        val draft=item.customDocument
        val factory=item.factoryCurve
        val captured=item.controlProfile
        val temperature=draft?.temperatureC ?: factory?.temperatureC ?: captured?.temperatureC
        val weight=draft?.targetHundredthsGram ?: factory?.weightTenthsGram?.times(10) ?: captured?.targetHundredthsGram
        val stages=draft?.stages?.size ?: factory?.segmentCount ?: captured?.parameters?.segmentCount
        val formatted=listOf(temperature?.let { "$it °C" } ?: "—",
            weight?.let { if(it==0) activity.getString(R.string.curve_details_no_weight)
                else String.format(Locale.getDefault(),"%.2f g",it/100.0) } ?: "—",
            stages?.toString() ?: "—")
        values.zip(formatted).forEachIndexed { index, (view,value) ->
            val unit = when {
                index == 0 && temperature != null -> "°C"
                index == 1 && weight != null && weight != 0 -> "g"
                else -> null
            }
            view.text = if (unit == null) value else android.text.SpannableString(value).apply {
                setSpan(android.text.style.RelativeSizeSpan(.6f), length - unit.length, length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        contentDescription=titles.zip(formatted).joinToString("\n") { (title,value) -> "${activity.getString(title)}: $value" }
    }
}

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
        values.zip(formatted).forEach { (view,value) -> view.text=value }
        contentDescription=titles.zip(formatted).joinToString("\n") { (title,value) -> "${activity.getString(title)}: $value" }
    }
}

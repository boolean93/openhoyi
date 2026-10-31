package io.openhoyi.mobile

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan

/** Preserve the full value for selection/accessibility while making units subordinate. */
internal fun curveMetricText(context: Context, value: String, unit: String?): CharSequence {
    if (unit == null || !value.endsWith(" $unit")) return value
    val face = TaskTypography.face(context, 400)
    return SpannableString(value).apply {
        val start = length - unit.length
        setSpan(RelativeSizeSpan(TaskVisualTokens.SUPPORTING_SP.toFloat() / TaskVisualTokens.RECIPE_VALUE_SP),
            start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(object : MetricAffectingSpan() {
            override fun updateDrawState(paint: TextPaint) { paint.typeface = face }
            override fun updateMeasureState(paint: TextPaint) { paint.typeface = face }
        }, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Records actual native typography/spacing next to screenshots, not an ImageGen match claim. */
internal object TaskTypographyEvidence {
    private fun currentShape(drawable: android.graphics.drawable.Drawable?): android.graphics.drawable.GradientDrawable? {
        if (drawable == null) return null
        val current = drawable.current
        if (current !== drawable) return currentShape(current)
        if (drawable is android.graphics.drawable.GradientDrawable) return drawable
        if (drawable is android.graphics.drawable.LayerDrawable) {
            for (index in 0 until drawable.numberOfLayers) {
                if (drawable.getId(index) == android.R.id.mask) continue
                currentShape(drawable.getDrawable(index))?.let { return it }
            }
        }
        return null
    }

    fun capture(activity: Activity, directory: File) {
        check(activity.resources.configuration.locales[0].language == "zh")
        val probe = TextView(activity)
        val sample = "萃取压力温度 0123456789 bar ml g °C"
        val themePaint = Paint().apply { textSize = 40f; typeface = probe.typeface }
        val fixedPaint = Paint().apply { textSize = 40f; typeface = TaskTypography.face(activity) }
        check(kotlin.math.abs(themePaint.measureText(sample) - fixedPaint.measureText(sample)) < .01f) {
            "Native theme does not use the fixed Chinese regular font"
        }
        if (Build.VERSION.SDK_INT >= 28) {
            check(probe.typeface.weight == 400) { "Native text inherited a thin default font" }
            for (weight in listOf(300, 400, 500, 700)) check(TaskTypography.face(activity, weight).weight == weight)
            probe.setTypeface(probe.typeface, Typeface.BOLD)
            check(probe.typeface.weight == 700) { "Native bold did not select the bold family member" }
        }
        val items = JSONArray()
        val layout = JSONArray()
        fun visit(view: View, path: String) {
            if (view.isShown) {
                val rect = Rect(); view.getGlobalVisibleRect(rect)
                val margins = view.layoutParams as? ViewGroup.MarginLayoutParams
                val background = currentShape(view.background)
                layout.put(JSONObject().put("path", path).put("class", view.javaClass.simpleName)
                    .put("tag", view.tag?.toString()).put("widthPx", view.width).put("heightPx", view.height)
                    .put("visibleBoundsPx", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                    .put("paddingPx", JSONArray(listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)))
                    .put("marginsPx", margins?.let { JSONArray(listOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)) })
                    .put("cornerRadiusPx", background?.cornerRadius).put("elevationPx", view.elevation)
                    .put("translationZPx", view.translationZ).put("hasStateListAnimator", view.stateListAnimator != null)
                    .put("clickable", view.isClickable)
                    .put("enabled", view.isEnabled))
            }
            if (view is TextView && view.isShown) {
                val rect = Rect(); view.getGlobalVisibleRect(rect)
                val runs = JSONArray()
                val styled = view.text as? Spanned
                if (styled != null) {
                    var start = 0
                    while (start < styled.length) {
                        val end = styled.nextSpanTransition(start, styled.length, MetricAffectingSpan::class.java)
                        val paint = TextPaint(view.paint)
                        val spans = styled.getSpans(start, end, MetricAffectingSpan::class.java)
                        spans.forEach { it.updateMeasureState(paint) }
                        runs.put(JSONObject().put("start", start).put("end", end)
                            .put("text", styled.subSequence(start, end).toString().take(160))
                            .put("textSizePx", paint.textSize)
                            .put("weight", if (Build.VERSION.SDK_INT >= 28) paint.typeface?.weight else null)
                            .put("fakeBold", paint.isFakeBoldText)
                            .put("spans", JSONArray(spans.map { it.javaClass.simpleName })))
                        start = end
                    }
                }
                items.put(JSONObject().put("path", path).put("class", view.javaClass.simpleName).put("tag", view.tag?.toString())
                    .put("text", view.text.toString().take(160)).put("textSizePx", view.textSize)
                    .put("metricRuns", runs)
                    .put("lineHeightPx", view.lineHeight).put("includeFontPadding", view.includeFontPadding)
                    .put("weight", if (Build.VERSION.SDK_INT >= 28) view.typeface?.weight else null)
                    .put("visibleBoundsPx", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                    .put("paddingPx", JSONArray(listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom))))
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i), "$path/$i")
        }
        visit(activity.window.decorView, "root")
        val metrics = activity.resources.displayMetrics
        File(directory, "${activity.javaClass.simpleName}-typography.json").writeText(JSONObject()
            .put("font", "Noto Sans SC 2.004-H2 fixed source; Chinese only")
            .put("density", metrics.density).put("scaledDensity", metrics.scaledDensity)
            .put("fontScale", activity.resources.configuration.fontScale).put("views", items).put("layout", layout).toString(2))
    }
}

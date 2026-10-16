package io.openhoyi.mobile

import android.app.Activity
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Records actual native typography/spacing next to screenshots, not an ImageGen match claim. */
internal object TaskTypographyEvidence {
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
                val background = view.background?.current as? android.graphics.drawable.GradientDrawable
                layout.put(JSONObject().put("path", path).put("class", view.javaClass.simpleName)
                    .put("tag", view.tag?.toString()).put("widthPx", view.width).put("heightPx", view.height)
                    .put("visibleBoundsPx", JSONArray(listOf(rect.left, rect.top, rect.right, rect.bottom)))
                    .put("paddingPx", JSONArray(listOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)))
                    .put("marginsPx", margins?.let { JSONArray(listOf(it.leftMargin, it.topMargin, it.rightMargin, it.bottomMargin)) })
                    .put("cornerRadiusPx", background?.cornerRadius).put("clickable", view.isClickable)
                    .put("enabled", view.isEnabled))
            }
            if (view is TextView && view.isShown) {
                val rect = Rect(); view.getGlobalVisibleRect(rect)
                items.put(JSONObject().put("path", path).put("class", view.javaClass.simpleName).put("tag", view.tag?.toString())
                    .put("text", view.text.toString().take(160)).put("textSizePx", view.textSize)
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

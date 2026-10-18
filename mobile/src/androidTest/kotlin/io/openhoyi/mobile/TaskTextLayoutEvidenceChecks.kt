package io.openhoyi.mobile

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import org.json.JSONObject

/** Synthetic API regression assertions; detached fixtures are not real-page acceptance. */
internal object TaskTextLayoutEvidenceChecks {
    private var verified = false
    fun verifyOnce(context: Context) {
        if (verified) return
        val empty = TextView(context)
        check(TaskTypographyEvidence.actualTextLayout(empty).getString("status") == "unknown")
        val view = TextView(context).apply {
            text = "第一行\n第二行\n第三行"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 12f)
            setPadding(7, 9, 11, 13)
            gravity = Gravity.BOTTOM
            includeFontPadding = false
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 400, 200)
        view.scrollTo(3, 5)
        view.clipBounds = Rect(1, 2, 390, 190)
        val before = listOf(view.scrollX, view.scrollY, view.width, view.height, view.baseline)
        val result = TaskTypographyEvidence.actualTextLayout(view)
        check(result.getString("status") == "actual_layout_api")
        val lines = result.getJSONArray("lines")
        val layout = requireNotNull(view.layout)
        check(lines.length() == 3)
        check(lines.getJSONObject(1).getInt("startUtf16") == 4)
        check(lines.getJSONObject(0).getInt("visibleEndUtf16") == 3)
        for (index in 0 until lines.length()) {
            val line = lines.getJSONObject(index)
            val bounds = Rect(); val baseline = view.getLineBounds(index, bounds)
            check(line.getInt("startUtf16") == layout.getLineStart(index))
            check(line.getInt("endUtf16") == layout.getLineEnd(index))
            check(line.getInt("visibleEndUtf16") == layout.getLineVisibleEnd(index))
            check(line.getInt("topPx") == layout.getLineTop(index))
            check(line.getInt("bottomPx") == layout.getLineBottom(index))
            check(line.getInt("baselinePx") == layout.getLineBaseline(index))
            check(line.getInt("viewLogicalBaselinePx") == baseline)
            check(line.getJSONArray("viewLogicalLineBoundsPx").getInt(1) == bounds.top)
        }
        check(result.getJSONArray("viewScrollPx").getInt(1) == 5)
        check(result.getJSONArray("viewExplicitClipBoundsPx").getInt(1) == 2)
        check(result.isNull("viewOriginOnScreenPx"))
        check(before == listOf(view.scrollX, view.scrollY, view.width, view.height, view.baseline))
        check(JSONObject(result.toString()).getJSONArray("lines").length() == 3)
        view.text = ""; view.hint = "hint"
        check(!TaskTypographyEvidence.actualTextLayout(view).getBoolean("displayedLayoutConfirmed"))
        val ellipsized = TextView(context).apply {
            text = "这是必须保留省略范围的长文本"
            setTextSize(TypedValue.COMPLEX_UNIT_PX, 16f)
            setSingleLine(true); ellipsize = android.text.TextUtils.TruncateAt.END
        }
        ellipsized.measure(View.MeasureSpec.makeMeasureSpec(50, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        ellipsized.layout(0, 0, 50, 40)
        val ellipsis = TaskTypographyEvidence.actualTextLayout(ellipsized).getJSONArray("lines").getJSONObject(0)
        check(ellipsis.getInt("ellipsisCountUtf16") > 0)
        check(ellipsis.getInt("ellipsisStartUtf16InLine") == ellipsized.layout.getEllipsisStart(0))

        val paint = Paint().apply { textSize = 32f; textScaleX = 1.2f; letterSpacing = .03f }
        val copy = Paint(paint)
        val numeric = TaskTypographyEvidence.numericAdvanceProbe(paint)
        val raw = FloatArray(10)
        val chars = "0123456789".toCharArray()
        val total = copy.getTextRunAdvances(chars, 0, 10, 0, 10, false, raw, 0)
        check(numeric.getDouble("totalAdvancePx").toFloat() == total)
        raw.forEachIndexed { index, value -> check(numeric.getJSONArray("advancesPx").getDouble(index).toFloat() == value) }
        check(paint.textSize == copy.textSize && paint.textScaleX == copy.textScaleX && paint.letterSpacing == copy.letterSpacing)
        check(TaskTypographyEvidence.advanceObservation(FloatArray(10) { 1f }).getBoolean("observedEqualAdvance"))
        val unequal = FloatArray(10) { 1f }.apply { this[9] = java.lang.Math.nextUp(1f) }
        check(!TaskTypographyEvidence.advanceObservation(unequal).getBoolean("observedEqualAdvance"))
        check(TaskTypographyEvidence.advanceObservation(FloatArray(10)).isNull("observedEqualAdvance"))
        check(JSONObject(TaskTypographyEvidence.advanceObservation(FloatArray(10) { Float.NaN }).toString())
            .getString("status") == "unknown")
        verified = true
    }
}

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
    /** Snapshot of the existing native Layout; never creates or independently reshapes one. */
    internal fun actualTextLayout(view: TextView): JSONObject {
        val attached = view.isAttachedToWindow
        val origin = IntArray(2)
        val windowOrigin = IntArray(2)
        val globalClip = Rect()
        val localClip = Rect()
        val globalVisible = attached && view.getGlobalVisibleRect(globalClip)
        val localVisible = attached && view.getLocalVisibleRect(localClip)
        if (attached) { view.getLocationOnScreen(origin); view.getLocationInWindow(windowOrigin) }
        val matrix = FloatArray(9); view.matrix.getValues(matrix)
        fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
        val hintVisible = view.text.isEmpty() && !view.hint.isNullOrEmpty()
        val nativeLayout = view.layout
        val result = JSONObject().put("source", "existing_TextView_layout_and_getLineBounds_APIs")
            .put("viewAttachedToWindow", attached).put("viewLayoutRequested", view.isLayoutRequested)
            .put("viewOriginOnScreenPx", if (attached) JSONArray(origin.toList()) else JSONObject.NULL)
            .put("viewOriginInWindowPx", if (attached) JSONArray(windowOrigin.toList()) else JSONObject.NULL)
            .put("viewOriginStatus", if (attached) "api_reported" else "unknown_detached_view")
            .put("viewScrollPx", JSONArray(listOf(view.scrollX, view.scrollY)))
            .put("viewSizePx", JSONArray(listOf(view.width, view.height)))
            .put("viewMatrix", JSONArray(matrix.toList()))
            .put("viewExplicitClipBoundsPx", view.clipBounds?.let(::rect) ?: JSONObject.NULL)
            .put("explicitClipSemantics", "null_means_no_explicit_View_clip_not_no_ancestor_clipping")
            .put("globalRootVisibleRectPx", if (attached) rect(globalClip) else JSONObject.NULL)
            .put("localVisibleRectPx", if (attached) rect(localClip) else JSONObject.NULL)
            .put("globalVisible", if (attached) globalVisible else JSONObject.NULL)
            .put("localVisible", if (attached) localVisible else JSONObject.NULL)
            .put("coordinateSemantics", "line_fields_are_Layout_local; viewLogical_fields_are_getLineBounds_unscrolled_View_coordinates_not_glyph_ink; origins_scroll_matrix_and_clips_are_separate")
            .put("displayedLayoutConfirmed", nativeLayout != null && attached && !view.isLayoutRequested && !hintVisible)
            .put("displayedLayoutReason", when {
                hintVisible -> "unknown_hint_layout_not_public; records_main_text_layout_only"
                !attached -> "unknown_detached_view_not_a_displayed_window"
                view.isLayoutRequested -> "unknown_pending_layout; records_existing_layout_snapshot"
                else -> "current_main_text_layout_API_snapshot_not_pixel_or_glyph_source_proof"
            })
            .put("screenLineMapping", "not_flattened; preserve_origin_scroll_matrix_and_clip_in_their_API_coordinate_spaces")
        if (nativeLayout == null) return result.put("status", "unknown").put("reason", "TextView_layout_not_built").put("lines", JSONArray())
        val lines = JSONArray()
        for (index in 0 until nativeLayout.lineCount) {
            val bounds = Rect()
            val baseline = view.getLineBounds(index, bounds)
            lines.put(JSONObject().put("lineIndex", index)
                .put("startUtf16", nativeLayout.getLineStart(index)).put("endUtf16", nativeLayout.getLineEnd(index))
                .put("visibleEndUtf16", nativeLayout.getLineVisibleEnd(index))
                .put("topPx", nativeLayout.getLineTop(index)).put("bottomPx", nativeLayout.getLineBottom(index))
                .put("baselinePx", nativeLayout.getLineBaseline(index))
                .put("ascentPx", nativeLayout.getLineAscent(index)).put("descentPx", nativeLayout.getLineDescent(index))
                .put("leftPx", nativeLayout.getLineLeft(index)).put("rightPx", nativeLayout.getLineRight(index))
                .put("widthPx", nativeLayout.getLineWidth(index)).put("maxPx", nativeLayout.getLineMax(index))
                .put("paragraphDirection", nativeLayout.getParagraphDirection(index)).put("containsTab", nativeLayout.getLineContainsTab(index))
                .put("ellipsisStartUtf16InLine", nativeLayout.getEllipsisStart(index)).put("ellipsisCountUtf16", nativeLayout.getEllipsisCount(index))
                .put("viewLogicalLineBoundsPx", rect(bounds)).put("viewLogicalBaselinePx", baseline))
        }
        return result.put("status", "actual_layout_api").put("layoutClass", nativeLayout.javaClass.name)
            .put("textSource", "Layout.text; offsets_are_UTF16_not_raw_TextView_text_or_codepoint_indices")
            .put("layoutTextLengthUtf16", nativeLayout.text.length).put("lineCount", nativeLayout.lineCount)
            .put("layoutHeightPx", nativeLayout.height).put("layoutWidthPx", nativeLayout.width)
            .put("layoutTopPaddingPx", nativeLayout.topPadding).put("layoutBottomPaddingPx", nativeLayout.bottomPadding)
            .put("lineBoxSemantics", "native_layout_metrics_including_spacing_not_Paint_glyph_ink_bounds")
            .put("glyphInkBounds", JSONObject().put("status", "unknown").put("reason", "not_collected_from_actual_TextView_renderer"))
            .put("lines", lines)
    }

    /** Exact raw float comparison, not a tolerance or an OpenType feature-activation claim. */
    internal fun advanceObservation(advances: FloatArray): JSONObject {
        val valid = advances.size == 10 && advances.all { it.isFinite() && it > 0f }
        return JSONObject().put("status", if (valid) "measured" else "unknown")
            .put("reason", if (valid) "raw_positive_finite_ASCII_digit_advances" else "requires_ten_positive_finite_advances")
            .put("advancesPx", JSONArray(advances.map { if (it.isFinite()) it else JSONObject.NULL }))
            .put("observedEqualAdvance", if (valid) advances.all { it == advances[0] } else JSONObject.NULL)
            .put("spreadPx", if (valid) advances.maxOrNull()!! - advances.minOrNull()!! else JSONObject.NULL)
            .put("comparison", "exact_returned_float_equality_no_rounding_no_tolerance")
            .put("tnumActivation", JSONObject().put("status", "unknown").put("reason", "equal_observed_advances_do_not_prove_tnum_enabled"))
    }

    internal fun numericAdvanceProbe(source: Paint): JSONObject {
        val paint = Paint(source)
        val text = "0123456789".toCharArray()
        val advances = FloatArray(text.size) { Float.NaN }
        val total = if (paint.textSize.isFinite() && paint.textSize > 0f)
            paint.getTextRunAdvances(text, 0, text.size, 0, text.size, false, advances, 0) else Float.NaN
        return advanceObservation(advances).put("probeText", String(text)).put("direction", "LTR")
            .put("contextStartUtf16", 0).put("contextCountUtf16", text.size)
            .put("totalAdvancePx", if (total.isFinite()) total else JSONObject.NULL)
            .put("semantics", "independent_ASCII_probe_with_copied_current_Paint_not_actual_displayed_text_glyph_advances")
            .put("paint", JSONObject().put("textSizePx", paint.textSize.takeIf { it.isFinite() } ?: JSONObject.NULL)
                .put("textScaleX", paint.textScaleX).put("textSkewX", paint.textSkewX).put("letterSpacingEm", paint.letterSpacing)
                .put("fontFeatureSettings", paint.fontFeatureSettings ?: JSONObject.NULL)
                .put("fontVariationSettings", paint.fontVariationSettings ?: JSONObject.NULL)
                .put("textLocales", paint.textLocales.toLanguageTags()).put("flags", paint.flags)
                .put("weight", if (Build.VERSION.SDK_INT >= 28) paint.typeface?.weight ?: JSONObject.NULL else JSONObject.NULL)
                .put("fakeBold", paint.isFakeBoldText))
    }

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
        TaskTextLayoutEvidenceChecks.verifyOnce(activity)
        TaskVisualColorEvidenceChecks.verifyOnce(activity)
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
                    .put("state", TaskVisualColorEvidence.state(view))
                    .put("backgroundEvidence", TaskVisualColorEvidence.background(view.background, view.resources))
                    .put("foregroundEvidence", TaskVisualColorEvidence.background(view.foreground, view.resources))
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
                            .put("numericAdvanceProbe", numericAdvanceProbe(paint))
                            .put("spans", JSONArray(spans.map { it.javaClass.simpleName })))
                        start = end
                    }
                }
                items.put(JSONObject().put("path", path).put("class", view.javaClass.simpleName).put("tag", view.tag?.toString())
                    .put("text", view.text.toString().take(160)).put("textSizePx", view.textSize)
                    .put("colors", TaskVisualColorEvidence.text(view))
                    .put("actualLayout", actualTextLayout(view))
                    .put("numericAdvanceProbe", numericAdvanceProbe(view.paint))
                    .put("baselinePx", view.baseline).put("letterSpacingEm", view.letterSpacing)
                    .put("textScaleX", view.textScaleX).put("lineSpacingExtraPx", view.lineSpacingExtra)
                    .put("lineSpacingMultiplier", view.lineSpacingMultiplier)
                    .put("baselineSemantics", "baseline_relative_to_untransformed_view_top")
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
            .put("visualEvidenceVersion", 1)
            .put("visualEvidenceScope", "TextView_and_static_drawable_evidence_not_full_visual_acceptance")
            .put("canvasPaintCoverage", "not_collected")
            .put("screenCompositeColorCoverage", "requires_corresponding_PNG_review")
            .put("font", "Noto Sans SC 2.004-H2 fixed source; Chinese only")
            .put("density", metrics.density).put("scaledDensity", metrics.scaledDensity)
            .put("fontScale", activity.resources.configuration.fontScale).put("views", items).put("layout", layout).toString(2))
    }
}

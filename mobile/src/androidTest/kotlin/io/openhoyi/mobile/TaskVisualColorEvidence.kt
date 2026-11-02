package io.openhoyi.mobile

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.view.View
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Read-only evidence. ARGB here is NOT the final screen-composited pixel color. */
internal object TaskVisualColorEvidence {
    private const val MAX_RASTER_PIXELS = 262144L

    private fun argb(color: Int) = String.format(Locale.ROOT, "#%08X", color)
    private fun unknown(reason: String) = JSONObject().put("status", "unknown").put("reason", reason)

    fun state(view: View): JSONObject = JSONObject()
        .put("enabled", view.isEnabled).put("selected", view.isSelected)
        .put("activated", view.isActivated).put("focused", view.isFocused)
        .put("pressed", view.isPressed).put("drawableState", JSONArray(view.drawableState.toList()))
        .put("viewAlpha", view.alpha)

    fun text(view: TextView): JSONObject {
        val runs = JSONArray()
        val styled = view.text as? Spanned
        if (styled != null) {
            var start = 0
            while (start < styled.length) {
                val end = styled.nextSpanTransition(start, styled.length, CharacterStyle::class.java)
                val paint = TextPaint(view.paint).apply {
                    color = view.currentTextColor
                    drawableState = view.drawableState
                }
                val spans = styled.getSpans(start, end, CharacterStyle::class.java)
                // A style can be custom/mutable. Calling it again is not proof of what it drew earlier.
                val supported = spans.all { it.javaClass.name in KNOWN_DRAW_SPANS }
                val run = JSONObject().put("start", start).put("end", end)
                    .put("spans", JSONArray(spans.map { it.javaClass.name }))
                if (supported) {
                    spans.forEach { it.updateDrawState(paint) }
                    run.put("status", "resolved_draw_state").put("colorArgb", argb(paint.color))
                        .put("backgroundColorArgb", argb(paint.bgColor))
                        .put("paintAlpha", paint.alpha)
                } else run.put("status", "unknown").put("reason", "custom_span_draw_state_not_replayed")
                runs.put(run)
                start = end
            }
        }
        return JSONObject().put("resolvedTextColorArgb", argb(view.currentTextColor))
            .put("resolvedHintColorArgb", if (view.hint != null) argb(view.currentHintTextColor) else JSONObject.NULL)
            .put("hintVisible", view.text.isEmpty() && !view.hint.isNullOrEmpty())
            .put("colorSemantics", "TextView_current_state_and_supported_span_paint_not_screen_composite")
            .put("colorRuns", runs)
    }

    private val KNOWN_DRAW_SPANS = setOf(
        "android.text.style.ForegroundColorSpan", "android.text.style.BackgroundColorSpan",
        "android.text.style.StyleSpan", "android.text.style.TypefaceSpan",
        "android.text.style.AbsoluteSizeSpan", "android.text.style.RelativeSizeSpan",
        "android.text.style.ScaleXSpan", "android.text.style.UnderlineSpan",
        "android.text.style.StrikethroughSpan", "android.text.style.SuperscriptSpan",
        "android.text.style.SubscriptSpan", "android.text.style.TextAppearanceSpan"
    )

    fun background(drawable: Drawable?, resources: Resources): JSONObject = background(drawable, resources, 0)

    private fun background(drawable: Drawable?, resources: Resources, depth: Int): JSONObject {
        if (drawable == null) return JSONObject().put("status", "none")
        if (depth >= 16) return unknown("drawable_nesting_limit")
        val result = JSONObject().put("class", drawable.javaClass.name)
            .put("state", JSONArray(drawable.state.toList())).put("alpha", drawable.alpha)
            .put("level", drawable.level).put("visible", drawable.isVisible)
            .put("boundsPx", drawable.bounds.let { JSONArray(listOf(it.left, it.top, it.right, it.bottom)) })
            .put("semantics", "isolated_drawable_on_transparent_bitmap_not_screen_composite")
        when {
            drawable is RippleDrawable -> {
                result.put("raster", unknown("ripple_animation_and_hardware_effect_not_reconstructed"))
                result.put("layers", layers(drawable, resources, depth))
            }
            drawable is StateListDrawable -> {
                val current = drawable.current
                result.put("raster", unknown("selector_color_is_in_current_child"))
                result.put("current", if (current !== drawable) background(current, resources, depth + 1)
                    else unknown("selector_has_no_current_child"))
            }
            drawable.javaClass == ColorDrawable::class.java -> result.put("raster", raster(drawable, resources))
            drawable.javaClass == GradientDrawable::class.java -> {
                val shape = drawable as GradientDrawable
                result.put("shape", shape.shape).put("gradientType", shape.gradientType)
                result.put("configuredStateFillArgb", shape.color?.let { argb(it.getColorForState(shape.state, it.defaultColor)) }
                    ?: JSONObject.NULL)
                result.put("configuredFillSemantics", "state_resolved_configuration_before_tint_alpha_stroke_not_measured_output")
                result.put("stroke", unknown("no_public_stroke_getter_cannot_separate_border_from_rendered_pixels"))
                result.put("raster", raster(drawable, resources))
            }
            drawable is LayerDrawable -> {
                result.put("raster", unknown("layer_compositing_not_reconstructed"))
                result.put("layers", layers(drawable, resources, depth))
            }
            drawable is DrawableWrapper -> {
                result.put("raster", unknown("wrapper_transform_not_reconstructed"))
                result.put("child", background(drawable.drawable, resources, depth + 1))
            }
            else -> result.put("raster", unknown("unsupported_or_custom_drawable"))
        }
        return result
    }

    private fun layers(drawable: LayerDrawable, resources: Resources, depth: Int): JSONArray = JSONArray().apply {
        for (index in 0 until drawable.numberOfLayers) {
            val id = drawable.getId(index)
            put(JSONObject().put("index", index).put("id", id).put("mask", id == android.R.id.mask)
                .put("drawable", background(drawable.getDrawable(index), resources, depth + 1)))
        }
    }

    private fun raster(drawable: Drawable, resources: Resources): JSONObject {
        val bounds = Rect(drawable.bounds)
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return unknown("empty_bounds")
        if (width.toLong() * height > MAX_RASTER_PIXELS) return unknown("original_size_exceeds_262144_pixel_limit")
        val constantState = drawable.constantState ?: return unknown("no_constant_state_for_read_only_clone")
        return try {
            val copy = constantState.newDrawable(resources).mutate()
            if (copy === drawable || copy.javaClass != drawable.javaClass) return unknown("clone_not_independent_or_same_type")
            copy.bounds = bounds
            copy.state = drawable.state.clone()
            copy.level = drawable.level
            copy.alpha = drawable.alpha
            copy.colorFilter = drawable.colorFilter
            copy.layoutDirection = drawable.layoutDirection
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
                copy.draw(canvas)
                val first = bitmap.getPixel(0, 0)
                val row = IntArray(width)
                var uniform = true
                var checked = 0
                scan@ for (y in 0 until height) {
                    bitmap.getPixels(row, 0, width, 0, y, width, 1)
                    for (pixel in row) {
                        checked++
                        if (pixel != first) { uniform = false; break@scan }
                    }
                }
                val samples = JSONArray()
                for ((x, y) in listOf(0 to 0, width / 2 to height / 2,
                    width / 2 to 0, width - 1 to height - 1)) {
                    samples.put(JSONObject().put("x", x).put("y", y).put("argb", argb(bitmap.getPixel(x, y))))
                }
                JSONObject().put("status", if (uniform) "uniform" else "non_uniform")
                    .put("coverage", if (uniform) "all_original_size_pixels" else "non_uniform_witness_and_samples")
                    .put("checkedPixelCount", checked).put("totalPixelCount", width.toLong() * height)
                    .put("uniformColorArgb", if (uniform) argb(first) else JSONObject.NULL)
                    .put("samples", samples)
                    .put("semantics", "software_clone_ARGB8888_transparent_base_no_parent_alpha_or_compositing")
            } finally { bitmap.recycle() }
        } catch (error: RuntimeException) {
            unknown("clone_or_software_render_failed:${error.javaClass.simpleName}")
        }
    }
}

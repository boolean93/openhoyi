package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import org.json.JSONObject

/** Synthetic Android checks only; no BLE, activities, preferences, or installed-device operations. */
internal object TaskVisualColorEvidenceChecks {
    private var verified = false

    fun verifyOnce(context: Context) {
        if (verified) return
        val states = ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(Color.GRAY, Color.GREEN, Color.RED))
        val text = TextView(context).apply {
            setTextColor(states)
            setHintTextColor(states)
            hint = "hint"
        }
        fun colors(expected: String) {
            val value = TaskVisualColorEvidence.text(text)
            check(value.getString("resolvedTextColorArgb") == expected)
            check(value.getString("resolvedHintColorArgb") == expected)
            check(JSONObject(value.toString()).getBoolean("hintVisible"))
        }
        colors("#FFFF0000")
        text.isSelected = true
        colors("#FF00FF00")
        text.isEnabled = false
        colors("#FF888888")
        val flags = TaskVisualColorEvidence.state(text)
        check(!flags.getBoolean("enabled") && flags.getBoolean("selected"))
        val styled = SpannableString("AB").apply {
            setSpan(ForegroundColorSpan(Color.BLUE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(object : CharacterStyle() {
                override fun updateDrawState(paint: TextPaint) = error("Custom span must not be replayed")
            }, 1, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text.text = styled
        val runs = TaskVisualColorEvidence.text(text).getJSONArray("colorRuns")
        check(runs.getJSONObject(0).getString("colorArgb") == "#FF0000FF")
        check(runs.getJSONObject(1).getString("status") == "unknown")
        text.hint = null
        check(TaskVisualColorEvidence.text(text).isNull("resolvedHintColorArgb"))

        fun evidence(drawable: Drawable) = TaskVisualColorEvidence.background(drawable, context.resources)
        val selector = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_selected), ColorDrawable(Color.GREEN))
            addState(intArrayOf(), ColorDrawable(Color.RED))
            bounds = Rect(3, 4, 19, 20)
        }
        check(evidence(selector).getJSONObject("current").getJSONObject("raster")
            .getString("uniformColorArgb") == "#FFFF0000")
        selector.state = intArrayOf(android.R.attr.state_selected)
        val originalBounds = Rect(selector.bounds)
        val originalState = selector.state.clone()
        check(evidence(selector).getJSONObject("current").getJSONObject("raster")
            .getString("uniformColorArgb") == "#FF00FF00")
        check(selector.bounds == originalBounds && selector.state.contentEquals(originalState))

        val solid = GradientDrawable().apply { setColor(states); setBounds(0, 0, 20, 20) }
        solid.state = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_selected)
        val uniform = evidence(solid).getJSONObject("raster")
        check(uniform.getString("uniformColorArgb") == "#FF00FF00")
        check(uniform.getInt("checkedPixelCount") == uniform.getInt("totalPixelCount"))
        solid.state = intArrayOf()
        check(evidence(solid).getJSONObject("raster").getString("uniformColorArgb") == "#FF888888")
        solid.setStroke(2, Color.BLUE)
        val border = evidence(solid)
        check(border.getJSONObject("raster").getString("status") == "non_uniform")
        check(border.getJSONObject("stroke").getString("status") == "unknown")
        val gradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.RED, Color.BLUE)).apply { setBounds(0, 0, 20, 20) }
        check(evidence(gradient).getJSONObject("raster").getString("status") == "non_uniform")

        val tinted = ColorDrawable(Color.RED).apply { setTint(Color.BLUE); setBounds(0, 0, 8, 8) }
        check(evidence(tinted).getJSONObject("raster").getString("uniformColorArgb") == "#FF0000FF")
        val translucent = ColorDrawable(Color.RED).apply { alpha = 128; setBounds(0, 0, 8, 8) }
        check(evidence(translucent).getJSONObject("raster").getString("uniformColorArgb") == "#80FF0000")
        val ripple = RippleDrawable(states, ColorDrawable(Color.WHITE), null).apply { setBounds(0, 0, 20, 20) }
        check(evidence(ripple).getJSONObject("raster").getString("status") == "unknown")
        val huge = ColorDrawable(Color.RED).apply { setBounds(0, 0, 4096, 4096) }
        check(evidence(huge).getJSONObject("raster").getString("status") == "unknown")
        val custom = object : Drawable() {
            override fun draw(canvas: Canvas) = Unit
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(colorFilter: ColorFilter?) = Unit
            @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSPARENT
        }
        check(evidence(custom).getJSONObject("raster").getString("status") == "unknown")
        check(JSONObject(evidence(null, context).toString()).getString("status") == "none")
        verified = true
    }

    private fun evidence(drawable: Drawable?, context: Context) =
        TaskVisualColorEvidence.background(drawable, context.resources)
}

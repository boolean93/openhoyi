package io.openhoyi.mobile

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.widget.Button
import java.util.Locale

/** Draws within the existing action bounds, leaving the chart its original space. */
internal object AutoPreheatButton {
    fun bind(button: Button, model: AutoPreheatPresentation.Model, current: Int?, targetC: Int?, message: String?) {
        val context = button.context
        val resource = when(model.phase) {
            AutoPreheatPresentation.Phase.HIDDEN -> null
            AutoPreheatPresentation.Phase.SENDING -> R.string.auto_preheat_sending
            AutoPreheatPresentation.Phase.WAITING -> R.string.auto_preheat_waiting
            AutoPreheatPresentation.Phase.COOLING -> R.string.auto_preheat_cooling
            AutoPreheatPresentation.Phase.TEMPERATURE_MISSING -> R.string.auto_preheat_temperature_missing
            AutoPreheatPresentation.Phase.FAILED -> R.string.auto_preheat_failed
            AutoPreheatPresentation.Phase.UNKNOWN -> R.string.auto_preheat_unknown
            AutoPreheatPresentation.Phase.DISCONNECTED -> R.string.auto_preheat_disconnected
            AutoPreheatPresentation.Phase.CANCELLING -> R.string.auto_preheat_cancelling
            AutoPreheatPresentation.Phase.CANCEL_WRITTEN -> R.string.auto_preheat_cancel_written
            AutoPreheatPresentation.Phase.TARGET_CHANGED -> R.string.auto_preheat_target_changed
            AutoPreheatPresentation.Phase.NOT_READY -> R.string.auto_preheat_not_ready
        }
        val title = if (!message.isNullOrBlank()) message else if(model.phase == AutoPreheatPresentation.Phase.WAITING)
            context.getString(R.string.auto_preheat_heating)
            else resource?.let(context::getString) ?: context.getString(R.string.extraction_start)
        val percentage = model.progress?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
        val temperature = if(message.isNullOrBlank() && resource != null && current != null && targetC != null)
            String.format(Locale.getDefault(), "%.1f / %d °C", current / 100.0, targetC) else ""
        val compact = model.phase in setOf(AutoPreheatPresentation.Phase.WAITING, AutoPreheatPresentation.Phase.COOLING)
        val text = if(compact && temperature.isNotEmpty()) "$title  |  $temperature"
            else title + percentage + if(temperature.isNotEmpty()) "\n$temperature" else ""
        button.text = SpannableString(text).apply {
            if(temperature.isNotEmpty()) setSpan(RelativeSizeSpan(if(compact) .78f else .68f),
                if(compact) text.indexOf('|') + 1 else text.indexOf('\n') + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        button.foreground = model.progress?.takeIf { message.isNullOrBlank() }?.let { Line(it, button.resources.displayMetrics.density) }
        button.contentDescription = listOf(text, resource?.let(context::getString), message).filterNotNull().filter(String::isNotBlank).joinToString(" · ")
    }
    private class Line(val progress: Float, val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF3DDBA0.toInt(); strokeWidth = 2 * density; strokeCap = Paint.Cap.ROUND }
        override fun draw(canvas: Canvas) {
            val left = bounds.left + 12 * density
            val right = bounds.right - 12 * density
            val y = bounds.top + 6 * density
            paint.alpha = 55
            canvas.drawLine(left, y, right, y, paint)
            paint.alpha = 230
            canvas.drawLine(left, y, left + (right - left) * progress, y, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

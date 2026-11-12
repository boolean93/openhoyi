package io.openhoyi.mobile

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

/** A fixed-width, quiet readout, separate from the unchanged four-metric strip. */
internal class ExtractionScaleReadoutView(context: Context, visual: ExtractionHistoryVisualStyle) : LinearLayout(context) {
    private val style = visual.palette
    private val heading = style.text(context.getString(R.string.extraction_scale_raw), TaskProductVisualStyle.Type(11, 400, 16), secondary = true)
    private val reading = style.text("— g", TaskProductVisualStyle.Type(22, 500, 28))
    private val detail = style.text("", TaskProductVisualStyle.Type(11, 400, 16), secondary = true)
    private val target = style.text("", TaskProductVisualStyle.Type(11, 400, 16), secondary = true)
    init {
        orientation = VERTICAL; gravity = Gravity.CENTER
        tag = "product/extraction/scale-readout"
        reading.tag = "product/extraction/scale-readout/value"
        reading.typeface = android.graphics.Typeface.create("sans-serif-monospace", android.graphics.Typeface.NORMAL)
        // Preserve the complete signed reading within its stable slot at larger font scales.
        reading.setAutoSizeTextTypeUniformWithConfiguration(12, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        val primary = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = HORIZONTAL }
        val secondary = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = HORIZONTAL }
        listOf(heading, reading).forEach {
            it.gravity = Gravity.CENTER; it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.END
            primary.addView(it, LayoutParams(if (it === reading) (112 * resources.displayMetrics.density).toInt() else 0, -2, if (it === heading) 1f else 0f).apply { marginEnd = (4 * resources.displayMetrics.density).toInt() })
        }
        listOf(detail, target).forEach {
            it.gravity = Gravity.CENTER; it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.END
            secondary.addView(it, LayoutParams(-2, -2).apply { marginEnd = (4 * resources.displayMetrics.density).toInt() })
        }
        addView(primary, LayoutParams(-1, -2))
        addView(secondary, LayoutParams(-1, (18 * resources.displayMetrics.density).toInt()))
    }
    fun present(value: ExtractionScaleReadout.Value) {
        reading.text = "${value.number} g"
        detail.text = when (value.reason) {
            ExtractionScaleReadout.Reason.NO_SCALE -> context.getString(R.string.extraction_scale_no_data)
            ExtractionScaleReadout.Reason.DISCONNECTED -> context.getString(R.string.extraction_scale_disconnected)
            ExtractionScaleReadout.Reason.UNAVAILABLE -> context.getString(R.string.extraction_scale_unavailable)
            ExtractionScaleReadout.Reason.STALE -> context.getString(R.string.extraction_scale_stale)
            ExtractionScaleReadout.Reason.END_UNAVAILABLE -> context.getString(R.string.extraction_scale_end_unavailable)
            ExtractionScaleReadout.Reason.UNKNOWN -> context.getString(R.string.extraction_scale_unknown)
            null -> if (value.ended) context.getString(R.string.extraction_scale_ended) else ""
        }
        detail.visibility = if (detail.text.isEmpty()) GONE else VISIBLE
        target.text = value.targetHundredthsGram?.let {
            context.getString(R.string.extraction_scale_target, java.lang.String.format(java.util.Locale.ROOT, "%.1f", it / 100.0))
        } ?: ""
        target.visibility = if (target.text.isEmpty() || detail.text.isNotEmpty()) GONE else VISIBLE
        contentDescription = listOf(heading.text, reading.text, detail.text, target.text).filter { it.isNotEmpty() }.joinToString(" · ")
    }
}

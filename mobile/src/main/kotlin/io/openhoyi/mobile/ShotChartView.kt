package io.openhoyi.mobile

import android.content.Context
import android.widget.FrameLayout

/** Historical samples use the same physical units, independent ranges and gaps as the live plot. */
class ShotChartView(context: Context) : FrameLayout(context) {
    private val chart = ExtractionOverlayView(context)
    private var revision = 0L

    var points: List<ShotPoint> = emptyList()
        set(value) {
            field = value
            // A history selection is a complete source, not an expanding live-cup range.
            chart.traceIdentity = "history-${++revision}"
            chart.points = value
        }

    init { addView(chart, LayoutParams(-1, -1)) }
}

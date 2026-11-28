package io.openhoyi.mobile

import android.os.Bundle
import android.widget.LinearLayout

/** Historical chart is decoded from bounded local samples off the UI thread. */
class HistoryDetailActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onStart() { super.onStart(); render() }
    private fun render() {
        val visual = ExtractionHistoryVisualStyle(this)
        val body = visual.palette.page(this, if (BuildConfig.MOCK_MODE) getString(R.string.history_detail_mock_title) else getString(R.string.history_detail_title))
        // This page alone tightens shell whitespace; shared/live page chrome remains unchanged.
        (body.parent as? LinearLayout)?.setPadding(dp(16), dp(4), dp(16), dp(4))
        val header = (body.tag as BeanPageRegions).header
        header.protectPhotoSection(visual.palette)
        (header.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(4)
        val comparisonHost = header.takeIf {
            resources.configuration.screenWidthDp >= 1000 && resources.configuration.fontScale <= 1.1f
        }
        val shotId = intent.getStringExtra("shotId")
        renderHistoryDetail(body, shotId, standalone = true, comparisonHost = comparisonHost, visual = visual)
        val hasCurve = body.findViewWithTag<ShotChartView>("product/history/chart") != null
        if (hasCurve && shotId != null) {
            val tools = header.findViewWithTag<android.view.View>("task-tools-host")
            header.addView(historyAiReviewButton(visual.palette, shotId),
                header.indexOfChild(tools), LinearLayout.LayoutParams(-2, -2))
        }
    }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}

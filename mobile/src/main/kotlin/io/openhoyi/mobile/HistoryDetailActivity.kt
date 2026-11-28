package io.openhoyi.mobile

import android.widget.LinearLayout

/** Historical chart is decoded from bounded local samples off the UI thread. */
class HistoryDetailActivity : ThemedActivity() {
    override fun onStart() {
        super.onStart()
        val visual = ExtractionHistoryVisualStyle(this)
        val body = visual.palette.page(this, if (BuildConfig.MOCK_MODE) getString(R.string.history_detail_mock_title) else getString(R.string.history_detail_title))
        // This page alone tightens shell whitespace; shared/live page chrome remains unchanged.
        (body.parent as? LinearLayout)?.setPadding(dp(16), dp(4), dp(16), dp(4))
        val header = (body.tag as BeanPageRegions).header
        header.protectPhotoSection(visual.palette)
        (header.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(4)
        renderHistoryDetail(body, intent.getStringExtra("shotId"), standalone = true, visual = visual)
    }

    private fun dp(value: Int) = HoyiUi.dp(this, value)
}

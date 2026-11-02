package io.openhoyi.mobile

import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout

/** Historical chart is decoded from bounded local samples off the UI thread. */
class HistoryDetailActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onStart() { super.onStart(); render() }
    private fun render() {
        val visual = ExtractionHistoryVisualStyle(this)
        val body = visual.palette.page(this, if (BuildConfig.MOCK_MODE) getString(R.string.history_detail_mock_title) else getString(R.string.history_detail_title))
        val header = (body.tag as BeanPageRegions).header
        val comparisonHost = header.takeIf {
            resources.configuration.screenWidthDp >= 1000 && resources.configuration.fontScale <= 1.1f
        }
        renderHistoryDetail(body, intent.getStringExtra("shotId"), standalone = true, comparisonHost = comparisonHost, visual = visual)
    }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}

package io.openhoyi.mobile

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LegacyHistoryDetailActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(bars[0], bars[1], bars[2], bars[3]); insets
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        HoyiUi.header(this, body, getString(R.string.legacy_history_detail_title), getString(R.string.legacy_history_readonly), back = true)
        val id = intent.getStringExtra("legacyId")
        val status = TextView(this).apply {
            text = getString(R.string.legacy_history_detail_loading); textSize = 16f; setTextColor(getColor(R.color.mobile_text))
            body.addView(this)
        }
        Thread({
            val result = runCatching { (application as MobileApplication).legacyHistory.list().firstOrNull { it.id == id } }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val shot = result.getOrNull()
                if (shot == null) { status.text = getString(R.string.legacy_history_unavailable); return@runOnUiThread }
                body.removeView(status)
                showShot(body, shot)
            }
        }, "legacy-history-detail").start()
    }

    private fun showShot(body: LinearLayout, shot: LegacyShot) {
        val summary = HoyiUi.card(this, body, getString(R.string.legacy_history_record))
        text(summary, getString(R.string.legacy_history_summary, shot.profileName.ifBlank { getString(R.string.legacy_history_unnamed) }, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(shot.createdAtMs)), shot.durationSec.toString()), 16)
        val chart = HoyiUi.card(this, body, getString(R.string.curve_view_legacy))
        chart.addView(LegacyShotChartView(this).apply { points = shot.points },
            LinearLayout.LayoutParams(-1, dp(300)))
        text(chart, getString(R.string.legacy_history_chart_disclaimer), 14)
    }

    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(6))
        })
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}

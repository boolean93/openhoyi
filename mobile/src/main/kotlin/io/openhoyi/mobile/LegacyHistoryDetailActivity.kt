package io.openhoyi.mobile

import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
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
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, LegacyHistoryDetailActivity::class.java)
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
                if (shot == null) {
                    status.text = getString(if (result.isFailure) R.string.legacy_history_failed_message else R.string.legacy_history_unavailable)
                    return@runOnUiThread
                }
                body.removeView(status)
                showShot(body, shot)
            }
        }, "legacy-history-detail").start()
    }

    private fun showShot(body: LinearLayout, shot: LegacyShot) {
        val summaryText = getString(R.string.legacy_history_summary,
            shot.profileName.ifBlank { getString(R.string.legacy_history_unnamed) },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(shot.createdAtMs)), shot.durationSec.toString())
        val disclaimer = getString(R.string.legacy_history_chart_disclaimer)
        val workspace = TaskWorkspaceLayout(this)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        workspace.onPrimarySpaceAvailable = { _, budget -> minOf(budget, dp(220)) }
        text(workspace.secondaryBody, summaryText, 16)
        text(workspace.secondaryBody, disclaimer, 14)
        val chart = HoyiUi.card(this, workspace.primaryBody, getString(R.string.curve_view_legacy)).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        chart.addView(LegacyShotChartView(this).apply { points = shot.points },
            LinearLayout.LayoutParams(-1, 0, 1f))
        // One explicit full-details entry preserves both texts without duplicate
        // overview buttons taking height from the historical plot.
        HoyiUi.button(this, body, getString(R.string.extraction_overview)) {
            android.app.AlertDialog.Builder(this).setTitle(getString(R.string.legacy_history_detail_title))
                .setMessage(summaryText + "\n\n" + disclaimer)
                .setPositiveButton(android.R.string.ok, null).show()
        }
    }

    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            if (bold) typeface = TaskTypography.face(context, 700)
            setPadding(0, dp(6), 0, dp(6))
        })
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
}

package io.openhoyi.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.openhoyi.session.StopReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Historical chart is decoded from bounded local samples off the UI thread. */
class HistoryDetailActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onStart() { super.onStart(); render() }
    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val area = if (Build.VERSION.SDK_INT >= 30) {
                    val a = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(a.left, a.top, a.right, a.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0], area[1], area[2], area[3]); insets
            }
        }
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        HoyiUi.header(this, body, if (BuildConfig.MOCK_MODE) getString(R.string.history_detail_mock_title) else getString(R.string.history_detail_title), back = true)
        val shotId = intent.getStringExtra("shotId")
        val app = application as MobileApplication
        val permanent = shotId?.let { app.journalResult.getOrNull()?.find(it) }
        val entry = runCatching { app.history.entries.firstOrNull { it.id == shotId } }.getOrNull()
            ?: permanent?.observation?.historySummary()
        if (entry == null) {
            HoyiUi.label(this, HoyiUi.card(this, body), getString(R.string.history_missing), 17)
            return
        }
        val curve = if (entry.curveId == "manual") getString(R.string.extraction_manual_label) else
            runCatching { app.curves.find(entry.curveId)?.name }.getOrNull() ?: entry.curveId
        val summary = HoyiUi.card(this, body, curve)
        HoyiUi.label(this, summary, status(entry.status), 20, true).apply {
            setTextColor(getColor(if (entry.status == ShotHistory.Status.UNKNOWN) R.color.mobile_danger else R.color.mobile_accent))
        }
        HoyiUi.label(this, summary, getString(R.string.history_started_at, date(entry.startedAtMs)), 14, muted = true).apply {
            setPadding(0, dp(8), 0, 0)
        }
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summary.addView(metrics, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        fun metric(label: String, value: String): TextView {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = HoyiUi.shape(this@HistoryDetailActivity, R.color.mobile_accent_soft, 12)
            }
            metrics.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            HoyiUi.label(this, box, label, 13, muted = true)
            return HoyiUi.label(this, box, value, 21, true).apply { setPadding(0, dp(7), 0, 0) }
        }
        metric(getString(R.string.history_duration), entry.elapsedMs?.let { getString(R.string.history_duration_value, "%.1f".format(Locale.ROOT, it / 1000.0)) } ?: "—")
        metric(getString(R.string.history_end_scale), entry.weightHundredthsGram?.let {
            "%.2f g".format(Locale.ROOT, it / 100.0)
        } ?: "—")
        if (entry.weightHundredthsGram != null) HoyiUi.label(this, summary,
            getString(R.string.history_scale_disclaimer), 13, muted = true).apply {
            setPadding(0, dp(8), 0, 0)
        }
        entry.slot?.takeIf { it in 1..5 }?.let {
            HoyiUi.label(this, summary, getString(R.string.history_slot, it.toString()), 14).apply { setPadding(0, dp(10), 0, 0) }
        }
        if (permanent != null) {
            journalNotes(HoyiUi.card(this, body, getString(R.string.journal_notes_title)), permanent.notes)
            HoyiUi.button(this, body, getString(R.string.journal_review), primary = true) {
                startActivity(Intent(this, BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.id))
            }
            HoyiUi.button(this, body, getString(R.string.journal_compare)) {
                startActivity(Intent(this, BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, entry.id))
            }
        } else {
            HoyiUi.label(this, HoyiUi.card(this, body), getString(if (app.journalResult.isFailure) R.string.journal_unavailable else R.string.journal_migrate_hint), 15)
        }
        val chartCard = HoyiUi.card(this, body, getString(R.string.extraction_chart))
        val chart = ShotChartView(this)
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, dp(300)))
        val chartStatus = HoyiUi.label(this, chartCard, getString(R.string.history_loading_samples), 13, muted = true)
        val outcome = HoyiUi.card(this, body, getString(R.string.history_outcome_title))
        HoyiUi.label(this, outcome,
            entry.reason?.let(::reasonLabel) ?: getString(R.string.history_no_reason), 16, true)
        entry.endedAtMs?.let {
            HoyiUi.label(this, outcome, getString(R.string.history_ended_at, date(it)), 14, muted = true).apply {
                setPadding(0, dp(8), 0, 0)
            }
        }
        if (entry.status == ShotHistory.Status.UNKNOWN) HoyiUi.label(this, outcome,
            getString(R.string.history_unknown_warning), 15, true).apply {
            setPadding(0, dp(12), 0, 0)
            setTextColor(getColor(R.color.mobile_danger))
        }
        Thread({
            val points = runCatching { app.samples.load(entry.id) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                points.onSuccess {
                    chart.points = it
                    chartStatus.text = if (it.isEmpty()) getString(R.string.history_no_samples) else
                        getString(if (entry.status == ShotHistory.Status.ENDED) R.string.history_samples_complete else R.string.history_samples_partial, it.size.toString())
                }.onFailure { chartStatus.text = getString(R.string.history_samples_failed) }
            }
        }, "history-detail-reader").start()
    }
    private fun status(value: ShotHistory.Status) = when (value) {
        ShotHistory.Status.STARTING -> getString(R.string.extraction_state_starting)
        ShotHistory.Status.RUNNING -> getString(R.string.extraction_state_running)
        ShotHistory.Status.STOP_REQUESTED -> getString(R.string.history_status_stop_requested)
        ShotHistory.Status.ENDED -> getString(R.string.history_status_ended)
        ShotHistory.Status.UNKNOWN -> getString(R.string.extraction_state_unknown)
        ShotHistory.Status.NOT_STARTED -> getString(R.string.history_status_not_started)
    }
    private fun reasonLabel(value: String) = when (value) {
        StopReason.TARGET_WEIGHT.name -> getString(R.string.extraction_stop_target)
        StopReason.SCALE_UNAVAILABLE.name -> getString(R.string.extraction_stop_scale)
        StopReason.TARE_UNCONFIRMED.name -> getString(R.string.extraction_stop_tare)
        StopReason.START_CONDITIONS_CHANGED.name -> getString(R.string.extraction_stop_changed)
        StopReason.MANUAL.name -> getString(R.string.extraction_stop_manual)
        else -> value
    }
    private fun date(epochMs: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(epochMs))
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}

package io.openhoyi.mobile

import android.app.AlertDialog
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
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, HistoryDetailActivity::class.java)
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
        HoyiUi.label(this, body, curve, 18, true).apply {
            // The fixed details button exposes the unabridged name and metadata.
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "full-text-details"
        }
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        body.addView(summary)
        HoyiUi.label(this, summary, status(entry.status), 14, true).apply {
            setTextColor(getColor(if (entry.status == ShotHistory.Status.UNKNOWN) R.color.mobile_danger else R.color.mobile_accent))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        HoyiUi.label(this, summary, date(entry.startedAtMs), 12, muted = true)
        val values = listOf(
            getString(R.string.history_duration) + " " + (entry.elapsedMs?.let {
                getString(R.string.history_duration_value, "%.1f".format(Locale.ROOT, it / 1000.0))
            } ?: "—"),
            getString(R.string.history_end_scale) + " " + (entry.weightHundredthsGram?.let {
                "%.2f g".format(Locale.ROOT, it / 100.0)
            } ?: "—")
        )
        HoyiUi.label(this, body, values.joinToString(" · "), 15, true).apply { setPadding(0, dp(6), 0, dp(6)) }
        HoyiUi.label(this, body, entry.reason?.let(::reasonLabel) ?: getString(R.string.history_no_reason), 13, muted = true)
        if (entry.status == ShotHistory.Status.UNKNOWN) HoyiUi.label(this, body,
            getString(R.string.history_unknown_warning), 14, true).apply {
            setPadding(0, dp(6), 0, dp(6))
            setTextColor(getColor(R.color.mobile_danger))
        }
        val chartCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = HoyiUi.shape(this@HistoryDetailActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        }
        body.addView(chartCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6) })
        val chart = ShotChartView(this).apply { contentDescription = getString(R.string.extraction_chart) }
        chartCard.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f))
        val chartStatus = HoyiUi.label(this, chartCard, getString(R.string.history_loading_samples), 12, muted = true)
        HoyiUi.button(this, body, getString(R.string.history_outcome_title) + " / " + getString(R.string.journal_notes_title)) {
            showDetails(entry, curve, permanent, app.journalResult.isFailure, chart.points)
        }
        if (permanent != null) {
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            body.addView(actions)
            HoyiUi.button(this, actions, getString(R.string.journal_review), primary = true) {
                startActivity(Intent(this, BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.id))
            }.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6); marginEnd = dp(6) }
            HoyiUi.button(this, actions, getString(R.string.journal_compare)) {
                startActivity(Intent(this, BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, entry.id))
            }.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(6) }
        } else {
            HoyiUi.label(this, body, getString(if (app.journalResult.isFailure) R.string.journal_unavailable else R.string.journal_migrate_hint), 13, muted = true)
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
    /** Long source metadata and all journal fields are available without shrinking the chart. */
    private fun showDetails(entry: ShotHistory.Entry, curve: String, permanent: BrewJournal.Entry?, journalFailed: Boolean, points: List<ShotPoint>) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        HoyiUi.label(this, content, curve, 18, true)
        HoyiUi.label(this, content, status(entry.status), 17, true)
        HoyiUi.label(this, content, getString(R.string.history_started_at, date(entry.startedAtMs)), 14)
        entry.endedAtMs?.let { HoyiUi.label(this, content, getString(R.string.history_ended_at, date(it)), 14) }
        entry.slot?.takeIf { it in 1..5 }?.let { HoyiUi.label(this, content, getString(R.string.history_slot, it.toString()), 14) }
        if (entry.weightHundredthsGram != null) HoyiUi.label(this, content, getString(R.string.history_scale_disclaimer), 14, muted = true)
        HoyiUi.label(this, content, entry.reason?.let(::reasonLabel) ?: getString(R.string.history_no_reason), 15, true)
        if (permanent != null) journalNotes(HoyiUi.card(this, content, getString(R.string.journal_notes_title)), permanent.notes)
        else HoyiUi.label(this, content, getString(if (journalFailed) R.string.journal_unavailable else R.string.journal_migrate_hint), 14)
        HoyiUi.button(this, content, getString(R.string.task_history_flow_details)) {
            val rows = points.joinToString("\n") { point ->
                "%.2f s · %.1f ml/s · %s".format(Locale.ROOT, point.elapsedMs / 1000.0,
                    point.machineFlowTenthsMlPerSecond / 10.0,
                    point.scaleFlowHundredths?.let { "%.2f g/s".format(Locale.ROOT, it / 100.0) } ?: "—")
            }
            beanDetails(getString(R.string.task_history_flow_details),
                getString(R.string.task_history_flow_columns) + "\n\n" + rows)
        }.isEnabled = points.isNotEmpty()
        val viewport = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle(curve).setView(viewport).setPositiveButton(android.R.string.ok, null).show()
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

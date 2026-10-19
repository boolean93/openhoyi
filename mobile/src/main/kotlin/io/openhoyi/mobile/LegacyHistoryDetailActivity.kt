package io.openhoyi.mobile

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView

class LegacyHistoryDetailActivity : ThemedActivity() {
    private val visual by lazy { LegacyVisualStyle(this) }
    private val style get() = visual.palette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = style.page(this, getString(R.string.legacy_history_detail_title))
        val id = intent.getStringExtra("legacyId")
        val status = style.text(getString(R.string.legacy_history_detail_loading), TaskProductVisualStyle.body,
            "legacy/detail/status").also { style.add(body, it, 8) }
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
        val name = shot.profileName.ifBlank { getString(R.string.legacy_history_unnamed) }
        val heading = LegacyNaturalFlow(this, dp(16), dp(8)).apply {
            addView(style.text(name, TaskProductVisualStyle.title, "legacy/detail/name"))
            addView(style.text(legacyMetadata(this@LegacyHistoryDetailActivity, shot), TaskProductVisualStyle.auxiliary,
                "legacy/detail/metadata", true))
            addView(visual.readonly())
        }
        style.add(body, heading, 8)
        val workspace = TaskWorkspaceLayout(this, supportingWidthFraction = .34f,
            supportingMaxWidthDp = 400, boundedSecondaryViewport = true).apply {
            fillSecondaryViewport = true
            visual.panel(primaryBody); visual.panel(secondaryBody)
        }
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        style.add(workspace.primaryBody, style.text(getString(R.string.legacy_detail_samples_heading),
            TaskProductVisualStyle.section, "legacy/detail/samples/title"))
        val chart = LegacyShotChartView(this).apply { visualStyle = visual; points = shot.points; tag = "legacy/detail/chart" }
        workspace.primaryBody.addView(LegacyChartViewport(this, chart), LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        workspace.onPrimarySpaceAvailable = { width, budget -> minOf(budget,
            chart.minimumVisualHeight((width - dp(32)).coerceAtLeast(0)) + dp(48) + style.sp(30).toInt()) }
        // A permanent supporting subtree: only prose scrolls in a split pane, the real action stays fixed.
        val prose = style.column("legacy/detail/prose")
        val scroll = ScrollView(this).apply { addView(prose) }
        workspace.secondaryBody.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun section(heading: Int, message: Int, separated: Boolean) {
            if (separated) style.divider(prose)
            style.add(prose, style.text(getString(heading), TaskProductVisualStyle.section))
            style.add(prose, style.text(getString(message), TaskProductVisualStyle.auxiliary), 8)
        }
        section(R.string.legacy_detail_source_heading, R.string.legacy_detail_source_body, false)
        section(R.string.legacy_detail_missing_heading, R.string.legacy_detail_missing_body, true)
        section(R.string.legacy_detail_limits_heading, R.string.legacy_detail_limits_body, true)
        style.action(workspace.secondaryBody, getString(R.string.ui_full_details), "legacy/detail/full-notice", outline = true, top = 16) {
            val summary = getString(R.string.legacy_history_summary, name,
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(shot.createdAtMs)),
                shot.durationSec.toString())
            val dialog = android.app.AlertDialog.Builder(this).setTitle(getString(R.string.legacy_history_detail_title))
                .setMessage(summary + "\n\n" + getString(R.string.legacy_history_chart_disclaimer))
                .setPositiveButton(android.R.string.ok, null).create()
            beanShowDialog(dialog)
        }
    }
    private fun dp(value: Int) = style.dp(value)
}

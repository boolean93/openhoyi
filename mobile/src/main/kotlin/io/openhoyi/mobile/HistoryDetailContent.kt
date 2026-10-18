package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import io.openhoyi.session.StopReason
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Same read-only detail in the collection pane and the dedicated full-screen page. */
internal fun ThemedActivity.renderHistoryDetail(body: LinearLayout, shotId: String?, standalone: Boolean = false, openFullPage: (() -> Unit)? = null) {
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
    if (!standalone) HoyiUi.label(this, body, curve, 18, true).apply {
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
    if (standalone) HoyiUi.label(this, summary, "$curve · ${date(entry.startedAtMs)}", 14, muted = true).apply {
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        tag = "full-text-details"
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(16) }
    }
    HoyiUi.label(this, summary, status(entry.status), 14, true).apply {
        setTextColor(getColor(if (entry.status == ShotHistory.Status.UNKNOWN) R.color.mobile_danger else R.color.mobile_accent))
        layoutParams = if (standalone) LinearLayout.LayoutParams(-2, -2)
            else LinearLayout.LayoutParams(0, -2, 1f)
    }
    if (!standalone) HoyiUi.label(this, summary, date(entry.startedAtMs), 14, muted = true)
    val values = listOf(
        getString(R.string.history_duration) + " " + (entry.elapsedMs?.let {
            getString(R.string.history_duration_value, "%.1f".format(Locale.ROOT, it / 1000.0))
        } ?: "—"),
        getString(R.string.history_end_scale) + " " + (entry.weightHundredthsGram?.let {
            "%.2f g".format(Locale.ROOT, it / 100.0)
        } ?: "—")
    )
    if (!standalone) HoyiUi.label(this, body, values.joinToString(" · "), 15, true).apply { setPadding(0, dp(6), 0, dp(6)) }
    if (entry.status == ShotHistory.Status.UNKNOWN) HoyiUi.label(this, body,
        getString(R.string.history_unknown_warning), 14, true).apply {
        setPadding(0, dp(6), 0, dp(6))
        setTextColor(getColor(R.color.mobile_danger))
    }
    // Only the dedicated page owns this split; collection previews already have a list pane.
    val workspace = if (standalone) TaskExtractionWorkspace(this).also {
        body.addView(it, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
    } else null
    val plotBody = workspace?.primaryBody ?: body
    if (standalone) {
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        plotBody.addView(metrics)
        listOf(
            R.string.history_duration to (entry.elapsedMs?.let {
                getString(R.string.history_duration_value, "%.1f".format(Locale.ROOT, it / 1000.0))
            } ?: "—"),
            R.string.history_end_scale to (entry.weightHundredthsGram?.let {
                "%.2f g".format(Locale.ROOT, it / 100.0)
            } ?: "—")
        ).forEach { (label, value) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, dp(16), dp(16))
            }
            metrics.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            HoyiUi.label(this, cell, getString(label), 14, muted = true)
            HoyiUi.label(this, cell, value, TaskVisualTokens.LIVE_VALUE_SP).apply {
                typeface = TaskTypography.face(this@renderHistoryDetail, 700)
                // Root-locale values have a stable numeric prefix; Japanese/Korean units
                // need not have a separating space. Missing values keep their plain dash.
                val number = Regex("^[+-]?[0-9]+(?:\\.[0-9]+)?\\s*").find(value)
                if (number != null && number.value.length < value.length) {
                    text = android.text.SpannableString(value).apply {
                        setSpan(android.text.style.RelativeSizeSpan(.55f), number.value.length, length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                setPadding(0, dp(8), 0, 0)
            }
        }
    }
    val chartCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (!standalone) setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    plotBody.addView(chartCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = if (standalone) 0 else dp(8) })
    val chart = ShotChartView(this).apply { contentDescription = getString(R.string.extraction_chart) }
    chartCard.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f))
    val chartStatus = HoyiUi.label(this, chartCard, getString(R.string.history_loading_samples), 14, muted = true)
    if (permanent != null && !standalone) {
        listOf(R.string.journal_bean to permanent.notes.beanName,
            R.string.journal_taste to permanent.notes.taste).forEach { (label, value) ->
            HoyiUi.label(this, body, getString(R.string.journal_field_value, getString(label),
                value?.takeIf { it.isNotBlank() } ?: getString(R.string.journal_optional)), TaskVisualTokens.BODY_SP).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                tag = "full-text-details"
                setPadding(0, dp(4), 0, 0)
            }
        }
    }
    val actions = LinearLayout(this).apply { orientation = if (standalone) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
    val notesBody = workspace?.secondaryBody
    if (notesBody != null) {
        HoyiUi.label(this, notesBody, getString(R.string.journal_notes_title), 20, true).apply {
            setPadding(0, 0, 0, dp(16))
        }
        if (permanent != null) {
            val notes = permanent.notes
            listOf(
                R.string.journal_bean to notes.beanName,
                R.string.journal_dose to notes.doseMg?.let { getString(R.string.journal_grams, BeanQuantity.formatGrams(it)) },
                R.string.journal_grind to notes.grind,
                R.string.journal_expectation to notes.curveExpectation,
                R.string.journal_taste to notes.taste,
                R.string.journal_next to notes.nextAdjustment
            ).forEachIndexed { index, (label, value) ->
                val text = value?.takeIf { it.isNotBlank() } ?: getString(R.string.journal_optional)
                notesBody.addView(android.view.View(this).apply {
                    setBackgroundColor(getColor(R.color.mobile_border))
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(-1, dp(1)))
                if (index < 3) {
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        isBaselineAligned = false
                        setPadding(0, dp(8), 0, dp(8))
                    }
                    notesBody.addView(row, LinearLayout.LayoutParams(-1, -2))
                    HoyiUi.label(this, row, getString(label), 14, muted = true).apply {
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
                    }
                    HoyiUi.label(this, row, text, 16).apply {
                        gravity = android.view.Gravity.END
                        layoutParams = LinearLayout.LayoutParams(0, -2, 1.3f)
                    }
                } else {
                    HoyiUi.label(this, notesBody, getString(label), 16, true).apply { setPadding(0, dp(16), 0, 0) }
                    HoyiUi.label(this, notesBody, text, 16).apply { setPadding(0, dp(8), 0, dp(8)) }
                }
            }
        }
        else HoyiUi.label(this, notesBody, getString(if (app.journalResult.isFailure)
            R.string.journal_unavailable else R.string.journal_migrate_hint), 14)
        notesBody.addView(actions)
    } else body.addView(actions)
    val primaryDock = if (standalone && permanent != null) TaskActionDock(this, edgeInsetDp = 0).also {
        requireNotNull(workspace).setFooter(it)
    } else null
    fun action(title: String, primary: Boolean = false, leading: Boolean = false, click: () -> Unit) {
        val parent = if (primary) primaryDock ?: actions else if (leading) notesBody ?: actions else actions
        HoyiUi.button(this, parent, title, primary = primary, action = click).apply {
            layoutParams = if (standalone) LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                else LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(8); marginEnd = dp(8) }
            if (standalone && !primary) {
                setTextColor(getColor(R.color.mobile_accent))
                val states = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@renderHistoryDetail, android.R.color.transparent, 8, R.color.mobile_accent))
                    addState(intArrayOf(), HoyiUi.shape(this@renderHistoryDetail, android.R.color.transparent, 8))
                }
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)),
                    states, HoyiUi.shape(this@renderHistoryDetail, android.R.color.white, 8))
            }
            if (standalone && leading) {
                parent.removeView(this)
                parent.addView(this, 0)
            }
        }
    }
    action(getString(R.string.history_outcome_title) + " / " + getString(R.string.journal_notes_title)) {
        showDetails(entry, curve, permanent, app.journalResult.isFailure, chart.points)
    }
    if (permanent != null) {
        action(getString(R.string.journal_review), primary = true) {
            startActivity(Intent(this, BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.id))
        }
        action(getString(R.string.journal_compare), leading = standalone) {
            startActivity(Intent(this, BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, entry.id))
        }
    }
    openFullPage?.let { action(getString(R.string.ui_full_details), click = it) }
    Thread({
        val points = runCatching { app.samples.load(entry.id) }
        // View.post queues pre-attach results until the chart can receive them. A late
        // result for an already detached selection still cannot update the visible cup.
        chart.post {
            if (isFinishing || isDestroyed || !chart.isAttachedToWindow) return@post
            points.onSuccess {
                chart.points = it
                chartStatus.text = if (it.isEmpty()) getString(R.string.history_no_samples) else
                    getString(if (entry.status == ShotHistory.Status.ENDED) R.string.history_samples_complete else R.string.history_samples_partial, it.size.toString())
            }.onFailure { chartStatus.text = getString(R.string.history_samples_failed) }
        }
    }, "history-detail-reader").start()
}
/** Long source metadata and all journal fields are available without shrinking the chart. */
private fun ThemedActivity.showDetails(entry: ShotHistory.Entry, curve: String, permanent: BrewJournal.Entry?, journalFailed: Boolean, points: List<ShotPoint>) {
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

private fun ThemedActivity.status(value: ShotHistory.Status) = when (value) {
    ShotHistory.Status.STARTING -> getString(R.string.extraction_state_starting)
    ShotHistory.Status.RUNNING -> getString(R.string.extraction_state_running)
    ShotHistory.Status.STOP_REQUESTED -> getString(R.string.history_status_stop_requested)
    ShotHistory.Status.ENDED -> getString(R.string.history_status_ended)
    ShotHistory.Status.UNKNOWN -> getString(R.string.extraction_state_unknown)
    ShotHistory.Status.NOT_STARTED -> getString(R.string.history_status_not_started)
}
private fun ThemedActivity.reasonLabel(value: String) = when (value) {
    StopReason.TARGET_WEIGHT.name -> getString(R.string.extraction_stop_target)
    StopReason.SCALE_UNAVAILABLE.name -> getString(R.string.extraction_stop_scale)
    StopReason.TARE_UNCONFIRMED.name -> getString(R.string.extraction_stop_tare)
    StopReason.START_CONDITIONS_CHANGED.name -> getString(R.string.extraction_stop_changed)
    StopReason.MANUAL.name -> getString(R.string.extraction_stop_manual)
    else -> value
}
private fun ThemedActivity.date(epochMs: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(epochMs))
private fun ThemedActivity.dp(value: Int) = HoyiUi.dp(this, value)

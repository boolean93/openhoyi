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
internal fun ThemedActivity.renderHistoryDetail(body: LinearLayout, shotId: String?, standalone: Boolean = false, comparisonHost: LinearLayout? = null, openFullPage: (() -> Unit)? = null, visual: ExtractionHistoryVisualStyle? = null) {
    if (visual != null) { renderProductHistoryDetail(body, shotId, standalone, comparisonHost, openFullPage, visual); return }
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
        val compare = {
            startActivity(Intent(this, BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, entry.id))
        }
        if (standalone && comparisonHost != null) {
            HoyiUi.button(this, comparisonHost, getString(R.string.journal_compare), action = compare).apply {
                asTaskTextAction()
                maxWidth = dp(200)
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(16) }
            }
        } else action(getString(R.string.journal_compare), leading = standalone, click = compare)
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
/** M5-only composition; models, source resolution and sample reader semantics remain identical. */
private fun ThemedActivity.renderProductHistoryDetail(body: LinearLayout, shotId: String?, standalone: Boolean,
    comparisonHost: LinearLayout?, openFullPage: (() -> Unit)?, visual: ExtractionHistoryVisualStyle) {
    val style = visual.palette
    val app = application as MobileApplication
    val permanent = shotId?.let { app.journalResult.getOrNull()?.find(it) }
    val entry = runCatching { app.history.entries.firstOrNull { it.id == shotId } }.getOrNull()
        ?: permanent?.observation?.historySummary()
    if (entry == null) { style.add(body, style.text(getString(R.string.history_missing))); return }
    val curve = if (entry.curveId == "manual") getString(R.string.extraction_manual_label) else
        runCatching { app.curves.find(entry.curveId)?.name }.getOrNull() ?: entry.curveId
    if (!standalone) {
        renderEmbeddedHistoryPreview(body, entry, curve, permanent, app.journalResult.isFailure, openFullPage, visual)
        return
    }
    val identity = HistoryVisualFlow(this, style.dp(16), style.dp(8)).apply { tag = "product/history/identity-meta" }
    if (standalone) identity.addView(style.text(curve, TaskProductVisualStyle.body, "product/history/identity"))
    identity.addView(style.text(date(entry.startedAtMs), TaskProductVisualStyle.auxiliary, "product/history/date", true))
    identity.addView(style.text(status(entry.status), TaskProductVisualStyle.auxiliary, "product/history/status").apply {
        setTextColor(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningInk else style.accent)
        background = style.shape(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningSurface else style.selected)
        setPadding(style.dp(8), 0, style.dp(8), 0)
    })
    style.add(body, identity, if (standalone) 0 else 8)
    if (entry.status == ShotHistory.Status.UNKNOWN) style.add(body, style.text(getString(R.string.history_unknown_warning),
        TaskProductVisualStyle.auxiliary, "product/history/warning").apply { setTextColor(visual.warningInk) }, 8)
    val workspace = if (standalone) TaskExtractionWorkspace(this, visual).also {
        body.addView(it, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(16) })
    } else null
    val plot = StandaloneHistoryPlot(this, visual).also {
        (workspace?.primaryBody ?: body).addView(it, LinearLayout.LayoutParams(-1, -1))
    }
    val metrics = HistoryVisualFlow(this, style.dp(24), style.dp(8)).apply { tag = "product/history/metrics" }
    style.add(plot, metrics, if (standalone) 0 else 16)
    fun metric(label: Int, value: String, unit: String?, id: String, prominent: Boolean = true): HistoryQuantity {
        val labelView = style.text(getString(label), TaskProductVisualStyle.auxiliary, "$id/label", true)
        val quantity = HistoryQuantity(this, visual, id, large = prominent).apply { present(value, unit) }
        val cell = EmbeddedHistoryMetricCell(this, style.dp(8), labelView, quantity).apply { tag = "$id/cell" }
        plot.metricCells += cell
        metrics.addView(cell); return quantity
    }
    metric(R.string.history_duration, entry.elapsedMs?.let { "%.1f".format(Locale.ROOT, it / 1000.0) } ?: "—",
        if (entry.elapsedMs != null) "s" else null, "product/history/elapsed")
    metric(R.string.history_end_scale, entry.weightHundredthsGram?.let { "%.2f".format(Locale.ROOT, it / 100.0) } ?: "—",
        if (entry.weightHundredthsGram != null) "g" else null, "product/history/weight")
    // This is a sample observation, not an inferred final outcome or stored new field.
    val sampledWater = metric(R.string.m5_last_sample_water, "—", null, "product/history/sampled-water", false)
    val chart = ShotChartView(this, visual).apply { tag = "product/history/chart"; contentDescription = getString(R.string.extraction_chart) }
    plot.chart = chart
    plot.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(16) })
    val chartStatus = style.text(getString(R.string.history_loading_samples), TaskProductVisualStyle.auxiliary, "product/history/chart-status", true)
    style.add(plot, chartStatus, 8)
    val notesBody = workspace?.secondaryBody ?: style.column("product/history/notes").also { style.add(body, it, 16) }
    if (standalone) style.add(notesBody, style.text(getString(R.string.journal_notes_title), TaskProductVisualStyle.section), 0)
    fun note(label: Int, value: String?, block: Boolean) {
        style.divider(notesBody, if (block) 16 else 8, 8)
        val actual = value?.takeIf { it.isNotBlank() } ?: getString(R.string.journal_optional)
        if (block) {
            style.add(notesBody, style.text(getString(label), TaskProductVisualStyle.section))
            style.add(notesBody, style.text(actual, TaskProductVisualStyle.body, "product/history/note/$label"), 8)
        } else {
            val row = style.row("product/history/note/$label/row")
            row.addView(style.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(16) })
            row.addView(style.text(actual, TaskProductVisualStyle.body, "product/history/note/$label").apply {
                if (!standalone) { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details"; contentDescription = actual }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { gravity = android.view.Gravity.END })
            style.add(notesBody, row)
        }
    }
    if (permanent != null) {
        val n = permanent.notes
        note(R.string.journal_bean, n.beanName, false)
        if (standalone) {
            note(R.string.journal_dose, n.doseMg?.let { getString(R.string.journal_grams, BeanQuantity.formatGrams(it)) }, false)
            note(R.string.journal_grind, n.grind, false)
            note(R.string.journal_expectation, n.curveExpectation, true)
            note(R.string.journal_taste, n.taste, true); note(R.string.journal_next, n.nextAdjustment, true)
        } else note(R.string.journal_taste, n.taste, false)
    } else style.add(notesBody, style.text(getString(if (app.journalResult.isFailure) R.string.journal_unavailable else R.string.journal_migrate_hint), TaskProductVisualStyle.auxiliary), 8)
    val actions = style.column("product/history/actions").apply { if (!standalone) orientation = LinearLayout.HORIZONTAL }
    if (workspace != null) workspace.setFooter(actions) else style.add(body, actions, 8)
    fun action(title: String, id: String, primary: Boolean = false, glyph: String? = null, click: () -> Unit) {
        style.action(actions, title, id, primary = primary, top = 8, onClick = click).apply {
            if (!standalone) layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) }
            glyph?.let { HomeVisualIcon.buttonIcon(this, it) }
        }
    }
    if (permanent != null) {
        action(getString(R.string.journal_review), "product/history/review", true, "edit") {
            startActivity(Intent(this, BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.id))
        }
        val compare = { startActivity(Intent(this, BrewComparisonActivity::class.java).putExtra(BrewComparisonActivity.FIRST_SHOT_ID, entry.id)) }
        if (standalone && comparisonHost != null) style.button(HoyiUi.button(this, comparisonHost, getString(R.string.journal_compare), action = compare)).apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(16) }
        } else if (standalone) action(getString(R.string.journal_compare), "product/history/compare", click = compare)
    }
    if (standalone) action(getString(R.string.history_outcome_title) + " / " + getString(R.string.journal_notes_title), "product/history/details", glyph = "chevron") {
        showDetails(entry, curve, permanent, app.journalResult.isFailure, chart.points)
    }
    openFullPage?.let { action(getString(R.string.ui_full_details), "product/history/full-page", glyph = "chevron", click = it) }
    Thread({
        val points = runCatching { app.samples.load(entry.id) }
        chart.post {
            if (isFinishing || isDestroyed || !chart.isAttachedToWindow) return@post
            points.onSuccess {
                chart.points = it
                sampledWater.present(it.lastOrNull()?.waterTenthsMl?.let { water -> "%.1f".format(Locale.ROOT, water / 10.0) } ?: "—",
                    if (it.isNotEmpty()) "ml" else null)
                sampledWater.contentDescription = getString(R.string.m5_last_sample_water) + ": " + sampledWater.contentDescription
                chartStatus.text = if (it.isEmpty()) getString(R.string.history_no_samples) else
                    getString(if (entry.status == ShotHistory.Status.ENDED) R.string.history_samples_complete else R.string.history_samples_partial, it.size.toString())
            }.onFailure { chartStatus.text = getString(R.string.history_samples_failed) }
        }
    }, "history-detail-reader").start()
}
/** Embedded collection preview: one permanent chart/notes viewport and fixed actions only. */
private fun ThemedActivity.renderEmbeddedHistoryPreview(body: LinearLayout, entry: ShotHistory.Entry, curve: String,
    permanent: BrewJournal.Entry?, journalFailed: Boolean, openFullPage: (() -> Unit)?, visual: ExtractionHistoryVisualStyle) {
    val style = visual.palette
    val app = application as MobileApplication
    val chart = ShotChartView(this, visual, embeddedHistory = true).apply { tag = "product/history/chart"; contentDescription = getString(R.string.extraction_chart) }
    val preview = EmbeddedHistoryPreview(this, visual, chart)
    body.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
    val title = style.row("product/history/title-row").apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = style.dp(48) }
    val identity = style.text(curve, TaskProductVisualStyle.title, "product/history/identity")
    title.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
    val titleSpacer = android.view.View(this)
    title.addView(titleSpacer, LinearLayout.LayoutParams(0, 0))
    val more = android.widget.ImageButton(this).apply {
        tag = "product/history/more"; contentDescription = getString(R.string.ui_full_details)
        setImageDrawable(HomeVisualIcon.drawable(this@renderEmbeddedHistoryPreview, "more", style.accent))
        setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12))
        minimumHeight = style.dp(48); minimumWidth = style.dp(48)
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(style.selected), null, style.shape(android.graphics.Color.WHITE))
        setOnClickListener {
            val choices = listOf(getString(R.string.history_outcome_title) + " / " + getString(R.string.journal_notes_title)) +
                if (permanent != null) listOf(getString(R.string.journal_compare)) else emptyList()
            val dialog = AppDialog.choices(this@renderEmbeddedHistoryPreview, getString(R.string.ui_full_details), choices) { index ->
                if (index == 0) showDetails(entry, curve, permanent, journalFailed, chart.points)
                else startActivity(Intent(this@renderEmbeddedHistoryPreview, BrewComparisonActivity::class.java)
                    .putExtra(BrewComparisonActivity.FIRST_SHOT_ID, entry.id))
            }
            (this@renderEmbeddedHistoryPreview as? MainActivity)?.trackDialog(dialog)
        }
    }
    title.addView(more, LinearLayout.LayoutParams(style.dp(48), style.dp(48)).apply { marginStart = style.dp(16) })
    val meta = HistoryVisualFlow(this, style.dp(16), style.dp(8)).apply { tag = "product/history/identity-meta" }
    meta.addView(style.text(date(entry.startedAtMs), TaskProductVisualStyle.auxiliary, "product/history/date", true))
    meta.addView(style.text(status(entry.status), TaskProductVisualStyle.auxiliary, "product/history/status").apply {
        setTextColor(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningInk else style.accent)
        background = style.shape(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningSurface else style.selected)
        setPadding(style.dp(8), 0, style.dp(8), 0)
    })
    val chartStatus = style.text(getString(R.string.history_loading_samples), TaskProductVisualStyle.auxiliary, "product/history/chart-status", true)
    meta.addView(chartStatus)
    val warning = if (entry.status == ShotHistory.Status.UNKNOWN) style.text(getString(R.string.history_unknown_warning),
        TaskProductVisualStyle.auxiliary, "product/history/warning").apply { setTextColor(visual.warningInk) } else null
    val header = EmbeddedHistoryHeader(this, title, identity, titleSpacer, more, meta, warning, style.dp(16), style.dp(8))
    style.add(preview.content, header)
    val metrics = HistoryVisualFlow(this, style.dp(24), style.dp(8)).apply { tag = "product/history/metrics" }
    style.add(preview.content, metrics, 16)
    fun metric(label: Int, number: String, unit: String?, id: String, prominent: Boolean = true): HistoryQuantity {
        val labelView = style.text(getString(label), TaskProductVisualStyle.auxiliary, "$id/label", true)
        val quantity = HistoryQuantity(this, visual, id, large = prominent).apply { present(number, unit) }
        val cell = EmbeddedHistoryMetricCell(this, style.dp(8), labelView, quantity).apply { tag = "$id/cell" }
        preview.metricCells += cell
        metrics.addView(cell)
        return quantity
    }
    metric(R.string.history_duration, entry.elapsedMs?.let { "%.1f".format(Locale.ROOT, it / 1000.0) } ?: "—",
        if (entry.elapsedMs != null) "s" else null, "product/history/elapsed")
    metric(R.string.history_end_scale, entry.weightHundredthsGram?.let { "%.2f".format(Locale.ROOT, it / 100.0) } ?: "—",
        if (entry.weightHundredthsGram != null) "g" else null, "product/history/weight")
    val sampledWater = metric(R.string.m5_last_sample_water, "—", null, "product/history/sampled-water", false)
    preview.content.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(16) })
    // The actual chart producer retains this permanent view and updates its text from the same projection.
    // Only embedded History places it after the complete chart, before its notes; resize never reparents it.
    style.add(preview.content, requireNotNull(chart.embeddedExplanationView), 8)
    fun summary(label: Int, value: String?): LinearLayout = style.row("product/history/note/$label/row").apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(style.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(16) })
        val actual = value?.takeIf { it.isNotBlank() } ?: getString(R.string.journal_optional)
        addView(style.text(actual, TaskProductVisualStyle.body, "full-text-details").apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; contentDescription = actual
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }
    val bean = if (permanent != null) summary(R.string.journal_bean, permanent.notes.beanName) else
        style.text(getString(if (journalFailed) R.string.journal_unavailable else R.string.journal_migrate_hint), TaskProductVisualStyle.auxiliary, secondary = true)
    style.add(preview.content, bean, 16)
    val taste = summary(R.string.journal_taste, permanent?.notes?.taste).apply { if (permanent == null) visibility = android.view.View.GONE }
    style.add(preview.content, taste, 8)
    val actions = style.row("product/history/actions").apply { gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL }
    fun action(text: String, id: String, primary: Boolean, glyph: String, click: () -> Unit) {
        val button = style.button(android.widget.Button(this), primary).apply {
            this.text = text; tag = id; setOnClickListener { click() }; HomeVisualIcon.buttonIcon(this, glyph)
        }
        actions.addView(button, LinearLayout.LayoutParams(-2, -2).apply { if (actions.childCount > 0) marginStart = style.dp(16) })
    }
    if (permanent != null) action(getString(R.string.journal_review), "product/history/review", true, "edit") {
        startActivity(Intent(this, BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.id))
    }
    openFullPage?.let { action(getString(R.string.ui_full_details), "product/history/full-page", false, "chevron", it) }
    preview.footer.addView(EmbeddedHistoryActionsRow(this, style, actions), LinearLayout.LayoutParams(-1, -2))
    Thread({
        val points = runCatching { app.samples.load(entry.id) }
        chart.post {
            if (isFinishing || isDestroyed || !chart.isAttachedToWindow) return@post
            points.onSuccess {
                chart.points = it
                sampledWater.present(it.lastOrNull()?.waterTenthsMl?.let { water -> "%.1f".format(Locale.ROOT, water / 10.0) } ?: "—", if (it.isNotEmpty()) "ml" else null)
                sampledWater.contentDescription = getString(R.string.m5_last_sample_water) + ": " + sampledWater.contentDescription
                chartStatus.text = if (it.isEmpty()) getString(R.string.history_no_samples) else
                    getString(if (entry.status == ShotHistory.Status.ENDED) R.string.history_samples_complete else R.string.history_samples_partial, it.size.toString())
            }.onFailure { chartStatus.text = getString(R.string.history_samples_failed) }
        }
    }, "history-detail-reader").start()
}

/** Standalone History alone recovers metric whitespace when the real 96dp plot budget requires it. */
private class StandaloneHistoryPlot(context: android.content.Context, visual: ExtractionHistoryVisualStyle) : LinearLayout(context) {
    private val style = visual.palette
    private val renderer = ProductHistoryChartRenderer(context, visual)
    val metricCells = mutableListOf<EmbeddedHistoryMetricCell>()
    lateinit var chart: ShotChartView
    val productLayoutEvidence = org.json.JSONObject()
    init { orientation = VERTICAL; tag = "product/history/standalone-plot-budget" }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (android.view.View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (android.view.View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val exact = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
        val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        fun chrome(): Int {
            var result = 0
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child === chart || child.visibility == android.view.View.GONE) continue
                child.forceLayout(); child.measure(exact, natural)
                val params = child.layoutParams as LayoutParams
                result += child.measuredHeight + params.topMargin + params.bottomMargin
            }
            return result
        }
        metricCells.forEach { it.inline = false }
        val verticalChrome = chrome()
        val overlay = chart.getChildAt(0) as LinearLayout
        val header = (overlay.getChildAt(0) as ScrollView).getChildAt(0)
        header.forceLayout(); header.measure(exact, natural)
        val headerHeight = header.measuredHeight
        val axesAndPlot = renderer.minimumEmbeddedHistoryHeight()
        val chartMinimum = maxOf(headerHeight, style.dp(48)) + axesAndPlot + overlay.paddingTop + overlay.paddingBottom
        val params = chart.layoutParams as LayoutParams
        val chartMargins = params.topMargin + params.bottomMargin
        val inlineWidth = metricCells.sumOf { it.inlineNaturalWidth() } + style.dp(24) * (metricCells.size - 1).coerceAtLeast(0)
        val inline = verticalChrome + chartMargins + chartMinimum > height && inlineWidth <= width
        metricCells.forEach { it.inline = inline }
        val resolvedChrome = chrome()
        productLayoutEvidence.put("availableHeightPx", height).put("availableWidthPx", width)
            .put("verticalChromeNaturalPx", verticalChrome).put("resolvedChromeNaturalPx", resolvedChrome)
            .put("metricsInlineNaturalWidthPx", inlineWidth).put("metricsInline", inline)
            .put("chartNativeHeaderNaturalPx", headerHeight).put("chartActualPaintAxisMinimumPx", axesAndPlot)
            .put("chartOverlayPaddingPx", overlay.paddingTop + overlay.paddingBottom).put("chartMarginsPx", chartMargins)
            .put("chartMinimumPx", chartMinimum).put("minimumPlotDp", 96)
            .put("primaryBudgetSatisfiable", resolvedChrome + chartMargins + chartMinimum <= height)
            .put("chartBudgetSource", "native_overlay_header + real_plot_96dp + renderer_bounds_actual_axis_bands")
        fun invalidateMeasure(view: android.view.View) {
            view.forceLayout()
            if (view is android.view.ViewGroup) (0 until view.childCount).forEach { invalidateMeasure(view.getChildAt(it)) }
        }
        for (index in 0 until childCount) invalidateMeasure(getChildAt(index))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** The UNKNOWN warning keeps its one TextView; only its measured position changes. */
private class EmbeddedHistoryHeader(context: android.content.Context, private val titleRow: LinearLayout,
    private val identity: android.widget.TextView, private val spacer: android.view.View,
    private val more: android.widget.ImageButton, private val meta: android.view.View,
    private val warning: android.widget.TextView?, private val inlineGapPx: Int, private val rowGapPx: Int
) : android.view.ViewGroup(context) {
    var inlineWarning = false
        set(value) { if (field != value) { field = value; forceLayout() } }
    var resolvedInline = false; private set
    var titleNaturalWidthPx = 0; private set
    var warningNaturalWidthPx = 0; private set
    var moreOccupationPx = 0; private set
    var rowAvailableWidthPx = 0; private set
    init {
        tag = "product/history/preview-header"
        addView(titleRow); addView(meta); warning?.let { addView(it) }
    }
    override fun generateDefaultLayoutParams() = LayoutParams(-1, -2)
    fun fitsInline(width: Int): Boolean = warning != null &&
        titleNaturalWidthPx + inlineGapPx + warningNaturalWidthPx <= width - moreOccupationPx
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
        val exact = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
        val free = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        identity.forceLayout(); identity.measure(free, free)
        titleNaturalWidthPx = identity.measuredWidth
        warning?.let { it.forceLayout(); it.measure(free, free) }
        warningNaturalWidthPx = warning?.measuredWidth ?: 0
        val moreParams = more.layoutParams as LinearLayout.LayoutParams
        moreOccupationPx = moreParams.width + moreParams.marginStart + moreParams.marginEnd
        rowAvailableWidthPx = width - moreOccupationPx
        resolvedInline = inlineWarning && fitsInline(width)
        (identity.layoutParams as LinearLayout.LayoutParams).apply {
            this.width = if (resolvedInline) titleNaturalWidthPx else 0
            weight = if (resolvedInline) 0f else 1f
        }
        (spacer.layoutParams as LinearLayout.LayoutParams).apply {
            this.width = 0; weight = if (resolvedInline) 1f else 0f
        }
        titleRow.forceLayout(); titleRow.measure(exact, free)
        meta.forceLayout(); meta.measure(exact, free)
        warning?.let {
            it.forceLayout()
            it.measure(if (resolvedInline) android.view.View.MeasureSpec.makeMeasureSpec(warningNaturalWidthPx, android.view.View.MeasureSpec.EXACTLY) else exact, free)
        }
        val height = titleRow.measuredHeight + rowGapPx + meta.measuredHeight +
            if (warning != null && !resolvedInline) rowGapPx + warning.measuredHeight else 0
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        titleRow.layout(0, 0, width, titleRow.measuredHeight)
        val metaTop = titleRow.measuredHeight + rowGapPx
        meta.layout(0, metaTop, width, metaTop + meta.measuredHeight)
        warning?.let {
            val left = if (resolvedInline && layoutDirection == LAYOUT_DIRECTION_RTL)
                width - titleNaturalWidthPx - inlineGapPx - it.measuredWidth
            else if (resolvedInline) titleNaturalWidthPx + inlineGapPx else 0
            val top = if (resolvedInline) (titleRow.measuredHeight - it.measuredHeight) / 2 else metaTop + meta.measuredHeight + rowGapPx
            it.layout(left, top, left + it.measuredWidth, top + it.measuredHeight)
        }
    }
}

/** Permanent layout: resize changes only native measure inputs, never a source or parent. */
private class EmbeddedHistoryPreview(context: android.content.Context, visual: ExtractionHistoryVisualStyle,
    private val chart: ShotChartView) : LinearLayout(context) {
    private val style = visual.palette
    val content = EmbeddedHistoryContent(context)
    val metricCells = mutableListOf<EmbeddedHistoryMetricCell>()
    val footer = style.column("product/history/preview-footer").apply { setPadding(0, style.dp(16), 0, 0) }
    val productLayoutEvidence = org.json.JSONObject()
    private val viewport = ScrollView(context).apply { tag = "product/history/preview-scroll"; addView(content) }
    // Exactly the production renderer's actual 14sp/400 Paint policy, not a fixed dp guess.
    private val paintBudget = ProductHistoryChartRenderer(context, visual)
    init {
        orientation = VERTICAL; tag = "product/history/preview"
        addView(viewport, LayoutParams(-1, 0, 1f)); addView(footer, LayoutParams(-1, -2))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (android.view.View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (android.view.View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val exact = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
        val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        footer.measure(exact, natural)
        fun measureChrome(sections: org.json.JSONArray? = null): Int {
            var total = 0
            for (index in 0 until content.childCount) {
                val child = content.getChildAt(index); if (child === chart || child.visibility == android.view.View.GONE) continue
                child.forceLayout(); child.measure(exact, natural)
                val params = child.layoutParams as LayoutParams
                total += child.measuredHeight + params.topMargin + params.bottomMargin
                sections?.put(org.json.JSONObject().put("tag", child.tag).put("naturalHeightPx", child.measuredHeight)
                    .put("marginsPx", params.topMargin + params.bottomMargin))
            }
            return total
        }
        val previewHeader = content.getChildAt(0) as EmbeddedHistoryHeader
        previewHeader.inlineWarning = false
        metricCells.forEach { it.inline = false }
        val verticalChrome = measureChrome()
        val overlay = chart.getChildAt(0) as LinearLayout
        val chartHeader = (overlay.getChildAt(0) as ScrollView).getChildAt(0)
        chartHeader.measure(exact, natural)
        val headerHeight = chartHeader.measuredHeight
        val axisMinimum = paintBudget.minimumEmbeddedHistoryHeight()
        val chartMinimum = maxOf(headerHeight, style.dp(48)) + axisMinimum + overlay.paddingTop + overlay.paddingBottom
        val chartParams = chart.layoutParams as LayoutParams
        val chartMargins = chartParams.topMargin + chartParams.bottomMargin
        val available = (height - footer.measuredHeight).coerceAtLeast(0)
        val verticalMinimum = verticalChrome + chartMargins + chartMinimum
        val inlineWidth = metricCells.sumOf { it.inlineNaturalWidth() } + style.dp(24) * (metricCells.size - 1).coerceAtLeast(0)
        val inlineMetrics = verticalMinimum > available && inlineWidth <= width
        metricCells.forEach { it.inline = inlineMetrics }
        measureChrome()
        fun firstScreenMinimum(): Int {
            var beforeChart = 0
            for (index in 0 until content.indexOfChild(chart)) {
                val child = content.getChildAt(index)
                if (child.visibility == android.view.View.GONE) continue
                val params = child.layoutParams as LayoutParams
                beforeChart += child.measuredHeight + params.topMargin + params.bottomMargin
            }
            return beforeChart + chartMargins + chartMinimum
        }
        val firstScreenBeforePx = firstScreenMinimum()
        val titleWarningWidthFits = previewHeader.fitsInline(width)
        val mergeWarning = firstScreenBeforePx > available && titleWarningWidthFits
        previewHeader.inlineWarning = mergeWarning
        val sections = org.json.JSONArray()
        val chrome = measureChrome(sections)
        val firstScreenResolvedPx = firstScreenMinimum()
        val minimumContent = chrome + chartMargins + chartMinimum
        val scrolling = minimumContent > available
        content.resolvedHeightPx = if (scrolling) minimumContent else available
        chartParams.height = if (scrolling) chartMinimum else 0; chartParams.weight = if (scrolling) 0f else 1f
        productLayoutEvidence.put("availableHeightPx", height).put("fixedFooterNaturalPx", footer.measuredHeight)
            .put("fixedFooterBudgetSatisfiable", footer.measuredHeight <= height)
            .put("contentViewportPx", available).put("headerAndMetricsNaturalPx", chrome).put("sections", sections)
            .put("firstScreenMinimumBeforePx", firstScreenBeforePx).put("firstScreenMinimumResolvedPx", firstScreenResolvedPx)
            .put("firstScreenBudgetSatisfiable", firstScreenResolvedPx <= available)
            .put("titleNaturalWidthPx", previewHeader.titleNaturalWidthPx)
            .put("warningNaturalWidthPx", previewHeader.warningNaturalWidthPx)
            .put("moreOccupationPx", previewHeader.moreOccupationPx)
            .put("titleWarningRowAvailableWidthPx", previewHeader.rowAvailableWidthPx)
            .put("titleWarningGapPx", style.dp(16)).put("titleWarningWidthFits", titleWarningWidthFits)
            .put("titleWarningInlineCondition", mergeWarning).put("titleWarningInlineResolved", previewHeader.resolvedInline)
            .put("verticalChromeNaturalPx", verticalChrome).put("verticalMinimumContentPx", verticalMinimum)
            .put("metricsInlineNaturalWidthPx", inlineWidth).put("metricsInline", inlineMetrics)
            .put("chartNativeHeaderNaturalPx", headerHeight).put("chartActualPaintAxisMinimumPx", axisMinimum)
            .put("chartLegendNaturalPx", chart.findViewWithTag<android.view.View>("product/history/chart-legend").measuredHeight)
            .put("externalExplanationNaturalPx", requireNotNull(chart.embeddedExplanationView).measuredHeight)
            .put("externalExplanationTopMarginPx", (requireNotNull(chart.embeddedExplanationView).layoutParams as LayoutParams).topMargin)
            .put("explanationPlacement", "same_permanent_producer_view_after_complete_chart_before_notes")
            .put("chartOverlayPaddingPx", overlay.paddingTop + overlay.paddingBottom)
            .put("chartMinimumPx", chartMinimum).put("chartMarginsPx", chartMargins).put("fullNaturalPx", minimumContent + footer.measuredHeight)
            .put("minimumPlotDp", 96).put("localContentScroll", scrolling)
            .put("chartBudgetSource", "native_overlay_header + real_plot_96dp + renderer_bounds_actual_axis_bands")
        fun remeasure(view: android.view.View) {
            view.forceLayout()
            if (view is android.view.ViewGroup) (0 until view.childCount).forEach { remeasure(view.getChildAt(it)) }
        }
        remeasure(content); remeasure(footer)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** ScrollView measures its child UNSPECIFIED; this permanent host supplies the resolved real content box. */
private class EmbeddedHistoryContent(context: android.content.Context) : LinearLayout(context) {
    var resolvedHeightPx = 0
    init { orientation = VERTICAL; tag = "product/history/preview-content" }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, android.view.View.MeasureSpec.makeMeasureSpec(resolvedHeightPx, android.view.View.MeasureSpec.EXACTLY))
    }
}

/** Native fixed actions remain complete and wrap only as whole controls. */
private class EmbeddedHistoryActionsRow(context: android.content.Context, private val style: TaskProductVisualStyle,
    private val actions: LinearLayout) : android.view.ViewGroup(context) {
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    init { tag = "product/history/preview-footer-row"; addView(actions) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
        val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        var actionsWidth = 0
        for (index in 0 until actions.childCount) {
            val child = actions.getChildAt(index); child.forceLayout(); child.measure(natural, natural)
            actionsWidth += child.measuredWidth + if (index > 0) style.dp(16) else 0
        }
        val inline = actionsWidth <= available
        actions.orientation = if (inline) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (index in 0 until actions.childCount) {
            val child = actions.getChildAt(index)
            val params = child.layoutParams as LinearLayout.LayoutParams
            params.width = if (inline) -2 else -1
            params.marginStart = if (inline && index > 0) style.dp(16) else 0
            params.topMargin = if (!inline && index > 0) style.dp(8) else 0
            params.resolveLayoutDirection(actions.layoutDirection)
            child.forceLayout()
        }
        actions.forceLayout()
        actions.measure(android.view.View.MeasureSpec.makeMeasureSpec(if (inline) actionsWidth else available,
            android.view.View.MeasureSpec.EXACTLY), natural)
        setMeasuredDimension(available, resolveSize(actions.measuredHeight, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) 0 else width - actions.measuredWidth
        actions.layout(left, 0, left + actions.measuredWidth, actions.measuredHeight)
    }
}

/** The same permanent label/quantity nodes change arrangement only when the full page needs it. */
private class EmbeddedHistoryMetricCell(context: android.content.Context, private val gap: Int,
    private val label: android.widget.TextView, private val quantity: HistoryQuantity) : android.view.ViewGroup(context) {
    var inline = false
        set(value) { if (field != value) { field = value; forceLayout() } }
    private var resolvedInline = false
    init { addView(label); addView(quantity) }
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    fun inlineNaturalWidth(): Int {
        val free = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        label.forceLayout(); quantity.forceLayout()
        label.measure(free, free); quantity.measure(free, free)
        val result = label.measuredWidth + gap + quantity.measuredWidth
        label.forceLayout(); quantity.forceLayout(); forceLayout()
        return result
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
        val free = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        resolvedInline = inline && inlineNaturalWidth() <= available
        val bounded = android.view.View.MeasureSpec.makeMeasureSpec(available, android.view.View.MeasureSpec.AT_MOST)
        label.measure(bounded, free); quantity.measure(bounded, free)
        val wantedWidth = if (resolvedInline) label.measuredWidth + gap + quantity.measuredWidth else maxOf(label.measuredWidth, quantity.measuredWidth)
        val wantedHeight = if (resolvedInline) maxOf(label.measuredHeight, quantity.measuredHeight) else label.measuredHeight + gap + quantity.measuredHeight
        setMeasuredDimension(resolveSize(wantedWidth, widthMeasureSpec), resolveSize(wantedHeight, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        fun place(view: android.view.View, x: Int, y: Int) {
            val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - view.measuredWidth else x
            view.layout(left, y, left + view.measuredWidth, y + view.measuredHeight)
        }
        place(label, 0, if (resolvedInline) (height - label.measuredHeight) / 2 else 0)
        place(quantity, if (resolvedInline) label.measuredWidth + gap else 0,
            if (resolvedInline) (height - quantity.measuredHeight) / 2 else label.measuredHeight + gap)
    }
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
        val text = android.widget.TextView(this).apply {
            this.text = getString(R.string.task_history_flow_columns) + "\n\n" + rows
            setTextIsSelectable(true)
        }
        val dialog = AppDialog.show(AlertDialog.Builder(this).setTitle(R.string.task_history_flow_details)
            .setView(ScrollView(this).apply { addView(text) }).setPositiveButton(android.R.string.ok, null).create())
        (this as? MainActivity)?.trackDialog(dialog)
    }.isEnabled = points.isNotEmpty()
    val viewport = ScrollView(this).apply { addView(content) }
    val dialog = AppDialog.show(AlertDialog.Builder(this).setTitle(curve).setView(viewport)
        .setPositiveButton(android.R.string.ok, null).create())
    (this as? MainActivity)?.trackDialog(dialog)
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

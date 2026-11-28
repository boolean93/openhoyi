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
internal fun ThemedActivity.renderHistoryDetail(body: LinearLayout, shotId: String?, standalone: Boolean = false, openFullPage: (() -> Unit)? = null, visual: ExtractionHistoryVisualStyle? = null) {
    if (visual != null) { renderProductHistoryDetail(body, shotId, standalone, openFullPage, visual); return }
    val app = application as MobileApplication
    val permanent = shotId?.let { app.journalResult.getOrNull()?.find(it) }
    val entry = runCatching { app.history.entries.firstOrNull { it.id == shotId } }.getOrNull()
        ?: permanent?.observation?.historySummary()
    if (entry == null) {
        HoyiUi.label(this, HoyiUi.card(this, body), getString(R.string.history_missing), 17)
        return
    }
    val curve = if (entry.curveId == "manual") getString(R.string.extraction_manual_label) else
        runCatching { app.historicalCurveName(entry.curveId) }.getOrNull()
            ?: CurveCatalog.find(entry.curveId)?.name ?: entry.curveId
    if (!standalone) HoyiUi.label(this, body, curve, 18, true).apply {
        // The fixed details button exposes the unabridged name and metadata.
        maxLines = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) Int.MAX_VALUE else 2
        ellipsize = if (maxLines == 2) android.text.TextUtils.TruncateAt.END else null
        tag = "full-text-details"
    }
    val summary = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }
    body.addView(summary)
    if (standalone) HoyiUi.label(this, summary, "$curve · ${date(entry.startedAtMs)}", 14, muted = true).apply {
        maxLines = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) Int.MAX_VALUE else 1
        ellipsize = if (maxLines == 1) android.text.TextUtils.TruncateAt.END else null
        tag = "full-text-details"
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(16) }
    }
    HoyiUi.label(this, summary, status(entry.status), 14, true).apply {
        setTextColor(appColor(if (entry.status == ShotHistory.Status.UNKNOWN) R.color.mobile_danger else R.color.mobile_accent))
        layoutParams = if (standalone) LinearLayout.LayoutParams(-2, -2)
            else LinearLayout.LayoutParams(0, -2, 1f)
    }
    if (!standalone) HoyiUi.label(this, summary, date(entry.startedAtMs), 14, muted = true)
    val values = listOf(
        shotTimingSummary(entry),
        getString(R.string.history_end_scale) + " " + (entry.weightHundredthsGram?.let {
            "%.2f g".format(Locale.ROOT, it / 100.0)
        } ?: "—")
    )
    if (!standalone) HoyiUi.label(this, body, values.joinToString(" · "), 15, true).apply { tag = "history-legacy-timing-summary"; setPadding(0, dp(6), 0, dp(6)) }
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
            entry.timingLabel to (entry.timingValue?.let { "$it s" } ?: "—"),
            R.string.history_end_scale to (entry.weightHundredthsGram?.let {
                "%.2f g".format(Locale.ROOT, it / 100.0)
            } ?: "—")
        ).forEach { (label, value) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, dp(16), dp(16))
            }
            metrics.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            HoyiUi.label(this, cell, getString(label), 14, muted = true).apply { if (label == entry.timingLabel) tag = "history-legacy-timing-label" }
            HoyiUi.label(this, cell, value, TaskVisualTokens.LIVE_VALUE_SP).apply {
                if (label == entry.timingLabel) tag = "history-legacy-timing-value"
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
                maxLines = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) Int.MAX_VALUE else 1
                ellipsize = if (maxLines == 1) android.text.TextUtils.TruncateAt.END else null
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
                setTextColor(appColor(R.color.mobile_accent))
                val states = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@renderHistoryDetail, android.R.color.transparent, 8, R.color.mobile_accent))
                    addState(intArrayOf(), HoyiUi.shape(this@renderHistoryDetail, android.R.color.transparent, 8))
                }
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(appColor(R.color.mobile_accent_soft)),
                    states, HoyiUi.shape(this@renderHistoryDetail, android.R.color.white, 8))
            }
            if (standalone && leading) {
                parent.removeView(this)
                parent.addView(this, 0)
            }
        }
    }
    action(getString(R.string.history_outcome_title) + " / " + getString(R.string.journal_notes_title)) {
        showDetails(entry, curve, chart.points)
    }
    if (permanent != null) {
        action(getString(R.string.journal_review), primary = true) {
            startActivity(Intent(this, BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.id))
        }
        action(getString(R.string.journal_compare), leading = standalone) { showHistoryCupPicker(entry.id) }
    }
    openFullPage?.let { action(getString(R.string.ui_full_details), click = it) }
    Thread({
        val points = runCatching { app.samples.load(entry.id) }
        // View.post queues pre-attach results until the chart can receive them. A late
        // result for an already detached selection still cannot update the visible cup.
        chart.post {
            if (isFinishing || isDestroyed || !chart.isAttachedToWindow) return@post
            points.onSuccess {
                val recovered = app.recoverViewedSampleCoverage(entry, it)
                body.findViewWithTag<android.widget.TextView>("history-legacy-timing-summary")?.text =
                    (listOf(shotTimingSummary(recovered)) + values.drop(1)).joinToString(" · ")
                body.findViewWithTag<android.widget.TextView>("history-legacy-timing-label")?.text = getString(recovered.timingLabel)
                body.findViewWithTag<android.widget.TextView>("history-legacy-timing-value")?.text = recovered.timingValue?.let { "$it s" } ?: "—"

                chart.points = it
                chartStatus.text = if (it.isEmpty()) getString(R.string.history_no_samples) else
                    getString(if (entry.status == ShotHistory.Status.ENDED) R.string.history_samples_complete else R.string.history_samples_partial, it.size.toString())
            }.onFailure { chartStatus.text = getString(R.string.history_samples_failed) }
        }
    }, "history-detail-reader").start()
}
/** M5-only composition; models, source resolution and sample reader semantics remain identical. */
private fun ThemedActivity.renderProductHistoryDetail(body: LinearLayout, shotId: String?, standalone: Boolean,
    openFullPage: (() -> Unit)?, visual: ExtractionHistoryVisualStyle) {
    val style = visual.palette
    val app = application as MobileApplication
    val permanent = shotId?.let { app.journalResult.getOrNull()?.find(it) }
    val entry = runCatching { app.history.entries.firstOrNull { it.id == shotId } }.getOrNull()
        ?: permanent?.observation?.historySummary()
    if (entry == null) { style.add(body, style.text(getString(R.string.history_missing))); return }
    val curve = if (entry.curveId == "manual") getString(R.string.extraction_manual_label) else
        runCatching { app.historicalCurveName(entry.curveId) }.getOrNull()
            ?: CurveCatalog.find(entry.curveId)?.name ?: entry.curveId
    if (!standalone) {
        renderEmbeddedHistoryPreview(body, entry, curve, permanent, app.journalResult.isFailure, openFullPage, visual)
        return
    }
    val identity = HistoryVisualFlow(this, style.dp(16), style.dp(8)).apply { tag = "product/history/identity-meta" }
    val curveName = style.text(curve, TaskProductVisualStyle.body, "product/history/identity")
    val started = style.text(date(entry.startedAtMs), TaskProductVisualStyle.auxiliary, "product/history/date", true)
    identity.addView(curveName)
    identity.addView(started)
    if (entry.status != ShotHistory.Status.ENDED) identity.addView(style.text(status(entry.status), TaskProductVisualStyle.auxiliary, "product/history/status").apply {
        setTextColor(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningInk else style.accent)
        background = style.shape(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningSurface else style.selected)
        setPadding(style.dp(8), 0, style.dp(8), 0)
    })

    val plot = StandaloneHistoryPlot(this, visual)
    val viewport = StandaloneHistoryViewport(this, plot)
    body.addView(viewport, LinearLayout.LayoutParams(-1, 0, 1f))
    val top = HistoryToolbarRow(this, style.dp(16), style.dp(4)) {
        val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        curveName.measure(natural, natural)
        started.measure(natural, natural)
        curveName.measuredWidth + style.dp(16) + started.measuredWidth
    }
    top.addView(identity)
    top.addView(historyShotActions(style, entry.id))
    top.protectPhotoSection(style)
    style.add(plot, top)
    if (entry.status == ShotHistory.Status.UNKNOWN) style.add(plot, style.text(getString(R.string.history_unknown_warning),
        TaskProductVisualStyle.auxiliary, "product/history/warning").apply { setTextColor(visual.warningInk) }, 4)
    val metrics = HistoryVisualFlow(this, style.dp(24), style.dp(8)).apply { tag = "product/history/metrics" }

    fun metric(label: Int, value: String, unit: String?, id: String): HistoryQuantity {
        val labelView = style.text(getString(label), TaskProductVisualStyle.auxiliary, "$id/label", true)
        val quantity = HistoryQuantity(this, visual, id, large = false).apply { present(value, unit) }
        val cell = EmbeddedHistoryMetricCell(this, style.dp(6), labelView, quantity).apply { tag = "$id/cell"; inline = true }
        plot.metricCells += cell
        metrics.addView(cell); return quantity
    }
    val timingQuantity = metric(entry.timingLabel, entry.timingValue ?: "—",
        if (entry.timingValue != null) "s" else null, "product/history/elapsed")
    metric(R.string.history_end_scale, entry.weightHundredthsGram?.let { "%.2f".format(Locale.ROOT, it / 100.0) } ?: "—",
        if (entry.weightHundredthsGram != null) "g" else null, "product/history/weight")
    // This is a sample observation, not an inferred final outcome or stored new field.
    val sampledWater = metric(R.string.m5_last_sample_water, "—", null, "product/history/sampled-water")
    val chart = ShotChartView(this, visual, standalonePlotMinimumDp = 160f).apply { protectBackdropContent(); tag = "product/history/chart"; contentDescription = getString(R.string.extraction_chart) }
    plot.chart = chart
    plot.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = style.dp(4) })
    val summary = style.column("product/history/summary")
    summary.protectPhotoSection(style)
    style.add(summary, metrics)
    val chartStatus = style.text(getString(R.string.history_loading_samples), TaskProductVisualStyle.auxiliary, "product/history/chart-status", true)
    style.add(summary, chartStatus, 4)
    val actions = style.column("product/history/actions")
    actions.protectPhotoSection(style)
    val footer = HistoryToolbarRow(this, style.dp(16), style.dp(4)) {
        plot.metricCells.sumOf { it.inlineNaturalWidth() } + style.dp(24) * (plot.metricCells.size - 1)
    }.apply { tag = "product/history/summary-actions" }
    footer.addView(summary)
    footer.addView(EmbeddedHistoryActionsRow(this, style, actions, rowTag = "product/history/standalone-footer-row"))
    style.add(plot, footer, 4)
    fun action(title: String, id: String, glyph: String? = null, click: () -> Unit) {
        style.action(actions, title, id, top = 0, onClick = click).apply {
            glyph?.let { HomeVisualIcon.buttonIcon(this, it) }
        }
    }
    if (permanent != null) {
        action(getString(R.string.journal_review), "product/history/review", "edit") {
            startActivity(Intent(this, BrewReviewActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.id))
        }
    }
    if (permanent == null) style.add(summary, style.text(
        getString(if (app.journalResult.isFailure) R.string.journal_unavailable else R.string.journal_migrate_hint),
        TaskProductVisualStyle.auxiliary, "product/history/journal-status", true), 4)
    action(getString(R.string.history_outcome_title) + " / " + getString(R.string.journal_notes_title), "product/history/details", glyph = "chevron") {
        showDetails(entry, curve, chart.points)
    }
    Thread({
        val points = runCatching { app.samples.load(entry.id) }
        chart.post {
            if (isFinishing || isDestroyed || !chart.isAttachedToWindow) return@post
            points.onSuccess {
                val recovered = app.recoverViewedSampleCoverage(entry, it)
                chart.points = it
                timingQuantity.present(recovered.timingValue ?: "—", if (recovered.timingValue != null) "s" else null)
                metrics.findViewWithTag<android.widget.TextView>("product/history/elapsed/label")?.text = getString(recovered.timingLabel)
                sampledWater.present(it.lastOrNull()?.waterTenthsMl?.let { water -> "%.1f".format(Locale.ROOT, water / 10.0) } ?: "—",
                    if (it.isNotEmpty()) "ml" else null)
                sampledWater.contentDescription = getString(R.string.m5_last_sample_water) + ": " + sampledWater.contentDescription
                val discloseSamples = it.isEmpty() || entry.status != ShotHistory.Status.ENDED
                chartStatus.text = if (it.isEmpty()) getString(R.string.history_no_samples)
                    else if (discloseSamples) getString(R.string.history_samples_partial, it.size.toString()) else ""
                chartStatus.visibility = if (discloseSamples) android.view.View.VISIBLE else android.view.View.GONE
                (chartStatus.layoutParams as LinearLayout.LayoutParams).topMargin = if (discloseSamples) style.dp(4) else 0
            }.onFailure { chartStatus.text = getString(R.string.history_samples_failed) }
        }
    }, "history-detail-reader").start()
}
/** Rendering inputs for a collection preview, refreshed when its source data or notes change. */
internal data class HistoryPreview(
    val entry: ShotHistory.Entry,
    val permanent: BrewJournal.Entry?,
    val journalFailed: Boolean,
)

internal fun ThemedActivity.historyPreview(entry: ShotHistory.Entry): HistoryPreview {
    val journal = (application as MobileApplication).journalResult
    return HistoryPreview(entry, journal.getOrNull()?.find(entry.id), journal.isFailure)
}

internal fun ThemedActivity.renderHistoryPreview(
    body: LinearLayout, preview: HistoryPreview, recoverTiming: Boolean,
    openFullPage: () -> Unit, visual: ExtractionHistoryVisualStyle, onReadFailure: () -> Unit,
) {
    val entry = preview.entry
    val app = application as MobileApplication
    val curve = if (entry.curveId == "manual") getString(R.string.extraction_manual_label) else
        runCatching { app.historicalCurveName(entry.curveId) }.getOrNull()
            ?: CurveCatalog.find(entry.curveId)?.name ?: entry.curveId
    renderEmbeddedHistoryPreview(body, entry, curve, preview.permanent, preview.journalFailed,
        openFullPage, visual, recoverTiming, onReadFailure)
}

private fun ThemedActivity.historyReviewActionLabel(record: BrewJournal.Entry): String =
    getString(if (record.notes == BrewJournal.Notes()) R.string.journal_review else R.string.history_preview_edit_review)

private fun ThemedActivity.historyShotActions(
    style: TaskProductVisualStyle, shotId: String, preview: Boolean = false,
): EmbeddedHistoryActionsRow {
    val actions = style.row("product/history/shot-actions")
    val review = historyAiReviewButton(style, shotId)
    actions.addView(review, LinearLayout.LayoutParams(-2, -2))
    val compare = style.action(actions, getString(R.string.journal_compare), "product/history/compare", outline = true, top = 0) {
        showHistoryCupPicker(shotId)
    }.also { HomeVisualIcon.buttonIcon(it, "compare") }
    if (preview) listOf(review, compare).forEach { button ->
        button.minHeight = style.dp(48)
        button.minimumHeight = style.dp(48)
        button.setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
    }
    return EmbeddedHistoryActionsRow(this, style, actions, rowTag = "product/history/shot-actions-row", equalInlineHeight = preview)
}

/** Notes may change while the same cup's chart and scroll position remain on screen. */
internal fun ThemedActivity.refreshHistoryPreviewNotes(body: LinearLayout, preview: HistoryPreview): Boolean {
    val permanent = preview.permanent ?: return false
    val section = body.findViewWithTag<LinearLayout>("product/history/personal-review") ?: return false
    for ((label, value) in listOf(R.string.journal_bean to permanent.notes.beanName, R.string.journal_taste to permanent.notes.taste)) {
        val field = section.findViewWithTag<LinearLayout>("product/history/note/$label/row")
            .findViewWithTag<android.widget.TextView>("full-text-details")
        val actual = value?.takeIf(String::isNotBlank) ?: getString(R.string.journal_optional)
        field.text = actual
        field.contentDescription = actual
    }
    section.findViewWithTag<android.widget.Button>("product/history/review").text = historyReviewActionLabel(permanent)
    return true
}

/** One scroll viewport owns the primary actions, chart, and notes. */
private fun ThemedActivity.renderEmbeddedHistoryPreview(body: LinearLayout, entry: ShotHistory.Entry, curve: String,
    permanent: BrewJournal.Entry?, journalFailed: Boolean, openFullPage: (() -> Unit)?, visual: ExtractionHistoryVisualStyle,
    recoverTiming: Boolean = true, onReadFailure: () -> Unit = {}) {
    val style = visual.palette
    val app = application as MobileApplication
    val chart = ShotChartView(this, visual, embeddedHistory = true).apply { tag = "product/history/chart"; contentDescription = getString(R.string.extraction_chart) }
    val preview = EmbeddedHistoryPreview(this, visual, chart)
    body.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
    val title = style.row("product/history/title-row").apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = style.dp(48) }
    val identity = style.text(curve, TaskProductVisualStyle.title, "product/history/identity")
    title.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
    val more = android.widget.ImageButton(this).apply {
        tag = "product/history/more"; contentDescription = getString(R.string.history_preview_more_actions)
        setImageDrawable(HomeVisualIcon.drawable(this@renderEmbeddedHistoryPreview, "more", style.accent))
        setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12))
        minimumHeight = style.dp(48); minimumWidth = style.dp(48)
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(style.selected), null, style.shape(android.graphics.Color.WHITE))
        setOnClickListener {
            val choices = listOf(getString(R.string.history_outcome_title) + " / " + getString(R.string.journal_notes_title))
            val dialog = AppDialog.choices(this@renderEmbeddedHistoryPreview, contentDescription.toString(), choices) {
                this@renderEmbeddedHistoryPreview.showDetails(entry, curve, chart.points)
            }
            (this@renderEmbeddedHistoryPreview as? MainActivity)?.trackDialog(dialog)
        }
    }
    val meta = HistoryVisualFlow(this, style.dp(16), style.dp(8)).apply { tag = "product/history/identity-meta" }
    meta.addView(style.text(date(entry.startedAtMs), TaskProductVisualStyle.auxiliary, "product/history/date", true))
    meta.addView(style.text(status(entry.status), TaskProductVisualStyle.auxiliary, "product/history/status").apply {
        setTextColor(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningInk else style.accent)
        background = style.shape(if (entry.status == ShotHistory.Status.UNKNOWN) visual.warningSurface else style.selected)
        setPadding(style.dp(8), 0, style.dp(8), 0)
    })
    val chartStatus = style.text(getString(R.string.history_loading_samples), TaskProductVisualStyle.auxiliary, "product/history/chart-status", true)
    if (!preview.portrait) meta.addView(chartStatus)
    else chartStatus.protectPhotoSection(style)
    val warning = if (entry.status == ShotHistory.Status.UNKNOWN) style.text(getString(R.string.history_unknown_warning),
        TaskProductVisualStyle.auxiliary, "product/history/warning").apply {
            setTextColor(visual.warningInk)
            background = style.shape(visual.warningSurface)
        } else null
    val header = EmbeddedHistoryHeader(this, title, identity, historyShotActions(style, entry.id, preview = true),
        more, meta, warning, style)
    header.protectPhotoSection(style)
    style.add(preview.content, header)
    val metrics = preview.metrics
    metrics.protectPhotoSection(style)
    fun metric(label: Int, number: String, unit: String?, id: String): HistoryQuantity {
        val labelView = style.text(getString(label), TaskProductVisualStyle.auxiliary, "$id/label", true)
        val quantity = HistoryQuantity(this, visual, id, large = false).apply { present(number, unit) }
        val cell = EmbeddedHistoryMetricCell(this, style.dp(4), labelView, quantity).apply { tag = "$id/cell" }
        preview.metricCells += cell
        metrics.addView(cell)
        return quantity
    }
    val timingQuantity = metric(entry.timingLabel, entry.timingValue ?: "—",
        if (entry.timingValue != null) "s" else null, "product/history/elapsed")
    metric(R.string.history_end_scale, entry.weightHundredthsGram?.let { "%.2f".format(Locale.ROOT, it / 100.0) } ?: "—",
        if (entry.weightHundredthsGram != null) "g" else null, "product/history/weight")
    val sampledWater = metric(R.string.m5_last_sample_water, "—", null, "product/history/sampled-water")
    if (!preview.portrait) style.add(preview.content, metrics, 8)
    val chartTitle = style.text(getString(R.string.extraction_chart), TaskProductVisualStyle.action, "product/history/chart-title").apply {
        setPaddingRelative(style.dp(12), 0, 0, 0)
        if (openFullPage != null) minimumHeight = style.dp(48)
    }
    val chartHeading = openFullPage?.let { open ->
        HistoryToolbarRow(this, style.dp(8), style.dp(4)) {
            val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            chartTitle.measure(natural, natural)
            chartTitle.measuredWidth
        }.apply {
            addView(chartTitle)
            addView(style.button(android.widget.Button(context)).apply {
                setText(R.string.ui_full_details)
                tag = "product/history/full-page"
                HomeVisualIcon.buttonIcon(this, "expand")
                setOnClickListener { open() }
            })
        }
    } ?: chartTitle
    fun chartEdge(top: Boolean) = style.shape(visual.embeddedChartSurface).apply {
        val radius = style.dp(12).toFloat()
        cornerRadii = if (top) floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            else floatArrayOf(0f, 0f, 0f, 0f, radius, radius, radius, radius)
    }
    chartHeading.background = chartEdge(top = true)
    style.add(preview.content, chartHeading, 8)
    preview.content.addView(chart, LinearLayout.LayoutParams(-1, -2))
    // The actual chart producer retains this permanent view and updates its text from the same projection.
    // Only embedded History places it after the complete chart, before its notes; resize never reparents it.
    style.add(preview.content, requireNotNull(chart.embeddedExplanationView).apply { background = chartEdge(top = false) })
    if (preview.portrait) {
        style.add(preview.content, chartStatus, 8)
        style.add(preview.content, metrics, 12)
    }
    fun summary(label: Int, value: String?): LinearLayout = style.row("product/history/note/$label/row").apply {
        gravity = android.view.Gravity.CENTER_VERTICAL
        addView(style.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true), LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(16) })
        val actual = value?.takeIf { it.isNotBlank() } ?: getString(R.string.journal_optional)
        addView(style.text(actual, TaskProductVisualStyle.body, "full-text-details").apply {
            maxLines = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) Int.MAX_VALUE else 1
            ellipsize = if (maxLines == 1) android.text.TextUtils.TruncateAt.END else null; contentDescription = actual
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }
    val notes = style.column("product/history/personal-review")
    notes.protectPhotoSection(style)
    style.add(preview.content, notes, 12)
    if (permanent != null) {
        val notesTitle = style.text(getString(R.string.history_preview_personal_review), TaskProductVisualStyle.action)
        val notesHeader = HistoryToolbarRow(this, style.dp(8), style.dp(4)) {
            val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            notesTitle.measure(natural, natural)
            notesTitle.measuredWidth
        }
        notesHeader.addView(notesTitle)
        notesHeader.addView(style.button(android.widget.Button(this)).apply {
            text = historyReviewActionLabel(permanent)
            tag = "product/history/review"
            HomeVisualIcon.buttonIcon(this, "edit")
            setOnClickListener {
                startActivity(Intent(this@renderEmbeddedHistoryPreview, BrewReviewActivity::class.java)
                    .putExtra(BrewReviewActivity.SHOT_ID, entry.id))
            }
        })
        style.add(notes, notesHeader)
        style.add(notes, summary(R.string.journal_bean, permanent.notes.beanName), 4)
        style.add(notes, summary(R.string.journal_taste, permanent.notes.taste), 4)
    } else {
        style.add(notes, style.text(getString(if (journalFailed) R.string.journal_unavailable else R.string.journal_migrate_hint),
            TaskProductVisualStyle.auxiliary, secondary = true))
    }
    Thread({
        val points = runCatching { app.samples.load(entry.id) }
        chart.post {
            if (isFinishing || isDestroyed || !chart.isAttachedToWindow) return@post
            points.onSuccess {
                // An automatic preview can display sample coverage without persisting repairs.
                val recovered = if (recoverTiming) app.recoverViewedSampleCoverage(entry, it)
                else if (entry.observedMachineElapsedSeconds == null && entry.observedSampleCoverageMs == null)
                    entry.copy(observedSampleCoverageMs = observedSampleCoverage(it))
                else entry
                chart.points = it
                preview.formatPortraitLegend()
                timingQuantity.present(recovered.timingValue ?: "—", if (recovered.timingValue != null) "s" else null)
                metrics.findViewWithTag<android.widget.TextView>("product/history/elapsed/label")?.text = getString(recovered.timingLabel)
                sampledWater.present(it.lastOrNull()?.waterTenthsMl?.let { water -> "%.1f".format(Locale.ROOT, water / 10.0) } ?: "—", if (it.isNotEmpty()) "ml" else null)
                sampledWater.contentDescription = getString(R.string.m5_last_sample_water) + ": " + sampledWater.contentDescription
                chartStatus.text = if (it.isEmpty()) getString(R.string.history_no_samples) else
                    getString(if (entry.status == ShotHistory.Status.ENDED) R.string.history_samples_complete else R.string.history_samples_partial, it.size.toString())
            }.onFailure {
                chartStatus.text = getString(R.string.history_samples_failed)
                onReadFailure()
            }
        }
    }, "history-detail-reader").start()
}

/** One page scroll keeps every original control reachable when the natural content exceeds the window. */
private class StandaloneHistoryViewport(context: android.content.Context, private val content: StandaloneHistoryPlot) : ScrollView(context) {
    init {
        id = R.id.history_detail_scroll
        tag = "product/history/detail-scroll"; isFillViewport = false
        addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = android.view.View.MeasureSpec.getSize(heightMeasureSpec)
        if (content.viewportHeightPx != height) { content.viewportHeightPx = height; content.forceLayout() }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** Chart owns the remaining height; a 160dp real plot survives short windows through page scrolling. */
private class StandaloneHistoryPlot(context: android.content.Context, visual: ExtractionHistoryVisualStyle) : LinearLayout(context) {
    private val style = visual.palette
    val metricCells = mutableListOf<EmbeddedHistoryMetricCell>()
    lateinit var chart: ShotChartView
    var viewportHeightPx = 0
    val productLayoutEvidence = org.json.JSONObject()
    init { orientation = VERTICAL; tag = "product/history/standalone-plot-budget" }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
        val exact = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
        val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        var chrome = paddingTop + paddingBottom
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child === chart || child.visibility == android.view.View.GONE) continue
            child.forceLayout(); child.measure(exact, natural)
            val params = child.layoutParams as LayoutParams
            chrome += child.measuredHeight + params.topMargin + params.bottomMargin
        }
        val overlay = chart.getChildAt(0) as LinearLayout
        val header = requireNotNull(overlay.findViewWithTag<ScrollView>("product/extraction/chart-header"))
        val headerContent = header.getChildAt(0)
        headerContent.forceLayout(); headerContent.measure(exact, natural)
        val axesAndPlot = requireNotNull(chart.standaloneMinimumPlotHeight(width))
        val chartMinimum = axesAndPlot + (if (chart.standaloneLegendOverlapsAxes) 0 else headerContent.measuredHeight) + (overlay as ExtractionOverlayView).fixedAxisNoticeHeight(width) + overlay.paddingTop + overlay.paddingBottom
        val params = chart.layoutParams as LayoutParams
        val margins = params.topMargin + params.bottomMargin
        val contentHeight = maxOf(viewportHeightPx, chrome + margins + chartMinimum)
        params.height = contentHeight - chrome - margins; params.weight = 0f
        productLayoutEvidence.put("availableHeightPx", viewportHeightPx).put("availableWidthPx", width)
            .put("resolvedChromeNaturalPx", chrome).put("chartNativeHeaderNaturalPx", headerContent.measuredHeight)
            .put("chartActualPaintAxisMinimumPx", axesAndPlot).put("chartMinimumPx", chartMinimum)
            .put("minimumPlotDp", 160).put("actualRendererMode", chart.plotRendererMode)
            .put("localContentScroll", contentHeight > viewportHeightPx)
            .put("contentHeightPx", contentHeight).put("chartFirst", true)
            .put("chartBudgetSource", "complete_native_readouts_below_same_renderer_160dp_plot")
        super.onMeasure(widthMeasureSpec, android.view.View.MeasureSpec.makeMeasureSpec(contentHeight, android.view.View.MeasureSpec.EXACTLY))
    }
}

/** Wide panels place the actions beside the title; narrow panels wrap the same controls below the metadata. */
private class EmbeddedHistoryHeader(context: android.content.Context, private val titleRow: LinearLayout,
    private val identity: android.widget.TextView, private val actions: EmbeddedHistoryActionsRow,
    private val more: android.widget.ImageButton, private val meta: android.view.View,
    private val warning: android.widget.TextView?, style: TaskProductVisualStyle,
) : android.view.ViewGroup(context) {
    private val inlineGapPx = style.dp(16)
    private val rowGapPx = style.dp(8)
    private val moreSizePx = style.dp(48)
    var inlineWarning = false
        set(value) { if (field != value) { field = value; forceLayout() } }
    var resolvedInline = false; private set
    var titleNaturalWidthPx = 0; private set
    var warningNaturalWidthPx = 0; private set
    val moreOccupationPx = moreSizePx + rowGapPx
    var rowAvailableWidthPx = 0; private set
    private var leadingOccupationPx = 0
    private var inlineActions = false
    private var topRowHeightPx = 0
    init {
        tag = "product/history/preview-header"
        addView(titleRow); addView(actions); addView(more); addView(meta); warning?.let { addView(it) }
    }
    override fun generateDefaultLayoutParams() = LayoutParams(-1, -2)
    fun fitsInline(width: Int): Boolean = warning != null &&
        titleNaturalWidthPx + inlineGapPx + warningNaturalWidthPx <= minOf(width, rowAvailableWidthPx)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value.coerceAtLeast(0), MeasureSpec.EXACTLY)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        identity.forceLayout(); identity.measure(free, free)
        titleNaturalWidthPx = identity.measuredWidth
        warning?.let { it.forceLayout(); it.measure(free, free) }
        warningNaturalWidthPx = warning?.measuredWidth ?: 0
        actions.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), free)
        more.measure(exact(moreSizePx), exact(moreSizePx))
        // The compact portrait host can insert its fixed-width back button before the title.
        val leading = titleRow.getChildAt(0)
        leadingOccupationPx = if (leading !== identity && leading.visibility != android.view.View.GONE) {
            val params = leading.layoutParams as LinearLayout.LayoutParams
            params.width + params.marginStart + params.marginEnd
        } else 0
        val titleRowWidth = width - moreOccupationPx
        inlineActions = leadingOccupationPx + titleNaturalWidthPx + inlineGapPx + actions.measuredWidth <= titleRowWidth
        val titleWidth = titleRowWidth - if (inlineActions) actions.measuredWidth + inlineGapPx else 0
        rowAvailableWidthPx = (titleWidth - leadingOccupationPx).coerceAtLeast(0)
        resolvedInline = inlineWarning && fitsInline(width)
        (identity.layoutParams as LinearLayout.LayoutParams).apply {
            this.width = if (resolvedInline) titleNaturalWidthPx else 0
            weight = if (resolvedInline) 0f else 1f
        }
        titleRow.forceLayout(); titleRow.measure(exact(titleWidth), free)
        meta.forceLayout(); meta.measure(exact(width), free)
        warning?.let {
            it.forceLayout()
            it.measure(exact(if (resolvedInline) warningNaturalWidthPx else width), free)
        }
        topRowHeightPx = maxOf(titleRow.measuredHeight, more.measuredHeight, if (inlineActions) actions.measuredHeight else 0,
            if (resolvedInline) warning?.measuredHeight ?: 0 else 0)
        val height = topRowHeightPx + rowGapPx + meta.measuredHeight +
            (if (warning != null && !resolvedInline) rowGapPx + warning.measuredHeight else 0) +
            (if (!inlineActions) rowGapPx + actions.measuredHeight else 0)
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        fun place(view: android.view.View, start: Int, top: Int) {
            val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - start - view.measuredWidth else start
            view.layout(left, top, left + view.measuredWidth, top + view.measuredHeight)
        }
        fun center(view: android.view.View) = (topRowHeightPx - view.measuredHeight) / 2
        place(titleRow, 0, center(titleRow))
        place(more, width - more.measuredWidth, center(more))
        val metaTop = topRowHeightPx + rowGapPx
        place(meta, 0, metaTop)
        var belowMeta = metaTop + meta.measuredHeight
        warning?.let {
            if (resolvedInline) place(it, leadingOccupationPx + titleNaturalWidthPx + inlineGapPx, center(it))
            else {
                place(it, 0, belowMeta + rowGapPx)
                belowMeta += rowGapPx + it.measuredHeight
            }
        }
        if (inlineActions) place(actions, width - moreOccupationPx - actions.measuredWidth, center(actions))
        else place(actions, 0, belowMeta + rowGapPx)
    }
}

/** Permanent layout: resize changes only native measure inputs, never a source or parent. */
private class EmbeddedHistoryPreview(context: android.content.Context, visual: ExtractionHistoryVisualStyle,
    private val chart: ShotChartView) : LinearLayout(context) {
    private val style = visual.palette
    val portrait = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val content = EmbeddedHistoryContent(context).apply { naturalHeight = portrait }
    val metricCells = mutableListOf<EmbeddedHistoryMetricCell>()
    val metrics = HistoryVisualFlow(context, style.dp(24), style.dp(8)).apply { tag = "product/history/metrics" }
    val productLayoutEvidence = org.json.JSONObject()
    private val viewport = ScrollView(context).apply { tag = "product/history/preview-scroll"; addView(content) }
    // Exactly the production renderer's actual 14sp/400 Paint policy, not a fixed dp guess.
    private val paintBudget = ProductHistoryChartRenderer(context, visual, embeddedHistory = true)
    init {
        orientation = VERTICAL; tag = "product/history/preview"
        addView(viewport, LayoutParams(-1, 0, 1f))
    }
    fun formatPortraitLegend() {
        if (!portrait) return
        ChartMetric.values().forEach { metric ->
            val button = chart.findViewWithTag<android.widget.Button>("chart-metric:$metric") ?: return@forEach
            if (!button.isEnabled) return@forEach
            val label = context.getString(when (metric) {
                ChartMetric.PRESSURE -> R.string.task_chart_pressure
                ChartMetric.WATER -> R.string.task_chart_water
                ChartMetric.YIELD -> R.string.chart_series_yield
                ChartMetric.WEIGHT -> R.string.task_chart_weight
                ChartMetric.FLOW -> R.string.task_chart_flow
                ChartMetric.TEMPERATURE -> R.string.task_chart_temperature
            })
            button.text = "$label\n${metric.unit}"
            val swatch = button.compoundDrawablesRelative[0] ?: button.compoundDrawablesRelative[1]
            swatch?.setBounds(0, 0, style.dp(18), style.dp(2))
            button.setCompoundDrawablesRelative(null, swatch, null, null)
            button.gravity = android.view.Gravity.CENTER
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (portrait) {
            val width = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
            val available = android.view.View.MeasureSpec.getSize(heightMeasureSpec)
            val exact = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
            val free = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
            var beforeChart = 0
            for (index in 0 until content.indexOfChild(chart)) {
                val child = content.getChildAt(index)
                val params = child.layoutParams as LayoutParams
                child.measure(exact, free)
                beforeChart += child.measuredHeight + params.topMargin + params.bottomMargin
            }
            val overlay = chart.getChildAt(0) as LinearLayout
            val nativeHeader = (overlay.getChildAt(0) as ScrollView).getChildAt(0)
            nativeHeader.measure(exact, free)
            val axisOverhead = paintBudget.minimumEmbeddedHistoryHeight() - style.dp(96)
            val fixed = nativeHeader.measuredHeight + axisOverhead + overlay.paddingTop + overlay.paddingBottom
            val plot = (available - beforeChart - fixed).coerceIn(style.dp(160), style.dp(240))
            val lp = chart.layoutParams as LayoutParams
            lp.height = fixed + plot; lp.weight = 0f
            chart.forceLayout(); content.forceLayout(); viewport.forceLayout()
            productLayoutEvidence.put("portraitChartFirst",true).put("viewportPx",available)
                .put("headerNaturalPx",beforeChart).put("requestedPlotPx",plot)
                .put("preservedNativeAxisAndLegendPx",fixed).put("chartHeightPx",lp.height)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        val width = (android.view.View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val available = (android.view.View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val exact = android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY)
        val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
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
        metrics.horizontalGapPx = style.dp(24)
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
        val verticalMinimum = verticalChrome + chartMargins + chartMinimum
        val defaultGap = style.dp(24); val compactGap = style.dp(16)
        val naturalSizes = metricCells.map { cell -> listOf(cell.naturalSize(false), cell.naturalSize(true)) }
        val inlineWidth = naturalSizes.sumOf { it[1].first } + defaultGap * (metricCells.size - 1).coerceAtLeast(0)
        fun measureMetrics(mask: Int, gap: Int): HistoryMetricArrangement {
            metricCells.forEachIndexed { index, cell -> cell.inline = (mask and (1 shl index)) != 0; cell.forceLayout() }
            metrics.horizontalGapPx = gap
            metrics.forceLayout(); metrics.measure(exact, natural)
            val sizes = naturalSizes.mapIndexed { index, modes -> modes[if ((mask and (1 shl index)) != 0) 1 else 0] }
            val resolved = metricCells.withIndex().fold(0) { bits, (index, cell) ->
                if (cell.resolvedInline) bits or (1 shl index) else bits }
            return HistoryMetricArrangement(mask, gap, sizes.map { it.first }, sizes.map { it.second },
                metricCells.map { it.measuredWidth }, metricCells.map { it.measuredHeight }, resolved, metrics.measuredHeight)
        }
        val originalMetrics = measureMetrics(0, defaultGap)
        val candidates = mutableListOf<HistoryMetricArrangement>()
        val metricsBudgetInsufficient = verticalMinimum > available
        if (metricsBudgetInsufficient) {
            for (mask in 0 until (1 shl metricCells.size)) candidates += measureMetrics(mask, defaultGap)
            if (selectHistoryMetricArrangement(true, width, originalMetrics, candidates, compactGap).arrangement === originalMetrics)
                for (mask in 0 until (1 shl metricCells.size)) candidates += measureMetrics(mask, compactGap)
        }
        val metricsSelection = selectHistoryMetricArrangement(metricsBudgetInsufficient, width, originalMetrics, candidates, compactGap)
        val selectedMetrics = measureMetrics(metricsSelection.arrangement.inlineMask, metricsSelection.arrangement.gapPx)
        val inlineMetrics = selectedMetrics.resolvedInlineMask == (1 shl metricCells.size) - 1
        fun metricEvidence(value: HistoryMetricArrangement) = org.json.JSONObject()
            .put("inlineMask", value.inlineMask).put("gapPx", value.gapPx)
            .put("naturalWidthsPx", org.json.JSONArray(value.naturalWidthsPx)).put("naturalHeightsPx", org.json.JSONArray(value.naturalHeightsPx))
            .put("measuredWidthsPx", org.json.JSONArray(value.measuredWidthsPx)).put("measuredHeightsPx", org.json.JSONArray(value.measuredHeightsPx))
            .put("resolvedInlineMask", value.resolvedInlineMask).put("measuredHeightPx", value.measuredHeightPx)
            .put("totalNaturalWidthPx", value.totalNaturalWidthPx).put("completeSingleRow", value.completeSingleRow(width))
        val metricsEvidence = org.json.JSONObject().put("schema", "EMBEDDED_HISTORY_MEASURED_METRICS_V1")
            .put("budgetInsufficient", metricsBudgetInsufficient).put("availableWidthPx", width)
            .put("defaultGapPx", defaultGap).put("compactGapPx", compactGap).put("layoutDirection", metrics.layoutDirection)
            .put("cellIdentities", org.json.JSONArray(metricCells.map { System.identityHashCode(it) }))
            .put("original", metricEvidence(originalMetrics))
            .put("candidates", org.json.JSONArray(candidates.map { metricEvidence(it) }))
            .put("selected", metricEvidence(selectedMetrics)).put("selectionReason", metricsSelection.reason)
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
        val explanationHeight = requireNotNull(chart.embeddedExplanationView).measuredHeight
        val roomThroughExplanation = available - (firstScreenResolvedPx - chartMinimum) - explanationHeight
        val preferredChartHeight = minOf(chartMinimum + style.dp(128), roomThroughExplanation)
        chartParams.height = maxOf(available - chrome - chartMargins, preferredChartHeight)
            .coerceIn(chartMinimum, chartMinimum + style.dp(176))
        content.resolvedHeightPx = chrome + chartMargins + chartParams.height
        val scrolling = content.resolvedHeightPx > available
        productLayoutEvidence.put("availableHeightPx", available)
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
            .put("metricsArrangement", metricsEvidence).put("metricsResolvedHeightPx", metrics.measuredHeight)
            .put("chartNativeHeaderNaturalPx", headerHeight).put("chartActualPaintAxisMinimumPx", axisMinimum)
            .put("chartLegendNaturalPx", chart.findViewWithTag<android.view.View>("product/history/chart-legend").measuredHeight)
            .put("externalExplanationNaturalPx", requireNotNull(chart.embeddedExplanationView).measuredHeight)
            .put("externalExplanationTopMarginPx", (requireNotNull(chart.embeddedExplanationView).layoutParams as LayoutParams).topMargin)
            .put("explanationPlacement", "same_permanent_producer_view_after_complete_chart_before_notes")
            .put("chartOverlayPaddingPx", overlay.paddingTop + overlay.paddingBottom)
            .put("chartMinimumPx", chartMinimum).put("chartMarginsPx", chartMargins).put("fullNaturalPx", minimumContent)
            .put("minimumPlotDp", 96).put("localContentScroll", scrolling)
            .put("chartBudgetSource", "native_overlay_header + real_plot_96dp + renderer_bounds_actual_axis_bands")
        fun remeasure(view: android.view.View) {
            view.forceLayout()
            if (view is android.view.ViewGroup) (0 until view.childCount).forEach { remeasure(view.getChildAt(it)) }
        }
        remeasure(content); viewport.forceLayout()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

/** ScrollView measures its child UNSPECIFIED; this permanent host supplies the resolved real content box. */
private class EmbeddedHistoryContent(context: android.content.Context) : LinearLayout(context) {
    var resolvedHeightPx = 0
    var naturalHeight = false
    init { orientation = VERTICAL; tag = "product/history/preview-content" }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, android.view.View.MeasureSpec.makeMeasureSpec(if (naturalHeight) 0 else resolvedHeightPx,
            if (naturalHeight) android.view.View.MeasureSpec.UNSPECIFIED else android.view.View.MeasureSpec.EXACTLY))
    }
}

/** Action groups are measured from their labels and wrap only as whole controls. */
internal fun historyActionsNaturalWidth(widths:List<Int>, gap:Int):Int =
    widths.sum() + gap * (widths.size - 1).coerceAtLeast(0)

private class EmbeddedHistoryActionsRow(context: android.content.Context, private val style: TaskProductVisualStyle,
    private val actions: LinearLayout, rowTag: String,
    private val equalInlineHeight: Boolean = false,
) : android.view.ViewGroup(context) {
    val productLayoutEvidence = org.json.JSONObject()
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    init { tag = rowTag; addView(actions) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (android.view.View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val natural = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        val widths = mutableListOf<Int>()
        var tallest = 0
        for (index in 0 until actions.childCount) {
            val child = actions.getChildAt(index); child.forceLayout(); child.measure(natural, natural)
            widths += child.measuredWidth
            tallest = maxOf(tallest, child.measuredHeight)
        }
        val actionsWidth = historyActionsNaturalWidth(widths,style.dp(16))
        val inline = actionsWidth <= available
        actions.orientation = if (inline) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        for (index in 0 until actions.childCount) {
            val child = actions.getChildAt(index)
            val params = child.layoutParams as LinearLayout.LayoutParams
            params.width = if (inline) -2 else -1
            if (equalInlineHeight) params.height = if (inline) tallest else -2
            params.marginStart = if (inline && index > 0) style.dp(16) else 0
            params.topMargin = if (!inline && index > 0) style.dp(8) else 0
            params.resolveLayoutDirection(actions.layoutDirection)
            child.forceLayout()
        }
        actions.forceLayout()
        actions.measure(android.view.View.MeasureSpec.makeMeasureSpec(if (inline) actionsWidth else available,
            android.view.View.MeasureSpec.EXACTLY), natural)
        val resolvedHeight = actions.measuredHeight + paddingTop + paddingBottom
        productLayoutEvidence.put("availableWidthPx",available).put("naturalActionWidthsPx",org.json.JSONArray(widths))
            .put("naturalActionsWidthPx",actionsWidth).put("gapPx",style.dp(16)).put("inline",inline)
            .put("actionsHeightPx",actions.measuredHeight).put("footerHeightPx",resolvedHeight)
            .put("layoutDirection",layoutDirection).put("actionCount",actions.childCount)
        setMeasuredDimension(resolveSize((if (inline) actionsWidth else available) + paddingLeft + paddingRight,widthMeasureSpec), resolveSize(resolvedHeight, heightMeasureSpec))
        productLayoutEvidence.put("measuredFooterHeightPx",measuredHeight)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) paddingLeft else width - paddingRight - actions.measuredWidth
        actions.layout(left, paddingTop, left + actions.measuredWidth, paddingTop + actions.measuredHeight)
    }
}

/** The same permanent label/quantity nodes change arrangement only when the full page needs it. */
private class EmbeddedHistoryMetricCell(context: android.content.Context, private val gap: Int,
    private val label: android.widget.TextView, private val quantity: HistoryQuantity) : android.view.ViewGroup(context) {
    var inline = false
        set(value) { if (field != value) { field = value; forceLayout() } }
    var resolvedInline = false; private set
    init { addView(label); addView(quantity) }
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    fun inlineNaturalWidth(): Int = naturalSize(true).first
    fun naturalSize(inlineMode: Boolean): Pair<Int, Int> {
        val free = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        label.forceLayout(); quantity.forceLayout()
        label.measure(free, free); quantity.measure(free, free)
        val result = if (inlineMode) (label.measuredWidth + gap + quantity.measuredWidth) to maxOf(label.measuredHeight, quantity.measuredHeight)
            else maxOf(label.measuredWidth, quantity.measuredWidth) to (label.measuredHeight + gap + quantity.measuredHeight)
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
private fun ThemedActivity.showDetails(original: ShotHistory.Entry, curve: String, points: List<ShotPoint>) {
    val app = application as MobileApplication
    val journal = app.journalResult
    val permanent = journal.getOrNull()?.find(original.id)
    val entry = runCatching { app.history.entries.firstOrNull { it.id == original.id } }.getOrNull()
        ?: permanent?.observation?.historySummary() ?: original
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(12), dp(20), dp(12))
    }
    HoyiUi.label(this, content, (if ((entry.timingEvidenceVersion > 0 || entry.observedSampleCoverageMs != null)) shotRecordElapsed(entry) + "\n" else "") + getString(R.string.history_timing_explanation), 13, muted = true)
    val review = HoyiUi.label(this, content, "", 14).apply {
        tag = "product/history/automatic-shot-review"
        visibility = android.view.View.GONE
        accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    val unbindReview = app.shotReviews.bindDetails(review, entry.id)
    review.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: android.view.View) = Unit
        override fun onViewDetachedFromWindow(v: android.view.View) { unbindReview(); v.removeOnAttachStateChangeListener(this) }
    })
    HoyiUi.label(this, content, curve, 18, true)
    HoyiUi.label(this, content, status(entry.status), 17, true)
    HoyiUi.label(this, content, getString(R.string.history_started_at, date(entry.startedAtMs)), 14)
    entry.endedAtMs?.let { HoyiUi.label(this, content, getString(R.string.history_ended_at, date(it)), 14) }
    entry.slot?.takeIf { it in 1..5 }?.let { HoyiUi.label(this, content, getString(R.string.history_slot, it.toString()), 14) }
    if (entry.weightHundredthsGram != null) HoyiUi.label(this, content, getString(R.string.history_scale_disclaimer), 14, muted = true)
    HoyiUi.label(this, content, entry.reason?.let(::reasonLabel) ?: getString(R.string.history_no_reason), 15, true)
    if (permanent != null) journalNotes(HoyiUi.card(this, content, getString(R.string.journal_notes_title)), permanent.notes)
    else HoyiUi.label(this, content, getString(if (journal.isFailure) R.string.journal_unavailable else R.string.journal_migrate_hint), 14)
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

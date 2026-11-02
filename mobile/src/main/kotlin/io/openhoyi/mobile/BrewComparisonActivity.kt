package io.openhoyi.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

class BrewComparisonActivity : ThemedActivity() {
    companion object {
        const val FIRST_SHOT_ID = "firstShotId"
        const val SECOND_SHOT_ID = "secondShotId"
        private const val METRIC = "comparisonMetric"
    }
    private data class SourceKey(val observation: BrewJournal.Observation)
    private sealed interface Samples {
        data object Loading : Samples
        data class Loaded(val points: List<ShotPoint>) : Samples
        data object Failed : Samples
    }
    private var firstId: String? = null
    private var secondId: String? = null
    private var selectedMetric = ChartMetric.PRESSURE
    private var refresh: () -> Unit = {}
    private var destroyed = false
    private val readers = Executors.newFixedThreadPool(2)
    private val samples = mutableMapOf<SourceKey, Samples>()
    private lateinit var chart: TwoCupChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val visual = ExtractionHistoryVisualStyle(this)
        val style = visual.palette
        val body = style.page(this, getString(R.string.journal_compare_title), getString(R.string.journal_compare_hint))
        val journal = journal(body) ?: return
        val entries = journal.entries()
        firstId = if (savedInstanceState?.containsKey(FIRST_SHOT_ID) == true) savedInstanceState.getString(FIRST_SHOT_ID)
            else intent.getStringExtra(FIRST_SHOT_ID) ?: entries.getOrNull(0)?.observation?.id
        secondId = if (savedInstanceState?.containsKey(SECOND_SHOT_ID) == true) savedInstanceState.getString(SECOND_SHOT_ID)
            else intent.getStringExtra(SECOND_SHOT_ID) ?: entries.getOrNull(1)?.observation?.id
        selectedMetric = savedInstanceState?.getString(METRIC)?.let { name -> ChartMetric.entries.firstOrNull { it.name == name } }
            ?: ChartMetric.PRESSURE
        if (entries.size < 2) {
            HoyiUi.label(this, body, getString(R.string.journal_compare_need_two), 17)
            if (listOfNotNull(firstId, secondId).any { journal.find(it) == null })
                HoyiUi.label(this, body, getString(R.string.task_comparison_missing_selection), 14)
            return
        }

        val workspace = TaskComparisonLayout(this, visual)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        val toolbarBody = workspace.toolbar
        val support = workspace.details
        val warning = style.text("", TaskProductVisualStyle.auxiliary, "product/comparison/warning").apply {
            visibility = View.GONE; setTextColor(visual.warningInk)
            toolbarBody.addView(this)
        }
        val metricSelector = ComparisonMetricTabs(this, selectedMetric, ::metricName, { metric ->
            selectedMetric = metric; chart.metric = metric; refresh()
        }, visual)
        toolbarBody.addView(metricSelector, LinearLayout.LayoutParams(-1, -2))
        val sourceLegend = HoyiUi.label(this, toolbarBody, "", 14).apply {
            tag = "comparison-source-legend"
            gravity = android.view.Gravity.END
            setTextColor(getColor(R.color.chart_pressure))
        }
        val metricLine = HistoryToolbarRow(this, dp(16), dp(8)) {
            (0 until metricSelector.childCount).sumOf { index ->
                val child = metricSelector.getChildAt(index) as TextView
                maxOf(dp(48), kotlin.math.ceil(child.paint.measureText(child.text.toString())).toInt() + child.compoundPaddingLeft + child.compoundPaddingRight)
            }
        }
        toolbarBody.removeView(metricSelector); toolbarBody.removeView(sourceLegend)
        metricLine.addView(metricSelector); metricLine.addView(sourceLegend)
        toolbarBody.addView(metricLine, LinearLayout.LayoutParams(-1, -2))
        chart = TwoCupChartView(this, visual).apply { metric = selectedMetric }
        workspace.setChart(chart)
        val selectors = ComparisonCupSelectors(this, dp(16), dp(8)).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            toolbarBody.addView(this, 0)
        }
        workspace.productLayoutEvidence.put("cupSelectors", selectors.productLayoutEvidence)
        val choiceIds = listOf<String?>(null) + entries.map { it.observation.id }
        val labels = listOf(getString(R.string.task_comparison_choose_cup)) + entries.map {
            getString(R.string.journal_selector_title, journalDate(it.observation.startedAtMs), journalCurve(it.observation.curveId))
        }
        fun cupSelector(label: Int, selected: String?, changed: (String?) -> Unit): (String?) -> Unit {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
                background = style.shape(style.surface, true)
            }
            selectors.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            style.add(cell, style.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true))
            // Track the displayed position separately from identity: an absent ID initially
            // shares the placeholder position, but must survive its automatic initial callback.
            var displayedPosition = choiceIds.indexOf(selected).coerceAtLeast(0)
            val spinner = Spinner(this).apply {
                tag = if (label == R.string.journal_first) "comparison-first" else "comparison-second"
                adapter = visual.choiceAdapter(this@BrewComparisonActivity, labels)
                contentDescription = getString(label)
                minimumHeight = dp(52)
                cell.addView(this, LinearLayout.LayoutParams(-1, -2))
                setSelection(displayedPosition)
                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: AdapterView<*>?, item: View?, position: Int, id: Long) {
                        if (position != displayedPosition) {
                            displayedPosition = position
                            changed(choiceIds[position])
                        }
                    }
                }
            }
            return { identity ->
                // Swapping is an identity operation, not a user clearing an absent record.
                displayedPosition = choiceIds.indexOf(identity).coerceAtLeast(0)
                spinner.setSelection(displayedPosition)
            }
        }
        val displayFirstSelection = cupSelector(R.string.journal_first, firstId) { id ->
            if (firstId != id) { firstId = id; refresh() }
        }
        val displaySecondSelection = cupSelector(R.string.journal_second, secondId) { id ->
            if (secondId != id) { secondId = id; refresh() }
        }
        HoyiUi.button(this, selectors, getString(R.string.task_comparison_swap)) {
            val previous = firstId; firstId = secondId; secondId = previous
            displayFirstSelection(firstId)
            displaySecondSelection(secondId)
            refresh()
        }.apply {
            tag = "comparison-swap"; style.button(this)
            layoutParams = LinearLayout.LayoutParams(-2, -2)
            selectors.removeView(this)
            selectors.addView(this, 1)
        }
        val shared = HoyiUi.label(this, toolbarBody, "", 14).apply {
            setPadding(0, dp(4), 0, dp(4))
            toolbarBody.removeView(this)
            toolbarBody.addView(this, 1, LinearLayout.LayoutParams(-1, -2))
        }
        val selectionLine = HistoryToolbarRow(this, dp(16), dp(8)) { selectors.naturalInlineWidth() }
        toolbarBody.removeView(selectors); toolbarBody.removeView(shared)
        selectionLine.addView(selectors); selectionLine.addView(shared)
        toolbarBody.addView(selectionLine, 0, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        val summaryTable = ComparisonSummaryTable(this, visual)
        support.addView(summaryTable, LinearLayout.LayoutParams(-1, -2))
        val recordActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
            support.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        val sourceCards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
            support.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        val sourceSummaries = mutableListOf<TextView>()
        val detailButtons = mutableListOf<Button>()
        val noteButtons = mutableListOf<Button>()
        listOf(R.string.journal_first, R.string.journal_second).forEachIndexed { index, label ->
            val cup = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = style.shape(style.surface, true)
            }
            sourceCards.addView(cup, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(16) })
            sourceSummaries += HoyiUi.label(this, cup, "", 14).apply { tag = "comparison-source-${index + 1}" }
            detailButtons += HoyiUi.button(this, recordActions, getString(label) + " · " + getString(R.string.journal_detail)) {
                val entry = journal.find((if (index == 0) firstId else secondId) ?: return@button) ?: return@button
                startActivity(Intent(this, HistoryDetailActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.observation.id))
            }.apply {
                style.button(this)
                HomeVisualIcon.buttonIcon(this, "chevron", atEnd = true)
                gravity = android.view.Gravity.CENTER_VERTICAL or
                    if (index == 0) android.view.Gravity.START else android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(16) }
            }
            noteButtons += HoyiUi.button(this, cup, getString(label) + " · " + getString(R.string.journal_notes_title)) {
                val entry = journal.find((if (index == 0) firstId else secondId) ?: return@button) ?: return@button
                val notes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                journalNotes(notes, entry.notes)
                val values = (0 until notes.childCount).map { (notes.getChildAt(it) as TextView).text.toString() }
                beanDetails(getString(label), journalObservationText(entry.observation) + "\n\n" + values.joinToString("\n"))
            }.apply { style.button(this); HomeVisualIcon.buttonIcon(this, "info") }
        }
        style.apply(shared, TaskProductVisualStyle.auxiliary, true)
        style.apply(sourceLegend, TaskProductVisualStyle.auxiliary)
        sourceLegend.setTextColor(visual.metric(selectedMetric))
        sourceSummaries.forEach { style.apply(it, TaskProductVisualStyle.auxiliary, true) }
        fun ensureSamples(entry: BrewJournal.Entry?) {
            entry ?: return
            val key = SourceKey(entry.observation)
            if (samples.containsKey(key)) return
            samples[key] = Samples.Loading
            readers.execute {
                val loaded = runCatching {
                    (application as MobileApplication).samples.load(entry.observation.id).also { ExtractionReference.align(it) }
                }
                runOnUiThread {
                    if (destroyed || isFinishing || isDestroyed) return@runOnUiThread
                    samples[key] = loaded.fold({ Samples.Loaded(it) }, { Samples.Failed })
                    // Cache an old selection, but never project it onto a newly selected cup.
                    val selected = listOfNotNull(firstId?.let(journal::find), secondId?.let(journal::find))
                    if (selected.any { SourceKey(it.observation) == key }) refresh()
                }
            }
        }
        fun render() {
            val first = firstId?.let(journal::find)
            val second = secondId?.let(journal::find)
            val same = first != null && second != null && first.observation.id == second.observation.id
            val valid = first != null && second != null && !same
            val selected = listOf(first, second)
            selected.forEach(::ensureSamples)
            val points = selected.map { entry ->
                entry?.let { (samples[SourceKey(it.observation)] as? Samples.Loaded)?.points }.orEmpty()
            }
            chart.firstPoints = if (valid) points[0] else emptyList()
            chart.secondPoints = if (valid) points[1] else emptyList()
            chart.comparisonMessage = when {
                first == null || second == null -> getString(R.string.task_comparison_missing_selection)
                same -> getString(R.string.journal_choose_different)
                else -> null
            }
            sourceLegend.setTextColor(visual.metric(selectedMetric))
            sourceLegend.setIfChanged("A ━ " + getString(R.string.journal_first) + "    B ┄ " + getString(R.string.journal_second))
            sourceLegend.contentDescription = selected.mapIndexed { index, entry ->
                val source = if (index == 0) "A ━ " + getString(R.string.journal_first)
                    else "B ┄ " + getString(R.string.journal_second)
                source + " · " + if (entry == null) getString(R.string.task_comparison_missing_selection) else
                    journalDate(entry.observation.startedAtMs) + " · " + journalStatus(entry.observation.status)
            }.joinToString("\n")
            val unknown = selected.any { it?.observation?.status == "UNKNOWN" }
            warning.visibility = if (unknown) View.VISIBLE else View.GONE
            warning.setIfChanged(if (unknown) getString(R.string.history_unknown_warning) else "")
            shared.setIfChanged(if (first != null && second != null && !same) {
                getString(when {
                    first.notes.beanId == null || second.notes.beanId == null -> R.string.journal_bean_unknown
                    first.notes.beanId == second.notes.beanId -> R.string.journal_same_bean
                    else -> R.string.journal_different_bean
                }) + " · " + getString(if (first.observation.curveId == second.observation.curveId)
                    R.string.journal_same_curve else R.string.journal_different_curve)
            } else chart.comparisonMessage.orEmpty())
            selected.forEachIndexed { index, entry ->
                summaryTable.update(index, entry)
                detailButtons[index].isEnabled = entry != null
                noteButtons[index].isEnabled = entry != null
                val label = getString(if (index == 0) R.string.journal_first else R.string.journal_second)
                if (entry == null) {
                    sourceSummaries[index].setIfChanged(label + " · " + getString(R.string.task_comparison_missing_selection))
                    return@forEachIndexed
                }
                val state = samples[SourceKey(entry.observation)]
                val stateText = when (state) {
                    is Samples.Loaded -> if (state.points.isEmpty()) getString(R.string.history_no_samples) else getString(
                        if (entry.observation.status == "ENDED") R.string.history_samples_complete else R.string.history_samples_partial,
                        state.points.size.toString())
                    Samples.Failed -> getString(R.string.history_samples_failed)
                    else -> getString(R.string.history_loading_samples)
                }
                val aligned = if (state is Samples.Loaded) ExtractionReference.align(state.points) else null
                val alignment = when {
                    aligned == null -> ""
                    aligned.points.isEmpty() -> getString(R.string.task_comparison_no_aligned_samples)
                    aligned.observedOrigin -> getString(R.string.task_comparison_observed_origin)
                    else -> getString(R.string.task_comparison_record_origin)
                }
                val weightMissing = if (state is Samples.Loaded && aligned?.points?.none { it.weightHundredthsGram != null } == true)
                    "\n" + getString(R.string.task_comparison_no_weight, if (index == 0) "A" else "B") else ""
                val resets = if (aligned != null && aligned.points.zipWithNext().any { (a, b) -> b.waterTenthsMl < a.waterTenthsMl })
                    "\n" + getString(R.string.task_chart_water_reset) else ""
                val gaps = if (aligned != null && aligned.points.zipWithNext().any { (a, b) -> b.elapsedMs - a.elapsedMs > 1500 })
                    "\n" + getString(R.string.task_chart_gap) else ""
                sourceSummaries[index].setIfChanged(label + " · " + journalDate(entry.observation.startedAtMs) + "\n" +
                    journalCurve(entry.observation.curveId) + " · " + journalStatus(entry.observation.status) + "\n" +
                    stateText + "\n" + alignment + weightMissing + resets + gaps)
            }
        }
        refresh = ::render
        render()
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { destroyed = true; readers.shutdownNow(); refresh = {}; super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FIRST_SHOT_ID, firstId); outState.putString(SECOND_SHOT_ID, secondId)
        outState.putString(METRIC, selectedMetric.name)
    }
    private fun metricName(metric: ChartMetric) = getString(when (metric) {
        ChartMetric.PRESSURE -> R.string.task_comparison_pressure
        ChartMetric.WATER -> R.string.task_comparison_water
        ChartMetric.WEIGHT -> R.string.task_comparison_weight
        ChartMetric.TEMPERATURE -> R.string.task_comparison_temperature
    })
    private fun TextView.setIfChanged(value: String) { if (text.toString() != value) text = value }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}

/** Permanent A/swap/B nodes. Only actual selected text and native control chrome decide wrapping. */
private class ComparisonCupSelectors(context: android.content.Context, private val gap: Int, private val rowGap: Int) : LinearLayout(context) {
    val productLayoutEvidence = org.json.JSONObject()
    private var naturalWidths = emptyList<Int>()
    private fun invalidateMeasured(view: View) {
        view.forceLayout()
        if (view is android.view.ViewGroup) (0 until view.childCount).forEach { invalidateMeasured(view.getChildAt(it)) }
    }
    fun naturalInlineWidth(): Int {
        val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        naturalWidths = (0 until childCount).map { index ->
            val child = getChildAt(index)
            invalidateMeasured(child)
            child.measure(free, free)
            var naturalWidth = child.measuredWidth
            if (child is android.view.ViewGroup && child.tag != "comparison-swap") {
                val spinner = (0 until child.childCount).map(child::getChildAt).filterIsInstance<Spinner>().single()
                val selected = spinner.selectedView as? TextView
                if (selected != null) {
                    val backgroundPadding = android.graphics.Rect()
                    spinner.background?.getPadding(backgroundPadding)
                    val selectedWidth = kotlin.math.ceil(android.text.Layout.getDesiredWidth(selected.text, selected.paint)).toInt() +
                        selected.compoundPaddingLeft + selected.compoundPaddingRight
                    val spinnerChrome = maxOf(spinner.paddingLeft, backgroundPadding.left) + maxOf(spinner.paddingRight, backgroundPadding.right)
                    naturalWidth = maxOf(naturalWidth, child.paddingLeft + child.paddingRight + selectedWidth + spinnerChrome)
                }
            }
            invalidateMeasured(child) // Restore real constraints after the native natural-width probe.
            naturalWidth
        }
        return naturalWidths.sum() + gap * (childCount - 1).coerceAtLeast(0)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val natural = naturalInlineWidth()
        val inline = natural <= available
        orientation = if (inline) HORIZONTAL else VERTICAL
        val flexibleCount = (0 until childCount).count { getChildAt(it).tag != "comparison-swap" }.coerceAtLeast(1)
        val surplus = (available - natural).coerceAtLeast(0)
        var distributed = 0
        var flexibleIndex = 0
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as LayoutParams
            val flexible = child.tag != "comparison-swap"
            val extra = if (inline && flexible) {
                flexibleIndex++
                (if (flexibleIndex == flexibleCount) surplus - distributed else surplus / flexibleCount).also { distributed += it }
            } else 0
            params.width = if (inline) naturalWidths[index] + extra else -1
            params.weight = 0f
            params.marginStart = if (inline && index > 0) gap else 0
            params.marginEnd = 0
            params.topMargin = if (!inline && index > 0) rowGap else 0
            params.bottomMargin = 0
            params.resolveLayoutDirection(this@ComparisonCupSelectors.layoutDirection)
        }
        productLayoutEvidence.put("availableWidthPx", available).put("naturalInlineWidthPx", natural)
            .put("childNaturalWidthsPx", org.json.JSONArray(naturalWidths)).put("inline", inline)
            .put("groupGapPx", gap).put("rowGapPx", rowGap)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

package io.openhoyi.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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
        data class Loaded(
            val points: List<ShotPoint>,
            val differences: Map<ComparisonDifferences.Metric, ComparisonDifferences.Reading>,
        ) : Samples
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
        firstId = if (savedInstanceState?.containsKey(FIRST_SHOT_ID) == true) savedInstanceState.getString(FIRST_SHOT_ID)
            else intent.getStringExtra(FIRST_SHOT_ID)
        secondId = if (savedInstanceState?.containsKey(SECOND_SHOT_ID) == true) savedInstanceState.getString(SECOND_SHOT_ID)
            else intent.getStringExtra(SECOND_SHOT_ID)
        selectedMetric = savedInstanceState?.getString(METRIC)?.let { name -> historicalMetrics.firstOrNull { it.name == name } }
            ?: ChartMetric.PRESSURE
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
        val actions = style.row().apply { gravity = android.view.Gravity.END }
        val swap = style.button(Button(this), outline = true).apply {
            text = getString(R.string.task_comparison_swap)
            tag = "comparison-swap"
            setOnClickListener {
                val previous = firstId
                firstId = secondId
                secondId = previous
                refresh()
            }
        }
        val changeSecond = style.button(Button(this), outline = true).apply {
            text = getString(R.string.history_cup_change_second)
            tag = "comparison-change-second"
            setOnClickListener { showHistoryCupPicker(firstId) }
        }
        actions.addView(swap, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        actions.addView(changeSecond, LinearLayout.LayoutParams(-2, -2))
        val summaryGroup = style.column()
        style.add(summaryGroup, actions)
        val cupValues = style.row()
        val cardRow = HistoryToolbarRow(this, dp(8), dp(8)) { dp(320) }
        cardRow.addView(cupValues)
        val cupCards = listOf(R.string.history_cup_first_mark, R.string.history_cup_second_mark, R.string.history_cup_difference)
            .mapIndexed { index, label ->
                val card = style.column().apply {
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = style.shape(style.surface, outline = true)
                }
                if (index == 2) cardRow.addView(card)
                else cupValues.addView(card, LinearLayout.LayoutParams(0, -1, 1f).apply {
                    if (index == 1) marginStart = dp(8)
                })
                style.add(card, style.text(getString(label), TaskProductVisualStyle.auxiliary))
                val identity = style.text("", TaskProductVisualStyle.compactMetric, "comparison-cup-identity-$index")
                val date = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
                if (index < 2) {
                    style.add(card, identity)
                    style.add(card, date)
                }
                val measurements = style.text("", TaskProductVisualStyle.auxiliary, "comparison-cup-measurements-$index")
                style.add(card, measurements)
                CupCard(identity, date, measurements)
            }
        style.add(summaryGroup, cardRow, top = 8)
        val shared = style.text("", TaskProductVisualStyle.auxiliary, secondary = true)
        style.add(summaryGroup, shared, top = 4)
        toolbarBody.addView(summaryGroup, 0, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val summaryTable = ComparisonSummaryTable(this, visual)
        support.addView(summaryTable, LinearLayout.LayoutParams(-1, -2))
        val recordActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
            support.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        val differenceSummary = ComparisonDifferenceSummary(this, style)
        workspace.setSupplementalDetails(differenceSummary)
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
                    val points = (application as MobileApplication).samples.load(entry.observation.id)
                    ExtractionReference.align(points)
                    Samples.Loaded(points, ComparisonDifferences.summarize(entry.observation.status, points))
                }
                runOnUiThread {
                    if (destroyed || isFinishing || isDestroyed) return@runOnUiThread
                    samples[key] = loaded.getOrElse { Samples.Failed }
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
            fun differenceCup(entry: BrewJournal.Entry): ComparisonDifferenceSummary.Cup =
                when (val state = samples[SourceKey(entry.observation)]) {
                    is Samples.Loaded -> ComparisonDifferenceSummary.Cup.Readings(state.differences)
                    Samples.Failed -> ComparisonDifferenceSummary.Cup.Pending(R.string.history_samples_failed)
                    else -> ComparisonDifferenceSummary.Cup.Pending(R.string.history_loading_samples)
                }
            if (first != null && second != null && !same)
                differenceSummary.update(differenceCup(first), differenceCup(second))
            else differenceSummary.update(null, null)
            chart.firstPoints = if (valid) points[0] else emptyList()
            chart.secondPoints = if (valid) points[1] else emptyList()
            chart.comparisonMessage = when {
                first == null || second == null -> getString(if (journal.entries().size < 2)
                    R.string.journal_compare_need_two else R.string.task_comparison_missing_selection)
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
            swap.isEnabled = valid
            changeSecond.text = getString(if (valid || first != null) R.string.history_cup_change_second else R.string.journal_compare)
            val outcome = if (valid) HistoryCupOutcome.difference(first?.observation, second?.observation)
                else HistoryCupOutcome.Difference(null, null)
            fun measured(label: Int, value: String) = getString(label) + " " + value
            cupCards[2].measurements.setIfChanged(listOf(
                measured(R.string.history_duration, historyCupSeconds(outcome.machineSeconds, signed = true)),
                measured(R.string.history_end_scale, historyCupGrams(outcome.scaleHundredths, signed = true)),
            ).joinToString("\n"))
            selected.forEachIndexed { index, entry ->
                val observation = entry?.observation
                val card = cupCards[index]
                card.identity.setIfChanged(observation?.let { journalCurve(it.curveId) } ?: "—")
                card.date.setIfChanged(observation?.let { journalDate(it.startedAtMs) }.orEmpty())
                card.measurements.setIfChanged(listOf(
                    measured(historyCupTimingLabel(observation), historyCupSeconds(observation?.observedMachineElapsedSeconds?.toLong())),
                    measured(R.string.history_end_scale, historyCupGrams(observation?.weightHundredthsGram?.toLong())),
                ).joinToString("\n"))
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
    internal fun setHistoryCupPair(first: String, second: String) {
        firstId = first
        secondId = second
        refresh()
    }
    private data class CupCard(val identity: TextView, val date: TextView, val measurements: TextView)

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
        ChartMetric.YIELD -> R.string.chart_series_yield
        ChartMetric.WEIGHT -> R.string.task_comparison_weight
        ChartMetric.FLOW -> R.string.task_chart_flow
        ChartMetric.TEMPERATURE -> R.string.task_comparison_temperature
    })
    private fun TextView.setIfChanged(value: String) { if (text.toString() != value) text = value }
    private fun dp(value: Int) = HoyiUi.dp(this, value)
}

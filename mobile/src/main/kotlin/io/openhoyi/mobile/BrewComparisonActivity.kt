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
        val body = beanPage(getString(R.string.journal_compare_title), getString(R.string.journal_compare_hint))
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

        val workspace = TaskComparisonLayout(this)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        val toolbarBody = workspace.toolbar
        val support = workspace.details
        val sourceLegend = HoyiUi.label(this, toolbarBody, "", 14, true).apply { tag = "comparison-source-legend" }
        val warning = HoyiUi.label(this, toolbarBody, "", 14, true).apply { visibility = View.GONE }
        val metricSelector = Spinner(this).apply {
            tag = "comparison-metric"
            minimumHeight = dp(48)
            adapter = beanChoiceAdapter(ChartMetric.entries.map(::metricName))
            contentDescription = getString(R.string.task_comparison_metric)
            toolbarBody.addView(this, LinearLayout.LayoutParams(-1, -2))
            setSelection(selectedMetric.ordinal)
        }
        chart = TwoCupChartView(this).apply { metric = selectedMetric }
        workspace.setChart(chart)
        val selectors = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                // A narrow supporting column cannot fit two complete date/time choices side by side.
                val next = if (View.MeasureSpec.getSize(widthMeasureSpec) >= dp(600)) HORIZONTAL else VERTICAL
                if (orientation != next) {
                    orientation = next
                    for (index in 0 until childCount) getChildAt(index).layoutParams =
                        if (next == HORIZONTAL) LayoutParams(0, -2, 1f) else LayoutParams(-1, -2)
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { orientation = LinearLayout.HORIZONTAL; toolbarBody.addView(this, 0) }
        val choiceIds = listOf<String?>(null) + entries.map { it.observation.id }
        val labels = listOf(getString(R.string.task_comparison_choose_cup)) + entries.map {
            getString(R.string.journal_selector_title, journalDate(it.observation.startedAtMs), journalCurve(it.observation.curveId))
        }
        val shortLabels = listOf(getString(R.string.task_comparison_choose_cup)) + entries.map { journalDate(it.observation.startedAtMs) }
        fun cupSelector(label: Int, selected: String?, changed: (String?) -> Unit): (String?) -> Unit {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = HoyiUi.shape(this@BrewComparisonActivity, R.color.mobile_surface, 12, R.color.mobile_border)
            }
            selectors.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            HoyiUi.label(this, cell, getString(label), 14, muted = true)
            // Track the displayed position separately from identity: an absent ID initially
            // shares the placeholder position, but must survive its automatic initial callback.
            var displayedPosition = choiceIds.indexOf(selected).coerceAtLeast(0)
            val spinner = Spinner(this).apply {
                tag = if (label == R.string.journal_first) "comparison-first" else "comparison-second"
                adapter = beanChoiceAdapter(labels, shortLabels, selectedMaxLines = Int.MAX_VALUE)
                contentDescription = getString(label)
                minimumHeight = dp(48)
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
        val comparisonActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            toolbarBody.addView(this, 1)
        }
        HoyiUi.button(this, comparisonActions, getString(R.string.task_comparison_swap)) {
            val previous = firstId; firstId = secondId; secondId = previous
            displayFirstSelection(firstId)
            displaySecondSelection(secondId)
            refresh()
        }.apply { tag = "comparison-swap"; asTaskTextAction(); layoutParams = LinearLayout.LayoutParams(-2, -2) }
        val shared = HoyiUi.label(this, comparisonActions, "", 14, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setPadding(dp(16), dp(8), 0, dp(8))
        }
        toolbarBody.removeView(metricSelector)
        comparisonActions.addView(metricSelector, LinearLayout.LayoutParams(dp(220), -2))
        val summaryTable = ComparisonSummaryTable(this)
        support.addView(summaryTable, LinearLayout.LayoutParams(-1, -2))
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
                background = HoyiUi.shape(this@BrewComparisonActivity, R.color.mobile_surface, 12, R.color.mobile_border)
            }
            sourceCards.addView(cup, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) marginStart = dp(16) })
            sourceSummaries += HoyiUi.label(this, cup, "", 14).apply { tag = "comparison-source-${index + 1}" }
            detailButtons += HoyiUi.button(this, cup, getString(label) + " · " + getString(R.string.journal_detail)) {
                val entry = journal.find((if (index == 0) firstId else secondId) ?: return@button) ?: return@button
                startActivity(Intent(this, HistoryDetailActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.observation.id))
            }.apply { asTaskTextAction() }
            noteButtons += HoyiUi.button(this, cup, getString(label) + " · " + getString(R.string.journal_notes_title)) {
                val entry = journal.find((if (index == 0) firstId else secondId) ?: return@button) ?: return@button
                val notes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                journalNotes(notes, entry.notes)
                val values = (0 until notes.childCount).map { (notes.getChildAt(it) as TextView).text.toString() }
                beanDetails(getString(label), journalObservationText(entry.observation) + "\n\n" + values.joinToString("\n"))
            }.apply { asTaskTextAction() }
        }
        metricSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, item: View?, position: Int, id: Long) {
                val metric = ChartMetric.entries[position]
                if (selectedMetric != metric) { selectedMetric = metric; chart.metric = metric; refresh() }
            }
        }
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

package io.openhoyi.mobile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner

class BrewComparisonActivity : ThemedActivity() {
    companion object { const val FIRST_SHOT_ID = "firstShotId"; const val SECOND_SHOT_ID = "secondShotId" }
    private var firstId: String? = null
    private var secondId: String? = null
    private var refresh: () -> Unit = {}
    private var renderVersion = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = beanPage(getString(R.string.journal_compare_title), getString(R.string.journal_compare_hint))
        val journal = journal(body) ?: return
        val entries = journal.entries()
        if (entries.size < 2) { HoyiUi.label(this, body, getString(R.string.journal_compare_need_two), 17); return }
        firstId = savedInstanceState?.getString(FIRST_SHOT_ID) ?: intent.getStringExtra(FIRST_SHOT_ID) ?: entries[0].observation.id
        secondId = savedInstanceState?.getString(SECOND_SHOT_ID) ?: intent.getStringExtra(SECOND_SHOT_ID) ?: entries[1].observation.id
        val selectors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; body.addView(this) }
        val shared = HoyiUi.label(this, body, "", 14, true)
        val results = beanWorkspace(body).apply { orientation = if (HoyiUi.wide(this@BrewComparisonActivity)) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }
        fun render() {
            val version = ++renderVersion
            results.removeAllViews()
            beanActions(body).removeAllViews()
            val first = journal.find(firstId ?: return) ?: return
            val second = journal.find(secondId ?: return) ?: return
            if (first.observation.id == second.observation.id) { shared.setText(R.string.journal_choose_different); return }
            shared.text = getString(when {
                first.notes.beanId == null || second.notes.beanId == null -> R.string.journal_bean_unknown
                first.notes.beanId == second.notes.beanId -> R.string.journal_same_bean
                else -> R.string.journal_different_bean
            }) + " · " + getString(if (first.observation.curveId == second.observation.curveId) R.string.journal_same_curve else R.string.journal_different_curve)
            listOf(R.string.journal_first to first, R.string.journal_second to second).forEach { (label, entry) ->
                val panel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(HoyiUi.dp(this@BrewComparisonActivity, 6), HoyiUi.dp(this@BrewComparisonActivity, 4), HoyiUi.dp(this@BrewComparisonActivity, 6), HoyiUi.dp(this@BrewComparisonActivity, 4))
                    background = HoyiUi.shape(this@BrewComparisonActivity, R.color.mobile_surface, 12, R.color.mobile_border)
                }
                results.addView(panel, if (HoyiUi.wide(this)) LinearLayout.LayoutParams(0, -1, 1f) else LinearLayout.LayoutParams(-1, 0, 1f))
                HoyiUi.label(this, panel, getString(label) + " · " + journalStatus(entry.observation.status), 14, true)
                if (entry.observation.status == "UNKNOWN") HoyiUi.label(this, panel, getString(R.string.history_unknown_warning), 13, true)
                val chart = ShotChartView(this)
                panel.addView(chart, LinearLayout.LayoutParams(-1, 0, 1f))
                val status = HoyiUi.label(this, panel, getString(R.string.history_loading_samples), 12, muted = true)
                HoyiUi.button(this, beanActions(body), getString(label) + " · " + getString(R.string.journal_detail)) {
                    startActivity(Intent(this, HistoryDetailActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.observation.id))
                }
                HoyiUi.button(this, beanActions(body), getString(label) + " · " + getString(R.string.journal_notes_title)) {
                    val notes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                    journalNotes(notes, entry.notes)
                    val values = (0 until notes.childCount).map { (notes.getChildAt(it) as android.widget.TextView).text.toString() }
                    beanDetails(getString(label), journalObservationText(entry.observation) + "\n\n" + values.joinToString("\n"))
                }
                Thread({
                    val loaded = runCatching { (application as MobileApplication).samples.load(entry.observation.id) }
                    runOnUiThread {
                        if (isFinishing || isDestroyed || version != renderVersion) return@runOnUiThread
                        loaded.onSuccess {
                            chart.points = it
                            status.text = if (it.isEmpty()) getString(R.string.history_no_samples) else getString(
                                if (entry.observation.status == "ENDED") R.string.history_samples_complete else R.string.history_samples_partial, it.size.toString())
                        }.onFailure { status.setText(R.string.history_samples_failed) }
                    }
                }, "brew-comparison-reader").start()
            }
        }
        fun selector(label: Int, selected: String?, changed: (String) -> Unit) {
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            selectors.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            HoyiUi.label(this, cell, getString(label), 13, muted = true)
            val view = Spinner(this).apply {
                // The compact selected chip identifies the cup by its timestamp;
                // the dropdown/accessibility keeps the complete date + curve label.
                adapter = beanChoiceAdapter(entries.map {
                    getString(R.string.journal_selector_title, journalDate(it.observation.startedAtMs), journalCurve(it.observation.curveId))
                }, entries.map { journalDate(it.observation.startedAtMs) })
                contentDescription = getString(label)
                minimumHeight = HoyiUi.dp(this@BrewComparisonActivity, 48)
                cell.addView(this)
            }
            view.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, item: View?, position: Int, id: Long) { changed(entries[position].observation.id); render() }
            }
            view.setSelection(entries.indexOfFirst { it.observation.id == selected }.coerceAtLeast(0))
        }
        selector(R.string.journal_first, firstId) { firstId = it }
        selector(R.string.journal_second, secondId) { secondId = it }
        refresh = ::render
        render()
    }
    override fun onResume() { super.onResume(); refresh() }
    override fun onDestroy() { renderVersion++; super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FIRST_SHOT_ID, firstId); outState.putString(SECOND_SHOT_ID, secondId)
    }
}

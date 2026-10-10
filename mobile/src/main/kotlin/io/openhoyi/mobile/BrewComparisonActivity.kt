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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = beanPage(getString(R.string.journal_compare_title), getString(R.string.journal_compare_hint))
        val journal = journal(body) ?: return
        val entries = journal.entries()
        if (entries.size < 2) { HoyiUi.label(this, body, getString(R.string.journal_compare_need_two), 17); return }
        firstId = savedInstanceState?.getString(FIRST_SHOT_ID) ?: intent.getStringExtra(FIRST_SHOT_ID) ?: entries[0].observation.id
        secondId = savedInstanceState?.getString(SECOND_SHOT_ID) ?: intent.getStringExtra(SECOND_SHOT_ID) ?: entries[1].observation.id
        val selectors = HoyiUi.card(this, body)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; body.addView(this) }
        fun render() {
            results.removeAllViews()
            val first = journal.find(firstId ?: return) ?: return
            val second = journal.find(secondId ?: return) ?: return
            if (first.observation.id == second.observation.id) { HoyiUi.label(this, results, getString(R.string.journal_choose_different), 17); return }
            val shared = HoyiUi.card(this, results)
            HoyiUi.label(this, shared, getString(when {
                first.notes.beanId == null || second.notes.beanId == null -> R.string.journal_bean_unknown
                first.notes.beanId == second.notes.beanId -> R.string.journal_same_bean
                else -> R.string.journal_different_bean
            }), 17, true)
            HoyiUi.label(this, shared, getString(if (first.observation.curveId == second.observation.curveId) R.string.journal_same_curve else R.string.journal_different_curve), 17, true)
            listOf(R.string.journal_first to first, R.string.journal_second to second).forEach { (label, entry) ->
                HoyiUi.label(this, results, getString(label), 20, true).setPadding(0, HoyiUi.dp(this, 24), 0, 0)
                journalSummary(results, entry.observation)
                journalNotes(HoyiUi.card(this, results, getString(R.string.journal_notes_title)), entry.notes)
                HoyiUi.button(this, results, getString(R.string.journal_detail)) {
                    startActivity(Intent(this, HistoryDetailActivity::class.java).putExtra(BrewReviewActivity.SHOT_ID, entry.observation.id))
                }
            }
        }
        fun selector(label: Int, selected: String?, changed: (String) -> Unit) {
            HoyiUi.label(this, selectors, getString(label), 15, muted = true)
            val view = Spinner(this).apply {
                adapter = HoyiUi.spinnerAdapter(this@BrewComparisonActivity, entries.map {
                    getString(R.string.journal_selector_title, journalDate(it.observation.startedAtMs), journalCurve(it.observation.curveId))
                })
                contentDescription = getString(label)
                minimumHeight = HoyiUi.dp(this@BrewComparisonActivity, 52)
                selectors.addView(this)
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
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FIRST_SHOT_ID, firstId); outState.putString(SECOND_SHOT_ID, secondId)
    }
}

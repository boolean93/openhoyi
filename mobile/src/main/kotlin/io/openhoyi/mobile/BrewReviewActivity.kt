package io.openhoyi.mobile

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import io.openhoyi.bean.Bean
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrewReviewActivity : ThemedActivity() {
    companion object { const val SHOT_ID = "shotId" }
    private val fields = linkedMapOf<String, EditText>()
    private var selectedBeanId: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = beanPage(getString(R.string.journal_title), getString(R.string.journal_subtitle))
        val app = application as MobileApplication
        val journal = journal(body) ?: return
        val id = intent.getStringExtra(SHOT_ID)
        val entry = id?.let(journal::find)
        if (entry == null) { HoyiUi.label(this, body, getString(R.string.journal_missing), 17); return }
        journalSummary(body, entry.observation)
        val card = HoyiUi.card(this, body, getString(R.string.journal_notes_title))
        val previousBean = entry.notes.beanId?.let { Bean(it, requireNotNull(entry.notes.beanName)) }
        val beans = (listOfNotNull(previousBean) + app.beanInventoryResult.getOrNull()?.batches().orEmpty().map { it.batch.bean }).distinctBy { it.id }
        selectedBeanId = if (savedInstanceState == null) entry.notes.beanId else savedInstanceState.getString("selectedBeanId")
        HoyiUi.label(this, card, getString(R.string.journal_bean), 15, muted = true)
        val select = Spinner(this).apply {
            adapter = HoyiUi.spinnerAdapter(this@BrewReviewActivity,
                listOf(getString(R.string.journal_no_bean)) + beans.map { it.name })
            minimumHeight = HoyiUi.dp(this@BrewReviewActivity, 52)
            contentDescription = getString(R.string.journal_bean)
            card.addView(this)
        }
        select.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { selectedBeanId = beans.getOrNull(position - 1)?.id }
        }
        select.setSelection(beans.indexOfFirst { it.id == selectedBeanId }.let { if (it < 0) 0 else it + 1 })
        fun field(key: String, label: Int, initial: String, numeric: Boolean = false) = beanInput(card, label, numeric).also {
            fields[key] = it
            if (!numeric) { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; it.setSingleLine(false); it.minLines = 2; it.filters = arrayOf(InputFilter.LengthFilter(16000)) }
            it.setText(savedInstanceState?.getString(key) ?: initial)
        }
        val dose = field("dose", R.string.journal_dose, entry.notes.doseMg?.let(BeanQuantity::formatGrams).orEmpty(), true)
        val grind = field("grind", R.string.journal_grind, entry.notes.grind)
        val expectation = field("expectation", R.string.journal_expectation, entry.notes.curveExpectation)
        val taste = field("taste", R.string.journal_taste, entry.notes.taste)
        val next = field("next", R.string.journal_next, entry.notes.nextAdjustment)
        val error = beanError(body)
        val save = HoyiUi.button(this, body, getString(R.string.journal_save), primary = true) {}
        save.setOnClickListener {
            val available = app.journalResult.getOrNull()
            if (available == null) { error.setText(R.string.journal_unavailable); save.isEnabled = false; return@setOnClickListener }
            val doseMg = runCatching { dose.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                BeanQuantity.parseGrams(it).also { mg -> require(mg > 0) }
            } }.getOrElse { error.setText(R.string.journal_bad_dose); return@setOnClickListener }
            val bean = beans.find { it.id == selectedBeanId }
            val notes = BrewJournal.Notes(bean?.id, bean?.name, doseMg, grind.text.toString().trim(),
                expectation.text.toString().trim(), taste.text.toString().trim(), next.text.toString().trim())
            save.isEnabled = false
            if (runCatching { available.edit(entry.observation.id, notes) }.isSuccess) finish()
            else { error.setText(R.string.journal_save_failed); save.isEnabled = true }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selectedBeanId", selectedBeanId)
        fields.forEach { (key, field) -> outState.putString(key, field.text.toString()) }
    }
}

internal fun ShotHistory.Entry.journalObservation() = BrewJournal.Observation(id, curveId, startedAtMs, endedAtMs, elapsedMs, status.name, reason, weightHundredthsGram)
internal fun BrewJournal.Observation.historySummary() = ShotHistory.Entry(id, curveId, startedAtMs, endedAtMs, elapsedMs, ShotHistory.Status.valueOf(status), reason, weightHundredthsGram)
internal fun ThemedActivity.journal(body: LinearLayout): BrewJournal? = (application as MobileApplication).journalResult.getOrNull().also {
    if (it == null) HoyiUi.label(this, HoyiUi.card(this, body), getString(R.string.journal_unavailable), 17, true)
}
internal fun ThemedActivity.journalStatus(value: String): String = getString(when (value) {
    "STARTING" -> R.string.extraction_state_starting
    "RUNNING" -> R.string.extraction_state_running
    "STOP_REQUESTED" -> R.string.history_status_stop_requested
    "ENDED" -> R.string.history_status_ended
    "NOT_STARTED" -> R.string.history_status_not_started
    else -> R.string.extraction_state_unknown
})
internal fun ThemedActivity.journalDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))
internal fun ThemedActivity.journalCurve(id: String): String = if (id == "manual") getString(R.string.extraction_manual_label)
    else runCatching { (application as MobileApplication).curves.find(id)?.name }.getOrNull() ?: id
internal fun ThemedActivity.journalField(parent: LinearLayout, label: Int, value: String?) {
    HoyiUi.label(this, parent, getString(R.string.journal_field_value, getString(label), value?.takeIf { it.isNotEmpty() } ?: getString(R.string.journal_optional)), 16)
        .setPadding(0, HoyiUi.dp(this, 10), 0, 0)
}
internal fun ThemedActivity.journalSummary(parent: LinearLayout, shot: BrewJournal.Observation): LinearLayout {
    val card = HoyiUi.card(this, parent, journalCurve(shot.curveId))
    journalField(card, R.string.journal_state, journalStatus(shot.status))
    journalField(card, R.string.journal_started, journalDate(shot.startedAtMs))
    journalField(card, R.string.journal_ended, shot.endedAtMs?.let { journalDate(it) })
    journalField(card, R.string.journal_elapsed, shot.elapsedMs?.let { getString(R.string.journal_seconds, BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString()) })
    journalField(card, R.string.journal_weight, shot.weightHundredthsGram?.let { getString(R.string.journal_grams, BigDecimal.valueOf(it.toLong(), 2).stripTrailingZeros().toPlainString()) })
    if (shot.status == "UNKNOWN") HoyiUi.label(this, card, getString(R.string.history_unknown_warning), 15, true)
    return card
}
internal fun ThemedActivity.journalNotes(parent: LinearLayout, notes: BrewJournal.Notes) {
    journalField(parent, R.string.journal_bean, notes.beanName)
    journalField(parent, R.string.journal_dose, notes.doseMg?.let { getString(R.string.journal_grams, BeanQuantity.formatGrams(it)) })
    journalField(parent, R.string.journal_grind, notes.grind)
    journalField(parent, R.string.journal_expectation, notes.curveExpectation)
    journalField(parent, R.string.journal_taste, notes.taste)
    journalField(parent, R.string.journal_next, notes.nextAdjustment)
}

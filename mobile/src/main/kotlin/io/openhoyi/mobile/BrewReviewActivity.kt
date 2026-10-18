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
    private var section = 0
    private var form: TaskReviewSections? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = beanPage(getString(R.string.journal_title), getString(R.string.journal_subtitle))
        val app = application as MobileApplication
        val journal = journal(body) ?: return
        val id = intent.getStringExtra(SHOT_ID)
        val entry = id?.let(journal::find)
        if (entry == null) { HoyiUi.label(this, body, getString(R.string.journal_missing), 17); return }
        val observation = entry.observation
        val cupContext = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        body.addView(cupContext, LinearLayout.LayoutParams(-1, -2))
        HoyiUi.label(this, cupContext,
            journalCurve(observation.curveId) + " · " + journalDate(observation.startedAtMs), 14, muted = true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "full-text-details"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = HoyiUi.dp(this@BrewReviewActivity, 16) }
        }
        val elapsed = observation.elapsedMs?.let {
            getString(R.string.journal_seconds, BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString())
        } ?: "—"
        val weightText = observation.weightHundredthsGram?.let {
            getString(R.string.journal_grams, BigDecimal.valueOf(it.toLong(), 2).stripTrailingZeros().toPlainString())
        } ?: "—"
        val outcome = LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned=false
        }
        cupContext.addView(outcome,LinearLayout.LayoutParams(0,-2,2f))
        listOf(R.string.journal_elapsed to elapsed, R.string.history_end_scale to weightText).forEach { (label,value) ->
            val cell=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                setPadding(HoyiUi.dp(this@BrewReviewActivity,8),0,HoyiUi.dp(this@BrewReviewActivity,8),HoyiUi.dp(this@BrewReviewActivity,8))
            }
            outcome.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
            HoyiUi.label(this,cell,value,TaskVisualTokens.RECIPE_VALUE_SP,true).apply {
                val unit = getString(if (label == R.string.journal_elapsed)
                    R.string.journal_seconds else R.string.journal_grams, "").trim()
                text = curveMetricText(this@BrewReviewActivity, value, unit)
                typeface = TaskTypography.face(this@BrewReviewActivity, 700)
            }
            HoyiUi.label(this,cell,getString(label),14,muted=true).apply {
                setPadding(0,HoyiUi.dp(this@BrewReviewActivity,4),0,0)
            }
        }
        HoyiUi.label(this,outcome,journalStatus(observation.status),14,true).apply {
            layoutParams=LinearLayout.LayoutParams(0,-2,1f)
            gravity=android.view.Gravity.END
        }
        if (entry.observation.status == "UNKNOWN") HoyiUi.label(this, body, getString(R.string.history_unknown_warning), 14, true).apply {
            setTextColor(getColor(R.color.mobile_danger))
        }
        body.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.mobile_border))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)))
        section = savedInstanceState?.getInt("section") ?: 0
        val sections = TaskReviewSections(this, body, listOf(R.string.journal_bean, R.string.journal_grind, R.string.journal_expectation, R.string.journal_taste, R.string.journal_next).map(::getString), section) { section = it }
        form = sections
        val card = sections.panels[0]
        val previousBean = entry.notes.beanId?.let { Bean(it, requireNotNull(entry.notes.beanName)) }
        val beans = (listOfNotNull(previousBean) + app.beanInventoryResult.getOrNull()?.batches().orEmpty().map { it.batch.bean }).distinctBy { it.id }
        selectedBeanId = if (savedInstanceState == null) entry.notes.beanId else savedInstanceState.getString("selectedBeanId")
        val beanLabel = HoyiUi.label(this, card, getString(R.string.journal_bean), 16, true).apply {
            setPadding(0,HoyiUi.dp(this@BrewReviewActivity,8),0,HoyiUi.dp(this@BrewReviewActivity,6))
        }
        val select = Spinner(this).apply {
            this.id=android.view.View.generateViewId()
            beanLabel.labelFor=this.id
            adapter = beanChoiceAdapter(
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
        fun field(key: String, label: Int, initial: String, numeric: Boolean = false) = beanInput(sections.panels[when (key) { "grind" -> 1; "expectation" -> 2; "taste" -> 3; "next" -> 4; else -> 0 }].apply { tag = "bean-full-field" }, label, numeric, emphasizeLabel=true).also {
            fields[key] = it
            it.textSize = TaskVisualTokens.BODY_SP.toFloat()
            if (!numeric) { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; it.setSingleLine(false); it.minLines = 2; it.gravity = android.view.Gravity.TOP; it.layoutParams = (it.layoutParams as LinearLayout.LayoutParams).apply { height = HoyiUi.dp(this@BrewReviewActivity, 160); weight = 0f }; it.filters = arrayOf(InputFilter.LengthFilter(16000)) }
            it.setText(savedInstanceState?.getString(key) ?: initial)
            val length = it.text.length
            it.setSelection((savedInstanceState?.getInt("selectionStart:$key", 0) ?: 0).coerceIn(0, length),
                (savedInstanceState?.getInt("selectionEnd:$key", 0) ?: 0).coerceIn(0, length))
        }
        val dose = field("dose", R.string.journal_dose, entry.notes.doseMg?.let(BeanQuantity::formatGrams).orEmpty(), true)
        val grind = field("grind", R.string.journal_grind, entry.notes.grind)
        HoyiUi.button(this, sections.panels[1], getString(R.string.journal_detail)) {
            beanDetails(journalCurve(entry.observation.curveId), journalObservationText(entry.observation))
        }.asTaskTextAction()
        val expectation = field("expectation", R.string.journal_expectation, entry.notes.curveExpectation)
        val taste = field("taste", R.string.journal_taste, entry.notes.taste)
        val next = field("next", R.string.journal_next, entry.notes.nextAdjustment)
        sections.fitMultiline(listOf(grind, expectation, taste, next))
        sections.restore(savedInstanceState, fields)
        val error = beanError(body)
        val footer = beanActions(body)
        footer.addView(View(this).apply {
            setBackgroundColor(getColor(R.color.mobile_border))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)))
        val saveRow = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val wide = View.MeasureSpec.getSize(widthMeasureSpec) >= HoyiUi.dp(this@BrewReviewActivity, 624)
                orientation = if (wide) HORIZONTAL else VERTICAL
                (getChildAt(0).layoutParams as LayoutParams).apply {
                    width = if (wide) 0 else -1
                    weight = if (wide) 1f else 0f
                    marginEnd = if (wide) HoyiUi.dp(this@BrewReviewActivity, TaskVisualTokens.GROUP_GAP_DP) else 0
                }
                (getChildAt(1).layoutParams as LayoutParams).apply {
                    width = if (wide) HoyiUi.dp(this@BrewReviewActivity, 320) else -1
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            isBaselineAligned = false
        }
        footer.addView(saveRow, LinearLayout.LayoutParams(-1, -2))
        HoyiUi.label(this, saveRow, getString(R.string.journal_subtitle), TaskVisualTokens.SUPPORTING_SP, muted = true).apply {
            tag = "review-save-scope"
            setPadding(0, HoyiUi.dp(this@BrewReviewActivity, TaskVisualTokens.RELATED_GAP_DP), 0,
                HoyiUi.dp(this@BrewReviewActivity, TaskVisualTokens.RELATED_GAP_DP))
        }
        val saveDock = TaskActionDock(this, edgeInsetDp = 0)
        saveRow.addView(saveDock, LinearLayout.LayoutParams(-1, -2))
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        saveDock.addView(actionRow, LinearLayout.LayoutParams(-1, -2))
        HoyiUi.button(this, actionRow, getString(R.string.beans_cancel)) { finish() }.apply {
            asTaskTextAction()
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = HoyiUi.dp(this@BrewReviewActivity, TaskVisualTokens.RELATED_GAP_DP)
            }
        }
        val save = HoyiUi.button(this, actionRow, getString(R.string.journal_save), primary = true) {}.apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 2f)
        }
        save.setOnClickListener {
            val available = app.journalResult.getOrNull()
            if (available == null) { error.setText(R.string.journal_unavailable); save.isEnabled = false; return@setOnClickListener }
            val doseMg = runCatching { dose.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                BeanQuantity.parseGrams(it).also { mg -> require(mg > 0) }
            } }.getOrElse { sections.jumpTo(0); error.setText(R.string.journal_bad_dose); return@setOnClickListener }
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
        outState.putInt("section", section)
        form?.save(outState, fields)
        fields.forEach { (key, field) ->
            outState.putString(key, field.text.toString())
            outState.putInt("selectionStart:$key", field.selectionStart)
            outState.putInt("selectionEnd:$key", field.selectionEnd)
        }
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

internal fun ThemedActivity.journalObservationText(shot: BrewJournal.Observation): String = buildString {
    append(getString(R.string.journal_state)).append(": ").append(journalStatus(shot.status))
    append("\n").append(getString(R.string.journal_started)).append(": ").append(journalDate(shot.startedAtMs))
    append("\n").append(getString(R.string.journal_ended)).append(": ").append(shot.endedAtMs?.let { journalDate(it) } ?: getString(R.string.journal_optional))
    append("\n").append(getString(R.string.journal_elapsed)).append(": ").append(shot.elapsedMs?.let { getString(R.string.journal_seconds, BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString()) } ?: getString(R.string.journal_optional))
    append("\n").append(getString(R.string.journal_weight)).append(": ").append(shot.weightHundredthsGram?.let { getString(R.string.journal_grams, BigDecimal.valueOf(it.toLong(), 2).stripTrailingZeros().toPlainString()) } ?: getString(R.string.journal_optional))
    if (shot.status == "UNKNOWN") append("\n").append(getString(R.string.history_unknown_warning))
}

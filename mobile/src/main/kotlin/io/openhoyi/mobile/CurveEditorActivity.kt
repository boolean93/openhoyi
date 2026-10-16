package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner

class CurveEditorActivity : ThemedActivity() {
    companion object { const val CURVE_ID = "curveId" }
    private var form: TaskCurveEditorForm? = null
    private val fields = linkedMapOf<String, EditText>()
    private var draftId = CustomCurveDocument.newId()
    private var stageCount = 2
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = beanPage(getString(R.string.profiles_editor), getString(R.string.profiles_pressure_execution_limits))
        val store = profileStore(body) ?: return
        val sourceId = intent.getStringExtra(CURVE_ID)
        draftId = savedInstanceState?.getString("draftId") ?: draftId
        val source = if (sourceId == null) CustomCurveDocument(draftId, getString(R.string.profiles_default_name), 93, 3600,
            CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        else runCatching {
            store.find(sourceId) ?: (application as MobileApplication).curves.find(sourceId)?.let { CustomCurveDocument.fromLibraryItem(it, draftId) }
        }.getOrNull()
        if (source == null) { HoyiUi.label(this, body, getString(R.string.profiles_missing), 17); return }
        draftId = source.id
        if (source.controlMode != CustomCurveDocument.ControlMode.PRESSURE) { profileCard(body, source); return }
        if (sourceId != null && !sourceId.startsWith("draft-")) HoyiUi.label(this, body, getString(R.string.profiles_copy_warning), 15, muted = true)
        stageCount = savedInstanceState?.getInt("stageCount") ?: source.stages.size
        val sections = TaskCurveEditorForm(this, body)
        form = sections
        fun field(parent: LinearLayout, key: String, label: Int, initial: String, numeric: Boolean = true) = beanInput(parent, label, numeric).also {
            fields[key] = it; it.setText(savedInstanceState?.getString(key) ?: initial)
        }
        val name = field(sections.metadata[0], "name", R.string.profiles_name, source.name, false)
        val temperature = field(sections.metadata[1], "temperature", R.string.profiles_temperature, source.temperatureC.toString())
        val cup = field(sections.metadata[2], "cup", R.string.profiles_cup, CurveDraftNumber.format(source.targetHundredthsGram, 2))
        HoyiUi.label(this, sections.stageHeader, getString(R.string.profiles_stage_count), 16, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val selector = Spinner(this).apply {
            adapter = HoyiUi.spinnerAdapter(this@CurveEditorActivity, (1..4).map(Int::toString))
            minimumHeight = HoyiUi.dp(this@CurveEditorActivity, 52)
            contentDescription = getString(R.string.profiles_stage_count)
            sections.stageHeader.addView(this, LinearLayout.LayoutParams(HoyiUi.dp(this@CurveEditorActivity, 80), -2))
        }
        (0..3).forEach { index ->
            val stage = source.stages.getOrNull(index) ?: CustomCurveDocument.Stage(60, 100)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            sections.stageRows[index].addView(row)
            fun stageCell() = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; tag = "bean-full-field"
                row.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (row.childCount > 0) marginStart = HoyiUi.dp(this@CurveEditorActivity, 16)
                })
            }
            field(stageCell(), "target-$index", R.string.profiles_pressure, CurveDraftNumber.format(stage.target, 1))
            field(stageCell(), "water-$index", R.string.profiles_water, CurveDraftNumber.format(stage.waterTenthsMl, 1))
        }
        var previewChanged: () -> Unit = {}
        fun visibility() {
            sections.setStageCount(stageCount)
            previewChanged()
        }
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { stageCount = position + 1; visibility() }
        }
        selector.setSelection(stageCount - 1)
        visibility()
        val error = beanError(body)
        fun parsed(): CustomCurveDocument = CustomCurveDocument(draftId, name.text.toString().trim(),
            CurveDraftNumber.parse(temperature.text.toString(), 0), CurveDraftNumber.parse(cup.text.toString(), 2), CustomCurveDocument.ControlMode.PRESSURE,
            (0 until stageCount).map { CustomCurveDocument.Stage(CurveDraftNumber.parse(fields.getValue("target-$it").text.toString(), 1), CurveDraftNumber.parse(fields.getValue("water-$it").text.toString(), 1)) })
            .also { it.validate() }
        val preview = CurveStageView(this).apply { tag = "task-form-stage-preview" }
        HoyiUi.label(this, sections.supportingBody, getString(R.string.profiles_preview), 20, true)
        sections.supportingBody.addView(preview, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 280)))
        HoyiUi.label(this, sections.supportingBody, getString(R.string.task_form_stage_caption), 14, muted = true)
        val previewStatus = HoyiUi.label(this, sections.supportingBody, "", 14).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        previewChanged = {
            runCatching { parsed() }.onSuccess { doc ->
                preview.targets = doc.stages.map { it.target }
                preview.contentDescription = profileSummary(doc)
                previewStatus.text = profileSummary(doc)
            }.onFailure {
                preview.targets = emptyList()
                preview.contentDescription = getString(R.string.profiles_invalid)
                previewStatus.setText(R.string.profiles_invalid)
            }
            Unit
        }
        fields.values.forEach { field ->
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { previewChanged() }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        previewChanged()
        sections.restore(savedInstanceState, fields)
        HoyiUi.button(this, sections.supportingBody, getString(R.string.profiles_preview)) {
            runCatching { parsed() }.onSuccess { profilePreviewDialog(it); error.text = "" }.onFailure { error.setText(R.string.profiles_invalid) }
        }
        val dock = TaskActionDock(this, edgeInsetDp = 0)
        body.addView(dock, LinearLayout.LayoutParams(-1, -2))
        val save = HoyiUi.button(this, dock, getString(R.string.profiles_save), primary = true) {}
        save.setOnClickListener {
            val doc = runCatching { parsed() }.getOrElse { error.setText(R.string.profiles_invalid); return@setOnClickListener }
            val available = (application as MobileApplication).customCurvesResult.getOrNull()
            if (available == null) { error.setText(R.string.profiles_unavailable); save.isEnabled = false; return@setOnClickListener }
            save.isEnabled = false
            if (runCatching { available.save(doc) }.isSuccess) finish()
            else { error.setText(R.string.profiles_save_failed); save.isEnabled = true }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draftId", draftId); outState.putInt("stageCount", stageCount)
        fields.forEach { (key, field) -> outState.putString(key, field.text.toString()) }
        form?.save(outState, fields)
    }
}

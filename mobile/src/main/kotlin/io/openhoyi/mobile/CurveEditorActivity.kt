package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout

class CurveEditorActivity : ThemedActivity() {
    private class FieldFailure(val key: String, val label: Int) : IllegalArgumentException()
    companion object { const val CURVE_ID = "curveId" }
    private var form: TaskCurveEditorForm? = null
    private val fields = linkedMapOf<String, EditText>()
    private var draftId = CustomCurveDocument.newId()
    private var stageCount = 2
    private var flowMode = false
    private var firstFlow = false
    private var pressureLogic = false
    private var pressureBeforeFlow = false
    private var sourceDocument: CustomCurveDocument? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val style = TaskProductFormStyle(this)
        val body = style.visual.page(this, getString(R.string.profiles_editor), getString(R.string.flow_editor_subtitle))
        val store = profileStore(body) ?: return
        val sourceId = intent.getStringExtra(CURVE_ID)
        draftId = savedInstanceState?.getString("draftId") ?: draftId
        val source = savedInstanceState?.getString("sourceDocument")?.let { runCatching { CustomCurveDocument.decode(it) }.getOrNull() }
            ?: if (sourceId == null) CustomCurveDocument(draftId, getString(R.string.profiles_default_name), 93, 3600,
            CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(30, 150), CustomCurveDocument.Stage(90, 400)))
        else runCatching {
            store.find(sourceId) ?: (application as MobileApplication).curves.find(sourceId)?.let { CustomCurveDocument.fromLibraryItem(it, draftId) }
        }.getOrNull()
        if (source == null) { HoyiUi.label(this, body, getString(R.string.profiles_missing), 17); return }
        sourceDocument = source
        draftId = source.id
        if (!source.isEditable) { profileCard(body, source); return }
        flowMode = savedInstanceState?.getBoolean("flowMode") ?: (source.controlMode == CustomCurveDocument.ControlMode.FLOW)
        firstFlow = savedInstanceState?.getBoolean("firstFlow") ?: (source.configuration?.firstSegmentFlowMode == true)
        pressureLogic = savedInstanceState?.getBoolean("pressureLogic") ?: (source.configuration?.pressureLogic == true)
        pressureBeforeFlow = savedInstanceState?.getBoolean("pressureBeforeFlow") ?: false
        if (sourceId != null && !sourceId.startsWith("draft-")) style.visual.add(body,
            style.visual.text(getString(R.string.profiles_copy_warning), TaskProductVisualStyle.auxiliary, secondary = true))
        stageCount = savedInstanceState?.getInt("stageCount") ?: source.stages.size
        val sections = TaskCurveEditorForm(this, body, style)
        form = sections
        fun field(parent: LinearLayout, key: String, label: Int, initial: String, numeric: Boolean = true,
            compactLabel: String? = null, tableCell: Boolean = false): EditText {
            val input = if (tableCell) EditText(this).apply {
                id = View.generateViewId()
                textSize = 16f
                typeface = TaskTypography.face(this@CurveEditorActivity, 400)
                includeFontPadding = false
                setTextColor(getColor(R.color.mobile_text))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(true)
                minimumHeight = HoyiUi.dp(this@CurveEditorActivity, 52)
                setPadding(HoyiUi.dp(this@CurveEditorActivity, 12), HoyiUi.dp(this@CurveEditorActivity, 8),
                    HoyiUi.dp(this@CurveEditorActivity, 12), HoyiUi.dp(this@CurveEditorActivity, 8))
                background = HoyiUi.shape(this@CurveEditorActivity, R.color.mobile_surface, 10, R.color.mobile_border)
                parent.addView(this, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = HoyiUi.dp(this@CurveEditorActivity, 8) })
            } else beanInput(parent, label, numeric).also { editor ->
                if (compactLabel != null) for (index in 0 until parent.childCount) {
                    val title = parent.getChildAt(index) as? android.widget.TextView ?: continue
                    if (title.labelFor == editor.id) {
                        title.text = compactLabel
                        style.label(title)
                    }
                }
            }
            style.input(input, "curve-editor", key)
            input.contentDescription = getString(label)
            input.tooltipText = getString(label)
            fields[key] = input
            input.setText(savedInstanceState?.getString(key) ?: initial)
            return input
        }
        val name = field(sections.metadata[0], "name", R.string.profiles_name, source.name, false,
            compactLabel = getString(R.string.profiles_name))
        val temperature = field(sections.metadata[1], "temperature", R.string.profiles_temperature, source.temperatureC.toString(),
            compactLabel = getString(R.string.curve_preview_temperature))
        val cup = field(sections.metadata[2], "cup", R.string.profiles_cup, CurveDraftNumber.format(source.targetHundredthsGram, 2),
            compactLabel = getString(R.string.curve_preview_weight))
        style.unit(temperature, "°C", "curve-editor", "temperature")
        style.unit(cup, "g", "curve-editor", "cup")
        fun numericRow(container: LinearLayout, label: Int, tag: String? = null): LinearLayout = object : LinearLayout(this@CurveEditorActivity) {
                init {
                    isBaselineAligned = false
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(style.visual.text(getString(label), TaskProductVisualStyle.auxiliary, tag, true), LayoutParams(-2, -2))
                    container.addView(this, LayoutParams(-1, -2).apply { topMargin = style.dp(6) })
                }
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val title = getChildAt(0)
                    val inputHost = if (childCount > 1) getChildAt(1) else null
                    fun input(view: View?): EditText? = when (view) {
                        is EditText -> view
                        is android.view.ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { input(view.getChildAt(it)) }
                        else -> null
                    }
                    val editor = input(inputHost)
                    val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    title.measure(free, free)
                    // Measuring the actual unit wrapper updates the real compound padding for bar/mL/s.
                    inputHost?.measure(free, free)
                    val digits = editor?.let {
                        kotlin.math.ceil(android.text.Layout.getDesiredWidth("999.0", it.paint).toDouble()).toInt() +
                            it.compoundPaddingLeft + it.compoundPaddingRight + style.dp(4)
                    } ?: 0
                    val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                    val stacked = title.measuredWidth + style.dp(8) + digits > available
                    orientation = if (stacked) VERTICAL else HORIZONTAL
                    (title.layoutParams as LayoutParams).apply {
                        width = if (stacked) -1 else -2
                        bottomMargin = if (stacked) style.dp(6) else 0
                    }
                    (inputHost?.layoutParams as? LayoutParams)?.apply {
                        width = if (stacked) -1 else 0
                        weight = if (stacked) 0f else 1f
                        marginStart = if (stacked) 0 else style.dp(8)
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
        (0..3).forEach { index ->
            val stage = source.allStages.getOrNull(index) ?: CustomCurveDocument.Stage(60, 100)
            val row = sections.stageRows[index]
            fun quantity(label: Int, tag: String) = numericRow(row, label, tag)
            val target = field(quantity(R.string.curve_editor_target_pressure, "curve-stage-target-label-$index"), "target-$index", R.string.profiles_pressure, CurveDraftNumber.format(stage.target, 1), tableCell = true)
            val water = field(quantity(R.string.curve_editor_stage_water, "curve-stage-water-label-$index"), "water-$index", R.string.profiles_water, CurveDraftNumber.format(stage.waterTenthsMl, 1), tableCell = true)
            style.unit(target, "bar", "curve-editor", "target-$index")
            style.unit(water, "ml", "curve-editor", "water-$index")
        }
        fun settingRow(parent: LinearLayout, label: Int) = numericRow(parent, label)
        val totalLimit = field(settingRow(sections.waterLimit, R.string.profiles_total_water_limit_title), "total-water-limit", R.string.profiles_total_water_limit,
            source.totalWaterLimitMl?.toString().orEmpty(), tableCell = true).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.profiles_total_water_limit_hint)
        }
        style.unit(totalLimit, "mL", "curve-editor", "total-water-limit")
        val assignedWater = style.visual.text("", TaskProductVisualStyle.auxiliary,
            "product/form/curve-editor/assigned-water", true)
        style.visual.add(sections.waterLimit, assignedWater, 8)
        var previewChanged: () -> Unit = {}
        fun picker(parent: LinearLayout, label: Int, options: List<String>, selection: Int, changed: (Int) -> Unit): android.widget.Spinner {
            style.visual.add(parent, style.visual.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true).apply { tag = "flow-picker-label-$label" })
            return android.widget.Spinner(this).apply {
                id = View.generateViewId(); contentDescription = getString(label); minimumHeight = style.dp(48)
                adapter = style.choiceAdapter(options)
                setSelection(selection)
                parent.addView(this, LinearLayout.LayoutParams(-1, -2))
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { changed(position) }
                }
            }
        }
        lateinit var firstModePicker: android.widget.Spinner
        lateinit var controlSwitch: android.widget.Switch
        lateinit var logicSwitch: android.widget.Switch
        fun units() {
            (0..3).forEach { index ->
                val flow = flowMode && (index > 0 || firstFlow)
                sections.stageModes[index].text = getString(when {
                    flowMode && index == 0 && !firstFlow -> R.string.curve_editor_mode_first_pressure
                    flow && pressureLogic -> R.string.curve_editor_mode_flow_fast
                    flow -> R.string.curve_editor_mode_flow_slow
                    pressureLogic -> R.string.curve_editor_mode_pressure_follow
                    else -> R.string.curve_editor_mode_pressure_constant
                })
                sections.stageRows[index].findViewWithTag<android.widget.TextView>("curve-stage-target-label-$index").setText(
                    if (flow) R.string.curve_editor_target_flow else R.string.curve_editor_target_pressure)
                val target = fields.getValue("target-$index")
                (target.parent as? android.view.ViewGroup)?.findViewWithTag<android.widget.TextView>("product/form/curve-editor/target-$index/unit")?.text = if (flow) "mL/s" else "bar"
                target.contentDescription = getString(when {
                    flow -> R.string.flow_editor_flow_target
                    flowMode -> R.string.flow_editor_first_pressure_error
                    source.configuration != null && index == 0 -> R.string.flow_editor_complete_first_pressure
                    source.configuration != null -> R.string.flow_editor_complete_next_pressure
                    else -> R.string.profiles_pressure
                })
                target.tooltipText = target.contentDescription
            }
            firstModePicker.isEnabled = flowMode
            firstModePicker.visibility = if (flowMode) View.VISIBLE else View.GONE
            sections.firstModeHost.findViewWithTag<View>("flow-picker-label-${R.string.flow_editor_first}")?.visibility = if (flowMode) View.VISIBLE else View.GONE
            controlSwitch.text = getString(if (flowMode) R.string.curve_control_flow else R.string.curve_control_pressure)
            controlSwitch.contentDescription = getString(R.string.flow_editor_control) + " · " + controlSwitch.text
            logicSwitch.text = getString(when {
                flowMode && pressureLogic -> R.string.curve_control_fast
                flowMode -> R.string.curve_control_slow
                pressureLogic -> R.string.curve_control_follow
                else -> R.string.curve_control_constant
            })
            logicSwitch.contentDescription = logicSwitch.text
            previewChanged()
        }
        val modeRow = object : LinearLayout(this) {
            init { isBaselineAligned = false }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val stacked = View.MeasureSpec.getSize(widthMeasureSpec) < style.dp((360 * resources.configuration.fontScale).toInt())
                orientation = if (stacked) VERTICAL else HORIZONTAL
                for (i in 0 until childCount) (getChildAt(i).layoutParams as LayoutParams).apply {
                    width = if (stacked) -1 else 0; weight = if (stacked) 0f else 1f
                    marginStart = if (i > 0 && !stacked) style.dp(24) else 0
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        sections.modeControls.addView(modeRow, LinearLayout.LayoutParams(-1, -2))
        controlSwitch = HoyiSwitch(this).apply {
            textSize = 16f; minimumHeight = style.dp(48)
            typeface = TaskTypography.face(this@CurveEditorActivity, 400)
            setPadding(0, style.dp(8), 0, style.dp(8))
            setTextColor(getColor(R.color.mobile_text))
            isChecked = flowMode; tag = "product/form/curve-editor/control-mode"
            modeRow.addView(this, LinearLayout.LayoutParams(-1, -2))
            setOnCheckedChangeListener { _, checked ->
                val next = CurveModeSelection(flowMode, pressureLogic, pressureBeforeFlow).selectFlow(checked)
                flowMode = next.flow
                pressureLogic = next.pressure
                pressureBeforeFlow = next.pressureBeforeFlow
                logicSwitch.isChecked = pressureLogic
                units()
            }
        }
        firstModePicker = picker(sections.firstModeHost, R.string.flow_editor_first,
            listOf(getString(R.string.flow_editor_first_pressure), getString(R.string.flow_editor_first_flow)), if (firstFlow) 1 else 0) { position ->
            firstFlow = position == 1; units()
        }.apply { tag = "product/form/curve-editor/first-mode"; isEnabled = flowMode }
        logicSwitch = HoyiSwitch(this).apply {
            textSize = 16f; minimumHeight = style.dp(48)
            typeface = TaskTypography.face(this@CurveEditorActivity, 400)
            setPadding(0, style.dp(8), 0, style.dp(8))
            setTextColor(getColor(R.color.mobile_text))
            isChecked = pressureLogic; tag = "product/form/curve-editor/pressure-logic"
            modeRow.addView(this, LinearLayout.LayoutParams(-1, -2))
            setOnCheckedChangeListener { _, checked ->
                pressureLogic = checked
                units()
            }
        }
        val bloom = field(settingRow(sections.configurationFields, R.string.flow_editor_preinfusion), "preinfusion", R.string.flow_editor_preinfusion,
            (source.configuration?.preinfusionSeconds ?: 0).toString(), tableCell = true).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val duration = field(settingRow(sections.configurationFields, R.string.flow_editor_first_duration), "first-duration", R.string.flow_editor_first_duration,
            (source.configuration?.firstDurationSeconds ?: 0).toString(), tableCell = true).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val compensation = field(settingRow(sections.configurationFields, R.string.flow_editor_compensation), "compensation", R.string.flow_editor_compensation,
            CurveDraftNumber.format(source.configuration?.compensationHundredthsGram ?: 0, 2), tableCell = true).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        style.unit(bloom, "s", "curve-editor", "preinfusion")
        style.unit(duration, "s", "curve-editor", "first-duration")
        style.unit(compensation, "g", "curve-editor", "compensation")
        style.visual.add(sections.modeControls, style.visual.text(getString(R.string.flow_editor_units_hint), TaskProductVisualStyle.auxiliary, secondary = true), 4)
        fun visibility() {
            sections.setStageCount(stageCount)
            previewChanged()
        }
        sections.onStageCountChanged { count ->
            stageCount = count
            visibility()
        }
        visibility()
        val error = style.error(beanError(body))
        fun parsed(): CustomCurveDocument {
            val allStages = (0..3).map {
                CustomCurveDocument.Stage(CurveDraftNumber.parse(fields.getValue("target-$it").text.toString(), 1),
                    CurveDraftNumber.parse(fields.getValue("water-$it").text.toString(), 1))
            }
            val stages = allStages.take(stageCount)
            val maximum = maxOf(((stages.sumOf { it.waterTenthsMl.toLong() } + 9) / 10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 800)
            val limit = totalLimit.text.toString().trim().takeIf { it.isNotEmpty() }?.let { text ->
                try { CurveDraftNumber.parse(text, 0) }
                catch (_: IllegalArgumentException) { throw CurveWaterLimitException(maximum) }
            }
            val preinfusion = CurveDraftNumber.parse(bloom.text.toString(), 0)
            val firstDuration = CurveDraftNumber.parse(duration.text.toString(), 0)
            val compensationText = compensation.text.toString().trim()
            val compensationValue = if (compensationText.startsWith("-")) -CurveDraftNumber.parse(compensationText.drop(1), 2)
                else CurveDraftNumber.parse(compensationText, 2)
            // Reducing an old four-stage recipe must retain its hidden slots after reopening.
            val tail = allStages.drop(stageCount)
            val editedTail = tail.indices.any { tail[it] != source.allStages[stageCount + it] }
            val complete = source.configuration != null || flowMode || pressureLogic || preinfusion != 0 || firstDuration != 0 || compensationValue != 0 ||
                ((sourceId != null || editedTail) && tail.any { it.target != 0 || it.waterTenthsMl != 0 })
            if (complete && stageCount < 2) throw FieldFailure("target-0", R.string.flow_editor_complete_stage_error)
            if (preinfusion !in 0..127) throw FieldFailure("preinfusion", R.string.flow_editor_bloom_error)
            if (firstDuration !in 0..254) throw FieldFailure("first-duration", R.string.flow_editor_time_error)
            if (compensationValue !in -10000..10000) throw FieldFailure("compensation", R.string.flow_editor_compensation_error)
            stages.forEachIndexed { index, stage ->
                val range = when { flowMode && (index > 0 || firstFlow) -> 10..100; flowMode -> 5..100
                    complete && index == 0 -> 5..120; complete -> 10..120; else -> 0..120 }
                if (stage.target !in range) throw FieldFailure("target-$index", when {
                    flowMode && (index > 0 || firstFlow) -> R.string.flow_editor_flow_error
                    flowMode -> R.string.flow_editor_first_pressure_error
                    complete && index == 0 -> R.string.flow_editor_complete_first_pressure
                    complete -> R.string.flow_editor_complete_next_pressure
                    else -> R.string.flow_editor_pressure_error
                })
                if (complete && stage.waterTenthsMl > 9990) throw FieldFailure("water-$index", R.string.flow_editor_complete_water_error)
                if (stage.waterTenthsMl == 0 && (!flowMode || index > 0)) throw FieldFailure("water-$index", R.string.flow_editor_water_positive_error)
            }
            if (flowMode && firstDuration == 0 && stages.first().waterTenthsMl == 0) throw FieldFailure("first-duration", R.string.flow_editor_exit_error)
            val targetCup = CurveDraftNumber.parse(cup.text.toString(), 2)
            if (complete && targetCup > 0 && compensationValue >= targetCup) throw FieldFailure("compensation", R.string.flow_editor_compensation_target_error)
            val config = if (complete)
                CustomCurveDocument.Configuration(pressureLogic, flowMode, flowMode && firstFlow, preinfusion, firstDuration,
                    compensationValue, tail) else null
            return CustomCurveDocument(draftId, name.text.toString().trim(),
                CurveDraftNumber.parse(temperature.text.toString(), 0), targetCup,
                if (flowMode) CustomCurveDocument.ControlMode.FLOW else CustomCurveDocument.ControlMode.PRESSURE, stages, limit, config).also { it.validate() }
        }
        fun showError(failure: Throwable) {
            error.text = if (failure is FieldFailure) {
                fields[failure.key]?.apply { setError(getString(failure.label)); requestFocus() }
                getString(failure.label)
            } else if (failure is CurveWaterLimitException)
                getString(R.string.profiles_total_water_limit_invalid, 1, failure.maximumMl)
            else getString(R.string.flow_editor_invalid)
        }
        val preview = CurveStageView(this).apply { tag = "task-form-stage-preview"; productVisualStyle = style.visual }
        style.visual.add(sections.supportingBody, style.visual.text(getString(R.string.profiles_preview), TaskProductVisualStyle.section))
        sections.supportingBody.addView(preview, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 200)))
        val previewStatus = style.visual.text("", TaskProductVisualStyle.auxiliary).apply {
            visibility = View.GONE
            style.visual.add(sections.supportingBody, this)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        previewChanged = {
            assignedWater.text = runCatching {
                val tenths = (0 until stageCount).sumOf {
                    CurveDraftNumber.parse(fields.getValue("water-$it").text.toString(), 1).also { require(it in 0..65535) }
                }
                getString(R.string.profiles_assigned_water, CurveDraftNumber.format(tenths, 1))
            }.getOrElse { getString(R.string.profiles_assigned_water_invalid) }
            runCatching { parsed() }.onSuccess { doc ->
                preview.targets = doc.stages.map { it.target }
                preview.preview = CurveStageParameters.preview(doc)
                preview.contentDescription = profileSummary(doc)
                previewStatus.text = listOfNotNull(
                    getString(R.string.profiles_summary, doc.temperatureC, CurveDraftNumber.format(doc.targetHundredthsGram, 2), doc.stages.size),
                    profileWaterLimitSummary(doc), profileExecutionSummary(doc), profileConfigurationSummary(doc)).joinToString("\n")
            }.onFailure {
                preview.targets = emptyList()
                preview.preview = null
                preview.contentDescription = getString(R.string.flow_editor_invalid)
                previewStatus.setText(R.string.flow_editor_invalid)
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
        units()
        sections.restore(savedInstanceState, fields)
        val supportingActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            sections.supportingBody.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = HoyiUi.dp(this@CurveEditorActivity, 8)
            })
        }
        HoyiUi.button(this, supportingActions, getString(R.string.profiles_preview)) {
            runCatching { parsed() }.onSuccess { profilePreviewDialog(it); error.text = "" }.onFailure(::showError)
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f); style.visual.button(this) }
        HoyiUi.button(this, supportingActions, getString(R.string.ui_full_details)) {
            beanDetails(getString(R.string.profiles_editor), listOf(R.string.profiles_temperature, R.string.profiles_cup,
                R.string.profiles_pressure, R.string.flow_editor_flow_target, R.string.profiles_water, R.string.profiles_total_water_limit,
                R.string.profiles_total_water_limit_scale, R.string.profiles_pressure_execution_limits,
                R.string.flow_editor_switch_hint, R.string.flow_editor_limit_warning).joinToString("\n\n") { getString(it) })
        }.apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f); style.visual.button(this) }
        val footer = beanActions(body)
        val actionRow = style.visual.row()
        style.visual.action(actionRow, getString(R.string.beans_cancel), "product/form/curve-editor/cancel", top = 0) { finish() }
            .layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(8) }
        val save = style.visual.action(actionRow, getString(R.string.profiles_save), "product/form/curve-editor/save", primary = true, top = 0) {}
        save.layoutParams = LinearLayout.LayoutParams(-2, -2)
        val notice = style.visual.text(getString(R.string.form_visual_local_curve_scope), TaskProductVisualStyle.auxiliary,
            "product/form/curve-editor/scope", true)
        footer.addView(style.footer(notice, actionRow, "curve-editor", topRule = true), LinearLayout.LayoutParams(-1, -2))
        save.setOnClickListener {
            val doc = runCatching { parsed() }.getOrElse { showError(it); return@setOnClickListener }
            val available = (application as MobileApplication).customCurvesResult.getOrNull()
            if (available == null) { error.setText(R.string.profiles_unavailable); save.isEnabled = false; return@setOnClickListener }
            save.isEnabled = false
            if (runCatching { available.save(doc, source.takeIf { sourceId?.startsWith("draft-") == true }) }.isSuccess) finish()
            else { error.setText(R.string.profiles_save_failed); save.isEnabled = true }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draftId", draftId); outState.putInt("stageCount", stageCount)
        sourceDocument?.let { outState.putString("sourceDocument", it.encode()) }
        outState.putBoolean("flowMode", flowMode); outState.putBoolean("firstFlow", firstFlow); outState.putBoolean("pressureLogic", pressureLogic)
        outState.putBoolean("pressureBeforeFlow", pressureBeforeFlow)
        fields.forEach { (key, field) -> outState.putString(key, field.text.toString()) }
        form?.save(outState, fields)
    }
}

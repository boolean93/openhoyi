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
    private var modeSwitchTouched = false
    private var sourceDocument: CustomCurveDocument? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val style = TaskProductFormStyle(this)
        val colors = CurveEditorColors(this)
        val body = style.visual.page(this, getString(R.string.curve_editor_title), getString(R.string.flow_editor_subtitle))
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
        modeSwitchTouched = savedInstanceState?.getBoolean("modeSwitchTouched") ?: false
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
            input.minimumHeight = style.dp(48); input.minHeight = style.dp(48)
            input.contentDescription = getString(label)
            input.tooltipText = getString(label)
            fields[key] = input
            input.setText(savedInstanceState?.getString(key) ?: initial)
            return input
        }
        val name = field(sections.metadata[0], "name", R.string.profiles_name, source.name, false,
            compactLabel = getString(R.string.profiles_name))
        // The name is the editable title companion, with its accessible label retained on the field.
        (name.parent as? android.view.ViewGroup)?.let { host ->
            (0 until host.childCount).map(host::getChildAt).filter { it !== name }.forEach { it.visibility = View.GONE }
        }
        val temperature = field(sections.metadata[1], "temperature", R.string.profiles_temperature, source.temperatureC.toString(),
            compactLabel = getString(R.string.curve_editor_temperature_short))
        val cup = field(sections.metadata[2], "cup", R.string.profiles_cup, CurveDraftNumber.format(source.targetHundredthsGram, 2),
            compactLabel = getString(R.string.curve_preview_weight))
        temperature.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        fun validateMetadata(input: EditText, label: Int, parse: (String) -> Int): Boolean {
            val valid = runCatching { parse(input.text.toString()) }.isSuccess
            input.error = if (valid) null else getString(label)
            return valid
        }
        temperature.setOnFocusChangeListener { _, focused ->
            if (!focused) validateMetadata(temperature, R.string.profiles_temperature, CurveMetadataInput::temperature)
        }
        cup.setOnFocusChangeListener { _, focused ->
            if (!focused) validateMetadata(cup, R.string.profiles_cup, CurveMetadataInput::targetWeight)
        }
        style.unit(temperature, "°C", "curve-editor", "temperature")
        style.unit(cup, "g", "curve-editor", "cup")
        fun numericRow(container: LinearLayout, label: Int, tag: String? = null): LinearLayout = object : LinearLayout(this@CurveEditorActivity) {
                init {
                    isBaselineAligned = false
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    addView(style.visual.text(getString(label), TaskProductVisualStyle.auxiliary, tag, true), LayoutParams(-2, -2))
                    container.addView(this, LayoutParams(-1, -2).apply { topMargin = style.dp(4) })
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
                        kotlin.math.ceil(android.text.Layout.getDesiredWidth("6553.5", it.paint).toDouble()).toInt() +
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
                        resolveLayoutDirection(this@CurveEditorActivity.resources.configuration.layoutDirection)
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
        val fieldErrors = linkedMapOf<String, android.widget.TextView>()
        fun attachError(key: String, editor: EditText, container: LinearLayout) {
            val label = style.visual.text("", TaskProductVisualStyle.auxiliary).apply {
                tag = "curve-editor-error-$key"; visibility = View.GONE
                setTextColor(getColor(R.color.mobile_danger))
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            var holder: View = editor
            while (holder.parent !== container) holder = holder.parent as View
            container.addView(label, container.indexOfChild(holder) + 1, LinearLayout.LayoutParams(-1, -2))
            fieldErrors[key] = label
        }
        (0..3).forEach { index ->
            val stage = source.allStages.getOrNull(index) ?: CustomCurveDocument.Stage(60, 100)
            val unused = index >= source.stages.size && source.configuration == null
            val row = sections.stageRows[index]
            fun quantity(label: Int, tag: String) = numericRow(row, label, tag)
            val target = field(quantity(R.string.curve_editor_target_pressure, "curve-stage-target-label-$index"), "target-$index", R.string.profiles_pressure, if (unused) "" else CurveDraftNumber.format(stage.target, 1), tableCell = true)
            val water = field(quantity(R.string.curve_editor_stage_water, "curve-stage-water-label-$index"), "water-$index", R.string.profiles_water, if (unused) "" else CurveDraftNumber.format(stage.waterTenthsMl, 1), tableCell = true)
            style.unit(target, "bar", "curve-editor", "target-$index")
            style.unit(water, "mL", "curve-editor", "water-$index")
            attachError("target-$index", target, row)
            attachError("water-$index", water, row)
            if (index > 0) row.addView(sections.sharedLogicHosts[index], LinearLayout.LayoutParams(-1, -2))
        }
        fun settingRow(parent: LinearLayout, label: Int) = numericRow(parent, label)
        val totalLimit = field(settingRow(sections.waterLimit, R.string.profiles_total_water_limit_title), "total-water-limit", R.string.profiles_total_water_limit,
            source.totalWaterLimitMl?.toString().orEmpty(), tableCell = true).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.profiles_total_water_limit_hint)
        }
        style.unit(totalLimit, "mL", "curve-editor", "total-water-limit")
        attachError("total-water-limit", totalLimit, sections.waterLimit)
        val assignedWater = style.visual.text("", TaskProductVisualStyle.auxiliary,
            "product/form/curve-editor/assigned-water", true)
        style.visual.add(sections.waterLimit, assignedWater, 8)
        val modeHint = style.visual.text("", TaskProductVisualStyle.auxiliary).apply {
            tag = "curve-editor-mode-hint"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        sections.stageHeader.addView(modeHint, LinearLayout.LayoutParams(-1, -2))
        fun updateModeHint() {
            val tail = (stageCount..3).map { index ->
                runCatching {
                    CustomCurveDocument.Stage(CurveDraftNumber.parse(fields.getValue("target-$index").text.toString(), 1),
                        CurveDraftNumber.parse(fields.getValue("water-$index").text.toString(), 1)).also {
                        require(it.target in 0..255 && it.waterTenthsMl in 0..65535)
                    }
                }.getOrDefault(source.allStages[index])
            }
            val editedTail = tail.indices.any { tail[it] != source.allStages[stageCount + it] }
            val configured = flowMode || pressureLogic || source.configuration != null ||
                ((sourceId != null || editedTail) && tail.any { it.target != 0 || it.waterTenthsMl != 0 }) ||
                listOf("preinfusion", "first-duration", "compensation").any { key ->
                    fields[key]?.text?.toString()?.trim()?.toBigDecimalOrNull()?.signum()?.let { it != 0 } == true
                }
            modeHint.visibility = if (stageCount == 1 && configured) View.VISIBLE else View.GONE
            modeHint.setText(R.string.flow_editor_complete_stage_error)
            modeHint.setTextColor(getColor(R.color.mobile_danger))
        }
        var previewChanged: () -> Unit = {}
        lateinit var firstModePicker: CurveEditorSegments
        val controlSwitches = mutableListOf<CurveEditorSegments>()
        val logicSwitches = mutableListOf<CurveEditorSegments>()
        fun units() {
            (0..3).forEach { index ->
                val flow = flowMode && (index > 0 || firstFlow)
                sections.setStageType(index, flow)
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
            sections.firstModeHost.visibility = View.VISIBLE
            sections.stageModes[0].visibility = if (flowMode) View.GONE else View.VISIBLE
            firstModePicker.contentDescription = getString(R.string.flow_editor_first) + " · " + sections.stageModes[0].text
            // First-stage type is independent; stages 2–4 share the control and logic bits.
            firstModePicker.labels(getString(R.string.curve_editor_mode_first_pressure), getString(R.string.curve_editor_first_flow))
            firstModePicker.select(firstFlow)
            controlSwitches.forEach { picker ->
                picker.colors(colors.pressureFill, colors.flowFill)
                picker.select(flowMode)
            }
            firstModePicker.colors(colors.pressureFill, colors.flowFill)
            logicSwitches.forEach { picker ->
                picker.colors(colors.fill(flowMode), colors.fill(flowMode))
                picker.labels(getString(if (flowMode) R.string.curve_control_slow else R.string.curve_control_constant),
                    getString(if (flowMode) R.string.curve_control_fast else R.string.curve_control_follow))
                picker.select(pressureLogic)
            }
            updateModeHint()
            previewChanged()
        }
        (1..3).forEach { index ->
            controlSwitches += CurveEditorSegments(this, style.visual,
                getString(R.string.curve_control_pressure), getString(R.string.curve_control_flow)).apply {
                tag = if (index == 1) "product/form/curve-editor/control-mode" else "product/form/curve-editor/control-mode-$index"
                compact()
                contentDescription = getString(R.string.curve_editor_shared_mode)
                sections.sharedModeHosts[index].addView(this, LinearLayout.LayoutParams(-1, -2))
                onSelected = { checked ->
                    modeSwitchTouched = true
                    val next = CurveModeSelection(flowMode, pressureLogic, pressureBeforeFlow).selectFlow(checked)
                    flowMode = next.flow; pressureLogic = next.pressure; pressureBeforeFlow = next.pressureBeforeFlow
                    units()
                }
            }
        }
        firstModePicker = CurveEditorSegments(this, style.visual,
            getString(R.string.curve_editor_mode_first_pressure), getString(R.string.curve_editor_first_flow)).apply {
            tag = "product/form/curve-editor/first-mode"
            compact()
            select(firstFlow)
            sections.firstModeHost.addView(this, LinearLayout.LayoutParams(-1, -2))
            onSelected = { firstFlow = it; units() }
        }
        (1..3).forEach { index ->
            logicSwitches += CurveEditorSegments(this, style.visual, "", "").apply {
                tag = if (index == 1) "product/form/curve-editor/pressure-logic" else "product/form/curve-editor/pressure-logic-$index"
                compact()
                contentDescription = getString(R.string.curve_editor_shared_mode)
                sections.sharedLogicHosts[index].addView(this, LinearLayout.LayoutParams(-1, -2))
                onSelected = { pressureLogic = it; units() }
            }
        }
        val bloom = field(settingRow(sections.configurationFields, R.string.curve_editor_bloom_short), "preinfusion", R.string.flow_editor_preinfusion,
            (source.configuration?.preinfusionSeconds ?: 0).toString(), tableCell = true).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val duration = field(settingRow(sections.configurationFields, R.string.curve_editor_duration_short), "first-duration", R.string.flow_editor_first_duration,
            (source.configuration?.firstDurationSeconds ?: 0).toString(), tableCell = true).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val compensation = field(settingRow(sections.configurationFields, R.string.curve_editor_compensation_short), "compensation", R.string.flow_editor_compensation,
            CurveDraftNumber.format(source.configuration?.compensationHundredthsGram ?: 0, 2), tableCell = true).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        style.unit(bloom, "s", "curve-editor", "preinfusion")
        style.unit(duration, "s", "curve-editor", "first-duration")
        style.unit(compensation, "g", "curve-editor", "compensation")
        attachError("preinfusion", bloom, sections.configurationFields)
        attachError("first-duration", duration, sections.configurationFields)
        attachError("compensation", compensation, sections.configurationFields)
        fun visibility() {
            sections.setStageCount(stageCount)
            updateModeHint()
            previewChanged()
        }
        sections.onStageCountChanged { count ->
            stageCount = count
            visibility()
        }
        visibility()
        val error = style.error(beanError(body)).apply { visibility = View.GONE }
        error.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                error.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        fun parsed(): CustomCurveDocument {
            val targetTemperature = try { CurveMetadataInput.temperature(temperature.text.toString()) }
                catch (_: IllegalArgumentException) { throw FieldFailure("temperature", R.string.profiles_temperature) }
            val targetCup = try { CurveMetadataInput.targetWeight(cup.text.toString()) }
                catch (_: IllegalArgumentException) { throw FieldFailure("cup", R.string.profiles_cup) }
            val allStages = (0..3).map {
                fun stage() = CustomCurveDocument.Stage(CurveDraftNumber.parse(fields.getValue("target-$it").text.toString(), 1),
                    CurveDraftNumber.parse(fields.getValue("water-$it").text.toString(), 1)).also { value ->
                        require(value.target in 0..255 && value.waterTenthsMl in 0..65535)
                    }
                // A removed, unfinished card must not block saving the remaining active stages.
                // Valid hidden edits survive; invalid hidden input retains its original protocol slot.
                if (it < stageCount) stage() else runCatching { stage() }.getOrDefault(source.allStages[it])
            }
            val stages = allStages.take(stageCount)
            val maximum = maxOf(((stages.sumOf { it.waterTenthsMl.toLong() } + 9) / 10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 800)
            val limit = totalLimit.text.toString().trim().takeIf { it.isNotEmpty() }?.let { text ->
                try { CurveDraftNumber.parse(text, 0) }
                catch (_: IllegalArgumentException) { throw CurveWaterLimitException(maximum) }
            }
            val preinfusion = try { CurveDraftNumber.parse(bloom.text.toString(), 0) }
                catch (_: IllegalArgumentException) { throw FieldFailure("preinfusion", R.string.flow_editor_bloom_error) }
            val firstDuration = try { CurveDraftNumber.parse(duration.text.toString(), 0) }
                catch (_: IllegalArgumentException) { throw FieldFailure("first-duration", R.string.flow_editor_time_error) }
            val compensationValue = try { CurveEditorSettings.compensation(compensation.text.toString()) }
                catch (_: IllegalArgumentException) { throw FieldFailure("compensation", R.string.flow_editor_compensation_error) }
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
            if (complete && targetCup > 0 && compensationValue >= targetCup) throw FieldFailure("compensation", R.string.flow_editor_compensation_target_error)
            val config = if (complete)
                CustomCurveDocument.Configuration(pressureLogic, flowMode, flowMode && firstFlow, preinfusion, firstDuration,
                    compensationValue, tail) else null
            return CustomCurveDocument(draftId, name.text.toString().trim(),
                targetTemperature, targetCup,
                if (flowMode) CustomCurveDocument.ControlMode.FLOW else CustomCurveDocument.ControlMode.PRESSURE, stages, limit, config).also { it.validate() }
        }
        fun showError(failure: Throwable) {
            error.text = if (failure is FieldFailure) {
                fields[failure.key]?.apply { setError(getString(failure.label)); requestFocus() }
                getString(failure.label)
            } else if (failure is CurveWaterLimitException) {
                val message = getString(R.string.profiles_total_water_limit_invalid, 1, failure.maximumMl)
                totalLimit.error = message; totalLimit.requestFocus()
                fieldErrors.getValue("total-water-limit").apply { text = message; visibility = View.VISIBLE }
                message
            }
            else getString(R.string.flow_editor_invalid)
        }
        val preview = CurveEditorPreviewView(this, style.visual).apply { tag = "task-form-stage-preview" }
        style.visual.add(sections.supportingBody, style.visual.text(getString(R.string.profiles_preview), TaskProductVisualStyle.section))
        val previewScroll = android.widget.HorizontalScrollView(this).apply {
            isFillViewport = true
            addView(preview, android.widget.FrameLayout.LayoutParams(-1, -1))
        }
        sections.supportingBody.addView(previewScroll, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 200)))
        val previewStatus = style.visual.text("", TaskProductVisualStyle.auxiliary).apply {
            style.visual.add(sections.supportingBody, this)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        previewChanged = {
            updateModeHint()
            assignedWater.text = runCatching {
                val tenths = (0 until stageCount).sumOf {
                    CurveDraftNumber.parse(fields.getValue("water-$it").text.toString(), 1).also { require(it in 0..65535) }
                }
                getString(R.string.profiles_assigned_water, CurveDraftNumber.format(tenths, 1))
            }.getOrElse { getString(R.string.profiles_assigned_water_invalid) }
            val draftPreview = CurveEditorPreview.parse((0 until stageCount).map {
                fields.getValue("target-$it").text.toString() to fields.getValue("water-$it").text.toString()
            }, flowMode, firstFlow, source.configuration != null || flowMode || pressureLogic ||
                (sourceId != null && source.allStages.drop(stageCount).any { it.target != 0 || it.waterTenthsMl != 0 }) ||
                listOf(bloom, duration, compensation).any { (it.text.toString().toBigDecimalOrNull()?.compareTo(java.math.BigDecimal.ZERO) ?: 0) != 0 })
            draftPreview.stages.forEachIndexed { index, stage ->
                listOf("target-$index" to stage.targetError, "water-$index" to stage.waterError).forEach { (key, reason) ->
                    val label = fieldErrors.getValue(key)
                    label.visibility = if (reason == null) View.GONE else View.VISIBLE
                    label.text = when (reason) {
                        CurveEditorPreview.Error.REQUIRED -> getString(R.string.curve_editor_value_required)
                        CurveEditorPreview.Error.NUMBER -> getString(R.string.curve_editor_value_number)
                        CurveEditorPreview.Error.RANGE -> if (key.startsWith("target")) getString(R.string.curve_editor_target_range,
                            CurveDraftNumber.format(stage.targetRange.first, 1), CurveDraftNumber.format(stage.targetRange.last, 1),
                            if (stage.flow) "mL/s" else "bar")
                            else getString(R.string.curve_editor_water_range, CurveDraftNumber.format(stage.waterRange.first, 1),
                                CurveDraftNumber.format(stage.waterRange.last, 1))
                        null -> ""
                    }
                }
            }
            val settingFailures = CurveEditorSettings.errors(
                bloom.text.toString(), duration.text.toString(), compensation.text.toString(), totalLimit.text.toString(),
                draftPreview.totalWater, flowMode, draftPreview.stages.firstOrNull()?.water,
                cup.text.toString())
            settingFailures.forEach { (key, reason) ->
                val message = when (reason) {
                    CurveEditorSettings.Error.BLOOM -> getString(R.string.flow_editor_bloom_error)
                    CurveEditorSettings.Error.DURATION -> getString(R.string.flow_editor_time_error)
                    CurveEditorSettings.Error.EXIT -> getString(R.string.flow_editor_exit_error)
                    CurveEditorSettings.Error.COMPENSATION -> getString(R.string.flow_editor_compensation_error)
                    CurveEditorSettings.Error.COMPENSATION_TARGET -> getString(R.string.flow_editor_compensation_target_error)
                    CurveEditorSettings.Error.TOTAL -> getString(R.string.profiles_total_water_limit_invalid, 1,
                        maxOf(((draftPreview.totalWater ?: 0) + 9) / 10, 800))
                    null -> ""
                }
                fieldErrors.getValue(key).apply { text = message; visibility = if (reason == null) View.GONE else View.VISIBLE }
            }
            fieldErrors.forEach { (key, label) -> if (label.visibility == View.GONE) fields[key]?.error = null }
            preview.model = draftPreview
            previewStatus.visibility = if (draftPreview.invalid) View.VISIBLE else View.GONE
            previewStatus.setText(R.string.curve_editor_preview_incomplete)
            Unit
        }
        fields.values.forEach { field ->
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Allow partial input while typing; clear an old error as soon as it is corrected.
                    if (field.error != null) when (field) {
                        temperature -> validateMetadata(field, R.string.profiles_temperature, CurveMetadataInput::temperature)
                        cup -> validateMetadata(field, R.string.profiles_cup, CurveMetadataInput::targetWeight)
                    }
                    error.text = ""
                    previewChanged()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        previewChanged()
        units()
        sections.restore(savedInstanceState, fields)
        val actionRow = object : LinearLayout(this) {
            init { isBaselineAligned = false; gravity = android.view.Gravity.END }
            override fun onMeasure(w: Int, h: Int) {
                val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                for (index in 0 until childCount) getChildAt(index).measure(free, free)
                val stacked = (0 until childCount).sumOf { getChildAt(it).measuredWidth } + style.dp(8) > View.MeasureSpec.getSize(w)
                orientation = if (stacked) VERTICAL else HORIZONTAL
                for (index in 0 until childCount) (getChildAt(index).layoutParams as LayoutParams).apply {
                    width = if (stacked) -1 else -2
                    marginEnd = if (!stacked && index == 0) style.dp(8) else 0
                }
                super.onMeasure(w, h)
            }
        }
        style.visual.action(actionRow, getString(R.string.beans_cancel), "product/form/curve-editor/cancel", top = 0) { finish() }
            .layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(8) }
        val save = style.visual.action(actionRow, getString(if (sourceId == null) R.string.curve_editor_create else R.string.curve_editor_save_copy),
            "product/form/curve-editor/save", primary = true, top = 0) {}
        save.layoutParams = LinearLayout.LayoutParams(-2, -2)
        sections.actions.addView(actionRow, LinearLayout.LayoutParams(-1, -2))
        save.setOnClickListener {
            if (!save.isEnabled || isFinishing) return@setOnClickListener
            val doc = runCatching { parsed() }.getOrElse { showError(it); return@setOnClickListener }
            val available = (application as MobileApplication).customCurvesResult.getOrNull()
            if (available == null) { error.setText(R.string.profiles_unavailable); save.isEnabled = false; return@setOnClickListener }
            save.isEnabled = false
            if (runCatching { if (sourceId == null) available.save(doc) else available.saveCopy(doc) }.isSuccess) finish()
            else { error.setText(R.string.profiles_save_failed); save.isEnabled = true }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("draftId", draftId); outState.putInt("stageCount", stageCount)
        sourceDocument?.let { outState.putString("sourceDocument", it.encode()) }
        outState.putBoolean("flowMode", flowMode); outState.putBoolean("firstFlow", firstFlow); outState.putBoolean("pressureLogic", pressureLogic)
        outState.putBoolean("pressureBeforeFlow", pressureBeforeFlow)
        outState.putBoolean("modeSwitchTouched", modeSwitchTouched)
        fields.forEach { (key, field) -> outState.putString(key, field.text.toString()) }
        form?.save(outState, fields)
    }
}

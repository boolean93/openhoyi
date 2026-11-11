package io.openhoyi.mobile

import android.content.Context

/** Presentation derives each unit from the complete mode parameters, never from pressureLogic. */
internal fun Context.profileModeLabel(doc: CustomCurveDocument): String {
    val preview = CurveStageParameters.preview(doc)
    return getString(when {
        !preview.renderable -> R.string.flow_curve_unknown_mode
        preview.mixed -> R.string.flow_curve_mixed_mode
        preview.hasFlow -> R.string.home_lever_flow
        else -> R.string.task_chart_pressure
    })
}

internal fun Context.profileStageCaption(doc: CustomCurveDocument): String {
    val preview = CurveStageParameters.preview(doc)
    return getString(when {
        !preview.renderable -> R.string.flow_curve_missing_modes
        preview.mixed -> R.string.curve_control_caption_mixed
        preview.hasFlow -> R.string.curve_control_caption_flow
        else -> R.string.profiles_stage_caption
    })
}

internal fun Context.profileTargetHeading(doc: CustomCurveDocument): String {
    val preview = CurveStageParameters.preview(doc)
    return getString(when {
        !preview.renderable -> R.string.flow_curve_raw_target
        preview.hasFlow -> R.string.flow_curve_control_target
        else -> R.string.import_table_pressure
    })
}

internal fun profileTableTarget(doc: CustomCurveDocument, index: Int): String {
    val preview = CurveStageParameters.preview(doc)
    val stage = preview.stages[index]
    if (stage.unit == CurveStageParameters.Unit.RAW_UNKNOWN) return stage.target.toString()
    val target = CurveDraftNumber.format(stage.target, 1)
    // The existing pressure-only table carries its unit in the column heading.
    return if (!preview.hasFlow) target else "$target ${if (stage.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND) "mL/s" else "bar"}"
}

internal fun Context.profileConfigurationSummary(doc: CustomCurveDocument): String? = doc.configuration?.let { configuration ->
    val mode = CurveStageParameters.preview(doc).stages.firstOrNull()?.unit
    val first = getString(if (mode == CurveStageParameters.Unit.FLOW_ML_PER_SECOND) R.string.home_lever_flow else R.string.task_chart_pressure)
    getString(R.string.flow_curve_configuration_summary,
        getString(when {
            configuration.variableFlowLogic && configuration.pressureLogic -> R.string.flow_curve_logic_hs
            configuration.variableFlowLogic -> R.string.flow_curve_logic_ls
            configuration.pressureLogic -> R.string.flow_curve_logic_follow
            else -> R.string.flow_curve_logic_pressure
        }),
        first, configuration.preinfusionSeconds, configuration.firstDurationSeconds,
        CurveDraftNumber.format(configuration.compensationHundredthsGram, 2))
}

internal fun Context.profileExecutionSummary(doc: CustomCurveDocument): String = getString(when {
    doc.controlMode == CustomCurveDocument.ControlMode.FLOW_RAW -> R.string.flow_curve_missing_modes_short
    doc.configuration?.let { !it.variableFlowLogic && it.pressureLogic } == true -> R.string.flow_curve_follow_read_only
    CustomCurveAdapter.profile(doc, false)?.parameters?.let(io.openhoyi.session.CustomCurveFirmwarePolicy::requiresReviewedFlowProfile) == true -> R.string.flow_curve_parameters_require_114
    CustomCurveAdapter.profile(doc, false) != null ->
        if (doc.configuration == null) R.string.profiles_pressure_compatible else R.string.flow_curve_parameters_compatible
    doc.configuration != null -> R.string.flow_curve_outside_execution_range
    else -> R.string.profiles_read_only
})

package io.openhoyi.mobile

/** A recipe has ordered stages, not samples or timestamps. Derive only from the shared recipe projection. */
internal class AiCurveRecipeDiagram private constructor(
    val stages: List<Stage>,
    val renderable: Boolean,
    val temperatureC: Int?,
    val preinfusionSeconds: Int?,
    val firstDurationSeconds: Int?,
    val waterLimitMl: Int?,
    val waterLimitFromStageSum: Boolean,
    val assignedWaterTenthsMl: Int,
    val targetHundredthsGram: Int?,
    private val pressureMaximumBar: Float,
    private val flowMaximumMlPerSecond: Float,
) {
    data class Stage(val ordinal: Int, val targetTenths: Int, val unit: CurveStageParameters.Unit, val waterTenthsMl: Int?)
    fun axisMaximum(unit: CurveStageParameters.Unit): Float? = if (stages.none { it.unit == unit }) null else when (unit) {
        CurveStageParameters.Unit.PRESSURE_BAR -> pressureMaximumBar
        CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> flowMaximumMlPerSecond
        CurveStageParameters.Unit.RAW_UNKNOWN -> null
    }
    fun axisTicks(unit: CurveStageParameters.Unit): List<Float> = axisMaximum(unit)?.let { maximum ->
        (0..4).map { maximum * it / 4 }
    } ?: emptyList()
    /** Independent unit scales; never infer a duration from water, target or the first-stage setting. */
    fun targetFraction(stage: Stage): Float? = when (stage.unit) {
        CurveStageParameters.Unit.PRESSURE_BAR -> stage.targetTenths / 10f / pressureMaximumBar
        CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> stage.targetTenths / 10f / flowMaximumMlPerSecond
        CurveStageParameters.Unit.RAW_UNKNOWN -> null
    }
    fun summary(english: Boolean): String = buildString {
        fun line(en: String, zh: String, value: String) { append(if (english) en else zh).append(": ").append(value).append('\n') }
        val missing = if (english) "not set" else "未设置"
        line("Temperature setting", "全程温度设定", temperatureC?.let { "$it °C" } ?: missing)
        val mode = when {
            stages.any { it.unit == CurveStageParameters.Unit.RAW_UNKNOWN } -> if (english) "unknown" else "未知"
            stages.all { it.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND } -> if (english) "flow" else "流量"
            stages.any { it.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND } -> if (english) "pressure / flow by stage" else "按段压力／流量"
            else -> if (english) "pressure" else "压力"
        }
        line("Stage control", "分段控制", mode)
        line("Preinfusion setting", "预浸时间设定", preinfusionSeconds?.let { "$it s" } ?: missing)
        line("First-stage duration setting", "仅首段时间设定", firstDurationSeconds?.let { "$it s" } ?: missing)
        line(if (waterLimitFromStageSum) "Stage water sum" else "Water ceiling setting", if (waterLimitFromStageSum) "分段水量合计" else "总水量上限设定", if (waterLimitFromStageSum) "${CurveDraftNumber.format(assignedWaterTenthsMl, 1)} mL" else waterLimitMl?.let { "$it mL" } ?: missing)
        line("Weight target setting", "目标重量设定", targetHundredthsGram?.let {
            if (it == 0) if (english) "off (0 g)" else "关闭（0 g）" else "${CurveDraftNumber.format(it, 2)} g"
        } ?: missing)
        append(if (english) "Actual stopping depends on machine / scale connection and execution rules; these are recipe settings."
            else "实际结束条件取决于设备／秤连接和执行规则；以上为配方设定。")
    }
    companion object {
        fun from(item: CurveLibraryItem): AiCurveRecipeDiagram {
            val plan = CurveRecipePlan.from(item)
            val preview = plan.preview
            return AiCurveRecipeDiagram(preview.stages.mapIndexed { index, stage ->
                Stage(index + 1, stage.target, stage.unit, plan.stageWaterTenthsMl.getOrNull(index))
            }, preview.renderable, item.customDocument?.temperatureC ?: item.factoryCurve?.temperatureC ?: item.controlProfile?.temperatureC,
                plan.preinfusionSeconds, plan.firstStageDurationSettingSeconds, plan.totalWaterLimitMl,
                item.customDocument?.let { it.totalWaterLimitMl == null } == true, plan.assignedWaterTenthsMl, plan.targetWeightHundredthsGram,
                preview.pressureAxisMax, preview.flowAxisMax)
        }
    }
}

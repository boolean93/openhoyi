package io.openhoyi.mobile

/** Visible changes from exact native snapshots, never a model-authored summary or an execution permit. */
internal object AiCurveRecipeChanges {
    data class Change(val label: String, val before: String, val after: String, val stageOrdinal: Int? = null)
    fun from(before: CustomCurveDocument, after: CustomCurveDocument, english: Boolean): List<Change> = buildList {
        fun words(en: String, zh: String) = if (english) en else zh
        val missing = words("not set", "未设置")
        fun addField(en: String, zh: String, old: String, new: String, ordinal: Int? = null) {
            if (old != new) add(Change(words(en, zh), old, new, ordinal))
        }
        fun name(doc: CustomCurveDocument) = doc.name.map {
            if (Character.isISOControl(it) || Character.getType(it) in setOf(Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt()))
                "\\u%04X".format(it.code) else it.toString()
        }.joinToString("")
        fun mode(doc: CustomCurveDocument) = when (doc.controlMode) {
            CustomCurveDocument.ControlMode.PRESSURE -> words("pressure", "压力")
            CustomCurveDocument.ControlMode.FLOW -> words("flow", "流量")
            CustomCurveDocument.ControlMode.FLOW_RAW -> words("unknown", "未知")
        }
        fun flag(value: Boolean?) = value?.let { if (it) words("on", "开启") else words("off", "关闭") } ?: missing
        fun seconds(value: Int?) = value?.let { "$it s" } ?: missing
        fun waterLimit(doc: CustomCurveDocument) = doc.totalWaterLimitMl?.let { "$it mL" }
            ?: words("stage sum", "分段合计") + " ${CurveDraftNumber.format(doc.assignedWaterTenthsMl, 1)} mL"
        addField("Name", "名称", name(before), name(after))
        addField("Temperature setting", "全程温度设定", "${before.temperatureC} °C", "${after.temperatureC} °C")
        addField("Control mode", "控制模式", mode(before), mode(after))
        addField("Active stages", "启用段数", before.stages.size.toString(), after.stages.size.toString())
        addField("Water ceiling setting", "总水量上限设定", waterLimit(before), waterLimit(after))
        addField("Weight target setting", "目标重量设定", "${CurveDraftNumber.format(before.targetHundredthsGram, 2)} g", "${CurveDraftNumber.format(after.targetHundredthsGram, 2)} g")
        val old = before.configuration; val new = after.configuration
        addField("Pressure logic flag", "压力逻辑开关", flag(old?.pressureLogic), flag(new?.pressureLogic))
        addField("Variable flow logic", "变流量逻辑", flag(old?.variableFlowLogic), flag(new?.variableFlowLogic))
        addField("First-stage flow mode", "首段流量模式", flag(old?.firstSegmentFlowMode), flag(new?.firstSegmentFlowMode))
        addField("Preinfusion setting", "预浸时间设定", seconds(old?.preinfusionSeconds), seconds(new?.preinfusionSeconds))
        addField("First-stage duration setting", "仅首段时间设定", seconds(old?.firstDurationSeconds), seconds(new?.firstDurationSeconds))
        addField("Weight compensation", "重量补偿", old?.compensationHundredthsGram?.let { "${CurveDraftNumber.format(it, 2)} g" } ?: missing,
            new?.compensationHundredthsGram?.let { "${CurveDraftNumber.format(it, 2)} g" } ?: missing)
        fun stage(doc: CustomCurveDocument, index: Int): String {
            val active = index < doc.stages.size
            val value = if (active) doc.stages[index] else doc.configuration?.inactiveStages?.getOrNull(index - doc.stages.size)
            val state = if (active) words("active", "启用") else words("inactive", "停用")
            // Legacy schemas do not store unused slots. Do not invent an original zero-valued stage.
            if (value == null) return state + words(" (no stored parameters)", "（无已存参数）")
            val unit = when {
                doc.controlMode == CustomCurveDocument.ControlMode.FLOW_RAW -> words("unknown unit", "未知单位")
                doc.isFlowStage(index) -> "mL/s"
                else -> "bar"
            }
            return "$state · ${CurveDraftNumber.format(value.target, 1)} $unit · ${CurveDraftNumber.format(value.waterTenthsMl, 1)} mL"
        }
        (0..3).forEach { index -> addField("Stage ${index + 1}", "第 ${index + 1} 段", stage(before, index), stage(after, index), index + 1) }
    }
}

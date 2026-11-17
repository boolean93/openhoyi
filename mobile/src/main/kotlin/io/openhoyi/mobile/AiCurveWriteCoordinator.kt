package io.openhoyi.mobile

import org.json.JSONObject
import io.openhoyi.protocol.CustomCurveStartPolicy
import io.openhoyi.protocol.CustomPressureStartPolicy
import java.util.UUID

/** A proposal is data, never authority. Only native UI calls confirm; not a provider tool. */
internal class AiCurveWriteCoordinator(
    private val store: CustomCurveStore,
    private val resolveFactory: (String) -> CurveLibraryItem? = { null },
    private val permitted: (String) -> Boolean = { true },
) {
    data class Epoch(val session: String, val credential: Long)
    class Proposal internal constructor(
        val token: String,
        val epoch: Epoch,
        val operation: String,
        private val beforeJson: String?,
        private val afterJson: String,
        internal val revision: Long,
        private val factorySourceJson: String? = null,
    ) {
        // Decode copies on every access so no caller can mutate the authorized snapshot's lists.
        val factorySource: AiCurveCard? get() = factorySourceJson?.let { AiCurveCard.decode(JSONObject(it)) }
        val before: CustomCurveDocument? get() = beforeJson?.let(CustomCurveDocument::decode)
        val after: CustomCurveDocument get() = CustomCurveDocument.decode(afterJson)
    }
    private var pending: Proposal? = null
    @Synchronized fun pending(epoch: Epoch): Proposal? = pending?.takeIf { it.epoch == epoch }
    @Synchronized fun cancel() { pending = null }
    @Synchronized fun readDocument(id: String): CustomCurveDocument? {
        if (!AiCurveDetail.validId(id)) return null
        val document = store.aiWriteSnapshot(id).second?.takeIf { it.isEditable } ?: return null
        return CustomCurveDocument.decode(document.encode())
    }

    /** Exact schema: operation + curve (normal document excluding id), and targetId only for replace. */
    @Synchronized fun propose(arguments: String, epoch: Epoch): Proposal {
        check(pending == null) { "A proposal is already awaiting confirmation" }
        require(arguments.toByteArray(Charsets.UTF_8).size <= MAX_ARGUMENT_BYTES)
        val root = CurveDocumentJson.objectFrom(arguments)
        val operation = CurveDocumentJson.string(root, "operation")
        require(operation in setOf("create", "replace", "copy_from_factory"))
        CurveDocumentJson.keys(root, setOf("operation", "curve") + when (operation) { "replace" -> setOf("targetId"); "copy_from_factory" -> setOf("sourceId"); else -> emptySet() })
        val target = if (operation == "replace") CurveDocumentJson.string(root, "targetId") else null
        val (revision, before) = store.aiWriteSnapshot(target)
        if (operation == "replace") {
            require(target?.startsWith("draft-") == true)
            require(before != null && before.id == target && before.isEditable) { "Editable custom curve required" }
        }
        val curve = root.get("curve")
        require(curve is JSONObject && !curve.has("id"))
        val factory = if (operation == "copy_from_factory") {
            val sourceId = CurveDocumentJson.string(root, "sourceId")
            require(AiCurveDetail.validFactoryId(sourceId))
            val item = requireNotNull(resolveFactory(sourceId))
            require(item.id == sourceId && item.factoryCurve != null && item.customDocument == null)
            AiCurveCard.recommendation(item)
        } else null
        val id = target ?: CustomCurveDocument.newId()
        val after = CustomCurveDocument.decode(JSONObject(curve.toString()).put("id", id).toString())
        require(after.isEditable)
        require(listOf(false, true).all { scale -> CustomCurveAdapter.profile(after, scale)?.let {
            if (after.configuration == null) CustomPressureStartPolicy.permits(it.parameters)
            else CustomCurveStartPolicy.permits(it.parameters)
        } == true }) { "Curve cannot be executed safely" }
        require(after.name.none { Character.isISOControl(it) || Character.getType(it) in setOf(Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt()) }) { "Unsafe curve name" }
        val baseline = before?.encode() ?: factory?.let { CustomCurveDocument.fromLibraryItem(it.item, id).encode() }
        return Proposal(UUID.randomUUID().toString(), epoch, operation, baseline, after.encode(), revision, factory?.encode()?.toString())
            .also { pending = it }
    }

    /** One use even on IO failure. Never persisted, replayed, or callable via model tool arguments. */
    @Synchronized fun confirm(token: String, epoch: Epoch): CustomCurveDocument {
        val proposal = pending
        pending = null
        check(proposal != null && proposal.token == token && proposal.epoch == epoch) { "Confirmation expired" }
        val after = proposal.after
        check(proposal.factorySource == null) { "Factory copies must be reviewed in the editor" }
        store.saveAiConfirmed(after, proposal.before, proposal.revision) { proposal.before == null || permitted(after.id) }
        return after
    }
    companion object { const val MAX_ARGUMENT_BYTES = 8192 }
}

/** Complete native before/after recipe, including inactive slots; no model-authored summary. */
internal object AiCurveWritePresentation {
    fun recipe(document: CustomCurveDocument, english: Boolean): String = buildString {
        fun row(en: String, zh: String, value: Any?) { append(if (english) en else zh).append(": ").append(value).append('\n') }
        row("Name", "名称", document.name.map { if (Character.isISOControl(it) || Character.getType(it) in setOf(Character.FORMAT.toInt(), Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt())) "\\u%04X".format(it.code) else it.toString() }.joinToString(""))
        row("ID", "ID", document.id)
        row("Temperature", "温度", "${document.temperatureC} °C")
        row("Weight target (0 = off)", "目标重量（0 = 关闭）", "${CurveDraftNumber.format(document.targetHundredthsGram, 2)} g")
        row("Control mode", "控制模式", if (document.controlMode == CustomCurveDocument.ControlMode.FLOW) { if (english) "Variable flow" else "变流量" } else { if (english) "Variable pressure" else "变压" })
        row("Active stages", "启用段数", document.stages.size)
        row("Water ceiling", "总水量上限", document.totalWaterLimitMl?.let { "$it ml" } ?: if (english) "stage sum (${CurveDraftNumber.format(document.assignedWaterTenthsMl, 1)} ml)" else "按分段合计（${CurveDraftNumber.format(document.assignedWaterTenthsMl, 1)} ml）")
        val c = document.configuration ?: CustomCurveDocument.Configuration()
        row("Pressure logic", "压力逻辑", if (c.variableFlowLogic) { if (c.pressureLogic) "Hs" else "Ls" } else if (english) { if (c.pressureLogic) "Follow" else "Constant pressure" } else { if (c.pressureLogic) "跟随" else "恒压" })
        row("Variable flow logic", "变流量逻辑", if (english) { if (c.variableFlowLogic) "On" else "Off" } else { if (c.variableFlowLogic) "开启" else "关闭" })
        row("First stage flow mode", "首段流量模式", if (english) { if (c.firstSegmentFlowMode) "On" else "Off" } else { if (c.firstSegmentFlowMode) "开启" else "关闭" })
        row("Preinfusion", "预浸时间", "${c.preinfusionSeconds} s")
        row("First stage duration", "首段时间", "${c.firstDurationSeconds} s")
        row("Weight compensation", "重量补偿", "${CurveDraftNumber.format(c.compensationHundredthsGram, 2)} g")
        document.allStages.forEachIndexed { index, stage ->
            val active = index < document.stages.size
            val unit = if (c.variableFlowLogic && (index > 0 || c.firstSegmentFlowMode)) "ml/s" else "bar"
            val value = if (!active && document.configuration == null) {
                if (english) "no stored parameters" else "无已存参数"
            } else "${CurveDraftNumber.format(stage.target, 1)} $unit; ${CurveDraftNumber.format(stage.waterTenthsMl, 1)} ml"
            row("Stage ${index + 1} (${if (active) "active" else "inactive"})", "第 ${index + 1} 段（${if (active) "启用" else "停用"}）", value)
        }
    }
}

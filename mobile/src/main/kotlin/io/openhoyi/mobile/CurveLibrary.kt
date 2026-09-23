package io.openhoyi.mobile

import java.util.Locale

/** Display selection and BLE eligibility stay separate: factory metadata never becomes a command. */
data class CurveLibraryItem(
    val id: String,
    val name: String,
    val category: String,
    val details: String,
    val controlProfile: CurveProfile?,
    val factoryCurve: FactoryCurve? = null,
)

class CurveLibrary(factory: List<FactoryCurve>, private val proof: FactoryWireProof? = null) {
    val items: List<CurveLibraryItem> = java.util.Collections.unmodifiableList(
        CurveCatalog.profiles.map { profile ->
            CurveLibraryItem(profile.id, profile.name, "已采集验证",
                "${profile.endMode}\n温度 ${profile.temperatureC} °C · 最多 ${profile.maximumWaterMl} ml · ${profile.parameters.segmentCount} 段\n目标重量 ${if (profile.targetHundredthsGram == 0) "不使用" else "${format(profile.targetHundredthsGram / 100.0)} g"}\n启动报文与采集样本逐字节一致。",
                profile)
        } + factory.map { curve ->
            CurveLibraryItem(curve.id, curve.name, categoryName(curve.category),
                "旧版工厂曲线 v3 · ${if (proof == null) "报文校验不可用，仅供浏览" else "启动报文对照通过，待实机验收"}\n温度 ${curve.temperatureC} °C · 水量 ${curve.flowMl} ml · ${curve.segmentCount} 段\n目标重量 ${if (curve.weightTenthsGram == 0) "不使用" else "${format(curve.weightTenthsGram / 10.0)} g"}\n分段目标 ${curve.targets.take(curve.segmentCount).joinToString(" / ")}\n分段水量 ${curve.segmentFlowMl.take(curve.segmentCount).joinToString(" / ")} ml\n\n${curve.tips}",
                null, curve)
        })
    fun find(id: String): CurveLibraryItem? = items.firstOrNull { it.id == id }
    fun canStart(item: CurveLibraryItem): Boolean = item.controlProfile?.let(CurveCatalog::validated) == true ||
        (item.factoryCurve != null && proof != null)
    fun resolve(id: String, scaleConnected: Boolean): CurveProfile? {
        val item = find(id) ?: return null
        item.controlProfile?.let { return it.takeIf(CurveCatalog::validated) }
        val curve = item.factoryCurve ?: return null
        return FactoryCurveAdapter.profile(curve, scaleConnected).takeIf { proof?.validated(it) == true }
    }
    fun validated(profile: CurveProfile): Boolean = CurveCatalog.validated(profile) || proof?.validated(profile) == true
    companion object {
        private fun format(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
        fun categoryName(raw: String): String = when (raw) {
            "dark" -> "深烘"; "medium" -> "中烘"; "light" -> "浅烘"; "super" -> "超萃"; else -> "其它"
        }
    }
}

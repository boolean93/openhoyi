package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.PreheatGate
import io.openhoyi.protocol.Settings

/** Maps shared studio decisions to product text and curve identifiers. */
object StudioStartGate {
    fun block(settings: Settings?, correctedHundredthsC: Int?, profile: CurveProfile,
        preparation: BrewPreparation): String? = when (PreheatGate.startBlock(
            settings, correctedHundredthsC, profile.id, profile.temperatureC, preparation)) {
        PreheatGate.StartBlock.SETTINGS_MISSING -> "尚未收到机器运行模式"
        PreheatGate.StartBlock.MODE_CHANGED -> "运行模式已变化，请先取消预热"
        PreheatGate.StartBlock.PREPARATION_MISMATCH -> "预热尚未就绪或曲线已变化，请先取消预热"
        PreheatGate.StartBlock.TEMPERATURE_NOT_READY -> "工作室模式温度未达到曲线目标，请先预热"
        null -> null
    }
}

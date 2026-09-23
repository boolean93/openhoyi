package io.openhoyi.mobile

import io.openhoyi.protocol.Settings

/** Final studio decision is repeated in the service at the moment a start is requested. */
object StudioStartGate {
    fun block(settings: Settings?, correctedHundredthsC: Int?, profile: CurveProfile,
        preparation: BrewPreparation): String? {
        if (settings == null) return "尚未收到机器运行模式"
        val studio = settings.flags and 0x04 != 0
        if (preparation.active) {
            if (!studio) return "运行模式已变化，请先取消预热"
            if (!preparation.matches(profile.id, profile.temperatureC))
                return "预热尚未就绪或曲线已变化，请先取消预热"
        }
        if (studio && (correctedHundredthsC == null ||
                !BrewPreparation.isAtTarget(correctedHundredthsC, profile.temperatureC)))
            return "工作室模式温度未达到曲线目标，请先预热"
        return null
    }
}

package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.StartParameters

/** Captured wire profiles only. Names describe the capture, not an unverified legacy catalog title. */
data class CurveProfile(
    val id: String,
    val name: String,
    val endMode: String,
    val targetHundredthsGram: Int,
    val parameters: StartParameters,
    val compensationHundredthsGram: Int = 0,
    val scaleMode: Boolean? = null,
) {
    val temperatureC: Int get() = parameters.temperatureC
    val maximumWaterMl: Int get() = parameters.maximumWaterMl
}

object CurveCatalog {
    val profiles: List<CurveProfile> = listOf(
        CurveProfile("capture-1", "采集曲线 1", "记录中由用户手动停止", 2700,
            StartParameters(false, false, 2, 7, 91, 108, false, 0, 90, 65, 0, 0, 350, 22, 170, 0, 0)),
        CurveProfile("capture-2", "采集曲线 2", "记录中由咖啡机按流量结束", 0,
            StartParameters(true, true, 3, 7, 92, 70, false, 0, 20, 38, 20, 0, 160, 5, 400, 140, 0)),
        CurveProfile("capture-3", "采集曲线 3", "记录中由秤达到 34.00 g 后停止", 3400,
            StartParameters(true, true, 3, 7, 92, 136, false, 0, 20, 35, 18, 0, 150, 5, 400, 130, 0)),
    )
    fun find(id: String): CurveProfile? = profiles.firstOrNull { it.id == id }
    private val capturedFrames = mapOf(
        "capture-1" to "02175B006C005A410000015E1600AA00000000DA",
        "capture-2" to "02DF5C0046001426140000A0050190008C000059",
        "capture-3" to "02DF5C00880014231200009605019000820000AC",
    )
    fun validated(profile: CurveProfile): Boolean =
        profile == find(profile.id) &&
            runCatching { CoffeeCommands.start(profile.parameters).frame.hex() }.getOrNull() == capturedFrames[profile.id]
}

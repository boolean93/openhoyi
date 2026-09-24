package io.openhoyi.mobile

import io.openhoyi.protocol.CoffeeCommands
import io.openhoyi.protocol.StartParameters
import org.json.JSONObject

/** Strict candidate conversion for offline comparison only; CurveLibrary never calls this adapter. */
object LegacyCurveAdapter {
    fun profile(curve: LegacyCurve, scaleConnected: Boolean, slot: Int = 7): CurveProfile? {
        if (slot !in 1..5 && slot != 7) return null
        val row = runCatching { JSONObject(curve.rawJson) }.getOrNull() ?: return null
        fun integer(key: String, minimum: Int, maximum: Int): Int? {
            val raw = row.opt(key)
            val value = when (raw) {
                is Number -> raw.toDouble().takeIf { it.isFinite() && it % 1.0 == 0.0 &&
                    it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }?.toInt()
                is String -> raw.takeIf { it.matches(Regex("-?[0-9]+")) }?.toIntOrNull()
                else -> null
            }
            return value?.takeIf { it in minimum..maximum }
        }
        val flow = integer("flow", 1, 65535) ?: return null
        val weight = integer("weight", 0, 60000) ?: return null
        val compensation = integer("wdelta", -1000, 1000) ?: return null
        val time = integer("time", 0, 127) ?: return null
        val temperature = integer("temp", 0, 255) ?: return null
        val segments = integer("seg", 1, 4) ?: return null
        val firstDuration = integer("time1", 0, 255) ?: return null
        val pressure = row.opt("press") as? Boolean ?: return null
        val variableFlow = row.opt("chart") as? Boolean ?: return null
        val firstFlowMode = when (val value = row.opt("seg1FlowMode")) {
            null -> false
            is Boolean -> value
            else -> return null
        }
        val targets = (1..4).map { integer("press$it", 0, 255) ?: return null }
        val flows = (1..4).map { integer("flow$it", 0, 6553) ?: return null }
        val maximumWater = if (scaleConnected && weight > 0) maxOf(flow, (weight * 2 + 4) / 5)
            else flow
        val parameters = StartParameters(pressure, variableFlow, segments, slot, temperature,
            maximumWater, variableFlow && firstFlowMode, time,
            targets[0], targets[1], targets[2], targets[3], flows[0] * 10, firstDuration,
            flows[1] * 10, flows[2] * 10, flows[3] * 10)
        if (runCatching { CoffeeCommands.start(parameters) }.isFailure) return null
        return CurveProfile("legacy-${curve.index}", curve.name,
            if (scaleConnected && weight > 0) "达到目标重量后停止" else "由咖啡机按水量结束",
            if (scaleConnected) weight * 10 else 0, parameters,
            if (scaleConnected) compensation * 10 else 0, scaleConnected)
    }
}

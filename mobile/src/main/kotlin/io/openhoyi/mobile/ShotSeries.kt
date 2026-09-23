package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry

/** In-memory projection of observed notifications; timestamps are monotonic milliseconds. */
data class ShotPoint(
    val elapsedMs: Long,
    val pressureTenthsBar: Int,
    val machineFlowTenthsMlPerSecond: Int,
    val waterTenthsMl: Int,
    val temperatureHundredthsC: Int,
    val weightHundredthsGram: Int?,
)

class ShotSeries {
    private var id: String? = null
    private var startedAtMs = 0L
    private var minimumGapMs = 0L
    private var lastCheckpointAtMs: Long? = null
    private val recorded = mutableListOf<ShotPoint>()
    val points: List<ShotPoint> get() = recorded.toList()

    fun begin(shotId: String, atElapsedMs: Long) {
        require(shotId.isNotBlank())
        id = shotId
        startedAtMs = atElapsedMs
        minimumGapMs = 0
        lastCheckpointAtMs = null
        recorded.clear()
    }

    /** The first observed point and then at most one snapshot per five seconds. */
    fun checkpoint(atElapsedMs: Long, force: Boolean = false): Pair<String, List<ShotPoint>>? {
        val shotId = id ?: return null
        if (recorded.isEmpty()) return null
        if (!force && lastCheckpointAtMs?.let { atElapsedMs - it < 5_000 } == true) return null
        lastCheckpointAtMs = atElapsedMs
        return shotId to recorded.toList()
    }

    fun machine(frame: ExtractionTelemetry, atElapsedMs: Long, weightHundredthsGram: Int?, weightAtElapsedMs: Long?) {
        if (id == null || atElapsedMs < startedAtMs) return
        val elapsed = atElapsedMs - startedAtMs
        val last = recorded.lastOrNull()
        if (last != null && elapsed <= last.elapsedMs) return
        val freshWeight = weightHundredthsGram?.takeIf {
            weightAtElapsedMs != null && weightAtElapsedMs <= atElapsedMs && atElapsedMs - weightAtElapsedMs <= 1500
        }
        val point = ShotPoint(elapsed, frame.pressureTenthsBar, frame.flowTenthsMlPerSecond,
            frame.totalWaterTenthsMl, frame.brewTemperatureHundredthsC, freshWeight)
        if (last != null && elapsed - last.elapsedMs < minimumGapMs) {
            recorded[recorded.lastIndex] = point
            return
        }
        if (recorded.size >= MAX_POINTS) {
            val reduced = recorded.filterIndexed { index, _ -> index % 2 == 0 }
            recorded.clear()
            recorded.addAll(reduced)
            minimumGapMs = if (minimumGapMs == 0L) 100 else minimumGapMs * 2
        }
        recorded.add(point)
    }

    fun finish(): Pair<String, List<ShotPoint>>? {
        val shotId = id ?: return null
        val result = shotId to recorded.toList()
        id = null
        return result
    }

    companion object { const val MAX_POINTS = 2_000 }
}

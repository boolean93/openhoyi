package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject

/** Read-only, exact journal membership before opening any sample file. No recovery or writers. */
internal object AiExtractionSamples {
    const val MAX_OUTPUT_CHARS = 32_000
    fun validId(id: String) = id.matches(Regex("[A-Za-z0-9-]{1,64}"))

    fun read(shotId: String, offset: Int, limit: Int,
        observations: () -> List<BrewJournal.Observation>, load: (String) -> List<ShotPoint>): JSONObject {
        if (!validId(shotId) || offset !in 0..ShotSeries.MAX_POINTS || limit !in 1..100)
            return status("invalid_arguments")
        return try {
            if (observations().none { it.id == shotId }) return status("not_found")
            val points = load(shotId)
            if (points.isEmpty()) status("samples_unavailable") else page(shotId, points, offset, limit)
        } catch (_: Exception) { status("unavailable") }
    }

    private fun status(value: String) = JSONObject().put("status", value).put("dataOnly", true)

    fun page(shotId: String, points: List<ShotPoint>, offset: Int, limit: Int): JSONObject {
        val selected = points.drop(offset).take(limit).map { point ->
            JSONObject().put("elapsedMs", point.elapsedMs)
                .put("pressureBar", point.pressureTenthsBar / 10.0)
                .put("machineFlowMlPerSecond", point.machineFlowTenthsMlPerSecond / 10.0)
                .put("waterMl", point.waterTenthsMl / 10.0)
                .put("temperatureC", point.temperatureHundredthsC / 100.0)
                .put("scaleWeightGrams", point.weightHundredthsGram?.div(100.0) ?: JSONObject.NULL)
                .put("estimatedScaleFlowGramsPerSecond", point.scaleFlowHundredths?.div(100.0) ?: JSONObject.NULL)
                .put("brewing", point.brewing ?: JSONObject.NULL)
                .put("statusBits", point.statusBits ?: JSONObject.NULL)
                .put("beverageWeightGrams", point.beverageHundredthsGram?.div(100.0) ?: JSONObject.NULL)
                .put("beverageWeightSource", point.beverageSource?.name ?: JSONObject.NULL)
                .put("beverageSegmentId", point.beverageSegmentId ?: JSONObject.NULL)
        }.toMutableList()
        fun result(): JSONObject {
            val end = offset + selected.size
            val more = end < points.size
            return status("available").put("shotId", shotId)
                .put("valueKind", "saved_historical_observations_not_current_status")
                .put("timeBasis", "elapsedMs is monotonic offset from sample-series start, not machine extraction timer")
                .put("sampling", "Stored observations only; recorder may thin at 2000 points. No interpolation or reconstruction.")
                .put("scaleEvidence", "scaleWeightGrams is raw scale weight, not necessarily this cup net weight. beverageWeightGrams carries source and segment; do not connect across beverageSegmentId.")
                .put("items", JSONArray(selected)).put("offset", offset).put("total", points.size)
                .put("truncated", more).put("nextOffset", if (more && selected.isNotEmpty()) end else JSONObject.NULL)
        }
        var result = result()
        while (result.toString().length > MAX_OUTPUT_CHARS && selected.isNotEmpty()) {
            selected.removeAt(selected.lastIndex)
            result = result()
        }
        return result
    }
}

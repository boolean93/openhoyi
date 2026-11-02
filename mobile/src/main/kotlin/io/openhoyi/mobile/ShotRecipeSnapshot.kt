package io.openhoyi.mobile

/** Requested recipe at this cup's start, not a machine readback or a later library version. */
internal data class ShotRecipeSnapshot(
    val traceId: String, val name: String, val temperatureC: Int, val targetHundredthsGram: Int,
) {
    fun forTrace(currentTraceId: String?): ShotRecipeSnapshot? = takeIf { currentTraceId == traceId }
    companion object {
        fun capture(traceId: String, profile: CurveProfile): ShotRecipeSnapshot {
            require(traceId.isNotBlank())
            return ShotRecipeSnapshot(traceId, profile.name, profile.temperatureC, profile.targetHundredthsGram)
        }
    }
}

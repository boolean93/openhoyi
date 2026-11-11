package io.openhoyi.mobile

/** Requested recipe at this cup's start, not a machine readback or a later library version. */
internal data class ShotRecipeSnapshot(
    val traceId: String, val name: String, val temperatureC: Int, val targetHundredthsGram: Int,
    val draftFingerprint: String? = null,
) {
    fun forTrace(currentTraceId: String?): ShotRecipeSnapshot? = takeIf { currentTraceId == traceId }
    companion object {
        fun capture(traceId: String, profile: CurveProfile,
                    draftDocument: CustomCurveDocument? = null): ShotRecipeSnapshot {
            require(traceId.isNotBlank())
            val fingerprint = draftDocument?.takeIf { document ->
                document.id == profile.id && CustomCurveAdapter.profile(document,
                    profile.scaleMode == true, profile.parameters.slot) == profile
            }?.let(CustomCurveFingerprint::of)
            return ShotRecipeSnapshot(traceId, profile.name, profile.temperatureC,
                profile.targetHundredthsGram, fingerprint)
        }
    }
}

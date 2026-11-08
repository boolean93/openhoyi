package io.openhoyi.mobile

internal sealed interface HomeLastCurve {
    data object Empty : HomeLastCurve
    data object Manual : HomeLastCurve
    data object DataUnavailable : HomeLastCurve
    enum class Reason { MISSING, NOT_STARTABLE, MUTABLE_DRAFT }
    data class Unavailable(val reason: Reason) : HomeLastCurve
    data class Ready(val item: CurveLibraryItem, val preview: CurveStageParameters.Preview?) : HomeLastCurve

    companion object {
        fun from(latest: CurveUsage?, manualStartedAtMs: Long?, items: List<CurveLibraryItem>,
                 recentObserved: CurveUsage? = null,
                 canStart: (CurveLibraryItem) -> Boolean): HomeLastCurve {
            val newestCurveAt = maxOf(latest?.startedAtMs ?: -1, recentObserved?.startedAtMs ?: -1)
            if (manualStartedAtMs != null && manualStartedAtMs >= newestCurveAt)
                return Manual
            if (recentObserved != null && (latest == null ||
                    recentObserved.startedAtMs > latest.startedAtMs ||
                    (recentObserved.startedAtMs == latest.startedAtMs && recentObserved.shotId != latest.shotId)))
                return DataUnavailable
            val use = latest ?: return Empty
            val item = items.firstOrNull { it.id == use.curveId } ?: return Unavailable(Reason.MISSING)
            if (use.curveId.startsWith("draft-")) {
                val document = item.customDocument ?: return Unavailable(Reason.MUTABLE_DRAFT)
                val current = runCatching { CustomCurveFingerprint.of(document) }.getOrNull()
                if (use.fingerprint == null || current != use.fingerprint)
                    return Unavailable(Reason.MUTABLE_DRAFT)
            }
            if (!canStart(item)) return Unavailable(Reason.NOT_STARTABLE)
            return Ready(item, CurveStageParameters.preview(item).takeIf { it.renderable })
        }
    }
}

package io.openhoyi.mobile

enum class BeverageWeightSource { CONFIRMED, ESTIMATED }
internal data class BeverageWeightSample(val hundredthsGram: Int, val source: BeverageWeightSource, val segmentId: Int = 0)

/** Display/recording only. Never sends tare, stops a pump, or grants an extraction permit. */
internal class BeverageWeightEstimator {
    private var segmentId = 0
    private var epoch: Long? = null
    private var baseline: Int? = null
    private var lastSource: BeverageWeightSource? = null

    fun observe(elapsedMs: Long, freshRaw: Int?, scaleEpoch: Long, confirmedNet: Int?): BeverageWeightSample? {
        if (epoch != null && epoch != scaleEpoch) {
            epoch = scaleEpoch; baseline = null; lastSource = null; segmentId++
            // A reconnect/tare changes coordinates. Keep an explicit gap before a new estimate.
            return null
        }
        epoch = scaleEpoch
        if (freshRaw == null) return null
        if (confirmedNet != null) {
            if (lastSource == BeverageWeightSource.ESTIMATED) {
                lastSource = BeverageWeightSource.CONFIRMED; baseline = null; segmentId++
                return null
            }
            lastSource = BeverageWeightSource.CONFIRMED
            return BeverageWeightSample(confirmedNet.coerceAtLeast(0), BeverageWeightSource.CONFIRMED, segmentId)
        }
        if (lastSource == BeverageWeightSource.CONFIRMED) {
            lastSource = null; baseline = null; segmentId++
            return null // Never silently reinterpret lost proof as the same continuous segment.
        }
        // The existing no-target automatic tare happens around 1.5 s. Like the official App,
        // begin the fallback baseline after 2 s; this is an estimate, NOT a tare acknowledgement.
        if (elapsedMs < 2_000) return null
        val old = baseline
        if (old != null && old > 80 && freshRaw in -50..50) {
            baseline = freshRaw; lastSource = BeverageWeightSource.ESTIMATED; segmentId++
            return null // Late zero/tare: break the line rather than connect different coordinates.
        }
        if (old == null) baseline = freshRaw
        lastSource = BeverageWeightSource.ESTIMATED
        val delta = (freshRaw.toLong() - requireNotNull(baseline)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        return BeverageWeightSample(delta, BeverageWeightSource.ESTIMATED, segmentId)
    }
}

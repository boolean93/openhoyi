package io.openhoyi.session

/** Business eligibility, distinct from decoding a well-formed scale notification. */
object ScaleReadingPolicy {
    fun supportedWeight(hundredthsGram: Int): Boolean = hundredthsGram in -50_000..600_000

    fun isFresh(hundredthsGram: Int, atMs: Long?, nowMs: Long, maxAgeMs: Long = 1500): Boolean =
        supportedWeight(hundredthsGram) && TelemetryFreshness.isFresh(atMs, nowMs, maxAgeMs)
}

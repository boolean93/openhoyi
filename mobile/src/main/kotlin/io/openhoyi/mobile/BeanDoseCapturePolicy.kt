package io.openhoyi.mobile

import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.session.ScaleReadingPolicy

/** Read-only bean capture; never grants extraction or BLE control. */
internal class BeanDoseCapturePolicy {
    private var source: Any? = null
    private var lastNow: Long? = null
    private val samples = ArrayDeque<ScaleObservation>()
    fun reset() { source = null; lastNow = null; samples.clear() }
    fun update(source: Any?, sample: ScaleObservation?, nowMs: Long): Int? {
        val backward = lastNow?.let { nowMs < it } == true
        if (backward || source == null || sample == null || !sample.capabilities.weight ||
            sample.evidence != ScaleEvidence.VERIFIED_TRANSPORT || sample.hundredthsGram <= 0 ||
            !ScaleReadingPolicy.isFresh(sample.hundredthsGram, sample.receivedAtMs, nowMs, 750)) {
            reset(); return null
        }
        if (source != this.source) reset()
        this.source = source; lastNow = nowMs
        val previous = samples.lastOrNull()
        if (previous != null && (sample.receivedAtMs < previous.receivedAtMs ||
                (sample.receivedAtMs == previous.receivedAtMs && sample.hundredthsGram != previous.hundredthsGram))) {
            reset(); return null
        }
        if (previous == null || sample.receivedAtMs > previous.receivedAtMs) {
            if (previous != null && sample.receivedAtMs - previous.receivedAtMs > 750) samples.clear()
            samples.addLast(sample)
            // Keep the immediately preceding sample at the one-second boundary as an anchor.
            while (samples.size > 1 && samples.elementAt(1).receivedAtMs <= sample.receivedAtMs - 1000) samples.removeFirst()
            if (samples.maxOf { it.hundredthsGram } - samples.minOf { it.hundredthsGram } > 10) {
                samples.clear(); samples.addLast(sample)
            }
        }
        return sample.hundredthsGram.takeIf {
            samples.size >= 5 && sample.receivedAtMs - samples.first().receivedAtMs >= 1000
        }
    }
}

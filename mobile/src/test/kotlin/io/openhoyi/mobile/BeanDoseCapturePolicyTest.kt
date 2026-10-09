package io.openhoyi.mobile

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class BeanDoseCapturePolicyTest {
    private fun sample(at: Long, weight: Int = 1800, evidence: ScaleEvidence = ScaleEvidence.VERIFIED_TRANSPORT) =
        ScaleObservation(weight, at, ScaleCapabilities(), evidence, raw = ByteFrame(byteArrayOf()))
    private fun stable(policy: BeanDoseCapturePolicy, source: Any = "scale", start: Long = 1000): Int? {
        for (at in start until start + 1000 step 250) assertNull(policy.update(source, sample(at), at))
        return policy.update(source, sample(start + 1000), start + 1000)
    }
    @Test fun distinctReadingsMustSpanOneSecondWithinPointOneGram() {
        val policy = BeanDoseCapturePolicy()
        for (i in 0..3) assertNull(policy.update("scale", sample(1000L + i * 250, 1800 + i * 2), 1000L + i * 250))
        assertEquals(1810, policy.update("scale", sample(2000, 1810), 2000))
    }
    @Test fun repaintingOneNotificationCannotMakeItStable() {
        val policy = BeanDoseCapturePolicy()
        for (at in 1000L..2500L step 250) assertNull(policy.update("scale", sample(1000), at))
    }
    @Test fun jitterRemovalAndTareRequireANewWindow() {
        val policy = BeanDoseCapturePolicy()
        assertEquals(1800, stable(policy))
        assertNull(policy.update("scale", sample(2250, 1820), 2250))
        assertNull(policy.update("scale", sample(2500, 0), 2500))
        assertEquals(1800, stable(policy, start = 3000))
        policy.reset()
        assertNull(policy.update("scale", sample(4250), 4250))
    }
    @Test fun ownerOrConnectionChangeAndDisconnectCannotReuseStability() {
        val policy = BeanDoseCapturePolicy()
        assertEquals(1800, stable(policy, "owner:1"))
        assertNull(policy.update("owner:2", sample(2250), 2250))
        assertNull(policy.update(null, null, 2500))
        assertEquals(1800, stable(policy, "owner:2", 3000))
    }
    @Test fun staleFutureBackwardOfflineAndLongGapResetTheWindow() {
        for (invalid in listOf<(BeanDoseCapturePolicy) -> Int?>(
            { it.update("scale", sample(2000), 2751) },
            { it.update("scale", sample(2300), 2250) },
            { it.update("scale", sample(1500), 1500) },
            { it.update("scale", sample(2250, evidence = ScaleEvidence.OFFLINE_CANDIDATE), 2250) },
            { it.update("scale", sample(3000), 3000) }
        )) {
            val policy = BeanDoseCapturePolicy()
            assertEquals(1800, stable(policy))
            assertNull(invalid(policy))
            assertNull(policy.update("scale", sample(3250), 3250))
        }
    }
    @Test fun clickCanReuseSameFreshSampleButNotChangeSameTimestampWeight() {
        val policy = BeanDoseCapturePolicy()
        assertEquals(1800, stable(policy))
        assertEquals(1800, policy.update("scale", sample(2000), 2100))
        assertNull(policy.update("scale", sample(2000, 1820), 2100))
    }
    @Test fun briefJitterBetweenScreenRefreshesInvalidatesCapture() {
        val policy = BeanDoseCapturePolicy()
        assertEquals(1800, stable(policy))
        assertNull(policy.update("scale", sample(2050, 1900), 2050))
        assertNull(policy.update("scale", sample(2100, 1800), 2100))
        assertNull(policy.update("scale", sample(2250, 1800), 2250))
    }
}

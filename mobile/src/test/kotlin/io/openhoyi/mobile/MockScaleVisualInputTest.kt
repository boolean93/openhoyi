package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ScaleCapabilities
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.session.DeviceState
import org.junit.Assert.*
import org.junit.Test

class MockScaleVisualInputTest {
    private data class OwnerIdentity(val value: String)
    private class Scope(val mock: Boolean = true) {
        val owner: Any = OwnerIdentity("scope")
        var main = true
        var clockOwner: Any? = owner
        var hubAbsent = true
        var now = 1000L
        val states = mutableListOf<DeviceState>()
        val observations = mutableListOf<ScaleObservation>()
        val trend = ScaleTrendWindow()
        var state = DeviceState.DISCONNECTED
        var clears = 0
        val input = MockScaleVisualInput(mock, { main }, { clockOwner === it }, { hubAbsent }, { now },
            { next -> state = next; states += next; trend.observe(null, next, now) },
            { sample -> observations += sample; trend.observe(sample, state, now) },
            { clears++; trend.clear() })
        fun sample(at: Long = now, weight: Int = 1800, capabilities: ScaleCapabilities = ScaleCapabilities(),
            evidence: ScaleEvidence = ScaleEvidence.LIVE_READ_ONLY, battery: Int? = null) =
            ScaleObservation(weight, at, capabilities, evidence, batteryPercent = battery, raw = ByteFrame(byteArrayOf(1, 2)))
    }
    @Test fun realBuildCannotInstallOfferOrClear() {
        val scope = Scope(mock = false)
        assertThrows(IllegalStateException::class.java) { scope.input.install(scope.owner) }
        assertThrows(IllegalStateException::class.java) { scope.input.offer(scope.owner, DeviceState.READY, scope.sample()) }
        assertThrows(IllegalStateException::class.java) { scope.input.clear(scope.owner) }
        assertTrue(scope.states.isEmpty() && scope.observations.isEmpty())
    }
    @Test fun mutationsRequireMainThreadOwnedClockAndNoDeviceHub() {
        val scope = Scope()
        scope.main = false
        assertThrows(IllegalStateException::class.java) { scope.input.install(scope.owner) }
        scope.main = true; scope.clockOwner = Any()
        assertThrows(IllegalStateException::class.java) { scope.input.install(scope.owner) }
        scope.clockOwner = scope.owner; scope.hubAbsent = false
        assertThrows(IllegalStateException::class.java) { scope.input.install(scope.owner) }
        scope.hubAbsent = true; scope.input.install(scope.owner)
        scope.main = false
        assertThrows(IllegalStateException::class.java) { scope.input.offer(scope.owner, DeviceState.READY, scope.sample()) }
        assertThrows(IllegalStateException::class.java) { scope.input.clear(scope.owner) }
        scope.main = true; scope.clockOwner = null
        assertThrows(IllegalStateException::class.java) { scope.input.offer(scope.owner, DeviceState.READY, scope.sample()) }
        assertThrows(IllegalStateException::class.java) { scope.input.clear(scope.owner) }
        scope.clockOwner = scope.owner; scope.hubAbsent = false
        assertThrows(IllegalStateException::class.java) { scope.input.offer(scope.owner, DeviceState.READY, scope.sample()) }
        assertThrows(IllegalStateException::class.java) { scope.input.clear(scope.owner) }
    }
    @Test fun installIsExclusiveAndAllMutationsUseIdentityNotEquality() {
        val scope = Scope()
        assertThrows(IllegalStateException::class.java) { scope.input.offer(scope.owner, DeviceState.READY, scope.sample()) }
        assertThrows(IllegalStateException::class.java) { scope.input.clear(scope.owner) }
        scope.input.install(scope.owner)
        assertThrows(IllegalStateException::class.java) { scope.input.install(scope.owner) }
        val other = OwnerIdentity("scope"); assertEquals(scope.owner, other); assertNotSame(scope.owner, other)
        scope.clockOwner = other
        assertThrows(IllegalStateException::class.java) { scope.input.offer(other, DeviceState.READY, scope.sample()) }
        assertThrows(IllegalStateException::class.java) { scope.input.clear(other) }
        scope.clockOwner = scope.owner; scope.input.clear(scope.owner)
        assertNull(scope.input.current())
        assertEquals(1, scope.clears)
        scope.input.install(scope.owner)
    }
    @Test fun controlEvidenceAndWriteCapabilitiesNeverEnterTheSnapshot() {
        val scope = Scope(); scope.input.install(scope.owner)
        val illegal = listOf(scope.sample(evidence = ScaleEvidence.VERIFIED_TRANSPORT),
            scope.sample(capabilities = ScaleCapabilities(tare = true)),
            scope.sample(capabilities = ScaleCapabilities(validatedWeightControl = true)))
        illegal.forEach { sample -> assertThrows(IllegalArgumentException::class.java) { scope.input.offer(scope.owner, DeviceState.READY, sample) } }
        assertTrue(scope.observations.isEmpty() && scope.states.isEmpty())
    }
    @Test fun invalidStateTimeWeightAndBatteryAreRejectedBeforeNotification() {
        val scope = Scope(); scope.input.install(scope.owner)
        assertThrows(IllegalArgumentException::class.java) { scope.input.offer(scope.owner, DeviceState.DISCONNECTED, scope.sample()) }
        listOf(scope.sample(at = -1), scope.sample(at = 1001), scope.sample(weight = Int.MAX_VALUE),
            scope.sample(battery = 82), scope.sample(capabilities = ScaleCapabilities(battery = true), battery = -1),
            scope.sample(capabilities = ScaleCapabilities(battery = true), battery = 101)).forEach { sample ->
            assertThrows(IllegalArgumentException::class.java) { scope.input.offer(scope.owner, DeviceState.READY, sample) }
        }
        assertTrue(scope.observations.isEmpty() && scope.states.isEmpty())
    }
    @Test fun copiedInputAndReturnedRawBytesDoNotAliasTheStoredSnapshot() {
        val scope = Scope(); scope.input.install(scope.owner)
        val original = scope.sample()
        scope.input.offer(scope.owner, DeviceState.READY, original)
        val snapshot = requireNotNull(scope.input.current())
        val stored = requireNotNull(snapshot.observation)
        assertEquals(original, stored)
        assertNotSame(original, stored); assertNotSame(original.raw, stored.raw)
        stored.raw.toByteArray()[0] = 99
        assertArrayEquals(byteArrayOf(1, 2), scope.input.current()!!.observation!!.raw.toByteArray())
        assertFalse(stored.eligibleForControl)
    }
    @Test fun onlyExplicitOffersNotifyAndDuplicateTimestampDoesNotAddTrendPoints() {
        val scope = Scope(); scope.input.install(scope.owner)
        scope.input.offer(scope.owner, DeviceState.READY, scope.sample(weight = -125))
        repeat(4) { scope.now += 250; assertEquals(1000L, scope.input.current()!!.observation!!.receivedAtMs) }
        assertEquals(1, scope.observations.size)
        scope.input.offer(scope.owner, DeviceState.READY, scope.sample(at = 1000))
        assertEquals(2, scope.observations.size)
        assertEquals(listOf(-125), scope.trend.points(scope.now).map { it.hundredthsGram })
    }
    @Test fun disconnectedAndEvidenceChangesUseTheOriginalTrendSegmentationAndExpiry() {
        val scope = Scope(); scope.input.install(scope.owner)
        scope.input.offer(scope.owner, DeviceState.READY, scope.sample())
        scope.now = 1100; scope.input.offer(scope.owner, DeviceState.DISCONNECTED, null)
        scope.now = 1200; scope.input.offer(scope.owner, DeviceState.READY, scope.sample())
        scope.now = 1300; scope.input.offer(scope.owner, DeviceState.READY, scope.sample(evidence = ScaleEvidence.OFFLINE_CANDIDATE))
        assertEquals(3, scope.trend.points(scope.now).map { it.segment }.distinct().size)
        assertEquals(listOf(DeviceState.READY, DeviceState.DISCONNECTED, DeviceState.READY), scope.states)
        scope.now = 11301
        assertTrue(scope.trend.points(scope.now).isEmpty())
        assertEquals(1300L, scope.input.current()!!.observation!!.receivedAtMs)
        scope.input.offer(scope.owner, DeviceState.READY, null)
        assertNull(scope.input.current()!!.observation)
        scope.input.clear(scope.owner)
        assertTrue(scope.trend.points(scope.now).isEmpty())
    }
}

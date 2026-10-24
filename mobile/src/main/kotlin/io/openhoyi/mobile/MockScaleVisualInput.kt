package io.openhoyi.mobile

import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ScaleEvidence
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ScaleReadingPolicy

/** Scoped synthetic read-only observations. Only explicit offers deliver notifications, never reads/ticks. */
internal class MockScaleVisualInput(
    private val mockMode: Boolean,
    private val isMainThread: () -> Boolean,
    private val clockOwned: (Any) -> Boolean,
    private val hubAbsent: () -> Boolean,
    private val now: () -> Long,
    private val onState: (DeviceState) -> Unit,
    private val onObservation: (ScaleObservation) -> Unit,
    private val onClear: () -> Unit,
) {
    data class Snapshot(val state: DeviceState, val observation: ScaleObservation?)
    private var installedOwner: Any? = null
    val isInstalled: Boolean get() = installedOwner != null
    private var snapshot: Snapshot? = null
    private fun requireAccess(owner: Any) {
        check(mockMode) { "Scale visual input is unavailable in a real build" }
        check(isMainThread()) { "Scale visual input requires the main thread" }
        check(clockOwned(owner)) { "Scale visual input requires the same clock owner" }
        check(hubAbsent()) { "Scale visual input cannot coexist with a device hub" }
    }
    private fun requireOwner(owner: Any) {
        requireAccess(owner)
        check(installedOwner === owner) { "Scale visual input owner identity mismatch or not installed" }
    }
    private fun ScaleObservation.detached() = copy(capabilities = capabilities.copy(), raw = ByteFrame(raw.toByteArray()))
    fun install(owner: Any) {
        requireAccess(owner)
        check(installedOwner == null) { "A scale visual input owner is already installed" }
        installedOwner = owner
        snapshot = Snapshot(DeviceState.DISCONNECTED, null)
    }
    fun offer(owner: Any, state: DeviceState, observation: ScaleObservation?) {
        requireOwner(owner)
        require(state == DeviceState.READY || observation == null) { "Only READY may carry a scale observation" }
        observation?.let { sample ->
            require(sample.receivedAtMs >= 0 && sample.receivedAtMs <= now()) { "Scale reception time is outside the owned monotonic domain" }
            require(ScaleReadingPolicy.supportedWeight(sample.hundredthsGram)) { "Scale weight is outside the supported display range" }
            require(!sample.capabilities.tare && !sample.capabilities.validatedWeightControl && sample.evidence != ScaleEvidence.VERIFIED_TRANSPORT) {
                "Synthetic scale input cannot grant write/control or verified-transport evidence"
            }
            require(sample.batteryPercent == null || sample.capabilities.battery && sample.batteryPercent in 0..100) { "Invalid synthetic battery evidence" }
        }
        val next = Snapshot(state, observation?.detached())
        if (snapshot?.state != state) onState(state)
        next.observation?.let { onObservation(it.detached()) }
        snapshot = next
    }
    fun clear(owner: Any) {
        requireOwner(owner)
        onClear()
        snapshot = null
        installedOwner = null
    }
    fun current(): Snapshot? {
        val owner = installedOwner ?: return null
        requireOwner(owner)
        return snapshot?.let { it.copy(observation = it.observation?.detached()) }
    }
}

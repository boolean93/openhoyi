package io.openhoyi.session

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry

/** Eligibility for explicit human acknowledgement, never automatic outcome confirmation.
 * Does not clear storage, change extraction state or issue any machine command.
 */
object ShotRecoveryGate {
    enum class Block { EXTRACTION_UNSETTLED, DEVICE_MISMATCH, IDLE_NOT_FRESH }

    fun clearBlock(recovery: ShotRecoveryState, address: String?, coffee: DeviceState,
        frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shot: ExtractionState, manualShotActive: Boolean): Block? {
        if (manualShotActive || DeviceConnectionGate.unsettled(shot)) return Block.EXTRACTION_UNSETTLED
        if (!recovery.matchesDevice(address)) return Block.DEVICE_MISMATCH
        if (coffee != DeviceState.READY || frame !is IdleTelemetry ||
            receivedAtMs?.let { it <= nowMs && nowMs - it <= 1500 } != true)
            return Block.IDLE_NOT_FRESH
        return null
    }
}

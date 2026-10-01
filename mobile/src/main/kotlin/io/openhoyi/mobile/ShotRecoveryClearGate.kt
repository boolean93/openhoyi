package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.ShotRecoveryGate
import io.openhoyi.session.ShotRecoveryState

/** Product text for shared human-acknowledgement eligibility. */
class ShotRecoveryClearGate(private val resolve: (Int, Array<out Any>) -> String) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })
    private fun text(id: Int): String = resolve(id, emptyArray())

    fun block(recovery: ShotRecoveryState, address: String?, coffee: DeviceState,
        frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shot: ExtractionState, manualShotActive: Boolean): String? =
        resource(recovery, address, coffee, frame, receivedAtMs, nowMs, shot, manualShotActive)?.let(::text)

    fun resource(recovery: ShotRecoveryState, address: String?, coffee: DeviceState,
        frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shot: ExtractionState, manualShotActive: Boolean): Int? = when (ShotRecoveryGate.clearBlock(
            recovery, address, coffee, frame, receivedAtMs, nowMs, shot, manualShotActive)) {
        ShotRecoveryGate.Block.EXTRACTION_UNSETTLED -> R.string.shot_recovery_block_extraction_unsettled
        ShotRecoveryGate.Block.DEVICE_MISMATCH -> R.string.shot_recovery_block_device_mismatch
        ShotRecoveryGate.Block.IDLE_NOT_FRESH -> R.string.shot_recovery_block_idle_not_fresh
        null -> null
    }
}

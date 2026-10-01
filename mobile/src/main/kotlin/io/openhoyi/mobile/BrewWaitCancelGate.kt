package io.openhoyi.mobile

import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.PreheatGate

/** Maps shared preheat recovery decisions to product text. */
class BrewWaitCancelGate(private val resolve: (Int, Array<out Any>) -> String) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })

    fun block(coffeeState: DeviceState, frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shotState: ExtractionState, unresolvedShot: Boolean): String? =
        blockMessage(coffeeState, frame, receivedAtMs, nowMs, shotState, unresolvedShot)?.render { id, args ->
            resolve(id, args.map { requireNotNull(it) }.toTypedArray())
        }

    fun blockMessage(coffeeState: DeviceState, frame: HoyiMessage?, receivedAtMs: Long?, nowMs: Long,
        shotState: ExtractionState, unresolvedShot: Boolean): ResourceMessage? = when (PreheatGate.cancelBlock(
            coffeeState, frame, receivedAtMs, nowMs, shotState, unresolvedShot)) {
        PreheatGate.CancelBlock.EXTRACTION_UNSETTLED -> ResourceMessage(R.string.cancel_preheat_block_extraction_unsettled)
        PreheatGate.CancelBlock.COFFEE_NOT_READY -> ResourceMessage(R.string.cancel_preheat_block_coffee_not_ready)
        PreheatGate.CancelBlock.IDLE_MISSING -> ResourceMessage(R.string.cancel_preheat_block_idle_missing)
        PreheatGate.CancelBlock.IDLE_STALE -> ResourceMessage(R.string.cancel_preheat_block_idle_stale)
        PreheatGate.CancelBlock.NOT_AWAKE -> ResourceMessage(R.string.cancel_preheat_block_not_awake)
        null -> null
    }
}

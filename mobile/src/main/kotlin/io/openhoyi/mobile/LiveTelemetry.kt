package io.openhoyi.mobile

import io.openhoyi.protocol.BookooSample
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.session.DeviceState

/** Only current, connected readings may be displayed as live values. */
object LiveTelemetry {
    private fun fresh(state: DeviceState, at: Long?, now: Long): Boolean =
        state == DeviceState.READY && at != null && at <= now && now - at <= 1500

    fun machine(frame: HoyiMessage?, state: DeviceState, at: Long?, now: Long): HoyiMessage? =
        frame.takeIf { fresh(state, at, now) }

    fun scale(sample: BookooSample?, state: DeviceState, at: Long?, now: Long): BookooSample? =
        sample.takeIf { fresh(state, at, now) }
}

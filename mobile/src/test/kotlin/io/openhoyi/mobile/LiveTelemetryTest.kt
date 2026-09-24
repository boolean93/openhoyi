package io.openhoyi.mobile

import io.openhoyi.protocol.BookooSample
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.DeviceState
import org.junit.Assert.*
import org.junit.Test

class LiveTelemetryTest {
    private val machine = IdleTelemetry(9200, 12000, 10, 8, 0, 0, 1, 0, ByteFrame(byteArrayOf()))
    private val scale = BookooSample(2510, 145, 43, 43, ByteFrame(byteArrayOf()))

    @Test fun oldFutureAndDisconnectedMachineReadingsAreNotCurrent() {
        assertSame(machine, LiveTelemetry.machine(machine, DeviceState.READY, 1000, 2500))
        assertNull(LiveTelemetry.machine(machine, DeviceState.READY, 1000, 2501))
        assertNull(LiveTelemetry.machine(machine, DeviceState.READY, 2501, 2500))
        assertNull(LiveTelemetry.machine(machine, DeviceState.FAILED, 2490, 2500))
    }

    @Test fun oldFutureAndDisconnectedScaleReadingsAreNotCurrent() {
        assertSame(scale, LiveTelemetry.scale(scale, DeviceState.READY, 1000, 2500))
        assertNull(LiveTelemetry.scale(scale, DeviceState.READY, 1000, 2501))
        assertNull(LiveTelemetry.scale(scale, DeviceState.READY, 2501, 2500))
        assertNull(LiveTelemetry.scale(scale, DeviceState.DISCONNECTED, 2490, 2500))
    }
}

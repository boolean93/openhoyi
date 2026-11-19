package io.openhoyi.mobile

import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import org.junit.Assert.*
import org.junit.Test

class HomeBoilerDialHoldTest {
    private val owner = Any()
    private val address = "00:00:00:AB:CD:01"
    private val frame = IdleTelemetry(9140, 11980, 88, 9, 0, 0, 1, 0, ByteFrame(byteArrayOf()))
    private val hold = HomeBoilerDialHold()

    @Test fun pendingGapPreservesLastValidTemperaturePressureAndTargetsUntilNewFrame() {
        val previous = hold.resolve(owner, address, frame, 1000, 92, 120, 1100, hold = false)
        assertEquals(previous, hold.resolve(owner, address.lowercase(), null, null,
            null, null, 2800, hold = true))
        val newer = frame.copy(brewTemperatureHundredthsC = 9150)
        val resumed = hold.resolve(owner, address, newer, 3000, 92, 120, 3100, hold = true)
        assertEquals(newer, resumed.frame)
    }

    @Test fun terminalOrRealDisconnectDoesNotRetainStaleReadings() {
        hold.resolve(owner, address, frame, 1000, 92, 120, 1100, hold = false)
        assertNull(hold.resolve(owner, address, null, null, null, null, 2800, hold = false).frame)
        assertNull(hold.resolve(owner, address, null, null, null, null, 2900, hold = true).frame)
    }

    @Test fun heldDisplayNeverOutlivesFiveSecondsOrCrossesDeviceOrService() {
        fun prime() = hold.resolve(owner, address, frame, 1000, 92, 120, 1100, hold = false)
        prime()
        assertEquals(frame, hold.resolve(owner, address, null, null, null, null, 6000, hold = true).frame)
        assertNull(hold.resolve(owner, address, null, null, null, null, 6001, hold = true).frame)
        prime()
        assertNull(hold.resolve(owner, "00:00:00:AB:CD:02", null, null, null, null, 2000, hold = true).frame)
        prime()
        assertNull(hold.resolve(Any(), address, null, null, null, null, 2000, hold = true).frame)
    }
}

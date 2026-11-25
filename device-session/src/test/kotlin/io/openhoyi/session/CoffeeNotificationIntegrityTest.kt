package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class CoffeeNotificationIntegrityTest {
    @Test fun corruptMachineReportsNeverAdvanceEvidenceButExtractionHasNoChecksumByte() {
        val f = CoffeeSessionFixture(trackCoffeeDelivery = true)
        fun receive(hex: String, corrupt: Boolean = false) {
            val frame = f.hex(hex)
            if (corrupt) frame[frame.lastIndex] = (frame.last().toInt() xor 1).toByte()
            f.session.onNotification(f.session.generation, KnownGatt.coffeeNotify, frame, ++f.now)
        }
        val base = f.session.coffeeReadbackCursor!!.sequence
        for (report in listOf("830113FD5C007D0F350019006E", "400024BF2F1C770B00000000000000190321AF",
                "8340FE0A00071E0A00071E0A00071E0A00071E3D", "83800A00071E0A00071E0A00071E10")) {
            receive(report, corrupt = true)
            assertEquals(base, f.session.coffeeReadbackCursor!!.sequence)
        }
        receive("830113FD5C007D0F350019006E")
        assertEquals(92, f.session.latestOrdinaryCoffeeSettings!!.value.brewTemperatureC)
        receive("8008070001000005241F1E510B")
        assertEquals(1, f.session.latestNativeExtraction!!.value.elapsedSeconds)
    }
}

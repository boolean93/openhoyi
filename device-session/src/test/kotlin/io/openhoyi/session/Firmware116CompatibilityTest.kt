package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class Firmware116CompatibilityTest {
    @Test fun firmware116RetainsSettingsReadbackGateAndExactExistingWrite() {
        val f=CoffeeSessionFixture()
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,f.hex("830116FD5C007D0F350019006B"))
        assertEquals(DeviceState.READY,f.session.state)
        assertEquals(CoffeeFirmware(1,1,6),f.session.observedFirmware)
        f.idle(0);var result:OperationResult?=null
        val size=f.calls.size
        f.session.writeSetting(MachineSettingChange.BrewTemperature(93)){result=it}
        assertEquals(size+1,f.calls.size)
        assertArrayEquals(f.hex("0402005D00"),(f.calls.last().third as GattOperation.Write).bytes)
        f.complete();assertTrue(result is OperationResult.Success)
    }
}

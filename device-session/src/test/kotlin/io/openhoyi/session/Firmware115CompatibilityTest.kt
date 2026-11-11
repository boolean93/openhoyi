package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class Firmware115CompatibilityTest {
    @Test fun controlProfilesAreExplicitRatherThanFutureVersionRange() {
        for(version in listOf(0x113,0x114,0x115))assertTrue(CoffeeFirmwareProfiles.permitsControl(version))
        for(version in listOf(0,0x112,0x116,0x120,0x200))assertFalse(CoffeeFirmwareProfiles.permitsControl(version))
    }
    @Test fun firmware115RetainsSettingsReadbackGateAndExactExistingWrite() {
        val f=CoffeeSessionFixture()
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,f.hex("830115FD5C007D0F3500190068"))
        assertEquals(DeviceState.READY,f.session.state)
        assertEquals(CoffeeFirmware(1,1,5),f.session.observedFirmware)
        f.idle(0);var result:OperationResult?=null
        val size=f.calls.size
        f.session.writeSetting(MachineSettingChange.BrewTemperature(93)){result=it}
        assertEquals(size+1,f.calls.size)
        assertArrayEquals(f.hex("0402005D00"),(f.calls.last().third as GattOperation.Write).bytes)
        f.complete();assertTrue(result is OperationResult.Success)
    }
}

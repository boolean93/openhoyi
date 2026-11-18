package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test

class BackgroundTemperatureWriteTest {
    private val address="AA:BB:CC:DD:EE:FF"
    private val initial=(HoyiCodec.decode("830113FD5C007D0F350019006E".chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value as Settings
    private class Memory:MachineWriteRecoveryState.Storage {
        var value=MachineWriteRecoveryState.Record(null,null)
        var fail=false
        override fun read()=value
        override fun write(record:MachineWriteRecoveryState.Record):Boolean { if(fail)return false;value=record;return true }
    }
    @Test fun transportOnlyIsBrieflyBusyAndReadbackIsNonBlocking() {
        val memory=Memory(); val write=BackgroundTemperatureWrite(memory)
        val token=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(93),address,1))
        assertTrue(write.busy)
        assertNull(write.begin(MachineSettingChange.BrewTemperature(94),address,1))
        assertTrue(write.written(token,OperationResult.Success(),2))
        assertFalse(write.busy)
        assertTrue(write.requiresExplicitRetry)
        assertEquals(SettingsWriteTracker.State.WAITING_READBACK,write.state)
        assertNull(write.observe(address,2,initial.copy(brewTemperatureC=93)))
        assertEquals(SettingsWriteTracker.State.CONFIRMED,write.observe(address,3,initial.copy(brewTemperatureC=93)))
        assertFalse(memory.value.pending)
        assertFalse(write.requiresExplicitRetry)
    }
    @Test fun explicitNewRequestSupersedesObserverNotOldWireAndLateCallbacksAreInert() {
        val write=BackgroundTemperatureWrite(Memory())
        val first=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(93),address,1))
        write.written(first,OperationResult.Success(),2)
        val second=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(94),address,3))
        assertFalse(write.permitsRefresh(first,address))
        assertFalse(write.written(first,OperationResult.Failed("late"),4))
        assertFalse(write.tracker.timeout(first,4))
        assertNull(write.observe(address,4,initial.copy(brewTemperatureC=94))) // before new transport success
        write.written(second,OperationResult.Success(),4)
        assertNull(write.observe(address,5,initial.copy(brewTemperatureC=93)))
        assertEquals(SettingsWriteTracker.State.WAITING_READBACK,write.state)
        assertEquals(SettingsWriteTracker.State.CONFIRMED,write.observe(address,6,initial.copy(brewTemperatureC=94)))
    }
    @Test fun timeoutOrDisconnectDoesNotBlockNextExplicitRequestOrClaimConfirmation() {
        for(disconnect in listOf(false,true)) {
            val write=BackgroundTemperatureWrite(Memory())
            val first=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(93),address,1))
            write.written(first,OperationResult.Success(),2)
            if(disconnect)write.disconnected(3) else assertTrue(write.tracker.timeout(first,3))
            assertEquals(SettingsWriteTracker.State.UNKNOWN,write.state)
            assertFalse(write.busy)
            assertFalse(write.permitsRefresh(first,address))
            assertNull(write.observe("00:11:22:33:44:55",4,initial.copy(brewTemperatureC=93)))
            assertNotNull(write.begin(MachineSettingChange.BrewTemperature(92),address,4))
        }
    }
    @Test fun companionFenceSurvivesAnUnrelatedTemperatureRequest() {
        val write=BackgroundTemperatureWrite(Memory())
        val first=requireNotNull(write.begin(MachineSettingChange.StandbyTemperature(54,15),address,1))
        write.written(first,OperationResult.Success(),3)
        assertFalse(write.companionAvailable(MachineSettingChange.StandbyDelay(30,53),3))
        val other=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(93),address,3))
        write.written(other,OperationResult.Success(),3)
        assertFalse(write.companionAvailable(MachineSettingChange.StandbyDelay(30,53),3))
        assertTrue(write.companionAvailable(MachineSettingChange.StandbyDelay(30,54),4))
    }
    @Test fun journalFailureDoesNotSendOrDiscardAnOldObserver() {
        val memory=Memory();val write=BackgroundTemperatureWrite(memory)
        val first=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(93),address,1))
        write.written(first,OperationResult.Success(),2)
        memory.fail=true
        assertNull(write.begin(MachineSettingChange.BrewTemperature(94),address,2))
        assertTrue(write.permitsRefresh(first,address))
        assertEquals(MachineSettingChange.BrewTemperature(93),write.change)
    }
    @Test fun restartHasLocalUnknownButNoGlobalSafetyMarkerAndNoReplay() {
        val memory=Memory();val write=BackgroundTemperatureWrite(memory)
        write.begin(MachineSettingChange.BrewTemperature(93),address,1)
        val restored=BackgroundTemperatureWrite(memory)
        assertEquals(SettingsWriteTracker.State.UNKNOWN,restored.state)
        assertTrue(restored.pendingAfterRestart)
        assertFalse(restored.busy)
        assertFalse(restored.permitsWrite(1,address))
        assertNotNull(restored.begin(MachineSettingChange.BrewTemperature(94),address,2))
    }
    @Test fun explicitNewDeviceIntentCannotBeClearedByOldDeviceReadback() {
        val memory=Memory();val write=BackgroundTemperatureWrite(memory)
        val first=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(93),address,1))
        write.written(first,OperationResult.Success(),2)
        write.disconnected(3)
        val nextAddress="00:11:22:33:44:55"
        val second=requireNotNull(write.begin(MachineSettingChange.BrewTemperature(94),nextAddress,4))
        write.written(second,OperationResult.Success(),5)
        assertNull(write.observe(address,6,initial.copy(brewTemperatureC=94)))
        assertEquals(nextAddress,memory.value.address)
        assertEquals(SettingsWriteTracker.State.WAITING_READBACK,write.state)
        assertEquals(SettingsWriteTracker.State.CONFIRMED,write.observe(nextAddress,7,initial.copy(brewTemperatureC=94)))
    }
    @Test fun definitePairedWriteFailureAllowsExplicitRetryWithoutPhantomReadbackWait() {
        val write=BackgroundTemperatureWrite(Memory())
        val token=requireNotNull(write.begin(MachineSettingChange.StandbyTemperature(54,15),address,1))
        write.written(token,OperationResult.Failed("not dispatched"),1)
        assertEquals(SettingsWriteTracker.State.FAILED,write.state)
        assertTrue(write.companionAvailable(MachineSettingChange.StandbyTemperature(54,15),1))
        assertNotNull(write.begin(MachineSettingChange.StandbyTemperature(54,15),address,1))
    }
    @Test fun boilerAndOtherSafetySettingsCannotEnterBackgroundPath() {
        val write=BackgroundTemperatureWrite(Memory())
        assertNull(write.begin(MachineSettingChange.BrewHeating(false),address,1))
        assertNull(write.begin(MachineSettingChange.Light(false),address,1))
    }
}

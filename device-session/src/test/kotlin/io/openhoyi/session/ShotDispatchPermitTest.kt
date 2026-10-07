package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class ShotDispatchPermitTest {
    private val address="AA:BB:CC:DD:EE:FF"
    private val empty=MachineWriteRecoveryState.Record(null,null)
    private fun valid()=ShotDispatchPermit.Context(address,address,true,true,true,false,false,true,address,empty,empty)
    @Test fun ownPendingShotIsRequiredAndPermitted()=assertTrue(ShotDispatchPermit.allows(valid()))
    @Test fun existingOwnedPreheatMayPersistAfterPreparationConsumed() {
        val record=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.BREW_WAIT,address)
        assertTrue(ShotDispatchPermit.allows(valid().copy(originalMachineIntent=record,currentMachineIntent=record)))
    }
    @Test fun addressCaseDoesNotChangeDeviceIdentity()=assertTrue(ShotDispatchPermit.allows(valid().copy(currentAddress=address.lowercase())))
    @Test fun eachLostProductConditionFailsClosed() {
        val v=valid()
        val bad=listOf(v.copy(originalAddress=null),v.copy(currentAddress=null),v.copy(currentAddress="11:22:33:44:55:66"),
            v.copy(sameHub=false),v.copy(ready=false),v.copy(sameValidatedProfile=false),v.copy(manualShotActive=true),
            v.copy(ordinaryWriteBusy=true),v.copy(shotPending=false),v.copy(shotAddress=null),v.copy(shotAddress="11:22:33:44:55:66"),
            v.copy(currentMachineIntent=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.SETTING,address)),
            v.copy(originalMachineIntent=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.UNKNOWN,null)))
        bad.forEachIndexed { index,c->assertFalse("mutation $index",ShotDispatchPermit.allows(c)) }
    }
    @Test fun unchangedForeignOrCorruptIntentNeverAuthorizesShot() {
        for(kind in MachineWriteRecoveryState.Kind.entries) {
            if(kind==MachineWriteRecoveryState.Kind.BREW_WAIT)continue
            val record=MachineWriteRecoveryState.Record(kind,address)
            assertFalse(kind.toString(),ShotDispatchPermit.allows(valid().copy(originalMachineIntent=record,currentMachineIntent=record)))
        }
        for(identity in listOf(null,"11:22:33:44:55:66")) {
            val record=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.BREW_WAIT,identity)
            assertFalse(ShotDispatchPermit.allows(valid().copy(originalMachineIntent=record,currentMachineIntent=record)))
        }
    }
    @Test fun clearedOrReplacedPreheatRecordBlocks() {
        val record=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.BREW_WAIT,address)
        assertFalse(ShotDispatchPermit.allows(valid().copy(originalMachineIntent=record)))
        assertFalse(ShotDispatchPermit.allows(valid().copy(originalMachineIntent=record,
            currentMachineIntent=record.copy(address="11:22:33:44:55:66"))))
    }
}

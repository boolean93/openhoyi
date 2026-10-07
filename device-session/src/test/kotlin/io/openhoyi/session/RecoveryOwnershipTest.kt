package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class RecoveryOwnershipTest {
    private val address="AA:BB:CC:DD:EE:01"
    private class ShotDisk:ShotRecoveryState.Storage {
        var record=ShotRecoveryState.Record(false,null);var writable=true;var writes=0
        override fun read()=record
        override fun write(record:ShotRecoveryState.Record):Boolean { writes++;if(!writable)return false;this.record=record;return true }
    }
    private class MachineDisk:MachineWriteRecoveryState.Storage {
        var record=MachineWriteRecoveryState.Record(null,null);var writable=true;var writes=0
        override fun read()=record
        override fun write(record:MachineWriteRecoveryState.Record):Boolean { writes++;if(!writable)return false;this.record=record;return true }
    }
    @Test fun shotSameAddressRearmRevokesOldCompletion() {
        val d=ShotDisk();val s=ShotRecoveryState(d);assertTrue(s.arm(address));val old=s.captureOwnership()
        assertTrue(s.clear());assertTrue(s.arm(address));val count=d.writes
        assertFalse(s.owns(old));assertFalse(s.clear(old));assertEquals(count,d.writes);assertTrue(s.pending)
    }
    @Test fun machineSameAddressAndKindRearmRevokesOldCompletion() {
        val d=MachineDisk();val s=MachineWriteRecoveryState(d);assertTrue(s.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address));val old=s.captureOwnership()
        assertTrue(s.clear());assertTrue(s.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address));val count=d.writes
        assertFalse(s.owns(old));assertFalse(s.clear(old));assertEquals(count,d.writes);assertTrue(s.pending)
    }
    @Test fun shotForeignOrMissingTokenNeverWrites() {
        val d=ShotDisk();val s=ShotRecoveryState(d);assertTrue(s.arm(address))
        val other=ShotRecoveryState(d);val token=other.captureOwnership();val count=d.writes
        assertFalse(s.clear(token));assertFalse(s.clear(null));assertEquals(count,d.writes);assertTrue(s.pending)
    }
    @Test fun machineForeignOrMissingTokenNeverWrites() {
        val d=MachineDisk();val s=MachineWriteRecoveryState(d);assertTrue(s.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address))
        val other=MachineWriteRecoveryState(d);val count=d.writes
        assertFalse(s.clear(other.captureOwnership()));assertFalse(s.clear(null));assertEquals(count,d.writes);assertTrue(s.pending)
    }
    @Test fun shotClearFailurePreservesOwnerAndRetry() {
        val d=ShotDisk();val s=ShotRecoveryState(d);assertNull(s.captureOwnership());assertTrue(s.arm(address));val token=s.captureOwnership()
        assertTrue(s.arm(address.lowercase()));assertTrue(s.owns(token))
        d.writable=false;assertFalse(s.clear(token));assertTrue(s.owns(token));d.writable=true
        assertTrue(s.clear(token));assertFalse(s.owns(token));val count=d.writes
        assertFalse(s.clear(token));assertEquals(count,d.writes)
    }
    @Test fun machineClearFailurePreservesOwnerAndRetry() {
        val d=MachineDisk();val s=MachineWriteRecoveryState(d);assertNull(s.captureOwnership());assertTrue(s.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address));val token=s.captureOwnership()
        assertFalse(s.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address));assertTrue(s.owns(token))
        d.writable=false;assertFalse(s.clear(token));assertTrue(s.owns(token));d.writable=true
        assertTrue(s.clear(token));assertFalse(s.owns(token));val count=d.writes
        assertFalse(s.clear(token));assertEquals(count,d.writes)
    }
    @Test fun shotUnknownIdentityTokenStillNeedsOriginalStateInstance() {
        val d=ShotDisk();val s=ShotRecoveryState(d);assertTrue(s.arm(null));val token=s.captureOwnership()
        assertTrue(s.owns(token));assertFalse(ShotRecoveryState(d).owns(token));assertTrue(s.clear(token))
    }
    @Test fun machineRestartCannotReuseOldToken() {
        val d=MachineDisk();val s=MachineWriteRecoveryState(d);assertTrue(s.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address));val token=s.captureOwnership()
        assertFalse(MachineWriteRecoveryState(d).owns(token));assertTrue(s.clear(token))
    }
}

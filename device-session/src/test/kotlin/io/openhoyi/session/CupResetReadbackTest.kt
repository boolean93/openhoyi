package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class CupResetReadbackTest {
    private class Fixture(kind:MachineWriteRecoveryState.Kind=MachineWriteRecoveryState.Kind.CUP_RESET) {
        var disk=MachineWriteRecoveryState.Record(null,null)
        var writable=true
        var throwing=false
        var writes=0
        var clears=0
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=disk
            override fun write(record:MachineWriteRecoveryState.Record):Boolean {
                writes++
                if(!record.pending){clears++;if(throwing)error("storage clear exception");if(!writable)return false}
                disk=record;return true
            }
        })
        val tracker=CupResetTracker()
        val token=requireNotNull(tracker.begin(25))
        init { assertTrue(recovery.arm(kind,"AA:BB:CC:DD:EE:01"));assertTrue(tracker.written(token,OperationResult.Success(),7,11)) }
        fun settings(serial:Long=8,count:Int=0)=CupResetReadback.observe(tracker,recovery,CupResetReadback.Sample.Settings(serial,count))
        fun idle(serial:Long=12,count:Int=0)=CupResetReadback.observe(tracker,recovery,CupResetReadback.Sample.Idle(serial,count))
        fun retained(){assertTrue(recovery.pending);assertTrue(disk.pending);assertEquals("AA:BB:CC:DD:EE:01",recovery.address)}
    }
    @Test fun bothNewChannelsClearInEitherOrderExactlyOnce() {
        for(settingsFirst in listOf(false,true)) {
            val f=Fixture()
            val first=if(settingsFirst)f.settings() else f.idle()
            assertTrue(first.isEmpty());f.retained();assertEquals(0,f.clears)
            assertEquals(listOf(CupResetReadback.Event.CONFIRMED),if(settingsFirst)f.idle() else f.settings())
            assertFalse(f.recovery.pending);assertFalse(f.disk.pending);assertEquals(1,f.clears)
            assertTrue(f.settings(9).isEmpty());assertTrue(f.idle(13).isEmpty());assertEquals(1,f.clears)
        }
    }
    @Test fun falseClearKeepsFailureAsLastFeedbackAndRetainsIntent() {
        val f=Fixture();f.writable=false;assertTrue(f.settings().isEmpty())
        assertEquals(listOf(CupResetReadback.Event.CONFIRMED,CupResetReadback.Event.CLEAR_FAILED),f.idle())
        f.retained();assertEquals(CupResetTracker.State.CONFIRMED,f.tracker.state);assertEquals(1,f.clears)
    }
    @Test fun thrownClearKeepsFailureAsLastFeedbackAndRetainsIntent() {
        val f=Fixture();f.throwing=true;assertTrue(f.idle().isEmpty())
        assertEquals(listOf(CupResetReadback.Event.CONFIRMED,CupResetReadback.Event.CLEAR_FAILED),f.settings())
        f.retained();assertEquals(1,f.clears)
    }
    @Test fun clearRetryOnlyAffectsStorageAndDoesNotRepeatConfirmation() {
        val f=Fixture();f.writable=false;f.settings();f.idle()
        assertEquals(listOf(CupResetReadback.Event.CLEAR_FAILED),f.settings(9));f.retained();assertEquals(2,f.clears)
        f.writable=true;assertTrue(f.idle(13).isEmpty());assertFalse(f.recovery.pending);assertFalse(f.disk.pending)
        assertEquals(3,f.clears);assertEquals(CupResetTracker.State.CONFIRMED,f.tracker.state)
    }
    @Test fun oldOrIncompleteEvidenceCannotClear() {
        val f=Fixture();assertTrue(f.settings(7).isEmpty());assertTrue(f.idle(11).isEmpty())
        assertTrue(f.settings(8).isEmpty());assertTrue(f.idle(11).isEmpty());f.retained();assertEquals(0,f.clears)
    }
    @Test fun anotherWriteKindCannotBeClearedByCupConfirmation() {
        val f=Fixture(MachineWriteRecoveryState.Kind.SETTING);f.settings()
        assertEquals(listOf(CupResetReadback.Event.CONFIRMED),f.idle())
        f.retained();assertEquals(MachineWriteRecoveryState.Kind.SETTING,f.recovery.kind);assertEquals(0,f.clears)
    }
    @Test fun unknownNonzeroReadbackReconcilesWithoutClearing() {
        val f=Fixture();f.tracker.disconnected(7,11);assertTrue(f.settings(8,25).isEmpty())
        assertEquals(listOf(CupResetReadback.Event.RECONCILED),f.idle(12,25));f.retained();assertEquals(0,f.clears)
    }
    @Test fun unknownZeroReadbackConfirmsAndClears() {
        val f=Fixture();f.tracker.disconnected(7,11);f.idle()
        assertEquals(listOf(CupResetReadback.Event.CONFIRMED),f.settings());assertFalse(f.recovery.pending);assertEquals(1,f.clears)
    }
}

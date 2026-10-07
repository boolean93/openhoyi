package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class TarePersistenceBarrierTest {
    private class F {
        var cached=false;var disk=false;var marked=false
        var failCommit=false;var failArm=false;var failClear=false;var armThrows=false;var clearThrows=false;var readThrows=false
        val order=mutableListOf<String>()
        val record=object:StandaloneTare.Storage {
            override fun read():Boolean { if(readThrows)error("read");return cached }
            override fun write(pending:Boolean):Boolean {
                order+="record=$pending";cached=pending
                if(failCommit)return false
                disk=pending;return true
            }
        }
        val marker=object:TarePersistenceBarrier.Marker {
            override fun exists()=marked
            override fun arm():Boolean { order+="arm";if(armThrows)error("arm");if(failArm)return false;marked=true;return true }
            override fun clear():Boolean { order+="clear";if(clearThrows)error("clear");if(failClear)return false;marked=false;return true }
        }
        fun storage()=TarePersistenceBarrier(record,marker)
        fun tracker()=StandaloneTare(storage()) { 10000L }
    }
    @Test fun markerBeforeRecordAndClearAfterRecord() { val f=F();assertTrue(f.storage().write(true));assertEquals(listOf("arm","record=true"),f.order);f.order.clear();assertTrue(f.storage().write(false));assertEquals(listOf("arm","record=false","clear"),f.order);assertFalse(f.storage().read()) }
    @Test fun failedClearCommitChangedCacheCannotClearNewTracker() { val f=F();assertTrue(f.storage().write(true));f.failCommit=true;assertFalse(f.storage().write(false));assertFalse(f.cached);assertTrue(f.disk);assertTrue(f.storage().read());assertEquals(StandaloneTare.State.UNKNOWN,f.tracker().state) }
    @Test fun markerAloneSurvivesAmbiguousDiskClear() { val f=F();f.marked=true;f.cached=false;f.disk=false;assertTrue(f.storage().read());assertTrue(f.tracker().unresolved) }
    @Test fun markerArmFailurePreventsRecordMutation() { val f=F();f.failArm=true;assertFalse(f.storage().write(true));assertFalse(f.cached);assertEquals(listOf("arm"),f.order) }
    @Test fun markerClearFailureRetainsUnknownAcrossInstances() { val f=F();assertTrue(f.storage().write(true));f.failClear=true;assertFalse(f.storage().write(false));assertFalse(f.cached);assertFalse(f.disk);assertTrue(f.tracker().unresolved) }
    @Test fun markerArmExceptionIsFailedWithoutRecordMutation() { val f=F();f.armThrows=true;assertFalse(f.storage().write(true));assertEquals(listOf("arm"),f.order) }
    @Test fun markerClearExceptionRemainsUnknown() { val f=F();assertTrue(f.storage().write(true));f.clearThrows=true;assertFalse(f.storage().write(false));assertTrue(f.tracker().unresolved) }
    @Test fun explicitSuccessfulRetryClearsBoth() { val f=F();assertTrue(f.storage().write(true));f.failCommit=true;assertFalse(f.storage().write(false));f.failCommit=false;assertTrue(f.storage().write(true));assertTrue(f.storage().write(false));assertFalse(f.marked);assertFalse(f.tracker().unresolved) }
}

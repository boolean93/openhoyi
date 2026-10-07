package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecoveryPersistenceBarrierTest(private val kind:String) {
    private data class R(val kind:String?,val address:String?)
    private inner class F {
        val original=R(kind,"AA:BB:CC:DD:EE:01");val empty=R(null,null)
        var cache=empty;var disk=empty;var saved:R?=null
        var failCommit=false;var throwCommit=false;var failMarker=false;var failDelete=false;var badMarker=false
        val order=mutableListOf<String>()
        val record=object:RecoveryPersistenceBarrier.RecordStorage<R> {
            override fun read()=cache
            override fun write(value:R):Boolean { order+="record";cache=value;if(throwCommit)error("commit");if(failCommit)return false;disk=value;return true }
        }
        val marker=object:RecoveryPersistenceBarrier.Marker<R> {
            override fun exists()=saved!=null
            override fun read():R? { if(badMarker)error("corrupt marker");return saved }
            override fun write(value:R):Boolean { order+="marker";if(failMarker)return false;saved=value;return true }
            override fun clear():Boolean { order+="delete";if(failDelete)return false;saved=null;badMarker=false;return true }
        }
        fun storage()=RecoveryPersistenceBarrier(record,marker) { it.kind!=null }
        fun arm() { assertTrue(storage().write(original));order.clear() }
    }
    @Test fun failedClearKeepsCompleteOriginalAcrossInstances() { val f=F();f.arm();f.failCommit=true;assertFalse(f.storage().write(f.empty));assertEquals(f.empty,f.cache);assertEquals(f.original,f.storage().read());assertEquals(f.original,f.disk) }
    @Test fun thrownCommitAfterCacheMutationKeepsOriginal() { val f=F();f.arm();f.throwCommit=true;assertFalse(f.storage().write(f.empty));assertEquals(f.empty,f.cache);assertEquals(f.original,f.storage().read()) }
    @Test fun markerFailurePreventsRecordMutation() { val f=F();f.failMarker=true;assertFalse(f.storage().write(f.original));assertEquals(f.empty,f.cache);assertEquals(listOf("marker"),f.order) }
    @Test fun failedDeleteKeepsOriginalAfterPreferencesCleared() { val f=F();f.arm();f.failDelete=true;assertFalse(f.storage().write(f.empty));assertEquals(f.empty,f.cache);assertEquals(f.empty,f.disk);assertEquals(f.original,f.storage().read()) }
    @Test fun legacyClearSavesOriginalBeforeTouchingPreferences() { val f=F();f.cache=f.original;f.disk=f.original;f.failCommit=true;assertFalse(f.storage().write(f.empty));assertEquals(listOf("marker","record"),f.order);assertEquals(f.original,f.storage().read()) }
    @Test fun successfulClearDoesNotOverwritePendingMarker() { val f=F();f.arm();assertTrue(f.storage().write(f.empty));assertEquals(listOf("record","delete"),f.order);assertEquals(f.empty,f.storage().read());assertNull(f.saved) }
    @Test fun corruptMarkerCanBeClearedAfterIndependentAuthorization() { val f=F();f.arm();f.badMarker=true;assertThrows(IllegalStateException::class.java) { f.storage().read() };assertTrue(f.storage().write(f.empty));assertEquals(listOf("record","delete"),f.order);assertEquals(f.empty,f.storage().read()) }
    @Test fun pendingRecordReplacesUnsentFailedArmSnapshot() { val f=F();f.failCommit=true;assertFalse(f.storage().write(f.original));f.failCommit=false;val next=R(kind,"AA:BB:CC:DD:EE:02");assertTrue(f.storage().write(next));assertEquals(next,f.storage().read());assertEquals(next,f.saved) }
    companion object { @JvmStatic @Parameterized.Parameters(name="{0}") fun cases()=listOf("SHOT","CUP_RESET","SETTING","SLEEP_SCHEDULE","SLEEP_NOW","BREW_WAIT").map { arrayOf(it) } }
}

package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test

class DurableStandaloneTareTest {
    private class Store(var pending:Boolean=false):StandaloneTare.Storage {
        var readFail=false;var failArm=false;var failClear=false;var writes=0
        override fun read():Boolean { if(readFail)error("read");return pending }
        override fun write(pending:Boolean):Boolean { writes++;if(if(pending)failArm else failClear)return false;this.pending=pending;return true }
    }
    private var now=10000L
    private fun tracker(s:Store)=StandaloneTare(s) { now }
    @Test fun persistedPendingRestoresUnknown() { val s=Store(true);assertEquals(StandaloneTare.State.UNKNOWN,tracker(s).state) }
    @Test fun unreadableRecordRestoresUnknown() { val s=Store();s.readFail=true;assertTrue(tracker(s).unresolved) }
    @Test fun beforeWriteHasDurableRecord() { val s=Store();val t=tracker(s);assertNotNull(t.begin());assertTrue(s.pending);assertEquals(StandaloneTare.State.UNKNOWN,tracker(s).state) }
    @Test fun failedArmPreventsToken() { val s=Store();s.failArm=true;val t=tracker(s);assertNull(t.begin());assertTrue(t.unresolved) }
    @Test fun waitingZeroSurvivesRestart() { val s=Store();val t=tracker(s);t.written(t.begin()!!,OperationResult.Success(),3);assertTrue(s.pending);assertTrue(tracker(s).unresolved) }
    @Test fun newZeroClearsBeforeConfirmation() { val s=Store();val t=tracker(s);t.written(t.begin()!!,OperationResult.Success(),3);t.sample(4,0);assertFalse(s.pending);assertFalse(tracker(s).unresolved);assertEquals(StandaloneTare.State.CONFIRMED,t.state) }
    @Test fun clearFailureKeepsUnknown() { val s=Store();val t=tracker(s);t.written(t.begin()!!,OperationResult.Success(),3);s.failClear=true;t.sample(4,0);assertEquals(StandaloneTare.State.UNKNOWN,t.state);assertTrue(s.pending);assertTrue(tracker(s).unresolved) }
    @Test fun clearFailureDoesNotSilentlyRetryOnAnotherZero() { val s=Store();val t=tracker(s);t.written(t.begin()!!,OperationResult.Success(),3);s.failClear=true;t.sample(4,0);val count=s.writes;s.failClear=false;t.sample(5,0);assertEquals(count,s.writes);assertTrue(s.pending) }
    @Test fun knownUnsentFirstAttemptClears() { val s=Store();val t=tracker(s);t.written(t.begin()!!,OperationResult.Failed("unsent"),3);assertFalse(s.pending);assertFalse(t.unresolved) }
    @Test fun knownUnsentClearFailureRemainsUnknown() { val s=Store();val t=tracker(s);val id=t.begin()!!;s.failClear=true;t.written(id,OperationResult.Failed("unsent"),3);assertTrue(t.unresolved);assertTrue(s.pending) }
    @Test fun unknownRetryKnownFailureKeepsRecord() { val s=Store(true);val t=tracker(s);t.written(t.begin()!!,OperationResult.Failed("unsent"),3);assertTrue(t.unresolved);assertTrue(s.pending) }
    @Test fun explicitRetryNewZeroCanRecover() { val s=Store(true);val t=tracker(s);t.written(t.begin()!!,OperationResult.Success(),3);t.sample(4,0);assertFalse(s.pending);assertFalse(t.unresolved) }
    @Test fun timeoutAndLateZeroKeepRecord() { val s=Store();val t=tracker(s);t.written(t.begin()!!,OperationResult.Success(),3);now+=5000;t.sample(4,0);assertTrue(s.pending);assertTrue(t.unresolved) }
    @Test fun disconnectedAndLateSuccessKeepRecord() { val s=Store();val t=tracker(s);val id=t.begin()!!;t.disconnected();assertFalse(t.written(id,OperationResult.Success(),3));assertTrue(s.pending);assertTrue(t.unresolved) }
}

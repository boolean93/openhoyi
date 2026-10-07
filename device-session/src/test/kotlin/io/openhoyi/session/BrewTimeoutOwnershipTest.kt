package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BrewTimeoutOwnershipTest(private val ready:Boolean) {
    companion object {@JvmStatic @Parameterized.Parameters(name="ready={0}") fun states()=listOf(arrayOf(false),arrayOf(true))}
    private class F(ready:Boolean) {
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=MachineWriteRecoveryState.Record(null,null)
            override fun write(record:MachineWriteRecoveryState.Record)=true
        })
        val tracker=BrewPreparation();val token=requireNotNull(tracker.begin("curve",91))
        val owner:MachineWriteRecoveryState.Ownership
        var cancels=0
        init {check(recovery.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,"AA:BB:CC:DD:EE:01"));owner=requireNotNull(recovery.captureOwnership())
            check(tracker.written(token,OperationResult.Success(),0));if(ready)check(tracker.observe(1,9100))}
        fun expire(guard:()->Boolean={recovery.owns(owner)})=BrewPreparationWatchdog.expire(tracker,token,guard,"ownership changed") {
            cancels++;requireNotNull(tracker.beginCancel());null as String?
        }
    }
    @Test fun originalOwnerRequestsCancelOnce() {
        val f=F(ready);assertEquals(BrewPreparationWatchdog.Outcome.CANCEL_REQUESTED,f.expire()?.outcome)
        assertEquals(1,f.cancels);assertEquals(BrewPreparation.State.CANCELLING,f.tracker.state);assertTrue(f.recovery.pending)
        assertNull(f.expire());assertEquals(1,f.cancels)
    }
    @Test fun sameAddressRearmNeverInvokesOldCancellation() {
        val f=F(ready);check(f.recovery.clear());check(f.recovery.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,"AA:BB:CC:DD:EE:01"))
        assertEquals(BrewPreparationWatchdog.Result(BrewPreparationWatchdog.Outcome.CANCEL_BLOCKED,"ownership changed"),f.expire())
        assertEquals(0,f.cancels);assertEquals(BrewPreparation.State.UNKNOWN,f.tracker.state);assertTrue(f.recovery.pending)
        assertNull(f.expire());assertEquals(0,f.cancels)
    }
    @Test fun missingOriginalHubAuthorizationBlocks() {
        val f=F(ready);assertEquals(BrewPreparationWatchdog.Outcome.CANCEL_BLOCKED,f.expire { false }?.outcome)
        assertEquals(0,f.cancels);assertEquals(BrewPreparation.State.UNKNOWN,f.tracker.state);assertTrue(f.recovery.pending)
    }
    @Test fun authorizationExceptionFailsClosed() {
        val f=F(ready);assertEquals(BrewPreparationWatchdog.Outcome.CANCEL_BLOCKED,f.expire { error("caller unavailable") }?.outcome)
        assertEquals(0,f.cancels);assertEquals(BrewPreparation.State.UNKNOWN,f.tracker.state)
    }
    @Test fun stalePreparationDoesNotEvaluateGuardOrCancel() {
        val f=F(ready);f.tracker.consumed();val next=requireNotNull(f.tracker.begin("new",93));check(f.tracker.written(next,OperationResult.Success(),2))
        assertNull(f.expire { error("old authorization") });assertEquals(0,f.cancels)
        assertEquals(BrewPreparation.State.WAITING_TEMP,f.tracker.state);assertTrue(f.tracker.isActive(next))
    }
    @Test fun guardReentrancyCannotTimeoutNewerRequest() {
        val f=F(ready);var next=0L
        assertNull(f.expire { f.tracker.consumed();next=requireNotNull(f.tracker.begin("new",93));false })
        assertEquals(0,f.cancels);assertTrue(f.tracker.permitsWrite(next,93))
    }
    @Test fun successfulGuardStillMustRetainOriginalPreparationToken() {
        val f=F(ready);var next=0L
        assertNull(f.expire { f.tracker.consumed();next=requireNotNull(f.tracker.begin("new",93));true })
        assertEquals(0,f.cancels);assertTrue(f.tracker.permitsWrite(next,93))
    }

}

package io.openhoyi.session

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BrewWriteDispatchPermitTest(private val cancel:Boolean) {
    companion object {@JvmStatic @Parameterized.Parameters(name="cancel={0}") fun phases()=listOf(arrayOf(false),arrayOf(true))}
    private val address="AA:BB:CC:DD:EE:01"
    private class F(cancel:Boolean) {
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=MachineWriteRecoveryState.Record(null,null)
            override fun write(record:MachineWriteRecoveryState.Record)=true
        })
        val prep=BrewPreparation();val target=if(cancel)0 else 91
        val token:Long
        val context:BrewWriteDispatchPermit.Context
        init {
            check(recovery.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,"AA:BB:CC:DD:EE:01"))
            val started=requireNotNull(prep.begin("curve",91))
            token=if(cancel){check(prep.written(started,OperationResult.Success(),0));requireNotNull(prep.beginCancel())} else started
            context=BrewWriteDispatchPermit.Context(recovery,recovery.captureOwnership(),"AA:BB:CC:DD:EE:01","AA:BB:CC:DD:EE:01",true,true,false,false,false,true)
        }
        fun allows(c:BrewWriteDispatchPermit.Context=context,t:Long=token)=BrewWriteDispatchPermit.allows(t,prep,target,c)
    }
    @Test fun originalOwnedRequestAllowed()=assertTrue(F(cancel).allows())
    @Test fun sameAddressRearmRevokesQueuedRequest() {
        val f=F(cancel);check(f.recovery.clear());check(f.recovery.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address));assertFalse(f.allows())
    }
    @Test fun missingOwnerCannotBorrowCurrentIntent() {val f=F(cancel);assertFalse(f.allows(f.context.copy(owner=null)))}
    @Test fun changedDeviceOrHubOrReadyBlocks() {
        val f=F(cancel)
        for(c in listOf(f.context.copy(originalAddress=null),f.context.copy(currentAddress=null),f.context.copy(currentAddress="AA:BB:CC:DD:EE:02"),f.context.copy(sameHub=false),f.context.copy(ready=false)))assertFalse(f.allows(c))
    }
    @Test fun manualOrPendingOrAppShotBlocks() {
        val f=F(cancel)
        for(c in listOf(f.context.copy(manualShot=true),f.context.copy(shotPending=true),f.context.copy(shotActive=true)))assertFalse(f.allows(c))
    }
    @Test fun selectedCurveChangeBlocksPrepareButAllowsCancellation() {
        val f=F(cancel);assertEquals(cancel,f.allows(f.context.copy(sameValidatedProfile=false)))
    }
    @Test fun foreignKindNeverAuthorizesPreheat() {
        val f=F(cancel);check(f.recovery.clear());check(f.recovery.arm(MachineWriteRecoveryState.Kind.SETTING,address))
        assertFalse(f.allows(f.context.copy(owner=f.recovery.captureOwnership())))
    }
    @Test fun wrongPreparationTokenNeverAuthorizes() {val f=F(cancel);assertFalse(f.allows(t=f.token+1))}
    @Test fun ownershipFromOtherStateCannotAuthorize() {
        val f=F(cancel);val other=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.BREW_WAIT,address)
            override fun write(record:MachineWriteRecoveryState.Record)=true
        });assertFalse(f.allows(f.context.copy(owner=other.captureOwnership())))
    }
    @Test fun restartRecoveryCanExplicitlyCancelWithoutCurve() {
        val f=F(cancel);f.prep.consumed();assertTrue(f.prep.restoreUnknown())
        if(cancel) {
            val token=requireNotNull(f.prep.beginCancel())
            assertTrue(BrewWriteDispatchPermit.allows(token,f.prep,0,f.context.copy(sameValidatedProfile=false)))
        } else assertFalse(f.allows())
    }
}

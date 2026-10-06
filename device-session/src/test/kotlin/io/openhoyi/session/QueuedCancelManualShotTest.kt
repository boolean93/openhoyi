package io.openhoyi.session

import io.openhoyi.protocol.CoffeeCommands
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class QueuedCancelManualShotTest(private val ready:Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="ready={0}") fun states()=listOf(arrayOf(false),arrayOf(true))
    }
    private inner class Fixture {
        val f=CoffeeSessionFixture()
        val preparation=BrewPreparation()
        val started=requireNotNull(preparation.begin("factory",92))
        var record=MachineWriteRecoveryState.Record(null,null)
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=record
            override fun write(value:MachineWriteRecoveryState.Record):Boolean { record=value;return true }
        })
        val results=mutableListOf<OperationResult>()
        var manual=false
        val before:Int
        val blocker:Triple<Long,Long,GattOperation>
        val cancel:Long
        init {
            preparation.written(started,OperationResult.Success(),0)
            if(ready)preparation.observe(1,9200)
            assertTrue(recovery.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,"AA:BB:CC:DD:EE:01"))
            f.session.setBrewWait(0,{true}) { }
            blocker=f.calls.last();before=f.calls.size
            cancel=requireNotNull(preparation.beginCancel())
            f.session.setBrewWait(0,{preparation.permitsCancelWrite(cancel,manual)}) {
                results.add(it);preparation.cancelled(cancel,it)
            }
            assertTrue(results.isEmpty());assertEquals(before,f.calls.size)
        }
        fun release(){f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())}
        fun retained(){assertTrue(recovery.pending);assertTrue(record.pending);assertEquals(MachineWriteRecoveryState.Kind.BREW_WAIT,recovery.kind)}
    }
    @Test fun manualShotAfterEnqueueRejectsCancelAndRetainsUnknownIntent() {
        val q=Fixture();q.manual=true;q.release()
        assertEquals(q.before,q.f.calls.size);assertTrue(q.results.single() is OperationResult.Failed)
        assertEquals(BrewPreparation.State.UNKNOWN,q.preparation.state);q.retained()
        q.manual=false;q.release();assertEquals(q.before,q.f.calls.size);assertEquals(1,q.results.size)
        q.f.session.disconnect()
    }
    @Test fun noManualShotAllowsExactCancellationButDoesNotClearIntent() {
        val q=Fixture();q.release();assertEquals(q.before+1,q.f.calls.size);assertTrue(q.results.isEmpty())
        val write=q.f.calls.last().third as GattOperation.Write
        assertEquals(KnownGatt.coffeeWrite,write.endpoint);assertTrue(write.withResponse)
        assertArrayEquals(CoffeeCommands.brewWait(0).frame.toByteArray(),write.bytes)
        q.f.complete();assertTrue(q.results.single() is OperationResult.Success)
        assertEquals(BrewPreparation.State.CANCEL_WRITTEN,q.preparation.state);q.retained()
        q.release();assertEquals(1,q.results.size);q.f.session.disconnect()
    }
    @Test fun replacementCancelTokenRejectsOldQueuedRequestWithoutChangingNewOwner() {
        val q=Fixture();q.preparation.disconnected();val newer=requireNotNull(q.preparation.beginCancel())
        q.release();assertEquals(q.before,q.f.calls.size);assertTrue(q.results.single() is OperationResult.Failed)
        assertEquals(BrewPreparation.State.CANCELLING,q.preparation.state)
        assertTrue(q.preparation.permitsCancelWrite(newer,false));assertFalse(q.preparation.permitsCancelWrite(q.cancel,false))
        q.retained();q.f.session.disconnect()
    }
    @Test fun manualFlagCannotPermitWrongTokenOrNonCancellingState() {
        val tracker=BrewPreparation();val first=requireNotNull(tracker.begin("factory",92))
        tracker.written(first,OperationResult.Success(),0)
        if(ready)tracker.observe(1,9200)
        assertFalse(tracker.permitsCancelWrite(first,false))
        val cancel=requireNotNull(tracker.beginCancel())
        assertFalse(tracker.permitsCancelWrite(cancel,true));assertTrue(tracker.permitsCancelWrite(cancel,false))
        assertFalse(tracker.permitsCancelWrite(cancel+1,false));tracker.consumed()
        assertFalse(tracker.permitsCancelWrite(cancel,false))
    }
}

package io.openhoyi.session

import io.openhoyi.protocol.CoffeeCommands
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class PreheatDispatchContentTest(private val target:Int) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="target={0}") fun targets()=listOf(arrayOf(0),arrayOf(92))
    }
    private inner class Queued {
        val f=CoffeeSessionFixture()
        var authorized=true
        var guardThrows=false
        var guardCalls=0
        val results=mutableListOf<OperationResult>()
        val before:Int
        val blocker:Triple<Long,Long,GattOperation>
        init {
            f.session.setBrewWait(0,{true}) { }
            before=f.calls.size;blocker=f.calls.last()
            f.session.setBrewWait(target,{
                guardCalls++
                if(guardThrows)error("host guard fault")
                authorized
            },results::add)
            assertTrue(results.isEmpty());assertEquals(before,f.calls.size);assertEquals(0,guardCalls)
        }
        fun idle(sleeping:Boolean=false,alarm:Int=0) {
            f.now=100
            val bytes=f.hex("400024BF2F1C770B00000000000000190321AF")
            bytes[8]=if(sleeping)1 else 0;bytes[12]=(alarm shr 8).toByte();bytes[13]=alarm.toByte()
            f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,bytes)
        }
        fun release(expectSend:Boolean) {
            f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
            if(expectSend) {
                assertEquals(before+1,f.calls.size);assertTrue(results.isEmpty())
                val op=f.calls.last().third as GattOperation.Write
                assertEquals(KnownGatt.coffeeWrite,op.endpoint);assertTrue(op.withResponse)
                assertArrayEquals(CoffeeCommands.brewWait(target).frame.toByteArray(),op.bytes)
                f.complete();assertTrue(results.single() is OperationResult.Success)
            } else {
                assertEquals(before,f.calls.size);assertTrue(results.single() is OperationResult.Failed)
                assertEquals("pre-dispatch guard rejected operation",(results.single() as OperationResult.Failed).reason)
            }
            val calls=f.calls.size
            f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
            assertEquals(calls,f.calls.size);assertEquals(1,results.size)
            assertEquals(DeviceState.READY,f.session.state)
            f.session.disconnect();assertEquals(1,results.size)
        }
    }
    @Test fun sleepingTelemetryPreventsQueuedWrite() {
        val q=Queued();q.idle(sleeping=true);q.release(false);assertEquals(1,q.guardCalls)
    }
    @Test fun extractionTelemetryReplacesIdleAndPreventsQueuedWrite() {
        val q=Queued();q.f.now=100
        q.f.session.onNotification(q.f.session.generation,KnownGatt.coffeeNotify,ByteArray(13).also { it[0]=0x80.toByte();it[2]=1 })
        q.release(false);assertEquals(1,q.guardCalls)
    }
    @Test fun revokedCallerAuthorizationCannotSend() {
        val q=Queued();q.authorized=false;q.release(false);assertEquals(1,q.guardCalls)
    }
    @Test fun throwingCallerAuthorizationCannotSend() {
        val q=Queued();q.guardThrows=true;q.release(false);assertEquals(1,q.guardCalls)
    }
    @Test fun alarmBlocksNewPreheatButAllowsFreshAwakeRecoveryCancel() {
        val q=Queued();q.idle(alarm=1);q.release(target==0);assertEquals(1,q.guardCalls)
    }
    @Test fun cafeModeBlocksNewPreheatButNotRecoveryCancel() {
        val q=Queued();q.f.now=100
        val bytes=q.f.hex("830113FD5C007D0F350019006E");bytes[3]=(bytes[3].toInt() and 0x04.inv()).toByte()
        q.f.session.onNotification(q.f.session.generation,KnownGatt.coffeeNotify,bytes)
        q.release(target==0);assertEquals(1,q.guardCalls)
    }
    @Test fun expiredSettingsBlockNewPreheatButFreshIdleStillAllowsCancel() {
        val q=Queued();q.f.idle(SettingsFreshness.MAX_AGE_MS+1)
        q.release(target==0);assertEquals(1,q.guardCalls)
    }
    @Test fun settingsRemainEligibleAtInclusiveAgeBoundary() {
        val q=Queued();q.f.idle(SettingsFreshness.MAX_AGE_MS)
        q.release(true);assertEquals(1,q.guardCalls)
    }
    @Test fun disconnectCancelsQueuedRequestWithoutCallingItsGuard() {
        val q=Queued();q.f.session.disconnect()
        assertEquals(q.before,q.f.calls.size);assertTrue(q.results.single() is OperationResult.Cancelled)
        assertEquals(0,q.guardCalls);assertEquals(DeviceState.DISCONNECTED,q.f.session.state)
        q.f.session.onComplete(q.blocker.first,q.blocker.second,OperationResult.Success())
        assertEquals(q.before,q.f.calls.size);assertEquals(1,q.results.size)
    }
}

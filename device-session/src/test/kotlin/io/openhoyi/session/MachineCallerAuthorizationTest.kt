package io.openhoyi.session

import io.openhoyi.protocol.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

internal class CallerAuthorizationFixture {
    val f=CoffeeSessionFixture()
    var authorized=true
    var guardThrows=false
    var guardCalls=0
    val results=mutableListOf<OperationResult>()
    val guard:()->Boolean={ guardCalls++;if(guardThrows)error("host authorization fault");authorized }
    val expected:WeeklySleepSchedule
    val target:WeeklySleepSchedule
    init {
        val first=f.hex("8340FE0A00071E0A00071E0A00071E0A00071E3D")
        val second=f.hex("83800A00071E0A00071E0A00071E10")
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,first)
        f.session.onNotification(f.session.generation,KnownGatt.coffeeNotify,second)
        expected=requireNotNull(WeeklySleepSchedule.fromReadback(
            (HoyiCodec.decode(first) as DecodeResult.Valid).value as SleepPart,
            (HoyiCodec.decode(second) as DecodeResult.Valid).value as SleepPart))
        target=WeeklySleepSchedule(expected.days.mapIndexed { i,d->if(i==0)d.copy(enabled=!d.enabled) else d })
    }
    fun assertFrame(command:EncodedCommand) {
        val op=f.calls.last().third as GattOperation.Write
        assertEquals(KnownGatt.coffeeWrite,op.endpoint);assertTrue(op.withResponse)
        assertArrayEquals(command.frame.toByteArray(),op.bytes)
    }
}

@RunWith(Parameterized::class)
class MachineCallerAuthorizationTest(private val control:String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun controls()=
            listOf("SETTING","CUPS","SLEEP","SCHEDULE").map { arrayOf(it) }
    }
    private fun submit(q:CallerAuthorizationFixture) {
        when(control) {
            "SETTING"->q.f.session.writeSetting(MachineSettingChange.BrewTemperature(93),q.guard,q.results::add)
            "CUPS"->q.f.session.resetCupCount(25,q.guard,q.results::add)
            "SLEEP"->q.f.session.enterSleep(q.guard,q.results::add)
            "SCHEDULE"->q.f.session.writeSleepSchedule(q.target,q.expected,q.guard,q.results::add)
        }
    }
    private fun command(q:CallerAuthorizationFixture)=when(control) {
        "SETTING"->CoffeeCommands.setting(MachineSettingChange.BrewTemperature(93))
        "CUPS"->CoffeeCommands.resetCupCount()
        "SLEEP"->CoffeeCommands.sleepNow()
        else->CoffeeCommands.sleepSchedule(q.target)[0]
    }
    private fun run(allowed:Boolean,throws:Boolean=false) {
        val q=CallerAuthorizationFixture()
        q.f.session.setBrewWait(0,{true}) { }
        val blocker=q.f.calls.last();val before=q.f.calls.size
        submit(q)
        assertTrue(q.results.isEmpty());assertEquals(before,q.f.calls.size);assertEquals(0,q.guardCalls)
        q.authorized=allowed;q.guardThrows=throws
        q.f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
        if(allowed && !throws) {
            assertEquals(before+1,q.f.calls.size);assertEquals(1,q.guardCalls)
            assertTrue(q.results.isEmpty());q.assertFrame(command(q));q.f.complete()
            if(control=="SCHEDULE") {
                assertTrue(q.results.isEmpty());q.f.now=500;q.f.session.tick()
                assertEquals(before+2,q.f.calls.size);assertEquals(2,q.guardCalls)
                q.assertFrame(CoffeeCommands.sleepSchedule(q.target)[1]);q.f.complete()
            }
            assertTrue(q.results.single() is OperationResult.Success)
        } else {
            assertEquals(before,q.f.calls.size);assertEquals(1,q.guardCalls)
            assertEquals(OperationResult.Failed("pre-dispatch guard rejected operation"),q.results.single())
        }
        val after=q.f.calls.size;val guards=q.guardCalls
        q.authorized=true;q.guardThrows=false
        q.f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
        q.f.now=1000;q.f.session.tick()
        assertEquals(after,q.f.calls.size);assertEquals(guards,q.guardCalls);assertEquals(1,q.results.size)
        q.f.session.disconnect();assertEquals(1,q.results.size)
    }
    @Test fun authorizedQueuedWriteKeepsOriginalWireBytes()=run(true)
    @Test fun authorizationRevokedWhileQueuedPreventsTransport()=run(false)
    @Test fun throwingAuthorizationPreventsTransport()=run(false,true)
}

class WeeklyCallerAuthorizationTest {
    private fun rejectSecond(throws:Boolean,queued:Boolean) {
        val q=CallerAuthorizationFixture()
        q.f.session.writeSleepSchedule(q.target,q.expected,q.guard,q.results::add)
        q.assertFrame(CoffeeCommands.sleepSchedule(q.target)[0]);q.f.complete()
        assertTrue(q.results.isEmpty());assertEquals(1,q.guardCalls)
        // Optional unrelated lifecycle barrier holds the second frame at the real queue boundary.
        if(queued) {
            val queue=DeviceSession::class.java.getDeclaredField("queue").apply { isAccessible=true }.get(q.f.session) as GattQueue
            queue.enqueue(GattOperation.Discover,1000) { }
        }
        val blocker=q.f.calls.last();val before=q.f.calls.size
        q.f.now=500
        if(!queued){q.authorized=false;q.guardThrows=throws}
        q.f.session.tick()
        if(queued) {
            assertEquals(1,q.guardCalls);assertTrue(q.results.isEmpty());assertEquals(before,q.f.calls.size)
            q.authorized=false;q.guardThrows=throws
            q.f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
        }
        assertEquals(before,q.f.calls.size);assertEquals(2,q.guardCalls)
        assertEquals(OperationResult.Unknown("weekly sleep schedule may be partially applied"),q.results.single())
        q.authorized=true;q.guardThrows=false
        q.f.session.onComplete(blocker.first,blocker.second,OperationResult.Success())
        q.f.now=1000;q.f.session.tick();assertEquals(before,q.f.calls.size);assertEquals(1,q.results.size)
        q.f.session.disconnect();assertEquals(1,q.results.size)
    }
    @Test fun revokedAuthorizationAfterFirstFrameRetainsPartialUnknown()=rejectSecond(false,false)
    @Test fun throwingAuthorizationAfterFirstFrameRetainsPartialUnknown()=rejectSecond(true,false)
    @Test fun revokedAuthorizationWhileSecondFrameQueuedRetainsPartialUnknown()=rejectSecond(false,true)
    @Test fun throwingAuthorizationWhileSecondFrameQueuedRetainsPartialUnknown()=rejectSecond(true,true)
}

package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/** Synthetic transport only, not physical device validation. */
class StaleSettingsRefreshTest {
    private fun hex(s:String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    private class Driver:GattDriver {
        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
            calls+=Triple(generation,token,operation);return true
        }
        override fun close(generation:Long) {}
        val writes get()=calls.mapNotNull {it.third as? GattOperation.Write}
    }
    private inner class Fixture {
        val driver=Driver();var now=0L
        val wall=LocalDateTime.of(2026,11,14,12,0)
        val session=DeviceSession(DeviceRole.COFFEE,driver,{now},wallClock={wall})
        val settings=hex("830113FD5C007D0F350019006E")
        val results=mutableListOf<OperationResult>()
        init {connect()}
        fun connect(address:String="coffee") {
            session.connect(address,CoffeeAuthentication(wall,"123456"))
            complete();complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete();receive(settings);idle()
            assertEquals(DeviceState.READY,session.state)
        }
        fun complete(result:OperationResult=OperationResult.Success()) {
            val(g,t,_)=driver.calls.last();session.onComplete(g,t,result)
        }
        fun receive(bytes:ByteArray)=session.onNotification(session.generation,KnownGatt.coffeeNotify,bytes)
        fun idle(sleep:Int=0,alarms:Int=0) {
            val b=hex("400024BF2F1C770B00000000000000190321AF")
            b[8]=sleep.toByte();b[12]=(alarms shr 8).toByte();b[13]=alarms.toByte();receive(b)
        }
        fun stale() {now=180_001;idle()}
        fun refresh(guard:()->Boolean={true})=session.refreshStaleSettings(guard){results+=it}
    }
    @Test fun freshDoesNotWriteAndStaleUsesOneAuthenticationOnly() {
        val f=Fixture();val before=f.driver.writes.size
        f.refresh();assertEquals(before,f.driver.writes.size);assertTrue(f.results.single() is OperationResult.Success)
        f.results.clear();f.stale();f.refresh()
        assertTrue(f.session.staleSettingsRefreshBusy)
        assertArrayEquals(CoffeeCommands.authenticate(f.wall,"123456").frame.toByteArray(),f.driver.writes.last().bytes)
        f.complete();assertTrue(f.results.isEmpty());f.receive(f.settings)
        assertTrue(f.results.single() is OperationResult.Success);assertFalse(f.session.staleSettingsRefreshBusy)
        f.session.tick();assertEquals(before+1,f.driver.writes.size)
    }
    @Test fun earlyNotificationWaitsForGattAndLateCallbacksCannotCompleteTwice() {
        val f=Fixture();f.stale();f.refresh();f.receive(f.settings)
        assertTrue(f.results.isEmpty());f.complete();f.complete();f.receive(f.settings);f.session.tick()
        assertEquals(1,f.results.size);assertTrue(f.results.single() is OperationResult.Success)
    }
    @Test fun singleFlightAndExpiredReadbackAreBounded() {
        val f=Fixture();f.stale();f.refresh();val before=f.driver.writes.size
        f.refresh();assertEquals(before,f.driver.writes.size);assertTrue(f.results.single() is OperationResult.Failed)
        f.results.clear();f.complete();f.now+=5000;f.idle();f.receive(f.settings);f.session.tick()
        assertEquals(1,f.results.size);assertTrue(f.results.single() is OperationResult.Failed)
        assertFalse(f.session.staleSettingsRefreshBusy)
    }
    @Test fun unsafeStatesAndCallerProtectionNeverDispatch() {
        for(kind in 0..3) {
            val f=Fixture();f.stale();val before=f.driver.writes.size
            when(kind) {0->f.idle(sleep=1);1->f.idle(alarms=1);2->f.now+=1501}
            f.refresh {kind!=3}
            assertTrue("case $kind",f.results.single() is OperationResult.Failed)
            assertEquals(before,f.driver.writes.size)
        }
        val advisory=Fixture();advisory.stale();advisory.idle(alarms=0x4000);advisory.refresh()
        assertTrue(advisory.session.staleSettingsRefreshBusy)
    }
    @Test fun changingGuardOrSleepInvalidatesRatherThanReplaying() {
        for(sleep in listOf(false,true)) {
            val f=Fixture();f.stale();var permitted=true;f.refresh {permitted};f.complete()
            if(sleep)f.idle(sleep=1) else {permitted=false;f.session.tick()}
            f.receive(f.settings);f.complete();assertEquals(1,f.results.size)
            assertTrue(f.results.single() is OperationResult.Failed)
        }
    }
    @Test fun connectionReplacementSettlesOldRequestOnce() {
        val f=Fixture();f.stale();f.refresh();val old=f.driver.calls.last()
        f.connect("replacement");f.session.onComplete(old.first,old.second,OperationResult.Success());f.receive(f.settings)
        assertEquals(1,f.results.size);assertTrue(f.results.single() is OperationResult.Failed)
        assertFalse(f.session.staleSettingsRefreshBusy)
    }
    @Test fun normalControlsCannotInterleaveWithFreshEarlyResponse() {
        val f=Fixture();f.stale();f.refresh();f.receive(f.settings)
        var blocked:OperationResult?=null
        f.session.writeSetting(MachineSettingChange.BrewHeating(false)){blocked=it}
        assertTrue(blocked is OperationResult.Failed)
        f.complete();assertTrue(f.results.single() is OperationResult.Success)
    }
    @Test fun extractionCancelsOutstandingRefreshAndPreventsNewOne() {
        val f=Fixture();f.stale();f.refresh();f.complete()
        f.receive(ByteArray(13).also {it[0]=0x80.toByte()})
        assertTrue(f.results.single() is OperationResult.Failed)
        val count=f.driver.writes.size;f.results.clear();f.refresh()
        assertTrue(f.results.single() is OperationResult.Failed);assertEquals(count,f.driver.writes.size)
    }
    @Test fun transportFailureNeverAcceptsEarlierSettingsAsSuccess() {
        val f=Fixture();f.stale();f.refresh();f.receive(f.settings)
        f.complete(OperationResult.Failed("rejected"));f.receive(f.settings);f.session.tick()
        assertEquals(1,f.results.size);assertTrue(f.results.single() is OperationResult.Failed)
    }
}

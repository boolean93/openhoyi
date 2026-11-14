package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/** Synthetic transport only: no hardware/time-sync acceptance is implied. */
class BoilerSettingsRefreshTest {
    private class Driver:GattDriver {
        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
            calls+=Triple(generation,token,operation);return true
        }
        override fun close(generation:Long) { }
        val writes get()=calls.mapNotNull {it.third as? GattOperation.Write}
    }
    private inner class Fixture {
        val driver=Driver()
        var now=0L
        var wallTime=LocalDateTime.of(2026,11,11,10,20,30)
        val tracker=SettingsWriteTracker()
        var serial=0L
        val session:DeviceSession=DeviceSession(DeviceRole.COFFEE,driver,{now},stateChanged={state->
            if(state!=DeviceState.READY)tracker.disconnected(serial)
        },coffeeFrame={frame,_->
            if(frame is Settings)tracker.observe(++serial,frame,session.boilerReconciliationReady)
        },wallClock={wallTime})
        val initial=hex("830113FD5C007D0F350019006E")
        init { connect() }
        fun connect(address:String="device") {
            session.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            finishConnect(initial)
        }
        fun finishConnect(settings:ByteArray) {
            complete()
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            receive(settings)
            session.onNotification(session.generation,KnownGatt.coffeeNotify,
                hex("400024BF2F1C770B00000000000000190321AF"))
            assertEquals(DeviceState.READY,session.state)
        }
        fun advance(time:Long) {
            now=time
            session.onNotification(session.generation,KnownGatt.coffeeNotify,
                hex("400024BF2F1C770B00000000000000190321AF"))
            session.tick()
        }
        fun complete(result:OperationResult=OperationResult.Success()) {
            val(g,t,_)=driver.calls.last();session.onComplete(g,t,result)
        }
        fun receive(bytes:ByteArray)=session.onNotification(session.generation,KnownGatt.coffeeNotify,bytes)
        fun disabled(change:MachineSettingChange):ByteArray=initial.copyOf().also {
            it[3]=(it[3].toInt() and (if(change is MachineSettingChange.BrewHeating)0x20 else 0x10).inv()).toByte()
            it[it.lastIndex]=it.dropLast(1).fold(0){a,v->a xor (v.toInt() and 255)}.toByte()
        }
        fun begin(change:MachineSettingChange,refresh:Boolean=true):Long {
            val token=requireNotNull(tracker.begin(change))
            session.writeSetting(change,{tracker.permitsWrite(token)}) { result->
                tracker.written(token,result,serial)
                if(refresh && result is OperationResult.Success)
                    session.refreshBoilerSettings({tracker.permitsBoilerRefresh(token)}) {_,_->}
            }
            return token
        }
    }
    private fun hex(value:String)=value.chunked(2).map {it.toInt(16).toByte()}.toByteArray()

    @Test fun leverModesRequestBoundedReadbackWithoutControlReplayOrReconnect() {
        for(change in listOf(MachineSettingChange.LeverMode(false,false),
            MachineSettingChange.LeverMode(true,false),MachineSettingChange.LeverMode(true,true))) {
            val f=Fixture();val before=f.driver.writes.size
            f.begin(change);f.complete();f.advance(250);f.complete();f.advance(1250)
            assertEquals(before+3,f.driver.writes.size)
            f.complete();f.advance(3000)
            assertFalse(f.session.canReconnectBoilerSettings)
            assertFalse(f.session.boilerReconciliationReady)
            assertFalse(f.session.reconnectBoilerSettings({true},{true}))
            val matched=f.initial.copyOf().also {
                it[3]=((it[3].toInt() and 0x3f) or (if(change.pressure)0x80 else 0) or
                    (if(change.flow)0x40 else 0)).toByte()
                it[it.lastIndex]=it.dropLast(1).fold(0){a,v->a xor (v.toInt() and 255)}.toByte()
            }
            f.receive(matched)
            assertEquals(SettingsWriteTracker.State.CONFIRMED,f.tracker.state)
            f.advance(18000);assertEquals(before+3,f.driver.writes.size)
            assertEquals(1,f.driver.writes.count {it.bytes.contentEquals(CoffeeCommands.setting(change).frame.toByteArray())})
        }
    }

    @Test fun leverMissingSettingsStaysUnknownAndManualRecoveryIsSingleSync() {
        val f=Fixture();val change=MachineSettingChange.LeverMode(false,false)
        val token=f.begin(change);f.complete();f.advance(250);f.complete();f.advance(1250);f.complete()
        f.advance(6000);assertTrue(f.tracker.timeout(token,f.serial))
        f.advance(181000);val count=f.driver.writes.size
        assertFalse(f.session.canReconnectBoilerSettings)
        assertTrue(f.session.canRefreshUnknownBoilerSettings)
        f.session.refreshUnknownBoilerSettings({f.tracker.permitsBoilerRecovery(token)}) {_,_->}
        f.session.refreshUnknownBoilerSettings({true}) {_,result->assertTrue(result is OperationResult.Failed)}
        f.advance(181250);f.complete();f.advance(187000)
        assertEquals(count+1,f.driver.writes.size)
        assertEquals(SettingsWriteTracker.State.UNKNOWN,f.tracker.state)
        f.session.disconnect();assertFalse(f.session.canRefreshUnknownBoilerSettings)
    }

    @Test fun successfulGattWithoutFirstReadbackUsesSecondSyncButNeverRepeatsControl() {
        for(change in listOf(MachineSettingChange.BrewHeating(false),MachineSettingChange.SteamHeating(false))) {
            val f=Fixture();val before=f.driver.writes.size
            f.begin(change);f.complete()
            assertEquals(before+1,f.driver.writes.size)
            f.advance(249);assertEquals(before+1,f.driver.writes.size)
            f.advance(250);assertEquals(before+2,f.driver.writes.size)
            assertArrayEquals(CoffeeCommands.authenticate(f.wallTime,"123456").frame.toByteArray(),f.driver.writes.last().bytes)
            f.complete() // All GATT callbacks succeed, but this first sync gets no 0x83.
            assertEquals(SettingsWriteTracker.State.WAITING_READBACK,f.tracker.state)
            f.advance(1250);assertEquals(before+3,f.driver.writes.size)
            f.receive(f.disabled(change)) // Response may beat the second sync's GATT callback.
            assertEquals(SettingsWriteTracker.State.CONFIRMED,f.tracker.state)
            f.complete();f.advance(3000);f.advance(6000)
            assertEquals(before+3,f.driver.writes.size)
            assertEquals(1,f.driver.writes.count {it.bytes.contentEquals(CoffeeCommands.setting(change).frame.toByteArray())})
        }
    }

    @Test fun earlyOriginalNotificationAndFirstSyncConfirmationCancelLateTimers() {
        val f=Fixture();val change=MachineSettingChange.BrewHeating(false)
        f.begin(change);f.receive(f.disabled(change))
        assertEquals(SettingsWriteTracker.State.WRITING,f.tracker.state)
        f.complete();assertEquals(SettingsWriteTracker.State.WAITING_READBACK,f.tracker.state)
        f.advance(250);f.receive(f.disabled(change));f.complete()
        val writes=f.driver.writes.size
        f.advance(1250);f.advance(3000);assertEquals(writes,f.driver.writes.size)
        assertEquals(SettingsWriteTracker.State.CONFIRMED,f.tracker.state)
    }

    @Test fun onlyPostSyncDifferentReportClosesTypedBoilerAndStopsFurtherRecovery() {
        val f=Fixture();f.begin(MachineSettingChange.BrewHeating(false));f.complete()
        f.receive(f.initial) // Unrelated pre-sync report must not declare failure.
        assertEquals(SettingsWriteTracker.State.WAITING_READBACK,f.tracker.state)
        f.advance(250);f.complete();f.receive(f.initial)
        assertEquals(SettingsWriteTracker.State.RECONCILED,f.tracker.state)
        val writes=f.driver.writes.size
        f.advance(1250);f.advance(3000)
        assertEquals(writes,f.driver.writes.size);assertFalse(f.session.canReconnectBoilerSettings)
    }

    @Test fun failedWritesNeverArmAndTwoFailedSyncsKeepOriginalUnknownDeadline() {
        for(result in listOf(OperationResult.Failed("rejected"),OperationResult.Unknown("lost"))) {
            val f=Fixture();f.begin(MachineSettingChange.BrewHeating(false));val count=f.driver.writes.size
            f.complete(result);f.advance(250);f.advance(1250)
            assertEquals(count,f.driver.writes.size)
        }
        val f=Fixture();val token=f.begin(MachineSettingChange.SteamHeating(false));f.complete()
        f.advance(250);f.complete(OperationResult.Unknown("no sync response"))
        f.advance(1250);f.complete(OperationResult.Failed("sync rejected"))
        val writes=f.driver.writes.size
        f.advance(6000);assertTrue(f.tracker.timeout(token,f.serial))
        assertEquals(SettingsWriteTracker.State.UNKNOWN,f.tracker.state)
        f.advance(9000);assertEquals(writes,f.driver.writes.size)
        assertFalse(f.session.canReconnectBoilerSettings)
    }

    @Test fun queuedPermissionChangeAndConnectionReplacementCancelOldSynchronization() {
        val f=Fixture();f.begin(MachineSettingChange.BrewHeating(false),refresh=false);f.complete()
        f.session.setBrewWait(92,{true}) { }
        var allowed=true
        f.session.refreshBoilerSettings({allowed}) {_,_->}
        f.advance(250);val count=f.driver.writes.size
        allowed=false;f.complete();f.advance(1250)
        assertEquals(count,f.driver.writes.size)
        val replacement=Fixture();replacement.begin(MachineSettingChange.SteamHeating(false));replacement.complete()
        replacement.advance(250);val old=replacement.driver.calls.last()
        replacement.connect("replacement");val writes=replacement.driver.writes.size
        replacement.session.onComplete(old.first,old.second,OperationResult.Success())
        replacement.advance(1250);replacement.advance(3000)
        assertEquals(writes,replacement.driver.writes.size)
        assertFalse(replacement.session.canRefreshUnknownBoilerSettings)
    }

    @Test fun staleIdleWaitsForNewReportWithoutBusyLoopOrExtraAttempt() {
        val f=Fixture();f.begin(MachineSettingChange.BrewHeating(false));f.complete()
        f.now=2000;val count=f.driver.writes.size
        repeat(5){f.session.tick()}
        assertEquals(count,f.driver.writes.size)
        f.advance(2100);f.complete();f.advance(2350);f.complete()
        assertEquals(count+2,f.driver.writes.size)
        f.advance(3000);assertEquals(count+2,f.driver.writes.size)
    }

    @Test fun oneReconnectUsesFreshAuthenticationNeverReplaysControlAndHasAbsolute18sCap() {
        val f=Fixture();val change=MachineSettingChange.BrewHeating(false)
        f.begin(change);f.complete();f.advance(250);f.complete();f.advance(1250);f.complete();f.advance(3000)
        assertTrue(f.session.canReconnectBoilerSettings)
        assertTrue(f.session.reconnectBoilerSettings({true},{true}))
        assertEquals(SettingsWriteTracker.State.UNKNOWN,f.tracker.state)
        assertFalse(f.session.reconnectBoilerSettings({true},{true}))
        val count=f.driver.writes.size
        f.now=17999;f.session.tick();assertEquals(DeviceState.CONNECTING,f.session.state)
        f.now=18000;f.session.tick();assertEquals(DeviceState.FAILED,f.session.state)
        assertEquals(count,f.driver.writes.size)
        assertEquals(SettingsWriteTracker.State.UNKNOWN,f.tracker.state)
    }

    @Test fun reconnectReadbackConfirmsOriginalAndDeadlineCannotTouchNewUserConnection() {
        val f=Fixture();val change=MachineSettingChange.SteamHeating(false)
        f.begin(change);f.complete();f.advance(250);f.complete();f.advance(1250);f.complete();f.advance(3000)
        assertTrue(f.session.reconnectBoilerSettings({true},{true}))
        f.finishConnect(f.disabled(change))
        assertEquals(SettingsWriteTracker.State.CONFIRMED,f.tracker.state)
        assertEquals(1,f.driver.writes.count {it.bytes.contentEquals(CoffeeCommands.setting(change).frame.toByteArray())})
        f.connect("user-selected");val count=f.driver.calls.size
        f.now=18000;f.session.tick()
        assertEquals(DeviceState.READY,f.session.state);assertEquals("user-selected",f.session.address)
        assertEquals(count,f.driver.calls.size)
    }

    @Test fun manualRecoveryIsSingleSyncAfterSettingsExpiryWithDebounceAndNoControl() {
        val f=Fixture();val token=f.begin(MachineSettingChange.BrewHeating(false));f.complete()
        f.advance(6000);assertTrue(f.tracker.timeout(token,f.serial))
        f.advance(181000)
        assertTrue(f.session.canRefreshUnknownBoilerSettings)
        val count=f.driver.writes.size
        f.session.refreshUnknownBoilerSettings({f.tracker.permitsBoilerRecovery(token)}) {_,_->}
        assertTrue(f.session.boilerSettingsRecoveryBusy)
        f.session.refreshUnknownBoilerSettings({true}) {_,result->assertTrue(result is OperationResult.Failed)}
        f.advance(181250);f.complete()
        assertEquals(count+1,f.driver.writes.size)
        assertEquals(SettingsWriteTracker.State.UNKNOWN,f.tracker.state)
        f.advance(187000);assertFalse(f.session.boilerSettingsRecoveryBusy)
        assertEquals(count+1,f.driver.writes.size)
        f.session.disconnect();assertFalse(f.session.canRefreshUnknownBoilerSettings)
    }
}

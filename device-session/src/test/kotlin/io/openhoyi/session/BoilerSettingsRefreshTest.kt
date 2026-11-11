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
        val session=DeviceSession(DeviceRole.COFFEE,driver,{now},coffeeFrame={frame,_->
            if(frame is Settings)tracker.observe(++serial,frame)
        },wallClock={wallTime})
        val initial=hex("830113FD5C007D0F350019006E")
        init { connect() }
        fun connect(address:String="device") {
            session.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,9,20,12,0),"123456"))
            complete()
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            receive(initial)
            session.onNotification(session.generation,KnownGatt.coffeeNotify,
                hex("400024BF2F1C770B00000000000000190321AF"))
            assertEquals(DeviceState.READY,session.state)
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
                    session.refreshBoilerSettings({tracker.permitsBoilerRefresh(token)}) { }
            }
            return token
        }
    }
    private fun hex(value:String)=value.chunked(2).map {it.toInt(16).toByte()}.toByteArray()

    @Test fun bothBoilersRequestExactlyOneCurrentTimeRefreshAndStillNeedNewReadback() {
        for(change in listOf(MachineSettingChange.BrewHeating(false),MachineSettingChange.SteamHeating(false))) {
            val f=Fixture();val before=f.driver.writes.size
            f.begin(change)
            assertArrayEquals(CoffeeCommands.setting(change).frame.toByteArray(),f.driver.writes.last().bytes)
            f.complete()
            assertEquals(SettingsWriteTracker.State.WAITING_READBACK,f.tracker.state)
            assertEquals(before+2,f.driver.writes.size)
            assertArrayEquals(CoffeeCommands.authenticate(f.wallTime,"123456").frame.toByteArray(),f.driver.writes.last().bytes)
            f.complete() // Refresh transport success is not settings confirmation.
            assertEquals(SettingsWriteTracker.State.WAITING_READBACK,f.tracker.state)
            f.receive(f.disabled(change))
            assertEquals(SettingsWriteTracker.State.CONFIRMED,f.tracker.state)
            var reentry:OperationResult?=null
            f.session.refreshBoilerSettings({true}) {reentry=it}
            assertTrue(reentry is OperationResult.Failed)
            repeat(3){f.now+=100;f.session.tick()}
            assertEquals(before+2,f.driver.writes.size)
        }
    }

    @Test fun earlySettingNotificationCannotStrandTheWriteOrConfirmBeforeTransport() {
        val f=Fixture();val change=MachineSettingChange.BrewHeating(false)
        f.begin(change)
        f.receive(f.disabled(change)) // Notify may beat the original GATT completion.
        assertEquals(SettingsWriteTracker.State.WRITING,f.tracker.state)
        f.complete()
        assertEquals(SettingsWriteTracker.State.WAITING_READBACK,f.tracker.state)
        f.receive(f.disabled(change)) // Fresh refresh response may beat its GATT completion.
        assertEquals(SettingsWriteTracker.State.CONFIRMED,f.tracker.state)
        f.complete()
        assertEquals(SettingsWriteTracker.State.CONFIRMED,f.tracker.state)
    }

    @Test fun failedUnknownOrNonBoilerWritesCannotRequestRefreshAndMissingReadbackStaysUnknown() {
        for(result in listOf(OperationResult.Failed("rejected"),OperationResult.Unknown("lost"),OperationResult.Cancelled("unsent"))) {
            val f=Fixture();f.begin(MachineSettingChange.BrewHeating(false));val count=f.driver.writes.size
            f.complete(result)
            f.session.refreshBoilerSettings({true}) { assertTrue(it is OperationResult.Failed) }
            assertEquals(count,f.driver.writes.size)
        }
        val ordinary=Fixture();ordinary.begin(MachineSettingChange.Light(false));val count=ordinary.driver.writes.size
        ordinary.complete();assertEquals(count,ordinary.driver.writes.size)
        val f=Fixture();val token=f.begin(MachineSettingChange.SteamHeating(false));f.complete()
        f.complete(OperationResult.Unknown("refresh failed"))
        assertEquals(SettingsWriteTracker.State.WAITING_READBACK,f.tracker.state)
        assertTrue(f.tracker.timeout(token,f.serial))
        assertEquals(SettingsWriteTracker.State.UNKNOWN,f.tracker.state)
        val writes=f.driver.writes.size
        repeat(3){f.now+=100;f.session.tick()}
        assertEquals(writes,f.driver.writes.size)
        assertFalse(f.tracker.permitsBoilerRefresh(token))
    }

    @Test fun queuedRefreshRechecksPermissionAndOldConnectionCannotRefreshReplacement() {
        val f=Fixture();f.begin(MachineSettingChange.BrewHeating(false),refresh=false);f.complete()
        f.session.setBrewWait(92,{true}) { }
        var allowed=true;var refreshResult:OperationResult?=null
        f.session.refreshBoilerSettings({allowed}) {refreshResult=it}
        val count=f.driver.writes.size
        allowed=false;f.complete()
        assertTrue(refreshResult is OperationResult.Failed)
        assertEquals(count,f.driver.writes.size)
        f.session.refreshBoilerSettings({true}) {assertTrue(it is OperationResult.Failed)}
        assertEquals(count,f.driver.writes.size)

        val switched=Fixture();switched.begin(MachineSettingChange.SteamHeating(false),refresh=false);switched.complete()
        switched.session.setBrewWait(92,{true}) { }
        val old=switched.driver.calls.last()
        var cancelled:OperationResult?=null
        switched.session.refreshBoilerSettings({true}) {cancelled=it}
        switched.connect("replacement")
        assertTrue(cancelled is OperationResult.Cancelled)
        val writes=switched.driver.writes.size
        switched.session.onComplete(old.first,old.second,OperationResult.Success())
        switched.session.refreshBoilerSettings({true}) {assertTrue(it is OperationResult.Failed)}
        assertEquals(writes,switched.driver.writes.size)
    }

    @Test fun refreshPermitKeepsDurableIdentityTokenAndProductSafetyGates() {
        val tracker=SettingsWriteTracker();val token=requireNotNull(tracker.begin(MachineSettingChange.BrewHeating(false)))
        val recovery=MachineWriteRecoveryState(object:MachineWriteRecoveryState.Storage {
            override fun read()=MachineWriteRecoveryState.Record(null,null)
            override fun write(record:MachineWriteRecoveryState.Record)=true
        })
        val address="AA:BB:CC:DD:EE:01";check(recovery.arm(MachineWriteRecoveryState.Kind.SETTING,address))
        val context=MachineWriteDispatchPermit.Context(recovery,address,address,false,false,false,false)
        val request=MachineWriteDispatchPermit.Request.BoilerSettingsRefresh(tracker)
        fun allowed(c:MachineWriteDispatchPermit.Context=context,t:Long=token)=MachineWriteDispatchPermit.allows(t,request,c)
        assertFalse(allowed())
        tracker.written(token,OperationResult.Success(),0)
        assertTrue(allowed());assertFalse(tracker.permitsWrite(token));assertFalse(allowed(t=token+1))
        for(block in listOf(context.copy(currentAddress="other"),context.copy(manualShotActive=true),
            context.copy(shotRecoveryPending=true),context.copy(shotActive=true),context.copy(preparationActive=true)))
            assertFalse(allowed(block))
        check(recovery.clear());assertFalse(allowed())
        tracker.timeout(token,0);assertFalse(allowed())
    }
}

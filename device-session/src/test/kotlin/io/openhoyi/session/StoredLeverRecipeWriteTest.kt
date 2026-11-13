package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/** Synthetic transport evidence only; no firmware persistence or real readback is implied. */
class StoredLeverRecipeWriteTest {
    private fun bytes(value:String)=value.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    private val recipe=StartParameters(false,false,2,7,92,70,false,0,30,90,0,0,150,0,400,0,0)
    private class Driver:GattDriver {
        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
            calls+=Triple(generation,token,operation);return true
        }
        override fun close(generation:Long) {}
        val recipeWrites get()=calls.mapNotNull {it.third as? GattOperation.Write}
            .filter {it.bytes.firstOrNull()?.toInt()==8}
    }
    private inner class Fixture(firmware:Int=0x13) {
        val driver=Driver();var now=0L
        val session=DeviceSession(DeviceRole.COFFEE,driver,{now})
        init { connect(firmware) }
        fun connect(firmware:Int=0x13,address:String="coffee") {
            session.connect(address,CoffeeAuthentication(LocalDateTime.of(2026,11,12,10,0),"123456"))
            complete()
            complete(OperationResult.Success(listOf(
                CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
            complete();complete()
            val settings=bytes("830113FD5C007D0F350019006E")
            settings[2]=firmware.toByte();settings[settings.lastIndex]=settings.dropLast(1).fold(0){a,b->a xor (b.toInt() and 255)}.toByte()
            receive(settings);idle()
            assertEquals(if(firmware in 0x13..0x15) DeviceState.READY else DeviceState.UNSUPPORTED,session.state)
        }
        fun complete(result:OperationResult=OperationResult.Success()) {
            val (g,t,_)=driver.calls.last();session.onComplete(g,t,result)
        }
        fun receive(frame:ByteArray)=session.onNotification(session.generation,KnownGatt.coffeeNotify,frame)
        fun idle(alarm:Boolean=false) {
            val frame=bytes("400024BF2F1C770B00000000000000190321AF")
            if(alarm)frame[13]=0x01
            receive(frame)
        }
        fun write(guard:()->Boolean={true},done:(OperationResult)->Unit)=session.writeStoredLeverRecipe(recipe,guard,done)
    }
    @Test fun supportedFirmwareAcceptsUnselectedCustomRecipeAndSuccessOnlyReportsTransport() {
        for(firmware in listOf(0x13,0x14,0x15)) {
            val f=Fixture(firmware);var result:OperationResult?=null
            f.write {result=it}
            assertEquals(1,f.driver.recipeWrites.size)
            assertArrayEquals(CoffeeCommands.storedLeverRecipe(recipe).frame.toByteArray(),f.driver.recipeWrites.single().bytes)
            assertNull(result)
            f.complete();assertTrue(result is OperationResult.Success)
            f.now=10000;f.session.tick()
            assertEquals(1,f.driver.recipeWrites.size) // no readback refresh or resend
        }
    }
    @Test fun unsupportedFirmwareStaleAndBlockingAlarmNeverWrite() {
        var result:OperationResult?=null
        for(firmware in listOf(0x12,0x16)) {
            val unsupported=Fixture(firmware)
            unsupported.write {result=it};assertTrue(result is OperationResult.Failed);assertTrue(unsupported.driver.recipeWrites.isEmpty())
        }
        val stale=Fixture();stale.now=10000;stale.write {result=it};assertTrue(result is OperationResult.Failed)
        assertTrue(stale.driver.recipeWrites.isEmpty())
        val alarm=Fixture();alarm.idle(true);alarm.write {result=it};assertTrue(result is OperationResult.Failed)
        assertTrue(alarm.driver.recipeWrites.isEmpty())
    }
    @Test fun deniedPermissionAndConnectionChangeAtDispatchCannotWrite() {
        val f=Fixture();var result:OperationResult?=null
        f.write({false}) {result=it};assertTrue(result is OperationResult.Failed);assertTrue(f.driver.recipeWrites.isEmpty())
        val replaced=Fixture()
        replaced.write({replaced.session.disconnect();true}) {result=it}
        assertTrue(replaced.driver.recipeWrites.isEmpty())
        assertTrue(result is OperationResult.Unknown || result is OperationResult.Failed)
    }
    @Test fun inFlightWriteBlocksOtherControlsAndOtherQueueWorkBlocksRecipe() {
        val f=Fixture();var first:OperationResult?=null;var other:OperationResult?=null
        f.write {first=it}
        f.write {other=it};assertTrue(other is OperationResult.Failed)
        f.session.writeSetting(MachineSettingChange.Light(true)) {other=it};assertTrue(other is OperationResult.Failed)
        f.session.setBrewWait(92,{true}) {other=it};assertTrue(other is OperationResult.Failed)
        assertEquals(1,f.driver.recipeWrites.size);f.complete();assertTrue(first is OperationResult.Success)
        f.session.writeSetting(MachineSettingChange.Light(true)) {}
        f.write {other=it};assertTrue(other is OperationResult.Failed);assertEquals(1,f.driver.recipeWrites.size)
    }
    @Test fun timeoutDisconnectAndFailedGattNeverBecomeConfirmedOrAutoRetry() {
        for(kind in 0..2) {
            val f=Fixture();var result:OperationResult?=null
            f.write {result=it}
            when(kind) {
                0 -> {f.now=5000;f.session.tick()}
                1 -> f.session.disconnect()
                else -> f.complete(OperationResult.Failed("GATT status 133"))
            }
            assertTrue(result is OperationResult.Unknown)
            assertEquals(1,f.driver.recipeWrites.size)
            f.connect();assertEquals(1,f.driver.recipeWrites.size)
        }
    }
    @Test fun dispatchInvalidatesSettingsAndOnlySuccessArmsOneSettingsSync() {
        val f=Fixture();var result:OperationResult?=null;var hook=false
        assertNotNull(f.session.settingsObservedAtMs)
        f.session.writeStoredLeverRecipe(recipe,{true},{
            hook=true;assertNull(f.session.settingsObservedAtMs)
            assertTrue(f.driver.recipeWrites.isEmpty())
        }) {result=it}
        assertTrue(hook);assertNull(f.session.settingsObservedAtMs)
        f.complete();assertTrue(result is OperationResult.Success)
        val before=f.driver.calls.size
        f.session.refreshStoredRecipeSettings({true}) {result=it}
        assertEquals(before+1,f.driver.calls.size)
        assertNull(f.session.settingsObservedAtMs) // a successful write is not a 0x83
        f.complete()
        f.session.refreshStoredRecipeSettings({true}) {result=it}
        assertTrue(result is OperationResult.Failed);assertEquals(before+1,f.driver.calls.size)
        val settings=bytes("830113FD5C007D0F350019006E")
        settings[settings.lastIndex]=settings.dropLast(1).fold(0){a,b->a xor (b.toInt() and 255)}.toByte()
        f.receive(settings);assertNotNull(f.session.settingsObservedAtMs)
    }
    @Test fun unknownOrChangedConnectionCannotRefreshAndDeniedDispatchKeepsSettings() {
        val denied=Fixture();denied.write({false}) {}
        assertNotNull(denied.session.settingsObservedAtMs)
        for(kind in 0..3) {
            val f=Fixture();var result:OperationResult?=null
            f.write {}
            if(kind==0) f.complete(OperationResult.Failed("GATT error")) else f.complete()
            assertNull(f.session.settingsObservedAtMs)
            if(kind==1) f.connect(address="other")
            if(kind==2) {f.now=7000;f.idle()}
            val before=f.driver.calls.size
            f.session.refreshStoredRecipeSettings({kind!=3}) {result=it}
            assertTrue(result is OperationResult.Failed)
            assertEquals(before,f.driver.calls.size)
            f.session.refreshStoredRecipeSettings({true}) {result=it}
            assertTrue(result is OperationResult.Failed)
        }
    }
    @Test fun unknownParametersCannotBypassKnownRecipeSubset() {
        val f=Fixture();var result:OperationResult?=null
        f.session.writeStoredLeverRecipe(recipe.copy(target1=255),{true}) {result=it}
        assertTrue(result is OperationResult.Failed);assertTrue(f.driver.recipeWrites.isEmpty())
    }
    @Test fun firmware113SendsExistingBloomFrameButRejectsUnreviewedFlowCombinations() {
        val bloom=StartParameters(true,true,3,7,92,70,false,4,20,40,22,0,200,6,400,100,0)
        val allowed=Fixture();var result:OperationResult?=null
        allowed.session.writeStoredLeverRecipe(bloom,{true}) {result=it}
        assertArrayEquals(bytes("08c35c0046041428160000c806019000640000c4"),allowed.driver.recipeWrites.single().bytes)
        allowed.complete();assertTrue(result is OperationResult.Success)
        for(unreviewed in listOf(bloom.copy(pressureLogic=false), bloom.copy(firstSegmentFlowMode=true))) {
            val restricted=Fixture()
            restricted.session.writeStoredLeverRecipe(unreviewed,{true}) {result=it}
            assertTrue(result is OperationResult.Failed);assertTrue(restricted.driver.recipeWrites.isEmpty())
        }
    }

}

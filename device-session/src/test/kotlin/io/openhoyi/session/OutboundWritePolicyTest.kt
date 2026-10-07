package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class OutboundWritePolicyTest {
    private fun bytes(hex:String)=hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private class Driver:GattDriver {
        val operations=mutableListOf<GattOperation>()
        var closed:Long?=null
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
            operations+=operation;return true
        }
        override fun close(generation:Long){closed=generation}
    }
    @Test fun unsupportedWritesNeverReachTransport() {
        val driver=Driver();val guarded=GuardedGattDriver(DeviceRole.COFFEE,driver)
        for(hex in listOf("1802000100","2102000100","0B02000000","0B0600010203040500","1701A5A500","1102000100","0200070001","")) {
            assertFalse(hex,guarded.execute(1,2,GattOperation.Write(KnownGatt.coffeeWrite,bytes(hex))))
        }
        assertTrue(driver.operations.isEmpty())
    }
    @Test fun wrongRoleOrEndpointNeverReachesTransport() {
        val driver=Driver();val guarded=GuardedGattDriver(DeviceRole.BOOKOO,driver)
        assertFalse(guarded.execute(1,2,GattOperation.Write(KnownGatt.coffeeWrite,CoffeeCommands.stop(7).frame.toByteArray())))
        assertFalse(guarded.execute(1,2,GattOperation.Write(KnownGatt.coffeeNotify,BookooCodec.tare().frame.toByteArray())))
        assertFalse(guarded.execute(1,2,GattOperation.Write(KnownGatt.bookooWrite,bytes("2102000100"))))
        assertTrue(driver.operations.isEmpty())
    }
    @Test fun supportedStopAndAuthenticationReachTransportUnchanged() {
        val driver=Driver();val guarded=GuardedGattDriver(DeviceRole.COFFEE,driver)
        val frames=(listOf(1,2,3,4,5,7).map { CoffeeCommands.stop(it) }+
            CoffeeCommands.authenticate(LocalDateTime.of(2026,10,1,12,34,56),"123456"))
        for(frame in frames) {
            val op=GattOperation.Write(KnownGatt.coffeeWrite,frame.frame.toByteArray(),false)
            assertTrue(guarded.execute(4,5,op));assertSame(op,driver.operations.last())
            assertArrayEquals(frame.frame.toByteArray(),(driver.operations.last() as GattOperation.Write).bytes)
        }
        guarded.close(4);assertEquals(4L,driver.closed)
    }
    @Test fun bookooInitializationAndTareReachTransportUnchanged() {
        val driver=Driver();val guarded=GuardedGattDriver(DeviceRole.BOOKOO,driver)
        for(frame in BookooCodec.initializationCommands().map{it.frame.toByteArray()}+listOf(BookooCodec.tare().frame.toByteArray())) {
            val op=GattOperation.Write(KnownGatt.bookooWrite,frame)
            assertTrue(guarded.execute(1,2,op));assertSame(op,driver.operations.last())
        }
        val connect=GattOperation.Connect("test")
        assertTrue(guarded.execute(1,2,connect));assertSame(connect,driver.operations.last())
    }
    @Test fun everySupportedSettingRangeRetainsItsEncoderBytes() {
        val changes=mutableListOf<MachineSettingChange>()
        for(v in listOf(false,true)) changes+=listOf(
            MachineSettingChange.RunMode(v),MachineSettingChange.WaterSupply(v),
            MachineSettingChange.SleepScheduleEnabled(v),MachineSettingChange.BrewHeating(v),
            MachineSettingChange.SteamHeating(v),MachineSettingChange.Light(v))
        changes+=listOf(MachineSettingChange.LeverMode(false,false),
            MachineSettingChange.LeverMode(true,false),MachineSettingChange.LeverMode(true,true))
        changes+=(75..105).map { MachineSettingChange.BrewTemperature(it) }
        changes+=(110..145).map { MachineSettingChange.SteamTemperature(it) }
        changes+=(0..5).map { MachineSettingChange.BrewCompensation(it) }
        for(minutes in listOf(0,15,30,60,120)) for(temp in 0..100) {
            changes+=MachineSettingChange.StandbyDelay(minutes,temp)
            changes+=MachineSettingChange.StandbyTemperature(temp,minutes)
        }
        for(change in changes) assertTrue(change.toString(),OutboundWritePolicy.permits(DeviceRole.COFFEE,
            GattOperation.Write(KnownGatt.coffeeWrite,CoffeeCommands.setting(change).frame.toByteArray())))
        for(target in listOf(0)+(75..105)) assertTrue(OutboundWritePolicy.permits(DeviceRole.COFFEE,
            GattOperation.Write(KnownGatt.coffeeWrite,CoffeeCommands.brewWait(target).frame.toByteArray())))
    }
    @Test fun capturedStartsAndBothSleepFragmentsAreAcceptedButMalformedFramesAreNot() {
        val frames=listOf("02175B006C005A410000015E1600AA00000000DA",
            "02DF5C0046001426140000A0050190008C000059",
            "02DF5C00880014231200009605019000820000AC").map(::bytes)+
            CoffeeCommands.sleepSchedule(WeeklySleepSchedule((0..6).map {
                WeeklySleepDay(it%2==0,SleepDay(23,59,0,0))
            })).map{it.frame.toByteArray()}+listOf(CoffeeCommands.sleepNow().frame.toByteArray(),
                CoffeeCommands.resetCupCount().frame.toByteArray())
        for(frame in frames) {
            assertTrue(OutboundWritePolicy.permits(DeviceRole.COFFEE,GattOperation.Write(KnownGatt.coffeeWrite,frame)))
            for(length in 0 until frame.size) assertFalse("truncated $length",OutboundWritePolicy.permits(
                DeviceRole.COFFEE,GattOperation.Write(KnownGatt.coffeeWrite,frame.copyOf(length))))
            assertFalse(OutboundWritePolicy.permits(DeviceRole.COFFEE,
                GattOperation.Write(KnownGatt.coffeeWrite,frame+byteArrayOf(0))))
            val damaged=frame.copyOf();damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
            assertFalse(OutboundWritePolicy.permits(DeviceRole.COFFEE,GattOperation.Write(KnownGatt.coffeeWrite,damaged)))
        }
        for(hex in listOf("0302000100","0502000100","1402056400","0402004A00","0602009200"))
            assertFalse(hex,OutboundWritePolicy.permits(DeviceRole.COFFEE,GattOperation.Write(KnownGatt.coffeeWrite,bytes(hex))))
    }
    @Test fun malformedAuthenticationAndUnknownOpcodesFailClosed() {
        val auth=CoffeeCommands.authenticate(LocalDateTime.of(2026,10,1,12,34,56),"123456").frame.toByteArray()
        for(index in listOf(1,3,8,14)) {
            val damaged=auth.copyOf();damaged[index]=255.toByte()
            assertFalse(OutboundWritePolicy.permits(DeviceRole.COFFEE,GattOperation.Write(KnownGatt.coffeeWrite,damaged)))
        }
        val supported=setOf(1,2,3,4,5,6,9,10,12,13,14,15,16,17,20,21,32)
        for(opcode in 0..255) if(opcode !in supported) assertFalse("opcode $opcode",
            OutboundWritePolicy.permits(DeviceRole.COFFEE,GattOperation.Write(KnownGatt.coffeeWrite,
                byteArrayOf(opcode.toByte(),2,0,1,0))))
    }
    @Test fun transportRejectionIsReturnedWithoutRetry() {
        var attempts=0
        val driver=object:GattDriver {
            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                assertEquals(41L,generation);assertEquals(99L,token);attempts++;return false
            }
            override fun close(generation:Long) {}
        }
        assertFalse(GuardedGattDriver(DeviceRole.COFFEE,driver).execute(41,99,
            GattOperation.Write(KnownGatt.coffeeWrite,CoffeeCommands.stop(7).frame.toByteArray())))
        assertEquals(1,attempts)
    }
}

package io.openhoyi.session

import io.openhoyi.protocol.*
import java.time.LocalDateTime

/** Wire-format boundary only. Authentication, freshness and captured-start authorization remain
 * DeviceSession responsibilities. Never invents, repairs, retries or logs payloads. */
object OutboundWritePolicy {
    fun permits(role:DeviceRole,operation:GattOperation.Write,
        scaleAdapter:ScaleProtocolAdapter = BookooScaleProtocolAdapter):Boolean {
        val bytes=operation.bytes
        if(role==DeviceRole.BOOKOO) return scaleAdapter.transportVerified && scaleAdapter.permitsWrite(operation)
        if(operation.endpoint!=KnownGatt.coffeeWrite || bytes.isEmpty())return false
        return runCatching { coffee(bytes) }.getOrDefault(false)
    }
    private fun coffee(b:ByteArray):Boolean {
        fun u(i:Int)=b[i].toInt() and 255
        fun same(c:EncodedCommand)=b.contentEquals(c.frame.toByteArray())
        fun word(i:Int)=(u(i) shl 8) or u(i+1)
        return when(u(0)) {
            1 -> {
                if(b.size!=15 || u(1)!=12 || (8..13).any { u(it)>9 })false
                else same(CoffeeCommands.authenticate(
                    LocalDateTime.of(2000+u(2),u(3),u(4),u(5),u(6),u(7)),
                    (8..13).joinToString(""){u(it).toString()}))
            }
            2 -> when(b.size) {
                5 -> same(CoffeeCommands.stop(u(2)))
                20 -> same(CoffeeCommands.start(StartParameters(
                    u(1) and 128!=0,u(1) and 64!=0,(u(1) shr 3) and 7,u(1) and 7,
                    u(2),word(3),u(5) and 128!=0,u(5) and 127,
                    u(6),u(7),u(8),u(9),word(10),u(12),word(13),word(15),word(17))))
                else -> false
            }
            9 -> {
                if(b.size !in listOf(15,19) || u(1)!=b.size-3 || u(b.lastIndex)!=0)false
                else (2 until b.lastIndex step 4).all { i ->
                    (u(i) and 127) in 0..23 && u(i+1) in 0..59 &&
                        u(i+2) in 0..23 && u(i+3) in 0..59
                }
            }
            10 -> same(CoffeeCommands.resetCupCount())
            17 -> b.size==5 && same(CoffeeCommands.brewWait(u(3)))
            32 -> same(CoffeeCommands.sleepNow())
            else -> {
                if(b.size!=5)return false
                fun boolean(v:Int):Boolean {require(v in 0..1);return v==1}
                val change=when(u(0)) {
                    3 -> MachineSettingChange.LeverMode(boolean(u(2)),boolean(u(3)))
                    4 -> MachineSettingChange.BrewTemperature(u(3))
                    5 -> {require(u(3)%10==0);MachineSettingChange.BrewCompensation(u(3)/10)}
                    6 -> MachineSettingChange.SteamTemperature(u(3))
                    12 -> MachineSettingChange.BrewHeating(boolean(u(3)))
                    13 -> MachineSettingChange.SteamHeating(boolean(u(3)))
                    14 -> MachineSettingChange.Light(boolean(u(3)))
                    15 -> MachineSettingChange.RunMode(boolean(u(3)))
                    16 -> MachineSettingChange.WaterSupply(boolean(u(3)))
                    20 -> MachineSettingChange.StandbyTemperature(u(3),listOf(0,15,30,60,120)[u(2)])
                    21 -> MachineSettingChange.SleepScheduleEnabled(boolean(u(3)))
                    else -> return false
                }
                same(CoffeeCommands.setting(change))
            }
        }
    }
}

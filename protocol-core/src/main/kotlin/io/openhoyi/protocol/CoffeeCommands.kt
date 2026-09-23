package io.openhoyi.protocol

import java.time.LocalDateTime

/** Integer wire units. maximumWaterMl is supplied by business policy, never adjusted here. */
data class StartParameters(
    val pressureLogic: Boolean, val variableFlowLogic: Boolean, val segmentCount: Int, val slot: Int,
    val temperatureC: Int, val maximumWaterMl: Int, val firstSegmentFlowMode: Boolean, val preinfusionSeconds: Int,
    val target1: Int, val target2: Int, val target3: Int, val target4: Int,
    val firstFlowTenths: Int, val firstDurationSeconds: Int, val secondFlowTenths: Int,
    val thirdFlowTenths: Int, val fourthFlowTenths: Int,
)
/** Only settings whose 0x83 readback fields and legacy write bytes are both known. */
sealed interface MachineSettingChange {
    fun matches(settings: Settings): Boolean
    /** Legacy lever control: manual, auto pressure, or auto flow. */
    data class LeverMode(val pressure: Boolean, val flow: Boolean) : MachineSettingChange {
        init { require(!flow || pressure) }
        override fun matches(settings: Settings) =
            (settings.flags and 0x80 != 0) == pressure && (settings.flags and 0x40 != 0) == flow
    }
    data class BrewTemperature(val celsius: Int) : MachineSettingChange {
        init { require(celsius in 75..105) }
        override fun matches(settings: Settings) = settings.brewTemperatureC == celsius
    }
    data class SteamTemperature(val celsius: Int) : MachineSettingChange {
        init { require(celsius in 110..145) }
        override fun matches(settings: Settings) = settings.steamTemperatureC == celsius
    }
    data class BrewHeating(val enabled: Boolean) : MachineSettingChange {
        override fun matches(settings: Settings) = settings.brewHeating == enabled
    }
    data class SteamHeating(val enabled: Boolean) : MachineSettingChange {
        override fun matches(settings: Settings) = settings.steamHeating == enabled
    }
    data class Light(val enabled: Boolean) : MachineSettingChange {
        override fun matches(settings: Settings) = (settings.flags and 0x08 != 0) == enabled
    }
}
/** Narrow API: no public arbitrary opcode builder. Unverified controls have no encoder. */
object CoffeeCommands {
    private fun range(value:Int,maximum:Int,name:String):Int { require(value in 0..maximum) { "$name outside wire range 0..$maximum" }; return value }
    private fun command(vararg values:Int)=EncodedCommand(ByteFrame(values.map(Int::toByte).toByteArray()))
    fun start(p:StartParameters):EncodedCommand {
        require(p.segmentCount in 1..4) { "segmentCount must be 1..4" }
        require(p.slot in 1..5 || p.slot==7) { "Unsupported curve slot" }
        range(p.temperatureC,255,"temperatureC"); range(p.maximumWaterMl,65535,"maximumWaterMl")
        range(p.preinfusionSeconds,127,"preinfusionSeconds")
        listOf(p.target1,p.target2,p.target3,p.target4,p.firstDurationSeconds).forEach { range(it,255,"target/time") }
        listOf(p.firstFlowTenths,p.secondFlowTenths,p.thirdFlowTenths,p.fourthFlowTenths).forEach { range(it,65535,"flowTenths") }
        val b=ByteArray(20)
        fun put(i:Int,v:Int){ b[i]=v.toByte() }
        fun word(i:Int,v:Int){put(i,v shr 8);put(i+1,v)}
        put(0,2);put(1,(if(p.pressureLogic)128 else 0) or (if(p.variableFlowLogic)64 else 0) or (p.segmentCount shl 3) or p.slot)
        put(2,p.temperatureC);word(3,p.maximumWaterMl);put(5,(if(p.firstSegmentFlowMode)128 else 0) or p.preinfusionSeconds)
        put(6,p.target1);put(7,p.target2);put(8,p.target3);put(9,p.target4)
        word(10,p.firstFlowTenths);put(12,p.firstDurationSeconds);word(13,p.secondFlowTenths);word(15,p.thirdFlowTenths);word(17,p.fourthFlowTenths)
        put(19,b.take(19).fold(0){a,v->a xor (v.toInt() and 255)})
        return EncodedCommand(ByteFrame(b))
    }
    fun stop(slot:Int=7):EncodedCommand { require(slot in 1..7); return command(2,0,slot,0,0) }
    fun brewTemperature(celsius:Int):EncodedCommand = command(4,2,0,range(celsius,255,"temperatureC"),0)
    fun brewHeating(enabled:Boolean):EncodedCommand=command(12,2,0,if(enabled)1 else 0,0)
    fun setting(change: MachineSettingChange): EncodedCommand = when (change) {
        is MachineSettingChange.LeverMode -> command(3,2,if(change.pressure)1 else 0,if(change.flow)1 else 0,0)
        is MachineSettingChange.BrewTemperature -> brewTemperature(change.celsius)
        is MachineSettingChange.SteamTemperature -> command(6,2,0,change.celsius,0)
        is MachineSettingChange.BrewHeating -> brewHeating(change.enabled)
        is MachineSettingChange.SteamHeating -> command(13,2,0,if(change.enabled)1 else 0,0)
        is MachineSettingChange.Light -> command(14,2,0,if(change.enabled)1 else 0,0)
    }
    fun sleepNow():EncodedCommand=command(32,1,165,165,33)
    /** Six ASCII decimal digits. Password omitted from diagnostic strings and error messages. */
    fun authenticate(time:LocalDateTime,password:String):EncodedCommand {
        require(password.length==6 && password.all { it in '0'..'9' }) { "Password must contain exactly six ASCII digits" }
        require(time.year in 2000..2099) { "Authentication year must be 2000..2099" }
        val b=byteArrayOf(1,12,(time.year-2000).toByte(),time.monthValue.toByte(),time.dayOfMonth.toByte(),time.hour.toByte(),time.minute.toByte(),time.second.toByte()) + password.map { (it - '0').toByte() }.toByteArray() + byteArrayOf(0)
        b[14]=b.take(14).fold(0){a,v->a xor (v.toInt() and 255)}.toByte()
        return EncodedCommand(ByteFrame(b,sensitive=true))
    }
}
/** Document-only boundaries, deliberately without generic dispatch/encode methods. */
enum class UnsupportedCommandGroup(val reason:String) {
    OTA("No recoverable hardware validation"), PASSWORD_CHANGE("Legacy encoded/write length mismatch"),
    LEVER_CALIBRATION("Legacy encoded/write length mismatch"), FACTORY_RESET_AND_ALARM_IGNORE("Opcode 0x17 conflict"),
    CURVE_COPY("Competing 14/20 byte formats"), SLEEP_SCHEDULE_WRITE("Single-day semantics and write evidence incomplete"),
    STEAM_AND_COMPENSATION_SETTINGS("No captured setting/writeback pair"), OTHER_SETTINGS("Outside captured safe subset"),
}

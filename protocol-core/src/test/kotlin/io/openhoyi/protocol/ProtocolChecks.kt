package io.openhoyi.protocol

import java.time.LocalDateTime
import kotlin.random.Random

private var checks = 0
private fun verify(value: Boolean) { checks++; check(value) { "Protocol check $checks failed" } }
private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun rejected(block: () -> Unit) { verify(runCatching(block).exceptionOrNull() is IllegalArgumentException) }
fun main() {
    val original = hex("830113FD5C007D0F350019006E")
    val immutable = ByteFrame(original); original[0] = 0
    verify(immutable.hex() == "830113FD5C007D0F350019006E")
    immutable.toByteArray()[0] = 0; verify(immutable.toByteArray()[0].toInt() and 255 == 131)
    val settings = (HoyiCodec.decode(immutable.toByteArray()) as DecodeResult.Valid).value as Settings
    verify(settings.firmwareMajor == 1 && settings.firmwareMinor == 1 && settings.firmwarePatch == 3)
    verify(settings.flags == 253 && settings.brewTemperatureC == 92 && settings.cupCount == 25)
    val idle = (HoyiCodec.decode(hex("400024BF2F1C770B00000000000000190321AF")) as DecodeResult.Valid).value as IdleTelemetry
    verify(idle.brewTemperatureHundredthsC == 9407 && idle.steamTemperatureHundredthsC == 12060)
    verify(idle.brewPressureTenthsBar == 119 && idle.extraSensorRaw == 801)
    val extraction = (HoyiCodec.decode(hex("80080700000000052421325103")) as DecodeResult.Valid).value as ExtractionTelemetry
    verify(extraction.slotOrPhase == 7 && extraction.totalWaterTenthsMl == 5 && extraction.statusBits == 81 && extraction.valveOpen)
    val sleep = (HoyiCodec.decode(hex("8340FE0A00071E0A00071E0A00071E0A00071E3D")) as DecodeResult.Valid).value as SleepPart
    verify(sleep.enabledBits == 254 && sleep.firstDaySundayIndex == 0 && sleep.days[0] == SleepDay(10,0,7,30))
    verify((HoyiCodec.decode(hex("83800A00071E0A00071E0A00071E10")) as DecodeResult.Valid).value is SleepPart)
    val negative = hex("030B000000012D007A3A2D03424600C803010084")
    val sample = (BookooCodec.decode(negative) as DecodeResult.Valid).value
    verify(sample.weightHundredthsGram == -31290 && sample.deviceFlowHundredths == -834)
    verify(sample.weightSignRaw == 45 && sample.flowSignRaw == 45)
    negative[7] = 1; verify(BookooCodec.decode(negative) is DecodeResult.Invalid)
    verify(BookooCodec.decode(hex("FFFF")) is DecodeResult.Unknown)
    verify(HoyiCodec.decode(hex("AABBCC")) is DecodeResult.Unknown)
    val validFrames = listOf("830113FD5C007D0F350019006E", "400024BF2F1C770B00000000000000190321AF", "80080700000000052421325103", "8340FE0A00071E0A00071E0A00071E0A00071E3D", "83800A00071E0A00071E0A00071E10")
    validFrames.forEach { h -> val b=hex(h); for(n in 0 until b.size) verify(HoyiCodec.decode(b.copyOf(n)) is DecodeResult.Invalid) }
    for(n in 0 until 20) verify(BookooCodec.decode(hex("030B000000012D007A3A2D03424600C803010084").copyOf(n)) is DecodeResult.Invalid)
    val random=Random(817)
    repeat(2000) { val b=random.nextBytes(random.nextInt(0,65)); HoyiCodec.decode(b); BookooCodec.decode(b); checks++ }
    val starts = listOf(
        StartParameters(false,false,2,7,91,108,false,0,90,65,0,0,350,22,170,0,0) to "02175B006C005A410000015E1600AA00000000DA",
        StartParameters(true,true,3,7,92,70,false,0,20,38,20,0,160,5,400,140,0) to "02DF5C0046001426140000A0050190008C000059",
        StartParameters(true,true,3,7,92,136,false,0,20,35,18,0,150,5,400,130,0) to "02DF5C00880014231200009605019000820000AC")
    starts.forEach { (p,h) -> verify(CoffeeCommands.start(p).frame.hex()==h) }
    verify(CoffeeCommands.stop().frame.hex()=="0200070000")
    verify(CoffeeCommands.brewTemperature(92).frame.hex()=="0402005C00")
    verify(CoffeeCommands.brewHeating(false).frame.hex()=="0C02000000")
    verify(CoffeeCommands.brewHeating(true).frame.hex()=="0C02000100")
    verify(CoffeeCommands.setting(MachineSettingChange.BrewTemperature(92)).frame.hex()=="0402005C00")
    verify(CoffeeCommands.setting(MachineSettingChange.SteamTemperature(125)).frame.hex()=="0602007D00")
    verify(CoffeeCommands.setting(MachineSettingChange.BrewHeating(true)).frame.hex()=="0C02000100")
    verify(CoffeeCommands.setting(MachineSettingChange.SteamHeating(false)).frame.hex()=="0D02000000")
    verify(CoffeeCommands.setting(MachineSettingChange.Light(true)).frame.hex()=="0E02000100")
    verify(CoffeeCommands.setting(MachineSettingChange.LeverMode(false,false)).frame.hex()=="0302000000")
    verify(CoffeeCommands.setting(MachineSettingChange.LeverMode(true,false)).frame.hex()=="0302010000")
    verify(CoffeeCommands.setting(MachineSettingChange.LeverMode(true,true)).frame.hex()=="0302010100")
    verify(CoffeeCommands.setting(MachineSettingChange.StandbyDelay(15, 92)).frame.hex()=="1402015C00")
    verify(CoffeeCommands.setting(MachineSettingChange.StandbyDelay(120, 92)).frame.hex()=="1402045C00")
    verify(CoffeeCommands.setting(MachineSettingChange.SleepScheduleEnabled(true)).frame.hex()=="1502000100")
    verify(CoffeeCommands.setting(MachineSettingChange.SleepScheduleEnabled(false)).frame.hex()=="1502000000")
    verify(CoffeeCommands.setting(MachineSettingChange.WaterSupply(piped = false)).frame.hex()=="1002000000")
    verify(CoffeeCommands.setting(MachineSettingChange.WaterSupply(piped = true)).frame.hex()=="1002000100")
    rejected { MachineSettingChange.StandbyDelay(45, 92) }
    rejected { MachineSettingChange.StandbyDelay(15, 256) }
    rejected { MachineSettingChange.LeverMode(false,true) }
    rejected { MachineSettingChange.BrewTemperature(74) }
    rejected { MachineSettingChange.SteamTemperature(146) }
    val settingOracle = object {}.javaClass.getResourceAsStream("/machine_settings_wire.tsv")
        ?: error("Missing legacy setting oracle")
    val settingLines = settingOracle.bufferedReader().use { it.readLines() }
    verify(settingLines.first() ==
        "# machine-setting-wire-v1\tsource-sha256=b55c8b125d272fcb04e81a9b968dfa193202d94bbd1f1f245bacb33896164037")
    verify(settingLines.size == 91)
    settingLines.drop(1).forEach { line ->
        val parts = line.split('\t')
        verify(parts.size == 3)
        val value = parts[1].toInt()
        val change = when (parts[0]) {
            "brew" -> MachineSettingChange.BrewTemperature(value)
            "steam" -> MachineSettingChange.SteamTemperature(value)
            "brew_heat" -> MachineSettingChange.BrewHeating(value == 1)
            "steam_heat" -> MachineSettingChange.SteamHeating(value == 1)
            "light" -> MachineSettingChange.Light(value == 1)
            "lever" -> MachineSettingChange.LeverMode(value >= 10, value % 10 == 1)
            "standby" -> MachineSettingChange.StandbyDelay(listOf(0, 15, 30, 60, 120)[value / 256], value % 256)
            "sleep_enabled" -> MachineSettingChange.SleepScheduleEnabled(value == 1)
            "water_supply" -> MachineSettingChange.WaterSupply(value == 1)
            else -> error("Unknown setting oracle kind")
        }
        verify(CoffeeCommands.setting(change).frame.hex() == parts[2])
    }
    verify(CoffeeCommands.sleepNow().frame.hex()=="2001A5A521")
    val auth=CoffeeCommands.authenticate(LocalDateTime.of(2026,9,20,12,30,5),"123456")
    verify(auth.frame.toByteArray().size == 15 && !auth.toString().contains("123456") && !auth.frame.toString().contains("313233343536"))
    verify(auth.frame.hex() == "010C1A09140C1E050102030405061A")
    verify(auth.frame.toByteArray().fold(0){a,b->a xor (b.toInt() and 255)}==0)
    rejected { CoffeeCommands.authenticate(LocalDateTime.now(),"12abc6") }; rejected { CoffeeCommands.authenticate(LocalDateTime.now(),"12345") }
    rejected { CoffeeCommands.brewTemperature(-1) }; rejected { CoffeeCommands.brewTemperature(256) }
    rejected { CoffeeCommands.start(starts[0].first.copy(maximumWaterMl=65536)) }
    rejected { CoffeeCommands.start(starts[0].first.copy(segmentCount=5)) }
    verify(BookooCodec.initializationCommands().map { it.frame.hex() } == listOf("030A02000308","030A0300141E","030A0700000E","030A08010000"))
    verify(BookooCodec.initializationCommands().map { it.delayMs } == listOf(500L,1000L,1500L,2000L))
    verify(BookooCodec.tare().frame.hex()=="030A01000008")
    val replay = object {}.javaClass.getResourceAsStream("/notifications.tsv") ?: error("Missing fixtures")
    var frames=0
    replay.bufferedReader().useLines { lines -> lines.filter { !it.startsWith("#") }.forEach { line ->
        val p=line.split('\t'); val result=if(p[3]=="coffee") HoyiCodec.decode(hex(p[4])) else BookooCodec.decode(hex(p[4])); verify(result is DecodeResult.Valid); frames++
    } }
    verify(frames==32856)
    println("protocol-core: $checks checks passed; $frames captured notifications replayed")
}

package io.openhoyi.mobile

import android.content.Context
import io.openhoyi.protocol.*
import io.openhoyi.session.*
import io.openhoyi.session.SettingsWriteTracker.State.CONFIRMED
import io.openhoyi.session.SettingsWriteTracker.State.WAITING_READBACK

/** Scripted evidence traverses the detached real service, codec, hub and guarded fake driver. */
internal object MockBleSameIdFixture {
    data class Outcome(val row: String, val passed: Boolean, val fakeWrites: Int)
    class Result(private val outcomes: List<Outcome>, private val edges: List<Outcome>) {
        fun render(zh: Boolean): String {
            val header = if (zh) "隔离模拟核心（部分行为）：${outcomes.count { it.passed }}/${outcomes.size}\n非实机测试"
                else "Detached fake core (partial behavior): ${outcomes.count { it.passed }}/${outcomes.size}\nNot a hardware test"
            return (listOf(header) + outcomes.map {
                "${it.row}: ${if (it.passed) "PASS" else "FAIL"} (fake writes=${it.fakeWrites})"
            } + edges.map { "${it.row}: ${if (it.passed) "PASS" else "FAIL"} (fake writes=${it.fakeWrites})" } +
                if (zh) "未测：主动同步、条件重连、萃取启停、拨杆配方、预热/取消、真实进程恢复和版本回退。"
                else "NOT RUN: active sync, conditional reconnect, shot start/stop, stored lever recipe, preheat/cancel, process restore and app downgrade.")
                .joinToString("\n")
        }
    }
    private data class Setting(val row: String, val change: MachineSettingChange, val observed: ByteArray)

    fun cases(context: Context): List<() -> Outcome> {
        val tests = mutableListOf<() -> Outcome>()
        fun case(row: String, run: MobileService.DetachedCorePort.() -> Unit) {
            tests += {
                var writes = 0
                val passed = try {
                    MobileService.withDetachedCore(context) { port ->
                        try { port.run() } finally {
                            writes = port.calls.count { it.operation is GattOperation.Write }
                        }
                    }
                    true
                } catch (_: Exception) { false } catch (_: AssertionError) { false }
                Outcome(row, passed, writes)
            }
        }
        case("01") {
            bootstrap()
            check(calls.count { it.operation is GattOperation.Write } == 1)
            check(settingState.name == "IDLE")
        }
        val settings = listOf(
            Setting("03", MachineSettingChange.LeverMode(false, true), settings(flags = 0x7d)),
            Setting("04", MachineSettingChange.BrewTemperature(94), settings(brew = 94)),
            Setting("05", MachineSettingChange.BrewCompensation(2), settings(compensation = 20)),
            Setting("06", MachineSettingChange.SteamTemperature(130), settings(steam = 130)),
            Setting("0C", MachineSettingChange.BrewHeating(false), settings(flags = 0xdd)),
            Setting("0D", MachineSettingChange.SteamHeating(false), settings(flags = 0xed)),
            Setting("0E", MachineSettingChange.Light(false), settings(flags = 0xf5)),
            Setting("0F", MachineSettingChange.RunMode(false), settings(flags = 0xf9)),
            Setting("10", MachineSettingChange.WaterSupply(true), settings(flags = 0xff)),
            Setting("14-delay", MachineSettingChange.StandbyDelay(30, 53), settings(delay = 30)),
            Setting("14-temp", MachineSettingChange.StandbyTemperature(56, 15), settings(standby = 56)),
            Setting("15", MachineSettingChange.SleepScheduleEnabled(true), settings()),
        )
        for ((row, change, target) in settings) case("$row/passive") {
            bootstrap()
            val baseline = if (row == "15") settings(flags = 0xfc) else settings()
            advance(1); observe(baseline)
            if (row == "15") {
                advance(1); observe(weeklyFirst())
                check(apply(change) != null)
                advance(1); observe(weeklySecond())
            }
            val requested = CoffeeCommands.setting(change).frame.toByteArray()
            check(apply(change) == null)
            expectLastWrite(requested)
            complete()
            check(settingState == WAITING_READBACK)
            advance(1); observe(baseline)
            check(settingState == WAITING_READBACK)
            advance(1); observe(target, generation = generation + 1)
            check(settingState == WAITING_READBACK)
            advance(1); observe(target)
            check(settingState == CONFIRMED)
            check(calls.count { (it.operation as? GattOperation.Write)?.bytes?.contentEquals(requested) == true } == 1)
        }
        case("09") {
            bootstrap()
            val first = weeklyFirst(); val second = weeklySecond()
            advance(1); observe(first)
            advance(1); observe(second)
            val expected = week(first, second)
            val target = WeeklySleepSchedule(expected.days.mapIndexed { i, day ->
                if (i == 0) day.copy(enabled = !day.enabled) else day
            })
            val frames = CoffeeCommands.sleepSchedule(target)
            check(changeSchedule(expected, target) == null)
            expectLastWrite(frames[0].frame.toByteArray())
            complete()
            advance(500)
            expectLastWrite(frames[1].frame.toByteArray())
            complete()
            advance(1); observe(second)
            check(scheduleState.name == "WAITING_READBACK")
            advance(1); observe(weeklyFirst(enabled = 0x7e))
            check(scheduleState.name == "WAITING_READBACK")
            advance(1); observe(second)
            check(scheduleState.name == "CONFIRMED")
        }
        case("0A") {
            bootstrap()
            check(resetCups(24) != null)
            check(resetCups(25) == null)
            expectLastWrite(CoffeeCommands.resetCupCount().frame.toByteArray())
            complete()
            advance(1); observe(settings(cups = 0))
            check(cupState.name == "WAITING_ZERO")
            advance(1); observe(idle(cups = 0))
            check(cupState.name == "CONFIRMED")
        }
        case("20") {
            bootstrap()
            check(sleep() == null)
            expectLastWrite(CoffeeCommands.sleepNow().frame.toByteArray())
            complete()
            advance(1); observe(idle())
            check(sleepState.name == "WAITING_ASLEEP")
            advance(1); observe(idle(asleep = 1))
            check(sleepState.name == "CONFIRMED")
        }
        return tests + listOf({ detachedLifecycle(context) })
    }

    /** Never builds legacy payloads or supplies these to a DeviceSession/real transport. */
    fun boundaryChecks(): List<Outcome> {
        val forbidden = listOf(
            Triple("07", 0x07, 5), Triple("08-short", 0x08, 5), Triple("09-single", 0x09, 7),
            Triple("0B", 0x0b, 9), Triple("12", 0x12, 5), Triple("13", 0x13, 5),
            Triple("16", 0x16, 5), Triple("17-reset", 0x17, 5), Triple("17-alarm", 0x17, 6),
            Triple("18", 0x18, 5), Triple("21", 0x21, 5), Triple("40-outbound", 0x40, 5),
            Triple("OTA-boundary", 0xa0, 5),
        )
        return forbidden.mapIndexed { index, (row, opcode, size) ->
            val sink = DetachedCoreDriver()
            val guard = GuardedGattDriver(DeviceRole.COFFEE, sink)
            val marker = ByteArray(size).apply { this[0] = opcode.toByte() }
            val denied = !guard.execute(1, index + 1L, GattOperation.Write(KnownGatt.coffeeWrite, marker, true))
            Outcome("deny/$row", denied && sink.calls.isEmpty(), sink.calls.size)
        }
    }

    /** Detached host disposal only. Never presents this as Android process death/upgrade. */
    private fun detachedLifecycle(context: Context): Outcome {
        var writes = 0
        val passed = try {
            val old = MobileService.withDetachedCore(context) { port ->
                port.bootstrap()
                check(port.apply(MachineSettingChange.Light(false)) == null)
                check(port.calls.count { it.operation is GattOperation.Write } == 2)
                port
            }
            check(runCatching { old.complete() }.isFailure)
            MobileService.withDetachedCore(context) { fresh ->
                fresh.bootstrap()
                fresh.advance(100)
                fresh.observe(settings(flags = 0xf5))
                writes = fresh.calls.count { it.operation is GattOperation.Write }
                check(writes == 1 && fresh.settingState.name == "IDLE" && fresh.recoveryKind == null)
            }
            true
        } catch (_: Exception) { false } catch (_: AssertionError) { false }
        return Outcome("detached-owner-dispose/no-replay", passed, writes)
    }

    private fun MobileService.DetachedCorePort.expectLastWrite(bytes: ByteArray) {
        val last = calls.last().operation as? GattOperation.Write ?: error("No fake write")
        check(last.endpoint == KnownGatt.coffeeWrite && last.bytes.contentEquals(bytes))
    }
    private fun settings(flags: Int = 0xfd, brew: Int = 92, compensation: Int = 0, steam: Int = 125,
        delay: Int = 15, standby: Int = 53, cups: Int = 25) =
        bytes(0x83, 1, 0x13, flags, brew, compensation, steam, delay, standby, 0, cups, 0, 0x6e)
    private fun idle(cups: Int = 25, asleep: Int = 0) =
        bytes(0x40, 0, 0x24, 0xbf, 0x2f, 0x1c, 0x77, 0x0b, asleep, 0, 0, 0, 0, 0, 0, cups, 3, 0x21, 0xaf)
    private fun weeklyFirst(enabled: Int = 0xfe) =
        bytes(0x83, 0x40, enabled, 10, 0, 7, 30, 10, 0, 7, 30, 10, 0, 7, 30, 10, 0, 7, 30, 0x3d)
    private fun weeklySecond() =
        bytes(0x83, 0x80, 10, 0, 7, 30, 10, 0, 7, 30, 10, 0, 7, 30, 0x10)
    private fun bytes(vararg data: Int) = data.map { it.toByte() }.toByteArray()
    private fun week(a: ByteArray, b: ByteArray) = requireNotNull(WeeklySleepSchedule.fromReadback(
        (HoyiCodec.decode(a) as DecodeResult.Valid).value as SleepPart,
        (HoyiCodec.decode(b) as DecodeResult.Valid).value as SleepPart
    ))
}

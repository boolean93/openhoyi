package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import io.openhoyi.bluetooth.AndroidDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.protocol.*
import io.openhoyi.session.*
import io.openhoyi.trace.TraceStore
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real prewrite entry branches, synthetic READY and fake storage faults; never a BLE connection. */
internal class ServiceWriteRegistrationChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun decode(hex: String) = (HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
    private class Driver : GattDriver {
        var executions = 0
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            executions++
            error("No transport execution permitted")
        }
        override fun close(generation: Long) = Unit
    }

    fun run() {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val address = "AA:BB:CC:DD:EE:01"
        val names = listOf("shot_safety", "machine_write_safety", "curves")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val first = decode("8340FE0A00071E0A00071E0A00071E0A00071E3D") as SleepPart
        val second = decode("83800A00071E0A00071E0A00071E10") as SleepPart
        val settings = decode("830113FD5C007D0F350019006E") as Settings
        val expected = requireNotNull(WeeklySleepSchedule.fromReadback(first, second))
        val target = WeeklySleepSchedule(expected.days.mapIndexed { index, day ->
            if (index == 0) day.copy(enabled = !day.enabled) else day
        })
        val kinds = MachineWriteRecoveryState.Kind.entries.filter { it != MachineWriteRecoveryState.Kind.UNKNOWN }
        var checkedFixtures = 0
        var failedRegistrations = 0
        for (throws in listOf(false, true)) for (kind in kinds) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_write_registration_${id}_$it" }
            val folder = File(app.cacheDir, "service-write-registration-$id")
            val journal = TraceStore(folder)
            val context = object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? = error("No system service: $name")
                override fun checkSelfPermission(permission: String): Int = error("No BLE permission access")
                override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(requireNotNull(fixtures[name]), mode)
                override fun startService(intent: Intent) = error("No component dispatch")
                override fun startForegroundService(intent: Intent) = error("No component dispatch")
                override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = error("No component dispatch")
                override fun startActivity(intent: Intent) = error("No component dispatch")
                override fun startActivity(intent: Intent, options: Bundle?) = error("No component dispatch")
                override fun stopService(intent: Intent) = error("No component dispatch")
            }
            try {
                val selected = CurveCatalog.profiles.first()
                check(app.curves.resolve(selected.id, true, 7) == selected && app.curves.validated(selected))
                check(app.getSharedPreferences(fixtures.getValue("curves"), Context.MODE_PRIVATE).edit()
                    .putString("selected", selected.id).commit())
                check(app.getSharedPreferences(fixtures.getValue("shot_safety"), Context.MODE_PRIVATE).edit()
                    .putBoolean("unresolved_shot", false).commit())
                val before = fixtures.mapValues { (_, name) -> app.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap() }
                var attempts = 0
                val attemptedRecords = mutableListOf<MachineWriteRecoveryState.Record>()
                val barrierStates = mutableListOf<String>()
                lateinit var tracker: Any
                val recovery = MachineWriteRecoveryState(object : MachineWriteRecoveryState.Storage {
                    override fun read() = MachineWriteRecoveryState.Record(null, null)
                    override fun write(record: MachineWriteRecoveryState.Record): Boolean {
                        attempts++
                        attemptedRecords += record
                        barrierStates += requireNotNull(field(tracker, "state").get(tracker)).toString()
                        if (throws) throw IllegalStateException("Injected registration storage failure")
                        return false
                    }
                })
                var failure: Throwable? = null
                test.runOnMainSync {
                    var service: MobileService? = null
                    var hub: NativeDeviceHub? = null
                    try {
                        val instance = MobileService()
                        service = instance
                        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                            .apply { isAccessible = true }.invoke(instance, context)
                        Service::class.java.getDeclaredField("mApplication").apply { isAccessible = true }.set(instance, app)
                        field(instance, "logs").set(instance, journal)
                        field(instance, "mock").set(instance, null)
                        field(instance, "machineWriteRecovery\$delegate").set(instance, lazyOf(recovery))
                        val owner = NativeDeviceHub(context)
                        hub = owner
                        val devices = listOf("coffee", "scale").map { field(owner, it).get(owner) as AndroidDevice }
                        val drivers = listOf(Driver(), Driver())
                        devices.zip(drivers).forEach { (device, driver) ->
                            val queue = field(device.session, "queue").get(device.session) as GattQueue
                            check(!queue.active)
                            field(queue, "driver").set(queue, driver)
                        }
                        field(devices[0].session, "state").set(devices[0].session, DeviceState.READY)
                        field(devices[0].session, "activeAddress").set(devices[0].session, address)
                        field(instance, "hub").set(instance, owner)
                        listOf("cupSettingsSerial", "cupIdleSerial", "settingsSampleSerial", "firstSleepSerial",
                            "secondSleepSerial", "sleepSampleSerial", "idleSampleSerial").forEach { field(instance, it).setLong(instance, 1) }
                        val now = SystemClock.elapsedRealtime()
                        val idle = IdleTelemetry(8000, 12000, 0, 0, 0, 0, settings.cupCount, 0, ByteFrame(byteArrayOf()))
                        field(instance, "snapshot").set(instance, MobileSnapshot(coffeeState = DeviceState.READY,
                            scaleState = DeviceState.READY, coffee = idle, coffeeAt = now, weightAt = now,
                            settings = settings, settingsAt = now, sleepFirst = first, sleepSecond = second,
                            sleepFirstAt = now, sleepSecondAt = now))
                        tracker = requireNotNull(field(instance, when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> "settingsWrite"
                            MachineWriteRecoveryState.Kind.CUP_RESET -> "cupReset"
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> "scheduleWrite"
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> "sleepNow"
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> "brewPreparation"
                            else -> error("Invalid fixture")
                        }).get(instance))
                        val errorResource = when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> R.string.service_write_setting_record_failed
                            MachineWriteRecoveryState.Kind.CUP_RESET -> R.string.service_write_cups_record_failed
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> R.string.service_write_schedule_record_failed
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> R.string.service_write_sleep_record_failed
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> R.string.service_shot_preheat_record_failed
                            else -> error("Invalid fixture")
                        }
                        val baselines = listOf("recoveryAfterSettingsSerial", "recoveryAfterIdleSerial", "recoveryAfterFirstSleepSerial",
                            "recoveryAfterSecondSleepSerial", "recoveryAfterSleepSerial", "recoveryAfterBrewWaitIdleSerial")
                        val baselineBefore = baselines.associateWith { field(instance, it).getLong(instance) }
                        repeat(2) { retry ->
                            val stamp = SystemClock.elapsedRealtime()
                            field(instance, "snapshot").set(instance, instance.snapshot.copy(coffeeAt = stamp,
                                weightAt = stamp, settingsAt = stamp, sleepFirstAt = stamp, sleepSecondAt = stamp))
                            val snapshotBefore = instance.snapshot
                            check(!recovery.pending && instance.machineControlSafetyMessage == null)
                            val result = when (kind) {
                                MachineWriteRecoveryState.Kind.SETTING -> instance.changeMachineSetting(MachineSettingChange.BrewTemperature(93))
                                MachineWriteRecoveryState.Kind.CUP_RESET -> instance.resetCupCount(settings.cupCount)
                                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> instance.changeSleepSchedule(expected, target)
                                MachineWriteRecoveryState.Kind.SLEEP_NOW -> instance.enterSleepNow()
                                MachineWriteRecoveryState.Kind.BREW_WAIT -> instance.prepareBrew(selected.id, selected.scaleMode, 7)
                                else -> error("Invalid fixture")
                            }
                            check(result == instance.getString(errorResource)) { "$kind throws=$throws retry=$retry: $result" }
                            check(attempts == retry + 1 && !recovery.pending && recovery.kind == null && recovery.address == null)
                            // Assert outside storage.write: recovery intentionally catches storage exceptions.
                            check(attemptedRecords.size == attempts && attemptedRecords.all { it == MachineWriteRecoveryState.Record(kind, address) })
                            check(barrierStates.size == attempts && barrierStates.all { it == "WRITING" })
                            check(requireNotNull(field(tracker, "state").get(tracker)).toString() ==
                                if (kind == MachineWriteRecoveryState.Kind.BREW_WAIT) "IDLE" else "FAILED")
                            check(instance.snapshot == snapshotBefore)
                            check(baselines.all { field(instance, it).getLong(instance) == baselineBefore.getValue(it) })
                            check(drivers.all { it.executions == 0 })
                            check(devices.all { !(field(it.session, "queue").get(it.session) as GattQueue).active })
                            before.forEach { (name, values) -> check(app.getSharedPreferences(fixtures.getValue(name), Context.MODE_PRIVATE).all == values) }
                            failedRegistrations++
                        }
                        checkedFixtures++
                    } catch (error: Throwable) { failure = error }
                    finally {
                        hub?.let { owner ->
                            listOf("coffee", "scale").forEach { name -> runCatching { (field(owner, name).get(owner) as AndroidDevice).close() } }
                            runCatching { owner.scanner.close() }
                            (field(owner, "handler").get(owner) as Handler).removeCallbacks(field(owner, "ticker").get(owner) as Runnable)
                        }
                        service?.let { (field(it, "handler").get(it) as Handler).removeCallbacksAndMessages(null) }
                    }
                }
                failure?.let { throw it }
            } finally {
                journal.close()
                check(journal.awaitTermination(5, TimeUnit.SECONDS))
                folder.deleteRecursively()
                fixtures.values.forEach { check(app.deleteSharedPreferences(it)) }
                original.forEach { (name, values) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
            }
        }
        check(checkedFixtures == 10 && failedRegistrations == 20)
    }
}

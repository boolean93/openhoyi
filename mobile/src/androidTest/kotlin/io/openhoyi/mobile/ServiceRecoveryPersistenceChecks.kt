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

/** Actual acknowledgement, synthetic READY/readbacks and local persistence fault; no transport sends. */
internal class ServiceRecoveryPersistenceChecks(private val test: Instrumentation) {
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
        for (kind in MachineWriteRecoveryState.Kind.entries.filter { it != MachineWriteRecoveryState.Kind.UNKNOWN }) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_recovery_persistence_${id}_$it" }
            val folder = File(app.cacheDir, "service-recovery-persistence-$id")
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
                // startShot reads selection before the BREW_WAIT cancellation gate. Keep it
                // isolated and valid, so rejection cannot come from a missing curve fixture.
                val selected = CurveCatalog.profiles.first()
                check(app.curves.resolve(selected.id, false, 7) == selected && CurveCatalog.validated(selected))
                val curvePrefs = app.getSharedPreferences(fixtures.getValue("curves"), Context.MODE_PRIVATE)
                check(curvePrefs.edit().putString("selected", selected.id).commit())
                val curveBefore = curvePrefs.all.toMap()
                val prefs = app.getSharedPreferences(fixtures.getValue("machine_write_safety"), Context.MODE_PRIVATE)
                check(prefs.edit().putString("pending_kind", kind.name).putString("pending_address", address).commit())
                val shotPrefs = app.getSharedPreferences(fixtures.getValue("shot_safety"), Context.MODE_PRIVATE)
                check(shotPrefs.edit().putBoolean("unresolved_shot", false).commit())
                val shotBefore = shotPrefs.all.toMap()
                val before = prefs.all.toMap()
                var writable = false
                var attempts = 0
                val recovery = MachineWriteRecoveryState(object : MachineWriteRecoveryState.Storage {
                    override fun read() = MachineWriteRecoveryState.Record(kind, address)
                    override fun write(record: MachineWriteRecoveryState.Record): Boolean {
                        attempts++
                        if (!writable) return false
                        return prefs.edit().putString("pending_kind", record.kind?.name)
                            .putString("pending_address", record.address).commit()
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
                        // This is test-supplied evidence, not an authentication or BLE connection.
                        field(devices[0].session, "state").set(devices[0].session, DeviceState.READY)
                        field(devices[0].session, "activeAddress").set(devices[0].session, address)
                        field(instance, "hub").set(instance, owner)
                        listOf("cupSettingsSerial", "cupIdleSerial", "settingsSampleSerial", "firstSleepSerial",
                            "secondSleepSerial", "sleepSampleSerial", "idleSampleSerial").forEach { field(instance, it).setLong(instance, 1) }
                        val now = SystemClock.elapsedRealtime()
                        val idle = IdleTelemetry(9300, 12000, 0, 0,
                            if (kind == MachineWriteRecoveryState.Kind.SLEEP_NOW) 1 else 0,
                            0, settings.cupCount, 0, ByteFrame(byteArrayOf()))
                        field(instance, "snapshot").set(instance, MobileSnapshot(coffeeState = DeviceState.READY,
                            coffee = idle, coffeeAt = now, settings = settings, settingsAt = now,
                            sleepFirst = first, sleepSecond = second, sleepFirstAt = now, sleepSecondAt = now))
                        val warning = requireNotNull(instance.machineControlSafetyMessage)
                        val waitingResource = when (kind) {
                            MachineWriteRecoveryState.Kind.CUP_RESET -> R.string.recovery_event_cups_recovery_waiting
                            MachineWriteRecoveryState.Kind.SETTING -> R.string.recovery_event_settings_recovery_waiting
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> R.string.recovery_event_sleep_schedule_recovery_waiting
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> R.string.recovery_event_sleep_recovery_waiting
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> R.string.recovery_event_brew_wait_recovery_waiting
                            else -> error("invalid fixture")
                        }
                        fun refreshPresentTimestamps() {
                            // Keep valid clocks fresh so another missing/invalid field is the intended blocker.
                            val current = instance.snapshot
                            val stamp = SystemClock.elapsedRealtime()
                            field(instance, "snapshot").set(instance, current.copy(
                                coffeeAt = current.coffeeAt?.let { stamp }, settingsAt = current.settingsAt?.let { stamp },
                                sleepFirstAt = current.sleepFirstAt?.let { stamp }, sleepSecondAt = current.sleepSecondAt?.let { stamp }))
                        }
                        fun checkAcknowledgementBlocked() {
                            refreshPresentTimestamps()
                            check(instance.machineWriteAcknowledgementAvailable)
                            instance.acknowledgeManualSafety()
                            check(attempts == 0 && recovery.pending && prefs.all == before && shotPrefs.all == shotBefore)
                            check(instance.machineControlSafetyMessage == warning)
                            check(instance.snapshot.messageForDisplay { resource, args -> instance.getString(resource, *args) } == instance.getString(waitingResource))
                            check(drivers.all { it.executions == 0 })
                        }
                        field(devices[0].session, "activeAddress").set(devices[0].session, "AA:BB:CC:DD:EE:02")
                        check(owner.coffeeAddress == "AA:BB:CC:DD:EE:02")
                        checkAcknowledgementBlocked()
                        field(devices[0].session, "activeAddress").set(devices[0].session, address)
                        field(instance, "snapshot").set(instance, instance.snapshot.copy(coffeeState = DeviceState.DISCONNECTED))
                        checkAcknowledgementBlocked()
                        field(instance, "snapshot").set(instance, instance.snapshot.copy(coffeeState = DeviceState.READY))
                        val validSnapshot = instance.snapshot
                        field(instance, "snapshot").set(instance, validSnapshot.copy(coffeeAt = null))
                        checkAcknowledgementBlocked()
                        field(instance, "snapshot").set(instance, validSnapshot)
                        val baselines = when (kind) {
                            MachineWriteRecoveryState.Kind.CUP_RESET -> listOf("recoveryAfterSettingsSerial", "recoveryAfterIdleSerial")
                            MachineWriteRecoveryState.Kind.SETTING -> listOf("recoveryAfterSettingsSerial")
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> listOf("recoveryAfterFirstSleepSerial", "recoveryAfterSecondSleepSerial")
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> listOf("recoveryAfterSleepSerial")
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> listOf("recoveryAfterBrewWaitIdleSerial")
                            else -> error("invalid fixture")
                        }
                        baselines.forEach { field(instance, it).setLong(instance, 1) }
                        checkAcknowledgementBlocked()
                        baselines.forEach { field(instance, it).setLong(instance, 0) }
                        val incomplete = when (kind) {
                            MachineWriteRecoveryState.Kind.CUP_RESET -> validSnapshot.copy(coffee = idle.copy(cupCount = settings.cupCount + 1))
                            MachineWriteRecoveryState.Kind.SETTING -> validSnapshot.copy(settings = null)
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> validSnapshot.copy(sleepSecond = null)
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> validSnapshot.copy(coffee = idle.copy(sleepStateRaw = 2))
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> validSnapshot.copy(coffee = idle.copy(sleepStateRaw = 1))
                            else -> error("invalid fixture")
                        }
                        field(instance, "snapshot").set(instance, incomplete)
                        checkAcknowledgementBlocked()
                        field(instance, "snapshot").set(instance, validSnapshot)
                        val tracker: Any = field(instance, when (kind) {
                            MachineWriteRecoveryState.Kind.CUP_RESET -> "cupReset"
                            MachineWriteRecoveryState.Kind.SETTING -> "settingsWrite"
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> "scheduleWrite"
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> "sleepNow"
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> "brewPreparation"
                            else -> error("invalid fixture")
                        }).get(instance)
                        val plan = requireNotNull(WeeklySleepSchedule.fromReadback(first, second))
                        val token = requireNotNull(when (tracker) {
                            is CupResetTracker -> tracker.begin(settings.cupCount)
                            is SettingsWriteTracker -> tracker.begin(MachineSettingChange.BrewTemperature(93))
                            is SleepScheduleWriteTracker -> tracker.begin(plan, 1, 1)
                            is SleepNowTracker -> tracker.begin()
                            is BrewPreparation -> tracker.begin("fixture", 93)
                            else -> error("invalid tracker")
                        })
                        fun checkBusyGate() {
                            refreshPresentTimestamps()
                            val priorState = field(tracker, "state").get(tracker)
                            check(!instance.machineWriteAcknowledgementAvailable)
                            instance.acknowledgeManualSafety()
                            check(attempts == 0 && recovery.pending && prefs.all == before && shotPrefs.all == shotBefore)
                            check(field(tracker, "state").get(tracker) == priorState)
                            val busyWarning = instance.machineControlSafetyMessage
                            val entries = listOf(
                                instance.changeMachineSetting(MachineSettingChange.BrewTemperature(93)),
                                instance.resetCupCount(settings.cupCount),
                                instance.changeSleepSchedule(plan, plan), instance.enterSleepNow(),
                                instance.prepareBrew("fixture", false, 7))
                            if (tracker is BrewPreparation) {
                                if (tracker.state == BrewPreparation.State.CANCELLING) {
                                    check(busyWarning == instance.getString(R.string.machine_recovery_brew_wait))
                                    check(entries.all { it == busyWarning })
                                    check(instance.startShot(selected.id, selected.scaleMode, 7) == busyWarning)
                                } else {
                                    // Ordinary preheat hides the advisory, but still blocks unrelated controls.
                                    check(busyWarning == null)
                                    val resources = listOf(R.string.service_write_preheat_block,
                                        R.string.service_write_preheat_or_shot_block, R.string.service_write_preheat_block,
                                        R.string.service_write_preheat_block, R.string.service_shot_preheat_pending)
                                    check(entries == resources.map(instance::getString))
                                    // Starting a shot is part of preheat consumption, governed separately.
                                }
                            } else {
                                check(busyWarning != null && entries.all { it == busyWarning })
                                check(instance.startShot(selected.id, selected.scaleMode, 7) == busyWarning)
                            }
                            check(drivers.all { it.executions == 0 } && curvePrefs.all == curveBefore)
                        }
                        checkBusyGate() // WRITING must not permit acknowledgement.
                        when (tracker) {
                            is CupResetTracker -> check(tracker.written(token, OperationResult.Success(), 1, 1))
                            is SettingsWriteTracker -> check(tracker.written(token, OperationResult.Success(), 1))
                            is SleepScheduleWriteTracker -> check(tracker.written(token, OperationResult.Success(), 1, 1, first, second))
                            is SleepNowTracker -> check(tracker.written(token, OperationResult.Success(), 1))
                            is BrewPreparation -> check(tracker.written(token, OperationResult.Success(), 1))
                        }
                        checkBusyGate() // Transport success still waits for application evidence.
                        if (tracker is BrewPreparation) {
                            check(tracker.observe(2, 9300) && tracker.state == BrewPreparation.State.READY)
                            checkBusyGate() // Ready preheat remains an active intent until explicitly consumed.
                            check(tracker.beginCancel() != null && tracker.state == BrewPreparation.State.CANCELLING)
                            checkBusyGate() // A queued cancellation is not evidence that the machine stopped preheating.
                        }
                        when (tracker) {
                            is CupResetTracker -> tracker.disconnected(1, 1)
                            is SettingsWriteTracker -> tracker.disconnected(1)
                            is SleepScheduleWriteTracker -> tracker.disconnected(1, 1)
                            is SleepNowTracker -> tracker.disconnected(1)
                            is BrewPreparation -> tracker.disconnected()
                        }
                        // Supply a fresh post-boundary snapshot; this is still synthetic readback evidence.
                        listOf("cupSettingsSerial", "cupIdleSerial", "settingsSampleSerial", "firstSleepSerial",
                            "secondSleepSerial", "sleepSampleSerial", "idleSampleSerial").forEach { field(instance, it).setLong(instance, 2) }
                        val freshNow = SystemClock.elapsedRealtime()
                        field(instance, "snapshot").set(instance, instance.snapshot.copy(coffeeAt = freshNow,
                            settingsAt = freshNow, sleepFirstAt = freshNow, sleepSecondAt = freshNow))
                        check(instance.machineWriteAcknowledgementAvailable)
                        instance.acknowledgeManualSafety()
                        check(attempts == 1 && recovery.pending && prefs.all == before && shotPrefs.all == shotBefore)
                        check(instance.machineControlSafetyMessage == warning)
                        val errorResource = when (kind) {
                            MachineWriteRecoveryState.Kind.CUP_RESET -> R.string.recovery_event_cups_recovery_clear_failed
                            MachineWriteRecoveryState.Kind.SETTING -> R.string.recovery_event_settings_recovery_clear_failed
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> R.string.recovery_event_sleep_schedule_recovery_clear_failed
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> R.string.recovery_event_sleep_recovery_clear_failed
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> R.string.recovery_event_brew_wait_recovery_clear_failed
                            else -> error("invalid fixture")
                        }
                        check(instance.snapshot.messageForDisplay { resource, args -> instance.getString(resource, *args) } == instance.getString(errorResource))
                        check(instance.changeMachineSetting(MachineSettingChange.BrewTemperature(93)) == warning)
                        writable = true
                        instance.acknowledgeManualSafety()
                        check(attempts == 2 && !recovery.pending && instance.machineControlSafetyMessage == null)
                        check(!prefs.contains("pending_kind") && !prefs.contains("pending_address"))
                        instance.acknowledgeManualSafety()
                        check(attempts == 2 && shotPrefs.all == shotBefore)
                        check(drivers.all { it.executions == 0 })
                        check(devices.all { !(field(it.session, "queue").get(it.session) as GattQueue).active })
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
                original.forEach { (name, values) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
            } finally {
                journal.close()
                check(journal.awaitTermination(5, TimeUnit.SECONDS))
                folder.deleteRecursively()
                fixtures.values.forEach { check(app.deleteSharedPreferences(it)) }
            }
        }
    }
}

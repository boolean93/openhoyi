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

/** Detached real entry/callback/persistence paths with synthetic evidence and successful fake GATT and real decoded notification readbacks. */
internal class ServiceWriteReadbackChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun decode(hex: String) = (HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
    private fun recovery(service: MobileService) =
        ((field(service, "machineWriteRecovery\$delegate").get(service) as Lazy<*>).value as MachineWriteRecoveryState)

    fun run() = run(null)
    fun runClearFailure(throwing: Boolean) = run(throwing)
    private fun run(clearFailureThrows: Boolean?) {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val address = "AA:BB:CC:DD:EE:01"
        val names = listOf("shot_safety", "machine_write_safety", "curves")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val first = decode("8340FE0A00071E0A00071E0A00071E0A00071E3D") as SleepPart
        val second = decode("83800A00071E0A00071E0A00071E10") as SleepPart
        val settings = decode("830113FD5C007D0F350019006E") as Settings
        val change = MachineSettingChange.BrewTemperature(93)
        val expected = requireNotNull(WeeklySleepSchedule.fromReadback(first, second))
        val target = WeeklySleepSchedule(expected.days.mapIndexed { index, day ->
            if (index == 0) day.copy(enabled = !day.enabled) else day
        })
        var checkedFixtures = 0
        var confirmations = 0
        var blockedEntries = 0
        var fakeDispatches = 0
        for (kind in MachineWriteRecoveryState.Kind.entries.filter { it in setOf(MachineWriteRecoveryState.Kind.SETTING, MachineWriteRecoveryState.Kind.CUP_RESET, MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE, MachineWriteRecoveryState.Kind.SLEEP_NOW) }) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_write_readback_${id}_$it" }
            val folder = File(app.cacheDir, "service-write-readback-$id")
            val journal = TraceStore(folder)
            var failure: Throwable? = null
            var expectedClearMessage: String? = null
            var completed = false
            val clearFailureTag = when(kind) {
                MachineWriteRecoveryState.Kind.SETTING -> "settings.recovery_clear_failed"
                MachineWriteRecoveryState.Kind.CUP_RESET -> "cups.recovery_clear_failed"
                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> "sleep_schedule.recovery_clear_failed"
                MachineWriteRecoveryState.Kind.SLEEP_NOW -> "sleep.recovery_clear_failed"
                else -> error("Invalid fixture")
            }
            var systemLookups = 0
            var permissionChecks = 0
            var componentCalls = 0
            val context = object : ContextWrapper(app) {
                override fun getNoBackupFilesDir():File=File(folder,"no-backup")
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? { systemLookups++; error("No system service: $name") }
                override fun checkSelfPermission(permission: String): Int { permissionChecks++; error("No BLE permission access") }
                override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(requireNotNull(fixtures[name]), mode)
                override fun startService(intent: Intent): android.content.ComponentName? { componentCalls++; error("No component dispatch") }
                override fun startForegroundService(intent: Intent): android.content.ComponentName? { componentCalls++; error("No component dispatch") }
                override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean { componentCalls++; error("No component dispatch") }
                override fun startActivity(intent: Intent) { componentCalls++; error("No component dispatch") }
                override fun startActivity(intent: Intent, options: Bundle?) { componentCalls++; error("No component dispatch") }
                override fun stopService(intent: Intent): Boolean { componentCalls++; error("No component dispatch") }
            }
            try {
                val selected = CurveCatalog.profiles.first()
                check(app.curves.resolve(selected.id, true, 7) == selected && app.curves.validated(selected))
                check(app.getSharedPreferences(fixtures.getValue("curves"), Context.MODE_PRIVATE).edit()
                    .putString("selected", selected.id).commit())
                check(app.getSharedPreferences(fixtures.getValue("shot_safety"), Context.MODE_PRIVATE).edit()
                    .putBoolean("unresolved_shot", false).commit())
                val before = fixtures.mapValues { (_, name) -> app.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap() }
                val prefs = app.getSharedPreferences(fixtures.getValue("machine_write_safety"), Context.MODE_PRIVATE)
                check(prefs.all.isEmpty())
                test.runOnMainSync {
                    val services = mutableListOf<MobileService>()
                    var hub: NativeDeviceHub? = null
                    try {
                        fun attach(): MobileService = MobileService().also { instance ->
                            services += instance
                            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                                .apply { isAccessible = true }.invoke(instance, context)
                            Service::class.java.getDeclaredField("mApplication").apply { isAccessible = true }.set(instance, app)
                            field(instance, "logs").set(instance, journal)
                            field(instance, "mock").set(instance, null)
                            check(!field(instance, "running").getBoolean(instance))
                        }
                        val instance = attach()
                        var clearWritable = clearFailureThrows == null
                        var clearAttempts = 0
                        var registrationAttempts = 0
                        if (clearFailureThrows != null) {
                            val failingRecovery = MachineWriteRecoveryState(object : MachineWriteRecoveryState.Storage {
                                override fun read() = MachineWriteRecoveryState.Record(null,null)
                                override fun write(record: MachineWriteRecoveryState.Record): Boolean {
                                    if (!record.pending) {
                                        clearAttempts++
                                        if (!clearWritable) {
                                            if (clearFailureThrows) throw IllegalStateException("Injected clear failure")
                                            return false
                                        }
                                    } else registrationAttempts++
                                    return prefs.edit().putString("pending_kind",record.kind?.name)
                                        .putString("pending_address",record.address).commit()
                                }
                            })
                            field(instance, "machineWriteRecovery\$delegate").set(instance, lazy { failingRecovery })
                        }
                        val readbackCallback = MobileService::class.java.getDeclaredMethod("onCoffeeFrame", HoyiMessage::class.java)
                            .apply { isAccessible = true }
                        val owner = NativeDeviceHub(context, onCoffee = { frame -> readbackCallback.invoke(instance, frame) })
                        hub = owner
                        field(instance, "hub").set(instance, owner)
                        val devices = listOf("coffee", "scale").map { field(owner, it).get(owner) as AndroidDevice }
                        val queues = devices.map { field(it.session, "queue").get(it.session) as GattQueue }
                        val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
                        val recordsAtDispatch = mutableListOf<Map<String, *>>()
                        val statesAtDispatch = mutableListOf<String>()
                        var coffeeCloses = 0
                        var scaleExecutions = 0
                        lateinit var tracker: Any
                        val driver = object : GattDriver {
                            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                                calls += Triple(generation, token, operation)
                                recordsAtDispatch += prefs.all.toMap()
                                statesAtDispatch += requireNotNull(field(tracker, "state").get(tracker)).toString()
                                return true
                            }
                            override fun close(generation: Long) { coffeeCloses++ }
                        }
                        check(queues.none { it.active })
                        field(queues[0], "driver").set(queues[0], GuardedGattDriver(DeviceRole.COFFEE, driver))
                        field(queues[1], "driver").set(queues[1], object : GattDriver {
                            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                                scaleExecutions++; error("No scale dispatch permitted")
                            }
                            override fun close(generation: Long) = Unit
                        })
                        queues[0].open()
                        val session = devices[0].session
                        field(session, "state").set(session, DeviceState.READY)
                        field(session, "activeAddress").set(session, address)
                        val stamp = SystemClock.elapsedRealtime()
                        var now = stamp
                        field(session, "clock").set(session, { now })
                        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
                        val serialNames = listOf("settingsSampleSerial", "cupSettingsSerial", "cupIdleSerial",
                            "firstSleepSerial", "secondSleepSerial", "sleepSampleSerial")
                        val watchdog = MachineWriteWatchdog({ delay, callback -> scheduled += delay to callback }, {
                            val values = serialNames.map { field(instance, it).getLong(instance) }
                            MachineWriteWatchdog.Serials(values[0], values[1], values[2], values[3], values[4], values[5])
                        })
                        field(instance, "writeWatchdog").set(instance, watchdog)
                        val idle = IdleTelemetry(8000, 12000, 0, 0, 0, 0, settings.cupCount, 0, ByteFrame(byteArrayOf()))
                        mapOf("lastIdle" to idle, "lastIdleAtMs" to stamp, "lastSettings" to settings,
                            "lastSettingsAtMs" to stamp, "sleepFirst" to first, "sleepSecond" to second,
                            "sleepFirstAtMs" to stamp, "sleepSecondAtMs" to stamp).forEach { (name, value) -> field(session, name).set(session, value) }
                        listOf("cupSettingsSerial", "cupIdleSerial", "settingsSampleSerial", "firstSleepSerial",
                            "secondSleepSerial", "sleepSampleSerial", "idleSampleSerial").forEach { field(instance, it).setLong(instance, 1) }
                        field(instance, "snapshot").set(instance, MobileSnapshot(coffeeState = DeviceState.READY,
                            scaleState = DeviceState.READY, coffee = idle, coffeeAt = stamp, weightAt = stamp,
                            settings = settings, settingsAt = stamp, sleepFirst = first, sleepSecond = second,
                            sleepFirstAt = stamp, sleepSecondAt = stamp))
                        tracker = requireNotNull(field(instance, when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> "settingsWrite"
                            MachineWriteRecoveryState.Kind.CUP_RESET -> "cupReset"
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> "scheduleWrite"
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> "sleepNow"
                            else -> error("Invalid fixture")
                        }).get(instance))
                        val wire = when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> CoffeeCommands.setting(change)
                            MachineWriteRecoveryState.Kind.CUP_RESET -> CoffeeCommands.resetCupCount()
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> CoffeeCommands.sleepSchedule(target).first()
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> CoffeeCommands.sleepNow()
                            else -> error("Invalid fixture")
                        }.frame.toByteArray()
                        fun request(service: MobileService) = when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> service.changeMachineSetting(change)
                            MachineWriteRecoveryState.Kind.CUP_RESET -> service.resetCupCount(settings.cupCount)
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> service.changeSleepSchedule(expected, target)
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> service.enterSleepNow()
                            else -> error("Invalid fixture")
                        }
                        check(!recovery(instance).pending)
                        check(request(instance) == null) { "$kind did not reach dispatch" }
                        check(calls.size == 1 && recordsAtDispatch.size == 1 && statesAtDispatch == listOf("WRITING"))
                        val operation = calls.single().third as GattOperation.Write
                        check(operation.endpoint == KnownGatt.coffeeWrite && operation.withResponse && operation.bytes.contentEquals(wire))
                        val saved = mapOf("pending_kind" to kind.name, "pending_address" to address)
                        check(recordsAtDispatch.single() == saved && prefs.all == saved)
                        check(scheduled.isEmpty())
                        fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        fun idleBytes(count: Int, sleeping: Boolean = false) = ByteArray(19).also {
                            it[0] = 0x40; it[2] = 0x1F; it[3] = 0x40; it[4] = 0x2E; it[5] = 0xE0.toByte()
                            it[8] = if (sleeping) 1 else 0; it[14] = (count shr 8).toByte(); it[15] = count.toByte()
                        }
                        val settingTarget = hex("830113FD5D007D0F350019006F")
                        val cupTarget = settings.raw.toByteArray().also { it[9] = 0; it[10] = 0 }
                        val planFirstTarget = first.raw.toByteArray().also { it[2] = (it[2].toInt() xor 0x80).toByte() }
                        val frames = when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> listOf(settingTarget)
                            MachineWriteRecoveryState.Kind.CUP_RESET -> listOf(cupTarget, idleBytes(0))
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> listOf(planFirstTarget, second.raw.toByteArray())
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> listOf(idleBytes(settings.cupCount, true))
                            else -> error("Invalid fixture")
                        }
                        fun notify(bytes: ByteArray, generation: Long = queues[0].generation, endpoint: Endpoint = KnownGatt.coffeeNotify) {
                            session.onNotification(generation, endpoint, bytes)
                        }
                        // Matching readbacks seen while WRITING cannot prove this transaction.
                        frames.forEach { notify(it) }
                        check(requireNotNull(field(tracker, "state").get(tracker)).toString() == "WRITING")
                        check(prefs.all == saved && recovery(instance).pending)
                        fun completeLast() {
                            val (generation, token, _) = calls.last()
                            session.onComplete(generation, token, OperationResult.Success())
                        }
                        completeLast()
                        if (kind == MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
                            check(scheduled.isEmpty() && calls.size == 1)
                            now += 500
                            session.tick()
                            check(calls.size == 2 && recordsAtDispatch.all { it == saved })
                            val next = calls.last().third as GattOperation.Write
                            check(next.endpoint == KnownGatt.coffeeWrite && next.withResponse &&
                                next.bytes.contentEquals(CoffeeCommands.sleepSchedule(target)[1].frame.toByteArray()))
                            completeLast()
                        }
                        val dispatches = if (kind == MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) 2 else 1
                        check(calls.size == dispatches && scheduled.size == 1)
                        check(requireNotNull(field(tracker, "state").get(tracker)).toString().startsWith("WAITING_"))
                        check(prefs.all == saved && recovery(instance).pending)
                        val serialsBeforeStale = serialNames.map { field(instance, it).getLong(instance) }
                        frames.forEach { notify(it, queues[0].generation - 1) }
                        frames.forEach { notify(it, endpoint = KnownGatt.coffeeWrite) }
                        check(serialNames.map { field(instance, it).getLong(instance) } == serialsBeforeStale)
                        check(prefs.all == saved && recovery(instance).pending)
                        // Mismatching single-channel evidence or an incomplete pair keeps the gate closed.
                        when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> notify(settings.raw.toByteArray())
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> notify(idleBytes(settings.cupCount))
                            else -> notify(frames.first())
                        }
                        check(requireNotNull(field(tracker, "state").get(tracker)).toString().startsWith("WAITING_"))
                        check(prefs.all == saved && recovery(instance).pending)
                        val remaining = if (frames.size == 2) frames.drop(1) else frames
                        remaining.forEach { notify(it) }
                        check(requireNotNull(field(tracker, "state").get(tracker)).toString() == "CONFIRMED")
                        if (clearFailureThrows == null) check(!recovery(instance).pending && prefs.all.isEmpty())
                        else check(recovery(instance).pending && prefs.all == saved && clearAttempts == 1 && registrationAttempts == 1)
                        val confirmedResource = when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> R.string.service_event_setting_confirmed
                            MachineWriteRecoveryState.Kind.CUP_RESET -> R.string.service_event_cups_confirmed
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> R.string.service_write_schedule_confirmed
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> R.string.service_event_sleep_confirmed
                            else -> error("Invalid fixture")
                        }
                        val clearFailureResource = when(kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> R.string.service_write_setting_clear_failed
                            MachineWriteRecoveryState.Kind.CUP_RESET -> R.string.service_event_cups_clear_failed
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> R.string.service_write_schedule_clear_failed
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> R.string.service_write_sleep_clear_failed
                            else -> error("Invalid fixture")
                        }
                        expectedClearMessage = instance.getString(clearFailureResource)
                        // Every clear failure remains the final visible feedback, including cup confirmation.
                        val lastResource = if (clearFailureThrows == null)
                            confirmedResource else clearFailureResource
                        check(instance.snapshot.messageForDisplay { resource, args -> instance.getString(resource, *args) } == instance.getString(lastResource))
                        val confirmedMessage = instance.snapshot.message
                        scheduled.single().second.invoke()
                        completeLast()
                        check(instance.snapshot.message === confirmedMessage && scheduled.size == 1)
                        check(requireNotNull(field(tracker, "state").get(tracker)).toString() == "CONFIRMED")
                        check(session.state == DeviceState.READY && session.address == address)
                        check(queues[0].active && !queues[0].inFlight && !queues[1].active && coffeeCloses == 0 && scaleExecutions == 0)
                        if (clearFailureThrows != null) {
                            fun blocked(service: MobileService) {
                                val warning = requireNotNull(service.machineControlSafetyMessage)
                                val entries = listOf(service.changeMachineSetting(change), service.resetCupCount(settings.cupCount),
                                    service.changeSleepSchedule(expected,target),service.enterSleepNow(),
                                    service.prepareBrew(selected.id,selected.scaleMode,7),service.startShot(selected.id,selected.scaleMode,7))
                                check(entries.all { it == warning })
                                blockedEntries += entries.size
                                check(recovery(service).pending && recovery(service).kind == kind && recovery(service).address == address)
                                check(prefs.all == saved && calls.size == dispatches)
                            }
                            blocked(instance)
                            blocked(attach()) // A separate real storage adapter reloads the retained record.
                            check(clearAttempts == 1 && registrationAttempts == 1)
                            clearWritable = true
                            frames.forEach { notify(it) } // Only new readbacks retry the local clear, never a wire command.
                            check(clearAttempts == 2 && registrationAttempts == 1)
                        }
                        check(!recovery(instance).pending && prefs.all.isEmpty() && calls.size == dispatches)
                        val reloaded = attach()
                        check(!recovery(reloaded).pending && prefs.all.isEmpty())
                        confirmations++
                        fakeDispatches += dispatches
                        before.filterKeys { it != "machine_write_safety" }.forEach { (name, values) ->
                            check(app.getSharedPreferences(fixtures.getValue(name), Context.MODE_PRIVATE).all == values)
                        }
                        check(systemLookups == 0 && permissionChecks == 0 && componentCalls == 0)
                        checkedFixtures++
                    } catch (error: Throwable) { failure = error }
                    finally {
                        hub?.let { owner ->
                            listOf("coffee", "scale").forEach { name -> runCatching { (field(owner, name).get(owner) as AndroidDevice).close() } }
                            runCatching { owner.scanner.close() }
                            (field(owner, "handler").get(owner) as Handler).removeCallbacks(field(owner, "ticker").get(owner) as Runnable)
                        }
                        services.forEach { (field(it, "handler").get(it) as Handler).removeCallbacksAndMessages(null) }
                    }
                }
                check(systemLookups == 0 && permissionChecks == 0 && componentCalls == 0)
                failure?.let { throw it }
                completed = true
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                journal.close()
                var cleanupFailure: Throwable? = null
                fun checked(action: () -> Unit) {
                    try { action() } catch (error: Throwable) {
                        val first = cleanupFailure
                        if (first == null) cleanupFailure = error
                        else if (first !== error) first.addSuppressed(error)
                    }
                }
                checked { check(journal.awaitTermination(5, TimeUnit.SECONDS)) }
                if (clearFailureThrows != null && completed) checked {
                    val failures = folder.listFiles().orEmpty().filter { it.extension == "jsonl" }
                        .flatMap { it.readLines() }.map { org.json.JSONObject(it) }
                        .filter { it.getString("kind") == clearFailureTag }
                    check(failures.size == 1 && failures.single().getJSONObject("fields").getString("message") == expectedClearMessage)
                }
                checked { check(folder.deleteRecursively()) }
                fixtures.values.forEach { name -> checked { check(app.deleteSharedPreferences(name)) } }
                original.forEach { (name, values) -> checked { check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) } }
                cleanupFailure?.let { error ->
                    val first = failure
                    if (first == null) throw error
                    if (first !== error) first.addSuppressed(error)
                }
            }
        }
        check(checkedFixtures == 4 && confirmations == 4 && fakeDispatches == 5 && blockedEntries == if (clearFailureThrows == null) 0 else 48)
    }
}

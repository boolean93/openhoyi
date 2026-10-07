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

/** Detached real entry/callback/persistence paths with synthetic evidence and guarded fake GATT cancellation. */
internal class ServicePreheatCancellationChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun decode(hex: String) = (HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
    private fun recovery(service: MobileService) =
        ((field(service, "machineWriteRecovery\$delegate").get(service) as Lazy<*>).value as MachineWriteRecoveryState)

    private enum class CheckMode { OUTCOMES, BLOCKED, QUEUED_MANUAL, QUEUED_OWNER, QUEUED_PREP, TIMEOUT_OWNER }
    fun run() = runChecks(CheckMode.OUTCOMES)
    fun runBlocked() = runChecks(CheckMode.BLOCKED)
    fun runQueuedManual() = runChecks(CheckMode.QUEUED_MANUAL)
    fun runQueuedOwner() = runChecks(CheckMode.QUEUED_OWNER)
    fun runQueuedPrepare() = runChecks(CheckMode.QUEUED_PREP)
    fun runTimeoutOwner() = runChecks(CheckMode.TIMEOUT_OWNER)
    private fun runChecks(checkMode:CheckMode) {
        val blockedOnly=checkMode==CheckMode.BLOCKED
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
        var blockedEntries = 0
        var recoveredAcknowledgements=0
        var retainedDisconnected=0
        var fakeWrites=0
        var blockedCancels=0
        var fakeBarriers=0
        for (ready in listOf(false,true)) for (mode in if(checkMode==CheckMode.OUTCOMES)(0..4).toList() else if(checkMode==CheckMode.TIMEOUT_OWNER)(0..2).toList() else listOf(0)) {
            val kind=MachineWriteRecoveryState.Kind.BREW_WAIT
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_preheat_cancel_${id}_$it" }
            val folder = File(app.cacheDir, "service-preheat-cancel-$id")
            val journal = TraceStore(folder)
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
            var outerFailure:Throwable?=null
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
                var failure: Throwable? = null
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
                        val scheduledPreheat=mutableListOf<Pair<Long,()->Unit>>()
                        if(checkMode==CheckMode.TIMEOUT_OWNER) {
                            val schedule:(Long,()->Unit)->Unit={ delay,action->scheduledPreheat+=delay to action }
                            field(instance,"preheatTimeoutScheduler").set(instance,schedule)
                        }
                        val readback = MobileService::class.java.getDeclaredMethod("onCoffeeFrame",HoyiMessage::class.java).apply { isAccessible=true }
                        val owner = NativeDeviceHub(context,onCoffee={ readback.invoke(instance,it) })
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
                                if(mode==4 && calls.size==2)throw IllegalStateException("Injected cancel dispatch uncertainty")
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
                        tracker = field(instance,"brewPreparation").get(instance) as BrewPreparation
                        val preparation=tracker as BrewPreparation
                        val saved=mapOf("pending_kind" to kind.name,"pending_address" to address)
                        fun assertPending() {
                            check(recovery(instance).pending && recovery(instance).kind==kind && recovery(instance).address==address)
                            check(prefs.all==saved && calls.size==2 && scaleExecutions==0)
                        }
                        fun notifyIdle(rawTemperature:Int) {
                            val bytes=ByteArray(19).also {
                                it[0]=0x40;it[2]=(rawTemperature shr 8).toByte();it[3]=rawTemperature.toByte()
                                it[4]=0x2E;it[5]=0xE0.toByte();it[14]=(settings.cupCount shr 8).toByte();it[15]=settings.cupCount.toByte()
                            }
                            session.onNotification(queues[0].generation,KnownGatt.coffeeNotify,bytes)
                        }
                        if(checkMode==CheckMode.QUEUED_PREP) {
                            queues[0].enqueue(GattOperation.Discover,5000) { check(it is OperationResult.Success) }
                            val blocker=calls.single();check(blocker.third==GattOperation.Discover)
                            check(instance.prepareBrew(selected.id,selected.scaleMode,7)==null)
                            check(preparation.state==BrewPreparation.State.WRITING && calls.size==1 && prefs.all==saved)
                            val originalOwner=requireNotNull(recovery(instance).captureOwnership())
                            if(ready) {
                                check(app.getSharedPreferences(fixtures.getValue("curves"),Context.MODE_PRIVATE).edit()
                                    .putString("selected",CurveCatalog.profiles[2].id).commit())
                            } else {
                                check(recovery(instance).clear());check(recovery(instance).arm(kind,address))
                                check(!recovery(instance).owns(originalOwner))
                            }
                            notifyIdle(8000)
                            check(TelemetryFreshness.isFresh(field(session,"lastIdleAtMs").get(session) as Long?,SystemClock.elapsedRealtime()))
                            check(preparation.permitsWrite(field(preparation,"serial").getLong(preparation),selected.temperatureC))
                            session.onComplete(blocker.first,blocker.second,OperationResult.Success())
                            check(preparation.state==BrewPreparation.State.FAILED && calls.size==1 && calls.none { it.third is GattOperation.Write })
                            check(instance.snapshot.messageForDisplay { r,a->instance.getString(r,*a) }==instance.getString(R.string.service_shot_preheat_not_written))
                            check(recovery(instance).pending && prefs.all==saved)
                            val after=instance.snapshot
                            session.onComplete(blocker.first,blocker.second,OperationResult.Success());session.tick()
                            check(instance.snapshot===after && calls.size==1 && preparation.state==BrewPreparation.State.FAILED && prefs.all==saved)
                            val reloaded=attach();check(recovery(reloaded).kind==kind && recovery(reloaded).address==address && recovery(reloaded).pending)
                            check(systemLookups==0 && permissionChecks==0 && componentCalls==0 && scaleExecutions==0)
                            fakeBarriers++;checkedFixtures++
                            return@runOnMainSync
                        }
                        check(instance.prepareBrew(selected.id,selected.scaleMode,7)==null)
                        check(calls.size==1 && statesAtDispatch==listOf("WRITING") && recordsAtDispatch.single()==saved)
                        val initial=calls.single()
                        val initialWrite=initial.third as GattOperation.Write
                        check(initialWrite.endpoint==KnownGatt.coffeeWrite && initialWrite.withResponse &&
                            initialWrite.bytes.contentEquals(CoffeeCommands.brewWait(selected.temperatureC).frame.toByteArray()))
                        session.onComplete(initial.first,initial.second,OperationResult.Success())
                        check(preparation.state==BrewPreparation.State.WAITING_TEMP)
                        if(ready) {
                            notifyIdle(selected.temperatureC*100+settings.brewCompensationTenthsC*10)
                            check(preparation.state==BrewPreparation.State.READY)
                        }
                        if(checkMode==CheckMode.TIMEOUT_OWNER) {
                            check(scheduledPreheat.size==1 && scheduledPreheat.single().first==BrewPreparationWatchdog.TIMEOUT_MS)
                            check(preparation.state==if(ready)BrewPreparation.State.READY else BrewPreparation.State.WAITING_TEMP)
                            notifyIdle(8000)
                            if(mode==1) {
                                val old=requireNotNull(recovery(instance).captureOwnership())
                                check(recovery(instance).clear());check(recovery(instance).arm(kind,address))
                                check(!recovery(instance).owns(old))
                            }
                            if(mode==2)field(instance,"hub").set(instance,null)
                            val expiry=scheduledPreheat.single().second
                            expiry()
                            if(mode==0) {
                                check(preparation.state==BrewPreparation.State.CANCELLING && calls.size==2)
                                val c=calls.last();val op=c.third as GattOperation.Write
                                check(op.endpoint==KnownGatt.coffeeWrite && op.withResponse && op.bytes.contentEquals(CoffeeCommands.brewWait(0).frame.toByteArray()))
                                session.onComplete(c.first,c.second,OperationResult.Success())
                                check(preparation.state==BrewPreparation.State.CANCEL_WRITTEN)
                            }else check(preparation.state==BrewPreparation.State.UNKNOWN && calls.size==1)
                            check(recovery(instance).pending && prefs.all==saved)
                            val after=instance.snapshot;val count=calls.size
                            expiry();session.onComplete(initial.first,initial.second,OperationResult.Success());session.tick()
                            check(calls.size==count && instance.snapshot===after && scheduledPreheat.size==1 && prefs.all==saved)
                            val reloaded=attach();check(recovery(reloaded).kind==kind && recovery(reloaded).address==address && recovery(reloaded).pending)
                            check(systemLookups==0 && permissionChecks==0 && componentCalls==0 && scaleExecutions==0)
                            fakeWrites+=calls.count { it.third is GattOperation.Write };checkedFixtures++
                            return@runOnMainSync
                        }
                        val idleBefore=field(instance,"idleSampleSerial").getLong(instance)
                        if(blockedOnly) {
                            val base=instance.snapshot
                            val baseIdle=base.coffee as IdleTelemetry
                            val prepState=preparation.state
                            val prepToken=field(preparation,"serial").getLong(preparation)
                            val baseline=field(instance,"recoveryAfterBrewWaitIdleSerial").getLong(instance)
                            val passive=field(instance,"passiveShot").get(instance) as PassiveShotDetector
                            val shotDelegate=field(instance,"shotRecovery\$delegate").get(instance)
                            val extractionState=owner.extraction.state
                            for(block in 0..9) {
                                val now=SystemClock.elapsedRealtime()
                                val resource=when(block) {
                                    0 -> { field(passive,"active").setBoolean(passive,true);R.string.service_shot_cancel_manual_block }
                                    1 -> { field(session,"activeAddress").set(session,"AA:BB:CC:DD:EE:02");R.string.service_shot_cancel_original_device }
                                    2 -> { field(instance,"snapshot").set(instance,base.copy(coffeeState=DeviceState.FAILED));R.string.cancel_preheat_block_coffee_not_ready }
                                    3 -> { field(instance,"snapshot").set(instance,base.copy(coffee=null));R.string.cancel_preheat_block_idle_missing }
                                    4 -> { field(instance,"snapshot").set(instance,base.copy(coffeeAt=null));R.string.cancel_preheat_block_idle_stale }
                                    5 -> { field(instance,"snapshot").set(instance,base.copy(coffeeAt=now-60_000));R.string.cancel_preheat_block_idle_stale }
                                    6 -> { field(instance,"snapshot").set(instance,base.copy(coffeeAt=Long.MAX_VALUE));R.string.cancel_preheat_block_idle_stale }
                                    7 -> { field(instance,"snapshot").set(instance,base.copy(coffee=baseIdle.copy(sleepStateRaw=1),coffeeAt=now));R.string.cancel_preheat_block_not_awake }
                                    8 -> {
                                        val pending=ShotRecoveryState(object:ShotRecoveryState.Storage {
                                            override fun read()=ShotRecoveryState.Record(true,address)
                                            override fun write(record:ShotRecoveryState.Record):Boolean=error("Block check cannot alter shot storage")
                                        })
                                        field(instance,"shotRecovery\$delegate").set(instance,lazy { pending })
                                        R.string.cancel_preheat_block_extraction_unsettled
                                    }
                                    else -> { field(owner.extraction,"state").set(owner.extraction,ExtractionState.RUNNING);R.string.cancel_preheat_block_extraction_unsettled }
                                }
                                try {
                                    val injected=instance.snapshot
                                    check(instance.cancelBrewPreparation()==instance.getString(resource)) { "ready=$ready block=$block" }
                                    check(instance.snapshot===injected && preparation.state==prepState)
                                    check(field(preparation,"serial").getLong(preparation)==prepToken)
                                    check(field(instance,"recoveryAfterBrewWaitIdleSerial").getLong(instance)==baseline)
                                    check(calls.size==1 && recordsAtDispatch.size==1 && statesAtDispatch==listOf("WRITING"))
                                    check(recovery(instance).pending && recovery(instance).kind==kind && recovery(instance).address==address && prefs.all==saved)
                                    blockedCancels++
                                } finally {
                                    field(instance,"snapshot").set(instance,base)
                                    field(passive,"active").setBoolean(passive,false)
                                    field(session,"activeAddress").set(session,address)
                                    field(instance,"shotRecovery\$delegate").set(instance,shotDelegate)
                                    field(owner.extraction,"state").set(owner.extraction,extractionState)
                                }
                            }
                            check(queues[0].active && !queues[0].inFlight && coffeeCloses==0 && scaleExecutions==0)
                            before.filterKeys { it!="machine_write_safety" }.forEach { (name,values)->
                                check(app.getSharedPreferences(fixtures.getValue(name),Context.MODE_PRIVATE).all==values)
                            }
                            check(systemLookups==0 && permissionChecks==0 && componentCalls==0)
                            fakeWrites+=calls.size;checkedFixtures++
                            return@runOnMainSync
                        }
                        if(checkMode in setOf(CheckMode.QUEUED_MANUAL,CheckMode.QUEUED_OWNER)) {
                            // Test-only lifecycle barrier has no device command bytes and only a fake driver.
                            var barrierCompleted=0
                            queues[0].enqueue(GattOperation.Discover,5000) { check(it is OperationResult.Success);barrierCompleted++ }
                            check(calls.size==2 && calls.last().third==GattOperation.Discover)
                            val barrier=calls.last()
                            notifyIdle(8000)
                            val queuedBaseline=field(instance,"idleSampleSerial").getLong(instance)
                            check(instance.cancelBrewPreparation()==null && preparation.state==BrewPreparation.State.CANCELLING)
                            check(calls.size==2 && field(instance,"recoveryAfterBrewWaitIdleSerial").getLong(instance)==queuedBaseline)
                            val passive=field(instance,"passiveShot").get(instance) as PassiveShotDetector
                            check(!passive.active && instance.snapshot.coffee is IdleTelemetry)
                            try {
                                notifyIdle(8000) // Freshness must not accidentally mask the missing manual check.
                                check(instance.brewWaitCancelBlock==null && recovery(instance).matchesDevice(session.address))
                                check(TelemetryFreshness.isFresh(field(session,"lastIdleAtMs").get(session) as Long?,SystemClock.elapsedRealtime()))
                                check(preparation.permitsWrite(field(preparation,"serial").getLong(preparation),0))
                                if(checkMode==CheckMode.QUEUED_OWNER) {
                                    val old=requireNotNull(recovery(instance).captureOwnership())
                                    check(recovery(instance).clear());check(recovery(instance).arm(kind,address))
                                    check(!recovery(instance).owns(old) && recovery(instance).matchesDevice(session.address))
                                }else field(passive,"active").setBoolean(passive,true)
                                session.onComplete(barrier.first,barrier.second,OperationResult.Success())
                                check(barrierCompleted==1 && preparation.state==BrewPreparation.State.UNKNOWN)
                                check(instance.snapshot.messageForDisplay { id,args->instance.getString(id,*args) }==
                                    instance.getString(R.string.service_shot_cancel_unknown))
                                check(calls.size==2 && calls.count { it.third is GattOperation.Write }==1)
                                check(queues[0].active && !queues[0].inFlight && session.state==DeviceState.READY)
                                assertPending()
                            } finally { field(passive,"active").setBoolean(passive,false) }
                            val after=instance.snapshot
                            session.onComplete(initial.first,initial.second,OperationResult.Success())
                            session.onComplete(barrier.first,barrier.second,OperationResult.Success())
                            check(barrierCompleted==1 && instance.snapshot===after && preparation.state==BrewPreparation.State.UNKNOWN)
                            fun blocked(service:MobileService) {
                                val warning=requireNotNull(service.machineControlSafetyMessage)
                                val entries=listOf(service.changeMachineSetting(change),service.resetCupCount(settings.cupCount),
                                    service.changeSleepSchedule(expected,target),service.enterSleepNow(),
                                    service.prepareBrew(selected.id,selected.scaleMode,7),service.startShot(selected.id,selected.scaleMode,7))
                                check(entries.all { it==warning });blockedEntries+=entries.size
                                check(recovery(service).pending && recovery(service).kind==kind && recovery(service).address==address && prefs.all==saved)
                            }
                            blocked(instance);blocked(attach())
                            check(coffeeCloses==0 && scaleExecutions==0 && calls.size==2)
                            before.filterKeys { it!="machine_write_safety" }.forEach { (name,values)->
                                check(app.getSharedPreferences(fixtures.getValue(name),Context.MODE_PRIVATE).all==values)
                            }
                            check(systemLookups==0 && permissionChecks==0 && componentCalls==0)
                            fakeWrites+=calls.count { it.third is GattOperation.Write };fakeBarriers++;checkedFixtures++
                            return@runOnMainSync
                        }
                        check(instance.cancelBrewPreparation()==null)
                        check(calls.size==2 && statesAtDispatch==listOf("WRITING","CANCELLING") && recordsAtDispatch.all { it==saved })
                        check(field(instance,"recoveryAfterBrewWaitIdleSerial").getLong(instance)==idleBefore)
                        val cancel=calls.last();val cancelWrite=cancel.third as GattOperation.Write
                        check(cancelWrite.endpoint==KnownGatt.coffeeWrite && cancelWrite.withResponse &&
                            cancelWrite.bytes.contentEquals(CoffeeCommands.brewWait(0).frame.toByteArray()))
                        if(mode!=4) {
                            check(preparation.state==BrewPreparation.State.CANCELLING)
                            instance.acknowledgeManualSafety();assertPending()
                            val result=when(mode) {
                                0->OperationResult.Success()
                                1->OperationResult.Failed("definite fake rejection")
                                2->OperationResult.Cancelled("fake cancellation")
                                else->OperationResult.Unknown("fake write callback uncertainty")
                            }
                            session.onComplete(cancel.first,cancel.second,result)
                        }
                        val expectedState=if(mode==0)BrewPreparation.State.CANCEL_WRITTEN else BrewPreparation.State.UNKNOWN
                        check(preparation.state==expectedState);assertPending()
                        val resource=if(mode==0)R.string.service_shot_cancel_written else R.string.service_shot_cancel_unknown
                        check(instance.snapshot.messageForDisplay { id,args->instance.getString(id,*args) }==instance.getString(resource))
                        val feedback=instance.snapshot
                        session.onComplete(initial.first,initial.second,OperationResult.Success())
                        session.onComplete(cancel.first,cancel.second,OperationResult.Success())
                        check(preparation.state==expectedState && instance.snapshot===feedback);assertPending()
                        instance.acknowledgeManualSafety();assertPending()
                        check(instance.snapshot.messageForDisplay { id,args->instance.getString(id,*args) }==
                            instance.getString(R.string.recovery_event_brew_wait_recovery_waiting))
                        fun blocked(service:MobileService) {
                            val warning=requireNotNull(service.machineControlSafetyMessage)
                            val entries=listOf(service.changeMachineSetting(change),service.resetCupCount(settings.cupCount),
                                service.changeSleepSchedule(expected,target),service.enterSleepNow(),
                                service.prepareBrew(selected.id,selected.scaleMode,7),service.startShot(selected.id,selected.scaleMode,7))
                            check(entries.all { it==warning });blockedEntries+=entries.size
                            check(recovery(service).pending && recovery(service).kind==kind && prefs.all==saved)
                        }
                        blocked(instance)
                        val reloaded=attach();blocked(reloaded)
                        if(mode==4) {
                            check(session.state==DeviceState.FAILED && session.address==null && !queues[0].active && coffeeCloses==1)
                            notifyIdle(8000)
                            check(field(instance,"idleSampleSerial").getLong(instance)==idleBefore)
                            instance.acknowledgeManualSafety();assertPending()
                            retainedDisconnected++
                        } else {
                            check(session.state==DeviceState.READY && queues[0].active && !queues[0].inFlight && coffeeCloses==0)
                            notifyIdle(8000)
                            check(field(instance,"idleSampleSerial").getLong(instance)>idleBefore)
                            assertPending() // Fresh awake telemetry does not itself prove cancellation or clear.
                            instance.acknowledgeManualSafety()
                            check(!recovery(instance).pending && preparation.state==BrewPreparation.State.IDLE)
                            check(prefs.getString("pending_kind",null)==null && prefs.getString("pending_address",null)==null)
                            check(!recovery(attach()).pending && calls.size==2)
                            recoveredAcknowledgements++
                        }
                        before.filterKeys { it != "machine_write_safety" }.forEach { (name, values) ->
                            check(app.getSharedPreferences(fixtures.getValue(name), Context.MODE_PRIVATE).all == values)
                        }
                        check(systemLookups == 0 && permissionChecks == 0 && componentCalls == 0)
                        fakeWrites+=calls.size
                        checkedFixtures++
                    } catch (error: Throwable) { failure = error }
                    finally {
                        fun cleaned(action:()->Unit) {
                            try { action() } catch(error:Throwable) {
                                if(failure==null)failure=error else if(failure!==error)failure!!.addSuppressed(error)
                            }
                        }
                        hub?.let { owner ->
                            listOf("coffee", "scale").forEach { name -> cleaned { (field(owner, name).get(owner) as AndroidDevice).close() } }
                            cleaned { owner.scanner.close() }
                            cleaned { (field(owner, "handler").get(owner) as Handler).removeCallbacks(field(owner, "ticker").get(owner) as Runnable) }
                        }
                        services.forEach { service -> cleaned { (field(service, "handler").get(service) as Handler).removeCallbacksAndMessages(null) } }
                    }
                }
                try { check(systemLookups == 0 && permissionChecks == 0 && componentCalls == 0) }
                catch(error:Throwable) {
                    if(failure==null)failure=error else if(failure!==error)failure!!.addSuppressed(error)
                }
                failure?.let { throw it }
            } catch(error:Throwable) { outerFailure=error;throw error }
            finally {
                var cleanupFailure:Throwable?=null
                fun cleaned(action:()->Unit) {
                    try { action() } catch(error:Throwable) {
                        val primary=outerFailure?:cleanupFailure
                        if(primary==null)cleanupFailure=error else if(primary!==error)primary.addSuppressed(error)
                    }
                }
                cleaned { journal.close() }
                cleaned { check(journal.awaitTermination(5, TimeUnit.SECONDS)) }
                cleaned { check(folder.deleteRecursively()) }
                fixtures.values.forEach { name->cleaned { check(app.deleteSharedPreferences(name)) } }
                original.forEach { (name, values)->cleaned { check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) } }
                cleanupFailure?.let { throw it }
            }
        }
        if(checkMode==CheckMode.TIMEOUT_OWNER)check(checkedFixtures==6 && fakeWrites==8 && fakeBarriers==0 && blockedEntries==0)
        else if(checkMode==CheckMode.QUEUED_PREP)check(checkedFixtures==2 && fakeWrites==0 && fakeBarriers==2 && blockedEntries==0)
        else if(checkMode in setOf(CheckMode.QUEUED_MANUAL,CheckMode.QUEUED_OWNER))check(checkedFixtures==2 && blockedEntries==24 && fakeWrites==2 && fakeBarriers==2 &&
            recoveredAcknowledgements==0 && retainedDisconnected==0)
        else if(blockedOnly)check(checkedFixtures==2 && blockedCancels==20 && fakeWrites==2 && blockedEntries==0 &&
            recoveredAcknowledgements==0 && retainedDisconnected==0)
        else check(checkedFixtures == 10 && blockedEntries == 120 && fakeWrites==20 &&
            recoveredAcknowledgements==8 && retainedDisconnected==2)
    }
}

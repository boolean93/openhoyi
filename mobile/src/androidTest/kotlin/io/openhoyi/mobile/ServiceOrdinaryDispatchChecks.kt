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

/** Detached real entry/callback/persistence paths with synthetic evidence and queued fake GATT and live product authorization. */
internal class ServiceOrdinaryDispatchChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun decode(hex: String) = (HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
    private fun recovery(service: MobileService) =
        ((field(service, "machineWriteRecovery\$delegate").get(service) as Lazy<*>).value as MachineWriteRecoveryState)

    fun run()=runChecks(false)
    fun runOwnership()=runChecks(true)
    private fun runChecks(ownership:Boolean) {
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
        var fakeWrites=0
        var fakeBarriers=0
        var clearedReloads=0
        var retainedReloads=0
        val kinds=listOf(MachineWriteRecoveryState.Kind.SETTING,MachineWriteRecoveryState.Kind.CUP_RESET,
            MachineWriteRecoveryState.Kind.SLEEP_NOW,MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE)
        val blocks=listOf("MANUAL","SHOT_RECOVERY","APP_SHOT","PREPARATION","ADDRESS","HUB","NOT_READY")
        val cases=if(ownership) kinds.flatMap { kind->listOf("OWNER_QUEUED","OWNER_FAILED","OWNER_READBACK").map { kind to it } } else kinds.flatMap { kind->(listOf("ALLOW")+blocks).map { kind to it } } +
            blocks.map { MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE to "SECOND_$it" }
        for ((kind,mode) in cases) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_ordinary_dispatch_${id}_$it" }
            val folder = File(app.cacheDir, "service-ordinary-dispatch-$id")
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
                        val owner = NativeDeviceHub(context)
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
                        fun request(service:MobileService)=when(kind) {
                            MachineWriteRecoveryState.Kind.SETTING->service.changeMachineSetting(change)
                            MachineWriteRecoveryState.Kind.CUP_RESET->service.resetCupCount(settings.cupCount)
                            MachineWriteRecoveryState.Kind.SLEEP_NOW->service.enterSleepNow()
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE->service.changeSleepSchedule(expected,target)
                            else->error("Invalid fixture")
                        }
                        fun state()=requireNotNull(field(tracker,"state").get(tracker)).toString()
                        val saved=mapOf("pending_kind" to kind.name,"pending_address" to address)
                        val partial=mode.startsWith("SECOND_")
                        val allowed=mode=="ALLOW"
                        fun complete(call:Triple<Long,Long,GattOperation>)=session.onComplete(call.first,call.second,OperationResult.Success())
                        fun barrier():Triple<Long,Long,GattOperation> {
                            queues[0].enqueue(GattOperation.Discover,1000) { }
                            check(calls.last().third==GattOperation.Discover);fakeBarriers++
                            return calls.last()
                        }
                        val held=if(!partial)barrier() else null
                        now=SystemClock.elapsedRealtime()
                        field(session,"lastIdleAtMs").set(session,now)
                        field(instance,"snapshot").set(instance,instance.snapshot.copy(coffeeAt=now))
                        check(request(instance)==null) { "$kind/$mode did not enqueue" }
                        check(state()=="WRITING" && prefs.all==saved && recovery(instance).pending && scheduled.isEmpty())
                        val originalWrite=if(partial)calls.single() else null
                        val blocker=if(partial) {
                            check((originalWrite!!.third as GattOperation.Write).bytes.contentEquals(wire))
                            complete(originalWrite)
                            check(state()=="WRITING" && scheduled.isEmpty())
                            val b=barrier();now+=500;session.tick()
                            check(calls.size==2 && state()=="WRITING" && scheduled.isEmpty())
                            b
                        } else requireNotNull(held)
                        if(ownership) {
                            val originalOwner=requireNotNull(recovery(instance).captureOwnership())
                            var submitted:Triple<Long,Long,GattOperation>?=null
                            if(mode!="OWNER_QUEUED") {
                                complete(blocker);submitted=calls.last()
                                val op=submitted.third as GattOperation.Write
                                check(op.endpoint==KnownGatt.coffeeWrite && op.withResponse && op.bytes.contentEquals(wire))
                                if(mode=="OWNER_READBACK") {
                                    complete(submitted)
                                    if(kind==MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
                                        now+=500;session.tick()
                                        val secondWrite=calls.last().third as GattOperation.Write
                                        check(secondWrite.endpoint==KnownGatt.coffeeWrite && secondWrite.withResponse &&
                                            secondWrite.bytes.contentEquals(CoffeeCommands.sleepSchedule(target)[1].frame.toByteArray()))
                                        complete(calls.last())
                                    }
                                    check(state().startsWith("WAITING_") && scheduled.size==1)
                                }
                            }
                            check(recovery(instance).clear());check(recovery(instance).arm(kind,address))
                            check(!recovery(instance).owns(originalOwner) && prefs.all==saved)
                            if(mode=="OWNER_QUEUED")complete(blocker)
                            else if(mode=="OWNER_FAILED") {
                                val c=requireNotNull(submitted)
                                session.onComplete(c.first,c.second,OperationResult.Failed("injected known failure"))
                            }else {
                                val read=MobileService::class.java.getDeclaredMethod("onCoffeeFrame",HoyiMessage::class.java).apply { isAccessible=true }
                                when(kind) {
                                    MachineWriteRecoveryState.Kind.SETTING->read.invoke(instance,settings.copy(brewTemperatureC=93))
                                    MachineWriteRecoveryState.Kind.CUP_RESET->{read.invoke(instance,settings.copy(cupCount=0));read.invoke(instance,idle.copy(cupCount=0))}
                                    MachineWriteRecoveryState.Kind.SLEEP_NOW->read.invoke(instance,idle.copy(sleepStateRaw=1))
                                    else->{read.invoke(instance,decode("83407E0A00071E0A00071E0A00071E0A00071E3D"));read.invoke(instance,second)}
                                }
                            }
                            check(state()==if(mode=="OWNER_READBACK")"CONFIRMED" else "FAILED")
                            check(recovery(instance).pending && recovery(instance).kind==kind && prefs.all==saved)
                            val writes=calls.count { it.third is GattOperation.Write }
                            check(writes==if(mode=="OWNER_QUEUED")0 else if(mode=="OWNER_READBACK" && kind==MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE)2 else 1)
                            check(calls.count { it.third==GattOperation.Discover }==1)
                            val finalState=state();val finalCount=calls.size
                            complete(blocker);submitted?.let(::complete);session.tick()
                            check(state()==finalState && calls.size==finalCount && prefs.all==saved)
                            val reloaded=attach()
                            check(recovery(reloaded).kind==kind && recovery(reloaded).address==address && recovery(reloaded).pending)
                            check(reloaded.machineControlSafetyMessage!=null);retainedReloads++
                            check(systemLookups==0 && permissionChecks==0 && componentCalls==0 && scaleExecutions==0)
                            fakeWrites+=writes;checkedFixtures++
                            return@runOnMainSync
                        }
                        // With its own durable record present, the request remains permitted before injection.
                        val permitRequest=when(kind) {
                            MachineWriteRecoveryState.Kind.SETTING->MachineWriteDispatchPermit.Request.Setting(tracker as SettingsWriteTracker)
                            MachineWriteRecoveryState.Kind.CUP_RESET->MachineWriteDispatchPermit.Request.CupReset(tracker as CupResetTracker)
                            MachineWriteRecoveryState.Kind.SLEEP_NOW->MachineWriteDispatchPermit.Request.Sleep(tracker as SleepNowTracker)
                            else->MachineWriteDispatchPermit.Request.Schedule(tracker as SleepScheduleWriteTracker)
                        }
                        val trackerToken=field(tracker,"serial").getLong(tracker)
                        val permit=MobileService::class.java.getDeclaredMethod("permitsOrdinaryWrite",NativeDeviceHub::class.java,
                            String::class.java,Long::class.javaPrimitiveType,MachineWriteDispatchPermit.Request::class.java).apply { isAccessible=true }
                        check(permit.invoke(instance,owner,address,trackerToken,permitRequest)==true)
                        check(now-requireNotNull(field(session,"lastIdleAtMs").get(session) as Long)<=1500)
                        val passive=field(instance,"passiveShot").get(instance)!!
                        val preparation=field(instance,"brewPreparation").get(instance) as BrewPreparation
                        val shotRecord=((field(instance,"shotRecovery\$delegate").get(instance) as Lazy<*>).value as ShotRecoveryState)
                        val injection=mode.removePrefix("SECOND_")
                        when(injection) {
                            "MANUAL"->field(passive,"active").setBoolean(passive,true)
                            "SHOT_RECOVERY"->check(shotRecord.arm(address))
                            "APP_SHOT"->field(owner.extraction,"state").set(owner.extraction,ExtractionState.STARTING)
                            "PREPARATION"->field(preparation,"state").set(preparation,BrewPreparation.State.WAITING_TEMP)
                            "ADDRESS"->field(session,"activeAddress").set(session,"AA:BB:CC:DD:EE:02")
                            "HUB"->field(instance,"hub").set(instance,null)
                            "NOT_READY"->field(instance,"snapshot").set(instance,instance.snapshot.copy(coffeeState=DeviceState.SYNCHRONIZING))
                        }
                        check(permit.invoke(instance,owner,address,trackerToken,permitRequest)==allowed)
                        complete(blocker)
                        if(allowed) {
                            check(state()=="WRITING" && scheduled.isEmpty())
                            val op=calls.last().third as GattOperation.Write
                            check(op.endpoint==KnownGatt.coffeeWrite && op.withResponse && op.bytes.contentEquals(wire))
                            complete(calls.last())
                            if(kind==MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
                                check(state()=="WRITING" && scheduled.isEmpty());now+=500;session.tick()
                                val secondOp=calls.last().third as GattOperation.Write
                                check(secondOp.endpoint==KnownGatt.coffeeWrite && secondOp.withResponse &&
                                    secondOp.bytes.contentEquals(CoffeeCommands.sleepSchedule(target)[1].frame.toByteArray()))
                                complete(calls.last())
                            }
                            check(state().startsWith("WAITING_") && scheduled.size==1 && prefs.all==saved)
                        } else if(partial) {
                            check(state()=="UNKNOWN" && scheduled.isEmpty() && prefs.all==saved && recovery(instance).pending)
                            check(instance.snapshot.messageForDisplay { r,a->instance.getString(r,*a) }==instance.getString(R.string.service_write_schedule_partial_unknown))
                        } else {
                            check(state()=="FAILED" && scheduled.isEmpty() && !recovery(instance).pending && prefs.all.isEmpty())
                        }
                        // Restore the injected product state; never retry a previously rejected write.
                        when(injection) {
                            "MANUAL"->field(passive,"active").setBoolean(passive,false)
                            "SHOT_RECOVERY"->check(shotRecord.clear())
                            "APP_SHOT"->field(owner.extraction,"state").set(owner.extraction,ExtractionState.IDLE)
                            "PREPARATION"->field(preparation,"state").set(preparation,BrewPreparation.State.IDLE)
                            "ADDRESS"->field(session,"activeAddress").set(session,address)
                            "HUB"->field(instance,"hub").set(instance,owner)
                            "NOT_READY"->field(instance,"snapshot").set(instance,instance.snapshot.copy(coffeeState=DeviceState.READY))
                        }
                        val writes=calls.count { it.third is GattOperation.Write }
                        val expectedWrites=if(allowed) { if(kind==MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE)2 else 1 } else if(partial)1 else 0
                        check(writes==expectedWrites && calls.count { it.third==GattOperation.Discover }==1)
                        check(recordsAtDispatch.filterIndexed { i,_->calls[i].third is GattOperation.Write }.all { it==saved })
                        check(statesAtDispatch.filterIndexed { i,_->calls[i].third is GattOperation.Write }.all { it=="WRITING" })
                        val callCount=calls.size;val finalState=state();val finalPrefs=prefs.all.toMap()
                        complete(blocker);originalWrite?.let(::complete)
                        now+=500;session.tick()
                        check(calls.size==callCount && state()==finalState && prefs.all==finalPrefs)
                        check(scheduled.size==if(allowed)1 else 0)
                        val reloaded=attach()
                        if(allowed || partial) {
                            check(recovery(reloaded).kind==kind && recovery(reloaded).address==address)
                            check(reloaded.machineControlSafetyMessage!=null && recovery(reloaded).pending);retainedReloads++
                        } else { check(!recovery(reloaded).pending);clearedReloads++ }
                        check(session.state==DeviceState.READY && session.address==address && queues[0].active && !queues[0].inFlight)
                        check(!queues[1].active && coffeeCloses==0 && scaleExecutions==0)
                        before.filterKeys { it!="machine_write_safety" }.forEach { (name,values)->
                            check(app.getSharedPreferences(fixtures.getValue(name),Context.MODE_PRIVATE).all==values)
                        }
                        check(systemLookups==0 && permissionChecks==0 && componentCalls==0)
                        fakeWrites+=writes;checkedFixtures++
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
        if(ownership)check(checkedFixtures==12 && fakeWrites==9 && fakeBarriers==12 && clearedReloads==0 && retainedReloads==12)
        else check(checkedFixtures==39 && fakeWrites==12 && fakeBarriers==39 && clearedReloads==28 && retainedReloads==11)
    }
}

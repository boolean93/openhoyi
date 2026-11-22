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
    fun runV2Settings()=runChecks(ownership=false,v2Settings=true)
    fun runV2Passive()=runChecks(ownership=false,v2Passive=true)
    fun runV2Boiler()=runChecks(ownership=false,v2Boiler=true)
    fun runV2Split()=runChecks(ownership=false,v2Split=true)
    private fun runChecks(ownership:Boolean,v2Settings:Boolean=false,v2Passive:Boolean=false,v2Boiler:Boolean=false,v2Split:Boolean=false) {
        val v2Native=v2Settings || v2Passive || v2Boiler || v2Split
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val address = "AA:BB:CC:DD:EE:01"
        val names = listOf("shot_safety", "machine_write_safety", "temperature_confirmation", "curves") +
            if(v2Boiler) listOf("devices") else emptyList()
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val first = decode("8340FE0A00071E0A00071E0A00071E0A00071E3D") as SleepPart
        val second = decode("83800A00071E0A00071E0A00071E10") as SleepPart
        val settings = decode("830113FD5C007D0F350019006E") as Settings
        val change = MachineSettingChange.Light(false)
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
        val cases=if(v2Split) listOf(MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE to "V2_SPLIT") else if(v2Settings) listOf("V2_DELAY","V2_TEMP","V2_WEEKLY_ENABLE","V2_REPLACED_TEMP","V2_ACK_UNKNOWN").map {
            MachineWriteRecoveryState.Kind.SETTING to it
        } else if(v2Passive) listOf(
            MachineWriteRecoveryState.Kind.CUP_RESET to "V2_CUP_PAIR",
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE to "V2_WEEKLY_PAIR",
            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE to "V2_WEEKLY_PARTIAL",
            MachineWriteRecoveryState.Kind.SLEEP_NOW to "V2_SLEEP_IDLE",
            MachineWriteRecoveryState.Kind.BREW_WAIT to "V2_PREHEAT",
        ) else if(v2Boiler) listOf("V2_BOILER_BREW","V2_BOILER_REBIND","V2_BOILER_REBIND_MANUAL").map {
            MachineWriteRecoveryState.Kind.SETTING to it
        } else if(ownership) kinds.flatMap { kind->listOf("OWNER_QUEUED","OWNER_FAILED","OWNER_READBACK").map { kind to it } } else kinds.flatMap { kind->(listOf("ALLOW")+blocks).map { kind to it } } +
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
                val selected = ServiceCurveFixtures.profile(app)
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
                    var splitCloser:(()->Unit)?=null
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
                        val receive = MobileService::class.java.getDeclaredMethod("onCoffeeFrame",HoyiMessage::class.java).apply { isAccessible=true }
                        val stateSink=MobileService::class.java.getDeclaredMethod("onDeviceState",DeviceRole::class.java,DeviceState::class.java)
                            .apply { isAccessible=true }
                        val owner = NativeDeviceHub(context, legacyVerifiedStartFrames=app.curves.legacyVerifiedStartFrames,
                            trackCoffeeDelivery=v2Native,
                            onState={ role,state -> if(v2Boiler) stateSink.invoke(instance,role,state) },
                            onCoffee={ frame -> if(v2Native) receive.invoke(instance,frame) })
                        hub = owner
                        field(instance, "hub").set(instance, owner)
                        val devices = listOf("coffee", "scale").map { hubDevice(owner, it) }
                        if(v2Split) {
                            check(context.noBackupFilesDir.mkdirs() || context.noBackupFilesDir.isDirectory)
                            val fence=MachineServiceWriterFence()
                            val binding=MobileService::class.java.getDeclaredMethod("splitBinding").apply { isAccessible=true }
                            val broker=CanonicalMachineOperationBroker(RecoveryPreferenceStorage.pinMachine(context),fence) {
                                binding.invoke(instance) as? CanonicalMachineOperationBroker.Binding
                            }
                            field(instance,"machineOperations").set(instance,broker)
                            field(instance,"splitAdmissionEnabled").setBoolean(instance,true)
                            splitCloser={fence.beginDrain(retire=true);fence.finishDrain(safe=true);broker.close()}
                        }
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
                            scaleState = DeviceState.READY, coffee = idle, coffeeAt = stamp, scaleObservation = scaleTestObservation(stamp),
                            settings = settings, settingsAt = stamp, sleepFirst = first, sleepSecond = second,
                            sleepFirstAt = stamp, sleepSecondAt = stamp))
                        tracker = requireNotNull(field(instance, when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> "settingsWrite"
                            MachineWriteRecoveryState.Kind.CUP_RESET -> "cupReset"
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> "scheduleWrite"
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> "sleepNow"
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> "brewPreparation"
                            else -> error("Invalid fixture")
                        }).get(instance))
                        val wire = when (kind) {
                            MachineWriteRecoveryState.Kind.SETTING -> CoffeeCommands.setting(change)
                            MachineWriteRecoveryState.Kind.CUP_RESET -> CoffeeCommands.resetCupCount()
                            MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> CoffeeCommands.sleepSchedule(target).first()
                            MachineWriteRecoveryState.Kind.SLEEP_NOW -> CoffeeCommands.sleepNow()
                            MachineWriteRecoveryState.Kind.BREW_WAIT -> CoffeeCommands.brewWait(selected.temperatureC)
                            else -> error("Invalid fixture")
                        }.frame.toByteArray()
                        fun request(service:MobileService)=when(kind) {
                            MachineWriteRecoveryState.Kind.SETTING->service.changeMachineSetting(change)
                            MachineWriteRecoveryState.Kind.CUP_RESET->service.resetCupCount(settings.cupCount)
                            MachineWriteRecoveryState.Kind.SLEEP_NOW->service.enterSleepNow()
                            MachineWriteRecoveryState.Kind.BREW_WAIT->service.prepareBrew(selected.id,selected.scaleMode,7)
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
                        if(v2Native) {
                            field(instance,"v2SettingEvidence").setBoolean(instance,true)
                            field(session,"coffeeAuthentication").set(session,CoffeeAuthentication(
                                java.time.LocalDateTime.of(2026,11,22,0,0),"123456"))
                            field(session,"notifyEndpoint").set(session,KnownGatt.coffeeNotify)
                            fun ingest(hex:String,capturedHint:Long?=null) {
                                // Advance the detached queue's clock, not the Android app's wall clock.
                                now=SystemClock.elapsedRealtime()
                                session.onNotification(session.generation,KnownGatt.coffeeNotify,
                                    hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),capturedHint ?: now)
                            }
                            fun writeCount() = calls.count { it.third is GattOperation.Write }
                            // Same monotonic clock as the ACK cut; no wall clock or future samples.
                            fun freshTick() = SystemClock.sleep(2)
                            val normal="830113FD5C007D0F350019006E"
                            val disabled=normal.replace("FD","FC")
                            val idleHex="400024BF2F1C770B00000000000000190321AF"
                            ingest(idleHex)
                            if (v2Split) {
                                val brew=MachineSettingChange.BrewHeating(false)
                                val steam=MachineSettingChange.SteamHeating(false)
                                val p1="8340FE0A00071E0A00071E0A00071E0A00071E3D"
                                val p2="83800A00071E0A00071E0A00071E10"
                                ingest(normal);freshTick();ingest(p1);freshTick();ingest(p2)
                                check(request(instance)==null && state()=="WRITING" && writeCount()==1)
                                check(!instance.allowsSplitBoiler() && instance.changeMachineSetting(brew)!=null)
                                complete(calls.last())
                                check(instance.changeMachineSetting(brew)!=null && writeCount()==1)
                                // Legitimate P1 echo does not disturb the real P2 safety check.
                                freshTick();ingest(p1.replace("FE","7E"))
                                // P2 due time and V2 evidence use the same elapsedRealtime domain.
                                SystemClock.sleep(505);now=SystemClock.elapsedRealtime();session.tick()
                                check(state()=="WRITING" && writeCount()==2)
                                check(instance.changeMachineSetting(brew)!=null)
                                complete(calls.last())
                                check(state()=="WAITING_READBACK" && instance.allowsSplitBoiler())
                                check(instance.changeMachineSetting(brew)==null && writeCount()==3)
                                val wireBrew=CoffeeCommands.setting(brew).frame.toByteArray()
                                check((calls.last().third as GattOperation.Write).bytes.contentEquals(wireBrew))
                                check(instance.changeMachineSetting(steam)!=null)
                                check(instance.changeMachineSetting(MachineSettingChange.StandbyDelay(30,53))!=null)
                                check(instance.changeMachineSetting(MachineSettingChange.SleepScheduleEnabled(false))!=null)
                                complete(calls.last())
                                freshTick();ingest(normal.replace("FD","ED"))
                                check(instance.settingWriteState==SettingsWriteTracker.State.WAITING_READBACK)
                                freshTick();ingest(normal.replace("FD","DD"))
                                check(instance.settingWriteState==SettingsWriteTracker.State.CONFIRMED && state()=="WAITING_READBACK")
                                check(!instance.allowsSplitBoiler() && instance.changeMachineSetting(steam)!=null)
                                freshTick();ingest(p2)
                                check(state()=="WAITING_READBACK")
                                freshTick();ingest(p1.replace("FE","7E"));freshTick();ingest(p2)
                                check(state()=="CONFIRMED" && instance.machineControlSafetyMessage==null && writeCount()==3)
                            } else if (v2Boiler) {
                                val intent=if(mode=="V2_BOILER_BREW") MachineSettingChange.BrewHeating(false)
                                    else MachineSettingChange.SteamHeating(false)
                                val owns=if(intent is MachineSettingChange.BrewHeating) normal.replace("FD","DD") else normal.replace("FD","ED")
                                val unrelated=if(intent is MachineSettingChange.BrewHeating) normal.replace("FD","ED") else normal.replace("FD","DD")
                                val command=CoffeeCommands.setting(intent).frame.toByteArray()
                                ingest(normal)
                                check(instance.changeMachineSetting(intent)==null && state()=="WRITING" && recovery(instance).pending)
                                check((calls.last().third as GattOperation.Write).bytes.contentEquals(command))
                                val preAck=SystemClock.elapsedRealtime()
                                complete(calls.last())
                                freshTick();ingest(owns,capturedHint=preAck)
                                check(state()=="WAITING_READBACK")
                                freshTick();ingest(unrelated)
                                check(state()=="WAITING_READBACK")
                                if (mode=="V2_BOILER_BREW") {
                                    freshTick();ingest(owns)
                                    check(state()=="CONFIRMED" && !recovery(instance).pending && writeCount()==1)
                                } else {
                                    val base=now
                                    fun simulated(hex:String,at:Long) {
                                        now=at
                                        session.onNotification(session.generation,KnownGatt.coffeeNotify,
                                            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(),at)
                                    }
                                    simulated(idleHex,base+250);session.tick();complete(calls.last())
                                    simulated(idleHex,base+1250);session.tick();complete(calls.last())
                                    check(writeCount()==3 && state()=="WAITING_READBACK")
                                    simulated(idleHex,base+3000)
                                    val fallback=field(instance,"boilerFallback").get(instance)!!
                                    field(fallback,"startedAt").setLong(fallback,SystemClock.elapsedRealtime()-3005)
                                    val attempt=MobileService::class.java.getDeclaredMethod("attemptBoilerFallback").apply { isAccessible=true }
                                    val before=calls.size
                                    field(owner.extraction,"state").set(owner.extraction,ExtractionState.STARTING)
                                    attempt.invoke(instance);check(calls.size==before)
                                    field(owner.extraction,"state").set(owner.extraction,ExtractionState.IDLE)
                                    val scheduleTracker=field(instance,"scheduleWrite").get(instance)!!
                                    field(scheduleTracker,"state").set(scheduleTracker,SleepScheduleWriteTracker.State.WAITING_READBACK)
                                    attempt.invoke(instance);check(calls.size==before)
                                    field(scheduleTracker,"state").set(scheduleTracker,SleepScheduleWriteTracker.State.IDLE)
                                    val generation=session.generation
                                    attempt.invoke(instance)
                                    check(calls.size==before+1 && calls.last().third==GattOperation.Connect(address))
                                    check(session.generation==generation+1 && state()=="UNKNOWN" && recovery(instance).pending)
                                    attempt.invoke(instance);check(calls.size==before+1)
                                    complete(calls.last()) // Connect
                                    val discovery=calls.last()
                                    session.onComplete(discovery.first,discovery.second,OperationResult.Success(listOf(
                                        CharacteristicInfo(KnownGatt.coffeeWrite,true,false,false,false),
                                        CharacteristicInfo(KnownGatt.coffeeNotify,false,false,true,false))))
                                    complete(calls.last()) // Subscribe
                                    complete(calls.last()) // Reconnect authentication/time sync
                                    if (mode=="V2_BOILER_REBIND_MANUAL") {
                                        val activeLease=field(owner,"boilerReadbackReconnectLease").get(owner) as BoilerReconnectLease
                                        val marker=File(context.noBackupFilesDir,"pending_machine_write.json")
                                        requireNotNull(MachineRecoveryMarkerLease.tryAcquire(marker)).use {
                                            simulated(owns,base+3100)
                                            check(state()=="RECONCILED" && recovery(instance).pending && activeLease.held)
                                            SystemClock.sleep((base+3101-SystemClock.elapsedRealtime()).coerceIn(0L,3101L))
                                            freshTick();ingest(idleHex)
                                            instance.acknowledgeManualSafety() // Private storage is still leased.
                                            check(recovery(instance).pending && activeLease.held)
                                        }
                                        val priorSafe=SystemClock.elapsedRealtime()
                                        freshTick();ingest("80080800000000052421322003")
                                        freshTick();ingest(idleHex,capturedHint=priorSafe)
                                        instance.acknowledgeManualSafety()
                                        check(recovery(instance).pending && activeLease.held)
                                        freshTick();ingest(idleHex)
                                        instance.acknowledgeManualSafety()
                                        check(!recovery(instance).pending && !activeLease.held)
                                    } else simulated(owns,base+3100)
                                    check(state()=="RECONCILED" && !recovery(instance).pending)
                                    check(calls.count { (it.third as? GattOperation.Write)?.bytes?.contentEquals(command)==true }==1)
                                    check(instance.snapshot.settings?.let(intent::matches)==true && writeCount()==4)
                                }
                            } else if (v2Passive) {
                                val firstBase="8340FE0A00071E0A00071E0A00071E0A00071E3D"
                                val firstTarget=firstBase.replace("FE","7E")
                                val secondBase="83800A00071E0A00071E0A00071E10"
                                fun idleWith(temp:Int=9407,count:Int=25,sleep:Int=0)=
                                    "4000%04X2F1C770B%02X0000000000%04X0321AF".format(temp,sleep,count)
                                ingest(normal)
                                val coolIdle=idleWith(temp=7500)
                                if(mode=="V2_PREHEAT") ingest(coolIdle)
                                if(mode.startsWith("V2_WEEKLY_")) {
                                    ingest(firstBase)
                                    ingest(secondBase)
                                }
                                check(request(instance)==null && writeCount()==1 && state()=="WRITING")
                                check((calls.last().third as GattOperation.Write).bytes.contentEquals(wire))
                                val beforeResult=SystemClock.elapsedRealtime()
                                complete(calls.last())
                                when(mode) {
                                    "V2_CUP_PAIR" -> {
                                        check(state()=="WAITING_ZERO" && recovery(instance).pending)
                                        freshTick();ingest(normal.replace("0019","0000"))
                                        freshTick();ingest(idleWith(count=0),capturedHint=beforeResult)
                                        check(state()=="WAITING_ZERO")
                                        freshTick();ingest(idleWith(count=25))
                                        check(state()=="WAITING_ZERO")
                                        freshTick();ingest(idleWith(count=0))
                                        check(state()=="CONFIRMED" && !recovery(instance).pending && writeCount()==1)
                                    }
                                    "V2_WEEKLY_PAIR","V2_WEEKLY_PARTIAL" -> {
                                        check(state()=="WRITING" && recovery(instance).pending)
                                        // Fake queue clock advances to the real second-packet appointment.
                                        now+=500;session.tick()
                                        check(writeCount()==2 && state()=="WRITING")
                                        check((calls.last().third as GattOperation.Write).bytes.contentEquals(CoffeeCommands.sleepSchedule(target)[1].frame.toByteArray()))
                                        ingest(firstTarget)
                                        ingest(secondBase)
                                        val preSecond=SystemClock.elapsedRealtime()
                                        if (mode=="V2_WEEKLY_PARTIAL") {
                                            val pending=calls.last()
                                            session.onComplete(pending.first,pending.second,OperationResult.Failed("fixture second did not send"))
                                            check(state()=="UNKNOWN" && recovery(instance).pending)
                                            freshTick();ingest(firstTarget)
                                            freshTick();ingest(secondBase)
                                            freshTick();ingest(idleWith())
                                            check(state()=="UNKNOWN" && recovery(instance).pending && writeCount()==2)
                                            val reopened=attach()
                                            check(recovery(reopened).pending && recovery(reopened).kind==MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE)
                                            instance.acknowledgeManualSafety()
                                            check(state()=="IDLE" && !recovery(instance).pending && writeCount()==2)
                                        } else {
                                            complete(calls.last())
                                            check(state()=="WAITING_READBACK")
                                            freshTick();ingest(secondBase)
                                            check(state()=="WAITING_READBACK")
                                            freshTick();ingest(firstTarget,capturedHint=preSecond)
                                            freshTick();ingest(secondBase)
                                            check(state()=="WAITING_READBACK")
                                            freshTick();ingest(firstTarget)
                                            freshTick();ingest(secondBase)
                                            check(state()=="CONFIRMED" && !recovery(instance).pending && writeCount()==2)
                                        }
                                    }
                                    "V2_SLEEP_IDLE" -> {
                                        check(state()=="WAITING_ASLEEP" && recovery(instance).pending)
                                        freshTick();ingest(idleWith(sleep=1),capturedHint=beforeResult)
                                        check(state()=="WAITING_ASLEEP")
                                        freshTick();ingest(idleWith(sleep=0))
                                        check(state()=="WAITING_ASLEEP")
                                        freshTick();ingest(idleWith(sleep=1))
                                        check(state()=="CONFIRMED" && !recovery(instance).pending && writeCount()==1)
                                    }
                                    "V2_PREHEAT" -> {
                                        check(state()=="WAITING_TEMP" && recovery(instance).pending)
                                        val near=idleWith(temp=selected.temperatureC*100)
                                        freshTick();ingest(near,capturedHint=beforeResult)
                                        check(state()=="WAITING_TEMP")
                                        freshTick();ingest("80080800000000052421322003")
                                        check(state()=="WAITING_TEMP")
                                        check(instance.snapshot.messageForDisplay { id,args->instance.getString(id,*args) }==
                                            instance.getString(R.string.service_event_preheat_machine_waiting))
                                        val olderCalibration=SystemClock.elapsedRealtime()
                                        freshTick();ingest(normal.replace("5C00","5C14"))
                                        freshTick();ingest(normal,capturedHint=olderCalibration)
                                        freshTick();ingest(near)
                                        check(state()=="WAITING_TEMP") // Fresh raw temperature, but old compensation.
                                        freshTick();ingest(normal.replace("5C00","5C14"))
                                        freshTick();ingest(idleWith(temp=selected.temperatureC*100+200))
                                        check(state()=="READY" && recovery(instance).pending && writeCount()==1)
                                        check(instance.cancelBrewPreparation()==null && state()=="CANCELLING" && writeCount()==2)
                                        check((calls.last().third as GattOperation.Write).bytes.contentEquals(CoffeeCommands.brewWait(0).frame.toByteArray()))
                                        complete(calls.last())
                                        freshTick();ingest(idleWith())
                                        check(state()=="CANCEL_WRITTEN" && recovery(instance).pending && writeCount()==2)
                                    }
                                    else -> error("Missing passive fixture")
                                }
                            } else if (mode=="V2_WEEKLY_ENABLE") {
                                val enable=MachineSettingChange.SleepScheduleEnabled(true)
                                mapOf("sleepFirst" to null,"sleepSecond" to null,"sleepFirstAtMs" to null,"sleepSecondAtMs" to null)
                                    .forEach { (name,value) -> field(session,name).set(session,value) }
                                field(instance,"snapshot").set(instance,instance.snapshot.copy(sleepFirst=null,sleepSecond=null,sleepFirstAt=null,sleepSecondAt=null))
                                ingest(disabled)
                                ingest("8340FE0A00071E0A00071E0A00071E0A00071E3D")
                                check(instance.changeMachineSetting(enable)!=null && writeCount()==0)
                                ingest("83800A00071E0A00071E0A00071E10")
                                val blocked=barrier()
                                check(instance.changeMachineSetting(enable)==null && state()=="WRITING")
                                ingest("8340FE0B00071E0A00071E0A00071E0A00071E3D")
                                ingest("83800A00071E0A00071E0A00071E10")
                                complete(blocked)
                                check(writeCount()==0 && state()=="FAILED" && !recovery(instance).pending)
                                check(instance.changeMachineSetting(enable)==null && writeCount()==1 && state()=="WRITING")
                                check((calls.last().third as GattOperation.Write).bytes.contentEquals(CoffeeCommands.setting(enable).frame.toByteArray()))
                                val preAck=SystemClock.elapsedRealtime()
                                complete(calls.last());check(state()=="WAITING_READBACK")
                                freshTick();ingest(normal,capturedHint=preAck)
                                check(state()=="WAITING_READBACK" && recovery(instance).pending)
                                freshTick();ingest(disabled)
                                check(state()=="WAITING_READBACK")
                                freshTick();ingest(normal)
                                check(state()=="CONFIRMED" && !recovery(instance).pending && writeCount()==1)
                            } else if (mode=="V2_ACK_UNKNOWN") {
                                ingest(normal)
                                check(instance.changeMachineSetting(MachineSettingChange.Light(false))==null && writeCount()==1)
                                val call=calls.last()
                                val preResult=SystemClock.elapsedRealtime()
                                session.onComplete(call.first,call.second,OperationResult.Unknown("fixture uncertain delivery"))
                                check(state()=="UNKNOWN" && recovery(instance).pending)
                                val lightOff=normal.replace("FD","F5")
                                freshTick();ingest(lightOff,capturedHint=preResult);ingest(idleHex)
                                instance.acknowledgeManualSafety()
                                check(state()=="UNKNOWN" && recovery(instance).pending && writeCount()==1)

                                // New authenticated synthetic generation on an incorrect address cannot
                                // clear the record, then same-address evidence allows explicit release.
                                queues[0].open()
                                field(session,"activeAddress").set(session,"AA:BB:CC:DD:EE:02")
                                ingest(idleHex);freshTick();ingest(lightOff)
                                instance.acknowledgeManualSafety()
                                check(state()=="UNKNOWN" && recovery(instance).pending && writeCount()==1)
                                queues[0].open()
                                field(session,"activeAddress").set(session,address)
                                ingest(idleHex);freshTick();ingest(lightOff)
                                check(state()=="UNKNOWN" && recovery(instance).pending && writeCount()==1)
                                instance.acknowledgeManualSafety()
                                check(state()=="IDLE" && !recovery(instance).pending && writeCount()==1)
                                check(instance.changeMachineSetting(MachineSettingChange.Light(true))==null && writeCount()==2)
                                check((calls.last().third as GattOperation.Write).bytes.contentEquals(
                                    CoffeeCommands.setting(MachineSettingChange.Light(true)).frame.toByteArray()))
                                complete(calls.last())
                                freshTick();ingest(normal)
                                check(state()=="CONFIRMED" && !recovery(instance).pending && writeCount()==2)
                            } else if (mode=="V2_REPLACED_TEMP") {
                                val temp=(field(instance,"temperatureWrite\$delegate").get(instance) as Lazy<*>).value as BackgroundTemperatureWrite
                                ingest(normal)
                                val paired=MachineSettingChange.StandbyDelay(30,53)
                                val other=MachineSettingChange.BrewTemperature(93)
                                check(instance.changeMachineSetting(paired)==null && writeCount()==1)
                                val prePairedAck=SystemClock.elapsedRealtime()
                                complete(calls.last());check(temp.state==SettingsWriteTracker.State.WAITING_READBACK)
                                check(instance.changeMachineSetting(other)==null && temp.change==other && writeCount()==2)
                                complete(calls.last());check(temp.state==SettingsWriteTracker.State.WAITING_READBACK)
                                freshTick();ingest(normal.replace("0F35","1E35"),capturedHint=prePairedAck)
                                check(instance.changeMachineSetting(MachineSettingChange.StandbyTemperature(60,30))!=null &&
                                    writeCount()==2 && temp.change==other)

                                // Rebind to a newly authenticated dummy generation, not to the old operation.
                                // Synthetic queue only: no BLE connecting, command replay, or device permission.
                                val previousGeneration=queues[0].generation
                                queues[0].open()
                                check(queues[0].generation>previousGeneration && writeCount()==2)
                                temp.disconnected(field(instance,"settingsSampleSerial").getLong(instance))
                                ingest(idleHex)
                                freshTick();ingest(normal.replace("5C","5D").replace("0F35","1E36"))
                                check(temp.state==SettingsWriteTracker.State.UNKNOWN && writeCount()==2)
                                val newIntent=MachineSettingChange.StandbyTemperature(60,30)
                                check(instance.changeMachineSetting(newIntent)==null && temp.change==newIntent && writeCount()==3)
                                check((calls.last().third as GattOperation.Write).bytes.contentEquals(CoffeeCommands.setting(newIntent).frame.toByteArray()))
                                complete(calls.last())
                                freshTick();ingest(normal.replace("5C","5D").replace("0F35","1E3C"))
                                check(temp.state==SettingsWriteTracker.State.CONFIRMED && writeCount()==3)
                            } else {
                                val temp=(field(instance,"temperatureWrite\$delegate").get(instance) as Lazy<*>).value as BackgroundTemperatureWrite
                                val delay=mode=="V2_DELAY"
                                val old = if(delay) MachineSettingChange.StandbyDelay(30,53)
                                    else MachineSettingChange.StandbyTemperature(60,15)
                                val fresh = if(delay) MachineSettingChange.StandbyDelay(30,54)
                                    else MachineSettingChange.StandbyTemperature(60,30)
                                val changed = if(delay) normal.replace("0F35","0F36") else normal.replace("0F35","1E35")
                                val matching = if(delay) normal.replace("0F35","1E36") else normal.replace("0F35","1E3C")
                                val mismatch = if(delay) normal.replace("0F35","1E35") else normal.replace("0F35","0F3C")
                                ingest(normal)
                                val blocked=barrier()
                                check(instance.changeMachineSetting(old)==null && temp.state==SettingsWriteTracker.State.WRITING)
                                ingest(changed)
                                complete(blocked)
                                check(writeCount()==0 && temp.state==SettingsWriteTracker.State.FAILED)
                                check(instance.changeMachineSetting(fresh)==null && writeCount()==1)
                                check((calls.last().third as GattOperation.Write).bytes.contentEquals(CoffeeCommands.setting(fresh).frame.toByteArray()))
                                val preAck=SystemClock.elapsedRealtime()
                                complete(calls.last())
                                check(temp.state==SettingsWriteTracker.State.WAITING_READBACK)
                                freshTick();ingest(matching,capturedHint=preAck)
                                val other = if(delay) MachineSettingChange.StandbyTemperature(60,30)
                                    else MachineSettingChange.StandbyDelay(120,60)
                                check(instance.changeMachineSetting(other)!=null && writeCount()==1)
                                freshTick();ingest(mismatch)
                                check(temp.state==SettingsWriteTracker.State.WAITING_READBACK)
                                freshTick();ingest(matching)
                                check(temp.state==SettingsWriteTracker.State.CONFIRMED && writeCount()==1)
                            }
                            check(systemLookups==0 && permissionChecks==0 && componentCalls==0 && scaleExecutions==0)
                            fakeWrites+=writeCount(); checkedFixtures++
                            return@runOnMainSync
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
                                    MachineWriteRecoveryState.Kind.SETTING->read.invoke(instance,settings.copy(flags=settings.flags and 0x08.inv()))
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
                            listOf("coffee", "scale").forEach { name -> cleaned { hubDevice(owner, name).close() } }
                            cleaned { owner.scanner.close() }
                            cleaned { (field(owner, "handler").get(owner) as Handler).removeCallbacks(field(owner, "ticker").get(owner) as Runnable) }
                        }
                        splitCloser?.let { cleaned(it) }
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
        if(v2Boiler)check(checkedFixtures==3 && fakeWrites==9 && fakeBarriers==0)
        else if(v2Passive)check(checkedFixtures==5 && fakeWrites==8 && fakeBarriers==0)
        else if(v2Settings)check(checkedFixtures==5 && fakeWrites==8 && fakeBarriers==3)
        else if(ownership)check(checkedFixtures==12 && fakeWrites==9 && fakeBarriers==12 && clearedReloads==0 && retainedReloads==12)
        else check(checkedFixtures==39 && fakeWrites==12 && fakeBarriers==39 && clearedReloads==28 && retainedReloads==11)
    }
}

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

/** Detached real entry/callback/persistence paths with synthetic evidence and queued fake GATT shot authorization. */
internal class ServiceShotDispatchChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun decode(hex: String) = (HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
    private fun recovery(service: MobileService) =
        ((field(service, "machineWriteRecovery\$delegate").get(service) as Lazy<*>).value as MachineWriteRecoveryState)

    fun run()=runChecks(false)
    fun runCompletion()=runChecks(true)
    private fun runChecks(completion:Boolean) {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val address = "AA:BB:CC:DD:EE:01"
        val names = listOf("shot_safety", "machine_write_safety", "curves")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        var checkedFixtures=0;var fakeWrites=0;var fakeBarriers=0;var blocked=0;var reloadedRecords=0;var clearedShots=0;var retainedShots=0;var clearedPreheat=0;var retainedPreheat=0
        val modes=if(completion)listOf("OWNER_ALLOW","OWNER_SHOT_REARM","OWNER_SHOT_OTHER","OWNER_HUB",
            "OWNER_MANUAL","OWNER_PREHEAT_ALLOW","OWNER_PREHEAT_REARM") else listOf("ALLOW","MANUAL","CURVE","HUB","NOT_READY","SHOT_CLEAR","SHOT_OTHER","MACHINE_NEW",
            "STUDIO_TEMP","SETTINGS_BUSY","PREHEAT_ALLOW","PREHEAT_CLEAR","PREHEAT_OTHER")
        for(window in listOf("TARE","WEIGHT_START","FLOW_START")) for(mode in modes) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_shot_dispatch_${id}_$it" }
            val folder = File(app.cacheDir, "service-shot-dispatch-$id")
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
                val selected=CurveCatalog.profiles[if(window=="FLOW_START")1 else 2]
                check(app.curves.resolve(selected.id,true,7)==selected && app.curves.validated(selected))
                val curvePrefs=app.getSharedPreferences(fixtures.getValue("curves"),Context.MODE_PRIVATE)
                check(curvePrefs.edit().putString("selected",selected.id).commit())
                val shotPrefs=app.getSharedPreferences(fixtures.getValue("shot_safety"),Context.MODE_PRIVATE)
                check(shotPrefs.edit().putBoolean("unresolved_shot",false).commit())
                val prefs=app.getSharedPreferences(fixtures.getValue("machine_write_safety"),Context.MODE_PRIVATE)
                var failure:Throwable?=null
                test.runOnMainSync {
                    val services=mutableListOf<MobileService>()
                    var hub:NativeDeviceHub?=null
                    try {
                        fun attach()=MobileService().also { instance->
                            services+=instance
                            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext",Context::class.java)
                                .apply { isAccessible=true }.invoke(instance,context)
                            Service::class.java.getDeclaredField("mApplication").apply { isAccessible=true }.set(instance,app)
                            field(instance,"logs").set(instance,journal);field(instance,"mock").set(instance,null)
                            field(instance,"history").set(instance,detachedShotHistory())
                            check(!field(instance,"running").getBoolean(instance))
                        }
                        val instance=attach();val owner=NativeDeviceHub(context);hub=owner
                        field(instance,"hub").set(instance,owner)
                        val devices=listOf("coffee","scale").map { field(owner,it).get(owner) as AndroidDevice }
                        val sessions=devices.map { it.session }
                        val queues=sessions.map { field(it,"queue").get(it) as GattQueue }
                        val calls=List(2) { mutableListOf<Triple<Long,Long,GattOperation>>() }
                        val durable=mutableListOf<Pair<DeviceRole,Map<String,*>>>()
                        for(index in 0..1) {
                            val role=if(index==0)DeviceRole.COFFEE else DeviceRole.BOOKOO
                            field(queues[index],"driver").set(queues[index],GuardedGattDriver(role,object:GattDriver {
                                override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                                    calls[index]+=Triple(generation,token,operation)
                                    if(operation is GattOperation.Write) {
                                        check(shotPrefs.getBoolean("unresolved_shot",false))
                                        check(shotPrefs.getString("unresolved_shot_address",null)==address)
                                        durable+=role to shotPrefs.all.toMap()
                                    }
                                    return true
                                }
                                override fun close(generation:Long)=Unit
                            }))
                            queues[index].open()
                            field(sessions[index],"state").set(sessions[index],DeviceState.READY)
                            field(sessions[index],"activeAddress").set(sessions[index],if(index==0)address else "AA:BB:CC:DD:EE:03")
                        }
                        val settings=decode("830113FD5C007D0F350019006E") as Settings
                        val idle=decode("400023F02F1C770B00000000000000190321AF") as IdleTelemetry
                        var now=SystemClock.elapsedRealtime()
                        field(sessions[0],"lastSettings").set(sessions[0],settings)
                        field(sessions[0],"lastSettingsAtMs").set(sessions[0],now)
                        field(sessions[0],"lastIdle").set(sessions[0],idle)
                        field(sessions[0],"lastIdleAtMs").set(sessions[0],now)
                        field(instance,"snapshot").set(instance,MobileSnapshot(coffeeState=DeviceState.READY,
                            scaleState=DeviceState.READY,coffee=idle,coffeeAt=now,weightAt=now,settings=settings,settingsAt=now))
                        fun zero() {
                            // New decoded BOOKOO sample uses the live monotonic clock; this is not physical tare proof.
                            SystemClock.sleep(2)
                            val bytes="030B000000012B0000002D00024600C803010081".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            sessions[1].onNotification(sessions[1].generation,KnownGatt.bookooNotify,bytes)
                            now=SystemClock.elapsedRealtime()
                            field(instance,"snapshot").set(instance,instance.snapshot.copy(weightAt=now))
                        }
                        zero()
                        val machine=recovery(instance)
                        val preparation=field(instance,"brewPreparation").get(instance) as BrewPreparation
                        val preheat=mode.contains("PREHEAT_")
                        if(preheat) {
                            check(machine.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address))
                            val token=requireNotNull(preparation.begin(selected.id,selected.temperatureC))
                            check(preparation.written(token,OperationResult.Success(),0))
                            check(preparation.observe(1,selected.temperatureC*100))
                            check(preparation.matches(selected.id,selected.temperatureC))
                        }
                        val savedMachine=prefs.all.toMap()
                        fun complete(index:Int,call:Triple<Long,Long,GattOperation>)=
                            sessions[index].onComplete(call.first,call.second,OperationResult.Success())
                        val index=if(window=="TARE")1 else 0
                        queues[index].enqueue(GattOperation.Discover,5000) { }
                        val blocker=calls[index].last();check(blocker.third==GattOperation.Discover);fakeBarriers++
                        now=SystemClock.elapsedRealtime()
                        field(sessions[0],"lastIdleAtMs").set(sessions[0],now)
                        field(instance,"snapshot").set(instance,instance.snapshot.copy(coffeeAt=now,weightAt=now))
                        check(instance.startShot(selected.id,selected.scaleMode,7)==null) { "$window/$mode initial request rejected" }
                        check(owner.extraction.state==ExtractionState.STARTING)
                        check(shotPrefs.getBoolean("unresolved_shot",false) && shotPrefs.getString("unresolved_shot_address",null)==address)
                        if(preheat)check(preparation.state==BrewPreparation.State.IDLE && machine.kind==MachineWriteRecoveryState.Kind.BREW_WAIT)
                        if(window=="WEIGHT_START") {
                            val tare=calls[1].single().third as GattOperation.Write
                            check(tare.endpoint==KnownGatt.bookooWrite && tare.withResponse && tare.bytes.contentEquals(BookooCodec.tare().frame.toByteArray()))
                            complete(1,calls[1].single());check(owner.scaleTareState==StandaloneTare.State.WAITING_ZERO)
                            zero();check(owner.scaleTareState==StandaloneTare.State.CONFIRMED)
                        }
                        check(calls[0].count { it.third is GattOperation.Write }==0)
                        check(calls[index].size==1)
                        val shot=((field(instance,"shotRecovery\$delegate").get(instance) as Lazy<*>).value as ShotRecoveryState)
                        val passive=field(instance,"passiveShot").get(instance)!!
                        val other="AA:BB:CC:DD:EE:02"
                        when(mode) {
                            "MANUAL"->field(passive,"active").setBoolean(passive,true)
                            "CURVE"->check(curvePrefs.edit().putString("selected",CurveCatalog.profiles.first().id).commit())
                            "HUB"->field(instance,"hub").set(instance,null)
                            "NOT_READY"->field(instance,"snapshot").set(instance,instance.snapshot.copy(coffeeState=DeviceState.SYNCHRONIZING))
                            "SHOT_CLEAR"->check(shot.clear())
                            "SHOT_OTHER"->{check(shot.clear());check(shot.arm(other))}
                            "MACHINE_NEW"->check(machine.arm(MachineWriteRecoveryState.Kind.SETTING,address))
                            "STUDIO_TEMP"->field(instance,"snapshot").set(instance,instance.snapshot.copy(coffee=idle.copy(brewTemperatureHundredthsC=8000)))
                            "SETTINGS_BUSY"->field(field(instance,"settingsWrite").get(instance)!!,"state").set(field(instance,"settingsWrite").get(instance),SettingsWriteTracker.State.WRITING)
                            "PREHEAT_CLEAR"->check(machine.clear())
                            "PREHEAT_OTHER"->{check(machine.clear());check(machine.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,other))}
                        }
                        val allowed=completion || mode=="ALLOW" || mode=="PREHEAT_ALLOW"
                        // Session's original eligibility remains intact: product-only injections must drive rejection.
                        check(sessions[0].captureStartContext(selected.parameters)!=null)
                        val expectedShot=shotPrefs.all.toMap();val expectedMachine=prefs.all.toMap()
                        complete(index,blocker)
                        if(allowed && window=="TARE") {
                            val tare=calls[1].last().third as GattOperation.Write
                            check(tare.endpoint==KnownGatt.bookooWrite && tare.withResponse && tare.bytes.contentEquals(BookooCodec.tare().frame.toByteArray()))
                            complete(1,calls[1].last());check(owner.scaleTareState==StandaloneTare.State.WAITING_ZERO)
                            zero();check(owner.scaleTareState==StandaloneTare.State.CONFIRMED)
                        }
                        if(allowed) {
                            val op=calls[0].last().third as GattOperation.Write
                            check(op.endpoint==KnownGatt.coffeeWrite && op.withResponse && op.bytes.contentEquals(CoffeeCommands.start(selected.parameters).frame.toByteArray()))
                            complete(0,calls[0].last());check(owner.extraction.state==ExtractionState.RUNNING)
                            check(prefs.all==savedMachine)
                        }else {
                            check(owner.extraction.state==ExtractionState.IDLE)
                            if(window=="TARE")check(owner.scaleTareState==StandaloneTare.State.FAILED)
                            blocked++
                        }
                        check(calls[0].count { it.third is GattOperation.Write }==if(allowed)1 else 0)
                        check(calls[1].count { it.third is GattOperation.Write }==if(window=="WEIGHT_START" || (window=="TARE" && allowed))1 else 0)
                        check(shotPrefs.all==expectedShot && prefs.all==expectedMachine)
                        val finalCounts=calls.map { it.size };val finalState=owner.extraction.state
                        complete(index,blocker);sessions.forEach { it.tick() }
                        check(calls.map { it.size }==finalCounts && owner.extraction.state==finalState)
                        check(shotPrefs.all==expectedShot && prefs.all==expectedMachine)
                        if(completion) {
                            when(mode) {
                                "OWNER_SHOT_REARM"->{check(shot.clear());check(shot.arm(address))}
                                "OWNER_SHOT_OTHER"->{check(shot.clear());check(shot.arm(other))}
                                "OWNER_HUB"->field(instance,"hub").set(instance,null)
                                "OWNER_MANUAL"->field(passive,"active").setBoolean(passive,true)
                                "OWNER_PREHEAT_REARM"->{check(machine.clear());check(machine.arm(MachineWriteRecoveryState.Kind.BREW_WAIT,address))}
                            }
                            val replacementShot=shotPrefs.all.toMap();val replacementMachine=prefs.all.toMap()
                            // Synthetic host-confirmed end; run actual Service ticker, never a physical idle claim.
                            owner.extraction.machineIdle()
                            check(owner.extraction.state==ExtractionState.ENDED_OBSERVED)
                            field(instance,"lastShotState").set(instance,ExtractionState.RUNNING)
                            val ticker=field(instance,"watchShot").get(instance) as Runnable
                            ticker.run()
                            val shouldClear=mode in setOf("OWNER_ALLOW","OWNER_PREHEAT_ALLOW","OWNER_PREHEAT_REARM")
                            if(shouldClear) {
                                check(!shot.pending && !shotPrefs.getBoolean("unresolved_shot",true) && !shotPrefs.contains("unresolved_shot_address"));clearedShots++
                            } else {check(shotPrefs.all==replacementShot && shot.pending);retainedShots++}
                            if(mode=="OWNER_PREHEAT_ALLOW") {check(!machine.pending && prefs.all.isEmpty());clearedPreheat++}
                            else if(mode=="OWNER_PREHEAT_REARM") {check(machine.pending && prefs.all==replacementMachine);retainedPreheat++}
                            else check(prefs.all==replacementMachine)
                            val settledShot=shotPrefs.all.toMap();val settledMachine=prefs.all.toMap()
                            ticker.run();complete(index,blocker)
                            check(shotPrefs.all==settledShot && prefs.all==settledMachine && calls.map { it.size }==finalCounts)
                        }
                        val reloaded=attach()
                        val reloadedShot=((field(reloaded,"shotRecovery\$delegate").get(reloaded) as Lazy<*>).value as ShotRecoveryState)
                        check(reloadedShot.pending==shot.pending && reloadedShot.address==shot.address)
                        check(recovery(reloaded).kind==machine.kind && recovery(reloaded).address==machine.address);reloadedRecords++
                        check(systemLookups==0 && permissionChecks==0 && componentCalls==0)
                        fakeWrites+=durable.size;checkedFixtures++
                    }catch(error:Throwable) { failure=IllegalStateException("$window/$mode",error) }
                    finally {
                        fun cleaned(action:()->Unit) {
                            try { action() }catch(error:Throwable) {
                                if(failure==null)failure=error else if(failure!==error)failure!!.addSuppressed(error)
                            }
                        }
                        hub?.let { owner->
                            listOf("coffee","scale").forEach { name->cleaned { (field(owner,name).get(owner) as AndroidDevice).close() } }
                            cleaned { owner.scanner.close() }
                            cleaned { (field(owner,"handler").get(owner) as Handler).removeCallbacks(field(owner,"ticker").get(owner) as Runnable) }
                        }
                        services.forEach { service->cleaned { (field(service,"handler").get(service) as Handler).removeCallbacksAndMessages(null) } }
                    }
                }
                try { check(systemLookups==0 && permissionChecks==0 && componentCalls==0) }
                catch(error:Throwable) { if(failure==null)failure=error else if(failure!==error)failure!!.addSuppressed(error) }
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
        if(completion)check(checkedFixtures==21 && fakeWrites==35 && fakeBarriers==21 && blocked==0 && reloadedRecords==21 &&
            clearedShots==9 && retainedShots==12 && clearedPreheat==3 && retainedPreheat==3)
        else check(checkedFixtures==39 && fakeWrites==21 && fakeBarriers==39 && blocked==33 && reloadedRecords==39)
    }
}

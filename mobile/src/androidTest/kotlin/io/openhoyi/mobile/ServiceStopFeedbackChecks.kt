package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import io.openhoyi.bluetooth.AndroidDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.protocol.*
import io.openhoyi.session.*
import io.openhoyi.trace.TraceStore
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Synthetic missing journal after real start; actual stop entry and guarded fake GATT. */
internal class ServiceStopFeedbackChecks(private val test:Instrumentation) {
    private fun field(subject:Any,name:String)=subject.javaClass.getDeclaredField(name).apply { isAccessible=true }
    fun run() {
        val app=test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName=="io.openhoyi.mobile.mock")
        val names=listOf("shot_safety","machine_write_safety","curves")
        val original=names.associateWith { app.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap() }
        var fixtures=0;var writes=0;var retained=0
        for(fault in listOf(false,true)) {
            val id=UUID.randomUUID().toString()
            val folder=File(app.cacheDir,"stop-feedback-$id")
            val journal=TraceStore(File(folder,"trace"))
            var external=0
            val context=object:ContextWrapper(app) {
                override fun getApplicationContext():Context=this
                override fun getNoBackupFilesDir()=File(folder,"no-backup")
                override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                    check(name in names);return app.getSharedPreferences("stop_${id}_$name",mode)
                }
                override fun getSystemService(name:String):Any? { external++;error("No system access") }
                override fun checkSelfPermission(permission:String):Int { external++;error("No permission access") }
                override fun startService(intent:Intent):ComponentName? { external++;error("No component") }
                override fun startForegroundService(intent:Intent):ComponentName? { external++;error("No component") }
                override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean { external++;error("No component") }
                override fun startActivity(intent:Intent) { external++;error("No component") }
                override fun startActivity(intent:Intent,options:Bundle?) { external++;error("No component") }
                override fun stopService(intent:Intent):Boolean { external++;error("No component") }
            }
            var primary:Throwable?=null
            try {
                val selected=CurveCatalog.profiles[1]
                check(selected.targetHundredthsGram==0 && app.curves.resolve(selected.id,true,7)==selected && app.curves.validated(selected))
                check(context.getSharedPreferences("curves",0).edit().putString("selected",selected.id).commit())
                var failure:Throwable?=null
                test.runOnMainSync {
                    val services=mutableListOf<MobileService>()
                    var owner:NativeDeviceHub?=null
                    fun clean(action:()->Unit) { try { action() }catch(error:Throwable) {
                        val first=failure;if(first==null)failure=error else if(first!==error)first.addSuppressed(error)
                    } }
                    try {
                        fun attach()=MobileService().also { service->
                            services+=service
                            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext",Context::class.java).apply { isAccessible=true }.invoke(service,context)
                            Service::class.java.getDeclaredField("mApplication").apply { isAccessible=true }.set(service,app)
                            field(service,"mock").set(service,null);field(service,"logs").set(service,journal)
                            field(service,"history").set(service,detachedShotHistory())
                            check(!field(service,"running").getBoolean(service))
                        }
                        val service=attach();val hub=NativeDeviceHub(context);owner=hub
                        field(service,"hub").set(service,hub)
                        val session=(field(hub,"coffee").get(hub) as AndroidDevice).session
                        val queue=field(session,"queue").get(session) as GattQueue
                        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
                        val address="AA:BB:CC:DD:EE:01"
                        field(queue,"driver").set(queue,GuardedGattDriver(DeviceRole.COFFEE,object:GattDriver {
                            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                                check(operation is GattOperation.Write)
                                check(File(context.noBackupFilesDir,"pending_shot.json").isFile)
                                calls+=Triple(generation,token,operation);return true
                            }
                            override fun close(generation:Long)=Unit
                        }))
                        queue.open();field(session,"state").set(session,DeviceState.READY)
                        field(session,"activeAddress").set(session,address)
                        fun decode(hex:String)=(HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
                        val settings=decode("830113FD5C007D0F350019006E") as Settings
                        val idle=decode("400023F02F1C770B00000000000000190321AF") as IdleTelemetry
                        val now=SystemClock.elapsedRealtime()
                        field(session,"lastSettings").set(session,settings);field(session,"lastSettingsAtMs").set(session,now)
                        field(session,"lastIdle").set(session,idle);field(session,"lastIdleAtMs").set(session,now)
                        field(service,"snapshot").set(service,MobileSnapshot(coffeeState=DeviceState.READY,scaleState=DeviceState.READY,
                            coffee=idle,coffeeAt=now,settings=settings,settingsAt=now,weightAt=now))
                        check(service.startShot(selected.id,selected.scaleMode,7)==null)
                        check(calls.size==1 && (calls[0].third as GattOperation.Write).bytes.contentEquals(CoffeeCommands.start(selected.parameters).frame.toByteArray()))
                        session.onComplete(calls[0].first,calls[0].second,OperationResult.Success())
                        check(hub.extraction.state==ExtractionState.RUNNING)
                        // Injection is deliberately not a normal lifecycle claim or disk-write fault.
                        if(fault)field(service,"logs").set(service,null)
                        service.stopShot()
                        check(calls.size==2)
                        val stop=calls[1].third as GattOperation.Write
                        check(stop.endpoint==KnownGatt.coffeeWrite && stop.withResponse && stop.bytes.contentEquals(CoffeeCommands.stop(7).frame.toByteArray()))
                        check(hub.extraction.state==ExtractionState.STOP_REQUESTED)
                        session.onComplete(calls[1].first,calls[1].second,OperationResult.Success())
                        check(hub.extraction.state==ExtractionState.STOP_REQUESTED)
                        service.stopShot();session.onComplete(calls[1].first,calls[1].second,OperationResult.Success())
                        check(calls.size==2 && hub.extraction.state==ExtractionState.STOP_REQUESTED)
                        val reloaded=attach()
                        val recovery=(field(reloaded,"shotRecovery\$delegate").get(reloaded) as Lazy<*>).value as ShotRecoveryState
                        check(recovery.pending && recovery.address==address)
                        check(external==0);fixtures++;writes+=calls.size;retained++
                    }catch(error:Throwable) { failure=error }
                    finally {
                        owner?.let { hub->clean { hub.close() } }
                        services.forEach { service->clean { (field(service,"handler").get(service) as android.os.Handler).removeCallbacksAndMessages(null) } }
                    }
                }
                failure?.let { throw it }
            }catch(error:Throwable) { primary=error;throw error }
            finally {
                var cleanup:Throwable?=null
                fun clean(action:()->Unit) { try { action() }catch(error:Throwable) {
                    val first=primary?:cleanup;if(first==null)cleanup=error else if(first!==error)first.addSuppressed(error)
                } }
                clean { journal.close() };clean { check(journal.awaitTermination(5,TimeUnit.SECONDS)) }
                clean { check(folder.deleteRecursively()) }
                names.forEach { name->
                    clean { check(app.deleteSharedPreferences("stop_${id}_$name")) }
                    clean { check(app.getSharedPreferences(name,0).all.toMap()==original[name]) }
                }
                cleanup?.let { throw it }
            }
        }
        check(fixtures==2 && writes==4 && retained==2)
    }
}

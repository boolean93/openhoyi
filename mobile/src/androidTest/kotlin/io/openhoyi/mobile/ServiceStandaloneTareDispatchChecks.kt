package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import io.openhoyi.bluetooth.AndroidDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.protocol.*
import io.openhoyi.session.*
import io.openhoyi.trace.TraceStore
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Actual detached Service button entry and scale queue; no Android BLE driver executes. */
internal class ServiceStandaloneTareDispatchChecks(private val test:Instrumentation) {
    private fun field(subject:Any,name:String)=subject.javaClass.getDeclaredField(name).apply { isAccessible=true }
    fun run()=runChecks(false)
    fun runPersistence()=runChecks(true)
    private fun runChecks(restored:Boolean) {
        val app=test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName=="io.openhoyi.mobile.mock")
        var fixtures=0;var writes=0;var barriers=0;var confirmed=0
        val modes=if(restored)listOf("RESTORED_ALLOW","RESTORED_FAILED","RESTORED_UNKNOWN","RESTORED_CLEAR_FAIL") else listOf("ALLOW","COFFEE_OFFLINE","MANUAL","APP_STARTING","APP_RUNNING",
            "APP_STOP_REQUESTED","APP_OUTCOME_UNKNOWN","HUB","ADDRESS","NOT_READY")
        for(mode in modes) {
            val id=UUID.randomUUID().toString()
            val folder=File(app.cacheDir,"service-standalone-tare-$id")
            val journal=TraceStore(folder)
            val prefs=mutableMapOf<String,Map<String,*>>()
            var systemCalls=0
            val context=object:ContextWrapper(app) {
                override fun getNoBackupFilesDir():File=File(folder,"no-backup")
                override fun getApplicationContext():Context=this
                override fun getSystemService(name:String):Any? { systemCalls++;error("No system service") }
                override fun checkSelfPermission(permission:String):Int { systemCalls++;error("No permission access") }
                override fun getSharedPreferences(name:String,mode:Int)=app.getSharedPreferences("tare_${id}_$name",mode).also {
                    prefs.putIfAbsent(name,app.getSharedPreferences(name,mode).all.toMap())
                }
                override fun startService(intent:Intent):android.content.ComponentName? { systemCalls++;error("No component") }
                override fun startForegroundService(intent:Intent):android.content.ComponentName? { systemCalls++;error("No component") }
                override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean { systemCalls++;error("No component") }
                override fun startActivity(intent:Intent) { systemCalls++;error("No component") }
                override fun startActivity(intent:Intent,options:Bundle?) { systemCalls++;error("No component") }
                override fun stopService(intent:Intent):Boolean { systemCalls++;error("No component") }
            }
            var failure:Throwable?=null
            var outerFailure:Throwable?=null
            try {
                test.runOnMainSync {
                    var owner:NativeDeviceHub?=null
                    var service:MobileService?=null
                    try {
                        val instance=MobileService();service=instance
                        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext",Context::class.java)
                            .apply { isAccessible=true }.invoke(instance,context)
                        Service::class.java.getDeclaredField("mApplication").apply { isAccessible=true }.set(instance,app)
                        field(instance,"logs").set(instance,journal);field(instance,"mock").set(instance,null)
                        check(!field(instance,"running").getBoolean(instance))
                        val marker=File(folder,"tare-pending")
                        val storage=io.openhoyi.bluetooth.SharedPreferenceTareStorage(context,marker)
                        if(restored)check(storage.write(true))
                        // Simulate ambiguous commit failure after the actual preferences cache/disk changed.
                        val failedContext=object:ContextWrapper(context) {
                            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                                val actual=context.getSharedPreferences(name,mode)
                                return object:android.content.SharedPreferences by actual {
                                    override fun edit():android.content.SharedPreferences.Editor {
                                        val editor=actual.edit();var pending=true
                                        return object:android.content.SharedPreferences.Editor by editor {
                                            override fun putBoolean(key:String,value:Boolean):android.content.SharedPreferences.Editor {
                                                editor.putBoolean(key,value);pending=value;return this
                                            }
                                            override fun commit():Boolean { val saved=editor.commit();return saved && pending }
                                        }
                                    }
                                }
                            }
                        }
                        val guardedStorage=if(mode=="RESTORED_CLEAR_FAIL")io.openhoyi.bluetooth.SharedPreferenceTareStorage(failedContext,marker) else storage
                        val hub=NativeDeviceHub(context,tareStorage=guardedStorage);owner=hub;
                        if(restored) {
                            check(hub.scaleTareState==StandaloneTare.State.UNKNOWN)
                            check(!(field(hub,"scaleControl").get(hub) as ScaleSessionControl).startAllowed)
                        }
                        field(instance,"hub").set(instance,hub)
                        val scale=(field(hub,"scale").get(hub) as AndroidDevice).session
                        val queue=field(scale,"queue").get(scale) as GattQueue
                        val calls=mutableListOf<Triple<Long,Long,GattOperation>>()
                        field(queue,"driver").set(queue,GuardedGattDriver(DeviceRole.BOOKOO,object:GattDriver {
                            override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
                                calls+=Triple(generation,token,operation);return true
                            }
                            override fun close(generation:Long)=Unit
                        }))
                        queue.open();field(scale,"state").set(scale,DeviceState.READY)
                        field(scale,"activeAddress").set(scale,"AA:BB:CC:DD:EE:03")
                        field(instance,"snapshot").set(instance,MobileSnapshot(coffeeState=if(mode=="COFFEE_OFFLINE")DeviceState.DISCONNECTED else DeviceState.READY,
                            scaleState=DeviceState.READY))
                        check(hub.scaleAddress=="AA:BB:CC:DD:EE:03")
                        queue.enqueue(GattOperation.Discover,5000) { };val blocker=calls.single();barriers++
                        check(instance.tareScale()==null)
                        check(hub.scaleTareState==StandaloneTare.State.WRITING && calls.size==1)
                        check(storage.read() && StandaloneTare(storage) { 0L }.state==StandaloneTare.State.UNKNOWN)
                        when(mode) {
                            "MANUAL"->field(field(instance,"passiveShot").get(instance)!!,"active").setBoolean(field(instance,"passiveShot").get(instance),true)
                            "HUB"->field(instance,"hub").set(instance,null)
                            "ADDRESS"->field(scale,"activeAddress").set(scale,"AA:BB:CC:DD:EE:04")
                            "NOT_READY"->field(instance,"snapshot").set(instance,instance.snapshot.copy(scaleState=DeviceState.FAILED))
                            else->if(mode.startsWith("APP_"))field(hub.extraction,"state").set(hub.extraction,ExtractionState.valueOf(mode.removePrefix("APP_")))
                        }
                        val beforeMessage=instance.snapshot.message
                        scale.onComplete(blocker.first,blocker.second,OperationResult.Success())
                        val allowed=restored || mode in listOf("ALLOW","COFFEE_OFFLINE")
                        if(allowed) {
                            check(calls.size==2)
                            val call=calls.last();val write=call.third as GattOperation.Write
                            check(write.endpoint==KnownGatt.bookooWrite && write.withResponse && write.bytes.contentEquals(BookooCodec.tare().frame.toByteArray()))
                            check(hub.scaleTareState==StandaloneTare.State.WRITING)
                            val result=when(mode) {
                                "RESTORED_FAILED"->OperationResult.Failed("known failure on explicit retry")
                                "RESTORED_UNKNOWN"->OperationResult.Unknown("retry uncertain")
                                else->OperationResult.Success()
                            }
                            scale.onComplete(call.first,call.second,result)
                            val zero="030B000000012B0000002D00024600C803010081".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            if(result is OperationResult.Success)check(hub.scaleTareState==StandaloneTare.State.WAITING_ZERO)
                            else check(hub.scaleTareState==StandaloneTare.State.UNKNOWN)
                            scale.onNotification(scale.generation,KnownGatt.bookooNotify,zero)
                            val resolved=mode !in listOf("RESTORED_FAILED","RESTORED_UNKNOWN","RESTORED_CLEAR_FAIL")
                            check(hub.scaleTareState==if(resolved)StandaloneTare.State.CONFIRMED else StandaloneTare.State.UNKNOWN)
                            writes++;if(resolved)confirmed++
                        } else {
                            check(calls.size==1 && hub.scaleTareState==StandaloneTare.State.FAILED)
                            if(mode=="HUB")check(instance.snapshot.message==beforeMessage)
                        }
                        val retained=mode in listOf("RESTORED_FAILED","RESTORED_UNKNOWN","RESTORED_CLEAR_FAIL")
                        check(storage.read()==retained && StandaloneTare(storage) { 0L }.unresolved==retained)
                        if(mode=="RESTORED_CLEAR_FAIL") {
                            check(!context.getSharedPreferences("scale_tare_safety",Context.MODE_PRIVATE).getBoolean("unresolved_tare",true))
                            check(marker.isFile)
                            val reopened=io.openhoyi.bluetooth.SharedPreferenceTareStorage(context,marker)
                            check(reopened.read() && StandaloneTare(reopened) { 0L }.unresolved)
                        }
                        val message=instance.snapshot.message
                        val count=calls.size
                        scale.onComplete(blocker.first,blocker.second,OperationResult.Success())
                        if(allowed) {
                            val old=calls.last()
                            scale.onComplete(old.first,old.second,OperationResult.Success())
                            check(hub.scaleTareState==if(retained)StandaloneTare.State.UNKNOWN else StandaloneTare.State.CONFIRMED)
                        }
                        scale.tick();check(calls.size==count && instance.snapshot.message==message)
                        check(systemCalls==0);fixtures++
                    }catch(error:Throwable) { failure=IllegalStateException(mode,error) }
                    finally {
                        fun cleaned(action:()->Unit) { try { action() }catch(error:Throwable) {
                            if(failure==null)failure=error else if(failure!==error)failure!!.addSuppressed(error)
                        } }
                        owner?.let { hub->
                            listOf("coffee","scale").forEach { name->cleaned { (field(hub,name).get(hub) as AndroidDevice).close() } }
                            cleaned { hub.scanner.close() }
                            cleaned { (field(hub,"handler").get(hub) as Handler).removeCallbacks(field(hub,"ticker").get(hub) as Runnable) }
                        }
                        service?.let { cleaned { (field(it,"handler").get(it) as Handler).removeCallbacksAndMessages(null) } }
                    }
                }
                failure?.let { throw it }
                check(systemCalls==0)
            }catch(error:Throwable) { outerFailure=error;throw error }
            finally {
                var cleanupFailure:Throwable?=null
                fun cleaned(action:()->Unit) { try { action() }catch(error:Throwable) {
                    val primary=outerFailure?:cleanupFailure
                    if(primary==null)cleanupFailure=error else if(primary!==error)primary.addSuppressed(error)
                } }
                cleaned { journal.close() }
                cleaned { check(journal.awaitTermination(5,TimeUnit.SECONDS)) }
                cleaned { check(folder.deleteRecursively()) }
                prefs.forEach { (name,original)->
                    cleaned { check(app.deleteSharedPreferences("tare_${id}_$name")) }
                    cleaned { check(app.getSharedPreferences(name,Context.MODE_PRIVATE).all.toMap()==original) }
                }
                cleanupFailure?.let { throw it }
            }
        }
        if(restored)check(fixtures==4 && writes==4 && barriers==4 && confirmed==1)
        else check(fixtures==10 && writes==2 && barriers==10 && confirmed==2)
    }
}

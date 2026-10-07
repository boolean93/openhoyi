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
    fun run() {
        val app=test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName=="io.openhoyi.mobile.mock")
        var fixtures=0;var writes=0;var barriers=0;var confirmed=0
        val modes=listOf("ALLOW","COFFEE_OFFLINE","MANUAL","APP_STARTING","APP_RUNNING",
            "APP_STOP_REQUESTED","APP_OUTCOME_UNKNOWN","HUB","ADDRESS","NOT_READY")
        for(mode in modes) {
            val id=UUID.randomUUID().toString()
            val folder=File(app.cacheDir,"service-standalone-tare-$id")
            val journal=TraceStore(folder)
            val prefs=mutableMapOf<String,Map<String,*>>()
            var systemCalls=0
            val context=object:ContextWrapper(app) {
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
                        val hub=NativeDeviceHub(context);owner=hub;field(instance,"hub").set(instance,hub)
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
                        when(mode) {
                            "MANUAL"->field(field(instance,"passiveShot").get(instance)!!,"active").setBoolean(field(instance,"passiveShot").get(instance),true)
                            "HUB"->field(instance,"hub").set(instance,null)
                            "ADDRESS"->field(scale,"activeAddress").set(scale,"AA:BB:CC:DD:EE:04")
                            "NOT_READY"->field(instance,"snapshot").set(instance,instance.snapshot.copy(scaleState=DeviceState.FAILED))
                            else->if(mode.startsWith("APP_"))field(hub.extraction,"state").set(hub.extraction,ExtractionState.valueOf(mode.removePrefix("APP_")))
                        }
                        val beforeMessage=instance.snapshot.message
                        scale.onComplete(blocker.first,blocker.second,OperationResult.Success())
                        val allowed=mode in listOf("ALLOW","COFFEE_OFFLINE")
                        if(allowed) {
                            check(calls.size==2)
                            val call=calls.last();val write=call.third as GattOperation.Write
                            check(write.endpoint==KnownGatt.bookooWrite && write.withResponse && write.bytes.contentEquals(BookooCodec.tare().frame.toByteArray()))
                            check(hub.scaleTareState==StandaloneTare.State.WRITING)
                            scale.onComplete(call.first,call.second,OperationResult.Success())
                            check(hub.scaleTareState==StandaloneTare.State.WAITING_ZERO)
                            val zero="030B000000012B0000002D00024600C803010081".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            scale.onNotification(scale.generation,KnownGatt.bookooNotify,zero)
                            check(hub.scaleTareState==StandaloneTare.State.CONFIRMED);writes++;confirmed++
                        } else {
                            check(calls.size==1 && hub.scaleTareState==StandaloneTare.State.FAILED)
                            if(mode=="HUB")check(instance.snapshot.message==beforeMessage)
                        }
                        val message=instance.snapshot.message
                        val count=calls.size
                        scale.onComplete(blocker.first,blocker.second,OperationResult.Success())
                        if(allowed) {
                            val old=calls.last()
                            scale.onComplete(old.first,old.second,OperationResult.Success())
                            check(hub.scaleTareState==StandaloneTare.State.CONFIRMED)
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
        check(fixtures==10 && writes==2 && barriers==10 && confirmed==2)
    }
}

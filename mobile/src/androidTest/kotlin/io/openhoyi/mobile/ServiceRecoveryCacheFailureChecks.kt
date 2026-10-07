package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.ShotRecoveryState
import java.io.File
import java.util.UUID

/** Real storage through fresh detached Service lazies; no connection or acknowledgement evidence is simulated. */
internal class ServiceRecoveryCacheFailureChecks(private val test:Instrumentation) {
    private fun field(subject:Any,name:String)=subject.javaClass.getDeclaredField(name).apply { isAccessible=true }
    private fun shot(service:MobileService)=(field(service,"shotRecovery\$delegate").get(service) as Lazy<*>).value as ShotRecoveryState
    private fun machine(service:MobileService)=(field(service,"machineWriteRecovery\$delegate").get(service) as Lazy<*>).value as MachineWriteRecoveryState
    fun run() {
        val app=test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName=="io.openhoyi.mobile.mock")
        val kinds=listOf("SHOT")+MachineWriteRecoveryState.Kind.entries.filter { it!=MachineWriteRecoveryState.Kind.UNKNOWN }.map { it.name }
        val address="AA:BB:CC:DD:EE:01"
        fun decode(hex:String)=(io.openhoyi.protocol.HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as io.openhoyi.protocol.DecodeResult.Valid).value
        val schedule=requireNotNull(io.openhoyi.protocol.WeeklySleepSchedule.fromReadback(
            decode("8340FE0A00071E0A00071E0A00071E0A00071E3D") as io.openhoyi.protocol.SleepPart,
            decode("83800A00071E0A00071E0A00071E10") as io.openhoyi.protocol.SleepPart))
        fun entries(service:MobileService)=listOf(service.changeMachineSetting(io.openhoyi.protocol.MachineSettingChange.BrewTemperature(93)),
            service.resetCupCount(25),service.changeSleepSchedule(schedule,schedule),service.enterSleepNow(),
            service.prepareBrew("fixture",false,7),service.startShot("fixture",false,7))
        var checked=0;var blockedEntries=0
        for(kind in kinds) for(throws in listOf(false,true)) {
            val id=UUID.randomUUID().toString()
            val folder=File(app.cacheDir,"recovery-cache-$id")
            val names=listOf("shot_safety","machine_write_safety")
            val original=names.associateWith { app.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap() }
            var failClear=false;var systemCalls=0
            val context=object:ContextWrapper(app) {
                override fun getNoBackupFilesDir()=File(folder,"no-backup")
                override fun getApplicationContext():Context=this
                override fun getSystemService(name:String):Any? { systemCalls++;error("No system service") }
                override fun checkSelfPermission(permission:String):Int { systemCalls++;error("No permission access") }
                override fun startService(intent:Intent):android.content.ComponentName? { systemCalls++;error("No component") }
                override fun startForegroundService(intent:Intent):android.content.ComponentName? { systemCalls++;error("No component") }
                override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean { systemCalls++;error("No component") }
                override fun startActivity(intent:Intent) { systemCalls++;error("No component") }
                override fun startActivity(intent:Intent,options:Bundle?) { systemCalls++;error("No component") }
                override fun stopService(intent:Intent):Boolean { systemCalls++;error("No component") }
                override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                    check(name in names)
                    val actual=app.getSharedPreferences("cache_${id}_$name",mode)
                    return object:android.content.SharedPreferences by actual {
                        override fun edit():android.content.SharedPreferences.Editor {
                            val editor=actual.edit();var clearing=false
                            return object:android.content.SharedPreferences.Editor by editor {
                                override fun putBoolean(key:String,value:Boolean):android.content.SharedPreferences.Editor {
                                    editor.putBoolean(key,value);if(key=="unresolved_shot")clearing=!value;return this
                                }
                                override fun putString(key:String,value:String?):android.content.SharedPreferences.Editor {
                                    editor.putString(key,value);if(key=="pending_kind")clearing=value==null;return this
                                }
                                override fun commit():Boolean {
                                    val saved=editor.commit()
                                    if(clearing && failClear) { if(throws)error("commit reported failure after mutation");return false }
                                    return saved
                                }
                            }
                        }
                    }
                }
            }
            var primary:Throwable?=null
            try {
                var failure:Throwable?=null
                test.runOnMainSync {
                    try {
                        fun fresh():MobileService=MobileService().also {
                            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext",Context::class.java).apply { isAccessible=true }.invoke(it,context)
                            Service::class.java.getDeclaredField("mApplication").apply { isAccessible=true }.set(it,app)
                            field(it,"mock").set(it,null)
                            check(field(it,"hub").get(it)==null && !field(it,"running").getBoolean(it))
                        }
                        val first=fresh()
                        if(kind=="SHOT")check(shot(first).arm(address)) else check(machine(first).arm(MachineWriteRecoveryState.Kind.valueOf(kind),address))
                        failClear=true
                        if(kind=="SHOT")check(!shot(first).clear(shot(first).captureOwnership())) else check(!machine(first).clear(machine(first).captureOwnership()))
                        val pref=context.getSharedPreferences(if(kind=="SHOT")"shot_safety" else "machine_write_safety",Context.MODE_PRIVATE)
                        if(kind=="SHOT")check(!pref.getBoolean("unresolved_shot",true) && pref.getString("unresolved_shot_address",null)==null)
                        else check(pref.getString("pending_kind",null)==null && pref.getString("pending_address",null)==null)
                        val second=fresh()
                        val warning=requireNotNull(second.machineControlSafetyMessage)
                        check(entries(second).all { it==warning });blockedEntries+=6
                        if(kind=="SHOT") {
                            check(shot(second).pending && shot(second).address==address && !shot(second).matchesDevice("AA:BB:CC:DD:EE:02"))
                        } else {
                            check(machine(second).kind==MachineWriteRecoveryState.Kind.valueOf(kind) && machine(second).address==address)
                            check(!machine(second).matchesDevice("AA:BB:CC:DD:EE:02"))
                        }
                        failClear=false
                        if(kind=="SHOT")check(shot(second).clear(shot(second).captureOwnership())) else check(machine(second).clear(machine(second).captureOwnership()))
                        val cleared=fresh()
                        check(cleared.machineControlSafetyMessage==null)
                        check(entries(cleared).all { it==cleared.getString(R.string.service_unavailable) })
                        check(File(context.noBackupFilesDir,if(kind=="SHOT")"pending_shot.json" else "pending_machine_write.json").exists().not())
                        check(systemCalls==0);checked++
                    }catch(error:Throwable) { failure=IllegalStateException("$kind/$throws",error) }
                }
                failure?.let { throw it }
            }catch(error:Throwable) { primary=error;throw error }
            finally {
                var cleanup:Throwable?=null
                fun cleaned(action:()->Unit) { try { action() }catch(error:Throwable) {
                    val first=primary?:cleanup;if(first==null)cleanup=error else if(first!==error)first.addSuppressed(error)
                } }
                cleaned { check(folder.deleteRecursively()) }
                names.forEach { name->
                    cleaned { check(app.deleteSharedPreferences("cache_${id}_$name")) }
                    cleaned { check(app.getSharedPreferences(name,Context.MODE_PRIVATE).all.toMap()==original[name]) }
                }
                cleanup?.let { throw it }
            }
        }
        check(checked==12 && blockedEntries==72)
    }
}

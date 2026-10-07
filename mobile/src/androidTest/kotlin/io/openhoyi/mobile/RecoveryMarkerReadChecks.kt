package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import io.openhoyi.bluetooth.SharedPreferenceTareStorage
import io.openhoyi.protocol.*
import io.openhoyi.session.*
import java.io.File
import java.util.UUID

/** Real disk inputs into product recovery lazies; existing control qualifications are unchanged. */
internal class RecoveryMarkerReadChecks(private val test:Instrumentation) {
    private fun field(subject:Any,name:String)=subject.javaClass.getDeclaredField(name).apply { isAccessible=true }
    fun run() {
        val app=test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName=="io.openhoyi.mobile.mock")
        val address="AA:BB:CC:DD:EE:01"
        fun decode(hex:String)=(HoyiCodec.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) as DecodeResult.Valid).value
        val schedule=requireNotNull(WeeklySleepSchedule.fromReadback(
            decode("8340FE0A00071E0A00071E0A00071E0A00071E3D") as SleepPart,
            decode("83800A00071E0A00071E0A00071E10") as SleepPart))
        val cases=listOf("ABSENT","EMPTY","BAD_JSON","OVERSIZE","DIRECTORY","CLEARED","MISSING","BAD_TYPE","VALID","RESIDUAL")
        var records=0;var blocked=0;var clear=0;var tares=0
        for(role in listOf("SHOT","MACHINE","TARE")) for(mode in if(role=="TARE")listOf("ABSENT","EMPTY","DIRECTORY","BAD_JSON") else cases) {
            val id=UUID.randomUUID().toString();val folder=File(app.cacheDir,"recovery-marker-read-$id")
            val names=listOf("shot_safety","machine_write_safety","scale_tare_safety")
            val original=names.associateWith { app.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap() }
            var systemCalls=0
            val context=object:ContextWrapper(app) {
                override fun getApplicationContext():Context=this
                override fun getNoBackupFilesDir()=File(folder,"no-backup")
                override fun getSystemService(name:String):Any? { systemCalls++;error("No system service") }
                override fun checkSelfPermission(permission:String):Int { systemCalls++;error("No permission access") }
                override fun startService(intent:Intent):android.content.ComponentName? { systemCalls++;error("No component") }
                override fun startForegroundService(intent:Intent):android.content.ComponentName? { systemCalls++;error("No component") }
                override fun bindService(intent:Intent,connection:ServiceConnection,flags:Int):Boolean { systemCalls++;error("No component") }
                override fun startActivity(intent:Intent) { systemCalls++;error("No component") }
                override fun startActivity(intent:Intent,options:Bundle?) { systemCalls++;error("No component") }
                override fun stopService(intent:Intent):Boolean { systemCalls++;error("No component") }
                override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                    check(name in names);return app.getSharedPreferences("marker_${id}_$name",mode)
                }
            }
            var primary:Throwable?=null
            try {
                // Clear cached preferences deliberately: only the file may establish pending evidence.
                check(context.getSharedPreferences("shot_safety",Context.MODE_PRIVATE).edit().putBoolean("unresolved_shot",false).commit())
                check(context.getSharedPreferences("machine_write_safety",Context.MODE_PRIVATE).edit().clear().commit())
                check(context.getSharedPreferences("scale_tare_safety",Context.MODE_PRIVATE).edit().putBoolean("unresolved_tare",false).commit())
                val marker=File(context.noBackupFilesDir,when(role) { "SHOT"->"pending_shot.json";"MACHINE"->"pending_machine_write.json";else->"scale_tare_pending" })
                check(requireNotNull(marker.parentFile).mkdirs())
                val content=when(mode) {
                    "EMPTY"->""
                    "BAD_JSON"->"{"
                    "OVERSIZE"->"x".repeat(4097)
                    "CLEARED"->if(role=="SHOT")"{\"pending\":false,\"address\":null}" else "{\"kind\":null,\"address\":null}"
                    "MISSING"->"{}"
                    "BAD_TYPE"->if(role=="SHOT")"{\"pending\":[],\"address\":null}" else "{\"kind\":\"future_kind\",\"address\":null}"
                    "VALID"->if(role=="SHOT")"{\"pending\":true,\"address\":\"$address\"}" else "{\"kind\":\"SETTING\",\"address\":\"$address\"}"
                    "RESIDUAL"->if(role=="SHOT")"{\"pending\":false,\"address\":\"$address\"}" else "{\"kind\":null,\"address\":\"$address\"}"
                    else->null
                }
                if(mode=="DIRECTORY")check(marker.mkdir()) else if(content!=null)marker.writeText(content)
                var failure:Throwable?=null
                test.runOnMainSync {
                    try {
                        if(role=="TARE") {
                            val tracker=StandaloneTare(SharedPreferenceTareStorage(context)) { 10000L }
                            check(tracker.unresolved==(mode!="ABSENT"))
                            if(mode!="ABSENT")check(tracker.state==StandaloneTare.State.UNKNOWN)
                            tares++
                        } else {
                            val service=MobileService()
                            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext",Context::class.java).apply { isAccessible=true }.invoke(service,context)
                            Service::class.java.getDeclaredField("mApplication").apply { isAccessible=true }.set(service,app)
                            field(service,"mock").set(service,null)
                            check(field(service,"hub").get(service)==null && !field(service,"running").getBoolean(service))
                            val shot=(field(service,"shotRecovery\$delegate").get(service) as Lazy<*>).value as ShotRecoveryState
                            val machine=(field(service,"machineWriteRecovery\$delegate").get(service) as Lazy<*>).value as MachineWriteRecoveryState
                            check(if(role=="SHOT")!machine.pending else !shot.pending)
                            val hasPending=mode!="ABSENT"
                            check(if(role=="SHOT")shot.pending==hasPending else machine.pending==hasPending)
                            if(mode in listOf("VALID","RESIDUAL")) {
                                check(if(role=="SHOT")shot.address==address && !shot.matchesDevice("AA:BB:CC:DD:EE:02") else machine.address==address && !machine.matchesDevice("AA:BB:CC:DD:EE:02"))
                            } else if(hasPending)check(if(role=="SHOT")shot.address==null else machine.kind==MachineWriteRecoveryState.Kind.UNKNOWN && machine.address==null)
                            if(role=="MACHINE" && mode=="VALID")check(machine.kind==MachineWriteRecoveryState.Kind.SETTING)
                            if(role=="MACHINE" && mode=="RESIDUAL")check(machine.kind==MachineWriteRecoveryState.Kind.UNKNOWN)
                            val warning=service.machineControlSafetyMessage
                            check((warning!=null)==hasPending)
                            val entries=listOf(service.changeMachineSetting(MachineSettingChange.BrewTemperature(93)),service.resetCupCount(25),
                                service.changeSleepSchedule(schedule,schedule),service.enterSleepNow(),service.prepareBrew("fixture",false,7),service.startShot("fixture",false,7))
                            if(hasPending) { check(entries.all { it==warning });blocked+=6 }
                            else { check(entries.all { it==service.getString(R.string.service_unavailable) });clear++ }
                            check(mode=="ABSENT" || marker.exists())
                            records++
                        }
                        check(systemCalls==0)
                    }catch(error:Throwable) { failure=IllegalStateException("$role/$mode",error) }
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
                    cleaned { check(app.deleteSharedPreferences("marker_${id}_$name")) }
                    cleaned { check(app.getSharedPreferences(name,Context.MODE_PRIVATE).all.toMap()==original[name]) }
                }
                cleanup?.let { throw it }
            }
        }
        check(records==20 && blocked==108 && clear==2 && tares==4)
    }
}

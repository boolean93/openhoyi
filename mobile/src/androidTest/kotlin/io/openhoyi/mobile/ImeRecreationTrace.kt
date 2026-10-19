package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Read-only native IME tracing around one existing fixture. Never requests focus/show/hide. */
internal class ImeRecreationTrace(private val test:Instrumentation,private val fixture:File) {
    private val directory=File(fixture,"ime-recreation-native")
    private val token=UUID.randomUUID().toString()
    private val events=JSONArray()
    private val commands=JSONArray()
    private val proof=JSONObject().put("schemaVersion",1).put("traceOwnerToken",token)
        .put("package",test.targetContext.packageName).put("fixture","Import.raw.recreate")
        .put("sourceSha",fixture.parentFile.parentFile.name).put("profile",fixture.parentFile.name)
        .put("productionImePolicyChanged",false).put("originalBusinessAssertionsChanged",false)
        .put("clock","real_SystemClock_elapsedRealtimeNanos; never MobileMonotonicClock")
        .put("shellOutputPolicy","stdout_and_stderr_explicitly_merged; rc_marker_required")
        .put("events",events).put("commands",commands).put("ownedStart",false).put("stopAttempted",false)
        .put("status","NOT_STARTED")
    private var owned=false
    private var stopAttempted=false
    private fun persist() {
        check(directory.isDirectory || directory.mkdirs())
        // Same-filesystem rename prevents host fallback from observing a partially written owner acknowledgement.
        val temporary=File(directory,"trace-state.json.tmp")
        temporary.writeText(proof.toString(2))
        check(temporary.renameTo(File(directory,"trace-state.json"))) {"Cannot persist native trace ownership proof"}
    }
    fun mark(name:String) {
        events.put(JSONObject().put("name",name).put("elapsedRealtimeNanos",SystemClock.elapsedRealtimeNanos())
            .put("wallTimeMs",System.currentTimeMillis()))
        persist()
    }
    private fun command(name:String,body:String):JSONObject {
        check(Looper.myLooper()!==Looper.getMainLooper()) {"Native trace shell I/O must not block the UI thread"}
        val started=SystemClock.elapsedRealtime();val marker="IME_TRACE_RC_$token="
        val executor=Executors.newSingleThreadExecutor {task->Thread(task,"ime-recreation-shell-$name")}
        val descriptor=java.util.concurrent.atomic.AtomicReference<ParcelFileDescriptor?>()
        val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
        val result=JSONObject().put("name",name).put("command",body).put("timeoutMs",10000)
        commands.put(result)
        try {
            val future=executor.submit<String> {
                val escaped=body.replace("'","'\\''")
                val wrapped="sh -c '$escaped; command_status=\$?; printf \"\\n$marker%s\\n\" \"\$command_status\"' 2>&1"
                val opened=test.uiAutomation.executeShellCommand(wrapped)
                descriptor.set(opened)
                // An interrupted Binder call may return its PFD after the owner timed out.
                // Whichever side wins the race closes it; no late result may start another read.
                if(cancelled.get()) {opened.close();error("Native trace command returned after cancellation")}
                ParcelFileDescriptor.AutoCloseInputStream(opened).use {input->
                    val bytes=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096)
                    while(true) {val count=input.read(buffer);if(count<0)break
                        check(bytes.size()+count<=131072) {"Native trace command output exceeded diagnostic limit"};bytes.write(buffer,0,count)}
                    bytes.toString(Charsets.UTF_8.name())
                }
            }
            val output=future.get(10000,TimeUnit.MILLISECONDS)
            val lines=output.replace("\r\n","\n").lines()
            val code=lines.filter {it.startsWith(marker)}.singleOrNull()?.removePrefix(marker)?.toIntOrNull()
            result.put("output",output).put("exitCode",code ?: JSONObject.NULL)
                .put("completedWithExitMarker",code!=null)
        } catch(error:Throwable) {result.put("completedWithExitMarker",false).put("failure",error.stackTraceToString())}
        finally {
            cancelled.set(true)
            runCatching {descriptor.get()?.close()}
            executor.shutdownNow()
            val terminated=executor.awaitTermination(2,TimeUnit.SECONDS)
            if(!terminated)result.put("lifecycleStatus","DIAGNOSTIC_FAIL_WORKER_NOT_TERMINATED; not_cleaned")
            result.put("workerTerminated",terminated)
                .put("elapsedMs",SystemClock.elapsedRealtime()-started)
            persist()
        }
        return result
    }
    private fun success(value:JSONObject)=value.optBoolean("completedWithExitMarker") && value.optInt("exitCode",-1)==0 && value.optBoolean("workerTerminated")
    fun start() {
        try {
            mark("diagnostic_preparation_begin")
            val uid=command("shell-uid","id -u")
            val text=uid.optString("output").lineSequence().map {it.trim()}.filter {it.isNotEmpty() && !it.startsWith("IME_TRACE_RC_")}.toList()
            val build=command("build-type","getprop ro.build.type")
            val type=build.optString("output").lineSequence().map {it.trim()}.firstOrNull {it in setOf("user","userdebug","eng")}
            val help=command("input-method-help","cmd input_method help")
            val helpText=help.optString("output")
            if(!success(uid) || text.size!=1 || text[0] !in setOf("0","2000") || !success(build) || type !in setOf("userdebug","eng") ||
                !success(help) || !helpText.contains("tracing") || !helpText.contains("start") || !helpText.contains("stop")) {
                proof.put("status","DIAGNOSTIC_UNAVAILABLE").put("reason","actual_shell_uid_build_or_tracing_help_not_supported_or_unknown")
                return
            }
            val start=command("start","cmd input_method tracing start")
            // Android14 writes this acknowledgement only on a new start. rc0/empty or already-started is NOT ownership.
            val expected="Starting tracing in /data/misc/wmtrace/: ime_trace_clients.winscope, ime_trace_service.winscope, ime_trace_managerservice.winscope"
            if(success(start) && start.optString("output").lineSequence().any {it.trim()==expected}) {
                owned=true;proof.put("ownedStart",true).put("status","OWNED_STARTED")
                mark("owned_start_acknowledged")
            } else proof.put("status","DIAGNOSTIC_FAIL_UNKNOWN_START_OWNERSHIP").put("reason","no_exact_success_ack; no_stop_of_unknown_trace")
        } catch(error:Throwable) {proof.put("status","DIAGNOSTIC_FAIL").put("preparationFailure",error.stackTraceToString())}
        finally {proof.put("diagnosticPreparationFinishedAtElapsedNs",SystemClock.elapsedRealtimeNanos());persist()}
    }
    fun finish():JSONObject {
        try {
            mark("finally_stop_begin")
            if(owned && !stopAttempted) {
                stopAttempted=true;proof.put("stopAttempted",true);persist()
                val stop=command("stop","cmd input_method tracing stop")
                val expected="Stopping tracing and writing traces in /data/misc/wmtrace/: ime_trace_clients.winscope, ime_trace_service.winscope, ime_trace_managerservice.winscope"
                proof.put("stopAcknowledged",success(stop) && stop.optString("output").lineSequence().any {it.trim()==expected})
                proof.put("status",if(proof.getBoolean("stopAcknowledged"))"OWNED_STOP_ACK_FILES_NOT_YET_VERIFIED" else "DIAGNOSTIC_FAIL_STOP_UNKNOWN")
            }
            mark("finally_stop_finished")
        } catch(error:Throwable) {proof.put("status","DIAGNOSTIC_FAIL").put("finishFailure",error.stackTraceToString())}
        finally {persist()}
        return JSONObject(proof.toString())
    }
}

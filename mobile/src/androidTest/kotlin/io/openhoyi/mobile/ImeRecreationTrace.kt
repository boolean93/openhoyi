package io.openhoyi.mobile

import android.app.Instrumentation
import android.os.Looper
import android.os.Process
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Read-only native IME tracing around one existing fixture. Never requests focus/show/hide. */
internal class ImeRecreationTrace(private val test:Instrumentation,private val fixture:File,
    private val fixtureLabel:String="Import.raw.recreate",
    private val captureCallerSite:String="CurveVisualMigrationChecks.importBusiness",
    private val priorRawReference:String?="build/task-first-evidence/ci-bcc64c1-curve/raw; historical run only") {
    private val directory=File(fixture,"ime-recreation-native")
    private val token=UUID.randomUUID().toString()
    private val events=JSONArray()
    private val commands=JSONArray()
    private val proof=JSONObject().put("schemaVersion",2).put("traceOwnerToken",token)
        .put("package",test.targetContext.packageName).put("fixture",fixtureLabel)
        .put("sourceSha",requireNotNull(requireNotNull(fixture.parentFile).parentFile).name).put("profile",requireNotNull(fixture.parentFile).name)
        .put("productionImePolicyChanged",false).put("originalBusinessAssertionsChanged",false)
        .put("clock","real_SystemClock_elapsedRealtimeNanos; never MobileMonotonicClock")
        .put("shellOutputPolicy","Rwe_fixed_shell_stdin; separate_bounded_stdout_stderr; per_command_nonce_rc; one_real_deadline")
        .put("events",events).put("commands",commands).put("ownedStart",false).put("stopAttempted",false)
        .put("status","NOT_STARTED")
    private val linksEvents=JSONArray()
    private val linksLock=Any()
    private val linksFileLock=Any()
    private var linksRevision=0L
    private var writtenLinksRevision=-1L
    private val links=JSONObject().put("schemaVersion",1).put("clock","device_SystemClock.elapsedRealtimeNanos")
        .put("events",linksEvents).put("comparison",JSONObject.NULL).put("comparisonStatus","unknown_replacement_missing")
        .put("trackerHistory",JSONObject().put("rawFiles",JSONArray(listOf("ime-diagnostics.txt","input-method.txt")))
            .put("currentRunRequestTags",JSONObject.NULL).put("currentRunOriginReasonTerminal",JSONObject.NULL)
            .put("relationshipToAppEvents","unknown")
            .put("priorRawReference",priorRawReference ?: JSONObject.NULL)
            .apply { if(priorRawReference==null) put("priorRawReferenceReason","no_same_scope_prior_native_trace") })
        .put("requestTagIsWindowToken",false).put("requestTagIsImeToken",false)
        .put("frameworkRequestInheritance","unknown").put("nearbyEventsRelationship","unknown")
        .apply { if(priorRawReference==null) put("nativeTraceRaw",JSONObject()
            .put("hostDirectory","form-visual-migration/ime-native-traces/light-1.0-curve-editor")
            .put("deviceDirectory","curve-editor/ime-recreation-native")
            .put("files",JSONArray(listOf("ime_trace_clients.winscope","ime_trace_service.winscope","ime_trace_managerservice.winscope")))
            .put("timeBase","device_elapsedRealtimeNanos; proto alignment pending actual collection")
            .put("appToNativeRequestRelationship","unknown")) }
    data class Snapshot(val activity:Any,val field:View,val decor:View,val windowToken:Any?)
    private fun id(value:Any?)=value?.let(System::identityHashCode) ?: JSONObject.NULL
    private fun editLinks(action:()->Unit) { synchronized(linksLock) { action();linksRevision++ } }
    private fun persistLinks() {
        val snapshot=synchronized(linksLock) { linksRevision to links.toString(2) }
        synchronized(linksFileLock) {
            if(snapshot.first<=writtenLinksRevision) return
        check(fixture.isDirectory || fixture.mkdirs())
            val temporary=File(fixture,"ime-causal-links.json.tmp")
            temporary.writeText(snapshot.second)
            check(temporary.renameTo(File(fixture,"ime-causal-links.json")))
            writtenLinksRevision=snapshot.first
        }
    }
    fun capture(name:String,activity:Any,field:View,decor:View,callerSite:String=captureCallerSite):Snapshot {
        val captured=Snapshot(activity,field,decor,field.windowToken)
        editLinks { linksEvents.put(JSONObject().put("name",name).put("callerSite",callerSite)
            .put("elapsedRealtimeNanos",SystemClock.elapsedRealtimeNanos()).put("pid",Process.myPid()).put("tid",Process.myTid())
            .put("activityIdentity",id(activity)).put("fieldIdentity",id(field)).put("decorIdentity",id(decor))
            .put("fieldId",field.id).put("windowTokenIdentity",id(captured.windowToken))
            .put("windowTokenText",captured.windowToken?.toString() ?: JSONObject.NULL)
            .put("fieldAttached",field.isAttachedToWindow)) }
        return captured
    }
    fun compare(old:Snapshot,new:Snapshot) {
        editLinks { links.put("comparison",JSONObject().put("sameActivity",old.activity===new.activity)
            .put("sameField",old.field===new.field).put("sameDecor",old.decor===new.decor)
            .put("sameWindowToken",if(old.windowToken==null || new.windowToken==null) JSONObject.NULL else old.windowToken===new.windowToken)
            .put("windowTokenComparisonStatus",if(old.windowToken==null || new.windowToken==null) "unknown_unattached" else "direct_reference_comparison"))
            .put("comparisonStatus","direct_same_process_references") }
    }
    fun observe(name:String,activity:Any?=null,view:View?=null,callerSite:String="CurveVisualMigrationChecks.importBusiness.observer",
        viewRole:String?=null) {
        editLinks { linksEvents.put(JSONObject().put("name",name).put("callerSite",callerSite)
            .put("elapsedRealtimeNanos",SystemClock.elapsedRealtimeNanos()).put("pid",Process.myPid()).put("tid",Process.myTid())
            .put("activityIdentity",id(activity)).put("viewIdentity",id(view))
            .put("viewRole",viewRole ?: JSONObject.NULL).put("decorIdentity",if(viewRole=="decor") id(view) else JSONObject.NULL)
            .put("viewAttached",view?.isAttachedToWindow ?: JSONObject.NULL)) }
        // Keep lifecycle/attach callbacks read-only and off the fixture's disk I/O path.
        if(Looper.myLooper()!==Looper.getMainLooper()) runCatching { persistLinks() }
    }
    fun productEvidence(value:JSONObject) { editLinks { links.put("productRestoreEvidence",value) };persistLinks() }
    private var owned=false
    private var stopAttempted=false
    private fun persist() {
        check(directory.isDirectory || directory.mkdirs())
        // Same-filesystem rename prevents host fallback from observing a partially written owner acknowledgement.
        val temporary=File(directory,"trace-state.json.tmp")
        temporary.writeText(proof.toString(2))
        check(temporary.renameTo(File(directory,"trace-state.json"))) {"Cannot persist native trace ownership proof"}
    }
    fun mark(name:String,callerSite:String="ImeRecreationTrace") {
        val elapsed=SystemClock.elapsedRealtimeNanos()
        events.put(JSONObject().put("name",name).put("elapsedRealtimeNanos",elapsed)
            .put("wallTimeMs",System.currentTimeMillis()))
        editLinks { linksEvents.put(JSONObject().put("name",name).put("callerSite",callerSite)
            .put("elapsedRealtimeNanos",elapsed).put("pid",Process.myPid()).put("tid",Process.myTid()))
        }
        runCatching { persistLinks() }.onFailure { proof.put("causalLinksWriteError",it.stackTraceToString())
            proof.put("causalLinksStatus","DIAGNOSTIC_FAIL_WRITE") }
        persist()
    }
    private fun command(name:String,body:String):JSONObject {
        check(Looper.myLooper()!==Looper.getMainLooper()) {"Native trace shell I/O must not block the UI thread"}
        val started=SystemClock.elapsedRealtime()
        val deadline=started+10000L
        val nonce=UUID.randomUUID().toString()
        val marker="IME_TRACE_RC_$nonce="
        val result=JSONObject().put("name",name).put("command",body).put("timeoutMs",10000)
            .put("commandNonce",nonce).put("process","/system/bin/sh").put("transport","executeShellCommandRwe_stdin_UTF8")
        commands.put(result)
        persist() // Persist the per-command intent/nonce before any potentially side-effecting command.
        val executor=Executors.newFixedThreadPool(4) {task->Thread(task,"ime-recreation-shell-$name")}
        val descriptors=java.util.concurrent.atomic.AtomicReference<Array<ParcelFileDescriptor>?>()
        val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
        val aggregate=java.util.concurrent.atomic.AtomicInteger(0)
        val stdout=java.io.ByteArrayOutputStream()
        val stderr=java.io.ByteArrayOutputStream()
        val tasks=mutableListOf<java.util.concurrent.Future<*>>()
        fun closeAll(opened:Array<ParcelFileDescriptor>?) {opened?.forEach {runCatching {it.close()}}}
        fun remaining():Long=(deadline-SystemClock.elapsedRealtime()).also {check(it>0) {"Native trace total deadline exhausted"}}
        fun read(fd:ParcelFileDescriptor,sink:java.io.ByteArrayOutputStream) {
            ParcelFileDescriptor.AutoCloseInputStream(fd).use {input->
                val buffer=ByteArray(4096)
                while(true) {
                    val count=input.read(buffer);if(count<0)break
                    check(aggregate.addAndGet(count)<=131072) {"Native trace aggregate output exceeded diagnostic limit"}
                    synchronized(sink) {sink.write(buffer,0,count)}
                }
            }
        }
        try {
            val opening=executor.submit<Array<ParcelFileDescriptor>> {
                val opened=test.uiAutomation.executeShellCommandRwe("/system/bin/sh")
                descriptors.set(opened)
                // Binder can return after cancellation: the late owner must close all three FDs.
                if(cancelled.get()) {closeAll(opened);error("Native trace Rwe returned after cancellation")}
                check(opened.size==3) {"Unexpected Rwe descriptor count"}
                opened
            }
            tasks.add(opening)
            val opened=opening.get(remaining(),TimeUnit.MILLISECONDS)
            val out=executor.submit {read(opened[0],stdout)}
            val err=executor.submit {read(opened[2],stderr)}
            val script=body+"\ncommand_status=\$?\nprintf '\n"+marker+"%s\n' \"\$command_status\"\nexit 0\n"
            val writer=executor.submit {
                ParcelFileDescriptor.AutoCloseOutputStream(opened[1]).use {output->
                    output.write(script.toByteArray(Charsets.UTF_8));output.flush()
                }
            }
            tasks.addAll(listOf(out,err,writer))
            writer.get(remaining(),TimeUnit.MILLISECONDS);result.put("stdinWrittenAndClosed",true)
            out.get(remaining(),TimeUnit.MILLISECONDS);result.put("stdoutEof",true)
            err.get(remaining(),TimeUnit.MILLISECONDS);result.put("stderrEof",true)
            val output=synchronized(stdout) {stdout.toString(Charsets.UTF_8.name())}
            val markers=output.replace("\r\n","\n").lines().filter {it.startsWith(marker)}
            val allMarkers=output.lineSequence().filter {it.trim().startsWith("IME_TRACE_RC_")}.count()
            val code=markers.singleOrNull()?.removePrefix(marker)?.takeIf {it.matches(Regex("[0-9]{1,3}"))}?.toIntOrNull()?.takeIf {it in 0..255}
            val terminal=Regex("\\r?\\n"+Regex.escape(marker)+"[0-9]{1,3}\\r?\\n\\z")
            result.put("exitCode",code ?: JSONObject.NULL).put("completedWithExitMarker",code!=null && allMarkers==1 && terminal.containsMatchIn(output))
            result.put("output",output.replace(terminal,""))
        } catch(error:Throwable) {
            result.put("completedWithExitMarker",false).put("failure",error.stackTraceToString())
        } finally {
            cancelled.set(true);closeAll(descriptors.get())
            tasks.forEach {if(!it.isDone)it.cancel(true)}
            executor.shutdownNow()
            val wait=(deadline-SystemClock.elapsedRealtime()).coerceAtLeast(0)
            val terminated=executor.awaitTermination(wait,TimeUnit.MILLISECONDS)
            result.put("stdout",synchronized(stdout) {stdout.toString(Charsets.UTF_8.name())})
                .put("stderr",synchronized(stderr) {stderr.toString(Charsets.UTF_8.name())})
                .put("stdoutBytes",synchronized(stdout) {stdout.size()}).put("stderrBytes",synchronized(stderr) {stderr.size()})
                .put("aggregateReadBytes",aggregate.get()).put("workerTerminated",terminated)
                .put("withinTotalBudget",SystemClock.elapsedRealtime()<=deadline).put("elapsedMs",SystemClock.elapsedRealtime()-started)
            if(!terminated)result.put("lifecycleStatus","DIAGNOSTIC_FAIL_WORKER_NOT_TERMINATED; not_cleaned")
            persist()
        }
        return result
    }
    private fun transportComplete(value:JSONObject)=value.optBoolean("completedWithExitMarker") &&
        value.optBoolean("stdinWrittenAndClosed") && value.optBoolean("stdoutEof") && value.optBoolean("stderrEof") &&
        value.optBoolean("workerTerminated") && value.optBoolean("withinTotalBudget")
    private fun success(value:JSONObject)=transportComplete(value) && value.optInt("exitCode",-1)==0 && value.optString("stderr").isEmpty()
    private fun uniqueAcknowledgement(value:JSONObject,expected:String)=success(value) &&
        value.optString("output").lineSequence().map {it.trim()}.filter {it.isNotEmpty()}.toList()==listOf(expected)
    private fun helpSuccess(value:JSONObject):Boolean {
        val lines=value.optString("output").lineSequence().map {it.trim().replace(Regex(" +")," ")}.toSet()
        return transportComplete(value) && value.optInt("exitCode",-1) in setOf(0,255) && value.optString("stderr").isEmpty() &&
            lines.containsAll(setOf("InputMethodManagerService commands:","tracing <command>","start: Start tracing.","stop : Stop tracing.","help : Show help."))
    }
    fun start() {
        try {
            mark("diagnostic_preparation_begin")
            val probe=command("transport-probe","(printf 'IME_TRANSPORT_STDOUT\\n'; printf 'IME_TRANSPORT_STDERR\\n' >&2; exit 7)")
            if(!transportComplete(probe) || probe.optInt("exitCode",-1)!=7 ||
                probe.optString("output")!="IME_TRANSPORT_STDOUT\n" || probe.optString("stderr")!="IME_TRANSPORT_STDERR\n") {
                proof.put("status","DIAGNOSTIC_UNAVAILABLE").put("reason","Rwe_two_stream_exit7_transport_probe_failed");return
            }
            val uid=command("shell-uid","id -u")
            val text=uid.optString("output").lineSequence().map {it.trim()}.filter {it.isNotEmpty() && !it.startsWith("IME_TRACE_RC_")}.toList()
            val build=command("build-type","getprop ro.build.type")
            val type=build.optString("output").lineSequence().map {it.trim()}.firstOrNull {it in setOf("user","userdebug","eng")}
            val help=command("input-method-help","cmd input_method help")
            if(!success(uid) || text.size!=1 || text[0] !in setOf("0","2000") || !success(build) || type !in setOf("userdebug","eng") ||
                !helpSuccess(help)) {
                proof.put("status","DIAGNOSTIC_UNAVAILABLE").put("reason","actual_shell_uid_build_or_tracing_help_not_supported_or_unknown")
                return
            }
            proof.put("startIntent",true).put("startIntentElapsedNs",SystemClock.elapsedRealtimeNanos());persist()
            val start=command("start","cmd input_method tracing start")
            // Android14 writes this acknowledgement only on a new start. rc0/empty or already-started is NOT ownership.
            val expected="Starting tracing in /data/misc/wmtrace/: ime_trace_clients.winscope, ime_trace_service.winscope, ime_trace_managerservice.winscope"
            if(uniqueAcknowledgement(start,expected)) {
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
                proof.put("stopAcknowledged",uniqueAcknowledgement(stop,expected))
                proof.put("status",if(proof.getBoolean("stopAcknowledged"))"OWNED_STOP_ACK_FILES_NOT_YET_VERIFIED" else "DIAGNOSTIC_FAIL_STOP_UNKNOWN")
            }
            mark("finally_stop_finished")
        } catch(error:Throwable) {proof.put("status","DIAGNOSTIC_FAIL").put("finishFailure",error.stackTraceToString())}
        finally {persist()}
        return JSONObject(proof.toString())
    }
}

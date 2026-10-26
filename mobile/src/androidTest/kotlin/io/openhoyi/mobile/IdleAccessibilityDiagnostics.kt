package io.openhoyi.mobile

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Isolated, Mock-package-only observation. Pausing one Activity redraw is a diagnostic probe,
 * never a fix or an acceptance result. The MobileService and its sampling/control loop stay intact. */
internal class IdleAccessibilityDiagnostics(private val test:Instrumentation) {
    private val context get()=test.targetContext
    private val activeWindow=AtomicReference<String?>(null)
    private val events=ConcurrentLinkedQueue<Event>()
    private val changes=ConcurrentLinkedQueue<TextChange>()
    private val watcherBindings=mutableListOf<Pair<TextView,TextWatcher>>()
    private data class Event(val window:String,val atMs:Long,val type:Int,val contentChanges:Int,
        val sourceClass:String?,val text:String,val viewId:String?,val bounds:String,val sourceError:String?)
    private data class TextChange(val window:String,val atMs:Long,val node:String,val before:String,val after:String)
    private data class OwnerState(val identity:Int,val running:Boolean,val coffeeAt:Long?,val weightAt:Long?)
    private data class Window(val name:String,val fromMs:Long,val toMs:Long,val before:OwnerState,val after:OwnerState)
    private data class ReadyObservation(val atMs:Long,val homeIdentity:Int?,val lifecyclePhase:String,
        val destroyed:Boolean?,val focus:Boolean?,val decorAttached:Boolean?,val decorLaidOut:Boolean?,
        val decorLayoutRequested:Boolean?,val decorWidth:Int?,val decorHeight:Int?,val tokenIdentity:Int?,
        val ownerPresent:Boolean?,val ownerIdentity:Int?,val ownerRunning:Boolean?) {
        fun unmet():List<String> = buildList {
            if(homeIdentity==null)add("currentHomeAbsent")
            if(destroyed!=false)add("homeDestroyedOrUnknown")
            if(focus!=true)add("homeFocusMissing")
            if(decorAttached!=true)add("decorDetachedOrUnknown")
            if(decorLaidOut!=true)add("decorNotLaidOut")
            if(decorLayoutRequested==true)add("decorLayoutRequested")
            if((decorWidth ?: 0)<=0 || (decorHeight ?: 0)<=0)add("decorEmptyOrUnknown")
            if(tokenIdentity==null)add("windowTokenMissing")
            if(ownerPresent!=true)add("ownerAbsent")
            if(ownerRunning!=true)add("ownerNotRunning")
        }
        fun json()=JSONObject().put("atElapsedMs",atMs).put("currentHomeIdentity",homeIdentity ?: JSONObject.NULL)
            .put("lifecyclePhase",lifecyclePhase).put("destroyed",destroyed ?: JSONObject.NULL)
            .put("focus",focus ?: JSONObject.NULL).put("decorAttached",decorAttached ?: JSONObject.NULL)
            .put("decorLaidOut",decorLaidOut ?: JSONObject.NULL).put("decorLayoutRequested",decorLayoutRequested ?: JSONObject.NULL)
            .put("decorSizePx",if(decorWidth!=null && decorHeight!=null)JSONArray(listOf(decorWidth,decorHeight)) else JSONObject.NULL)
            .put("windowTokenIdentity",tokenIdentity ?: JSONObject.NULL).put("ownerPresent",ownerPresent ?: JSONObject.NULL)
            .put("ownerIdentity",ownerIdentity ?: JSONObject.NULL).put("ownerRunning",ownerRunning ?: JSONObject.NULL)
            .put("unmet",JSONArray(unmet()))
    }
    private fun stream(text:String) = test.sendStatus(0,Bundle().apply {putString("stream",text+"\n")})
    private fun quote(value:String?)=JSONObject.quote(value.orEmpty())
    private fun onMain(action:()->Unit) {
        var error:Throwable?=null
        test.runOnMainSync {try {action()} catch(failure:Throwable) {error=failure}}
        error?.let {throw it}
    }
    private fun awaitMain(deadline:Long=SystemClock.elapsedRealtime()+10_000,predicate:()->Boolean) {
        while(true) {
            var ready=false;onMain {ready=predicate()}
            if(ready)return
            check(SystemClock.elapsedRealtime()<deadline) {"Home/service startup timed out (no accessibility idle wait)"}
            SystemClock.sleep(25)
        }
    }
    private fun <T> field(home:MainActivity,name:String):T {
        @Suppress("UNCHECKED_CAST")
        val fragment = MainPageTestHost(test, home).active("root:BREW") as HomeFragment
        return HomeFragment::class.java.getDeclaredField(name).apply {isAccessible=true}.get(fragment) as T
    }
    private fun owner(home:MainActivity):MobileService?=home.currentService
    private fun ownerState(home:MainActivity):OwnerState {
        val service=requireNotNull(owner(home)) {"Home lost its existing MobileService owner"}
        return OwnerState(System.identityHashCode(service),service.running,service.snapshot.coffeeAt,service.snapshot.weightAt)
    }
    private fun views(root:View,path:String="0"):List<Pair<String,View>> = listOf(path to root)+
        if(root is ViewGroup)(0 until root.childCount).flatMap {views(root.getChildAt(it),"$path/$it")} else emptyList()
    private fun id(view:View):String = if(view.id==View.NO_ID)"NO_ID" else
        runCatching {view.resources.getResourceName(view.id)}.getOrElse {"id=${view.id}"}
    private fun bounds(view:View):String {
        val xy=IntArray(2);view.getLocationOnScreen(xy)
        return Rect(xy[0],xy[1],xy[0]+view.width,xy[1]+view.height).flattenToString()
    }
    private fun safeText(view:TextView,value:CharSequence?):String =
        if(view.transformationMethod is PasswordTransformationMethod)"[REDACTED]" else value?.toString().orEmpty().take(512)
    private fun watch(home:MainActivity) {
        onMain {
            views(home.window.decorView).forEach {(path,view)->
                if(view !is TextView)return@forEach
                val key="$path ${view.javaClass.name} ${id(view)} ${bounds(view)}"
                val watcher=object:TextWatcher {
                    private var before=""
                    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {before=safeText(view,s)}
                    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {}
                    override fun afterTextChanged(s:Editable?) {
                        activeWindow.get()?.let {window->changes.add(TextChange(window,SystemClock.elapsedRealtime(),key,before,safeText(view,s)))}
                    }
                }
                view.addTextChangedListener(watcher);watcherBindings+=view to watcher
            }
        }
        stream("IDLE_DIAGNOSTIC_WATCHERS count=${watcherBindings.size} changedAndSameValueCallbacks=true")
    }
    private fun dumpNodes(home:MainActivity,label:String) {
        val lines=mutableListOf<String>()
        onMain {
            views(home.window.decorView).forEach {(path,view)->
                lines+="IDLE_DIAGNOSTIC_NODE stage=$label path=$path class=${quote(view.javaClass.name)} viewId=${quote(id(view))} bounds=${quote(bounds(view))} shown=${view.isShown} enabled=${view.isEnabled} clickable=${view.isClickable} text=${quote((view as? TextView)?.let {safeText(it,it.text)})}"
            }
        }
        lines.chunked(20).forEach {stream(it.joinToString("\n"))}
    }
    private fun sample(home:MainActivity,name:String):Window {
        var before:OwnerState?=null;var from=0L
        onMain {before=ownerState(home);from=SystemClock.elapsedRealtime();activeWindow.set(name)}
        // Instrumentation worker only: no accessibility waitForIdle / UIAutomator idle gate.
        SystemClock.sleep(2_000)
        var after:OwnerState?=null;var to=0L
        onMain {activeWindow.set(null);to=SystemClock.elapsedRealtime();after=ownerState(home)}
        val result=Window(name,from,to,requireNotNull(before),requireNotNull(after))
        check(result.before.identity==result.after.identity && result.before.running && result.after.running) {
            "Mock owner changed/stopped while collecting $name"
        }
        return result
    }
    private fun dump(window:Window) {
        val eventRows=events.filter {it.window==window.name}
        val textRows=changes.filter {it.window==window.name}
        val changed=textRows.count {it.before!=it.after}
        stream("IDLE_DIAGNOSTIC_WINDOW name=${window.name} fromMs=${window.fromMs} toMs=${window.toMs} durationMs=${window.toMs-window.fromMs} events=${eventRows.size} textCallbacks=${textRows.size} actualTextChanges=$changed sameValueTextResets=${textRows.size-changed} ownerBefore=${window.before} ownerAfter=${window.after}")
        eventRows.groupingBy {"type=${it.type} contentChangeTypes=${it.contentChanges} class=${it.sourceClass} viewId=${it.viewId} bounds=${it.bounds}"}
            .eachCount().forEach {(key,count)->stream("IDLE_DIAGNOSTIC_EVENT_COUNT window=${window.name} count=$count $key")}
        textRows.groupingBy {it.node}.eachCount().forEach {(key,count)->
            val rows=textRows.filter {it.node==key}
            stream("IDLE_DIAGNOSTIC_TEXT_COUNT window=${window.name} count=$count actualChanges=${rows.count {it.before!=it.after}} node=${quote(key)}")
        }
        eventRows.map {event->
            "IDLE_DIAGNOSTIC_EVENT window=${event.window} atMs=${event.atMs} type=${AccessibilityEvent.eventTypeToString(event.type)} typeRaw=${event.type} contentChangeTypes=${event.contentChanges} sourceClass=${quote(event.sourceClass)} text=${quote(event.text)} viewId=${quote(event.viewId)} bounds=${quote(event.bounds)} sourceError=${quote(event.sourceError)}"
        }.chunked(20).forEach {stream(it.joinToString("\n"))}
        textRows.map {change->
            "IDLE_DIAGNOSTIC_TEXT window=${change.window} atMs=${change.atMs} changed=${change.before!=change.after} node=${quote(change.node)} before=${quote(change.before)} after=${quote(change.after)}"
        }.chunked(20).forEach {stream(it.joinToString("\n"))}
    }
    fun run() {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock") {"Idle diagnostics are strictly Mock-package-only"}
        val automation=test.uiAutomation
        val info=automation.serviceInfo
        val priorFlags=info.flags
        var home:MainActivity?=null
        var refreshHandler:Handler?=null
        var refreshRunnable:Runnable?=null
        var paused=false
        var restored=false
        val windows=mutableListOf<Window>()
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        val app=context.applicationContext as Application
        val currentHome=AtomicReference<MainActivity?>(null)
        val currentPhase=AtomicReference("not_created")
        val readyLast=AtomicReference<ReadyObservation?>(null)
        val readyChanges=ConcurrentLinkedQueue<ReadyObservation>()
        val callbacks=object:Application.ActivityLifecycleCallbacks {
            private fun track(activity:Activity,phase:String) {
                if(activity is MainActivity) {
                    currentHome.set(activity)
                    currentPhase.set(phase)
                    stream("IDLE_DIAGNOSTIC_HOME phase=$phase identity=${System.identityHashCode(activity)}")
                }
            }
            override fun onActivityCreated(activity:Activity,state:Bundle?)=track(activity,"created")
            override fun onActivityStarted(activity:Activity)=track(activity,"started")
            override fun onActivityResumed(activity:Activity)=track(activity,"resumed")
            override fun onActivityPaused(activity:Activity) {if(activity is MainActivity && currentHome.get()===activity)currentPhase.set("paused")}
            override fun onActivityStopped(activity:Activity) {if(activity is MainActivity && currentHome.get()===activity)currentPhase.set("stopped")}
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle) {}
            override fun onActivityDestroyed(activity:Activity) {
                if(activity is MainActivity && currentHome.compareAndSet(activity,null))currentPhase.set("destroyed")
            }
        }
        var callbacksRegistered=false
        try {
            onMain {app.registerActivityLifecycleCallbacks(callbacks);callbacksRegistered=true}
            info.flags=priorFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            automation.serviceInfo=info
            automation.setOnAccessibilityEventListener {event->
                val window=activeWindow.get() ?: return@setOnAccessibilityEventListener
                if(event.packageName?.toString()!=context.packageName)return@setOnAccessibilityEventListener
                var sourceClass:String?=event.className?.toString()
                var text=if(event.isPassword)"[REDACTED]" else event.text.joinToString(" | ").take(512)
                var viewId:String?=null;var bounds="";var sourceError:String?=null
                try {
                    val source=event.source
                    if(source!=null)try {
                        sourceClass=source.className?.toString() ?: sourceClass
                        text=if(source.isPassword)"[REDACTED]" else source.text?.toString()?.take(512) ?: text
                        viewId=source.viewIdResourceName
                        val rectangle=Rect();source.getBoundsInScreen(rectangle);bounds=rectangle.flattenToString()
                    } finally {@Suppress("DEPRECATION") source.recycle()}
                } catch(error:Throwable) {sourceError="${error.javaClass.simpleName}: ${error.message}"}
                events.add(Event(window,SystemClock.elapsedRealtime(),event.eventType,event.contentChangeTypes,sourceClass,text,viewId,bounds,sourceError))
            }
            onMain {
                context.startActivity(Intent(context,MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            home=test.waitForMonitorWithTimeout(monitor,10_000) as? MainActivity
                ?: error("Home startup not observed")
            val readyDeadline=SystemClock.elapsedRealtime()+10_000
            fun observeReady():Boolean {
                // Only read actual Activity/View/owner state on the main thread.
                val current=currentHome.get()
                val decor=current?.window?.decorView
                val service=current?.let(::owner)
                val observation=ReadyObservation(SystemClock.elapsedRealtime(),current?.let(System::identityHashCode),
                    currentPhase.get(),current?.isDestroyed,current?.hasWindowFocus(),decor?.isAttachedToWindow,
                    decor?.isLaidOut,decor?.isLayoutRequested,decor?.width,decor?.height,
                    decor?.windowToken?.let(System::identityHashCode),current?.let {service!=null},
                    service?.let(System::identityHashCode),service?.running)
                val prior=readyLast.getAndSet(observation)
                if(prior?.copy(atMs=0)!=observation.copy(atMs=0))readyChanges.add(observation)
                if(current!=null)home=current
                return current!=null && !current.isDestroyed && current.hasWindowFocus() &&
                    decor?.isLaidOut==true && service?.running==true
            }
            var mainAckMissing=false
            fun awaitReady() {
                while(true) {
                    val left=readyDeadline-SystemClock.elapsedRealtime()
                    check(left>0) {"Home/service startup timed out (no accessibility idle wait)"}
                    val latch=CountDownLatch(1)
                    val cancelled=AtomicBoolean(false)
                    var ready=false;var failure:Throwable?=null
                    Handler(Looper.getMainLooper()).post {
                        if(!cancelled.get())try {ready=observeReady()} catch(error:Throwable) {failure=error}
                        latch.countDown()
                    }
                    if(!latch.await(left,TimeUnit.MILLISECONDS)) {
                        cancelled.set(true);mainAckMissing=true
                        error("Home/service startup timed out (no accessibility idle wait)")
                    }
                    failure?.let {throw it}
                    if(ready)return
                    check(SystemClock.elapsedRealtime()<readyDeadline) {"Home/service startup timed out (no accessibility idle wait)"}
                    SystemClock.sleep(25)
                }
            }
            fun recordReadyTimeout(failure:Throwable) {
                val last=readyLast.get()
                val status=if(last==null)"UNAVAILABLE_NO_MAIN_ACK" else if(mainAckMissing)"STALE_LAST_MAIN_ACK" else "LAST_MAIN_ACK"
                val diagnostic=JSONObject().put("status","DIAGNOSTIC_NOT_ACCEPTANCE")
                    .put("reason","HOME_READY_TIMEOUT").put("mainState",status)
                    .put("readyDeadlineElapsedMs",readyDeadline).put("recordedAtElapsedMs",SystemClock.elapsedRealtime())
                    .put("lastObservationElapsedMs",last?.atMs ?: JSONObject.NULL)
                    .put("lastMainAck",last?.json() ?: JSONObject.NULL)
                    .put("unmet",JSONArray(last?.unmet() ?: listOf("mainObservationUnavailable")))
                    .put("changes",JSONArray(readyChanges.map {it.json()}))
                    .put("failure",failure.toString())
                try {
                    val folder=File(requireNotNull(context.getExternalFilesDir(null)),"idle-accessibility-diagnostic")
                    check(folder.isDirectory || folder.mkdirs())
                    File(folder,"ready-timeout.json").writeText(diagnostic.toString(2))
                } catch(writeFailure:Throwable) {
                    try {stream("IDLE_DIAGNOSTIC_READY_TIMEOUT_WRITE_FAILED ${writeFailure.javaClass.simpleName}")} catch(_:Throwable) {}
                }
            }
            try {
                awaitReady()
                SystemClock.sleep(1_000) // Discard launch events; both measured windows remain exactly 2s.
                awaitReady()
            } catch(failure:Throwable) {
                if(failure.message?.startsWith("Home/service startup timed out")==true)recordReadyTimeout(failure)
                throw failure
            }
            val screen=requireNotNull(home)
            watch(screen);dumpNodes(screen,"baseline-start")
            windows+=sample(screen,"baseline")
            dumpNodes(screen,"baseline-end")
            onMain {
                check(field<Boolean>(screen,"visible")) {"Home is not visible"}
                refreshHandler=field(screen,"handler");refreshRunnable=field(screen,"refresh")
                requireNotNull(refreshHandler).removeCallbacks(requireNotNull(refreshRunnable));paused=true
            }
            try {windows+=sample(screen,"home-refresh-paused")}
            finally {
                onMain {
                    requireNotNull(refreshHandler).removeCallbacks(requireNotNull(refreshRunnable))
                    requireNotNull(refreshHandler).post(requireNotNull(refreshRunnable))
                    restored=true;paused=false
                }
            }
            dumpNodes(screen,"paused-end-restored")
            windows.forEach(::dump)
            val baseline=windows[0];val probe=windows[1]
            val baselineEvents=events.count {it.window==baseline.name};val probeEvents=events.count {it.window==probe.name}
            val baselineChanges=changes.count {it.window==baseline.name};val probeChanges=changes.count {it.window==probe.name}
            stream("IDLE_DIAGNOSTIC_COMPARISON baselineEvents=$baselineEvents pausedEvents=$probeEvents eventDelta=${baselineEvents-probeEvents} baselineTextCallbacks=$baselineChanges pausedTextCallbacks=$probeChanges textDelta=${baselineChanges-probeChanges} sameService=${baseline.before.identity==probe.after.identity} refreshRestored=$restored observationOnly=true noIdleClaim=true noProductionFix=true noAcceptancePass=true")
        } finally {
            activeWindow.set(null)
            val cleanupErrors=mutableListOf<Throwable>()
            fun cleanup(action:()->Unit) {try {action()} catch(error:Throwable) {cleanupErrors+=error}}
            var listenerRemoved=false;var flagsRestored=false
            cleanup {automation.setOnAccessibilityEventListener(null);listenerRemoved=true} // Isolated branch owns this listener.
            cleanup {info.flags=priorFlags;automation.serviceInfo=info;flagsRestored=true}
            cleanup {onMain {
                if(paused) {
                    requireNotNull(refreshHandler).removeCallbacks(requireNotNull(refreshRunnable))
                    requireNotNull(refreshHandler).post(requireNotNull(refreshRunnable));restored=true;paused=false
                }
            }}
            cleanup {onMain {
                watcherBindings.forEach {(view,watcher)->view.removeTextChangedListener(watcher)}
                watcherBindings.clear()
            }}
            cleanup {test.removeMonitor(monitor)}
            cleanup {onMain {
                if(callbacksRegistered) {app.unregisterActivityLifecycleCallbacks(callbacks);callbacksRegistered=false}
            }}
            cleanup {onMain {home?.let {if(!it.isDestroyed)it.finish()}}}
            stream("IDLE_DIAGNOSTIC_CLEANUP listenerRemoved=$listenerRemoved serviceInfoFlagsRestored=$flagsRestored callbacksRemoved=${!callbacksRegistered} watcherBindings=${watcherBindings.size} refreshRestored=$restored serviceStopInvoked=false errors=${cleanupErrors.size}")
            check(cleanupErrors.isEmpty()) {"Idle diagnostic cleanup failed: ${cleanupErrors.joinToString {it.toString()}}"}
        }
    }
}

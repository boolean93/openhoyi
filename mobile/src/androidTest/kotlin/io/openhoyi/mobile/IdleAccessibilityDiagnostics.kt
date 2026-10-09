package io.openhoyi.mobile

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
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
    private fun <T> field(home:HomeActivity,name:String):T {
        @Suppress("UNCHECKED_CAST")
        return HomeActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.get(home) as T
    }
    private fun owner(home:HomeActivity):MobileService?=field(home,"service")
    private fun ownerState(home:HomeActivity):OwnerState {
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
    private fun watch(home:HomeActivity) {
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
    private fun dumpNodes(home:HomeActivity,label:String) {
        val lines=mutableListOf<String>()
        onMain {
            views(home.window.decorView).forEach {(path,view)->
                lines+="IDLE_DIAGNOSTIC_NODE stage=$label path=$path class=${quote(view.javaClass.name)} viewId=${quote(id(view))} bounds=${quote(bounds(view))} shown=${view.isShown} enabled=${view.isEnabled} clickable=${view.isClickable} text=${quote((view as? TextView)?.let {safeText(it,it.text)})}"
            }
        }
        lines.chunked(20).forEach {stream(it.joinToString("\n"))}
    }
    private fun sample(home:HomeActivity,name:String):Window {
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
        var home:HomeActivity?=null
        var refreshHandler:Handler?=null
        var refreshRunnable:Runnable?=null
        var paused=false
        var restored=false
        val windows=mutableListOf<Window>()
        val monitor=test.addMonitor(HomeActivity::class.java.name,null,false)
        val app=context.applicationContext as Application
        val currentHome=AtomicReference<HomeActivity?>(null)
        val callbacks=object:Application.ActivityLifecycleCallbacks {
            private fun track(activity:Activity,phase:String) {
                if(activity is HomeActivity) {
                    currentHome.set(activity)
                    stream("IDLE_DIAGNOSTIC_HOME phase=$phase identity=${System.identityHashCode(activity)}")
                }
            }
            override fun onActivityCreated(activity:Activity,state:Bundle?)=track(activity,"created")
            override fun onActivityStarted(activity:Activity)=track(activity,"started")
            override fun onActivityResumed(activity:Activity)=track(activity,"resumed")
            override fun onActivityPaused(activity:Activity) {}
            override fun onActivityStopped(activity:Activity) {}
            override fun onActivitySaveInstanceState(activity:Activity,state:Bundle) {}
            override fun onActivityDestroyed(activity:Activity) {
                if(activity is HomeActivity) currentHome.compareAndSet(activity,null)
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
                context.startActivity(Intent(context,HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            home=test.waitForMonitorWithTimeout(monitor,10_000) as? HomeActivity
                ?: error("Home startup not observed")
            val readyDeadline=SystemClock.elapsedRealtime()+10_000
            fun ready():Boolean {
                // ActivityMonitor does not reliably report configuration relaunches.
                // Application callbacks observe each framework-created replacement.
                val current=currentHome.get() ?: return false
                home=current
                return !current.isDestroyed && current.hasWindowFocus() &&
                    current.window.decorView.isLaidOut && owner(current)?.running==true
            }
            awaitMain(readyDeadline,::ready)
            SystemClock.sleep(1_000) // Discard launch events; both measured windows remain exactly 2s.
            awaitMain(readyDeadline,::ready)
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

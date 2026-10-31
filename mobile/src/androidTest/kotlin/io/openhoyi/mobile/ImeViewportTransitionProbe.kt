package io.openhoyi.mobile

import android.app.Activity
import android.os.Build
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** One recreate interval. Observes existing Views; never changes focus, IME, layout, or scroll. */
internal class ImeViewportTransitionProbe(
    private val fixture: String,
    private val resolve: (Activity, ResolutionAttempt) -> Targets,
    private val productEvidence: (Activity) -> JSONObject?
) {
    data class Targets(val root: View, val host: View?, val field: EditText?,
        val scrolls: List<ScrollView>, val resolution: String = "exact_named_references")

    /** Values read by the original resolver, not a later reconstruction of its View tree. */
    inner class ResolutionAttempt(owner: Activity, private val phase: String) {
        private val values=JSONObject()
        private val proof=JSONObject().put("fixture",fixture).put("phase",phase)
            .put("startedElapsedRealtimeNanos",SystemClock.elapsedRealtimeNanos())
            .put("mainThread",Looper.myLooper()===Looper.getMainLooper())
            .put("ownerIdentity",System.identityHashCode(owner))
            .put("ownerClass",owner.javaClass.name).put("stage","created")
            .put("values",values).put("result","in_progress")
        private fun safely(action:()->Unit) {
            try { action() } catch(error:Throwable) {
                try { proof.put("diagnosticFailure",error.stackTraceToString()) } catch(_:Throwable) { }
                try { issues.put("resolution_diagnostic_${phase}_${error.javaClass.name}") } catch(_:Throwable) { }
            }
        }
        fun stage(name:String) { safely { proof.put("stage",name) } }
        fun value(name:String, value:Any?) { safely {
            check(value==null || value is String || value is Boolean || value is Number)
            values.put(name,value ?: JSONObject.NULL)
        } }
        fun reference(name:String, ref:Any?) {
            value("${name}Identity",ref?.let(System::identityHashCode))
            value("${name}Class",ref?.javaClass?.name)
        }
        fun viewReference(name:String, view:View?) {
            reference(name,view)
            safely { reference("${name}Parent",view?.parent) }
        }
        fun finish(error:Throwable?) {
            safely { proof.put("endedElapsedRealtimeNanos",SystemClock.elapsedRealtimeNanos()) }
            safely { proof.put("result",if(error==null) "exact_named_references" else "fallback_partial") }
            if(error!=null) {
                safely { proof.put("resolutionFailureClass",error.javaClass.name) }
                safely { proof.put("resolutionFailureMessage",error.message ?: JSONObject.NULL) }
                safely { proof.put("resolutionFailureStack",error.stackTraceToString()) }
            }
        }
        fun json():JSONObject=proof
    }

    private inner class Observed(val owner: Activity, val ordinal: Int, val targets: Targets) {
        val decor: View = owner.window.decorView
        val fieldAtInstall = targets.field
        val rootAtInstall = targets.root
        val hostAtInstall = targets.host
        val scrollsAtInstall = targets.scrolls.toList()
        val tokenAtInstall = decor.windowToken
        val textShaAtInstall = targets.field?.text?.toString()?.toByteArray(Charsets.UTF_8)?.let { bytes ->
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
        val attachments = mutableListOf<Pair<View, View.OnAttachStateChangeListener>>()
        var observer: ViewTreeObserver? = null
        val focus = ViewTreeObserver.OnGlobalFocusChangeListener { _, _ -> observe(this,"global-focus") }
        val window = ViewTreeObserver.OnWindowFocusChangeListener { _ -> observe(this,"window-focus") }
        val layout = ViewTreeObserver.OnGlobalLayoutListener { observe(this,"global-layout") }
        val scroll = ViewTreeObserver.OnScrollChangedListener { observe(this,"scroll-changed") }
        val draw = ViewTreeObserver.OnPreDrawListener { observe(this,"pre-draw"); true }
        fun bindObserver() {
            val next=targets.root.viewTreeObserver
            if(observer===next) return
            unbindObserver()
            if(!next.isAlive) { issues.put("viewTreeObserver_not_alive_for_$ordinal"); return }
            next.addOnGlobalFocusChangeListener(focus)
            next.addOnWindowFocusChangeListener(window)
            next.addOnGlobalLayoutListener(layout)
            next.addOnScrollChangedListener(scroll)
            next.addOnPreDrawListener(draw)
            observer=next; listenerInstallCount+=5
        }
        fun unbindObserver() {
            val old=observer ?: return
            observer=null
            if(!old.isAlive) { issues.put("viewTreeObserver_dead_before_remove_$ordinal"); return }
            old.removeOnGlobalFocusChangeListener(focus)
            old.removeOnWindowFocusChangeListener(window)
            old.removeOnGlobalLayoutListener(layout)
            old.removeOnScrollChangedListener(scroll)
            old.removeOnPreDrawListener(draw)
            listenerRemoveCount+=5
        }
        fun attach() {
            val distinct=listOfNotNull(targets.root,targets.host,targets.field,decor).plus(targets.scrolls).distinct()
            for(view in distinct) {
                val listener=object:View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(attached:View) {
                        if(attached===targets.root) bindObserver()
                        observe(this@Observed,"view-attached")
                    }
                    override fun onViewDetachedFromWindow(detached:View) { observe(this@Observed,"view-detached") }
                }
                view.addOnAttachStateChangeListener(listener)
                attachments.add(view to listener);listenerInstallCount++
            }
            bindObserver()
        }
        fun detach() {
            unbindObserver()
            attachments.forEach { (view,listener) -> view.removeOnAttachStateChangeListener(listener);listenerRemoveCount++ }
            attachments.clear()
        }
    }

    private val events=JSONArray()
    private val issues=JSONArray()
    private val resolutionAttempts=JSONArray()
    private val observed=mutableListOf<Observed>()
    private var current:Observed?=null
    private var observations=0
    private var deduplicated=0
    private var dropped=0
    private var truncatedAt:Long?=null
    private var listenerInstallCount=0
    private var listenerRemoveCount=0
    private var closed=false
    private var lastSignature:String?=null
    private val maximumEvents=256
    private val callbackEventLimit=248 // Keep room for install, before/after, strict assertion, and close endpoints.

    private fun id(value:Any?):Any=value?.let(System::identityHashCode) ?: JSONObject.NULL
    private fun mainOnly() { check(Looper.myLooper()===Looper.getMainLooper()) { "IME probe must sample on main" } }
    private fun nullable(value:Any?):Any=value ?: JSONObject.NULL
    private fun viewState(view:View?):Any = view?.let {
        JSONObject().put("identity",id(it)).put("attached",it.isAttachedToWindow)
            .put("widthPx",it.width).put("heightPx",it.height).put("layoutRequested",it.isLayoutRequested)
            .put("windowTokenIdentity",id(it.windowToken))
    } ?: JSONObject.NULL
    private fun range(scroll:ScrollView):Any {
        val child=scroll.getChildAt(0) ?: return JSONObject.NULL
        val margins=child.layoutParams as? ViewGroup.MarginLayoutParams
        val content=child.height.toLong()+(margins?.topMargin ?: 0).toLong()+(margins?.bottomMargin ?: 0).toLong()
        val viewport=scroll.height.toLong()-scroll.paddingTop-scroll.paddingBottom
        if(viewport<0 || content<0) return JSONObject.NULL
        return (content-viewport).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong())
    }
    private fun actual(scope:Observed):JSONObject {
        val target=scope.targets
        val insets=target.root.rootWindowInsets
        val ime=if(Build.VERSION.SDK_INT>=30) insets?.getInsets(WindowInsets.Type.ime()) else null
        val field=scope.fieldAtInstall
        val layout=field?.layout
        val contentHeight=field?.let { it.height-it.compoundPaddingTop-it.compoundPaddingBottom }
        val rawRange=if(layout!=null && contentHeight!=null) (layout.height.toLong()-contentHeight)
            .coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()) else null
        val scrolls=JSONArray(scope.scrollsAtInstall.map { scroll ->
            JSONObject().put("tag",nullable(scroll.tag)).put("identity",id(scroll))
                .put("tokenIdentity",id(scroll.windowToken)).put("attached",scroll.isAttachedToWindow)
                .put("x",scroll.scrollX).put("y",scroll.scrollY)
                .put("viewportPx",scroll.height-scroll.paddingTop-scroll.paddingBottom)
                .put("rangePx",range(scroll)).put("widthPx",scroll.width).put("heightPx",scroll.height)
                .put("layoutRequested",scroll.isLayoutRequested)
        })
        val product=runCatching { productEvidence(scope.owner) }.getOrElse { error ->
            issues.put("productEvidence_read_${error.javaClass.name}");null }
        val sessions=product?.optJSONArray("imeSessionEvents")
        return JSONObject().put("activityOrdinal",scope.ordinal).put("resolution",target.resolution)
            .put("activityIdentity",id(scope.owner)).put("windowIdentity",id(scope.owner.window))
            .put("decorIdentity",id(scope.decor)).put("appWindowTokenIdentity",id(scope.decor.windowToken))
            .put("root",viewState(scope.rootAtInstall)).put("host",viewState(scope.hostAtInstall))
            .put("field",viewState(field)).put("fieldIdentity",id(field))
            .put("fieldTokenIdentity",id(field?.windowToken))
            .put("windowFocus",scope.owner.hasWindowFocus()).put("fieldFocus",nullable(field?.hasFocus()))
            .put("focusedViewIdentity",id(scope.owner.currentFocus))
            .put("imeVisible",if(Build.VERSION.SDK_INT>=30) nullable(insets?.isVisible(WindowInsets.Type.ime())) else JSONObject.NULL)
            .put("imeInsetsPx",ime?.let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) } ?: JSONObject.NULL)
            .put("rawScrollX",nullable(field?.scrollX)).put("rawScrollY",nullable(field?.scrollY))
            .put("rawLayoutHeightPx",nullable(layout?.height)).put("rawLineCount",nullable(layout?.lineCount))
            .put("rawScrollRangeYPx",nullable(rawRange))
            .put("selectionStart",nullable(field?.selectionStart)).put("selectionEnd",nullable(field?.selectionEnd))
            .put("textLength",nullable(field?.length())).put("scrolls",scrolls)
            .put("productRestoreStatus",nullable(product?.optString("status")?.takeIf { it.isNotEmpty() }))
            .put("productSessionEventCount",nullable(sessions?.length()))
            .put("productLastSessionPhase",nullable(sessions?.takeIf { it.length()>0 }?.optJSONObject(sessions.length()-1)?.optString("phase")))
    }
    private fun record(scope:Observed, source:String, frameIndex:Int?=null, force:Boolean=false) {
        if(closed)return
        mainOnly()
        observations++
        val now=SystemClock.elapsedRealtimeNanos()
        val value=try {actual(scope)} catch(error:Throwable) {
            issues.put("sample_${source}_${error.javaClass.name}")
            JSONObject().put("activityOrdinal",scope.ordinal).put("sampleFailure",error.stackTraceToString())
        }
        val signature=value.toString()
        if(!force && signature==lastSignature) {deduplicated++;return}
        lastSignature=signature
        val ceiling=if(force) maximumEvents else callbackEventLimit
        if(events.length()>=ceiling) {dropped++;if(truncatedAt==null)truncatedAt=now;return}
        events.put(JSONObject().put("seq",observations).put("elapsedRealtimeNanos",now)
            .put("pid",Process.myPid()).put("tid",Process.myTid())
            .put("eventSource",source).put("frameIndex",nullable(frameIndex))
            .put("forcedEndpoint",force).put("actual",value))
    }
    private fun observe(scope:Observed,source:String,frameIndex:Int?=null,force:Boolean=false) {
        try {record(scope,source,frameIndex,force)}
        catch(error:Throwable) {issues.put("observe_${source}_${error.javaClass.name}")}
    }
    fun install(owner:Activity, phase:String) {
        mainOnly()
        if(closed) {issues.put("install_after_close");return}
        if(observed.any { it.owner===owner }) {issues.put("duplicate_activity_install_$phase");return}
        val attempt=ResolutionAttempt(owner,phase)
        resolutionAttempts.put(attempt.json())
        val targets=try { val exact=resolve(owner,attempt);attempt.finish(null);exact } catch(error:Throwable) {
            attempt.finish(error)
            issues.put("resolve_${phase}_${error.javaClass.name}")
            Targets(owner.window.decorView,null,null,emptyList(),"unknown_named_reference_resolution")
        }
        val scope=try { Observed(owner,observed.size+1,targets) } catch(error:Throwable) {
            issues.put("scope_${phase}_${error.javaClass.name}");return
        }
        observed.add(scope);current=scope
        try {scope.attach()} catch(error:Throwable) {issues.put("attach_${phase}_${error.javaClass.name}")}
        observe(scope,"install-$phase",force=true)
    }
    fun sample(source:String,frameIndex:Int?=null,force:Boolean=true) {
        mainOnly()
        val scope=current
        if(scope==null) {issues.put("sample_without_owner_$source");return}
        observe(scope,source,frameIndex,force)
    }
    fun closeAndWrite(file:File) {
        mainOnly()
        if(closed)return
        current?.let { observe(it,"before-listener-removal",force=true) }
        observed.asReversed().forEach { scope -> try {scope.detach()} catch(error:Throwable) {
            issues.put("remove_${scope.ordinal}_${error.javaClass.name}") } }
        closed=true
        val old=observed.getOrNull(0);val replacement=observed.getOrNull(1)
        val same=if(old!=null && replacement!=null) JSONObject()
            .put("sameActivity",old.owner===replacement.owner)
            .put("sameWindow",old.owner.window===replacement.owner.window)
            .put("sameDecor",old.decor===replacement.decor)
            .put("sameField",if(old.fieldAtInstall==null || replacement.fieldAtInstall==null) JSONObject.NULL else old.fieldAtInstall===replacement.fieldAtInstall)
            .put("sameRoot",old.rootAtInstall===replacement.rootAtInstall)
            .put("sameAppWindowToken",if(old.tokenAtInstall==null || replacement.tokenAtInstall==null) JSONObject.NULL else old.tokenAtInstall===replacement.tokenAtInstall)
            .put("tokenComparisonStatus",if(old.tokenAtInstall==null || replacement.tokenAtInstall==null) "unknown_unattached" else "direct_reference_comparison")
            else JSONObject.NULL
        val status=if(issues.length()>0 || dropped>0 || listenerInstallCount!=listenerRemoveCount) "PARTIAL_OR_ERROR" else "COMPLETE_OBSERVED_INTERVAL_NOT_CAUSAL_CALLER_PROOF"
        val proof=JSONObject().put("schemaVersion",1).put("fixture",fixture)
            .put("clock","device_SystemClock.elapsedRealtimeNanos")
            .put("scope","one_recreate_interval; no_input_or_layout_mutation")
            .put("requestTagRelationship","unknown_not_inferred_from_time_proximity")
            .put("imeTokenRelationship","unknown_distinct_from_app_window_token")
            .put("maximumEvents",maximumEvents).put("callbackEventLimit",callbackEventLimit)
            .put("observations",observations).put("deduplicatedCount",deduplicated)
            .put("droppedCount",dropped).put("truncatedAtElapsedRealtimeNanos",nullable(truncatedAt))
            .put("listenerInstallCount",listenerInstallCount).put("listenerRemoveCount",listenerRemoveCount)
            .put("oldNewIdentity",same)
            .put("textSha256AtInstall",JSONArray(observed.map { nullable(it.textShaAtInstall) }))
            .put("issues",issues).put("resolutionAttempts",resolutionAttempts)
            .put("events",events).put("status",status)
        check(file.parentFile.isDirectory || file.parentFile.mkdirs())
        file.writeText(proof.toString(2))
    }
}

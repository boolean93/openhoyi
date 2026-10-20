package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import io.openhoyi.session.ShotRecoveryState
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.DeviceState
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ScrollView
import android.widget.Button
import android.widget.TextView
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import io.openhoyi.trace.TraceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** M6 two real Scale pages, one scoped Mock owner, actual readonly notifications. Never hardware evidence. */
internal class ScaleVisualMigrationChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val frames = NativeWindowFrameEvidence(test)
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready && SystemClock.elapsedRealtime() <= deadline) return
            check(SystemClock.elapsedRealtime() < deadline) { "M6 timeout: $description" }
            test.waitForIdleSync()
            // Bounded condition polling, never an acceptance delay or retry of a failed assertion.
            SystemClock.sleep(20)
        }
    }
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun write(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    private fun sha(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun businessDisk(): Map<String,String> {
        val data=File(context.applicationInfo.dataDir)
        val roots=listOf(File(data,"files"),File(data,"shared_prefs"),File(data,"databases"),context.noBackupFilesDir).distinctBy { it.canonicalPath }
        return roots.flatMap { directory -> directory.walkTopDown().filter { it.isFile &&
            !(directory.name=="shared_prefs" && it.name in setOf("appearance.xml","appearance.xml.bak","curves.xml","curves.xml.bak","scale_tool.xml","scale_tool.xml.bak")) }
            .map { it.canonicalPath to sha(it) }.toList() }.toMap()
    }
    private fun services(): List<String> = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(100).filter { it.service.packageName == context.packageName }.map { it.service.className }
    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    /** Keep raw hierarchy coordinates; derive a screen rect from measured origins, not clipped edges. */
    private fun visibility(view: View): JSONObject {
        val ownerRoot=view.rootView
        val origin=IntArray(2);view.getLocationOnScreen(origin)
        val rootScreen=IntArray(2);ownerRoot.getLocationOnScreen(rootScreen)
        val rootWindow=IntArray(2);ownerRoot.getLocationInWindow(rootWindow)
        val raw=Rect();val targetGlobalOffset=android.graphics.Point()
        val shown=view.getGlobalVisibleRect(raw,targetGlobalOffset)
        val rootRaw=Rect();val rootGlobalOffset=android.graphics.Point()
        val rootShown=ownerRoot.getGlobalVisibleRect(rootRaw,rootGlobalOffset)
        // getGlobalVisibleRect's Point includes the view's own scroll; undo it to obtain its origin.
        val dx=rootScreen[0]-rootGlobalOffset.x-ownerRoot.scrollX
        val dy=rootScreen[1]-rootGlobalOffset.y-ownerRoot.scrollY
        val derived=Rect(raw).apply {if(shown && rootShown)offset(dx,dy)}
        val full=Rect(origin[0],origin[1],origin[0]+view.width,origin[1]+view.height)
        val mappedOrigin=intArrayOf(targetGlobalOffset.x+view.scrollX+dx,targetGlobalOffset.y+view.scrollY+dy)
        var identity=true;var current:View?=view
        while(current!=null) {
            val target=current
            identity=identity && target.matrix.isIdentity && target.scaleX==1f && target.scaleY==1f &&
                target.rotation==0f && target.rotationX==0f && target.rotationY==0f &&
                target.translationX==0f && target.translationY==0f && target.translationZ==0f
            if(target===ownerRoot)break
            current=target.parent as? View
        }
        val sameRoot=current===ownerRoot && view.isAttachedToWindow && ownerRoot.isAttachedToWindow &&
            view.windowToken!=null && view.windowToken===ownerRoot.windowToken && view.display!=null &&
            view.display?.displayId==ownerRoot.display?.displayId
        val mapping=shown && rootShown && sameRoot && identity && mappedOrigin.contentEquals(origin)
        return JSONObject().put("rectPx",rect(full)).put("visibleRectPx",rect(derived)).put("rectCoordinateSpace","screen")
            .put("rawGlobalVisibleRectRootPx",rect(raw)).put("rawRootGlobalVisibleRect",rect(rootRaw))
            .put("rawGlobalVisibleRectDefined",shown).put("rootGlobalVisibleRectDefined",rootShown)
            .put("targetGlobalOffsetPointPx",JSONArray(listOf(targetGlobalOffset.x,targetGlobalOffset.y)))
            .put("rootGlobalOffsetPointPx",JSONArray(listOf(rootGlobalOffset.x,rootGlobalOffset.y)))
            .put("rootOriginOnScreenPx",JSONArray(rootScreen.toList())).put("rootOriginInWindowPx",JSONArray(rootWindow.toList()))
            .put("rootScrollPx",JSONArray(listOf(ownerRoot.scrollX,ownerRoot.scrollY)))
            .put("targetScrollPx",JSONArray(listOf(view.scrollX,view.scrollY)))
            .put("derivedScreenOffsetPx",JSONArray(listOf(dx,dy))).put("mappedScreenOriginPx",JSONArray(mappedOrigin.toList()))
            .put("rootIdentity",System.identityHashCode(ownerRoot)).put("attachedToSameRootWindow",sameRoot)
            .put("rootAttached",ownerRoot.isAttachedToWindow).put("targetAttached",view.isAttachedToWindow)
            .put("rootDisplayId",ownerRoot.display?.displayId ?: JSONObject.NULL).put("targetDisplayId",view.display?.displayId ?: JSONObject.NULL)
            .put("identityTransformChain",identity).put("screenMappingVerified",mapping)
            .put("visibleRectConversion","raw_global_rect_plus_measured_root_screen_minus_root_global_Point_origin; own_scroll_explicitly_removed")
            .put("visibilitySemantics","exact_hierarchy_clip_only; not_system_window_or_IME_occlusion_proof")
            .put("globalVisible",shown).put("complete",mapping && view.width>0 && view.height>0 && derived==full)
    }
    private fun observation(view: View): JSONObject {
        val visible=visibility(view)
        val texts = nodes(view).filterIsInstance<TextView>().map { text ->
            val textVisibility=visibility(text)
            val layout = text.layout
            JSONObject().put("semanticId", text.tag).put("text", text.text).put("fullViewVisible", textVisibility.getBoolean("complete")).put("visibility",textVisibility)
                .put("layoutPresent", layout != null).put("lineCount", layout?.lineCount ?: 0)
                .put("ellipsisCount", layout?.let { (0 until it.lineCount).sumOf(it::getEllipsisCount) } ?: JSONObject.NULL)
                .put("layoutHeightPx", layout?.height ?: JSONObject.NULL)
                .put("contentHeightPx", text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                .put("contentWidthPx", text.width - text.compoundPaddingLeft - text.compoundPaddingRight)
                .put("horizontalLayoutLines",layout?.let { actual -> JSONArray((0 until actual.lineCount).map { index ->
                    JSONObject().put("line",index).put("leftPx",actual.getLineLeft(index)).put("rightPx",actual.getLineRight(index))
                }) } ?: JSONObject.NULL)
        }
        return visible.put("semanticId",view.tag).put("textLineBoxes",JSONArray(texts))
    }
    private fun whole(value: JSONObject): Boolean = value.getBoolean("complete") && value.getJSONArray("textLineBoxes").let { lines ->
        (0 until lines.length()).all { index -> val line = lines.getJSONObject(index)
            line.getBoolean("fullViewVisible") && line.getBoolean("layoutPresent") && line.getInt("lineCount") > 0 &&
                line.getInt("ellipsisCount") == 0 && line.getInt("layoutHeightPx") <= line.getInt("contentHeightPx") &&
                line.getJSONArray("horizontalLayoutLines").let { horizontal ->
                    (0 until horizontal.length()).all { row -> val bounds=horizontal.getJSONObject(row)
                        bounds.getDouble("leftPx")>=0 && bounds.getDouble("rightPx")<=line.getInt("contentWidthPx") &&
                            bounds.getDouble("rightPx")-bounds.getDouble("leftPx")<=line.getInt("contentWidthPx") }
                } }
    }
    private inner class Scope(private val directory: File, private val page: String) {
        private val delegateNames = listOf("feedbackPreferences", "logs", "curves", "samples", "history", "customCurvesResult",
            "curveUsageResult", "beanInventoryResult", "beanPreparationResult", "journalStoreResult")
        private val legacyNames = listOf("legacyHistory", "legacyCurves")
        private val saved = linkedMapOf<String, Lazy<*>>()
        private val originalInitialized = linkedMapOf<String, Boolean>()
        private val legacy = linkedMapOf<String, Pair<Lazy<*>, Boolean>>()
        private var oldLanguage: Any? = null
        private var oldFailure: Any? = null
        private val savedPrefs = linkedMapOf<String, Map<String, *>>()
        private var diskBefore = emptyMap<String, String>()
        private var whitePrefFileBefore = emptyMap<String, String>()
        private fun whitePrefFiles(): Map<String,String> = listOf("appearance.xml","appearance.xml.bak","curves.xml","curves.xml.bak","scale_tool.xml","scale_tool.xml.bak").mapNotNull { name ->
            File(File(context.applicationInfo.dataDir,"shared_prefs"),name).takeIf { it.isFile }?.let { name to sha(it) }
        }.toMap()
        private var installed = false
        private val clockOwner=Any()
        private val initialMonotonicMs=100000L
        private var clockInstalled=false
        private var service: MobileService?=null
        private var serviceBound=false
        private var serviceStarted=false
        private var serviceCallbackFailure: Throwable?=null
        private val recoveryNames=listOf("shotRecovery","machineWriteRecovery","coffeeCredentials","coffeeCredentialRetries")
        private val serviceOriginalLazy=linkedMapOf<String,Lazy<*>>()
        private val serviceOriginalInitialized=linkedMapOf<String,Boolean>()
        private val scopedServiceLazy=linkedMapOf<String,Lazy<*>>()
        private var originalVisibilityObservers=emptyMap<Any?,Any?>()
        private var serviceOwnerId: Any?=null
        private val logicalInputTimes=mutableListOf<Long>()
        private val shotRecovery=ShotRecoveryState(object: ShotRecoveryState.Storage {
            private var value=ShotRecoveryState.Record(false,null)
            override fun read()=value
            override fun write(record: ShotRecoveryState.Record): Boolean { value=record; return true }
        })
        private val machineWriteRecovery=MachineWriteRecoveryState(object: MachineWriteRecoveryState.Storage {
            private var value=MachineWriteRecoveryState.Record(null,null)
            override fun read()=value
            override fun write(record: MachineWriteRecoveryState.Record): Boolean { value=record; return true }
        })
        private val serviceConnection=object: ServiceConnection {
            override fun onServiceConnected(name: ComponentName,binder: IBinder) {
                try {
                    check(service==null) { "M6 unexpected second service callback/owner" }
                    val owner=(binder as MobileService.LocalBinder).service
                    // Retain identity before any assertion so strict cleanup can still destroy a partial scope.
                    service=owner
                    check(field(owner,"mock")!=null && field(owner,"hub")==null && !owner.running)
                    check(field(owner,"logs")===logs && field(owner,"history")===history)
                    check(MobileMonotonicClock.ownsMockClock(clockOwner) && MobileMonotonicClock.elapsedRealtime()==initialMonotonicMs)
                    recoveryNames.forEach { key ->
                        val lazy=field(owner,key+"\$delegate") as Lazy<*>
                        serviceOriginalLazy[key]=lazy; serviceOriginalInitialized[key]=lazy.isInitialized()
                    }
                    check(serviceOriginalInitialized.values.none { it }) { "M6 original service recovery/credential Lazy already initialized; original state not read or cleared" }
                    serviceOwnerId=field(owner,"ownerId")
                    check(serviceOwnerId!=null && (field(app.visibility,"observers") as Map<*,*>).containsKey(serviceOwnerId))
                    check((field(feedback,"observers") as Map<*,*>).containsKey(serviceOwnerId))
                    // Only legal in-memory recovery stores replace the two original unopened delegates.
                    // Credentials and credential retries are not replaced, evaluated, or accessed.
                    mapOf("shotRecovery" to shotRecovery,"machineWriteRecovery" to machineWriteRecovery).forEach { (key,value) ->
                        val scoped=lazyOf(value); scopedServiceLazy[key]=scoped; write(owner,key+"\$delegate",scoped)
                    }
                } catch(error: Throwable) { serviceCallbackFailure=error }
            }
            override fun onServiceDisconnected(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M6 scoped service disconnected unexpectedly") }
            override fun onBindingDied(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M6 scoped service binding died") }
            override fun onNullBinding(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M6 scoped service returned null binding") }
        }
        private val ownedDirectory = File(context.cacheDir, "scale-visual-${UUID.randomUUID()}")
        private val tracked = linkedSetOf<Activity>()
        private val destroyed = linkedSetOf<Activity>()
        val inventory = BeanInventory(object : InventoryStorage { var value: ByteArray? = null
            override fun read() = value?.clone()
            override fun write(bytes: ByteArray) { value=bytes.clone() } })
        val preparation = BeanPreparation(object : BeanPreparation.Storage { var value: String? = null
            override fun read() = value
            override fun write(value: String) {
                this.value=value
            } })
        val custom = CustomCurveStore(object : CustomCurveStore.Storage { var value: String?=null
            override fun read()=value; override fun write(value: String) { this.value=value } })
        val usage = CurveUsageLedger(object : CurveUsageLedger.Storage { var value = ""
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val journal = BrewJournal(object : BrewJournal.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val feedback = BrewFeedbackPreference(object : BrewFeedbackPreference.Storage {
            override fun read() = false; override fun write(enabled: Boolean) = !enabled })
        private lateinit var logs: TraceStore
        private lateinit var samples: ShotSamplesRepository
        lateinit var curves: CurveLibrary
            private set
        lateinit var history: ShotHistory
            private set
        val curveId="factory-v3-002"
        private val sampleFailures=java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        lateinit var fixture: JSONObject
            private set
        private val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                tracked.add(activity)
            }
            override fun onActivityDestroyed(activity: Activity) { destroyed.add(activity) }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        fun prepare() {
            check(!::logs.isInitialized && !::samples.isInitialized)
            logs=TraceStore(File(ownedDirectory,"traces"))
            samples=ShotSamplesRepository(File(ownedDirectory,"samples")) { sampleFailures.add(it) }
            val factory=FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val proof=FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"),factory,context.assets.open("factory_slot_wire_v1.tsv"))
            curves=CurveLibrary(factory,proof,{ custom.items() },true)
            history=ShotHistory(object: ShotHistory.Storage { var value=""
                override fun read()=value; override fun write(value:String) {this.value=value}
            },now={1700100000000L},newId={error("M6 read-only scale scope may not create a shot")},onObservedUse={ entry ->
                usage.record(entry.id,entry.curveId,entry.startedAtMs)
            },onEntryChanged={ entry -> journal.observe(entry.journalObservation())
                if(preparation.current()?.shotId==entry.id)preparation.associatePending(journal,inventory)
            })
            fixture=JSONObject().put("page",page).put("synthetic",true).put("hardwareMeasurement",false)
                .put("noHome",true).put("noScan",true).put("noConnect",true).put("noTare",true).put("noShotControl",true)
                .put("source","scoped_memory_10Lazy; real_Mock_service; explicit_read_only_ScaleObservation_notifications")
                .put("clockInitialMs",initialMonotonicMs)
        }
        fun install(dark: Boolean) {
            main {
                check(MobileMonotonicClock.mockOwnerIdentity()==null && services().isEmpty() && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) {
                    "Fresh isolated process required: service or attached window already exists"
                }
                delegateNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>
                    saved[name]=lazy; originalInitialized[name]=lazy.isInitialized() }
                legacyNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>; legacy[name]=lazy to lazy.isInitialized() }
                // A fresh process must not have acquired any original store before this entire group is installed.
                check(saved.values.none { it.isInitialized() }) { "Original delegate initialized before fixture isolation" }
                oldLanguage = field(app,"languagePreferences"); oldFailure=field(app,"journalObservationFailure")
                listOf("appearance","curves","scale_tool").forEach { savedPrefs[it]=context.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap() }
                diskBefore=businessDisk(); whitePrefFileBefore=whitePrefFiles()
                originalVisibilityObservers=(field(app.visibility,"observers") as Map<*,*>).entries.associate { it.key to it.value }
                app.registerActivityLifecycleCallbacks(lifecycle)
                val values = mapOf("feedbackPreferences" to feedback,"logs" to logs,"curves" to curves,"samples" to samples,"history" to history,
                    "customCurvesResult" to Result.success(custom),"curveUsageResult" to Result.success(usage),"beanInventoryResult" to Result.success(inventory),
                    "beanPreparationResult" to Result.success(preparation),"journalStoreResult" to Result.success(journal))
                // Set installed before mutation, so a partial install still runs the same strict finally cleanup.
                installed=true
                values.forEach { (name,value) -> write(app,name+"\$delegate",lazyOf(value)) }
                write(app,"journalObservationFailure",null)
                write(app,"languagePreferences",AppLanguagePreference(object : AppLanguagePreference.Storage {
                    override fun read() = "zh-Hans"; override fun write(tag: String) = tag == "zh-Hans" }))
                check(app.feedbackPreferences === feedback && app.logs === logs && app.curves === curves && app.samples === samples && app.history === history)
                check(app.customCurvesResult.getOrThrow() === custom && app.curveUsageResult.getOrThrow() === usage && app.beanInventoryResult.getOrThrow() === inventory &&
                    app.beanPreparationResult.getOrThrow() === preparation && app.journalResult.getOrThrow() === journal)
                check(context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dark",dark).commit())
                check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).edit().putString("selected",curveId).commit())
                check(context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).edit().putBoolean("one_decimal",false).commit())
                run {
                    MobileMonotonicClock.installForMock(clockOwner,initialMonotonicMs)
                    clockInstalled=true
                    check(MobileMonotonicClock.ownsMockClock(clockOwner))
                }
            }
        }
        private fun verifyServiceLazyIsolation(owner: MobileService) {
            check(serviceOriginalLazy.size==4 && serviceOriginalInitialized.values.none { it })
            serviceOriginalLazy.forEach { (key,lazy) ->
                check(!lazy.isInitialized()) { "M6 original service Lazy was evaluated: $key" }
                check(field(owner,key+"\$delegate") === (scopedServiceLazy[key] ?: lazy))
            }
            check(field(owner,"logs")===logs && field(owner,"history")===history && field(owner,"hub")==null && field(owner,"mock")!=null)
        }
        var bridgeInstalled=false;private set
        val inputOwner:Any get()=clockOwner
        val ownerService:MobileService get()=requireNotNull(service)
        fun advance(now:Long) { main { MobileMonotonicClock.advanceForMock(clockOwner,now);logicalInputTimes.add(now) } }
        fun beginService() {
            check(installed && clockInstalled)
            main {serviceBound=context.bindService(Intent(context,MobileService::class.java),serviceConnection,Context.BIND_AUTO_CREATE);check(serviceBound)}
            await("unique actual service callback and isolated unopened recovery stores") {serviceCallbackFailure?.let {throw it};service!=null && scopedServiceLazy.size==2}
            main {verifyServiceLazyIsolation(ownerService);check(context.startService(Intent(context,MobileService::class.java))!=null);serviceStarted=true}
            await("actual default Mock tick before readonly input") {ownerService.running && ownerService.snapshot.coffeeAt==initialMonotonicMs && ownerService.snapshot.weightAt==initialMonotonicMs}
            main {
                check(ownerService.snapshot.scaleObservation==null && ownerService.snapshot.scaleCapabilities==null)
                check(ownerService.snapshot.weight!=null && field(ownerService,"hub")==null && serial(ownerService)==0L)
                ownerService.installScaleVisualInput(clockOwner);bridgeInstalled=true
                check(ownerService.snapshot.weight==null && ownerService.snapshot.weightAt==null)
                fixture.put("defaultUninstalledRuntimeObserved",true).put("singleServiceOwnerIdentity",System.identityHashCode(ownerService))
            }
        }
        fun offer(state:DeviceState,sample:io.openhoyi.protocol.ScaleObservation?) { main {ownerService.offerScaleVisualInput(clockOwner,state,sample)} }
        fun closeActivities() {
            main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
            await("all current scoped pages/dialogs closed before next saved-record case") {
                tracked.all { it.isDestroyed && it in destroyed } && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()
            }

        }
        fun stopForNoServiceState() {
            closeActivities()
            main {
                if(serviceBound) {context.unbindService(serviceConnection);serviceBound=false}
                check(context.stopService(Intent(context,MobileService::class.java)));serviceStarted=false
            }
            await("actual scoped Service destroyed before no-service state") {
                services().isEmpty() && !(field(ownerService,"handler") as Handler).hasCallbacks(field(ownerService,"mockTick") as Runnable) &&
                    !(field(feedback,"observers") as Map<*,*>).containsKey(serviceOwnerId)
            }
        }
        fun activeActivity(): Activity? = tracked.lastOrNull { !it.isDestroyed }
        fun cleanup() {
            val proof = JSONObject().put("scopedOwnedDirectory",ownedDirectory.absolutePath)
                .put("noBackupFilesDir",context.noBackupFilesDir.canonicalPath)
                .put("businessDiskRoots",JSONArray(listOf("files","shared_prefs","databases",context.noBackupFilesDir.canonicalPath)))
            try {
                if (installed) {
                    main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
                    await("all fixture Activities and dialogs destroyed") {
                        tracked.all { it.isDestroyed && it in destroyed } && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()
                    }
                    run {
                        main {
                            tracked.filterIsInstance<ScaleOwnerActivity>().forEach {activity ->
                                fun parentField(name:String)=ScaleOwnerActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.get(activity)
                                check(parentField("owner")==null && parentField("bound")==false && parentField("visible")==false &&
                                    !(parentField("handler") as Handler).hasCallbacks(parentField("refresh") as Runnable)) {"M6 ScaleOwner binding/refresh survived destruction"}
                            }
                            if(serviceBound) { context.unbindService(serviceConnection); serviceBound=false }
                            val stopped=context.stopService(Intent(context,MobileService::class.java))
                            if(serviceStarted) check(stopped) { "M6 started scoped service stop rejected" }
                        }
                        await("scoped service actually destroyed and owner callbacks removed") {
                            val owner=service
                            services().isEmpty() && (owner==null || run {
                                val handler=field(owner,"handler") as Handler
                                val callbacks=listOf("mockTick","watchShot","leaveForeground","stopAutomaticScale")
                                callbacks.none { handler.hasCallbacks(field(owner,it) as Runnable) } && field(owner,"hub")==null &&
                                    !(field(feedback,"observers") as Map<*,*>).containsKey(serviceOwnerId) &&
                                    !(field(app.visibility,"observers") as Map<*,*>).containsKey(serviceOwnerId)
                            })
                        }
                        main {
                            check(!serviceBound && services().isEmpty())
                            check((field(app.visibility,"observers") as Map<*,*>)==originalVisibilityObservers) { "M6 original visibility observers changed" }
                            service?.let { owner ->
                                verifyServiceLazyIsolation(owner)
                                proof.put("serviceOwnerId",serviceOwnerId).put("serviceOwnerCallbacksRemoved",true)
                                    .put("serviceHubClosed",field(owner,"hub")==null).put("serviceHandlersDrained",true)
                            }
                        }
                        proof.put("scopedServiceDestroyed",true).put("scaleOwnersUnboundAndRefreshRemoved",true)
                            .put("originalVisibilityObserversPreserved",true)
                    }
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) { "M6 fixture dialog/window remains after Activity destruction" }
                    // M6 creates no reader pool. All Activity/Service references are drained before restoring delegates.
                    main { check(services().isEmpty()) }
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("ownedAsyncReaderCount",0).put("noServiceBeforeOriginalDelegateRestore",true)
                        .put("mainQueueBarrierAfterOwners",true)
                }
                proof.put("ownedSampleRepositoryCreated",::samples.isInitialized).put("ownedTraceCreated",::logs.isInitialized)
                if (::samples.isInitialized) {
                    val writer=field(samples,"writer") as ExecutorService
                    writer.shutdown(); check(writer.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped sample writer did not terminate" }
                }
                if (::logs.isInitialized) { logs.close(); check(logs.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped trace writer did not terminate" } }
                check(sampleFailures.isEmpty()) { "Scoped sample writer failed" }
                proof.put("scopedWritersTerminated",true)
                proof.put("clipboardSAFSlots","NOT_TOUCHED")
                if(installed) main {
                    // Only after all pages, service callbacks, readers and writers have drained.
                    service?.let { owner ->
                        verifyServiceLazyIsolation(owner)
                        if(bridgeInstalled) {owner.clearScaleVisualInput(clockOwner);bridgeInstalled=false;check(owner.scaleTrendAt(MobileMonotonicClock.elapsedRealtime()).isEmpty())}
                        proof.put("inputClearedAfterServiceAndWritersBeforeClock",true)
                        serviceOriginalLazy.forEach { (key,lazy) -> write(owner,key+"\$delegate",lazy) }
                        serviceOriginalLazy.forEach { (key,lazy) ->
                            check(field(owner,key+"\$delegate")===lazy && lazy.isInitialized()==serviceOriginalInitialized.getValue(key))
                        }
                    }
                    if(clockInstalled) {
                        check(MobileMonotonicClock.ownsMockClock(clockOwner))
                        MobileMonotonicClock.uninstallForMock(clockOwner); clockInstalled=false
                        check(MobileMonotonicClock.mockOwnerIdentity()==null)
                    }
                    proof.put("serviceOriginalLazyIdentityAndInitializedRestored",true).put("mockClockRemovedAfterAllOwnersDrained",true)
                }
                if (installed) main {
                    saved.forEach { (name,lazy) -> write(app,name+"\$delegate",lazy) }
                    write(app,"languagePreferences",oldLanguage); write(app,"journalObservationFailure",oldFailure)
                    savedPrefs.forEach { (name,values) ->
                        val preferences = context.getSharedPreferences(name,Context.MODE_PRIVATE)
                        val keys = when(name) {"appearance"->listOf("dark");"scale_tool"->listOf("one_decimal");else->listOf("selected")}
                        val edit = preferences.edit()
                        keys.forEach { key ->
                            if (key !in values) edit.remove(key)
                            else when (val value = values[key]) {
                                is Boolean -> edit.putBoolean(key,value)
                                is String -> edit.putString(key,value)
                                is Int -> edit.putInt(key,value)
                                is Long -> edit.putLong(key,value)
                                is Float -> edit.putFloat(key,value)
                                is Set<*> -> {
                                    check(value.all { it is String }) { "Invalid string-set preference type" }
                                    @Suppress("UNCHECKED_CAST")
                                    edit.putStringSet(key,value as Set<String>)
                                }
                                else -> error("Unsupported original whitelist preference type: $name/$key")
                            }
                        }
                        check(edit.commit()) { "Cannot restore whitelist preference keys: $name/$keys" }
                    }
                    app.unregisterActivityLifecycleCallbacks(lifecycle)
                    saved.forEach { (name,lazy) -> check(field(app,name+"\$delegate") === lazy && lazy.isInitialized()==originalInitialized.getValue(name)) }
                    legacy.forEach { (name,pair) -> check(field(app,name+"\$delegate") === pair.first && pair.first.isInitialized()==pair.second) }
                    check(field(app,"languagePreferences") === oldLanguage && field(app,"journalObservationFailure") === oldFailure)
                    savedPrefs.forEach { (name,values) -> check(context.getSharedPreferences(name,Context.MODE_PRIVATE).all==values) }
                    val after = businessDisk()
                    proof.put("businessDiskBefore",JSONObject(diskBefore)).put("businessDiskAfter",JSONObject(after))
                    check(after==diskBefore) { "Original business disk changed; no rollback attempted" }
                    proof.put("originalLazyIdentityAndInitializedRestored",true).put("legacyUnchanged",true)
                        .put("typedPreferencesRestored",true).put("businessDiskByteUnchanged",true)
                        .put("whitePrefFilesBefore",JSONObject(whitePrefFileBefore)).put("whitePrefFilesAfter",JSONObject(whitePrefFiles()))
                        .put("whitePrefPhysicalBytesMayDiffer","only appearance.dark/curves.selected/scale_tool.one_decimal typed restoration; no file deletion or byte rollback")
                }
                proof.put("status","PASS_ISOLATION_ONLY")
            } catch (failure: Throwable) {
                proof.put("status","FAIL").put("failure",failure.stackTraceToString())
                // Do not restore original delegates while a scoped Activity/service/writer may still hold references.
                throw failure
            } finally { File(directory,"fixture-isolation.json").writeText(proof.toString(2)) }
        }
    }
    private fun geometry(activity: Activity): JSONObject {
        val content=root(activity);val insets=requireNotNull(content.rootWindowInsets)
        val origin=IntArray(2);content.getLocationOnScreen(origin)
        val config=activity.resources.configuration;val bars=insets.getInsets(WindowInsets.Type.systemBars())
        val charts=android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap(::nodes)
            .filterIsInstance<ScaleTrendView>().filter {it.isShown}
        return JSONObject().put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("appOriginPx",JSONArray(origin.toList())).put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("systemInsetsPx",JSONArray(listOf(bars.left,bars.top,bars.right,bars.bottom)))
            .put("imeVisible",insets.isVisible(WindowInsets.Type.ime())).put("densityDpi",config.densityDpi)
            .put("fontScale",config.fontScale).put("locale",config.locales.toLanguageTags())
            .put("uiModeNightMeaning",if(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)"dark" else "light")
            .put("nodes",JSONArray(nodes(content).filter {it.tag is String || it is TextView}.map(::observation)))
            .put("charts",JSONArray(charts.map {JSONObject().put("rect",observation(it)).put("canvasEvidence",JSONArray(it.visualEvidence.toString()))}))
            .put("attachedWindows",JSONArray(android.view.inspector.WindowInspector.getGlobalWindowViews().map {view ->
                JSONObject().put("class",view.javaClass.name).put("hasWindowFocus",view.hasWindowFocus()).put("nodes",JSONArray(nodes(view).filter {it.tag is String || it is TextView}.map(::observation)))
            }))
            .put("productLayoutEvidence",when(activity) {
                is ScaleActivity->JSONObject(activity.productLayoutEvidence.toString())
                is ScaleConnectionActivity->JSONObject(activity.productLayoutEvidence.toString())
                else->JSONObject.NULL
            })
    }
    private fun start(type:Class<out Activity>,extras:(Intent)->Unit={}):Activity {
        val intent=Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);extras(intent)
        return test.startActivitySync(intent).also {activity->await("${type.simpleName} actual window ready") {
            activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested
        }}
    }
    private fun recreate(activity:Activity):Activity {
        var expectedOwner:Any?=null
        main {if(activity is ScaleOwnerActivity)expectedOwner=ScaleOwnerActivity::class.java.getDeclaredField("owner").apply {isAccessible=true}.get(activity)}
        val monitor=test.addMonitor(activity.javaClass.name,null,false)
        try {
            main {activity.recreate()}
            return requireNotNull(test.waitForMonitorWithTimeout(monitor,10000)).also {replacement->await("replacement actual window ready") {
                replacement.hasWindowFocus() && root(replacement).isLaidOut && !root(replacement).isLayoutRequested &&
                    (replacement !is ScaleOwnerActivity || ScaleOwnerActivity::class.java.getDeclaredField("owner").apply {isAccessible=true}.get(replacement)===expectedOwner)
            }}
        } finally {test.removeMonitor(monitor)}
    }
    private fun render(activity:Activity) {main {activity.javaClass.getDeclaredMethod("renderScale").apply {isAccessible=true}.invoke(activity)}}
    private fun tagged(activity:Activity,tag:String):View=requireNotNull(root(activity).findViewWithTag<View>(tag)) {"Missing actual semantic target: $tag"}
    private fun reveal(activity:Activity,view:View) {
        main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
        frames.committedFrame(root(activity))
        main {check(whole(observation(view))) {"M6 target not completely reachable: ${view.tag}"}}
    }
    private fun click(activity:Activity,tag:String) {
        val view=tagged(activity,tag);reveal(activity,view)
        main {check(view.isShown && view.isEnabled && view.isClickable && view.height>=HoyiUi.dp(activity,48));check(view.performClick())}
    }
    private fun capture(activity:Activity,folder:File, measuredShort:Boolean=false):NativeWindowFrameEvidence.CapturedFrame {
        check(folder.isDirectory || folder.mkdirs())
        val captured=if (measuredShort) frames.screenshotMeasuredShortActivity(activity,root(activity),File(folder,"window.png")) {geometry(activity)}
            else frames.screenshot(activity,root(activity),File(folder,"window.png")) {geometry(activity)}
        File(folder,"geometry.json").writeText(captured.target.toString(2))
        main {
            TaskTypographyEvidence.capture(activity,folder)
            val pairs=JSONArray()
            nodes(root(activity)).filterIsInstance<TextView>().filter {(it.tag as? String)?.endsWith("/number")==true && it.isShown}.forEach {number->
                val prefix=(number.tag as String).removeSuffix("/number")
                val unit=root(activity).findViewWithTag<TextView>("$prefix/unit")
                if(unit!=null && unit.isShown) {
                    val a=IntArray(2);val b=IntArray(2);number.getLocationOnScreen(a);unit.getLocationOnScreen(b)
                    pairs.put(JSONObject().put("semanticId",prefix).put("numberBaselinePx",a[1]+number.baseline).put("unitBaselinePx",b[1]+unit.baseline))
                    check(a[1]+number.baseline==b[1]+unit.baseline) {"M6 actual number/unit baseline mismatch: $prefix"}
                }
            }
            File(folder,"baseline-alignment.json").writeText(pairs.toString(2))
        }
        check(captured.proof.getInt("protocolVersion")==2 && captured.proof.getBoolean("accepted"))
        return captured
    }
    /** Observe only the explicitly captured window, never another global chart or the owner background. */
    private fun targetWindowGeometry(window: android.view.Window, target: View): JSONObject {
        val origin=IntArray(2);target.getLocationOnScreen(origin)
        val windowOrigin=IntArray(2);target.getLocationInWindow(windowOrigin)
        val config=target.resources.configuration
        return JSONObject().put("windowIdentity",System.identityHashCode(window))
            .put("decorIdentity",System.identityHashCode(window.decorView)).put("targetIdentity",System.identityHashCode(target))
            .put("targetScreenOriginPx",JSONArray(origin.toList())).put("targetWindowOriginPx",JSONArray(windowOrigin.toList()))
            .put("targetSizePx",JSONArray(listOf(target.width,target.height))).put("hasWindowFocus",target.hasWindowFocus())
            .put("densityDpi",config.densityDpi).put("fontScale",config.fontScale).put("locale",config.locales.toLanguageTags())
            .put("observationScope","only_explicit_target_window_decor_subtree")
            .put("nodes",JSONArray(nodes(target).filter {it.tag is String || it is TextView}.map(::observation)))
            .put("charts",JSONArray(nodes(target).filterIsInstance<ScaleTrendView>().filter {it.isShown}.map {
                JSONObject().put("rect",observation(it)).put("canvasEvidence",JSONArray(it.visualEvidence.toString())) }))
            .put("textLayout",JSONArray(nodes(target).filterIsInstance<TextView>().map { text ->
                JSONObject().put("actualView",observation(text)).put("textSizePx",text.textSize).put("baselinePx",text.baseline)
                    .put("includeFontPadding",text.includeFontPadding).put("letterSpacing",text.letterSpacing)
                    .put("fakeBold",text.paint.isFakeBoldText).put("colorArgb",text.currentTextColor)
                    .put("typefaceWeight",if(Build.VERSION.SDK_INT>=28)text.typeface?.weight ?: JSONObject.NULL else JSONObject.NULL)
            }))
    }
    /** Real rejected targets in the same modal scope; these are not main captures or positive evidence. */
    private fun targetWindowNegatives(activity:Activity, targetWindow:android.view.Window, folder:File) {
        check(folder.isDirectory || folder.mkdirs())
        val targetDecor=targetWindow.decorView;val identity=System.identityHashCode(targetDecor)
        main {check(targetDecor.hasWindowFocus() && !activity.hasWindowFocus())}
        val cases=JSONArray()
        fun rejected(name:String, window:android.view.Window, target:View, reason:String) {
            val path=File(folder,name).apply {check(isDirectory || mkdirs())}
            val result=runCatching {frames.screenshot(window,target,File(path,"window.png")) {
                error("Negative Window ownership/focus must be rejected before observing target contents")
            }}
            check(result.isFailure) {"M6 invalid Window target unexpectedly accepted: $name"}
            val proof=JSONObject(File(path,"window-frame-sync.json").readText())
            check(proof.getInt("protocolVersion")==2 && !proof.getBoolean("accepted") && proof.getInt("timeoutMs")==5000 &&
                proof.getString("failure").contains(reason)) {"M6 negative did not fail for its real Window constraint: $name"}
            main {check(System.identityHashCode(targetWindow.decorView)==identity && targetDecor.hasWindowFocus() && !activity.hasWindowFocus())}
            cases.put(JSONObject().put("case",name).put("rejectedFor",reason).put("proof",proof)
                .put("realWindowTarget",true).put("targetModalFocusPreserved",true))
        }
        rejected("wrong-window-root",targetWindow,root(activity),"Wrong Window/root/token/display owner")
        rejected("unfocused-owner",activity.window,root(activity),"not a ready focused attached surface")
        File(folder,"manifest.json").writeText(JSONObject().put("cases",cases).put("mainConfigurationCountContribution",0)
            .put("positiveWindowCaptureStillRequired",true).put("bitmapOrSurfaceInsetsInjection",false)
            .put("unknownLayerSizeAndNonzeroSurfaceInsetsNegative","PENDING_NO_LEGAL_REAL_FIXTURE; never synthetic native evidence").toString(2))
    }
    private fun captureTargetWindow(window: android.view.Window, folder:File):NativeWindowFrameEvidence.CapturedFrame {
        check(folder.isDirectory || folder.mkdirs())
        val target=window.decorView
        val captured=frames.screenshotMeasuredWindowBufferTranslation(window,target,File(folder,"window.png")) {targetWindowGeometry(window,target)}
        File(folder,"geometry.json").writeText(captured.target.toString(2))
        check(captured.proof.getInt("protocolVersion")==2 && captured.proof.getBoolean("accepted") &&
            captured.proof.getString("mappingMode")=="MEASURED_WINDOW_BUFFER_TRANSLATION")
        return captured
    }
    /** References only completed independent capture files; the summary never owns a geometry/proof tree. */
    private fun captureRefs(pageRoot:File, captureFolder:File):JSONObject {
        fun reference(name:String):JSONObject {
            val file=File(captureFolder,name)
            check(file.isFile && file.length()>0) {"M6 missing completed capture file: $file"}
            val path=file.relativeTo(pageRoot).invariantSeparatorsPath
            check(path.split('/').none {it.isEmpty() || it==".." || it=="."} && !path.startsWith('/'))
            return JSONObject().put("path",path).put("sha256",sha(file))
        }
        return JSONObject().put("geometryRef",reference("geometry.json"))
            .put("frameSyncRef",reference("window-frame-sync.json"))
    }
    private fun trendReturnState(activity:Activity):JSONObject {
        val panel=requireNotNull(field(activity,"trend"))
        return JSONObject().put("buffer",field(panel,"values").toString()).put("bufferNow",field(panel,"now"))
            .put("timer",(field(activity,"timer") as LocalScaleTimer).snapshot().toString())
            .put("scrolls",JSONArray(nodes(root(activity)).filterIsInstance<ScrollView>().map {
                JSONObject().put("identity",System.identityHashCode(it)).put("x",it.scrollX).put("y",it.scrollY)
            }))
    }
    private fun diagnostic(activity:Activity,folder:File) {
        check(folder.isDirectory || folder.mkdirs())
        val image=requireNotNull(test.uiAutomation.takeScreenshot())
        try {File(folder,"window.png").outputStream().use {check(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}}
        finally {image.recycle()}
        main {File(folder,"geometry.json").writeText(geometry(activity).put("sameCompositorFrameProven",false).toString(2))}
    }
    private fun sample(at:Long,value:Int=1800,battery:Boolean=false,percent:Int?=null,
        evidence:io.openhoyi.protocol.ScaleEvidence=io.openhoyi.protocol.ScaleEvidence.LIVE_READ_ONLY,
        weight:Boolean=true)=io.openhoyi.protocol.ScaleObservation(value,at,io.openhoyi.protocol.ScaleCapabilities(weight=weight,battery=battery),
            evidence,batteryPercent=percent,raw=io.openhoyi.protocol.ByteFrame(byteArrayOf(0x6d,0x36)))
    private fun serial(service:MobileService)=(field(service,"mockScaleVisualObservationSerial") as Long)
    private fun serviceGuards(scope:Scope,folder:File) {
        val service=scope.ownerService;val cases=JSONArray()
        fun rejected(name:String,requiredMessage:String?=null,action:()->Unit) {
            var before=0L;main {before=serial(service)}
            val result=runCatching(action)
            check(result.isFailure) {"M6 real Service guard unexpectedly accepted: $name"}
            requiredMessage?.let {check(result.exceptionOrNull()!!.message?.contains(it)==true) {"M6 $name failed for another reason: ${result.exceptionOrNull()}"}}
            main {check(serial(service)==before)}
            cases.put(JSONObject().put("case",name).put("exceptionClass",result.exceptionOrNull()!!.javaClass.name)
                .put("notificationDelta",0).put("actualServiceOwner",System.identityHashCode(service)))
        }
        rejected("duplicate-install") {main {service.installScaleVisualInput(scope.inputOwner)}}
        rejected("different-clock-owner-install") {main {service.installScaleVisualInput(Any())}}
        rejected("different-clock-owner-offer") {main {service.offerScaleVisualInput(Any(),DeviceState.READY,sample(100000))}}
        rejected("different-clock-owner-clear") {main {service.clearScaleVisualInput(Any())}}
        for(operation in listOf("install","offer","clear")) rejected("background-$operation",requiredMessage="main thread") {
            var error:Throwable?=null
            val worker=Thread({try {when(operation) {
                "install"->service.installScaleVisualInput(scope.inputOwner)
                "offer"->service.offerScaleVisualInput(scope.inputOwner,DeviceState.READY,sample(100000))
                else->service.clearScaleVisualInput(scope.inputOwner)
            }}catch(failure:Throwable) {error=failure}},"m6-guard-background")
            worker.start();worker.join(10000);check(!worker.isAlive) {"M6 guard thread did not terminate"}
            throw requireNotNull(error) {"Background guard did not reject"}
        }
        val invalid=mapOf("future-time" to sample(100001),"negative-time" to sample(-1),"unsupported-weight" to sample(100000,600001),
            "tare-capability" to sample(100000).copy(capabilities=io.openhoyi.protocol.ScaleCapabilities(tare=true)),
            "control-capability" to sample(100000).copy(capabilities=io.openhoyi.protocol.ScaleCapabilities(validatedWeightControl=true)),
            "verified-evidence" to sample(100000,evidence=io.openhoyi.protocol.ScaleEvidence.VERIFIED_TRANSPORT),
            "battery-without-capability" to sample(100000,percent=82),"illegal-battery" to sample(100000,battery=true,percent=101))
        invalid.forEach {(name,value)->rejected(name) {scope.offer(DeviceState.READY,value)}}
        rejected("disconnected-observation") {scope.offer(DeviceState.DISCONNECTED,sample(100000))}
        var before=0L;main {check(field(service,"hub")==null && field(service,"mock")!=null);before=serial(service)}
        scope.offer(DeviceState.READY,sample(100000))
        var pointCount=0;main {check(serial(service)==before+1);pointCount=service.scaleTrendAt(100000).size}
        scope.offer(DeviceState.READY,sample(100000))
        main {check(serial(service)==before+2 && service.scaleTrendAt(100000).size==pointCount)}
        File(folder,"service-guards.json").writeText(JSONObject().put("cases",cases).put("positiveMockMainClockOwnerHubNull",true)
            .put("duplicateNotificationDelta",2).put("duplicateTrendPointDelta",0)
            .put("realBuildAndHubNonNullNegativeRuntimeTest","NOT_INVOKED_IN_THIS_MOCK_HUB_NULL_SCOPE; separate pure policy tests, not a Service runtime claim").toString(2))
    }
    private fun mainScale(scope:Scope,activity:Activity,folder:File) {
        click(activity,"product/scale/timer/toggle")
        var before=0L;main {before=serial(scope.ownerService)}
        scope.advance(114600)
        await("unmodified real Mock tick sampled the advanced clock") {scope.ownerService.snapshot.coffeeAt==114600L}
        main {check(serial(scope.ownerService)==before && scope.ownerService.snapshot.scaleObservation?.receivedAtMs==100000L)}
        val values=listOf(-125,200,800,1200,1600,1750,1800,1801,1799,1800,1800)
        values.forEachIndexed {index,value->val at=114600L+1000L*index;scope.advance(at);scope.offer(DeviceState.READY,sample(at,value))}
        render(activity);click(activity,"product/scale/timer/toggle")
        main {
            check((field(activity,"timer") as LocalScaleTimer).elapsedMs(124600)==24600L)
            check((field(activity,"weight") as TextView).text.toString()=="18.00")
            check(scope.ownerService.scaleTrendAt(124600).map {it.hundredthsGram}==values)
            check(serial(scope.ownerService)==before+11)
            check(!(field(activity,"tare") as Button).isEnabled && scope.ownerService.snapshot.scaleObservation?.eligibleForControl==false)
        }
        scope.fixture.put("explicitMainSamples",JSONArray(values)).put("mainSampleTimes",JSONArray(values.indices.map {114600L+it*1000}))
            .put("actualPausedTimerMs",24600).put("actualNotifyDelta",11).put("tickNotifyDelta",0)
            .put("sourceMeaning","synthetic readonly scale observations; not tared cup mass; no automatic stop")
        File(folder,"fixture.json").writeText(scope.fixture.toString(2))
    }
    private fun trendEvidence(activity:Activity,folder:File,requireInline:Boolean) {
        var inline=false
        main {inline=(field(activity,"productLayoutEvidence") as JSONObject).getBoolean("trendInline")}
        if(requireInline)check(inline) {"M6 standard main fixture must have a real inline trend"}
        var retainedState:JSONObject?=null
        if(!inline) {
            reveal(activity,tagged(activity,"scale-trend-fallback"))
            main {retainedState=trendReturnState(activity)}
            click(activity,"scale-trend-fallback")
            await("same-buffer actual detail window") {
                val dialog=field(field(activity,"trend")!!,"dialog") as? AlertDialog
                val decor=dialog?.window?.decorView
                dialog?.isShowing==true && decor?.isLaidOut==true && decor.hasWindowFocus() && !decor.isLayoutRequested
            }
        }
        val evidence=if(inline)capture(activity,folder).target.getJSONArray("charts") else {
            val dialog=field(field(activity,"trend")!!,"dialog") as AlertDialog
            targetWindowNegatives(activity,requireNotNull(dialog.window),File(folder,"window-negative-cases"))
            val target=captureTargetWindow(requireNotNull(dialog.window),folder)
            target.target.getJSONArray("charts")
        }
        check(evidence.length()>0)
        var plotted=false
        for(i in 0 until evidence.length()) {
            val chart=evidence.getJSONObject(i).getJSONArray("canvasEvidence")
            val operations=(0 until chart.length()).map {chart.getJSONObject(it)}
            val plot=operations.single {it.optString("operation")=="plot"}
            check(plot.getInt("pointCount")==11 && plot.getDouble("lowGrams")<=-1.25)
            check(operations.any {it.optString("operation")=="dataLine"} && operations.any {it.optString("operation")=="endpointCircle"})
            plotted=true
        }
        check(plotted)
        if(!inline) {
            main {(field(field(activity,"trend")!!,"dialog") as AlertDialog).dismiss()}
            await("actual Activity focus/layout restored after trend detail") {activity.hasWindowFocus() && !root(activity).isLayoutRequested}
            main {val after=trendReturnState(activity);check(after.toString()==requireNotNull(retainedState).toString())
                File(folder,"dialog-return-state.json").writeText(JSONObject().put("before",retainedState).put("after",after)
                    .put("exactBufferTimerAndScrollRestored",true).put("activityFocusRestored",activity.hasWindowFocus()).toString(2))}
        }
    }
    private fun supportReachability(activity:Activity,folder:File) {
        val scroll=tagged(activity,"product/scale/support-scroll") as ScrollView
        var saved=0;main {saved=scroll.scrollY}
        val targets=listOf("product/scale/timer/toggle","product/scale/timer/reset","product/scale/battery","product/scale/device-status","product/scale/connect","product/scale/settings")
        val results=JSONArray()
        val report=File(folder,"scroll-reachability.json")
        fun persist(completed:Boolean) {report.writeText(JSONObject().put("evidenceSummaryVersion",2)
            .put("referenceBase","page_root").put("completed",completed).put("targets",results)
            .put("restoredY",if(completed) saved else JSONObject.NULL).toString(2))}
        try {
            targets.forEachIndexed {index,tag->val view=tagged(activity,tag);reveal(activity,view)
                val captureFolder=File(folder,"after-scroll-${index+1}")
                capture(activity,captureFolder)
                val refs=captureRefs(folder,captureFolder)
                results.put(JSONObject().put("tag",tag).put("case","after-scroll-${index+1}")
                    .put("geometryRef",refs.getJSONObject("geometryRef"))
                    .put("frameSyncRef",refs.getJSONObject("frameSyncRef")))
                persist(false)
            }
        } finally {main {scroll.scrollTo(0,saved)};frames.committedFrame(root(activity));main {check(scroll.scrollY==saved)}}
        persist(true)
    }
    private fun scaleSpecials(scope:Scope,initial:Activity,folder:File) {
        var activity=initial;val proofs=JSONArray()
        val pageRoot=requireNotNull(folder.parentFile)
        val summary=File(folder,"manifest.json")
        fun persist(completed:Boolean) {summary.writeText(JSONObject().put("evidenceSummaryVersion",2)
            .put("referenceBase","page_root").put("cases",proofs).put("mainConfigurationCountContribution",0)
            .put("noControlOrInventoryWrite",true).put("pendingStrictEvidence",JSONArray())
            .put("hardwareTareStatePages","PENDING_NO_LEGAL_MOCK_UI_WRITING_WAITING_ZERO_CONFIRMED_FAILED_UNKNOWN; original StandaloneTare unit coverage unchanged")
            .put("visualAcceptance","ASTRA_PENDING").put("completed",completed).toString(2))}
        fun state(name:String,assertion:()->Unit) {render(activity);val captureFolder=File(folder,name);capture(activity,captureFolder);main(assertion)
            val refs=captureRefs(pageRoot,captureFolder)
            proofs.put(JSONObject().put("case",name).put("geometryRef",refs.getJSONObject("geometryRef"))
                .put("frameSyncRef",refs.getJSONObject("frameSyncRef")));persist(false)}
        main {check(context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).edit().putBoolean("one_decimal",true).commit())}
        state("one-decimal") {check((field(activity,"weight") as TextView).text.toString()=="18.0")}
        main {check(context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).edit().putBoolean("one_decimal",false).commit())}
        activity=recreate(activity);render(activity)
        main {check((field(activity,"timer") as LocalScaleTimer).snapshot()==LocalScaleTimer.State(24600,null))}
        state("paused-recreated") {check((field(activity,"weight") as TextView).text.toString()=="18.00")}
        click(activity,"product/scale/timer/toggle");scope.advance(125600);render(activity);click(activity,"product/scale/timer/toggle")
        main {check((field(activity,"timer") as LocalScaleTimer).elapsedMs(125600)==25600L)}
        click(activity,"product/scale/timer/reset");main {check((field(activity,"timer") as LocalScaleTimer).snapshot()==LocalScaleTimer.State())}
        scope.advance(126101)
        state("stale-1501") {check((field(activity,"weight") as TextView).text.toString()=="—")}
        scope.advance(134601)
        state("expired-10001") {check(scope.ownerService.scaleTrendAt(134601).isEmpty())}
        scope.offer(DeviceState.READY,null)
        state("ready-null") {check(scope.ownerService.snapshot.scaleState==DeviceState.READY && (field(activity,"weight") as TextView).text.toString()=="—")}
        scope.offer(DeviceState.DISCONNECTED,null)
        state("disconnected") {check((field(activity,"freshness") as TextView).text.toString()==activity.getString(R.string.scale_disconnected))}
        scope.advance(135000);scope.offer(DeviceState.READY,sample(135000,-125,evidence=io.openhoyi.protocol.ScaleEvidence.OFFLINE_CANDIDATE))
        state("negative-offline") {check((field(activity,"weight") as TextView).text.toString()=="-1.25")}
        scope.advance(135100);scope.offer(DeviceState.READY,sample(135100,100,evidence=io.openhoyi.protocol.ScaleEvidence.OFFLINE_CANDIDATE))
        scope.advance(137000);scope.offer(DeviceState.READY,sample(137000,1800))
        state("gap-and-evidence-change") {check(scope.ownerService.scaleTrendAt(137000).map {it.segment}.distinct().size>=2)}
        scope.offer(DeviceState.READY,sample(137000,1800,battery=true,percent=82))
        reveal(activity,field(activity,"battery") as View)
        state("battery-capability-82") {check((field(activity,"battery") as TextView).text.toString()==activity.getString(R.string.scale_battery_value,82))}
        scope.advance(137001);scope.offer(DeviceState.READY,sample(137001))
        reveal(activity,field(activity,"battery") as View)
        state("battery-unavailable") {check((field(activity,"battery") as TextView).text.toString()==activity.getString(R.string.scale_battery_unavailable))}
        scope.advance(137002);scope.offer(DeviceState.READY,sample(137002,weight=false))
        state("weight-capability-false") {check((field(activity,"weight") as TextView).text.toString()=="—")}
        scope.advance(137003);scope.offer(DeviceState.READY,sample(137003))
        scope.closeActivities()
        activity=start(ScaleActivity::class.java) {it.putExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE,true)}
        render(activity)
        val capture=tagged(activity,"product/scale/capture")
        reveal(activity,capture)
        state("mock-capture-disabled") {check(!capture.isEnabled && !activity.isFinishing);check(!(field(activity,"tare") as Button).isEnabled)}
        // Actual short-height app window; the same observation buffer, never a component-only main image.
        main {activity.window.setLayout(-1,HoyiUi.dp(activity,420))}
        await("actual short window laid out") {root(activity).height<=HoyiUi.dp(activity,420) && !root(activity).isLayoutRequested}
        render(activity)
        // Explicitly declared short Activity, never a fallback from the default gate.
        val shortFolder=File(folder,"short-height")
        capture(activity,shortFolder,measuredShort=true)
        main {check((activity as ScaleActivity).productLayoutEvidence.getBoolean("trendInline").not())}
        val shortRefs=captureRefs(pageRoot,shortFolder)
        proofs.put(JSONObject().put("case","short-height").put("acceptanceStatus","PASS_PROTOCOL2_ONLY_NOT_VISUAL")
            .put("geometryRef",shortRefs.getJSONObject("geometryRef"))
            .put("frameSyncRef",shortRefs.getJSONObject("frameSyncRef")));persist(false)
        reveal(activity,tagged(activity,"scale-trend-fallback"))
        lateinit var beforeDetail:JSONObject;main {beforeDetail=trendReturnState(activity)}
        click(activity,"scale-trend-fallback")
        await("short-height real same-buffer detail") {
            val owner=field(field(activity,"trend")!!,"dialog") as? AlertDialog
            val decor=owner?.window?.decorView
            owner?.isShowing==true && decor?.isLaidOut==true && decor.hasWindowFocus() && !decor.isLayoutRequested
        }
        val dialog=field(field(activity,"trend")!!,"dialog") as AlertDialog
        targetWindowNegatives(activity,requireNotNull(dialog.window),File(folder,"short-height-trend-detail/window-negative-cases"))
        val detailFolder=File(folder,"short-height-trend-detail")
        check(captureTargetWindow(requireNotNull(dialog.window),detailFolder).target.getJSONArray("charts").length()>0)
        val detailRefs=captureRefs(pageRoot,detailFolder)
        proofs.put(JSONObject().put("case","short-height-trend-detail").put("acceptanceStatus","PASS_PROTOCOL2_ONLY_NOT_VISUAL")
            .put("geometryRef",detailRefs.getJSONObject("geometryRef"))
            .put("frameSyncRef",detailRefs.getJSONObject("frameSyncRef")));persist(false)
        main {dialog.dismiss()}
        await("actual short Activity focus/layout restored after trend detail") {activity.hasWindowFocus() && !root(activity).isLayoutRequested}
        main {val after=trendReturnState(activity);check(after.toString()==beforeDetail.toString())
            File(folder,"short-height-trend-detail/dialog-return-state.json").writeText(JSONObject().put("before",beforeDetail).put("after",after)
                .put("exactBufferTimerAndScrollRestored",true).put("activityFocusRestored",activity.hasWindowFocus()).toString(2))}
        scope.stopForNoServiceState();activity=start(ScaleActivity::class.java);render(activity)
        state("no-service") {check(services().isEmpty() && (field(activity,"weight") as TextView).text.toString()=="—" && !(field(activity,"tare") as Button).isEnabled)}
        persist(true)
    }
    private fun candidates(count:Int,longName:Boolean=false)= (1..count).map {index->io.openhoyi.bluetooth.DiscoveredDevice(
        "02:00:00:00:60:${index.toString(16).padStart(2,'0')}",
        if(longName)"测试只读秤 · 保留完整设备名称与实验限制 · 第 $index 台" else when(index){1->"测试秤 A";2->"测试只读秤";3->"测试秤 B";else->"测试秤 $index"},
        when(index){1->-52;2->-65;else->-70},io.openhoyi.session.DeviceRole.BOOKOO,
        if(index==2)io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id else io.openhoyi.session.BookooScaleProtocolAdapter.id)}
    private fun setCandidates(scope:Scope,activity:Activity,values:List<io.openhoyi.bluetooth.DiscoveredDevice>) {
        main {write(scope.ownerService,"snapshot",scope.ownerService.snapshot.copy(candidates=values))};render(activity)
        frames.committedFrame(root(activity))
        main {
            check(scope.ownerService.snapshot.candidates==values && !scope.ownerService.snapshot.scanning)
            val actual=nodes(field(activity,"rows") as View).filter {it.tag?.toString()?.endsWith("/connect")==true}
            check(actual.size==values.size && actual.all {!it.isEnabled && it is Button})
            check(!(field(activity,"scan") as Button).isEnabled)
        }
    }
    private fun swipe(view:View) {
        val bounds=Rect();main {check(view.getGlobalVisibleRect(bounds) && bounds.width()>32 && bounds.height()>64)}
        val x=bounds.exactCenterX();val from=bounds.top+bounds.height()*.82f;val to=bounds.top+bounds.height()*.22f
        val down=SystemClock.uptimeMillis()
        fun event(action:Int,y:Float) {
            val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).apply {source=android.view.InputDevice.SOURCE_TOUCHSCREEN}
            try {check(test.uiAutomation.injectInputEvent(event,true))}finally {event.recycle()}
        }
        event(android.view.MotionEvent.ACTION_DOWN,from)
        for(step in 1..16) {SystemClock.sleep(25);event(android.view.MotionEvent.ACTION_MOVE,from+(to-from)*step/16)}
        repeat(4) {SystemClock.sleep(50);event(android.view.MotionEvent.ACTION_MOVE,to)}
        event(android.view.MotionEvent.ACTION_UP,to);test.waitForIdleSync()
    }
    private fun connectionSpecials(scope:Scope,initial:Activity,folder:File) {
        var activity=initial;val proofs=JSONArray()
        val pageRoot=requireNotNull(folder.parentFile)
        val summary=File(folder,"manifest.json")
        var saved=0
        var preRecreateRefs:JSONObject?=null
        fun persist(completed:Boolean) {summary.writeText(JSONObject().put("evidenceSummaryVersion",2)
            .put("referenceBase","page_root").put("cases",proofs).put("mainConfigurationCountContribution",0)
            .put("fortyCandidateGestureScrollY",if(completed) saved else JSONObject.NULL)
            .put("preRecreateRefs",preRecreateRefs ?: JSONObject.NULL)
            .put("exactRecreateAndReturnScroll",completed).put("noScanNoConnect",true)
            .put("completed",completed).put("visualAcceptance","ASTRA_PENDING").toString(2))}
        fun state(name:String) {val captureFolder=File(folder,name);capture(activity,captureFolder)
            val refs=captureRefs(pageRoot,captureFolder)
            proofs.put(JSONObject().put("case",name).put("geometryRef",refs.getJSONObject("geometryRef"))
                .put("frameSyncRef",refs.getJSONObject("frameSyncRef")));persist(false)}
        val original=candidates(3)
        for(candidate in original) {
            val tag="product/scale-connection/candidate/${candidate.address}"
            listOf("name","address","rssi").forEach {reveal(activity,tagged(activity,"$tag/$it"))}
            if(candidate.scaleProtocolId==io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id)
                reveal(activity,tagged(activity,"$tag/restriction"))
        }
        state("main-three-rows-reachable")
        setCandidates(scope,activity,emptyList());state("empty")
        setCandidates(scope,activity,candidates(3,true))
        for(candidate in candidates(3,true))reveal(activity,tagged(activity,"product/scale-connection/candidate/${candidate.address}/name"))
        state("long-name")
        setCandidates(scope,activity,candidates(40))
        var scroll=field(activity,"candidateScroll") as ScrollView
        main {check(scroll.canScrollVertically(1))};swipe(scroll)
        // Compare only after a real frame with the gesture no longer moving; never wait for a restored expected value.
        frames.committedFrame(root(activity))
        main {check(scroll.scrollY>0);saved=scroll.scrollY}
        val beforeFolder=File(folder,"forty-gesture")
        capture(activity,beforeFolder)
        preRecreateRefs=captureRefs(pageRoot,beforeFolder);persist(false)
        main {check(scroll.scrollY==saved)}
        activity=recreate(activity);render(activity);frames.committedFrame(root(activity))
        scroll=field(activity,"candidateScroll") as ScrollView
        main {check(scroll.scrollY==saved);check(nodes(field(activity,"rows") as View).filterIsInstance<Button>().size==40)}
        state("forty-recreated")
        val monitor=test.addMonitor(ScaleSettingsActivity::class.java.name,null,false)
        var settings:Activity?=null
        try {
            main {activity.startActivity(Intent(activity,ScaleSettingsActivity::class.java))}
            settings=requireNotNull(test.waitForMonitorWithTimeout(monitor,10000))
            await("actual settings return target ready") {settings!!.hasWindowFocus() && root(settings!!).isLaidOut}
            main {settings!!.finish()}
            await("real return to connection window") {activity.hasWindowFocus() && !root(activity).isLayoutRequested}
            frames.committedFrame(root(activity));main {check(scroll.scrollY==saved)}
        } finally {test.removeMonitor(monitor);settings?.let {if(!it.isDestroyed)main {it.finish()}}}
        state("forty-navigation-return")
        persist(true)
    }
    fun run(arguments:Bundle):Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("scalePage"));check(page in setOf("scale","connection"))
        val source=requireNotNull(arguments.getString("sourceSha"));check(source.matches(Regex("[0-9a-f]{40}")))
        val theme=requireNotNull(arguments.getString("sampleTheme"));check(theme in setOf("light","dark"))
        val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat();check(scale in setOf(1f,1.3f))
        val profile="tablet-user-landscape-$theme-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"scale-visual-migration/$source/$profile/$page").apply {check(isDirectory || mkdirs())}
        val reference=if(page=="scale")"d33ae2f0348cd0464b992196a5e1b1c855b3bdce7232f211755e19d4b693bfaf" else "21cba381b9c7025aa138458d37d0c0f0f3e0a9fb277bff943ea3cbcec1edc931"
        check(arguments.getString("referencePngSha256")==reference)
        val manifest=JSONObject().put("sourceSha",source).put("headSha",arguments.getString("headSha"))
            .put("sourceDirty",arguments.getString("sourceDirty")).put("apkSha256",arguments.getString("apkSha"))
            .put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("referenceDesignPngSha256",reference).put("profile",profile).put("page",page)
            .put("realProductActivity",true).put("specimenOnly",false).put("visualAcceptance","ASTRA_PENDING")
            .put("pid",android.os.Process.myPid()).put("scopePolicy","M6_10Lazy_actual_single_Mock_Service_owned_readonly_input_same_clock_no_Home")
        var scope:Scope?=null;var failure:Throwable?=null
        try {
            val active=Scope(directory,page);scope=active;active.prepare();active.install(theme=="dark");active.beginService()
            serviceGuards(active,directory)
            val activity=start(if(page=="scale")ScaleActivity::class.java else ScaleConnectionActivity::class.java)
            await("page bound to exact scoped owner") {ScaleOwnerActivity::class.java.getDeclaredField("owner").apply {isAccessible=true}.get(activity)===active.ownerService}
            if(page=="scale")mainScale(active,activity,directory) else {
                setCandidates(active,activity,candidates(3));File(directory,"fixture.json").writeText(active.fixture.put("candidateCount",3).toString(2))
            }
            val captured=capture(activity,directory)
            val png=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true}
            android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,png)
            check(png.outWidth==2560 && png.outHeight==1600)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(png.outWidth,png.outHeight)))
                .put("initialFrameSync",captured.proof).put("window",captured.target)
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use {input->
                val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192)
                while(true) {val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}
                digest.digest().joinToString("") {"%02x".format(it)}
            }
            check(font==TaskProductVisualStyle.FONT_SHA);manifest.put("fontSha256",font)
            main {
                val window=captured.target
                check(window.getDouble("fontScale").toFloat()==scale && window.getString("uiModeNightMeaning")==theme &&
                    window.getInt("densityDpi")==360 && window.getString("locale").startsWith("zh") && !window.getBoolean("imeVisible"))
                if(page=="scale") {
                    check(whole(observation(field(activity,"weight") as View)))
                    check(whole(observation(field(activity,"tare") as View)))
                    check((field(activity,"timer") as LocalScaleTimer).elapsedMs(124600)==24600L)
                }
            }
            if(page=="scale") {
                trendEvidence(activity,File(directory,"trend-main-data"),scale==1f)
                supportReachability(activity,directory)
                scaleSpecials(active,activity,File(directory,"special-states"))
            } else connectionSpecials(active,activity,File(directory,"special-states"))
            manifest.put("layoutStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE")
        } catch(error:Throwable) {
            failure=error;manifest.put("layoutStatus","FAIL").put("failure",error.stackTraceToString())
            scope?.activeActivity()?.let {activity->try {diagnostic(activity,File(directory,"failure-context"))}catch(other:Throwable) {error.addSuppressed(other)}}
        } finally {
            try {scope?.cleanup();manifest.put("isolationStatus",if(scope==null)"NOT_INSTALLED" else "PASS_ISOLATION_ONLY")}
            catch(error:Throwable) {manifest.put("isolationStatus","FAIL").put("cleanupFailure",error.stackTraceToString());if(failure==null)failure=error else failure!!.addSuppressed(error)}
            manifest.put("overallStatus",if(failure==null)"PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            failure?.let {manifest.put("failure",it.stackTraceToString())}
            manifest.put("fileSha256",JSONObject(directory.walkTopDown().filter {it.isFile && it!=File(directory,"manifest.json")}.associate {it.relativeTo(directory).path to sha(it)}))
            File(directory,"manifest.json").writeText(manifest.toString(2))
        }
        failure?.let {throw it};return 1
    }
}

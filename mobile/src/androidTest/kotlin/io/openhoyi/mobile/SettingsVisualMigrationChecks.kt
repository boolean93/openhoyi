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
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ScrollView
import android.widget.CompoundButton
import android.widget.Button
import android.widget.TextView
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import io.openhoyi.trace.TraceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** M8 three real settings pages. Scoped preferences and one real Mock Service only for Machine. */
internal class SettingsVisualMigrationChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val frames = NativeWindowFrameEvidence(test)
    private val textSnapshotContract="SETTINGS_TEXT_AT_MAIN_THREAD_OBSERVATION_V1"
    private val ownedProviderSessions=linkedSetOf<String>()
    private val providerLifecycle=JSONArray()
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
            check(SystemClock.elapsedRealtime() < deadline) { "M8 timeout: $description" }
            test.waitForIdleSync()
            // Bounded condition polling, never an acceptance delay or retry of a failed assertion.
            SystemClock.sleep(20)
        }
    }
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun appPage(activity: MainActivity) =
        MainPageTestHost(test,activity).active("root:SETTINGS") as AppSettingsFragment
    private fun field(owner: Any, name: String): Any? {
        val page = (owner as? MainActivity)?.supportFragmentManager?.primaryNavigationFragment
        val target = page?.takeIf { runCatching { it.javaClass.getDeclaredField(name) }.isSuccess } ?: owner
        return target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    }
    private fun write(owner: Any, name: String, value: Any?) {
        val page = (owner as? MainActivity)?.supportFragmentManager?.primaryNavigationFragment
        val target = page?.takeIf { runCatching { it.javaClass.getDeclaredField(name) }.isSuccess } ?: owner
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
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
    private fun actualTypography(text:TextView,textSnapshot:String):JSONObject {
        val paint=text.paint;val metrics=paint.fontMetrics;val origin=IntArray(2);text.getLocationOnScreen(origin)
        return JSONObject().put("text",textSnapshot).put("class",text.javaClass.name).put("tag",text.tag)
            .put("textSizePx",paint.textSize).put("weight",paint.typeface?.weight ?: JSONObject.NULL)
            .put("typeface",paint.typeface?.toString() ?: JSONObject.NULL).put("glyphFontIdentity","UNKNOWN_PUBLIC_TEXTVIEW_API_DOES_NOT_EXPOSE_FALLBACK_GLYPH_FONT")
            .put("textScaleX",paint.textScaleX).put("textSkewX",paint.textSkewX).put("fakeBold",paint.isFakeBoldText)
            .put("letterSpacingEm",text.letterSpacing).put("includeFontPadding",text.includeFontPadding)
            .put("baselineScreenPx",origin[1]+text.baseline).put("lineHeightGetterPx",text.lineHeight)
            .put("actualLayout",TaskTypographyEvidence.actualTextLayout(text))
            .put("fontMetricsPx",JSONArray(listOf(metrics.top,metrics.ascent,metrics.descent,metrics.bottom,metrics.leading)))
            .put("colors",TaskVisualColorEvidence.text(text)).put("background",TaskVisualColorEvidence.background(text.background,text.resources))
            .put("foreground",TaskVisualColorEvidence.background(text.foreground,text.resources))
    }
    private fun textLineBox(text:TextView):JSONObject {
        val textVisibility=visibility(text)
        val layout = text.layout
        val textSnapshot=text.text.toString() // Main-thread observation value, not a live Editable/Spannable reference.
        return JSONObject().put("actualTypography",actualTypography(text,textSnapshot)).put("semanticId", text.tag)
                .put("text",textSnapshot).put("fullViewVisible", textVisibility.getBoolean("complete")).put("visibility",textVisibility)
                .put("layoutPresent", layout != null).put("lineCount", layout?.lineCount ?: 0)
                .put("ellipsisCount", layout?.let { (0 until it.lineCount).sumOf(it::getEllipsisCount) } ?: JSONObject.NULL)
                .put("layoutHeightPx", layout?.height ?: JSONObject.NULL)
                .put("contentHeightPx", text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                .put("contentWidthPx", text.width - text.compoundPaddingLeft - text.compoundPaddingRight)
                .put("horizontalLayoutLines",layout?.let { actual -> JSONArray((0 until actual.lineCount).map { index ->
                    JSONObject().put("line",index).put("leftPx",actual.getLineLeft(index)).put("rightPx",actual.getLineRight(index))
                }) } ?: JSONObject.NULL)
    }
    private fun observation(view: View, textCache:IdentityHashMap<TextView,JSONObject>?=null): JSONObject {
        val visible=visibility(view)
        val texts = nodes(view).filterIsInstance<TextView>().map { text ->
            if(textCache==null)textLineBox(text)
            else textCache[text] ?: textLineBox(text).also {textCache[text]=it}
        }
        return visible.put("viewIdentity",System.identityHashCode(view)).put("semanticId",view.tag).put("textLineBoxes",JSONArray(texts))
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
                    check(service==null) { "M8 unexpected second service callback/owner" }
                    val owner=(binder as MobileService.LocalBinder).service
                    // Retain identity before any assertion so strict cleanup can still destroy a partial scope.
                    service=owner
                    check(field(owner,"mock")!=null && field(owner,"hub")==null && !owner.running)
                    check(field(owner,"logs")===logs && field(owner,"history")===history)
                    check(MobileMonotonicClock.mockOwnerIdentity()==null)
                    recoveryNames.forEach { key ->
                        val lazy=field(owner,key+"\$delegate") as Lazy<*>
                        serviceOriginalLazy[key]=lazy; serviceOriginalInitialized[key]=lazy.isInitialized()
                    }
                    check(serviceOriginalInitialized.values.none { it }) { "M8 original service recovery/credential Lazy already initialized; original state not read or cleared" }
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
            override fun onServiceDisconnected(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M8 scoped service disconnected unexpectedly") }
            override fun onBindingDied(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M8 scoped service binding died") }
            override fun onNullBinding(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M8 scoped service returned null binding") }
        }
        private val ownedDirectory = File(context.cacheDir, "settings-visual-${UUID.randomUUID()}")
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
        var expectedTraceExportErrors=0
        var rejectFeedbackWrites=false
        var rejectLanguageWrites=false
        val feedback = BrewFeedbackPreference(object : BrewFeedbackPreference.Storage {
            private var stored=false
            override fun read()=stored
            override fun write(enabled:Boolean):Boolean {if(rejectFeedbackWrites)return false;stored=enabled;return true}
        })
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
            },now={1700100000000L},newId={error("M8 read-only scale scope may not create a shot")},onObservedUse={ entry ->
                usage.record(entry.id,entry.curveId,entry.startedAtMs)
            },onEntryChanged={ entry -> journal.observe(entry.journalObservation())
                if(preparation.current()?.shotId==entry.id)preparation.associatePending(journal,inventory)
            })
            fixture=JSONObject().put("page",page).put("synthetic",true).put("hardwareMeasurement",false)
                .put("noHome",true).put("noScan",true).put("noConnect",true).put("noTare",true).put("noShotControl",true)
                .put("source","scoped_memory_10Lazy; real_Mock_service_only_for_Machine; no_device_control")
                .put("clock","DEFAULT_SYSTEM_MONOTONIC").put("noDeviceControl",true).put("noMockReadOnlyScaleInput",true)
                .put("realMockService",page=="machine").put("hardwareMeasurement",false)
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
                    override fun read() = "zh-Hans"; override fun write(tag: String) = !rejectLanguageWrites }))
                check(app.feedbackPreferences === feedback && app.logs === logs && app.curves === curves && app.samples === samples && app.history === history)
                check(app.customCurvesResult.getOrThrow() === custom && app.curveUsageResult.getOrThrow() === usage && app.beanInventoryResult.getOrThrow() === inventory &&
                    app.beanPreparationResult.getOrThrow() === preparation && app.journalResult.getOrThrow() === journal)
                check(context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dark",dark).commit())
                check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).edit().putString("selected",curveId).commit())
                check(context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).edit().putBoolean("one_decimal",false).commit())
                check(MobileMonotonicClock.mockOwnerIdentity()==null)
            }
        }
        private fun verifyServiceLazyIsolation(owner: MobileService) {
            check(serviceOriginalLazy.size==4 && serviceOriginalInitialized.values.none { it })
            serviceOriginalLazy.forEach { (key,lazy) ->
                check(!lazy.isInitialized()) { "M8 original service Lazy was evaluated: $key" }
                check(field(owner,key+"\$delegate") === (scopedServiceLazy[key] ?: lazy))
            }
            check(field(owner,"logs")===logs && field(owner,"history")===history && field(owner,"hub")==null && field(owner,"mock")!=null)
        }
        val ownerService:MobileService get()=requireNotNull(service)
        fun beginService() {
            check(installed && page=="machine")
            main {check(MobileMonotonicClock.mockOwnerIdentity()==null);serviceBound=context.bindService(Intent(context,MobileService::class.java),serviceConnection,Context.BIND_AUTO_CREATE);check(serviceBound)}
            await("unique actual service callback and isolated unopened recovery stores") {serviceCallbackFailure?.let {throw it};service!=null && scopedServiceLazy.size==2}
            main {verifyServiceLazyIsolation(ownerService);check(context.startService(Intent(context,MobileService::class.java))!=null);serviceStarted=true}
            await("actual default-system-clock Mock IDLE readback") {
                val snapshot=ownerService.snapshot;val now=SystemClock.elapsedRealtime()
                ownerService.running && snapshot.settings!=null && snapshot.coffee is io.openhoyi.protocol.IdleTelemetry &&
                    snapshot.coffeeAt?.let {it<=now && now-it<=1500}==true && ownerService.machineSettingsFresh
            }
            main {check(MobileMonotonicClock.mockOwnerIdentity()==null);fixture.put("realSettingsReadback",true)
                .put("singleServiceOwnerIdentity",System.identityHashCode(ownerService)).put("systemMonotonicMs",SystemClock.elapsedRealtime())}
        }
        fun closeActivities() {
            main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
            await("all current scoped pages/dialogs closed before next saved-record case") {
                tracked.all { it.isDestroyed && it in destroyed } && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()
            }

        }
        fun stopForNoServiceState() {
            closeActivities()
            main {if(serviceBound) {context.unbindService(serviceConnection);serviceBound=false}
                check(context.stopService(Intent(context,MobileService::class.java)));serviceStarted=false}
            await("actual service stopped before no-service settings case") {
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
                                    !(parentField("handler") as Handler).hasCallbacks(parentField("refresh") as Runnable)) {"M8 ScaleOwner binding/refresh survived destruction"}
                            }
                            tracked.filter {it is MainActivity || it is MachineSettingsActivity}.forEach {activity->
                                check(field(activity,"service")==null && field(activity,"bound")==false) {"M8 settings binding survived destruction"}
                                if(activity is MachineSettingsActivity)check(field(activity,"visible")==false && !(field(activity,"handler") as Handler).hasCallbacks(field(activity,"refresh") as Runnable))
                            }
                            if(serviceBound) { context.unbindService(serviceConnection); serviceBound=false }
                            val stopped=context.stopService(Intent(context,MobileService::class.java))
                            if(serviceStarted) check(stopped) { "M8 started scoped service stop rejected" }
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
                            check((field(app.visibility,"observers") as Map<*,*>)==originalVisibilityObservers) { "M8 original visibility observers changed" }
                            service?.let { owner ->
                                verifyServiceLazyIsolation(owner)
                                proof.put("serviceOwnerId",serviceOwnerId).put("serviceOwnerCallbacksRemoved",true)
                                    .put("serviceHubClosed",field(owner,"hub")==null).put("serviceHandlersDrained",true)
                            }
                        }
                        proof.put("actualServiceDestroyed",service!=null).put("noServiceWasStartedForThisPage",service==null).put("serviceRecoveryLazyIdentityRestored",true)
                            .put("serviceVisibilityObserverRemoved",true).put("serviceFeedbackObserverRemoved",true).put("scaleOwnersUnboundAndRefreshRemoved",true)
                            .put("originalVisibilityObserversPreserved",true)
                    }
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) { "M8 fixture dialog/window remains after Activity destruction" }
                    // M8 creates no reader pool. All Activity/Service references are drained before restoring delegates.
                    main { check(services().isEmpty()) }
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("ownedAsyncReaderCount",0).put("noServiceBeforeOriginalDelegateRestore",true)
                        .put("mainQueueBarrierAfterOwners",true).put("allOwnedCallbacksDrained",true)
                }
                proof.put("ownedSampleRepositoryCreated",::samples.isInitialized).put("ownedTraceCreated",::logs.isInitialized)
                if (::samples.isInitialized) {
                    val writer=field(samples,"writer") as ExecutorService
                    writer.shutdown(); check(writer.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped sample writer did not terminate" }
                }
                if (::logs.isInitialized) { logs.close(); check(logs.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped trace writer did not terminate" } }
                main {} // Export callbacks post UI notifications; drain them before restoring any owner.
                proof.put("mainQueueBarrierAfterWriters",true)
                if(::logs.isInitialized) {
                    val status=logs.status.split(" ")
                    check("dropped=0" in status && "errors=$expectedTraceExportErrors" in status)
                    proof.put("scopedTraceStatusAfterTermination",logs.status).put("expectedActualExportFailures",expectedTraceExportErrors)
                }
                check(sampleFailures.isEmpty()) { "Scoped sample writer failed" }
                main {check((field(feedback,"observers") as Map<*,*>).isEmpty())}
                main {tracked.filterIsInstance<MainActivity>().forEach {activity->
                    val settings=activity.supportFragmentManager.findFragmentByTag("root:SETTINGS") as? AppSettingsFragment
                        ?: return@forEach
                    val card=requireNotNull(field(settings,"feedbackCard"));val audio=field(card,"audioOwner") as Lazy<*>
                    if(audio.isInitialized()) {val owner=audio.value as BrewFeedbackAudio;check(!owner.playing && field(owner,"request")==null && field(owner,"cancel")==null) {"Preview audio/token/cancel survived page destruction"}}
                }}
                check(ownedProviderSessions.isEmpty()) {"Owned SAF provider session not removed"}
                proof.put("providerLifecycle",providerLifecycle).put("feedbackPreviewStoppedAndUnobserved",true).put("providerSessionRemoved",true)
                proof.put("scopedWritersTerminated",true)
                proof.put("clipboard","NOT_READ_OR_WRITTEN")
                if(installed) main {
                    // Only after all pages, service callbacks, readers and writers have drained.
                    service?.let { owner ->
                        verifyServiceLazyIsolation(owner)
                        serviceOriginalLazy.forEach { (key,lazy) -> write(owner,key+"\$delegate",lazy) }
                        serviceOriginalLazy.forEach { (key,lazy) ->
                            check(field(owner,key+"\$delegate")===lazy && lazy.isInitialized()==serviceOriginalInitialized.getValue(key))
                        }
                    }
                    check(MobileMonotonicClock.mockOwnerIdentity()==null)
                    proof.put("serviceOriginalLazyIdentityAndInitializedRestored",true).put("mockClockOwnerAbsentBeforeAndAfter",true)

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
    private fun syntheticObservationEquivalence() {
        check(Looper.myLooper()==Looper.getMainLooper())
        val outer=android.widget.LinearLayout(context).apply {orientation=android.widget.LinearLayout.VERTICAL;tag="synthetic/outer"}
        val inner=android.widget.LinearLayout(context).apply {orientation=android.widget.LinearLayout.VERTICAL;tag="synthetic/inner"}
        val styled=android.text.SpannableString("第一行\n第二行").apply {
            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),0,3,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val background=android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(18,52,86))
        val first=TextView(context).apply {tag="synthetic/same";text=styled;setBackground(background);setTextColor(android.graphics.Color.rgb(20,40,60))}
        val second=TextView(context).apply {tag="synthetic/same";text=styled}
        val empty=TextView(context).apply {tag="synthetic/empty"}
        inner.addView(first,android.widget.LinearLayout.LayoutParams(180,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        inner.addView(second,android.widget.LinearLayout.LayoutParams(180,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        outer.addView(inner,android.widget.LinearLayout.LayoutParams(220,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT))
        outer.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST))
        outer.layout(0,0,outer.measuredWidth,outer.measuredHeight)
        check(first.layout!=null && first.layout.lineCount>=2 && empty.layout==null && first!==second && first.tag==second.tag)
        val views=listOf(outer,inner,first,second,empty)
        fun expanded(cache:IdentityHashMap<TextView,JSONObject>?):String = JSONArray(views.map {observation(it,cache)}).toString(2)
        val beforeText=first.text.toString();val beforeColor=first.currentTextColor
        val beforePaintSize=first.paint.textSize;val beforeBackground=first.background
        val beforeBounds=Rect(background.bounds);val beforeAlpha=background.alpha
        val beforeSize=first.width to first.height;val beforeScroll=outer.scrollX to outer.scrollY
        val original=expanded(null)
        val cache=IdentityHashMap<TextView,JSONObject>()
        val reused=expanded(cache)
        check(original==reused && cache.size==3 && cache.containsKey(first) && cache.containsKey(second) &&
            cache[first]!==cache[second] && first.text.toString()==beforeText && first.currentTextColor==beforeColor &&
            first.paint.textSize==beforePaintSize && first.background===beforeBackground &&
            background.bounds==beforeBounds && background.alpha==beforeAlpha &&
            (first.width to first.height)==beforeSize && (outer.scrollX to outer.scrollY)==beforeScroll)
        first.text="更新\n下一行";first.setTextColor(android.graphics.Color.rgb(60,40,20));outer.scrollTo(0,1)
        outer.measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.AT_MOST))
        outer.layout(0,0,outer.measuredWidth,outer.measuredHeight)
        val changedOriginal=expanded(null)
        val changedCache=IdentityHashMap<TextView,JSONObject>()
        val changedReused=expanded(changedCache)
        check(changedOriginal==changedReused && changedOriginal!=original && changedCache.size==3 &&
            changedCache[first]?.getString("text")=="更新\n下一行" &&
            first.currentTextColor==android.graphics.Color.rgb(60,40,20) && first.background===beforeBackground)
    }
    private fun geometry(activity:Activity):JSONObject {
        check(Looper.myLooper()==Looper.getMainLooper()) {"M8 Settings geometry must observe text on the main thread"}
        val textCache=IdentityHashMap<TextView,JSONObject>()
        val content=root(activity);val origin=IntArray(2);content.getLocationOnScreen(origin)
        val insets=requireNotNull(content.rootWindowInsets);val bars=insets.getInsets(WindowInsets.Type.systemBars())
        val config=activity.resources.configuration
        return JSONObject().put("textSnapshotContract",textSnapshotContract)
            .put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("appOriginPx",JSONArray(origin.toList())).put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("systemInsetsPx",JSONArray(listOf(bars.left,bars.top,bars.right,bars.bottom)))
            .put("imeVisible",insets.isVisible(WindowInsets.Type.ime())).put("densityDpi",config.densityDpi)
            .put("fontScale",config.fontScale).put("locale",config.locales.toLanguageTags())
            .put("uiModeNightMeaning",if(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES)"dark" else "light")
            .put("nodes",JSONArray(nodes(content).filter {it.tag is String || it is TextView}.map {observation(it,textCache)}))
            .put("productLayoutEvidence",if(activity is MachineSettingsActivity)JSONObject(activity.productLayoutEvidence.toString()) else JSONObject.NULL)
            .put("scrolls",JSONArray(nodes(content).filterIsInstance<ScrollView>().map {JSONObject().put("tag",it.tag).put("x",it.scrollX).put("y",it.scrollY)}))
    }
    private fun ready(activity:Activity)=activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested &&
        !activity.window.decorView.isLayoutRequested && root(activity).width>0 && root(activity).height>0
    private fun start(type:Class<out Activity>):Activity {
        if(type==AppSettingsActivity::class.java) return MainPageTestHost.launch(test,TaskDestination.SETTINGS).activity
        return test.startActivitySync(Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also {activity->await("${type.simpleName} real window ready") {ready(activity)}}
    }
    private fun recreate(activity:Activity):Activity {
        val monitor=test.addMonitor(activity.javaClass.name,null,false)
        try {main {activity.recreate()};return requireNotNull(test.waitForMonitorWithTimeout(monitor,10000)).also {replacement->await("replacement window ready") {ready(replacement)}}}
        finally {test.removeMonitor(monitor)}
    }
    private fun tagged(activity:Activity,tag:String):View=requireNotNull(root(activity).findViewWithTag<View>(tag)) {"Missing real settings target: $tag"}
    private fun reveal(activity:Activity,view:View) {
        main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
        frames.committedFrame(root(activity));main {check(whole(observation(view))) {"M8 target not completely reachable: ${view.tag}/${(view as? TextView)?.text}"}}
    }
    private fun click(activity:Activity,view:View) {
        reveal(activity,view)
        main {
            check(view.isShown && view.isEnabled && view.isClickable && view.width>=HoyiUi.dp(activity,48) && view.height>=HoyiUi.dp(activity,48))
            val precision=view.tag=="product/settings/scale/precision"
            val toggleTag=view.tag in setOf("product/settings/app/theme/toggle", "product/settings/app/feedback/toggle",
                "product/settings/scale/precision")
            if (toggleTag) {
                check(view is CompoundButton) { "M8 real settings toggle changed class: ${view.tag}" }
                if(precision) check(!view.isChecked &&
                    !context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).getBoolean("one_decimal",false))
                view.performClick()
                if(precision) check(view.isChecked &&
                    context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).getBoolean("one_decimal",false))
            } else check(view.performClick())
        }
    }
    private fun click(activity:Activity,tag:String)=click(activity,tagged(activity,tag))
    private fun capture(activity:Activity,folder:File):NativeWindowFrameEvidence.CapturedFrame {
        check(folder.isDirectory || folder.mkdirs())
        val captured=frames.screenshot(activity,root(activity),File(folder,"window.png")) {geometry(activity)}
        File(folder,"geometry.json").writeText(captured.target.toString(2))
        main {
            if(activity.resources.configuration.locales[0].language=="zh")TaskTypographyEvidence.capture(activity,folder)
            // Non-Chinese fallback is observable as the real Layout/Paint only, never a Chinese-font match claim.
            File(folder,"actual-text-typography.json").writeText(JSONObject().put("locale",activity.resources.configuration.locales.toLanguageTags())
                .put("source","accepted_protocol_target_geometry_actual_TextView_Paint_Layout_not_owner_or_specimen")
                .put("nodes",captured.target.getJSONArray("nodes")).toString(2))
            val pairs=JSONArray()
            nodes(root(activity)).filterIsInstance<TextView>().filter {(it.tag as? String)?.endsWith("/number")==true && it.isShown}.forEach {number->
                val prefix=(number.tag as String).removeSuffix("/number");val unit=root(activity).findViewWithTag<TextView>("$prefix/unit")
                if(unit!=null && unit.isShown) {
                    val a=IntArray(2);val b=IntArray(2);number.getLocationOnScreen(a);unit.getLocationOnScreen(b)
                    pairs.put(JSONObject().put("semanticId",prefix).put("numberBaselinePx",a[1]+number.baseline).put("unitBaselinePx",b[1]+unit.baseline))
                    check(a[1]+number.baseline==b[1]+unit.baseline) {"M8 real number/unit baseline mismatch: $prefix"}
                }
            }
            File(folder,"baseline-alignment.json").writeText(pairs.toString(2))
        }
        check(captured.proof.getInt("protocolVersion")==2 && captured.proof.getBoolean("accepted"));return captured
    }
    private fun diagnostic(activity:Activity,folder:File) {
        check(folder.isDirectory || folder.mkdirs());val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
        try {File(folder,"window.png").outputStream().use {check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}}finally {bitmap.recycle()}
        main {File(folder,"geometry.json").writeText(geometry(activity).put("sameCompositorFrameProven",false).toString(2))}
    }
    private fun stableTextTargets(activity:Activity,subtree:View):List<Pair<String,TextView>> {
        val editors=if(activity is MachineSettingsActivity)mapOf(
            field(activity,"brewInput") to "brew",field(activity,"compensationInput") to "compensation",
            field(activity,"steamInput") to "steam",field(activity,"standbyTemperatureInput") to "standby") else emptyMap()
        val all=mutableListOf<Pair<String,TextView>>()
        fun visit(view:View,path:String) {
            if(!view.isShown)return
            if(view is TextView && view.text.isNotEmpty()) {
                val tagged=view.tag as? String
                val id=when {tagged?.startsWith("product/settings/")==true->tagged
                    view in editors->"product/settings/machine/field/${editors.getValue(view)}"
                    else->"body/text/$path"}
                all.add(id to view)
            }
            if(view is ViewGroup)(0 until view.childCount).forEach {visit(view.getChildAt(it),"$path/$it")}
        }
        visit(subtree,"root");check(all.map {it.first}.distinct().size==all.size);return all
    }
    private fun reachability(activity:Activity,folder:File,domain:String="main",subtree:View?=null):JSONObject {
        check(folder.isDirectory || folder.mkdirs())
        val page=when(activity) {is MainActivity->"app";is MachineSettingsActivity->"machine";else->"scale"}
        val source=subtree ?: requireNotNull(root(activity).findViewWithTag<ScrollView>("product/settings/$page/scroll"))
        val targets=stableTextTargets(activity,source)
        check(targets.isNotEmpty())
        val scrolls=mutableListOf<Pair<String,View>>()
        fun visit(view:View,path:String) {
            if(view is ScrollView || view is android.widget.HorizontalScrollView)scrolls.add(path to view)
            if(view is ViewGroup)(0 until view.childCount).forEach {visit(view.getChildAt(it),"$path/$it")}
        }
        visit(root(activity),"root")
        fun scrollState()=JSONArray(scrolls.map {(id,view)->JSONObject().put("id",id).put("x",view.scrollX).put("y",view.scrollY)})
        lateinit var before:JSONArray;main {before=scrollState()}
        val saved=scrolls.map {(_,view)->view.scrollX to view.scrollY}
        val reached=JSONArray();val document=JSONObject().put("domain",domain).put("page",page)
            .put("allActualBodyTargets",JSONArray(targets.map {(id,view)->JSONObject().put("id",id).put("viewIdentity",System.identityHashCode(view)).put("text",view.text.toString())}))
            .put("coreTargets",JSONArray(targets.map {it.first}.filter {it.startsWith("product/settings/")})).put("beforeScroll",before).put("targets",reached)
        var failure:Throwable?=null
        try {
            for((index,pair) in targets.withIndex()) {
                val (id,view)=pair;reveal(activity,view)
                val frame=capture(activity,File(folder,"target-$index"))
                val all=frame.target.getJSONArray("nodes")
                val actual=(0 until all.length()).map {all.getJSONObject(it)}.single {it.getInt("viewIdentity")==System.identityHashCode(view)}
                check(whole(actual)) {"Accepted frame did not contain complete target: $id"}
                reached.put(JSONObject().put("id",id).put("viewIdentity",System.identityHashCode(view)).put("acceptedActualTarget",actual)
                    .put("proof","target-$index/window-frame-sync.json").put("window","target-$index/window.png").put("geometry","target-$index/geometry.json"))
            }
            document.put("completed",true)
        } catch(error:Throwable) {failure=error;document.put("completed",false).put("failure",error.stackTraceToString());throw error}
        finally {
            try {
                main {scrolls.forEachIndexed {index,(_,view)->view.scrollTo(saved[index].first,saved[index].second)}}
                frames.committedFrame(root(activity));main {val restored=scrollState();document.put("restoredScroll",restored);check(restored.toString()==before.toString())}
                capture(activity,File(folder,"restored"));document.put("restoreProof","restored/window-frame-sync.json").put("exactScrollRestored",true)
            } catch(other:Throwable) {document.put("exactScrollRestored",false).put("restoreFailure",other.stackTraceToString());if(failure==null)throw other else failure.addSuppressed(other)}
            finally {File(folder,"reachability.json").writeText(document.toString(2))}
        }
        return document
    }
    private fun dismiss(dialog:AlertDialog,activity:Activity) {
        main {dialog.dismiss()};await("actual dialog dismissed and owner focus restored") {!dialog.isShowing && !requireNotNull(dialog.window).decorView.isAttachedToWindow && ready(activity)}
        frames.committedFrame(root(activity))
    }
    private fun dialogSafeControls(dialog:AlertDialog,owner:Activity,window:android.view.Window):JSONObject {
        var ownerContext:Context=dialog.context
        while(ownerContext is android.content.ContextWrapper && ownerContext !is Activity)ownerContext=ownerContext.baseContext
        val decor=window.decorView;val visible=Rect();decor.getWindowVisibleDisplayFrame(visible)
        val origin=IntArray(2);decor.getLocationOnScreen(origin)
        val safe=Rect(origin[0],origin[1],origin[0]+decor.width,origin[1]+decor.height)
        val safeIntersectsVisibleFrame=safe.intersect(visible)
        val focused=decor.findFocus()
        val positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);val negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val buttons=listOf("positive" to positive,"negative" to negative)
        val controls=JSONArray(listOfNotNull(positive,negative,focused).distinct().filter {it.isShown}.map {view ->
            observation(view).put("roles",JSONArray(listOfNotNull(if(view===positive)"positive" else null,
                if(view===negative)"negative" else null,if(view===focused)"focused" else null)))
                .put("isShown",view.isShown).put("heightPx",view.height)
                .put("isButton",view is Button).put("isEditText",view is android.widget.EditText)
        })
        val focusedWindows=android.view.inspector.WindowInspector.getGlobalWindowViews()
            .filter {it.isAttachedToWindow && it.isLaidOut && it.hasWindowFocus()}
        return JSONObject().put("safeRectScreenPx",rect(safe)).put("safeIntersectsVisibleFrame",safeIntersectsVisibleFrame)
            .put("visibleFrameScreenPx",rect(visible)).put("density",owner.resources.displayMetrics.density)
            .put("dialogIdentity",System.identityHashCode(dialog)).put("windowIdentity",System.identityHashCode(window))
            .put("decorIdentity",System.identityHashCode(decor)).put("ownerActivityIdentity",System.identityHashCode(owner))
            .put("callbackIsDialog",window.callback===dialog).put("dialogWindowIsWindow",dialog.window===window)
            .put("ownerMatches",dialog.ownerActivity===owner || ownerContext===owner)
            .put("decorAttached",decor.isAttachedToWindow).put("decorLaidOut",decor.isLaidOut)
            .put("decorLayoutRequested",decor.isLayoutRequested).put("decorHasWindowFocus",decor.hasWindowFocus())
            .put("uniqueFocusedWindow",focusedWindows.size==1 && focusedWindows[0]===decor)
            .put("focusedControlIdentity",focused?.let(System::identityHashCode) ?: JSONObject.NULL)
            .put("buttonRoles",JSONArray(buttons.map {(role,button)->JSONObject().put("role",role)
                .put("present",button!=null).put("isShown",button?.isShown==true)
                .put("identity",button?.let(System::identityHashCode) ?: JSONObject.NULL)}))
            .put("controls",controls)
    }
    private fun dialogGeometry(window:android.view.Window,dialog:AlertDialog?=null,owner:Activity?=null):JSONObject {
        check(Looper.myLooper()==Looper.getMainLooper()) {"M8 Settings dialog geometry must observe text on the main thread"}
        val decor=window.decorView;val insets=requireNotNull(decor.rootWindowInsets);val screen=IntArray(2);decor.getLocationOnScreen(screen)
        val available=Rect();decor.getWindowVisibleDisplayFrame(available)
        return JSONObject().put("textSnapshotContract",textSnapshotContract)
            .put("decorScreenPx",rect(Rect(screen[0],screen[1],screen[0]+decor.width,screen[1]+decor.height)))
            .put("actualVisibleFrameScreenPx",rect(available)).put("imeVisible",insets.isVisible(WindowInsets.Type.ime()))
            .put("softInputMode",window.attributes.softInputMode).put("hasWindowFocus",decor.hasWindowFocus())
            .put("nodes",JSONArray(nodes(decor).filter {view ->
                view is TextView || view.tag is String || (dialog!=null &&
                    (view===dialog.getButton(AlertDialog.BUTTON_POSITIVE) ||
                        view===dialog.getButton(AlertDialog.BUTTON_NEGATIVE) || view===decor.findFocus()))
            }.map(::observation)))
            .apply {if(dialog!=null && owner!=null)put("safeControls",dialogSafeControls(dialog,owner,window))}
    }
    private fun captureDialog(dialog:AlertDialog,folder:File) {
        check(folder.isDirectory || folder.mkdirs());val window=requireNotNull(dialog.window)
        var ownerContext:Context=dialog.context
        while(ownerContext is android.content.ContextWrapper && ownerContext !is Activity)ownerContext=ownerContext.baseContext
        val owner=dialog.ownerActivity ?: ownerContext as Activity
        check(window.callback===dialog && (dialog.ownerActivity===owner || ownerContext===owner))
        val languageRejected=folder.name=="language-write-rejected"
        if(languageRejected)check(owner is MainActivity)
        val frame=if(languageRejected)
            frames.screenshotMeasuredWindowBufferTranslation(window,window.decorView,File(folder,"window.png")) {
                dialogGeometry(window,dialog,owner)
            }
        else frames.screenshot(window,window.decorView,File(folder,"window.png")) {dialogGeometry(window)}
        File(folder,"geometry.json").writeText(frame.target.toString(2))
        File(folder,"dialog-typography.json").writeText(JSONObject().put("source","accepted_explicit_dialog_window_target_geometry_not_owner_Activity")
            .put("nodes",frame.target.getJSONArray("nodes")).toString(2))
        if(languageRejected) {
            val observed=frame.target.getJSONArray("nodes")
            val error=(0 until observed.length()).map {observed.getJSONObject(it)}.single {
                it.optString("semanticId")=="product/settings/app/language/error" }
            check(whole(error) && error.getJSONArray("textLineBoxes").getJSONObject(0).getString("text")==owner.getString(R.string.language_save_failed))
            val safe=frame.target.getJSONObject("safeControls")
            File(folder,"safe-controls.json").writeText(safe.toString(2))
            check(safe.getBoolean("callbackIsDialog") && safe.getBoolean("dialogWindowIsWindow") && safe.getBoolean("ownerMatches") &&
                safe.getBoolean("decorAttached") && safe.getBoolean("decorLaidOut") && !safe.getBoolean("decorLayoutRequested") &&
                safe.getBoolean("decorHasWindowFocus") && safe.getBoolean("uniqueFocusedWindow") && safe.getBoolean("safeIntersectsVisibleFrame"))
            val buttons=safe.getJSONArray("buttonRoles")
            check(buttons.length()==2 && !buttons.getJSONObject(0).getBoolean("isShown") &&
                buttons.getJSONObject(1).getBoolean("isShown")) {"M8 actual native dialog button role mismatch: ${folder.name}"}
            val bounds=safe.getJSONArray("safeRectScreenPx")
            val controls=safe.getJSONArray("controls")
            check(controls.length()>0)
            for(index in 0 until controls.length()) {
                val info=controls.getJSONObject(index);val full=info.getJSONArray("rectPx")
                check(info.getBoolean("isShown") && whole(info) &&
                    full.getInt(0)>=bounds.getInt(0) && full.getInt(1)>=bounds.getInt(1) &&
                    full.getInt(2)<=bounds.getInt(2) && full.getInt(3)<=bounds.getInt(3))
                if(info.getBoolean("isButton"))check(info.getInt("heightPx")>=HoyiUi.dp(owner,48))
                if(info.getBoolean("isEditText"))check(info.getInt("heightPx")>=HoyiUi.dp(owner,52))
            }
        } else main {
            val visible=Rect();window.decorView.getWindowVisibleDisplayFrame(visible)
            val origin=IntArray(2);window.decorView.getLocationOnScreen(origin)
            val safe=Rect(origin[0],origin[1],origin[0]+window.decorView.width,origin[1]+window.decorView.height)
            check(safe.intersect(visible))
            val focused=window.decorView.findFocus()
            val positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);val negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val expectedButtonRoles=when(folder.name) {
                "language-write-rejected"->setOf("negative")
                "legal-input-mock-preview"->setOf("positive")
                "schedule-draft-native-ime","schedule-cancel-no-write"->setOf("positive","negative")
                else->error("Unexpected native dialog state: ${folder.name}")
            }
            val buttons=listOf("positive" to positive,"negative" to negative)
            buttons.forEach {(role,button)->check((button?.isShown==true)==(role in expectedButtonRoles)) {"M8 actual native dialog button role mismatch: ${folder.name} $role"}}
            val visibleDistinctControls=listOfNotNull(positive,negative,focused).distinct().filter {it.isShown}
            for(view in visibleDistinctControls) {
                val info=observation(view);val a=IntArray(2);view.getLocationOnScreen(a)
                val full=Rect(a[0],a[1],a[0]+view.width,a[1]+view.height)
                check(safe.contains(full) && whole(info)) {"M8 real native dialog control clipped: ${(view as? TextView)?.text} $full safe=$safe"}
                if(view is Button)check(view.height>=HoyiUi.dp(owner,48))
                if(view is android.widget.EditText)check(view.height>=HoyiUi.dp(owner,52))
            }
            File(folder,"safe-controls.json").writeText(JSONObject().put("safeRectScreenPx",rect(safe))
                .put("buttonRoles",JSONArray(buttons.map {(role,button)->JSONObject().put("role",role).put("present",button!=null).put("isShown",button?.isShown==true)}))
                .put("controls",JSONArray(visibleDistinctControls.map {view->observation(view)
                    .put("roles",JSONArray(listOfNotNull(if(view===positive)"positive" else null,if(view===negative)"negative" else null,if(view===focused)"focused" else null)))
                    .put("isShown",view.isShown)})).toString(2))
        }
    }
    private fun mainDialogStatus(dialog:AlertDialog,tag:String):TextView =
        nodes(requireNotNull(dialog.window).decorView).single {it.tag==tag} as TextView
    /** SDK34 read-only identity discovery; missing reflection or an ambiguous Window is a hard failure. */
    private fun focusedDialog(activity:Activity):AlertDialog? {
        val candidates=android.view.inspector.WindowInspector.getGlobalWindowViews().filter {it!==activity.window.decorView && it.isAttachedToWindow && it.isLaidOut && it.hasWindowFocus()}
        check(candidates.size<=1) {"Ambiguous actual focused native dialog decor"}
        val decor=candidates.singleOrNull() ?: return null
        val window=decor.javaClass.getDeclaredField("mWindow").apply {isAccessible=true}.get(decor) as android.view.Window
        check(window.decorView===decor)
        val dialog=window.callback as AlertDialog
        var actualContext:Context=dialog.context
        while(actualContext is android.content.ContextWrapper && actualContext!==activity)actualContext=actualContext.baseContext
        check(dialog.window===window && (dialog.ownerActivity===activity || actualContext===activity)) {"Focused native dialog owner is not the current settings Activity"}
        check(dialog.isShowing);return dialog
    }
    private fun requireDialog(activity:Activity):AlertDialog {
        var found:AlertDialog?=null
        await("unique real native dialog focused/laid out") {found=focusedDialog(activity);found!=null && !requireNotNull(found?.window).decorView.isLayoutRequested}
        return requireNotNull(found)
    }
    private fun languageCard(activity:MainActivity)=field(activity,"languageCard") as AppLanguagePreferencesCard
    private fun chooseLanguage(activity:MainActivity,language:AppLanguage):MainActivity {
        click(activity,"product/settings/app/language/current")
        val chooser=requireNotNull(languageCard(activity).dialog)
        await("native language chooser ready") {chooser.isShowing && requireNotNull(chooser.window).decorView.hasWindowFocus() && chooser.listView.isLaidOut}
        val index=AppLanguage.entries.indexOf(language)
        val changes=app.languagePreferences.current!=language
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        try {
            main {chooser.listView.setSelection(index)};frames.committedFrame(requireNotNull(chooser.window).decorView)
            main {val row=chooser.listView.getChildAt(index-chooser.listView.firstVisiblePosition);check(row!=null && whole(observation(row)) && row.height>=HoyiUi.dp(activity,52));check(chooser.listView.performItemClick(row,index,chooser.listView.adapter.getItemId(index)))}
            return if(changes)requireNotNull(test.waitForMonitorWithTimeout(monitor,10000)).let {replacement->
                await("selected language real recreated context") {ready(replacement) && app.languagePreferences.current==language && replacement.resources.configuration.locales[0].language==language.locale.language}
                replacement as MainActivity
            } else activity.also {await("unchanged language dialog dismissed") {!chooser.isShowing && ready(activity)}}
        } finally {test.removeMonitor(monitor)}
    }
    private fun appExport(scope:Scope,activity:MainActivity,folder:File) {
        check(context.checkSelfPermission(CurveFixtureProvider.PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
        check(context.packageManager.checkSignatures(context.packageName,test.context.packageName)==android.content.pm.PackageManager.SIGNATURE_MATCH)
        val session=UUID.randomUUID().toString();val base=android.net.Uri.parse("content://${CurveFixtureProvider.AUTHORITY}")
        fun provider(method:String,data:Bundle?=null)=requireNotNull(context.contentResolver.call(base,method,session,data))
        fun picker(code:Int,uri:android.net.Uri?) {
            var observed:Intent?=null;var calls=0
            val monitor=object:Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult {observed=Intent(intent);calls++;return Instrumentation.ActivityResult(code,uri?.let {Intent().setData(it).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)})}
            }
            test.addMonitor(monitor)
            try {click(activity,"product/settings/app/export");main {
                val intent=requireNotNull(observed);check(calls==1 && intent.action==Intent.ACTION_CREATE_DOCUMENT && intent.type=="application/zip" && intent.categories==setOf(Intent.CATEGORY_OPENABLE))
                check(intent.component==null && intent.`package`==null && intent.getStringExtra(Intent.EXTRA_TITLE)?.startsWith("openhoyi-diagnostics-")==true)
            }} finally {test.removeMonitor(monitor)}
        }
        provider("create",Bundle().apply {putString("document","{\"kind\":\"openhoyi.local-curve\"}")})
        ownedProviderSessions.add(session);providerLifecycle.put(JSONObject().put("session",session).put("operation","created_owned_provider"))
        val result=JSONArray()
        try {
            picker(Activity.RESULT_CANCELED,null);test.waitForIdleSync();main {}
            check(provider("stats").getInt("write:output.json")==0)
            main {val status=tagged(activity,"product/settings/app/export/result") as TextView;check(!status.isShown && status.text.isEmpty())}
            result.put(JSONObject().put("case","saf-cancel").put("writes",0).put("originalCallback",true))
            val failedPrefix=activity.getString(R.string.export_trace_failed,"")
            picker(Activity.RESULT_OK,CurveFixtureProvider.uri(session,"valid.json"))
            await("actual SAF failure inline result") {val status=tagged(activity,"product/settings/app/export/result") as TextView
                status.isShown && status.text.toString().startsWith(failedPrefix) && status.text.length>failedPrefix.length}
            scope.expectedTraceExportErrors++
            result.put(JSONObject().put("case","saf-failure").put("actualFailedInline",true).put("outputWrites",provider("stats").getInt("write:output.json")))
            picker(Activity.RESULT_OK,CurveFixtureProvider.uri(session,"output.json"))
            await("actual SAF success inline result") {val status=tagged(activity,"product/settings/app/export/result") as TextView
                status.isShown && status.text.toString()==activity.getString(R.string.export_trace_success)}
            check(provider("stats").getInt("write:output.json")==1)
            val bytes=requireNotNull(context.contentResolver.openInputStream(CurveFixtureProvider.uri(session,"output.json"))).use {it.readBytes()}
            val names=mutableListOf<String>();java.util.zip.ZipInputStream(bytes.inputStream()).use {zip->while(true) {val entry=zip.nextEntry ?: break;names.add(entry.name);zip.readBytes()}}
            check("metadata.json" in names);result.put(JSONObject().put("case","saf-owned-success").put("zipEntries",JSONArray(names)).put("actualSuccessInline",true).put("outputWrites",provider("stats").getInt("write:output.json")))
        } finally {provider("cleanup");check(ownedProviderSessions.remove(session));providerLifecycle.put(JSONObject().put("session",session).put("operation","owned_provider_cleanup_returned"))}
        check(folder.isDirectory || folder.mkdirs());File(folder,"saf-business.json").writeText(JSONObject().put("cases",result).put("providerSession",session).put("providerSessionRemoved",!ownedProviderSessions.contains(session)).toString(2))
    }
    private fun appSpecials(scope:Scope,initial:MainActivity,folder:File):JSONArray {
        var activity=initial;val cases=JSONArray()
        fun state(name:String) {capture(activity,File(folder,name));cases.put(JSONObject().put("case",name).put("acceptanceStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE"))}
        val stored=context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dark",false)
        val writer=appPage(activity).appearanceWrite
        try {
            main {appPage(activity).appearanceWrite={false}}
            click(activity,"product/settings/app/theme/toggle")
            main {check(appPage(activity).themeCheckBox.isChecked==stored && appPage(activity).themeError.text.isNotEmpty() && context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dark",false)==stored)}
            state("theme-write-rejected")
        } finally {main {appPage(activity).appearanceWrite=writer}}
        main {scope.rejectLanguageWrites=true}
        click(activity,"product/settings/app/language/current")
        val chooser=requireNotNull(languageCard(activity).dialog)
        await("rejected-language native chooser ready") {chooser.listView.isLaidOut && requireNotNull(chooser.window).decorView.hasWindowFocus()}
        main {
            val index=AppLanguage.entries.indexOf(AppLanguage.ENGLISH);chooser.listView.setSelection(index)
        }
        frames.committedFrame(requireNotNull(chooser.window).decorView)
        main {val index=AppLanguage.entries.indexOf(AppLanguage.ENGLISH);val row=chooser.listView.getChildAt(index-chooser.listView.firstVisiblePosition);check(chooser.listView.performItemClick(row,index,chooser.listView.adapter.getItemId(index)));check(app.languagePreferences.current==AppLanguage.CHINESE && chooser.listView.checkedItemPosition==0)
            val error=mainDialogStatus(chooser,"product/settings/app/language/error");check(error.isShown && error.text.toString()==activity.getString(R.string.language_save_failed))}
        captureDialog(chooser,File(folder,"language-write-rejected"));dismiss(chooser,activity)
        main {scope.rejectLanguageWrites=false}
        cases.put(JSONObject().put("case","language-write-rejected").put("acceptanceStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE"))
        for(language in AppLanguage.entries.filter {it!=AppLanguage.CHINESE}+AppLanguage.CHINESE) {
            activity=chooseLanguage(activity,language)
            check(app.languagePreferences.current==language)
            capture(activity,File(folder,"language-${language.tag}"));reachability(activity,File(folder,"language-${language.tag}/current-reachable"),"language-current",tagged(activity,"product/settings/app/language/current"))
            cases.put(JSONObject().put("case","language-${language.tag}").put("actualLanguage",activity.resources.configuration.locales.toLanguageTags()).put("acceptanceStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE"))
        }
        main {scope.rejectFeedbackWrites=true}
        click(activity,"product/settings/app/feedback/toggle")
        main {check(!scope.feedback.enabled && !(requireNotNull(field(activity,"feedbackCard")) as BrewFeedbackPreferencesCard).enabledSwitch.isChecked)
            val error=tagged(activity,"product/settings/app/feedback/error") as TextView
            check(error.isShown && error.text.toString()==activity.getString(R.string.feedback_save_failed))};state("feedback-write-rejected")
        main {scope.rejectFeedbackWrites=false}
        click(activity,"product/settings/app/feedback/toggle");main {check(scope.feedback.enabled)}
        click(activity,"product/settings/app/feedback/preview")
        val card=requireNotNull(field(activity,"feedbackCard")) as BrewFeedbackPreferencesCard
        main {val lazy=field(card,"audioOwner") as Lazy<*>;check(lazy.isInitialized() && (lazy.value as BrewFeedbackAudio).playing)}
        main {val preview=tagged(activity,"product/settings/app/feedback/preview");check(whole(observation(preview)) && preview.isEnabled && preview.isClickable);check(preview.performClick());check(!((field(card,"audioOwner") as Lazy<*>).value as BrewFeedbackAudio).playing)}
        state("feedback-preview-start-stop")
        click(activity,"product/settings/app/feedback/preview")
        val monitor=test.addMonitor(MachineSettingsActivity::class.java.name,null,false)
        try {
            click(activity,"product/settings/app/machine")
            val next=requireNotNull(test.waitForMonitorWithTimeout(monitor,10000));await("real machine settings navigation ready") {ready(next)}
            main {check(!((field(card,"audioOwner") as Lazy<*>).value as BrewFeedbackAudio).playing);check(!(field(scope.feedback,"observers") as Map<*,*>).containsKey(card));next.finish()}
            await("real app return ready") {ready(activity)}
        } finally {test.removeMonitor(monitor)}
        state("feedback-preview-leave-stopped")
        appExport(scope,activity,folder)
        listOf("saf-cancel","saf-failure","saf-owned-success").forEach {cases.put(JSONObject().put("case",it).put("acceptanceStatus","PASS_BUSINESS_ONLY_NOT_VISUAL_ACCEPTANCE"))}
        // Successful theme selection uses the real preference/recreate path after the rejection contract.
        val themeMonitor=test.addMonitor(MainActivity::class.java.name,null,false)
        try {click(activity,"product/settings/app/theme/toggle");activity=requireNotNull(test.waitForMonitorWithTimeout(themeMonitor,10000)) as MainActivity;await("theme recreated actual window") {ready(activity)}
            main {check(appPage(activity).themeCheckBox.isChecked==!stored && context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dark",stored)==!stored)}
            state("theme-success-recreated")
        } finally {test.removeMonitor(themeMonitor)}
        cases.put(JSONObject().put("case","hardware-audio-output").put("acceptanceStatus","PENDING_PHYSICAL_AUDIO_OUTPUT_NOT_INFERRED_FROM_PLAYER"))
        return cases
    }
    private fun machineSpecials(scope:Scope,initial:MachineSettingsActivity,folder:File):JSONArray {
        var activity=initial;val cases=JSONArray()
        fun state(name:String) {capture(activity,File(folder,name));cases.put(JSONObject().put("case",name).put("acceptanceStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE"))}
        @Suppress("UNCHECKED_CAST")
        fun tabs()=field(activity,"settingTabs") as List<TextView>
        fun section(index:Int) {
            click(activity,tabs()[index]);main {check(field(activity,"selectedSettingsSection")==index)}
            frames.committedFrame(root(activity))
        }
        for(index in 0..4) {
            section(index);state("section-$index")
            reachability(activity,File(folder,"section-$index/reachable"),"section-$index",(field(activity,"settingSections") as List<*>)[index] as View)
        }
        activity=recreate(activity) as MachineSettingsActivity
        main {check(field(activity,"selectedSettingsSection")==4)};state("section-recreated")
        section(0)
        val fields=listOf("brewInput" to "brew","compensationInput" to "compensation","steamInput" to "steam")
        for((name,key) in fields) {
            val editor=field(activity,name) as android.widget.EditText
            val original=editor.text.toString()
            val invalid="987654321098765432109876543210987654321098765432109876543210"
            main {editor.setText(invalid);editor.setSelection(7,14)}
            // The complete editor rectangle and unit stay visible, while native single-line text scrolls.
            main {editor.requestRectangleOnScreen(Rect(0,0,editor.width,editor.height),true)};frames.committedFrame(root(activity))
            val unit=tagged(activity,"product/settings/machine/$key/unit")
            capture(activity,File(folder,"invalid-$key"))
            main {
                check(visibility(editor).getBoolean("complete") && editor.height>=HoyiUi.dp(activity,52))
                check(whole(observation(unit)) && editor.text.toString()==invalid && editor.selectionStart==7 && editor.selectionEnd==14)
                val parent=editor.parent as View;val a=IntArray(2);val b=IntArray(2);editor.getLocationOnScreen(a);unit.getLocationOnScreen(b)
                val origin=IntArray(2);parent.getLocationOnScreen(origin)
                check(Rect(origin[0],origin[1],origin[0]+parent.width,origin[1]+parent.height).contains(Rect(b[0],b[1],b[0]+unit.width,b[1]+unit.height)))
            }
            click(activity,"product/settings/machine/$key/action")
            main {check(editor.error!=null && scope.ownerService.pendingSetting==null)}
            cases.put(JSONObject().put("case","invalid-$key").put("rawText",invalid).put("selection",JSONArray(listOf(7,14))).put("unitStillInsideQuantity",true).put("acceptanceStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE"))
            main {editor.setText(original);editor.clearFocus()}
        }
        val brew=field(activity,"brewInput") as android.widget.EditText
        val baseline=brew.text.toString()
        main {brew.setText("94");brew.setSelection(0,2)}
        click(activity,"product/settings/machine/brew/action")
        val mockConfirm=requireDialog(activity)
        captureDialog(mockConfirm,File(folder,"legal-input-mock-preview"))
        main {check(scope.ownerService.pendingSetting==null && scope.ownerService.settingWriteState==io.openhoyi.session.SettingsWriteTracker.State.IDLE)}
        dismiss(mockConfirm,activity);main {brew.setText(baseline)}
        cases.put(JSONObject().put("case","legal-input-mock-preview").put("noWriteOrAckInjected",true).put("acceptanceStatus","PASS_BUSINESS_ONLY_NOT_HARDWARE"))
        reveal(activity,brew)
        main {check(brew.requestFocus());brew.setSelection(0,brew.text.length);(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(brew,0)}
        await("actual native machine input IME and resized layout") {root(activity).rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true && !root(activity).isLayoutRequested}
        main {brew.requestRectangleOnScreen(Rect(0,0,brew.width,brew.height),true)};frames.committedFrame(root(activity))
        capture(activity,File(folder,"temperature-native-ime"))
        main {check(whole(observation(brew)) && brew.hasFocus() && brew.height>=HoyiUi.dp(activity,52))}
        main {(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(brew.windowToken,0);brew.clearFocus()}
        await("actual keyboard dismissed before schedule") {root(activity).rootWindowInsets?.isVisible(WindowInsets.Type.ime())==false && ready(activity)}
        cases.put(JSONObject().put("case","temperature-native-ime").put("acceptanceStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE"))
        section(3)
        @Suppress("UNCHECKED_CAST")
        val day=(field(activity,"scheduleButtons") as List<Button>).first()
        click(activity,day);val schedule=requireDialog(activity)
        val editors=nodes(requireNotNull(schedule.window).decorView).filterIsInstance<android.widget.EditText>();check(editors.size==4)
        val draft=editors.map {it.text.toString()}
        val last=editors.last()
        main {last.setText("37");last.setSelection(0,2);check(last.requestFocus());(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(last,0)}
        await("actual native schedule IME and final dialog viewport") {requireNotNull(schedule.window).decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true && !requireNotNull(schedule.window).decorView.isLayoutRequested}
        main {last.requestRectangleOnScreen(Rect(0,0,last.width,last.height),true)};frames.committedFrame(requireNotNull(schedule.window).decorView)
        captureDialog(schedule,File(folder,"schedule-draft-native-ime"))
        main {check(last.text.toString()=="37" && last.selectionStart==0 && last.selectionEnd==2);check(scope.ownerService.scheduleWriteState==io.openhoyi.session.SleepScheduleWriteTracker.State.IDLE)}
        dismiss(schedule,activity)
        main {(activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(root(activity).windowToken,0)}
        await("native IME hidden after schedule cancellation") {root(activity).rootWindowInsets?.isVisible(WindowInsets.Type.ime())==false && ready(activity)}
        click(activity,day);val reopened=requireDialog(activity)
        main {check(nodes(requireNotNull(reopened.window).decorView).filterIsInstance<android.widget.EditText>().map {it.text.toString()}==draft)}
        captureDialog(reopened,File(folder,"schedule-cancel-no-write"));dismiss(reopened,activity)
        cases.put(JSONObject().put("case","schedule-cancel-no-write").put("acceptanceStatus","PASS_BUSINESS_ONLY_NOT_HARDWARE"))
        cases.put(JSONObject().put("case","schedule-draft-native-ime").put("draftObserved",true).put("cancelDidNotCommit",true).put("acceptanceStatus","PASS_BUSINESS_ONLY_NOT_HARDWARE"))
        cases.put(JSONObject().put("case","device-confirmed-unknown-recovery").put("acceptanceStatus","PENDING_REAL_DEVICE_NOT_LEGALLY_GENERATED"))
        // No service special is real destruction, not a synthetic disconnected snapshot.
        scope.stopForNoServiceState()
        activity=start(MachineSettingsActivity::class.java) as MachineSettingsActivity
        main {
            @Suppress("UNCHECKED_CAST") val actions=field(activity,"controlButtons") as List<Button>
            check(services().isEmpty() && actions.all {!it.isEnabled} && MobileMonotonicClock.mockOwnerIdentity()==null)
        }
        state("no-service-disabled")
        return cases
    }
    private fun scaleSpecials(initial:ScaleSettingsActivity,folder:File):JSONArray {
        var activity=initial;val cases=JSONArray()
        click(activity,"product/settings/scale/precision")
        main {check((tagged(activity,"product/settings/scale/precision") as android.widget.Switch).isChecked &&
            context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).getBoolean("one_decimal",false))}
        activity=recreate(activity) as ScaleSettingsActivity
        main {check((tagged(activity,"product/settings/scale/precision") as android.widget.Switch).isChecked);check((field(activity,"capabilities") as TextView).text.toString()==activity.getString(R.string.scale_capability_no_device))}
        capture(activity,File(folder,"precision-recreated"));reachability(activity,File(folder,"all-instructions"),"all-instructions")
        cases.put(JSONObject().put("case","precision-recreated").put("acceptanceStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE"))
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        try {
            main {activity.startActivity(Intent(activity,ScaleActivity::class.java))}
            val scale=requireNotNull(test.waitForMonitorWithTimeout(monitor,10000)) as MainActivity
            await("actual no-service scale page ready") {ready(scale)}
            main {check((field(scale,"weight") as TextView).text.toString()=="—" && context.getSharedPreferences("scale_tool",Context.MODE_PRIVATE).getBoolean("one_decimal",false) && services().isEmpty())}
            capture(scale,File(folder,"real-scale-no-data"))
            main {scale.finish()};await("return from actual scale") {ready(activity)}
        } finally {test.removeMonitor(monitor)}
        cases.put(JSONObject().put("case","real-scale-no-data").put("acceptanceStatus","PASS_PREF_AND_ROUTE_ONLY_NO_FAKE_READING"))
        cases.put(JSONObject().put("case","ready-nonmock-hardware-capabilities").put("acceptanceStatus","PENDING_REAL_DEVICE_MOCK_GATE_PRESERVED"))
        return cases
    }
    fun run(arguments:Bundle):Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("settingsPage"));check(page in setOf("app","machine","scale-settings"))
        val source=requireNotNull(arguments.getString("sourceSha"));check(source.matches(Regex("[0-9a-f]{40}")))
        val theme=requireNotNull(arguments.getString("sampleTheme"));check(theme in setOf("light","dark"))
        val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat();check(scale in setOf(1f,1.3f))
        val profile="tablet-user-landscape-$theme-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"settings-visual-migration/$source/$profile/$page").apply {check(isDirectory || mkdirs())}
        val reference=when(page) {"app"->"f2022d5eb60b798a722d30f01933028a63f14a342958400c74fcdc25a7c1a899";"machine"->"ccb438958aae1cb9a6e88bec1c2fdd5ffafd4a18d385ff63a0e9024c68f6c60d";else->"f87b9cf3d260a3b01bd9d88e83fa2a5cb40d8e1d456de37b8c579515a8eec36f"}
        check(arguments.getString("referencePngSha256")==reference)
        val manifest=JSONObject().put("sourceSha",source).put("headSha",arguments.getString("headSha"))
            .put("sourceDirty",arguments.getString("sourceDirty")).put("apkSha256",arguments.getString("apkSha"))
            .put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("referenceDesignPngSha256",reference).put("profile",profile).put("page",page)
            .put("realProductActivity",true).put("specimenOnly",false).put("visualAcceptance","ASTRA_PENDING")
            .put("pid",android.os.Process.myPid()).put("scopePolicy","M8_10Lazy_default_system_clock_single_real_Mock_Service_only_for_Machine_no_control_injection")
        var scope:Scope?=null;var failure:Throwable?=null
        try {
            main {syntheticObservationEquivalence()}
            val active=Scope(directory,page);scope=active;active.prepare();active.install(theme=="dark")
            if(page=="machine")active.beginService()
            val activity=start(when(page) {"app"->AppSettingsActivity::class.java;"machine"->MachineSettingsActivity::class.java;else->ScaleSettingsActivity::class.java})
            if(activity is MachineSettingsActivity)await("actual Machine binding to exact isolated Service") {field(activity,"service")===active.ownerService}
            else main {check(services().isEmpty())}
            File(directory,"fixture.json").writeText(active.fixture.toString(2))
            val frame=capture(activity,directory)
            val png=android.graphics.BitmapFactory.Options().apply {inJustDecodeBounds=true};android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,png)
            check(png.outWidth==2560 && png.outHeight==1600)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(png.outWidth,png.outHeight))).put("initialFrameSync",frame.proof).put("window",frame.target)
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use {input->val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192)
                while(true) {val count=input.read(buffer);if(count<0)break;digest.update(buffer,0,count)};digest.digest().joinToString("") {"%02x".format(it)}}
            check(font==TaskProductVisualStyle.FONT_SHA);manifest.put("fontSha256",font)
            main {
                val window=frame.target
                check(window.getDouble("fontScale").toFloat()==scale && window.getString("uiModeNightMeaning")==theme && window.getInt("densityDpi")==360 && window.getString("locale").startsWith("zh") && !window.getBoolean("imeVisible"))
                check(MobileMonotonicClock.mockOwnerIdentity()==null)
                when(activity) {
                    is MainActivity -> {
                        check((tagged(activity,"product/settings/app/info/package/value") as TextView).text.toString()==BuildConfig.APPLICATION_ID)
                        check((tagged(activity,"product/settings/app/info/mode/value") as TextView).text.toString()==activity.getString(R.string.application_mode_mock))
                        check(!(field(requireNotNull(field(activity,"feedbackCard")),"audioOwner") as Lazy<*>).isInitialized()) {"Main screenshot started preview audio"}
                    }
                    is MachineSettingsActivity -> {
                        val pageScroll=requireNotNull(activity.findViewById<ScrollView>(R.id.m8_settings_scroll))
                        check(pageScroll.isVerticalScrollBarEnabled)
                        check(!pageScroll.isScrollbarFadingEnabled)
                        check(field(activity,"selectedSettingsSection")==0)
                        listOf("brewInput","compensationInput","steamInput").forEach {check((field(activity,it) as TextView).text.isNotEmpty())}
                        check(active.ownerService.snapshot.settings!=null && active.ownerService.pendingSetting==null)
                    }
                    is ScaleSettingsActivity -> check((field(activity,"capabilities") as TextView).text.toString()==activity.getString(R.string.scale_capability_no_device))
                }
            }
            reachability(activity,File(directory,"main-reachability"))
            val specialFolder=File(directory,"special-states").apply {check(isDirectory || mkdirs())}
            val cases=when(activity) {
                is MainActivity->appSpecials(active,activity,specialFolder)
                is MachineSettingsActivity->machineSpecials(active,activity,specialFolder)
                is ScaleSettingsActivity->scaleSpecials(activity,specialFolder)
                else->error("Unexpected real M8 Activity")
            }
            File(specialFolder,"manifest.json").writeText(JSONObject().put("cases",cases).put("completed",true).put("mainConfigurationCountContribution",0).put("visualAcceptance","ASTRA_PENDING").toString(2))
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
            try { WindowProofStreamingWriter.writeStrict(manifest,File(directory,"manifest.json")) }
            catch(error:Throwable) { if(failure==null) failure=error else failure!!.addSuppressed(error) }
        }
        failure?.let {throw it};return 1
    }
}

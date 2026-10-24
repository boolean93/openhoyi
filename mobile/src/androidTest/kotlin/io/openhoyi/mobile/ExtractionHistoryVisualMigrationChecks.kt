package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import io.openhoyi.session.ShotRecoveryState
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.DeviceState
import io.openhoyi.session.OperationResult
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.protocol.ScaleCapabilities
import io.openhoyi.protocol.ScaleEvidence
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ScrollView
import android.widget.EditText
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import io.openhoyi.session.ExtractionState
import io.openhoyi.trace.TraceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** M5 four real Activities, with scoped stores and a single real Mock owner. Never hardware evidence. */
internal class ExtractionHistoryVisualMigrationChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val frames = NativeWindowFrameEvidence(test)
    private val extractionPendingSpecialCases=listOf("STARTING","STOP_REQUESTED","UNKNOWN_live","no_scale",
        "stale_scale","no_service_ready","manual","historical_reference_toggle","missing_samples",
        "water_reset","sample_gap","journal_unavailable")
    private val extractionScenarioShotIds=linkedMapOf(
        "STARTING" to "m5-extraction-starting", "STOP_REQUESTED" to "m5-extraction-stop-requested",
        "UNKNOWN_live" to "m5-extraction-unknown-live", "no_scale" to "m5-extraction-no-scale",
        "stale_scale" to "m5-extraction-stale-scale", "no_service_ready" to "m5-extraction-no-service-ready",
        "manual" to "m5-extraction-manual", "historical_reference_toggle" to "m5-extraction-historical-reference-toggle",
        "missing_samples" to "m5-extraction-missing-samples", "water_reset" to "m5-extraction-water-reset",
        "sample_gap" to "m5-extraction-sample-gap", "journal_unavailable" to "m5-extraction-journal-unavailable")
    private val historyPendingSpecialCases=listOf("STARTING","STOP_REQUESTED","UNKNOWN_live","no_scale",
        "stale_scale","no_service_ready","manual","historical_reference_toggle","journal_unavailable")
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
            check(SystemClock.elapsedRealtime() < deadline) { "M5 timeout: $description" }
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
            !(directory.name=="shared_prefs" && it.name in setOf("appearance.xml","appearance.xml.bak","curves.xml","curves.xml.bak")) }
            .map { it.canonicalPath to sha(it) }.toList() }.toMap()
    }
    private fun services(): List<String> = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(100).filter { it.service.packageName == context.packageName }.map { it.service.className }
    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    private fun pointRecords(points: List<ShotPoint>)=JSONArray(points.map { point ->
        JSONObject().put("elapsedMs",point.elapsedMs).put("pressureTenthsBar",point.pressureTenthsBar)
            .put("machineFlowTenthsMlPerSecond",point.machineFlowTenthsMlPerSecond).put("waterTenthsMl",point.waterTenthsMl)
            .put("temperatureHundredthsC",point.temperatureHundredthsC).put("weightHundredthsGram",point.weightHundredthsGram ?: JSONObject.NULL)
            .put("scaleFlowHundredths",point.scaleFlowHundredths ?: JSONObject.NULL).put("brewing",point.brewing ?: JSONObject.NULL)
    })
    private fun observation(view: View): JSONObject {
        val origin = IntArray(2); view.getLocationOnScreen(origin)
        val full = Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
        val visible = Rect(); val shown = view.getGlobalVisibleRect(visible)
        val texts = nodes(view).filterIsInstance<TextView>().map { text ->
            val box = Rect(); val point = IntArray(2); text.getLocationOnScreen(point)
            val whole = text.getGlobalVisibleRect(box) && box == Rect(point[0], point[1], point[0] + text.width, point[1] + text.height)
            val layout = text.layout
            JSONObject().put("semanticId", text.tag).put("text", text.text.toString()).put("fullViewVisible", whole)
                .put("layoutPresent", layout != null).put("lineCount", layout?.lineCount ?: 0)
                .put("ellipsisCount", layout?.let { (0 until it.lineCount).sumOf(it::getEllipsisCount) } ?: JSONObject.NULL)
                .put("layoutHeightPx", layout?.height ?: JSONObject.NULL)
                .put("contentHeightPx", text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                .put("contentWidthPx", text.width - text.compoundPaddingLeft - text.compoundPaddingRight)
                .put("horizontalLayoutLines",layout?.let { actual -> JSONArray((0 until actual.lineCount).map { index ->
                    JSONObject().put("line",index).put("leftPx",actual.getLineLeft(index)).put("rightPx",actual.getLineRight(index))
                }) } ?: JSONObject.NULL)
        }
        return JSONObject().put("semanticId", view.tag).put("rectPx", rect(full)).put("visibleRectPx", rect(visible))
            .put("complete", shown && view.width > 0 && view.height > 0 && visible == full)
            .put("textLineBoxes", JSONArray(texts))
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
        private fun whitePrefFiles(): Map<String,String> = listOf("appearance.xml","appearance.xml.bak","curves.xml","curves.xml.bak").mapNotNull { name ->
            File(File(context.applicationInfo.dataDir,"shared_prefs"),name).takeIf { it.isFile }?.let { name to sha(it) }
        }.toMap()
        private var installed = false
        private val clockOwner=Any()
        private val initialMonotonicMs=1000000L
        private var clockInstalled=false
        private var service: MobileService?=null
        private var serviceBound=false
        private var serviceStarted=false
        private var scenarioInstalled=false
        private var scaleInputInstalled=false
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
                    check(service==null) { "M5 unexpected second service callback/owner" }
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
                    check(serviceOriginalInitialized.values.none { it }) { "M5 original service recovery/credential Lazy already initialized; original state not read or cleared" }
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
            override fun onServiceDisconnected(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M5 scoped service disconnected unexpectedly") }
            override fun onBindingDied(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M5 scoped service binding died") }
            override fun onNullBinding(name: ComponentName) { serviceCallbackFailure=IllegalStateException("M5 scoped service returned null binding") }
        }
        private val ownedDirectory = File(context.cacheDir, "extraction-history-visual-${UUID.randomUUID()}")
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
        val firstId="m5-history-a"
        val secondId="m5-history-b"
        val curveId="factory-v3-002"
        private var nextId="m5-unassigned"
        val historySpecialIds=linkedMapOf("missing-samples" to "m5-history-extra-38","no-notes" to "m5-history-extra-6",
            "unknown-observation" to "m5-history-extra-7","sample-gap" to "m5-history-extra-8","water-reset" to "m5-history-extra-9")
        private val sampleInputs=linkedMapOf<String,List<ShotPoint>>()
        fun expectedSamples(id: String)=requireNotNull(sampleInputs[id]) { "M5 no declared sample input: $id" }.toList()
        private val sampleFailures=java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        private val comparisonReaders=linkedMapOf<Activity,ExecutorService>()
        private val readerNames=setOf("history-detail-reader")
        private fun detailReaders()=Thread.getAllStackTraces().keys.filter { it.isAlive && it.name in readerNames }
        private fun points(elapsed: Long, water: Int, weight: Int): List<ShotPoint> =
            ((0L..elapsed step 1000).toList()+elapsed).distinct().map { time ->
                ShotPoint(time,90,100,(water*time/elapsed).toInt(),9300,(weight*time/elapsed).toInt(),null,true)
            }
        lateinit var fixture: JSONObject
            private set
        private val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                tracked.add(activity)
                if(activity is BrewComparisonActivity) comparisonReaders[activity]=field(activity,"readers") as ExecutorService
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
            samples=ShotSamplesRepository(File(ownedDirectory,"samples")) { sampleFailures.add(it); logs.record("fixture.samples_error",mapOf("type" to it.javaClass.simpleName)) }
            val factory=FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val proof=FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"),factory,context.assets.open("factory_slot_wire_v1.tsv"))
            curves=CurveLibrary(factory,proof,{ custom.items() },true)
            history=ShotHistory(object: ShotHistory.Storage { var value=""
                override fun read()=value; override fun write(value: String) { this.value=value }
            },now={1700100000000L},newId={nextId},onObservedUse={ entry ->
                usage.record(entry.id,entry.curveId,entry.startedAtMs)
            },onEntryChanged={ entry ->
                journal.observe(entry.journalObservation())
                if(preparation.current()?.shotId==entry.id) preparation.associatePending(journal,inventory)
            })
            fun observed(id: String, start: Long, elapsed: Long, weight: Int, unknown: Boolean=false) {
                nextId=id
                check(history.begin(curveId,start)==id)
                history.transition(ExtractionState.RUNNING,null,null,start)
                history.observeRunning()
                if(unknown) history.abandon(id,"合成隔离夹具：连接中断",start+elapsed)
                else history.transition(ExtractionState.ENDED_OBSERVED,null,weight,start+elapsed)
            }
            observed(firstId,1700000000000L,27400,3620)
            observed(secondId,1700086400000L,31000,3950)
            // Enough genuine continuous rows for nonzero viewport restoration in both sources.
            (1..38).forEach { index -> observed("m5-history-extra-$index",1700000000000L-index*60000L,30000,3600,index%7==0) }
            val notes=BrewJournal.Notes("m5-bean","晨间拼配",18000L,"2.3圈 / 中细磨",
                "后段流速略快，希望更平稳。","柑橘酸质，焦糖甜感，尾段略干。","研磨调细一点，其他条件不变。")
            journal.edit(firstId,notes)
            journal.edit(secondId,notes.copy(taste="焦糖甜感更明显。",nextAdjustment="保持当前参数。"))
            val first=points(27400,604,3620); val second=points(31000,650,3950)
            sampleInputs[firstId]=first; sampleInputs[secondId]=second
            (1..37).forEach { index ->
                val ordinary=points(30000,620,3600)
                sampleInputs["m5-history-extra-$index"]=when(index) {
                    8 -> ordinary.filter { it.elapsedMs !in 4000L..7000L } // Real missing observations, not a fabricated connecting path.
                    9 -> ordinary.map { point -> if(point.elapsedMs>=15000L) point.copy(waterTenthsMl=point.waterTenthsMl-300) else point }
                    else -> ordinary
                }
            }
            sampleInputs.forEach { (id,input) -> samples.save(id,input) }
            sampleInputs["m5-history-extra-38"]=emptyList() // Deliberate missing file, never replaced by a fake load result.
            // Other extra rows retain legitimate blank BrewJournal.Notes().
            // A legal queued writer barrier; do not substitute the repository load result.
            (field(samples,"writer") as ExecutorService).submit {}.get(10,TimeUnit.SECONDS)
            check(sampleFailures.isEmpty() && samples.load(firstId)==first && samples.load(secondId)==second)
            historySpecialIds.values.forEach { id -> check(samples.load(id)==expectedSamples(id)) }
            check(!File(ownedDirectory,"samples/samples-m5-history-extra-38.tsv").exists())
            fixture=JSONObject().put("page",page).put("firstId",firstId).put("secondId",secondId).put("curveId",curveId)
                .put("history",JSONArray(history.entries.map { it.toString() })).put("journal",JSONObject(journal.exportJson()))
                .put("firstSamples",pointRecords(first)).put("secondSamples",pointRecords(second))
                .put("historyReferenceSamples",pointRecords(emptyList()))
                .put("historySpecialInputs",JSONArray(historySpecialIds.map { (name,id) ->
                    JSONObject().put("case",name).put("shotId",id).put("journal",requireNotNull(journal.find(id)).toString())
                        .put("sampleFileExists",File(ownedDirectory,"samples/samples-$id.tsv").isFile).put("samples",pointRecords(expectedSamples(id)))
                }))
                .put("sampleUnits","elapsedMs; pressureTenthsBar; machineFlowTenthsMlPerSecond=100 independent; waterTenthsMl; temperatureHundredthsC; weightHundredthsGram")
                .put("source","legal_memory_Storage; actual_ShotHistory_begin_observeRunning_transition_and_BrewJournal_edit; repository_save_then_writer_barrier")
                .put("synthetic",true).put("hardwareMeasurement",false).put("servicePolicy","history_only_no_Home_no_service")
                .put("clipboardSAFSlots","not_touched")
        }
        fun install(dark: Boolean) {
            main {
                check(MobileMonotonicClock.mockOwnerIdentity()==null && detailReaders().isEmpty() && services().isEmpty() && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) {
                    "Fresh isolated process required: service or attached window already exists"
                }
                delegateNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>
                    saved[name]=lazy; originalInitialized[name]=lazy.isInitialized() }
                legacyNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>; legacy[name]=lazy to lazy.isInitialized() }
                // A fresh process must not have acquired any original store before this entire group is installed.
                check(saved.values.none { it.isInitialized() }) { "Original delegate initialized before fixture isolation" }
                oldLanguage = field(app,"languagePreferences"); oldFailure=field(app,"journalObservationFailure")
                listOf("appearance","curves").forEach { savedPrefs[it]=context.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap() }
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
                if(page=="extraction") {
                    MobileMonotonicClock.installForMock(clockOwner,initialMonotonicMs)
                    clockInstalled=true
                    check(MobileMonotonicClock.ownsMockClock(clockOwner))
                }
            }
        }
        private fun verifyServiceLazyIsolation(owner: MobileService) {
            check(serviceOriginalLazy.size==4 && serviceOriginalInitialized.values.none { it })
            serviceOriginalLazy.forEach { (key,lazy) ->
                check(!lazy.isInitialized()) { "M5 original service Lazy was evaluated: $key" }
                check(field(owner,key+"\$delegate") === (scopedServiceLazy[key] ?: lazy))
            }
            check(field(owner,"logs")===logs && field(owner,"history")===history && field(owner,"hub")==null && field(owner,"mock")!=null)
        }
        private fun beginService() {
            check(page=="extraction" && installed && clockInstalled)
            main {
                serviceBound=context.bindService(Intent(context,MobileService::class.java),serviceConnection,Context.BIND_AUTO_CREATE)
                check(serviceBound) { "M5 scoped service bind rejected" }
            }
            await("first scoped service callback with unopened recovery stores") {
                serviceCallbackFailure?.let { throw it }
                service!=null && scopedServiceLazy.size==2
            }
            val owner=requireNotNull(service)
            main {
                verifyServiceLazyIsolation(owner)
                check(context.startService(Intent(context,MobileService::class.java))!=null)
                serviceStarted=true
            }
            await("real Mock started with current machine and scale observation") {
                owner.running && owner.snapshot.coffeeAt==initialMonotonicMs && owner.snapshot.weightAt==initialMonotonicMs &&
                    owner.snapshot.coffeeState==DeviceState.READY && owner.snapshot.scaleState==DeviceState.READY
            }
            main { verifyServiceLazyIsolation(owner) }
        }
        fun beginExtraction() {
            beginService()
            val owner=requireNotNull(service)
            main {
                verifyServiceLazyIsolation(owner)
                val profile=requireNotNull(curves.resolve(curveId,true,7))
                check(curves.validated(profile))
                nextId="m5-live-shot"
                check(owner.startShot(curveId,profile.scaleMode,7)==null) { "M5 real Mock start rejected" }
                check(owner.shotState==ExtractionState.RUNNING && owner.chartTraceIdentity==nextId && owner.chartCurveId==curveId)
                journal.edit(nextId,requireNotNull(journal.find(firstId)).notes)
            }
            val elapsedInputs=(0L..24000L step 1000).toList()+24600L
            for(elapsed in elapsedInputs) {
                val now=initialMonotonicMs+elapsed
                main { MobileMonotonicClock.advanceForMock(clockOwner,now); logicalInputTimes.add(now) }
                await("legal Mock tick/ShotSeries sample at elapsed=$elapsed") {
                    val frame=owner.snapshot.coffee as? ExtractionTelemetry
                    owner.snapshot.coffeeAt==now && owner.snapshot.weightAt==now && frame!=null &&
                        owner.shotState==ExtractionState.RUNNING && owner.chartPoints.lastOrNull()?.elapsedMs==elapsed
                }
            }
            main {
                verifyServiceLazyIsolation(owner)
                check(owner.chartPoints.map { it.elapsedMs }==elapsedInputs)
                check(owner.chartPoints.last().elapsedMs==24600L && owner.chartPoints.last().weightHundredthsGram!=null)
                check(!shotRecovery.pending && machineWriteRecovery.kind==null)
                fixture.put("servicePolicy","unique_real_Mock_service_created_after_10Lazy_and_clock; guarded_2_recovery_Lazy_only; no_Home_no_BLE")
                    .put("liveShotId",owner.chartTraceIdentity).put("clockInputMonotonicMs",JSONArray(logicalInputTimes))
                    .put("clockInitialMs",initialMonotonicMs).put("clockFrozenForFrameOnly",true)
                    .put("liveSamples",pointRecords(owner.chartPoints)).put("machineObservation",owner.snapshot.coffee.toString())
                    .put("scaleObservation",owner.snapshot.weight.toString()).put("scaleSemantics","scale_reading_no_tare_context_no_effective_cup_claim")
                    .put("serviceOwnerId",serviceOwnerId).put("recoveryOriginalUninitialized",true)
            }
        }
        fun beginScenarioService(case: String): MobileService {
            check(case in extractionPendingSpecialCases && case!="no_service_ready")
            beginService()
            val owner=requireNotNull(service)
            if(case=="no_scale") {
                main { (field(owner,"mock") as MockDeviceRuntime).scaleConnected=false }
                await("original Mock scale-disconnected observation") { owner.snapshot.scaleState==DeviceState.DISCONNECTED }
            }
            main {
                owner.installMockExtractionScenario(clockOwner)
                scenarioInstalled=true
                nextId=extractionScenarioShotIds.getValue(case)
                check(owner.shotState==ExtractionState.IDLE && owner.mockExtractionOfferedFrameSerial==0L)
            }
            return owner
        }
        fun beginUnreadyService(): MobileService {
            check(page=="extraction" && installed && clockInstalled)
            main { serviceBound=context.bindService(Intent(context,MobileService::class.java),serviceConnection,Context.BIND_AUTO_CREATE);check(serviceBound) }
            await("unstarted unique Mock Service callback") { serviceCallbackFailure?.let {throw it};service!=null }
            return requireNotNull(service).also { owner -> main {
                check(!owner.running && owner.shotState==ExtractionState.IDLE && owner.snapshot.coffeeState==DeviceState.DISCONNECTED)
            } }
        }
        fun scenarioOwner()=clockOwner
        fun now()=MobileMonotonicClock.elapsedRealtime()
        fun installStaleScaleInput(owner: MobileService) {
            main {
                check(scenarioInstalled && !scaleInputInstalled && owner===service)
                owner.installScaleVisualInput(clockOwner)
                scaleInputInstalled=true
                val sample=ScaleObservation(1800,initialMonotonicMs,
                    ScaleCapabilities(tare=false,validatedWeightControl=false),ScaleEvidence.LIVE_READ_ONLY,
                    raw=ByteFrame(byteArrayOf()))
                owner.offerScaleVisualInput(clockOwner,DeviceState.READY,sample)
                check(owner.snapshot.scaleObservation?.receivedAtMs==initialMonotonicMs)
            }
        }
        fun advanceTo(elapsed: Long) {
            main {
                val target=initialMonotonicMs+elapsed
                check(target>MobileMonotonicClock.elapsedRealtime())
                MobileMonotonicClock.advanceForMock(clockOwner,target)
                logicalInputTimes.add(target)
            }
        }
        fun startScenarioShot(owner: MobileService, case: String): String {
            var id=""
            main {
                check(scenarioInstalled && owner===service && owner.shotState==ExtractionState.IDLE)
                val profile=requireNotNull(curves.resolve(curveId,owner.snapshot.scaleState==DeviceState.READY,7))
                check(curves.validated(profile) && profile.targetHundredthsGram==0)
                check(owner.startShot(curveId,profile.scaleMode,7)==null)
                id=requireNotNull(owner.chartTraceIdentity)
                check(id==extractionScenarioShotIds.getValue(case) && owner.shotState==ExtractionState.STARTING)
                check(owner.chartPoints.isEmpty() && owner.mockExtractionPendingStartId!=null)
            }
            return id
        }
        fun completeScenarioStart(owner: MobileService) {
            main {
                val request=requireNotNull(owner.mockExtractionPendingStartId)
                owner.completeMockExtractionStart(clockOwner,request,OperationResult.Success())
                check(owner.shotState==ExtractionState.RUNNING && owner.mockExtractionPendingStartId==null)
            }
        }
        fun offerScenarioFrame(owner: MobileService, elapsed: Long, water: Int, slot: Int=8): JSONObject {
            lateinit var record: JSONObject
            main {
                val before=owner.chartPoints.size;val serial=owner.mockExtractionOfferedFrameSerial
                val target=initialMonotonicMs+elapsed
                check(target>MobileMonotonicClock.elapsedRealtime())
                MobileMonotonicClock.advanceForMock(clockOwner,target)
                logicalInputTimes.add(target)
                val frame=ExtractionTelemetry(slot,(elapsed/1000).toInt(),90,water,9300,100,64,0,ByteFrame(byteArrayOf()))
                owner.offerMockExtractionFrame(clockOwner,frame)
                check(owner.mockExtractionOfferedFrameSerial==serial+1)
                record=JSONObject().put("serial",serial+1).put("ownedMonotonicMs",MobileMonotonicClock.elapsedRealtime())
                    .put("elapsedMs",elapsed).put("waterTenthsMl",water).put("pointsBefore",before)
                    .put("pointsAfter",owner.chartPoints.size).put("actualNewPoints",owner.chartPoints.size-before)
            }
            return record
        }
        fun verifyExtraction(activity: Activity, captureStartedAtRealMs: Long,captureFinishedAtRealMs: Long) {
            main {
                val owner=requireNotNull(service)
                verifyServiceLazyIsolation(owner)
                check(field(activity,"service")===owner && field(activity,"bound")==true && field(activity,"visible")==true)
                check(owner.shotState==ExtractionState.RUNNING && owner.chartTraceIdentity=="m5-live-shot")
                check(MobileMonotonicClock.elapsedRealtime()==initialMonotonicMs+24600 && owner.snapshot.coffeeAt==MobileMonotonicClock.elapsedRealtime())
                val chart=field(activity,"chart") as ExtractionOverlayView
                check(chart.traceIdentity==owner.chartTraceIdentity && chart.points==ExtractionReference.align(owner.chartPoints).points && chart.reference.isEmpty())
                check(chart.visualEvidence.length()>0) { "M5 live main chart must have actual Canvas evidence" }
                val stop=root(activity).findViewWithTag<View>("product/extraction/stop")
                check(stop.isEnabled && stop.isShown && stop.height>=HoyiUi.dp(activity,56) && whole(observation(stop))) { "M5 actual running stop action not complete/fixed" }
                listOf("product/extraction/elapsed/number","product/extraction/quantity/number").forEach { tag ->
                    val value=requireNotNull(root(activity).findViewWithTag<TextView>(tag)); check(value.text.toString()!="—" && whole(observation(value)))
                }
                File(directory,"extraction-business.json").writeText(JSONObject().put("actualMockStart",true).put("running",true)
                    .put("lastSampleElapsedMs",owner.chartPoints.last().elapsedMs).put("samples",pointRecords(owner.chartPoints))
                    .put("sameMonotonicDomain",true).put("clockMonotonicMs",MobileMonotonicClock.elapsedRealtime())
                    .put("realSystemClockEvidenceDeadline",true).put("captureStartedAtSystemElapsedMs",captureStartedAtRealMs)
                    .put("captureFinishedAtSystemElapsedMs",captureFinishedAtRealMs).put("frameProtocol","NativeWindowFrameEvidence_protocol2_unmodified_real_SystemClock_5000ms_zero_difference")
                    .put("originalRecoveryAndCredentialsUntouched",true).put("activeTraceIdentity",owner.chartTraceIdentity)
                    .put("scaleMeaning","scale_reading_not_tared_cup_mass").put("completed",true).toString(2))
            }
        }
        fun closeActivities() {
            main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
            await("all current scoped pages/dialogs closed before next saved-record case") {
                tracked.all { it.isDestroyed && it in destroyed } && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()
            }
            main { check(services().isEmpty()) { "M5 historical special states may not inherit a service" } }
        }
        fun activeActivity(): Activity? = tracked.lastOrNull { !it.isDestroyed }
        fun cleanup() {
            val proof = JSONObject().put("scopedOwnedDirectory",ownedDirectory.absolutePath)
                .put("noBackupFilesDir",context.noBackupFilesDir.canonicalPath)
                .put("businessDiskRoots",JSONArray(listOf("files","shared_prefs","databases",context.noBackupFilesDir.canonicalPath)))
            var cleanupFailure: Throwable? = null
            try {
                if (installed) {
                    main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
                    await("all fixture Activities and dialogs destroyed") {
                        tracked.all { it.isDestroyed && it in destroyed } && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()
                    }
                    if(page=="extraction") {
                        main {
                            tracked.filterIsInstance<ExtractionActivity>().forEach { activity ->
                                val handler=field(activity,"handler") as Handler
                                check(field(activity,"service")==null && field(activity,"bound")==false && field(activity,"visible")==false &&
                                    !handler.hasCallbacks(field(activity,"refresh") as Runnable)) { "M5 destroyed Extraction still holds a service or refresh" }
                            }
                            if(serviceBound) { context.unbindService(serviceConnection); serviceBound=false }
                            val stopped=context.stopService(Intent(context,MobileService::class.java))
                            if(serviceStarted) check(stopped) { "M5 started scoped service stop rejected" }
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
                            check((field(app.visibility,"observers") as Map<*,*>)==originalVisibilityObservers) { "M5 original visibility observers changed" }
                            service?.let { owner ->
                                verifyServiceLazyIsolation(owner)
                                if(scenarioInstalled) {
                                    owner.clearMockExtractionScenario(clockOwner)
                                    scenarioInstalled=false
                                }
                                if(scaleInputInstalled) {
                                    owner.clearScaleVisualInput(clockOwner)
                                    scaleInputInstalled=false
                                }
                                proof.put("serviceOwnerId",serviceOwnerId).put("serviceOwnerCallbacksRemoved",true)
                                    .put("serviceHubClosed",field(owner,"hub")==null).put("serviceHandlersDrained",true)
                            }
                        }
                        proof.put("scopedServiceDestroyed",true).put("extractionActivityUnboundAndRefreshRemoved",true)
                            .put("originalVisibilityObserversPreserved",true)
                    } else main { check(services().isEmpty()) { "M5 history-only scope unexpectedly holds a service; original delegates retained" } }
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) { "M5 fixture dialog/window remains after Activity destruction" }
                    // Activities close before any original Lazy is restored. Raw detail readers belong to
                    // this fresh scope (none existed before install); comparison pools are per actual instance.
                    val readerDeadline=SystemClock.elapsedRealtime()+10000
                    while(true) {
                        val active=detailReaders(); if(active.isEmpty()) break
                        active.forEach { reader -> reader.join((readerDeadline-SystemClock.elapsedRealtime()).coerceAtLeast(1)) }
                        check(SystemClock.elapsedRealtime()<readerDeadline) { "M5 detail reader did not terminate; original delegates retained" }
                    }
                    comparisonReaders.forEach { (activity,reader) ->
                        check(activity.isDestroyed && reader.isShutdown && reader.awaitTermination(10,TimeUnit.SECONDS)) {
                            "M5 comparison reader pool not terminated; original delegates retained" }
                    }
                    main { check(detailReaders().isEmpty() && services().isEmpty()) }
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("historyDetailReadersTerminated",true).put("comparisonReaderPoolsTerminated",true)
                        .put("noServiceBeforeOriginalDelegateRestore",true).put("mainQueueBarrierAfterReaders",true)
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
                if(installed && page=="extraction") main {
                    // Only after all pages, service callbacks, readers and writers have drained.
                    service?.let { owner ->
                        verifyServiceLazyIsolation(owner)
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
                        val keys = if (name == "appearance") listOf("dark") else listOf("selected")
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
                        .put("whitePrefPhysicalBytesMayDiffer","only appearance.dark/curves.selected typed restoration; no file deletion or byte rollback")
                }
                proof.put("status","PASS_ISOLATION_ONLY")
            } catch (failure: Throwable) {
                cleanupFailure=failure
                try {
                    proof.put("status","FAIL").put("failure",failure.stackTraceToString())
                } catch (reportFailure: Throwable) {
                    failure.addSuppressed(reportFailure)
                }
                // Do not restore original delegates while a scoped Activity/service/writer may still hold references.
                throw failure
            } finally {
                try {
                    proof.put("sampleFailureScopeCase",directory.name).put("sampleFailureOwnedScope",ownedDirectory.absolutePath)
                    proof.put("sampleFailures",JSONArray(sampleFailures.map { error -> JSONObject()
                        .put("exceptionClass",error.javaClass.name).put("message",error.message ?: JSONObject.NULL)
                        .put("stackTrace",error.stackTraceToString())
                        .put("causeClass",error.cause?.javaClass?.name ?: JSONObject.NULL)
                        .put("causeMessage",error.cause?.message ?: JSONObject.NULL)
                        .put("suppressed",JSONArray(error.suppressed.map {it.stackTraceToString()})) }))
                    File(directory,"fixture-isolation.json").writeText(proof.toString(2))
                } catch (reportFailure: Throwable) {
                    val original=cleanupFailure
                    if(original==null) throw reportFailure else original.addSuppressed(reportFailure)
                }
            }
        }
    }
    /** Keep raw hierarchy coordinates; derive a screen rect from measured origins, not clipped edges. */
    private fun historyVisibility(view: View): JSONObject {
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
    /** Embedded History only: copy the existing producer budget and actual Canvas geometry, not estimates. */
    private fun embeddedHistoryEvidence(activity: Activity): JSONObject? {
        val preview=root(activity).findViewWithTag<View>("product/history/preview") ?: return null
        val chart=requireNotNull(root(activity).findViewWithTag<ShotChartView>("product/history/chart"))
        val plot=requireNotNull(chart.findViewWithTag<View>("chart-plot"))
        val explanation=requireNotNull(chart.embeddedExplanationView)
        val producer=chart.getChildAt(0) as ExtractionOverlayView
        val content=chart.parent as ViewGroup
        val budget=JSONObject((field(preview,"productLayoutEvidence") as JSONObject).toString())
        val plotOrigin=IntArray(2);plot.getLocationOnScreen(plotOrigin)
        val visible=historyVisibility(plot).getJSONArray("visibleRectPx")
        val viewport=android.graphics.RectF(visible.getDouble(0).toFloat(),visible.getDouble(1).toFloat(),visible.getDouble(2).toFloat(),visible.getDouble(3).toFloat())
        val operations=(0 until chart.visualEvidence.length()).map {chart.visualEvidence.getJSONObject(it)}
        val drawing=operations.singleOrNull {it.optString("operation")=="plot"}
        val plotBox=drawing?.getJSONArray("boundsPx")?.let {box -> android.graphics.RectF(
            box.getDouble(0).toFloat()+plotOrigin[0],box.getDouble(1).toFloat()+plotOrigin[1],
            box.getDouble(2).toFloat()+plotOrigin[0],box.getDouble(3).toFloat()+plotOrigin[1])}
        val visiblePlot=plotBox?.let {box -> android.graphics.RectF(box).apply {if(!intersect(viewport))setEmpty()} }
        val inks=operations.filter {it.optString("operation")=="text" &&
            it.optString("role") in setOf("axis-unit","axis-tick","time-tick","time-unit")}.map {operation ->
            val raw=operation.getJSONArray("glyphBoundsPx")
            val ink=android.graphics.RectF(raw.getDouble(0).toFloat()+plotOrigin[0],raw.getDouble(1).toFloat()+plotOrigin[1],
                raw.getDouble(2).toFloat()+plotOrigin[0],raw.getDouble(3).toFloat()+plotOrigin[1])
            JSONObject().put("role",operation.getString("role")).put("text",operation.getString("text"))
                .put("glyphScreenBoundsPx",JSONArray(listOf(ink.left,ink.top,ink.right,ink.bottom)))
                .put("completeInVisiblePlotView",ink.left>=viewport.left && ink.top>=viewport.top && ink.right<=viewport.right && ink.bottom<=viewport.bottom)
        }
        val projected=field(producer,"data") as FourMetricChartData
        val sampling=projected.observedThroughMs?.let {activity.getString(R.string.task_chart_summary_observed,
            String.format(java.util.Locale.ROOT,"%.1f",it/1000.0))} ?: activity.getString(R.string.task_chart_summary_waiting)
        val legend=requireNotNull(chart.findViewWithTag<View>("product/history/chart-legend"))
        val warning=root(activity).findViewWithTag<TextView>("product/history/warning")
        return JSONObject().put("budget",budget).put("budgetSource","deep_copy_existing_EmbeddedHistoryPreview.productLayoutEvidence")
            .put("chart",historyVisibility(chart)).put("plotView",historyVisibility(plot)).put("explanation",observation(explanation))
            .put("explanationScreenVisibility",historyVisibility(explanation)).put("explanationText",explanation.text.toString())
            .put("samePermanentProducerView",producer.embeddedExplanationView===explanation)
            .put("externalSiblingAfterChart",explanation.parent===content && content.indexOfChild(explanation)==content.indexOfChild(chart)+1)
            .put("sameProjectionSamplingDescription",explanation.text.toString().contains(sampling))
            .put("warning",warning?.let { observation(it) } ?: JSONObject.NULL)
            .put("warningScreenVisibility",warning?.let { historyVisibility(it) } ?: JSONObject.NULL)
            .put("warningText",warning?.text?.toString() ?: JSONObject.NULL)
            .put("legend",historyVisibility(legend)).put("legendActions",JSONArray(nodes(legend).filterIsInstance<Button>().filter {it.isShown}.map {
                JSONObject().put("observation",observation(it)).put("screenVisibility",historyVisibility(it)).put("heightPx",it.height)
            })).put("axisInk",JSONArray(inks)).put("actualCanvas",JSONArray(chart.visualEvidence.toString())).put("actualSampleCount",chart.points.size)
            .put("actualPlotHeightDp",plotBox?.let {it.height()/activity.resources.displayMetrics.density} ?: JSONObject.NULL)
            .put("initialVisiblePlotHeightDp",visiblePlot?.let {it.height()/activity.resources.displayMetrics.density} ?: JSONObject.NULL)
    }
    private fun requireEmbeddedHistoryInitial(activity: Activity, folder: File, evidence:JSONObject,
        declaredCurrent:List<ShotPoint>,declaredReference:List<ShotPoint>,emptyExpected:Boolean=false) {
        val density=activity.resources.displayMetrics.density
        File(folder,"history-initial-chart.json").writeText(evidence.toString(2))
        val chart=nodes(root(activity)).filterIsInstance<ShotChartView>().single()
        val overlay=nodes(chart).filterIsInstance<ExtractionOverlayView>().single()
        check(chart.points==declaredCurrent && overlay.reference==declaredReference)
        check(evidence.getBoolean("samePermanentProducerView") && evidence.getBoolean("externalSiblingAfterChart") &&
            evidence.getBoolean("sameProjectionSamplingDescription"))
        if(evidence.isNull("actualPlotHeightDp")) {
            check(emptyExpected && evidence.getInt("actualSampleCount")==0) {"M5 nonempty History has no Canvas plot"}
            check((0 until evidence.getJSONArray("actualCanvas").length()).none {evidence.getJSONArray("actualCanvas").getJSONObject(it).optString("operation")=="path"})
        }
        else check(evidence.getDouble("actualPlotHeightDp")>=96.0 && evidence.getDouble("initialVisiblePlotHeightDp")>=96.0)
        val budget=evidence.getJSONObject("budget")
        check(budget.getInt("chartLegendNaturalPx")>0 && budget.getInt("externalExplanationNaturalPx")>0 &&
            budget.getString("explanationPlacement")=="same_permanent_producer_view_after_complete_chart_before_notes")
        val widthFits=!evidence.isNull("warning") && budget.getInt("titleNaturalWidthPx")+
            budget.getInt("titleWarningGapPx")+budget.getInt("warningNaturalWidthPx")<=budget.getInt("titleWarningRowAvailableWidthPx")
        val shouldInline=widthFits && budget.getInt("firstScreenMinimumBeforePx")>budget.getInt("contentViewportPx")
        check(budget.getBoolean("titleWarningWidthFits")==widthFits &&
            budget.getBoolean("titleWarningInlineCondition")==shouldInline &&
            budget.getBoolean("titleWarningInlineResolved")==shouldInline &&
            budget.getBoolean("firstScreenBudgetSatisfiable")==
                (budget.getInt("firstScreenMinimumResolvedPx")<=budget.getInt("contentViewportPx")))
        if(!evidence.isNull("warning")) check(evidence.getString("warningText")==activity.getString(R.string.history_unknown_warning) &&
            evidence.getJSONObject("warningScreenVisibility").getBoolean("complete") && whole(evidence.getJSONObject("warning")))
        val hasWeight=(declaredCurrent+declaredReference).any {it.weightHundredthsGram!=null}
        val expectedLegend=listOf("chart-metric:PRESSURE","chart-metric:WATER")+
            (if(hasWeight)listOf("chart-metric:WEIGHT") else emptyList())+"chart-metric:TEMPERATURE"
        val actions=evidence.getJSONArray("legendActions")
        check((0 until actions.length()).map {actions.getJSONObject(it).getJSONObject("observation").getString("semanticId")}==expectedLegend)
        for(index in 0 until actions.length()) {
            val value=actions.getJSONObject(index)
            check(value.getInt("heightPx")>=(48*density).toInt())
            check(value.getJSONObject("screenVisibility").getBoolean("complete") && whole(value.getJSONObject("observation")))
        }
        if(activity.resources.configuration.fontScale==1f && !evidence.isNull("actualPlotHeightDp")) {
            check(evidence.getJSONObject("chart").getBoolean("complete")) {"M5 standard History initial chart/time axis is not wholly visible; no pre-scroll accepted"}
            val inks=evidence.getJSONArray("axisInk");check(inks.length()>0)
            for(index in 0 until inks.length())check(inks.getJSONObject(index).getBoolean("completeInVisiblePlotView")) {
                "M5 standard History initial actual axis glyph is clipped"}
        }
    }
    private fun historyPreviewReachability(activity: Activity, scope:Scope, folder:File, emptyExpected:Boolean=false) {
        val scroll=requireNotNull(root(activity).findViewWithTag<ScrollView>("product/history/preview-scroll"))
        val chart=requireNotNull(root(activity).findViewWithTag<ShotChartView>("product/history/chart"))
        val explanation=requireNotNull(chart.embeddedExplanationView)
        val saved=scroll.scrollY;check(saved==0) {"M5 fresh initial History preview must not start pre-scrolled"}
        val nativeBefore=scope.journal.exportJson();val reached=JSONArray()
        fun fixedActions() {
            listOf("product/history/review" to 56,"product/history/full-page" to 48).forEach {(tag,minimum) ->
                val action=requireNotNull(root(activity).findViewWithTag<Button>(tag))
                check(action.height>=HoyiUi.dp(activity,minimum) && historyVisibility(action).getBoolean("complete") && whole(observation(action)))
            }
        }
        fun reach(view:View, name:String) {
            main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
            frames.committedFrame(root(activity))
            capture(activity,File(folder,"states/history-preview-$name-reachable"))
            main {
                check(historyVisibility(view).getBoolean("complete"));fixedActions()
                if(view is TextView)check(whole(observation(view))) {"M5 full explanation/note text cannot be reached"}
                if(view===chart) {
                    val evidence=requireNotNull(embeddedHistoryEvidence(activity));val inks=evidence.getJSONArray("axisInk")
                    if(evidence.isNull("actualPlotHeightDp"))check(emptyExpected && chart.points.isEmpty())
                    else {
                        check(evidence.getDouble("actualPlotHeightDp")>=96.0 && inks.length()>0)
                        for(index in 0 until inks.length())check(inks.getJSONObject(index).getBoolean("completeInVisiblePlotView"))
                    }
                }
                reached.put(JSONObject().put("target",name).put("visibility",historyVisibility(view)).put("scrollY",scroll.scrollY))
            }
        }
        try {
            reach(chart,"chart")
            reach(explanation,"explanation")
            val notes=nodes(root(activity)).filterIsInstance<TextView>().filter {it.tag=="full-text-details" && it.isShown}
            check(notes.size==2)
            notes.forEachIndexed {index,note ->reach(note,"note-$index")}
        } finally {
            main {scroll.scrollTo(0,saved)};frames.committedFrame(root(activity))
            main {check(scroll.scrollY==saved);fixedActions();check(chart.embeddedExplanationView===explanation)}
        }
        check(scope.journal.exportJson()==nativeBefore)
        capture(activity,File(folder,"states/history-preview-restored"))
        File(folder,"history-preview-reachability.json").writeText(JSONObject().put("targets",reached).put("initialScrollY",saved)
            .put("restoredScrollY",scroll.scrollY).put("exactScrollRestored",true).put("permanentExplanationIdentityPreserved",true)
            .put("readOnlyJournalUnchanged",true).put("completed",true).toString(2))
    }
    private fun screenObservation(view:View):JSONObject {
        val result=observation(view).put("complete",historyVisibility(view).getBoolean("complete"))
        val texts=nodes(view).filterIsInstance<TextView>()
        val lines=result.getJSONArray("textLineBoxes")
        check(texts.size==lines.length())
        texts.forEachIndexed {index,text -> lines.getJSONObject(index).put("fullViewVisible",historyVisibility(text).getBoolean("complete")) }
        return result
    }
    private fun standaloneHistoryEvidence(activity:Activity):JSONObject? {
        val budgetView=root(activity).findViewWithTag<View>("product/history/standalone-plot-budget") ?: return null
        val chart=requireNotNull(root(activity).findViewWithTag<ShotChartView>("product/history/chart"))
        val plot=requireNotNull(chart.findViewWithTag<View>("chart-plot"))
        val visibility=historyVisibility(plot)
        val clipped=visibility.getJSONArray("visibleRectPx")
        val clip=android.graphics.RectF(clipped.getDouble(0).toFloat(),clipped.getDouble(1).toFloat(),clipped.getDouble(2).toFloat(),clipped.getDouble(3).toFloat())
        val origin=IntArray(2);plot.getLocationOnScreen(origin)
        val operations=(0 until chart.visualEvidence.length()).map {chart.visualEvidence.getJSONObject(it)}
        val drawing=operations.singleOrNull {it.optString("operation")=="plot"}
        val bounds=drawing?.getJSONArray("boundsPx")?.let {b -> android.graphics.RectF(
            b.getDouble(0).toFloat()+origin[0],b.getDouble(1).toFloat()+origin[1],
            b.getDouble(2).toFloat()+origin[0],b.getDouble(3).toFloat()+origin[1]) }
        val visible=bounds?.let {android.graphics.RectF(it).apply {if(!intersect(clip))setEmpty()} }
        return JSONObject().put("budget",JSONObject((field(budgetView,"productLayoutEvidence") as JSONObject).toString()))
            .put("budgetSource","deep_copy_existing_StandaloneHistoryPlot.productLayoutEvidence")
            .put("plotView",visibility).put("actualCanvas",JSONArray(chart.visualEvidence.toString()))
            .put("emptySamples",chart.points.isEmpty()).put("actualSampleCount",chart.points.size)
            .put("plotPresent",bounds!=null).put("actualPlotHeightDp",bounds?.height()?.div(activity.resources.displayMetrics.density) ?: JSONObject.NULL)
            .put("visiblePlotHeightDp",visible?.height()?.div(activity.resources.displayMetrics.density) ?: JSONObject.NULL)
            .put("plotBoundsScreenPx",bounds?.let {JSONArray(listOf(it.left,it.top,it.right,it.bottom))} ?: JSONObject.NULL)
            .put("visiblePlotBoundsScreenPx",visible?.let {JSONArray(listOf(it.left,it.top,it.right,it.bottom))} ?: JSONObject.NULL)
    }
    private fun requireStandaloneHistoryPlot(activity:Activity,folder:File,evidence:JSONObject,emptyExpected:Boolean) {
        File(folder,"standalone-history-plot.json").writeText(evidence.toString(2))
        check(evidence.getBoolean("emptySamples")==emptyExpected)
        check(evidence.getJSONObject("budget").getInt("minimumPlotDp")==96)
        if(!evidence.getBoolean("plotPresent")) {
            check(emptyExpected && evidence.getInt("actualSampleCount")==0) {"M5 nonempty Detail has no actual Canvas plot"}
            check((0 until evidence.getJSONArray("actualCanvas").length()).none {
                evidence.getJSONArray("actualCanvas").getJSONObject(it).optString("operation")=="path" })
        } else {
            check(evidence.getJSONObject("plotView").getBoolean("screenMappingVerified"))
            check(evidence.getDouble("actualPlotHeightDp")>=96.0 && evidence.getDouble("visiblePlotHeightDp")>=96.0) {
                "M5 Detail real Canvas plot / visible intersection <96dp: $evidence"}
        }
    }
    private fun geometry(activity: Activity): JSONObject {
        val content=root(activity); val origin=IntArray(2); content.getLocationOnScreen(origin)
        val insets=requireNotNull(content.rootWindowInsets); val bars=insets.getInsets(WindowInsets.Type.systemBars())
        val config=activity.resources.configuration
        val charts=nodes(content).filter { it is ShotChartView || it is TwoCupChartView || it is ExtractionOverlayView }
        return JSONObject().apply { if(activity is HistoryActivity) {
            put("embeddedHistoryPreview",embeddedHistoryEvidence(activity) ?: JSONObject.NULL)
        }
            if(activity is HistoryDetailActivity)put("standaloneHistoryPlot",standaloneHistoryEvidence(activity) ?: JSONObject.NULL)
        }.put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("appOriginPx",JSONArray(origin.toList())).put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("systemInsetsPx",JSONArray(listOf(bars.left,bars.top,bars.right,bars.bottom)))
            .put("imeVisible",insets.isVisible(WindowInsets.Type.ime())).put("densityDpi",config.densityDpi)
            .put("fontScale",config.fontScale).put("locale",config.locales.toLanguageTags())
            .put("uiModeNightMeaning",when(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> "dark"
                android.content.res.Configuration.UI_MODE_NIGHT_NO -> "light"; else -> "undefined"
            }).put("nodes",JSONArray(nodes(content).filter { it.tag is String || it is TextView }.map(::observation)))
            .put("charts",JSONArray(charts.map { chart ->
                val evidence=when(chart) { is ShotChartView->chart.visualEvidence; is TwoCupChartView->chart.visualEvidence
                    is ExtractionOverlayView->chart.visualEvidence; else->error("Unrecognized chart") }
                JSONObject().put("class",chart.javaClass.simpleName).put("rect",observation(chart)).put("canvasEvidence",evidence)
            }))
    }
    private fun start(type: Class<out Activity>, extras: (Intent)->Unit = {}): Activity {
        val intent=Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); extras(intent)
        return test.startActivitySync(intent).also { activity -> await("${type.simpleName} ready") {
            activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested } }
    }
    private fun recreate(activity: Activity): Activity {
        val monitor=test.addMonitor(activity.javaClass.name,null,false)
        try {
            main { activity.recreate() }
            return (test.waitForMonitorWithTimeout(monitor,10000) ?: error("M5 real recreation missing")).also { replacement ->
                await("recreated page ready") { replacement.hasWindowFocus() && root(replacement).isLaidOut && !root(replacement).isLayoutRequested }
            }
        } finally { test.removeMonitor(monitor) }
    }
    private fun chartReady(activity: Activity) {
        await("actual asynchronous saved samples displayed") {
            val chart=nodes(root(activity)).filterIsInstance<ShotChartView>().firstOrNull()
            val comparison=nodes(root(activity)).filterIsInstance<TwoCupChartView>().firstOrNull()
            (chart!=null && chart.points.isNotEmpty() || comparison!=null && comparison.firstPoints.isNotEmpty() && comparison.secondPoints.isNotEmpty()) &&
                !root(activity).isLayoutRequested
        }
    }
    private fun diagnostic(activity: Activity, folder: File) {
        check(folder.isDirectory || folder.mkdirs())
        val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
        try { File(folder,"window.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } }
        finally { bitmap.recycle() }
        main { WindowProofStreamingWriter.writeStrict(geometry(activity).put("sameCompositorFrameProven",false)
            .put("protocol","unsynchronized_failure_diagnostic_not_protocol2_acceptance"),File(folder,"geometry.json")) }
    }
    private fun baselineView(view: TextView?): JSONObject {
        if (view == null) return JSONObject().put("present", false).put("valid", false)
            .put("invalidReason", "missing_unit_view")
        val attached=view.isAttachedToWindow; val shown=view.isShown; val requested=view.isLayoutRequested
        val layoutPresent=view.layout!=null; val local=view.baseline
        val origin=IntArray(2); if(attached) view.getLocationOnScreen(origin)
        val valid=attached && shown && layoutPresent && !requested && local>=0
        val reason=when {
            !attached -> "detached_view"
            !shown -> "not_shown"
            !layoutPresent -> "text_layout_not_built"
            requested -> "view_layout_requested"
            local<0 -> "negative_local_baseline"
            else -> null
        }
        return JSONObject().put("present",true).put("identity",System.identityHashCode(view))
            .put("text",view.text.toString()).put("attached",attached).put("isShown",shown)
            .put("layoutPresent",layoutPresent).put("viewLayoutRequested",requested)
            .put("originOnScreenPx",if(attached) JSONArray(origin.toList()) else JSONObject.NULL)
            .put("localBaselinePx",local)
            .put("screenBaselinePx",if(valid) origin[1]+local else JSONObject.NULL)
            .put("valid",valid).put("invalidReason",reason ?: JSONObject.NULL)
    }
    private fun baselinePairs(activity: Activity): JSONArray {
        val content=root(activity); val pairs=JSONArray()
        nodes(content).filterIsInstance<TextView>().filter { (it.tag as? String)?.endsWith("/number")==true && it.isShown }
            .forEach { number ->
                val prefix=(number.tag as String).removeSuffix("/number")
                val unit=content.findViewWithTag<TextView>("$prefix/unit")
                if(unit==null || !unit.isShown) return@forEach
                val numberEvidence=baselineView(number); val unitEvidence=baselineView(unit)
                val valid=numberEvidence.getBoolean("valid") && unitEvidence.getBoolean("valid")
                val exact=valid && numberEvidence.getInt("screenBaselinePx")==unitEvidence.getInt("screenBaselinePx")
                pairs.put(JSONObject().put("semanticId",prefix)
                    .put("number",numberEvidence).put("unit",unitEvidence)
                    .put("numberBaselineOnScreenPx",if(valid) numberEvidence.getInt("screenBaselinePx") else JSONObject.NULL)
                    .put("unitBaselineOnScreenPx",if(valid) unitEvidence.getInt("screenBaselinePx") else JSONObject.NULL)
                    .put("status",when { !valid -> "FAIL_INVALID_BASELINE"; exact -> "PASS_EXACT_BASELINE_ONLY"; else -> "FAIL_EXACT_BASELINE_MISMATCH" }))
            }
        return pairs
    }
    private fun captureTarget(activity: Activity): JSONObject {
        val pairs=baselinePairs(activity)
        return geometry(activity).put("typographyEvidence",TaskTypographyEvidence.snapshot(activity))
            .put("baselinePairs",pairs).put("baselinePairCount",pairs.length())
            .put("baselinePairIds",JSONArray((0 until pairs.length()).map {pairs.getJSONObject(it).getString("semanticId")}))
    }
    private fun capture(activity: Activity, folder: File): NativeWindowFrameEvidence.CapturedFrame {
        check(folder.isDirectory || folder.mkdirs())
        main { TaskTypographyEvidence.verify(activity) }
        val result=frames.screenshot(activity,root(activity),File(folder,"window.png")) { captureTarget(activity) }
        val target=result.target; val pairs=target.getJSONArray("baselinePairs")
        WindowProofStreamingWriter.writeStrict(target,File(folder,"geometry.json"))
        WindowProofStreamingWriter.writeStrict(target.getJSONObject("typographyEvidence"),
            File(folder,"${activity.javaClass.simpleName}-typography.json"))
        File(folder,"baseline-alignment.json").writeText(pairs.toString(2))
        check(target.getInt("baselinePairCount")==pairs.length() &&
            target.getJSONArray("baselinePairIds").length()==pairs.length()) {"M5 baseline pair count/ID mismatch"}
        for(index in 0 until pairs.length()) {
            val pair=pairs.getJSONObject(index); val id=pair.getString("semanticId")
            check(target.getJSONArray("baselinePairIds").getString(index)==id) {"M5 baseline pair ID mismatch: $id"}
            check(pair.getJSONObject("number").getBoolean("valid") && pair.getJSONObject("unit").getBoolean("valid")) {
                "M5 actual number/unit baseline invalid: $id ${pair.getJSONObject("number").optString("invalidReason")} / ${pair.getJSONObject("unit").optString("invalidReason")}" }
            check(pair.getInt("numberBaselineOnScreenPx")==pair.getInt("unitBaselineOnScreenPx") &&
                pair.getString("status")=="PASS_EXACT_BASELINE_ONLY") {"M5 actual number/unit baseline mismatch: $id"}
        }
        check(result.proof.getInt("protocolVersion")==2 && result.proof.getBoolean("accepted"))
        return result
    }
    private fun requireExtractionPlot(activity: Activity, folder: File, frame: NativeWindowFrameEvidence.CapturedFrame): JSONObject {
        val chart=requireNotNull(root(activity).findViewWithTag<ExtractionOverlayView>("product/extraction/chart"))
        val plot=requireNotNull(chart.findViewWithTag<View>("chart-plot"))
        val visibility=historyVisibility(plot)
        check(visibility.getBoolean("screenMappingVerified")) {"M5 Extraction plot screen mapping failed"}
        val rect=visibility.getJSONArray("visibleRectPx")
        val viewport=android.graphics.RectF(rect.getDouble(0).toFloat(),rect.getDouble(1).toFloat(),
            rect.getDouble(2).toFloat(),rect.getDouble(3).toFloat())
        val origin=IntArray(2);plot.getLocationOnScreen(origin)
        val charts=frame.target.getJSONArray("charts")
        val canvas=(0 until charts.length()).map {charts.getJSONObject(it)}
            .single {it.getString("class")=="ExtractionOverlayView"}.getJSONArray("canvasEvidence")
        val operations=(0 until canvas.length()).map {canvas.getJSONObject(it)}
        val drawing=operations.single {it.optString("operation")=="plot"}.getJSONArray("boundsPx")
        val bounds=android.graphics.RectF(drawing.getDouble(0).toFloat()+origin[0],drawing.getDouble(1).toFloat()+origin[1],
            drawing.getDouble(2).toFloat()+origin[0],drawing.getDouble(3).toFloat()+origin[1])
        val visible=android.graphics.RectF(bounds).apply {if(!intersect(viewport))setEmpty()}
        val inks=operations.filter {it.optString("operation")=="text" &&
            it.optString("role") in setOf("axis-unit","axis-tick","time-tick","time-unit")}.map {operation ->
            val raw=operation.getJSONArray("glyphBoundsPx")
            val ink=android.graphics.RectF(raw.getDouble(0).toFloat()+origin[0],raw.getDouble(1).toFloat()+origin[1],
                raw.getDouble(2).toFloat()+origin[0],raw.getDouble(3).toFloat()+origin[1])
            JSONObject().put("role",operation.getString("role")).put("text",operation.getString("text"))
                .put("glyphScreenBoundsPx",JSONArray(listOf(ink.left,ink.top,ink.right,ink.bottom)))
                .put("completeInVisiblePlotView",ink.left>=viewport.left && ink.top>=viewport.top &&
                    ink.right<=viewport.right && ink.bottom<=viewport.bottom)
        }
        val density=activity.resources.displayMetrics.density
        val evidence=JSONObject().put("frameSync",frame.proof).put("plotView",visibility)
            .put("canvasPlotBoundsScreenPx",JSONArray(listOf(bounds.left,bounds.top,bounds.right,bounds.bottom)))
            .put("visibleCanvasPlotBoundsScreenPx",JSONArray(listOf(visible.left,visible.top,visible.right,visible.bottom)))
            .put("actualPlotHeightDp",bounds.height()/density).put("visiblePlotHeightDp",visible.height()/density)
            .put("axisInk",JSONArray(inks)).put("actualSampleCount",chart.points.size)
        WindowProofStreamingWriter.writeStrict(evidence,File(folder,"extraction-plot-budget.json"))
        check(bounds.height()/density>=96f && visible.height()/density>=96f) {
            "M5 Extraction real Canvas plot / visible intersection <96dp: $evidence" }
        check(inks.isNotEmpty() && inks.all {it.getBoolean("completeInVisiblePlotView")}) {
            "M5 Extraction axis glyph clipped: $evidence" }
        return evidence
    }
    private fun extractionChartHeaderReachability(activity: Activity, scope: Scope, folder: File,
        initial: NativeWindowFrameEvidence.CapturedFrame): JSONObject {
        val chart=requireNotNull(root(activity).findViewWithTag<ExtractionOverlayView>("product/extraction/chart"))
        val header=requireNotNull(chart.findViewWithTag<ScrollView>("product/extraction/chart-header"))
        val legend=field(chart,"legend") as View
        val explanation=field(chart,"explanation") as TextView
        val caption=field(chart,"referenceCaption") as TextView
        val targets=nodes(legend).filterIsInstance<Button>().filter {it.visibility==View.VISIBLE}.map {button ->
            val metric=(button.tag as String).removePrefix("chart-metric:").lowercase(java.util.Locale.ROOT)
            "legend-$metric" to (button as View)
        } + listOf("explanation" to (explanation as View)) +
            (if(caption.visibility==View.VISIBLE && caption.text.isNotEmpty())
                listOf("reference-caption" to (caption as View)) else emptyList())
        check(targets.size in 4..6 && targets.map {it.first}.distinct().size==targets.size)
        val savedX=header.scrollX;val savedY=header.scrollY
        val owner=requireNotNull(field(activity,"service") as? MobileService)
        val support=requireNotNull(root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll"))
        val start=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/start"))
        val stop=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/stop"))
        val density=activity.resources.displayMetrics.density
        fun fixedAction(): JSONObject {
            check(root(activity).findViewWithTag<Button>("product/extraction/start")===start &&
                root(activity).findViewWithTag<Button>("product/extraction/stop")===stop)
            val expected=StopActionPresentation.describe(owner.shotState,owner.snapshot.coffeeState,owner.running)
            check(stop.isEnabled==expected.enabled &&
                (stop.visibility==View.VISIBLE)==expected.visible &&
                (start.visibility==View.VISIBLE)==!expected.visible)
            val visible=if(expected.visible)stop else start
            check(whole(screenObservation(visible))) {"M5 Extraction visible fixed action clipped: ${visible.tag}"}
            return JSONObject().put("semanticId",visible.tag).put("rectPx",observation(visible).getJSONArray("rectPx"))
                .put("enabled",visible.isEnabled).put("text",visible.text.toString())
                .put("startVisibility",start.visibility).put("startEnabled",start.isEnabled).put("startText",start.text.toString())
                .put("stopVisibility",stop.visibility).put("stopEnabled",stop.isEnabled).put("stopText",stop.text.toString())
                .put("startIdentity",System.identityHashCode(start)).put("stopIdentity",System.identityHashCode(stop))
        }
        fun unchangedState(): JSONObject {
            check(field(activity,"service")===owner && field(activity,"chart")===chart &&
                root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll")===support)
            return JSONObject().put("serviceIdentity",System.identityHashCode(owner))
                .put("chartIdentity",System.identityHashCode(chart)).put("headerIdentity",System.identityHashCode(header))
                .put("supportScrollIdentity",System.identityHashCode(support))
                .put("supportScrollPx",JSONArray(listOf(support.scrollX,support.scrollY)))
                .put("shotState",owner.shotState.name).put("manualShotActive",owner.manualShotActive)
                .put("traceIdentity",owner.chartTraceIdentity ?: JSONObject.NULL)
                .put("curveId",owner.chartCurveId ?: JSONObject.NULL)
                .put("ownerPoints",pointRecords(owner.chartPoints))
                .put("chartTraceIdentity",chart.traceIdentity ?: JSONObject.NULL)
                .put("chartPoints",pointRecords(chart.points)).put("chartReference",pointRecords(chart.reference))
                .put("referenceId",field(activity,"referenceId") ?: JSONObject.NULL)
                .put("referenceCurveId",field(activity,"referenceCurveId") ?: JSONObject.NULL)
                .put("journalExport",scope.journal.exportJson())
                .put("journalResultFailed",app.journalResult.isFailure)
                .put("fixedAction",fixedAction())
        }
        fun targetRecord(key:String,view:View):JSONObject {
            val result=JSONObject().put("semanticKey",key).put("layout",screenObservation(view))
                .put("identity",System.identityHashCode(view))
            if(key.startsWith("legend-")) {
                check(view is Button && view.width/density>=48f && view.height/density>=48f) {
                    "M5 Extraction legend actual touch target <48dp: $key ${view.width}x${view.height} density=$density" }
                result.put("touchWidthPx",view.width).put("touchHeightPx",view.height).put("density",density)
                    .put("minimumTouchDp",48)
            }
            return result
        }
        val initialState=unchangedState()
        val initialTargets=JSONArray(targets.map {(key,view)->targetRecord(key,view)})
        val initialPlot=requireExtractionPlot(activity,folder,initial)
        val plotRect=initialPlot.getJSONArray("canvasPlotBoundsScreenPx").toString()
        val reached=JSONArray();var restored: NativeWindowFrameEvidence.CapturedFrame?=null
        try {
            targets.forEach { (key,view) ->
                main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
                frames.committedFrame(root(activity))
                val targetFolder=File(folder,"states/chart-header-$key-reachable")
                val frame=capture(activity,targetFolder)
                main {
                    val target=targetRecord(key,view)
                    val layout=target.getJSONObject("layout")
                    check(whole(layout)) {"M5 Extraction chart header target not wholly reachable: $key $layout"}
                    check(view.parent!=null && nodes(header).any {it===view})
                    val current=unchangedState()
                    check(current.toString()==initialState.toString()) {"M5 Extraction header reach changed business state: $key"}
                    val plot=requireExtractionPlot(activity,targetFolder,frame)
                    check(plot.getJSONArray("canvasPlotBoundsScreenPx").toString()==plotRect)
                    reached.put(target.put("state",current).put("scrollPx",JSONArray(listOf(header.scrollX,header.scrollY)))
                        .put("frameSync",frame.proof))
                }
            }
        } finally {
            main {header.scrollTo(savedX,savedY)}
            frames.committedFrame(root(activity))
            restored=capture(activity,File(folder,"states/chart-header-restored"))
        }
        var restoredState: JSONObject?=null
        main {
            check(root(activity).findViewWithTag<ExtractionOverlayView>("product/extraction/chart")===chart &&
                chart.findViewWithTag<ScrollView>("product/extraction/chart-header")===header &&
                header.scrollX==savedX && header.scrollY==savedY &&
                targets.all {(_,view)->nodes(header).any {it===view}})
            restoredState=unchangedState()
            check(restoredState.toString()==initialState.toString()) {"M5 Extraction header restore changed business state"}
            val plot=requireExtractionPlot(activity,File(folder,"states/chart-header-restored"),requireNotNull(restored))
            check(plot.getJSONArray("canvasPlotBoundsScreenPx").toString()==plotRect)
        }
        return JSONObject().put("targetKeys",JSONArray(targets.map {it.first})).put("initialTargets",initialTargets)
            .put("reached",reached).put("initialState",initialState).put("restoredState",requireNotNull(restoredState))
            .put("initialScrollPx",JSONArray(listOf(savedX,savedY)))
            .put("restoredScrollPx",JSONArray(listOf(header.scrollX,header.scrollY)))
            .put("exactScrollRestored",true).put("completed",true).also {
                WindowProofStreamingWriter.writeStrict(it,File(folder,"chart-header-reachability.json"))
            }
    }
    private fun action(activity: Activity, label: Int): TextView=nodes(root(activity)).filterIsInstance<TextView>()
        .single { it.isClickable && it.text.toString()==activity.getString(label) }
    private fun list(activity: Activity)=field(activity,"historyList") as ListView
    private fun selectHistory(activity: Activity, id: String, requireNonemptySamples: Boolean=true) {
        var index=-1
        main { val actual=list(activity); index=(0 until actual.count).single { (actual.getItemAtPosition(it) as ShotHistory.Entry).id==id }
            actual.setSelection(index) }
        await("selected real history row visible") { list(activity).firstVisiblePosition<=index && list(activity).lastVisiblePosition>=index }
        main { val actual=list(activity); check(actual.performItemClick(actual.getChildAt(index-actual.firstVisiblePosition),index,actual.adapter.getItemId(index))) }
        if(requireNonemptySamples) chartReady(activity)
    }
    private data class Anchor(val position: Int,val top: Int,val id: String)
    private fun anchor(activity: Activity): Anchor {
        val actual=list(activity); return Anchor(actual.firstVisiblePosition,actual.getChildAt(0).top,
            (actual.getItemAtPosition(actual.firstVisiblePosition) as ShotHistory.Entry).id)
    }
    private fun savedHistoryBusiness(initial: Activity, scope: Scope, folder: File) {
        var activity=initial; val cases=JSONArray()
        for(journalSource in listOf(false,true)) {
            if(journalSource) {
                main { check(action(activity,R.string.journal_tab).performClick()) }
                await("journal actual list populated") { field(activity,"showJournal")==true && list(activity).count==40 }
            }
            main { val view=list(activity); check(view.canScrollVertically(1)); view.setSelectionFromTop(12,-HoyiUi.dp(activity,8)) }
            await("nonzero actual continuous list viewport") { list(activity).firstVisiblePosition>0 && list(activity).childCount>0 && !list(activity).isLayoutRequested }
            lateinit var before: Anchor
            main { before=anchor(activity) }
            main { val actual=list(activity); val position=actual.firstVisiblePosition
                check(actual.performItemClick(actual.getChildAt(0),position,actual.adapter.getItemId(position))) }
            chartReady(activity)
            capture(activity,File(folder,"states/${if(journalSource) "journal" else "local"}-nonzero-selected"))
            activity=recreate(activity)
            await("same source, selection and nonzero viewport restored") {
                field(activity,"showJournal")==journalSource && list(activity).childCount>0 && !list(activity).isLayoutRequested && anchor(activity)==before &&
                    (field(activity,"selectedIds") as Map<*,*>)[journalSource]==before.id
            }
            chartReady(activity)
            capture(activity,File(folder,"states/${if(journalSource) "journal" else "local"}-recreated"))
            cases.put(JSONObject().put("source",if(journalSource) "journal" else "local").put("anchor",before.toString())
                .put("selectedId",before.id).put("recreateRestored",true).put("nonzeroViewport",true))
        }
        File(folder,"history-business.json").writeText(JSONObject().put("cases",cases).put("completed",true)
            .put("scopeHistoryCount",scope.history.entries.size).put("ioExportsImports","PENDING_NOT_INVOKED_no_legacy_or_SAF_scope").toString(2))
    }
    private fun comparisonBusiness(initial: Activity, scope: Scope, folder: File) {
        var activity=initial; val original=scope.journal.exportJson(); val cases=JSONArray()
        val first=scope.firstId; val second=scope.secondId
        for(metric in ChartMetric.entries) {
            main {
                val label=activity.getString(when(metric) {
                    ChartMetric.PRESSURE->R.string.task_comparison_pressure; ChartMetric.WATER->R.string.task_comparison_water
                    ChartMetric.WEIGHT->R.string.task_comparison_weight; ChartMetric.TEMPERATURE->R.string.task_comparison_temperature })
                check(nodes(root(activity)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString()==label }.performClick())
                check((field(activity,"chart") as TwoCupChartView).metric==metric)
            }
            capture(activity,File(folder,"states/metric-${metric.name.lowercase()}"))
            cases.put(JSONObject().put("metric",metric.name).put("oneMetricAtATime",true))
        }
        main { check(root(activity).findViewWithTag<View>("comparison-swap").performClick()) }
        await("swap actual identities") { field(activity,"firstId")==second && field(activity,"secondId")==first }
        activity=recreate(activity); chartReady(activity)
        main { check(field(activity,"firstId")==second && field(activity,"secondId")==first && field(activity,"selectedMetric")==ChartMetric.TEMPERATURE) }
        capture(activity,File(folder,"states/swapped-recreated"))
        // Select the real placeholder; do not reflectively overwrite either identity.
        main { root(activity).findViewWithTag<Spinner>("comparison-first").setSelection(0) }
        await("placeholder clears cup and projection") { field(activity,"firstId")==null && (field(activity,"chart") as TwoCupChartView).firstPoints.isEmpty() }
        capture(activity,File(folder,"states/placeholder-clears"))
        check(scope.journal.exportJson()==original)
        File(folder,"comparison-business.json").writeText(JSONObject().put("cases",cases).put("swapRecreateIdsAndMetric",true)
            .put("placeholderClearsIdentityAndProjection",true).put("journalUnchanged",true).put("completed",true).toString(2))
    }
    private fun detailReachability(activity: Activity, scope:Scope, folder: File) {
        val scroll=requireNotNull(root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll"))
        val savedX=scroll.scrollX;val savedY=scroll.scrollY
        val id=activity.intent.getStringExtra("shotId");val journal=scope.journal.exportJson()
        val records=JSONArray()
        val targets=nodes(root(activity)).filterIsInstance<TextView>().filter {
            val tag=it.tag as? String ?: ""
            tag.startsWith("product/history/note/") && !tag.endsWith("/row") && it.isShown }
        check(targets.size==6) {"M5 actual saved note value targets missing: ${targets.map {it.tag}}"}
        fun fixedEdit() {
            val edit=requireNotNull(root(activity).findViewWithTag<Button>("product/history/review"))
            check(historyVisibility(edit).getBoolean("complete") && whole(screenObservation(edit)))
        }
        try {
            targets.forEachIndexed {index,view ->
                main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
                frames.committedFrame(root(activity))
                capture(activity,File(folder,"states/detail-note-$index-reachable"))
                main {
                    check(whole(screenObservation(view))) {"M5 complete saved note not reachable: ${view.tag}"}
                    fixedEdit();records.put(JSONObject().put("target",view.tag).put("visibility",historyVisibility(view))
                        .put("layout",screenObservation(view)).put("scrollX",scroll.scrollX).put("scrollY",scroll.scrollY))
                }
            }
        } finally {
            main {scroll.scrollTo(savedX,savedY)};frames.committedFrame(root(activity))
            main {check(scroll.scrollX==savedX && scroll.scrollY==savedY);check(activity.intent.getStringExtra("shotId")==id);fixedEdit()}
        }
        capture(activity,File(folder,"states/detail-notes-restored"))
        check(scope.journal.exportJson()==journal)
        File(folder,"detail-business.json").writeText(JSONObject().put("targets",records).put("savedNotesReachable",true)
            .put("initialScrollPx",JSONArray(listOf(savedX,savedY))).put("restoredScrollPx",JSONArray(listOf(scroll.scrollX,scroll.scrollY)))
            .put("shotId",id).put("exactScrollRestored",true).put("shotIdUnchanged",true).put("journalUnchanged",true)
            .put("fixedEditComplete",true).put("notesSource","actual_BrewJournal_Notes").put("noEditOrSaveInvoked",true).put("completed",true).toString(2))
    }
    private fun extractionReachability(activity: Activity, folder: File) {
        val scroll=requireNotNull(root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll"))
        val savedX=scroll.scrollX;val savedY=scroll.scrollY
        check(savedX==0 && savedY==0) { "M5 fresh RUNNING support viewport must retain its initial scroll" }
        val owner=requireNotNull(field(activity,"service") as? MobileService)
        val trace=owner.chartTraceIdentity;val curve=owner.chartCurveId
        val shot=owner.shotState;val points=owner.chartPoints.toList()
        val reference=field(activity,"referenceId");val referenceCurve=field(activity,"referenceCurveId")
        val chart=field(activity,"chart") as ExtractionOverlayView
        val chartReference=chart.reference.toList()
        val stop=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/stop"))
        val fixedRect=observation(stop).getJSONArray("rectPx").toString()
        val tools=listOf("reference-choice" to R.string.reference_choose,
            "overview" to R.string.extraction_overview).map { (name,label) ->
            name to nodes(scroll).filterIsInstance<Button>().single {it.text.toString()==activity.getString(label)}
        }
        check(tools[0].second !== tools[1].second && tools[0].second.parent===tools[1].second.parent)
        check(tools.all { (_,view) -> view.isShown && view.isAttachedToWindow && view.isClickable })
        val mode=nodes(root(activity)).filterIsInstance<TextView>().single {
            it.text.toString()==activity.getString(R.string.extraction_mock_subtitle) && it.isShown
        }
        check(mode !in nodes(scroll) && whole(screenObservation(mode))) {
            "M5 current RUNNING mode subtitle is not complete in its actual header location"
        }
        val initial=JSONArray(tools.map { (name,view) ->
            JSONObject().put("semanticKey",name).put("layout",screenObservation(view))
        })
        val reached=JSONArray()
        fun unchanged() {
            check(owner.shotState==shot && owner.chartTraceIdentity==trace && owner.chartCurveId==curve && owner.chartPoints==points)
            check(field(activity,"service")===owner && field(activity,"referenceId")==reference &&
                field(activity,"referenceCurveId")==referenceCurve && chart.reference==chartReference)
            check(stop.isShown && stop.isEnabled && whole(screenObservation(stop)) &&
                observation(stop).getJSONArray("rectPx").toString()==fixedRect)
            check(whole(screenObservation(mode)))
        }
        main { unchanged() }
        try {
            tools.forEach { (name,view) ->
                main { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
                frames.committedFrame(root(activity))
                capture(activity,File(folder,"states/extraction-$name-reachable"))
                main {
                    check(whole(screenObservation(view))) { "M5 RUNNING tool not fully reachable: $name" }
                    unchanged()
                    reached.put(JSONObject().put("semanticKey",name).put("layout",screenObservation(view))
                        .put("scrollPx",JSONArray(listOf(scroll.scrollX,scroll.scrollY))))
                }
            }
        } finally {
            main { scroll.scrollTo(savedX,savedY) }
            frames.committedFrame(root(activity))
            main { check(scroll.scrollX==savedX && scroll.scrollY==savedY);unchanged() }
        }
        capture(activity,File(folder,"states/extraction-tools-restored"))
        main { check(scroll.scrollX==savedX && scroll.scrollY==savedY);unchanged() }
        File(folder,"extraction-reachability.json").writeText(JSONObject()
            .put("targetKeys",JSONArray(tools.map {it.first})).put("initialTargets",initial).put("reached",reached)
            .put("initialScrollPx",JSONArray(listOf(savedX,savedY)))
            .put("restoredScrollPx",JSONArray(listOf(scroll.scrollX,scroll.scrollY)))
            .put("exactScrollRestored",true).put("traceIdentity",trace)
            .put("referenceId",reference ?: JSONObject.NULL).put("referenceCurveId",referenceCurve ?: JSONObject.NULL)
            .put("shotState",shot.name).put("fixedStopRectPx",JSONObject().put("rect",fixedRect))
            .put("modeSubtitle",screenObservation(mode)).put("modeLocation","header_outside_support_scroll_in_current_version")
            .put("noToolClicks",true).put("modelAndSelectionUnchanged",true).put("completed",true).toString(2))
    }
    private fun extractionSpecialSupportReachability(activity: Activity, scope: Scope, folder: File, case: String): JSONObject {
        val expected=when(case) {
            "UNKNOWN_live"->listOf("choose-beans","reference-choice","overview","prepare","cancel-prepare")
            "manual"->listOf("reference-choice","overview","prepare","cancel-prepare")
            "no_service_ready"->listOf("connect-devices","choose-beans","reference-choice","overview","prepare","cancel-prepare")
            else->listOf("reference-choice","overview")
        }
        val scroll=requireNotNull(root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll"))
        val chart=requireNotNull(field(activity,"chart") as? ExtractionOverlayView)
        val header=requireNotNull(chart.findViewWithTag<ScrollView>("product/extraction/chart-header"))
        val owner=requireNotNull(field(activity,"service") as? MobileService)
        val start=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/start"))
        val stop=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/stop"))
        val choice=field(activity,"referenceChoice") as Button
        val overview=nodes(requireNotNull(choice.parent as? View)).filterIsInstance<Button>()
            .single {it!==choice && it.text.toString()==activity.getString(R.string.extraction_overview)}
        val all=linkedMapOf("connect-devices" to field(activity,"connectDevices") as Button,
            "choose-beans" to field(activity,"chooseBeans") as Button,
            "reference-choice" to choice,"overview" to overview,
            "prepare" to field(activity,"prepare") as Button,
            "cancel-prepare" to field(activity,"cancelPrepare") as Button)
        val chooseCurve=field(activity,"chooseCurve") as Button
        val preparationTools=field(activity,"preparationTools") as View
        fun expectedEnabled(key:String)=key!="prepare" && key!="cancel-prepare" &&
            !(case=="manual" && key=="reference-choice")
        check(chooseCurve.visibility==View.GONE && all.keys.toList().filter {all.getValue(it).isShown}==expected &&
            all.all {(key,view)->view.isEnabled==expectedEnabled(key)} &&
            all.values.distinct().size==all.size && all.values.all {it.isAttachedToWindow && it in nodes(scroll)}) {
            "M5 Extraction special support targets mismatch: $case" }
        val saved=JSONArray(listOf(scroll.scrollX,scroll.scrollY));val headerSaved=JSONArray(listOf(header.scrollX,header.scrollY))
        val visibleAction=if(StopActionPresentation.describe(owner.shotState,owner.snapshot.coffeeState,owner.running).visible)stop else start
        val density=activity.resources.displayMetrics.density
        val state: () -> JSONObject = {
            check(field(activity,"service")===owner && field(activity,"chart")===chart &&
                root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll")===scroll &&
                chart.findViewWithTag<ScrollView>("product/extraction/chart-header")===header &&
                all.all {(key,view)->key=="overview" || field(activity,when(key) {
                    "connect-devices"->"connectDevices";"choose-beans"->"chooseBeans";"reference-choice"->"referenceChoice"
                    "prepare"->"prepare";else->"cancelPrepare"})===view})
            check(chooseCurve.visibility==View.GONE &&
                all.keys.filter {all.getValue(it).isShown}==expected &&
                all.all {(key,view)->view.isEnabled==expectedEnabled(key)})
            val stopPolicy=StopActionPresentation.describe(owner.shotState,owner.snapshot.coffeeState,owner.running)
            check(stop.isEnabled==stopPolicy.enabled && (stop.visibility==View.VISIBLE)==stopPolicy.visible &&
                (start.visibility==View.VISIBLE)==!stopPolicy.visible && whole(screenObservation(visibleAction)))
            val plot=requireNotNull(chart.findViewWithTag<View>("chart-plot"))
            JSONObject().put("serviceIdentity",System.identityHashCode(owner))
                .put("chartIdentity",System.identityHashCode(chart)).put("supportIdentity",System.identityHashCode(scroll))
                .put("headerIdentity",System.identityHashCode(header)).put("headerScrollPx",JSONArray(listOf(header.scrollX,header.scrollY)))
                .put("plotRectPx",observation(plot).getJSONArray("rectPx"))
                .put("shotState",owner.shotState.name).put("manualShotActive",owner.manualShotActive)
                .put("traceIdentity",owner.chartTraceIdentity ?: JSONObject.NULL).put("curveId",owner.chartCurveId ?: JSONObject.NULL)
                .put("ownerPoints",pointRecords(owner.chartPoints))
                .put("pendingStartId",owner.mockExtractionPendingStartId ?: JSONObject.NULL)
                .put("pendingStopId",owner.mockExtractionPendingStopId ?: JSONObject.NULL)
                .put("preparationToolsVisibility",preparationTools.visibility)
                .put("preparationToolsEffectiveShown",preparationTools.isShown)
                .put("toolStates",JSONArray(all.map {(key,view)->JSONObject().put("semanticKey",key)
                    .put("identity",System.identityHashCode(view)).put("visibility",view.visibility)
                    .put("effectiveShown",view.isShown).put("parentVisibility",(view.parent as View).visibility)
                    .put("enabled",view.isEnabled).put("text",view.text.toString())}))
                .put("chartTraceIdentity",chart.traceIdentity ?: JSONObject.NULL)
                .put("chartPoints",pointRecords(chart.points)).put("chartReference",pointRecords(chart.reference))
                .put("referenceId",field(activity,"referenceId") ?: JSONObject.NULL)
                .put("referenceCurveId",field(activity,"referenceCurveId") ?: JSONObject.NULL)
                .put("journalExport",scope.journal.exportJson()).put("journalResultFailed",app.journalResult.isFailure)
                .put("fixedAction",JSONObject().put("semanticId",visibleAction.tag).put("rectPx",observation(visibleAction).getJSONArray("rectPx"))
                    .put("text",visibleAction.text.toString()).put("enabled",visibleAction.isEnabled)
                    .put("startVisibility",start.visibility).put("startEnabled",start.isEnabled)
                    .put("stopVisibility",stop.visibility).put("stopEnabled",stop.isEnabled))
        }
        fun target(key:String,view:View):JSONObject {
            check(view.isAttachedToWindow && view in nodes(scroll))
            val result=JSONObject().put("semanticKey",key).put("identity",System.identityHashCode(view))
                .put("layout",screenObservation(view))
            if(view is Button) {
                check(view.width/density>=48f && view.height/density>=48f)
                result.put("widthPx",view.width).put("heightPx",view.height).put("density",density)
                    .put("enabled",view.isEnabled).put("text",view.text.toString())
            }
            return result
        }
        val baseline=state()
        check(headerSaved.toString()==baseline.getJSONArray("headerScrollPx").toString())
        val tools=expected.map {it to all.getValue(it)}
        val initial=JSONArray(tools.map {(key,view)->target(key,view)})
        val textViews=listOf("device-summary" to field(activity,"deviceSummary") as TextView,
            "preparation-summary" to field(activity,"preparationSummary") as TextView)
            .filter {(_,view)->view.visibility==View.VISIBLE}
        val initialText=JSONArray(textViews.map {(key,view)->target(key,view)})
        val reached=JSONArray();val reachedText=JSONArray()
        try {
            for ((key,view) in tools) {
                main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
                frames.committedFrame(root(activity))
                val phase=File(folder,"states/extraction-support-$key-reachable")
                val frame=capture(activity,phase)
                main {
                    val record=target(key,view)
                    check(whole(record.getJSONObject("layout"))) {"M5 Extraction special support tool clipped: $case/$key"}
                    val current=state();check(current.toString()==baseline.toString())
                    reached.put(record.put("state",current).put("scrollPx",JSONArray(listOf(scroll.scrollX,scroll.scrollY)))
                        .put("frameSync",frame.proof))
                }
            }
            for (index in textViews.indices) {
                val (key,view)=textViews[index]
                if(!whole(initialText.getJSONObject(index).getJSONObject("layout"))) {
                    main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
                    frames.committedFrame(root(activity))
                    val phase=File(folder,"states/extraction-support-$key-reachable")
                    val frame=capture(activity,phase)
                    main {
                        val record=target(key,view)
                        check(whole(record.getJSONObject("layout"))) {"M5 Extraction special support text clipped: $case/$key"}
                        val current=state();check(current.toString()==baseline.toString())
                        reachedText.put(record.put("state",current).put("scrollPx",JSONArray(listOf(scroll.scrollX,scroll.scrollY)))
                            .put("frameSync",frame.proof))
                    }
                }
            }
        } finally {
            main {scroll.scrollTo(saved.getInt(0),saved.getInt(1))}
            frames.committedFrame(root(activity))
        }
        val restored=capture(activity,File(folder,"states/extraction-support-restored"))
        val finalState=state()
        check(scroll.scrollX==saved.getInt(0) && scroll.scrollY==saved.getInt(1) &&
            finalState.toString()==baseline.toString())
        return JSONObject().put("case",case).put("targetKeys",JSONArray(expected)).put("initialTargets",initial)
            .put("reached",reached).put("initialText",initialText).put("reachedText",reachedText)
            .put("initialState",baseline).put("restoredState",finalState)
            .put("initialScrollPx",saved).put("restoredScrollPx",JSONArray(listOf(scroll.scrollX,scroll.scrollY)))
            .put("restoredFrameSync",restored.proof).put("chooseCurveVisibility",chooseCurve.visibility)
            .put("noToolClicks",true).put("completed",true).also {
                WindowProofStreamingWriter.writeStrict(it,File(folder,"extraction-support-reachability.json"))
            }
    }
    private fun extractionSpecialStates(folder: File, theme: String, source: String, profile: String): JSONObject {
        check(extractionScenarioShotIds.keys.toList()==extractionPendingSpecialCases &&
            extractionScenarioShotIds.values.toSet().size==extractionPendingSpecialCases.size &&
            extractionScenarioShotIds.values.all {it.matches(Regex("[A-Za-z0-9-]{1,64}"))}) {
            "M5 explicit scenario IDs must be unique legal ShotSamplesStore IDs"
        }
        val base=File(folder,"special-states").apply {check(isDirectory || mkdirs())}
        val idContractFolder=File(base,"id-contract")
        val idContractStore=ShotSamplesStore(idContractFolder)
        val idContractPoint=listOf(ShotPoint(0L,90,100,6,9300,null,null,true))
        val idContract=JSONObject()
        extractionScenarioShotIds.forEach { (case,id) ->
            idContractStore.save(id,idContractPoint)
            check(idContractStore.load(id)==idContractPoint) {"M5 actual ShotSamplesStore rejected declared ID: $case"}
            idContract.put(case,JSONObject().put("shotId",id).put("sha256",sha(File(idContractFolder,"samples-$id.tsv"))))
        }
        File(base,"id-contract.json").writeText(JSONObject().put("cases",idContract)
            .put("realShotSamplesStoreSaveAndLoad",true).toString(2))
        val cases=JSONArray()
        val summary=JSONObject().put("sourceSha",source).put("profile",profile).put("page","extraction")
            .put("synthetic",true).put("hardwareMeasurement",false).put("realProductActivity",true)
            .put("visualAcceptance","ASTRA_PENDING")
            .put("expectedCases",JSONArray(extractionPendingSpecialCases))
            .put("scenarioShotIds",JSONObject(extractionScenarioShotIds)).put("cases",cases)
            .put("mainConfigurationCountContribution",0)
        fun persist() { WindowProofStreamingWriter.writeStrict(summary,File(base,"manifest.json")) }
        var primaryFailure: Throwable?=null
        try {
            extractionPendingSpecialCases.forEach { case ->
                val caseFolder=File(base,case).apply {check(isDirectory || mkdirs())}
                val scope=Scope(caseFolder,"extraction")
                val proof=JSONObject().put("case",case).put("sourceSha",source).put("profile",profile)
                    .put("declaredShotId",extractionScenarioShotIds.getValue(case))
                    .put("synthetic",true).put("hardwareMeasurement",false).put("realProductActivity",true)
                    .put("visualAcceptance","ASTRA_PENDING")
                cases.put(proof)
                var failure: Throwable?=null
                try {
                    scope.prepare();scope.install(theme=="dark")
                    val inputs=JSONArray()
                    val operationInputs=JSONArray()
                    val owner=if(case=="no_service_ready") scope.beginUnreadyService() else scope.beginScenarioService(case)
                    val requested=case in setOf("STARTING","STOP_REQUESTED","UNKNOWN_live","no_scale","stale_scale",
                        "historical_reference_toggle","missing_samples","water_reset","sample_gap","journal_unavailable")
                    val shotId=if(requested) scope.startScenarioShot(owner,case) else null
                    if(requested) operationInputs.put(JSONObject().put("ordinal",operationInputs.length()+1)
                        .put("kind","start_requested").put("requestId",requireNotNull(owner.mockExtractionPendingStartId))
                        .put("synthetic",true))
                    if(requested && case!="STARTING") {
                        val request=requireNotNull(owner.mockExtractionPendingStartId)
                        scope.completeScenarioStart(owner)
                        operationInputs.put(JSONObject().put("ordinal",operationInputs.length()+1)
                            .put("kind","start_completed").put("requestId",request)
                            .put("result","synthetic_Success").put("synthetic",true))
                    }
                    when(case) {
                        "STOP_REQUESTED"->{
                            inputs.put(scope.offerScenarioFrame(owner,1000,6))
                            main {owner.stopShot();check(owner.shotState==ExtractionState.STOP_REQUESTED && owner.mockExtractionPendingStopId!=null)}
                            operationInputs.put(JSONObject().put("ordinal",operationInputs.length()+1)
                                .put("kind","manual_stop_requested").put("requestId",requireNotNull(owner.mockExtractionPendingStopId))
                                .put("completion","not_offered").put("synthetic",true))
                        }
                        "UNKNOWN_live"->{
                            inputs.put(scope.offerScenarioFrame(owner,1000,6))
                            main {owner.offerMockExtractionCoffeeState(scope.scenarioOwner(),DeviceState.DISCONNECTED)
                                check(owner.shotState==ExtractionState.OUTCOME_UNKNOWN && owner.snapshot.coffeeState==DeviceState.DISCONNECTED)}
                            operationInputs.put(JSONObject().put("ordinal",operationInputs.length()+1)
                                .put("kind","coffee_ready_changed").put("state","DISCONNECTED").put("synthetic",true))
                        }
                        "no_scale"->{
                            inputs.put(scope.offerScenarioFrame(owner,1000,6))
                            check(owner.snapshot.scaleState==DeviceState.DISCONNECTED && owner.chartPoints.all {it.weightHundredthsGram==null})
                        }
                        "stale_scale"->{
                            scope.installStaleScaleInput(owner)
                            inputs.put(scope.offerScenarioFrame(owner,6000,36))
                            check(owner.snapshot.scaleObservation?.receivedAtMs==1000000L &&
                                owner.chartPoints.lastOrNull()?.weightHundredthsGram==null)
                        }
                        "manual"->{
                            inputs.put(scope.offerScenarioFrame(owner,1000,6,6))
                            inputs.put(scope.offerScenarioFrame(owner,2000,12,6))
                            check(owner.manualShotActive && owner.shotState==ExtractionState.IDLE &&
                                owner.chartCurveId=="manual" && owner.chartPoints.size==2)
                        }
                        "historical_reference_toggle"->{inputs.put(scope.offerScenarioFrame(owner,1000,6))}
                        "water_reset"->{
                            inputs.put(scope.offerScenarioFrame(owner,1000,10))
                            inputs.put(scope.offerScenarioFrame(owner,2000,20))
                            inputs.put(scope.offerScenarioFrame(owner,3000,5))
                            val projected=FourMetricChartProjection.build(owner.chartPoints,emptyList())
                            check(projected.waterResetsMs==listOf(3000L))
                            check(projected.series.single {it.metric==ChartMetric.WATER}.current.size==2)
                        }
                        "sample_gap"->{
                            listOf(1000L,2000L,3000L,8000L).forEach {elapsed ->
                                inputs.put(scope.offerScenarioFrame(owner,elapsed,(elapsed/1000*6).toInt()))
                            }
                            val projected=FourMetricChartProjection.build(owner.chartPoints,emptyList())
                            check(projected.series.single {it.metric==ChartMetric.WATER}.current.size==2)
                        }
                        "journal_unavailable"->{
                            main {write(app,"journalObservationFailure",IllegalStateException("isolated synthetic journal observation unavailable"))
                                check(app.journalResult.isFailure)}
                            inputs.put(scope.offerScenarioFrame(owner,1000,6))
                        }
                        "missing_samples"->{check(owner.chartPoints.isEmpty() && owner.shotState==ExtractionState.RUNNING)}
                        "STARTING","no_service_ready"->Unit
                    }
                    File(caseFolder,"fixture.json").writeText(scope.fixture.put("case",case)
                        .put("declaredShotId",extractionScenarioShotIds.getValue(case))
                        .put("synthetic",true).put("hardwareMeasurement",false)
                        .put("offeredMachineFrames",inputs).put("operationInputs",operationInputs).toString(2))
                    val activity=start(ExtractionActivity::class.java)
                    await("actual Extraction special state $case bound and rendered") {
                        field(activity,"service")===owner && field(activity,"bound")==true &&
                            (field(activity,"chart") as ExtractionOverlayView).traceIdentity==owner.chartTraceIdentity
                    }
                    val frame=capture(activity,caseFolder)
                    proof.put("chartHeaderReachability",extractionChartHeaderReachability(activity,scope,caseFolder,frame))
                    proof.put("supportReachability",extractionSpecialSupportReachability(activity,scope,caseFolder,case))
                    main {
                        val chart=field(activity,"chart") as ExtractionOverlayView
                        check(field(activity,"service")===owner && chart.traceIdentity==owner.chartTraceIdentity)
                        check(chart.points==ExtractionReference.align(owner.chartPoints).points)
                        val expected=when(case) {
                            "STARTING"->ExtractionState.STARTING
                            "STOP_REQUESTED"->ExtractionState.STOP_REQUESTED
                            "UNKNOWN_live"->ExtractionState.OUTCOME_UNKNOWN
                            "no_service_ready","manual"->ExtractionState.IDLE
                            else->ExtractionState.RUNNING
                        }
                        check(owner.shotState==expected) {"M5 actual model state mismatch: $case"}
                        if(case=="no_service_ready") check(!owner.running && owner.snapshot.coffeeState!=DeviceState.READY)
                        if(case=="manual")check(owner.manualShotActive && shotId==null)
                        if(case=="missing_samples")check(chart.points.isEmpty() &&
                            (0 until chart.visualEvidence.length()).none { chart.visualEvidence.getJSONObject(it).optString("operation")=="path" })
                        if(case=="journal_unavailable")check(app.journalResult.isFailure && chart.reference.isEmpty())
                        val projected=FourMetricChartProjection.build(owner.chartPoints,chart.reference)
                        val water=projected.series.singleOrNull {it.metric==ChartMetric.WATER}
                        val stop=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/stop"))
                        if(stop.isShown)check(whole(screenObservation(stop)))
                        proof.put("actualState",owner.shotState.name).put("actualRunningService",owner.running)
                            .put("actualManualShotActive",owner.manualShotActive).put("actualTraceIdentity",owner.chartTraceIdentity ?: JSONObject.NULL)
                            .put("actualCurveId",owner.chartCurveId ?: JSONObject.NULL).put("actualSampleCount",owner.chartPoints.size)
                            .put("actualOfferedFrameSerial",owner.mockExtractionOfferedFrameSerial)
                            .put("actualWaterResetsMs",JSONArray(projected.waterResetsMs))
                            .put("actualWaterSegments",JSONArray(water?.current?.map {segment ->
                                JSONArray(segment.map {it.elapsedMs}) } ?: emptyList<JSONArray>()))
                            .put("actualScaleState",owner.snapshot.scaleState.name).put("actualCoffeeState",owner.snapshot.coffeeState.name)
                            .put("stop",screenObservation(stop)).put("frameSync",frame.proof)
                    }
                    if(case=="historical_reference_toggle")extractionReferenceToggle(activity,scope,caseFolder,proof)
                    if(case=="journal_unavailable")extractionJournalUnavailable(activity,scope,caseFolder,proof)
                    proof.put("offeredMachineFrames",inputs).put("operationInputs",operationInputs)
                        .put("shotId",shotId ?: JSONObject.NULL)
                        .put("status","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE")
                } catch(error: Throwable) {
                    failure=error;proof.put("status","FAIL").put("failure",error.stackTraceToString())
                } finally {
                    try { scope.cleanup() } catch(error: Throwable) {
                        if(failure==null)failure=error else if(failure!==error)failure!!.addSuppressed(error)
                        proof.put("status","FAIL").put("cleanupFailure",error.stackTraceToString())
                    }
                    try { WindowProofStreamingWriter.writeStrict(proof,File(caseFolder,"case.json"));persist() }
                    catch(error: Throwable) {
                        if(failure==null)failure=error else if(failure!==error)failure!!.addSuppressed(error)
                    }
                }
                failure?.let {throw it}
            }
            summary.put("completed",true).put("actualAdditionalActivityCaptures",cases.length())
            return summary
        } catch(error: Throwable) { primaryFailure=error;throw error }
        finally {
            try {persist()} catch(error: Throwable) {
                if(primaryFailure==null)throw error
                if(primaryFailure!==error)primaryFailure!!.addSuppressed(error)
            }
        }
    }
    private fun extractionReferenceToggle(activity: Activity, scope: Scope, folder: File, proof: JSONObject) {
        val owner=requireNotNull(field(activity,"service") as? MobileService)
        val button=field(activity,"referenceChoice") as Button
        val scroll=requireNotNull(root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll"))
        val beforeScroll=scroll.scrollX to scroll.scrollY
        val beforeJournal=scope.journal.exportJson()
        val trace=owner.chartTraceIdentity;val points=owner.chartPoints.toList()
        val stop=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/stop"))
        val stopRect=observation(stop).getJSONArray("rectPx").toString()
        val eligible=scope.journal.entries().filter {it.observation.curveId==scope.curveId && it.observation.status=="ENDED"}.take(20)
        val chosenIndex=eligible.indexOfFirst {scope.expectedSamples(it.observation.id).isNotEmpty()}
        check(chosenIndex>=0)
        val selected=eligible[chosenIndex].observation.id
        fun select(index: Int) {
            main {button.requestRectangleOnScreen(Rect(0,0,button.width,button.height),true)}
            frames.committedFrame(root(activity))
            main {check(whole(screenObservation(button)) && button.isEnabled);check(button.performClick())}
            test.waitForIdleSync()
            main {
                val dialog=android.view.inspector.WindowInspector.getGlobalWindowViews().single {it !== activity.window.decorView}
                val list=nodes(dialog).filterIsInstance<ListView>().single()
                check(list.count==eligible.size+1 && index in 0 until list.count)
                check(list.performItemClick(requireNotNull(list.getChildAt(index)),index,list.adapter.getItemId(index)))
            }
            test.waitForIdleSync()
        }
        try {
            select(chosenIndex+1)
            await("real saved historical reference selected") {
                field(activity,"referenceId")==selected && (field(activity,"chart") as ExtractionOverlayView).reference.isNotEmpty()
            }
            val on=capture(activity,File(folder,"states/extraction-reference-on"))
            proof.put("referenceOnChartHeaderReachability",extractionChartHeaderReachability(activity,scope,
                File(folder,"states/extraction-reference-on"),on))
            main {
                val chart=field(activity,"chart") as ExtractionOverlayView
                check(chart.reference==ExtractionReference.align(scope.expectedSamples(selected)).points &&
                    owner.chartTraceIdentity==trace && owner.chartPoints==points)
                proof.put("selectedReferenceId",selected).put("referenceOnCount",chart.reference.size)
                    .put("referenceOnFrameSync",on.proof)
            }
            select(0)
            await("real saved historical reference switched off") {
                field(activity,"referenceId")==null && (field(activity,"chart") as ExtractionOverlayView).reference.isEmpty()
            }
            val off=capture(activity,File(folder,"states/extraction-reference-off"))
            proof.put("referenceOffChartHeaderReachability",extractionChartHeaderReachability(activity,scope,
                File(folder,"states/extraction-reference-off"),off))
            proof.put("referenceOffFrameSync",off.proof)
        } finally {
            main {scroll.scrollTo(beforeScroll.first,beforeScroll.second)}
            frames.committedFrame(root(activity))
        }
        val restored=capture(activity,File(folder,"states/extraction-reference-restored"))
        proof.put("referenceRestoredChartHeaderReachability",extractionChartHeaderReachability(activity,scope,
            File(folder,"states/extraction-reference-restored"),restored))
        main {
            check(scroll.scrollX==beforeScroll.first && scroll.scrollY==beforeScroll.second)
            check(owner.chartTraceIdentity==trace && owner.chartPoints==points && field(activity,"referenceId")==null)
            check((field(activity,"chart") as ExtractionOverlayView).reference.isEmpty())
            check(stop.isShown && observation(stop).getJSONArray("rectPx").toString()==stopRect)
        }
        check(scope.journal.exportJson()==beforeJournal)
        proof.put("referenceToggleActualSavedJournal",true).put("selectionAndScrollRestored",true)
            .put("currentCupAndJournalUnchanged",true)
    }
    private fun focusedJournalDialog(activity: Activity): AlertDialog? {
        val foreign=android.view.inspector.WindowInspector.getGlobalWindowViews().filter {it!==activity.window.decorView}
        check(foreign.size<=1) {"M5 ambiguous journal chooser Window"}
        val decor=foreign.singleOrNull() ?: return null
        if(!decor.isAttachedToWindow || !decor.isLaidOut || !decor.hasWindowFocus() || decor.isLayoutRequested) return null
        val window=decor.javaClass.getDeclaredField("mWindow").apply {isAccessible=true}.get(decor) as android.view.Window
        check(window.decorView===decor)
        val dialog=window.callback as AlertDialog
        var owner:Context=dialog.context
        while(owner is ContextWrapper && owner!==activity) owner=owner.baseContext
        check(dialog.window===window && dialog.isShowing &&
            (dialog.ownerActivity===activity || owner===activity) && decor.windowToken!=null) {
            "M5 journal chooser Window is not owned by current Activity" }
        return dialog
    }
    private fun extractionJournalUnavailable(activity: Activity, scope: Scope, folder: File, proof: JSONObject) {
        val button=field(activity,"referenceChoice") as Button
        val support=requireNotNull(root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll"))
        val chart=field(activity,"chart") as ExtractionOverlayView
        val header=requireNotNull(chart.findViewWithTag<ScrollView>("product/extraction/chart-header"))
        val owner=requireNotNull(field(activity,"service") as? MobileService)
        val supportX=support.scrollX;val supportY=support.scrollY
        val headerX=header.scrollX;val headerY=header.scrollY
        val trace=owner.chartTraceIdentity;val shot=owner.shotState;val points=owner.chartPoints.toList()
        val referenceCurve=field(activity,"referenceCurveId")
        val journal=scope.journal.exportJson()
        val start=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/start"))
        val stop=requireNotNull(root(activity).findViewWithTag<Button>("product/extraction/stop"))
        val fixedAction=if(stop.visibility==View.VISIBLE)stop else start
        check(app.journalResult.isFailure && chart.reference.isEmpty() && field(activity,"referenceId")==null)
        fun activityState(requireActionWhole:Boolean):JSONObject {
            check(field(activity,"service")===owner && field(activity,"chart")===chart &&
                root(activity).findViewWithTag<ScrollView>("product/extraction/support-scroll")===support &&
                chart.findViewWithTag<ScrollView>("product/extraction/chart-header")===header)
            check(owner.chartTraceIdentity==trace && owner.shotState==shot && owner.chartPoints==points &&
                chart.traceIdentity==trace && chart.reference.isEmpty() && field(activity,"referenceId")==null &&
                field(activity,"referenceCurveId")==referenceCurve && app.journalResult.isFailure &&
                scope.journal.exportJson()==journal && header.scrollX==headerX && header.scrollY==headerY)
            val actionLayout=observation(fixedAction)
            if(requireActionWhole)check(whole(screenObservation(fixedAction)))
            return JSONObject().put("serviceIdentity",System.identityHashCode(owner))
                .put("chartIdentity",System.identityHashCode(chart))
                .put("supportIdentity",System.identityHashCode(support))
                .put("headerIdentity",System.identityHashCode(header))
                .put("headerScrollPx",JSONArray(listOf(header.scrollX,header.scrollY)))
                .put("shotState",owner.shotState.name).put("manualShotActive",owner.manualShotActive)
                .put("traceIdentity",owner.chartTraceIdentity ?: JSONObject.NULL)
                .put("curveId",owner.chartCurveId ?: JSONObject.NULL)
                .put("ownerPoints",pointRecords(owner.chartPoints))
                .put("chartTraceIdentity",chart.traceIdentity ?: JSONObject.NULL)
                .put("chartPoints",pointRecords(chart.points)).put("chartReference",pointRecords(chart.reference))
                .put("pendingStartId",owner.mockExtractionPendingStartId ?: JSONObject.NULL)
                .put("pendingStopId",owner.mockExtractionPendingStopId ?: JSONObject.NULL)
                .put("referenceId",field(activity,"referenceId") ?: JSONObject.NULL)
                .put("referenceCurveId",field(activity,"referenceCurveId") ?: JSONObject.NULL)
                .put("journalExport",scope.journal.exportJson()).put("journalResultFailed",app.journalResult.isFailure)
                .put("fixedAction",JSONObject().put("identity",System.identityHashCode(fixedAction))
                    .put("semanticId",fixedAction.tag).put("rectPx",actionLayout.getJSONArray("rectPx"))
                    .put("text",fixedAction.text.toString()).put("enabled",fixedAction.isEnabled)
                    .put("visibility",fixedAction.visibility).put("startVisibility",start.visibility)
                    .put("startEnabled",start.isEnabled).put("stopVisibility",stop.visibility)
                    .put("stopEnabled",stop.isEnabled))
        }
        val initialState=activityState(true)
        fun unchanged(requireActionWhole:Boolean):JSONObject=activityState(requireActionWhole).also {
            check(it.toString()==initialState.toString()) {"M5 journal chooser changed Activity model or fixed action"}
        }
        var dialog:AlertDialog?=null;var failure:Throwable?=null
        try {
            main {check(button.isEnabled);button.requestRectangleOnScreen(Rect(0,0,button.width,button.height),true)}
            frames.committedFrame(root(activity))
            main {check(whole(screenObservation(button)));proof.put("journalUnavailableBeforeDialogState",unchanged(true));check(button.performClick())}
            await("unique focused real journal chooser") {
                dialog=focusedJournalDialog(activity);dialog!=null
            }
            val actual=requireNotNull(dialog);val window=requireNotNull(actual.window);val decor=window.decorView
            val list=nodes(decor).filterIsInstance<ListView>().single()
            val title=nodes(decor).filterIsInstance<TextView>().single {it.text.toString()==activity.getString(R.string.reference_choose)}
            val cancel=requireNotNull(actual.getButton(AlertDialog.BUTTON_NEGATIVE))
            check(cancel.id==android.R.id.button2 && cancel.text.toString()==activity.getString(R.string.machine_settings_cancel))
            check(list.count==1 && list.adapter.getItem(0).toString()==activity.getString(R.string.reference_off))
            val off=requireNotNull(list.getChildAt(0)) as TextView
            check(off.text.toString()==activity.getString(R.string.reference_off))
            fun typography(view:TextView):JSONObject=JSONObject().put("identity",System.identityHashCode(view))
                .put("text",view.text.toString()).put("textSizePx",view.textSize).put("textColor",view.currentTextColor)
                .put("typefaceWeight",if(Build.VERSION.SDK_INT>=28) view.typeface?.weight ?: JSONObject.NULL else JSONObject.NULL)
                .put("fakeBold",view.paint.isFakeBoldText).put("letterSpacing",view.letterSpacing)
                .put("includeFontPadding",view.includeFontPadding).put("baseline",view.baseline)
                .put("layout",screenObservation(view))
            fun dialogState():JSONObject {
                check(focusedJournalDialog(activity)===actual && window.decorView===decor &&
                    decor.windowToken===list.windowToken && decor.windowToken===cancel.windowToken)
                val observedActivity=unchanged(false)
                val density=activity.resources.displayMetrics.density
                check(whole(screenObservation(title)) && whole(screenObservation(off)) && whole(screenObservation(cancel)))
                check(off.width/density>=48f && off.height/density>=48f &&
                    cancel.width/density>=48f && cancel.height/density>=48f)
                val position=IntArray(2);decor.getLocationOnScreen(position)
                val safe=Rect();decor.getWindowVisibleDisplayFrame(safe)
                return JSONObject().put("windowIdentity",System.identityHashCode(window))
                    .put("decorIdentity",System.identityHashCode(decor)).put("tokenIdentity",System.identityHashCode(decor.windowToken))
                    .put("ownerActivityIdentity",System.identityHashCode(activity))
                    .put("decorOriginScreenPx",JSONArray(position.toList())).put("decorSizePx",JSONArray(listOf(decor.width,decor.height)))
                    .put("safeRectScreenPx",rect(safe)).put("title",typography(title))
                    .put("off",typography(off)).put("cancel",typography(cancel))
                    .put("allNativeText",JSONArray(nodes(decor).filterIsInstance<TextView>().map(::typography)))
                    .put("listCount",list.count).put("offItemId",list.adapter.getItemId(0))
                    .put("offTouchPx",JSONArray(listOf(off.width,off.height)))
                    .put("cancelTouchPx",JSONArray(listOf(cancel.width,cancel.height)))
                    .put("density",density).put("journalResultFailed",app.journalResult.isFailure)
                    .put("referenceId",field(activity,"referenceId") ?: JSONObject.NULL)
                    .put("chartReferenceCount",chart.reference.size)
                    .put("traceIdentity",trace ?: JSONObject.NULL).put("shotState",shot.name)
                    .put("sampleCount",points.size)
                    .put("activityState",observedActivity)
            }
            val dialogFolder=File(folder,"states/extraction-journal-unavailable-dialog").apply {check(isDirectory || mkdirs())}
            val dialogFrame=frames.screenshotMeasuredWindowBufferTranslation(window,decor,File(dialogFolder,"window.png")) {dialogState()}
            WindowProofStreamingWriter.writeStrict(dialogFrame.target,File(dialogFolder,"geometry.json"))
            File(dialogFolder,"typography.json").writeText(dialogFrame.target.getJSONArray("allNativeText").toString(2))
            check(dialogFrame.proof.getInt("protocolVersion")==2 && dialogFrame.proof.getBoolean("accepted"))
            proof.put("journalUnavailableDialog",dialogFrame.target).put("journalUnavailableDialogFrameSync",dialogFrame.proof)
            main {check(focusedJournalDialog(activity)===actual &&
                list.performItemClick(off,0,list.adapter.getItemId(0)))}
            await("journal chooser closed after original off item") {
                android.view.inspector.WindowInspector.getGlobalWindowViews().none {it!==activity.window.decorView} && activity.hasWindowFocus()
            }
            val frame=capture(activity,File(folder,"states/extraction-journal-unavailable-choice"))
            main {proof.put("journalUnavailableChoiceState",unchanged(true))}
            proof.put("journalUnavailableChoiceFrameSync",frame.proof)
        } catch(error:Throwable) {failure=error}
        finally {
            try {
                main {dialog?.takeIf {it.isShowing}?.dismiss()
                    support.scrollTo(supportX,supportY);header.scrollTo(headerX,headerY)}
                frames.committedFrame(root(activity))
                val restored=capture(activity,File(folder,"states/extraction-journal-unavailable-restored"))
                main {check(android.view.inspector.WindowInspector.getGlobalWindowViews().none {it!==activity.window.decorView} &&
                    support.scrollX==supportX && support.scrollY==supportY)
                    proof.put("journalUnavailableRestoredState",unchanged(true))}
                proof.put("journalUnavailableRestoredFrameSync",restored.proof)
                    .put("journalUnavailableInitialState",initialState)
                    .put("journalUnavailableInitialScrollPx",JSONArray(listOf(supportX,supportY,headerX,headerY)))
                    .put("journalUnavailableRestoredScrollPx",JSONArray(listOf(support.scrollX,support.scrollY,header.scrollX,header.scrollY)))
            } catch(error:Throwable) {if(failure==null)failure=error else failure!!.addSuppressed(error)}
        }
        failure?.let {throw it}
        proof.put("actualJournalResultFailed",true).put("chooserOnlyOffNoFakeRecord",true)
    }
    private fun comparisonReachability(activity:Activity,scope:Scope,folder:File) {
        val toolbar=requireNotNull(root(activity).findViewWithTag<ScrollView>("comparison-toolbar"))
        val details=requireNotNull(root(activity).findViewWithTag<ScrollView>("comparison-details-scroll"))
        val scrolls=listOf(toolbar,details)
        val saved=scrolls.map {it.scrollX to it.scrollY}
        val first=field(activity,"firstId");val second=field(activity,"secondId");val metric=field(activity,"selectedMetric")
        val journal=scope.journal.exportJson();val records=JSONArray()
        fun tagged(owner:View,tag:String):View = nodes(owner).filter {it.tag==tag}.single().also {
            check(it.isShown && it.isAttachedToWindow) {"M5 required comparison target missing/hidden: $tag"}
        }
        val targets=linkedMapOf<String,View>()
        val controlTags=listOf("comparison-first","comparison-second","comparison-swap") +
            listOf("PRESSURE","WATER","WEIGHT","TEMPERATURE").map {"comparison-metric-$it"}
        controlTags.forEach {targets[it]=tagged(toolbar,it)}
        targets["comparison-source-legend"]=tagged(toolbar,"comparison-source-legend")
        val selectedStatuses=listOf(first,second).filterIsInstance<String>().map {requireNotNull(scope.journal.find(it)).observation.status}
        val expectedWarning=selectedStatuses.any {it=="UNKNOWN"}
        val warning=nodes(toolbar).filter {it.tag=="product/comparison/warning"}.single()
        check(warning.isShown==expectedWarning && warning.visibility==if(expectedWarning)View.VISIBLE else View.GONE)
        if(expectedWarning)targets["comparison-unknown-warning"]=warning
        val sourceViews=listOf("comparison-source-1","comparison-source-2").map {tag ->tagged(details,tag).also {targets[tag]=it}}
        val summary=tagged(details,"comparison-summary-table") as ViewGroup
        val rows=(0 until summary.childCount).map(summary::getChildAt).filterIsInstance<LinearLayout>()
        check(rows.size==4 && rows.all {it.childCount==3})
        check((rows[0].getChildAt(0) as TextView).text.isEmpty())
        targets["summary-header"]=rows[0]
        listOf("duration" to R.string.history_duration,"end-scale" to R.string.history_end_scale,"taste" to R.string.journal_taste)
            .forEachIndexed {index,(key,label) ->
                val row=rows[index+1]
                check(row.tag=="comparison-summary-row-$index" && (row.getChildAt(0) as TextView).text.toString()==activity.getString(label))
                targets["summary-$key"]=row
            }
        val tastes=listOf(rows[3].getChildAt(1),rows[3].getChildAt(2))
        check(tastes.all {it is TextView && it.tag=="full-text-details"})
        targets["taste-A"]=tastes[0];targets["taste-B"]=tastes[1]
        val detailButtons=mutableListOf<Button>()
        val noteButtons=mutableListOf<Button>()
        listOf("A" to R.string.journal_first,"B" to R.string.journal_second).forEachIndexed {index,(key,label) ->
            fun originalButton(action:Int):Button=nodes(details).filterIsInstance<Button>().single {
                it.text.toString()==activity.getString(label)+" · "+activity.getString(action)
            }.also {check(it.isShown && it.isAttachedToWindow && it.isClickable)}
            val detail=originalButton(R.string.journal_detail)
            val notes=originalButton(R.string.journal_notes_title)
            check(notes.parent===sourceViews[index].parent) {"M5 notes action lost its original source card ownership"}
            detailButtons+=detail;noteButtons+=notes
            targets["detail-$key"]=detail;targets["notes-$key"]=notes
        }
        check(detailButtons[0].parent===detailButtons[1].parent)
        check(targets.values.distinct().size==targets.size && targets.size==if(expectedWarning)21 else 20)
        try {
            targets.entries.forEachIndexed {index,(semanticKey,view) ->
                main {view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true)}
                frames.committedFrame(root(activity))
                capture(activity,File(folder,"states/comparison-target-$index-reachable"))
                main {
                    val observed=screenObservation(view)
                    // Taste cells intentionally ellipsize and retain their original full-text dialog/tooltip semantics.
                    val isTaste=view.tag=="full-text-details"
                    check(historyVisibility(view).getBoolean("complete")) {"M5 comparison target clipped: ${view.tag}"}
                    if(!isTaste) {
                        val textTargets=nodes(view).filterIsInstance<TextView>().filter {it.isShown && it.tag!="full-text-details"}
                        textTargets.forEach {check(whole(screenObservation(it))) {"M5 comparison target text clipped: ${it.tag}"}}
                    }
                    if(view is TextView && isTaste)check(view.tooltipText?.toString()==view.text.toString())
                    records.put(JSONObject().put("index",index).put("semanticKey",semanticKey).put("target",view.tag ?: view.javaClass.simpleName)
                        .put("tasteEllipsisRetainsFullDialogSemantics",isTaste).put("visibility",historyVisibility(view)).put("layout",observed))
                }
            }
        } finally {
            main {scrolls.forEachIndexed {index,scroll ->scroll.scrollTo(saved[index].first,saved[index].second)}}
            frames.committedFrame(root(activity))
            main {
                check(scrolls.map {it.scrollX to it.scrollY}==saved)
                check(field(activity,"firstId")==first && field(activity,"secondId")==second && field(activity,"selectedMetric")==metric)
            }
        }
        capture(activity,File(folder,"states/comparison-scrolls-restored"))
        check(scope.journal.exportJson()==journal)
        File(folder,"comparison-reachability.json").writeText(JSONObject().put("targets",records).put("expectedTargetCount",targets.size)
            .put("initialScrollPx",JSONArray(saved.map {JSONArray(listOf(it.first,it.second))}))
            .put("restoredScrollPx",JSONArray(scrolls.map {JSONArray(listOf(it.scrollX,it.scrollY))}))
            .put("exactScrollRestored",true).put("idsAndMetricUnchanged",true).put("journalUnchanged",true)
            .put("summaryRowCount",rows.size).put("tasteTargetCount",tastes.size).put("toolbarControlCount",controlTags.size)
            .put("selectedSourceStatuses",JSONArray(selectedStatuses)).put("expectedUnknownWarning",expectedWarning).put("requiredSemanticKeys",JSONArray(targets.keys.toList()))
            .put("originalFourActionIdentitiesAndOwnershipVerified",detailButtons.size==2 && noteButtons.size==2)
            .put("tasteBusinessDialogNotInvoked",true).put("completed",true).toString(2))
    }
    private fun chartMetricAction(activity: Activity, metric: ChartMetric) {
        val label=activity.getString(when(metric) {
            ChartMetric.PRESSURE->R.string.task_comparison_pressure; ChartMetric.WATER->R.string.task_comparison_water
            ChartMetric.WEIGHT->R.string.task_comparison_weight; ChartMetric.TEMPERATURE->R.string.task_comparison_temperature })
        main { check(nodes(root(activity)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString()==label }.performClick()) }
    }
    /** Additional real page states, never another contribution to the four-page/16-profile main matrix. */
    private fun historySpecialStates(scope: Scope, page: String, folder: File, parent: JSONObject): JSONObject {
        check(page in setOf("history","history-detail","comparison"))
        scope.closeActivities()
        val directory=File(folder,"special-states").apply { check(isDirectory || mkdirs()) }
        val entries=JSONArray()
        val summary=JSONObject().put("sourceSha",parent.getString("sourceSha")).put("profile",parent.getString("profile"))
            .put("page",page).put("visualAcceptance","ASTRA_PENDING").put("realProductActivity",true).put("synthetic",true)
            .put("mainConfigurationCountContribution",0).put("expectedCases",JSONArray(scope.historySpecialIds.keys.toList()))
            .put("cases",entries).put("completed",false)
        val journalBefore=scope.journal.exportJson(); val historyBefore=scope.history.entries
        fun persist() { WindowProofStreamingWriter.writeStrict(summary,File(directory,"manifest.json")) }
        persist()
        var primaryFailure: Throwable?=null
        try {
            for((case,id) in scope.historySpecialIds) {
                val expected=scope.expectedSamples(id)
                val source=requireNotNull(scope.journal.find(id))
                val caseDirectory=File(directory,case).apply { check(isDirectory || mkdirs()) }
                val proof=JSONObject().put("case",case).put("shotId",id).put("page",page)
                    .put("observation",JSONObject().put("id",source.observation.id).put("curveId",source.observation.curveId)
                        .put("status",source.observation.status).put("elapsedMs",source.observation.elapsedMs ?: JSONObject.NULL)
                        .put("endedAtMs",source.observation.endedAtMs ?: JSONObject.NULL)
                        .put("weightHundredthsGram",source.observation.weightHundredthsGram ?: JSONObject.NULL))
                    .put("notes",source.notes.toString()).put("declaredSamples",pointRecords(expected))
                    .put("declaredReferenceSamples",pointRecords(emptyList()))
                    .put("status","RUNNING").put("visualAcceptance","ASTRA_PENDING")
                entries.put(proof); persist()
                var caseFailure: Throwable?=null
                try {
                    val activity=when(page) {
                        "history"->start(HistoryActivity::class.java).also { selectHistory(it,id,requireNonemptySamples=false) }
                        "history-detail"->start(HistoryDetailActivity::class.java) { it.putExtra("shotId",id) }
                        else->start(BrewComparisonActivity::class.java) {
                            it.putExtra(BrewComparisonActivity.FIRST_SHOT_ID,id).putExtra(BrewComparisonActivity.SECOND_SHOT_ID,scope.firstId)
                        }
                    }
                    await("saved special-state asynchronous reader finished: $page/$case") {
                        if(page=="comparison") {
                            listOf("comparison-source-1","comparison-source-2").all { tag ->
                                val text=root(activity).findViewWithTag<TextView>(tag)?.text?.toString().orEmpty()
                                text.isNotEmpty() && !text.contains(activity.getString(R.string.history_loading_samples))
                            }
                        } else {
                            val status=root(activity).findViewWithTag<TextView>("product/history/chart-status")
                            status!=null && status.text.toString()!=activity.getString(R.string.history_loading_samples)
                        }
                    }
                    val metric=if(case=="water-reset") ChartMetric.WATER else ChartMetric.PRESSURE
                    if(page=="comparison") chartMetricAction(activity,metric)
                    // Take original full-window protocol2 evidence before mechanical assertions.
                    val captured=capture(activity,caseDirectory)
                    proof.put("frameSync",captured.proof)
                    main {
                        if(page=="history")requireEmbeddedHistoryInitial(activity,caseDirectory,captured.target.getJSONObject("embeddedHistoryPreview"),expected,emptyList(),expected.isEmpty())
                        if(page=="history-detail")requireStandaloneHistoryPlot(activity,caseDirectory,captured.target.getJSONObject("standaloneHistoryPlot"),expected.isEmpty())
                    }
                    main {
                        check(services().isEmpty() && MobileMonotonicClock.mockOwnerIdentity()==null)
                        val data: FourMetricChartData
                        val canvas: JSONArray
                        val raw: List<ShotPoint>
                        if(page=="comparison") {
                            val chart=field(activity,"chart") as TwoCupChartView
                            check(field(activity,"firstId")==id && field(activity,"secondId")==scope.firstId && chart.metric==metric)
                            check(chart.secondPoints==scope.expectedSamples(scope.firstId))
                            data=chart.projection; canvas=chart.visualEvidence; raw=chart.firstPoints
                            val sourceText=root(activity).findViewWithTag<TextView>("comparison-source-1").text.toString()
                            val status=activity.getString(if(expected.isEmpty()) R.string.history_no_samples else if(source.observation.status=="ENDED")
                                R.string.history_samples_complete else R.string.history_samples_partial,expected.size.toString())
                            check(sourceText.contains(status)) { "M5 comparison must disclose the actual saved sample state: $case" }
                            if(case=="sample-gap") check(sourceText.contains(activity.getString(R.string.task_chart_gap)))
                            if(case=="water-reset") check(sourceText.contains(activity.getString(R.string.task_chart_water_reset)))
                            proof.put("firstSourceDisclosure",sourceText).put("secondShotId",scope.firstId)
                        } else {
                            val chart=nodes(root(activity)).filterIsInstance<ShotChartView>().single()
                            val overlay=nodes(chart).filterIsInstance<ExtractionOverlayView>().single()
                            data=field(overlay,"data") as FourMetricChartData; canvas=chart.visualEvidence; raw=chart.points
                            val status=root(activity).findViewWithTag<TextView>("product/history/chart-status").text.toString()
                            val expectedStatus=if(expected.isEmpty()) activity.getString(R.string.history_no_samples) else activity.getString(
                                if(source.observation.status=="ENDED") R.string.history_samples_complete else R.string.history_samples_partial,expected.size.toString())
                            check(status==expectedStatus)
                            if(page=="history") check((field(activity,"selectedIds") as Map<*,*>)[false]==id)
                            else check(activity.intent.getStringExtra("shotId")==id)
                            fun displayed(role: String)=root(activity).findViewWithTag<TextView>("product/history/$role/number").text.toString()
                            if(case=="unknown-observation") {
                                check(displayed("elapsed")=="—" && displayed("weight")=="—") { "M5 unknown record facts may not be replaced with chart-end facts" }
                            }
                            if(case=="missing-samples") {
                                check(displayed("elapsed")=="30.0" && displayed("weight")=="36.00" && displayed("sampled-water")=="—")
                            }
                            proof.put("sampleStateDisclosure",status).put("displayedElapsed",displayed("elapsed"))
                                .put("displayedWeight",displayed("weight")).put("displayedLastSampleWater",displayed("sampled-water"))
                        }
                        check(raw==expected) { "M5 late reader or substituted samples for $page/$case" }
                        if(case=="no-notes") {
                            check(source.notes==BrewJournal.Notes())
                            if(page=="comparison") {
                                val row=root(activity).findViewWithTag<ViewGroup>("comparison-summary-row-2")
                                val taste=row.getChildAt(1) as TextView
                                check(taste.text.toString()=="—" && !taste.isClickable) { "M5 comparison blank first-cup taste must not carry second-cup notes" }
                                proof.put("blankNotesPlaceholder",taste.text.toString()).put("blankTasteDisclosureDisabled",true)
                            } else {
                                val placeholders=nodes(root(activity)).filterIsInstance<TextView>().count { it.text.toString()==activity.getString(R.string.journal_optional) }
                                check(placeholders>0) { "M5 blank legal notes must not display prior cup notes" }
                                proof.put("blankNotesPlaceholderCount",placeholders)
                            }
                        }
                        if(case=="unknown-observation") {
                            check(source.observation.status=="UNKNOWN" && source.observation.elapsedMs==null && source.observation.endedAtMs==null && source.observation.weightHundredthsGram==null)
                            val warning=requireNotNull(root(activity).findViewWithTag<TextView>(if(page=="comparison") "product/comparison/warning" else "product/history/warning"))
                            check(warning.text.toString()==activity.getString(R.string.history_unknown_warning) && warning.isShown &&
                                warning.isAttachedToWindow && warning.rootView===root(activity).rootView &&
                                (page=="comparison" || whole(observation(warning))))
                            proof.put("unknownWarning",observation(warning)).put("unknownIsNotEnded",true)
                        }
                        val series=requireNotNull(data.series.firstOrNull { it.metric==metric })
                        when(case) {
                            "missing-samples"->check(raw.isEmpty() && series.current.isEmpty() && data.observedThroughMs==null)
                            "sample-gap"->{
                                check(series.current.size==2 && series.current[0].last().elapsedMs==3000L && series.current[1].first().elapsedMs==8000L)
                                check(data.waterResetsMs.isEmpty())
                            }
                            "water-reset"->{
                                check(data.waterResetsMs==listOf(15000L) && series.current.size==2 && series.current[0].last().elapsedMs==14000L && series.current[1].first().elapsedMs==15000L)
                            }
                        }
                        val paths=(0 until canvas.length()).map(canvas::getJSONObject).filter {
                            it.optString("operation")=="path" && it.optString("metric")==metric.name && !it.optBoolean("reference") }
                        check(paths.size==series.current.size) { "M5 actual Canvas paths must preserve missing/reset/gap segments: $page/$case" }
                        paths.zip(series.current).forEach { (path,segment) -> check(path.getJSONArray("verticesPx").length()==segment.size) }
                        proof.put("actualRawSamples",pointRecords(raw)).put("metric",metric.name)
                            .put("actualProjectedSegments",JSONArray(series.current.map { segment -> JSONArray(segment.map { it.elapsedMs }) }))
                            .put("actualWaterResetTimesMs",JSONArray(data.waterResetsMs)).put("actualCanvasCurrentMetricPathCount",paths.size)
                            .put("actualCanvasCurrentMetricVertexCounts",JSONArray(paths.map { it.getJSONArray("verticesPx").length() }))
                            .put("noServiceOrClockOverride",true)
                    }
                    when(page) {
                        "history"->historyPreviewReachability(activity,scope,caseDirectory,expected.isEmpty())
                        "history-detail"->detailReachability(activity,scope,caseDirectory)
                        else->comparisonReachability(activity,scope,caseDirectory)
                    }
                    scope.closeActivities()
                    check(scope.journal.exportJson()==journalBefore && scope.history.entries==historyBefore)
                    proof.put("readOnlyModelsUnchanged",true).put("status","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE")
                } catch(error: Throwable) {
                    caseFailure=error;proof.put("status","FAIL").put("failure",error.stackTraceToString()); throw error
                } finally {
                    try { WindowProofStreamingWriter.writeStrict(proof,File(caseDirectory,"case.json")); persist() }
                    catch(error: Throwable) {
                        if(caseFailure==null)throw error
                        if(caseFailure!==error)caseFailure!!.addSuppressed(error)
                    }
                }
            }
            summary.put("completed",true).put("actualAdditionalActivityCaptures",entries.length()).put("mainConfigurationCountContribution",0)
            return summary
        } catch(error: Throwable) { primaryFailure=error;throw error }
        finally {
            try {persist()} catch(error: Throwable) {
                if(primaryFailure==null)throw error
                if(primaryFailure!==error)primaryFailure!!.addSuppressed(error)
            }
        }
    }
    fun run(arguments: Bundle): Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("extractionHistoryPage")); check(page in setOf("extraction","history","history-detail","comparison"))
        val source=requireNotNull(arguments.getString("sourceSha")); check(source.matches(Regex("[0-9a-f]{40}")))
        val theme=requireNotNull(arguments.getString("sampleTheme")); check(theme in setOf("light","dark"))
        val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat(); check(scale in setOf(1f,1.3f))
        val profile="tablet-user-landscape-$theme-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"extraction-history-visual-migration/$source/$profile/$page").apply { check(isDirectory || mkdirs()) }
        val reference=when(page) {
            "extraction"->"dab980016ff32a5134e1d6ac767c0afad7b2edc1aed741dbac8e5c7e5f9741f2"
            "history"->"f3b9d375ed73a445f9a00752c7a213ca79b1f8c69b7ac327af03062881384283"
            "history-detail"->"7eaaee545d1cf6b13402d210e49b71453d26f6aef47c6a4a876e9875574f5a6d"
            else->"7c7f43ebf4ce7508ab302c2aef75753e930dbde196c33b799e2d95c1d6832269" }
        check(arguments.getString("referencePngSha256")==reference)
        val manifest=JSONObject().put("sourceSha",source).put("sourceDirty",arguments.getString("sourceDirty"))
            .put("headSha",arguments.getString("headSha")).put("apkSha256",arguments.getString("apkSha"))
            .put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("referenceDesignPngSha256",reference).put("referenceIdentitySource","host_hash_of_committed_original_design_not_APK_asset")
            .put("profile",profile).put("page",page).put("realProductActivity",true).put("specimenOnly",false).put("visualAcceptance","ASTRA_PENDING")
            .put("pid",android.os.Process.myPid()).put("scopePolicy","M5_10Lazy_owned_samples_and_readers; no_Home_no_legacy_IO")
            .put("pendingSpecialCases",JSONArray(extractionPendingSpecialCases))
        var scope: Scope?=null; var failure: Throwable?=null
        try {
            val active=Scope(directory,page); scope=active; active.prepare(); active.install(theme=="dark")
            if(page=="extraction") active.beginExtraction()
            File(directory,"fixture.json").writeText(active.fixture.toString(2))
            val activity=when(page) {
                "extraction"->start(ExtractionActivity::class.java)
                "history"->start(HistoryActivity::class.java).also { selectHistory(it,active.firstId) }
                "history-detail"->start(HistoryDetailActivity::class.java) { it.putExtra("shotId",active.firstId) }
                else->start(BrewComparisonActivity::class.java) { it.putExtra(BrewComparisonActivity.FIRST_SHOT_ID,active.firstId).putExtra(BrewComparisonActivity.SECOND_SHOT_ID,active.secondId) }
            }
            if(page=="extraction") await("actual Extraction bound to the real running Mock owner") {
                val owner=field(activity,"service") as? MobileService
                owner!=null && owner.shotState==ExtractionState.RUNNING &&
                    (field(activity,"chart") as ExtractionOverlayView).points.lastOrNull()?.elapsedMs==24600L
            } else chartReady(activity)
            val captureStartedAtRealMs=SystemClock.elapsedRealtime()
            val captured=capture(activity,directory)
            val captureFinishedAtRealMs=SystemClock.elapsedRealtime()
            val image=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,image)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(image.outWidth,image.outHeight))).put("initialFrameSync",captured.proof)
            check(image.outWidth==2560 && image.outHeight==1600)
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use { input ->
                val digest=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(8192)
                while(true) { val count=input.read(buffer); if(count<0) break; digest.update(buffer,0,count) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            check(font==TaskProductVisualStyle.FONT_SHA); manifest.put("fontSha256",font)
            main {
                val window=captured.target; manifest.put("window",window)
                if(page=="history")requireEmbeddedHistoryInitial(activity,directory,window.getJSONObject("embeddedHistoryPreview"),active.expectedSamples(active.firstId),emptyList())
                if(page=="history-detail")requireStandaloneHistoryPlot(activity,directory,window.getJSONObject("standaloneHistoryPlot"),false)
                val charts=window.getJSONArray("charts")
                check(charts.length()>0 && (0 until charts.length()).all { charts.getJSONObject(it).getJSONArray("canvasEvidence").length()>0 }) {
                    "M5 main page must retain a real readable actual-draw chart, not a short-height placeholder"
                }
                check(window.getDouble("fontScale").toFloat()==scale && window.getString("uiModeNightMeaning")==theme &&
                    window.getInt("densityDpi")==360 && window.getString("locale").startsWith("zh") && !window.getBoolean("imeVisible"))
            }
            when(page) {
                "extraction"->{active.verifyExtraction(activity,captureStartedAtRealMs,captureFinishedAtRealMs)
                    manifest.put("chartHeaderReachability",extractionChartHeaderReachability(activity,active,directory,captured))
                    extractionReachability(activity,directory)}
                "history"->{historyPreviewReachability(activity,active,directory);savedHistoryBusiness(activity,active,directory)}
                "history-detail"->detailReachability(activity,active,directory)
                else->{comparisonReachability(activity,active,directory);comparisonBusiness(activity,active,directory)}
            }
            manifest.put("layoutStatus","PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
            if(page=="extraction") {
                // Each special state owns a fresh scoped Service and Activity. The
                // reviewed RUNNING main case is fully cleaned before the first one.
                active.cleanup();scope=null
                manifest.put("isolationStatus","PASS_ISOLATION_ONLY")
                manifest.put("extractionSpecialStates",extractionSpecialStates(directory,theme,source,profile))
                    .put("completedSpecialCases",JSONArray(extractionPendingSpecialCases))
                    .put("pendingSpecialCases",JSONArray())
            } else {
                manifest.put("historySpecialStates",historySpecialStates(active,page,directory,manifest))
                    .put("pendingSpecialCases",JSONArray(historyPendingSpecialCases))
            }
        } catch(error: Throwable) {
            failure=error; manifest.put("layoutStatus","FAIL").put("failure",error.stackTraceToString())
            scope?.activeActivity()?.let { activity -> try { diagnostic(activity,File(directory,"failure-context")) } catch(secondary: Throwable) { error.addSuppressed(secondary) } }
        } finally {
            try { if(scope!=null) {scope.cleanup();manifest.put("isolationStatus","PASS_ISOLATION_ONLY")}
                else if(!manifest.has("isolationStatus"))manifest.put("isolationStatus","NOT_INSTALLED") }
            catch(error: Throwable) { manifest.put("isolationStatus","FAIL").put("cleanupFailure",error.stackTraceToString())
                if(failure==null) failure=error else failure!!.addSuppressed(error) }
            manifest.put("overallStatus",if(failure==null) "PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            failure?.let { manifest.put("failure",it.stackTraceToString()) }
            manifest.put("fileSha256",JSONObject(directory.walkTopDown().filter { it.isFile && it!=File(directory,"manifest.json") }.associate { it.relativeTo(directory).path to sha(it) }))
            try { WindowProofStreamingWriter.writeStrict(manifest,File(directory,"manifest.json")) }
            catch(error:Throwable) { if(failure==null) failure=error else failure!!.addSuppressed(error) }
        }
        failure?.let { throw it }; return 1
    }
}

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
import io.openhoyi.protocol.ExtractionTelemetry
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
            JSONObject().put("semanticId", text.tag).put("text", text.text).put("fullViewVisible", whole)
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
        fun beginExtraction() {
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
                proof.put("status","FAIL").put("failure",failure.stackTraceToString())
                // Do not restore original delegates while a scoped Activity/service/writer may still hold references.
                throw failure
            } finally { File(directory,"fixture-isolation.json").writeText(proof.toString(2)) }
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
        return JSONObject().put("budget",budget).put("budgetSource","deep_copy_existing_EmbeddedHistoryPreview.productLayoutEvidence")
            .put("chart",historyVisibility(chart)).put("plotView",historyVisibility(plot)).put("explanation",observation(explanation))
            .put("explanationScreenVisibility",historyVisibility(explanation)).put("explanationText",explanation.text.toString())
            .put("samePermanentProducerView",producer.embeddedExplanationView===explanation)
            .put("externalSiblingAfterChart",explanation.parent===content && content.indexOfChild(explanation)==content.indexOfChild(chart)+1)
            .put("sameProjectionSamplingDescription",explanation.text.toString().contains(sampling))
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
        main { File(folder,"geometry.json").writeText(geometry(activity).put("sameCompositorFrameProven",false)
            .put("protocol","unsynchronized_failure_diagnostic_not_protocol2_acceptance").toString(2)) }
    }
    private fun capture(activity: Activity, folder: File): NativeWindowFrameEvidence.CapturedFrame {
        check(folder.isDirectory || folder.mkdirs())
        val result=frames.screenshot(activity,root(activity),File(folder,"window.png")) { geometry(activity) }
        main {
            File(folder,"geometry.json").writeText(geometry(activity).toString(2)); TaskTypographyEvidence.capture(activity,folder)
            val pairs=JSONArray()
            nodes(root(activity)).filterIsInstance<TextView>().filter { (it.tag as? String)?.endsWith("/number")==true }.forEach { number ->
                val prefix=(number.tag as String).removeSuffix("/number")
                val unit=root(activity).findViewWithTag<TextView>("$prefix/unit")
                if(unit!=null && number.isShown && unit.isShown) {
                    val n=IntArray(2); val u=IntArray(2); number.getLocationOnScreen(n); unit.getLocationOnScreen(u)
                    val numberBaseline=n[1]+number.baseline; val unitBaseline=u[1]+unit.baseline
                    check(numberBaseline==unitBaseline) { "M5 actual number/unit baseline mismatch: $prefix" }
                    pairs.put(JSONObject().put("semanticId",prefix).put("numberBaselineOnScreenPx",numberBaseline)
                        .put("unitBaselineOnScreenPx",unitBaseline).put("status","PASS_EXACT_BASELINE_ONLY"))
                }
            }
            File(folder,"baseline-alignment.json").writeText(pairs.toString(2))
        }
        check(result.proof.getInt("protocolVersion")==2 && result.proof.getBoolean("accepted"))
        return result
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
        fun persist() { File(directory,"manifest.json").writeText(summary.toString(2)) }
        persist()
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
                                proof.put("blankNotesPlaceholder",taste.text).put("blankTasteDisclosureDisabled",true)
                            } else {
                                val placeholders=nodes(root(activity)).filterIsInstance<TextView>().count { it.text.toString()==activity.getString(R.string.journal_optional) }
                                check(placeholders>0) { "M5 blank legal notes must not display prior cup notes" }
                                proof.put("blankNotesPlaceholderCount",placeholders)
                            }
                        }
                        if(case=="unknown-observation") {
                            check(source.observation.status=="UNKNOWN" && source.observation.elapsedMs==null && source.observation.endedAtMs==null && source.observation.weightHundredthsGram==null)
                            val warning=requireNotNull(root(activity).findViewWithTag<TextView>(if(page=="comparison") "product/comparison/warning" else "product/history/warning"))
                            check(warning.text.toString()==activity.getString(R.string.history_unknown_warning) && warning.isShown && whole(observation(warning)))
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
                    proof.put("status","FAIL").put("failure",error.stackTraceToString()); throw error
                } finally { File(caseDirectory,"case.json").writeText(proof.toString(2)); persist() }
            }
            summary.put("completed",true).put("actualAdditionalActivityCaptures",entries.length()).put("mainConfigurationCountContribution",0)
            return summary
        } finally { persist() }
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
            .put("pendingSpecialCases",JSONArray(listOf("STARTING","STOP_REQUESTED","UNKNOWN_live","no_scale","stale_scale","no_service_ready","manual","historical_reference_toggle","missing_samples","water_reset","sample_gap","journal_unavailable")))
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
                "extraction"->active.verifyExtraction(activity,captureStartedAtRealMs,captureFinishedAtRealMs)
                "history"->{historyPreviewReachability(activity,active,directory);savedHistoryBusiness(activity,active,directory)}
                "history-detail"->detailReachability(activity,active,directory)
                else->{comparisonReachability(activity,active,directory);comparisonBusiness(activity,active,directory)}
            }
            manifest.put("layoutStatus","PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
            if(page!="extraction") {
                manifest.put("historySpecialStates",historySpecialStates(active,page,directory,manifest))
                    .put("pendingSpecialCases",JSONArray(listOf("STARTING","STOP_REQUESTED","UNKNOWN_live","no_scale","stale_scale","no_service_ready","manual","historical_reference_toggle","journal_unavailable")))
            }
        } catch(error: Throwable) {
            failure=error; manifest.put("layoutStatus","FAIL").put("failure",error.stackTraceToString())
            scope?.activeActivity()?.let { activity -> try { diagnostic(activity,File(directory,"failure-context")) } catch(secondary: Throwable) { error.addSuppressed(secondary) } }
        } finally {
            try { scope?.cleanup(); manifest.put("isolationStatus",if(scope!=null) "PASS_ISOLATION_ONLY" else "NOT_INSTALLED") }
            catch(error: Throwable) { manifest.put("isolationStatus","FAIL").put("cleanupFailure",error.stackTraceToString())
                if(failure==null) failure=error else failure!!.addSuppressed(error) }
            manifest.put("overallStatus",if(failure==null) "PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            failure?.let { manifest.put("failure",it.stackTraceToString()) }
            manifest.put("fileSha256",JSONObject(directory.walkTopDown().filter { it.isFile && it!=File(directory,"manifest.json") }.associate { it.relativeTo(directory).path to sha(it) }))
            File(directory,"manifest.json").writeText(manifest.toString(2))
        }
        failure?.let { throw it }; return 1
    }
}

package io.openhoyi.mobile

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ListView
import android.widget.ScrollView
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

/** M7 real imported-data pages. Legal owned archives only; never a machine controller. */
internal class LegacyVisualMigrationChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val frames = NativeWindowFrameEvidence(test)
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private val readerNames=setOf("legacy-curve-list","legacy-curve-import","legacy-history-list","legacy-history-detail","legacy-history-import")
    private val ownedReaders=linkedSetOf<Thread>()
    private fun observeReaders() { ownedReaders.addAll(Thread.getAllStackTraces().keys.filter { it.isAlive && it.name in readerNames }) }
    private val readerDrainProofs=JSONArray()
    private fun drainReaders(label:String="scope-cleanup", settled:()->Boolean={true}) {
        val started=SystemClock.elapsedRealtime();val deadline=started+10000
        val rounds=JSONArray();val proof=JSONObject().put("label",label).put("timeoutMs",10000).put("rounds",rounds)
        fun snapshot(threads:Collection<Thread>)=JSONArray(threads.map { thread -> JSONObject().put("id",thread.id)
            .put("name",thread.name).put("state",thread.state.name).put("alive",thread.isAlive) })
        fun live()=Thread.getAllStackTraces().keys.filter {it.isAlive && it.name in readerNames}
        var drained=false
        try {
            while(!drained && SystemClock.elapsedRealtime()<deadline) {
                observeReaders()
                val known=ownedReaders.toList();val priorIds=known.map {it.id}.toSet()
                val round=JSONObject().put("beforeJoin",snapshot(known)).put("elapsedBeforeJoinMs",SystemClock.elapsedRealtime()-started)
                rounds.put(round)
                known.filter {it.isAlive}.forEach {thread ->
                    val remaining=deadline-SystemClock.elapsedRealtime();check(remaining>0) {"M7 reader drain total budget exhausted: $label"}
                    thread.join(remaining)
                    check(!thread.isAlive && SystemClock.elapsedRealtime()<=deadline) {"M7 owned reader did not terminate within the original total budget: ${thread.name}/${thread.id}"}
                }
                var uiSettled=false
                // An import's main callback can start refresh's successor reader. Re-observe after this barrier.
                main {uiSettled=settled()}
                observeReaders()
                val after=live()
                round.put("afterMainBarrier",snapshot(after)).put("newThreadIds",JSONArray(ownedReaders.filter {it.id !in priorIds}.map {it.id}))
                    .put("uiSettled",uiSettled).put("remainingMs",deadline-SystemClock.elapsedRealtime())
                if(after.isEmpty() && uiSettled) {
                    // Drain a completion posted by a reader that exited between the first barrier and observation.
                    main {uiSettled=settled()}
                    observeReaders()
                    val confirmed=live()
                    round.put("afterConfirmationBarrier",snapshot(confirmed)).put("confirmedUiSettled",uiSettled)
                    drained=confirmed.isEmpty() && uiSettled
                }
            }
            check(drained && SystemClock.elapsedRealtime()<=deadline) {"M7 reader/main-callback lifecycle did not settle in original 10s: $label"}
            check(Thread.getAllStackTraces().keys.none { it.isAlive && it.name in readerNames })
            val completed=SystemClock.elapsedRealtime();check(completed<=deadline) {"M7 final reader guard exceeded original total budget"}
            proof.put("status","PASS_DRAIN_ONLY").put("finalNoLiveReaderGuard",true).put("completionElapsedMs",completed-started)
        } catch(error:Throwable) {
            proof.put("status","FAIL").put("failure",error.stackTraceToString()).put("readersAtFailure",snapshot(live()))
            throw error
        } finally {
            proof.put("elapsedMs",SystemClock.elapsedRealtime()-started);readerDrainProofs.put(proof)
        }
    }
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            observeReaders()
            var ready = false; main { ready = condition() }
            if (ready && SystemClock.elapsedRealtime() <= deadline) return
            check(SystemClock.elapsedRealtime() < deadline) { "M7 timeout: $description" }
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
    private fun businessDisk(): Map<String, String> {
        val data=File(context.applicationInfo.dataDir)
        return listOf(File(data,"files"),File(data,"shared_prefs"),File(data,"databases"),context.noBackupFilesDir)
            .distinctBy { it.canonicalPath }.flatMap { folder -> folder.walkTopDown().filter { it.isFile &&
                !(folder.name=="shared_prefs" && it.name in setOf("appearance.xml","appearance.xml.bak")) }
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
            JSONObject().put("semanticId", text.tag).put("text", text.text).put("fullViewVisible", textVisibility.getBoolean("complete"))
                .put("visibility",textVisibility)
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
        private fun whitePrefFiles(): Map<String,String> = listOf("appearance.xml","appearance.xml.bak").mapNotNull { name ->
            File(File(context.applicationInfo.dataDir,"shared_prefs"),name).takeIf { it.isFile }?.let { name to sha(it) }
        }.toMap()
        private var installed = false
        private val ownedDirectory = File(context.cacheDir, "legacy-visual-${UUID.randomUUID()}")
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
        private val sampleFailures=java.util.concurrent.CopyOnWriteArrayList<Throwable>()
        lateinit var fixture: JSONObject
            private set
        private val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { tracked.add(activity) }
            override fun onActivityDestroyed(activity: Activity) { destroyed.add(activity) }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        val curveFile=File(ownedDirectory,"legacy-curves.json")
        val historyFile=File(ownedDirectory,"legacy-history.json")
        val legacyCurves=LegacyCurveStore(curveFile)
        val legacyHistory=LegacyHistoryStore(historyFile)
        val mainId="m7-main-legacy-shot"
        val mainShot=LegacyShot(mainId,1790721120000L,30,0,"经典浓缩",
            JSONObject().put("source","synthetic legacy archive").put("unknownFixtureKey","preserve-me").toString(),
            (0..30).map { second -> LegacyPoint(second.toDouble(),
                if(second<5) second*1.8 else if(second>25) (30-second)*1.8 else 9.0,
                if(second<5) second*.6 else if(second>25) (30-second)*.6 else 3.0,
                second*.37, -second*.11) })
        val mainCurves: ByteArray get() = curveArchive()
        fun curveArchive(long: Boolean=false, missing: Boolean=false): ByteArray {
            val items=JSONArray((0 until 41).map { index -> JSONObject().put("name",
                if(long && index==0) "经典浓缩长名称".repeat(15) else if(index==0) "经典浓缩" else "旧版配方 %02d".format(index))
                .put("category",if(long && index==0) "长分类".repeat(20) else "espresso")
                .put("factory",index%2==0).put("unknownFixtureKey",JSONObject().put("keep",index))
                .apply { if(!missing || index!=0) put("temp",93).put("flow",60).put("weight",360).put("seg",4) } })
            return JSONObject().put("format","openhoyi-legacy-curves-v1").put("factoryVersion",3)
                .put("categoryConfig",JSONObject().put("labels",JSONObject().put("espresso","浓缩")))
                .put("unknownRoot",true).put("items",items).toString().toByteArray(Charsets.UTF_8)
        }
        val shots get() = listOf(mainShot)+(1..40).map { index -> mainShot.copy(id="m7-legacy-$index",
            createdAtMs=mainShot.createdAtMs-index*3600000L,profileName="旧版萃取 %02d".format(index)) }
        fun prepare() {
            check(!::logs.isInitialized && !::samples.isInitialized)
            check(MobileMonotonicClock.mockOwnerIdentity()==null) { "M7 must not inherit a Mock clock owner" }
            check(Thread.getAllStackTraces().keys.none { it.isAlive && it.name in readerNames })
            logs=TraceStore(File(ownedDirectory,"traces"))
            samples=ShotSamplesRepository(File(ownedDirectory,"samples")) { sampleFailures.add(it) }
            val factory=FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val wire=FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"),factory,context.assets.open("factory_slot_wire_v1.tsv"))
            curves=CurveLibrary(factory,wire,{ custom.items() },true)
            history=ShotHistory(object: ShotHistory.Storage { var value=""
                override fun read()=value; override fun write(value:String) { this.value=value }
            },now={1790721120000L},newId={error("M7 read-only archive must not create a native shot")},onObservedUse={ entry ->
                usage.record(entry.id,entry.curveId,entry.startedAtMs)
            },onEntryChanged={ entry -> journal.observe(entry.journalObservation())
                if(preparation.current()?.shotId==entry.id)preparation.associatePending(journal,inventory)
            })
            legacyCurves.import(mainCurves.inputStream())
            legacyHistory.import(LegacyHistoryCodec.encode(shots).byteInputStream())
            check(legacyCurves.load()?.curves?.size==41 && legacyHistory.list()==shots)
            fixture=JSONObject().put("page",page).put("synthetic",true).put("hardwareMeasurement",false)
                .put("source","synthetic legacy archive; real LegacyCurve/History codec and owned cache stores")
                .put("mainLegacyId",mainId).put("historyArchive",JSONObject(LegacyHistoryCodec.encode(shots)))
                .put("curveArchive",JSONObject(String(mainCurves,Charsets.UTF_8)))
                .put("noServiceNoMockClock",true).put("unknownFieldsAreNotNativeMetrics",true)
        }
        fun install(dark: Boolean) {
            main {
                check(MobileMonotonicClock.mockOwnerIdentity()==null) { "M7 clock owner appeared before installation" }
                check(services().isEmpty() && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) {
                    "Fresh isolated process required: service or attached window already exists"
                }
                delegateNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>
                    saved[name]=lazy; originalInitialized[name]=lazy.isInitialized() }
                legacyNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>; legacy[name]=lazy to lazy.isInitialized() }
                // A fresh process must not have acquired any original store before this entire group is installed.
                check(saved.values.none { it.isInitialized() } && legacy.values.none { it.first.isInitialized() }) { "Original delegate initialized before fixture isolation" }
                oldLanguage = field(app,"languagePreferences"); oldFailure=field(app,"journalObservationFailure")
                listOf("appearance").forEach { savedPrefs[it]=context.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap() }
                diskBefore=businessDisk(); whitePrefFileBefore=whitePrefFiles()
                app.registerActivityLifecycleCallbacks(lifecycle)
                val values = mapOf("feedbackPreferences" to feedback,"logs" to logs,"curves" to curves,"samples" to samples,"history" to history,
                    "customCurvesResult" to Result.success(custom),"curveUsageResult" to Result.success(usage),"beanInventoryResult" to Result.success(inventory),
                    "beanPreparationResult" to Result.success(preparation),"journalStoreResult" to Result.success(journal),
                    "legacyCurves" to legacyCurves,"legacyHistory" to legacyHistory)
                // Set installed before mutation, so a partial install still runs the same strict finally cleanup.
                installed=true
                values.forEach { (name,value) -> write(app,name+"\$delegate",lazyOf(value)) }
                write(app,"journalObservationFailure",null)
                write(app,"languagePreferences",AppLanguagePreference(object : AppLanguagePreference.Storage {
                    override fun read() = "zh-Hans"; override fun write(tag: String) = tag == "zh-Hans" }))
                check(app.legacyCurves===legacyCurves && app.legacyHistory===legacyHistory)
                check(app.feedbackPreferences === feedback && app.logs === logs && app.curves === curves && app.samples === samples && app.history === history)
                check(app.customCurvesResult.getOrThrow() === custom && app.curveUsageResult.getOrThrow() === usage && app.beanInventoryResult.getOrThrow() === inventory &&
                    app.beanPreparationResult.getOrThrow() === preparation && app.journalResult.getOrThrow() === journal)
                check(context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dark",dark).commit())
            }
        }
        private var providerSession: String?=null
        fun provider(method: String, extras: Bundle?=null)=requireNotNull(context.contentResolver.call(
            android.net.Uri.parse("content://${CurveFixtureProvider.AUTHORITY}"),method,requireNotNull(providerSession),extras))
        fun providerUri(bytes: ByteArray): android.net.Uri {
            if(providerSession==null) {
                check(context.checkSelfPermission(CurveFixtureProvider.PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
                check(context.packageManager.checkSignatures(context.packageName,test.context.packageName)==android.content.pm.PackageManager.SIGNATURE_MATCH)
                providerSession=UUID.randomUUID().toString()
                provider("create",Bundle().apply { putString("document",JSONObject().put("kind","openhoyi.local-curve").toString()) })
            }
            val uri=CurveFixtureProvider.uri(requireNotNull(providerSession),"output.json")
            requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { it.write(bytes) }
            return uri
        }
        fun activeActivity(): Activity? = tracked.lastOrNull { !it.isDestroyed }
        fun cleanup() {
            val proof = JSONObject().put("scopedOwnedDirectory",ownedDirectory.absolutePath)
            try {
                if (installed) {
                    observeReaders()
                    main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
                    await("all fixture Activities and dialogs destroyed") {
                        tracked.all { it.isDestroyed && it in destroyed } && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()
                    }
                    main { check(services().isEmpty()) { "M7 no-service scope unexpectedly holds a service; original delegates not restored" } }
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) { "M7 fixture dialog/window remains after Activity destruction" }
                    drainReaders()
                    proof.put("ownedReaderThreads",JSONArray(ownedReaders.map { JSONObject().put("id",it.id).put("name",it.name).put("alive",it.isAlive) }))
                        .put("allOwnedReadersExitedAndMainBarrier",true)
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("noServiceBeforeOriginalDelegateRestore",true).put("mainQueueBarrierAfterDestroy",true)
                }
                proof.put("ownedSampleRepositoryCreated",::samples.isInitialized).put("ownedTraceCreated",::logs.isInitialized)
                if (::samples.isInitialized) {
                    val writer=field(samples,"writer") as ExecutorService
                    writer.shutdown(); check(writer.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped sample writer did not terminate" }
                }
                if (::logs.isInitialized) {
                    logs.close(); check(logs.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped trace writer did not terminate" }
                    val status=logs.status; val tokens=status.split(' ')
                    proof.put("scopedTraceStatusAfterTermination",status)
                    check(tokens.first()=="closed" && "dropped=0" in tokens && "errors=0" in tokens &&
                        tokens.none { it.startsWith("lastError=") }) { "M7 scoped trace writer reported an error or drop: $status" }
                }
                check(MobileMonotonicClock.mockOwnerIdentity()==null) { "M7 unexpected Mock clock owner at cleanup; original delegates not restored" }
                proof.put("mockClockOwnerAbsentBeforeAndAfter",true).put("scopedWritersTerminated",true)
                check(sampleFailures.isEmpty()) { "M7 sample writer failure" }
                providerSession?.let { provider("cleanup"); providerSession=null }
                proof.put("clipboard","NOT_READ_OR_WRITTEN").put("providerSessionRemoved",providerSession==null)
                if (installed) main {
                    saved.forEach { (name,lazy) -> write(app,name+"\$delegate",lazy) }
                    legacy.forEach { (name,pair) -> write(app,name+"\$delegate",pair.first) }
                    write(app,"languagePreferences",oldLanguage); write(app,"journalObservationFailure",oldFailure)
                    savedPrefs.forEach { (name,values) ->
                        val preferences = context.getSharedPreferences(name,Context.MODE_PRIVATE)
                        val keys = listOf("dark")
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
                        .put("whitePrefPhysicalBytesMayDiffer","only appearance.dark typed restoration; no file deletion or byte rollback")
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
        val content=root(activity); val origin=IntArray(2); content.getLocationOnScreen(origin)
        val insets=requireNotNull(content.rootWindowInsets); val bars=insets.getInsets(WindowInsets.Type.systemBars())
        val config=activity.resources.configuration
        return JSONObject().put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("appOriginPx",JSONArray(origin.toList())).put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("systemInsetsPx",JSONArray(listOf(bars.left,bars.top,bars.right,bars.bottom)))
            .put("imeVisible",insets.isVisible(WindowInsets.Type.ime())).put("densityDpi",config.densityDpi)
            .put("fontScale",config.fontScale).put("locale",config.locales.toLanguageTags())
            .put("uiModeNightMeaning",if(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light")
            .put("naturalBudgetEvidence","actual permanent view rects and actual Paint plotBounds; no synthetic dp viewport claim")
            .put("charts",JSONArray(nodes(content).filterIsInstance<LegacyShotChartView>().map { chart ->
                JSONObject().put("view",observation(chart)).put("actualDraw",JSONObject(chart.visualEvidence.toString())) }))
            .put("scrolls",JSONArray(nodes(content).filterIsInstance<ScrollView>().map { JSONObject().put("tag",it.tag).put("x",it.scrollX).put("y",it.scrollY) }))
            .put("nodes",JSONArray(nodes(content).filter { it.tag is String }.map { observation(it) }))
    }
    private fun diagnostic(activity: Activity, folder: File) {
        check(folder.isDirectory || folder.mkdirs())
        lateinit var before: JSONObject; main { before=geometry(activity) }
        val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
        try { File(folder,"window.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } }
        finally { bitmap.recycle() }
        lateinit var after: JSONObject; main { after=geometry(activity) }
        File(folder,"geometry.json").writeText(JSONObject().put("before",before).put("after",after)
            .put("geometryUnchanged",before.toString()==after.toString()).put("sameCompositorFrameProven",false).toString(2))
    }
    private fun stableWindow(activity: Activity, directory: File, phase: String) {
        val deadline=SystemClock.elapsedRealtime()+5000; val samples=JSONArray()
        var prior: String?=null; var consecutive=0
        try {
            while(SystemClock.elapsedRealtime()<deadline) {
                frames.committedFrame(root(activity))
                lateinit var value: JSONObject; var pending=true
                main { value=geometry(activity); pending=nodes(root(activity)).any { it.isShown && it.isLayoutRequested } }
                val signature=value.toString(); val within=SystemClock.elapsedRealtime()<=deadline
                consecutive=if(!pending && signature==prior) consecutive+1 else if(!pending) 1 else 0
                samples.put(JSONObject().put("geometry",value).put("pendingLayout",pending)
                    .put("consecutiveUnchanged",consecutive).put("withinBudget",within))
                prior=signature
                if(consecutive>=3 && within) return
            }
            error("M7 $phase geometry did not stabilize on three actual frames within 5000ms")
        } finally { File(directory,"$phase-window-stability.json").writeText(JSONObject().put("samples",samples)
            .put("acceptancePolicy","geometry_stability_only; pending_only_isShown_tree; hidden_nodes_do_not_block; not_waiting_for_visibility_or_business_assertion_success").toString(2)) }
    }

    private fun ready(activity: Activity) = activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested &&
        when(activity) {
            is LegacyCurveActivity -> (field(activity,"list") as ListView).visibility==View.VISIBLE || (field(activity,"emptyState") as View).visibility==View.VISIBLE
            is LegacyHistoryActivity -> (field(activity,"list") as ListView).visibility==View.VISIBLE || (field(activity,"emptyState") as View).visibility==View.VISIBLE
            is LegacyHistoryDetailActivity -> nodes(root(activity)).any { it is LegacyShotChartView } ||
                nodes(root(activity)).filterIsInstance<TextView>().any { it.tag=="legacy/detail/status" && it.text.toString()!=activity.getString(R.string.legacy_history_detail_loading) }
            else -> true
        }
    private fun start(type: Class<out Activity>, id: String?=null): Activity {
        val intent=Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        id?.let { intent.putExtra("legacyId",it) }
        return test.startActivitySync(intent).also { activity -> await("${type.simpleName} loaded") { ready(activity) } }
    }
    private fun close(activity: Activity) {
        main { activity.finish() }; await("${activity.javaClass.simpleName} destroyed") { activity.isDestroyed }
    }
    private fun recreate(activity: Activity): Activity {
        val monitor=test.addMonitor(activity.javaClass.name,null,false)
        return try {
            main { activity.recreate() }
            requireNotNull(test.waitForMonitorWithTimeout(monitor,10000)).also { await("recreated archive loaded") { ready(it) } }
        } finally { test.removeMonitor(monitor) }
    }
    private fun action(activity: Activity, id: Int)=nodes(root(activity)).filterIsInstance<TextView>()
        .single { it.isClickable && it.isShown && it.text.toString()==activity.getString(id) }
    private fun click(activity: Activity, id: Int) { main { val view=action(activity,id)
        check(view.isEnabled && whole(observation(view)) && view.height>=HoyiUi.dp(activity,48)); check(view.performClick())
    } }
    private fun captureState(activity: Activity, directory: File, phase: String) {
        val folder=File(directory,"states/$phase").apply { check(isDirectory || mkdirs()) }
        stableWindow(activity,folder,"before-capture")
        frames.screenshot(activity,root(activity),File(folder,"window.png")) { geometry(activity) }
        main { File(folder,"geometry.json").writeText(geometry(activity).toString(2)); TaskTypographyEvidence.capture(activity,folder) }
    }
    private fun baselinePairs(activity: Activity): JSONArray {
        val values=nodes(root(activity)).filterIsInstance<TextView>().filter { it.tag?.toString()?.endsWith("/number")==true }
        return JSONArray(values.map { number ->
            val id=number.tag.toString().removeSuffix("/number")
            val unit=nodes(root(activity)).filterIsInstance<TextView>().singleOrNull { it.tag=="$id/unit" }
            fun baseline(view: TextView): Int { val origin=IntArray(2); view.getLocationOnScreen(origin); return origin[1]+view.baseline }
            JSONObject().put("id",id).put("number",number.text).put("unit",unit?.text ?: JSONObject.NULL)
                .put("numberBaselinePx",baseline(number)).put("unitBaselinePx",unit?.let(::baseline) ?: JSONObject.NULL)
                .put("numberVisible",visibility(number)).put("unitVisible",unit?.let(::visibility) ?: JSONObject.NULL)
                .put("status",if(unit==null) "NO_UNIT" else if(baseline(number)==baseline(unit)) "PASS_BASELINE_ONLY" else "FAIL")
        })
    }
    private fun validateChart(activity: Activity, expected: LegacyShot) {
        val chart=nodes(root(activity)).filterIsInstance<LegacyShotChartView>().single()
        check(chart.isShown && chart.points==expected.points)
        val evidence=chart.visualEvidence
        check(evidence.getDouble("plotHeightDp")>=160.0) { "M7 actual plot, excluding axes/legend, under 160dp" }
        val operations=evidence.getJSONArray("operations")
        check(evidence.getInt("pointCount")==expected.points.size)
        val text=(0 until operations.length()).map { operations.getJSONObject(it) }.filter { it.getString("operation")=="text" }
        check(text.isNotEmpty() && text.all { it.getInt("weight")==400 && !it.getBoolean("fakeBold") &&
            it.getDouble("textSizePx").toFloat()==TaskProductVisualStyle(activity).sp(14) })
        val paths=(0 until operations.length()).map { operations.getJSONObject(it) }.filter { it.getString("operation")=="path" }
        check(paths.size==2 && paths.all { it.getJSONArray("verticesPx").length()==expected.points.size })
        // These points intentionally contain oldWFlow/oldWTrend. Only pressure and machine water are ever vertices.
        val box=evidence.getJSONArray("plotBoundsPx")
        paths.forEachIndexed { series,path ->
            val maximum=if(series==0) maxOf(12.0,expected.points.maxOf { it.pressureBar }) else maxOf(6.0,expected.points.maxOf { it.waterFlow })
            expected.points.forEachIndexed { index,point ->
                val value=if(series==0) point.pressureBar else point.waterFlow
                val predicted=box.getDouble(3).toFloat()-(box.getDouble(3).toFloat()-box.getDouble(1).toFloat())*(value/maximum).toFloat()
                check(path.getJSONArray("verticesPx").getJSONArray(index).getDouble(1).toFloat()==predicted) { "M7 wrong physical series" }
            }
        }
    }
    private fun list(activity: Activity)=field(activity,"list") as ListView
    private fun listState(activity: Activity): JSONObject {
        val list=list(activity)
        return JSONObject().put("first",list.firstVisiblePosition).put("offset",list.getChildAt(0)?.top ?: 0)
            .put("count",list.count).put("selected",if(activity is LegacyCurveActivity) field(activity,"selectedIndex") else field(activity,"selectedId"))
    }
    private fun gesture(view: View) {
        val visible=Rect()
        main { check(view.isShown && view.getGlobalVisibleRect(visible) && visible.width()>32 && visible.height()>64) }
        val x=visible.exactCenterX(); val start=visible.top+visible.height()*.82f; val end=visible.top+visible.height()*.22f
        val down=SystemClock.uptimeMillis()
        fun event(action: Int,y: Float) {
            val value=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).apply { source=android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { check(test.uiAutomation.injectInputEvent(value,true)) { "M7 real local list gesture rejected" } } finally { value.recycle() }
        }
        event(android.view.MotionEvent.ACTION_DOWN,start)
        for(step in 1..16) { SystemClock.sleep(25); event(android.view.MotionEvent.ACTION_MOVE,start+(end-start)*step/16) }
        // Real stationary MOVE events remove fling velocity before UP; delays are gesture timing, not acceptance waits.
        repeat(4) { SystemClock.sleep(50); event(android.view.MotionEvent.ACTION_MOVE,end) }
        event(android.view.MotionEvent.ACTION_UP,end)
    }
    private fun nativeInvariant(scope: Scope)=JSONObject()
        .put("history",JSONArray(scope.history.entries.map { it.toString() }))
        .put("journal",scope.journal.exportJson()).put("inventory",JSONArray(scope.inventory.events().map { it.toString() }))
        .put("custom",JSONArray(scope.custom.list().map { it.toJson() }))
        .put("preparation",scope.preparation.current()?.toString() ?: JSONObject.NULL).toString()
    private fun stableList(activity: Activity, directory: File, phase: String): JSONObject {
        stableWindow(activity,directory,phase)
        lateinit var state: JSONObject; main { state=listState(activity) }
        File(directory,"$phase-list-state.json").writeText(state.toString(2)); return state
    }
    private fun listBusiness(initial: Activity, scope: Scope, directory: File) {
        val frozen=nativeInvariant(scope)
        var page=initial
        val before=stableList(page,directory,"list-before-gesture")
        gesture(list(page))
        val moved=stableList(page,directory,"list-after-gesture")
        check(moved.getInt("first")>before.getInt("first") && moved.getInt("count")==41)
        main {
            val target=list(page).firstVisiblePosition+1
            val child=requireNotNull(list(page).getChildAt(1)); check(whole(observation(child)))
            check(list(page).performItemClick(child,target,list(page).adapter.getItemId(target)))
        }
        stableWindow(page,directory,"selected-after-real-row-click")
        val selected=stableList(page,directory,"selected-list")
        if(page is LegacyHistoryActivity) {
            val expected=scope.legacyHistory.list().single { it.id==field(page,"selectedId") }
            main { validateChart(page,expected) }
            val monitor=test.addMonitor(LegacyHistoryDetailActivity::class.java.name,null,false)
            val detail=try {
                click(page,R.string.ui_full_details)
                requireNotNull(test.waitForMonitorWithTimeout(monitor,10000))
            } finally { test.removeMonitor(monitor) }
            await("selected legacyId detail ready") { ready(detail) }
            check(detail.intent.getStringExtra("legacyId")==expected.id)
            captureState(detail,directory,"selected-independent-detail")
            main { validateChart(detail,expected) }
            close(detail); await("real list return") { ready(page) }
        } else {
            val index=field(page,"selectedIndex") as Int
            val curve=requireNotNull(scope.legacyCurves.load()).curves[index]
            val fingerprint=MessageDigest.getInstance("SHA-256").digest(curve.rawJson.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            check(field(page,"selectedFingerprint")==fingerprint)
        }
        val returned=stableList(page,directory,"list-return")
        check(returned.toString()==selected.toString()) { "M7 selected row/list offset changed on return" }
        page=recreate(page)
        val rebuilt=stableList(page,directory,"list-recreated")
        check(rebuilt.toString()==selected.toString()) { "M7 selected id or exact first-row offset changed on recreation" }
        captureState(page,directory,"list-restored")
        check(nativeInvariant(scope)==frozen && services().isEmpty())
        File(directory,"continuous-list-business.json").writeText(JSONObject().put("count",41)
            .put("realTouchGesture",true).put("before",before).put("afterGesture",moved).put("selection",selected)
            .put("return",returned).put("recreated",rebuilt).put("exactOffsetAndSelectionRestored",true)
            .put("nativeModelsUnchanged",true).toString(2))
        close(page)
    }
    private fun callback(activity: Activity, request: Int, result: Int, uri: android.net.Uri?) {
        main {
            val method=activity.javaClass.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }
            method.invoke(activity,request,result,uri?.let { Intent().setData(it) })
            observeReaders()
        }
        drainReaders("activity-result-${activity.javaClass.simpleName}-$request-$result") {
            if(activity is LegacyCurveActivity) {
                val status=(field(activity,"count") as TextView).text.toString()
                val pending=setOf(R.string.legacy_curve_loading_initial,R.string.legacy_curve_loading,R.string.legacy_curve_checking).map {activity.getString(it)}
                status !in pending && ((field(activity,"list") as View).visibility==View.VISIBLE ||
                    (field(activity,"emptyState") as View).visibility==View.VISIBLE)
            } else true
        }
    }
    private fun sourcesBusiness(scope: Scope, directory: File) {
        var legacy=start(LegacyHistoryActivity::class.java)
        val sourceRows=JSONArray()
        for((id,journal) in listOf(R.string.journal_tab to true,R.string.history_local_tab to false)) {
            val frozen=nativeInvariant(scope)
            val original=stableList(legacy,directory,"source-$journal-before")
            val monitor=test.addMonitor(HistoryActivity::class.java.name,null,false)
            var native=try {
                click(legacy,id)
                requireNotNull(test.waitForMonitorWithTimeout(monitor,10000))
            } finally { test.removeMonitor(monitor) }
            await("real native source ready") { ready(native) }
            check(field(native,"showJournal")==journal)
            native=recreate(native)
            check(field(native,"showJournal")==journal)
            captureState(native,directory,"source-$journal-recreated")
            close(native); await("return from real source") { ready(legacy) }
            val returned=stableList(legacy,directory,"source-$journal-return")
            check(returned.toString()==original.toString() && nativeInvariant(scope)==frozen)
            sourceRows.put(JSONObject().put("journal",journal).put("realRouteAndRecreate",true)
                .put("exactReturnState",true).put("nativeModelsUnchanged",true))
        }
        File(directory,"sources-business.json").writeText(sourceRows.toString(2)); close(legacy)
    }
    private fun archiveImports(scope: Scope, directory: File, curve: Boolean): JSONArray {
        val cases=JSONArray(); val nativeBefore=nativeInvariant(scope)
        val page=start(if(curve) LegacyCurveActivity::class.java else HistoryActivity::class.java)
        val request=if(curve) 43 else 42
        val target=if(curve) scope.curveFile else scope.historyFile
        fun checkImport(label: String, bytes: ByteArray, success: Boolean) {
            val before=sha(target); val uri=scope.providerUri(bytes)
            callback(page,request,Activity.RESULT_OK,uri)
            if(curve) await("curve import result reader completed") { ready(page) }
            val after=sha(target)
            check((after!=before)==success) { "M7 $label wrong archive mutation" }
            check(nativeInvariant(scope)==nativeBefore && services().isEmpty())
            cases.put(JSONObject().put("case",label).put("ownedProviderUri",uri.toString()).put("originalArchiveSha",before)
                .put("resultArchiveSha",after).put("changed",after!=before).put("nativeModelsUnchanged",true))
        }
        val valid=if(curve) scope.mainCurves else LegacyHistoryCodec.encode(scope.shots).toByteArray(Charsets.UTF_8)
        val uri=scope.providerUri(valid)
        val beforeCancel=sha(target); val reads=scope.provider("stats").getInt("read:output.json")
        callback(page,request,Activity.RESULT_CANCELED,uri)
        check(sha(target)==beforeCancel && scope.provider("stats").getInt("read:output.json")==reads)
        cases.put(JSONObject().put("case","saf-cancel").put("archiveUnchanged",true).put("providerNotRead",true))
        checkImport("duplicate-no-change",valid,false)
        checkImport("malformed-utf8",byteArrayOf(0xc3.toByte(),0x28),false)
        checkImport("wrong-format","{}".toByteArray(),false)
        checkImport("over-16MiB",ByteArray((if(curve) LegacyCurveCodec.MAX_BYTES else LegacyHistoryCodec.MAX_BYTES)+1) { 0x20 },false)
        if(curve) {
            val replacement=JSONObject(String(valid,Charsets.UTF_8)).apply {
                getJSONArray("items").getJSONObject(0).put("name","显式替换后的旧曲线")
            }.toString().toByteArray(Charsets.UTF_8)
            checkImport("curve-explicit-replacement",replacement,true)
            val loaded=requireNotNull(scope.legacyCurves.load())
            check(loaded.curves.size==41 && loaded.curves.first().name=="显式替换后的旧曲线")
            check(JSONObject(loaded.curves.first().rawJson).getJSONObject("unknownFixtureKey").getInt("keep")==0)
            check(scope.curveFile.readBytes().contentEquals(replacement)) { "M7 original curve bytes/unknown root fields lost" }
            // Explicitly return this owned fixture to its known archive, never touch original user disk.
            scope.legacyCurves.import(valid.inputStream())
        } else {
            val added=scope.mainShot.copy(id="m7-explicit-added",createdAtMs=scope.mainShot.createdAtMs+1000)
            checkImport("history-merge-new-id",LegacyHistoryCodec.encode(listOf(added)).toByteArray(),true)
            check(scope.legacyHistory.list().size==42 && scope.legacyHistory.list().first()==added)
            checkImport("history-conflicting-id",LegacyHistoryCodec.encode(listOf(scope.mainShot.copy(profileName="冲突"))).toByteArray(),false)
            val duplicate=JSONObject(LegacyHistoryCodec.encode(listOf(scope.mainShot))).apply {
                getJSONArray("items").put(getJSONArray("items").getJSONObject(0))
            }.toString().toByteArray()
            checkImport("history-duplicate-id-in-file",duplicate,false)
            check(scope.legacyHistory.list().single { it.id==scope.mainId }.snapshotJson==scope.mainShot.snapshotJson)
        }
        captureState(page,directory,if(curve) "curve-explicit-import-complete" else "history-explicit-import-complete")
        close(page)
        return cases
    }
    private fun detailReachability(activity: Activity, directory: File) {
        val report=JSONArray()
        for(id in listOf(R.string.legacy_detail_source_heading,R.string.legacy_detail_source_body,
            R.string.legacy_detail_missing_heading,R.string.legacy_detail_missing_body,
            R.string.legacy_detail_limits_heading,R.string.legacy_detail_limits_body)) {
            lateinit var target: TextView
            main {
                target=nodes(root(activity)).filterIsInstance<TextView>().single { it.text.toString()==activity.getString(id) }
                target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true)
            }
            stableWindow(activity,directory,"detail-reach-$id")
            main {
                val value=observation(target); check(whole(value))
                check(whole(observation(action(activity,R.string.ui_full_details))))
                report.put(JSONObject().put("resource",id).put("actualView",value).put("fixedActionComplete",true))
            }
        }
        captureState(activity,directory,"detail-prose-reached")
        click(activity,R.string.ui_full_details)
        await("real full explanation dialog") { !activity.hasWindowFocus() &&
            android.view.inspector.WindowInspector.getGlobalWindowViews().flatMap(::nodes).filterIsInstance<TextView>()
                .any { it.text.toString().contains(activity.getString(R.string.legacy_history_chart_disclaimer)) } }
        diagnostic(activity,File(directory,"full-explanation-dialog-diagnostic"))
        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await("dialog dismissed") { ready(activity) }
        File(directory,"detail-reachability.json").writeText(JSONObject().put("prose",report)
            .put("fullOriginalDisclaimerReachable",true).put("dialogScreenshotStatus","UNSYNCHRONIZED_DIAGNOSTIC_NOT_MAIN")
            .put("stopConfirmationNotInferred",true).toString(2))
    }
    private fun shortWindow(activity: Activity, directory: File): JSONObject {
        val old=android.view.WindowManager.LayoutParams().apply { copyFrom(activity.window.attributes) }
        val ids=nodes(root(activity)).filter { it is LegacyShotChartView || it is ListView }.map(System::identityHashCode)
        try {
            main { activity.window.setLayout(-1,HoyiUi.dp(activity,280)) }
            stableWindow(activity,directory,"short-window")
            val folder=File(directory,"short-window").apply {check(isDirectory || mkdirs())}
            // Explicitly declared short Activity; default captures remain strict.
            val captured=frames.screenshotMeasuredShortActivity(activity,root(activity),File(folder,"window.png")) {geometry(activity)}
            check(captured.proof.getBoolean("accepted") && captured.proof.getInt("protocolVersion")==2)
            File(folder,"geometry.json").writeText(captured.target.toString(2))
            main {
                check(nodes(root(activity)).filter { it is LegacyShotChartView || it is ListView }.map(System::identityHashCode)==ids)
                val content=root(activity)
                check(content.height<1200) { "Short window was not actually applied" }
                if(activity is LegacyHistoryDetailActivity) {
                    check(whole(observation(action(activity,R.string.ui_full_details))))
                    check(nodes(content).filterIsInstance<LegacyShotChartView>().single().visualEvidence.getDouble("plotHeightDp")>=96.0)
                }
            }
            return JSONObject().put("case","short-window").put("realWindowResize",true).put("permanentListAndChartIdentity",true)
                .put("acceptanceStatus","PASS_PROTOCOL2_ONLY_NOT_VISUAL").put("frameSync",captured.proof).put("componentOnly",false)
        } finally {
            main { activity.window.attributes=old }
            stableWindow(activity,directory,"short-window-restored")
        }
    }
    private fun specials(scope: Scope, page: String, directory: File): JSONObject {
        val cases=JSONArray(); val nativeBefore=nativeInvariant(scope)
        if(page=="curve") {
            scope.legacyCurves.import(scope.curveArchive(missing=true).inputStream())
            var activity=start(LegacyCurveActivity::class.java)
            captureState(activity,directory,"missing-parameters")
            main { val values=nodes(root(activity)).filterIsInstance<TextView>().filter { it.tag?.toString()?.endsWith("/number")==true }
                check(values.size==4 && values.all { it.text.toString()==activity.getString(R.string.settings_unknown) }) }
            cases.put(JSONObject().put("case","missing-parameters").put("noInventedZeroOrStage",true)); close(activity)
            scope.legacyCurves.import(scope.curveArchive(long=true).inputStream())
            activity=start(LegacyCurveActivity::class.java); captureState(activity,directory,"long-name-category")
            cases.put(JSONObject().put("case","long-name-category").put("actualLegalArchive",true))
            cases.put(shortWindow(activity,directory)); close(activity)
            check(scope.curveFile.delete())
            activity=start(LegacyCurveActivity::class.java); captureState(activity,directory,"not-imported")
            main { check((field(activity,"emptyState") as View).isShown && (field(activity,"adapter") as android.widget.ArrayAdapter<*>).count==0) }
            cases.put(JSONObject().put("case","not-imported").put("emptyDistinctFromReadFailure",true)); close(activity)
            scope.curveFile.writeBytes(byteArrayOf(0xc3.toByte(),0x28))
            activity=start(LegacyCurveActivity::class.java); captureState(activity,directory,"bad-file-read-failure")
            main { check((field(activity,"emptyTitle") as TextView).text.toString()==activity.getString(R.string.legacy_curve_failed)) }
            cases.put(JSONObject().put("case","bad-file-read-failure").put("notEmptySuccess",true)); close(activity)
            // Known owned cache archive, not an original data rollback.
            scope.legacyCurves.import(scope.mainCurves.inputStream())
            val imports=archiveImports(scope,directory,true)
            (0 until imports.length()).forEach { cases.put(imports.getJSONObject(it)) }
        } else {
            if(page=="history") {
                sourcesBusiness(scope,directory)
                cases.put(JSONObject().put("case","three-sources").put("journalAndLocalRealRouteRecreatedReturned",true))
                val imports=archiveImports(scope,directory,false)
                (0 until imports.length()).forEach { cases.put(imports.getJSONObject(it)) }
            }
            val long=scope.mainShot.copy(profileName="经典浓缩长名称".repeat(20))
            check(scope.historyFile.delete()); scope.legacyHistory.import(LegacyHistoryCodec.encode(listOf(long)).byteInputStream())
            var activity=start(if(page=="history") LegacyHistoryActivity::class.java else LegacyHistoryDetailActivity::class.java,
                if(page=="detail") scope.mainId else null)
            captureState(activity,directory,"long-name-source")
            cases.put(JSONObject().put("case","long-name-source").put("originalSnapshotRetained",scope.legacyHistory.list().single().snapshotJson==scope.mainShot.snapshotJson))
            cases.put(shortWindow(activity,directory)); close(activity)
            check(scope.historyFile.delete()); scope.legacyHistory.import(LegacyHistoryCodec.encode(emptyList()).byteInputStream())
            activity=start(if(page=="history") LegacyHistoryActivity::class.java else LegacyHistoryDetailActivity::class.java,
                if(page=="detail") scope.mainId else null)
            captureState(activity,directory,"empty-or-unavailable")
            main { if(activity is LegacyHistoryActivity) check((field(activity,"emptyTitle") as TextView).text.toString()==activity.getString(R.string.legacy_history_empty_title))
                else check(nodes(root(activity)).none { it is LegacyShotChartView }) }
            cases.put(JSONObject().put("case","empty-or-unavailable").put("noPreviousChart",true)); close(activity)
            scope.historyFile.writeText("{}")
            activity=start(if(page=="history") LegacyHistoryActivity::class.java else LegacyHistoryDetailActivity::class.java,
                if(page=="detail") scope.mainId else null)
            captureState(activity,directory,"bad-file-read-failure")
            main { if(activity is LegacyHistoryActivity) check((field(activity,"emptyTitle") as TextView).text.toString()==activity.getString(R.string.legacy_history_failed))
                else check(nodes(root(activity)).none { it is LegacyShotChartView }) }
            cases.put(JSONObject().put("case","bad-file-read-failure").put("notEmptySuccess",true)); close(activity)
            check(scope.historyFile.delete()); scope.legacyHistory.import(LegacyHistoryCodec.encode(scope.shots).byteInputStream())
            activity=start(LegacyHistoryDetailActivity::class.java)
            captureState(activity,directory,"missing-legacy-id")
            main { check(nodes(root(activity)).none { it is LegacyShotChartView }) }
            cases.put(JSONObject().put("case","missing-legacy-id").put("noPreviousChart",true)); close(activity)
        }
        check(nativeInvariant(scope)==nativeBefore && services().isEmpty())
        return JSONObject().put("completed",true).put("cases",cases).put("mainConfigurationCountContribution",0)
            .put("visualAcceptance","ASTRA_PENDING").put("nativeModelsUnchanged",true)
    }
    fun run(arguments: Bundle): Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("legacyPage")); check(page in setOf("curve","history","detail"))
        val source=requireNotNull(arguments.getString("sourceSha")); check(source.matches(Regex("[0-9a-f]{40}")))
        val theme=requireNotNull(arguments.getString("sampleTheme")); check(theme in setOf("light","dark"))
        val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat(); check(scale==1f || scale==1.3f)
        val reference=when(page) {
            "curve" -> "5dad2df70071007507e626fec6db35ac87b590a7f79f036c44af3b7ecfbf35bf"
            "history" -> "ca299170ee2a154015ca5d7894ff73e8e49c39a2d73ea48d56800ddd1899ff5e"
            else -> "35f3c2e39575558f3e3b229858d507edb016cc9e4212384557241909d968b7bb"
        }
        check(arguments.getString("referencePngSha256")==reference)
        val profile="tablet-user-landscape-$theme-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"legacy-visual-migration/$source/$profile/$page").apply { check(isDirectory || mkdirs()) }
        val manifest=JSONObject().put("sourceSha",source).put("headSha",arguments.getString("headSha"))
            .put("sourceDirty",arguments.getString("sourceDirty")).put("apkSha256",arguments.getString("apkSha"))
            .put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("referenceDesignPngSha256",reference).put("profile",profile).put("page",page)
            .put("realProductActivity",true).put("specimenOnly",false).put("visualAcceptance","ASTRA_PENDING")
            .put("pid",android.os.Process.myPid()).put("scopePolicy","original_12Lazy_identity_before_value; scoped_owned_legal_legacy_archives; no_Service_no_Mock_clock")
        var scope: Scope?=null; var failure: Throwable?=null
        try {
            val active=Scope(directory,page);scope=active;active.prepare();active.install(theme=="dark")
            File(directory,"fixture.json").writeText(active.fixture.toString(2))
            val activity=when(page) { "curve" -> start(LegacyCurveActivity::class.java)
                "history" -> start(LegacyHistoryActivity::class.java)
                else -> start(LegacyHistoryDetailActivity::class.java,active.mainId) }
            stableWindow(activity,directory,"main-ready")
            // First retain the untouched nonempty main frame, only then run any assertions or business mutation.
            val captured=frames.screenshot(activity,root(activity),File(directory,"window.png")) { geometry(activity) }
            val png=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,png)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(png.outWidth,png.outHeight))).put("initialFrameSync",captured.proof).put("window",captured.target)
            check(png.outWidth==2560 && png.outHeight==1600 && captured.proof.getInt("protocolVersion")==2 && captured.proof.getBoolean("accepted"))
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use { input ->
                val digest=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            check(font==TaskProductVisualStyle.FONT_SHA); manifest.put("fontSha256",font)
            main {
                val actual=captured.target
                check(actual.getDouble("fontScale").toFloat()==scale && actual.getString("uiModeNightMeaning")==theme)
                check(actual.getInt("densityDpi")==360 && actual.getString("locale").startsWith("zh") && !actual.getBoolean("imeVisible"))
                File(directory,"geometry.json").writeText(geometry(activity).toString(2)); TaskTypographyEvidence.capture(activity,directory)
                val baseline=baselinePairs(activity); File(directory,"baseline-alignment.json").writeText(baseline.toString(2))
                (0 until baseline.length()).forEach { check(baseline.getJSONObject(it).getString("status")!="FAIL") }
                if(page=="curve") {
                    check(list(activity).count==41 && field(activity,"selectedIndex")==0)
                    check(whole(observation(action(activity,R.string.curve_import_legacy))))
                } else {
                    validateChart(activity,active.mainShot)
                    val chart=nodes(root(activity)).filterIsInstance<LegacyShotChartView>().single()
                    File(directory,"canvas.json").writeText(chart.visualEvidence.toString(2))
                    if(page=="history") check(list(activity).count==41 && field(activity,"selectedId")==active.mainId)
                    check(whole(observation(action(activity,R.string.ui_full_details))))
                }
            }
            check(services().isEmpty())
            if(page=="detail") { detailReachability(activity,directory); close(activity) } else listBusiness(activity,active,directory)
            val special=specials(active,page,File(directory,"special-states").apply { check(isDirectory || mkdirs()) })
            File(directory,"special-states/manifest.json").writeText(special.toString(2))
            manifest.put("layoutStatus","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE")
        } catch(error: Throwable) {
            failure=error;manifest.put("layoutStatus","FAIL").put("failure",error.stackTraceToString())
            scope?.activeActivity()?.let { activity -> try { diagnostic(activity,File(directory,"failure-context")) }
                catch(other: Throwable) { error.addSuppressed(other) } }
        } finally {
            try { scope?.cleanup();manifest.put("isolationStatus",if(scope==null) "NOT_INSTALLED" else "PASS_ISOLATION_ONLY") }
            catch(error: Throwable) { manifest.put("isolationStatus","FAIL").put("cleanupFailure",error.stackTraceToString())
                if(failure==null) failure=error else failure!!.addSuppressed(error) }
            try {File(directory,"reader-drains.json").writeText(readerDrainProofs.toString(2))}
            catch(error:Throwable) {if(failure==null)failure=error else failure!!.addSuppressed(error)}
            manifest.put("overallStatus",if(failure==null) "PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            failure?.let { manifest.put("failure",it.stackTraceToString()) }
            manifest.put("fileSha256",JSONObject(directory.walkTopDown().filter { it.isFile && it!=File(directory,"manifest.json") }
                .associate { it.relativeTo(directory).path to sha(it) }))
            File(directory,"manifest.json").writeText(manifest.toString(2))
        }
        failure?.let { throw it };return 1
    }
}

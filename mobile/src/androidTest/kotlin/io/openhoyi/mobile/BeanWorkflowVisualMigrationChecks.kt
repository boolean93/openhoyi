package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
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

/** M4 real Preparation/Batch/Ledger in fresh disposable Mock processes. No Home or service is launched. */
internal class BeanWorkflowVisualMigrationChecks(private val test: Instrumentation) {
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
            check(SystemClock.elapsedRealtime() < deadline) { "M4 timeout: $description" }
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
    private fun businessDisk(): Map<String, String> = listOf("files", "shared_prefs", "databases").flatMap { name ->
        File(context.applicationInfo.dataDir, name).walkTopDown().filter { it.isFile &&
            !(name == "shared_prefs" && it.name in setOf("appearance.xml", "appearance.xml.bak", "curves.xml", "curves.xml.bak")) }
            .map { it.relativeTo(File(context.applicationInfo.dataDir)).path to sha(it) }.toList()
    }.toMap()
    private fun services(): List<String> = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(100).filter { it.service.packageName == context.packageName }.map { it.service.className }
    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    private fun visibility(view: View): JSONObject {
        val origin=IntArray(2); view.getLocationOnScreen(origin)
        val rootOrigin=IntArray(2); view.rootView.getLocationOnScreen(rootOrigin)
        val rootWindowOrigin=IntArray(2); view.rootView.getLocationInWindow(rootWindowOrigin)
        val full=Rect(origin[0],origin[1],origin[0]+view.width,origin[1]+view.height)
        // getGlobalVisibleRect is root-local, whereas getLocationOnScreen is screen-relative.
        // Retain the raw API result; compare only after translating into the same screen domain.
        val rawVisible=Rect(); val shown=view.getGlobalVisibleRect(rawVisible)
        val screenVisible=Rect(rawVisible).apply { if(shown) offset(rootOrigin[0],rootOrigin[1]) }
        return JSONObject().put("rectPx",rect(full)).put("visibleRectPx",rect(screenVisible))
            .put("rectCoordinateSpace","screen").put("rawGlobalVisibleRectRootPx",rect(rawVisible))
            .put("rawGlobalVisibleRectDefined",shown).put("rootOriginOnScreenPx",JSONArray(rootOrigin.toList()))
            .put("rootOriginInWindowPx",JSONArray(rootWindowOrigin.toList()))
            .put("visibleRectConversion","raw_getGlobalVisibleRect_plus_rootView_getLocationOnScreen_when_defined")
            .put("visibilitySemantics","exact_hierarchy_clip_only; not_system_window_or_IME_occlusion_proof")
            .put("globalVisible",shown)
            .put("complete",shown && view.width>0 && view.height>0 && screenVisible==full)
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
        private fun whitePrefFiles(): Map<String,String> = listOf("appearance.xml","appearance.xml.bak","curves.xml","curves.xml.bak").mapNotNull { name ->
            File(File(context.applicationInfo.dataDir,"shared_prefs"),name).takeIf { it.isFile }?.let { name to sha(it) }
        }.toMap()
        private var installed = false
        private val ownedDirectory = File(context.cacheDir, "bean-workflow-visual-${UUID.randomUUID()}")
        private val tracked = linkedSetOf<Activity>()
        private val destroyed = linkedSetOf<Activity>()
        var rejectInventoryWrites=false
        var rejectPreparationWrites=false
        var preparationWritesBeforeFailure: Int?=null
        val inventory = BeanInventory(object : InventoryStorage { var value: ByteArray? = null
            override fun read() = value?.clone()
            override fun write(bytes: ByteArray) { check(!rejectInventoryWrites) { "M4 scoped inventory write failure before publication" }; value=bytes.clone() } })
        val preparation = BeanPreparation(object : BeanPreparation.Storage { var value: String? = null
            override fun read() = value
            override fun write(value: String) {
                check(!rejectPreparationWrites && preparationWritesBeforeFailure!=0) { "M4 scoped preparation write failure before publication" }
                preparationWritesBeforeFailure=preparationWritesBeforeFailure?.minus(1); this.value=value
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
        val batchId="m4-main-batch"
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
        fun prepare() {
            check(!::logs.isInitialized && !::samples.isInitialized)
            logs=TraceStore(File(ownedDirectory,"traces"))
            samples=ShotSamplesRepository(File(ownedDirectory,"samples")) { logs.record("fixture.samples_error",mapOf("type" to it.javaClass.simpleName)) }
            val factory=FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val proof=FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"),factory,context.assets.open("factory_slot_wire_v1.tsv"))
            curves=CurveLibrary(factory,proof,{ custom.items() },true)
            var shotIndex=0
            history=ShotHistory(object: ShotHistory.Storage { var value=""
                override fun read()=value; override fun write(value: String) { this.value=value }
            },now={1792026750000L},newId={"m4-scoped-observed-${++shotIndex}"},onObservedUse={ entry ->
                usage.record(entry.id,entry.curveId,entry.startedAtMs)
            },onEntryChanged={ entry ->
                journal.observe(entry.journalObservation())
                if(preparation.current()?.shotId==entry.id) preparation.associatePending(journal,inventory)
            })
            journal.reconcileRecent(history.entries.map { it.journalObservation() })
            preparation.associatePending(journal,inventory); samples.prune(history.entries.map(ShotHistory.Entry::id).toSet())
            val initial=if(page=="batch") 100000L else 250000L
            inventory.addBatch("m4-main-add",BeanBatch(batchId,Bean("m4-main-bean","哥伦比亚 水洗"),initial,
                LocalDate.of(2026,10,2),LocalDate.of(2026,9,28),LocalDate.of(2026,10,3)))
            if(page=="batch") inventory.adjust("m4-main-adjust",batchId,-10000L,"盘点修正")
            else { inventory.consume("m4-main-consume-1",batchId,18000L); inventory.consume("m4-main-consume-2",batchId,18000L) }
            if(page=="preparation") listOf("巴西 日晒","埃塞俄比亚 水洗").forEachIndexed { index,name ->
                inventory.addBatch("m4-other-add-$index",BeanBatch("m4-other-batch-$index",Bean("m4-other-bean-$index",name),250000L)) }
            fixture=JSONObject().put("page",page).put("batchId",batchId).put("events",JSONArray(inventory.events().map { it.toString() }))
                .put("batches",JSONArray(inventory.batches().map { it.toString() })).put("preparation",JSONObject.NULL)
                .put("manualInputGrams",if(page=="preparation") "18" else JSONObject.NULL)
                .put("source","legal_memory_Storage; actual_Inventory_add_consume_adjust; no_TextView_fake_model_values")
                .put("servicePolicy","no_Home_no_startService_no_bind_AUTO_CREATE; Preparation_parent_flags0_and_250ms_refresh_unchanged")
                .put("clipboardSAFSlots","not_touched")
        }
        fun install(dark: Boolean) {
            main {
                check(services().isEmpty() && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) {
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
                check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).edit().putString("selected",curves.items.first().id).commit())
            }
        }
        fun activeActivity(): Activity? = tracked.lastOrNull { !it.isDestroyed }
        fun cleanup() {
            val proof = JSONObject().put("scopedOwnedDirectory",ownedDirectory.absolutePath)
            try {
                if (installed) {
                    main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
                    await("all fixture Activities and dialogs destroyed") {
                        tracked.all { it.isDestroyed && it in destroyed } && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()
                    }
                    main { check(services().isEmpty()) { "M4 no-service scope unexpectedly holds a service; original delegates not restored" } }
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) { "M4 fixture dialog/window remains after Activity destruction" }
                    val ownerProof=JSONArray()
                    main { tracked.filterIsInstance<BeanPreparationActivity>().forEach { activity ->
                        fun parentField(name: String)=ScaleOwnerActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
                        val handler=parentField("handler") as Handler
                        val refresh=parentField("refresh") as Runnable
                        val owner=parentField("owner"); val bound=parentField("bound") as Boolean; val visible=parentField("visible") as Boolean
                        val scheduled=handler.hasCallbacks(refresh)
                        ownerProof.put(JSONObject().put("instanceIdentity",System.identityHashCode(activity)).put("destroyed",activity.isDestroyed)
                            .put("ownerNull",owner==null).put("bound",bound).put("visible",visible).put("refresh250msScheduled",scheduled))
                        check(activity.isDestroyed && owner==null && !bound && !visible && !scheduled) { "M4 ScaleOwner did not unbind/remove real refresh" }
                    } }
                    proof.put("preparationScaleOwnerCleanup",ownerProof).put("noHomeRouteCovered",true)
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("noServiceBeforeOriginalDelegateRestore",true).put("mainQueueBarrierAfterDestroy",true)
                }
                proof.put("ownedSampleRepositoryCreated",::samples.isInitialized).put("ownedTraceCreated",::logs.isInitialized)
                if (::samples.isInitialized) {
                    val writer=field(samples,"writer") as ExecutorService
                    writer.shutdown(); check(writer.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped sample writer did not terminate" }
                }
                if (::logs.isInitialized) { logs.close(); check(logs.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped trace writer did not terminate" } }
                proof.put("scopedWritersTerminated",true)
                proof.put("clipboardSAFSlots","NOT_TOUCHED")
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
    private fun geometry(activity: Activity): JSONObject {
        val content=root(activity); val origin=IntArray(2); content.getLocationOnScreen(origin)
        val insets=requireNotNull(content.rootWindowInsets)
        val bars=insets.getInsets(WindowInsets.Type.systemBars())
        val config=activity.resources.configuration
        val budget=when(activity) {
            is BeanBatchActivity -> JSONObject(activity.productLayoutEvidence.toString())
            is BeanPreparationActivity -> JSONObject(activity.productLayoutEvidence.toString())
            else -> JSONObject().put("status","unknown").put("reason","no_existing_M4_natural_budget_interface; actual_View_rects_and_Layout_recorded")
        }
        return JSONObject().put("naturalBudgetActualPx",budget)
            .put("resourcesDisplayMetricsPx",JSONArray(listOf(activity.resources.displayMetrics.widthPixels,activity.resources.displayMetrics.heightPixels)))
            .put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("windowMetricsSemantics","Android_window_metrics_not_physical_panel; original_PNG_header_recorded_separately")
            .put("appOriginPx",JSONArray(origin.toList())).put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("systemInsetsPx",JSONArray(listOf(bars.left,bars.top,bars.right,bars.bottom)))
            .put("imeVisible",insets.isVisible(WindowInsets.Type.ime())).put("densityDpi",config.densityDpi)
            .put("fontScale",config.fontScale).put("locale",config.locales.toLanguageTags())
            .put("uiModeNightMeaning",when(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> "dark"; android.content.res.Configuration.UI_MODE_NIGHT_NO -> "light"; else -> "undefined"
            }).put("nodes",JSONArray(nodes(content).filter { it.tag is String }.map { observation(it) }))
    }
    private fun captureState(activity: Activity, directory: File, phase: String) {
        val folder=File(directory,"states/$phase").apply { check(isDirectory || mkdirs()) }
        frames.screenshot(activity,root(activity),File(folder,"window.png")) { geometry(activity) }
        main { File(folder,"geometry.json").writeText(geometry(activity).toString(2)); TaskTypographyEvidence.capture(activity,folder) }
    }
    private fun diagnostic(activity: Activity, folder: File) {
        check(folder.isDirectory || folder.mkdirs())
        lateinit var before: JSONObject; main { before=geometry(activity); TaskTypographyEvidence.capture(activity,folder) }
        val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
        try { File(folder,"window.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } }
        finally { bitmap.recycle() }
        lateinit var after: JSONObject; main { after=geometry(activity) }
        File(folder,"geometry.json").writeText(JSONObject().put("before",before).put("after",after)
            .put("geometryUnchanged",before.toString()==after.toString()).put("sameCompositorFrameProven",false)
            .put("protocol","unsynchronized_IME_diagnostic_not_main_protocol2").toString(2))
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
            error("M4 $phase geometry did not stabilize on three actual frames within 5000ms")
        } finally { File(directory,"$phase-window-stability.json").writeText(JSONObject().put("samples",samples)
            .put("acceptancePolicy","geometry_stability_only; pending_only_isShown_tree; hidden_nodes_do_not_block; not_waiting_for_visibility_or_business_assertion_success").toString(2)) }
    }
    private fun start(type: Class<out Activity>, batchId: String?=null): Activity {
        val intent=Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        batchId?.let { intent.putExtra(if(type==BeanPreparationActivity::class.java) BeanPreparationActivity.EXTRA_BATCH_ID else BeanBatchActivity.BATCH_ID,it) }
        return test.startActivitySync(intent).also { page -> await("${type.simpleName} actual ready") {
            page.hasWindowFocus() && root(page).isLaidOut && !root(page).isLayoutRequested } }
    }
    private fun close(activity: Activity) {
        main { activity.finish() }; await("${activity.javaClass.simpleName} destroyed") { activity.isDestroyed }
    }
    private fun recreate(activity: Activity, expectedAdjustmentDialog: Boolean = false, directory: File? = null): Activity {
        val monitor=test.addMonitor(activity.javaClass.name,null,false)
        val samples=JSONArray()
        var replacement: Activity?=null
        var accepted=false
        return try {
            main { activity.recreate() }
            val page=test.waitForMonitorWithTimeout(monitor,10000) ?: error("M4 recreation missing")
            replacement=page
            val deadline=SystemClock.elapsedRealtime()+10000
            await("recreated real page or its original adjustment window ready") {
                val ownerDecor=page.window.decorView
                fun laidOut(view: View)=view.isAttachedToWindow && view.isLaidOut && view.width>0 && view.height>0 && !view.isLayoutRequested
                fun windowProof(view: View)=JSONObject().put("identity",System.identityHashCode(view))
                    .put("hasWindowFocus",view.hasWindowFocus()).put("attached",view.isAttachedToWindow)
                    .put("laidOut",view.isLaidOut).put("layoutRequested",view.isLayoutRequested)
                    .put("windowTokenIdentity",view.windowToken?.let(System::identityHashCode) ?: JSONObject.NULL)
                    .put("geometry",visibility(view))
                val proof=JSONObject().put("owner",windowProof(ownerDecor)).put("ownerContent",windowProof(root(page)))
                    .put("expectedAdjustmentDialog",expectedAdjustmentDialog)
                var ready=laidOut(ownerDecor) && laidOut(root(page))
                if(expectedAdjustmentDialog) {
                    check(page is BeanBatchActivity)
                    val showing=(field(page,"dialogs") as List<*>).filterIsInstance<AlertDialog>().filter { it.isShowing }
                    proof.put("showingDialogCount",showing.size)
                    val adjustment=showing.singleOrNull()
                    val dialogDecor=adjustment?.window?.decorView
                    val target=field(page,"targetField") as? EditText
                    val reason=field(page,"reasonField") as? EditText
                    val ownedNodes=dialogDecor?.let(::nodes).orEmpty()
                    val ownsFields=target!=null && reason!=null && ownedNodes.any { it===target } && ownedNodes.any { it===reason }
                    proof.put("dialog",dialogDecor?.let(::windowProof) ?: JSONObject.NULL)
                        .put("dialogOwnsOriginalFields",ownsFields)
                        .put("targetFieldIdentity",target?.let(System::identityHashCode) ?: JSONObject.NULL)
                        .put("reasonFieldIdentity",reason?.let(System::identityHashCode) ?: JSONObject.NULL)
                        .put("targetField",target?.let(::observation) ?: JSONObject.NULL)
                        .put("reasonField",reason?.let(::observation) ?: JSONObject.NULL)
                    ready=ready && dialogDecor!=null && laidOut(dialogDecor) && dialogDecor.hasWindowFocus() && ownsFields
                } else ready=ready && page.hasWindowFocus()
                val within=SystemClock.elapsedRealtime()<=deadline
                samples.put(proof.put("ready",ready).put("withinBudget",within))
                ready && within
            }
            check(SystemClock.elapsedRealtime()<=deadline) { "M4 recreated window readiness accepted after deadline" }
            accepted=true
            page
        } catch(failure: Throwable) {
            if(directory!=null && replacement!=null) runCatching {
                diagnostic(requireNotNull(replacement),File(directory,"recreate-adjustment-failure"))
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            try {
                if(directory!=null) File(directory,"recreate-adjustment-window-readiness.json").writeText(JSONObject()
                    .put("accepted",accepted).put("expectedAdjustmentDialog",expectedAdjustmentDialog).put("samples",samples).toString(2))
            } finally { test.removeMonitor(monitor) }
        }
    }
    private fun unfocus(activity: Activity) {
        main { root(activity).isFocusableInTouchMode=true; check(root(activity).requestFocus())
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(root(activity).windowToken,0) }
        await("main window without IME") { !requireNotNull(root(activity).rootWindowInsets).isVisible(WindowInsets.Type.ime()) }
    }
    private fun action(activity: Activity, label: Int): TextView = nodes(root(activity)).filterIsInstance<TextView>()
        .single { it.isClickable && it.text.toString()==activity.getString(label) }
    private fun click(activity: Activity, label: Int) {
        main { val view=action(activity,label); check(view.isShown && view.isEnabled); check(view.performClick()) }
        test.waitForIdleSync()
    }
    private fun balance(scope: Scope)=scope.inventory.batches().single { it.batch.id==scope.batchId }.balanceMg
    private fun otherModels(scope: Scope)=JSONObject().put("custom",JSONArray(scope.custom.list().map { it.toJson() }))
        .put("history",JSONArray(scope.history.entries.map { it.toString() })).put("journal",JSONObject(scope.journal.exportJson()))
        .put("usage",JSONArray(scope.curves.items.map { JSONObject().put("id",it.id).put("stats",scope.usage.stats(it.id).toString()) })).toString()
    private fun dialog(activity: Activity): AlertDialog = when(activity) {
        is BeanBatchActivity -> (field(activity,"dialogs") as List<*>).filterIsInstance<AlertDialog>().last { it.isShowing }
        is BeanPreparationActivity -> field(activity,"dialog") as AlertDialog
        else -> error("M4 unsupported dialog owner")
    }
    private fun dialogClick(activity: Activity, which: Int, label: Int) {
        main { val button=dialog(activity).getButton(which)
            check(button.isEnabled && button.text.toString()==activity.getString(label)); check(button.performClick()) }
        test.waitForIdleSync()
    }
    /** This CI fixture uses a bottom-docked IME; not a general floating-window occlusion model. */
    private fun dialogImeAvoidance(activity: Activity, window: View): JSONObject {
        val ownerDecor=activity.window.decorView
        val ownerInsets=requireNotNull(ownerDecor.rootWindowInsets)
        val dialogInsets=requireNotNull(window.rootWindowInsets)
        val bounds=Rect(activity.windowManager.currentWindowMetrics.bounds)
        val ime=ownerInsets.getInsets(WindowInsets.Type.ime())
        val bars=ownerInsets.getInsets(WindowInsets.Type.systemBars())
        val avoided=ownerInsets.getInsets(WindowInsets.Type.ime() or WindowInsets.Type.systemBars())
        val safe=Rect(bounds.left+avoided.left,bounds.top+avoided.top,bounds.right-avoided.right,bounds.bottom-avoided.bottom)
        val ownerVisibleFrame=Rect(); ownerDecor.getWindowVisibleDisplayFrame(ownerVisibleFrame)
        val dialogVisibleFrame=Rect(); window.getWindowVisibleDisplayFrame(dialogVisibleFrame)
        val focused=window.findFocus()
        val targets=listOf("focused-input" to focused,
            "native-negative" to dialog(activity).getButton(AlertDialog.BUTTON_NEGATIVE),
            "native-positive" to dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE))
        val targetEvidence=JSONArray(targets.map { (role,view) ->
            val result=JSONObject().put("role",role).put("present",view!=null)
            if(view!=null) {
                val observed=observation(view); val full=observed.getJSONArray("rectPx")
                val screen=Rect(full.getInt(0),full.getInt(1),full.getInt(2),full.getInt(3))
                result.put("observation",observed).put("whole",whole(observed))
                    .put("fullScreenRectInsideSafe",safe.contains(screen))
            } else result.put("whole",false).put("fullScreenRectInsideSafe",false)
            result
        })
        return JSONObject().put("scope","this_CI_bottom_docked_IME_only_not_arbitrary_floating_occlusion")
            .put("coordinateSpace","screen").put("ownerWindowMetricsBoundsPx",rect(bounds))
            .put("ownerImeInsetsPx",JSONArray(listOf(ime.left,ime.top,ime.right,ime.bottom)))
            .put("ownerSystemBarsInsetsPx",JSONArray(listOf(bars.left,bars.top,bars.right,bars.bottom)))
            .put("ownerImeOrSystemBarsInsetsPx",JSONArray(listOf(avoided.left,avoided.top,avoided.right,avoided.bottom)))
            .put("safeScreenRectPx",rect(safe)).put("safeScreenRectNonempty",!safe.isEmpty)
            .put("ownerVisibleDisplayFrameScreenPx",rect(ownerVisibleFrame)).put("dialogVisibleDisplayFrameScreenPx",rect(dialogVisibleFrame))
            .put("ownerImeVisible",ownerInsets.isVisible(WindowInsets.Type.ime())).put("dialogImeVisible",dialogInsets.isVisible(WindowInsets.Type.ime()))
            .put("focusedInputIsActualTarget",focused is EditText && focused===field(activity,"targetField"))
            .put("targets",targetEvidence).put("sameCompositorFrameProven",false)
    }
    private fun requireDialogImeAvoidance(value: JSONObject) {
        check(value.getBoolean("ownerImeVisible") && value.getBoolean("dialogImeVisible") && value.getBoolean("safeScreenRectNonempty") &&
            value.getBoolean("focusedInputIsActualTarget")) { "M4 actual focused input/docked IME safe-domain observation missing" }
        val targets=value.getJSONArray("targets")
        check(targets.length()==3)
        (0 until targets.length()).forEach { index -> val target=targets.getJSONObject(index)
            check(target.getBoolean("present") && target.getBoolean("whole") && target.getBoolean("fullScreenRectInsideSafe")) {
                "M4 ${target.getString("role")} not completely inside actual docked-IME safe screen rect; saved geometry remains diagnostic only"
            }
        }
    }
    private fun dialogSnapshot(activity: Activity, directory: File, phase: String) {
        val folder=File(directory,"states/$phase").apply { check(isDirectory || mkdirs()) }
        lateinit var before: JSONObject
        main {
            val window=requireNotNull(dialog(activity).window).decorView
            val text=nodes(window).filterIsInstance<TextView>().filter { it.isShown }.map { view ->
                JSONObject().put("class",view.javaClass.name).put("tag",view.tag).put("text",view.text)
                    .put("textSizePx",view.textSize).put("weight",view.typeface?.weight).put("fakeBold",view.paint.isFakeBoldText)
                    .put("letterSpacingEm",view.letterSpacing).put("includeFontPadding",view.includeFontPadding)
                    .put("actualLayout",TaskTypographyEvidence.actualTextLayout(view))
                    .put("colors",TaskVisualColorEvidence.text(view)).put("numericAdvanceProbe",TaskTypographyEvidence.numericAdvanceProbe(view.paint))
            }
            val observed=observation(window)
            before=JSONObject().put("activity",geometry(activity)).put("dialog",observed).put("dialogTypography",JSONArray(text))
            if(phase=="adjustment-input-IME") {
                before.put("imeAvoidance",dialogImeAvoidance(activity,window))
                // Exercise the actual floating native window, not a zero-origin Activity or fabricated Rect.
                val origin=observed.getJSONArray("rootOriginOnScreenPx")
                check(origin.getInt(0)!=0 || origin.getInt(1)!=0) { "M4 floating-dialog coordinate regression requires an actual nonzero root origin" }
                check(observed.getBoolean("rawGlobalVisibleRectDefined")) { "M4 floating-dialog root visibility API result undefined" }
                check(observed.getJSONArray("rawGlobalVisibleRectRootPx").toString()!=observed.getJSONArray("visibleRectPx").toString())
                before.put("nonzeroWindowOriginRegression",JSONObject().put("actualNativeDialog",true)
                    .put("rootOriginOnScreenPx",origin).put("nonzeroOriginExercised",true)
                    .put("rawRootAndScreenDomainsDiffer",true).put("comparison","exact_full_Rect_in_screen_domain_no_tolerance")
                    .put("occlusionAcceptance","not_proven_by_hierarchy_API; original_unmasked_IME_screenshot_still_requires_review"))
            }
        }
        val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
        try { File(folder,"window.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } }
        finally { bitmap.recycle() }
        main {
            val window=requireNotNull(dialog(activity).window).decorView
            val after=JSONObject().put("activity",geometry(activity)).put("dialog",observation(window))
            if(phase=="adjustment-input-IME") after.put("imeAvoidance",dialogImeAvoidance(activity,window))
            File(folder,"geometry.json").writeText(JSONObject().put("before",before).put("after",after)
                .put("sameCompositorFrameProven",false).put("protocol","unsynchronized_native_dialog_diagnostic_not_main_protocol2").toString(2))
            // Persist both raw observations before either new IME gate or the caller's original Layout/size assertions.
            if(phase=="adjustment-input-IME") {
                requireDialogImeAvoidance(before.getJSONObject("imeAvoidance"))
                requireDialogImeAvoidance(after.getJSONObject("imeAvoidance"))
            }
        }
    }
    private fun baselines(activity: Activity): JSONArray {
        val result=JSONArray()
        val views=nodes(root(activity)).filterIsInstance<TextView>().filter { it.isShown }
        val pairs=views.filter { it.tag?.toString()?.endsWith("/number")==true }.mapNotNull { number ->
            val prefix=number.tag.toString().removeSuffix("/number")
            views.singleOrNull { it.tag=="$prefix/unit" }?.let { number to it }
        }.toMutableList()
        root(activity).findViewWithTag<EditText>("product/form/preparation/amount")?.takeIf { it.isShown }?.let { number ->
            root(activity).findViewWithTag<TextView>("product/form/preparation/amount/unit")?.takeIf { it.isShown }?.let { pairs.add(number to it) } }
        pairs.forEach { (number,unit) ->
            val a=IntArray(2); val b=IntArray(2); number.getLocationOnScreen(a); unit.getLocationOnScreen(b)
            val delta=a[1]+number.baseline-b[1]-unit.baseline
            result.put(JSONObject().put("numberId",number.tag).put("unitId",unit.tag).put("numberScreenBaselinePx",a[1]+number.baseline)
                .put("unitScreenBaselinePx",b[1]+unit.baseline).put("deltaPx",delta)
                .put("status",if(number.baseline>=0 && unit.baseline>=0 && delta==0) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL"))
        }
        return result
    }
    private fun reach(activity: Activity, directory: File, targets: List<View>, initialRequired: Set<Int>, fixed: List<View>) {
        val scrolls=nodes(root(activity)).filterIsInstance<ScrollView>(); val prior=scrolls.map { Triple(it,it.scrollX,it.scrollY) }
        val initial=mutableListOf<JSONObject>(); val fixedBounds=mutableListOf<String>(); val reports=JSONArray(); var failed=false
        main { targets.forEach { initial.add(observation(it)) }; fixed.forEach { fixedBounds.add(observation(it).getJSONArray("rectPx").toString()) } }
        try {
            targets.forEachIndexed { index,view ->
                main { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
                frames.committedFrame(root(activity))
                lateinit var after: JSONObject; var fixedSame=false
                main { after=observation(view); fixedSame=fixed.indices.all { observation(fixed[it]).getJSONArray("rectPx").toString()==fixedBounds[it] } }
                val pass=whole(after) && fixedSame && (index !in initialRequired || whole(initial[index]))
                reports.put(JSONObject().put("index",index).put("before",initial[index]).put("after",after)
                    .put("initialRequired",index in initialRequired).put("fixedActionsUnmoved",fixedSame).put("status",if(pass) "PASS_LAYOUT_ONLY" else "FAIL"))
                captureState(activity,directory,"reachable-$index")
                failed=failed || !pass
            }
        } finally {
            main { prior.forEach { (view,x,y) -> view.scrollTo(x,y) } }; frames.committedFrame(root(activity))
            val restored=prior.all { (view,x,y) -> view.scrollX==x && view.scrollY==y }
            File(directory,"scroll-reachability.json").writeText(JSONObject().put("targets",reports).put("scrollRestored",restored).toString(2))
            check(restored) { "M4 local scroll was not restored exactly" }
        }
        check(!failed) { "M4 initial/reachable text/control bounds failed; original PNGs saved before failure" }
    }
    /** Reaches an existing business-state explanation without changing that state or its fixed actions. */
    private fun preparationDescriptionReach(page: Activity, scope: Scope, directory: File, phase: String, expectedText: String) {
        check(phase in setOf("confirmed-ledger-matched-receipt", "post-consume-intent-write-failed"))
        val folder=File(directory,"states/$phase")
        check(File(folder,"window-frame-sync.json").isFile && File(folder,"window.png").isFile) {
            "M4 original business-state image must precede description reachability: $phase"
        }
        lateinit var scroll: ScrollView
        lateinit var target: TextView
        lateinit var fixedViews: List<View>
        lateinit var initial: JSONObject
        lateinit var fixedBefore: List<String>
        var priorX=0; var priorY=0
        main {
            scroll=requireNotNull(root(page).findViewWithTag("product/preparation/support-scroll"))
            val matching=nodes(scroll).filterIsInstance<TextView>().filter { it.text.toString()==expectedText }
            check(matching.size==1) { "M4 expected one actual explanation in support scroll: $phase" }
            target=matching.single()
            check(target.rootView===root(page).rootView && target.isAttachedToWindow && target.isShown)
            val actions=requireNotNull(root(page).findViewWithTag<View>("product/preparation/actions"))
            fixedViews=listOf(actions)+nodes(actions).filterIsInstance<Button>()
            check(fixedViews.size>=2 && fixedViews.all { it.isAttachedToWindow && it.isShown })
            fixedBefore=fixedViews.map { visibility(it).getJSONArray("rectPx").toString() }
            priorX=scroll.scrollX; priorY=scroll.scrollY
            initial=observation(target)
        }
        val intent=requireNotNull(scope.preparation.current())
        val eventId=intent.eventId
        val balanceBefore=balance(scope)
        val batchesBefore=scope.inventory.batches().map { it.batch.id to it.balanceMg }
        val eventsBefore=scope.inventory.events().map { it.toString() }
        val journalBefore=scope.journal.exportJson()
        fun unchanged(): Boolean = scope.preparation.current()===intent && scope.preparation.current()?.eventId==eventId &&
            balance(scope)==balanceBefore && scope.inventory.batches().map { it.batch.id to it.balanceMg }==batchesBefore &&
            scope.inventory.events().map { it.toString() }==eventsBefore && scope.journal.exportJson()==journalBefore
        fun fixedSame(): Boolean {
            var same=false
            main { same=fixedViews.indices.all { index -> fixedViews[index].isAttachedToWindow &&
                visibility(fixedViews[index]).getJSONArray("rectPx").toString()==fixedBefore[index] } }
            return same
        }
        val proof=JSONObject().put("phase",phase).put("scope","same_real_Preparation_business_state")
            .put("initialStateImage","states/$phase/window.png")
            .put("targetText",expectedText).put("targetIdentity",System.identityHashCode(target))
            .put("supportScrollIdentity",System.identityHashCode(scroll))
            .put("intentIdentity",System.identityHashCode(intent)).put("eventId",eventId)
            .put("initial",initial).put("initialScrollPx",JSONArray(listOf(priorX,priorY)))
            .put("fixedActionIdentities",JSONArray(fixedViews.map(System::identityHashCode)))
            .put("fixedActionRectsBeforePx",JSONArray(fixedBefore))
            .put("balanceMgBefore",balanceBefore).put("eventsBefore",JSONArray(eventsBefore))
            .put("journalBefore",journalBefore)
        var failure:Throwable?=null
        try {
            main { target.requestRectangleOnScreen(Rect(0,0,target.width,target.height),true) }
            frames.committedFrame(root(page))
            captureState(page,directory,"$phase/description-visible")
            lateinit var after:JSONObject
            main { after=observation(target) }
            val fixedAfter=fixedSame(); val businessAfter=unchanged()
            proof.put("after",after).put("wholeAfter",whole(after))
                .put("fixedActionsUnmovedAfter",fixedAfter).put("businessUnchangedAfter",businessAfter)
                .put("visibleImage","states/$phase/description-visible/window.png")
            check(whole(after) && fixedAfter && businessAfter) { "M4 original $phase explanation did not become fully reachable without business/fixed-action changes" }
        } catch(error:Throwable) { failure=error }
        finally {
            try {
                main { scroll.scrollTo(priorX,priorY) }
                frames.committedFrame(root(page))
                captureState(page,directory,"$phase/description-restored")
                lateinit var restored:JSONObject
                main { restored=observation(target) }
                val exactScroll=scroll.scrollX==priorX && scroll.scrollY==priorY
                val fixedRestored=fixedSame(); val businessRestored=unchanged()
                val targetRestored=restored.toString()==initial.toString()
                proof.put("restored",restored).put("scrollRestoredExactly",exactScroll)
                    .put("fixedActionsUnmovedRestored",fixedRestored).put("businessUnchangedRestored",businessRestored)
                    .put("targetObservationRestoredExactly",targetRestored)
                    .put("restoredImage","states/$phase/description-restored/window.png")
                check(exactScroll && fixedRestored && businessRestored && targetRestored) { "M4 original $phase state did not restore exactly" }
            } catch(error:Throwable) { if(failure==null) failure=error else failure!!.addSuppressed(error) }
            proof.put("status",if(failure==null) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            failure?.let { proof.put("failure",it.stackTraceToString()) }
            File(folder,"support-description-reachability.json").writeText(proof.toString(2))
        }
        failure?.let { throw it }
    }
    private fun preparationBusiness(initial: Activity, scope: Scope, directory: File) {
        var page=initial; val frozen=otherModels(scope); val cases=JSONArray()
        fun events()=scope.inventory.events().filter { it.batchId==scope.batchId }
        fun record(name: String) {
            check(services().isEmpty())
            cases.put(JSONObject().put("case",name).put("balanceMg",balance(scope)).put("events",JSONArray(events().map { it.toString() }))
                .put("intent",scope.preparation.current()?.toString() ?: JSONObject.NULL))
            File(directory,"bean-workflow-business.json").writeText(JSONObject().put("cases",cases).put("completed",false).toString(2))
        }
        fun amount(value: String) { main { (field(page,"amount") as EditText).setText(value) } }
        fun error(id: Int) { main { check((field(page,"error") as TextView).text.toString()==page.getString(id)) } }
        val initialEvents=scope.inventory.events(); val initialBalance=balance(scope)
        val initialEventId=field(page,"nextEventId") as String
        main {
            val alternative=requireNotNull(root(page).findViewWithTag<View>("dose-batch-m4-other-batch-0"))
            check(alternative.performClick() && alternative.isSelected)
        }
        page=recreate(page); stableWindow(page,directory,"preparation-draft-recreated")
        main {
            check(field(page,"draftBatchId")=="m4-other-batch-0" && field(page,"nextEventId")==initialEventId &&
                (field(page,"amount") as EditText).text.toString()=="18")
            check(requireNotNull(root(page).findViewWithTag<View>("dose-batch-${scope.batchId}")).performClick())
        }
        check(scope.preparation.current()==null && scope.inventory.events()==initialEvents); record("real-choice-and-draft-batch-amount-event-id-recreate-no-write")
        main {
            val input=field(page,"amount") as EditText
            check(input.requestFocus()); input.setSelection(0,1)
            (page.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input,0)
        }
        await("Preparation actual IME") { requireNotNull(root(page).rootWindowInsets).isVisible(WindowInsets.Type.ime()) }
        main { val input=field(page,"amount") as EditText; input.requestRectangleOnScreen(Rect(0,0,input.width,input.height),true) }
        stableWindow(page,directory,"preparation-amount-IME")
        diagnostic(page,File(directory,"states/preparation-amount-IME"))
        main {
            check(whole(observation(field(page,"amount") as EditText))) { "M4 actual amount not completely reachable with IME" }
            listOf(R.string.dose_save_selection,R.string.dose_return).forEach { id ->
                val button=action(page,id); check(whole(observation(button)) && button.height>=HoyiUi.dp(page,if(id==R.string.dose_save_selection) 56 else 48)) }
        }
        unfocus(page); record("actual-amount-IME-local-reveal-and-fixed-actions")
        for(invalid in listOf("18.0001","0","bad")) {
            amount(invalid); click(page,R.string.dose_save_selection); error(R.string.dose_bad_amount)
            check(scope.preparation.current()==null && scope.inventory.events()==initialEvents); record("invalid-$invalid-no-intent-or-stock-write")
        }
        amount("18"); scope.rejectPreparationWrites=true
        try { click(page,R.string.dose_save_selection); error(R.string.dose_save_failed)
            main { check(action(page,R.string.dose_save_selection).isEnabled) }
            check(scope.preparation.current()==null && scope.inventory.events()==initialEvents)
            captureState(page,directory,"selection-write-failed"); record("selection-write-failed-reenabled-no-stock-write")
        } finally { scope.rejectPreparationWrites=false }
        click(page,R.string.dose_save_selection)
        val selected=requireNotNull(scope.preparation.current())
        check(!selected.attempted && !selected.confirmed && selected.amountMg==18000L && selected.batchId==scope.batchId)
        check(scope.inventory.events()==initialEvents && balance(scope)==initialBalance)
        captureState(page,directory,"selection-saved-no-deduction"); record("selection-saves-intent-not-deduction")
        click(page,R.string.dose_cancel_selection)
        check(scope.preparation.current()==null && scope.inventory.events()==initialEvents); record("cancel-unattempted-selection-no-deduction")
        amount("18"); click(page,R.string.dose_save_selection)
        val pending=requireNotNull(scope.preparation.current()); check(pending.eventId!=selected.eventId)
        scope.rejectInventoryWrites=true
        try { click(page,R.string.dose_confirm); error(R.string.dose_confirm_failed)
            val current=requireNotNull(scope.preparation.current())
            check(current.eventId==pending.eventId && current.attempted && !current.confirmed && scope.inventory.events()==initialEvents)
            captureState(page,directory,"attempted-stock-write-failed"); record("attempted-persisted-before-stock-write-failure")
        } finally { scope.rejectInventoryWrites=false }
        page=recreate(page); check(scope.preparation.current()?.eventId==pending.eventId && scope.preparation.current()?.attempted==true)
        lateinit var staleRetry: TextView
        main { staleRetry=action(page,R.string.dose_retry) }
        click(page,R.string.dose_retry); main { staleRetry.performClick() }
        page=recreate(page)
        val confirmed=requireNotNull(scope.preparation.current()); val receipt=events().single { it.eventId==pending.eventId }
        check(confirmed.confirmed && confirmed.eventId==pending.eventId && balance(scope)==initialBalance-18000L &&
            receipt.kind==io.openhoyi.bean.InventoryEventKind.CONSUME && receipt.deltaMg == -18000L && receipt.balanceMg==initialBalance-18000L)
        main {
            check(field(page,"amount")==null) { "M4 confirmed state must not retain editable amount" }
            val label=requireNotNull(root(page).findViewWithTag<TextView>("dose-balance-receipt"))
            check(label.text.toString()==page.getString(R.string.dose_balance_receipt,BeanQuantity.formatGrams(initialBalance),BeanQuantity.formatGrams(receipt.balanceMg)))
        }
        captureState(page,directory,"confirmed-ledger-matched-receipt")
        preparationDescriptionReach(page,scope,directory,"confirmed-ledger-matched-receipt",
            page.getString(R.string.dose_confirmed,BeanQuantity.formatGrams(confirmed.amountMg)))
        record("explicit-retry-repeat-recreate-consume-exactly-once")
        click(page,R.string.dose_discard); dialogClick(page,AlertDialog.BUTTON_NEGATIVE,R.string.dose_cancel_dialog)
        check(scope.preparation.current()==confirmed && events().count { it.eventId==pending.eventId }==1)
        click(page,R.string.dose_discard); dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.dose_discard_confirm)
        check(scope.preparation.current()==null && balance(scope)==receipt.balanceMg); record("explicit-discard-no-refund")
        // A post-consume preparation persistence failure leaves a real unresolved durable intent and matching ledger event.
        amount("18"); click(page,R.string.dose_save_selection)
        val uncertainId=requireNotNull(scope.preparation.current()).eventId
        scope.preparationWritesBeforeFailure=1
        try { click(page,R.string.dose_confirm); error(R.string.dose_confirm_failed)
            check(scope.preparation.current()?.let { it.eventId==uncertainId && it.attempted && !it.confirmed }==true)
            check(events().count { it.eventId==uncertainId }==1)
            captureState(page,directory,"post-consume-intent-write-failed")
            preparationDescriptionReach(page,scope,directory,"post-consume-intent-write-failed",page.getString(R.string.dose_attempted))
            record("unresolved-intent-matching-authoritative-ledger-not-replaced")
        } finally { scope.preparationWritesBeforeFailure=null }
        val beforeRetry=events(); click(page,R.string.dose_retry)
        check(scope.preparation.current()?.confirmed==true && events()==beforeRetry); record("explicit-reconcile-same-event-no-second-consume")
        check(otherModels(scope)==frozen) { "M4 non-shot preparation actions changed unrelated models" }
        // Only legal domain observations, not a fake machine command or fabricated live telemetry.
        val at=1792026700000L
        scope.history.begin(scope.curves.items.first().id,at)
        scope.history.transition(ExtractionState.RUNNING,null,null,at+1000); scope.history.observeRunning()
        scope.history.transition(ExtractionState.ENDED_OBSERVED,null,3600,at+27400)
        val shot=scope.history.entries.single(); scope.preparation.claim(shot.id)
        close(page); page=start(BeanPreparationActivity::class.java,scope.batchId)
        val beforeAssociation=events(); click(page,R.string.dose_association_retry)
        check(scope.preparation.current()?.associated==true && events()==beforeAssociation)
        check(scope.journal.find(shot.id)?.notes?.let { it.beanId=="m4-main-bean" && it.doseMg==18000L }==true)
        captureState(page,directory,"association-recovered-original-reconcile-path"); record("association-retry-scoped-history-journal-preparation-inventory-no-consume")
        val beforeInsufficient=events(); val balanceBeforeInsufficient=balance(scope)
        amount("500"); click(page,R.string.dose_save_selection); click(page,R.string.dose_confirm); error(R.string.dose_confirm_failed)
        check(scope.preparation.current()?.let { it.attempted && !it.confirmed && it.amountMg==500000L }==true &&
            events()==beforeInsufficient && balance(scope)==balanceBeforeInsufficient)
        captureState(page,directory,"insufficient-stock-unresolved-intent"); record("insufficient-stock-not-replaced-or-auto-retried")
        click(page,R.string.dose_discard); dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.dose_discard_confirm)
        check(scope.preparation.current()==null && events()==beforeInsufficient && balance(scope)==balanceBeforeInsufficient)
        record("insufficient-explicit-discard-authoritative-no-refund")
        check(scope.custom.list().isEmpty() && services().isEmpty())
        // The earlier non-shot operations must not have changed history/journal/usage; record before deliberate observation separately.
        File(directory,"bean-workflow-business.json").writeText(JSONObject().put("cases",cases).put("completed",true)
            .put("noMachineOrServiceCommands",true).put("homeServiceRouteCoverage","NOT_COVERED_BY_THIS_NO_SERVICE_SCOPE")
            .put("associationSource","legal_ShotHistory_domain_observation_not_hardware_telemetry")
            .put("otherModelsInitialFingerprint",frozen).toString(2))
        close(page)
    }
    private fun detailsRoot(activity: Activity): View = android.view.inspector.WindowInspector.getGlobalWindowViews()
        .single { it!==activity.window.decorView && it.hasWindowFocus() }
    private fun dismissDetails(activity: Activity) {
        await("native details dialog focused") { android.view.inspector.WindowInspector.getGlobalWindowViews().any { it!==activity.window.decorView && it.hasWindowFocus() } }
        main { check(requireNotNull(detailsRoot(activity).findViewById<View>(android.R.id.button1)).performClick()) }
        await("native details returned") { activity.hasWindowFocus() }
    }
    private fun batchBusiness(initial: Activity, scope: Scope, directory: File) {
        var page=initial; val frozen=otherModels(scope); val cases=JSONArray()
        fun record(name: String) {
            check(otherModels(scope)==frozen && scope.preparation.current()==null && services().isEmpty())
            cases.put(JSONObject().put("case",name).put("balanceMg",balance(scope)).put("events",JSONArray(scope.inventory.events().map { it.toString() })))
            File(directory,"bean-workflow-business.json").writeText(JSONObject().put("cases",cases).put("completed",false).toString(2))
        }
        fun fill(target: String, reason: String) { main {
            (field(page,"targetField") as EditText).setText(target); (field(page,"reasonField") as EditText).setText(reason) } }
        fun inlineError(id: Int) { main {
            check(nodes(requireNotNull(dialog(page).window).decorView).filterIsInstance<TextView>().any { it.text.toString()==page.getString(id) }) } }
        val before=scope.inventory.events()
        click(page,R.string.beans_adjust); fill("80","复核盘点")
        main {
            val target=field(page,"targetField") as EditText; val reason=field(page,"reasonField") as EditText
            check(target.requestFocus()); target.setSelection(0,1)
            (page.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(target,0)
            check(target.minimumHeight>=HoyiUi.dp(page,52) && reason.minimumHeight>=HoyiUi.dp(page,52))
        }
        await("Batch adjustment actual IME") { requireNotNull(requireNotNull(dialog(page).window).decorView.rootWindowInsets).isVisible(WindowInsets.Type.ime()) }
        frames.committedFrame(requireNotNull(dialog(page).window).decorView)
        dialogSnapshot(page,directory,"adjustment-input-IME")
        main {
            val target=field(page,"targetField") as EditText
            check(target.height>=HoyiUi.dp(page,52) && whole(observation(target))) { "M4 focused adjustment input clipped with actual IME" }
            listOf(AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_POSITIVE).forEach { which ->
                val button=dialog(page).getButton(which)
                check(whole(observation(button)) && button.height>=HoyiUi.dp(page,48)) { "M4 native dialog action clipped with IME" }
            }
        }
        page=recreate(page,expectedAdjustmentDialog=true,directory=directory)
        main { check((field(page,"targetField") as EditText).text.toString()=="80" && (field(page,"reasonField") as EditText).text.toString()=="复核盘点") }
        check(scope.inventory.events()==before); record("adjustment-target-reason-drafts-recreate-no-write")
        dialogClick(page,AlertDialog.BUTTON_NEGATIVE,R.string.beans_cancel)
        check(scope.inventory.events()==before); record("adjustment-form-cancel-no-write")
        click(page,R.string.beans_adjust)
        for((target,reason,errorId) in listOf(Triple("bad","复核",R.string.beans_bad_quantity),Triple("-1","复核",R.string.beans_bad_quantity),
            Triple("80","",R.string.beans_reason_required),Triple("80","长".repeat(4097),R.string.beans_reason_required),
            Triple("90","无变化",R.string.beans_no_change))) {
            fill(target,reason); dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_preview); inlineError(errorId)
            check(scope.inventory.events()==before)
        }
        record("invalid-target-negative-empty-long-reason-and-no-change-rejected")
        fill("80","复核盘点")
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_preview)
        check(scope.inventory.events()==before); dialogSnapshot(page,directory,"adjustment-preview-no-write")
        dialogClick(page,AlertDialog.BUTTON_NEGATIVE,R.string.beans_edit)
        check(scope.inventory.events()==before); record("adjustment-preview-cancel-no-write")
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_preview)
        scope.rejectInventoryWrites=true
        try { dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_confirm_adjust); inlineError(R.string.beans_save_failed)
            check(scope.inventory.events()==before); dialogSnapshot(page,directory,"adjustment-write-failed"); record("adjustment-write-failed-no-publish")
        } finally { scope.rejectInventoryWrites=false }
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_preview)
        lateinit var staleConfirm: Button
        main { staleConfirm=dialog(page).getButton(AlertDialog.BUTTON_POSITIVE) }
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_confirm_adjust); main { staleConfirm.performClick() }
        check(balance(scope)==80000L && scope.inventory.events().size==before.size+1 && scope.inventory.events().last().let {
            it.kind==io.openhoyi.bean.InventoryEventKind.ADJUST && it.deltaMg == -10000L && it.reason=="复核盘点" })
        captureState(page,directory,"adjusted-exactly-once"); record("explicit-adjust-confirm-stale-duplicate-click-only-one-event")
        click(page,R.string.beans_adjust); fill("70","并发后确认")
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_preview)
        val concurrentBefore=scope.inventory.events()
        scope.inventory.adjust("m4-concurrent-adjust",scope.batchId,1000L,"另一合法盘点")
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_confirm_adjust); inlineError(R.string.beans_changed)
        check(balance(scope)==81000L && scope.inventory.events().size==concurrentBefore.size+1)
        dialogSnapshot(page,directory,"adjustment-concurrent-conflict-no-auto-retry"); record("concurrent-balance-re-read-no-stale-commit")
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_preview)
        dialogClick(page,AlertDialog.BUTTON_POSITIVE,R.string.beans_confirm_adjust)
        check(balance(scope)==70000L && scope.inventory.events().size==concurrentBefore.size+2 && scope.inventory.events().last().deltaMg == -11000L)
        record("concurrent-change-explicit-new-preview-confirm-only")
        val longReason="盘点修正：保留完整的库存核对说明。".repeat(30)
        scope.inventory.adjust("m4-long-reason-adjust",scope.batchId,1000L,longReason)
        page=recreate(page)
        val row=requireNotNull(root(page).findViewWithTag<View>("product/batch/events/row/m4-long-reason-adjust"))
        main { check(row.height>=HoyiUi.dp(page,48)); row.requestRectangleOnScreen(Rect(0,0,row.width,row.height),true) }
        frames.committedFrame(root(page)); main { check(row.performClick()) }
        lateinit var expectedDetailsDecor:View
        await("long-reason native details window ready") {
            val candidates=android.view.inspector.WindowInspector.getGlobalWindowViews().filter {
                it!==page.window.decorView && it.isAttachedToWindow && it.isLaidOut && it.hasWindowFocus()
            }
            if(candidates.isEmpty()) false else { expectedDetailsDecor=candidates.single();true }
        }
        main {
            val actual=detailsRoot(page)
            check(actual===expectedDetailsDecor && actual.isAttachedToWindow && actual.isLaidOut && actual.hasWindowFocus())
            check(nodes(actual).filterIsInstance<TextView>().any { it.text.toString().contains(longReason) })
        }
        dismissDetails(page); record("long-reason-original-detail-disclosure-no-data-truncation")
        val ledgerMonitor=test.addMonitor(BeanLedgerActivity::class.java.name,null,false)
        val ledger: Activity
        try { click(page,R.string.beans_ledger); ledger=test.waitForMonitorWithTimeout(ledgerMonitor,10000) ?: error("M4 full ledger route missing") }
        finally { test.removeMonitor(ledgerMonitor) }
        check(ledger.intent.getStringExtra(BeanBatchActivity.BATCH_ID)==scope.batchId); close(ledger); record("full-ledger-keeps-real-batch-id")
        close(page)
        val missing=start(BeanBatchActivity::class.java,"m4-missing-batch")
        main { check(nodes(root(missing)).filterIsInstance<TextView>().any { it.text.toString()==missing.getString(R.string.beans_missing) }) }
        captureState(missing,directory,"missing-batch-read-only"); close(missing); record("missing-batch-original-empty-handling-no-write")
        File(directory,"bean-workflow-business.json").writeText(JSONObject().put("cases",cases).put("completed",true)
            .put("noMachineOrServiceCommands",true).put("otherModelsAndPreparationUnchanged",true).toString(2))
    }
    private fun gesture(view: View) {
        val visible=Rect(); main { check(view.isShown && view.getGlobalVisibleRect(visible) && visible.height()>64) }
        val x=visible.exactCenterX(); val start=visible.top+visible.height()*.82f; val end=visible.top+visible.height()*.22f
        val down=SystemClock.uptimeMillis()
        fun event(action: Int,y: Float) {
            val value=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).apply { source=android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { check(test.uiAutomation.injectInputEvent(value,true)) } finally { value.recycle() }
        }
        event(android.view.MotionEvent.ACTION_DOWN,start)
        for(step in 1..16) { SystemClock.sleep(25); event(android.view.MotionEvent.ACTION_MOVE,start+(end-start)*step/16) }
        // Gesture event timing removes fling; never an arbitrary acceptance sleep or retry.
        repeat(4) { SystemClock.sleep(50); event(android.view.MotionEvent.ACTION_MOVE,end) }
        event(android.view.MotionEvent.ACTION_UP,end)
    }
    private fun ledgerBusiness(initial: Activity, scope: Scope, directory: File) {
        var page=initial; val frozen=otherModels(scope); val cases=JSONArray(); val original=scope.inventory.events()
        fun record(name: String) { check(otherModels(scope)==frozen && scope.preparation.current()==null && services().isEmpty())
            cases.put(JSONObject().put("case",name)); File(directory,"bean-workflow-business.json").writeText(JSONObject().put("cases",cases).put("completed",false).toString(2)) }
        // Add real append-only events only to the owned legal store, AFTER retaining the three-event primary screenshot.
        repeat(24) { index -> scope.inventory.adjust("m4-scroll-adjust-$index",scope.batchId,if(index%2==0) 1000L else -1000L,"滚动核对 ${index+1}") }
        page=recreate(page)
        fun scroll()=field(page,"listScroll") as ScrollView
        stableWindow(page,directory,"ledger-before-gesture"); gesture(scroll()); stableWindow(page,directory,"ledger-after-gesture")
        val beforeY=scroll().scrollY; check(beforeY>0) { "M4 actual ledger gesture did not establish nonzero viewport" }
        val expected=scope.inventory.events().filter { it.batchId==scope.batchId }.asReversed().map { it.eventId }
        fun ids()=nodes(scroll()).mapNotNull { it.tag?.toString()?.takeIf { it.startsWith("product/ledger/row/") }?.removePrefix("product/ledger/row/") }
        main { check(ids()==expected)
            val row=nodes(scroll()).first { it.tag?.toString()?.startsWith("product/ledger/row/")==true && whole(observation(it)) }
            check(row.height>=HoyiUi.dp(page,48) && row.performClick()) }
        dismissDetails(page); stableWindow(page,directory,"ledger-after-detail-return")
        check(scroll().scrollY==beforeY); record("ledger-detail-return-keeps-exact-nonzero-scroll-and-order")
        page=recreate(page); stableWindow(page,directory,"ledger-after-recreate")
        main { check(scroll().scrollY==beforeY && ids()==expected) }
        captureState(page,directory,"ledger-nonzero-restored"); record("ledger-recreate-original-savedScrollY-and-event-order")
        scope.inventory.addBatch("m4-ledger-other-add",BeanBatch("m4-ledger-other-batch",Bean("m4-ledger-other-bean","全库核对另一批"),50000L))
        val beforeBrowse=scope.inventory.events()
        val batchMonitor=test.addMonitor(BeanBatchActivity::class.java.name,null,false)
        val batch: Activity
        try { click(page,R.string.beans_batch_title); batch=test.waitForMonitorWithTimeout(batchMonitor,10000) ?: error("M4 ledger batch-return missing") }
        finally { test.removeMonitor(batchMonitor) }
        check(batch.intent.getStringExtra(BeanBatchActivity.BATCH_ID)==scope.batchId); close(batch)
        await("Ledger returned from batch") { page.hasWindowFocus() }
        stableWindow(page,directory,"ledger-after-batch-return"); check(scroll().scrollY==beforeY)
        check(scope.inventory.events()==beforeBrowse); record("ledger-open-batch-return-is-read-only-restores-viewport")
        close(page)
        val all=start(BeanLedgerActivity::class.java)
        main {
            check(nodes(root(all)).filter { it.tag?.toString()?.startsWith("product/ledger/row/")==true }.size==scope.inventory.events().size)
            check(root(all).findViewWithTag<View>("product/ledger/summary")==null && root(all).findViewWithTag<View>("product/ledger/balance/number")==null) {
                "M4 all-inventory ledger must not invent a combined batch balance"
            }
        }
        captureState(all,directory,"all-inventory-ledger-no-fake-combined-balance"); close(all); record("all-library-ledger-original-no-batch-filter")
        val missing=start(BeanLedgerActivity::class.java,"m4-missing-batch")
        main { check(nodes(root(missing)).none { it.tag?.toString()?.startsWith("product/ledger/row/")==true }) }
        captureState(missing,directory,"missing-batch-ledger"); close(missing)
        check(scope.inventory.events()==beforeBrowse && beforeBrowse.containsAll(original)); record("missing-filter-does-not-invent-events-or-write")
        File(directory,"bean-workflow-business.json").writeText(JSONObject().put("cases",cases).put("completed",true)
            .put("noMachineOrServiceCommands",true).put("readOnlyUiInventoryUnchanged",true)
            .put("scopedScrollAdjustmentsAddedAfterPrimary",24).put("scopedOtherBatchAddedForFilterCoverage",true).put("originalEventsRetained",true).toString(2))
    }
    /** Exactly one of three real pages in one requested profile per fresh process. Host requires all 12. */
    fun run(arguments: Bundle): Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("beanWorkflowPage")); check(page in setOf("preparation","batch","ledger"))
        val source=requireNotNull(arguments.getString("sourceSha")); check(source.matches(Regex("[0-9a-f]{40}")))
        val theme=requireNotNull(arguments.getString("sampleTheme")); check(theme in setOf("light","dark")); val dark=theme=="dark"
        val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat(); check(scale in setOf(1f,1.3f))
        val profile="tablet-user-landscape-$theme-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"bean-workflow-visual-migration/$source/$profile/$page").apply { check(isDirectory || mkdirs()) }
        val expectedReference=when(page) {
            "preparation" -> "ff154e547f7a8ff73be115e10560646aa4754d18b5807bd6b84d554735701cd7"
            "batch" -> "81ccf1510d6f780bd0e1e83b06130b9521820f2d51a89e045cfe4841158614ca"
            else -> "114940f8efb35918334c5281d705e1a981394bb989110f617f9a0c7b4cf2f6ba"
        }
        check(arguments.getString("referencePngSha256")==expectedReference)
        val manifest=JSONObject().put("sourceSha",source).put("sourceDirty",arguments.getString("sourceDirty"))
            .put("headSha",arguments.getString("headSha")).put("apkSha256",arguments.getString("apkSha"))
            .put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("referenceDesignPngSha256",expectedReference).put("referenceIdentitySource","host_hash_of_committed_original_design_not_APK_asset")
            .put("profile",profile).put("page",page).put("realProductActivity",true).put("specimenOnly",false).put("visualAcceptance","ASTRA_PENDING")
            .put("pid",android.os.Process.myPid()).put("scopePolicy","M4_no_Home_no_service; ScaleOwner_flags0_real_refresh_unchanged")
        var scope: Scope?=null; var failure: Throwable?=null
        try {
            val active=Scope(directory,page); scope=active; active.prepare(); active.install(dark)
            File(directory,"fixture.json").writeText(active.fixture.toString(2))
            val preferences=context.getSharedPreferences("curves",Context.MODE_PRIVATE).all.toMap()
            val activity=when(page) {
                "preparation" -> start(BeanPreparationActivity::class.java,active.batchId)
                "batch" -> start(BeanBatchActivity::class.java,active.batchId)
                else -> start(BeanLedgerActivity::class.java,active.batchId)
            }
            if(page=="preparation") main { (field(activity,"amount") as EditText).setText("18") }
            unfocus(activity); frames.committedFrame(root(activity)); check(services().isEmpty())
            val captured=frames.screenshot(activity,root(activity),File(directory,"window.png")) { geometry(activity) }
            val image=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,image)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(image.outWidth,image.outHeight)))
                .put("wholeWindowPngSizeSource","original_unmodified_window.png_header_not_resources_or_physical_panel_claim")
                .put("initialFrameSync",captured.proof)
            check(image.outWidth==2560 && image.outHeight==1600 && captured.proof.getInt("protocolVersion")==2 && captured.proof.getBoolean("accepted"))
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use { input ->
                val digest=MessageDigest.getInstance("SHA-256"); val bytes=ByteArray(8192)
                while(true) { val count=input.read(bytes); if(count<0) break; digest.update(bytes,0,count) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            manifest.put("fontSha256",font); check(font==TaskProductVisualStyle.FONT_SHA)
            val targets=mutableListOf<View>(); val fixed=mutableListOf<View>(); val initialRequired=mutableSetOf<Int>()
            main {
                val actual=geometry(activity); manifest.put("window",actual); File(directory,"geometry.json").writeText(actual.toString(2))
                check(actual.getDouble("fontScale").toFloat()==scale && actual.getString("uiModeNightMeaning")==theme &&
                    actual.getInt("densityDpi")==360 && actual.getString("locale").startsWith("zh") && !actual.getBoolean("imeVisible"))
                TaskTypographyEvidence.capture(activity,directory)
                val pairs=baselines(activity); File(directory,"baseline-alignment.json").writeText(pairs.toString(2))
                check(pairs.length()>=2); (0 until pairs.length()).forEach { check(pairs.getJSONObject(it).getString("status")!="FAIL") }
                val prefixes=when(page) {
                    "preparation" -> listOf("product/preparation/preview/before","product/preparation/preview/after")+
                        active.inventory.batches().map { "product/preparation/choice/${it.batch.id}/balance" }
                    "batch" -> listOf("product/batch/balance","product/batch/initial")
                    else -> listOf("product/ledger/balance","product/ledger/initial")
                }
                prefixes.forEach { prefix -> check(root(activity).findViewWithTag<TextView>("$prefix/number")?.isShown==true &&
                    root(activity).findViewWithTag<TextView>("$prefix/unit")?.isShown==true) { "M4 number/unit evidence missing: $prefix" } }
                val standard=activity.resources.configuration.let { it.screenWidthDp>=1000 && it.screenHeightDp>=600 && it.fontScale<=1.1f }
                when(page) {
                    "preparation" -> {
                        check(active.preparation.current()==null && balance(active)==214000L)
                        check((field(activity,"amount") as EditText).text.toString()=="18" && (field(activity,"amount") as EditText).height>=HoyiUi.dp(activity,52))
                        check((field(activity,"capture") as Button).isEnabled.not() &&
                            (field(activity,"scaleGuard") as TextView).text.toString()==activity.getString(R.string.dose_scale_wait))
                        check(ScaleOwnerActivity::class.java.getDeclaredField("owner").apply { isAccessible=true }.get(activity)==null)
                        val preview=requireNotNull(root(activity).findViewWithTag<TextView>("dose-balance-preview"))
                        check(preview.text.toString()==activity.getString(R.string.dose_balance_preview,"214","196"))
                        active.inventory.batches().forEach { batch ->
                            val row=requireNotNull(root(activity).findViewWithTag<View>("dose-batch-${batch.batch.id}"))
                            check(row.isClickable && row.isEnabled && row.height>=HoyiUi.dp(activity,72)); targets.add(row)
                        }
                        listOf(field(activity,"amount") as View,field(activity,"capture") as View,field(activity,"scaleGuard") as View,
                            requireNotNull(root(activity).findViewWithTag<View>("product/preparation/preview"))).forEach(targets::add)
                        fixed.add(requireNotNull(root(activity).findViewWithTag("product/preparation/actions")))
                        listOf(R.string.dose_save_selection,R.string.dose_return).forEach { id ->
                            val action=action(activity,id); check(whole(observation(action)) && action.height>=HoyiUi.dp(activity,if(id==R.string.dose_save_selection) 56 else 48)) }
                    }
                    "batch","ledger" -> {
                        val prefix=if(page=="batch") "product/batch" else "product/ledger"
                        check(root(activity).findViewWithTag<TextView>("$prefix/balance/number").text.toString()==if(page=="batch") "90" else "214")
                        check(root(activity).findViewWithTag<TextView>("$prefix/initial/number").text.toString()==if(page=="batch") "100" else "250")
                        val events=active.inventory.events().filter { it.batchId==active.batchId }.asReversed()
                        events.forEachIndexed { index,event ->
                            val id=if(page=="batch") "product/batch/events/row/${event.eventId}" else "product/ledger/row/${event.eventId}"
                            val row=requireNotNull(root(activity).findViewWithTag<View>(id))
                            check(row.isClickable && row.height>=HoyiUi.dp(activity,48)); targets.add(row)
                            if(standard) initialRequired.add(index)
                        }
                        if(page=="batch") {
                            listOf("2026-10-02","2026-09-28","2026-10-03").forEachIndexed { index,date ->
                                val value=requireNotNull(root(activity).findViewWithTag<TextView>("product/batch/date/$index/value")); check(value.text.toString()==date); targets.add(value) }
                            fixed.add(requireNotNull(root(activity).findViewWithTag("product/form/batch/footer")))
                            listOf(R.string.beans_adjust,R.string.beans_ledger).forEach { id ->
                                val view=action(activity,id); check(whole(observation(view)) && view.height>=HoyiUi.dp(activity,if(id==R.string.beans_adjust) 56 else 48)) }
                        } else {
                            val header=requireNotNull(root(activity).findViewWithTag<View>("product/ledger/header")); check(whole(observation(header)))
                            fixed.add(header); fixed.add(action(activity,R.string.beans_batch_title))
                        }
                    }
                }
            }
            reach(activity,directory,targets,initialRequired,fixed)
            when(page) {
                "preparation" -> preparationBusiness(activity,active,directory)
                "batch" -> batchBusiness(activity,active,directory)
                else -> ledgerBusiness(activity,active,directory)
            }
            check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).all==preferences)
            manifest.put("layoutStatus","PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
        } catch(error: Throwable) {
            failure=error; manifest.put("layoutStatus","FAIL").put("failure",error.stackTraceToString())
            scope?.activeActivity()?.let { page -> try { diagnostic(page,File(directory,"failure-context")) } catch(capture: Throwable) { error.addSuppressed(capture) } }
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

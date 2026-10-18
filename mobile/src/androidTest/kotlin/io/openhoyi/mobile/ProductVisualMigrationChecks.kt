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
import android.os.Handler
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ScrollView
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

/** M1 real Activities, legal scoped stores. No BLE/shot commands or external share receivers. */
internal class ProductVisualMigrationChecks(private val test: Instrumentation) {
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
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "M1 timeout: $description" }
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
        }
        return JSONObject().put("semanticId", view.tag).put("rectPx", rect(full)).put("visibleRectPx", rect(visible))
            .put("complete", shown && view.width > 0 && view.height > 0 && visible == full)
            .put("textLineBoxes", JSONArray(texts))
    }
    private fun whole(value: JSONObject): Boolean = value.getBoolean("complete") && value.getJSONArray("textLineBoxes").let { lines ->
        (0 until lines.length()).all { index -> val line = lines.getJSONObject(index)
            line.getBoolean("fullViewVisible") && line.getBoolean("layoutPresent") && line.getInt("lineCount") > 0 &&
                line.getInt("ellipsisCount") == 0 && line.getInt("layoutHeightPx") <= line.getInt("contentHeightPx") }
    }
    private fun captureReachable(activity: Activity, directory: File, ids: List<String>, initialRequired: Set<String>): JSONArray {
        val results = JSONArray()
        ids.forEachIndexed { index, id ->
            lateinit var view: View; lateinit var before: JSONObject
            lateinit var scrolls: List<Triple<ScrollView, Int, Int>>
            main {
                view = nodes(root(activity)).single { it.tag == id }
                before = observation(view)
                scrolls = generateSequence(view.parent) { it.parent }.filterIsInstance<ScrollView>().map { Triple(it, it.scrollX, it.scrollY) }.toList()
            }
            lateinit var after: JSONObject; lateinit var proof: JSONObject
            var restored = false
            val image = "after-scroll-${index + 1}.png"
            try {
                main { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }
                val captured = frames.screenshot(activity, root(activity), File(directory, image)) { observation(view) }
                after = captured.target; proof = captured.proof
            } finally {
                main { scrolls.forEach { (scroll, x, y) -> scroll.scrollTo(x, y) } }
                frames.committedFrame(root(activity))
                main { restored = scrolls.all { (scroll, x, y) -> scroll.scrollX == x && scroll.scrollY == y } }
            }
            val pass = whole(after) && restored && (id !in initialRequired || whole(before))
            results.put(JSONObject().put("semanticId", id).put("initial", before).put("afterScroll", after)
                .put("initialRequired", id in initialRequired).put("scrollRestored", restored)
                .put("initialPng", "window.png").put("afterScrollPng", image).put("frameSync", proof)
                .put("status", if (pass) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL"))
            File(directory, "scroll-reachability.json").writeText(results.toString(2))
        }
        return results
    }
    private fun baselines(activity: Activity, page: String): JSONArray {
        val pairs = when (page) {
            "home" -> (0..1).map { "product/home/summary/$it/values/number" to "product/home/summary/$it/values/unit" } +
                ("home-dose-quantity" to "product/home/dose/unit")
            "share" -> (0..1).map { "product/share/metric-$it/number" to "product/share/metric-$it/unit" }
            else -> listOf("product/inventory/balance/number" to "product/inventory/balance/unit")
        }
        return JSONArray(pairs.map { (number, unit) ->
            fun baseline(id: String): Int { val view = nodes(root(activity)).single { it.tag == id } as TextView
                check(view.baseline >= 0); val point = IntArray(2); view.getLocationOnScreen(point); return point[1] + view.baseline }
            val a = baseline(number); val b = baseline(unit)
            JSONObject().put("numberSemanticId", number).put("unitSemanticId", unit)
                .put("numberBaselineScreenPx", a).put("unitBaselineScreenPx", b).put("baselineDeltaPx", b-a)
                .put("status", if (a == b) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
        })
    }
    private fun geometry(activity: Activity): JSONObject {
        val content = root(activity); val origin = IntArray(2); content.getLocationOnScreen(origin)
        val insets = requireNotNull(content.rootWindowInsets)
        val bars = insets.getInsets(WindowInsets.Type.systemBars())
        val metrics = activity.resources.displayMetrics; val config = activity.resources.configuration
        return JSONObject().put("resourcesDisplayMetricsPx", JSONArray(listOf(metrics.widthPixels, metrics.heightPixels)))
            .put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("maximumWindowMetricsBoundsPx",rect(activity.windowManager.maximumWindowMetrics.bounds))
            .put("windowMetricsSemantics","Android_window_metrics_not_physical_panel_dimensions; whole_display_PNG_size_recorded_separately")
            .put("appOriginPx", JSONArray(origin.toList())).put("appSizePx", JSONArray(listOf(content.width, content.height)))
            .put("systemInsetsPx", JSONArray(listOf(bars.left, bars.top, bars.right, bars.bottom)))
            .put("imeVisible", insets.isVisible(WindowInsets.Type.ime())).put("imeApplicability", "N/A_three_read_only_pages_no_editor_focus")
            .put("densityDpi", config.densityDpi).put("fontScale", config.fontScale).put("locale", config.locales.toLanguageTags())
            .put("nodes", JSONArray(nodes(content).filter { it.tag is String }.map { observation(it) }))
    }
    private inner class Scope(private val page: String, private val directory: File) {
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
        private val ownedDirectory = File(context.cacheDir, "product-visual-${UUID.randomUUID()}")
        private val tracked = linkedSetOf<Activity>()
        private val destroyed = linkedSetOf<Activity>()
        var service: MobileService? = null
        val inventory = BeanInventory(object : InventoryStorage { var value: ByteArray? = null
            override fun read() = value?.clone(); override fun write(bytes: ByteArray) { value = bytes.clone() } })
        val preparation = BeanPreparation(object : BeanPreparation.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val custom = CustomCurveStore(object : CustomCurveStore.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val usage = CurveUsageLedger(object : CurveUsageLedger.Storage { var value = ""
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val journal = BrewJournal(object : BrewJournal.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val feedback = BrewFeedbackPreference(object : BrewFeedbackPreference.Storage {
            override fun read() = false; override fun write(enabled: Boolean) = !enabled })
        val logs = TraceStore(File(ownedDirectory, "traces"))
        val samples = ShotSamplesRepository(File(ownedDirectory, "samples")) { logs.record("fixture.samples_error", mapOf("type" to it.javaClass.simpleName)) }
        val curves: CurveLibrary
        val history: ShotHistory
        val document: CustomCurveDocument
        val batchId = "m1-selected-batch"
        val fixture: JSONObject
        private val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { tracked.add(activity) }
            override fun onActivityDestroyed(activity: Activity) { destroyed.add(activity) }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        init {
            val targets = if (page == "share") listOf(30,90,70,50) else listOf(20,90,80,60)
            document = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000001", "经典浓缩", 93, 3600,
                CustomCurveDocument.ControlMode.PRESSURE, targets.zip(listOf(150,250,150,50)).map { CustomCurveDocument.Stage(it.first,it.second) })
            custom.save(document)
            val other = listOf(document.copy(id="draft-00000000-0000-0000-0000-000000000002", name="柔和浓缩"), document.copy(id="draft-00000000-0000-0000-0000-000000000003", name="渐降浓缩"))
            other.forEach(custom::save)
            (listOf(document) + other).forEachIndexed { index, doc -> usage.record("m1-usage-$index", doc.id, 1700000000000L-index*1000) }
            val factory = FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val proof = FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"), factory, context.assets.open("factory_slot_wire_v1.tsv"))
            curves = CurveLibrary(factory, proof, { custom.items() }, true)
            check(curves.canStart(requireNotNull(curves.find(document.id))))
            val dates = listOf(LocalDate.of(2026,10,2), LocalDate.of(2026,9,28), LocalDate.of(2026,10,3))
            listOf(100000L,160000L,250000L).forEachIndexed { index, amount ->
                inventory.addBatch("m1-add-$index", BeanBatch(if(index==0) batchId else "m1-batch-$index",
                    Bean("m1-bean-$index", listOf("巴西 黄波旁", "埃塞俄比亚 耶加雪菲", "哥伦比亚 水洗")[index]), amount, dates[0],dates[1],dates[2]))
            }
            if (page == "home") {
                val bean = inventory.batches().first().batch.bean
                preparation.select("m1-dose-consume", batchId, bean.id, bean.name, 18000)
                preparation.confirm(inventory); preparation.verifyConfirmed(inventory)
            } else if (page == "inventory") inventory.adjust("m1-adjust",batchId,-10000,"盘点调整")
            history = ShotHistory(object : ShotHistory.Storage { var value = ""
                override fun read() = value; override fun write(value: String) { this.value = value }
            }, onObservedUse = { entry -> usage.record(entry.id,entry.curveId,entry.startedAtMs) }, onEntryChanged = { entry ->
                journal.observe(entry.journalObservation())
                if (preparation.current()?.shotId == entry.id) preparation.associatePending(journal, inventory)
            })
            journal.reconcileRecent(history.entries.map { it.journalObservation() })
            preparation.associatePending(journal,inventory)
            samples.prune(history.entries.map(ShotHistory.Entry::id).toSet())
            fixture = JSONObject().put("page",page).put("document",document.toJson())
                .put("usage",JSONArray(custom.list().map { JSONObject().put("curveId",it.id).put("count",usage.stats(it.id).count).put("lastUsedAtMs",usage.stats(it.id).lastUsedAtMs) }))
                .put("batches",JSONArray(inventory.batches().map { item -> JSONObject().put("batchId",item.batch.id).put("beanId",item.batch.bean.id)
                    .put("beanName",item.batch.bean.name).put("initialMg",item.batch.initialMg).put("balanceMg",item.balanceMg)
                    .put("purchasedOn",item.batch.purchasedOn).put("roastedOn",item.batch.roastedOn).put("openedOn",item.batch.openedOn) }))
                .put("inventoryEvents",JSONArray(inventory.events().map { JSONObject().put("id",it.eventId).put("batch",it.batchId).put("kind",it.kind).put("deltaMg",it.deltaMg).put("balanceMg",it.balanceMg) }))
                .put("preparation",preparation.current()?.let { JSONObject().put("amountMg",it.amountMg).put("confirmed",it.confirmed).put("attempted",it.attempted).put("shotId",it.shotId ?: JSONObject.NULL).put("associated",it.associated).put("eventId",it.eventId).put("batchId",it.batchId) } ?: JSONObject.NULL)
                .put("source","legal_memory_Storage_and_scoped_owned_cache_writers").put("mockDeviceState","MobileService_MockDeviceRuntime_no_BLE")
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
                check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).edit().putString("selected",document.id).commit())
            }
        }
        fun cleanup() {
            val proof = JSONObject().put("scopedOwnedDirectory",ownedDirectory.absolutePath)
            try {
                if (installed) {
                    main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
                    await("all fixture Activities destroyed") { tracked.all { it.isDestroyed && it in destroyed } }
                    main { context.stopService(Intent(context,MobileService::class.java)) }
                    await("Mock service removed and onDestroy postconditions") {
                        services().isEmpty() && (service?.let { owner ->
                            val id = field(owner,"ownerId")
                            val handler = field(owner,"handler") as Handler
                            val noCallbacks = listOf("mockTick","watchShot","leaveForeground","stopAutomaticScale").all { !handler.hasCallbacks(field(owner,it) as Runnable) }
                            val observers = field(feedback,"observers") as Map<*,*>
                            val visibility = field(app.visibility,"observers") as Map<*,*>
                            noCallbacks && id !in observers && id !in visibility && field(owner,"hub") == null
                        } ?: true)
                    }
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("serviceRemovedAndOnDestroyPostconditions",true).put("mainQueueBarrierAfterDestroy",true)
                }
                val writer = field(samples,"writer") as ExecutorService
                writer.shutdown(); check(writer.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped sample writer did not terminate" }
                logs.close(); check(logs.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped trace writer did not terminate" }
                proof.put("scopedWritersTerminated",true)
                if (installed) main {
                    saved.forEach { (name,lazy) -> write(app,name+"\$delegate",lazy) }
                    write(app,"languagePreferences",oldLanguage); write(app,"journalObservationFailure",oldFailure)
                    savedPrefs.forEach { (name,values) ->
                        val preferences = context.getSharedPreferences(name,Context.MODE_PRIVATE)
                        val key = if (name == "appearance") "dark" else "selected"
                        val edit = preferences.edit()
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
                        check(edit.commit()) { "Cannot restore whitelist preference: $name/$key" }
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
                        .put("whitePrefPhysicalBytesMayDiffer","only appearance/curves typed restoration; no file deletion or byte rollback")
                }
                proof.put("status","PASS_ISOLATION_ONLY")
            } catch (failure: Throwable) {
                proof.put("status","FAIL").put("failure",failure.stackTraceToString())
                // Do not restore original delegates while a scoped Activity/service/writer may still hold references.
                throw failure
            } finally { File(directory,"fixture-isolation.json").writeText(proof.toString(2)) }
        }
    }
    private fun homeRoutes(activity: HomeActivity, scope: Scope): JSONArray {
        val results = JSONArray(); val prefs=context.getSharedPreferences("curves",Context.MODE_PRIVATE)
        val owner=requireNotNull(scope.service)
        val mock=requireNotNull(field(owner,"mock"))
        val beforeEvents=scope.inventory.events(); val beforeDose=scope.preparation.current()
        val beforeUsage=scope.custom.list().associate { it.id to scope.usage.stats(it.id) }
        main {
            val selected=prefs.getString("selected",null)
            val oldState=field(mock,"shotState")
            fun render() = HomeActivity::class.java.getDeclaredMethod("render").apply { isAccessible=true }.invoke(activity)
            try {
                val readOnly=scope.document.copy(id="draft-00000000-0000-0000-0000-000000000004", controlMode=CustomCurveDocument.ControlMode.FLOW_RAW,
                    stages=listOf(CustomCurveDocument.Stage(60,150)))
                scope.custom.save(readOnly)
                listOf("missing" to null,"read-only" to readOnly.id,"startable" to scope.document.id,"active" to null).forEach { (state,id) ->
                    check(prefs.edit().putString("selected",id).commit())
                    write(mock,"shotState",if(state=="active") ExtractionState.RUNNING else ExtractionState.IDLE)
                    render()
                    val rows=nodes(root(activity)).filter { it.tag?.toString()?.startsWith("home-recent-curve-")==true && it.isShown }
                    if(state=="active") check(rows.all { !it.isEnabled }) { "Active cup recent choices enabled" }
                    var dispatched: Intent?=null; var count=0
                    val monitor=object: Instrumentation.ActivityMonitor() {
                        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult { dispatched=Intent(intent); count++; return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null) }
                    }
                    test.addMonitor(monitor)
                    try {
                        val action=field(activity,"brewButton") as View
                        check(action.isEnabled && action.performClick())
                        val expected=if(state in setOf("active","startable")) ExtractionActivity::class.java.name else CurveActivity::class.java.name
                        check(count==1 && dispatched?.component?.className==expected) { "Wrong Home route $state: $dispatched" }
                        results.put(JSONObject().put("state",state).put("destination",expected).put("intercepted",true)
                            .put("scope","UI_route_branch_only_not_extraction_lifecycle_or_hardware").put("noStartStopShot",true))
                    } finally { test.removeMonitor(monitor) }
                }
            } finally { write(mock,"shotState",oldState); check(prefs.edit().putString("selected",selected).commit()); render() }
        }
        check(scope.inventory.events()==beforeEvents && scope.preparation.current()==beforeDose)
        check(beforeUsage.all { (id,value) -> scope.usage.stats(id)==value }) { "Routing changed usage ledger" }
        return results
    }
    fun run(arguments: Bundle): Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("productPage")); check(page in setOf("home","share","inventory"))
        val source=requireNotNull(arguments.getString("sourceSha")); check(source.matches(Regex("[0-9a-f]{40}")))
        val dark=arguments.getString("sampleTheme")=="dark"
        val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat()
        val profile="tablet-user-landscape-${if(dark) "dark" else "light"}-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"product-visual-migration/$source/$profile/$page").apply { check(isDirectory || mkdirs()) }
        val manifest=JSONObject().put("sourceSha",source).put("sourceDirty",arguments.getString("sourceDirty"))
            .put("headSha",arguments.getString("headSha")).put("api",Build.VERSION.SDK_INT).put("apkSha256",arguments.getString("apkSha")).put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("profile",profile).put("page",page).put("realProductActivity",true).put("specimenOnly",false)
            .put("visualAcceptance","ASTRA_PENDING").put("pid",android.os.Process.myPid()).put("noBle",true)
        var failure: Throwable?=null
        var scope: Scope?=null
        try {
            val activeScope=Scope(page,directory)
            scope=activeScope
            activeScope.install(dark)
            File(directory,"fixture.json").writeText(activeScope.fixture.toString(2))
            val type=when(page) { "home" -> HomeActivity::class.java; "share" -> CurveShareActivity::class.java; else -> BeanInventoryActivity::class.java }
            val activity=test.startActivitySync(Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
                if(page=="share") putExtra(CurveShareActivity.CURVE_ID,activeScope.document.id)
            })
            await("real Activity ready") { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
            if(activity is HomeActivity) await("same scoped Mock owner") {
                activeScope.service=field(activity,"service") as? MobileService; activeScope.service?.running==true
            }
            if(page=="inventory") { main {
                val row=root(activity).findViewWithTag<View>("inventory-batch-${activeScope.batchId}")
                check(row.isEnabled && row.performClick())
            }; frames.committedFrame(root(activity)) }
            check(kotlin.math.abs(activity.resources.configuration.fontScale-scale)<.01f)
            check(activity.resources.configuration.densityDpi==360 && activity.resources.configuration.locales[0].language=="zh")
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use { input ->
                val digest=MessageDigest.getInstance("SHA-256"); val buffer=ByteArray(8192)
                while(true) { val count=input.read(buffer); if(count<0) break; digest.update(buffer,0,count) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            check(font==TaskProductVisualStyle.FONT_SHA); manifest.put("fontSha256",font)
            val initial=frames.screenshot(activity,root(activity),File(directory,"window.png")) { geometry(activity) }
            val imageBounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,imageBounds)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(imageBounds.outWidth,imageBounds.outHeight)))
                .put("wholeWindowPngSizeSource","original_unmodified_window.png_header_not_resource_metrics_or_physical_panel_claim")
            check(imageBounds.outWidth==2560 && imageBounds.outHeight==1600) { "M1 original window PNG profile size mismatch" }
            lateinit var baseline: JSONArray
            main {
                TaskTypographyEvidence.capture(activity,directory)
                baseline=baselines(activity,page)
                File(directory,"baseline-alignment.json").writeText(baseline.toString(2))
                File(directory,"anchors.json").writeText(geometry(activity).toString(2))
                File(directory,"canvas.json").writeText(JSONArray(nodes(root(activity)).filterIsInstance<CurveStageView>().map { chart ->
                    JSONObject().put("semanticId",chart.tag).put("targets",JSONArray(chart.targets)).put("actualDrawOperations",chart.productDrawEvidence)
                }).toString(2))
                manifest.put("window",geometry(activity)).put("initialFrameSync",initial.proof)
            }
            val ids=when(page) {
                "home" -> listOf("product/home/state-machine","product/home/state-scale","product/home/confirmation")+(0..2).map { "home-recent-curve-$it" }
                "share" -> listOf("product/share/table/header")+(1..4).map { "product/share/table/row$it" }+"product/share/privacy"
                else -> listOf("product/inventory/events/row-m1-adjust","product/inventory/events/row-m1-add-0")
            }
            val initialRequired=when(page) {
                "home" -> setOf("product/home/confirmation")+if(scale==1f) ids.take(3).toSet()+ids.drop(3).take(2) else setOf(ids[3])
                "share" -> if(scale==1f) ids.dropLast(1).toSet() else emptySet()
                else -> if(scale==1f) ids.toSet() else emptySet()
            }
            val reached=captureReachable(activity,directory,ids,initialRequired)
            if(activity is HomeActivity) File(directory,"home-route-business.json").writeText(homeRoutes(activity,activeScope).toString(2))
            if(activity is CurveShareActivity) AdvancedBusinessUiChecks(test).verifyM1ExportContract(activity,activeScope.document,directory)
            if(activity is BeanInventoryActivity) AdvancedBusinessUiChecks(test).verifyM1InventoryThreeEvents(activity,activeScope.inventory,directory)
            val issues=JSONArray()
            (0 until baseline.length()).forEach { if(baseline.getJSONObject(it).getString("status")=="FAIL") issues.put("baseline:$it") }
            (0 until reached.length()).forEach { if(reached.getJSONObject(it).getString("status")=="FAIL") issues.put("reachability:$it") }
            manifest.put("mechanicalIssues",issues).put("layoutStatus",if(issues.length()==0) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            check(issues.length()==0) { "M1 actual layout failed after preserving original images: $issues" }
        } catch(error: Throwable) { failure=error; manifest.put("failure",error.stackTraceToString()).put("layoutStatus","FAIL") }
        finally {
            try { scope?.cleanup() } catch(error: Throwable) { if(failure==null) failure=error else failure!!.addSuppressed(error) }
            manifest.put("fileSha256",JSONObject(directory.listFiles().orEmpty().filter { it.isFile && it.name!="manifest.json" }.associate { it.name to sha(it) }))
            manifest.put("isolationStatus",if(failure==null) "PASS" else "SEE_fixture-isolation.json")
            File(directory,"manifest.json").writeText(manifest.toString(2))
        }
        failure?.let { throw it }
        return 1
    }
}

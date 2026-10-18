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
import android.widget.EditText
import android.widget.TextView
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import io.openhoyi.trace.TraceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** M2 real forms in fresh disposable Mock processes. M1 scopes and frame protocol are not modified. */
internal class FormVisualMigrationChecks(private val test: Instrumentation) {
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
            check(SystemClock.elapsedRealtime() < deadline) { "M2 timeout: $description" }
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
    private fun geometry(activity: Activity): JSONObject {
        val content = root(activity); val origin = IntArray(2); content.getLocationOnScreen(origin)
        val insets = requireNotNull(content.rootWindowInsets)
        val bars = insets.getInsets(WindowInsets.Type.systemBars())
        val metrics = activity.resources.displayMetrics; val config = activity.resources.configuration
        val form=field(activity,"form")
        val naturalBudget=when(form) {
            is TaskFormSections -> JSONObject(form.productLayoutEvidence.toString())
            is TaskCurveEditorForm -> JSONObject(form.productLayoutEvidence.toString())
            is TaskReviewSections -> JSONObject((field(form,"style") as TaskProductFormStyle).multilineMeasurements.toString())
            else -> JSONObject().put("status","unknown").put("reason","form_not_constructed_in_unavailable_state")
        }
        return JSONObject().put("formNaturalBudgetActualPx",naturalBudget).put("resourcesDisplayMetricsPx", JSONArray(listOf(metrics.widthPixels, metrics.heightPixels)))
            .put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("maximumWindowMetricsBoundsPx",rect(activity.windowManager.maximumWindowMetrics.bounds))
            .put("windowMetricsSemantics","Android_window_metrics_not_physical_panel_dimensions; whole_display_PNG_size_recorded_separately")
            .put("appOriginPx", JSONArray(origin.toList())).put("appSizePx", JSONArray(listOf(content.width, content.height)))
            .put("systemInsetsPx", JSONArray(listOf(bars.left, bars.top, bars.right, bars.bottom)))
            .put("imeVisible", insets.isVisible(WindowInsets.Type.ime())).put("imeApplicability", "main_capture_has_no_editor_focus; IME_states_separately_observed")
            .put("uiModeNight", config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            .put("uiModeNightMeaning", when(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) { android.content.res.Configuration.UI_MODE_NIGHT_YES -> "dark"; android.content.res.Configuration.UI_MODE_NIGHT_NO -> "light"; else -> "undefined" })
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
        private val ownedDirectory = File(context.cacheDir, "form-visual-${UUID.randomUUID()}")
        private val tracked = linkedSetOf<Activity>()
        private val destroyed = linkedSetOf<Activity>()
        var service: MobileService? = null
        @Volatile var rejectWrites = false
        val inventory = BeanInventory(object : InventoryStorage { var value: ByteArray? = null
            override fun read() = value?.clone(); override fun write(bytes: ByteArray) { check(!rejectWrites) { "M2 scoped inventory write failure" }; value = bytes.clone() } })
        val preparation = BeanPreparation(object : BeanPreparation.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { check(!rejectWrites) { "M2 scoped memory write failure" }; this.value = value } })
        val custom = CustomCurveStore(object : CustomCurveStore.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { check(!rejectWrites) { "M2 scoped memory write failure" }; this.value = value } })
        val usage = CurveUsageLedger(object : CurveUsageLedger.Storage { var value = ""
            override fun read() = value; override fun write(value: String) { check(!rejectWrites) { "M2 scoped memory write failure" }; this.value = value } })
        val journal = BrewJournal(object : BrewJournal.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { check(!rejectWrites) { "M2 scoped memory write failure" }; this.value = value } })
        val feedback = BrewFeedbackPreference(object : BrewFeedbackPreference.Storage {
            override fun read() = false; override fun write(enabled: Boolean) = !enabled })
        val logs = TraceStore(File(ownedDirectory, "traces"))
        val samples = ShotSamplesRepository(File(ownedDirectory, "samples")) { logs.record("fixture.samples_error", mapOf("type" to it.javaClass.simpleName)) }
        val curves: CurveLibrary
        val history: ShotHistory
        val document: CustomCurveDocument
        val batchId = "m2-existing-batch"
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
        val shotId = "m2-review-ended"
        val observation = BrewJournal.Observation(shotId, "draft-00000000-0000-0000-0000-000000000201",
            1792026720000L, 1792026747400L, 27400L, "ENDED", null, 3620)
        init {
            document = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000201", "经典浓缩", 93, 3600,
                CustomCurveDocument.ControlMode.PRESSURE, listOf(30,90,70,50).zip(listOf(150,250,150,50)).map { CustomCurveDocument.Stage(it.first,it.second) })
            custom.save(document)
            val factory = FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val proof = FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"), factory, context.assets.open("factory_slot_wire_v1.tsv"))
            curves = CurveLibrary(factory, proof, { custom.items() }, true)
            inventory.addBatch("m2-existing-add", BeanBatch(batchId, Bean("m2-existing-bean", "哥伦比亚 水洗"), 100000,
                LocalDate.of(2026,10,6), LocalDate.of(2026,10,4), LocalDate.of(2026,10,10)))
            history = ShotHistory(object : ShotHistory.Storage { var value = ""
                override fun read() = value; override fun write(value: String) { check(!rejectWrites) { "M2 scoped memory write failure" }; this.value = value }
            }, onObservedUse = { entry -> usage.record(entry.id,entry.curveId,entry.startedAtMs) }, onEntryChanged = { entry ->
                journal.observe(entry.journalObservation())
                if (preparation.current()?.shotId == entry.id) preparation.associatePending(journal, inventory)
            })
            journal.reconcileRecent(history.entries.map { it.journalObservation() })
            preparation.associatePending(journal,inventory)
            samples.prune(history.entries.map(ShotHistory.Entry::id).toSet())
            journal.observe(observation)
            journal.edit(shotId, BrewJournal.Notes("m2-existing-bean", "哥伦比亚 水洗", 18000, "2.3圈 / 中细磨",
                "后段流速略快，希望更平稳。", "柑橘酸质，焦糖甜感，尾段略干。", "研磨调细一点，其他条件不变。"))
            fixture = JSONObject().put("page",page).put("document",document.toJson()).put("journal",JSONObject(journal.exportJson()))
                .put("beanEntryDraft", JSONObject().put("name","哥伦比亚 水洗").put("quantity","250")
                    .put("purchased","2026-10-06").put("roasted","2026-10-04").put("opened","2026-10-10"))
                .put("existingBatchId",batchId).put("initialInventoryEventIds",JSONArray(inventory.events().map { it.eventId }))
                .put("source","legal_memory_Storage_and_scoped_owned_cache_writers")
                .put("servicePolicy","no_service_launch_or_bind; no_BLE_no_machine_commands")
                .put("timezone",java.util.TimeZone.getDefault().id)
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
        fun unavailable(enabled: Boolean) = main {
            val failure=IllegalStateException("M2 scoped store unavailable")
            when(page) {
                "review" -> write(app,"journalObservationFailure",if(enabled) failure else null)
                "curve-editor" -> write(app,"customCurvesResult\$delegate",lazyOf(if(enabled) Result.failure<CustomCurveStore>(failure) else Result.success(custom)))
                else -> write(app,"beanInventoryResult\$delegate",lazyOf(if(enabled) Result.failure<BeanInventory>(failure) else Result.success(inventory)))
            }
        }
        fun activeActivity(): Activity? = tracked.lastOrNull { !it.isDestroyed }
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
    @Suppress("UNCHECKED_CAST")
    private fun inputs(activity: Activity) = field(activity,"fields") as Map<String,EditText>
    private fun unfocus(activity: Activity) {
        main {
            val content=root(activity)
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(content.windowToken,0)
            content.isFocusableInTouchMode=true; content.requestFocus()
        }
        frames.committedFrame(root(activity))
    }
    private fun baselines(activity: Activity): JSONArray = JSONArray(nodes(root(activity)).filterIsInstance<TextView>()
        .filter { (it.tag as? String)?.endsWith("/unit")==true && it.isShown }.map { unit ->
            val id=(unit.tag as String).removeSuffix("/unit")
            val number=nodes(root(activity)).filterIsInstance<TextView>().single { it.tag==id || it.tag=="$id/number" }
            val a=IntArray(2); val b=IntArray(2); number.getLocationOnScreen(a); unit.getLocationOnScreen(b)
            val delta=(a[1]+number.baseline)-(b[1]+unit.baseline)
            JSONObject().put("numberId",id).put("unitId",unit.tag).put("numberBaselineScreenPx",a[1]+number.baseline)
                .put("unitBaselineScreenPx",b[1]+unit.baseline).put("deltaPx",delta)
                .put("status",if(number.baseline>=0 && unit.baseline>=0 && delta==0) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
        })
    private fun captureState(activity: Activity, directory: File, label: String) {
        unfocus(activity)
        val stateDirectory=File(directory,"states/$label").apply { check(isDirectory || mkdirs()) }
        frames.screenshot(activity,root(activity),File(stateDirectory,"window.png")) { geometry(activity) }
        main {
            File(stateDirectory,"geometry.json").writeText(geometry(activity).toString(2))
            TaskTypographyEvidence.capture(activity,stateDirectory)
        }
    }
    fun run(arguments: Bundle): Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("formPage")); check(page in setOf("bean-entry","review","curve-editor"))
        val source=requireNotNull(arguments.getString("sourceSha")); check(source.matches(Regex("[0-9a-f]{40}")))
        val dark=arguments.getString("sampleTheme")=="dark"
        val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat()
        val profile="tablet-user-landscape-${if(dark) "dark" else "light"}-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"form-visual-migration/$source/$profile/$page").apply { check(isDirectory || mkdirs()) }
        val manifest=JSONObject().put("sourceSha",source).put("sourceDirty",arguments.getString("sourceDirty"))
            .put("headSha",arguments.getString("headSha")).put("api",Build.VERSION.SDK_INT).put("apkSha256",arguments.getString("apkSha")).put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("profile",profile).put("page",page).put("realProductActivity",true).put("specimenOnly",false)
            .put("visualAcceptance","ASTRA_PENDING").put("pid",android.os.Process.myPid()).put("noBle",true)
        var failure: Throwable?=null
        var scope: Scope?=null
        var current: Activity?=null
        try {
            val activeScope=Scope(page,directory); scope=activeScope; activeScope.install(dark)
            File(directory,"fixture.json").writeText(activeScope.fixture.toString(2))
            val type=when(page) { "bean-entry" -> BeanEntryActivity::class.java; "review" -> BrewReviewActivity::class.java; else -> CurveEditorActivity::class.java }
            val activity=test.startActivitySync(Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).apply {
                if(page=="review") putExtra(BrewReviewActivity.SHOT_ID,activeScope.shotId)
                if(page=="curve-editor") putExtra(CurveEditorActivity.CURVE_ID,activeScope.document.id)
            }); current=activity
            await("real form ready") { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
            check(services().isEmpty()) { "M2 forms must not start a service" }
            check(activity.resources.configuration.fontScale==scale)
            check(activity.resources.configuration.densityDpi==360 && activity.resources.configuration.locales[0].language=="zh")
            // Empty new-bean initial state is checked BEFORE draft population, any focus or reveal.
            if(activity is BeanEntryActivity) {
                ChineseFormWindowChecks.run(test,activity,File(directory,"empty-initial").apply { mkdirs() },"empty-BeanEntryActivity", listOf("name","quantity","opened")) {
                    frames.committedFrame(root(activity))
                }
                main {
                    val values=mapOf("name" to "哥伦比亚 水洗","quantity" to "250","purchased" to "2026-10-06","roasted" to "2026-10-04","opened" to "2026-10-10")
                    inputs(activity).forEach { (key,input) -> input.setText(values.getValue(key)) }
                }
            }
            unfocus(activity)
            main { nodes(root(activity)).filterIsInstance<ScrollView>().forEach { it.scrollTo(0,0) } }
            frames.committedFrame(root(activity))
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
            check(imageBounds.outWidth==2560 && imageBounds.outHeight==1600)
            lateinit var baseline: JSONArray
            lateinit var ids: List<String>
            main {
                check(nodes(root(activity)).filterIsInstance<EditText>().none { it.hasFocus() }) { "Main form PNG has editor focus" }
                TaskTypographyEvidence.capture(activity,directory)
                baseline=baselines(activity)
                File(directory,"baseline-alignment.json").writeText(baseline.toString(2))
                File(directory,"geometry.json").writeText(geometry(activity).toString(2))
                File(directory,"canvas.json").writeText(JSONArray(nodes(root(activity)).filterIsInstance<CurveStageView>().map { chart ->
                    JSONObject().put("semanticId",chart.tag).put("targets",JSONArray(chart.targets)).put("actualDrawOperations",chart.productDrawEvidence)
                }).toString(2))
                val actualWindow=geometry(activity)
                manifest.put("window",actualWindow).put("initialFrameSync",initial.proof)
                check(actualWindow.getDouble("fontScale").toFloat()==scale) { "Actual M2 fontScale does not match requested profile" }
                check(actualWindow.getString("uiModeNightMeaning")==if(dark) "dark" else "light") { "Actual M2 uiMode does not match requested profile" }
                check(!actualWindow.getBoolean("imeVisible")) { "Main M2 PNG has actual IME visible" }
                check(initial.proof.getInt("protocolVersion")==2 && initial.proof.getBoolean("accepted"))
                ids=inputs(activity).values.filter { it.isShown }.map { it.tag as String } + listOf("product/form/$page/save","product/form/$page/cancel")
                inputs(activity).values.filter { it.isShown }.forEach { input ->
                    check(input.height>=HoyiUi.dp(activity,52)) { "M2 short/natural field under 52dp: ${input.tag}" }
                }
            }
            val initialRequired=if(scale<=1.05f && page!="review") ids.toSet() else ids.takeLast(2).toSet()
            val reached=captureReachable(activity,directory,ids,initialRequired)
            if(activity !is BeanEntryActivity) ChineseFormWindowChecks.run(test,activity,directory,"${activity.javaClass.simpleName}",
                if(activity is CurveEditorActivity) listOf("name","temperature","cup","target-3","water-3") else listOf("dose")) {
                frames.committedFrame(root(activity))
            }
            AdvancedBusinessUiChecks(test).verifyM2FormContract(activity,page,directory,{ activeScope.rejectWrites=it },{ activeScope.unavailable(it) }) { owner,label ->
                current=owner; captureState(owner,directory,label)
            }
            check(services().isEmpty()) { "M2 form business unexpectedly started a service" }
            val issues=JSONArray()
            (0 until baseline.length()).forEach { if(baseline.getJSONObject(it).getString("status")=="FAIL") issues.put("baseline:$it") }
            (0 until reached.length()).forEach { if(reached.getJSONObject(it).getString("status")=="FAIL") issues.put("reachability:$it") }
            manifest.put("mechanicalIssues",issues).put("layoutStatus",if(issues.length()==0) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            check(issues.length()==0) { "M2 actual layout failed after preserving original images: $issues" }
        } catch(error: Throwable) {
            failure=error; manifest.put("failure",error.stackTraceToString()).put("layoutStatus","FAIL")
            // Never clean up a failed state before preserving its actual context. Caret/IME sync is NOT presumed.
            (scope?.activeActivity() ?: current)?.takeUnless { it.isDestroyed }?.let { activity -> try {
                lateinit var before: JSONObject; main { before=geometry(activity) }
                val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
                try { File(directory,"failure-context.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } }
                finally { bitmap.recycle() }
                lateinit var after: JSONObject; main { after=geometry(activity) }
                File(directory,"failure-context-geometry.json").writeText(JSONObject().put("before",before).put("after",after)
                    .put("geometryUnchanged",before.toString()==after.toString()).put("sameCompositorFrameProven",false).toString(2))
            } catch(captureFailure: Throwable) { error.addSuppressed(captureFailure) } }
        } finally {
            try { scope?.cleanup() } catch(error: Throwable) { if(failure==null) failure=error else failure!!.addSuppressed(error) }
            manifest.put("fileSha256",JSONObject(directory.walkTopDown().filter { it.isFile && it!=File(directory,"manifest.json") }
                .associate { it.relativeTo(directory).path to sha(it) }))
            manifest.put("isolationStatus",if(failure==null) "PASS" else "SEE_fixture-isolation.json")
            File(directory,"manifest.json").writeText(manifest.toString(2))
        }
        failure?.let { throw it }
        return 1
    }
}

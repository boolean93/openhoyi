package io.openhoyi.mobile

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
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
import android.widget.ListView
import android.widget.Button
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

/** M3 real library/import/preview evidence. Only isolated Mock domain stores; no machine commands. */
internal class CurveVisualMigrationChecks(private val test: Instrumentation) {
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
            check(SystemClock.elapsedRealtime() < deadline) { "M3 timeout: $description" }
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
        private val ownedDirectory = File(context.cacheDir, "curve-visual-${UUID.randomUUID()}")
        private val tracked = linkedSetOf<Activity>()
        private val destroyed = linkedSetOf<Activity>()
        var service: MobileService? = null
        val inventory = BeanInventory(object : InventoryStorage { var value: ByteArray? = null
            override fun read() = value?.clone(); override fun write(bytes: ByteArray) { value = bytes.clone() } })
        val preparation = BeanPreparation(object : BeanPreparation.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { this.value = value } })
        var rejectCustomWrites = false
        var customWriteAttempts = 0
            private set
        val custom = CustomCurveStore(object : CustomCurveStore.Storage { var value: String? = null
            override fun read() = value
            override fun write(value: String) {
                customWriteAttempts++
                check(!rejectCustomWrites) { "M3 scoped memory custom write failure before publish" }
                this.value = value
            }
        })
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
        lateinit var document: CustomCurveDocument
            private set
        lateinit var baseLibraryIds: List<String>
            private set
        lateinit var incoming: CustomCurveDocument
            private set
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
        private var clipboardCaptured = false
        private var clipboardRestored = false
        private var originalClip: ClipData? = null
        private var originalClipFingerprint: String? = null
        private var originalClipPresent = false
        private var providerSession: String? = null
        private val importThreads = linkedSetOf<Thread>()
        private fun liveImportThreads() = Thread.getAllStackTraces().keys.filter { it.isAlive && it.name == "curve-document-import" }
        private fun clipFingerprint(clip: ClipData?): String? = clip?.let { value ->
            // Inert payload only: never resolve/coerce unknown URI/Intent or persist clipboard content.
            JSONObject().put("label",value.description.label?.toString())
                .put("mimeTypes",JSONArray((0 until value.description.mimeTypeCount).map(value.description::getMimeType)))
                .put("items",JSONArray((0 until value.itemCount).map { index -> val item=value.getItemAt(index)
                    JSONObject().put("text",item.text?.toString()).put("htmlText",item.htmlText)
                        .put("uri",item.uri?.toString()).put("intent",item.intent?.toUri(0)) })).toString()
        }
        fun pasteFixture(owner: Activity, value: String) {
            main {
                check(owner is CurveImportActivity && owner.hasWindowFocus())
                val manager=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if(!clipboardCaptured) {
                    originalClipPresent=manager.hasPrimaryClip(); originalClip=manager.primaryClip
                    check(originalClipPresent==(originalClip!=null)) { "Foreground clipboard presence/read inconsistent" }
                    originalClipFingerprint=clipFingerprint(originalClip); clipboardCaptured=true
                }
                manager.setPrimaryClip(ClipData.newPlainText("M3 isolated explicit paste fixture",value))
            }
        }
        fun observeImportThreads() { importThreads.addAll(liveImportThreads()) }
        fun drainImportThreads() {
            observeImportThreads()
            val deadline=SystemClock.elapsedRealtime()+10000
            importThreads.filter { it.isAlive }.forEach { thread ->
                val remaining=deadline-SystemClock.elapsedRealtime()
                check(remaining>0) { "M3 import thread drain budget exhausted" }
                thread.join(remaining)
                check(!thread.isAlive && SystemClock.elapsedRealtime()<=deadline) { "M3 owned import thread did not exit" }
            }
            // Thread may have finished before it was observable; main callback outcome and this barrier prove no pending callback remains.
            main { }
            check(liveImportThreads().isEmpty()) { "M3 new import thread still alive" }
        }
        fun restoreClipboard(owner: Activity?) {
            if(!clipboardCaptured || clipboardRestored) return
            drainImportThreads()
            main {
                val manager=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if(originalClipPresent) manager.setPrimaryClip(requireNotNull(originalClip)) else manager.clearPrimaryClip()
                val foreground=owner is CurveImportActivity && owner.hasWindowFocus() && !owner.isDestroyed
                if(foreground) {
                    check(manager.hasPrimaryClip()==originalClipPresent && clipFingerprint(manager.primaryClip)==originalClipFingerprint)
                    clipboardRestored=true
                }
                File(directory,"clipboard-isolation.json").writeText(JSONObject()
                    .put("explicitPasteFixtureOnly",true).put("originalPayloadNotPersisted",true)
                    .put("restoreCallCompleted",true).put("foregroundLogicalReadback",if(foreground) "PASS" else "UNKNOWN_background_clipboard_policy")
                    .put("timestampOrParcelByteIdentity","not_claimed").toString(2))
                check(foreground) { "M3 clipboard restore readback UNKNOWN without foreground Import; isolation not passed" }
            }
        }
        fun createProvider(document: CustomCurveDocument): String {
            check(providerSession==null)
            check(context.checkSelfPermission(CurveFixtureProvider.PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
            check(context.packageManager.checkSignatures(context.packageName,test.context.packageName)==android.content.pm.PackageManager.SIGNATURE_MATCH)
            val session=UUID.randomUUID().toString(); providerSession=session
            provider("create",Bundle().apply { putString("document",document.encode()) })
            return session
        }
        fun provider(method: String, data: Bundle?=null): Bundle = requireNotNull(context.contentResolver.call(
            Uri.parse("content://${CurveFixtureProvider.AUTHORITY}"),method,requireNotNull(providerSession),data))
        fun prepare() {
            // Scope is assigned to the outer finally BEFORE allocating either owned IO resource.
            check(!::logs.isInitialized && !::samples.isInitialized)
            check(liveImportThreads().isEmpty()) { "Original import thread exists before scoped installation" }
            logs=TraceStore(File(ownedDirectory,"traces"))
            samples=ShotSamplesRepository(File(ownedDirectory,"samples")) { logs.record("fixture.samples_error",mapOf("type" to it.javaClass.simpleName)) }
            document=CustomCurveDocument("draft-00000000-0000-0000-0000-000000000301", "经典浓缩", 93, 3600,
                CustomCurveDocument.ControlMode.PRESSURE, listOf(20,90,80,60).zip(listOf(150,250,150,50)).map { CustomCurveDocument.Stage(it.first,it.second) })
            custom.save(document)
            incoming=document.copy(name="经典浓缩（新版）",stages=listOf(30,90,70,50).zip(listOf(150,250,150,50)).map { CustomCurveDocument.Stage(it.first,it.second) })
            val factory=FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val proof=FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"),factory,context.assets.open("factory_slot_wire_v1.tsv"))
            baseLibraryIds=CurveLibrary(factory,proof).items.map { it.id }
            check(baseLibraryIds.size==103) { "M3 must preserve the original complete 103-item fixture" }
            curves=CurveLibrary(factory,proof,{ custom.items() },true)
            check(curves.items.map { it.id }.containsAll(baseLibraryIds) && curves.items.size==baseLibraryIds.size+1)
            var nextShot=0
            val fixedNow=1792026750000L
            history=ShotHistory(object : ShotHistory.Storage { var value=""
                override fun read()=value; override fun write(value: String) { this.value=value }
            },now={fixedNow},newId={ "m3-scoped-observed-${++nextShot}" },onObservedUse={ entry ->
                usage.record(entry.id,entry.curveId,entry.startedAtMs)
            },onEntryChanged={ entry ->
                journal.observe(entry.journalObservation())
                if(preparation.current()?.shotId==entry.id) preparation.associatePending(journal,inventory)
            })
            journal.reconcileRecent(history.entries.map { it.journalObservation() })
            preparation.associatePending(journal,inventory)
            samples.prune(history.entries.map(ShotHistory.Entry::id).toSet())
            (1..3).forEach { index ->
                val at=fixedNow-(4-index)*30000L
                history.begin(document.id,at)
                history.transition(ExtractionState.RUNNING,null,null,at+1000)
                history.observeRunning()
                history.transition(ExtractionState.ENDED_OBSERVED,null,3600,at+27400)
            }
            check(usage.stats(document.id).count==3L && history.entries.size==3 && journal.entries().size==3)
            fixture=JSONObject().put("page",page).put("document",document.toJson())
                .put("incomingPreviewDocumentNotSaved",incoming.toJson()).put("baseLibraryIds",JSONArray(baseLibraryIds))
                .put("actualLibraryCount",curves.items.size).put("scopedHistory",JSONArray(history.entries.map { entry ->
                    JSONObject().put("id",entry.id).put("curveId",entry.curveId).put("status",entry.status).put("observedRunning",entry.observedRunning)
                })).put("usageCount",usage.stats(document.id).count)
                .put("usageSource","three_synthetic_legal_ShotHistory_domain_observations_not_hardware_telemetry")
                .put("source","legal_memory_Storage_and_scoped_owned_cache_writers")
                .put("servicePolicy","no_service_launch_bind_or_machine_commands")
                .put("clipboardAndPresetPolicy","clipboard_only_explicit_import_business_with_foreground_restore; presets_not_touched")
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
                check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).edit().putString("selected",document.id).putString("sort",CurveSortMode.RECENT.name).commit())
            }
        }
        fun activeActivity(): Activity? = tracked.lastOrNull { !it.isDestroyed }
        fun cleanup() {
            val proof = JSONObject().put("scopedOwnedDirectory",ownedDirectory.absolutePath)
            try {
                if (installed) {
                    drainImportThreads()
                    restoreClipboard(activeActivity())
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
                    drainImportThreads()
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) { "M3 fixture dialog/window remains after Activity destruction" }
                    proof.put("ownedImportThreadsObserved",importThreads.size).put("allImportThreadsExitedAndMainCallbackBarrier",true)
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("serviceRemovedAndOnDestroyPostconditions",true).put("mainQueueBarrierAfterDestroy",true)
                }
                proof.put("ownedSampleRepositoryCreated",::samples.isInitialized).put("ownedTraceCreated",::logs.isInitialized)
                if (::samples.isInitialized) {
                    val writer=field(samples,"writer") as ExecutorService
                    writer.shutdown(); check(writer.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped sample writer did not terminate" }
                }
                if (::logs.isInitialized) { logs.close(); check(logs.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped trace writer did not terminate" } }
                proof.put("scopedWritersTerminated",true)
                providerSession?.let { provider("cleanup"); providerSession=null; proof.put("ownedProviderSessionRemoved",true) }
                proof.put("clipboardIsolation",if(!clipboardCaptured) "NOT_TOUCHED" else if(clipboardRestored) "PASS_FOREGROUND_LOGICAL_RESTORE" else "UNKNOWN")
                if (installed) main {
                    saved.forEach { (name,lazy) -> write(app,name+"\$delegate",lazy) }
                    write(app,"languagePreferences",oldLanguage); write(app,"journalObservationFailure",oldFailure)
                    savedPrefs.forEach { (name,values) ->
                        val preferences = context.getSharedPreferences(name,Context.MODE_PRIVATE)
                        val keys = if (name == "appearance") listOf("dark") else listOf("selected","sort")
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
                        .put("whitePrefPhysicalBytesMayDiffer","only appearance.dark/curves.selected/curves.sort typed restoration; no file deletion or byte rollback")
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
            is CurveActivity -> JSONObject(activity.productLayoutEvidence.toString())
            is CurveImportPreviewActivity -> JSONObject(activity.productLayoutEvidence.toString())
            else -> JSONObject().put("status","unknown").put("reason","Activity_has_no_existing_natural_budget_interface; actual_View_geometry_recorded")
        }
        val restoreEvidence=when(activity) {
            is CurveImportActivity -> JSONObject(activity.productRestoreEvidence.toString())
            else -> JSONObject().put("status","unknown").put("reason","Activity_has_no_productRestoreEvidence_interface")
        }
        val actualRestoreState=JSONObject().put("imeVisible",insets.isVisible(WindowInsets.Type.ime()))
            .put("hasWindowFocus",activity.hasWindowFocus()).put("focusedViewTag",activity.currentFocus?.tag ?: JSONObject.NULL)
            .put("focusedViewClass",activity.currentFocus?.javaClass?.name ?: JSONObject.NULL)
            .put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("scrolls",JSONArray(nodes(content).filter { it is ScrollView || it is EditText }.map { view ->
                JSONObject().put("semanticId",view.tag ?: JSONObject.NULL).put("x",view.scrollX).put("y",view.scrollY)
            }))
        return JSONObject().put("naturalBudgetActualPx",budget)
            .put("productRestoreEvidence",restoreEvidence)
            .put("productRestoreEvidenceSemantics","deep_copy_of_page_self_report; not_OS_confirmation; actualRestoreState_is_separate_current_View_and_WindowInsets_observation")
            .put("actualRestoreState",actualRestoreState)
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
    private fun baselines(activity: Activity): JSONArray = JSONArray((0..1).map { index ->
        val prefix=if(activity is CurveActivity) "product/curve-library/summary/$index" else "product/import-preview/metric-$index"
        val number=requireNotNull(root(activity).findViewWithTag<TextView>("$prefix/number"))
        val unit=requireNotNull(root(activity).findViewWithTag<TextView>("$prefix/unit"))
        val a=IntArray(2); val b=IntArray(2); number.getLocationOnScreen(a); unit.getLocationOnScreen(b)
        val delta=a[1]+number.baseline-b[1]-unit.baseline
        JSONObject().put("numberId",number.tag).put("unitId",unit.tag).put("deltaPx",delta)
            .put("numberBaselineScreenPx",a[1]+number.baseline).put("unitBaselineScreenPx",b[1]+unit.baseline)
            .put("status",if(number.isShown && unit.isShown && number.baseline>=0 && unit.baseline>=0 && delta==0)
                "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
    })
    private fun list(activity: Activity)=field(activity,"curveList") as ListView
    private fun listState(activity: Activity): JSONObject {
        val view=list(activity)
        check(view.childCount>0 && view.count>0)
        return JSONObject().put("position",view.firstVisiblePosition).put("offsetPx",view.getChildAt(0).top)
            .put("firstItemId",view.getItemAtPosition(view.firstVisiblePosition).toString()).put("count",view.count)
            .put("query",(field(activity,"search") as EditText).text.toString()).put("category",field(activity,"category").toString())
            .put("sort",field(activity,"sortMode").toString()).put("selectedId",(field(activity,"selected") as? CurveLibraryItem)?.id ?: JSONObject.NULL)
    }
    private fun stableList(activity: Activity, directory: File, phase: String): JSONObject {
        val samples=JSONArray(); val deadline=SystemClock.elapsedRealtime()+5000
        var previous: String?=null; var consecutive=0
        try {
            while(SystemClock.elapsedRealtime()<deadline) {
                frames.committedFrame(root(activity))
                lateinit var value: JSONObject; var pending=true
                main { value=listState(activity); pending=root(activity).isLayoutRequested || list(activity).isLayoutRequested }
                val signature=value.toString()
                consecutive=if(!pending && signature==previous) consecutive+1 else if(!pending) 1 else 0
                val within=SystemClock.elapsedRealtime()<=deadline
                samples.put(JSONObject().put("actualFrameIndex",samples.length()+1).put("state",value)
                    .put("pendingLayout",pending).put("consecutiveUnchanged",consecutive).put("withinBudget",within))
                previous=signature
                if(consecutive>=3 && within) return value
            }
            error("M3 $phase list did not stabilize on three actual frames within 5000ms")
        } finally { File(directory,"$phase-list-stability.json").writeText(JSONObject().put("samples",samples)
            .put("policy","actual_committed_frames; stability_not_waiting_for_expected_anchor_match").toString(2)) }
    }
    private fun gesture(view: View) {
        val visible=Rect()
        main { check(view.isShown && view.getGlobalVisibleRect(visible) && visible.width()>32 && visible.height()>64) }
        val x=visible.exactCenterX(); val start=visible.top+visible.height()*.82f; val end=visible.top+visible.height()*.22f
        val down=SystemClock.uptimeMillis()
        fun event(action: Int,y: Float) {
            val value=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).apply { source=android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { check(test.uiAutomation.injectInputEvent(value,true)) { "M3 real local list gesture rejected" } } finally { value.recycle() }
        }
        event(android.view.MotionEvent.ACTION_DOWN,start)
        for(step in 1..16) { SystemClock.sleep(25); event(android.view.MotionEvent.ACTION_MOVE,start+(end-start)*step/16) }
        // Real stationary MOVE events remove fling velocity before UP; delays are gesture timing, not acceptance waits.
        repeat(4) { SystemClock.sleep(50); event(android.view.MotionEvent.ACTION_MOVE,end) }
        event(android.view.MotionEvent.ACTION_UP,end)
    }
    private fun invariant(scope: Scope): String = JSONObject().put("inventoryEvents",JSONArray(scope.inventory.events().map { it.toString() }))
        .put("preparation",scope.preparation.current()?.toString() ?: JSONObject.NULL).put("journal",JSONObject(scope.journal.exportJson()))
        .put("history",JSONArray(scope.history.entries.map { it.toString() }))
        .put("drafts",JSONArray(scope.custom.list().map { it.toJson() }))
        .put("usage",JSONArray(scope.curves.items.map { JSONObject().put("id",it.id).put("stats",scope.usage.stats(it.id).toString()) })).toString()
    private fun captureState(activity: Activity, directory: File, phase: String) {
        val folder=File(directory,"states/$phase").apply { check(isDirectory || mkdirs()) }
        frames.screenshot(activity,root(activity),File(folder,"window.png")) { geometry(activity) }
        main { File(folder,"geometry.json").writeText(geometry(activity).toString(2)); TaskTypographyEvidence.capture(activity,folder) }
    }
    private fun libraryBusiness(initial: Activity, scope: Scope, directory: File) {
        var activity=initial
        val frozen=invariant(scope)
        val preferences=context.getSharedPreferences("curves",Context.MODE_PRIVATE)
        val selectedPreference=preferences.getString("selected",null)
        val category=CurveCategoryFilter.entries.filter { it!=CurveCategoryFilter.ALL }
            .maxBy { CurveSearch.filter(scope.curves.items,it,"factory-v3").size }
        main {
            (field(activity,"search") as EditText).setText("factory-v3")
            nodes(root(activity)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString()==category.label(activity) }.let { check(it.performClick()) }
            nodes(root(activity)).filterIsInstance<Spinner>().single().setSelection(CurveSortMode.MOST_USED.ordinal)
        }
        stableList(activity,directory,"filtered")
        main { check(list(activity).canScrollVertically(1)) { "M3 fixture has no genuine continuous-list range" } }
        gesture(list(activity))
        val before=stableList(activity,directory,"before-selection")
        check(before.getInt("position")>0) { "M3 real gesture did not establish non-first-page anchor" }
        main {
            val list=list(activity)
            val child=(0 until list.childCount).map { list.getChildAt(it) }.first { child ->
                val visible=Rect(); child.getGlobalVisibleRect(visible) && visible.width()==child.width && visible.height()==child.height
            }
            val position=list.firstVisiblePosition+list.indexOfChild(child)
            check(list.performItemClick(child,position,list.adapter.getItemId(position)))
        }
        val after=stableList(activity,directory,"after-selection")
        listOf("position","offsetPx","firstItemId","count").forEach { check(before.get(it)==after.get(it)) { "M3 preview moved list $it" } }
        check(preferences.getString("selected",null)==selectedPreference && invariant(scope)==frozen)
        captureState(activity,directory,"non-first-selection")
        main { check((field(activity,"parameterShortcut") as Button).performClick()) }
        await("real parameter dialog") { android.view.inspector.WindowInspector.getGlobalWindowViews().any { it!==activity.window.decorView && it.hasWindowFocus() } }
        main {
            val dialog=android.view.inspector.WindowInspector.getGlobalWindowViews().single { it!==activity.window.decorView && it.hasWindowFocus() }
            check(dialog.findViewById<View>(android.R.id.button1).performClick())
        }
        await("parameter dialog closed") { activity.hasWindowFocus() }
        val returned=stableList(activity,directory,"after-detail-return")
        check(returned.toString()==after.toString()) { "M3 details return changed list/selection" }
        val monitor=test.addMonitor(CurveActivity::class.java.name,null,false)
        try {
            main { activity.recreate() }
            activity=test.waitForMonitorWithTimeout(monitor,10000) ?: error("M3 Curve Activity recreation missing")
        } finally { test.removeMonitor(monitor) }
        await("recreated real Curve ready") { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
        val recreated=stableList(activity,directory,"after-recreate")
        File(directory,"continuous-list-business.json").writeText(JSONObject().put("beforeSelection",before).put("afterSelection",after)
            .put("afterDetailsReturn",returned).put("afterRecreate",recreated).put("originalBaseCount",scope.baseLibraryIds.size)
            .put("actualLibraryCountIncludingRealDraft",scope.curves.items.size).put("completed",false).toString(2))
        check(recreated.toString()==after.toString()) { "M3 recreate changed query/category/sort/selected or exact non-first anchor" }
        check(invariant(scope)==frozen && preferences.getString("selected",null)==selectedPreference)
        captureState(activity,directory,"list-restored")
        lateinit var chosen: CurveLibraryItem
        val beforeUse=preferences.all.toMap()
        main {
            chosen=field(activity,"selected") as CurveLibraryItem
            val use=field(activity,"select") as Button
            check(use.isEnabled==scope.curves.canStart(chosen) && use.isEnabled)
            check(whole(observation(use)) && use.height>=HoyiUi.dp(activity,48))
            check(use.performClick())
        }
        await("real use-for-cup callback finished") { activity.isDestroyed }
        check(preferences.all==beforeUse.toMutableMap().apply { put("selected",chosen.id) }) { "M3 use-for-cup changed unrelated preferences" }
        check(invariant(scope)==frozen && services().isEmpty()) { "M3 library action changed domain data or started service" }
        File(directory,"continuous-list-business.json").writeText(JSONObject().put("beforeSelection",before).put("afterSelection",after)
            .put("afterDetailsReturn",returned).put("afterRecreate",recreated).put("originalBaseCount",scope.baseLibraryIds.size)
            .put("actualLibraryCountIncludingRealDraft",scope.curves.items.size).put("onlySelectedPreferenceChanged",true)
            .put("usedCurveId",chosen.id).put("noMachineOrServiceCommands",true).put("modelsAndUsageUnchanged",true)
            .put("presetSlotsAndClipboardNotTouched",true).put("completed",true).toString(2))
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
            error("M3 $phase geometry did not stabilize on three actual frames within 5000ms")
        } finally { File(directory,"$phase-window-stability.json").writeText(JSONObject().put("samples",samples)
            .put("acceptancePolicy","geometry_stability_only; pending_only_isShown_tree; hidden_nodes_do_not_block; not_waiting_for_visibility_or_business_assertion_success").toString(2)) }
    }
    private fun start(type: Class<out Activity>, document: CustomCurveDocument?=null): Activity {
        val intent=Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        document?.let { intent.putExtra(CurveImportPreviewActivity.DOCUMENT,it.encode()) }
        return test.startActivitySync(intent).also { activity -> await("${type.simpleName} ready") {
            activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested } }
    }
    private fun close(activity: Activity) {
        main { activity.finish() }
        await("${activity.javaClass.simpleName} destroyed") { activity.isDestroyed }
    }
    private fun action(activity: Activity, id: Int): TextView = nodes(root(activity)).filterIsInstance<TextView>()
        .single { it.isClickable && it.text.toString()==activity.getString(id) }
    private fun click(activity: Activity, id: Int) { main {
        val view=action(activity,id); check(view.isEnabled && view.isShown)
        check(view.performClick())
    }; test.waitForIdleSync() }
    private fun unfocus(activity: Activity) {
        main {
            root(activity).isFocusableInTouchMode=true; check(root(activity).requestFocus())
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(root(activity).windowToken,0)
        }
        await("unfocused real window without IME") { !requireNotNull(root(activity).rootWindowInsets).isVisible(WindowInsets.Type.ime()) }
    }
    private fun input(activity: Activity)=field(activity,"text") as EditText
    private fun rawState(activity: Activity): JSONObject {
        val view=input(activity)
        return JSONObject().put("text",view.text.toString()).put("selectionStart",view.selectionStart).put("selectionEnd",view.selectionEnd)
            .put("focused",view.hasFocus()).put("scrollX",view.scrollX).put("scrollY",view.scrollY)
    }
    private fun nonCustomInvariant(scope: Scope)=JSONObject(invariant(scope)).apply { remove("drafts") }.toString()
    private fun dismissDialog(owner: Activity) {
        await("real local dialog") { android.view.inspector.WindowInspector.getGlobalWindowViews().any { it!==owner.window.decorView && it.hasWindowFocus() } }
        main {
            val window=android.view.inspector.WindowInspector.getGlobalWindowViews().single { it!==owner.window.decorView && it.hasWindowFocus() }
            check(requireNotNull(window.findViewById<View>(android.R.id.button1)).performClick())
        }
        await("local dialog dismissed") { owner.hasWindowFocus() }
    }
    private fun importBusiness(initial: Activity, scope: Scope, directory: File) {
        var activity=initial; val frozen=invariant(scope); val cases=JSONArray()
        fun record(name: String) {
            cases.put(JSONObject().put("case",name).put("modelsUnchanged",invariant(scope)==frozen))
            File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases).put("completed",false).toString(2))
        }
        fun errorText()=(field(activity,"error") as TextView).text.toString()
        main { check(input(activity).text.isEmpty() && !action(activity,R.string.profiles_import_preview).isEnabled) }
        // Raw draft is intentionally invalid and multiline: test the original restoration contract without decoding it.
        val raw="  {未完成的原文\n  保留空格与换行}\n"+(1..90).joinToString("\n") { "  未完成行 $it  " }
        main { input(activity).setText(raw); check(input(activity).requestFocus())
            input(activity).setSelection(raw.length-15,raw.length-7)
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input(activity),0) }
        await("Import actual IME") { requireNotNull(root(activity).rootWindowInsets).isVisible(WindowInsets.Type.ime()) }
        main { val view=input(activity); val layout=requireNotNull(view.layout)
            view.scrollTo(0,(layout.height-view.height+view.compoundPaddingTop+view.compoundPaddingBottom).coerceAtLeast(0)) }
        stableWindow(activity,directory,"raw-before-recreate")
        lateinit var before: JSONObject
        main {
            before=rawState(activity)
            // Preserve the actual pre-assertion state in this SAME main turn, even if its establishment failed.
            File(directory,"raw-ime-geometry.json").writeText(JSONObject().put("geometry",geometry(activity))
                .put("state",before).put("phase","before_focus_nonzero_scroll_and_footer_assertions")
                .put("sameCompositorFrameProven",false).toString(2))
            check(before.getBoolean("focused") && before.getInt("scrollY")>0) {
                "M3 raw draft state not established: focused=${before.getBoolean("focused")} scrollY=${before.getInt("scrollY")} " +
                    "inputHeightPx=${input(activity).height}; original focus/nonzero-scroll gate is unchanged"
            }
            listOf(R.string.beans_cancel,R.string.profiles_import_preview).forEach { id ->
                val button=action(activity,id); check(whole(observation(button)) && button.height>=HoyiUi.dp(activity,48)) }
        }
        diagnostic(activity,File(directory,"raw-ime"))
        val monitor=test.addMonitor(CurveImportActivity::class.java.name,null,false)
        try { main { activity.recreate() }; activity=test.waitForMonitorWithTimeout(monitor,10000) ?: error("M3 Import recreate missing") }
        finally { test.removeMonitor(monitor) }
        await("Import restored ready") { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
        stableWindow(activity,directory,"raw-after-recreate")
        main { val after=rawState(activity)
            File(directory,"raw-restoration.json").writeText(JSONObject().put("before",before).put("after",after)
                .put("afterGeometryBeforeAssertion",geometry(activity)).toString(2))
            check(after.toString()==before.toString()) { "M3 raw text/focus/selection/scroll changed before screenshot side effects" } }
        check(invariant(scope)==frozen); record("raw-exact-text-selection-focus-nonzero-scroll-recreate")
        unfocus(activity); captureState(activity,directory,"raw-restored")
        for((index,invalid) in listOf("{bad",scope.document.encode().replace("\"schema\":1","\"schema\":99"),
            scope.document.copy(temperatureC=200).toJson().toString()).withIndex()) {
            check(runCatching { CustomCurveDocument.decode(invalid) }.isFailure)
            main { input(activity).setText(invalid); (field(activity,"error") as TextView).text=""
                check(action(activity,R.string.profiles_import_preview).isEnabled) }
            click(activity,R.string.profiles_import_preview)
            main { check(activity.hasWindowFocus() && !activity.isFinishing && input(activity).text.toString()==invalid &&
                errorText()==activity.getString(R.string.profiles_import_invalid)) }
            check(invariant(scope)==frozen); captureState(activity,directory,"invalid-$index")
            main { check(whole(observation(field(activity,"error") as TextView))) { "M3 inline invalid-input error clipped" } }
            record("invalid-$index-no-write")
        }
        val pasted="  \n${scope.document.encode()}\n  "
        scope.pasteFixture(activity,pasted); click(activity,R.string.profiles_paste)
        main { check(input(activity).text.toString()==pasted && errorText().isEmpty()) }
        check(invariant(scope)==frozen)
        val previewMonitor=test.addMonitor(CurveImportPreviewActivity::class.java.name,null,false)
        val preview: Activity
        try { click(activity,R.string.profiles_import_preview)
            preview=test.waitForMonitorWithTimeout(previewMonitor,10000) ?: error("M3 pasted valid document did not preview") }
        finally { test.removeMonitor(previewMonitor) }
        await("pasted real preview ready") { preview.hasWindowFocus() && root(preview).isLaidOut }
        check(invariant(scope)==frozen); close(preview)
        await("raw return after preview") { activity.hasWindowFocus() }
        main { check(input(activity).text.toString()==pasted) }; record("explicit-paste-preview-return-original-whitespace-no-write")
        val session=scope.createProvider(scope.document)
        fun picker(code: Int, uri: Uri?) {
            var observed: Intent?=null; var count=0
            val intercept=object: Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if(intent.action!=Intent.ACTION_OPEN_DOCUMENT) {
                        check(intent.component?.className==CurveImportPreviewActivity::class.java.name) { "M3 unexpected external launch" }
                        return null
                    }
                    observed=Intent(intent); count++
                    return Instrumentation.ActivityResult(code,uri?.let { Intent().setData(it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                }
            }
            test.addMonitor(intercept)
            try {
                click(activity,R.string.profiles_open_file); scope.observeImportThreads()
                main { val intent=requireNotNull(observed)
                    check(count==1 && intent.action==Intent.ACTION_OPEN_DOCUMENT && intent.type=="*/*" &&
                        intent.categories==setOf(Intent.CATEGORY_OPENABLE) && intent.component==null && intent.`package`==null)
                    check(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()==listOf("application/json","text/plain")) }
            } finally { test.removeMonitor(intercept) }
        }
        main { input(activity).setText(pasted) }
        val priorStats=scope.provider("stats")
        picker(Activity.RESULT_CANCELED,null); scope.drainImportThreads()
        main { check(input(activity).text.toString()==pasted) }
        check(scope.provider("stats").keySet().all { scope.provider("stats").getInt(it)==priorStats.getInt(it) } && invariant(scope)==frozen)
        record("SAF-cancel-preserves-draft-no-read")
        for(name in listOf("output.json","oversize.json","malformed.json")) {
            main { (field(activity,"error") as TextView).text="" }
            picker(Activity.RESULT_OK,CurveFixtureProvider.uri(session,name))
            await("SAF $name real failure callback") { errorText()==activity.getString(R.string.profiles_import_invalid) }
            scope.drainImportThreads()
            main { check(input(activity).text.toString()==pasted && activity.hasWindowFocus()) }
            check(invariant(scope)==frozen); captureState(activity,directory,"SAF-failed-$name")
            main { check(whole(observation(field(activity,"error") as TextView))) { "M3 SAF error clipped" } }
            record("SAF-$name-preserves-draft-no-write")
        }
        val successMonitor=test.addMonitor(CurveImportPreviewActivity::class.java.name,null,false)
        val fromFile: Activity
        try { picker(Activity.RESULT_OK,CurveFixtureProvider.uri(session,"valid.json"))
            fromFile=test.waitForMonitorWithTimeout(successMonitor,10000) ?: error("M3 valid UTF8 SAF did not directly enter preview") }
        finally { test.removeMonitor(successMonitor) }
        scope.drainImportThreads(); await("file preview ready") { fromFile.hasWindowFocus() && root(fromFile).isLaidOut }
        check(invariant(scope)==frozen); close(fromFile)
        await("Import foreground after file preview") { activity.hasWindowFocus() }
        main { check(input(activity).text.toString()==scope.document.encode()) }
        val stats=scope.provider("stats")
        check(stats.getInt("read:valid.json")==1 && stats.getInt("read:malformed.json")==1 && stats.getInt("read:oversize.json")==1)
        check(stats.keySet().filter { it.startsWith("write:") }.all { stats.getInt(it)==0 })
        record("SAF-valid-strict-UTF8-direct-preview-no-write")
        scope.restoreClipboard(activity)
        click(activity,R.string.beans_cancel); await("Import cancel destroyed") { activity.isDestroyed }
        check(invariant(scope)==frozen && services().isEmpty()); record("cancel-no-store-or-machine-write")
        File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases)
            .put("providerStats",JSONObject(stats.keySet().associateWith { stats.getInt(it) }))
            .put("completed",true).put("noMachineOrServiceCommands",true).put("modelsAndUsageUnchanged",true).toString(2))
    }
    private fun previewReachability(activity: Activity, directory: File) {
        val rows=mutableListOf<View>(); val reports=JSONArray(); val scrolls=nodes(root(activity)).filterIsInstance<ScrollView>()
        val prior=scrolls.map { Triple(it,it.scrollX,it.scrollY) }; val dock=requireNotNull(root(activity).findViewWithTag<View>("product/import-preview/dock"))
        lateinit var fixed: JSONObject
        main {
            val table=requireNotNull(root(activity).findViewWithTag<ViewGroup>("import-stage-table")); check(table.childCount==5)
            (0 until 5).forEach { rows.add(table.getChildAt(it)) }
            fixed=observation(dock)
        }
        val chart=nodes(root(activity)).filterIsInstance<CurveStageView>().single()
        val targets=rows+listOf(chart)+listOf("stage-caption","scope","compatibility","ability","full-details").map { requireNotNull(root(activity).findViewWithTag<View>("product/import-preview/$it")) }
        val standard=activity.resources.configuration.let { it.screenWidthDp>=1000 && it.screenHeightDp>=600 && it.fontScale<=1.1f }
        val initial=mutableListOf<JSONObject>()
        main { targets.forEach { initial.add(observation(it)) } }
        var failed=false
        try {
            targets.forEachIndexed { index,view ->
                val before=initial[index]
                main { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
                frames.committedFrame(root(activity))
                lateinit var after: JSONObject; var fixedDock=false
                main { after=observation(view); fixedDock=observation(dock).getJSONArray("rectPx").toString()==fixed.getJSONArray("rectPx").toString() }
                val initialRequired=standard && index<6 // Original five-row table plus chart, sampled together before any reveal.
                val pass=whole(after) && fixedDock && (!initialRequired || whole(before))
                reports.put(JSONObject().put("targetIndex",index).put("before",before).put("after",after)
                    .put("initialRequired",initialRequired).put("fixedDock",fixedDock).put("status",if(pass) "PASS_LAYOUT_ONLY" else "FAIL"))
                captureState(activity,directory,"reachable-$index")
                failed=failed || !pass
            }
        } finally {
            main { prior.forEach { (view,x,y) -> view.scrollTo(x,y) } }
            frames.committedFrame(root(activity))
            val restored=prior.all { (view,x,y) -> view.scrollX==x && view.scrollY==y }
            File(directory,"scroll-reachability.json").writeText(JSONObject().put("targets",reports).put("scrollRestored",restored).toString(2))
            check(restored) { "M3 Preview scroll restoration failed" }
        }
        check(!failed) { "M3 Preview actual text/table reachability or standard initial full-table gate failed; original PNGs retained" }
    }
    private fun previewBusiness(initial: Activity, scope: Scope, directory: File) {
        val fixedDomain=nonCustomInvariant(scope); val cases=JSONArray(); val original=scope.custom.list()
        fun expectDomain() { check(nonCustomInvariant(scope)==fixedDomain && services().isEmpty()) }
        fun record(name: String) {
            expectDomain(); cases.put(JSONObject().put("case",name).put("documents",JSONArray(scope.custom.list().map { it.toJson() })))
            File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases).put("completed",false).toString(2))
        }
        click(initial,R.string.profiles_existing); dismissDialog(initial)
        check(scope.custom.list()==original); record("conflict-view-local-no-write")
        click(initial,R.string.profiles_keep_existing); await("keep existing closes Preview") { initial.isDestroyed }
        check(scope.custom.list()==original); record("conflict-keep-no-write")
        var page=start(CurveImportPreviewActivity::class.java,scope.incoming)
        scope.rejectCustomWrites=true
        try { click(page,R.string.profiles_save_copy)
            main { check(action(page,R.string.profiles_save_copy).isEnabled && !page.isFinishing &&
                nodes(root(page)).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.profiles_save_failed) }) }
            check(scope.custom.list()==original); captureState(page,directory,"copy-write-failed"); record("copy-write-failure-reenabled-no-publish")
        } finally { scope.rejectCustomWrites=false }
        val attempts=scope.customWriteAttempts
        click(page,R.string.profiles_save_copy); await("explicit copy retry closes Preview") { page.isDestroyed }
        val copy=scope.custom.list().single { it.id!=scope.document.id }
        check(copy.id!=scope.document.id && copy.copy(id=scope.incoming.id)==scope.incoming && scope.custom.find(scope.document.id)==scope.document &&
            scope.customWriteAttempts==attempts+1); record("copy-explicit-retry-new-id-original-unchanged")
        val fresh=scope.document.copy(id="draft-00000000-0000-0000-0000-000000000302",name="无冲突配方")
        page=start(CurveImportPreviewActivity::class.java,fresh)
        val beforeSave=scope.custom.list()
        scope.rejectCustomWrites=true
        try {
            click(page,R.string.profiles_import_save)
            main { check(!page.isFinishing && action(page,R.string.profiles_import_save).isEnabled &&
                nodes(root(page)).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.profiles_save_failed) }) }
            check(scope.custom.list()==beforeSave); captureState(page,directory,"save-write-failed"); record("save-write-failure-reenabled-no-publish")
        } finally { scope.rejectCustomWrites=false }
        val attemptsBefore=scope.customWriteAttempts
        click(page,R.string.profiles_import_save); await("new import saved") { page.isDestroyed }
        check(scope.custom.list()==beforeSave+fresh && scope.customWriteAttempts==attemptsBefore+1); record("no-conflict-explicit-retry-save-once")
        page=start(CurveImportPreviewActivity::class.java,fresh)
        val repeatBefore=scope.custom.list(); val repeatAttempts=scope.customWriteAttempts
        click(page,R.string.profiles_import_save); await("identical repeated import finished") { page.isDestroyed }
        check(scope.custom.list()==repeatBefore && scope.customWriteAttempts==repeatAttempts); record("identical-repeat-idempotent-no-conflict-no-write")
        val runtime=scope.document.copy(id="draft-00000000-0000-0000-0000-000000000303",name="运行时冲突输入")
        page=start(CurveImportPreviewActivity::class.java,runtime)
        val existing=runtime.copy(name="运行时已存本机")
        scope.custom.save(existing); val runtimeBefore=scope.custom.list()
        click(page,R.string.profiles_import_save)
        main { check(!page.isFinishing && root(page).findViewWithTag<View>("import-conflict-notice")!=null &&
            action(page,R.string.profiles_save_copy).isEnabled) }
        check(scope.custom.list()==runtimeBefore); captureState(page,directory,"runtime-conflict")
        click(page,R.string.profiles_keep_existing); await("runtime keep closes Preview") { page.isDestroyed }
        check(scope.custom.list()==runtimeBefore); record("runtime-conflict-rerender-no-overwrite")
        val flow=scope.document.copy(id="draft-00000000-0000-0000-0000-000000000304",name="FlowRaw只读参数",controlMode=CustomCurveDocument.ControlMode.FLOW_RAW)
        page=start(CurveImportPreviewActivity::class.java,flow)
        main { check(nodes(root(page)).filterIsInstance<CurveStageView>().isEmpty())
            check(CustomPressureCurveAdapter.profile(flow,false)==null && nodes(root(page)).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.profiles_read_only) }) }
        val flowBefore=scope.custom.list(); captureState(page,directory,"flow-raw-read-only"); close(page)
        check(scope.custom.list()==flowBefore); record("FlowRaw-no-fake-pressure-no-write")
        val invalid=test.startActivitySync(Intent(context,CurveImportPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CurveImportPreviewActivity.DOCUMENT,"{bad"))
        await("invalid Preview inline error") { invalid.hasWindowFocus() && root(invalid).isLaidOut }
        main { check(nodes(root(invalid)).filterIsInstance<TextView>().any { it.text.toString()==invalid.getString(R.string.profiles_import_invalid) }) }
        check(scope.custom.list()==flowBefore); captureState(invalid,directory,"invalid-document"); close(invalid); record("invalid-document-no-write")
        File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases).put("completed",true)
            .put("noMachineOrServiceCommands",true).put("nonCustomModelsAndUsageUnchanged",true)
            .put("scopedCustomOnlyExpectedWrites",true).toString(2))
    }
    /** One real page/profile per fresh process. The host must require all three pages × four profiles. */
    fun run(arguments: Bundle): Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("curvePage")); check(page in setOf("library","import","preview"))
        val source=requireNotNull(arguments.getString("sourceSha")); check(source.matches(Regex("[0-9a-f]{40}")))
        val dark=arguments.getString("sampleTheme")=="dark"; val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat()
        val profile="tablet-user-landscape-${if(dark) "dark" else "light"}-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"curve-visual-migration/$source/$profile/$page").apply { check(isDirectory || mkdirs()) }
        val manifest=JSONObject().put("sourceSha",source).put("sourceDirty",arguments.getString("sourceDirty"))
            .put("headSha",arguments.getString("headSha")).put("apkSha256",arguments.getString("apkSha"))
            .put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("profile",profile).put("page",page).put("realProductActivity",true).put("specimenOnly",false).put("visualAcceptance","ASTRA_PENDING")
            .put("pid",android.os.Process.myPid()).put("deliveryScope","one_of_M3_three_real_Activities_by_four_profiles; host_requires_complete_12")
        var scope: Scope?=null; var failure: Throwable?=null
        try {
            val active=Scope(directory,page); scope=active; active.prepare(); active.install(dark)
            File(directory,"fixture.json").writeText(active.fixture.toString(2))
            val selectionPrefsBefore=context.getSharedPreferences("curves",Context.MODE_PRIVATE).all.toMap()
            val activity=when(page) {
                "library" -> start(CurveActivity::class.java)
                "import" -> start(CurveImportActivity::class.java)
                else -> start(CurveImportPreviewActivity::class.java,active.incoming)
            }
            unfocus(activity)
            frames.committedFrame(root(activity))
            check(services().isEmpty())
            val captured=frames.screenshot(activity,root(activity),File(directory,"window.png")) { geometry(activity) }
            val image=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,image)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(image.outWidth,image.outHeight)))
                .put("wholeWindowPngSizeSource","original_unmodified_window.png_header_not_resource_metrics_or_physical_panel_claim")
                .put("initialFrameSync",captured.proof)
            check(image.outWidth==2560 && image.outHeight==1600 && captured.proof.getInt("protocolVersion")==2 && captured.proof.getBoolean("accepted"))
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use { input ->
                val digest=MessageDigest.getInstance("SHA-256"); val bytes=ByteArray(8192)
                while(true) { val count=input.read(bytes); if(count<0) break; digest.update(bytes,0,count) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            manifest.put("fontSha256",font); check(font==TaskProductVisualStyle.FONT_SHA)
            main {
                val actual=geometry(activity); manifest.put("window",actual)
                File(directory,"geometry.json").writeText(actual.toString(2))
                check(actual.getDouble("fontScale").toFloat()==scale && actual.getString("uiModeNightMeaning")==if(dark) "dark" else "light")
                check(actual.getInt("densityDpi")==360 && actual.getString("locale").startsWith("zh") && !actual.getBoolean("imeVisible"))
                TaskTypographyEvidence.capture(activity,directory)
                if(page=="import") {
                    check(input(activity).text.isEmpty() && !action(activity,R.string.profiles_import_preview).isEnabled)
                    check(nodes(root(activity)).filterIsInstance<EditText>().size==1)
                    listOf(R.string.profiles_paste,R.string.profiles_open_file,R.string.beans_cancel,R.string.profiles_import_preview).forEach { id ->
                        val view=action(activity,id); check(whole(observation(view)) && view.height>=HoyiUi.dp(activity,48))
                    }
                } else {
                    val pairs=baselines(activity); File(directory,"baseline-alignment.json").writeText(pairs.toString(2))
                    val chart=nodes(root(activity)).filterIsInstance<CurveStageView>().single { it.productDrawEvidence.length()>0 }
                    File(directory,"canvas.json").writeText(JSONObject().put("targets",JSONArray(chart.targets))
                        .put("actualDrawOperations",chart.productDrawEvidence).toString(2))
                    check(chart.targets==if(page=="library") active.document.stages.map { it.target } else active.incoming.stages.map { it.target })
                    check(chart.isShown && chart.height>=HoyiUi.dp(activity,160)) { "M3 main pressure fixture chart missing or under 160dp" }
                    (0 until pairs.length()).forEach { check(pairs.getJSONObject(it).getString("status")!="FAIL") }
                    if(page=="library") {
                        check(list(activity).count==active.curves.items.size && list(activity).count==104)
                        check((field(activity,"selected") as CurveLibraryItem).id==active.document.id)
                        val actions=listOf(field(activity,"parameterShortcut") as Button,field(activity,"select") as Button)
                        actions.forEach { check(whole(observation(it)) && it.height>=HoyiUi.dp(activity,48)) }
                        check(kotlin.math.abs((actions[0].top+actions[0].bottom)-(actions[1].top+actions[1].bottom))<=1) {
                            "M3 original doubled-integer footer center constraint failed" }
                    } else {
                        check(root(activity).findViewWithTag<View>("import-conflict-notice")!=null && active.custom.find(active.document.id)==active.document)
                        listOf(R.string.profiles_existing,R.string.profiles_keep_existing,R.string.profiles_save_copy).forEach { id ->
                            val view=action(activity,id); check(whole(observation(view)) && view.height>=HoyiUi.dp(activity,48)) }
                    }
                }
            }
            when(page) {
                "library" -> {
                    libraryBusiness(activity,active,directory)
                    File(directory,"curve-business.json").writeText(JSONObject(File(directory,"continuous-list-business.json").readText()).toString(2))
                }
                "import" -> importBusiness(activity,active,directory)
                else -> { previewReachability(activity,directory); previewBusiness(activity,active,directory) }
            }
            if(page!="library") check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).all==selectionPrefsBefore) {
                "M3 Import/Preview changed curve selection/sort or unrelated preferences" }
            manifest.put("layoutStatus","PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
        } catch(error: Throwable) {
            failure=error; manifest.put("failure",error.stackTraceToString()).put("layoutStatus","FAIL")
            scope?.activeActivity()?.let { owner -> try {
                lateinit var before: JSONObject; main { before=geometry(owner) }
                val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
                try { File(directory,"failure-context.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
                lateinit var after: JSONObject; main { after=geometry(owner) }
                File(directory,"failure-context-geometry.json").writeText(JSONObject().put("before",before).put("after",after)
                    .put("geometryUnchanged",before.toString()==after.toString()).put("sameCompositorFrameProven",false).toString(2))
            } catch(captureFailure: Throwable) { error.addSuppressed(captureFailure) } }
        } finally {
            try {
                scope?.cleanup()
                manifest.put("isolationStatus", if(scope != null) "PASS_ISOLATION_ONLY" else "NOT_INSTALLED")
            } catch(error: Throwable) {
                manifest.put("isolationStatus","FAIL").put("cleanupFailure",error.stackTraceToString())
                if(failure==null) failure=error else failure!!.addSuppressed(error)
            }
            manifest.put("overallStatus",if(failure==null) "PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            failure?.let { manifest.put("failure",it.stackTraceToString()) }
            manifest.put("fileSha256",JSONObject(directory.walkTopDown().filter { it.isFile && it!=File(directory,"manifest.json") }.associate { it.relativeTo(directory).path to sha(it) }))
            File(directory,"manifest.json").writeText(manifest.toString(2))
        }
        failure?.let { throw it }; return 1
    }
}
